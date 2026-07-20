package com.qeetgroup.qeetpay.paymentlinks;

import com.qeetgroup.qeetpay.payments.PaymentMethod;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment-links API (PRD Module 01): create a shareable link (fixed or open amount), read/list/cancel
 * links, and pay a link by its code (which captures a real payment).
 */
@Tag(
        name = "Payment Links",
        description = "Create, read, list and cancel shareable payment links (fixed or open amount), and pay a link by its code.")
@RestController
@RequestMapping("/v1/payment-links")
public class PaymentLinkController {

    private final PaymentLinkService paymentLinks;

    public PaymentLinkController(PaymentLinkService paymentLinks) {
        this.paymentLinks = paymentLinks;
    }

    @PostMapping
    public ResponseEntity<LinkView> create(@Valid @RequestBody CreateLinkRequest req) {
        PaymentLink link =
                paymentLinks.createLink(
                        MerchantContext.require(), req.title(), req.amountMinor(), req.currency(),
                        req.reference(), req.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(LinkView.of(link));
    }

    @GetMapping
    public List<LinkView> list() {
        return paymentLinks.listLinks(MerchantContext.require()).stream().map(LinkView::of).toList();
    }

    @GetMapping("/{linkId}")
    public LinkView get(@PathVariable UUID linkId) {
        return LinkView.of(paymentLinks.getLink(MerchantContext.require(), linkId));
    }

    @GetMapping("/by-code/{code}")
    public LinkView getByCode(@PathVariable String code) {
        return LinkView.of(paymentLinks.getByCode(MerchantContext.require(), code));
    }

    @PostMapping("/{code}/pay")
    public LinkView pay(@PathVariable String code, @Valid @RequestBody PayRequest req) {
        return LinkView.of(
                paymentLinks.pay(
                        MerchantContext.require(), code, req.method(), req.amountMinor(),
                        req.simulateFailure() != null && req.simulateFailure()));
    }

    @PostMapping("/{linkId}/cancel")
    public LinkView cancel(@PathVariable UUID linkId) {
        return LinkView.of(paymentLinks.cancel(MerchantContext.require(), linkId));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record CreateLinkRequest(
            @NotBlank String title,
            @Positive Long amountMinor,          // null = open amount (payer enters it)
            @NotBlank String currency,
            String reference,
            Instant expiresAt) {}

    @Schema(name = "PaymentLinkPayRequest")
    public record PayRequest(
            @NotNull PaymentMethod method,
            @Positive Long amountMinor,           // required only for open-amount links
            Boolean simulateFailure) {}

    public record LinkView(
            String id, String code, String title, Long amountMinor, String currency, String reference,
            String status, String paymentId, Instant expiresAt, Instant createdAt, Instant paidAt) {
        static LinkView of(PaymentLink l) {
            return new LinkView(
                    l.getId().toString(), l.getCode(), l.getTitle(), l.getAmountMinor(), l.getCurrency(),
                    l.getReference(), l.getStatus().name(),
                    l.getPaymentId() == null ? null : l.getPaymentId().toString(),
                    l.getExpiresAt(), l.getCreatedAt(), l.getPaidAt());
        }
    }
}
