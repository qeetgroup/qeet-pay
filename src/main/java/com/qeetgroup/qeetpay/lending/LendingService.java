package com.qeetgroup.qeetpay.lending;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.AccountType;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Embedded lending (PRD Module 10, TAD §5). Underwrites an advance offer via the pluggable {@link
 * UnderwritingAdapter}; on acceptance disburses it (debit {@code settlement} + {@code fees} / credit a
 * dedicated {@code loan_payable} account) and sweeps repayments from settlements (debit {@code
 * loan_payable} / credit {@code settlement}) until the total repayable clears. Every state change is
 * a balanced ledger posting and an outbox event.
 */
@Service
public class LendingService {

    private static final String LOAN_PAYABLE = "loan_payable";

    private final LoanOfferRepository offers;
    private final LoanRepository loans;
    private final LoanRepaymentRepository repayments;
    private final UnderwritingAdapter underwriter;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public LendingService(
            LoanOfferRepository offers,
            LoanRepository loans,
            LoanRepaymentRepository repayments,
            UnderwritingAdapter underwriter,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.offers = offers;
        this.loans = loans;
        this.repayments = repayments;
        this.underwriter = underwriter;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Underwrites and persists an advance offer, or rejects if the merchant is not eligible. */
    @Transactional
    public LoanOffer requestOffer(UUID merchantId, String currency, long avgMonthlyVolumeMinor) {
        merchantScope.apply(merchantId);
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        if (avgMonthlyVolumeMinor < 0) {
            throw new IllegalArgumentException("avgMonthlyVolumeMinor must be non-negative");
        }
        OfferTerms terms = underwriter.underwrite(merchantId, currency, avgMonthlyVolumeMinor);
        if (!terms.eligible()) {
            throw new IllegalStateException("merchant not eligible for a working-capital advance");
        }
        long feeMinor = bps(terms.principalMinor(), terms.feeBps());
        Instant expiresAt = Instant.now().plus(terms.validityDays(), ChronoUnit.DAYS);
        LoanOffer offer =
                offers.save(
                        new LoanOffer(
                                merchantId, currency, terms.principalMinor(), terms.feeBps(), feeMinor,
                                terms.repaymentPercentBps(), avgMonthlyVolumeMinor, expiresAt));
        outbox.enqueue(merchantId, "lending.offer.created", offerJson(offer));
        return offer;
    }

    /** Accepts an offer and disburses the advance with a balanced ledger posting. */
    @Transactional
    public LoanWithRepayments acceptOffer(UUID merchantId, UUID offerId) {
        merchantScope.apply(merchantId);
        LoanOffer offer = loadOffer(merchantId, offerId);
        if (offer.getStatus() == LoanOfferStatus.OFFERED && offer.isExpired(Instant.now())) {
            offer.markDeclined();
            offers.save(offer);
            throw new IllegalStateException("offer has expired");
        }

        UUID entryId = postDisbursement(merchantId, offer);
        offer.markAccepted();
        offers.save(offer);

        Loan loan = loans.save(new Loan(merchantId, offer, entryId));
        outbox.enqueue(merchantId, "lending.loan.disbursed", loanJson(loan));
        return new LoanWithRepayments(loan, List.of());
    }

    /**
     * Sweeps the repayment share of a settlement toward a loan (revenue-based repayment). Returns the
     * updated loan; a settlement too small to sweep any paise is a no-op.
     */
    @Transactional
    public Loan applyRepayment(UUID merchantId, UUID loanId, long settlementAmountMinor, String sourceRef) {
        merchantScope.apply(merchantId);
        if (settlementAmountMinor < 0) {
            throw new IllegalArgumentException("settlement amount must be non-negative");
        }
        Loan loan = loadLoan(merchantId, loanId);
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new IllegalStateException("loan is " + loan.getStatus() + ", nothing to repay");
        }
        long sweep = Math.min(loan.getOutstandingMinor(), bps(settlementAmountMinor, loan.getRepaymentPercentBps()));
        if (sweep <= 0) {
            return loan; // settlement too small to sweep a whole paise
        }

        UUID entryId = postRepayment(merchantId, loan, sweep);
        loan.applyRepayment(sweep);
        loans.save(loan);
        repayments.save(new LoanRepayment(loanId, merchantId, settlementAmountMinor, sweep, entryId, sourceRef));

        String eventType = loan.getStatus() == LoanStatus.REPAID ? "lending.loan.repaid" : "lending.loan.repayment";
        outbox.enqueue(merchantId, eventType, repaymentJson(loan, sweep, entryId));
        return loan;
    }

    @Transactional(readOnly = true)
    public LoanWithRepayments getLoan(UUID merchantId, UUID loanId) {
        merchantScope.apply(merchantId);
        Loan loan = loadLoan(merchantId, loanId);
        return new LoanWithRepayments(loan, repayments.findByLoanIdOrderByCreatedAt(loanId));
    }

    @Transactional(readOnly = true)
    public List<Loan> listLoans(UUID merchantId) {
        merchantScope.apply(merchantId);
        return loans.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public List<LoanOffer> listOffers(UUID merchantId) {
        merchantScope.apply(merchantId);
        return offers.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    // ── Ledger postings ──────────────────────────────────────────────────────

    private UUID postDisbursement(UUID merchantId, LoanOffer offer) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID loanPayable =
                ledger.ensureAccount(merchantId, LOAN_PAYABLE, "Loan payable", AccountType.LIABILITY, offer.getCurrency())
                        .getId();
        List<LedgerLineInput> entry = new ArrayList<>();
        entry.add(new LedgerLineInput(settlement, Direction.DEBIT, offer.getPrincipalMinor()));
        if (offer.getFeeMinor() > 0) {
            UUID fees = ledger.accountByCode(merchantId, "fees").getId();
            entry.add(new LedgerLineInput(fees, Direction.DEBIT, offer.getFeeMinor()));
        }
        entry.add(new LedgerLineInput(loanPayable, Direction.CREDIT, offer.getTotalRepayableMinor()));
        return ledger.postEntry(merchantId, "loan disbursement " + offer.getId(), offer.getCurrency(), entry);
    }

    private UUID postRepayment(UUID merchantId, Loan loan, long sweep) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID loanPayable =
                ledger.ensureAccount(merchantId, LOAN_PAYABLE, "Loan payable", AccountType.LIABILITY, loan.getCurrency())
                        .getId();
        return ledger.postEntry(
                merchantId,
                "loan repayment " + loan.getId(),
                loan.getCurrency(),
                List.of(
                        new LedgerLineInput(loanPayable, Direction.DEBIT, sweep),
                        new LedgerLineInput(settlement, Direction.CREDIT, sweep)));
    }

    /** amount · bps / 10000, HALF_UP to whole minor units. */
    private static long bps(long amountMinor, int basisPoints) {
        return BigDecimal.valueOf(amountMinor)
                .multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private LoanOffer loadOffer(UUID merchantId, UUID offerId) {
        return offers
                .findById(offerId)
                .filter(o -> o.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new LoanNotFoundException("no offer " + offerId));
    }

    private Loan loadLoan(UUID merchantId, UUID loanId) {
        return loans
                .findById(loanId)
                .filter(l -> l.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new LoanNotFoundException("no loan " + loanId));
    }

    /** A loan plus its repayment history. */
    public record LoanWithRepayments(Loan loan, List<LoanRepayment> repayments) {}

    private String offerJson(LoanOffer o) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("offerId", o.getId().toString());
        b.put("principalMinor", o.getPrincipalMinor());
        b.put("totalRepayableMinor", o.getTotalRepayableMinor());
        return write(b);
    }

    private String loanJson(Loan l) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("loanId", l.getId().toString());
        b.put("principalMinor", l.getPrincipalMinor());
        b.put("totalRepayableMinor", l.getTotalRepayableMinor());
        b.put("disbursedEntryId", l.getDisbursedEntryId().toString());
        return write(b);
    }

    private String repaymentJson(Loan l, long sweep, UUID entryId) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("loanId", l.getId().toString());
        b.put("sweptMinor", sweep);
        b.put("outstandingMinor", l.getOutstandingMinor());
        b.put("status", l.getStatus().name());
        b.put("ledgerEntryId", entryId.toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise lending event", e);
        }
    }
}
