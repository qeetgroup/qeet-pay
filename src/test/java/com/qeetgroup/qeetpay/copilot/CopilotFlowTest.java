package com.qeetgroup.qeetpay.copilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.ai.AiDecision;
import com.qeetgroup.qeetpay.ai.AiDecisionRepository;
import com.qeetgroup.qeetpay.analytics.AnalyticsIngestor;
import com.qeetgroup.qeetpay.analytics.PaymentAnalyticsEvent;
import com.qeetgroup.qeetpay.analytics.SubscriptionAnalyticsEvent;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import com.qeetgroup.qeetpay.reconciliation.SettlementReport;
import com.qeetgroup.qeetpay.reconciliation.SettlementService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * LLM copilots flow (PRD Module 12.5 / N7 / 17), exercised through the offline sandbox AI model
 * stand-in. Covers: an ask returns an answer + cited figures and persists the conversation (USER +
 * ASSISTANT turns) with the AI-gateway audit link; the answer falls back to the deterministic summary
 * when the model client errors; the reconciliation copilot explains breaks + leakage from the
 * reconciliation reads; and NLQ resolves a metric via the deterministic intent-matcher.
 */
class CopilotFlowTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired AnalyticsIngestor ingestor;
    @Autowired LedgerService ledger;
    @Autowired SettlementService settlements;
    @Autowired TreasuryCopilot treasury;
    @Autowired ReconciliationCopilot reconciliation;
    @Autowired NlqService nlq;
    @Autowired CopilotService copilot;
    @Autowired AiDecisionRepository aiDecisions;

    @Test
    void treasuryAskReturnsAnswerCitedFiguresAndPersistsConversation() {
        UUID merchantId = newMerchant();
        seedSettlementBalance(merchantId, 500_000L);
        ingestor.recordPayment(merchantId, UUID.randomUUID(), PaymentAnalyticsEvent.CAPTURED, 200_000L, "UPI");
        ingestor.recordSubscriptionEvent(merchantId, UUID.randomUUID(), SubscriptionAnalyticsEvent.NEW, 100_000L);

        CopilotAnswer answer = treasury.ask(merchantId, null, "How is my cash flow and working capital?");

        // Answer + cited figures + confidence, produced through the gateway (sandbox stand-in).
        assertThat(answer.answer()).isNotBlank();
        assertThat(answer.surface()).isEqualTo("TREASURY");
        assertThat(answer.figures()).isNotEmpty();
        assertThat(answer.citations()).isNotEmpty();
        assertThat(answer.aiDecisionId()).isNotNull();
        assertThat(answer.sandbox()).isTrue();
        assertThat(answer.requiresHumanReview()).isFalse();
        assertThat(answer.fellBack()).isFalse(); // sandbox is confident; advisory feature
        assertThat(figure(answer, "settlement_balance")).isEqualTo(500_000L);
        assertThat(figure(answer, "mrr")).isEqualTo(100_000L);

        // Conversation persisted: USER question + ASSISTANT answer, with the audit link.
        CopilotService.Transcript transcript = copilot.transcript(merchantId, answer.conversationId());
        assertThat(transcript.messages()).hasSize(2);
        assertThat(transcript.messages().get(0).getRole()).isEqualTo(CopilotRole.USER);
        CopilotMessage assistant = transcript.messages().get(1);
        assertThat(assistant.getRole()).isEqualTo(CopilotRole.ASSISTANT);
        assertThat(assistant.getContent()).isEqualTo(answer.answer());
        assertThat(assistant.getFiguresJson()).contains("settlement_balance");
        assertThat(assistant.getAiDecisionId()).isEqualTo(answer.aiDecisionId());

        // Every AI call is in the gateway's decision audit.
        assertThat(aiDecisions.findByMerchantIdOrderByCreatedAtDesc(merchantId))
                .extracting(AiDecision::getFeature)
                .contains("treasury.copilot");
    }

    @Test
    void deterministicFallbackWhenModelErrors() {
        UUID merchantId = newMerchant();
        seedSettlementBalance(merchantId, 250_000L);

        // "fail_" makes the sandbox model client throw → fail-closed to the deterministic summary.
        CopilotAnswer answer = treasury.ask(merchantId, null, "fail_ what is my runway?");

        assertThat(answer.fellBack()).isTrue();
        assertThat(answer.answer()).isNotBlank().contains("₹"); // still cites the figures in the narrative
        assertThat(answer.figures()).isNotEmpty();
        assertThat(figure(answer, "settlement_balance")).isEqualTo(250_000L);
    }

    @Test
    void reconciliationCopilotExplainsBreaksAndLeakage() {
        UUID merchantId = newMerchant();

        // A settlement whose only line references a payment that was never captured →
        // MISSING_IN_LEDGER (+ NODAL_IMBALANCE from the resulting negative holding balance).
        SettlementReport report =
                new SettlementReport(
                        "razorpay",
                        "setl_" + UUID.randomUUID(),
                        "INR",
                        Instant.now(),
                        null,
                        List.of(new SettlementReport.Line(UUID.randomUUID(), "pay_unmatched", 100_000L, 2_000L, 360L)));
        settlements.ingest(merchantId, report);

        CopilotAnswer answer =
                reconciliation.ask(merchantId, null, "Why are my settlements not matching? Any leakage?");

        assertThat(answer.surface()).isEqualTo("RECONCILIATION");
        assertThat(answer.answer()).containsIgnoringCase("leakage");
        assertThat(figure(answer, "total_settlements")).isEqualTo(1L);
        assertThat(figure(answer, "total_discrepancies")).isGreaterThanOrEqualTo(1L);
        assertThat(figure(answer, "estimated_leakage")).isEqualTo(100_000L);
        assertThat(answer.citations()).contains("reconciliation.discrepancies");

        // Persisted for audit.
        assertThat(copilot.transcript(merchantId, answer.conversationId()).messages()).hasSize(2);
    }

    @Test
    void nlqIntentMatcherResolvesMetric() {
        UUID merchantId = newMerchant();
        ingestor.recordSubscriptionEvent(merchantId, UUID.randomUUID(), SubscriptionAnalyticsEvent.NEW, 100_000L);

        CopilotAnswer answer = nlq.ask(merchantId, null, "What is my MRR right now?");

        assertThat(answer.surface()).isEqualTo("QUERY");
        assertThat(answer.answer()).containsIgnoringCase("MRR");
        assertThat(figure(answer, "mrr")).isEqualTo(100_000L);
        assertThat(answer.aiDecisionId()).isNotNull();
    }

    @Test
    void continuesAnExistingConversation() {
        UUID merchantId = newMerchant();
        ingestor.recordSubscriptionEvent(merchantId, UUID.randomUUID(), SubscriptionAnalyticsEvent.NEW, 100_000L);

        CopilotAnswer first = nlq.ask(merchantId, null, "What is my MRR?");
        CopilotAnswer second = nlq.ask(merchantId, first.conversationId(), "And my TPV?");

        assertThat(second.conversationId()).isEqualTo(first.conversationId());
        assertThat(copilot.transcript(merchantId, first.conversationId()).messages()).hasSize(4);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static long figure(CopilotAnswer answer, String key) {
        return answer.figures().stream()
                .filter(f -> f.key().equals(key))
                .map(f -> ((Number) f.value()).longValue())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no figure " + key));
    }

    private UUID newMerchant() {
        return merchants
                .create("copilot-" + UUID.randomUUID().toString().substring(0, 8), "Copilot Co")
                .merchant()
                .getId();
    }

    private void seedSettlementBalance(UUID merchantId, long amountMinor) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        ledger.postEntry(
                merchantId,
                "seed",
                "INR",
                List.of(
                        new LedgerLineInput(settlement, Direction.DEBIT, amountMinor),
                        new LedgerLineInput(revenue, Direction.CREDIT, amountMinor)));
    }
}
