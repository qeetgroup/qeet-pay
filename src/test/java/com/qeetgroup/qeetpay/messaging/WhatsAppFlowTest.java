package com.qeetgroup.qeetpay.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxEvent;
import com.qeetgroup.qeetpay.platform.outbox.OutboxRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * WhatsApp bot + Pay flow (PRD Module 09.2/09.3): a PAUSE command renders the right templated reply
 * (dispatched via the outbox→qeet-notify path) and emits a subscription-pause intent event; a
 * WhatsApp Pay confirm posts a balanced money-in ledger entry (debit settlement / credit revenue);
 * and inbound handling is idempotent on the provider message id.
 */
class WhatsAppFlowTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired WhatsAppService whatsapp;
    @Autowired LedgerService ledger;
    @Autowired OutboxRepository outbox;

    @Test
    void pauseCommandRepliesAndEmitsIntent() {
        UUID merchantId = newMerchant();
        String from = "+919876500001";

        WhatsAppService.InboundResult result =
                whatsapp.handleInbound(merchantId, from, "PAUSE", "wamid.pause.1");

        // New message processed, inbound persisted with the parsed command.
        assertThat(result.processed()).isTrue();
        assertThat(result.message().getParsedCommand()).isEqualTo("PAUSE");

        // A templated WhatsApp reply was rendered + queued for qeet-notify.
        MessageDispatch reply = result.reply();
        assertThat(reply).isNotNull();
        assertThat(reply.getChannel()).isEqualTo(MessageChannel.WHATSAPP);
        assertThat(reply.getStatus()).isEqualTo(DispatchStatus.QUEUED);
        assertThat(reply.getRecipient()).isEqualTo(from);
        assertThat(reply.getRenderedBody()).contains("pause your subscription");
        // The {{ref}} placeholder was substituted (falls back to the phone when no subscription).
        assertThat(reply.getRenderedBody()).contains(from).doesNotContain("{{");

        // Both the dispatch request and the subscription-pause intent are on the outbox.
        assertThat(subjectsFor(merchantId))
                .anyMatch(s -> s.endsWith(".events.messaging.subscription.pause.requested"))
                .anyMatch(s -> s.endsWith(".events.notify.dispatch.requested"));
    }

    @Test
    void whatsAppPayConfirmPostsBalancedMoneyIn() {
        UUID merchantId = newMerchant();

        WhatsAppPayCollection coll =
                whatsapp.createPayCollection(
                        merchantId, "+919876500002", "buyer@upi", 150_000L, "INR", "chai order", "order-9");
        assertThat(coll.getStatus()).isEqualTo(WhatsAppPayStatus.CREATED);

        WhatsAppPayCollection paid =
                whatsapp.confirmPayCollection(merchantId, coll.getId(), "utr-abc", true);
        assertThat(paid.getStatus()).isEqualTo(WhatsAppPayStatus.PAID);
        assertThat(paid.getLedgerEntryId()).isNotNull();

        // Balanced money-in: debit settlement / credit revenue, each 150_000 paise.
        assertThat(balance(merchantId, "settlement")).isEqualTo(150_000L);
        assertThat(balance(merchantId, "revenue")).isEqualTo(150_000L);

        // Confirming again is idempotent — no second ledger posting.
        WhatsAppPayCollection again =
                whatsapp.confirmPayCollection(merchantId, coll.getId(), "utr-abc", true);
        assertThat(again.getStatus()).isEqualTo(WhatsAppPayStatus.PAID);
        assertThat(balance(merchantId, "settlement")).isEqualTo(150_000L); // unchanged
    }

    @Test
    void inboundIsIdempotentOnMessageId() {
        UUID merchantId = newMerchant();
        String from = "+919876500003";

        WhatsAppService.InboundResult first =
                whatsapp.handleInbound(merchantId, from, "BALANCE", "wamid.dup.1");
        assertThat(first.processed()).isTrue();
        assertThat(first.reply()).isNotNull();

        // Replaying the same provider message id stores nothing new and sends no second reply.
        WhatsAppService.InboundResult replay =
                whatsapp.handleInbound(merchantId, from, "BALANCE", "wamid.dup.1");
        assertThat(replay.processed()).isFalse();
        assertThat(replay.reply()).isNull();
        assertThat(replay.message().getId()).isEqualTo(first.message().getId());

        assertThat(whatsapp.listInbound(merchantId)).hasSize(1);
    }

    private UUID newMerchant() {
        return merchants.create("wa-" + UUID.randomUUID().toString().substring(0, 8), "WhatsApp Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }

    private java.util.List<String> subjectsFor(UUID merchantId) {
        return outbox.findAll().stream()
                .filter(e -> e.getSubject().startsWith("pay." + merchantId + "."))
                .map(OutboxEvent::getSubject)
                .toList();
    }
}
