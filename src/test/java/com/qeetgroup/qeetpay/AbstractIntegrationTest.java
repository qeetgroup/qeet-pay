package com.qeetgroup.qeetpay;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for {@code @SpringBootTest} integration tests — the <b>singleton-container</b> pattern.
 *
 * <p>Historically every one of the ~58 integration tests declared its own {@code @Container} Postgres.
 * Run together (and with RYUK disabled locally) those containers pile up and exhaust Docker, cascading
 * into "context load threshold exceeded" failures even though each class passes in isolation.
 *
 * <p>This base fixes that two ways at once:
 *
 * <ul>
 *   <li><b>One container per JVM.</b> The static {@link #POSTGRES} is created and {@code start()}ed
 *       exactly once (a JVM-wide singleton — no {@code @Testcontainers}/{@code @Container}, so JUnit
 *       never stops/restarts it between classes); Testcontainers' own shutdown hook reaps it at JVM
 *       exit. {@code withReuse(true)} additionally lets it survive across runs when the developer has
 *       {@code testcontainers.reuse.enable=true} (the Gradle {@code test} task sets it).
 *   <li><b>One Spring context for all adopters.</b> Because every subclass shares the <i>same</i>
 *       {@code @ServiceConnection} container instance (identical JDBC coordinates) and the same
 *       {@code @SpringBootTest}/{@code @ActiveProfiles} config, Spring's context cache returns the
 *       same {@code ApplicationContext} instead of building one per class.
 * </ul>
 *
 * <p><b>To adopt:</b> make the test {@code extends AbstractIntegrationTest} and delete its local
 * {@code @SpringBootTest}, {@code @ActiveProfiles("test")}, {@code @Testcontainers}, {@code @Container}
 * and the {@code PostgreSQLContainer} field. Add {@code @AutoConfigureMockMvc} etc. as needed. See
 * {@code ContextLoadsTest} / {@code RlsIsolationTest} for worked examples. Rolling the remaining
 * classes onto this base is what makes {@code ./gradlew test} deterministic and lets
 * {@code maxParallelForks} be raised.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine")).withReuse(true);

    static {
        POSTGRES.start();
    }
}
