# api/openapi/

The Qeet Pay REST contract, split into **seven self-contained, bounded-context
OpenAPI 3.1 documents**. There is **no monolithic `v1.yaml`** — these files are the
published contract, generated from the springdoc `GroupedOpenApi` groups declared in
[`platform/openapi/OpenApiConfig.java`](../../src/main/java/com/qeetgroup/qeetpay/platform/openapi/OpenApiConfig.java).

| File | Context | Surface |
|---|---|---|
| [payments.yaml](payments.yaml) | acceptance | payments, payment-links, checkout, mandates, offline & rural, virtual accounts |
| [payouts.yaml](payouts.yaml) | disbursement | payouts, payout-batches, payroll, treasury, ledger, settlements / reconciliation |
| [billing.yaml](billing.yaml) | billing | plans, subscriptions, invoices, usage metering, dunning, revenue recognition |
| [tax.yaml](tax.yaml) | tax & GST | GST invoices / e-invoicing (IRN) / returns, input-tax-credit, TDS/TCS |
| [commerce.yaml](commerce.yaml) | commerce & embedded finance | marketplace, ONDC, cross-border, ESG, lending, BNPL, cards, insurance, escrow |
| [risk.yaml](risk.yaml) | risk & compliance | fraud, AML/CFT, KYB, customer-KYC / V-CIP / UBO |
| [platform.yaml](platform.yaml) | platform | merchants, webhooks, analytics, AI gateway, agentic mandates, copilots, messaging, accounting |

Each file carries its own `components` (the transitive `$ref` closure of the schemas
its paths use) plus the shared `X-Api-Key` security scheme, so every file validates
standalone. The union across the seven files is the complete `/v1` surface (226 paths).

## Regenerating

These are **generated** — never hand-edit the YAML. Add or change routes by editing the
controllers (springdoc reads their annotations); which group a path lands in is decided
by the `pathsToMatch` filters in `OpenApiConfig.java`. Then, with the backend running
(`make dev`, port 4201):

```bash
for g in payments payouts billing tax commerce risk platform; do
  curl -s "http://localhost:4201/v3/api-docs.yaml/$g" -o "api/openapi/$g.yaml"
done
```

The full merged spec is still served at `/v3/api-docs` (JSON) and `/v3/api-docs.yaml`
(YAML) for tooling that wants one document; Swagger UI (`/swagger-ui.html`) shows a
group dropdown.
