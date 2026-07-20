"""Tests for the fraud-scoring service contract and rules."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.scoring import velocity_tracker


@pytest.fixture(autouse=True)
def _reset_velocity():
    """Isolate each test from the shared in-memory velocity counters."""
    velocity_tracker.reset()
    yield
    velocity_tracker.reset()


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


def _payload(**overrides):
    base = {
        "paymentId": "pay_1",
        "merchantId": "merch_1",
        "amountMinor": 499900,  # INR 4,999.00
        "currency": "INR",
        "method": "UPI",
        "customerVpa": "user@bank",
        "ip": "1.2.3.4",
    }
    base.update(overrides)
    return base


def test_healthz(client: TestClient):
    resp = client.get("/healthz")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_low_risk_allow(client: TestClient):
    resp = client.post("/score", json=_payload())
    assert resp.status_code == 200
    body = resp.json()
    assert body["decision"] == "allow"
    assert body["score"] < 30
    assert body["reasons"] == ["no risk signals triggered"]
    assert isinstance(body["latencyMs"], float)
    assert body["latencyMs"] >= 0


def test_high_amount_block(client: TestClient):
    # > INR 5,00,000 stacks both high-amount signals -> score 65, plus this is a
    # standalone request so no velocity; verify a very large amount blocks.
    resp = client.post(
        "/score",
        json=_payload(merchantId="merch_block", amountMinor=60_000_000),
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["score"] > 70
    assert body["decision"] == "block"
    assert any("very-high threshold" in r for r in body["reasons"])


def test_missing_vpa_penalty_challenges(client: TestClient):
    resp = client.post(
        "/score",
        json=_payload(merchantId="merch_novpa", customerVpa=None),
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["decision"] == "challenge"
    assert 30 <= body["score"] <= 70
    assert any("customerVpa" in r for r in body["reasons"])


def test_blank_vpa_penalty(client: TestClient):
    resp = client.post(
        "/score",
        json=_payload(merchantId="merch_blankvpa", customerVpa="   "),
    )
    assert resp.status_code == 200
    body = resp.json()
    assert any("customerVpa" in r for r in body["reasons"])
    assert body["score"] >= 40


def test_velocity_escalation(client: TestClient):
    merchant = "merch_velocity"
    # First 5 requests in-window carry no velocity risk -> allow.
    for _ in range(5):
        resp = client.post("/score", json=_payload(merchantId=merchant))
        assert resp.json()["decision"] == "allow"

    # 6th+ requests accumulate velocity risk and eventually escalate the decision
    # away from "allow".
    escalated = False
    last_body = None
    for _ in range(6):
        last_body = client.post("/score", json=_payload(merchantId=merchant)).json()
        if last_body["decision"] != "allow":
            escalated = True
            break
    assert escalated, f"velocity never escalated: {last_body}"
    assert any("velocity" in r for r in last_body["reasons"])


def test_invalid_request_rejected(client: TestClient):
    # amountMinor must be >= 0; missing required field -> 422.
    resp = client.post("/score", json={"paymentId": "p", "merchantId": "m"})
    assert resp.status_code == 422


def test_score_response_carries_explanation_and_model(client: TestClient):
    # Explainable AI: every /score response includes the SHAP-style explanation
    # and the scoring model, in addition to the legacy `reasons` list.
    body = client.post("/score", json=_payload()).json()
    assert body["model"] == "rules"  # default path (no ONNX model in CI)
    assert isinstance(body["explanation"], list) and body["explanation"]
    top = body["explanation"][0]
    assert {"feature", "contribution", "value", "reason"} <= set(top.keys())
    # Legacy reasons contract is preserved for a clean, low-risk payment.
    assert body["reasons"] == ["no risk signals triggered"]
