package com.qeetgroup.qeetpay.platform.outbox;

/** Publishes an outbox payload to a NATS subject. */
public interface NatsPublisher {
    void publish(String subject, String payload);
}
