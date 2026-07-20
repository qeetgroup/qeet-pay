package com.qeetgroup.qeetpay.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Live Zoho Books connector — posts the period as a manual journal to
 * {@code POST /journals?organization_id=…} with a {@code Zoho-oauthtoken} bearer. Registered only
 * when {@code qeetpay.accounting.zoho.enabled=true} (see {@link AccountingConfig}); otherwise
 * {@link SandboxAccountingConnector} stands in. Never throws for transport errors — returns a
 * {@link SyncResult#failure(String)} so an export run is recorded as FAILED rather than blowing up.
 */
public class ZohoBooksConnector implements AccountingConnector {

    private static final Logger log = LoggerFactory.getLogger(ZohoBooksConnector.class);

    private final RestClient http;
    private final String organizationId;
    private final ObjectMapper objectMapper;

    public ZohoBooksConnector(ZohoBooksProperties props, ObjectMapper objectMapper) {
        // SimpleClientHttpRequestFactory reliably writes the POST body with a Content-Length
        // (matching HttpFraudClient); the auto-detected factory dropped it in this environment.
        this.http =
                RestClient.builder()
                        .baseUrl(props.baseUrl())
                        .requestFactory(new SimpleClientHttpRequestFactory())
                        .defaultHeader("Authorization", "Zoho-oauthtoken " + props.accessToken())
                        .build();
        this.organizationId = props.organizationId();
        this.objectMapper = objectMapper;
    }

    /** Package-private: test injection with a mocked {@link RestClient}. */
    ZohoBooksConnector(RestClient http, String organizationId, ObjectMapper objectMapper) {
        this.http = http;
        this.organizationId = organizationId;
        this.objectMapper = objectMapper;
    }

    @Override
    public AccountingTarget target() {
        return AccountingTarget.ZOHO;
    }

    @Override
    public SyncResult push(ExportPayload payload, AccountingConnection connection) {
        String orgId =
                connection != null && connection.getZohoOrganizationId() != null
                                && !connection.getZohoOrganizationId().isBlank()
                        ? connection.getZohoOrganizationId()
                        : organizationId;
        try {
            String body = objectMapper.writeValueAsString(toJournal(payload));
            String responseJson =
                    http.post()
                            .uri(uri -> uri.path("/journals").queryParam("organization_id", orgId).build())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);

            String externalRef = "";
            if (responseJson != null && !responseJson.isBlank()) {
                JsonNode n = objectMapper.readTree(responseJson);
                externalRef = n.path("journal").path("journal_id").asText("");
            }
            return SyncResult.ok(payload.recordCount(), externalRef.isBlank() ? "zoho-journal" : externalRef, body);
        } catch (Exception e) {
            log.warn("zoho books push failed", e);
            return SyncResult.failure("zoho push failed: " + e.getMessage());
        }
    }

    /** Maps the export into a Zoho Books manual-journal request body (line per ledger/tax entry). */
    private Map<String, Object> toJournal(ExportPayload payload) {
        List<Map<String, Object>> lineItems = new ArrayList<>();
        for (ExportPayload.JournalVoucher v : payload.vouchers()) {
            for (ExportPayload.VoucherLine line : v.lines()) {
                lineItems.add(lineItem(line.accountCode(), line.direction() == Direction.DEBIT, line.amountMinor()));
            }
        }
        for (ExportPayload.InvoiceExport inv : payload.invoices()) {
            lineItems.add(lineItem("Accounts Receivable (" + inv.invoiceNumber() + ")", true, inv.totalMinor()));
            lineItems.add(lineItem("Sales", false, inv.taxableMinor()));
            if (inv.cgstMinor() > 0) lineItems.add(lineItem("Output CGST", false, inv.cgstMinor()));
            if (inv.sgstMinor() > 0) lineItems.add(lineItem("Output SGST", false, inv.sgstMinor()));
            if (inv.igstMinor() > 0) lineItems.add(lineItem("Output IGST", false, inv.igstMinor()));
        }
        Map<String, Object> journal = new LinkedHashMap<>();
        journal.put("journal_date", payload.periodEnd().toString());
        journal.put("reference_number", "QeetPay " + payload.periodStart() + "/" + payload.periodEnd());
        journal.put("notes", "Qeet Pay accounting export");
        journal.put("line_items", lineItems);
        return journal;
    }

    private Map<String, Object> lineItem(String description, boolean debit, long amountMinor) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("description", description);
        item.put("debit_or_credit", debit ? "debit" : "credit");
        item.put("amount", new java.math.BigDecimal(amountMinor).movePointLeft(2));
        return item;
    }
}
