package com.qeetgroup.qeetpay.accounting;

/**
 * A pluggable sink for an {@link ExportPayload} — Tally XML, Zoho Books, a generic webhook, or the
 * sandbox no-op. The {@link AccountingSyncService} routes an export to the connector whose
 * {@link #target()} matches the requested target.
 */
public interface AccountingConnector {

    /** The target this connector serves. Exactly one active connector exists per target. */
    AccountingTarget target();

    /**
     * Delivers the payload to the target. {@code connection} carries per-merchant settings (webhook
     * URL, Zoho org id) and may be {@code null} when none is configured. Implementations never throw
     * for transport failures — they return {@link SyncResult#failure(String)}.
     */
    SyncResult push(ExportPayload payload, AccountingConnection connection);
}
