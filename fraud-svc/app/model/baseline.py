"""Documented baseline linear fraud model + deterministic SHAP-style explainer.

This module gives the fraud service an *interpretable* view of every score
(TAD Module 08.1/8.4 — "real-time ML scoring + Explainable AI"). It exists so
the explainability path runs **end-to-end offline**, with no trained model file
shipped: the weights below are the documented baseline.

Two responsibilities:

1. **Explainability (always on).** For a linear model the additive contribution
   of feature *i* to the risk log-odds is exactly ``weight_i * value_i`` — i.e.
   the exact SHAP value against a zero baseline. That makes the "top contributing
   features" list fully deterministic and reproducible, which is what an
   RBI / model-governance audit expects (a fixed, inspectable reason for every
   decision). :func:`explain` renders those contributions as plain-English
   reason strings.

2. **A fully offline linear scorer** (:func:`linear_score`) usable when neither
   a trained ONNX model (``FRAUD_MODEL_PATH``) nor the heuristic rules are the
   desired path. The default non-ONNX path in :mod:`app.scoring` stays the
   transparent rules scorer; this linear scorer is provided so the ML-style path
   is exercisable without a model artefact.

The weights are on the *risk-points* scale (a raw sum, clamped to 0–100). They
are hand-calibrated defaults, versioned as ``MODEL_VERSION`` — a trained model
would replace both the ONNX artefact and these baseline weights.
"""

from __future__ import annotations

import math
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from app.model.features import FeatureVector

MODEL_VERSION = "baseline-linear-v1"

# Feature order — MUST match FeatureVector.to_list() / predict._FEATURE_NAMES.
FEATURE_NAMES = [
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

# Documented baseline weights (risk points per unit of the normalised feature).
# Positive = increases fraud risk; negative = protective (reduces risk).
WEIGHTS: dict[str, float] = {
    "amount_log": 1.5,        # compressed amount; up to ~18 pts for very large txns
    "amount_zscore": 3.0,     # amount vs a soft cap; up to +30 for outsized amounts
    "velocity_1m": 2.0,       # per in-window payment from the same merchant
    "method_risk": 25.0,      # method base risk (0–1) scaled to 0–25 pts
    "is_upi_collect": -10.0,  # verified UPI collect w/ known VPA lowers risk
    "missing_vpa": 40.0,      # strong signal: UPI txn with no VPA
    "is_round_amount": 8.0,   # round amounts over-represented in test/abuse flows
    "hour_of_day_norm": 5.0,  # mild diurnal component
    "ip_risk": 30.0,          # geo/IP reputation (0–1) scaled to 0–30 pts
}

# Intercept: baseline log-odds shift so an ordinary txn sits comfortably in "allow".
BIAS = -8.0


def _values(fv: "FeatureVector") -> dict[str, float]:
    """Feature name -> normalised value, aligned with :data:`FEATURE_NAMES`."""
    return dict(zip(FEATURE_NAMES, fv.to_list()))


def contributions(fv: "FeatureVector") -> list[tuple[str, float, float]]:
    """Return ``(feature, contribution_points, value)`` for every feature.

    ``contribution_points = WEIGHTS[feature] * value`` — the exact per-feature
    additive attribution of the linear model. Deterministic for a given vector.
    """
    vals = _values(fv)
    out: list[tuple[str, float, float]] = []
    for name in FEATURE_NAMES:
        value = vals[name]
        out.append((name, WEIGHTS[name] * value, value))
    return out


def linear_score(fv: "FeatureVector") -> int:
    """A fully offline linear risk score in ``0..100`` (no model artefact)."""
    raw = BIAS + sum(c for _, c, _ in contributions(fv))
    return max(0, min(100, int(round(raw))))


def _reason(name: str, value: float, contribution: float, *, amount_minor: int, method: str | None) -> str:
    """Plain-English, audit-friendly explanation for one feature contribution."""
    inr = amount_minor / 100.0
    direction = "increased" if contribution >= 0 else "reduced"
    method_label = (method or "unknown").upper()
    if name == "missing_vpa":
        return "No customer VPA was supplied on a UPI-style payment (increased risk)"
    if name == "is_upi_collect":
        return "Verified UPI collect with a known VPA (reduced risk)"
    if name == "velocity_1m":
        return f"{int(value)} payment(s) from this merchant in the last 60s (increased risk)"
    if name == "method_risk":
        return f"Payment method '{method_label}' carries a base risk of {value:.2f} ({direction} risk)"
    if name == "amount_log":
        return f"Transaction amount of INR {inr:,.2f} ({direction} risk)"
    if name == "amount_zscore":
        return f"Amount is high relative to the platform baseline ({direction} risk)"
    if name == "is_round_amount":
        return "Round-number amount, over-represented in test/abuse patterns (increased risk)"
    if name == "ip_risk":
        return f"Originating IP reputation score {value:.2f} ({direction} risk)"
    if name == "hour_of_day_norm":
        return f"Time-of-day component ({direction} risk)"
    return f"{name} {direction} risk"


def explain(
    fv: "FeatureVector",
    *,
    amount_minor: int,
    method: str | None,
    top_k: int = 5,
    min_abs: float = 0.5,
) -> list[dict]:
    """Deterministic SHAP-style top-contributing-features explanation.

    Returns a list of ``{feature, contribution, value, reason}`` dicts, ordered
    by absolute contribution (most influential first), keeping at most ``top_k``
    features whose absolute contribution is at least ``min_abs`` points.
    """
    ranked = sorted(contributions(fv), key=lambda c: abs(c[1]), reverse=True)
    out: list[dict] = []
    for name, contribution, value in ranked:
        if abs(contribution) < min_abs:
            continue
        out.append(
            {
                "feature": name,
                "contribution": round(contribution, 2),
                "value": round(value, 4),
                "reason": _reason(name, value, contribution, amount_minor=amount_minor, method=method),
            }
        )
        if len(out) >= top_k:
            break
    if not out:
        out.append(
            {
                "feature": "none",
                "contribution": 0.0,
                "value": 0.0,
                "reason": "No material risk contributions",
            }
        )
    return out
