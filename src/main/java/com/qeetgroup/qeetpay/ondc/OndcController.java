package com.qeetgroup.qeetpay.ondc;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ONDC payment API (PRD Module 13.4): create a network order with per-party lines (holding the
 * collected gross), fulfil it, settle it post-fulfilment (releasing each party's net after commission
 * + GST + statutory TCS §52), and cancel via an offsetting entry.
 */
@Tag(
        name = "ONDC",
        description =
                "Multi-party settlement for ONDC network orders: create with per-party lines, fulfil, settle post-fulfilment (commission + GST + statutory TCS §52), and cancel.")
@RestController
@RequestMapping("/v1/ondc")
public class OndcController {

    private final OndcService ondc;

    public OndcController(OndcService ondc) {
        this.ondc = ondc;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderView> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        List<OndcLineInput> lines =
                req.lines().stream()
                        .map(l -> new OndcLineInput(
                                l.partyRef(), parseRole(l.role()), l.grossMinor(),
                                l.commissionBps(), l.commissionGstRate(), l.tcsBps()))
                        .toList();
        OndcService.OrderWithLines created =
                ondc.createOrder(
                        MerchantContext.require(), req.networkOrderId(), req.buyerApp(), req.sellerApp(),
                        req.currency(), lines);
        return ResponseEntity.ok(OrderView.of(created));
    }

    @GetMapping("/orders")
    public List<OrderSummary> listOrders() {
        return ondc.listOrders(MerchantContext.require()).stream().map(OrderSummary::of).toList();
    }

    @GetMapping("/orders/{orderId}")
    public OrderView getOrder(@PathVariable UUID orderId) {
        return OrderView.of(ondc.getOrder(MerchantContext.require(), orderId));
    }

    @PostMapping("/orders/{orderId}/fulfill")
    public OrderView fulfill(@PathVariable UUID orderId) {
        UUID merchantId = MerchantContext.require();
        ondc.fulfill(merchantId, orderId);
        return OrderView.of(ondc.getOrder(merchantId, orderId));
    }

    @PostMapping("/orders/{orderId}/settle")
    public OrderView settle(@PathVariable UUID orderId) {
        UUID merchantId = MerchantContext.require();
        ondc.settle(merchantId, orderId);
        return OrderView.of(ondc.getOrder(merchantId, orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public OrderView cancel(@PathVariable UUID orderId) {
        UUID merchantId = MerchantContext.require();
        ondc.cancel(merchantId, orderId);
        return OrderView.of(ondc.getOrder(merchantId, orderId));
    }

    private static PartyRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return PartyRole.SELLER;
        }
        try {
            return PartyRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown party role '" + role + "'");
        }
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record OrderLineRequest(
            @NotBlank String partyRef,
            String role,
            @NotNull @Positive Long grossMinor,
            Integer commissionBps,
            Integer commissionGstRate,
            Integer tcsBps) {}

    public record CreateOrderRequest(
            @NotBlank String networkOrderId,
            @NotBlank String buyerApp,
            @NotBlank String sellerApp,
            @NotBlank String currency,
            @NotEmpty List<@Valid OrderLineRequest> lines) {}

    public record OrderLineView(
            String partyRef, String role, long grossMinor, long commissionMinor,
            long commissionGstMinor, long tcsMinor, long netMinor) {
        static OrderLineView of(OndcSettlementLine l) {
            return new OrderLineView(
                    l.getPartyRef(), l.getPartyRole().name(), l.getGrossMinor(), l.getCommissionMinor(),
                    l.getCommissionGstMinor(), l.getTcsMinor(), l.getNetMinor());
        }
    }

    public record OrderSummary(
            String id, String networkOrderId, String buyerApp, String sellerApp, String currency,
            long grossMinor, long commissionMinor, long commissionGstMinor, long tcsMinor,
            long partyNetMinor, int partyCount, String status, String holdEntryId, String settleEntryId,
            Instant createdAt) {
        static OrderSummary of(OndcOrder o) {
            return new OrderSummary(
                    o.getId().toString(), o.getNetworkOrderId(), o.getBuyerApp(), o.getSellerApp(),
                    o.getCurrency(), o.getGrossMinor(), o.getCommissionMinor(), o.getCommissionGstMinor(),
                    o.getTcsMinor(), o.getPartyNetMinor(), o.getPartyCount(), o.getStatus().name(),
                    o.getHoldEntryId().toString(),
                    o.getSettleEntryId() != null ? o.getSettleEntryId().toString() : null,
                    o.getCreatedAt());
        }
    }

    public record OrderView(OrderSummary order, List<OrderLineView> lines) {
        static OrderView of(OndcService.OrderWithLines o) {
            return new OrderView(
                    OrderSummary.of(o.order()),
                    o.lines().stream().map(OrderLineView::of).toList());
        }
    }
}
