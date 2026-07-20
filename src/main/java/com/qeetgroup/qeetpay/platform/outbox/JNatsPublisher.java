package com.qeetgroup.qeetpay.platform.outbox;

import com.qeetgroup.qeetpay.platform.config.AppProperties;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real NATS <b>JetStream</b> publisher, active only when {@code qeetpay.nats.enabled=true}. Connects
 * lazily, self-bootstraps the durable {@code PAY_EVENTS} stream (subjects {@code pay.>}) if absent, and
 * publishes with a server acknowledgement: an un-acked or failed publish throws, so the relay leaves the
 * outbox row un-marked for a later retry. That gives true at-least-once delivery end to end — a
 * committed outbox row and a durable, acknowledged JetStream message.
 */
@Component
@ConditionalOnProperty(name = "qeetpay.nats.enabled", havingValue = "true")
public class JNatsPublisher implements NatsPublisher {

    private static final Logger log = LoggerFactory.getLogger(JNatsPublisher.class);

    /** Durable stream capturing every merchant's {@code pay.{merchant_id}.events.{type}} subject. */
    static final String STREAM_NAME = "PAY_EVENTS";

    static final String STREAM_SUBJECTS = "pay.>";

    private final String url;
    private volatile Connection connection;
    private volatile JetStream jetStream;

    public JNatsPublisher(AppProperties props) {
        this.url = props.getNats().getUrl();
    }

    @Override
    public void publish(String subject, String payload) {
        try {
            jetStream().publish(subject, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("NATS JetStream publish failed for " + subject, e);
        }
    }

    private JetStream jetStream() throws IOException, JetStreamApiException, InterruptedException {
        JetStream js = jetStream;
        if (js != null && connection != null && connection.getStatus() == Connection.Status.CONNECTED) {
            return js;
        }
        synchronized (this) {
            if (connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
                log.info("Connecting to NATS at {}", url);
                connection = Nats.connect(url);
                ensureStream(connection);
                jetStream = connection.jetStream();
            } else if (jetStream == null) {
                ensureStream(connection);
                jetStream = connection.jetStream();
            }
            return jetStream;
        }
    }

    private void ensureStream(Connection c) throws IOException, JetStreamApiException {
        JetStreamManagement jsm = c.jetStreamManagement();
        if (!jsm.getStreamNames().contains(STREAM_NAME)) {
            log.info("Creating NATS JetStream stream {} for subjects {}", STREAM_NAME, STREAM_SUBJECTS);
            jsm.addStream(
                    StreamConfiguration.builder()
                            .name(STREAM_NAME)
                            .subjects(STREAM_SUBJECTS)
                            .storageType(StorageType.File)
                            .build());
        }
    }

    @PreDestroy
    void close() throws InterruptedException {
        if (connection != null) {
            connection.close();
        }
    }
}
