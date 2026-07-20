package com.qeetgroup.qeetpay.fraud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.config.AppProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the Python fraud-svc {@code POST /score} (active only when {@code qeetpay.fraud.enabled=true}).
 * Serializes/parses with Jackson explicitly (a String JSON body — robust across RestClient request
 * factories). Fails <em>open</em> — any transport/parse error returns ALLOW so a fraud-service outage
 * never blocks legitimate payments (scoring is advisory; TAD §8). Parses the fraud-svc explainable
 * output ({@code explanation[]} + {@code model}, TAD §8.4) onto the {@link FraudDecision}.
 *
 * <p>This is the low-level {@link FraudScorer} (the deterministic path); {@link AiGatewayFraudClient}
 * wraps it with the §6.4 safety substrate.
 */
@Component
@ConditionalOnProperty(name = "qeetpay.fraud.enabled", havingValue = "true")
public class HttpFraudClient implements FraudScorer {

    private static final Logger log = LoggerFactory.getLogger(HttpFraudClient.class);

    private final RestClient http;
    private final ObjectMapper objectMapper;

    public HttpFraudClient(AppProperties props, ObjectMapper objectMapper) {
        // SimpleClientHttpRequestFactory (HttpURLConnection) reliably writes the POST body with a
        // Content-Length; the auto-detected JDK HttpClient factory dropped it in this environment.
        this.http =
                RestClient.builder()
                        .baseUrl(props.getFraud().getUrl())
                        .requestFactory(new SimpleClientHttpRequestFactory())
                        .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public FraudDecision score(FraudCheck check) {
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("paymentId", check.paymentId().toString());
            req.put("merchantId", check.merchantId().toString());
            req.put("amountMinor", check.amountMinor());
            req.put("currency", check.currency());
            req.put("method", check.method());
            if (check.customerVpa() != null) {
                req.put("customerVpa", check.customerVpa());
            }
            if (check.ip() != null) {
                req.put("ip", check.ip());
            }

            String responseJson =
                    http.post()
                            .uri("/score")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(objectMapper.writeValueAsString(req))
                            .retrieve()
                            .body(String.class);

            if (responseJson == null || responseJson.isBlank()) {
                return FraudDecision.allow("empty fraud response");
            }
            JsonNode n = objectMapper.readTree(responseJson);
            List<String> reasons = new ArrayList<>();
            n.path("reasons").forEach(r -> reasons.add(r.asText()));

            List<FraudReason> topReasons = new ArrayList<>();
            for (JsonNode e : n.path("explanation")) {
                topReasons.add(
                        new FraudReason(
                                e.path("feature").asText(""),
                                e.path("contribution").asDouble(0.0),
                                e.path("value").asDouble(0.0),
                                e.path("reason").asText("")));
            }
            String model = n.path("model").asText("rules");

            return new FraudDecision(
                    n.path("score").asInt(0),
                    parse(n.path("decision").asText("allow")),
                    reasons,
                    topReasons,
                    model);
        } catch (Exception e) {
            log.warn("fraud scoring failed; failing open (allow)", e);
            return FraudDecision.allow("fraud service unavailable");
        }
    }

    private static FraudDecisionType parse(String decision) {
        return switch (decision == null ? "" : decision.toLowerCase()) {
            case "block" -> FraudDecisionType.BLOCK;
            case "challenge" -> FraudDecisionType.CHALLENGE;
            default -> FraudDecisionType.ALLOW;
        };
    }
}
