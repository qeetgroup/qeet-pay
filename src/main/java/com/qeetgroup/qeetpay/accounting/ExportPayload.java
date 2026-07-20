package com.qeetgroup.qeetpay.accounting;

import com.qeetgroup.qeetpay.ledger.Direction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The bookkeeping slice for one export: the ledger's journal vouchers plus the GST invoices issued
 * in the window. Amounts stay in integer minor units (paise); connectors convert to major units at
 * their own boundary.
 */
public record ExportPayload(
        UUID merchantId,
        Instant periodStart,
        Instant periodEnd,
        List<JournalVoucher> vouchers,
        List<InvoiceExport> invoices) {

    /** Total records exported = journal vouchers + GST invoices. */
    public int recordCount() {
        return vouchers.size() + invoices.size();
    }

    /** One balanced ledger entry with its debit/credit lines. */
    public record JournalVoucher(UUID entryId, Instant date, List<VoucherLine> lines) {}

    /** A single debit or credit against a named ledger account, in minor units. */
    public record VoucherLine(String accountCode, Direction direction, long amountMinor) {}

    /** A GST invoice header flattened for export (tax split retained). */
    public record InvoiceExport(
            String invoiceNumber,
            Instant issuedAt,
            String buyerGstin,
            String placeOfSupply,
            String currency,
            String status,
            long taxableMinor,
            long cgstMinor,
            long sgstMinor,
            long igstMinor,
            long totalMinor) {}
}
