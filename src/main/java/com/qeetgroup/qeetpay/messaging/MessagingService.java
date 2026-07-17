package com.qeetgroup.qeetpay.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WhatsApp-native messaging (PRD Module 09, TAD §5). Merchants configure templates per key + channel;
 * dispatching renders the template with the supplied variables, persists a {@link MessageDispatch}
 * (QUEUED), and emits it to the transactional outbox for qeet-notify to deliver. A delivery callback
 * marks the dispatch SENT/FAILED. Depends only on {@code platform}; merchant-scoped via RLS.
 */
@Service
public class MessagingService {

    private final MessageTemplateRepository templates;
    private final MessageDispatchRepository dispatches;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public MessagingService(
            MessageTemplateRepository templates,
            MessageDispatchRepository dispatches,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.templates = templates;
        this.dispatches = dispatches;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Creates or updates a template for a (key, channel). */
    @Transactional
    public MessageTemplate upsertTemplate(
            UUID merchantId, String templateKey, MessageChannel channel, String body) {
        merchantScope.apply(merchantId);
        if (templateKey == null || templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body is required");
        }
        MessageTemplate template =
                templates
                        .findByMerchantIdAndTemplateKeyAndChannel(merchantId, templateKey, channel)
                        .map(t -> {
                            t.update(body, true);
                            return t;
                        })
                        .orElseGet(() -> new MessageTemplate(merchantId, templateKey, channel, body));
        return templates.save(template);
    }

    @Transactional(readOnly = true)
    public List<MessageTemplate> listTemplates(UUID merchantId) {
        merchantScope.apply(merchantId);
        return templates.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /**
     * Renders the (key, channel) template with {@code variables}, persists a QUEUED dispatch to
     * {@code recipient}, and emits it to the outbox for delivery.
     */
    @Transactional
    public MessageDispatch dispatch(
            UUID merchantId, String templateKey, MessageChannel channel, String recipient,
            Map<String, String> variables, String relatedRef) {
        merchantScope.apply(merchantId);
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient is required");
        }
        MessageTemplate template =
                templates
                        .findByMerchantIdAndTemplateKeyAndChannel(merchantId, templateKey, channel)
                        .filter(MessageTemplate::isActive)
                        .orElseThrow(() ->
                                new MessagingNotFoundException(
                                        "no active template '" + templateKey + "' for " + channel));

        String rendered = TemplateRenderer.render(template.getBody(), variables);
        MessageDispatch dispatch =
                dispatches.save(
                        new MessageDispatch(merchantId, templateKey, channel, recipient, rendered, relatedRef));
        outbox.enqueue(merchantId, "notify.dispatch.requested", dispatchJson(dispatch));
        return dispatch;
    }

    /** Delivery callback from qeet-notify: marks the dispatch SENT with the provider's delivery id. */
    @Transactional
    public MessageDispatch markDelivered(UUID merchantId, UUID dispatchId, String providerRef) {
        merchantScope.apply(merchantId);
        MessageDispatch dispatch = load(merchantId, dispatchId);
        dispatch.markSent(providerRef);
        return dispatches.save(dispatch);
    }

    /** Delivery callback from qeet-notify: marks the dispatch FAILED with a reason. */
    @Transactional
    public MessageDispatch markFailed(UUID merchantId, UUID dispatchId, String reason) {
        merchantScope.apply(merchantId);
        MessageDispatch dispatch = load(merchantId, dispatchId);
        dispatch.markFailed(reason);
        return dispatches.save(dispatch);
    }

    @Transactional(readOnly = true)
    public List<MessageDispatch> listDispatches(UUID merchantId) {
        merchantScope.apply(merchantId);
        return dispatches.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public MessageDispatch getDispatch(UUID merchantId, UUID dispatchId) {
        merchantScope.apply(merchantId);
        return load(merchantId, dispatchId);
    }

    private MessageDispatch load(UUID merchantId, UUID dispatchId) {
        return dispatches
                .findById(dispatchId)
                .filter(d -> d.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new MessagingNotFoundException("no dispatch " + dispatchId));
    }

    private String dispatchJson(MessageDispatch d) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("dispatchId", d.getId().toString());
        b.put("channel", d.getChannel().name());
        b.put("recipient", d.getRecipient());
        b.put("templateKey", d.getTemplateKey());
        b.put("body", d.getRenderedBody());
        if (d.getRelatedRef() != null) {
            b.put("relatedRef", d.getRelatedRef());
        }
        try {
            return objectMapper.writeValueAsString(b);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise messaging event", e);
        }
    }
}
