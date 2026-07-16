# Qeet Pay — Production-Readiness Checklist

An honest GA-readiness view for a **regulated India payments platform**. It
separates what is genuinely **built and in the codebase today** from what is
**still required for a real GA** — which, for payments, is dominated by
*external/operational/regulatory* work, not more application code.

Section references are to the Qeet Pay TAD
(`../qeet-files/qeet-pay/Technical_Architecture_Document.md`) and PRD.

> **Reality check.** Qeet Pay is pre-GA (`0.0.1-SNAPSHOT`). The engineering core
> is substantial and correct-by-construction in the areas that matter most for
> money (ledger, tenancy, idempotency). GA is gated on licensing, security
> attestation, and operational maturity — the items in "Remaining" — not on
> finishing features.

---

## DONE — built and in the repo

| Area | Status | Where / evidence |
| --- | --- | --- |
| **Modular-monolith skeleton** | Done | Spring Modulith; boundaries verified by `ModularityTests`; `platform/` OPEN infra module. TAD §3.1, §5 |
| **Multi-tenancy via Postgres RLS** | Done | `merchant_id`-scoped; GUC `app.current_merchant_id` set by `MerchantFilter`/`MerchantScope`; `RlsIsolationTest` proves isolation under `NOSUPERUSER`. TAD §6.1, ADR-001 |
| **Double-entry, append-only ledger** | Done | `LedgerService.postEntry` enforces Σdebits = Σcredits; deferred COMMIT trigger; SELECT/INSERT-only role; nodal non-negative check. TAD §6.2, §7.1, ADR-002 |
| **Money as integer minor units** | Done | `amount_minor` BIGINT + ISO currency; `BigDecimal`/HALF_UP; no floats. TAD §6.2 |
| **20+ domain modules** | Done | payments (smart routing/orchestration), payouts (+bulk maker-checker), billing, mandates, dunning (+AI failure classification), gst (+IRN e-invoicing), kyb, fraud, webhooks, analytics (+cash-flow forecast), reconciliation/settlements, plus Phase-2: revrec, marketplace, filing, lending, virtualaccounts, escrow, crossborder, messaging, paymentlinks, bnpl, cards, insurance, tds, itc, esg. TAD §5 |
| **Idempotency + transactional outbox** | Done | Idempotency on money-moving endpoints; `platform.outbox_event` written in-transaction. TAD §4.3, §9.1 |
| **Identity — Qeet ID OIDC + API keys** | Done | OIDC resource server validates Qeet ID JWTs; `qp_live_`/`qp_test_` keys via `X-Api-Key`; profile-split security chains. TAD §2.2, §12.1 |
| **Operator console** | Done | `apps/console/` (TanStack Start + Vite + React 19 + `@qeetrix/ui`, :3201); covers every backend domain. TAD §11 |
| **Client SDKs** | Done | `../qeet-sdks/qeet-pay-{node,go,react}`. TAD §11 |
| **`qp` CLI** | Done | `cli/` (Go); covers payments/payouts/ledger/gst/etc. + `qp sandbox seed`. TAD §11 |
| **Developer sandbox** | Done | Sandbox adapters are the default when no live keys are set (`@ConditionalOnMissingBean`); `qp sandbox seed` populates a demo merchant. TAD §11, §11.1 |
| **Flyway migrations** | Done | `V1..V35` under `db/migration`, auto-apply on startup, immutable-once-applied discipline. TAD §14 |
| **CI** | Done (this PR) | Path-filtered workflows: `ci` (JDK 21 + Testcontainers), `console` (Node 22/pnpm), `cli` (Go), `dependency-review`, `codeql`. Least-privilege tokens. |
| **Dockerfile / compose** | Done | `Dockerfile` (`eclipse-temurin:21-jre`) + `docker-compose.yml` (Postgres/NATS/Redis). k8s/Helm is the deploy target. TAD §14 |
| **In progress (landing now)** | Partial | OpenAPI publication, OTel/tracing wiring, and rate limiting are being added (config keys present in `.env.example`, off by default). k8s/Helm manifests. TAD §12.3, §12.4 |

---

## REMAINING for GA — mostly external / operational / regulatory

These are ordered roughly by how blocking they are for a real production launch.
Most cannot be "coded around" — they are licenses, audits, and operational
commitments.

### Regulatory & licensing (hard blockers)

- [ ] **Acquiring path.** Phase 1 launches as a **sub-merchant under a licensed
  PA** (Razorpay/Cashfree) — legitimate and the standard path. **Own RBI PA
  license** (apply ~Month 6, receive ~Month 18–24) is required before direct
  acquiring / own nodal account / T+1 self-settlement. TAD §17, ADR-006, PRD roadmap.
- [ ] **Nodal / sponsor-bank structure.** ICICI + HDFC nodal accounts (two-bank,
  by design, for concentration-risk mitigation) provisioned and reconciled daily;
  sponsor/nodal-bank regulatory-health monitoring live. TAD §2.3, §14, §15.4.
- [ ] **DPDP compliance attestation.** Consent flows, data-principal rights
  (access/erasure), breach-notification runbook, and retention policy formally
  operational and attested — not just architected. TAD §6.4, §15.2.
- [ ] **Data-residency attestation.** Confirm/prove all data (incl. backups,
  logs, analytics) stays in `ap-south-1`; document any exceptions. TAD §14, §15.2.
- [ ] **Regulatory reporting.** GST (GSTR-1/3B/2B), TDS/TCS (Form 24Q/26Q), and
  RBI reporting exercised end-to-end against real GSTN/IRP endpoints (not sandbox
  adapters). TAD §7.4, §15.7.

### Security & privacy (attestation)

- [ ] **KMS BYOK + secrets rotation.** Per-merchant DEKs and PAN/Aadhaar keys in
  AWS KMS with customer-managed keys / BYOK; scheduled + on-compromise rotation
  of all secrets in Secrets Manager. TAD §14, §15.6.
- [ ] **Third-party penetration test / VAPT.** Independent pentest with findings
  remediated; annual VAPT cadence established. TAD §13 (Security), §15.
- [ ] **PCI-DSS scope & vault.** Phase 1 relies on a licensed PG's vault + RBI
  CoFT tokenization (zero raw PANs). Own **PCI-DSS Level 1** vault + QSA audit is
  Phase 2 and required before self-storing card data. TAD §15.5.
- [ ] **Confirm NOSUPERUSER role in every environment.** Prod/staging must run as
  `qeet_pay_app` (V2); verify RLS is *not* silently bypassed. CLAUDE.md Gotchas, §6.1.
- [ ] **STRIDE threat model closed out** with mitigations tracked. TAD §15.1.
- [ ] **SOC 2 Type II.** Controls implemented and audited (Phase 2/3 milestone).

### Reliability & operations

- [ ] **Load / performance testing + capacity plan.** Prove the NFR targets:
  API P50 < 30 ms / P99 < 300 ms, UPI P90 < 3 s, peak 5,000 → 50,000 tx/s.
  Establish autoscaling (HPA) headroom. TAD §13.
- [ ] **DR / backup with RPO/RTO.** Multi-AZ + synchronous replica; backups
  tested by restore; meet **RTO < 30 min / RPO < 1 min**; runbooked failover
  drills. TAD §13, §14.
- [ ] **Observability end-to-end.** OTel traces + Prometheus metrics + dashboards
  + SLO alerting wired and validated in prod. TAD §12.3. (Config scaffolding
  present; off by default.)
- [ ] **Audit trail live.** Enable the NATS outbox relay so events reach Qeet
  Logs end-to-end — today it ships **disabled by default**; audit is
  outbox-guaranteed but relay-pending. TAD §9.5, §12.2, §15.7.
- [ ] **Rate limiting enabled** (Redis-backed) in prod. TAD §12.4. (Off by default.)
- [ ] **On-call + runbooks.** Rotation, incident process, and runbooks for
  payment failures, provider outages, settlement/recon breaks, and the
  **compliance-hold / account-freeze communication SLA** (time-to-first-
  explanation) — the single most-repeated failure mode in the research corpus.
  TAD §13, §15.3.
- [ ] **Provider failover exercised.** Orchestration scorecards + real multi-
  provider failover validated against live providers, not just the sandbox
  simulator. TAD §4.5, §9.2, §11.1.

### Product / financial correctness at scale

- [ ] **Reconciliation at production volume.** Daily (+ continuous, §7.7) recon
  proven at scale; 100% settlement/recon accuracy target held; nodal invariant
  monitored. TAD §6.2, §7.7.
- [ ] **Settlement-speed decision.** Decide T+0 as a Growth-tier-included default
  vs. paid add-on (flagged in NFRs). TAD §13.

---

## How to read this

- **DONE** items are verifiable in the repo (code, migrations `V1..V35`, tests,
  console, CLI, SDKs, these CI workflows) and reconciled against the AS-BUILT
  notes in `../qeet-files/qeet-pay/research/`.
- **REMAINING** items are the real GA gate. Note the shape: a payments platform is
  not "done" when the code is written — it is done when it is **licensed,
  attested, load-proven, and operable under incident**. Track these as launch
  criteria, owned outside engineering where noted.
