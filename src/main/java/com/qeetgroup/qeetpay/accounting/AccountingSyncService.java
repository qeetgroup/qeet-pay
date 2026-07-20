package com.qeetgroup.qeetpay.accounting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates accounting exports (PRD Module 11.3). {@link #export} builds the period payload from
 * ledger + GST, records an {@link AccountingSync} run, dispatches it to the connector matching the
 * requested target, persists the outcome (status / external ref / record count / document), and
 * emits an outbox event. Also manages per-merchant {@link AccountingConnection} settings. Every unit
 * of work applies the merchant RLS scope first.
 */
@Service
public class AccountingSyncService {

    private final AccountingSyncRepository syncs;
    private final AccountingConnectionRepository connections;
    private final AccountingExportBuilder exportBuilder;
    private final Map<AccountingTarget, AccountingConnector> connectors;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public AccountingSyncService(
            AccountingSyncRepository syncs,
            AccountingConnectionRepository connections,
            AccountingExportBuilder exportBuilder,
            List<AccountingConnector> connectorBeans,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.syncs = syncs;
        this.connections = connections;
        this.exportBuilder = exportBuilder;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.connectors = new EnumMap<>(AccountingTarget.class);
        for (AccountingConnector c : connectorBeans) {
            AccountingConnector prior = this.connectors.put(c.target(), c);
            if (prior != null) {
                throw new IllegalStateException(
                        "two accounting connectors registered for target " + c.target()
                                + " (" + prior.getClass().getSimpleName() + ", " + c.getClass().getSimpleName() + ")");
            }
        }
    }

    /** Runs an export of {@code [from, to)} to {@code target}, recording the run. */
    @Transactional
    public AccountingSync export(UUID merchantId, AccountingTarget target, Instant from, Instant to) {
        merchantScope.apply(merchantId);
        AccountingConnector connector = connectors.get(target);
        if (connector == null) {
            throw new IllegalArgumentException("no connector available for target " + target);
        }

        ExportPayload payload = exportBuilder.build(merchantId, from, to);
        AccountingConnection connection =
                connections.findByMerchantIdAndTarget(merchantId, target).orElse(null);

        AccountingSync run = syncs.save(new AccountingSync(merchantId, target, from, to));
        SyncResult result = connector.push(payload, connection);
        if (result.success()) {
            run.markSuccess(result.recordCount(), result.externalRef(), result.document());
        } else {
            run.markFailed(result.detail());
        }
        syncs.save(run);

        outbox.enqueue(merchantId, "accounting.export.completed", exportJson(run));
        return run;
    }

    @Transactional(readOnly = true)
    public List<AccountingSync> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return syncs.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public AccountingSync get(UUID merchantId, UUID syncId) {
        merchantScope.apply(merchantId);
        return load(merchantId, syncId);
    }

    /** The generated document for download (Tally XML for a TALLY run; JSON otherwise). */
    @Transactional(readOnly = true)
    public AccountingSync download(UUID merchantId, UUID syncId) {
        merchantScope.apply(merchantId);
        AccountingSync run = load(merchantId, syncId);
        if (run.getDocument() == null) {
            throw new AccountingSyncNotFoundException("export " + syncId + " has no downloadable document");
        }
        return run;
    }

    // ── Connections ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AccountingConnection> listConnections(UUID merchantId) {
        merchantScope.apply(merchantId);
        return connections.findByMerchantIdOrderByTarget(merchantId);
    }

    @Transactional
    public AccountingConnection upsertConnection(
            UUID merchantId, AccountingTarget target, boolean enabled, String webhookUrl, String zohoOrganizationId) {
        merchantScope.apply(merchantId);
        AccountingConnection connection =
                connections
                        .findByMerchantIdAndTarget(merchantId, target)
                        .orElseGet(() -> new AccountingConnection(merchantId, target));
        connection.update(enabled, webhookUrl, zohoOrganizationId);
        AccountingConnection saved = connections.save(connection);
        outbox.enqueue(merchantId, "accounting.connection.updated", connectionJson(saved));
        return saved;
    }

    private AccountingSync load(UUID merchantId, UUID syncId) {
        return syncs
                .findById(syncId)
                .filter(s -> s.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new AccountingSyncNotFoundException("no export " + syncId));
    }

    private String exportJson(AccountingSync run) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("syncId", run.getId().toString());
        b.put("target", run.getTarget().name());
        b.put("status", run.getStatus().name());
        b.put("recordCount", run.getRecordCount());
        b.put("externalRef", run.getExternalRef());
        return write(b);
    }

    private String connectionJson(AccountingConnection c) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("connectionId", c.getId().toString());
        b.put("target", c.getTarget().name());
        b.put("enabled", c.isEnabled());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise accounting event", e);
        }
    }
}
