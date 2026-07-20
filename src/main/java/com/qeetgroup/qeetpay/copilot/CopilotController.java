package com.qeetgroup.qeetpay.copilot;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM copilots API (PRD Module 12.5 / N7 / 17). Three conversational surfaces answering over the
 * merchant's own data — every answer cites the figures it used, routes through the AI gateway (PII
 * masked, audited, deterministic fallback), and is persisted as a conversation for audit.
 */
@Tag(
        name = "Copilot",
        description =
                "LLM copilots — treasury & cash-flow, reconciliation, and natural-language query over the merchant's own"
                        + " data. Every answer cites its figures, routes through the AI gateway, and is persisted for audit.")
@RestController
@RequestMapping("/v1/copilot")
public class CopilotController {

    private final TreasuryCopilot treasury;
    private final ReconciliationCopilot reconciliation;
    private final NlqService nlq;
    private final CopilotService copilot;

    public CopilotController(
            TreasuryCopilot treasury,
            ReconciliationCopilot reconciliation,
            NlqService nlq,
            CopilotService copilot) {
        this.treasury = treasury;
        this.reconciliation = reconciliation;
        this.nlq = nlq;
        this.copilot = copilot;
    }

    /** Treasury &amp; Cash-Flow Copilot (PRD Module 12.5). */
    @PostMapping("/treasury/ask")
    public CopilotAnswer treasuryAsk(@Valid @RequestBody AskRequest req) {
        return treasury.ask(MerchantContext.require(), req.conversationId(), req.question());
    }

    /** Reconciliation Copilot (PRD N7). */
    @PostMapping("/reconciliation/ask")
    public CopilotAnswer reconciliationAsk(@Valid @RequestBody AskRequest req) {
        return reconciliation.ask(MerchantContext.require(), req.conversationId(), req.question());
    }

    /** Natural-Language Query (PRD Module 17). */
    @PostMapping("/query")
    public CopilotAnswer query(@Valid @RequestBody AskRequest req) {
        return nlq.ask(MerchantContext.require(), req.conversationId(), req.question());
    }

    /** Lists the active merchant's copilot conversations (most-recently-updated first). */
    @GetMapping("/conversations")
    public List<ConversationSummary> conversations() {
        return copilot.listConversations(MerchantContext.require()).stream()
                .map(ConversationSummary::of)
                .toList();
    }

    /** The full transcript of one conversation (its ordered turns). */
    @GetMapping("/conversations/{conversationId}")
    public ConversationView conversation(@PathVariable UUID conversationId) {
        return ConversationView.of(copilot.transcript(MerchantContext.require(), conversationId));
    }

    // ── Request / response DTOs ────────────────────────────────────────────────

    /**
     * An ask on any surface.
     *
     * @param conversationId an existing thread to continue, or {@code null} to start a new one
     * @param question the plain-English question (required)
     */
    public record AskRequest(UUID conversationId, @NotBlank String question) {}

    public record ConversationSummary(
            String id, String surface, String title, Instant createdAt, Instant updatedAt) {
        static ConversationSummary of(CopilotConversation c) {
            return new ConversationSummary(
                    c.getId().toString(), c.getSurface().name(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record ConversationView(
            String id, String surface, String title, List<MessageView> messages) {
        static ConversationView of(CopilotService.Transcript t) {
            return new ConversationView(
                    t.conversation().getId().toString(),
                    t.conversation().getSurface().name(),
                    t.conversation().getTitle(),
                    t.messages().stream().map(MessageView::of).toList());
        }
    }

    public record MessageView(
            String id,
            String role,
            String content,
            String figuresJson,
            Double confidence,
            Boolean fellBack,
            String aiDecisionId,
            Instant createdAt) {
        static MessageView of(CopilotMessage m) {
            return new MessageView(
                    m.getId().toString(),
                    m.getRole().name(),
                    m.getContent(),
                    m.getFiguresJson(),
                    m.getConfidence(),
                    m.getFellBack(),
                    m.getAiDecisionId() == null ? null : m.getAiDecisionId().toString(),
                    m.getCreatedAt());
        }
    }
}
