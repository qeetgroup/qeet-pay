# qeet-pay fraud-scoring service (`fraud-svc`)

Python/FastAPI fraud-scoring microservice for **qeet-pay** — TAD **Module 08
(fraud scoring)**.

The Java 21 / Spring Boot payment engine calls this service **synchronously**
during payment authorization with a target of **< 100ms P99**. It returns a
risk score, a decision, and the explainable signals behind the decision.

> **Phase 1 = deterministic rules-based stub.** Dependencies are kept light
> (no ML wheels) so tests run fast and offline. The production model is
> XGBoost/LightGBM served via ONNX — see the `# TODO` in `app/scoring.py`
> (TAD §8.1). The rules stay as the transparent cold-start / fallback scorer.

## Port

| Service | Port |
| --- | --- |
| fraud-svc (this service) | **8201** |
| qeet-pay backend API | 4201 |

## Run

```bash
cd qeet-pay/fraud-svc
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt

# dev server with reload
uvicorn app.main:app --host 0.0.0.0 --port 8201 --reload
# or: python -m app.main
```

### Docker

```bash
docker build -t qeet-pay-fraud-svc .
docker run --rm -p 8201:8201 qeet-pay-fraud-svc
```

## Test

```bash
cd qeet-pay/fraud-svc
. .venv/bin/activate
pytest -q
```

## API

### `GET /healthz`

```json
{ "status": "ok" }
```

### `POST /score`

The contract the payment engine integrates against.

**Request**

```json
{
  "paymentId": "pay_abc123",
  "merchantId": "merch_42",
  "amountMinor": 499900,
  "currency": "INR",
  "method": "UPI",
  "customerVpa": "user@bank",
  "ip": "1.2.3.4"
}
```

| Field | Type | Notes |
| --- | --- | --- |
| `paymentId` | string (required) | Payment attempt id. |
| `merchantId` | string (required) | Merchant id (velocity key). |
| `amountMinor` | int ≥ 0 (required) | Amount in smallest unit (paise for INR). `499900` = ₹4,999.00. |
| `currency` | string, 3 chars (required) | ISO-4217, e.g. `INR`. |
| `method` | string (required) | `UPI`, `CARD`, … |
| `customerVpa` | string or null | UPI VPA, e.g. `user@bank` (UPI only). |
| `ip` | string or null | Client IP. |

Invalid input → HTTP `422`.

**Response**

```json
{
  "score": 0,
  "decision": "allow",
  "reasons": ["no risk signals triggered"],
  "latencyMs": 0.21
}
```

| Field | Type | Notes |
| --- | --- | --- |
| `score` | int `0`–`100` | Higher = riskier. |
| `decision` | `"allow"` \| `"challenge"` \| `"block"` | Mapped from `score`. |
| `reasons` | string[] | Explainable signals that fired (TAD §8.2). |
| `latencyMs` | float | Server-side scoring latency. |

## Scoring rules (Phase 1)

Deterministic signals combined into a 0–100 score:

| Signal | Risk added |
| --- | --- |
| Amount > ₹2,00,000 (`amountMinor` > 20,000,000) | +35 |
| Amount > ₹5,00,000 (`amountMinor` > 50,000,000) | +35 +45 (stacks → block) |
| Missing / blank `customerVpa` | +40 |
| Merchant velocity: > 5 payments / 60s window | +12 per extra request (cap +60) |

**Decision thresholds:** `score < 30` → **allow**, `30 ≤ score ≤ 70` →
**challenge**, `score > 70` → **block**.

> Velocity uses an in-memory sliding-window counter (Phase 1, per-instance).
> Production reads/writes these features from **Redis** so they are shared
> across instances (TAD: "Cache — Fraud velocity features").

## Layout

```
fraud-svc/
├── app/
│   ├── main.py       # FastAPI app: /healthz, /score
│   ├── models.py     # Pydantic request/response models
│   └── scoring.py    # rules-based stub + velocity tracker (XGBoost TODO here)
├── tests/
│   └── test_scoring.py
├── requirements.txt
├── Dockerfile
├── pytest.ini
└── README.md
```
