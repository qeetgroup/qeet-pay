package com.qeetgroup.qeetpay.merchants;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Merchant onboarding (TAD §19 — KYB is Phase 1; this is the minimal tenant-create slice). */
@Tag(
        name = "Merchants",
        description = "Merchant onboarding (mints an API key + seeds the chart of accounts) and the current-merchant profile.")
@RestController
@RequestMapping("/v1/merchants")
public class MerchantController {

    private final MerchantService service;

    public MerchantController(MerchantService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CreateMerchantResponse> create(@Valid @RequestBody CreateMerchantRequest request) {
        MerchantService.Onboarded onboarded = service.create(request.slug(), request.name());
        Merchant m = onboarded.merchant();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new CreateMerchantResponse(
                                m.getId().toString(), m.getSlug(), m.getName(), onboarded.apiKey()));
    }

    @GetMapping("/me")
    public MerchantView me() {
        return MerchantView.of(service.current(MerchantContext.require()));
    }

    public record CreateMerchantRequest(@NotBlank String slug, @NotBlank String name) {}

    /** {@code apiKey} is the raw secret — shown only here, never retrievable again. */
    public record CreateMerchantResponse(String id, String slug, String name, String apiKey) {}

    /** The active merchant's own tenant record (no secrets — the API key is never re-served). */
    public record MerchantView(String id, String slug, String name, String status) {
        static MerchantView of(Merchant m) {
            return new MerchantView(m.getId().toString(), m.getSlug(), m.getName(), m.getStatus());
        }
    }
}
