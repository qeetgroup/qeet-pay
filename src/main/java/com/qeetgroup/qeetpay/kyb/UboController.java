package com.qeetgroup.qeetpay.kyb;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UBO registry API (PRD Module 19): register / list / remove a merchant's ultimate beneficial owners
 * (natural persons holding {@code > 10%} equity per RBI Master Directions).
 */
@Tag(
        name = "KYB UBO",
        description = "Ultimate Beneficial Owner registry — natural persons holding > 10% equity (RBI Master Directions).")
@RestController
@RequestMapping("/v1/merchants/kyb/ubo")
public class UboController {

    private final UboService ubo;

    public UboController(UboService ubo) {
        this.ubo = ubo;
    }

    @PostMapping
    public ResponseEntity<OwnerView> add(@Valid @RequestBody AddRequest req) {
        BeneficialOwner owner =
                ubo.add(
                        MerchantContext.require(), req.name(), req.pan(), req.din(), req.nationality(),
                        req.ownershipBps(), Boolean.TRUE.equals(req.controlPerson()));
        return ResponseEntity.status(HttpStatus.CREATED).body(OwnerView.of(owner));
    }

    @GetMapping
    public List<OwnerView> list() {
        return ubo.list(MerchantContext.require()).stream().map(OwnerView::of).toList();
    }

    @GetMapping("/{id}")
    public OwnerView get(@PathVariable UUID id) {
        return OwnerView.of(ubo.get(MerchantContext.require(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        ubo.remove(MerchantContext.require(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record AddRequest(
            @NotBlank String name,
            String pan,
            String din,
            String nationality,
            @NotNull @Min(1001) @Max(10000) Integer ownershipBps,
            Boolean controlPerson) {}

    public record OwnerView(
            String id, String merchantId, String name, String pan, String din, String nationality,
            int ownershipBps, boolean controlPerson, String panStatus, Instant createdAt) {
        static OwnerView of(BeneficialOwner o) {
            return new OwnerView(
                    o.getId().toString(), o.getMerchantId().toString(), o.getName(), o.getPan(), o.getDin(),
                    o.getNationality(), o.getOwnershipBps(), o.isControlPerson(), o.getPanStatus().name(),
                    o.getCreatedAt());
        }
    }
}
