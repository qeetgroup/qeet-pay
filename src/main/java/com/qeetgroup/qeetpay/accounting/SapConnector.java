package com.qeetgroup.qeetpay.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Live SAP Business One connector — logs in against the SAP Business One <b>Service Layer</b>
 * ({@code POST /Login} with {@code CompanyDB}/{@code UserName}/{@code Password}), then posts the
 * period as a single {@code JournalEntry} to {@code POST /JournalEntries} carrying the returned
 * {@code B1SESSION}. Registered only when {@code qeetpay.accounting.sap.enabled=true} (see
 * {@link AccountingConfig}). Never throws for transport errors — returns a
 * {@link SyncResult#failure(String)} so an export run is recorded as FAILED rather than blowing up,
 * exactly like {@link ZohoBooksConnector}.
 */
public class SapConnector implements AccountingConnector {

    private static final Logger log = LoggerFactory.getLogger(SapConnector.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final RestClient http;
    private final String companyDb;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper;

    public SapConnector(SapProperties props, ObjectMapper objectMapper) {
        // SimpleClientHttpRequestFactory reliably writes the POST body with a Content-Length
        // (matching ZohoBooksConnector); the auto-detected factory dropped it in this environment.
        this.http =
                RestClient.builder()
                        .baseUrl(props.baseUrl())
                        .requestFactory(new SimpleClientHttpRequestFactory())
                        .build();
        this.companyDb = props.companyDb();
        this.username = props.username();
        this.password = props.password();
        this.objectMapper = objectMapper;
    }

    /** Package-private: test injection with a mocked {@link RestClient}. */
    SapConnector(RestClient http, String companyDb, String username, String password, ObjectMapper objectMapper) {
        this.http = http;
        this.companyDb = companyDb;
        this.username = username;
        this.password = password;
        this.objectMapper = objectMapper;
    }

    @Override
    public AccountingTarget target() {
        return AccountingTarget.SAP;
    }

    @Override
    public SyncResult push(ExportPayload payload, AccountingConnection connection) {
        try {
            String sessionId = login();

            String body = objectMapper.writeValueAsString(toJournalEntry(payload));
            String responseJson =
                    http.post()
                            .uri("/JournalEntries")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Cookie", "B1SESSION=" + sessionId)
                            .body(body)
                            .retrieve()
                            .body(String.class);

            String externalRef = "";
            if (responseJson != null && !responseJson.isBlank()) {
                JsonNode n = objectMapper.readTree(responseJson);
                // Service Layer returns the internal journal document number as JdtNum.
                externalRef = n.path("JdtNum").asText("");
            }
            return SyncResult.ok(payload.recordCount(), externalRef.isBlank() ? "sap-journal" : externalRef, body);
        } catch (Exception e) {
            log.warn("sap business one push failed", e);
            return SyncResult.failure("sap push failed: " + e.getMessage());
        }
    }

    /** Authenticates against the Service Layer and lifts the {@code SessionId} from the response. */
    private String login() throws Exception {
        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("CompanyDB", companyDb);
        loginBody.put("UserName", username);
        loginBody.put("Password", password);
        String loginResponse =
                http.post()
                        .uri("/Login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(loginBody))
                        .retrieve()
                        .body(String.class);
        if (loginResponse == null || loginResponse.isBlank()) {
            return "";
        }
        return objectMapper.readTree(loginResponse).path("SessionId").asText("");
    }

    /** Maps the export into a SAP Business One JournalEntry request body (line per ledger/tax entry). */
    private Map<String, Object> toJournalEntry(ExportPayload payload) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ExportPayload.JournalVoucher v : payload.vouchers()) {
            String memo = "Qeet Pay entry " + v.entryId();
            for (ExportPayload.VoucherLine line : v.lines()) {
                lines.add(line(line.accountCode(), line.direction() == Direction.DEBIT, line.amountMinor(), memo));
            }
        }
        for (ExportPayload.InvoiceExport inv : payload.invoices()) {
            lines.add(line("Accounts Receivable (" + inv.invoiceNumber() + ")", true, inv.totalMinor(), inv.invoiceNumber()));
            lines.add(line("Sales", false, inv.taxableMinor(), inv.invoiceNumber()));
            if (inv.cgstMinor() > 0) lines.add(line("Output CGST", false, inv.cgstMinor(), inv.invoiceNumber()));
            if (inv.sgstMinor() > 0) lines.add(line("Output SGST", false, inv.sgstMinor(), inv.invoiceNumber()));
            if (inv.igstMinor() > 0) lines.add(line("Output IGST", false, inv.igstMinor(), inv.invoiceNumber()));
        }

        String refDate = LocalDate.ofInstant(payload.periodEnd(), INDIA).toString(); // yyyy-MM-dd
        Map<String, Object> journal = new LinkedHashMap<>();
        journal.put("ReferenceDate", refDate);
        journal.put("TaxDate", refDate);
        journal.put("DueDate", refDate);
        journal.put("Memo", "Qeet Pay " + payload.periodStart() + "/" + payload.periodEnd());
        journal.put("JournalEntryLines", lines);
        return journal;
    }

    private Map<String, Object> line(String accountCode, boolean debit, long amountMinor, String memo) {
        BigDecimal amount = BigDecimal.valueOf(amountMinor).movePointLeft(2);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("AccountCode", accountCode);
        item.put("Debit", debit ? amount : BigDecimal.ZERO);
        item.put("Credit", debit ? BigDecimal.ZERO : amount);
        item.put("LineMemo", memo);
        return item;
    }
}
