# qeet-pay — CLAUDE.md

**qeet-pay** — Qeet Pay, the India-first payments / billing / financial-infrastructure platform
(PRD + TAD in [../qeet-files/qeet-pay/](../qeet-files/qeet-pay/)). **Status: Phase-1 build in progress** —
the modular-monolith skeleton + multi-tenant RLS backbone + **double-entry ledger core** are in place,
and the Phase-1 domain modules are landing one at a time (payments/payouts/billing/mandates/dunning/
gst/kyb/fraud/webhooks/analytics + settlements & reconciliation are built; see the module list and
TAD §17 for remaining scope — SDKs/CLI/sandbox, IRN/GSTR filing and AI/ML are Phase 2+).

**Stack:** Java 21 + Spring Boot 3.4 modular monolith (per `../qeet-files/TECH-STACK-GUIDE.md` —
chosen over Go because GST/INR math needs `BigDecimal` and the NPCI/GSTN/Razorpay SDKs are
JVM-only; sibling **qeet-people** is the closest reference, in Kotlin). Postgres 17 via Spring Data
JPA. The Python/FastAPI fraud service + Next.js console come later. Product docs live in **qeet-docs**
(`docs.qeet.in/pay`), not here.

## Ports ("pay" band — avoids qeet-id 5001, qeet-people 4101/5101/3101)

| Service | Port |
| --- | --- |
| backend API | **4201** |
| Postgres (docker-compose) | **5201** |
| NATS (outbox relay target) | **4222** |
| console (reserved, Phase 1) | **3201** |

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
  `payments/` (acceptance + provider routing/orchestration), `payouts/` (single + **bulk** batches
  via API/CSV, maker-checker at create→approve; `BulkPayoutService`/`PayoutBatch`), `billing/`,
  `mandates/`, `dunning/`, `gst/`, `kyb/`, `fraud/` (+ Python `fraud-svc/`), `webhooks/`, `analytics/`.

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
