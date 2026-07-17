package com.qeetgroup.qeetpay.platform.security;

import com.qeetgroup.qeetpay.platform.config.AppProperties;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless API security. Qeet Pay is an OIDC relying party (TAD §2.2, §12.1): it holds no
 * passwords or sessions. Two authentication paths share one chain:
 *
 * <ul>
 *   <li><b>API keys</b> ({@link ApiKeyAuthenticationFilter}) for programmatic {@code /v1} calls;
 *   <li><b>Qeet ID JWTs</b> (OAuth2 resource server) for the dashboard, mapping {@code roles}.
 * </ul>
 *
 * The {@code dev}/{@code test} profiles are permissive and accept an {@code X-Merchant-Id} header
 * so the skeleton boots without a live Qeet ID, and the JWT decoder is not created (no startup
 * network calls).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** API-doc endpoints — public-safe (they describe the API; auth is still enforced on /v1). */
    static final String[] DOC_PATHS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    /**
     * PUBLIC hosted-checkout endpoints. Deliberately unauthenticated: a payer opens a payment-link URL
     * and pays it with no {@code X-Api-Key} — the link code is the capability (see {@code checkout/}).
     * Still rate-limited: the {@code /v1/*} limiter (prod/staging) covers these paths.
     */
    static final String[] PUBLIC_CHECKOUT_PATHS = {"/v1/checkout/**"};

    private static final String[] PUBLIC_PATHS = {
        "/healthz", "/readyz", "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", // OpenAPI / Swagger UI — public
        "/v1/payments/razorpay/webhook", // Razorpay webhook — auth via HMAC-SHA256 signature
        "/v1/checkout/**" // PUBLIC hosted checkout — the link code is the capability (no API key)
    };

    private final AppProperties props;
    private final ApiKeyRepository apiKeys;

    public SecurityConfig(AppProperties props, ApiKeyRepository apiKeys) {
        this.props = props;
        this.apiKeys = apiKeys;
    }

    @Bean
    @Profile("!dev & !test")
    SecurityFilterChain securedFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // stateless bearer/API-key API; no cookies
                .cors(Customizer.withDefaults())
                // Production security headers. HSTS is emitted on secure requests (behind the
                // TLS-terminating proxy via forward-headers-strategy); nosniff, frame DENY and a
                // strict referrer policy are safe for Swagger UI (it uses no frames).
                .headers(
                        headers ->
                                headers.httpStrictTransportSecurity(
                                                hsts ->
                                                        hsts.includeSubDomains(true)
                                                                .maxAgeInSeconds(31_536_000L))
                                        .contentTypeOptions(Customizer.withDefaults())
                                        .frameOptions(frame -> frame.deny())
                                        .referrerPolicy(
                                                rp ->
                                                        rp.policy(
                                                                ReferrerPolicyHeaderWriter
                                                                        .ReferrerPolicy
                                                                        .STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        reg ->
                                reg.requestMatchers(PUBLIC_PATHS)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
                .addFilterBefore(
                        new ApiKeyAuthenticationFilter(apiKeys),
                        BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new MerchantFilter(false), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Profile("dev | test")
    SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                // Docs + the PUBLIC hosted-checkout paths are explicitly permitted (the chain permits
                // everything anyway in dev/test, but this keeps the public contract visible in both chains).
                .authorizeHttpRequests(
                        reg ->
                                reg.requestMatchers(DOC_PATHS)
                                        .permitAll()
                                        .requestMatchers(PUBLIC_CHECKOUT_PATHS)
                                        .permitAll()
                                        .anyRequest()
                                        .permitAll())
                .addFilterBefore(
                        new ApiKeyAuthenticationFilter(apiKeys),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new MerchantFilter(true), ApiKeyAuthenticationFilter.class);
        return http.build();
    }

    /** Lazy JWKS-backed decoder + issuer validation — no network at startup. */
    @Bean
    @Profile("!dev & !test")
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(props.getOidc().getJwksUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.getOidc().getIssuerUri()));
        return decoder;
    }

    private JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
