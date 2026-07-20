package com.qeetgroup.qeetpay.kyb;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * V-CIP API (PRD Module 19): schedule a video-KYC session for a merchant's signatory, drive it
 * through start → complete/fail, and read its status/history.
 */
@Tag(
        name = "KYB V-CIP",
        description = "Video-based Customer Identification Process — schedule and run video-KYC sessions for a merchant's signatory (RBI Master Directions).")
@RestController
@RequestMapping("/v1/merchants/kyb/vcip")
public class VcipController {

    private final VcipService vcip;

    public VcipController(VcipService vcip) {
        this.vcip = vcip;
    }

    @PostMapping
    public ResponseEntity<SessionView> schedule(@Valid @RequestBody ScheduleRequest req) {
        VcipSession s =
                vcip.schedule(
                        MerchantContext.require(), req.subjectName(), req.subjectRef(), req.agentId(), req.scheduledAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionView.of(s));
    }

    @GetMapping
    public List<SessionView> list() {
        return vcip.list(MerchantContext.require()).stream().map(SessionView::of).toList();
    }

    @GetMapping("/{sessionId}")
    public SessionView get(@PathVariable UUID sessionId) {
        return SessionView.of(vcip.get(MerchantContext.require(), sessionId));
    }

    @PostMapping("/{sessionId}/start")
    public SessionView start(@PathVariable UUID sessionId) {
        return SessionView.of(vcip.start(MerchantContext.require(), sessionId));
    }

    @PostMapping("/{sessionId}/complete")
    public SessionView complete(@PathVariable UUID sessionId, @Valid @RequestBody CompleteRequest req) {
        return SessionView.of(
                vcip.complete(
                        MerchantContext.require(), sessionId, req.biometricRef(), req.livenessScore(), req.geoTag()));
    }

    @PostMapping("/{sessionId}/fail")
    public SessionView fail(@PathVariable UUID sessionId, @RequestBody(required = false) FailRequest req) {
        String reason = req == null ? null : req.reason();
        return SessionView.of(vcip.fail(MerchantContext.require(), sessionId, reason));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record ScheduleRequest(
            @NotBlank String subjectName, String subjectRef, String agentId, Instant scheduledAt) {}

    public record CompleteRequest(
            @NotBlank String biometricRef,
            @Min(0) @Max(100) Integer livenessScore,
            String geoTag) {}

    public record FailRequest(String reason) {}

    public record SessionView(
            String id, String merchantId, String subjectName, String subjectRef, String status,
            String agentId, Instant scheduledAt, Instant startedAt, Instant completedAt,
            Integer livenessScore, String geoTag, Instant retentionExpiresAt, String failureReason) {
        static SessionView of(VcipSession s) {
            return new SessionView(
                    s.getId().toString(), s.getMerchantId().toString(), s.getSubjectName(), s.getSubjectRef(),
                    s.getStatus().name(), s.getAgentId(), s.getScheduledAt(), s.getStartedAt(), s.getCompletedAt(),
                    s.getLivenessScore(), s.getGeoTag(), s.getRetentionExpiresAt(), s.getFailureReason());
        }
    }
}
