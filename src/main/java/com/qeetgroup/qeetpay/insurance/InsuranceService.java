package com.qeetgroup.qeetpay.insurance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.AccountType;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Embedded insurance (PRD Module 10, TAD §5). Issuing a policy collects its premium into an on-demand
 * {@code insurance_reserve} liability (debit {@code settlement} / credit reserve); an approved claim is
 * paid out of the reserve (debit reserve / credit {@code settlement}), while a rejected claim moves no
 * money. Every action is a balanced ledger posting and an outbox event.
 */
@Service
public class InsuranceService {

    private static final String INSURANCE_RESERVE = "insurance_reserve";

    private final InsurancePolicyRepository policies;
    private final InsuranceClaimRepository claims;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public InsuranceService(
            InsurancePolicyRepository policies,
            InsuranceClaimRepository claims,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.policies = policies;
        this.claims = claims;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Issues a policy and collects the premium into the reserve (debit settlement / credit reserve). */
    @Transactional
    public PolicyWithClaims issuePolicy(
            UUID merchantId, InsuranceProduct product, String holderRef, long premiumMinor,
            long coverAmountMinor, String currency) {
        merchantScope.apply(merchantId);
        if (product == null) {
            throw new IllegalArgumentException("product is required");
        }
        if (holderRef == null || holderRef.isBlank()) {
            throw new IllegalArgumentException("holderRef is required");
        }
        if (premiumMinor <= 0) {
            throw new IllegalArgumentException("premium must be positive");
        }
        if (coverAmountMinor <= 0) {
            throw new IllegalArgumentException("cover amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID reserve = insuranceReserve(merchantId, currency);
        UUID entryId =
                ledger.postEntry(
                        merchantId, "insurance premium " + holderRef, currency,
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, premiumMinor),
                                new LedgerLineInput(reserve, Direction.CREDIT, premiumMinor)));

        InsurancePolicy policy =
                policies.save(
                        new InsurancePolicy(
                                merchantId, product, holderRef, premiumMinor, coverAmountMinor, currency, entryId));
        outbox.enqueue(merchantId, "insurance.policy.issued", policyJson(policy));
        return new PolicyWithClaims(policy, List.of());
    }

    /** Files a claim against an active policy's cover; no money moves until it is approved. */
    @Transactional
    public InsuranceClaim fileClaim(UUID merchantId, UUID policyId, long amountMinor, String reason) {
        merchantScope.apply(merchantId);
        InsurancePolicy policy = loadPolicy(merchantId, policyId);
        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            throw new IllegalStateException("policy is " + policy.getStatus() + ", cannot file a claim");
        }
        if (amountMinor <= 0 || amountMinor > policy.getCoverAmountMinor()) {
            throw new IllegalArgumentException(
                    "claim amount " + amountMinor + " must be positive and within cover "
                            + policy.getCoverAmountMinor());
        }
        InsuranceClaim claim = claims.save(new InsuranceClaim(policyId, merchantId, amountMinor, reason));
        outbox.enqueue(merchantId, "insurance.claim.filed", claimJson(policy, claim, null));
        return claim;
    }

    /** Approves a filed claim and pays it out of the reserve (debit reserve / credit settlement). */
    @Transactional
    public InsuranceClaim approveClaim(UUID merchantId, UUID claimId) {
        merchantScope.apply(merchantId);
        InsuranceClaim claim = loadClaim(merchantId, claimId);
        InsurancePolicy policy = loadPolicy(merchantId, claim.getPolicyId());
        UUID reserve = insuranceReserve(merchantId, policy.getCurrency());
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId, "insurance payout " + claimId, policy.getCurrency(),
                        List.of(
                                new LedgerLineInput(reserve, Direction.DEBIT, claim.getAmountMinor()),
                                new LedgerLineInput(settlement, Direction.CREDIT, claim.getAmountMinor())));
        claim.approveAndPay(entryId);
        claims.save(claim);
        outbox.enqueue(merchantId, "insurance.claim.paid", claimJson(policy, claim, null));
        return claim;
    }

    /** Rejects a filed claim; no ledger movement. */
    @Transactional
    public InsuranceClaim rejectClaim(UUID merchantId, UUID claimId, String note) {
        merchantScope.apply(merchantId);
        InsuranceClaim claim = loadClaim(merchantId, claimId);
        claim.reject();
        claims.save(claim);
        InsurancePolicy policy = loadPolicy(merchantId, claim.getPolicyId());
        outbox.enqueue(merchantId, "insurance.claim.rejected", claimJson(policy, claim, note));
        return claim;
    }

    /** Cancels an active policy; no further claims may be filed against it. */
    @Transactional
    public InsurancePolicy cancelPolicy(UUID merchantId, UUID policyId) {
        merchantScope.apply(merchantId);
        InsurancePolicy policy = loadPolicy(merchantId, policyId);
        policy.cancel();
        policies.save(policy);
        outbox.enqueue(merchantId, "insurance.policy.cancelled", policyJson(policy));
        return policy;
    }

    @Transactional(readOnly = true)
    public PolicyWithClaims getPolicy(UUID merchantId, UUID policyId) {
        merchantScope.apply(merchantId);
        InsurancePolicy policy = loadPolicy(merchantId, policyId);
        return new PolicyWithClaims(policy, claims.findByPolicyIdOrderByCreatedAt(policyId));
    }

    @Transactional(readOnly = true)
    public List<InsurancePolicy> listPolicies(UUID merchantId) {
        merchantScope.apply(merchantId);
        return policies.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    private UUID insuranceReserve(UUID merchantId, String currency) {
        return ledger
                .ensureAccount(merchantId, INSURANCE_RESERVE, "Insurance reserve", AccountType.LIABILITY, currency)
                .getId();
    }

    private InsurancePolicy loadPolicy(UUID merchantId, UUID policyId) {
        return policies
                .findById(policyId)
                .filter(p -> p.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new InsuranceNotFoundException("no policy " + policyId));
    }

    private InsuranceClaim loadClaim(UUID merchantId, UUID claimId) {
        return claims
                .findById(claimId)
                .filter(c -> c.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new InsuranceNotFoundException("no claim " + claimId));
    }

    /** A policy plus its claim history. */
    public record PolicyWithClaims(InsurancePolicy policy, List<InsuranceClaim> claims) {}

    private String policyJson(InsurancePolicy p) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("policyId", p.getId().toString());
        b.put("product", p.getProduct().name());
        b.put("holderRef", p.getHolderRef());
        b.put("premiumMinor", p.getPremiumMinor());
        b.put("coverAmountMinor", p.getCoverAmountMinor());
        b.put("status", p.getStatus().name());
        b.put("premiumEntryId", p.getPremiumEntryId().toString());
        return write(b);
    }

    private String claimJson(InsurancePolicy p, InsuranceClaim c, String note) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("policyId", p.getId().toString());
        b.put("claimId", c.getId().toString());
        b.put("amountMinor", c.getAmountMinor());
        b.put("status", c.getStatus().name());
        if (c.getPayoutEntryId() != null) {
            b.put("payoutEntryId", c.getPayoutEntryId().toString());
        }
        if (note != null && !note.isBlank()) {
            b.put("note", note);
        }
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise insurance event", e);
        }
    }
}
