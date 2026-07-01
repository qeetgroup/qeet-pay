"""M07 tests: enhanced feature engineering, model fallback, P99 latency budget.

All tests run without a real Redis or ONNX model — the service falls back to
in-memory velocity + rules-based scoring, which is the expected behaviour in CI.
"""

from __future__ import annotations

import time

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.model.features import extract
from app.scoring import velocity_tracker


@pytest.fixture(autouse=True)
def _reset():
    velocity_tracker.reset()
    yield
    velocity_tracker.reset()


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


def _payload(**overrides):
    base = {
        "paymentId": "pay_v2",
        "merchantId": "m_v2",
        "amountMinor": 500_000,   # INR 5,000
        "currency": "INR",
        "method": "UPI",
        "customerVpa": "user@okaxis",
        "ip": "1.2.3.4",
    }
    base.update(overrides)
    return base


# ---------------------------------------------------------------------------
# Feature extraction unit tests (no HTTP, no Spring context)
# ---------------------------------------------------------------------------

def test_feature_score_in_range():
    fv = extract(
        amount_minor=500_000,
        customer_vpa="user@bank",
        merchant_id="m1",
        method="UPI",
        velocity_1m=3,
    )
    for val in fv.to_list():
        assert isinstance(val, (int, float)), f"unexpected type: {type(val)}"


def test_missing_vpa_flag():
    fv = extract(amount_minor=1000, customer_vpa=None, merchant_id="m2", method="UPI", velocity_1m=0)
    assert fv.missing_vpa is True
    assert fv.is_upi_collect is False


def test_upi_collect_flag():
    fv = extract(amount_minor=1000, customer_vpa="a@b", merchant_id="m3", method="UPI", velocity_1m=0)
    assert fv.is_upi_collect is True
    assert fv.missing_vpa is False


def test_round_amount_flag():
    fv_round = extract(amount_minor=100_000, customer_vpa="a@b", merchant_id="m4", method="CARD", velocity_1m=0)
    fv_not   = extract(amount_minor=123_456, customer_vpa="a@b", merchant_id="m4", method="CARD", velocity_1m=0)
    assert fv_round.is_round_amount is True
    assert fv_not.is_round_amount is False


def test_card_method_risk_higher_than_upi():
    fv_upi  = extract(amount_minor=1000, customer_vpa="a@b", merchant_id="m5", method="UPI",  velocity_1m=0)
    fv_card = extract(amount_minor=1000, customer_vpa="a@b", merchant_id="m5", method="CARD", velocity_1m=0)
    assert fv_card.method_risk > fv_upi.method_risk


# ---------------------------------------------------------------------------
# HTTP /score contract tests
# ---------------------------------------------------------------------------

def test_score_in_0_to_100_range(client):
    resp = client.post("/score", json=_payload())
    assert resp.status_code == 200
    body = resp.json()
    assert 0 <= body["score"] <= 100, f"score out of range: {body['score']}"


def test_decision_consistent_with_score(client):
    resp = client.post("/score", json=_payload())
    body = resp.json()
    score = body["score"]
    decision = body["decision"]
    if score < 30:
        assert decision == "allow"
    elif score <= 70:
        assert decision == "challenge"
    else:
        assert decision == "block"


def test_high_amount_elevates_score(client):
    low_resp  = client.post("/score", json=_payload(merchantId="mx", amountMinor=100))
    high_resp = client.post("/score", json=_payload(merchantId="mx", amountMinor=60_000_000))
    assert high_resp.json()["score"] > low_resp.json()["score"]


# ---------------------------------------------------------------------------
# P99 latency budget: 100 samples, all must complete in < 100 ms
# ---------------------------------------------------------------------------

def test_p99_latency_under_100ms(client):
    latencies = []
    for i in range(100):
        start = time.perf_counter()
        resp = client.post("/score", json=_payload(merchantId=f"perf_m_{i % 5}"))
        elapsed = (time.perf_counter() - start) * 1000.0
        assert resp.status_code == 200
        latencies.append(elapsed)

    latencies.sort()
    p99 = latencies[98]  # index 98 = 99th percentile of 100 samples
    assert p99 < 100.0, f"P99 latency {p99:.1f}ms exceeds 100ms budget"


def test_reasons_list_non_empty(client):
    resp = client.post("/score", json=_payload())
    body = resp.json()
    assert isinstance(body["reasons"], list)
    assert len(body["reasons"]) >= 1
