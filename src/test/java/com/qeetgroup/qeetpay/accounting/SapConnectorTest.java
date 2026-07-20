package com.qeetgroup.qeetpay.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for the credential-gated {@link SapConnector} (PRD Module 11.3) using
 * {@link MockRestServiceServer} — no live HTTP. Proves the SAP Business One Service Layer sequence
 * (login → JournalEntries), the {@link ExportPayload} → JournalEntry mapping (debit/credit split),
 * the {@code JdtNum} → external-ref lift, and the {@code qeetpay.accounting.sap.enabled} gating in
 * {@link AccountingConfig}. (The default-profile behaviour — no SAP connector unless enabled — is the
 * gating {@code @Nested} block below.)
 */
class SapConnectorTest {

    private static final String BASE = "https://sap-b1.test/b1s/v1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loginsThenPostsJournalEntryAndReturnsExternalRef() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // 1) Service Layer login with the configured company DB + credentials.
        server.expect(ExpectedCount.once(), requestTo(BASE + "/Login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.CompanyDB").value("QEET_DB"))
                .andExpect(jsonPath("$.UserName").value("svc"))
                .andExpect(jsonPath("$.Password").value("secret"))
                .andRespond(withSuccess("{\"SessionId\":\"SESS-1\"}", MediaType.APPLICATION_JSON));

        // 2) Journal entry post carries the B1SESSION cookie + the mapped debit/credit lines.
        server.expect(ExpectedCount.once(), requestTo(BASE + "/JournalEntries"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Cookie", Matchers.containsString("B1SESSION=SESS-1")))
                .andExpect(jsonPath("$.JournalEntryLines[0].AccountCode").value("settlement"))
                .andExpect(jsonPath("$.JournalEntryLines[1].AccountCode").value("revenue"))
                .andRespond(withSuccess("{\"JdtNum\":777}", MediaType.APPLICATION_JSON));

        SapConnector connector = new SapConnector(builder.build(), "QEET_DB", "svc", "secret", objectMapper);

        SyncResult result = connector.push(samplePayload(), null);

        assertThat(result.success()).isTrue();
        assertThat(result.externalRef()).isEqualTo("777");
        assertThat(result.recordCount()).isEqualTo(1);
        server.verify();

        // Payload mapping: paise → rupees, debit on one line, credit on the other.
        JsonNode je = objectMapper.readTree(result.document());
        JsonNode lines = je.path("JournalEntryLines");
        assertThat(lines.get(0).path("Debit").decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(lines.get(0).path("Credit").decimalValue()).isEqualByComparingTo("0");
        assertThat(lines.get(1).path("Credit").decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(lines.get(1).path("Debit").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void responseWithoutJdtNumFallsBackToDefaultRef() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(ExpectedCount.once(), requestTo(BASE + "/Login"))
                .andRespond(withSuccess("{\"SessionId\":\"S\"}", MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(BASE + "/JournalEntries"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        SapConnector connector = new SapConnector(builder.build(), "QEET_DB", "svc", "secret", objectMapper);

        SyncResult result = connector.push(samplePayload(), null);

        assertThat(result.success()).isTrue();
        assertThat(result.externalRef()).isEqualTo("sap-journal");
        server.verify();
    }

    @Test
    void transportFailureIsRecordedNotThrown() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(ExpectedCount.once(), requestTo(BASE + "/Login"))
                .andRespond(
                        org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        SapConnector connector = new SapConnector(builder.build(), "QEET_DB", "svc", "secret", objectMapper);

        SyncResult result = connector.push(samplePayload(), null);

        assertThat(result.success()).isFalse();
        assertThat(result.detail()).startsWith("sap push failed");
        server.verify();
    }

    private ExportPayload samplePayload() {
        return new ExportPayload(
                UUID.randomUUID(),
                Instant.now().minusSeconds(3600),
                Instant.now(),
                List.of(
                        new ExportPayload.JournalVoucher(
                                UUID.randomUUID(),
                                Instant.now(),
                                List.of(
                                        new ExportPayload.VoucherLine("settlement", Direction.DEBIT, 500_000),
                                        new ExportPayload.VoucherLine("revenue", Direction.CREDIT, 500_000)))),
                List.of());
    }

    /**
     * The gated {@code @Bean} in {@link AccountingConfig}: no {@link SapConnector} unless
     * {@code qeetpay.accounting.sap.enabled=true}.
     */
    @Nested
    class Gating {

        private final ApplicationContextRunner runner =
                new ApplicationContextRunner()
                        .withBean(ObjectMapper.class, ObjectMapper::new)
                        .withUserConfiguration(AccountingConfig.class, PropsConfig.class);

        @Test
        void offByDefault() {
            runner.run(ctx -> assertThat(ctx).doesNotHaveBean(SapConnector.class));
        }

        @Test
        void offWhenEnabledFalse() {
            runner.withPropertyValues("qeetpay.accounting.sap.enabled=false")
                    .run(ctx -> assertThat(ctx).doesNotHaveBean(SapConnector.class));
        }

        @Test
        void onWhenEnabledTrue() {
            runner.withPropertyValues(
                            "qeetpay.accounting.sap.enabled=true",
                            "qeetpay.accounting.sap.base-url=" + BASE,
                            "qeetpay.accounting.sap.company-db=QEET_DB",
                            "qeetpay.accounting.sap.username=svc",
                            "qeetpay.accounting.sap.password=secret")
                    .run(
                            ctx -> {
                                assertThat(ctx).hasSingleBean(SapConnector.class);
                                assertThat(ctx.getBean(SapConnector.class).target())
                                        .isEqualTo(AccountingTarget.SAP);
                            });
        }

        @Configuration(proxyBeanMethods = false)
        @EnableConfigurationProperties({SapProperties.class, ZohoBooksProperties.class})
        static class PropsConfig {}
    }
}
