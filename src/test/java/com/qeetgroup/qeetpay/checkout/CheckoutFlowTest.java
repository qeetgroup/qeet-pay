package com.qeetgroup.qeetpay.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qeetgroup.qeetpay.merchants.MerchantService;
import com.qeetgroup.qeetpay.paymentlinks.PaymentLink;
import com.qeetgroup.qeetpay.paymentlinks.PaymentLinkService;
import com.qeetgroup.qeetpay.payments.PaymentMethod;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * PUBLIC hosted-checkout flow (PRD Module 01). A merchant creates a payment link WITH merchant context;
 * a payer then resolves and pays it through {@link CheckoutService} with <em>no</em> merchant context set
 * (proving the code → merchant routing map works without a tenant), the link becomes PAID, and the safe
 * public view exposes the merchant name but no internal ids. Unknown codes 404; an open-amount link still
 * requires an amount at pay time.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class CheckoutFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PaymentLinkService paymentLinks;
    @Autowired CheckoutService checkout;
    @Autowired MockMvc mvc;

    @Test
    void publicPathResolvesAndPaysWithoutMerchantContext() {
        UUID merchantId = newMerchant("Acme Retail");
        PaymentLink link = paymentLinks.createLink(merchantId, "Invoice #100", 250_000L, "INR", "order-100", null);
        String code = link.getCode();

        // No merchant context whatsoever — the code is the only capability the payer holds.
        MerchantContext.clear();

        CheckoutService.PublicView view = checkout.getPublic(code);
        assertThat(view.code()).isEqualTo(code);
        assertThat(view.title()).isEqualTo("Invoice #100");
        assertThat(view.amountMinor()).isEqualTo(250_000L);
        assertThat(view.currency()).isEqualTo("INR");
        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.merchantName()).isEqualTo("Acme Retail");
        // The safe view record has no accessor for payment id, reference or internal ids — nothing leaks.

        MerchantContext.clear();
        CheckoutService.PayResult result =
                checkout.pay(code, PaymentMethod.UPI, null, "Riya Sharma", "riya@example.com");
        assertThat(result.code()).isEqualTo(code);
        assertThat(result.status()).isEqualTo("PAID");
        assertThat(result.paid()).isTrue();

        MerchantContext.clear();
        assertThat(checkout.getPublic(code).status()).isEqualTo("PAID");
    }

    @Test
    void unknownCodeIsNotFound() {
        MerchantContext.clear();
        assertThatThrownBy(() -> checkout.getPublic("plink_does_not_exist"))
                .isInstanceOf(CheckoutNotFoundException.class);
    }

    @Test
    void openAmountLinkRequiresAmountAtPayTime() {
        UUID merchantId = newMerchant("Donations Co");
        PaymentLink open = paymentLinks.createLink(merchantId, "Donate", null, "INR", null, null);
        String code = open.getCode();

        MerchantContext.clear();
        // Open link paid with no amount → IllegalArgumentException (→ RFC-7807 400 via the global handler).
        assertThatThrownBy(() -> checkout.pay(code, PaymentMethod.CARD, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicHttpEndpointIsReachableWithoutAuthAndLeaksNoIds() throws Exception {
        UUID merchantId = newMerchant("Bright Books");
        PaymentLink link = paymentLinks.createLink(merchantId, "HTTP Invoice", 120_000L, "INR", "order-9", null);

        // No X-Api-Key, no X-Merchant-Id: the public chain must permit /v1/checkout/**.
        mvc.perform(get("/v1/checkout/" + link.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(link.getCode()))
                .andExpect(jsonPath("$.title").value("HTTP Invoice"))
                .andExpect(jsonPath("$.amountMinor").value(120_000))
                .andExpect(jsonPath("$.merchantName").value("Bright Books"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                // safe view: none of these must ever appear
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.paymentId").doesNotExist())
                .andExpect(jsonPath("$.reference").doesNotExist())
                .andExpect(jsonPath("$.merchantId").doesNotExist());
    }

    private UUID newMerchant(String name) {
        return merchants.create("co-" + UUID.randomUUID().toString().substring(0, 8), name).merchant().getId();
    }
}
