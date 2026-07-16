package com.qeetgroup.qeetpay.lending;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Embedded-lending API (PRD Module 10): request an advance offer, accept it (disburses funds), read
 * loans/offers, and apply a settlement sweep toward repayment.
 */
@Tag(
        name = "Lending",
        description = "Working-capital advances — request/accept offers (disburses funds) and apply settlement sweeps to repayment.")
@RestController
@RequestMapping("/v1/lending")
public class LendingController {

    private final LendingService lending;

    public LendingController(LendingService lending) {
        this.lending = lending;
    }

    // ── Offers ─────────────────────────────────────────────────────────────

    @PostMapping("/offers")
    public ResponseEntity<OfferView> requestOffer(@Valid @RequestBody OfferRequest req) {
        LoanOffer offer =
                lending.requestOffer(MerchantContext.require(), req.currency(), req.avgMonthlyVolumeMinor());
        return ResponseEntity.ok(OfferView.of(offer));
    }

    @GetMapping("/offers")
    public List<OfferView> listOffers() {
        return lending.listOffers(MerchantContext.require()).stream().map(OfferView::of).toList();
    }

    @PostMapping("/offers/{offerId}/accept")
    public ResponseEntity<LoanView> accept(@PathVariable UUID offerId) {
        return ResponseEntity.ok(LoanView.of(lending.acceptOffer(MerchantContext.require(), offerId)));
    }

    // ── Loans ──────────────────────────────────────────────────────────────

    @GetMapping("/loans")
    public List<LoanSummary> listLoans() {
        return lending.listLoans(MerchantContext.require()).stream().map(LoanSummary::of).toList();
    }

    @GetMapping("/loans/{loanId}")
    public LoanView getLoan(@PathVariable UUID loanId) {
        return LoanView.of(lending.getLoan(MerchantContext.require(), loanId));
    }

    @PostMapping("/loans/{loanId}/repayments")
    public LoanView applyRepayment(@PathVariable UUID loanId, @Valid @RequestBody RepaymentRequest req) {
        UUID merchantId = MerchantContext.require();
        lending.applyRepayment(merchantId, loanId, req.settlementAmountMinor(), req.sourceRef());
        return LoanView.of(lending.getLoan(merchantId, loanId));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record OfferRequest(
            @NotBlank String currency, @NotNull @PositiveOrZero Long avgMonthlyVolumeMinor) {}

    public record RepaymentRequest(
            @NotNull @Positive Long settlementAmountMinor, String sourceRef) {}

    public record OfferView(
            String id, String currency, long principalMinor, int feeBps, long feeMinor,
            long totalRepayableMinor, int repaymentPercentBps, String status, Instant expiresAt,
            Instant createdAt) {
        static OfferView of(LoanOffer o) {
            return new OfferView(
                    o.getId().toString(), o.getCurrency(), o.getPrincipalMinor(), o.getFeeBps(),
                    o.getFeeMinor(), o.getTotalRepayableMinor(), o.getRepaymentPercentBps(),
                    o.getStatus().name(), o.getExpiresAt(), o.getCreatedAt());
        }
    }

    public record RepaymentView(
            long settlementAmountMinor, long sweptMinor, String ledgerEntryId, Instant createdAt) {
        static RepaymentView of(LoanRepayment r) {
            return new RepaymentView(
                    r.getSettlementAmountMinor(), r.getSweptMinor(), r.getLedgerEntryId().toString(),
                    r.getCreatedAt());
        }
    }

    public record LoanSummary(
            String id, String offerId, String currency, long principalMinor, long feeMinor,
            long totalRepayableMinor, long outstandingMinor, int repaymentPercentBps, String status,
            String disbursedEntryId, Instant disbursedAt, Instant repaidAt) {
        static LoanSummary of(Loan l) {
            return new LoanSummary(
                    l.getId().toString(), l.getOfferId().toString(), l.getCurrency(), l.getPrincipalMinor(),
                    l.getFeeMinor(), l.getTotalRepayableMinor(), l.getOutstandingMinor(),
                    l.getRepaymentPercentBps(), l.getStatus().name(), l.getDisbursedEntryId().toString(),
                    l.getDisbursedAt(), l.getRepaidAt());
        }
    }

    public record LoanView(LoanSummary loan, List<RepaymentView> repayments) {
        static LoanView of(LendingService.LoanWithRepayments l) {
            return new LoanView(
                    LoanSummary.of(l.loan()),
                    l.repayments().stream().map(RepaymentView::of).toList());
        }
    }
}
