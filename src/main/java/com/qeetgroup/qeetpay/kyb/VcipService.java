package com.qeetgroup.qeetpay.kyb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V-CIP session orchestrator (PRD Module 19). Drives the SCHEDULED → IN_PROGRESS →
 * COMPLETED/FAILED state machine; the transitions themselves are enforced on {@link VcipSession}.
 * Merchant-scoped via platform RLS.
 */
@Service
public class VcipService {

    private final VcipSessionRepository sessions;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public VcipService(
            VcipSessionRepository sessions,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public VcipSession schedule(
            UUID merchantId, String subjectName, String subjectRef, String agentId, Instant scheduledAt) {
        merchantScope.apply(merchantId);
        if (subjectName == null || subjectName.isBlank()) {
            throw new IllegalArgumentException("subjectName is required");
        }
        VcipSession session =
                sessions.save(new VcipSession(merchantId, subjectName, subjectRef, agentId, scheduledAt));
        emit(merchantId, "kyb.vcip_scheduled", session);
        return session;
    }

    @Transactional
    public VcipSession start(UUID merchantId, UUID sessionId) {
        merchantScope.apply(merchantId);
        VcipSession session = load(merchantId, sessionId);
        session.start();
        sessions.save(session);
        return session;
    }

    @Transactional
    public VcipSession complete(
            UUID merchantId, UUID sessionId, String biometricRef, Integer livenessScore, String geoTag) {
        merchantScope.apply(merchantId);
        VcipSession session = load(merchantId, sessionId);
        session.complete(biometricRef, livenessScore, geoTag);
        sessions.save(session);
        emit(merchantId, "kyb.vcip_completed", session);
        return session;
    }

    @Transactional
    public VcipSession fail(UUID merchantId, UUID sessionId, String reason) {
        merchantScope.apply(merchantId);
        VcipSession session = load(merchantId, sessionId);
        session.fail(reason);
        sessions.save(session);
        emit(merchantId, "kyb.vcip_failed", session);
        return session;
    }

    @Transactional(readOnly = true)
    public VcipSession get(UUID merchantId, UUID sessionId) {
        merchantScope.apply(merchantId);
        return load(merchantId, sessionId);
    }

    @Transactional(readOnly = true)
    public List<VcipSession> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return sessions.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    private VcipSession load(UUID merchantId, UUID sessionId) {
        return sessions
                .findById(sessionId)
                .filter(s -> s.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new VcipNotFoundException("no V-CIP session " + sessionId));
    }

    private void emit(UUID merchantId, String type, VcipSession session) {
        try {
            String payload =
                    objectMapper.writeValueAsString(Map.of(
                            "merchantId", merchantId.toString(),
                            "sessionId", session.getId().toString(),
                            "status", session.getStatus().name()));
            outbox.enqueue(merchantId, type, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("vcip event serialisation failed", e);
        }
    }
}
