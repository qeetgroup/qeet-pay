"""Redis-backed sliding-window velocity tracker for fraud scoring.

Production implementation: uses Redis ZADD / ZREMRANGEBYSCORE to maintain
per-merchant rolling windows. Falls back to the in-process VelocityTracker
from ``app.scoring`` when Redis is unavailable (QEETPAY_FRAUD_REDIS_URL not set
or connection refused).

Redis key schema:
  ``fraud:velocity:{merchant_id}``  — sorted set; score = epoch milliseconds
"""

from __future__ import annotations

import os
import time
from typing import Protocol


class VelocityCounter(Protocol):
    def record_and_count(self, merchant_id: str, now: float | None = None) -> int: ...
    def reset(self) -> None: ...


class RedisVelocityTracker:
    """Sliding-window velocity counter backed by Redis sorted sets.

    TTL on the key is set to ``window_seconds`` + 10 s so Redis evicts stale
    keys automatically without a background job.
    """

    def __init__(self, redis_url: str, window_seconds: int = 60) -> None:
        import redis

        self._redis = redis.from_url(redis_url, decode_responses=True)
        self._window = window_seconds

    def record_and_count(self, merchant_id: str, now: float | None = None) -> int:
        now_ms = int((now or time.time()) * 1000)
        cutoff_ms = now_ms - self._window * 1000
        key = f"fraud:velocity:{merchant_id}"
        pipe = self._redis.pipeline()
        pipe.zremrangebyscore(key, "-inf", cutoff_ms)
        pipe.zadd(key, {str(now_ms): now_ms})
        pipe.zcard(key)
        pipe.expire(key, self._window + 10)
        results = pipe.execute()
        return results[2]

    def reset(self) -> None:
        """For testing only — flushes all velocity keys (no-op in production)."""
        for key in self._redis.scan_iter("fraud:velocity:*"):
            self._redis.delete(key)


def build_tracker(window_seconds: int = 60) -> VelocityCounter:
    """Return a RedisVelocityTracker if QEETPAY_FRAUD_REDIS_URL is configured,
    falling back to the in-memory VelocityTracker from app.scoring."""
    from app.scoring import VelocityTracker

    redis_url = os.getenv("QEETPAY_FRAUD_REDIS_URL")
    if not redis_url:
        return VelocityTracker(window_seconds=window_seconds)

    try:
        tracker = RedisVelocityTracker(redis_url, window_seconds)
        # Probe the connection to verify it works at startup
        tracker._redis.ping()
        return tracker
    except Exception:
        return VelocityTracker(window_seconds=window_seconds)
