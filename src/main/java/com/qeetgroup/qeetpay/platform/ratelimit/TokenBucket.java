package com.qeetgroup.qeetpay.platform.ratelimit;

/**
 * A minimal thread-safe token bucket. {@code capacity} tokens accrue at {@code refillPerSecond};
 * each admitted request costs one token. Purely in-memory (no Redis) — see {@link RateLimitFilter}
 * for the per-caller registry and graceful behaviour when disabled.
 */
final class TokenBucket {

    private final double capacity;
    private final double refillPerSecond;

    private double tokens;
    private long lastRefillNanos;

    TokenBucket(double capacity, double refillPerSecond, long nowNanos) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    /** @return {@code true} if a token was available (and consumed); {@code false} if throttled. */
    synchronized boolean tryConsume(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** Whole seconds a caller should wait before one token is available (>= 1 for a Retry-After). */
    synchronized long retryAfterSeconds(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0 || refillPerSecond <= 0) {
            return 1;
        }
        double deficit = 1.0 - tokens;
        return Math.max(1, (long) Math.ceil(deficit / refillPerSecond));
    }

    private void refill(long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        double added = (elapsed / 1_000_000_000.0) * refillPerSecond;
        if (added > 0) {
            tokens = Math.min(capacity, tokens + added);
            lastRefillNanos = nowNanos;
        }
    }
}
