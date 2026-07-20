package com.qeetgroup.qeetpay.accounting;

import com.qeetgroup.qeetpay.gst.GstInvoice;
import com.qeetgroup.qeetpay.gst.GstInvoiceService;
import com.qeetgroup.qeetpay.ledger.Account;
import com.qeetgroup.qeetpay.ledger.JournalEntry;
import com.qeetgroup.qeetpay.ledger.JournalLine;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Assembles an {@link ExportPayload} from the {@code ledger} and {@code gst} read APIs for a period.
 * Journal entries in {@code [from, to)} become vouchers (account ids resolved to their chart codes);
 * GST invoices issued in the same window come from {@link GstInvoiceService#findIssuedInPeriod}. Runs
 * inside the caller's transaction (RLS already applied by the service) — read-only throughout.
 */
@Component
public class AccountingExportBuilder {

    private final LedgerEntryReadRepository entries;
    private final LedgerLineReadRepository lines;
    private final LedgerService ledger;
    private final GstInvoiceService gstInvoices;

    public AccountingExportBuilder(
            LedgerEntryReadRepository entries,
            LedgerLineReadRepository lines,
            LedgerService ledger,
            GstInvoiceService gstInvoices) {
        this.entries = entries;
        this.lines = lines;
        this.ledger = ledger;
        this.gstInvoices = gstInvoices;
    }

    public ExportPayload build(UUID merchantId, Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("period requires periodStart < periodEnd");
        }

        Map<UUID, String> codeById = new HashMap<>();
        for (Account a : ledger.accountsOf(merchantId)) {
            codeById.put(a.getId(), a.getCode());
        }

        List<ExportPayload.JournalVoucher> vouchers = new ArrayList<>();
        for (JournalEntry entry :
                entries.findByMerchantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAt(
                        merchantId, from, to)) {
            List<ExportPayload.VoucherLine> voucherLines = new ArrayList<>();
            for (JournalLine line : lines.findByEntryId(entry.getId())) {
                String code = codeById.getOrDefault(line.getAccountId(), line.getAccountId().toString());
                voucherLines.add(new ExportPayload.VoucherLine(code, line.getDirection(), line.getAmountMinor()));
            }
            vouchers.add(new ExportPayload.JournalVoucher(entry.getId(), entry.getCreatedAt(), voucherLines));
        }

        List<ExportPayload.InvoiceExport> invoices = new ArrayList<>();
        for (GstInvoice inv : gstInvoices.findIssuedInPeriod(merchantId, from, to)) {
            invoices.add(
                    new ExportPayload.InvoiceExport(
                            inv.getInvoiceNumber(),
                            inv.getIssuedAt(),
                            inv.getBuyerGstin(),
                            inv.getPlaceOfSupply(),
                            inv.getCurrency(),
                            inv.getStatus().name(),
                            inv.getTaxableMinor(),
                            inv.getCgstMinor(),
                            inv.getSgstMinor(),
                            inv.getIgstMinor(),
                            inv.getTotalMinor()));
        }

        return new ExportPayload(merchantId, from, to, vouchers, invoices);
    }
}
