package com.qeetgroup.qeetpay.gst;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
 * GST AI API (PRD Module 05 classification + Module 06.5 Regulatory-Change Impact Radar). Both features
 * route through the AiGateway §6.4 safety matrix; classification returns ranked suggestions + confidence
 * + explanation (low-confidence flagged for review), and the radar returns a labelled forecast report.
 * Active merchant is resolved from {@link MerchantContext}.
 */
@Tag(
        name = "GST AI",
        description =
                "AI-assisted HSN/SAC classification and the Regulatory-Change Impact Radar — both via the "
                        + "AiGateway §6.4 safety matrix with a deterministic fallback.")
@RestController
@RequestMapping("/v1/gst")
public class GstAiController {

    private final HsnClassifier classifier;
    private final RegChangeRadar radar;

    public GstAiController(HsnClassifier classifier, RegChangeRadar radar) {
        this.classifier = classifier;
        this.radar = radar;
    }

    // ── HSN/SAC classification (Module 05) ─────────────────────────────────────

    @PostMapping("/classify")
    public ClassificationResult classify(@Valid @RequestBody ClassifyRequest req) {
        return classifier.classify(MerchantContext.require(), req.description(), Set.of("gst:classify"));
    }

    // ── Regulatory-Change Impact Radar (Module 06.5) ───────────────────────────

    @PostMapping("/reg-changes")
    public ResponseEntity<RegChangeView> createRegChange(@Valid @RequestBody CreateRegChangeRequest req) {
        RegulatoryChange change =
                radar.recordChange(
                        MerchantContext.require(),
                        req.hsnSac(),
                        req.changeType() == null ? RegChangeType.RATE_CHANGE : req.changeType(),
                        req.oldRatePct(),
                        req.newRatePct(),
                        req.effectiveDate(),
                        req.title(),
                        req.source());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegChangeView.of(change));
    }

    @GetMapping("/reg-changes")
    public List<RegChangeView> listRegChanges() {
        return radar.list(MerchantContext.require()).stream().map(RegChangeView::of).toList();
    }

    @GetMapping("/reg-changes/{id}/impact")
    public RegChangeImpactReport impact(@PathVariable UUID id) {
        return radar.computeImpact(MerchantContext.require(), id, Set.of("gst:read"));
    }

    // ── Request / response records ─────────────────────────────────────────────

    @Schema(name = "GstClassifyRequest")
    public record ClassifyRequest(@NotBlank String description) {}

    public record CreateRegChangeRequest(
            @NotBlank String hsnSac,
            RegChangeType changeType,
            Integer oldRatePct,
            @NotNull Integer newRatePct,
            @NotNull LocalDate effectiveDate,
            @NotBlank String title,
            String source) {}

    public record RegChangeView(
            String id,
            String hsnSac,
            String changeType,
            Integer oldRatePct,
            int newRatePct,
            LocalDate effectiveDate,
            String title,
            String source,
            Instant announcedAt) {
        static RegChangeView of(RegulatoryChange c) {
            return new RegChangeView(
                    c.getId().toString(),
                    c.getHsnSac(),
                    c.getChangeType().name(),
                    c.getOldRatePct(),
                    c.getNewRatePct(),
                    c.getEffectiveDate(),
                    c.getTitle(),
                    c.getSource(),
                    c.getAnnouncedAt());
        }
    }
}
