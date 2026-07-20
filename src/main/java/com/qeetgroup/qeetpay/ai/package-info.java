/**
 * AI gateway module (PRD §6 "AI Feature Specifications", esp. §6.4 Human-Oversight & Safety Matrix;
 * TAD §8.2 AI Gateway / §8.3 AI safety controls). The single substrate every future AI feature must
 * call — no feature talks to a model directly. {@link com.qeetgroup.qeetpay.ai.AiGateway} enforces the
 * §6.4 matrix on every request: PII/PAN/Aadhaar is masked before any model call, money-affecting
 * decision types require a human-review flag (fail-closed to a deterministic path otherwise), calls
 * run under the caller's merchant RLS scope, and every request + decision is persisted to an
 * append-only audit table and emitted to the outbox for qeet-logs. A model call that times out,
 * errors, or returns a stale/ambiguous/low-confidence result falls back to a caller-supplied
 * deterministic function. Ships with an offline {@link com.qeetgroup.qeetpay.ai.SandboxAiModelClient}
 * stand-in (no network/LLM); a real client is a drop-in replacement. Merchant-scoped via platform RLS.
 *
 * <p>This module deliberately depends on {@code platform} only (tenancy + outbox); it holds no
 * money-movement logic and never touches the ledger — it is a governance layer that sits <em>on top
 * of</em> the deterministic core, per TAD §8.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "AI Gateway",
        allowedDependencies = {"platform"})
package com.qeetgroup.qeetpay.ai;
