/**
 * Fraud module (TAD Module 08) — the Java client to the standalone Python/FastAPI fraud-scoring
 * service (`fraud-svc/`, port 8201). Payments consult {@link com.qeetgroup.qeetpay.fraud.FraudClient}
 * during authorization. Disabled by default ({@code qeetpay.fraud.enabled=false}) → allow-all; the
 * HTTP client fails open if the service is unreachable (scoring is advisory, never a hard dependency).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Fraud")
package com.qeetgroup.qeetpay.fraud;
