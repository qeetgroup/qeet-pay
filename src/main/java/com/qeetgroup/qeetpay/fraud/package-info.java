/**
 * Fraud module (TAD Module 08 / PRD Module 08.1 + 08.4) — the Java side of the standalone
 * Python/FastAPI fraud-scoring service (`fraud-svc/`, port 8201). Payments consult
 * {@link com.qeetgroup.qeetpay.fraud.FraudClient} during authorization.
 *
 * <p>The low-level {@link com.qeetgroup.qeetpay.fraud.FraudScorer} is the fraud-svc HTTP client
 * ({@link com.qeetgroup.qeetpay.fraud.HttpFraudClient}, active when {@code qeetpay.fraud.enabled=true})
 * or an allow-all fallback (default in dev/test); it fails open if the service is unreachable. The
 * {@link com.qeetgroup.qeetpay.fraud.FraudClient} bean payments actually receive
 * ({@link com.qeetgroup.qeetpay.fraud.AiGatewayFraudClient}) routes that scorer through the
 * {@code ai/AiGateway} §6.4 safety substrate ({@link com.qeetgroup.qeetpay.fraud.FraudGatewayAuditor}):
 * the input is PII-masked, the decision is audited + emitted to the outbox, and — fraud being a
 * money-affecting AI feature — it fails closed to the deterministic scorer. Each decision (score,
 * verdict, Explainable-AI top reasons, model) is persisted to the merchant-scoped
 * {@code fraud.fraud_decision} table and exposed read-only at {@code GET /v1/fraud/decisions}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Fraud")
package com.qeetgroup.qeetpay.fraud;
