package com.qeetgroup.qeetpay.kyb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Live KYB verification backend (PRD Module 19). Talks to a SurePASS / SignDesk / NSDL / GSTN-style
 * provider over HTTP for PAN, GSTIN and bank penny-drop verification. Active only when
 * {@code qeetpay.kyb.enabled=true}; {@code @Primary} so it wins over the {@link SandboxKybAdapter}
 * whenever both are somehow present.
 *
 * <p>Fails <em>closed</em>: any transport / parse error yields {@code REJECTED} rather than silently
 * passing verification (the conservative choice for KYC/KYB — a merchant is never auto-approved on an
 * outage). Requests and responses are serialized/parsed with Jackson explicitly (a String JSON body,
 * robust across RestClient request factories, mirroring {@code HttpFraudClient}).
 *
 * <p>The bean name is {@code liveKybAdapter}, which is exactly what {@code SandboxKybAdapter}'s
 * {@code @ConditionalOnMissingBean(name = "liveKybAdapter")} keys off, so the sandbox backs off
 * automatically when this adapter is enabled.
 */
@ConditionalOnProperty(prefix = "qeetpay.kyb", name = "enabled", havingValue = "true")
@Primary
@Component
public class LiveKybAdapter implements KybVerificationAdapter {

    private static final Logger log = LoggerFactory.getLogger(LiveKybAdapter.class);

    private final RestClient http;
    private final ObjectMapper objectMapper;

    public LiveKybAdapter(KybProperties props, RestClient.Builder builder, ObjectMapper objectMapper) {
        // NB: we deliberately do NOT override the request factory here — that lets a test bind a
        // MockRestServiceServer to the same builder (which installs its own mock factory) so the
        // provider call is intercepted without any network I/O. In production the Boot-configured
        // RestClient.Builder supplies the default client.
        RestClient.Builder b = builder.baseUrl(props.baseUrl());
        if (!props.apiKey().isBlank()) {
            b = b.defaultHeader("Authorization", "Bearer " + props.apiKey());
        }
        if (!props.clientId().isBlank()) {
            b = b.defaultHeader("X-Client-Id", props.clientId());
        }
        this.http = b.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String verifyPan(String pan) {
        return verify("/pan", Map.of("pan", pan == null ? "" : pan));
    }

    @Override
    public String verifyGstin(String gstin) {
        return verify("/gstin", Map.of("gstin", gstin == null ? "" : gstin));
    }

    @Override
    public String verifyBankAccount(String accountNumber, String ifsc) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account_number", accountNumber == null ? "" : accountNumber);
        body.put("ifsc", ifsc == null ? "" : ifsc);
        return verify("/bank/penny-drop", body);
    }

    /** POST the request, map the provider's boolean/status response to VERIFIED / REJECTED. */
    private String verify(String path, Map<String, Object> body) {
        try {
            String responseJson =
                    http.post()
                            .uri(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(objectMapper.writeValueAsString(body))
                            .retrieve()
                            .body(String.class);

            if (responseJson == null || responseJson.isBlank()) {
                log.warn("KYB provider returned an empty response for {}; treating as REJECTED", path);
                return MerchantKyb.REJECTED;
            }
            JsonNode n = objectMapper.readTree(responseJson);
            boolean ok =
                    n.path("verified").asBoolean(false)
                            || n.path("valid").asBoolean(false)
                            || "VALID".equalsIgnoreCase(n.path("status").asText(""))
                            || "VERIFIED".equalsIgnoreCase(n.path("status").asText(""));
            return ok ? MerchantKyb.VERIFIED : MerchantKyb.REJECTED;
        } catch (Exception e) {
            log.warn("KYB provider call to {} failed; failing closed (REJECTED)", path, e);
            return MerchantKyb.REJECTED;
        }
    }
}
