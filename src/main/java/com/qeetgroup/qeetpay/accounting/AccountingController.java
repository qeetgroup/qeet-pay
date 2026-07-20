package com.qeetgroup.qeetpay.accounting;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accounting integrations API (PRD Module 11.3): run an export of a period's ledger + GST to an
 * external system (tally | zoho | webhook), read/download past runs, and manage per-target
 * connection settings. Merchant comes from {@link MerchantContext}.
 */
@Tag(
        name = "Accounting",
        description = "Export a period's ledger journal entries + GST invoices to Tally / Zoho Books / a webhook, and manage connections.")
@RestController
@RequestMapping("/v1/accounting")
public class AccountingController {

    private final AccountingSyncService service;

    public AccountingController(AccountingSyncService service) {
        this.service = service;
    }

    // ── Exports ────────────────────────────────────────────────────────────

    @PostMapping("/exports")
    public ResponseEntity<ExportView> createExport(@Valid @RequestBody CreateExportRequest req) {
        AccountingTarget target = AccountingTarget.parse(req.target());
        AccountingSync run = service.export(MerchantContext.require(), target, req.periodStart(), req.periodEnd());
        return ResponseEntity.ok(ExportView.of(run));
    }

    @GetMapping("/exports")
    public List<ExportView> listExports() {
        return service.list(MerchantContext.require()).stream().map(ExportView::of).toList();
    }

    @GetMapping("/exports/{id}")
    public ExportView getExport(@PathVariable UUID id) {
        return ExportView.of(service.get(MerchantContext.require(), id));
    }

    @GetMapping("/exports/{id}/download")
    public ResponseEntity<String> download(@PathVariable UUID id) {
        AccountingSync run = service.download(MerchantContext.require(), id);
        boolean tally = run.getTarget() == AccountingTarget.TALLY;
        MediaType type = tally ? MediaType.APPLICATION_XML : MediaType.APPLICATION_JSON;
        String filename = "accounting-export-" + run.getId() + (tally ? ".xml" : ".json");
        return ResponseEntity.ok()
                .contentType(type)
                .header(
                        "Content-Disposition",
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(run.getDocument());
    }

    // ── Connections ────────────────────────────────────────────────────────

    @PutMapping("/connections")
    public ConnectionView upsertConnection(@Valid @RequestBody ConnectionRequest req) {
        AccountingConnection saved =
                service.upsertConnection(
                        MerchantContext.require(),
                        AccountingTarget.parse(req.target()),
                        req.enabled() == null || req.enabled(),
                        req.webhookUrl(),
                        req.zohoOrganizationId());
        return ConnectionView.of(saved);
    }

    @GetMapping("/connections")
    public List<ConnectionView> listConnections() {
        return service.listConnections(MerchantContext.require()).stream().map(ConnectionView::of).toList();
    }

    // ── Records ────────────────────────────────────────────────────────────

    public record CreateExportRequest(
            @NotBlank String target,
            @NotNull Instant periodStart,
            @NotNull Instant periodEnd) {}

    public record ConnectionRequest(
            @NotBlank String target,
            Boolean enabled,
            String webhookUrl,
            String zohoOrganizationId) {}

    public record ExportView(
            String id, String target, Instant periodStart, Instant periodEnd, String status,
            int recordCount, String externalRef, String detail, Instant createdAt, Instant completedAt) {
        static ExportView of(AccountingSync s) {
            return new ExportView(
                    s.getId().toString(), s.getTarget().name(), s.getPeriodStart(), s.getPeriodEnd(),
                    s.getStatus().name(), s.getRecordCount(), s.getExternalRef(), s.getDetail(),
                    s.getCreatedAt(), s.getCompletedAt());
        }
    }

    public record ConnectionView(
            String id, String target, boolean enabled, String webhookUrl, String zohoOrganizationId,
            Instant updatedAt) {
        static ConnectionView of(AccountingConnection c) {
            return new ConnectionView(
                    c.getId().toString(), c.getTarget().name(), c.isEnabled(), c.getWebhookUrl(),
                    c.getZohoOrganizationId(), c.getUpdatedAt());
        }
    }
}
