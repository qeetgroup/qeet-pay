package com.qeetgroup.qeetpay.crossborder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for the live FX-rate adapter: request shape + rate mapping + short-TTL caching (via
 * {@link MockRestServiceServer}, no live HTTP) and {@code @ConditionalOnProperty} enabled-gating +
 * {@code @Primary} selection with {@link SandboxFxRateAdapter} kept as the default (via
 * {@link ApplicationContextRunner}).
 */
class LiveFxRateAdapterTest {

    private static final String BASE_URL = "https://fx.test";
    private static final FxProperties PROPS =
            new FxProperties(true, BASE_URL, "fx_api_key", Duration.ofSeconds(60));

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private LiveFxRateAdapter fx;

    private void bind() {
        this.builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.fx = new LiveFxRateAdapter(PROPS, builder, new ObjectMapper());
    }

    @Test
    void fetchesRateWithExpectedQueryShapeAndParsesBigDecimal() {
        bind();
        server.expect(ExpectedCount.once(), requestTo(Matchers.startsWith(BASE_URL + "/latest")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("base", "USD"))
                .andExpect(queryParam("symbols", "INR"))
                .andExpect(queryParam("access_key", "fx_api_key"))
                .andRespond(
                        withSuccess(
                                "{\"base\":\"USD\",\"rates\":{\"INR\":83.42}}",
                                MediaType.APPLICATION_JSON));

        BigDecimal rate = fx.rate("usd", "inr"); // also proves case-normalization

        assertThat(rate).isEqualByComparingTo("83.42");
        server.verify();
    }

    @Test
    void secondLookupWithinTtlIsServedFromCache() {
        bind();
        // ExpectedCount.once(): a second HTTP call would fail verify() — proves the cache is used.
        server.expect(ExpectedCount.once(), requestTo(Matchers.startsWith(BASE_URL + "/latest")))
                .andRespond(
                        withSuccess(
                                "{\"rates\":{\"INR\":83.42}}", MediaType.APPLICATION_JSON));

        BigDecimal first = fx.rate("USD", "INR");
        BigDecimal second = fx.rate("USD", "INR");

        assertThat(first).isEqualByComparingTo("83.42");
        assertThat(second).isEqualByComparingTo(first);
        server.verify();
    }

    // --- gating -----------------------------------------------------------------------------------

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(GatingConfig.class);

    @Test
    void sandboxIsDefaultWhenFxDisabled() {
        runner.run(
                ctx -> {
                    assertThat(ctx).doesNotHaveBean(LiveFxRateAdapter.class);
                    assertThat(ctx.getBean(FxRateAdapter.class))
                            .isInstanceOf(SandboxFxRateAdapter.class);
                });
    }

    @Test
    void liveAdapterActiveAndPrimaryWhenEnabled() {
        runner.withPropertyValues("qeetpay.fx.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(LiveFxRateAdapter.class);
                            // @Primary (and Sandbox's @ConditionalOnMissingBean backoff) select live
                            assertThat(ctx.getBean(FxRateAdapter.class))
                                    .isInstanceOf(LiveFxRateAdapter.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FxProperties.class)
    @Import({SandboxFxRateAdapter.class, LiveFxRateAdapter.class})
    static class GatingConfig {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
