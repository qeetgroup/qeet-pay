package com.qeetgroup.qeetpay.platform.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly-typed app config (bound from {@code qeetpay.*} in application.yml / env). */
@ConfigurationProperties(prefix = "qeetpay")
public class AppProperties {

    private Oidc oidc = new Oidc();
    private Cors cors = new Cors();
    private Nats nats = new Nats();
    private Fraud fraud = new Fraud();
    private RateLimit rateLimit = new RateLimit();

    public Oidc getOidc() {
        return oidc;
    }

    public void setOidc(Oidc oidc) {
        this.oidc = oidc;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Nats getNats() {
        return nats;
    }

    public void setNats(Nats nats) {
        this.nats = nats;
    }

    public Fraud getFraud() {
        return fraud;
    }

    public void setFraud(Fraud fraud) {
        this.fraud = fraud;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    /** Qeet ID OIDC relying-party contract (TAD §2.2). */
    public static class Oidc {
        private String issuerUri = "https://api.id.qeet.in";
        private String jwksUri = "https://api.id.qeet.in/.well-known/jwks.json";

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getJwksUri() {
            return jwksUri;
        }

        public void setJwksUri(String jwksUri) {
            this.jwksUri = jwksUri;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:3201");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /** Transactional-outbox relay to NATS JetStream (TAD §9.1). Disabled by default. */
    public static class Nats {
        private boolean enabled = false;
        private String url = "nats://localhost:4222";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /**
     * Per-caller rate limiting on {@code /v1/**} (TAD §11). An in-memory token bucket keyed by
     * merchant / API key; no Redis dependency. <b>Disabled by default</b> so dev/test never throttle;
     * the {@code prod}/{@code staging} profiles turn it on. {@code capacity} is the burst size and
     * {@code refillPerSecond} the sustained rate; a tripped bucket returns RFC-7807 429 with a
     * {@code Retry-After} header.
     */
    public static class RateLimit {
        private boolean enabled = false;
        private int capacity = 120;
        private double refillPerSecond = 50;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public double getRefillPerSecond() {
            return refillPerSecond;
        }

        public void setRefillPerSecond(double refillPerSecond) {
            this.refillPerSecond = refillPerSecond;
        }
    }

    /** Python/FastAPI fraud-scoring service (TAD §8). Disabled by default → allow-all. */
    public static class Fraud {
        private boolean enabled = false;
        private String url = "http://localhost:8201";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
