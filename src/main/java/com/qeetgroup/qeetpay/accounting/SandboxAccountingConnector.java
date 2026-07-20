package com.qeetgroup.qeetpay.accounting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sandbox stand-in for the live Zoho Books connector (same idea as {@code kyb}'s
 * {@code SandboxKybAdapter}). Registered only when {@link ZohoBooksConnector} is absent — i.e. no
 * Zoho creds are configured — so the {@code ZOHO} target still works out of the box in dev/test. It
 * performs no network call: it records the run as SUCCESS with a synthetic external reference and
 * echoes the payload JSON as the document.
 */
public class SandboxAccountingConnector implements AccountingConnector {

    private final ObjectMapper objectMapper;

    public SandboxAccountingConnector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AccountingTarget target() {
        return AccountingTarget.ZOHO;
    }

    @Override
    public SyncResult push(ExportPayload payload, AccountingConnection connection) {
        String externalRef = "sandbox-zoho-" + UUID.randomUUID();
        return SyncResult.ok(payload.recordCount(), externalRef, document(payload));
    }

    private String document(ExportPayload payload) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sandbox", true);
            body.put("target", AccountingTarget.ZOHO.name());
            body.put("recordCount", payload.recordCount());
            body.put("vouchers", payload.vouchers());
            body.put("invoices", payload.invoices());
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise sandbox accounting document", e);
        }
    }
}
