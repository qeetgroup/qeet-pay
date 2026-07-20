package com.qeetgroup.qeetpay.payouts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for the live RazorpayX payout adapter: request shape + response mapping (via
 * {@link MockRestServiceServer}, no live HTTP) and {@code @ConditionalOnProperty} enabled-gating +
 * {@code @Primary} selection (via {@link ApplicationContextRunner}).
 */
class RazorpayXPayoutProviderTest {

    private static final String BASE_URL = "https://rzpx.test";
    private static final PayoutRailProperties PROPS =
            new PayoutRailProperties(true, BASE_URL, "rzp_key", "rzp_secret", "2323230000123456", "payout");

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private RazorpayXPayoutProvider provider;

    private void bind() {
        this.builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.provider = new RazorpayXPayoutProvider(PROPS, builder, new ObjectMapper());
    }

    @Test
    void bankPayoutSendsRazorpayXCompositeRequestAndMapsSuccess() {
        bind();
        String basic =
                Base64.getEncoder()
                        .encodeToString("rzp_key:rzp_secret".getBytes(StandardCharsets.UTF_8));
        Payout payout =
                new Payout(UUID.randomUUID(), 250000, "INR", PayoutRail.IMPS, "11122233@HDFC0001234", "vendor");

        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/v1/payouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + basic))
                .andExpect(jsonPath("$.account_number").value("2323230000123456"))
                .andExpect(jsonPath("$.amount").value(250000))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.mode").value("IMPS"))
                .andExpect(jsonPath("$.purpose").value("payout"))
                .andExpect(jsonPath("$.reference_id").value(payout.getId().toString()))
                .andExpect(jsonPath("$.fund_account.account_type").value("bank_account"))
                .andExpect(jsonPath("$.fund_account.bank_account.account_number").value("11122233"))
                .andExpect(jsonPath("$.fund_account.bank_account.ifsc").value("HDFC0001234"))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"pout_ABC123\",\"status\":\"processing\"}",
                                MediaType.APPLICATION_JSON));

        PayoutProvider.ProviderResult result = provider.process(payout);

        assertThat(result.success()).isTrue();
        assertThat(result.providerPayoutId()).isEqualTo("pout_ABC123");
        assertThat(result.failureReason()).isNull();
        server.verify();
    }

    @Test
    void upiPayoutSendsVpaFundAccount() {
        bind();
        Payout payout =
                new Payout(UUID.randomUUID(), 5000, "INR", PayoutRail.UPI, "alice@okhdfc", null);

        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/v1/payouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.mode").value("UPI"))
                .andExpect(jsonPath("$.fund_account.account_type").value("vpa"))
                .andExpect(jsonPath("$.fund_account.vpa.address").value("alice@okhdfc"))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"pout_UPI9\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));

        PayoutProvider.ProviderResult result = provider.process(payout);

        assertThat(result.success()).isTrue();
        assertThat(result.providerPayoutId()).isEqualTo("pout_UPI9");
        server.verify();
    }

    @Test
    void terminalFailureStatusMapsToFailedResult() {
        bind();
        Payout payout = new Payout(UUID.randomUUID(), 5000, "INR", PayoutRail.NEFT, "9999@ICIC0000001", null);

        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/v1/payouts"))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"pout_x\",\"status\":\"failed\"}", MediaType.APPLICATION_JSON));

        PayoutProvider.ProviderResult result = provider.process(payout);

        assertThat(result.success()).isFalse();
        assertThat(result.providerPayoutId()).isNull();
        assertThat(result.failureReason()).contains("failed");
        server.verify();
    }

    @Test
    void fetchStatusMapsProviderStatusesOntoPayoutEnum() {
        bind();
        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/v1/payouts/pout_done"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"pout_done\",\"status\":\"processed\"}",
                                MediaType.APPLICATION_JSON));

        assertThat(provider.fetchStatus("pout_done")).isEqualTo(PayoutStatus.PAID);
        server.verify();
    }

    @Test
    void fetchStatusMapsInFlightToPendingApproval() {
        bind();
        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/v1/payouts/pout_wip"))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"pout_wip\",\"status\":\"processing\"}",
                                MediaType.APPLICATION_JSON));

        assertThat(provider.fetchStatus("pout_wip")).isEqualTo(PayoutStatus.PENDING_APPROVAL);
        server.verify();
    }

    // --- gating -----------------------------------------------------------------------------------

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(GatingConfig.class);

    @Test
    void sandboxIsUsedWhenPayoutsDisabled() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasSingleBean(SandboxPayoutProvider.class);
                    assertThat(ctx).doesNotHaveBean(RazorpayXPayoutProvider.class);
                    assertThat(ctx.getBean(PayoutProvider.class))
                            .isInstanceOf(SandboxPayoutProvider.class);
                });
    }

    @Test
    void liveProviderActiveAndPrimaryWhenEnabled() {
        runner.withPropertyValues("qeetpay.payouts.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(RazorpayXPayoutProvider.class);
                            // both providers exist; @Primary resolves the single-type injection point
                            assertThat(ctx.getBean(PayoutProvider.class))
                                    .isInstanceOf(RazorpayXPayoutProvider.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PayoutRailProperties.class)
    @Import({SandboxPayoutProvider.class, RazorpayXPayoutProvider.class})
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
