package com.qeetgroup.qeetpay.platform.outbox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Test double for {@link NatsPublisher}: records every published {@code (subject, payload)} instead of
 * hitting a broker, and can be told to fail for chosen subjects to exercise the relay's retry path.
 * Requires no NATS server.
 */
class TestCapturingNatsPublisher implements NatsPublisher {

    /** One captured publish. */
    record Captured(String subject, String payload) {}

    private final List<Captured> published = new ArrayList<>();
    private Predicate<String> failOn = subject -> false;

    /** Make {@link #publish} throw (as a real broker outage would) for subjects matching {@code p}. */
    void failOnSubject(Predicate<String> p) {
        this.failOn = p;
    }

    @Override
    public void publish(String subject, String payload) {
        if (failOn.test(subject)) {
            throw new IllegalStateException("simulated NATS failure for " + subject);
        }
        published.add(new Captured(subject, payload));
    }

    List<Captured> captured() {
        return published;
    }
}
