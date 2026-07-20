package com.qeetgroup.qeetpay.checkout;

import com.qeetgroup.qeetpay.payments.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PUBLIC hosted-checkout API (PRD Module 01). Unlike the merchant-authenticated
 * {@code /v1/payment-links} endpoints, these carry <em>no</em> {@code X-Api-Key} and never call
 * {@code MerchantContext.require()} — the link {@code code} in the URL is the capability, exactly like a
 * Stripe/Razorpay checkout URL. {@code /v1/checkout/**} is permitted in every {@code SecurityConfig}
 * chain and stays rate-limited under {@code /v1/**} in prod.
 *
 * <ul>
 *   <li>{@code GET  /v1/checkout/{code}} — a checkout-safe public view of the link (no ids/ledger).
 *   <li>{@code POST /v1/checkout/{code}/pay} — pay the link, driving a real captured payment.
 * </ul>
 */
@Tag(
        name = "Checkout",
        description = "Public hosted-checkout — resolve a payment link by code and pay it with no merchant API key.")
@RestController
@RequestMapping("/v1/checkout")
public class CheckoutController {

    private final CheckoutService checkout;

    public CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @GetMapping("/{code}")
    public CheckoutService.PublicView get(@PathVariable String code) {
        return checkout.getPublic(code);
    }

    @PostMapping("/{code}/pay")
    public CheckoutService.PayResult pay(@PathVariable String code, @Valid @RequestBody PayRequest req) {
        return checkout.pay(code, req.method(), req.amountMinor(), req.customerName(), req.customerEmail());
    }

    // ── Request ──────────────────────────────────────────────────────────────────

    /**
     * A payer's checkout submission. {@code method} is required; {@code amountMinor} is required only for
     * open (payer-entered) links and must be positive when present; {@code customerName}/{@code customerEmail}
     * are optional and captured best-effort.
     */
    @Schema(name = "CheckoutPayRequest")
    public record PayRequest(
            @NotNull PaymentMethod method,
            @Positive Long amountMinor, // required only for open-amount links
            String customerName,
            String customerEmail) {}
}
