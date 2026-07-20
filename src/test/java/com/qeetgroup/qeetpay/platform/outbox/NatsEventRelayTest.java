package com.qeetgroup.qeetpay.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qeetgroup.qeetpay.platform.outbox.TestCapturingNatsPublisher.Captured;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for the outbox → NATS relay (no Spring context, no DB, no broker). Proves the
 * at-least-once contract: pending rows are drained to the correct subject and stamped published, and a
 * publish failure leaves the row un-stamped (and un-persisted) so the next tick retries it.
 */
class NatsEventRelayTest {

    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final TestCapturingNatsPublisher publisher = new TestCapturingNatsPublisher();
    private final NatsEventRelay relay = new NatsEventRelay(outbox, publisher);

    @Test
    void drainsPendingEventsToCorrectSubjectAndMarksPublished() {
        UUID merchant = UUID.randomUUID();
        OutboxEvent captured = event(merchant, "payment.captured");
        OutboxEvent refunded = event(merchant, "payment.refunded");
        when(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(captured, refunded));

        relay.drain();

        // Published to pay.{merchant_id}.events.{type}, in enqueue order.
        assertThat(publisher.captured())
                .extracting(Captured::subject)
                .containsExactly(
                        "pay." + merchant + ".events.payment.captured",
                        "pay." + merchant + ".events.payment.refunded");
        // Both rows are stamped published and persisted (each in its own short transaction).
        assertThat(captured.getPublishedAt()).isNotNull();
        assertThat(refunded.getPublishedAt()).isNotNull();
        verify(outbox, times(2)).save(any(OutboxEvent.class));
    }

    @Test
    void publishFailureLeavesEventUnmarkedForRetry() {
        UUID merchant = UUID.randomUUID();
        OutboxEvent first = event(merchant, "payment.captured");
        OutboxEvent poison = event(merchant, "payment.failed");
        when(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(first, poison));
        publisher.failOnSubject(subject -> subject.endsWith("payment.failed"));

        relay.drain();

        // The first succeeded and is stamped; the poison row is left un-marked for the next tick.
        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(poison.getPublishedAt()).isNull();
        // Only the first was actually published — the loop stops at the failure to preserve ordering.
        assertThat(publisher.captured())
                .extracting(Captured::subject)
                .containsExactly("pay." + merchant + ".events.payment.captured");
        // Only the successful row was persisted; the poison row stays pending.
        verify(outbox, times(1)).save(first);
        verify(outbox, never()).save(poison);
    }

    private static OutboxEvent event(UUID merchant, String type) {
        return new OutboxEvent(merchant, "pay." + merchant + ".events." + type, type, "{}");
    }
}
