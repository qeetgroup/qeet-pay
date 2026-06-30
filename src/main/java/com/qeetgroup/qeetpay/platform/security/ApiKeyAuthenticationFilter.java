package com.qeetgroup.qeetpay.platform.security;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates programmatic API calls presented with a Qeet Pay API key (TAD §10.1) — either an
 * {@code X-Api-Key} header or {@code Authorization: Bearer qp_…}. On success it authenticates the
 * caller (scopes → {@code SCOPE_*} authorities) and sets {@link MerchantContext}. Requests without
 * a key pass through untouched so the OIDC/JWT (or dev) path can handle them.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Api-Key";

    private final ApiKeyRepository apiKeys;

    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeys) {
        this.apiKeys = apiKeys;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String raw = extractKey(request);
        if (raw == null) {
            filterChain.doFilter(request, response);
            return;
        }

        var found = apiKeys.findByKeyHashAndStatus(ApiKeys.hash(raw), "active");
        if (found.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Invalid API key\",\"status\":401}");
            return;
        }

        ApiKey key = found.get();
        List<SimpleGrantedAuthority> authorities =
                Arrays.stream(key.getScopes().trim().split("\\s+"))
                        .filter(s -> !s.isBlank())
                        .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                        .toList();
        var auth =
                new UsernamePasswordAuthenticationToken(
                        key.getMerchantId().toString(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        MerchantContext.set(key.getMerchantId());
        filterChain.doFilter(request, response);
    }

    private static String extractKey(HttpServletRequest request) {
        String headerKey = request.getHeader(API_KEY_HEADER);
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey.trim();
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer qp_")) {
            return auth.substring("Bearer ".length()).trim();
        }
        return null;
    }
}
