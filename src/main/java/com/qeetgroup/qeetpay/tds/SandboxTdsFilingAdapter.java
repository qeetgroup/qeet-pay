package com.qeetgroup.qeetpay.tds;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Sandbox TDS/TCS filing adapter (PRD Module 06.4) — a deterministic, offline stand-in for a live
 * TIN-FC / TRACES upload. Returns a 15-digit provisional receipt number in the real shape, derived
 * from the return identity so a given return always "files" to the same token (idempotent). Active
 * whenever no live adapter is present (mirrors {@code SandboxGstnFilingAdapter}).
 */
@Component
@ConditionalOnMissingBean(name = "liveTdsFilingAdapter")
public class SandboxTdsFilingAdapter implements TdsFilingAdapter {

    @Override
    public String file(TdsReturn ret) {
        String seed = ret.getMerchantId() + "|" + ret.getForm() + "|" + ret.getFy() + "|"
                + ret.getQuarter() + "|" + ret.getId();
        String digest = sha256Hex(seed);
        // The provisional receipt number is a 15-digit numeric token; fold the hash into digits.
        StringBuilder token = new StringBuilder(15);
        for (int i = 0; token.length() < 15 && i < digest.length(); i++) {
            token.append(Character.digit(digest.charAt(i), 16) % 10);
        }
        return token.toString();
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
