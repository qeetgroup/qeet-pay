package com.qeetgroup.qeetpay.agentic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyRecord;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, authorizes, revokes and lists agent mandates (PRD Module 17.5, Novel N1).
 *
 * <p><b>Authorization is purely deterministic</b> — no model call. An action is ALLOWED only if the
 * mandate is ACTIVE, inside its validity window, the amount is within both the per-transaction cap and
 * the remaining cumulative cap, and the operation + payee are on their allowlists; otherwise it is
 * DENIED with a specific reason. On an allowed <em>capture</em> the mandate's spent counter is
 * incremented. Every decision is an append-only {@link AgentMandateUse} and an outbox event
 * ({@code agentic.mandate.authorized}/{@code .denied}).
 *
 * <p>Authorize is idempotent on an agent-supplied key: the key is namespaced per mandate and stored
 * via the shared {@link IdempotencyService}, so a retried authorize replays the original decision and
 * never double-spends.
 *
 * <p>Merchant-scoped via {@link MerchantScope} + Postgres RLS. Depends on {@code platform} only; it
 * never touches the ledger — the money movement for an authorized action happens in the relevant
 * payments/payouts/etc. module, gated by this decision.
 */
@Service
public class AgentMandateService {

    private static final String IDEM_NAMESPACE = "agentic:authorize:";

    private final AgentMandateRepository mandates;
    private final AgentMandateUseRepository uses;
    private final McpManifestService mcp;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public AgentMandateService(
            AgentMandateRepository mandates,
            AgentMandateUseRepository uses,
            McpManifestService mcp,
            MerchantScope merchantScope,
            OutboxService outbox,
            IdempotencyService idempotency,
            ObjectMapper objectMapper) {
        this.mandates = mandates;
        this.uses = uses;
        this.mcp = mcp;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    /** Issues a new ACTIVE mandate for an agent. Allowlisted operations must be known MCP tools. */
    @Transactional
    public AgentMandate issue(
            UUID merchantId,
            String agentId,
            String label,
            long maxTxnMinor,
            long cumulativeCapMinor,
            List<String> allowedOperations,
            List<String> allowedPayees,
            Instant validFrom,
            Instant expiresAt) {
        merchantScope.apply(merchantId);
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        if (maxTxnMinor <= 0) {
            throw new IllegalArgumentException("maxTxnMinor must be positive");
        }
        if (cumulativeCapMinor <= 0) {
            throw new IllegalArgumentException("cumulativeCapMinor must be positive");
        }
        if (cumulativeCapMinor < maxTxnMinor) {
            throw new IllegalArgumentException("cumulativeCapMinor must be >= maxTxnMinor");
        }
        if (allowedOperations != null) {
            for (String op : allowedOperations) {
                if (!mcp.toolNames().contains(op)) {
                    throw new IllegalArgumentException("unknown operation (not an MCP tool): " + op);
                }
            }
        }
        AgentMandate mandate =
                mandates.save(
                        new AgentMandate(
                                merchantId,
                                agentId,
                                label,
                                maxTxnMinor,
                                cumulativeCapMinor,
                                allowedOperations,
                                allowedPayees,
                                validFrom,
                                expiresAt));
        outbox.enqueue(merchantId, "agentic.mandate.issued", issueJson(mandate));
        return mandate;
    }

    /**
     * Deterministically authorizes (and, on capture, charges) an action against a mandate. Idempotent
     * on {@code idempotencyKey} (agent-supplied): a repeat replays the original decision.
     */
    @Transactional
    public AuthorizationDecision authorize(
            UUID merchantId,
            UUID mandateId,
            String operation,
            String payeeRef,
            long amountMinor,
            boolean capture,
            String idempotencyKey) {
        merchantScope.apply(merchantId);

        String namespacedKey = namespacedKey(mandateId, idempotencyKey);
        if (namespacedKey != null) {
            Optional<IdempotencyRecord> prior = idempotency.lookup(merchantId, namespacedKey);
            if (prior.isPresent()) {
                return readDecision(prior.get().getResponseBody()); // replay — no re-spend, no new event
            }
        }

        AgentMandate mandate = load(merchantId, mandateId);
        Instant now = Instant.now();

        // Lazily flip an ACTIVE-but-past-expiry mandate to EXPIRED so the state is honest.
        if (mandate.getStatus() == AgentMandateStatus.ACTIVE && mandate.isExpired(now)) {
            mandate.markExpired();
            mandates.save(mandate);
        }

        Eval eval = evaluate(mandate, operation, payeeRef, amountMinor, now);
        if (eval.allowed() && capture) {
            mandate.recordSpend(amountMinor);
            mandates.save(mandate);
        }

        AgentMandateUse use =
                uses.save(
                        new AgentMandateUse(
                                mandateId, merchantId, idempotencyKey, operation, payeeRef,
                                amountMinor, eval.allowed(), eval.reason()));

        AuthorizationDecision decision =
                new AuthorizationDecision(
                        mandateId.toString(),
                        eval.allowed(),
                        eval.reason(),
                        operation,
                        payeeRef,
                        amountMinor,
                        mandate.getSpentMinor(),
                        mandate.remainingMinor(),
                        use.getId().toString());

        outbox.enqueue(
                merchantId,
                eval.allowed() ? "agentic.mandate.authorized" : "agentic.mandate.denied",
                writeDecision(decision));

        if (namespacedKey != null) {
            idempotency.save(merchantId, namespacedKey, 200, writeDecision(decision));
        }
        return decision;
    }

    /** Revokes a mandate; further authorizations are denied. Idempotent-ish (revoke of a revoked stays revoked). */
    @Transactional
    public AgentMandate revoke(UUID merchantId, UUID mandateId, String reason) {
        merchantScope.apply(merchantId);
        AgentMandate mandate = load(merchantId, mandateId);
        if (mandate.getStatus() != AgentMandateStatus.REVOKED) {
            mandate.revoke();
            mandates.save(mandate);
            outbox.enqueue(merchantId, "agentic.mandate.revoked", revokeJson(mandate, reason));
        }
        return mandate;
    }

    @Transactional(readOnly = true)
    public List<AgentMandate> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return mandates.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public AgentMandate get(UUID merchantId, UUID mandateId) {
        merchantScope.apply(merchantId);
        return load(merchantId, mandateId);
    }

    @Transactional(readOnly = true)
    public MandateWithUses getWithUses(UUID merchantId, UUID mandateId) {
        merchantScope.apply(merchantId);
        AgentMandate mandate = load(merchantId, mandateId);
        return new MandateWithUses(mandate, uses.findByMandateIdOrderByCreatedAt(mandateId));
    }

    // ── Deterministic decision core ──────────────────────────────────────────

    private Eval evaluate(
            AgentMandate mandate, String operation, String payeeRef, long amountMinor, Instant now) {
        if (mandate.getStatus() == AgentMandateStatus.REVOKED) {
            return Eval.deny("mandate revoked");
        }
        if (mandate.getStatus() == AgentMandateStatus.EXPIRED || mandate.isExpired(now)) {
            return Eval.deny("mandate expired");
        }
        if (mandate.isNotYetValid(now)) {
            return Eval.deny("mandate not yet valid");
        }
        if (amountMinor <= 0) {
            return Eval.deny("amount must be positive");
        }
        if (amountMinor > mandate.getMaxTxnMinor()) {
            return Eval.deny("amount exceeds per-transaction cap");
        }
        if (mandate.getSpentMinor() + amountMinor > mandate.getCumulativeCapMinor()) {
            return Eval.deny("amount exceeds cumulative cap");
        }
        if (operation == null || operation.isBlank()) {
            return Eval.deny("operation is required");
        }
        List<String> ops = mandate.getAllowedOperations();
        if (!ops.isEmpty() && !ops.contains(operation)) {
            return Eval.deny("operation not permitted");
        }
        List<String> payees = mandate.getAllowedPayees();
        if (!payees.isEmpty() && (payeeRef == null || !payees.contains(payeeRef))) {
            return Eval.deny("payee not in allowlist");
        }
        return Eval.allow();
    }

    private record Eval(boolean allowed, String reason) {
        static Eval allow() {
            return new Eval(true, "authorized");
        }

        static Eval deny(String reason) {
            return new Eval(false, reason);
        }
    }

    /** A mandate plus its decision history. */
    public record MandateWithUses(AgentMandate mandate, List<AgentMandateUse> uses) {}

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AgentMandate load(UUID merchantId, UUID mandateId) {
        return mandates
                .findById(mandateId)
                .filter(m -> m.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new AgentMandateNotFoundException("no agent mandate " + mandateId));
    }

    private String namespacedKey(UUID mandateId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return IDEM_NAMESPACE + mandateId + ":" + idempotencyKey;
    }

    private String writeDecision(AuthorizationDecision decision) {
        try {
            return objectMapper.writeValueAsString(decision);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise authorization decision", e);
        }
    }

    private AuthorizationDecision readDecision(String json) {
        try {
            return objectMapper.readValue(json, AuthorizationDecision.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialise authorization decision", e);
        }
    }

    private String issueJson(AgentMandate m) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("mandateId", m.getId().toString());
        b.put("agentId", m.getAgentId());
        b.put("maxTxnMinor", m.getMaxTxnMinor());
        b.put("cumulativeCapMinor", m.getCumulativeCapMinor());
        b.put("allowedOperations", m.getAllowedOperations());
        b.put("allowedPayees", m.getAllowedPayees());
        b.put("status", m.getStatus().name());
        return writeMap(b);
    }

    private String revokeJson(AgentMandate m, String reason) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("mandateId", m.getId().toString());
        b.put("agentId", m.getAgentId());
        b.put("status", m.getStatus().name());
        b.put("reason", reason);
        return writeMap(b);
    }

    private String writeMap(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise agentic event", e);
        }
    }
}
