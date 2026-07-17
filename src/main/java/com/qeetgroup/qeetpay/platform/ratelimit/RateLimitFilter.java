package com.qeetgroup.qeetpay.platform.ratelimit;

import com.qeetgroup.qeetpay.platform.config.AppProperties;
import com.qeetgroup.qeetpay.platform.security.ApiKeyAuthenticationFilter;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-caller token-bucket rate limiter for {@code /v1/**} (TAD §11). Keyed, in priority order, by the
 * authenticated merchant, then the presented {@code X-Api-Key}, then the client IP — so a single
 * merchant/key can't starve others. Purely in-memory (a {@link ConcurrentHashMap} of {@link
 * TokenBucket}s): no Redis dependency, and it degrades to a no-op when disabled, so tests never
 * throttle.
 *
 * <p><b>Off by default</b> ({@code qeetpay.ratelimit.enabled=false}); the {@code prod}/{@code staging}
 * profiles enable it. When tripped it short-circuits with an RFC-7807 {@code 429} and a
 * {@code Retry-After} header. Registered for {@code /v1/*} only (see {@link RateLimitConfig}) and
 * ordered just after the Spring Security chain so {@link MerchantContext} is already populated.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final AppProperties.RateLimit config;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties.RateLimit config) {
        this.config = config;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!config.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveKey(request);
        long now = System.nanoTime();
        TokenBucket bucket =
                buckets.computeIfAbsent(
                        key,
                        k -> new TokenBucket(config.getCapacity(), config.getRefillPerSecond(), now));

        if (bucket.tryConsume(now)) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfter = bucket.retryAfterSeconds(now);
        response.setStatus(429);
        response.setContentType("application/problem+json");
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.getWriter()
                .write(
                        "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                                + "\"detail\":\"Rate limit exceeded. Retry after "
                                + retryAfter
                                + "s.\"}");
    }

    /** Prefer the tenant, then the API key, then the peer IP — never let the map key be null. */
    private static String resolveKey(HttpServletRequest request) {
        UUID merchant = MerchantContext.current();
        if (merchant != null) {
            return "m:" + merchant;
        }
        String apiKey = request.getHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "k:" + Integer.toHexString(apiKey.hashCode());
        }
        return "ip:" + request.getRemoteAddr();
    }
}
