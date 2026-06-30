package com.qeetgroup.qeetpay.platform.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the active merchant for each request into {@link MerchantContext}.
 *
 * <p>Priority: (1) a merchant already set by the API-key filter; (2) the {@code merchant_id} claim
 * of the authenticated Qeet ID JWT; (3) when {@code allowHeaderFallback} is true (dev/test only),
 * an {@code X-Merchant-Id} header so the skeleton runs without a live Qeet ID (TAD §6.1).
 */
public class MerchantFilter extends OncePerRequestFilter {

    public static final String MERCHANT_CLAIM = "merchant_id";
    public static final String MERCHANT_HEADER = "X-Merchant-Id";

    private final boolean allowHeaderFallback;

    public MerchantFilter(boolean allowHeaderFallback) {
        this.allowHeaderFallback = allowHeaderFallback;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (MerchantContext.current() == null) {
                UUID resolved = resolveFromJwt();
                if (resolved == null) {
                    resolved = resolveFromHeader(request);
                }
                MerchantContext.set(resolved);
            }
            filterChain.doFilter(request, response);
        } finally {
            MerchantContext.clear();
        }
    }

    private UUID resolveFromJwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return parseUuid(jwt.getClaimAsString(MERCHANT_CLAIM));
        }
        return null;
    }

    private UUID resolveFromHeader(HttpServletRequest request) {
        return allowHeaderFallback ? parseUuid(request.getHeader(MERCHANT_HEADER)) : null;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
