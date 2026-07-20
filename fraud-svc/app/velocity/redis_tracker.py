"""Redis-backed sliding-window velocity tracker for fraud scoring.

Production implementation: uses Redis ZADD / ZREMRANGEBYSCORE to maintain
per-merchant rolling windows, shared across service instances. Falls back to the
in-process singleton ``VelocityTracker`` from ``app.scoring`` when Redis is
unavailable (``REDIS_URL`` / legacy ``QEETPAY_FRAUD_REDIS_URL`` not set, or the
connection is refused).

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
    """Return a RedisVelocityTracker when a Redis URL is configured, else the
    shared in-memory ``velocity_tracker`` singleton from ``app.scoring``.

    Reads ``REDIS_URL`` first, then the legacy ``QEETPAY_FRAUD_REDIS_URL``.
    Returning the shared singleton (not a fresh instance) keeps test resets of
    ``app.scoring.velocity_tracker`` effective in the in-memory fallback path.
    """
    from app.scoring import velocity_tracker as shared_tracker

    redis_url = os.getenv("REDIS_URL") or os.getenv("QEETPAY_FRAUD_REDIS_URL")
    if not redis_url:
        return shared_tracker

    try:
        tracker = RedisVelocityTracker(redis_url, window_seconds)
        # Probe the connection to verify it works at startup.
        tracker._redis.ping()
        return tracker
    except Exception:
        return shared_tracker
