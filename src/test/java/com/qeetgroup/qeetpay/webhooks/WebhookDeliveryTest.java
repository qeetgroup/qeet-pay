package com.qeetgroup.qeetpay.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Webhook delivery integration tests using MockRestServiceServer (no live HTTP). */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class WebhookDeliveryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired WebhookEndpointRepository endpointRepo;
    @Autowired WebhookDeliveryRepository deliveryRepo;
    @Autowired MerchantScope merchantScope;
    @Autowired ObjectMapper objectMapper;

    /** Build a service instance with a stub RestTemplate that can be mocked per test. */
    private WebhookDeliveryService service(RestTemplate restTemplate) {
        return new WebhookDeliveryService(endpointRepo, deliveryRepo, merchantScope, objectMapper, restTemplate);
    }

    @Test
    void deliverySuccessIsRecorded() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        server.expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo("http://localhost:9999/hook"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.header("X-QeetPay-Signature",
                        org.hamcrest.Matchers.startsWith("sha256=")))
                .andRespond(MockRestResponseCreators.withSuccess("ok", MediaType.TEXT_PLAIN));

        UUID merchantId = newMerchant();
        merchantScope.apply(merchantId);
        WebhookEndpoint ep = endpointRepo.save(
                new WebhookEndpoint(merchantId, "http://localhost:9999/hook", "[\"*\"]", "test_secret"));

        service(rt).fanOut(merchantId, "payment.captured", Map.of("amount", 5000, "currency", "INR"));

        List<WebhookDelivery> logs = deliveryRepo.findByEndpointIdOrderByCreatedAtDesc(ep.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo(WebhookDelivery.DELIVERED);
        assertThat(logs.get(0).getLastResponseCode()).isEqualTo(200);
        server.verify();
    }

    @Test
    void endpointNotSubscribedToEventIsSkipped() {
        UUID merchantId = newMerchant();
        merchantScope.apply(merchantId);
        WebhookEndpoint ep = endpointRepo.save(
                new WebhookEndpoint(merchantId, "http://localhost:9998/hook", "[\"payout.paid\"]", "secret2"));

        service(new RestTemplate()).fanOut(merchantId, "payment.captured", Map.of("amount", 1000));

        List<WebhookDelivery> logs = deliveryRepo.findByEndpointIdOrderByCreatedAtDesc(ep.getId());
        assertThat(logs).isEmpty();
    }

    @Test
    void registerAndListEndpoints() {
        UUID merchantId = newMerchant();
        WebhookDeliveryService svc = service(new RestTemplate());
        svc.register(merchantId, "http://example.com/h1", "[\"*\"]", "s1");
        svc.register(merchantId, "http://example.com/h2", "[\"invoice.paid\"]", "s2");
        assertThat(svc.listEndpoints(merchantId)).hasSize(2);
    }

    @Test
    void disableEndpointRemovesFromActiveList() {
        UUID merchantId = newMerchant();
        WebhookDeliveryService svc = service(new RestTemplate());
        WebhookEndpoint ep = svc.register(merchantId, "http://example.com/disable", "[\"*\"]", "s3");
        assertThat(svc.listEndpoints(merchantId)).hasSize(1);
        svc.disable(merchantId, ep.getId());
        assertThat(svc.listEndpoints(merchantId)).isEmpty();
    }

    private UUID newMerchant() {
        return merchants.create("wh-" + UUID.randomUUID().toString().substring(0, 8), "Webhook Co")
                .merchant().getId();
    }
}
