package com.qeetgroup.qeetpay.filing;

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
 * Live GST-return filing adapter (TAD §7.4) — talks to a real GSTN endpoint (via an ASP/GSP such as
 * ClearTax) over HTTPS. Active only when {@code qeetpay.gstn.enabled=true}; otherwise
 * {@link SandboxGstnFilingAdapter} files offline. Marked {@link Primary} so that whenever both the
 * live and sandbox beans are present it is the one injected into {@link FilingService}; its default
 * bean name ({@code liveGstnFilingAdapter}) also switches the sandbox's {@code @ConditionalOnMissingBean}
 * off.
 *
 * <p>Flow: fetch (and cache until expiry) an auth token, POST the prepared return JSON to the GSTN
 * {@code save} endpoint (capturing a reference id), then POST to {@code file} to obtain the ARN. The
 * GSTR-1 payload carries per-invoice B2B detail from the return lines; GSTR-3B is summary-only. No
 * secrets are hardcoded — all credentials/endpoints come from {@link GstnProperties}
 * ({@code qeetpay.gstn.*}). Money is carried in the return JSON in rupees (major units) via
 * {@link BigDecimal} {@code HALF_UP}; internal state stays in paise.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "qeetpay.gstn", name = "enabled", havingValue = "true")
public class LiveGstnFilingAdapter implements GstnFilingAdapter {

    private static final Logger log = LoggerFactory.getLogger(LiveGstnFilingAdapter.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter GSTN_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long EXPIRY_SKEW_SECONDS = 30;

    private final GstnProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient http;
    private final AtomicReference<CachedToken> token = new AtomicReference<>();

    public LiveGstnFilingAdapter(
            GstnProperties props, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = builder.baseUrl(props.baseUrl()).build();
    }

    @Override
    public String file(GstReturn ret, List<GstReturnLine> lines) {
        String authToken = authToken();
        String typeSeg = ret.getReturnType() == GstReturnType.GSTR1 ? "gstr1" : "gstr3b";
        String period = filingPeriod(ret.getPeriod());

        // 1) SAVE the prepared return; capture the reference id GSTN echoes back.
        String saveJson =
                http.post()
                        .uri(props.returnsPath() + "/" + typeSeg + "/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("gstin", props.gstin())
                        .header("authtoken", authToken)
                        .body(returnJson(ret, lines, period))
                        .retrieve()
                        .body(String.class);
        String referenceId = text(unwrap(readTree(saveJson)), "reference_id", "referenceId", "ref_id");

        // 2) FILE the saved return; GSTN issues the ARN on acceptance.
        Map<String, Object> fileBody = new LinkedHashMap<>();
        fileBody.put("gstin", props.gstin());
        fileBody.put("ret_period", period);
        if (referenceId != null) {
            fileBody.put("reference_id", referenceId);
        }
        String fileJson =
                http.post()
                        .uri(props.returnsPath() + "/" + typeSeg + "/file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("gstin", props.gstin())
                        .header("authtoken", authToken)
                        .body(writeJson(fileBody))
                        .retrieve()
                        .body(String.class);

        String arn = text(unwrap(readTree(fileJson)), "arn", "ARN", "Arn");
        if (arn == null) {
            throw new GstnFilingException("GSTN file response missing ARN: " + fileJson);
        }
        return arn;
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
        body.put("gstin", props.gstin());
        String responseJson =
                http.post()
                        .uri(props.authPath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(writeJson(body))
                        .retrieve()
                        .body(String.class);
        JsonNode data = unwrap(readTree(responseJson));
        String value = text(data, "authToken", "AuthToken", "token", "access_token");
        if (value == null) {
            throw new GstnFilingException("GSTN auth response missing token: " + responseJson);
        }
        Instant expiresAt = resolveExpiry(data, now);
        log.debug("refreshed GSTN auth token (expires at {})", expiresAt);
        return new CachedToken(value, expiresAt);
    }

    private Instant resolveExpiry(JsonNode n, Instant now) {
        JsonNode expiresIn = firstNumber(n, "expiresIn", "expires_in", "ExpiresIn");
        if (expiresIn != null) {
            return now.plusSeconds(expiresIn.asLong());
        }
        String tokenExpiry = text(n, "tokenExpiry", "TokenExpiry");
        if (tokenExpiry != null) {
            Instant parsed = parseGstnDateTime(tokenExpiry);
            if (parsed != null) {
                return parsed;
            }
        }
        return now.plusSeconds(props.tokenTtlSeconds());
    }

    // ── Request building ─────────────────────────────────────────────────────

    /** Builds the GSTN return JSON: GSTR-1 = per-invoice B2B detail, GSTR-3B = period summary. */
    private String returnJson(GstReturn ret, List<GstReturnLine> lines, String period) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("gstin", props.gstin());
        root.put("fp", period);
        root.put("ret_period", period);

        if (ret.getReturnType() == GstReturnType.GSTR1) {
            root.put("b2b", b2b(lines));
        } else {
            Map<String, Object> osup = new LinkedHashMap<>();
            osup.put("txval", major(ret.getTotalTaxableMinor()));
            osup.put("iamt", major(ret.getTotalIgstMinor()));
            osup.put("camt", major(ret.getTotalCgstMinor()));
            osup.put("samt", major(ret.getTotalSgstMinor()));
            Map<String, Object> supDetails = new LinkedHashMap<>();
            supDetails.put("osup_det", osup);
            root.put("sup_details", supDetails);
        }
        return writeJson(root);
    }

    /** Groups GSTR-1 outward-supply lines by buyer GSTIN into the GSTN {@code b2b} structure. */
    private List<Map<String, Object>> b2b(List<GstReturnLine> lines) {
        Map<String, List<GstReturnLine>> byBuyer = new LinkedHashMap<>();
        for (GstReturnLine line : lines) {
            String ctin = line.getBuyerGstin();
            if (ctin == null || ctin.isBlank()) {
                continue; // B2C supplies are not reported under b2b
            }
            byBuyer.computeIfAbsent(ctin, k -> new ArrayList<>()).add(line);
        }

        List<Map<String, Object>> b2bList = new ArrayList<>();
        for (Map.Entry<String, List<GstReturnLine>> entry : byBuyer.entrySet()) {
            List<Map<String, Object>> invoices = new ArrayList<>();
            for (GstReturnLine line : entry.getValue()) {
                Map<String, Object> itmDet = new LinkedHashMap<>();
                itmDet.put("txval", major(line.getTaxableMinor()));
                itmDet.put("iamt", major(line.getIgstMinor()));
                itmDet.put("camt", major(line.getCgstMinor()));
                itmDet.put("samt", major(line.getSgstMinor()));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("num", 1);
                item.put("itm_det", itmDet);

                long lineTotal =
                        line.getTaxableMinor()
                                + line.getCgstMinor()
                                + line.getSgstMinor()
                                + line.getIgstMinor();
                Map<String, Object> inv = new LinkedHashMap<>();
                inv.put("inum", line.getInvoiceNumber());
                inv.put("pos", line.getPlaceOfSupply());
                inv.put("val", major(lineTotal));
                inv.put("itms", List.of(item));
                invoices.add(inv);
            }
            Map<String, Object> ctinObj = new LinkedHashMap<>();
            ctinObj.put("ctin", entry.getKey());
            ctinObj.put("inv", invoices);
            b2bList.add(ctinObj);
        }
        return b2bList;
    }

    /** {@code YYYY-MM} → GSTN {@code MMYYYY} filing period. */
    private static String filingPeriod(String period) {
        String[] parts = period.split("-");
        if (parts.length != 2) {
            throw new GstnFilingException("period must be YYYY-MM, got '" + period + "'");
        }
        return parts[1] + parts[0];
    }

    /** Minor units (paise) → major units (rupees) as a scale-2 {@link BigDecimal} (HALF_UP). */
    private static BigDecimal major(long minor) {
        return BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    private static Instant parseGstnDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, GSTN_DATETIME).atZone(INDIA).toInstant();
        } catch (RuntimeException e) {
            try {
                return Instant.parse(raw);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private static JsonNode unwrap(JsonNode n) {
        return n.has("data") && n.get("data").isObject() ? n.get("data") : n;
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
            throw new GstnFilingException("failed to serialise GSTN request", e);
        }
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            throw new GstnFilingException("empty GSTN response");
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new GstnFilingException("failed to parse GSTN response: " + json, e);
        }
    }

    private record CachedToken(String value, Instant expiresAt) {}
}
