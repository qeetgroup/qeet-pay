/**
 * LLM copilots module (PRD Module 12.5 Treasury &amp; Cash-Flow Copilot, N7 Reconciliation Copilot,
 * Module 17 Natural-Language Query; TAD §8). A merchant-facing conversational surface that answers
 * cash-flow / settlement / working-capital, reconciliation-break / leakage, and plain-English metric
 * questions over the merchant's <em>own</em> data.
 *
 * <p><b>Every answer routes through the {@link com.qeetgroup.qeetpay.ai.AiGateway}</b> (PRD §6.4): the
 * question is PII-masked before any model call, the decision is written to the append-only AI audit
 * trail, and a caller-supplied <em>deterministic summary</em> is used when the model errors / is
 * stale / ambiguous / low-confidence — the offline {@code SandboxAiModelClient} stand-in means the
 * deterministic path is what actually answers today. Every answer additionally <b>cites the underlying
 * figures</b> it used (computed deterministically from the analytics + reconciliation public read
 * APIs), so a citation is always present regardless of which path the gateway took.
 *
 * <p>This module never moves money and holds no ledger postings — it is a read-only + conversational
 * layer on top of the deterministic core. It reads figures only through other modules' public services
 * ({@code analytics} cash-flow forecast / TPV / MRR / success-rate, and {@code reconciliation}
 * settlement + discrepancy reads), governs the model call through {@code ai}, and persists the
 * conversation trail (merchant-scoped, RLS) for audit. Its dependencies are declared below;
 * {@code ledger} is deliberately not one — no direct ledger access.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Copilot",
        allowedDependencies = {"platform", "ai", "analytics", "reconciliation"})
package com.qeetgroup.qeetpay.copilot;
