package com.qeetgroup.qeetpay.tds;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Renders a {@link TdsReturn} + its deductee/collectee detail rows into an NSDL FVU-style e-TDS/TCS
 * statement (PRD Module 06.4). Pure + deterministic — no Spring/DB — so it is unit-testable in
 * isolation like {@link TdsCalculator}.
 *
 * <p>The output is the caret ({@code ^})-delimited, one-record-per-line ASCII layout the NSDL FVU
 * consumes, with the standard record markers:
 *
 * <pre>
 *   FH — File Header    (statement type, FVU version, form, FY, quarter, creation date)
 *   BH — Batch Header   (TAN, form, challan count, deductee count, total tax)
 *   CD — Challan Detail (BSR code, challan serial, date, deposited amount)
 *   DH — Detail Header  (the deductee/collectee column names — see {@link #columnHeader})
 *   DD — Detail record  (one per transaction: PAN, section, amount, tax, date, rate)
 *   FT — File Trailer   (record count, total tax)
 * </pre>
 *
 * All rupee amounts are rendered in whole rupees to 2 decimals (paise / 100, HALF_UP), the NSDL
 * money convention.
 */
public final class TdsReturnExporter {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final String SEP = "^";
    private static final String FVU_VERSION = "FVU8.5";

    private TdsReturnExporter() {}

    /** The caret-joined deductee/collectee column header line for {@code form} (the {@code DH} record). */
    public static String columnHeader(TdsReturnForm form) {
        String party = form.isTcs() ? "Collectee" : "Deductee";
        String amount = form.isTcs() ? "AmountReceivedRs" : "AmountPaidRs";
        String tax = form.isTcs() ? "TaxCollectedRs" : "TaxDeductedRs";
        return String.join(
                SEP, "DH", "SrNo", party + "PAN", party + "Name", "Section", amount, tax,
                form.isTcs() ? "DateOfCollection" : "DateOfDeduction", "RatePct");
    }

    /** Renders the full FVU-style statement as a newline-separated string. */
    public static String export(TdsReturn ret, List<TdsReturnLine> lines) {
        TdsReturnForm form = ret.getForm();
        StringBuilder out = new StringBuilder(256 + lines.size() * 96);

        String created = LocalDate.ofInstant(
                        ret.getPreparedAt() != null ? ret.getPreparedAt() : ret.getCreatedAt(), INDIA)
                .toString();
        String tan = sandboxTan(ret.getMerchantId());
        String totalTaxRs = rupees(ret.getTotalTaxMinor());

        // FH — File Header
        line(out, "FH", "NSDL", "e-TDS/TCS", "RegularStatement", FVU_VERSION,
                form.code(), ret.getFy(), ret.getQuarter(), created);
        // BH — Batch Header
        line(out, "BH", "1", tan, form.code(), "1",
                Integer.toString(ret.getDeducteeCount()), totalTaxRs);
        // CD — Challan Detail (the single consolidated deposit challan)
        line(out, "CD", "1", nz(ret.getBsrCode()), nz(ret.getChallanNo()),
                ret.getChallanDate() == null ? "" : ret.getChallanDate().toString(),
                totalTaxRs, totalTaxRs);
        // DH — Detail Header (column names)
        out.append(columnHeader(form)).append('\n');
        // DD — one detail record per transaction
        int sr = 0;
        for (TdsReturnLine l : lines) {
            sr++;
            line(out, "DD", Integer.toString(sr), nz(l.getDeducteePan()), l.getDeducteeName(),
                    l.getSection(), rupees(l.getGrossMinor()), rupees(l.getTaxMinor()),
                    l.getDeductedOn().toString(), percent(l.getRateBps()));
        }
        // FT — File Trailer
        line(out, "FT", Integer.toString(lines.size()), totalTaxRs);
        return out.toString();
    }

    private static void line(StringBuilder out, String... fields) {
        out.append(String.join(SEP, fields)).append('\n');
    }

    /** paise → rupees, 2 decimals HALF_UP (e.g. 100_000 → "1000.00"). */
    private static String rupees(long minor) {
        return BigDecimal.valueOf(minor)
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** basis points → percent, 2 decimals (e.g. 1000 → "10.00"). */
    private static String percent(int rateBps) {
        return BigDecimal.valueOf(rateBps)
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /**
     * A stable, sandbox deductor TAN (Tax Deduction Account Number) in the real {@code AAAA00000A}
     * shape, derived from the merchant id (a live integration would carry the merchant's real TAN).
     */
    static String sandboxTan(UUID merchantId) {
        int h = Math.abs(merchantId.hashCode());
        int digits = h % 100_000;
        char last = (char) ('A' + (h % 26));
        return String.format("QPAY%05d%c", digits, last);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
