package com.qeetgroup.qeetpay.agentic;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent-mandate API (PRD Module 17.5, Novel N1): issue a scoped mandate of authority to an AI agent,
 * ask for a deterministic ALLOW/DENY on a proposed action (idempotent on an agent-supplied key),
 * revoke it, and read mandates with their decision history. No money moves here — the decision gates
 * the real action in the relevant payments/payouts/etc. module.
 */
@Tag(
        name = "Agentic Mandates",
        description =
                "AI-agent mandates — scoped, capped, revocable authority with a deterministic authorize decision.")
@RestController
@RequestMapping("/v1/agentic/mandates")
public class AgentMandateController {

    private final AgentMandateService service;

    public AgentMandateController(AgentMandateService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MandateView> issue(@Valid @RequestBody IssueRequest req) {
        AgentMandate m =
                service.issue(
                        MerchantContext.require(),
                        req.agentId(),
                        req.label(),
                        req.maxTxnMinor(),
                        req.cumulativeCapMinor(),
                        req.allowedOperations(),
                        req.allowedPayees(),
                        req.validFrom(),
                        req.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(MandateView.of(m));
    }

    @GetMapping
    public List<MandateView> list() {
        return service.list(MerchantContext.require()).stream().map(MandateView::of).toList();
    }

    @GetMapping("/{mandateId}")
    public MandateDetail get(@PathVariable UUID mandateId) {
        return MandateDetail.of(service.getWithUses(MerchantContext.require(), mandateId));
    }

    @PostMapping("/{mandateId}/authorize")
    public AuthorizationDecision authorize(
            @PathVariable UUID mandateId,
            @Valid @RequestBody AuthorizeRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String headerKey) {
        String key =
                (req.idempotencyKey() != null && !req.idempotencyKey().isBlank())
                        ? req.idempotencyKey()
                        : headerKey;
        boolean capture = req.capture() == null || req.capture();
        return service.authorize(
                MerchantContext.require(),
                mandateId,
                req.operation(),
                req.payeeRef(),
                req.amountMinor(),
                capture,
                key);
    }

    @PostMapping("/{mandateId}/revoke")
    public MandateView revoke(@PathVariable UUID mandateId, @RequestBody(required = false) RevokeRequest req) {
        String reason = req == null ? null : req.reason();
        return MandateView.of(service.revoke(MerchantContext.require(), mandateId, reason));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record IssueRequest(
            @NotBlank String agentId,
            String label,
            @NotNull @Positive Long maxTxnMinor,
            @NotNull @Positive Long cumulativeCapMinor,
            List<String> allowedOperations,
            List<String> allowedPayees,
            Instant validFrom,
            Instant expiresAt) {}

    public record AuthorizeRequest(
            @NotBlank String operation,
            String payeeRef,
            @NotNull @Positive Long amountMinor,
            Boolean capture,
            String idempotencyKey) {}

    public record RevokeRequest(String reason) {}

    public record MandateView(
            String id,
            String agentId,
            String label,
            long maxTxnMinor,
            long cumulativeCapMinor,
            long spentMinor,
            long remainingMinor,
            List<String> allowedOperations,
            List<String> allowedPayees,
            Instant validFrom,
            Instant expiresAt,
            String status,
            Instant createdAt,
            Instant revokedAt) {
        static MandateView of(AgentMandate m) {
            return new MandateView(
                    m.getId().toString(),
                    m.getAgentId(),
                    m.getLabel(),
                    m.getMaxTxnMinor(),
                    m.getCumulativeCapMinor(),
                    m.getSpentMinor(),
                    m.remainingMinor(),
                    m.getAllowedOperations(),
                    m.getAllowedPayees(),
                    m.getValidFrom(),
                    m.getExpiresAt(),
                    m.getStatus().name(),
                    m.getCreatedAt(),
                    m.getRevokedAt());
        }
    }

    public record UseView(
            String id,
            String operation,
            String payeeRef,
            long amountMinor,
            boolean allowed,
            String reason,
            Instant createdAt) {
        static UseView of(AgentMandateUse u) {
            return new UseView(
                    u.getId().toString(),
                    u.getOperation(),
                    u.getPayeeRef(),
                    u.getAmountMinor(),
                    u.isAllowed(),
                    u.getReason(),
                    u.getCreatedAt());
        }
    }

    public record MandateDetail(MandateView mandate, List<UseView> uses) {
        static MandateDetail of(AgentMandateService.MandateWithUses m) {
            return new MandateDetail(
                    MandateView.of(m.mandate()), m.uses().stream().map(UseView::of).toList());
        }
    }
}
