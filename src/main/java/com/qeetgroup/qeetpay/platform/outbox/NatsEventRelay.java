package com.qeetgroup.qeetpay.platform.outbox;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the transactional outbox to NATS (TAD §9.1). Active only when {@code qeetpay.nats.enabled=true}
 * (in dev/test the outbox simply accumulates — the rows prove the foundation without needing a broker).
 *
 * <p>Delivery contract:
 *
 * <ul>
 *   <li><b>At-least-once</b> — a row is stamped {@code published_at} only <em>after</em> the publish
 *       returns, so a crash or broker failure re-delivers it on a later tick; an event is never lost.
 *   <li><b>Never blocks the writer</b> — pending rows are read, and each is stamped in its own short
 *       transaction, but the (network) publish happens <em>outside</em> any DB transaction. A slow or
 *       hung broker can therefore never hold a lock against the domain transactions that enqueue events.
 *   <li><b>Ordered, with backoff</b> — publishing stops at the first failure to preserve per-merchant
 *       ordering, and consecutive failures back the loop off exponentially (capped) so a broker outage
 *       doesn't hot-loop the scheduler.
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "qeetpay.nats.enabled", havingValue = "true")
public class NatsEventRelay {

    private static final Logger log = LoggerFactory.getLogger(NatsEventRelay.class);

    /** Backoff floor/ceiling applied after consecutive publish failures. */
    private static final long BACKOFF_BASE_MS = 1_000L;

    private static final long BACKOFF_MAX_MS = 60_000L;

    private final OutboxRepository outbox;
    private final NatsPublisher publisher;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong nextAttemptAtMs = new AtomicLong();

    public NatsEventRelay(OutboxRepository outbox, NatsPublisher publisher) {
        this.outbox = outbox;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${qeetpay.nats.relay-interval-ms:1000}")
    public void drain() {
        if (System.currentTimeMillis() < nextAttemptAtMs.get()) {
            return; // still backing off from a recent failure
        }
        List<OutboxEvent> pending = outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                publisher.publish(event.getSubject(), event.getPayload());
            } catch (RuntimeException e) {
                backOff();
                log.warn(
                        "Outbox publish failed for {} (failure streak {}); retrying after backoff",
                        event.getId(),
                        consecutiveFailures.get(),
                        e);
                return; // preserve ordering; resume from this un-marked row on the next eligible tick
            }
            markPublished(event);
        }
        if (consecutiveFailures.getAndSet(0) > 0) {
            nextAttemptAtMs.set(0);
            log.info("Outbox relay recovered; resumed normal draining");
        }
    }

    /** Stamp the row published in its own short transaction — no DB tx is held during the publish. */
    private void markPublished(OutboxEvent event) {
        event.markPublished();
        outbox.save(event);
    }

    private void backOff() {
        int failures = consecutiveFailures.incrementAndGet();
        long delay = Math.min(BACKOFF_MAX_MS, BACKOFF_BASE_MS << Math.min(failures - 1, 16));
        nextAttemptAtMs.set(System.currentTimeMillis() + delay);
    }
}
