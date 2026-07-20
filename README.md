# qeet-pay

**Qeet Pay** is the unified payments, billing, and financial infrastructure platform for the Qeet Group and for external multi-tenant SaaS companies, marketplaces, and enterprises. One API, one dashboard, one integration — for payment acceptance, payouts, subscription billing, GST-compliant invoicing, payment orchestration, fraud detection, embedded finance, and financial analytics.

> Status: **Phase-1 complete; Phase-2 well underway + operator console shipped.** The modular-monolith skeleton, multi-tenant RLS backbone, and double-entry ledger core are in place; payments, payouts, billing, mandates, dunning, GST, KYB, fraud, webhooks, and analytics modules plus settlements/reconciliation are built. Phase-2 backend now spans 20+ bounded contexts: revenue recognition (IndAS 115), marketplace split settlements (GST/TCS/TDS), GST return filing (GSTR-1/3B), IRN e-invoicing, AI-dunning UPI-failure classification, smart provider orchestration, embedded lending (AA-underwritten working-capital advances), virtual accounts (auto-reconciled B2B collection), cash-flow forecasting, digital escrow, cross-border collection (export invoices + FX + FIRA), WhatsApp-native messaging, payment links, BNPL, virtual cards, embedded insurance, TDS/TCS tracking, ITC/GSTR-2B reconciliation, and per-transaction ESG/carbon. The **operator console** ([`apps/console/`](apps/console/), TanStack Start + `@qeetrix/ui`, port 3201) covers every domain above. SDKs (in `../qeet-sdks/`), CLI, and sandbox remain (see `CLAUDE.md` and the TAD §17 for exact scope).

---

## Why Qeet Pay exists

Every Qeet product needs to collect money, disburse money, and generate GST invoices. Without Qeet Pay, each product independently wires Razorpay (payments) + Chargebee (billing) + ClearTax (GST) — tripling vendor overhead, splitting financial data across systems, and rebuilding reconciliation for every product.

Externally: Indian SaaS companies, marketplaces, and enterprises are doing the same thing. Four vendors, four dashboards, four reconciliations. No platform today combines UPI + NACH + Cards + Subscription Billing + GST e-invoicing with IRN + GSTR-1 auto-filing + AI dunning + Marketplace GST split + WhatsApp-native invoicing + Embedded lending via Account Aggregator — in a single API.

Qeet Pay is that platform.

---

## What it covers

### Payment Acceptance
| Method | Details |
|---|---|
| **UPI** | Standard collect/intent, QR, UPI 2.0 (one-time mandate, invoice in inbox), UPI AutoPay (recurring), UPI Circle (delegated), RuPay Credit Card via UPI, Credit Line on UPI |
| **Cards** | Visa, Mastercard, RuPay, Amex; 3DS 2.0 (frictionless + challenge); RBI CoFT network tokenization; Card EMI |
| **Net Banking** | 72+ Indian banks |
| **Wallets** | Paytm, PhonePe, Mobikwik, Freecharge, Amazon Pay |
| **NACH** | E-NACH (Aadhaar OTP) + physical NACH for high-value B2B recurring |
| **Virtual Accounts** | Unique IFSC+account per customer for auto-reconciled B2B collection |
| **UPI Lite** | Offline ₹500 payments, no PIN (Phase 2) |
| **UPI 123Pay** | Feature phone IVR payments for 250M+ rural users (Phase 2) |
| **International** | Visa/Mastercard/Amex + SWIFT/ACH/SEPA (Phase 2, PA-CB license) |

### Payouts and Disbursements
- UPI (instant), IMPS (instant), NEFT, RTGS
- Bulk payouts via API or CSV upload
- Maker-checker approval workflow for bulk disbursals
- Salary disbursement integration with Qeet People (EWA, PF, ESI, PT)
- Refunds: full, partial, instant (IMPS) or standard

### Subscription Billing Engine
- Pricing models: flat-rate, per-unit, tiered, volume, stairstep, usage-based, committed + overage, hybrid
- Full subscription lifecycle: create, upgrade, downgrade, pause, cancel, reactivate
- Usage metering: high-throughput event ingestion, real-time customer usage widget
- AI-powered dunning: UPI failure classification → channel + timing adaptation → 3x recovery vs. static retry
- Proration, free trials, freemium, addons, one-time charges

### GST-Compliant Invoicing
- Auto-generated GST invoices with all mandatory CGST Act fields
- CGST/SGST/IGST auto-calculation (inter-state vs. intrastate, place of supply rules)
- HSN/SAC code database (10,000+ codes) with AI classification
- **E-invoicing (IRN):** IRP API integration → IRN generation → QR code embedded on PDF
- **Combined QR (exclusive):** One QR on invoice = IRP-validated e-invoice data + UPI payment intent — buyer scans once, verifies and pays
- GSTR-1 auto-filing via GSTN API (no manual filing)
- GSTR-2B reconciliation (ITC verification)
- TDS/TCS calculation and tracking (Sections 194J/C/H, 194-O, Section 52)
- Multi-GSTIN management (multiple Indian states, one account)
- Credit notes, debit notes, export invoices (LUT/Bond, zero-rated)
- Delivery: email + WhatsApp + self-serve portal

### Payment Orchestration
- Route each payment to the optimal provider by cost + authorization rate + provider health
- Rule-based routing (Phase 1): by payment method, BIN, amount, geography
- ML-based routing (Phase 2): +5–11 percentage point auth rate lift over single-provider
- Automatic failover: provider 5xx → instant retry on secondary provider, transparent to customer
- Supported providers: Razorpay, Cashfree, PayU, PhonePe, Stripe India (PA-CB), NPCI direct (Phase 2)

### WhatsApp-Native Payments and Billing
- Invoice delivery via WhatsApp: PDF + summary + combined e-invoice/UPI QR
- WhatsApp subscription management bot: PAUSE, CANCEL, INVOICE, USAGE, PAY commands
- WhatsApp dunning sequences (via Qeet Notify)
- WhatsApp Pay (UPI inside WhatsApp) support via Meta BSP partnership
- WhatsApp payout confirmation to recipients (salary, EWA, vendor)

### Fraud Detection and Risk
- Real-time scoring in < 100ms: 100+ signals (device, IP, velocity, behavioral biometrics, UPI handle history)
- India-specific fraud patterns: UPI collect-request scams, screen monitoring, fake payment screenshots
- **Explainable AI (XAI):** Top-5 SHAP-value signals per blocked transaction in plain English — first India-native explainable fraud system
- Automated chargeback response: evidence package compiled and submitted automatically
- RBI-compliant dispute resolution timeline enforcement

### Embedded Finance
- **Embedded lending:** AA-powered instant underwriting using bank statement + GST data; working capital advances up to ₹25L; repaid as % of daily settlement
- **BNPL at checkout:** Credit Line on UPI (August 2025 RBI guidelines); installment selector at checkout
- **Virtual cards:** Employee expense cards + customer wallet cards (Phase 2)
- **Embedded insurance:** Payment protection, fraud insurance, subscription interruption cover (Phase 3)
- **Digital escrow:** Conditional release on delivery/milestone confirmation

### Analytics (Native — No BI Tool Needed)
- MRR waterfall: New / Expansion / Contraction / Churned / Reactivation MRR
- ARR, churn rate (logo + revenue), NRR, LTV, ARPU, cohort retention
- Dunning funnel: failures → recovery attempts → recovered; channel effectiveness
- AI cash flow forecast: 30-day projected balance with working capital recommendations
- Real-time TPV, revenue by method/product/geography, refund rate

### India Compliance (Built In, Not Bolted On)
| Compliance | Coverage |
|---|---|
| KYC/KYB | PAN, GSTIN, bank, Aadhaar OTP, V-CIP, beneficial owner |
| RBI CoFT | Network tokenization (Visa VTS / Mastercard MDES) — zero raw PANs |
| DPDP Act 2023 | Consent management, data residency (ap-south-1), erasure, breach notification |
| FEMA | Export invoice tagging, FIRA capture, eBRC assistance |
| AML / CFT | Transaction monitoring, STRs to FIU-IND, PEP/sanctions screening |
| PCI-DSS Level 1 | Phase 2 (own vault); covered by licensed PG in Phase 1 |

---

## What makes it different

### No single competitor covers all of this

| Feature | Razorpay | Chargebee | Stripe | **Qeet Pay** |
|---|---|---|---|---|
| UPI + NACH | ✓ | ✗ | ✗ | ✓ |
| Subscription billing | Partial | ✓ | ✓ | ✓ |
| GST e-invoicing (IRN) | ✗ | ✗ | ✗ | ✓ |
| GSTR-1 auto-filing | ✗ | ✗ | ✗ | ✓ |
| Multi-GSTIN billing | ✗ | ✗ | ✗ | ✓ |
| AI dunning (UPI-aware) | ✗ | Partial | ✗ | ✓ |
| WhatsApp-native invoicing | ✗ | ✗ | ✗ | ✓ |
| Payment orchestration | ✗ | ✗ | ✗ | ✓ |
| Marketplace GST split | ✗ | ✗ | ✗ | ✓ |
| Embedded lending (AA) | Partial | ✗ | ✗ | ✓ |
| Combined e-invoice + UPI QR | ✗ | ✗ | ✗ | ✓ |
| Carbon footprint tracking | ✗ | ✗ | Partial | ✓ |
| Native subscription analytics | ✗ | Partial | ✗ | ✓ |
| Qeet ID subscriber federation | — | — | — | ✓ |

---

## Market

- India payments market: **₹409 lakh crore ($409B) in 2025** — projected $958B by 2030 (18.5% CAGR)
- UPI: **21.63B transactions in December 2025** alone; world's largest real-time payment system (IMF)
- UPI AutoPay mandates: **1.27 billion** (November 2025) — 10x growth from January 2024
- India fintech market: **$51.2B in 2025** → $186B by 2035
- E-invoicing mandate: **₹2 crore turnover threshold** from October 2025 — millions of SMBs now require IRN
- Global subscription billing: **$8.47B in 2025** → $37.36B by 2035
- Payment orchestration: **$1.97B in 2024** → $23.9B by 2034 (25.8% CAGR)

---

## Build path (regulatory)

| Phase | Model | What's enabled |
|---|---|---|
| **Phase 1 (now)** | Sub-merchant under Razorpay/Cashfree PA license | Payment collection, billing, GST invoicing, payouts — everything except direct acquiring |
| **Phase 2** | Own RBI PA license (apply Month 6, receive ~Month 18–24) | Direct acquiring, own nodal account, T+1 settlement, PA-CB for international |
| **Phase 3** | PA-CB + NBFC co-lending + PPI | Cross-border payments, own embedded lending, stored-value wallet |

Most Indian fintech startups (including early Razorpay, Cashfree) launched under the sub-merchant model before obtaining their own PA license. This is the standard path.

---

## Pricing

| Tier | Fee | TPV |
|---|---|---|
| Free | ₹0 | ₹10 lakh/month |
| Starter | ₹2,999/month | ₹1 crore/month |
| Growth | ₹9,999/month | ₹10 crore/month |
| Scale | ₹29,999/month | ₹100 crore/month |
| Enterprise | Custom | Unlimited |

**Processing fees (transparent pass-through):**
- UPI: 0.15% (Qeet Pay) + 0% MDR (government mandate) = 0.15%
- Cards: ~1.75% total (0.25% Qeet Pay + ~1.5% interchange)
- NACH: ₹8/debit
- Net banking: ₹5/transaction

**Zero MDR on UPI** per government mandate. Zero per-invoice fee on IRN/e-invoicing (included in Growth+).

---

## Technical stack

Per the Qeet Group [`TECH-STACK-GUIDE.md`](../qeet-files/TECH-STACK-GUIDE.md):

| Layer | Technology |
|---|---|
| Financial engine | Java 21 + Spring Boot 3.x (JVM `BigDecimal` for INR/GST math; Java-native NPCI/GSTN/Razorpay SDKs; Spring Security for PCI DSS) |
| Database | PostgreSQL 17 + Spring Data JPA / Hibernate (RLS for tenant isolation; double-entry ledger) |
| Message bus | NATS JetStream (durable at-least-once; payment event ordering) |
| Cache | Redis 7 Cluster (rate limiting, fraud velocity features, session state) |
| Analytics | TimescaleDB (PostgreSQL extension for time-series MRR/TPV aggregations) |
| Fraud scoring | Python/FastAPI (XGBoost/LightGBM; < 100ms fraud scoring) |
| Frontend (Dashboard) | Next.js 16 + React 19 + Tailwind v4 |
| UI Components | @qeetrix/ui |
| Auth | Qeet ID OIDC |
| Observability | qeet-logs (OTLP) + Prometheus + Grafana |
| Cloud | AWS ap-south-1 (Mumbai) — India data residency |
| CDN | CloudFront (checkout SDK delivery + WAF) |
| Secrets | AWS KMS + AWS Secrets Manager |
| Migrations | Flyway (immutable SQL files) |

---

## Roadmap

### Phase 1 — Foundation (Q3–Q4 2026)
Payments (UPI, cards, net banking, wallets), payouts (UPI/IMPS/NEFT bulk), subscription billing (flat-rate, per-unit, tiered), GST invoicing (no IRN), basic dunning, Go + TS + Python + Java SDKs, `qp` CLI, Qeet ecosystem integration (Qeet ID, Qeet Notify, qeet-logs, Qeet People payroll).

### Phase 2 — Depth and Intelligence (Q1–Q2 2027)
UPI 2.0, NACH, virtual accounts, AI dunning (UPI failure classification), payment orchestration ML, e-invoicing (IRN via IRP), GSTR-1 auto-filing, multi-GSTIN, usage-based billing, WhatsApp invoicing + bot, marketplace split payments + GST attribution, AA-powered embedded lending, cash flow forecasting, revenue recognition.

### Phase 3 — Enterprise and Global (Q3–Q4 2027)
Own PA license, PA-CB for international, UPI Lite + 123Pay, virtual card issuing, embedded insurance, digital escrow, AI natural language queries, tax optimizer, SOC 2 Type II, multi-region, ONDC integration, carbon footprint tracking + ESG reporting.

---

## Repository structure

A single Spring Boot modular monolith (Spring Modulith). The foundation (platform/, ledger/,
merchants/, reconciliation/) and the Phase-1 domain modules (payments, payouts, billing, mandates,
dunning, gst, kyb, fraud, webhooks, analytics) are built as sibling packages; remaining Phase 2+ scope
(SDKs/CLI/sandbox, IRN/GSTR filing, AI/ML) lands incrementally — see `CLAUDE.md` for current status.

```
qeet-pay/
├── build.gradle.kts · settings.gradle.kts · gradlew      # Gradle (Kotlin DSL), Java 21
├── Dockerfile · docker-compose.yml · Makefile · .env.example
├── src/main/java/com/qeetgroup/qeetpay/
│   ├── QeetPayApplication.java                            # @Modulithic entrypoint
│   ├── platform/                                          # OPEN module — shared infra
│   │   ├── config/        # AppProperties (qeetpay.*)
│   │   ├── security/      # OIDC resource server + API-key filter
│   │   ├── tenancy/       # MerchantContext + MerchantFilter + MerchantScope (RLS)
│   │   ├── idempotency/   # Idempotency-Key store
│   │   ├── outbox/        # transactional outbox + NATS relay
│   │   └── api/           # health, /v1/me, request-id, RFC-7807 errors
│   ├── ledger/                                            # double-entry ledger (crown jewel)
│   └── merchants/                                         # tenant aggregate + onboarding
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/      # Flyway: V1 platform+RLS, V2 ledger (never edit applied)
├── src/test/java/...      # Testcontainers: ContextLoads, Modularity, RlsIsolation,
│                          #   LedgerBalance, Idempotency
└── .github/workflows/ci.yml
```

Full PRD + TAD live in the workspace hub: [`../qeet-files/qeet-pay/`](../qeet-files/qeet-pay/).

---

## Design partners

Qeet Pay is in the design-partner phase. If you are a CTO, CFO, or engineering lead at an Indian SaaS company, marketplace, or enterprise dealing with any of these problems — GST invoicing pain, UPI AutoPay failures and involuntary churn, multi-vendor payment fragmentation, or marketplace settlement complexity — reach out at [partnerships@qeet.in](mailto:partnerships@qeet.in).

Design partners get:
- Direct access to the founding team for requirements and feedback
- White-glove integration support and migration from Razorpay/Chargebee
- GSTR-1 auto-filing setup end-to-end
- Locked pricing for 2 years
- Influence over Phase 1 scope

---

## Full documentation

See [Product_Requirement_Document.md](reqs/Product_Requirement_Document.md) — 15 sections covering every feature in detail: competitive analysis with specific pricing, India regulatory landscape (RBI PA license, UPI, NACH, GST, DPDP, FEMA, AA framework), feature specifications for all 19 feature areas, technical architecture, full API endpoint list, regulatory roadmap, pricing model, 3-phase development roadmap, and 12 open questions.

---

## Workspace context

`qeet-pay` is part of the [QG workspace](../CLAUDE.md). Each project has its own `.git` and toolchain.

| Project | Relationship to Qeet Pay |
|---|---|
| [qeet-id/](../qeet-servers/qeet-id-server/) | Identity — merchant auth, customer identity federation, RBAC |
| [qeet-notify/](../qeet-notify/) | Notifications — payment receipts, dunning, WhatsApp invoicing |
| [qeet-logs/](../qeet-logs/) | Logging — immutable financial audit trail |
| [qeet-people/](../qeet-people/) | HCM — salary disbursement, EWA, statutory payments |
| [qeet-group/](../qeet-websites/qeet-group/) | Marketing site — Qeet Pay will be listed when launched |
| [qeetrix/](../qeetrix/) | Design system — dashboard UI and checkout SDK components |
