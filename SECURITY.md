# Security Policy — Qeet Pay

Qeet Pay is a payments, billing, and financial-infrastructure platform. It moves
money and processes regulated financial data (GST, KYB, settlement, ledger), so
we treat security reports with high priority. Thank you for helping keep it and
its merchants safe.

---

## Supported versions

Qeet Pay is a continuously-deployed platform, not a versioned library. Security
fixes land on the actively-developed lines only:

| Branch / version         | Supported                          |
| ------------------------ | ---------------------------------- |
| `main` (release line)    | Yes — fixes shipped here first     |
| `develop` (integration)  | Yes                                |
| Tagged pre-1.0 snapshots | No — upgrade to the latest `main`  |

The current artifact version is `0.0.1-SNAPSHOT` (pre-GA). Until GA, assume only
the tip of `main`/`develop` receives fixes.

---

## Reporting a vulnerability

**Do not open a public GitHub issue or PR for a security problem**, and do not
disclose it publicly until we have shipped a fix and agreed on disclosure.

Report privately through either channel:

1. **GitHub Private Vulnerability Reporting** — open a draft advisory via the
   repo's **Security → Report a vulnerability** tab. Preferred; keeps the report,
   fix, and advisory in one place.
2. **Email** — `security@qeet.in` (org-wide security contact for Qeet Group).
   Encrypt sensitive details with our PGP key if you have it. Please include:
   - a description and impact assessment,
   - reproduction steps or a proof-of-concept,
   - affected component (backend module, console, `qp` CLI, an SDK), commit/branch,
   - any logs, request IDs (we emit `X-Request-Id`), or merchant/tenant context
     needed to reproduce — **redact real secrets and real cardholder/PII data**.

**Our commitment:**

| Stage                | Target                                              |
| -------------------- | --------------------------------------------------- |
| Acknowledgement      | within 2 business days                              |
| Triage & severity    | within 5 business days (CVSS-based)                 |
| Fix / mitigation     | Critical: days · High: ~2 weeks · Medium/Low: next cycle |
| Coordinated disclosure | after a fix ships; credit given unless you prefer otherwise |

Please act in good faith: no data exfiltration beyond what proves the issue, no
service degradation, no access to other merchants' data, and no social
engineering of Qeet staff or users.

---

## Security model

Qeet Pay's defenses are layered; the most important ones are enforced at the
**database** layer, not in application code.

### Multi-tenancy — Postgres Row-Level Security (TAD §6.1, ADR-001)

Every tenant-owned table is scoped by `merchant_id` and protected by **RLS**
policies keyed off the per-request GUC `app.current_merchant_id`. The value is
set by `MerchantFilter` (from the API key, the Qeet ID JWT claim, or the dev-only
`X-Merchant-Id` header) and pushed into the DB session by `MerchantScope`.
Cross-tenant reads/writes are architecturally impossible — no application bug can
leak across merchants.

> **RLS only holds under a least-privilege DB role.** A superuser or table owner
> bypasses RLS. Production **must** connect as the `NOSUPERUSER` `qeet_pay_app`
> role (created in migration `V2`). The dev docker-compose superuser is
> intentionally *not* tenant-scoped; `RlsIsolationTest` proves the policy under
> the restricted role.

### Authentication & authorization (TAD §2.2, §12.1)

- Qeet Pay is a **Qeet ID OIDC relying party** — it validates Qeet ID JWTs and
  maps `roles`; it does not roll its own auth.
- Programmatic `/v1` calls authenticate with API keys via the `X-Api-Key` header:
  `qp_live_…` (production) and `qp_test_…` (sandbox). Live and test keys are
  strictly separated.
- Two Spring Security chains by profile: `prod`/`staging` **require** auth;
  `dev`/`test` are permissive and accept `X-Merchant-Id` so the skeleton boots
  without a live Qeet ID. **Never run a non-dev deployment on the `dev`/`test`
  profile.**

### Money integrity — minor units + append-only ledger (TAD §6.2, §7.1, ADR-002)

- **Money is integer minor units** (`amount_minor` BIGINT, e.g. paise) plus an
  ISO currency code. Conversions use `BigDecimal` (HALF_UP). Floats are never
  used for money — this removes an entire class of rounding/precision bugs.
- The **ledger is append-only, double-entry**: `LedgerService.postEntry` enforces
  Σdebits = Σcredits, and a deferred DB trigger rejects unbalanced entries at
  COMMIT. There is **no UPDATE/DELETE on `ledger.*`** — the app role is granted
  SELECT/INSERT only; corrections are offsetting entries. This gives a tamper-
  evident financial record and a nodal-balance invariant (`settlement` never goes
  negative).
- **Idempotency** on money-moving endpoints prevents duplicate captures/payouts.

### Data protection & residency — DPDP posture (TAD §6.4, §14, §15.2)

- **Data residency:** primary region is AWS `ap-south-1` (Mumbai) for RBI/DPDP
  India residency. Multi-region is Phase 3.
- **Encryption:** TLS 1.3 in transit; AES-256 at rest. Per-merchant DEKs and
  PAN/Aadhaar keys are held in **AWS KMS**.
- **DPDP Act 2023:** consent management, data-principal rights (access/erasure),
  breach notification, and a documented retention/classification policy (§6.4).
- **Cards / PAN:** Phase 1 uses a licensed PG's vault + RBI CoFT network
  tokenization (Visa VTS / Mastercard MDES) — **zero raw PANs** stored by Qeet
  Pay. Own PCI-DSS Level 1 vault + QSA audit is Phase 2 (§15.5).
- **Audit trail:** financial and compliance events are emitted via the
  transactional outbox to **Qeet Logs** (append-only, identity-aware). Note the
  NATS relay ships **disabled by default** (§9.5) — end-to-end audit is
  outbox-guaranteed but relay-pending until enabled in an environment.

---

## Secure configuration

- **Never commit secrets.** `.env`, `.env.local` are gitignored; `.env.example`
  contains only placeholders and safe defaults. CI runs `dependency-review` and
  CodeQL, but secret hygiene is on every contributor.
- **Production secrets** come from **AWS Secrets Manager**, and key material from
  **AWS KMS** (per-merchant DEKs, PAN/Aadhaar keys). Rotate on a schedule and on
  suspected compromise.
- **Run as `qeet_pay_app` (NOSUPERUSER) in prod** — see the RLS note above.
- **Use the `prod`/`staging` profile in any deployed environment.** The
  permissive dev/test chains and the `X-Merchant-Id` header are for local dev
  only.
- **Provider keys default to sandbox.** Live adapters (Razorpay, GSTN/IRP, AA,
  FX, KYB) are `*_ENABLED=false` until real credentials are supplied; keep them
  off outside production.
- **Least-privilege CI:** workflows pin `permissions:` to the minimum
  (`contents: read`, plus `security-events: write` only for CodeQL).

Related: [`CONTRIBUTING.md`](CONTRIBUTING.md), [`.env.example`](.env.example),
[`docs/PRODUCTION-READINESS.md`](docs/PRODUCTION-READINESS.md), and TAD §15
(Security, Privacy & Compliance Architecture) in
`../qeet-files/qeet-pay/Technical_Architecture_Document.md`.
