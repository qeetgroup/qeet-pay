package com.qeetgroup.qeetpay.messaging;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * WhatsApp API (PRD Module 09.2 "WhatsApp Pay" + 09.3 "WhatsApp subscription bot"). Receives inbound
 * WhatsApp Business/Meta-style messages (persisted idempotently on the provider message id, then run
 * through the command bot), and drives in-chat Pay collections (create → confirm callback that posts
 * money-in to the ledger). All endpoints are merchant-scoped (RLS); the confirm rail is sandboxed.
 */
@Tag(
        name = "Messaging — WhatsApp",
        description = "Inbound WhatsApp webhook + subscription command bot, and in-chat WhatsApp Pay collections (sandbox rail).")
@RestController
@RequestMapping("/v1/messaging/whatsapp")
public class WhatsAppController {

    private final WhatsAppService whatsapp;

    public WhatsAppController(WhatsAppService whatsapp) {
        this.whatsapp = whatsapp;
    }

    // ── Inbound webhook + bot ─────────────────────────────────────────────────

    @PostMapping("/inbound")
    public InboundView inbound(@Valid @RequestBody InboundRequest req) {
        WhatsAppService.InboundResult result =
                whatsapp.handleInbound(MerchantContext.require(), req.from(), req.text(), req.messageId());
        return InboundView.of(result);
    }

    @GetMapping("/inbound")
    public List<InboundMessageView> listInbound() {
        return whatsapp.listInbound(MerchantContext.require()).stream().map(InboundMessageView::of).toList();
    }

    // ── WhatsApp Pay ──────────────────────────────────────────────────────────

    @PostMapping("/pay")
    public ResponseEntity<PayCollectionView> createPay(@Valid @RequestBody PayRequest req) {
        WhatsAppPayCollection coll =
                whatsapp.createPayCollection(
                        MerchantContext.require(), req.payerPhone(), req.payerVpa(), req.amountMinor(),
                        req.currency(), req.description(), req.relatedRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(PayCollectionView.of(coll));
    }

    @GetMapping("/pay")
    public List<PayCollectionView> listPay() {
        return whatsapp.listPayCollections(MerchantContext.require()).stream()
                .map(PayCollectionView::of)
                .toList();
    }

    @PostMapping("/pay/{id}/confirm")
    public PayCollectionView confirmPay(@PathVariable UUID id, @RequestBody(required = false) ConfirmRequest req) {
        String providerRef = req == null ? null : req.providerRef();
        boolean success = req == null || req.success() == null || req.success();
        return PayCollectionView.of(
                whatsapp.confirmPayCollection(MerchantContext.require(), id, providerRef, success));
    }

    // ── Records ────────────────────────────────────────────────────────────────

    /** A WhatsApp Business/Meta-style inbound message (unwrapped to from / text / message id). */
    public record InboundRequest(@NotBlank String from, String text, @NotBlank String messageId) {}

    @Schema(name = "WhatsAppPayRequest")
    public record PayRequest(
            String payerPhone,
            String payerVpa,
            @NotNull @Positive Long amountMinor,
            String currency,
            String description,
            String relatedRef) {}

    public record ConfirmRequest(String providerRef, Boolean success) {}

    public record InboundView(String messageId, String from, String command, boolean processed, ReplyView reply) {
        static InboundView of(WhatsAppService.InboundResult r) {
            InboundMessage m = r.message();
            return new InboundView(
                    m.getProviderMessageId(), m.getWaFrom(), m.getParsedCommand(), r.processed(),
                    r.reply() == null ? null : ReplyView.of(r.reply()));
        }
    }

    public record ReplyView(String dispatchId, String channel, String recipient, String body, String status) {
        static ReplyView of(MessageDispatch d) {
            return new ReplyView(
                    d.getId().toString(), d.getChannel().name(), d.getRecipient(), d.getRenderedBody(),
                    d.getStatus().name());
        }
    }

    public record InboundMessageView(
            String id, String messageId, String from, String body, String command, Instant receivedAt) {
        static InboundMessageView of(InboundMessage m) {
            return new InboundMessageView(
                    m.getId().toString(), m.getProviderMessageId(), m.getWaFrom(), m.getBody(),
                    m.getParsedCommand(), m.getReceivedAt());
        }
    }

    public record PayCollectionView(
            String id, String payerPhone, String payerVpa, long amountMinor, String currency, String status,
            String description, String providerRef, String ledgerEntryId, String relatedRef,
            Instant createdAt, Instant confirmedAt) {
        static PayCollectionView of(WhatsAppPayCollection c) {
            return new PayCollectionView(
                    c.getId().toString(), c.getPayerPhone(), c.getPayerVpa(), c.getAmountMinor(),
                    c.getCurrency(), c.getStatus().name(), c.getDescription(), c.getProviderRef(),
                    c.getLedgerEntryId() == null ? null : c.getLedgerEntryId().toString(),
                    c.getRelatedRef(), c.getCreatedAt(), c.getConfirmedAt());
        }
    }
}
