/**
 * Agentic mandates + MCP tool manifest (PRD Module 17.5 "AI-agent mandates", Novel N1). Lets an AI
 * agent transact on a merchant's behalf under a scoped, revocable <em>mandate</em> of authority: a
 * per-agent allowlist of operations + payees, a per-transaction cap and a cumulative spend cap, a
 * validity window and a running spent counter. {@link com.qeetgroup.qeetpay.agentic.AgentMandateService}
 * turns every action request into a purely <b>deterministic</b> ALLOW/DENY decision (active +
 * in-window + within caps + operation/payee allowlisted), increments the spent counter on capture,
 * and records an append-only {@code agent_mandate_use} for every decision. Authorization is idempotent
 * on an agent-supplied key (reusing {@code platform.idempotency}), so a retry never double-spends.
 *
 * <p>{@link com.qeetgroup.qeetpay.agentic.McpManifestService} publishes a static, curated
 * Model-Context-Protocol tool manifest — the safe operations an agent may call (payment create,
 * payment-link create, payout create, invoice create, balance read) with input schemas + required
 * scopes. The manifest is a <em>descriptor only</em>; it never executes anything.
 *
 * <p>Like {@code ai/}, this module depends on {@code platform} only (tenancy + outbox + idempotency):
 * it holds no money-movement logic and never touches the ledger — it is a governance layer that sits
 * on top of the deterministic core. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Agentic Mandates",
        allowedDependencies = {"platform"})
package com.qeetgroup.qeetpay.agentic;
