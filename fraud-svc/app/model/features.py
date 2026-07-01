"""Feature engineering for fraud scoring.

Converts a raw ScoreRequest into a flat numeric feature vector.
All features are normalised to [0, 1] or bounded integers so that
gradient-boosted models / ONNX inference can consume them directly.
"""

from __future__ import annotations

import datetime
import math
from dataclasses import dataclass

# ---------------------------------------------------------------------------
# Method risk map: 0 = low; 1 = medium; 2 = high
# ---------------------------------------------------------------------------
_METHOD_RISK: dict[str | None, float] = {
    "UPI": 0.2,
    "CARD": 0.4,
    "NETBANKING": 0.3,
    "WALLET": 0.5,
    "NACH": 0.1,
    "EMI": 0.3,
    None: 0.6,   # unknown method → elevated risk
}


@dataclass(frozen=True)
class FeatureVector:
    """Normalised feature vector fed to the ML model or rules scorer."""

    amount_log: float          # log1p(amount_minor / 100)     — compressed amount in INR
    amount_zscore: float       # raw amount relative to a soft cap (z-score proxy)
    velocity_1m: int           # payment count from merchant in last 60 s
    method_risk: float         # 0–1 method risk score
    is_upi_collect: bool       # True if method==UPI AND vpa is provided
    missing_vpa: bool          # True if customerVpa is None / blank
    is_round_amount: bool      # True if amount_minor is divisible by 100_000 paise (₹1000)
    hour_of_day: int           # 0–23 (UTC)
    ip_risk: float             # placeholder: 0.0 = trusted, 1.0 = high-risk

    def to_list(self) -> list[float]:
        return [
            self.amount_log,
            self.amount_zscore,
            float(self.velocity_1m),
            self.method_risk,
            float(self.is_upi_collect),
            float(self.missing_vpa),
            float(self.is_round_amount),
            float(self.hour_of_day) / 23.0,
            self.ip_risk,
        ]


# Soft upper-bound for amount z-score normalisation (INR 10,000 in paise).
_AMOUNT_SCALE = 1_000_000


def extract(
    *,
    amount_minor: int,
    customer_vpa: str | None,
    merchant_id: str,
    method: str | None,
    velocity_1m: int,
    ip: str | None = None,
) -> FeatureVector:
    """Build a :class:`FeatureVector` from raw request fields."""
    amount_inr = amount_minor / 100.0
    amount_log = math.log1p(amount_inr)
    amount_zscore = min(amount_minor / _AMOUNT_SCALE, 10.0)

    has_vpa = customer_vpa is not None and customer_vpa.strip() != ""
    method_upper = (method or "").upper() if method else None

    return FeatureVector(
        amount_log=amount_log,
        amount_zscore=amount_zscore,
        velocity_1m=velocity_1m,
        method_risk=_METHOD_RISK.get(method_upper, 0.6),
        is_upi_collect=method_upper == "UPI" and has_vpa,
        missing_vpa=not has_vpa,
        is_round_amount=(amount_minor % 100_000) == 0 and amount_minor > 0,
        hour_of_day=datetime.datetime.now(datetime.timezone.utc).hour,
        ip_risk=_ip_risk(ip),
    )


def _ip_risk(ip: str | None) -> float:
    """Stub IP risk: returns 0.0 (trusted) for all IPs. Production integrates MaxMind."""
    return 0.0
