package com.qeetgroup.qeetpay.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Verifies the credential-gated {@link ZohoBooksConnector} posts a manual journal to Zoho Books and
 * lifts the returned {@code journal_id} as the external reference — using {@link MockRestServiceServer}
 * so no live HTTP is made. (The default-profile fallback to the sandbox connector is covered in
 * {@link AccountingSyncTest#zohoTargetUsesSandboxConnectorWhenNoCreds}.)
 */
class ZohoBooksConnectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postsJournalAndReturnsExternalRef() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://books.zoho.test/api/v3");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(Matchers.containsString("/journals")))
                .andExpect(requestTo(Matchers.containsString("organization_id=ORG-1")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"journal\":{\"journal_id\":\"JN-123\"}}", MediaType.APPLICATION_JSON));

        ZohoBooksConnector connector = new ZohoBooksConnector(builder.build(), "ORG-1", objectMapper);

        SyncResult result = connector.push(samplePayload(), null);

        assertThat(result.success()).isTrue();
        assertThat(result.externalRef()).isEqualTo("JN-123");
        assertThat(result.recordCount()).isEqualTo(1);
        server.verify();
    }

    @Test
    void responseWithoutJournalIdFallsBackToDefaultRef() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://books.zoho.test/api/v3");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(Matchers.containsString("/journals")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ZohoBooksConnector connector = new ZohoBooksConnector(builder.build(), "ORG-1", objectMapper);

        // The POST succeeded but the body carried no journal_id → success with a fallback ref.
        SyncResult result = connector.push(samplePayload(), null);
        assertThat(result.success()).isTrue();
        assertThat(result.externalRef()).isEqualTo("zoho-journal");
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
}
