package com.qeetgroup.qeetpay.kyb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Live KYB adapter (PRD Module 19) against a mocked SurePASS/SignDesk-style provider via
 * {@link MockRestServiceServer} — no network. Verifies request routing + auth header, VERIFIED /
 * REJECTED mapping, and the fail-closed behaviour on a provider error.
 */
class LiveKybAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = "https://kyb.example.test";

    /** Builds a MockRestServiceServer-bound adapter; expectations are set on the returned server. */
    private record Fixture(LiveKybAdapter adapter, MockRestServiceServer server) {}

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KybProperties props = new KybProperties(true, BASE, "test-token", "client-42");
        return new Fixture(new LiveKybAdapter(props, builder, MAPPER), server);
    }

    @Test
    void verifyPanVerifiedWhenProviderReturnsValid() {
        Fixture f = fixture();
        f.server()
                .expect(requestTo(BASE + "/pan"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(header("X-Client-Id", "client-42"))
                .andRespond(withSuccess("{\"verified\":true}", MediaType.APPLICATION_JSON));

        assertThat(f.adapter().verifyPan("ABCDE1234F")).isEqualTo(MerchantKyb.VERIFIED);
        f.server().verify();
    }

    @Test
    void verifyPanRejectedWhenProviderReturnsInvalid() {
        Fixture f = fixture();
        f.server()
                .expect(requestTo(BASE + "/pan"))
                .andRespond(withSuccess("{\"verified\":false}", MediaType.APPLICATION_JSON));

        assertThat(f.adapter().verifyPan("ZZZZZ9999Z")).isEqualTo(MerchantKyb.REJECTED);
        f.server().verify();
    }

    @Test
    void verifyGstinAcceptsStatusField() {
        Fixture f = fixture();
        f.server()
                .expect(requestTo(BASE + "/gstin"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"VALID\"}", MediaType.APPLICATION_JSON));

        assertThat(f.adapter().verifyGstin("27ABCDE1234F1Z5")).isEqualTo(MerchantKyb.VERIFIED);
        f.server().verify();
    }

    @Test
    void verifyBankPennyDropVerified() {
        Fixture f = fixture();
        f.server()
                .expect(requestTo(BASE + "/bank/penny-drop"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"verified\":true}", MediaType.APPLICATION_JSON));

        assertThat(f.adapter().verifyBankAccount("9876543210", "HDFC0001234")).isEqualTo(MerchantKyb.VERIFIED);
        f.server().verify();
    }

    @Test
    void failsClosedOnProviderError() {
        Fixture f = fixture();
        f.server().expect(requestTo(BASE + "/pan")).andRespond(withServerError());

        // A provider outage must never auto-approve KYB — the call fails closed to REJECTED.
        assertThat(f.adapter().verifyPan("ABCDE1234F")).isEqualTo(MerchantKyb.REJECTED);
        f.server().verify();
    }
}
