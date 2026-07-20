package com.qeetgroup.qeetpay.ondc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ONDC multi-party settlement flow (PRD Module 13.4): a network order holds the collected gross
 * (escrow-like) on creation and, post-fulfilment, settles it into commission revenue, tax payable
 * (commission GST + TCS §52), and per-party payables — with every account netting back to zero when
 * the order is cancelled by an offsetting entry.
 */
class OndcSettlementFlowTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired OndcService ondc;
    @Autowired LedgerService ledger;

    @Test
    void createHoldsGrossThenSettlesBalancedAcrossParties() {
        UUID merchantId = newMerchant();

        OndcService.OrderWithLines order =
                ondc.createOrder(
                        merchantId, "ondc_order_1", "buyer.app", "seller.app", "INR",
                        List.of(
                                new OndcLineInput("seller-a", PartyRole.SELLER, 100_000, 500, null, null),
                                new OndcLineInput("logi-1", PartyRole.LOGISTICS, 100_000, 500, null, null)));

        // header totals
        assertThat(order.lines()).hasSize(2);
        assertThat(order.order().getStatus()).isEqualTo(OndcOrderStatus.CREATED);
        assertThat(order.order().getGrossMinor()).isEqualTo(200_000);
        assertThat(order.order().getCommissionMinor()).isEqualTo(10_000);
        assertThat(order.order().getCommissionGstMinor()).isEqualTo(1_800);
        assertThat(order.order().getTcsMinor()).isEqualTo(2_000);
        assertThat(order.order().getPartyNetMinor()).isEqualTo(186_200);
        // every paise attributed
        assertThat(order.order().getCommissionMinor()
                        + order.order().getCommissionGstMinor()
                        + order.order().getTcsMinor()
                        + order.order().getPartyNetMinor())
                .isEqualTo(order.order().getGrossMinor());

        // hold: settlement debited by gross; funds parked in ondc_hold liability.
        assertThat(balance(merchantId, "settlement")).isEqualTo(200_000);
        assertThat(balance(merchantId, "ondc_hold")).isEqualTo(200_000);
        assertThat(balance(merchantId, "revenue")).isZero();
        assertThat(balance(merchantId, "liability")).isZero();

        // settlement is post-fulfilment only.
        UUID orderId = order.order().getId();
        assertThatThrownBy(() -> ondc.settle(merchantId, orderId)).isInstanceOf(IllegalStateException.class);

        ondc.fulfill(merchantId, orderId);
        assertThat(ondc.getOrder(merchantId, orderId).order().getStatus()).isEqualTo(OndcOrderStatus.FULFILLED);

        ondc.settle(merchantId, orderId);
        assertThat(ondc.getOrder(merchantId, orderId).order().getStatus()).isEqualTo(OndcOrderStatus.SETTLED);

        // hold released: revenue = commission, tax_payable = gst + tcs, liability = party net; hold nets 0.
        assertThat(balance(merchantId, "settlement")).isEqualTo(200_000);
        assertThat(balance(merchantId, "ondc_hold")).isZero();
        assertThat(balance(merchantId, "revenue")).isEqualTo(10_000);
        assertThat(balance(merchantId, "tax_payable")).isEqualTo(3_800);
        assertThat(balance(merchantId, "liability")).isEqualTo(186_200);
    }

    @Test
    void cancellingASettledOrderOffsetsEveryAccount() {
        UUID merchantId = newMerchant();
        OndcService.OrderWithLines order =
                ondc.createOrder(
                        merchantId, "ondc_order_2", "buyer.app", "seller.app", "INR",
                        List.of(new OndcLineInput("seller-x", PartyRole.SELLER, 100_000, 300, null, null)));
        UUID orderId = order.order().getId();
        ondc.fulfill(merchantId, orderId);
        ondc.settle(merchantId, orderId);

        OndcOrder cancelled = ondc.cancel(merchantId, orderId);
        assertThat(cancelled.getStatus()).isEqualTo(OndcOrderStatus.CANCELLED);
        assertThat(cancelled.getReversalEntryId()).isNotNull();

        assertThat(balance(merchantId, "settlement")).isZero();
        assertThat(balance(merchantId, "ondc_hold")).isZero();
        assertThat(balance(merchantId, "revenue")).isZero();
        assertThat(balance(merchantId, "tax_payable")).isZero();
        assertThat(balance(merchantId, "liability")).isZero();

        // Cancelling again is a no-op.
        assertThat(ondc.cancel(merchantId, orderId).getStatus()).isEqualTo(OndcOrderStatus.CANCELLED);
    }

    @Test
    void cancellingBeforeSettlementReversesTheHold() {
        UUID merchantId = newMerchant();
        OndcService.OrderWithLines order =
                ondc.createOrder(
                        merchantId, "ondc_order_3", "buyer.app", "seller.app", "INR",
                        List.of(new OndcLineInput("seller-y", PartyRole.SELLER, 50_000, 400, null, null)));
        UUID orderId = order.order().getId();

        ondc.cancel(merchantId, orderId);

        assertThat(balance(merchantId, "settlement")).isZero();
        assertThat(balance(merchantId, "ondc_hold")).isZero();
        assertThat(balance(merchantId, "revenue")).isZero();
        assertThat(balance(merchantId, "liability")).isZero();
    }

    @Test
    void listAndGetReturnPersistedOrderUnderRls() {
        UUID merchantId = newMerchant();
        OndcService.OrderWithLines order =
                ondc.createOrder(
                        merchantId, "ondc_order_4", "buyer.app", "seller.app", "INR",
                        List.of(new OndcLineInput("seller-z", PartyRole.SELLER, 75_000, 250, null, null)));

        assertThat(ondc.listOrders(merchantId))
                .extracting(OndcOrder::getNetworkOrderId)
                .contains("ondc_order_4");
        assertThat(ondc.getOrder(merchantId, order.order().getId()).lines()).hasSize(1);
    }

    private UUID newMerchant() {
        return merchants.create("ondc-" + UUID.randomUUID().toString().substring(0, 8), "ONDC Operator")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
