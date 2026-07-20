package com.qeetgroup.qeetpay.cards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for the live card-issuing rail: issue request shape (program id + client reference + card
 * type + currency + {@code X-Api-Key} header) and response mapping (network ref / last4 / expiry) via
 * {@link MockRestServiceServer} — no live HTTP — plus {@code @ConditionalOnProperty} enabled-gating and
 * {@code @Primary} selection with {@link SandboxCardIssuingProvider} kept as the default (via
 * {@link ApplicationContextRunner}). Mirrors {@code LiveFxRateAdapterTest} / {@code LiveKybAdapterTest}.
 */
class LiveCardIssuingProviderTest {

    private static final String BASE_URL = "https://cards.test";
    private static final CardIssuingProperties PROPS =
            new CardIssuingProperties(true, BASE_URL, "cards_api_key", "prog_123");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A MockRestServiceServer-bound live provider; expectations are set on the returned server. */
    private record Fixture(LiveCardIssuingProvider provider, MockRestServiceServer server) {}

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new LiveCardIssuingProvider(PROPS, builder, MAPPER), server);
    }

    @Test
    void issueSendsExpectedRequestShapeAndMapsResponse() {
        Fixture f = fixture();
        UUID cardId = UUID.randomUUID();

        f.server()
                .expect(requestTo(BASE_URL + "/cards"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "cards_api_key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.programId").value("prog_123"))
                .andExpect(jsonPath("$.clientReference").value(cardId.toString()))
                .andExpect(jsonPath("$.holderReference").value("holder-1"))
                .andExpect(jsonPath("$.cardType").value("WALLET"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andRespond(
                        withSuccess(
                                "{\"cardReference\":\"card_abc123\",\"last4\":\"4242\",\"expiry\":\"12/28\"}",
                                MediaType.APPLICATION_JSON));

        CardIssuingProvider.IssuedCard issued =
                f.provider()
                        .issue(
                                new CardIssuingProvider.CardIssueRequest(
                                        cardId, UUID.randomUUID(), "holder-1", CardType.WALLET, "INR"));

        assertThat(issued.networkCardRef()).isEqualTo("card_abc123");
        assertThat(issued.last4()).isEqualTo("4242");
        assertThat(issued.expiry()).isEqualTo("12/28");
        f.server().verify();
    }

    @Test
    void freezePostsToRailLifecycleEndpoint() {
        Fixture f = fixture();
        f.server()
                .expect(requestTo(BASE_URL + "/cards/card_abc123/freeze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "cards_api_key"))
                .andRespond(withSuccess("{\"status\":\"FROZEN\"}", MediaType.APPLICATION_JSON));

        f.provider().freeze("card_abc123");
        f.server().verify();
    }

    @Test
    void authorizeSpendPostsAmountToRail() {
        Fixture f = fixture();
        f.server()
                .expect(requestTo(BASE_URL + "/cards/card_abc123/authorizations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amountMinor").value(30_000))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));

        f.provider()
                .authorizeSpend(new CardIssuingProvider.CardAuthorization("card_abc123", 30_000L, "INR"));
        f.server().verify();
    }

    // --- gating -----------------------------------------------------------------------------------

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(GatingConfig.class);

    @Test
    void sandboxIsDefaultWhenCardsDisabled() {
        runner.run(
                ctx -> {
                    assertThat(ctx).doesNotHaveBean(LiveCardIssuingProvider.class);
                    assertThat(ctx.getBean(CardIssuingProvider.class))
                            .isInstanceOf(SandboxCardIssuingProvider.class);
                });
    }

    @Test
    void liveProviderActiveAndPrimaryWhenEnabled() {
        runner.withPropertyValues("qeetpay.cards.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(LiveCardIssuingProvider.class);
                            // @Primary (and Sandbox's @ConditionalOnMissingBean backoff) select live
                            assertThat(ctx.getBean(CardIssuingProvider.class))
                                    .isInstanceOf(LiveCardIssuingProvider.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CardIssuingProperties.class)
    @Import({SandboxCardIssuingProvider.class, LiveCardIssuingProvider.class})
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
