package com.qeetgroup.qeetpay.marketplace;

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
 * Marketplace API (TAD §5 "Marketplace"): register sellers, split a collected payment across them
 * with commission + statutory TCS/TDS, and read/cancel splits.
 */
@Tag(
        name = "Marketplace",
        description = "Register sellers and split a collected payment across them with commission + statutory TCS/TDS; read and cancel splits.")
@RestController
@RequestMapping("/v1/marketplace")
public class MarketplaceController {

    private final MarketplaceService marketplace;

    public MarketplaceController(MarketplaceService marketplace) {
        this.marketplace = marketplace;
    }

    // ── Sellers ────────────────────────────────────────────────────────────

    @PostMapping("/sellers")
    public ResponseEntity<SellerView> registerSeller(@Valid @RequestBody RegisterSellerRequest req) {
        MarketplaceSeller seller =
                marketplace.registerSeller(
                        MerchantContext.require(), req.sellerRef(), req.name(), req.gstin(), req.pan(),
                        req.commissionBps() == null ? 0 : req.commissionBps());
        return ResponseEntity.ok(SellerView.of(seller));
    }

    @GetMapping("/sellers")
    public List<SellerView> listSellers() {
        return marketplace.listSellers(MerchantContext.require()).stream().map(SellerView::of).toList();
    }

    @PostMapping("/sellers/{sellerId}/suspend")
    public SellerView suspend(@PathVariable UUID sellerId) {
        return SellerView.of(marketplace.setSellerStatus(MerchantContext.require(), sellerId, false));
    }

    @PostMapping("/sellers/{sellerId}/activate")
    public SellerView activate(@PathVariable UUID sellerId) {
        return SellerView.of(marketplace.setSellerStatus(MerchantContext.require(), sellerId, true));
    }

    // ── Splits ─────────────────────────────────────────────────────────────

    @PostMapping("/splits")
    public ResponseEntity<SplitView> createSplit(@Valid @RequestBody CreateSplitRequest req) {
        List<SplitLineInput> lines =
                req.lines().stream()
                        .map(l -> new SplitLineInput(
                                l.sellerRef(), l.grossMinor(), l.commissionBps(),
                                l.commissionGstRate(), l.tcsBps(), l.tdsBps()))
                        .toList();
        MarketplaceService.SplitWithItems created =
                marketplace.createSplit(
                        MerchantContext.require(), req.paymentId(), req.sourceRef(), req.currency(), lines);
        return ResponseEntity.ok(SplitView.of(created));
    }

    @GetMapping("/splits")
    public List<SplitSummary> listSplits() {
        return marketplace.listSplits(MerchantContext.require()).stream().map(SplitSummary::of).toList();
    }

    @GetMapping("/splits/{splitId}")
    public SplitView getSplit(@PathVariable UUID splitId) {
        return SplitView.of(marketplace.getSplit(MerchantContext.require(), splitId));
    }

    @PostMapping("/splits/{splitId}/cancel")
    public SplitView cancelSplit(@PathVariable UUID splitId) {
        UUID merchantId = MerchantContext.require();
        marketplace.cancelSplit(merchantId, splitId);
        return SplitView.of(marketplace.getSplit(merchantId, splitId));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record RegisterSellerRequest(
            @NotBlank String sellerRef, @NotBlank String name, String gstin, String pan, Integer commissionBps) {}

    public record SplitLineRequest(
            @NotBlank String sellerRef,
            @NotNull @Positive Long grossMinor,
            Integer commissionBps,
            Integer commissionGstRate,
            Integer tcsBps,
            Integer tdsBps) {}

    public record CreateSplitRequest(
            UUID paymentId,
            String sourceRef,
            @NotBlank String currency,
            @NotEmpty List<@Valid SplitLineRequest> lines) {}

    public record SellerView(
            String id, String sellerRef, String name, String gstin, String pan,
            int commissionBps, String status, Instant createdAt) {
        static SellerView of(MarketplaceSeller s) {
            return new SellerView(
                    s.getId().toString(), s.getSellerRef(), s.getName(), s.getGstin(), s.getPan(),
                    s.getCommissionBps(), s.getStatus().name(), s.getCreatedAt());
        }
    }

    public record SplitItemView(
            String sellerRef, long grossMinor, long commissionMinor, long commissionGstMinor,
            long tcsMinor, long tdsMinor, long netMinor) {
        static SplitItemView of(SplitItem i) {
            return new SplitItemView(
                    i.getSellerRef(), i.getGrossMinor(), i.getCommissionMinor(), i.getCommissionGstMinor(),
                    i.getTcsMinor(), i.getTdsMinor(), i.getNetMinor());
        }
    }

    public record SplitSummary(
            String id, String currency, long grossMinor, long commissionMinor, long commissionGstMinor,
            long tcsMinor, long tdsMinor, long sellerNetMinor, int itemCount, String status,
            String ledgerEntryId, Instant createdAt) {
        static SplitSummary of(SplitPayment s) {
            return new SplitSummary(
                    s.getId().toString(), s.getCurrency(), s.getGrossMinor(), s.getCommissionMinor(),
                    s.getCommissionGstMinor(), s.getTcsMinor(), s.getTdsMinor(), s.getSellerNetMinor(),
                    s.getItemCount(), s.getStatus().name(), s.getLedgerEntryId().toString(), s.getCreatedAt());
        }
    }

    public record SplitView(SplitSummary split, List<SplitItemView> items) {
        static SplitView of(MarketplaceService.SplitWithItems s) {
            return new SplitView(
                    SplitSummary.of(s.split()),
                    s.items().stream().map(SplitItemView::of).toList());
        }
    }
}
