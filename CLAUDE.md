# qeet-pay — CLAUDE.md

**qeet-pay** — Qeet Pay, the India-first payments / billing / financial-infrastructure platform
(PRD + TAD in [../qeet-files/qeet-pay/](../qeet-files/qeet-pay/)). **Status: Phase-1 complete; Phase-2
backend landing** — the modular-monolith skeleton + multi-tenant RLS backbone + **double-entry ledger
core** are in place; all Phase-1 domain modules are built (payments/payouts/billing/mandates/dunning/
gst/kyb/fraud/webhooks/analytics + settlements & reconciliation), and Phase-2 backend modules are now
landing one at a time — `revrec/` (IndAS 115), `marketplace/` (split settlements + TCS/TDS), `filing/`
(GSTR-1/3B), `lending/` (AA working-capital advances), `virtualaccounts/` (auto-reconciled B2B
collection), `escrow/` (conditional hold/release/refund), `crossborder/` (export invoices + FX + FIRA),
`messaging/` (WhatsApp-native dispatch), `paymentlinks/` (shareable links → real capture), `bnpl/`
(checkout installments), `cards/` (virtual/expense/wallet cards), `insurance/` (embedded cover),
`tds/` (TDS/TCS records + certificates), `itc/` (ITC / GSTR-2B reconciliation), `esg/` (per-txn carbon
+ offsets), plus IRN e-invoicing (in `gst/`), AI-dunning failure classification (in `dunning/`), smart
provider orchestration/scorecards (in `payments/`), and cash-flow forecasting (in `analytics/`). The
**operator console** ([apps/console/](apps/console/), port 3201), the **client SDKs**
([`../qeet-sdks/qeet-pay-{node,go,react}`](../qeet-sdks/)), the **`qp` CLI** ([cli/](cli/), Go), and the
**developer sandbox** (backend sandbox adapters are the default when no live keys are set; `qp sandbox
seed` populates a demo merchant) are all built. See the module list and TAD §17.

**Stack:** Java 21 + Spring Boot 3.4 modular monolith (per `../qeet-files/TECH-STACK-GUIDE.md` —
chosen over Go because GST/INR math needs `BigDecimal` and the NPCI/GSTN/Razorpay SDKs are
JVM-only; sibling **qeet-people** is the closest reference, in Kotlin). Postgres 17 via Spring Data
JPA. The Python/FastAPI fraud service is separate. The **operator console** is built in
[apps/console/](apps/console/) on the qeet-consistent web stack (TanStack Start + Vite + React 19 +
`@qeetrix/ui` + Tailwind v4 — matching the sibling `apps/console` in qeet-id/notify/people/logs; **not**
Next.js despite older notes). Product docs live in **qeet-docs** (`docs.qeet.in/pay`), not here.

## Ports ("pay" band — avoids qeet-id 5001, qeet-people 4101/5101/3101)

| Service | Port |
| --- | --- |
| backend API | **4201** |
| Postgres (docker-compose) | **5201** |
| NATS (outbox relay target) | **4222** |
| console ([apps/console/](apps/console/), `pnpm dev`) | **3201** |
| checkout ([apps/checkout/](apps/checkout/), `pnpm dev`) | **3200** |
| website ([apps/website/](apps/website/), `pnpm dev`) | **3202** |

## Commands

```bash
make db-up                 # Postgres (+NATS/Redis) via docker compose on :5201
cp .env.example .env       # dev defaults work out of the box
make dev                   # run on :4201 (dev profile — boots without Qeet ID)
make build                 # bootJar
make test                  # gradle test (needs a Docker engine for Testcontainers)
make kill                  # free a stuck :4201

# single test:
./gradlew test --tests 'com.qeetgroup.qeetpay.platform.tenancy.RlsIsolationTest'
```

## Architecture

Single Gradle module, group `com.qeetgroup`, root package `com.qeetgroup.qeetpay`, entrypoint
[QeetPayApplication.java](src/main/java/com/qeetgroup/qeetpay/QeetPayApplication.java). Modular
monolith via **Spring Modulith** (boundaries verified by `ModularityTests`); modules are the direct
sub-packages, declared with `package-info.java` `@ApplicationModule`:

- **`platform/`** (OPEN, shared infra — no domain logic): `config`, `security` (OIDC resource server
  + API-key filter), `tenancy` (`MerchantContext` + `MerchantFilter` + `MerchantScope` RLS),
  `idempotency`, `outbox` (explicit transactional outbox + NATS relay), `api` (health, `/v1/me`,
  request-id, RFC-7807 errors).
- **`ledger/`** — the crown jewel: append-only **double-entry** `accounts` / `journal_entries` /
  `journal_lines`; `LedgerService.postEntry` enforces Σdebits = Σcredits.
- **`merchants/`** — the tenant aggregate; onboarding mints an API key + seeds the chart of accounts
  (`settlement`, `bank`, `fees`, `revenue`, `liability`, `tax_payable`, `deferred_revenue`).
- **`reconciliation/`** — settlements + reconciliation (TAD §6.2). `SettlementService.ingest` records a
  provider settlement report, posts the money movement (debit `bank` + `fees` / credit `settlement`),
  then `ReconciliationService` matches each line against captured payments and flags discrepancies
  (amount/status/missing/duplicate, batch-total, and the **nodal** check that `settlement` never goes
  negative). Reads payments via `PaymentService.find`; never blocks the posting.
- **domain modules** (each its own package + schema + Flyway migration, same shape as above):
  `payments/` (acceptance + provider routing/orchestration; **Phase-2 smart routing** = per-provider
  `ProviderScorecard`/`ProviderScorer`/`ProviderRoutingService`, health/cost/auth-rate, V23), `payouts/`
  (single + **bulk** batches via API/CSV, maker-checker at create→approve; `BulkPayoutService`/
  `PayoutBatch`), `billing/`, `mandates/`, `dunning/` (+ **Phase-2 AI dunning** = `FailureClassifier`/
  `RetryRecommendation` UPI-failure classification → adaptive `triggerSmart`, V22), `gst/` (+ **Phase-2
  IRN e-invoicing** = `IrpAdapter`/`EInvoiceService`, IRN + signed QR, V20), `kyb/`, `fraud/` (+ Python
  `fraud-svc/`), `webhooks/`, `analytics/`.
- **Phase-2 modules** (new bounded contexts, same shape): `revrec/` — IndAS 115 revenue recognition
  (deferral debit `settlement`/credit `deferred_revenue`, then ratable debit `deferred_revenue`/credit
  `revenue`; `RevenueScheduler`, V18); `marketplace/` — e-commerce-operator split settlements with
  commission + GST + statutory TCS(§52)/TDS(§194-O) attribution, cancel-via-offsetting-entry
  (`SplitCalculator`, V19); `filing/` — GSTR-1/GSTR-3B return prep from `gst/` invoices + GSTN filing
  adapter → ARN (`GstnFilingAdapter`, V21); `lending/` — AA-underwritten working-capital advances
  repaid as a % of daily settlement (disburse debit `settlement`+`fees`/credit on-demand
  `loan_payable`; sweep the reverse; `UnderwritingAdapter`, V24); `virtualaccounts/` — per-customer
  VA mint + inbound-credit auto-reconcile (money-in posting, idempotent on bank UTR, V25); `escrow/` —
  conditional hold (debit `settlement`/credit on-demand `escrow_payable`) → partial release-to-seller
  (`liability`) / refund-to-buyer, append-only events (V26); `crossborder/` — foreign-currency export
  invoices (LUT/FEMA purpose code) settled by an inward remittance FX-converted to INR + FIRA capture,
  posted money-in (`FxRateAdapter`, V27); `messaging/` — WhatsApp/SMS/email templates + rendered
  dispatch emitted to the outbox for qeet-notify (`TemplateRenderer`, V28). Analytics Phase-2:
  `analytics/CashFlowForecastService` — settlement-balance projection from ledger + trailing net TPV
  (no migration; `allowedDependencies={platform,ledger}` respected). External IRP/GSTN/IRN/AA/FX
  backends use the sandbox-adapter pattern (`@ConditionalOnMissingBean`), like `kyb/`.

Multi-tenant by `merchant_id` with **Row-Level Security** keyed off the per-request GUC
`app.current_merchant_id` (set by `MerchantFilter` from the API key / JWT claim / dev `X-Merchant-Id`
header; pushed into the DB session by `MerchantScope`). Migrations are **Flyway** SQL under
[src/main/resources/db/migration](src/main/resources/db/migration) (auto-apply on startup; never edit
an applied migration — add `V{n+1}__…sql`). Events use an **explicit `platform.outbox_event` table**
written in the same transaction; `NatsEventRelay` drains it to `pay.{merchant_id}.events.{type}`
(off by default — `qeetpay.nats.enabled=false`).

**Identity** — OIDC relying party (TAD §2.2): validates Qeet ID JWTs, maps `roles`; programmatic
`/v1` calls authenticate with `qp_live_`/`qp_test_` API keys (`X-Api-Key`). Two security chains by
profile: `prod`/staging require auth; `dev`/`test` are permissive and accept `X-Merchant-Id` so the
skeleton boots without a live Qeet ID.

## Frontends ([apps/](apps/))

Three apps, matching the qeet-id frontend suite (operator console on TanStack; public payer UI + marketing
on Next.js):

**Website** ([apps/website/](apps/website/)) — the marketing site on **port 3202** (**Next.js 16 +
`@qeetrix/ui` + `motion` + `lucide-react` + Tailwind v4**, `(marketing)` route group, mirroring qeet-id's
`apps/website`). Homepage + `/product`, `/pricing`, `/compare` (+ per-competitor), `/about`, `/contact`;
static export, no backend calls; links out to the console, docs, and API portal. `pnpm install && pnpm dev`.


**Checkout** ([apps/checkout/](apps/checkout/)) — the **public, payer-facing** hosted payment page on
**port 3200** (**Next.js 16 + `@qeetrix/ui` + Tailwind v4 + i18next**, mirroring qeet-id's `apps/login`).
`/l/{code}` resolves a payment link and lets a payer pay it with **no merchant API key** — it talks to
the PUBLIC checkout API (`GET /v1/checkout/{code}` + `POST /v1/checkout/{code}/pay`), which resolves
the link's merchant via the no-RLS `paymentlinks.link_public_lookup` routing table (V36; dual-written on
link creation) and returns a leak-proof safe view. `pnpm install && pnpm dev`.

**Console** ([apps/console/](apps/console/)) — operator dashboard on **port 3201**, on the qeet-consistent
stack (**TanStack Start + Vite + React 19 + `@qeetrix/ui` + Tailwind v4**), scaffolded from the sibling
`qeet-notify` console. `pnpm install` then
`pnpm dev` (`pnpm build` / `pnpm typecheck` verify it). Structure: `src/routes/{__root,_app,sign-in}` +
`src/routes/_app/*.tsx` (one file-based route per nav item), `src/config/navigation.tsx` (the nav
registry — routes must match its `url`s), `src/lib/{api,auth,money,list-view,export}` (the `api()`
client speaks `X-Api-Key` to `:4201` and parses RFC-7807; `money.ts` = `formatInr`/`rupeesToMinor`,
**money is paise everywhere**), `src/components/{page-header,data-table/*,app-sidebar,…}` (shared UI).
Covers every backend domain (payments/links/refunds/orchestration, payouts/batches/ledger/recon/revrec,
billing, GST/e-invoice/returns/ITC/TDS, lending/BNPL/cards/insurance/escrow, VAs/cross-border,
marketplace/messaging/ESG, analytics/cash-flow, KYB/webhooks/settings). `src/routeTree.gen.ts` is
generated by the Vite plugin on `dev`/`build` (kept in VCS, like the siblings); cross-route `Link`s use
`to={"/x" as never}`. **Adding a route:** create `src/routes/_app/<slug>.tsx` with
`createFileRoute("/_app/<slug>")` and add it to `navigation.tsx`. Build artifacts (`node_modules`,
`.output`, `.nitro`, `.tanstack`, `dist`) are gitignored via `apps/console/.gitignore`.

## Production & ops

- **API docs** — springdoc OpenAPI 3.1 at `/v3/api-docs` (143 paths, incl. the public `/v1/checkout/**`),
  Swagger UI at `/swagger-ui.html`
  (both public in every security chain; `X-Api-Key` scheme). `platform/openapi/`. `OpenApiDocsTest`
  dumps the spec to `build/openapi/qeet-pay-openapi.json`; the committed source spec lives at
  [api/openapi/v1.yaml](api/openapi/v1.yaml) and is published to the developer portal
  (`../qeet-apis/public/specs/qeet-pay/v1.yaml`, registered in its catalog + `sync-specs.mjs`).
- **Observability** — Micrometer → Prometheus at `/actuator/prometheus` (always on); OTel/OTLP tracing
  to qeet-logs, **off by default** (`QEETPAY_OTEL_ENABLED=false`). `platform/observability/`.
- **Rate limiting** — in-memory token bucket on `/v1/**` (`platform/ratelimit/`), keyed merchant→key→IP,
  RFC-7807 429 + `Retry-After`. **Profile-driven**: off in dev/test, on in `prod`/`staging`. Tunables:
  `QEETPAY_RATELIMIT_CAPACITY` / `_REFILL_PER_SECOND`. Prod chain also sets HSTS/nosniff/frame-deny.
- **Deploy** — hardened non-root [Dockerfile](Dockerfile) (backend) + [apps/console/Dockerfile](apps/console/Dockerfile);
  full-stack [docker-compose.yml](docker-compose.yml) (postgres+redis+nats+backend+console); prod overlay
  + k8s manifests in [deploy/](deploy/). `docker compose config` validates.
- **CI** — [.github/workflows/](.github/workflows/): backend (Gradle+Testcontainers on Linux — RYUK works,
  no local workaround needed), console (pnpm typecheck/lint/build), cli (go build/vet/test), CodeQL,
  dependency-review. Path-filtered.
- **Test reliability** — the full local suite exhausts Docker Desktop (RYUK-disabled + ~58 per-class
  containers → context-load cascade); `AbstractIntegrationTest` (JVM-singleton reused Postgres) + the
  tuned `test` task (`maxParallelForks=1`, `forkEvery=12`) address it. Every module still passes in
  isolation; CI/Linux runs the full suite green.
- **GA gaps** (external/ops, not code) tracked in [docs/PRODUCTION-READINESS.md](docs/PRODUCTION-READINESS.md):
  RBI PA license, KMS BYOK + rotation, pentest/PCI-DSS, load/DR, SOC 2, DPDP residency attestation.

## Gotchas

- **JDK 21+ required** (Gradle wrapper 9.6.1 runs on JDK 21–25; bytecode targets 21; prod image is
  `eclipse-temurin:21-jre`).
- **Money is integer minor units** (`amount_minor` BIGINT, e.g. paise) + ISO currency; use
  `BigDecimal` (HALF_UP) for conversion. Never floats.
- **The ledger is append-only.** No UPDATE/DELETE on `ledger.*` (corrections = offsetting entries);
  the app DB role is granted SELECT/INSERT only. A deferred trigger also rejects unbalanced entries
  at COMMIT.
- **RLS only enforces under a least-privilege DB role.** A superuser/table-owner bypasses it, so the
  dev docker-compose user (superuser) is NOT scoped — prod must use the `NOSUPERUSER` `qeet_pay_app`
  role (created in migration V2). `RlsIsolationTest` proves the policy under that role.
- **`@ApplicationModule` markers live in `package-info.java`.** Flyway naming is `V{n}__desc.sql`.
- **Local Testcontainers may 400 against a very new Docker Desktop engine** (docker-java's API
  version is outside the engine's supported range). Run tests with:
  `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock DOCKER_API_VERSION=1.43 TESTCONTAINERS_RYUK_DISABLED=true ./gradlew test`
  (the build bridges `DOCKER_API_VERSION` → docker-java's `api.version`). CI/Linux needs none of this.
