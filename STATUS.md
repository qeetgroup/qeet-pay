# Qeet Pay — Implementation Status

**PRD ⇄ codebase reconciliation.** What is actually built, what is simulated, and what is not started
yet — mapped against the PRD's 20 modules, AI spec, compliance obligations, and 3-phase roadmap.

- **As of:** 2026-07-20 · backend `0.0.1-SNAPSHOT`
- **Sources:** [PRD](../qeet-files/qeet-pay/Product_Requirement_Document.md) · [TAD](../qeet-files/qeet-pay/Technical_Architecture_Document.md) · [GAP-ANALYSIS](../qeet-files/qeet-pay/research/GAP-ANALYSIS.md) · code under [src/](src/), [fraud-svc/](fraud-svc/), [cli/](cli/), [apps/](apps/)
- **Companion:** [docs/PRODUCTION-READINESS.md](docs/PRODUCTION-READINESS.md) — the external/regulatory/ops GA gate.

> ⚠️ **Working-tree state (2026-07-20).** A large expansion (below) is **uncommitted** — it exists as
> working-tree changes for review, not in git history. It **compiles green**, passes **Spring-Modulith
> boundary verification**, and **boots end-to-end** (all 50 migrations apply in order on a fresh DB, all
> beans wire, all JPA mappings validate). Live third-party adapters are **gated off by default** (no creds
> in-repo); all "AI" runs through the AI-gateway on **deterministic offline stand-ins** (no trained models
> in-repo). Two known issues: **qeetrix 1.0.1** consumer-bump is blocked (broken registry publish — apps
> stay on `@qeetrix/ui ^0.4.0`); the **webhook-endpoint seed** returns a pre-existing 400 (JSON binding
> bug in the existing `webhooks` module).

---

## Snapshot by the numbers

| Dimension | Was (pre-build) | Now |
| --- | --- | --- |
| Backend modules (Spring Modulith) | 30 | **38** |
| Backend `.java` (main) | ~380 | **612** |
| Test files | 62 | **94** |
| Flyway migrations | V1–V36 | **V1–V56 (50 files)** |
| REST endpoints | 169 | **262** |
| Console feature routes | 38 | **47** |
| Live external adapters (gated) | 1 (Razorpay, flag) | **8** |

Verification this build: `./gradlew compileJava compileTestJava` ✅ · `ModularityTests` ✅ · fresh-DB boot to `readyz` 200 with all V1–V56 applied ✅. (Full Testcontainers suite still runs on CI/Linux only.)

---

## What changed in the 2026-07-20 build

Grouped by the backlog tiers. Everything is additive and follows existing conventions (RLS per module,
paise money, append-only ledger, transactional outbox, sandbox-adapter pattern, tests per module).

### Tier A — live integrations (real adapter code, flag-gated; sandbox stays the default)
- **Razorpay webhook end-to-end** — `payments/RazorpayWebhookService`: parses + processes `payment.captured/authorized/failed`, `refund.*`, `settlement.processed`; merchant resolved from signed order notes; idempotent on `x-razorpay-event-id` (V37); reuses `PaymentService` ledger logic. **(now real, not a stub)**
- **NATS outbox relay hardened** — `platform/outbox`: real JetStream publish + stream bootstrap, at-least-once drain, backoff, publish-outside-tx. Still `qeetpay.nats.enabled=false` by default.
- **Live IRP e-invoicing + GSTN filing** — `gst/LiveIrpAdapter`, `filing/LiveGstnFilingAdapter` (RestClient, `@ConditionalOnProperty` `qeetpay.irp` / `qeetpay.gstn`).
- **Live KYB** — `kyb/LiveKybAdapter` (`qeetpay.kyb`); PAN/GSTIN/penny-drop.
- **Live payout rail + FX** — `payouts/RazorpayXPayoutProvider` (`qeetpay.payouts`), `crossborder/LiveFxRateAdapter` (`qeetpay.fx`).

### Tier B — code-gap modules
- **AML/CFT** (`aml/`, V43) — sanctions/PEP screening, transaction-monitoring rules, mule detection, STR (FIU-IND) generation.
- **AI gateway** (`ai/`, V44) — the §6.4 safety substrate: PII masking, human-review gating, deterministic fallback, decision audit + outbox; `SandboxAiModelClient` offline stand-in. **All AI features route through this.**
- **Payroll** (`payroll/`, V45) — batch salary disbursement via the payouts rail + statutory PF/ESI/PT/TDS + salary slips (M02.5/M18.4, Qeet People).
- **Unified Compliance Dashboard** (`analytics/ComplianceHealthService`, `GET /v1/analytics/compliance-health`) — recon/nodal + GSTR filing + fraud posture + KYB on one screen (M12.6).
- **Accounting integrations** (`accounting/`, V47) — Tally XML export, Zoho Books (gated), generic webhook (M11.3).
- **TDS/TCS returns** (`tds/`, V48) — Form 24Q/26Q/27EQ preparation + NSDL FVU-style export + filing (M06.4).
- **KYB expansion** (`kyb/`, V40) — V-CIP video-KYC, customer-KYC (Aadhaar/PAN), UBO registry (M19.2–19.4).

### Tier C — net-new modules
- **Offline & Rural** (`offline/`, V49) — Bharat QR, UPI Lite (₹500/₹2000 limits), UPI 123Pay, POS/Tap-to-Pay (M15).
- **Outbound cross-border** (`crossborder/`, V50) — SWIFT vendor payments + LRS tracking + 2.5% TCS (M14.4).
- **ONDC** (`ondc/`, V51) — multi-party settlement + per-seller TCS/GST (M13.4).
- **MCP + agentic mandates** (`agentic/`, V52) — per-agent spend authority (caps/expiry/allowlists, idempotent) + MCP tool manifest (M17.5/N1).

### Tier D — intelligence (via AI-gateway + offline stand-ins; deterministic fallback; money-decisions stay deterministic)
- **Fraud ML + XAI** (`fraud/` V53 + Python `fraud-svc`) — ONNX/Redis path wired in (baseline model), SHAP-style explanations on `/score`, persisted fraud decisions + audit. Python tests pass (29).
- **AI dunning + orchestration ML + compliance-aware routing** (`dunning/`, `payments/`) — advisory recommendations + explanations (M04/M07).
- **GST classification + Regulatory-Change Radar** (`gst/` V55) — HSN/SAC suggestion + rate-change impact forecast (M05/M06.5).
- **Copilots** (`copilot/` V56) — Treasury (M12.5), reconciliation (N7), NLQ — conversational, cited figures, confidence.

### Frontend
- **Premium console + checkout redesign** — design system (KPI tiles, section cards, chart-kit, elevated tables), real dashboard, restyled ~35 routes, Stripe-tier checkout states.
- **New-module console screens** — 15 routes created/restyled/extended (Compliance dashboard, Copilot chat, Fraud XAI, KYC/V-CIP/UBO tabs, cross-border in/out, TDS returns, GST-AI, agentic, accounting, offline, ondc, aml, ai audit, payroll) + 12 nav entries. `pnpm typecheck` + `build` pass.

---

## Executive scorecard — the 20 PRD modules (updated)

| # | Module | Status now | Note |
| --- | --- | --- | --- |
| 01 | Payment Acceptance | 🟢 Built (+ live rail gated) | Razorpay webhook real; links/checkout/VAs/mandates real; live acquiring flag-gated, sandbox default |
| 02 | Payouts & Disbursements | 🟢 Built (+ live rail gated) | maker-checker + bulk + refunds; **RazorpayX live adapter** gated; **payroll module added** |
| 03 | Subscription Billing | ✅ Built | full lifecycle + pricing + metering |
| 04 | AI Dunning & Recovery | 🟢 Built | classifier + **AI retry strategy via gateway** + explainable (offline stand-in) |
| 05 | GST Invoicing & e-Invoicing | 🟢 Built (+ live IRP gated) | calc/notes/multi-GSTIN real; **LiveIrpAdapter** gated; **HSN classifier added** |
| 06 | GST Filing & Tax | 🟢 Built (+ live GSTN gated) | GSTR prep/ITC/TDS real; **LiveGstnFilingAdapter** gated; **24Q/26Q/27EQ returns + Reg-Change Radar added** |
| 07 | Payment Orchestration | 🟢 Built | scorecard routing + **AI ranking + compliance-aware routing** (advisory) |
| 08 | Fraud & Risk | 🟢 Built | **ML path wired + SHAP XAI + persisted decisions**; gateway-audited (baseline model, no trained model) |
| 09 | WhatsApp-Native | 🟡 Partial | dispatch via outbox→Notify (NATS relay hardened but off); no inbound bot |
| 10 | Embedded Finance | ✅ Built (sim rails) | lending/BNPL/cards/insurance/escrow (internal sims) |
| 11 | Revenue Recognition | 🟢 Built | IndAS-115 engine + **accounting integrations (Tally/Zoho/webhook) added** |
| 12 | Analytics & Cash-Flow | 🟢 Built | metrics + forecast + **unified compliance dashboard (12.6)** + **Treasury copilot (12.5)** |
| 13 | Marketplace & Split | 🟢 Built | splits + GST/TCS/TDS attribution + **ONDC (13.4) added** |
| 14 | Cross-Border | 🟢 Built (+ live FX gated) | inbound export real; **outbound/LRS/TCS (14.4) added**; LiveFxRateAdapter gated |
| 15 | Offline & Rural | 🟢 Built (sim rails) | **new module**: Bharat QR / UPI Lite / 123Pay / POS |
| 16 | Carbon & ESG | ✅ Built | per-txn carbon + offsets |
| 17 | Developer Experience & SDKs | 🟢 Built | API/SDKs/CLI/sandbox + **MCP manifest + agentic mandates (17.5) added** |
| 18 | Qeet Ecosystem Integrations | 🟡 Partial | Qeet ID OIDC real; **payroll integration added**; NATS relay hardened (off by default) |
| 19 | KYC/KYB & Onboarding | 🟢 Built (+ live gated) | **V-CIP + customer-KYC + UBO added**; LiveKybAdapter gated |
| 20 | Novel / Extra (N1–N10) | 🟡 Partial | N1 agentic mandates, N7 recon copilot, N8-style fraud audit added; others not started |

Legend: ✅ complete · 🟢 built this build (logic real; external rail gated / AI on offline stand-in) · 🟡 partial · ⬜ not started.

---

## AI features (PRD §6) — now wired, on offline stand-ins

Every AI surface now routes through **`ai/AiGateway`** (§6.4: PII masking, human-in-loop on money-affecting
types, decision audit + outbox, fail-closed to a deterministic path). The model backend is
`SandboxAiModelClient` — a deterministic offline stand-in — because **no trained models/data ship in-repo**;
swapping a real `liveAiModelClient` bean lights up the real models with the same guardrails.

Fraud scoring+XAI, AI dunning, orchestration ranking + compliance-aware routing, cash-flow forecasting, GST
classification, Regulatory-Change Radar, and the Treasury/reconciliation/NLQ copilots are all built against
this substrate. The Python `fraud-svc` additionally has the ONNX inference + Redis velocity path wired
(behind `FRAUD_MODEL_PATH`/`REDIS_URL`) with a documented baseline linear model.

---

## What's genuinely still remaining

- **Live credentials + real endpoints** — the 8 gated adapters (IRP/GSTN/KYB/RazorpayX/FX/Razorpay-acquire)
  need real keys + integration testing; they are code-complete but unproven against live services.
- **Trained models** — replace the offline stand-ins / baseline fraud model with real trained models
  (fraud GBM+SHAP, dunning, orchestration, GST classification) behind the existing gateway seam.
- **WhatsApp inbound bot / WhatsApp Pay** (M09.2/9.3); enabling the NATS relay in prod for end-to-end Notify/Logs.
- **Accounting: SAP**; **e-invoice/GSTR live exercised end-to-end**; **AML STR filed to real FIU**.
- **The external / regulatory / ops GA gate** — RBI PA licence, nodal, KMS/BYOK, pentest/PCI-DSS, SOC 2, DR,
  load testing — tracked in [docs/PRODUCTION-READINESS.md](docs/PRODUCTION-READINESS.md).
- **Housekeeping**: fix the pre-existing `webhooks` endpoint JSON-binding 400; regenerate the committed
  `api/openapi/v1.yaml` + client SDKs + `qp` CLI for the 93 new endpoints; resolve the qeetrix 1.0.1 publish.

---

## Local run (validated this build)

```bash
docker compose -f docker-compose.yml up -d postgres      # Postgres :5201
SPRING_PROFILES_ACTIVE=dev java -jar build/libs/qeet-pay-0.0.1-SNAPSHOT.jar   # API :4201 (or: make dev)
cli/qp sandbox seed                                       # demo merchant → qp_test_… key
pnpm -C apps/console dev                                  # console :3201
```
