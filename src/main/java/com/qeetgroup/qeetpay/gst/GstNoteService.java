package com.qeetgroup.qeetpay.gst;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GST Credit/Debit Note service (TAD Module 08).
 * <p>
 * Credit note: reduces tax payable (debit tax_payable / credit settlement — money returned to customer).
 * Debit note: increases tax payable (debit settlement / credit revenue + tax_payable — additional charge).
 * All corrections must reference a PAID or ISSUED original invoice.
 */
@Service
public class GstNoteService {

    private final GstNoteRepository notes;
    private final GstInvoiceRepository invoices;
    private final MerchantGstinRepository gstins;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public GstNoteService(
            GstNoteRepository notes,
            GstInvoiceRepository invoices,
            MerchantGstinRepository gstins,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.notes = notes;
        this.invoices = invoices;
        this.gstins = gstins;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GstNote issueCreditNote(UUID merchantId, UUID originalInvoiceId, String reason,
            long taxableMinor, long cgstMinor, long sgstMinor, long igstMinor) {
        merchantScope.apply(merchantId);
        loadInvoice(merchantId, originalInvoiceId);
        GstNote note = notes.save(new GstNote(merchantId, GstNote.CREDIT_NOTE,
                originalInvoiceId, reason, taxableMinor, cgstMinor, sgstMinor, igstMinor));
        outbox.enqueue(merchantId, "gst.credit_note.issued", json("noteId", note.getId()));
        return note;
    }

    @Transactional
    public GstNote issueDebitNote(UUID merchantId, UUID originalInvoiceId, String reason,
            long taxableMinor, long cgstMinor, long sgstMinor, long igstMinor) {
        merchantScope.apply(merchantId);
        loadInvoice(merchantId, originalInvoiceId);
        GstNote note = notes.save(new GstNote(merchantId, GstNote.DEBIT_NOTE,
                originalInvoiceId, reason, taxableMinor, cgstMinor, sgstMinor, igstMinor));
        outbox.enqueue(merchantId, "gst.debit_note.issued", json("noteId", note.getId()));
        return note;
    }

    /**
     * Applies a note: posts an offsetting ledger entry and marks the note APPLIED.
     * Credit note → refunds by debiting tax_payable + revenue / crediting settlement.
     * Debit note  → additional charge by debiting settlement / crediting revenue + tax_payable.
     */
    @Transactional
    public GstNote applyNote(UUID merchantId, UUID noteId) {
        merchantScope.apply(merchantId);
        GstNote note = loadNote(merchantId, noteId);

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue    = ledger.accountByCode(merchantId, "revenue").getId();
        UUID taxPayable = ledger.accountByCode(merchantId, "tax_payable").getId();

        long taxTotal = note.getCgstMinor() + note.getSgstMinor() + note.getIgstMinor();
        UUID entryId;

        if (GstNote.CREDIT_NOTE.equals(note.getType())) {
            // Return money to customer: debit revenue (+ tax_payable if any), credit settlement
            var lines = new java.util.ArrayList<LedgerLineInput>();
            lines.add(new LedgerLineInput(revenue,    Direction.DEBIT,  note.getTaxableMinor()));
            if (taxTotal > 0) lines.add(new LedgerLineInput(taxPayable, Direction.DEBIT, taxTotal));
            lines.add(new LedgerLineInput(settlement, Direction.CREDIT, note.getTotalMinor()));
            entryId = ledger.postEntry(merchantId, "credit note " + noteId, "INR", lines);
        } else {
            // Additional charge: debit settlement, credit revenue (+ tax_payable if any)
            var lines = new java.util.ArrayList<LedgerLineInput>();
            lines.add(new LedgerLineInput(settlement, Direction.DEBIT,  note.getTotalMinor()));
            lines.add(new LedgerLineInput(revenue,    Direction.CREDIT, note.getTaxableMinor()));
            if (taxTotal > 0) lines.add(new LedgerLineInput(taxPayable, Direction.CREDIT, taxTotal));
            entryId = ledger.postEntry(merchantId, "debit note " + noteId, "INR", lines);
        }

        note.apply(entryId);
        notes.save(note);
        outbox.enqueue(merchantId, "gst." + note.getType().toLowerCase() + ".applied", json("noteId", noteId));
        return note;
    }

    @Transactional
    public GstNote cancelNote(UUID merchantId, UUID noteId) {
        merchantScope.apply(merchantId);
        GstNote note = loadNote(merchantId, noteId);
        note.cancel();
        notes.save(note);
        return note;
    }

    @Transactional(readOnly = true)
    public List<GstNote> listNotes(UUID merchantId) {
        merchantScope.apply(merchantId);
        return notes.findByMerchantIdOrderByIssuedAtDesc(merchantId);
    }

    // ── Multi-GSTIN ──────────────────────────────────────────────────────────

    @Transactional
    public MerchantGstin registerGstin(UUID merchantId, String gstin, String legalName, String stateCode, boolean isDefault) {
        merchantScope.apply(merchantId);
        if (gstins.existsByMerchantIdAndGstin(merchantId, gstin)) {
            throw new IllegalArgumentException("GSTIN already registered: " + gstin);
        }
        return gstins.save(new MerchantGstin(merchantId, gstin, legalName, stateCode, isDefault));
    }

    @Transactional(readOnly = true)
    public List<MerchantGstin> listGstins(UUID merchantId) {
        merchantScope.apply(merchantId);
        return gstins.findByMerchantId(merchantId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private GstInvoice loadInvoice(UUID merchantId, UUID invoiceId) {
        return invoices.findById(invoiceId)
                .filter(i -> i.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new GstInvoiceNotFoundException("no gst invoice " + invoiceId));
    }

    private GstNote loadNote(UUID merchantId, UUID noteId) {
        return notes.findById(noteId)
                .filter(n -> n.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new GstInvoiceNotFoundException("no gst note " + noteId));
    }

    private String json(String key, UUID value) {
        try {
            return objectMapper.writeValueAsString(Map.of(key, value.toString()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("gst note event serialisation failed", e);
        }
    }
}
