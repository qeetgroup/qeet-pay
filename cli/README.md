# qp — Qeet Pay CLI

A dependency-free command-line interface for the [Qeet Pay](../) REST API, built
with the Go standard library only (`net/http`, `flag`, `encoding/json`,
`text/tabwriter`). It talks to the same `/v1/…` HTTP endpoints as the Java
backend.

> The qeet-pay backend is **Java/Spring Boot**, so this CLI is its own
> **standalone Go module** (`github.com/qeetgroup/qeet-pay/cli`). It builds
> offline with no external dependencies.

## Install / build

From this directory:

```bash
make build              # -> bin/qp
# or
go build -o bin/qp .
```

Put it on your `PATH` if you like:

```bash
install bin/qp /usr/local/bin/
# or
make install            # go install .
```

## Configuration

The CLI authenticates with a merchant API key sent in the `X-Api-Key` header
(`qp_live_…` / `qp_test_…`) and targets a base URL. In the permissive dev/test
profile the backend also accepts an `X-Merchant-Id` header, so `--merchant`
alone is enough for local work. Flags win over environment.

| Setting     | Environment variable  | Flag         | Default                 |
| ----------- | --------------------- | ------------ | ----------------------- |
| API key     | `QEETPAY_API_KEY`     | `--api-key`  | _(one of key/merchant)_ |
| Base URL    | `QEETPAY_BASE_URL`    | `--base-url` | `http://localhost:4201` |
| Merchant id | `QEETPAY_MERCHANT_ID` | `--merchant` | _(dev/sandbox only)_    |
| Output      | —                     | `--json`     | table                   |

Global flags are accepted by **every** subcommand and must come **after** it:

```bash
qp payments list --json
qp payments create --amount 150000 --method UPI --merchant <merchant-id>
export QEETPAY_API_KEY="qp_test_..."
export QEETPAY_BASE_URL="http://localhost:4201"
```

**Money is always integer minor units (paise).** `--amount 150000` = ₹1,500.00.

By default each command prints a friendly aligned table; nested objects/arrays in
a response (e.g. a card and its transactions) render as labelled sub-tables. Pass
`--json` for the raw, pretty-printed response. A non-2xx response decodes the
RFC-7807 `application/problem+json` body to stderr and exits non-zero.

## Quick start — one-command sandbox

```bash
qp sandbox seed
```

This provisions a fresh demo merchant (`POST /v1/merchants`) and, using its
returned **test API key**, seeds representative data across domains — a couple of
payments (created + captured, one partially refunded), a payment link, a GST
invoice (created + paid), a plan + subscription, a working-capital loan offer
(+ accepted), a virtual card, an escrow hold, KYB submissions and a webhook
endpoint — then prints a summary and the API key to keep using the sandbox:

```
Sandbox seeded.

  Merchant : demo-1a2b3c4d (5f3c…)
  Base URL : http://localhost:4201
  API key  : qp_test_…
  (test key — shown once; export it as $QEETPAY_API_KEY to keep using this sandbox)

  DOMAIN     RESOURCE          STATUS   ID / DETAIL
  merchants  merchant          created  5f3c…  demo-1a2b3c4d
  payments   payment #1        created  9b2e…  ₹1,500.00 UPI
  …
```

The backend runs its sandbox adapters by default when no live keys are set, so
this exercises the real sandbox end-to-end. Options: `--slug`, `--name`.

## Commands

Run `qp --help` for the full list, or `qp <cmd> <subcmd> -h` for a subcommand's
flags.

### Payments

```bash
qp payments create  --amount 150000 --method UPI --description "Order #1"
qp payments get     --id <payment-id>
qp payments capture --id <payment-id>          # idempotent (random Idempotency-Key)
qp payments refund  --id <payment-id> --amount 50000 --reason "return"
qp payments refunds --id <payment-id>
```

Methods: `UPI | CARD | NET_BANKING | WALLET`. Add `--simulate-failure` to make the
sandbox provider decline authorization.

### Payment links

```bash
qp links create --title "Invoice #42" --amount 250000 --reference inv-42
qp links create --title "Donation"     # omit --amount for an open (payer-entered) amount
qp links list
qp links get    --id <link-id>
qp links pay    --code <public-code> --method UPI --amount 250000
qp links cancel --id <link-id>
```

### Payouts

```bash
qp payouts create  --amount 500000 --rail IMPS --destination user@upi
qp payouts approve --id <payout-id>            # maker-checker disburse (idempotent)
qp payouts reject  --id <payout-id>
qp payouts get     --id <payout-id>
```

Rails: `UPI | IMPS | NEFT | RTGS`.

### Billing (plans / subscriptions / invoices)

```bash
qp billing plan-create          --code pro --name "Pro" --amount 99900 --interval MONTH
qp billing subscription-create  --plan <plan-id> --customer cust_123
qp billing subscription-get     --id <subscription-id>
qp billing subscription-pause   --id <subscription-id>
qp billing subscription-resume  --id <subscription-id>
qp billing subscription-cancel  --id <subscription-id> --at-period-end
qp billing invoice-get          --id <invoice-id>
qp billing invoice-pay          --id <invoice-id>
```

### GST (invoices + IRN e-invoicing + returns)

```bash
qp gst invoice-create \
  --supplier-gstin 27ABCDE1234F1Z5 --buyer-gstin 29PQRSX6789K2Z1 --place-of-supply 29 \
  --lines '[{"description":"Consulting","hsnSac":"9983","quantity":1,"unitPriceMinor":500000,"gstRate":18}]'
qp gst invoice-pay     --id <invoice-id>
qp gst irn-generate    --invoice <invoice-id>     # register at the IRP → IRN + signed QR
qp gst irn-get         --invoice <invoice-id>
qp gst irn-cancel      --invoice <invoice-id> --reason "data entry error"
qp gst return-prepare  --type GSTR1 --period 2026-06
qp gst return-list
qp gst return-get      --id <return-id>
qp gst return-file     --id <return-id>           # file to GSTN → ARN
```

### Ledger (accounts / balance / post)

```bash
qp ledger accounts
qp ledger balance --id <account-id>
qp ledger post --description "manual adjustment" \
  --lines '[{"accountId":"<uuid>","direction":"DEBIT","amountMinor":100000},
            {"accountId":"<uuid>","direction":"CREDIT","amountMinor":100000}]'
```

The ledger is append-only, double-entry: Σdebits must equal Σcredits.

### Analytics

```bash
qp analytics tpv          --from 2026-06-01T00:00:00Z --to 2026-07-01T00:00:00Z --granularity DAY
qp analytics mrr          --from 2026-06-01T00:00:00Z --to 2026-07-01T00:00:00Z
qp analytics arr
qp analytics success-rate --from 2026-06-01T00:00:00Z --to 2026-07-01T00:00:00Z --method UPI
qp analytics cash-flow    --horizon-days 30 --window-days 30
```

### Lending (embedded working capital)

```bash
qp lending offer-request --avg-monthly-volume 5000000
qp lending offer-list
qp lending offer-accept  --offer <offer-id>       # disburses the advance
qp lending loan-list
qp lending loan-get      --loan <loan-id>
qp lending repay         --loan <loan-id> --settlement-amount 100000
```

### Cards (virtual expense / wallet cards)

```bash
qp cards issue    --holder emp_9 --type EXPENSE
qp cards list
qp cards get      --id <card-id>
qp cards load     --id <card-id> --amount 200000
qp cards spend    --id <card-id> --amount 50000 --description "SaaS subscription"
qp cards freeze   --id <card-id>
qp cards unfreeze --id <card-id>
qp cards close    --id <card-id>
```

### Escrow

```bash
qp escrow hold    --buyer buyer_1 --seller seller_1 --amount 300000
qp escrow list
qp escrow get     --id <escrow-id>
qp escrow release --id <escrow-id> --amount 200000 --note "goods delivered"
qp escrow refund  --id <escrow-id> --amount 100000 --note "dispute"
```

### KYB

```bash
qp kyb pan    --pan ABCDE1234F
qp kyb gstin  --gstin 27ABCDE1234F1Z5
qp kyb bank   --account 000123456789 --ifsc HDFC0000123
qp kyb status
```

### Webhooks

```bash
qp webhooks register   --url https://example.com/hook --events payment.captured,payout.processed --secret whsec_...
qp webhooks list
qp webhooks deliveries --id <endpoint-id>
qp webhooks disable    --id <endpoint-id>
```

## Exit codes

| Code | Meaning                                               |
| ---- | ----------------------------------------------------- |
| `0`  | Success                                               |
| `1`  | Request error (network, non-2xx response, bad input)  |
| `2`  | Usage error (unknown command / missing subcommand)    |

## Development

```bash
make build       # go build -o bin/qp .
make test        # go vet ./... + go test -race ./...
make vet
make fmt         # go fmt ./...
make fmt-check   # fail if gofmt would change anything
```
