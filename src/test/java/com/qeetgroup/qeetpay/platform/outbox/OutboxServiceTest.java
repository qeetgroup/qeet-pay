package com.qeetgroup.qeetpay.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pure unit test (no Spring context, no DB): {@link OutboxService#enqueue} builds the canonical
 * {@code pay.{merchant_id}.events.{type}} subject and persists the row verbatim.
 */
class OutboxServiceTest {

    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final OutboxService service = new OutboxService(repository);

    @Test
    void enqueueBuildsCanonicalSubjectAndSavesRow() {
        UUID merchant = UUID.randomUUID();

        service.enqueue(merchant, "payment.captured", "{\"id\":\"pay_1\"}");

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSubject())
                .isEqualTo("pay." + merchant + ".events.payment.captured");
        assertThat(saved.getValue().getPayload()).isEqualTo("{\"id\":\"pay_1\"}");
        assertThat(saved.getValue().getPublishedAt()).isNull(); // freshly enqueued = still pending
    }
}
