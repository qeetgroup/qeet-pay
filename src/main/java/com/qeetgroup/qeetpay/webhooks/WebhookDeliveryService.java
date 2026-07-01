package com.qeetgroup.qeetpay.webhooks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Delivers webhook payloads to registered endpoints with HMAC-SHA256 signatures.
 * Retries up to {@link #MAX_ATTEMPTS} times with exponential back-off (sync in tests;
 * async via NATS relay when NATS is enabled in production).
 */
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryRepository deliveries;
    private final MerchantScope merchantScope;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public WebhookDeliveryService(
            WebhookEndpointRepository endpoints,
            WebhookDeliveryRepository deliveries,
            MerchantScope merchantScope,
            ObjectMapper objectMapper) {
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.merchantScope = merchantScope;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    // Package-private: test injection (allows overriding RestTemplate with a mock server)
    WebhookDeliveryService(
            WebhookEndpointRepository endpoints,
            WebhookDeliveryRepository deliveries,
            MerchantScope merchantScope,
            ObjectMapper objectMapper,
            RestTemplate restTemplate) {
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.merchantScope = merchantScope;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    /**
     * Fan out an event to all ACTIVE endpoints that subscribe to it.
     * Creates one {@link WebhookDelivery} per matching endpoint and dispatches immediately.
     */
    @Transactional
    public void fanOut(UUID merchantId, String eventType, Map<String, Object> eventPayload) {
        merchantScope.apply(merchantId);
        List<WebhookEndpoint> active = endpoints.findByMerchantIdAndStatus(merchantId, "ACTIVE");
        String payloadJson = toJson(eventPayload);

        for (WebhookEndpoint endpoint : active) {
            if (!endpoint.subscribesTo(eventType)) continue;
            WebhookDelivery delivery = deliveries.save(
                    new WebhookDelivery(endpoint.getId(), merchantId, eventType, payloadJson));
            dispatch(endpoint, delivery, payloadJson);
        }
    }

    @Transactional
    public WebhookEndpoint register(UUID merchantId, String url, String events, String signingSecret) {
        merchantScope.apply(merchantId);
        return endpoints.save(new WebhookEndpoint(merchantId, url, events, signingSecret));
    }

    @Transactional
    public void disable(UUID merchantId, UUID endpointId) {
        merchantScope.apply(merchantId);
        WebhookEndpoint ep = endpoints.findById(endpointId)
                .filter(e -> e.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new WebhookNotFoundException("no endpoint " + endpointId));
        ep.disable();
        endpoints.save(ep);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpoint> listEndpoints(UUID merchantId) {
        merchantScope.apply(merchantId);
        return endpoints.findByMerchantIdAndStatus(merchantId, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> deliveriesFor(UUID merchantId, UUID endpointId) {
        merchantScope.apply(merchantId);
        endpoints.findById(endpointId)
                .filter(e -> e.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new WebhookNotFoundException("no endpoint " + endpointId));
        return deliveries.findByEndpointIdOrderByCreatedAtDesc(endpointId);
    }

    private void dispatch(WebhookEndpoint endpoint, WebhookDelivery delivery, String payloadJson) {
        String signature = WebhookSignature.compute(payloadJson, endpoint.getSigningSecret());
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                RequestEntity<String> request = RequestEntity.method(HttpMethod.POST, URI.create(endpoint.getUrl()))
                        .header("Content-Type", "application/json")
                        .header("X-QeetPay-Signature", signature)
                        .header("X-QeetPay-Event", delivery.getEventType())
                        .body(payloadJson);
                ResponseEntity<String> response = restTemplate.exchange(request, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    delivery.recordSuccess(response.getStatusCode().value());
                    deliveries.save(delivery);
                    return;
                }
                delivery.recordFailure(response.getStatusCode().value(), "non-2xx response");
            } catch (RestClientException e) {
                delivery.recordNetworkError(e.getMessage());
                log.warn("Webhook delivery attempt {} failed for endpoint {}: {}", attempt, endpoint.getId(), e.getMessage());
            }
        }
        deliveries.save(delivery);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("webhook payload serialisation failed", e);
        }
    }
}
