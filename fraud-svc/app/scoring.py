"""Fraud-scoring engine for qeet-pay (TAD Module 08.1/8.4).

The scorer combines three layers, all resolved at request time:

* **Model path (when ``FRAUD_MODEL_PATH`` is set).** A gradient-boosted tree
  exported to ONNX is loaded (:mod:`app.model.predict`) and run over the
  normalised feature vector (:mod:`app.model.features`). Its calibrated 0-100
  probability is the score.
* **Rules path (default / cold-start / CI).** When no ONNX model is present the
  transparent, deterministic rules below produce the score. They combine a
  handful of explainable signals (amount, missing VPA, merchant velocity) and
  return every signal that fired in ``reasons`` (TAD §8.2).
* **Explainability (always on).** Regardless of which path scored the payment,
  a deterministic SHAP-style contribution list is attached via
  :mod:`app.model.baseline` — the RBI-audit-friendly "top contributing features"
  with plain-English reason strings (TAD §8.4 Explainable AI).

Velocity features come from Redis when ``REDIS_URL`` (or the legacy
``QEETPAY_FRAUD_REDIS_URL``) is set — shared across instances — otherwise from
the in-process sliding-window counter below (:class:`VelocityTracker`).
"""

from __future__ import annotations

import threading
import time
from collections import defaultdict, deque
from dataclasses import dataclass, field

from app.model import baseline, features
from app.model.predict import score_with_features

# ---------------------------------------------------------------------------
# Tunable constants (would move to config / feature store in production)
# ---------------------------------------------------------------------------

# Amount thresholds, expressed in the smallest currency unit (paise for INR).
# 20_000_000 paise == INR 2,00,000.
HIGH_AMOUNT_MINOR = 20_000_000
VERY_HIGH_AMOUNT_MINOR = 50_000_000  # INR 5,00,000

# Velocity: how many payments from the same merchant within the window before we
# start adding risk, and the rolling window length in seconds.
VELOCITY_WINDOW_SECONDS = 60
VELOCITY_FREE_COUNT = 5  # first N requests in-window are "free" of velocity risk

# Decision thresholds applied to the final 0-100 score.
ALLOW_MAX = 30      # score < 30           -> allow
CHALLENGE_MAX = 70  # 30 <= score <= 70    -> challenge; score > 70 -> block

# Per-signal risk weights.
W_HIGH_AMOUNT = 35             # > HIGH_AMOUNT_MINOR alone -> challenge band
W_VERY_HIGH_AMOUNT = 45        # stacks on W_HIGH_AMOUNT so a huge amount -> block
W_MISSING_VPA = 40
W_VELOCITY_PER_OVER = 12       # risk added per request above the free count
W_VELOCITY_MAX = 60            # cap on velocity contribution


@dataclass
class VelocityTracker:
    """In-memory sliding-window counter of payments per merchant.

    Phase 1 only. Production reads/writes these counters in Redis (TAD: "Cache
    -- Fraud velocity features") so they are shared across service instances.
    """

    window_seconds: int = VELOCITY_WINDOW_SECONDS
    _events: dict[str, deque[float]] = field(default_factory=lambda: defaultdict(deque))
    _lock: threading.Lock = field(default_factory=threading.Lock)

    def record_and_count(self, merchant_id: str, now: float | None = None) -> int:
        """Record a payment for ``merchant_id`` and return the in-window count
        (including the one just recorded)."""
        now = time.monotonic() if now is None else now
        cutoff = now - self.window_seconds
        with self._lock:
            events = self._events[merchant_id]
            events.append(now)
            while events and events[0] < cutoff:
                events.popleft()
            return len(events)

    def reset(self) -> None:
        """Clear all counters (used by tests for isolation)."""
        with self._lock:
            self._events.clear()


# Module-level singleton shared by the FastAPI app.
velocity_tracker = VelocityTracker()

# Scoring-path labels surfaced on the response ``model`` field.
MODEL_ONNX = "onnx"
MODEL_RULES = "rules"

# Structural type: any counter with record_and_count/reset (in-memory or Redis).
VelocityCounterLike = VelocityTracker

# The velocity counter the request path uses: Redis when configured, else the
# in-memory singleton above. Built lazily on first use and cached.
_configured_tracker: VelocityCounterLike | None = None


def _get_tracker() -> "VelocityCounterLike":
    """Return the configured velocity tracker (Redis if available, else in-memory).

    Imported lazily to avoid a hard dependency on ``redis`` in CI. The in-memory
    fallback is the shared :data:`velocity_tracker` singleton, so test resets of
    that singleton continue to work.
    """
    global _configured_tracker
    if _configured_tracker is None:
        from app.velocity.redis_tracker import build_tracker

        _configured_tracker = build_tracker(window_seconds=VELOCITY_WINDOW_SECONDS)
    return _configured_tracker


@dataclass
class ScoreResult:
    score: int
    decision: str
    reasons: list[str]
    explanation: list[dict] = field(default_factory=list)
    model: str = MODEL_RULES


def _decision_for(score: int) -> str:
    if score < ALLOW_MAX:
        return "allow"
    if score <= CHALLENGE_MAX:
        return "challenge"
    return "block"


def _rules_score(
    amount_minor: int,
    customer_vpa: str | None,
    count: int,
    window_seconds: int,
) -> tuple[int, list[str]]:
    """The transparent rules scorer (TAD §8.2). Returns ``(score, reasons)``.

    ``count`` is the in-window merchant payment count (already recorded by the
    caller so the model and rules paths share one velocity read).
    """
    reasons: list[str] = []
    score = 0

    # --- Signal: large amount -------------------------------------------------
    if amount_minor > VERY_HIGH_AMOUNT_MINOR:
        score += W_HIGH_AMOUNT + W_VERY_HIGH_AMOUNT
        reasons.append(
            f"amount {amount_minor} minor exceeds very-high threshold "
            f"{VERY_HIGH_AMOUNT_MINOR}"
        )
    elif amount_minor > HIGH_AMOUNT_MINOR:
        score += W_HIGH_AMOUNT
        reasons.append(
            f"amount {amount_minor} minor exceeds high threshold {HIGH_AMOUNT_MINOR}"
        )

    # --- Signal: missing / blank VPA -----------------------------------------
    if customer_vpa is None or not customer_vpa.strip():
        score += W_MISSING_VPA
        reasons.append("missing or blank customerVpa")

    # --- Signal: merchant velocity -------------------------------------------
    if count > VELOCITY_FREE_COUNT:
        over = count - VELOCITY_FREE_COUNT
        contribution = min(over * W_VELOCITY_PER_OVER, W_VELOCITY_MAX)
        score += contribution
        reasons.append(
            f"merchant velocity: {count} payments in last "
            f"{window_seconds}s (threshold {VELOCITY_FREE_COUNT})"
        )

    score = max(0, min(100, score))
    if not reasons:
        reasons.append("no risk signals triggered")
    return score, reasons


def score_payment(
    *,
    amount_minor: int,
    customer_vpa: str | None,
    merchant_id: str,
    method: str | None = None,
    ip: str | None = None,
    tracker: "VelocityCounterLike | None" = None,
) -> ScoreResult:
    """Compute a risk score + explanation for a payment.

    Uses the ONNX model when ``FRAUD_MODEL_PATH`` is set, otherwise the
    deterministic rules. Either way a SHAP-style ``explanation`` is attached.

    Returns a :class:`ScoreResult` with a 0-100 ``score`` (higher = riskier),
    a ``decision``, the contributing ``reasons``, the ``explanation`` (top
    contributing features) and the ``model`` that produced the score.
    """
    tracker = tracker if tracker is not None else _get_tracker()

    # One velocity read shared by both the feature vector and the rules path.
    count = tracker.record_and_count(merchant_id)

    fv = features.extract(
        amount_minor=amount_minor,
        customer_vpa=customer_vpa,
        merchant_id=merchant_id,
        method=method,
        velocity_1m=count,
        ip=ip,
    )

    # --- Model path (ONNX) with graceful fallback to the rules ---------------
    prediction = score_with_features(fv)
    if prediction is not None:
        score = prediction.score
        decision = prediction.decision
        reasons = prediction.reasons or ["model score"]
        model = MODEL_ONNX
    else:
        score, reasons = _rules_score(
            amount_minor, customer_vpa, count, getattr(tracker, "window_seconds", VELOCITY_WINDOW_SECONDS)
        )
        decision = _decision_for(score)
        model = MODEL_RULES

    # --- Explainability (always on) -----------------------------------------
    explanation = baseline.explain(fv, amount_minor=amount_minor, method=method)

    return ScoreResult(
        score=score,
        decision=decision,
        reasons=reasons,
        explanation=explanation,
        model=model,
    )
