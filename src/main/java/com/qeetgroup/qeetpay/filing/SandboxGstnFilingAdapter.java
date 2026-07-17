package com.qeetgroup.qeetpay.filing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Sandbox GSTN filing adapter (TAD §7.4, §11.1) — deterministic, offline stand-in for a live GSTN
 * endpoint. Returns a 15-character ARN in the real shape ({@code AA} + a period/return-derived body),
 * so downstream code can round-trip it without a network call. Active whenever no live adapter is present.
 */
@Component
@ConditionalOnMissingBean(name = "liveGstnFilingAdapter")
public class SandboxGstnFilingAdapter implements GstnFilingAdapter {

    @Override
    public String file(GstReturn ret, List<GstReturnLine> lines) {
        // ARN is unique per filing; derive a stable 13-char body from the return identity.
        String seed = ret.getMerchantId() + "|" + ret.getReturnType() + "|" + ret.getPeriod() + "|" + ret.getId();
        String body = sha256Hex(seed).substring(0, 13).toUpperCase();
        return "AA" + body; // AA + 13 chars = 15, the ARN length
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
