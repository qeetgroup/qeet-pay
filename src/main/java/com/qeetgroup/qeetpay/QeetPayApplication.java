package com.qeetgroup.qeetpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Qeet Pay — payments/billing modular monolith (TAD §3.1).
 *
 * <p>Bounded contexts are the direct sub-packages of this one (TAD §5). Phase 0 ships the
 * {@code platform} foundation (multi-tenant RLS, OIDC + API-key auth, idempotency, outbox),
 * the {@code merchants} tenant aggregate, and the {@code ledger} double-entry core (the crown
 * jewel). Payment rails / payouts / billing / GST land in Phase 1.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@Modulithic(systemName = "QeetPay")
public class QeetPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(QeetPayApplication.class, args);
    }
}
