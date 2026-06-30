"""Deterministic, rules-based fraud-scoring stub for qeet-pay.

This is the Phase-1 placeholder for TAD Module 08 (fraud scoring). It combines a
handful of explainable signals into a 0-100 risk score and maps that score to an
allow / challenge / block decision. Every signal that fires is returned in
``reasons`` so the decision is fully explainable (TAD section 8.2).

# TODO: replace with XGBoost/LightGBM + ONNX (TAD section 8.1)
#   The production model loads a trained gradient-boosted tree exported to ONNX,
#   builds a feature vector (amount, velocity, device, geo-IP, VPA reputation,
#   merchant-category risk, ...) from Redis-backed velocity features + the request,
#   runs inference via onnxruntime, and calibrates the raw probability to 0-100.
#   The rules below remain only as a transparent fallback / cold-start scorer.
"""

from __future__ import annotations

import threading
import time
from collections import defaultdict, deque
from dataclasses import dataclass, field

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


@dataclass
class ScoreResult:
    score: int
    decision: str
    reasons: list[str]


def _decision_for(score: int) -> str:
    if score < ALLOW_MAX:
        return "allow"
    if score <= CHALLENGE_MAX:
        return "challenge"
    return "block"


def score_payment(
    *,
    amount_minor: int,
    customer_vpa: str | None,
    merchant_id: str,
    method: str | None = None,
    tracker: VelocityTracker | None = None,
) -> ScoreResult:
    """Compute a deterministic risk score for a payment.

    Returns a :class:`ScoreResult` with a 0-100 ``score`` (higher = riskier),
    a ``decision`` and the list of contributing ``reasons``.
    """
    tracker = tracker or velocity_tracker
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
    count = tracker.record_and_count(merchant_id)
    if count > VELOCITY_FREE_COUNT:
        over = count - VELOCITY_FREE_COUNT
        contribution = min(over * W_VELOCITY_PER_OVER, W_VELOCITY_MAX)
        score += contribution
        reasons.append(
            f"merchant velocity: {count} payments in last "
            f"{tracker.window_seconds}s (threshold {VELOCITY_FREE_COUNT})"
        )

    score = max(0, min(100, score))
    decision = _decision_for(score)
    if not reasons:
        reasons.append("no risk signals triggered")
    return ScoreResult(score=score, decision=decision, reasons=reasons)
