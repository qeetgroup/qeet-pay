package com.qeetgroup.qeetpay.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability wiring (TAD §13). Metrics flow to the already-exposed {@code /actuator/prometheus};
 * traces are exported over OTLP to qeet-logs when enabled (see {@code management.otlp.tracing.*} —
 * export is a no-op by default). This class stamps a stable {@code service} tag onto every meter so
 * Qeet Pay's series are identifiable in a shared Prometheus/qeet-logs backend, complementing the
 * {@code service.name} resource attribute Boot derives from {@code spring.application.name}.
 */
@Configuration
public class ObservabilityConfig {

    /** Adds a common {@code service} tag to every meter in the registry. */
    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonMetricTags(
            @Value("${spring.application.name:qeet-pay}") String serviceName) {
        return registry ->
                registry.config().meterFilter(MeterFilter.commonTags(Tags.of("service", serviceName)));
    }
}
