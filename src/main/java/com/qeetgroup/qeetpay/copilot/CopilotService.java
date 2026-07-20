package com.qeetgroup.qeetpay.copilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ai.AiDecisionResult;
import com.qeetgroup.qeetpay.ai.AiGateway;
import com.qeetgroup.qeetpay.ai.AiRequest;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared copilot core (PRD Module 12.5 / N7 / 17): the one place a copilot surface routes an answer
 * through the {@link AiGateway} and persists the conversation trail. A surface ({@link TreasuryCopilot},
 * {@link ReconciliationCopilot}, {@link NlqService}) gathers the figures deterministically and builds a
 * deterministic summary, then hands both to {@link #respond} which:
 *
 * <ol>
 *   <li>opens (or continues) the merchant-scoped conversation and appends the {@code USER} turn;
 *   <li>routes the question through the gateway ({@link AiRequest#advisory} — PII masked, audited to
 *       {@code ai.ai_decision}), supplying the deterministic summary as the fail-closed fallback;
 *   <li>surfaces the answer: the model's when it returns a usable {@code answer}, else the deterministic
 *       summary (the offline sandbox stand-in has no usable answer, so the deterministic path answers);
 *   <li>persists the {@code ASSISTANT} turn with the cited figures + gateway outcome and returns it.
 * </ol>
 *
 * <p>The cited figures are computed by the surface (never by the model), so a citation is always present
 * whichever path the gateway took.
 */
@Service
public class CopilotService {

    private static final int TITLE_MAX = 80;

    private final CopilotConversationRepository conversations;
    private final CopilotMessageRepository messages;
    private final AiGateway aiGateway;
    private final MerchantScope merchantScope;
    private final ObjectMapper objectMapper;

    public CopilotService(
            CopilotConversationRepository conversations,
            CopilotMessageRepository messages,
            AiGateway aiGateway,
            MerchantScope merchantScope,
            ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.messages = messages;
        this.aiGateway = aiGateway;
        this.merchantScope = merchantScope;
        this.objectMapper = objectMapper;
    }

    /**
     * Answers one question on a surface: routes through the gateway, persists both turns, returns the
     * answer with its cited figures and the gateway outcome.
     *
     * @param conversationId an existing thread to continue, or {@code null} to start a new one
     * @param feature the {@code AiFeature} key for this surface (advisory / non-money-affecting)
     * @param figures the cited underlying figures (computed deterministically by the surface)
     * @param citations the data sources read to compute {@code figures}
     * @param deterministicAnswer the deterministic summary — the fail-closed fallback and the answer
     *     surfaced whenever the model cannot produce a usable one (e.g. the offline sandbox stand-in)
     */
    @Transactional
    public CopilotAnswer respond(
            UUID merchantId,
            UUID conversationId,
            CopilotSurface surface,
            String question,
            String feature,
            List<Figure> figures,
            List<String> citations,
            String deterministicAnswer) {
        merchantScope.apply(merchantId);
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question is required");
        }

        CopilotConversation conversation = conversation(merchantId, conversationId, surface, question);
        messages.save(CopilotMessage.user(conversation.getId(), merchantId, question));

        // Route through the AI gateway: it masks PII in the input, enforces the §6.4 safety matrix,
        // audits the decision, and falls back to our deterministic summary when the model can't be used.
        String fallbackJson = jsonAnswer(deterministicAnswer);
        AiDecisionResult decision =
                aiGateway.evaluate(
                        AiRequest.advisory(merchantId, feature, buildInput(question, figures), Set.of()),
                        () -> fallbackJson);

        // Prefer a usable model answer; otherwise the deterministic summary (sandbox stand-in path).
        String answer =
                extractAnswer(decision.outputJson())
                        .filter(s -> !s.isBlank())
                        .orElse(deterministicAnswer);

        String figuresJson = jsonFigures(figures);
        CopilotMessage assistant =
                messages.save(
                        CopilotMessage.assistant(
                                conversation.getId(),
                                merchantId,
                                answer,
                                figuresJson,
                                decision.confidence(),
                                decision.fellBack(),
                                decision.decisionId()));
        conversation.touch();
        conversations.save(conversation);

        AiGateway.GatewayHealth health = aiGateway.health();
        return new CopilotAnswer(
                conversation.getId(),
                assistant.getId(),
                surface.name(),
                question,
                answer,
                figures,
                citations,
                decision.confidence(),
                decision.fellBack(),
                decision.requiresHumanReview(),
                decision.decisionId(),
                health.model(),
                health.sandbox());
    }

    // ── Reads (audit / console) ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CopilotConversation> listConversations(UUID merchantId) {
        merchantScope.apply(merchantId);
        return conversations.findByMerchantIdOrderByUpdatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public Transcript transcript(UUID merchantId, UUID conversationId) {
        merchantScope.apply(merchantId);
        CopilotConversation conversation =
                conversations
                        .findById(conversationId)
                        .filter(c -> c.getMerchantId().equals(merchantId))
                        .orElseThrow(
                                () -> new CopilotConversationNotFoundException("no conversation " + conversationId));
        return new Transcript(conversation, messages.findByConversationIdOrderByCreatedAtAsc(conversationId));
    }

    /** A conversation plus its ordered turns. */
    public record Transcript(CopilotConversation conversation, List<CopilotMessage> messages) {}

    // ── Internals ──────────────────────────────────────────────────────────────

    private CopilotConversation conversation(
            UUID merchantId, UUID conversationId, CopilotSurface surface, String question) {
        if (conversationId == null) {
            return conversations.save(new CopilotConversation(merchantId, surface, title(question)));
        }
        CopilotConversation existing =
                conversations
                        .findById(conversationId)
                        .filter(c -> c.getMerchantId().equals(merchantId))
                        .orElseThrow(
                                () -> new CopilotConversationNotFoundException("no conversation " + conversationId));
        if (existing.getSurface() != surface) {
            throw new IllegalArgumentException("conversation belongs to a different copilot surface");
        }
        return existing;
    }

    private static String title(String question) {
        String trimmed = question.strip();
        return trimmed.length() <= TITLE_MAX ? trimmed : trimmed.substring(0, TITLE_MAX - 1) + "…";
    }

    /** The prompt handed to the gateway (which masks PII): the question plus the figures as context. */
    private static String buildInput(String question, List<Figure> figures) {
        StringBuilder sb = new StringBuilder("Question: ").append(question).append("\n\nMerchant figures:\n");
        for (Figure f : figures) {
            sb.append("- ").append(f.label()).append(": ").append(f.display()).append('\n');
        }
        return sb.toString();
    }

    private Optional<String> extractAnswer(String outputJson) {
        if (outputJson == null || outputJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(outputJson);
            JsonNode answer = node.get("answer");
            return (answer != null && answer.isTextual()) ? Optional.of(answer.asText()) : Optional.empty();
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private String jsonAnswer(String answer) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("answer", answer);
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise deterministic answer", e);
        }
    }

    private String jsonFigures(List<Figure> figures) {
        try {
            return objectMapper.writeValueAsString(figures);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise cited figures", e);
        }
    }
}
