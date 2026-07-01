package com.qeetgroup.qeetpay.gst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.gst.GstInvoiceService.InvoiceWithLines;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** GST Credit/Debit Notes and Multi-GSTIN integration tests. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class GstNoteTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired GstInvoiceService invoiceService;
    @Autowired GstNoteService noteService;

    @Test
    void issueCreditNoteAndApplyIt() {
        UUID merchantId = newMerchant();
        GstInvoice invoice = createTestInvoice(merchantId);

        // Issue a credit note (partial return)
        GstNote note = noteService.issueCreditNote(merchantId, invoice.getId(),
                "Damaged goods return", 5000L, 450L, 450L, 0L);

        assertThat(note.getType()).isEqualTo(GstNote.CREDIT_NOTE);
        assertThat(note.getStatus()).isEqualTo(GstNote.ISSUED);
        assertThat(note.getTotalMinor()).isEqualTo(5000L + 450L + 450L);

        // Apply it — should post ledger offsetting entry
        GstNote applied = noteService.applyNote(merchantId, note.getId());
        assertThat(applied.getStatus()).isEqualTo(GstNote.APPLIED);
        assertThat(applied.getLedgerEntryId()).isNotNull();
    }

    @Test
    void issueDebitNoteAndApplyIt() {
        UUID merchantId = newMerchant();
        GstInvoice invoice = createTestInvoice(merchantId);

        GstNote note = noteService.issueDebitNote(merchantId, invoice.getId(),
                "Price revision", 2000L, 180L, 180L, 0L);

        assertThat(note.getType()).isEqualTo(GstNote.DEBIT_NOTE);
        assertThat(note.getStatus()).isEqualTo(GstNote.ISSUED);

        GstNote applied = noteService.applyNote(merchantId, note.getId());
        assertThat(applied.getStatus()).isEqualTo(GstNote.APPLIED);
    }

    @Test
    void cancelCreditNote() {
        UUID merchantId = newMerchant();
        GstInvoice invoice = createTestInvoice(merchantId);
        GstNote note = noteService.issueCreditNote(merchantId, invoice.getId(),
                "Cancelled order", 1000L, 90L, 90L, 0L);

        GstNote cancelled = noteService.cancelNote(merchantId, note.getId());
        assertThat(cancelled.getStatus()).isEqualTo(GstNote.CANCELLED);
    }

    @Test
    void cannotCancelAppliedNote() {
        UUID merchantId = newMerchant();
        GstInvoice invoice = createTestInvoice(merchantId);
        GstNote note = noteService.issueCreditNote(merchantId, invoice.getId(), "r", 1000L, 0L, 0L, 0L);
        noteService.applyNote(merchantId, note.getId());

        assertThatThrownBy(() -> noteService.cancelNote(merchantId, note.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listNotesForMerchant() {
        UUID merchantId = newMerchant();
        GstInvoice invoice = createTestInvoice(merchantId);
        noteService.issueCreditNote(merchantId, invoice.getId(), "reason1", 1000L, 0L, 0L, 0L);
        noteService.issueDebitNote(merchantId, invoice.getId(), "reason2", 2000L, 0L, 0L, 0L);

        List<GstNote> list = noteService.listNotes(merchantId);
        assertThat(list).hasSize(2);
    }

    @Test
    void registerAndListMultipleGstins() {
        UUID merchantId = newMerchant();
        noteService.registerGstin(merchantId, "27ABCDE1234F1Z5", "Entity Maharashtra", "27", true);
        noteService.registerGstin(merchantId, "29ABCDE1234F1Z3", "Entity Karnataka", "29", false);

        List<MerchantGstin> gstins = noteService.listGstins(merchantId);
        assertThat(gstins).hasSize(2);
        assertThat(gstins.stream().anyMatch(g -> "27".equals(g.getStateCode()))).isTrue();
    }

    @Test
    void duplicateGstinRejected() {
        UUID merchantId = newMerchant();
        noteService.registerGstin(merchantId, "27ABCDE1234F1Z5", "Entity MH", "27", true);

        assertThatThrownBy(() ->
                noteService.registerGstin(merchantId, "27ABCDE1234F1Z5", "Duplicate", "27", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private GstInvoice createTestInvoice(UUID merchantId) {
        return invoiceService.createInvoice(merchantId, "27ABCDE1234F1Z5", null, "27", "INR",
                List.of(new GstLineInput("Test Service", "998314", 1L, 10000L, 18))).invoice();
    }

    private UUID newMerchant() {
        return merchants.create("gn-" + UUID.randomUUID().toString().substring(0, 8), "GST Note Co")
                .merchant().getId();
    }
}
