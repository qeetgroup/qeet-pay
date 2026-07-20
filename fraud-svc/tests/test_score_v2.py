"""M07 tests: enhanced feature engineering, model fallback, P99 latency budget.

All tests run without a real Redis or ONNX model — the service falls back to
in-memory velocity + rules-based scoring, which is the expected behaviour in CI.
"""

from __future__ import annotations

import time

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.model import baseline, predict
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


# ---------------------------------------------------------------------------
# Explainability (SHAP-style top-contributing features) — TAD §8.4
# ---------------------------------------------------------------------------

def test_response_has_explanation_and_model(client):
    body = client.post("/score", json=_payload()).json()
    assert "explanation" in body and isinstance(body["explanation"], list)
    assert len(body["explanation"]) >= 1
    for item in body["explanation"]:
        assert set(item.keys()) == {"feature", "contribution", "value", "reason"}
        assert isinstance(item["reason"], str) and item["reason"]
    # model field surfaces which path scored the payment.
    assert body["model"] in {"rules", "onnx"}


def test_model_is_rules_by_default(client):
    # No FRAUD_MODEL_PATH in CI -> the transparent rules scorer is used.
    body = client.post("/score", json=_payload()).json()
    assert body["model"] == "rules"


def test_explanation_flags_missing_vpa(client):
    body = client.post("/score", json=_payload(merchantId="expl_novpa", customerVpa=None)).json()
    feats = {c["feature"] for c in body["explanation"]}
    assert "missing_vpa" in feats
    missing = next(c for c in body["explanation"] if c["feature"] == "missing_vpa")
    assert missing["contribution"] > 0  # missing VPA increases risk
    assert "VPA" in missing["reason"]


def test_explanation_is_deterministic():
    fv = extract(amount_minor=250_000, customer_vpa=None, merchant_id="d1", method="CARD", velocity_1m=4)
    first = baseline.explain(fv, amount_minor=250_000, method="CARD")
    second = baseline.explain(fv, amount_minor=250_000, method="CARD")
    assert first == second  # same feature vector -> identical explanation


def test_explanation_ranked_by_absolute_contribution():
    fv = extract(amount_minor=60_000_000, customer_vpa=None, merchant_id="d2", method="WALLET", velocity_1m=9)
    expl = baseline.explain(fv, amount_minor=60_000_000, method="WALLET")
    abs_contribs = [abs(c["contribution"]) for c in expl]
    assert abs_contribs == sorted(abs_contribs, reverse=True)


def test_baseline_contribution_signs():
    # is_upi_collect (verified) is protective; missing_vpa increases risk.
    fv_collect = extract(amount_minor=1000, customer_vpa="a@b", merchant_id="s1", method="UPI", velocity_1m=0)
    contribs = dict((n, c) for n, c, _ in baseline.contributions(fv_collect))
    assert contribs["is_upi_collect"] < 0

    fv_novpa = extract(amount_minor=1000, customer_vpa=None, merchant_id="s2", method="UPI", velocity_1m=0)
    contribs2 = dict((n, c) for n, c, _ in baseline.contributions(fv_novpa))
    assert contribs2["missing_vpa"] > 0


def test_linear_score_in_range():
    fv = extract(amount_minor=60_000_000, customer_vpa=None, merchant_id="s3", method="WALLET", velocity_1m=8)
    assert 0 <= baseline.linear_score(fv) <= 100


# ---------------------------------------------------------------------------
# Model-path fallback: FRAUD_MODEL_PATH set but no usable artefact -> rules
# ---------------------------------------------------------------------------

def test_score_with_features_returns_none_without_model():
    # No FRAUD_MODEL_PATH configured -> ONNX path unavailable -> None (rules fallback).
    predict._session = None
    fv = extract(amount_minor=1000, customer_vpa="a@b", merchant_id="mf1", method="UPI", velocity_1m=0)
    assert predict.score_with_features(fv) is None


def test_model_path_missing_file_falls_back(monkeypatch):
    # A configured-but-missing model file must not break scoring: fall back to rules.
    monkeypatch.setenv("FRAUD_MODEL_PATH", "/nonexistent/fraud-model.onnx")
    predict._session = None  # force a reload attempt with the bogus path
    fv = extract(amount_minor=1000, customer_vpa="a@b", merchant_id="mf2", method="UPI", velocity_1m=0)
    assert predict.score_with_features(fv) is None
    predict._session = None  # reset cache for other tests


def test_score_endpoint_falls_back_to_rules_on_missing_model(client, monkeypatch):
    monkeypatch.setenv("FRAUD_MODEL_PATH", "/nonexistent/fraud-model.onnx")
    predict._session = None
    body = client.post("/score", json=_payload(merchantId="mf3")).json()
    assert body["model"] == "rules"
    assert 0 <= body["score"] <= 100
    predict._session = None


def test_model_path_used_when_available(client, monkeypatch):
    # Simulate a loaded ONNX model (no onnxruntime needed): the response reports
    # model == "onnx", uses the model's score, and still carries the explanation.
    from app.model.predict import ModelPrediction

    def fake_predict(fv):
        return ModelPrediction(score=88, decision="block", reasons=["model: high risk"])

    monkeypatch.setattr("app.scoring.score_with_features", fake_predict)
    body = client.post("/score", json=_payload(merchantId="onnx_m")).json()
    assert body["model"] == "onnx"
    assert body["score"] == 88
    assert body["decision"] == "block"
    assert isinstance(body["explanation"], list) and body["explanation"]
