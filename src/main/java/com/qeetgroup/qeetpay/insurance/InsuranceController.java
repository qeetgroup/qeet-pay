package com.qeetgroup.qeetpay.insurance;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Embedded-insurance API (PRD Module 10): issue a protection policy (collecting its premium into the
 * insurance reserve), file claims against the cover, and approve (pay out) or reject them.
 */
@Tag(
        name = "Insurance",
        description = "Embedded insurance — issue protection policies (collecting premium) and file, approve or reject claims.")
@RestController
@RequestMapping("/v1/insurance")
public class InsuranceController {

    private final InsuranceService insurance;

    public InsuranceController(InsuranceService insurance) {
        this.insurance = insurance;
    }

    @PostMapping("/policies")
    public ResponseEntity<PolicyView> issue(@Valid @RequestBody IssuePolicyRequest req) {
        InsuranceService.PolicyWithClaims issued =
                insurance.issuePolicy(
                        MerchantContext.require(), req.product(), req.holderRef(),
                        req.premiumMinor(), req.coverAmountMinor(), req.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(PolicyView.of(issued));
    }

    @GetMapping("/policies")
    public List<PolicySummary> list() {
        return insurance.listPolicies(MerchantContext.require()).stream().map(PolicySummary::of).toList();
    }

    @GetMapping("/policies/{policyId}")
    public PolicyView get(@PathVariable UUID policyId) {
        return PolicyView.of(insurance.getPolicy(MerchantContext.require(), policyId));
    }

    @PostMapping("/policies/{policyId}/cancel")
    public PolicyView cancel(@PathVariable UUID policyId) {
        UUID merchantId = MerchantContext.require();
        insurance.cancelPolicy(merchantId, policyId);
        return PolicyView.of(insurance.getPolicy(merchantId, policyId));
    }

    @PostMapping("/policies/{policyId}/claims")
    public ResponseEntity<ClaimView> fileClaim(
            @PathVariable UUID policyId, @Valid @RequestBody FileClaimRequest req) {
        InsuranceClaim claim =
                insurance.fileClaim(MerchantContext.require(), policyId, req.amountMinor(), req.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(ClaimView.of(claim));
    }

    @PostMapping("/claims/{claimId}/approve")
    public ClaimView approve(@PathVariable UUID claimId) {
        return ClaimView.of(insurance.approveClaim(MerchantContext.require(), claimId));
    }

    @PostMapping("/claims/{claimId}/reject")
    public ClaimView reject(@PathVariable UUID claimId, @RequestBody(required = false) RejectRequest req) {
        String note = req == null ? null : req.note();
        return ClaimView.of(insurance.rejectClaim(MerchantContext.require(), claimId, note));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record IssuePolicyRequest(
            @NotNull InsuranceProduct product,
            @NotBlank String holderRef,
            @NotNull @Positive Long premiumMinor,
            @NotNull @Positive Long coverAmountMinor,
            @NotBlank String currency) {}

    public record FileClaimRequest(@NotNull @Positive Long amountMinor, String reason) {}

    public record RejectRequest(String note) {}

    public record ClaimView(
            String id, String policyId, long amountMinor, String reason, String status,
            String payoutEntryId, Instant createdAt, Instant decidedAt) {
        static ClaimView of(InsuranceClaim c) {
            return new ClaimView(
                    c.getId().toString(), c.getPolicyId().toString(), c.getAmountMinor(), c.getReason(),
                    c.getStatus().name(),
                    c.getPayoutEntryId() == null ? null : c.getPayoutEntryId().toString(),
                    c.getCreatedAt(), c.getDecidedAt());
        }
    }

    public record PolicySummary(
            String id, String product, String holderRef, String currency, long premiumMinor,
            long coverAmountMinor, String status, Instant createdAt, Instant cancelledAt) {
        static PolicySummary of(InsurancePolicy p) {
            return new PolicySummary(
                    p.getId().toString(), p.getProduct().name(), p.getHolderRef(), p.getCurrency(),
                    p.getPremiumMinor(), p.getCoverAmountMinor(), p.getStatus().name(),
                    p.getCreatedAt(), p.getCancelledAt());
        }
    }

    public record PolicyView(PolicySummary policy, List<ClaimView> claims) {
        static PolicyView of(InsuranceService.PolicyWithClaims p) {
            return new PolicyView(
                    PolicySummary.of(p.policy()),
                    p.claims().stream().map(ClaimView::of).toList());
        }
    }
}
