package com.qeetgroup.qeetpay.platform.outbox;

import com.qeetgroup.qeetpay.platform.config.AppProperties;
import io.nats.client.Connection;
import io.nats.client.Nats;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real JetStream publisher, active only when {@code qeetpay.nats.enabled=true}. Connects lazily and
 * tolerates an unreachable broker (logs + rethrows so the relay leaves the row unpublished for a
 * later retry).
 */
@Component
@ConditionalOnProperty(name = "qeetpay.nats.enabled", havingValue = "true")
public class JNatsPublisher implements NatsPublisher {

    private static final Logger log = LoggerFactory.getLogger(JNatsPublisher.class);

    private final String url;
    private volatile Connection connection;

    public JNatsPublisher(AppProperties props) {
        this.url = props.getNats().getUrl();
    }

    @Override
    public void publish(String subject, String payload) {
        try {
            connection().publish(subject, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("NATS publish failed for " + subject, e);
        }
    }

    private Connection connection() throws Exception {
        Connection c = connection;
        if (c == null || c.getStatus() != Connection.Status.CONNECTED) {
            synchronized (this) {
                if (connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
                    log.info("Connecting to NATS at {}", url);
                    connection = Nats.connect(url);
                }
                c = connection;
            }
        }
        return c;
    }

    @PreDestroy
    void close() throws InterruptedException {
        if (connection != null) {
            connection.close();
        }
    }
}
