package com.qeetgroup.qeetpay.messaging;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Messaging API (PRD Module 09): configure WhatsApp/SMS/email templates and dispatch rendered
 * messages (queued to qeet-notify via the outbox); delivery callbacks mark a dispatch SENT/FAILED.
 */
@RestController
@RequestMapping("/v1/messaging")
public class MessagingController {

    private final MessagingService messaging;

    public MessagingController(MessagingService messaging) {
        this.messaging = messaging;
    }

    // ── Templates ────────────────────────────────────────────────────────────

    @PutMapping("/templates")
    public TemplateView upsertTemplate(@Valid @RequestBody TemplateRequest req) {
        return TemplateView.of(
                messaging.upsertTemplate(MerchantContext.require(), req.templateKey(), req.channel(), req.body()));
    }

    @GetMapping("/templates")
    public List<TemplateView> listTemplates() {
        return messaging.listTemplates(MerchantContext.require()).stream().map(TemplateView::of).toList();
    }

    // ── Dispatch ─────────────────────────────────────────────────────────────

    @PostMapping("/dispatch")
    public ResponseEntity<DispatchView> dispatch(@Valid @RequestBody DispatchRequest req) {
        MessageDispatch d =
                messaging.dispatch(
                        MerchantContext.require(), req.templateKey(), req.channel(), req.recipient(),
                        req.variables(), req.relatedRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(DispatchView.of(d));
    }

    @GetMapping("/dispatches")
    public List<DispatchView> listDispatches() {
        return messaging.listDispatches(MerchantContext.require()).stream().map(DispatchView::of).toList();
    }

    @GetMapping("/dispatches/{dispatchId}")
    public DispatchView getDispatch(@PathVariable UUID dispatchId) {
        return DispatchView.of(messaging.getDispatch(MerchantContext.require(), dispatchId));
    }

    @PostMapping("/dispatches/{dispatchId}/delivered")
    public DispatchView delivered(@PathVariable UUID dispatchId, @Valid @RequestBody DeliveredRequest req) {
        return DispatchView.of(messaging.markDelivered(MerchantContext.require(), dispatchId, req.providerRef()));
    }

    @PostMapping("/dispatches/{dispatchId}/failed")
    public DispatchView failed(@PathVariable UUID dispatchId, @Valid @RequestBody FailedRequest req) {
        return DispatchView.of(messaging.markFailed(MerchantContext.require(), dispatchId, req.reason()));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record TemplateRequest(
            @NotBlank String templateKey, @NotNull MessageChannel channel, @NotBlank String body) {}

    public record DispatchRequest(
            @NotBlank String templateKey,
            @NotNull MessageChannel channel,
            @NotBlank String recipient,
            Map<String, String> variables,
            String relatedRef) {}

    public record DeliveredRequest(@NotBlank String providerRef) {}

    public record FailedRequest(@NotBlank String reason) {}

    public record TemplateView(
            String id, String templateKey, String channel, String body, boolean active, Instant createdAt) {
        static TemplateView of(MessageTemplate t) {
            return new TemplateView(
                    t.getId().toString(), t.getTemplateKey(), t.getChannel().name(), t.getBody(),
                    t.isActive(), t.getCreatedAt());
        }
    }

    public record DispatchView(
            String id, String templateKey, String channel, String recipient, String renderedBody,
            String status, String providerRef, String relatedRef, String failureReason,
            Instant createdAt, Instant sentAt) {
        static DispatchView of(MessageDispatch d) {
            return new DispatchView(
                    d.getId().toString(), d.getTemplateKey(), d.getChannel().name(), d.getRecipient(),
                    d.getRenderedBody(), d.getStatus().name(), d.getProviderRef(), d.getRelatedRef(),
                    d.getFailureReason(), d.getCreatedAt(), d.getSentAt());
        }
    }
}
