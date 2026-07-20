package com.qeetgroup.qeetpay.kyb;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * Customer-KYC API (PRD Module 19): create a KYC record, run Aadhaar-OTP e-KYC (initiate → verify),
 * submit PAN, and read status. Aadhaar-OTP is simulated; PAN uses the shared KYB verification adapter.
 */
@Tag(
        name = "Customer KYC",
        description = "End-customer KYC — Aadhaar-OTP e-KYC (simulated) + PAN verification + consent capture.")
@RestController
@RequestMapping("/v1/kyc/customers")
public class CustomerKycController {

    private final CustomerKycService kyc;

    public CustomerKycController(CustomerKycService kyc) {
        this.kyc = kyc;
    }

    @PostMapping
    public ResponseEntity<KycView> create(@Valid @RequestBody CreateRequest req) {
        CustomerKyc c =
                kyc.create(
                        MerchantContext.require(), req.customerRef(), req.fullName(),
                        Boolean.TRUE.equals(req.consentGiven()), req.consentArtifact());
        return ResponseEntity.status(HttpStatus.CREATED).body(KycView.of(c));
    }

    @GetMapping
    public List<KycView> list() {
        return kyc.list(MerchantContext.require()).stream().map(KycView::of).toList();
    }

    @GetMapping("/{id}")
    public KycView get(@PathVariable UUID id) {
        return KycView.of(kyc.get(MerchantContext.require(), id));
    }

    @PostMapping("/{id}/consent")
    public KycView consent(@PathVariable UUID id, @RequestBody(required = false) ConsentRequest req) {
        String artifact = req == null ? null : req.consentArtifact();
        return KycView.of(kyc.consent(MerchantContext.require(), id, artifact));
    }

    @PostMapping("/{id}/aadhaar/initiate")
    public KycView initiateAadhaar(@PathVariable UUID id, @Valid @RequestBody AadhaarInitiateRequest req) {
        return KycView.of(kyc.initiateAadhaar(MerchantContext.require(), id, req.aadhaar()));
    }

    @PostMapping("/{id}/aadhaar/verify")
    public KycView verifyAadhaar(@PathVariable UUID id, @Valid @RequestBody AadhaarVerifyRequest req) {
        return KycView.of(kyc.verifyAadhaar(MerchantContext.require(), id, req.txnId(), req.otp()));
    }

    @PostMapping("/{id}/pan")
    public KycView submitPan(@PathVariable UUID id, @Valid @RequestBody PanRequest req) {
        return KycView.of(kyc.submitPan(MerchantContext.require(), id, req.pan()));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record CreateRequest(
            @NotBlank String customerRef, @NotBlank String fullName, Boolean consentGiven, String consentArtifact) {}

    public record ConsentRequest(String consentArtifact) {}

    public record AadhaarInitiateRequest(@NotBlank String aadhaar) {}

    public record AadhaarVerifyRequest(@NotBlank String txnId, @NotBlank String otp) {}

    public record PanRequest(@NotBlank String pan) {}

    public record KycView(
            String id, String merchantId, String customerRef, String fullName, String aadhaarLast4,
            String aadhaarTxnId, String aadhaarStatus, String pan, String panStatus,
            boolean consentGiven, Instant consentAt, String overallStatus, Instant verifiedAt) {
        static KycView of(CustomerKyc c) {
            return new KycView(
                    c.getId().toString(), c.getMerchantId().toString(), c.getCustomerRef(), c.getFullName(),
                    c.getAadhaarLast4(), c.getAadhaarTxnId(), c.getAadhaarStatus().name(), c.getPan(),
                    c.getPanStatus().name(), c.isConsentGiven(), c.getConsentAt(),
                    c.getOverallStatus().name(), c.getVerifiedAt());
        }
    }
}
