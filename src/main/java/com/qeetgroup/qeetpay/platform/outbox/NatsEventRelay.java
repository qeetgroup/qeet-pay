package com.qeetgroup.qeetpay.platform.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the transactional outbox to NATS (TAD §9.1). Active only when
 * {@code qeetpay.nats.enabled=true}; in dev/test the outbox simply accumulates (rows prove the
 * foundation without requiring a broker). At-least-once: a row is marked published only after a
 * successful send, so a failed publish is retried on the next tick.
 */
@Component
@ConditionalOnProperty(name = "qeetpay.nats.enabled", havingValue = "true")
public class NatsEventRelay {

    private static final Logger log = LoggerFactory.getLogger(NatsEventRelay.class);

    private final OutboxRepository outbox;
    private final NatsPublisher publisher;

    public NatsEventRelay(OutboxRepository outbox, NatsPublisher publisher) {
        this.outbox = outbox;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${qeetpay.nats.relay-interval-ms:1000}")
    @Transactional
    public void drain() {
        for (OutboxEvent event : outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                publisher.publish(event.getSubject(), event.getPayload());
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("Outbox publish failed for {}; will retry", event.getId(), e);
                break; // preserve ordering; retry from here next tick
            }
        }
    }
}
