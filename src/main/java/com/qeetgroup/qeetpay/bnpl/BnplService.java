package com.qeetgroup.qeetpay.bnpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buy-Now-Pay-Later (PRD Module 10, TAD §5). At checkout the provider funds the merchant the full
 * order amount immediately, posted once as a balanced ledger entry (debit {@code settlement} / credit
 * {@code revenue}); the customer then repays the platform over the installment schedule built by
 * {@link InstallmentCalculator}. Installment repayments are between the customer and the platform, so
 * they change agreement/installment state only and never post to the merchant ledger. Every
 * transition is an outbox event.
 */
@Service
public class BnplService {

    private final BnplAgreementRepository agreements;
    private final BnplInstallmentRepository installments;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public BnplService(
            BnplAgreementRepository agreements,
            BnplInstallmentRepository installments,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.agreements = agreements;
        this.installments = installments;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Opens a BNPL agreement: funds the merchant the full order amount (debit settlement / credit
     * revenue) and schedules the customer's installments.
     */
    @Transactional
    public AgreementWithInstallments createAgreement(
            UUID merchantId, String customerRef, String orderRef, long orderAmountMinor,
            String currency, int installmentsCount, int interestBps, LocalDate firstDueDate) {
        merchantScope.apply(merchantId);
        if (customerRef == null || customerRef.isBlank() || orderRef == null || orderRef.isBlank()) {
            throw new IllegalArgumentException("customerRef and orderRef are required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }

        List<InstallmentCalculator.Installment> schedule =
                InstallmentCalculator.schedule(orderAmountMinor, installmentsCount, interestBps, firstDueDate);
        long totalPayableMinor = schedule.stream().mapToLong(InstallmentCalculator.Installment::amountMinor).sum();

        // The BNPL provider funds the merchant the full order amount upfront (one balanced posting).
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId, "bnpl sale " + orderRef, currency,
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, orderAmountMinor),
                                new LedgerLineInput(revenue, Direction.CREDIT, orderAmountMinor)));

        BnplAgreement agreement =
                agreements.save(
                        new BnplAgreement(
                                merchantId, customerRef, orderRef, orderAmountMinor, interestBps,
                                totalPayableMinor, installmentsCount, currency, entryId));

        List<BnplInstallment> saved = new ArrayList<>(schedule.size());
        for (InstallmentCalculator.Installment slice : schedule) {
            saved.add(
                    installments.save(
                            new BnplInstallment(
                                    agreement.getId(), merchantId, slice.seq(), slice.dueDate(), slice.amountMinor())));
        }
        outbox.enqueue(merchantId, "bnpl.agreement.created", agreementJson(agreement));
        return new AgreementWithInstallments(agreement, saved);
    }

    /**
     * Records the customer's repayment of one installment (state only — no ledger posting). Paying an
     * already-paid installment is a no-op; the final installment settles the agreement.
     */
    @Transactional
    public AgreementWithInstallments payInstallment(UUID merchantId, UUID agreementId, int seq) {
        merchantScope.apply(merchantId);
        BnplAgreement agreement = load(merchantId, agreementId);
        BnplInstallment installment =
                installments
                        .findByAgreementIdAndSeq(agreementId, seq)
                        .filter(i -> i.getMerchantId().equals(merchantId))
                        .orElseThrow(() -> new BnplNotFoundException("no installment " + seq + " on agreement " + agreementId));

        if (installment.getStatus() == InstallmentStatus.PAID) {
            return new AgreementWithInstallments(agreement, installments.findByAgreementIdOrderBySeq(agreementId));
        }

        installment.markPaid();
        installments.save(installment);
        agreement.markInstallmentPaid();
        agreements.save(agreement);

        String eventType = agreement.getStatus() == BnplStatus.SETTLED ? "bnpl.settled" : "bnpl.installment.paid";
        outbox.enqueue(merchantId, eventType, installmentJson(agreement, installment));
        return new AgreementWithInstallments(agreement, installments.findByAgreementIdOrderBySeq(agreementId));
    }

    @Transactional(readOnly = true)
    public AgreementWithInstallments getAgreement(UUID merchantId, UUID agreementId) {
        merchantScope.apply(merchantId);
        BnplAgreement agreement = load(merchantId, agreementId);
        return new AgreementWithInstallments(agreement, installments.findByAgreementIdOrderBySeq(agreementId));
    }

    @Transactional(readOnly = true)
    public List<BnplAgreement> listAgreements(UUID merchantId) {
        merchantScope.apply(merchantId);
        return agreements.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    private BnplAgreement load(UUID merchantId, UUID agreementId) {
        return agreements
                .findById(agreementId)
                .filter(a -> a.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new BnplNotFoundException("no bnpl agreement " + agreementId));
    }

    /** A BNPL agreement plus its installment schedule. */
    public record AgreementWithInstallments(BnplAgreement agreement, List<BnplInstallment> installments) {}

    private String agreementJson(BnplAgreement a) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("agreementId", a.getId().toString());
        b.put("customerRef", a.getCustomerRef());
        b.put("orderRef", a.getOrderRef());
        b.put("orderAmountMinor", a.getOrderAmountMinor());
        b.put("totalPayableMinor", a.getTotalPayableMinor());
        b.put("installmentsCount", a.getInstallmentsCount());
        b.put("status", a.getStatus().name());
        b.put("saleEntryId", a.getSaleEntryId().toString());
        return write(b);
    }

    private String installmentJson(BnplAgreement a, BnplInstallment i) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("agreementId", a.getId().toString());
        b.put("seq", i.getSeq());
        b.put("amountMinor", i.getAmountMinor());
        b.put("paidInstallments", a.getPaidInstallments());
        b.put("installmentsCount", a.getInstallmentsCount());
        b.put("status", a.getStatus().name());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise bnpl event", e);
        }
    }
}
