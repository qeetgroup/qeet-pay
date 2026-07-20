package com.qeetgroup.qeetpay.filing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Unit tests for the live GSTN filing adapter (TAD §7.4) using {@link MockRestServiceServer} — no
 * live HTTP. Proves the request shape (auth then save+file, credentials + GSTR-1 B2B / GSTR-3B
 * summary JSON, MMYYYY period, reference-id round-trip), the ARN response mapping, auth-token caching,
 * and the {@code qeetpay.gstn.enabled} gating (sandbox otherwise).
 */
class LiveGstnFilingAdapterTest {

    private static final String BASE = "https://gstn.test";
    private static final String GSTIN = "27ABCDE1234F1Z5";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID merchantId = UUID.randomUUID();

    private record Fixture(LiveGstnFilingAdapter adapter, MockRestServiceServer server) {}

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        GstnProperties props =
                new GstnProperties(
                        true, BASE, "cid", "csecret", "user", "pass", GSTIN, null, null, 0);
        return new Fixture(new LiveGstnFilingAdapter(props, objectMapper, builder), server);
    }

    private GstReturn gstr1() {
        GstReturn ret = new GstReturn(merchantId, GstReturnType.GSTR1, "2026-07");
        ret.prepare(1, 100_000, 9_000, 9_000, 0);
        return ret;
    }

    private GstReturnLine line(GstReturn ret) {
        return new GstReturnLine(
                ret.getId(), merchantId, UUID.randomUUID(), "INV-1", "27AAAAA0000A1Z5", "27",
                "INTRA_STATE", 100_000, 9_000, 9_000, 0);
    }

    @Test
    void filesGstr1AndReturnsArn() {
        Fixture f = fixture();
        GstReturn ret = gstr1();

        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/authenticate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.gstin").value(GSTIN))
                .andExpect(jsonPath("$.clientId").value("cid"))
                .andRespond(withSuccess("{\"authToken\":\"TOK\",\"expiresIn\":3600}", MediaType.APPLICATION_JSON));

        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/returns/gstr1/save"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("authtoken", "TOK"))
                .andExpect(header("gstin", GSTIN))
                .andExpect(jsonPath("$.gstin").value(GSTIN))
                .andExpect(jsonPath("$.fp").value("072026"))
                .andExpect(jsonPath("$.b2b[0].ctin").value("27AAAAA0000A1Z5"))
                .andExpect(jsonPath("$.b2b[0].inv[0].inum").value("INV-1"))
                .andRespond(withSuccess("{\"status\":\"P\",\"reference_id\":\"REF-777\"}", MediaType.APPLICATION_JSON));

        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/returns/gstr1/file"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.reference_id").value("REF-777"))
                .andExpect(jsonPath("$.ret_period").value("072026"))
                .andRespond(withSuccess("{\"arn\":\"AA270720260000123\",\"status\":\"Filed\"}", MediaType.APPLICATION_JSON));

        String arn = f.adapter().file(ret, List.of(line(ret)));

        assertThat(arn).isEqualTo("AA270720260000123");
        f.server().verify();
    }

    @Test
    void filesGstr3bAsSummaryOnly() {
        Fixture f = fixture();
        GstReturn ret = new GstReturn(merchantId, GstReturnType.GSTR3B, "2026-07");
        ret.prepare(1, 100_000, 0, 0, 18_000); // inter-state → IGST

        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/authenticate"))
                .andRespond(withSuccess("{\"authToken\":\"TOK\",\"expiresIn\":3600}", MediaType.APPLICATION_JSON));
        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/returns/gstr3b/save"))
                .andExpect(jsonPath("$.sup_details.osup_det.txval").exists())
                .andExpect(jsonPath("$.b2b").doesNotExist())
                .andRespond(withSuccess("{\"reference_id\":\"REF-9\"}", MediaType.APPLICATION_JSON));
        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/returns/gstr3b/file"))
                .andRespond(withSuccess("{\"arn\":\"AA270720269999999\"}", MediaType.APPLICATION_JSON));

        String arn = f.adapter().file(ret, List.of());

        assertThat(arn).isEqualTo("AA270720269999999");
        f.server().verify();
    }

    @Test
    void cachesAuthTokenAcrossFilings() {
        Fixture f = fixture();
        GstReturn ret = gstr1();

        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/authenticate"))
                .andRespond(withSuccess("{\"authToken\":\"TOK\",\"expiresIn\":3600}", MediaType.APPLICATION_JSON));
        f.server()
                .expect(ExpectedCount.times(2), requestTo(BASE + "/returns/gstr1/save"))
                .andRespond(withSuccess("{\"reference_id\":\"REF\"}", MediaType.APPLICATION_JSON));
        f.server()
                .expect(ExpectedCount.times(2), requestTo(BASE + "/returns/gstr1/file"))
                .andRespond(withSuccess("{\"arn\":\"AA270720260000001\"}", MediaType.APPLICATION_JSON));

        f.adapter().file(ret, List.of(line(ret)));
        f.adapter().file(ret, List.of(line(ret)));

        f.server().verify(); // auth hit exactly once → token was cached
    }

    @Test
    void throwsWhenFileResponseMissingArn() {
        Fixture f = fixture();
        GstReturn ret = gstr1();

        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/authenticate"))
                .andRespond(withSuccess("{\"authToken\":\"TOK\",\"expiresIn\":3600}", MediaType.APPLICATION_JSON));
        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/returns/gstr1/save"))
                .andRespond(withSuccess("{\"reference_id\":\"REF\"}", MediaType.APPLICATION_JSON));
        f.server()
                .expect(ExpectedCount.once(), requestTo(BASE + "/returns/gstr1/file"))
                .andRespond(withSuccess("{\"status\":\"ERROR\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.adapter().file(ret, List.of(line(ret))))
                .isInstanceOf(GstnFilingException.class);
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
            runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LiveGstnFilingAdapter.class));
        }

        @Test
        void offWhenEnabledFalse() {
            runner.withPropertyValues("qeetpay.gstn.enabled=false")
                    .run(ctx -> assertThat(ctx).doesNotHaveBean(LiveGstnFilingAdapter.class));
        }

        @Test
        void onWhenEnabledTrue() {
            runner.withPropertyValues("qeetpay.gstn.enabled=true", "qeetpay.gstn.base-url=" + BASE)
                    .run(
                            ctx -> {
                                assertThat(ctx).hasSingleBean(LiveGstnFilingAdapter.class);
                                assertThat(ctx.getBean(GstnFilingAdapter.class))
                                        .isInstanceOf(LiveGstnFilingAdapter.class);
                            });
        }

        @Configuration(proxyBeanMethods = false)
        @EnableConfigurationProperties(GstnProperties.class)
        @Import(LiveGstnFilingAdapter.class)
        static class GatingConfig {}
    }
}
