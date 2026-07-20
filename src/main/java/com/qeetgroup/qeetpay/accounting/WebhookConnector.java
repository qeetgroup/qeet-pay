package com.qeetgroup.qeetpay.accounting;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Generic webhook connector — POSTs the export payload as JSON to the merchant's configured
 * {@code webhookUrl} (from {@link AccountingConnection}). Fails cleanly (recorded, not thrown) when
 * no URL is configured or the endpoint errors.
 */
@Component
public class WebhookConnector implements AccountingConnector {

    private static final Logger log = LoggerFactory.getLogger(WebhookConnector.class);

    private final RestClient http;
    private final ObjectMapper objectMapper;

    @Autowired
    public WebhookConnector(ObjectMapper objectMapper) {
        this.http = RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()).build();
        this.objectMapper = objectMapper;
    }

    /** Package-private: test injection with a mocked {@link RestClient}. */
    WebhookConnector(RestClient http, ObjectMapper objectMapper) {
        this.http = http;
        this.objectMapper = objectMapper;
    }

    @Override
    public AccountingTarget target() {
        return AccountingTarget.WEBHOOK;
    }

    @Override
    public SyncResult push(ExportPayload payload, AccountingConnection connection) {
        if (connection == null || connection.getWebhookUrl() == null || connection.getWebhookUrl().isBlank()) {
            return SyncResult.failure("no webhook url configured for this merchant");
        }
        String url = connection.getWebhookUrl();
        try {
            String body = objectMapper.writeValueAsString(toBody(payload));
            var response =
                    http.post()
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .toBodilessEntity();
            int status = response.getStatusCode().value();
            if (!response.getStatusCode().is2xxSuccessful()) {
                return SyncResult.failure("webhook returned HTTP " + status);
            }
            return SyncResult.ok(payload.recordCount(), "http-" + status, body);
        } catch (Exception e) {
            log.warn("accounting webhook push failed for {}", url, e);
            return SyncResult.failure("webhook push failed: " + e.getMessage());
        }
    }

    private Map<String, Object> toBody(ExportPayload payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantId", payload.merchantId().toString());
        body.put("periodStart", payload.periodStart().toString());
        body.put("periodEnd", payload.periodEnd().toString());
        body.put("recordCount", payload.recordCount());
        body.put("vouchers", payload.vouchers());
        body.put("invoices", payload.invoices());
        return body;
    }
}
