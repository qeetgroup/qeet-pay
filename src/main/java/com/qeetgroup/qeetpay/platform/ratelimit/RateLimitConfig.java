package com.qeetgroup.qeetpay.platform.ratelimit;

import com.qeetgroup.qeetpay.platform.config.AppProperties;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link RateLimitFilter} for {@code /v1/*} only, ordered just after the Spring Security
 * chain ({@link SecurityProperties#DEFAULT_FILTER_ORDER}) so the merchant is already resolved into
 * {@link com.qeetgroup.qeetpay.platform.tenancy.MerchantContext} when the filter keys its bucket.
 *
 * <p>The filter is always registered but no-ops unless {@code qeetpay.ratelimit.enabled=true}, so
 * there is nothing profile-specific to wire here — dev/test leave it off, prod/staging turn it on.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(AppProperties props) {
        FilterRegistrationBean<RateLimitFilter> reg =
                new FilterRegistrationBean<>(new RateLimitFilter(props.getRateLimit()));
        reg.addUrlPatterns("/v1/*");
        // Run immediately after the security filter chain so MerchantContext is populated.
        reg.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 10);
        reg.setName("rateLimitFilter");
        return reg;
    }
}
