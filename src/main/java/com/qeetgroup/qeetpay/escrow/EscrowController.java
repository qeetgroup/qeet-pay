package com.qeetgroup.qeetpay.escrow;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
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
 * Digital-escrow API (PRD Module 10): hold a buyer's funds, release (part) to the seller on
 * confirmation, refund (part) to the buyer, and read agreements with their event history.
 */
@Tag(
        name = "Escrow",
        description = "Conditional escrow — hold buyer funds, release (partly) to the seller or refund to the buyer, with event history.")
@RestController
@RequestMapping("/v1/escrow")
public class EscrowController {

    private final EscrowService escrow;

    public EscrowController(EscrowService escrow) {
        this.escrow = escrow;
    }

    @PostMapping
    public ResponseEntity<AgreementView> hold(@Valid @RequestBody HoldRequest req) {
        EscrowService.AgreementWithEvents held =
                escrow.hold(MerchantContext.require(), req.buyerRef(), req.sellerRef(),
                        req.amountMinor(), req.currency(), req.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(AgreementView.of(held));
    }

    @GetMapping
    public List<AgreementSummary> list() {
        return escrow.listAgreements(MerchantContext.require()).stream().map(AgreementSummary::of).toList();
    }

    @GetMapping("/{escrowId}")
    public AgreementView get(@PathVariable UUID escrowId) {
        return AgreementView.of(escrow.getAgreement(MerchantContext.require(), escrowId));
    }

    @PostMapping("/{escrowId}/release")
    public AgreementView release(@PathVariable UUID escrowId, @Valid @RequestBody MovementRequest req) {
        UUID merchantId = MerchantContext.require();
        escrow.release(merchantId, escrowId, req.amountMinor(), req.note());
        return AgreementView.of(escrow.getAgreement(merchantId, escrowId));
    }

    @PostMapping("/{escrowId}/refund")
    public AgreementView refund(@PathVariable UUID escrowId, @Valid @RequestBody MovementRequest req) {
        UUID merchantId = MerchantContext.require();
        escrow.refund(merchantId, escrowId, req.amountMinor(), req.note());
        return AgreementView.of(escrow.getAgreement(merchantId, escrowId));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record HoldRequest(
            @NotBlank String buyerRef,
            @NotBlank String sellerRef,
            @NotNull @Positive Long amountMinor,
            @NotBlank String currency,
            String description) {}

    public record MovementRequest(@NotNull @Positive Long amountMinor, String note) {}

    public record EventView(String type, long amountMinor, String ledgerEntryId, String note, Instant createdAt) {
        static EventView of(EscrowEvent e) {
            return new EventView(
                    e.getType().name(), e.getAmountMinor(), e.getLedgerEntryId().toString(),
                    e.getNote(), e.getCreatedAt());
        }
    }

    public record AgreementSummary(
            String id, String buyerRef, String sellerRef, String currency, long amountMinor,
            long releasedMinor, long refundedMinor, long remainingMinor, String status,
            Instant createdAt, Instant closedAt) {
        static AgreementSummary of(EscrowAgreement a) {
            return new AgreementSummary(
                    a.getId().toString(), a.getBuyerRef(), a.getSellerRef(), a.getCurrency(), a.getAmountMinor(),
                    a.getReleasedMinor(), a.getRefundedMinor(), a.remainingMinor(), a.getStatus().name(),
                    a.getCreatedAt(), a.getClosedAt());
        }
    }

    public record AgreementView(AgreementSummary escrow, List<EventView> events) {
        static AgreementView of(EscrowService.AgreementWithEvents a) {
            return new AgreementView(
                    AgreementSummary.of(a.agreement()),
                    a.events().stream().map(EventView::of).toList());
        }
    }
}
