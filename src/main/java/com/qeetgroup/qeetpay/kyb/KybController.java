package com.qeetgroup.qeetpay.kyb;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** KYB API (TAD Module 06): submit PAN / GSTIN / bank; query overall status. */
@RestController
@RequestMapping("/v1/merchants/kyb")
public class KybController {

    private final KybService kybService;

    public KybController(KybService kybService) {
        this.kybService = kybService;
    }

    @PostMapping("/pan")
    public ResponseEntity<KybView> submitPan(@Valid @RequestBody PanRequest req) {
        return ResponseEntity.ok(KybView.of(kybService.submitPan(MerchantContext.require(), req.pan())));
    }

    @PostMapping("/gstin")
    public ResponseEntity<KybView> submitGstin(@Valid @RequestBody GstinRequest req) {
        return ResponseEntity.ok(KybView.of(kybService.submitGstin(MerchantContext.require(), req.gstin())));
    }

    @PostMapping("/bank")
    public ResponseEntity<KybView> submitBank(@Valid @RequestBody BankRequest req) {
        return ResponseEntity.ok(KybView.of(kybService.submitBank(MerchantContext.require(), req.accountNumber(), req.ifsc())));
    }

    @GetMapping("/status")
    public KybView status() {
        return KybView.of(kybService.status(MerchantContext.require()));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record PanRequest(@NotBlank String pan) {}
    public record GstinRequest(@NotBlank String gstin) {}
    public record BankRequest(@NotBlank String accountNumber, @NotBlank String ifsc) {}

    public record KybView(
            String merchantId, String overallStatus,
            String panStatus, String gstinStatus, String bankStatus,
            Instant verifiedAt) {
        static KybView of(MerchantKyb k) {
            return new KybView(
                    k.getMerchantId().toString(), k.getOverallStatus(),
                    k.getPanStatus(), k.getGstinStatus(), k.getBankStatus(),
                    k.getVerifiedAt());
        }
    }
}
