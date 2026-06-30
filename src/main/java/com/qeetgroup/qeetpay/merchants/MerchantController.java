package com.qeetgroup.qeetpay.merchants;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Merchant onboarding (TAD §19 — KYB is Phase 1; this is the minimal tenant-create slice). */
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

    public record CreateMerchantRequest(@NotBlank String slug, @NotBlank String name) {}

    /** {@code apiKey} is the raw secret — shown only here, never retrievable again. */
    public record CreateMerchantResponse(String id, String slug, String name, String apiKey) {}
}
