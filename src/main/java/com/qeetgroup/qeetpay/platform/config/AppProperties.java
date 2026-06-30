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
