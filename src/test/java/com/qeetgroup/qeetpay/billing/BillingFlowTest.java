package com.qeetgroup.qeetpay.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Billing flow (TAD Module 03): a subscription issues an OPEN invoice; paying it recognises revenue
 * by posting to the ledger, and is idempotent (never double-posts).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BillingFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired BillingService billing;
    @Autowired LedgerService ledger;

    @Test
    void subscriptionIssuesInvoiceAndPayingItPostsRevenue() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "pro", "Pro", 299900, "INR", BillingInterval.MONTH);

        BillingService.Subscribed sub = billing.createSubscription(merchantId, plan.getId(), "cust_1");
        assertThat(sub.subscription().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.firstInvoice().getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(sub.firstInvoice().getAmountMinor()).isEqualTo(299900);

        Invoice paid = billing.payInvoice(merchantId, sub.firstInvoice().getId());
        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paid.getLedgerEntryId()).isNotNull();
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "revenue"))).isEqualTo(299900);
    }

    @Test
    void payingInvoiceIsIdempotent() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "basic", "Basic", 100000, "INR", BillingInterval.YEAR);
        var invoiceId =
                billing.createSubscription(merchantId, plan.getId(), "cust_2").firstInvoice().getId();

        billing.payInvoice(merchantId, invoiceId);
        billing.payInvoice(merchantId, invoiceId); // no-op replay

        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "revenue"))).isEqualTo(100000);
    }

    private UUID newMerchant() {
        return merchants.create("bill-" + UUID.randomUUID().toString().substring(0, 8), "Bill Co")
                .merchant()
                .getId();
    }

    private UUID account(UUID merchantId, String code) {
        return ledger.accountByCode(merchantId, code).getId();
    }
}
