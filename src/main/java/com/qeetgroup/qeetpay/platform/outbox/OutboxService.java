package com.qeetgroup.qeetpay.platform.outbox;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enqueues domain events into the outbox. {@link #enqueue} MUST run inside the caller's
 * transaction (default propagation) so the event commits atomically with the state change.
 */
@Service
public class OutboxService {

    private final OutboxRepository repository;

    public OutboxService(OutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID merchantId, String eventType, String payload) {
        String subject = "pay." + merchantId + ".events." + eventType;
        repository.save(new OutboxEvent(merchantId, subject, eventType, payload));
    }
}
