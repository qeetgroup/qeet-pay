package com.qeetgroup.qeetpay.crossborder;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live FX-rate source configuration (TAD §5). Picked up by {@code @ConfigurationPropertiesScan} on
 * the application class. All values default to empty/sandbox so the deterministic
 * {@link SandboxFxRateAdapter} remains the default when {@code qeetpay.fx.enabled} is unset
 * (dev/test) — the live {@link LiveFxRateAdapter} only activates when {@code enabled=true}.
 *
 * @param enabled gate for {@link LiveFxRateAdapter} (default {@code false} → sandbox rates)
 * @param baseUrl FX-rate API base URL (default an exchangerate.host-style host)
 * @param apiKey  API access key sent as {@code access_key} when non-blank — inject via env/secret
 * @param ttl     short cache TTL for fetched rates (default 60s); guards against per-call fan-out
 */
@ConfigurationProperties("qeetpay.fx")
public record FxProperties(boolean enabled, String baseUrl, String apiKey, Duration ttl) {

    public FxProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.exchangerate.host";
        if (apiKey == null) apiKey = "";
        if (ttl == null || ttl.isZero() || ttl.isNegative()) ttl = Duration.ofSeconds(60);
    }
}
