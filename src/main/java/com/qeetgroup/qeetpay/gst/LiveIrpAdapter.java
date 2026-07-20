package com.qeetgroup.qeetpay.gst;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Live e-invoicing adapter (TAD §7.3) — talks to a real NIC-IRP / GSP e-invoice endpoint over HTTPS.
 * Active only when {@code qeetpay.irp.enabled=true}; otherwise {@link SandboxIrpAdapter} handles
 * registration offline. Marked {@link Primary} so that whenever both the live and sandbox beans are
 * present it is the one injected into {@link EInvoiceService}; its default bean name
 * ({@code liveIrpAdapter}) also switches the sandbox's {@code @ConditionalOnMissingBean} off.
 *
 * <p>Flow: fetch (and cache until expiry) an auth token from the IRP, POST the e-invoice JSON to
 * obtain an IRN + signed QR, and POST a cancellation with a reason code. No secrets are hardcoded —
 * all credentials/endpoints come from {@link IrpProperties} ({@code qeetpay.irp.*}). Money is carried
 * in the invoice JSON in rupees (major units) via {@link BigDecimal} {@code HALF_UP}; internal state
 * stays in paise.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "qeetpay.irp", name = "enabled", havingValue = "true")
public class LiveIrpAdapter implements IrpAdapter {

    private static final Logger log = LoggerFactory.getLogger(LiveIrpAdapter.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DOC_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(INDIA);
    private static final DateTimeFormatter NIC_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Refresh a little before the token actually expires, so an in-flight call never races expiry. */
    private static final long EXPIRY_SKEW_SECONDS = 30;

    private final IrpProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient http;
    private final AtomicReference<CachedToken> token = new AtomicReference<>();

    public LiveIrpAdapter(IrpProperties props, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = builder.baseUrl(props.baseUrl()).build();
    }

    @Override
    public IrpResult generateIrn(GstInvoice invoice, List<GstInvoiceLine> lines) {
        String authToken = authToken();
        String responseJson =
                http.post()
                        .uri(props.generatePath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("gstin", invoice.getSupplierGstin())
                        .header("authtoken", authToken)
                        .body(invoiceJson(invoice, lines))
                        .retrieve()
                        .body(String.class);
        return mapIrn(responseJson);
    }

    @Override
    public void cancelIrn(String irn, String reason) {
        String authToken = authToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Irn", irn);
        body.put("CnlRsn", reason);
        body.put("CnlRem", "Cancelled via Qeet Pay");
        http.post()
                .uri(props.cancelPath())
                .contentType(MediaType.APPLICATION_JSON)
                .header("authtoken", authToken)
                .body(writeJson(body))
                .retrieve()
                .toBodilessEntity();
    }

    // ── Auth token fetch + cache ─────────────────────────────────────────────

    private String authToken() {
        Instant now = Instant.now();
        CachedToken cached = token.get();
        if (isFresh(cached, now)) {
            return cached.value();
        }
        synchronized (this) {
            cached = token.get();
            if (isFresh(cached, now)) {
                return cached.value();
            }
            CachedToken fresh = fetchToken(now);
            token.set(fresh);
            return fresh.value();
        }
    }

    private static boolean isFresh(CachedToken cached, Instant now) {
        return cached != null && cached.expiresAt().isAfter(now.plusSeconds(EXPIRY_SKEW_SECONDS));
    }

    private CachedToken fetchToken(Instant now) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", props.clientId());
        body.put("clientSecret", props.clientSecret());
        body.put("username", props.username());
        body.put("password", props.password());
        String responseJson =
                http.post()
                        .uri(props.authPath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(writeJson(body))
                        .retrieve()
                        .body(String.class);
        JsonNode n = readTree(responseJson);
        JsonNode data = n.has("data") && n.get("data").isObject() ? n.get("data") : n;
        String value = text(data, "authToken", "AuthToken", "token", "access_token");
        if (value == null) {
            throw new IrpException("IRP auth response missing token: " + responseJson);
        }
        Instant expiresAt = resolveExpiry(data, now);
        log.debug("refreshed IRP auth token (expires at {})", expiresAt);
        return new CachedToken(value, expiresAt);
    }

    private Instant resolveExpiry(JsonNode n, Instant now) {
        JsonNode expiresIn = firstNumber(n, "expiresIn", "expires_in", "ExpiresIn");
        if (expiresIn != null) {
            return now.plusSeconds(expiresIn.asLong());
        }
        String tokenExpiry = text(n, "tokenExpiry", "TokenExpiry");
        if (tokenExpiry != null) {
            Instant parsed = parseNicDateTime(tokenExpiry);
            if (parsed != null) {
                return parsed;
            }
        }
        return now.plusSeconds(props.tokenTtlSeconds());
    }

    // ── Request/response mapping ─────────────────────────────────────────────

    private IrpResult mapIrn(String responseJson) {
        JsonNode n = readTree(responseJson);
        JsonNode data = n.has("data") && n.get("data").isObject() ? n.get("data") : n;
        String irn = text(data, "Irn", "irn");
        if (irn == null) {
            throw new IrpException("IRP generate response missing Irn: " + responseJson);
        }
        String ackNo = text(data, "AckNo", "ackNo");
        Instant ackDate = ackDate(text(data, "AckDt", "ackDt"));
        String signedQr = text(data, "SignedQRCode", "signedQRCode", "SignedQrCode");
        return new IrpResult(irn, ackNo, ackDate, signedQr);
    }

    /** Builds the NIC e-invoice JSON schema (subset) for {@code invoice} with amounts in rupees. */
    private String invoiceJson(GstInvoice inv, List<GstInvoiceLine> lines) {
        boolean b2b = inv.getBuyerGstin() != null && !inv.getBuyerGstin().isBlank();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", "1.1");

        Map<String, Object> tran = new LinkedHashMap<>();
        tran.put("TaxSch", "GST");
        tran.put("SupTyp", b2b ? "B2B" : "B2C");
        root.put("TranDtls", tran);

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("Typ", "INV");
        doc.put("No", inv.getInvoiceNumber());
        doc.put("Dt", DOC_DATE.format(inv.getIssuedAt()));
        root.put("DocDtls", doc);

        Map<String, Object> seller = new LinkedHashMap<>();
        seller.put("Gstin", inv.getSupplierGstin());
        seller.put("Pos", inv.getPlaceOfSupply());
        root.put("SellerDtls", seller);

        Map<String, Object> buyer = new LinkedHashMap<>();
        buyer.put("Gstin", b2b ? inv.getBuyerGstin() : "URP");
        buyer.put("Pos", inv.getPlaceOfSupply());
        root.put("BuyerDtls", buyer);

        List<Map<String, Object>> items = new ArrayList<>();
        int slNo = 1;
        for (GstInvoiceLine line : lines) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("SlNo", Integer.toString(slNo++));
            item.put("PrdDesc", line.getDescription());
            item.put("HsnCd", line.getHsnSac());
            item.put("IsServc", "Y");
            item.put("GstRt", gstRate(line));
            item.put("AssAmt", major(line.getTaxableMinor()));
            item.put("CgstAmt", major(line.getCgstMinor()));
            item.put("SgstAmt", major(line.getSgstMinor()));
            item.put("IgstAmt", major(line.getIgstMinor()));
            item.put("TotItemVal", major(line.getLineTotalMinor()));
            items.add(item);
        }
        root.put("ItemList", items);

        Map<String, Object> val = new LinkedHashMap<>();
        val.put("AssVal", major(inv.getTaxableMinor()));
        val.put("CgstVal", major(inv.getCgstMinor()));
        val.put("SgstVal", major(inv.getSgstMinor()));
        val.put("IgstVal", major(inv.getIgstMinor()));
        val.put("TotInvVal", major(inv.getTotalMinor()));
        root.put("ValDtls", val);

        return writeJson(root);
    }

    /** Effective GST rate for a line, derived from its tax vs. taxable amounts (whole-percent). */
    private static BigDecimal gstRate(GstInvoiceLine line) {
        long tax = line.getCgstMinor() + line.getSgstMinor() + line.getIgstMinor();
        if (line.getTaxableMinor() == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tax)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(line.getTaxableMinor()), 0, RoundingMode.HALF_UP);
    }

    /** Minor units (paise) → major units (rupees) as a scale-2 {@link BigDecimal} (HALF_UP). */
    private static BigDecimal major(long minor) {
        return BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    private Instant ackDate(String raw) {
        Instant parsed = parseNicDateTime(raw);
        return parsed != null ? parsed : Instant.now();
    }

    private static Instant parseNicDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, NIC_DATETIME).atZone(INDIA).toInstant();
        } catch (RuntimeException e) {
            try {
                return Instant.parse(raw);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode f = node.get(name);
            if (f != null && !f.isNull()) {
                String v = f.asText();
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        }
        return null;
    }

    private static JsonNode firstNumber(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode f = node.get(name);
            if (f != null && f.isNumber()) {
                return f;
            }
        }
        return null;
    }

    private String writeJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IrpException("failed to serialise IRP request", e);
        }
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            throw new IrpException("empty IRP response");
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IrpException("failed to parse IRP response: " + json, e);
        }
    }

    private record CachedToken(String value, Instant expiresAt) {}
}
