package com.qeetgroup.qeetpay.accounting;

import com.qeetgroup.qeetpay.ledger.Direction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Renders the export as a <b>Tally Prime import XML</b> ({@code ENVELOPE} → {@code IMPORTDATA} →
 * {@code TALLYMESSAGE}s). Ledger journal entries become {@code Journal} vouchers; GST invoices become
 * {@code Sales} vouchers with their tax ledgers. Following Tally's convention, a debit line is
 * {@code ISDEEMEDPOSITIVE=Yes} with a negative {@code AMOUNT}, a credit is {@code No} with positive.
 * Amounts are converted paise → rupees (2dp, HALF_UP). The XML is returned in the {@link SyncResult}
 * for download; there is no network call, so this connector never fails.
 */
@Component
public class TallyXmlConnector implements AccountingConnector {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TALLY_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(INDIA);

    @Override
    public AccountingTarget target() {
        return AccountingTarget.TALLY;
    }

    @Override
    public SyncResult push(ExportPayload payload, AccountingConnection connection) {
        String xml = render(payload);
        String externalRef = "tally-" + payload.recordCount() + "-vouchers";
        return SyncResult.ok(payload.recordCount(), externalRef, xml);
    }

    /** Builds the Tally Prime import XML document for the payload. */
    public String render(ExportPayload payload) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<ENVELOPE>\n");
        sb.append("  <HEADER>\n    <TALLYREQUEST>Import Data</TALLYREQUEST>\n  </HEADER>\n");
        sb.append("  <BODY>\n    <IMPORTDATA>\n");
        sb.append("      <REQUESTDESC>\n        <REPORTNAME>Vouchers</REPORTNAME>\n      </REQUESTDESC>\n");
        sb.append("      <REQUESTDATA>\n");

        for (ExportPayload.JournalVoucher v : payload.vouchers()) {
            appendJournalVoucher(sb, v);
        }
        for (ExportPayload.InvoiceExport inv : payload.invoices()) {
            appendSalesVoucher(sb, inv);
        }

        sb.append("      </REQUESTDATA>\n");
        sb.append("    </IMPORTDATA>\n  </BODY>\n");
        sb.append("</ENVELOPE>\n");
        return sb.toString();
    }

    private void appendJournalVoucher(StringBuilder sb, ExportPayload.JournalVoucher v) {
        sb.append("        <TALLYMESSAGE xmlns:UDF=\"TallyUDF\">\n");
        sb.append("          <VOUCHER VCHTYPE=\"Journal\" ACTION=\"Create\">\n");
        sb.append("            <DATE>").append(TALLY_DATE.format(v.date())).append("</DATE>\n");
        sb.append("            <VOUCHERTYPENAME>Journal</VOUCHERTYPENAME>\n");
        sb.append("            <NARRATION>").append(esc("Qeet Pay entry " + v.entryId())).append("</NARRATION>\n");
        for (ExportPayload.VoucherLine line : v.lines()) {
            boolean debit = line.direction() == Direction.DEBIT;
            appendLedgerEntry(sb, line.accountCode(), debit, line.amountMinor());
        }
        sb.append("          </VOUCHER>\n");
        sb.append("        </TALLYMESSAGE>\n");
    }

    private void appendSalesVoucher(StringBuilder sb, ExportPayload.InvoiceExport inv) {
        String party = (inv.buyerGstin() != null && !inv.buyerGstin().isBlank()) ? inv.buyerGstin() : "Sundry Debtors";
        sb.append("        <TALLYMESSAGE xmlns:UDF=\"TallyUDF\">\n");
        sb.append("          <VOUCHER VCHTYPE=\"Sales\" ACTION=\"Create\">\n");
        sb.append("            <DATE>").append(TALLY_DATE.format(inv.issuedAt())).append("</DATE>\n");
        sb.append("            <VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>\n");
        sb.append("            <VOUCHERNUMBER>").append(esc(inv.invoiceNumber())).append("</VOUCHERNUMBER>\n");
        sb.append("            <PARTYLEDGERNAME>").append(esc(party)).append("</PARTYLEDGERNAME>\n");
        // Party is debited by the invoice total; sales + output-tax ledgers are credited.
        appendLedgerEntry(sb, party, true, inv.totalMinor());
        appendLedgerEntry(sb, "Sales", false, inv.taxableMinor());
        if (inv.cgstMinor() > 0) appendLedgerEntry(sb, "Output CGST", false, inv.cgstMinor());
        if (inv.sgstMinor() > 0) appendLedgerEntry(sb, "Output SGST", false, inv.sgstMinor());
        if (inv.igstMinor() > 0) appendLedgerEntry(sb, "Output IGST", false, inv.igstMinor());
        sb.append("          </VOUCHER>\n");
        sb.append("        </TALLYMESSAGE>\n");
    }

    private void appendLedgerEntry(StringBuilder sb, String ledgerName, boolean debit, long amountMinor) {
        sb.append("            <ALLLEDGERENTRIES.LIST>\n");
        sb.append("              <LEDGERNAME>").append(esc(ledgerName)).append("</LEDGERNAME>\n");
        sb.append("              <ISDEEMEDPOSITIVE>").append(debit ? "Yes" : "No").append("</ISDEEMEDPOSITIVE>\n");
        sb.append("              <AMOUNT>").append(debit ? "-" : "").append(rupees(amountMinor)).append("</AMOUNT>\n");
        sb.append("            </ALLLEDGERENTRIES.LIST>\n");
    }

    /** paise → rupees, 2dp HALF_UP, plain string (no grouping, no sign). */
    static String rupees(long minor) {
        return BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
