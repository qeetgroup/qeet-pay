package com.qeetgroup.qeetpay.crossborder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Live FX-rate feed (TAD §5) backed by an exchangerate.host/fixer-style API. Active only when
 * {@code qeetpay.fx.enabled=true}; marked {@link Primary} so it wins over {@link SandboxFxRateAdapter}
 * for the single {@link FxRateAdapter} injected into {@link CrossBorderService}. The default bean name
 * ({@code liveFxRateAdapter}) is also what {@code SandboxFxRateAdapter}'s
 * {@code @ConditionalOnMissingBean(name="liveFxRateAdapter")} keys off, so the sandbox backs off when
 * this adapter is present — and stays the default when it is not.
 *
 * <p>Fetches {@code GET /latest?base={from}&symbols={to}} (plus {@code access_key} when configured)
 * and reads {@code rates.<to>}. Results are held in a short-TTL in-process cache (no external cache
 * dependency) to avoid a network round-trip per remittance. Rates are parsed from the JSON literal
 * into {@link BigDecimal} — no float — so downstream INR-paise conversion stays exact.
 *
 * <p>No custom request factory is set so tests can bind {@code MockRestServiceServer} to the injected
 * {@link RestClient.Builder}.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "qeetpay.fx", name = "enabled", havingValue = "true")
public class LiveFxRateAdapter implements FxRateAdapter {

    private final RestClient http;
    private final FxProperties props;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedRate> cache = new ConcurrentHashMap<>();

    public LiveFxRateAdapter(
            FxProperties props, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = builder.baseUrl(props.baseUrl()).build();
    }

    @Override
    public BigDecimal rate(String fromCurrency, String toCurrency) {
        if (fromCurrency == null || fromCurrency.isBlank() || toCurrency == null || toCurrency.isBlank()) {
            throw new IllegalArgumentException("fromCurrency and toCurrency are required");
        }
        String from = fromCurrency.toUpperCase(Locale.ROOT);
        String to = toCurrency.toUpperCase(Locale.ROOT);
        String key = from + "->" + to;

        long now = System.currentTimeMillis();
        CachedRate cached = cache.get(key);
        if (cached != null && cached.isFresh(now)) {
            return cached.rate();
        }

        BigDecimal rate = fetch(from, to);
        cache.put(key, new CachedRate(rate, now + props.ttl().toMillis()));
        return rate;
    }

    private BigDecimal fetch(String from, String to) {
        String responseJson =
                http.get()
                        .uri(
                                uri -> {
                                    uri.path("/latest")
                                            .queryParam("base", from)
                                            .queryParam("symbols", to);
                                    if (!props.apiKey().isBlank()) {
                                        uri.queryParam("access_key", props.apiKey());
                                    }
                                    return uri.build();
                                })
                        .retrieve()
                        .body(String.class);

        JsonNode rateNode = parse(responseJson == null ? "{}" : responseJson).path("rates").path(to);
        if (rateNode.isMissingNode() || rateNode.isNull() || rateNode.asText().isBlank()) {
            throw new IllegalStateException("no FX rate for " + from + "->" + to);
        }
        return new BigDecimal(rateNode.asText());
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("invalid FX rate response", e);
        }
    }

    private record CachedRate(BigDecimal rate, long expiresAtMillis) {
        boolean isFresh(long now) {
            return now < expiresAtMillis;
        }
    }
}
