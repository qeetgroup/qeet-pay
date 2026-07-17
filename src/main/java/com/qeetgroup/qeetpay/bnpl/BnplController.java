package com.qeetgroup.qeetpay.bnpl;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.time.LocalDate;
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
 * Buy-Now-Pay-Later API (PRD Module 10): open an agreement (funding the merchant upfront and
 * scheduling the customer's installments), read agreements with their schedule, and record each
 * installment repayment.
 */
@Tag(
        name = "BNPL",
        description = "Buy-Now-Pay-Later — open agreements (fund the merchant upfront), read installment schedules, and record repayments.")
@RestController
@RequestMapping("/v1/bnpl")
public class BnplController {

    private final BnplService bnpl;

    public BnplController(BnplService bnpl) {
        this.bnpl = bnpl;
    }

    @PostMapping("/agreements")
    public ResponseEntity<AgreementView> create(@Valid @RequestBody CreateAgreementRequest req) {
        BnplService.AgreementWithInstallments created =
                bnpl.createAgreement(
                        MerchantContext.require(), req.customerRef(), req.orderRef(), req.orderAmountMinor(),
                        req.currency(), req.installments(), req.interestBps(), req.firstDueDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(AgreementView.of(created));
    }

    @GetMapping("/agreements")
    public List<AgreementSummary> list() {
        return bnpl.listAgreements(MerchantContext.require()).stream().map(AgreementSummary::of).toList();
    }

    @GetMapping("/agreements/{agreementId}")
    public AgreementView get(@PathVariable UUID agreementId) {
        return AgreementView.of(bnpl.getAgreement(MerchantContext.require(), agreementId));
    }

    @PostMapping("/agreements/{agreementId}/installments/{seq}/pay")
    public AgreementView pay(@PathVariable UUID agreementId, @PathVariable int seq) {
        return AgreementView.of(bnpl.payInstallment(MerchantContext.require(), agreementId, seq));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record CreateAgreementRequest(
            @NotBlank String customerRef,
            @NotBlank String orderRef,
            @NotNull @Positive Long orderAmountMinor,
            @NotBlank String currency,
            @NotNull @Positive Integer installments,
            @NotNull @PositiveOrZero Integer interestBps,
            @NotNull LocalDate firstDueDate) {}

    public record InstallmentView(int seq, LocalDate dueDate, long amountMinor, String status, Instant paidAt) {
        static InstallmentView of(BnplInstallment i) {
            return new InstallmentView(
                    i.getSeq(), i.getDueDate(), i.getAmountMinor(), i.getStatus().name(), i.getPaidAt());
        }
    }

    public record AgreementSummary(
            String id, String customerRef, String orderRef, String currency, long orderAmountMinor,
            int interestBps, long totalPayableMinor, int installmentsCount, int paidInstallments,
            String status, Instant createdAt, Instant settledAt) {
        static AgreementSummary of(BnplAgreement a) {
            return new AgreementSummary(
                    a.getId().toString(), a.getCustomerRef(), a.getOrderRef(), a.getCurrency(), a.getOrderAmountMinor(),
                    a.getInterestBps(), a.getTotalPayableMinor(), a.getInstallmentsCount(), a.getPaidInstallments(),
                    a.getStatus().name(), a.getCreatedAt(), a.getSettledAt());
        }
    }

    public record AgreementView(AgreementSummary agreement, List<InstallmentView> installments) {
        static AgreementView of(BnplService.AgreementWithInstallments a) {
            return new AgreementView(
                    AgreementSummary.of(a.agreement()),
                    a.installments().stream().map(InstallmentView::of).toList());
        }
    }
}
