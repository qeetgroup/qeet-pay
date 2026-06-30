/**
 * Platform (cross-cutting) module — shared infrastructure, no payments domain logic (TAD §5):
 * merchant/tenant context + RLS, security (OIDC + API keys), idempotency, the transactional
 * outbox + NATS relay, config, and API plumbing.
 *
 * <p>Declared OPEN so every domain module (merchants, ledger, …) may build on it without a
 * boundary violation.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Platform")
package com.qeetgroup.qeetpay.platform;
