package com.qeetgroup.qeetpay.cards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Live card-issuing rail (PRD Module 10.3) backed by an M2P / Decentro-style issuing API over HTTP.
 * Active only when {@code qeetpay.cards.enabled=true}; marked {@link Primary} so it wins over
 * {@link SandboxCardIssuingProvider} for the single {@link CardIssuingProvider} injected into
 * {@link CardService}. The default bean name ({@code liveCardIssuingProvider}) is exactly what
 * {@code SandboxCardIssuingProvider}'s {@code @ConditionalOnMissingBean(name = "liveCardIssuingProvider")}
 * keys off, so the sandbox backs off when this adapter is present — and stays the default otherwise.
 *
 * <p>Card issue is {@code POST /cards} with the merchant's {@code programId} and the internal card id
 * as the {@code clientReference}; the response's card reference / last4 / expiry are read robustly
 * (several field-name spellings). Lifecycle ({@code /freeze}, {@code /unfreeze}, {@code /close}) and
 * optional load / spend authorization ({@code /load}, {@code /authorizations}) are keyed by that same
 * client reference. Failures throw {@link CardIssuingException} so the caller's transaction rolls back
 * — unlike KYB, an issuing outage must not silently succeed.
 *
 * <p>Requests/responses are serialized/parsed with Jackson explicitly (a String JSON body, robust
 * across RestClient request factories, mirroring {@code LiveKybAdapter}). No custom request factory is
 * set so tests can bind {@code MockRestServiceServer} to the injected {@link RestClient.Builder}.
 */
@ConditionalOnProperty(prefix = "qeetpay.cards", name = "enabled", havingValue = "true")
@Primary
@Component
public class LiveCardIssuingProvider implements CardIssuingProvider {

    private static final Logger log = LoggerFactory.getLogger(LiveCardIssuingProvider.class);

    private final RestClient http;
    private final CardIssuingProperties props;
    private final ObjectMapper objectMapper;

    public LiveCardIssuingProvider(
            CardIssuingProperties props, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        RestClient.Builder b = builder.baseUrl(props.baseUrl());
        if (!props.apiKey().isBlank()) {
            b = b.defaultHeader("X-Api-Key", props.apiKey());
        }
        this.http = b.build();
    }

    @Override
    public IssuedCard issue(CardIssueRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("programId", props.programId());
        body.put("clientReference", request.cardId().toString());
        body.put("holderReference", request.holderRef());
        body.put("cardType", request.type().name());
        body.put("currency", request.currency());

        JsonNode n = post("/cards", body);
        String ref = firstNonBlank(n, "cardReference", "card_reference", "cardId", "id");
        String last4 = firstNonBlank(n, "last4", "last_four", "lastFour");
        if (ref == null || last4 == null) {
            throw new CardIssuingException("issuing rail returned no card reference / last4");
        }
        String expiry = firstNonBlank(n, "expiry", "expiry_date", "expiryDate");
        return new IssuedCard(ref, last4, expiry == null ? "" : expiry);
    }

    @Override
    public void freeze(String networkCardRef) {
        post("/cards/" + networkCardRef + "/freeze", Map.of());
    }

    @Override
    public void unfreeze(String networkCardRef) {
        post("/cards/" + networkCardRef + "/unfreeze", Map.of());
    }

    @Override
    public void close(String networkCardRef) {
        post("/cards/" + networkCardRef + "/close", Map.of());
    }

    @Override
    public void authorizeLoad(CardAuthorization authorization) {
        post("/cards/" + authorization.networkCardRef() + "/load", amountBody(authorization));
    }

    @Override
    public void authorizeSpend(CardAuthorization authorization) {
        post("/cards/" + authorization.networkCardRef() + "/authorizations", amountBody(authorization));
    }

    private Map<String, Object> amountBody(CardAuthorization a) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amountMinor", a.amountMinor());
        body.put("currency", a.currency());
        return body;
    }

    /** POSTs a JSON body to the rail and returns the parsed response (empty object if none). */
    private JsonNode post(String path, Map<String, Object> body) {
        try {
            String responseJson =
                    http.post()
                            .uri(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(objectMapper.writeValueAsString(body))
                            .retrieve()
                            .body(String.class);
            return objectMapper.readTree(
                    responseJson == null || responseJson.isBlank() ? "{}" : responseJson);
        } catch (CardIssuingException e) {
            throw e;
        } catch (Exception e) {
            log.warn("card-issuing rail call to {} failed", path, e);
            throw new CardIssuingException("card-issuing rail call failed: " + path, e);
        }
    }

    private String firstNonBlank(JsonNode n, String... fields) {
        for (String field : fields) {
            JsonNode v = n.path(field);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }
}
