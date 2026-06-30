"""qeet-pay fraud-scoring microservice (TAD Module 08).

A Python/FastAPI service the Java payment engine calls synchronously during
payment authorization (target < 100ms P99). Phase 1 ships a deterministic,
rules-based scoring stub; the production model is XGBoost/LightGBM via ONNX.
"""

from __future__ import annotations

import time

from fastapi import FastAPI

from app.models import HealthResponse, ScoreRequest, ScoreResponse
from app.scoring import score_payment

app = FastAPI(
    title="qeet-pay fraud-scoring service",
    version="0.1.0",
    description="Phase-1 rules-based fraud-scoring stub for qeet-pay.",
)


@app.get("/healthz", response_model=HealthResponse)
def healthz() -> HealthResponse:
    return HealthResponse(status="ok")


@app.post("/score", response_model=ScoreResponse)
def score(req: ScoreRequest) -> ScoreResponse:
    start = time.perf_counter()
    result = score_payment(
        amount_minor=req.amountMinor,
        customer_vpa=req.customerVpa,
        merchant_id=req.merchantId,
        method=req.method,
    )
    latency_ms = (time.perf_counter() - start) * 1000.0
    return ScoreResponse(
        score=result.score,
        decision=result.decision,
        reasons=result.reasons,
        latencyMs=round(latency_ms, 3),
    )


if __name__ == "__main__":  # pragma: no cover - manual run convenience
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=8201, reload=True)
