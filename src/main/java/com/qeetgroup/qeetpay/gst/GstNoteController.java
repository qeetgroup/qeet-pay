package com.qeetgroup.qeetpay.gst;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
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

/** GST Credit/Debit Notes + Multi-GSTIN API (TAD Module 08). */
@RestController
@RequestMapping("/v1/gst")
public class GstNoteController {

    private final GstNoteService noteService;

    public GstNoteController(GstNoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping("/invoices/{invoiceId}/credit-note")
    public ResponseEntity<NoteView> creditNote(
            @PathVariable UUID invoiceId, @Valid @RequestBody NoteRequest req) {
        GstNote note = noteService.issueCreditNote(
                MerchantContext.require(), invoiceId, req.reason(),
                req.taxableMinor(), req.cgstMinor(), req.sgstMinor(), req.igstMinor());
        return ResponseEntity.status(HttpStatus.CREATED).body(NoteView.of(note));
    }

    @PostMapping("/invoices/{invoiceId}/debit-note")
    public ResponseEntity<NoteView> debitNote(
            @PathVariable UUID invoiceId, @Valid @RequestBody NoteRequest req) {
        GstNote note = noteService.issueDebitNote(
                MerchantContext.require(), invoiceId, req.reason(),
                req.taxableMinor(), req.cgstMinor(), req.sgstMinor(), req.igstMinor());
        return ResponseEntity.status(HttpStatus.CREATED).body(NoteView.of(note));
    }

    @PostMapping("/notes/{noteId}/apply")
    public ResponseEntity<NoteView> applyNote(@PathVariable UUID noteId) {
        return ResponseEntity.ok(NoteView.of(noteService.applyNote(MerchantContext.require(), noteId)));
    }

    @PostMapping("/notes/{noteId}/cancel")
    public ResponseEntity<NoteView> cancelNote(@PathVariable UUID noteId) {
        return ResponseEntity.ok(NoteView.of(noteService.cancelNote(MerchantContext.require(), noteId)));
    }

    @GetMapping("/notes")
    public List<NoteView> listNotes() {
        return noteService.listNotes(MerchantContext.require()).stream().map(NoteView::of).toList();
    }

    @PostMapping("/gstins")
    public ResponseEntity<GstinView> registerGstin(@Valid @RequestBody RegisterGstinRequest req) {
        MerchantGstin mg = noteService.registerGstin(
                MerchantContext.require(), req.gstin(), req.legalName(), req.stateCode(), req.isDefault() != null && req.isDefault());
        return ResponseEntity.status(HttpStatus.CREATED).body(GstinView.of(mg));
    }

    @GetMapping("/gstins")
    public List<GstinView> listGstins() {
        return noteService.listGstins(MerchantContext.require()).stream().map(GstinView::of).toList();
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record NoteRequest(
            @NotBlank String reason,
            @Positive long taxableMinor,
            long cgstMinor, long sgstMinor, long igstMinor) {}

    public record RegisterGstinRequest(
            @NotBlank String gstin, @NotBlank String legalName,
            @NotBlank String stateCode, Boolean isDefault) {}

    public record NoteView(String id, String type, String originalInvoiceId, String reason,
            long taxableMinor, long totalMinor, String status, String ledgerEntryId, Instant issuedAt) {
        static NoteView of(GstNote n) {
            return new NoteView(n.getId().toString(), n.getType(), n.getOriginalInvoiceId().toString(),
                    n.getReason(), n.getTaxableMinor(), n.getTotalMinor(), n.getStatus(),
                    n.getLedgerEntryId() != null ? n.getLedgerEntryId().toString() : null, n.getIssuedAt());
        }
    }

    public record GstinView(String id, String gstin, String legalName, String stateCode, boolean isDefault) {
        static GstinView of(MerchantGstin g) {
            return new GstinView(g.getId().toString(), g.getGstin(), g.getLegalName(), g.getStateCode(), g.isDefault());
        }
    }
}
