package com.qeetgroup.qeetpay.gst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for the live IRP adapter (TAD §7.3) using {@link MockRestServiceServer} — no live HTTP.
 * Proves the request shape (auth then generate/cancel, credentials + e-invoice JSON), the response
 * mapping into {@link IrpResult}, auth-token caching across calls, and the {@code qeetpay.irp.enabled}
 * gating (sandbox otherwise).
 */
class LiveIrpAdapterTest {

    private static final String BASE = "https://irp.test";
    private static final String SUPPLIER_GSTIN = "27ABCDE1234F1Z5";
    private static final DateTimeFormatter NIC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Fixture(LiveIrpAdapter adapter, MockRestServiceServer server) {}

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        IrpProperties props =
                new IrpProperties(true, BASE, "cid", "csecret", "user", "pass", null, null, null, 0);
        return new Fixture(new LiveIrpAdapter(props, objectMapper, builder), server);
    }

    private GstInvoice invoice() {
        return new GstInvoice(
                UUID.randomUUID(), "INV-1", SUPPLIER_GSTIN, "27AAAAA0000A1Z5", "27",
                SupplyType.INTRA_STATE, "INR", 100_000, 9_000, 9_000, 0);
    }

    private List<GstInvoiceLine> lines(GstInvoice inv) {
        GstCalculator.GstAmounts gst = GstCalculator.compute(100_000, 18, SupplyType.INTRA_STATE);
        return List.of(
                new GstInvoiceLine(
                        inv.getId(), inv.getMerchantId(), "Pro plan", "998314", 1, 100_000, 18,
                        100_000, gst));
    }

    @Test
    void generatesIrnMappingAndRequestShape() {
        Fixture f = fixture();

        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/eivital/v1.04/auth"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.clientId").value("cid"))
                .andExpect(jsonPath("$.username").value("user"))
                .andRespond(
                        withSuccess(
                                "{\"authToken\":\"TOKEN-XYZ\",\"expiresIn\":3600}",
                                MediaType.APPLICATION_JSON));

        String irn = "a".repeat(64);
        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/eicore/v1.03/invoice"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("authtoken", "TOKEN-XYZ"))
                .andExpect(header("gstin", SUPPLIER_GSTIN))
                .andExpect(jsonPath("$.DocDtls.No").value("INV-1"))
                .andExpect(jsonPath("$.SellerDtls.Gstin").value(SUPPLIER_GSTIN))
                .andExpect(jsonPath("$.BuyerDtls.Gstin").value("27AAAAA0000A1Z5"))
                .andExpect(jsonPath("$.ItemList[0].HsnCd").value("998314"))
                .andRespond(
                        withSuccess(
                                "{\"Irn\":\"" + irn + "\",\"AckNo\":\"112010036563\","
                                        + "\"AckDt\":\"2026-07-20 14:30:00\",\"SignedQRCode\":\"eyJx\"}",
                                MediaType.APPLICATION_JSON));

        GstInvoice inv = invoice();
        IrpResult result = f.adapter().generateIrn(inv, lines(inv));

        assertThat(result.irn()).isEqualTo(irn);
        assertThat(result.ackNo()).isEqualTo("112010036563");
        assertThat(result.signedQrCode()).isEqualTo("eyJx");
        assertThat(result.ackDate())
                .isEqualTo(LocalDateTime.parse("2026-07-20 14:30:00", NIC).atZone(INDIA).toInstant());
        f.server().verify();
    }

    @Test
    void cancelsIrnWithReason() {
        Fixture f = fixture();

        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/eivital/v1.04/auth"))
                .andRespond(withSuccess("{\"authToken\":\"TOK\",\"expiresIn\":3600}", MediaType.APPLICATION_JSON));
        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/eicore/v1.03/invoice/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("authtoken", "TOK"))
                .andExpect(jsonPath("$.Irn").value("IRN-123"))
                .andExpect(jsonPath("$.CnlRsn").value("1"))
                .andRespond(withSuccess("{\"Irn\":\"IRN-123\"}", MediaType.APPLICATION_JSON));

        f.adapter().cancelIrn("IRN-123", "1");
        f.server().verify();
    }

    @Test
    void cachesAuthTokenAcrossCalls() {
        Fixture f = fixture();

        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/eivital/v1.04/auth"))
                .andRespond(withSuccess("{\"authToken\":\"TOK\",\"expiresIn\":3600}", MediaType.APPLICATION_JSON));
        f.server()
                .expect(ExpectedCount.times(2), requestTo(BASE + "/eicore/v1.03/invoice"))
                .andExpect(header("authtoken", "TOK"))
                .andRespond(
                        withSuccess(
                                "{\"Irn\":\"" + "b".repeat(64) + "\",\"AckNo\":\"1\","
                                        + "\"AckDt\":\"2026-07-20 14:30:00\",\"SignedQRCode\":\"q\"}",
                                MediaType.APPLICATION_JSON));

        GstInvoice inv = invoice();
        f.adapter().generateIrn(inv, lines(inv));
        f.adapter().generateIrn(inv, lines(inv));

        f.server().verify(); // auth hit exactly once → token was cached
    }

    @Test
    void throwsWhenGenerateResponseMissingIrn() {
        Fixture f = fixture();
        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/eivital/v1.04/auth"))
                .andRespond(withSuccess("{\"authToken\":\"TOK\",\"expiresIn\":3600}", MediaType.APPLICATION_JSON));
        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/eicore/v1.03/invoice"))
                .andRespond(withSuccess("{\"ErrorDetails\":[{\"error_cd\":\"2150\"}]}", MediaType.APPLICATION_JSON));

        GstInvoice inv = invoice();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> f.adapter().generateIrn(inv, lines(inv)))
                .isInstanceOf(IrpException.class);
    }

    @Nested
    class Gating {

        private final ApplicationContextRunner runner =
                new ApplicationContextRunner()
                        .withBean(ObjectMapper.class, ObjectMapper::new)
                        .withBean(RestClient.Builder.class, RestClient::builder)
                        .withUserConfiguration(GatingConfig.class);

        @Test
        void offByDefault() {
            runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LiveIrpAdapter.class));
        }

        @Test
        void offWhenEnabledFalse() {
            runner.withPropertyValues("qeetpay.irp.enabled=false")
                    .run(ctx -> assertThat(ctx).doesNotHaveBean(LiveIrpAdapter.class));
        }

        @Test
        void onWhenEnabledTrue() {
            runner.withPropertyValues("qeetpay.irp.enabled=true", "qeetpay.irp.base-url=" + BASE)
                    .run(
                            ctx -> {
                                assertThat(ctx).hasSingleBean(LiveIrpAdapter.class);
                                assertThat(ctx.getBean(IrpAdapter.class)).isInstanceOf(LiveIrpAdapter.class);
                            });
        }

        @Configuration(proxyBeanMethods = false)
        @EnableConfigurationProperties(IrpProperties.class)
        @Import(LiveIrpAdapter.class)
        static class GatingConfig {}
    }
}
