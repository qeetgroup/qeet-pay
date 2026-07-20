package com.qeetgroup.qeetpay.payouts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Live disbursement rail backed by a RazorpayX-style payouts API (TAD Module 02). Active only when
 * {@code qeetpay.payouts.enabled=true}; marked {@link Primary} so it wins over the always-present
 * {@link SandboxPayoutProvider} for the single {@link PayoutProvider} injected into
 * {@link PayoutService}. When disabled (dev/test default) the sandbox rail is used.
 *
 * <p>Creates a <em>composite payout</em> ({@code POST /v1/payouts}) — one call that resolves the
 * destination inline as a fund account: a VPA for {@link PayoutRail#UPI}, otherwise a bank account
 * (destination encoded as {@code <accountNumber>@<ifsc>}). {@link PayoutRail} maps 1:1 to RazorpayX
 * {@code mode} (UPI/IMPS/NEFT/RTGS). The internal payout id is sent as {@code reference_id} for
 * idempotency/correlation. Auth is HTTP Basic ({@code keyId:keySecret}), same scheme as
 * {@link com.qeetgroup.qeetpay.payments.DefaultRazorpayGateway}.
 *
 * <p>JSON is serialized/parsed explicitly with Jackson (a String body — robust across RestClient
 * request factories, matching {@code HttpFraudClient}). No custom request factory is set so tests can
 * bind {@code MockRestServiceServer} to the injected {@link RestClient.Builder}.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "qeetpay.payouts", name = "enabled", havingValue = "true")
public class RazorpayXPayoutProvider implements PayoutProvider {

    private final RestClient http;
    private final PayoutRailProperties props;
    private final ObjectMapper objectMapper;

    public RazorpayXPayoutProvider(
            PayoutRailProperties props, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        String basic =
                Base64.getEncoder()
                        .encodeToString(
                                (props.keyId() + ":" + props.keySecret())
                                        .getBytes(StandardCharsets.UTF_8));
        this.http =
                builder.baseUrl(props.baseUrl())
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .build();
    }

    /**
     * Submits the payout to the rail. On acceptance (a payout id back and a non-terminal-failure
     * status) returns {@link ProviderResult#ok} carrying the provider payout id, which
     * {@link PayoutService} stores and treats as disbursed; on a terminal-failure status or any
     * transport/parse error returns {@link ProviderResult#failed} so the payout is marked
     * {@link PayoutStatus#FAILED} without a ledger posting.
     */
    @Override
    public ProviderResult process(Payout payout) {
        try {
            String responseJson =
                    http.post()
                            .uri("/v1/payouts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(objectMapper.writeValueAsString(buildRequest(payout)))
                            .retrieve()
                            .body(String.class);

            if (responseJson == null || responseJson.isBlank()) {
                return ProviderResult.failed("razorpayx_payout_empty_response");
            }
            JsonNode n = parse(responseJson);
            String providerPayoutId = n.path("id").asText(null);
            String status = n.path("status").asText("");
            if (providerPayoutId == null || providerPayoutId.isBlank() || isTerminalFailure(status)) {
                return ProviderResult.failed(
                        "razorpayx_payout_" + (status.isBlank() ? "no_id" : status));
            }
            return ProviderResult.ok(providerPayoutId);
        } catch (Exception e) {
            return ProviderResult.failed("razorpayx_payout_error: " + e.getMessage());
        }
    }

    /**
     * Fetches the current provider-side status of a payout ({@code GET /v1/payouts/{id}}) and maps it
     * onto the existing {@link PayoutStatus} enum. RazorpayX in-flight states (queued/pending/
     * processing/…) have no dedicated enum, so they collapse to {@link PayoutStatus#PENDING_APPROVAL}
     * as the nearest non-terminal value; {@code processed}→PAID, {@code failed}/{@code reversed}→FAILED,
     * {@code cancelled}/{@code rejected}→REJECTED.
     */
    public PayoutStatus fetchStatus(String providerPayoutId) {
        String responseJson =
                http.get().uri("/v1/payouts/{id}", providerPayoutId).retrieve().body(String.class);
        JsonNode n = parse(responseJson == null ? "{}" : responseJson);
        return mapStatus(n.path("status").asText(""));
    }

    /** Builds the RazorpayX composite-payout request body. Amount stays in integer minor units (paise). */
    private Map<String, Object> buildRequest(Payout payout) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("account_number", props.accountNumber());
        req.put("amount", payout.getAmountMinor());
        req.put("currency", payout.getCurrency());
        req.put("mode", payout.getRail().name()); // UPI/IMPS/NEFT/RTGS == RazorpayX modes
        req.put("purpose", props.purpose());
        req.put("queue_if_low_balance", true);
        req.put("reference_id", payout.getId().toString());
        req.put("narration", "Qeet Pay payout");
        req.put("fund_account", buildFundAccount(payout));
        return req;
    }

    private Map<String, Object> buildFundAccount(Payout payout) {
        Map<String, Object> fundAccount = new LinkedHashMap<>();
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("name", "Qeet Pay payee");
        contact.put("type", "customer");
        fundAccount.put("contact", contact);

        String destination = payout.getDestination() == null ? "" : payout.getDestination();
        if (payout.getRail() == PayoutRail.UPI) {
            fundAccount.put("account_type", "vpa");
            Map<String, Object> vpa = new LinkedHashMap<>();
            vpa.put("address", destination);
            fundAccount.put("vpa", vpa);
        } else {
            fundAccount.put("account_type", "bank_account");
            Map<String, Object> bankAccount = new LinkedHashMap<>();
            bankAccount.put("name", "Qeet Pay payee");
            int at = destination.indexOf('@'); // <accountNumber>@<ifsc>
            if (at > 0) {
                bankAccount.put("account_number", destination.substring(0, at));
                bankAccount.put("ifsc", destination.substring(at + 1));
            } else {
                bankAccount.put("account_number", destination);
            }
            fundAccount.put("bank_account", bankAccount);
        }
        return fundAccount;
    }

    private static boolean isTerminalFailure(String status) {
        return switch (normalize(status)) {
            case "failed", "reversed", "rejected", "cancelled" -> true;
            default -> false;
        };
    }

    private static PayoutStatus mapStatus(String status) {
        return switch (normalize(status)) {
            case "processed" -> PayoutStatus.PAID;
            case "failed", "reversed" -> PayoutStatus.FAILED;
            case "cancelled", "rejected" -> PayoutStatus.REJECTED;
            default -> PayoutStatus.PENDING_APPROVAL; // queued/pending/processing/scheduled/…
        };
    }

    private static String normalize(String status) {
        return status == null ? "" : status.toLowerCase(Locale.ROOT);
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("invalid RazorpayX payout response", e);
        }
    }
}
