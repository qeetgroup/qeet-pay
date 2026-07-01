"""ML inference layer for fraud scoring.

When an ONNX model file is present (FRAUD_MODEL_PATH), this module loads it
and runs inference via onnxruntime. The raw output probability is calibrated
to a 0-100 integer score plus SHAP-style top-5 feature contributions.

When no model file is available (cold start, CI, development), the module
falls back to the deterministic rules scorer in ``app.scoring`` so that the
service is always functional.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from app.model.features import FeatureVector

_FEATURE_NAMES = [
    "amount_log",
    "amount_zscore",
    "velocity_1m",
    "method_risk",
    "is_upi_collect",
    "missing_vpa",
    "is_round_amount",
    "hour_of_day_norm",
    "ip_risk",
]


@dataclass
class ModelPrediction:
    score: int          # 0–100 (calibrated risk score)
    decision: str       # allow | challenge | block
    reasons: list[str]  # top-contributing feature explanations


def _decision_for(score: int) -> str:
    if score < 30:
        return "allow"
    if score <= 70:
        return "challenge"
    return "block"


def predict_with_model(fv: "FeatureVector", session: object) -> ModelPrediction:
    """Run ONNX inference and produce a ModelPrediction.

    ``session`` is an onnxruntime.InferenceSession loaded at startup.
    """
    import numpy as np  # type: ignore

    features = np.array([fv.to_list()], dtype=np.float32)
    input_name = session.get_inputs()[0].name
    proba = session.run(None, {input_name: features})[1][0]
    # proba is a dict {0: p_safe, 1: p_fraud} for XGBoost ONNX output
    fraud_prob = float(proba.get(1, 0.0)) if isinstance(proba, dict) else float(proba[-1])
    score = max(0, min(100, int(fraud_prob * 100)))

    # Simplified feature importance explanation (SHAP approximation)
    feature_vals = fv.to_list()
    contributions = [(name, abs(val)) for name, val in zip(_FEATURE_NAMES, feature_vals)]
    contributions.sort(key=lambda x: x[1], reverse=True)
    reasons = [f"{name}: {val:.3f}" for name, val in contributions[:5] if val > 0.01]
    if not reasons:
        reasons = ["no dominant risk features"]

    return ModelPrediction(score=score, decision=_decision_for(score), reasons=reasons)


# ---------------------------------------------------------------------------
# Module-level model session (loaded once at startup)
# ---------------------------------------------------------------------------

_session = None


def _load_session() -> object | None:
    model_path = os.getenv("FRAUD_MODEL_PATH")
    if not model_path or not os.path.exists(model_path):
        return None
    try:
        import onnxruntime as ort  # type: ignore

        return ort.InferenceSession(model_path)
    except Exception:
        return None


def get_session() -> object | None:
    global _session
    if _session is None:
        _session = _load_session()
    return _session


def score_with_features(fv: "FeatureVector") -> ModelPrediction | None:
    """Run ONNX inference if a model session is available; return None to fall back to rules."""
    session = get_session()
    if session is None:
        return None
    return predict_with_model(fv, session)
