package com.qeetgroup.qeetpay.crossborder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound / import cross-border remittances (PRD Module 14.4). Pays a foreign vendor/SaaS/cloud in a
 * foreign currency via SWIFT: the foreign amount is converted to INR at the current rate from the
 * pluggable {@link FxRateAdapter}, the LRS financial-year running total is tracked per merchant, and
 * 2.5% TCS ({@link LrsTcsCalculator}) is collected on the slice of the FY total above the LRS
 * threshold. Creating a remittance debits the merchant's {@code settlement} balance for INR principal +
 * TCS, credits {@code bank} for the wired principal, and credits {@code tax_payable} for the TCS — one
 * balanced money-out entry. Marking it FAILED posts the exact offsetting entry (append-only
 * corrections). Every write is outbox-published and recorded as an append-only event.
 */
@Service
public class OutboundRemittanceService {

    private static final String INR = "INR";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final OutboundRemittanceRepository remittances;
    private final OutboundRemittanceEventRepository events;
    private final FxRateAdapter fx;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public OutboundRemittanceService(
            OutboundRemittanceRepository remittances,
            OutboundRemittanceEventRepository events,
            FxRateAdapter fx,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.remittances = remittances;
        this.events = events;
        this.fx = fx;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Quotes an outbound remittance without persisting: current FX rate, INR principal, the projected
     * TCS given the merchant's LRS financial-year running total, and the total INR that would be debited.
     */
    @Transactional(readOnly = true)
    public Quote quote(UUID merchantId, String currency, long foreignAmountMinor) {
        merchantScope.apply(merchantId);
        requireForeignCurrency(currency);
        if (foreignAmountMinor <= 0) {
            throw new IllegalArgumentException("foreign amount must be positive");
        }
        BigDecimal rate = fx.rate(currency, INR);
        long principalInr = CrossBorderService.toInrMinor(foreignAmountMinor, rate);
        String fy = currentFinancialYear();
        long priorCumulative = lrsCumulative(merchantId, fy);
        long tcs = LrsTcsCalculator.tcsMinor(priorCumulative, principalInr);
        return new Quote(
                currency, foreignAmountMinor, rate, principalInr, fy, priorCumulative,
                LrsTcsCalculator.LRS_THRESHOLD_INR_MINOR, tcs, LrsTcsCalculator.TCS_BPS,
                principalInr + tcs);
    }

    /**
     * Creates an outbound remittance: quotes it, posts the balanced money-out ledger entry (debit
     * {@code settlement} inrDebited / credit {@code bank} principal / credit {@code tax_payable} TCS),
     * and records the LRS running total. Status starts CREATED (SWIFT instruction issued).
     */
    @Transactional
    public OutboundRemittance create(
            UUID merchantId, String beneficiaryName, String beneficiarySwift, String beneficiaryAccount,
            String beneficiaryCountry, String currency, long foreignAmountMinor, String purposeCode) {
        merchantScope.apply(merchantId);
        requireText(beneficiaryName, "beneficiaryName");
        requireText(beneficiarySwift, "beneficiarySwift");
        requireText(beneficiaryAccount, "beneficiaryAccount");
        requireText(beneficiaryCountry, "beneficiaryCountry");
        requireText(purposeCode, "FEMA purpose code");
        requireForeignCurrency(currency);
        if (foreignAmountMinor <= 0) {
            throw new IllegalArgumentException("foreign amount must be positive");
        }

        BigDecimal rate = fx.rate(currency, INR);
        long principalInr = CrossBorderService.toInrMinor(foreignAmountMinor, rate);
        String fy = currentFinancialYear();
        long priorCumulative = lrsCumulative(merchantId, fy);
        long tcs = LrsTcsCalculator.tcsMinor(priorCumulative, principalInr);
        long inrDebited = principalInr + tcs;
        long cumulativeAfter = priorCumulative + principalInr;

        UUID entryId = postDebit(merchantId, principalInr, tcs, inrDebited);

        OutboundRemittance remittance =
                remittances.save(
                        new OutboundRemittance(
                                merchantId, beneficiaryName, beneficiarySwift, beneficiaryAccount,
                                beneficiaryCountry, purposeCode, currency, foreignAmountMinor, rate,
                                principalInr, tcs, inrDebited, fy, priorCumulative, cumulativeAfter,
                                entryId));
        recordEvent(merchantId, remittance.getId(), OutboundRemittanceEventType.CREATED, inrDebited, entryId,
                "SWIFT instruction to " + beneficiaryName);
        outbox.enqueue(merchantId, "crossborder.outbound.created", remittanceJson(remittance));
        return remittance;
    }

    /** Marks a CREATED remittance REMITTED once the SWIFT wire settles at the beneficiary. */
    @Transactional
    public OutboundRemittance markRemitted(UUID merchantId, UUID remittanceId, String remittanceReference) {
        merchantScope.apply(merchantId);
        OutboundRemittance remittance = load(merchantId, remittanceId);
        if (remittance.getStatus() != OutboundRemittanceStatus.CREATED) {
            throw new IllegalStateException("cannot mark remitted from status " + remittance.getStatus());
        }
        remittance.markRemitted(remittanceReference);
        remittances.save(remittance);
        recordEvent(merchantId, remittanceId, OutboundRemittanceEventType.REMITTED,
                remittance.getPrincipalInrMinor(), null, remittanceReference);
        outbox.enqueue(merchantId, "crossborder.outbound.remitted", remittanceJson(remittance));
        return remittance;
    }

    /**
     * Marks a CREATED remittance FAILED (wire rejected) and posts the exact offsetting ledger entry —
     * credit {@code settlement} / debit {@code bank} / debit {@code tax_payable} — returning the funds.
     */
    @Transactional
    public OutboundRemittance markFailed(UUID merchantId, UUID remittanceId, String reason) {
        merchantScope.apply(merchantId);
        OutboundRemittance remittance = load(merchantId, remittanceId);
        if (remittance.getStatus() != OutboundRemittanceStatus.CREATED) {
            throw new IllegalStateException("cannot fail remittance in status " + remittance.getStatus());
        }
        UUID reversal =
                postReversal(
                        merchantId, remittance.getPrincipalInrMinor(), remittance.getTcsMinor(),
                        remittance.getInrDebitedMinor(), remittanceId);
        remittance.markFailed(reason, reversal);
        remittances.save(remittance);
        recordEvent(merchantId, remittanceId, OutboundRemittanceEventType.FAILED,
                remittance.getInrDebitedMinor(), reversal, reason);
        outbox.enqueue(merchantId, "crossborder.outbound.failed", remittanceJson(remittance));
        return remittance;
    }

    @Transactional(readOnly = true)
    public List<OutboundRemittance> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return remittances.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public RemittanceWithEvents get(UUID merchantId, UUID remittanceId) {
        merchantScope.apply(merchantId);
        OutboundRemittance remittance = load(merchantId, remittanceId);
        return new RemittanceWithEvents(
                remittance, events.findByRemittanceIdOrderByCreatedAt(remittanceId));
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    /** Debit settlement (principal + TCS) / credit bank (principal) / credit tax_payable (TCS). */
    private UUID postDebit(UUID merchantId, long principalInr, long tcs, long inrDebited) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID bank = ledger.accountByCode(merchantId, "bank").getId();
        List<LedgerLineInput> lines = new ArrayList<>(3);
        lines.add(new LedgerLineInput(settlement, Direction.DEBIT, inrDebited));
        lines.add(new LedgerLineInput(bank, Direction.CREDIT, principalInr));
        if (tcs > 0) {
            UUID taxPayable = ledger.accountByCode(merchantId, "tax_payable").getId();
            lines.add(new LedgerLineInput(taxPayable, Direction.CREDIT, tcs));
        }
        return ledger.postEntry(merchantId, "outbound remittance", INR, lines);
    }

    /** The exact offsetting entry for a failed remittance (never mutates the original posting). */
    private UUID postReversal(UUID merchantId, long principalInr, long tcs, long inrDebited, UUID remittanceId) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID bank = ledger.accountByCode(merchantId, "bank").getId();
        List<LedgerLineInput> lines = new ArrayList<>(3);
        lines.add(new LedgerLineInput(settlement, Direction.CREDIT, inrDebited));
        lines.add(new LedgerLineInput(bank, Direction.DEBIT, principalInr));
        if (tcs > 0) {
            UUID taxPayable = ledger.accountByCode(merchantId, "tax_payable").getId();
            lines.add(new LedgerLineInput(taxPayable, Direction.DEBIT, tcs));
        }
        return ledger.postEntry(merchantId, "outbound remittance reversal " + remittanceId, INR, lines);
    }

    /** Sum of INR principal already remitted this FY (failed/reversed remittances excluded). */
    private long lrsCumulative(UUID merchantId, String financialYear) {
        long total = 0L;
        for (OutboundRemittance r : remittances.findByMerchantIdAndFinancialYear(merchantId, financialYear)) {
            if (r.getStatus() != OutboundRemittanceStatus.FAILED) {
                total += r.getPrincipalInrMinor();
            }
        }
        return total;
    }

    private String currentFinancialYear() {
        return LrsTcsCalculator.financialYearOf(LocalDate.now(INDIA));
    }

    private void recordEvent(
            UUID merchantId, UUID remittanceId, OutboundRemittanceEventType type, long amountMinor,
            UUID ledgerEntryId, String note) {
        events.save(new OutboundRemittanceEvent(remittanceId, merchantId, type, amountMinor, ledgerEntryId, note));
    }

    private OutboundRemittance load(UUID merchantId, UUID remittanceId) {
        return remittances
                .findById(remittanceId)
                .filter(r -> r.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new OutboundRemittanceNotFoundException("no outbound remittance " + remittanceId));
    }

    private static void requireForeignCurrency(String currency) {
        if (currency == null || currency.isBlank() || INR.equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("remittance currency must be a non-INR foreign currency");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    /** A non-persisted outbound quote (FX + projected LRS TCS). */
    public record Quote(
            String currency, long foreignAmountMinor, BigDecimal fxRate, long principalInrMinor,
            String financialYear, long lrsCumulativeBeforeMinor, long lrsThresholdMinor, long tcsMinor,
            int tcsBps, long inrDebitedMinor) {}

    /** An outbound remittance plus its append-only events. */
    public record RemittanceWithEvents(OutboundRemittance remittance, List<OutboundRemittanceEvent> events) {}

    private String remittanceJson(OutboundRemittance r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("remittanceId", r.getId().toString());
        b.put("beneficiaryName", r.getBeneficiaryName());
        b.put("currency", r.getCurrency());
        b.put("foreignAmountMinor", r.getForeignAmountMinor());
        b.put("fxRate", r.getFxRate().toPlainString());
        b.put("principalInrMinor", r.getPrincipalInrMinor());
        b.put("tcsMinor", r.getTcsMinor());
        b.put("inrDebitedMinor", r.getInrDebitedMinor());
        b.put("financialYear", r.getFinancialYear());
        b.put("lrsCumulativeAfterMinor", r.getLrsCumulativeAfterMinor());
        b.put("purposeCode", r.getPurposeCode());
        b.put("status", r.getStatus().name());
        b.put("ledgerEntryId", r.getLedgerEntryId().toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise outbound remittance event", e);
        }
    }
}
