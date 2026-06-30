package com.qeetgroup.qeetpay.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Helpers for minting and hashing API keys (TAD §10.1). */
public final class ApiKeys {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private ApiKeys() {}

    /** A freshly minted raw key, e.g. {@code qp_test_<32 url-safe bytes>}. Shown once. */
    public static String generate(boolean live) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return (live ? "qp_live_" : "qp_test_") + B64.encodeToString(bytes);
    }

    /** First 12 chars of the raw key, kept for display (e.g. {@code qp_test_AbC}). */
    public static String prefix(String rawKey) {
        return rawKey.substring(0, Math.min(12, rawKey.length()));
    }

    /** SHA-256 hex of the raw key — the only form persisted. */
    public static String hash(String rawKey) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
