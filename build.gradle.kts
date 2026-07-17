plugins {
    java
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.qeetgroup"
version = "0.0.1-SNAPSHOT"
description = "Qeet Pay — payments/billing backend (modular monolith)"

// Targets JVM 21 (TAD §3.2 / TECH-STACK-GUIDE). Compiles with whatever JDK runs Gradle;
// the production image runs on a JRE 21 (see Dockerfile).
java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

extra["springModulithVersion"] = "1.3.4"

dependencies {
    // Web + persistence
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Identity: OIDC relying party (Qeet ID) — resource server validates JWTs
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Modular monolith boundaries (TAD §3.1, §5). Core only — qeet-pay uses its own explicit
    // transactional outbox table, not Modulith's JPA event-publication registry.
    implementation("org.springframework.modulith:spring-modulith-starter-core")

    // Eventing: transactional outbox relay → NATS JetStream (TAD §9.1)
    implementation("io.nats:jnats:2.20.4")

    // Observability — metrics (Prometheus) + traces (Micrometer Tracing → OTLP export to qeet-logs).
    // OTLP export is a no-op until enabled (management.otlp.tracing.export.enabled, default false);
    // the Prometheus registry always feeds the already-exposed /actuator/prometheus endpoint.
    // Versions of the tracing/OTLP artifacts are managed by the Spring Boot 3.4 BOM.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // API docs — OpenAPI 3 + Swagger UI (springdoc 2.8.x targets Spring Boot 3.4).
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    // Migrations + driver
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Payment provider SDKs
    implementation("com.razorpay:razorpay-java:1.4.5")

    // Tests — integration-test-first against a real Postgres (Testcontainers)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Bridge DOCKER_API_VERSION env -> docker-java's `api.version` system property for the forked
    // test JVM. Needed on some Docker Desktop engines whose API max is below docker-java's default
    // (or whose min is above it), which otherwise 400s Testcontainers. Unset on CI/Linux = no-op.
    System.getenv("DOCKER_API_VERSION")?.let { systemProperty("api.version", it) }

    // --- Full-suite Testcontainers reliability -------------------------------------------------
    // ~58 @SpringBootTest classes each spin their own Postgres. Running them concurrently (or letting
    // containers pile up in one long-lived JVM with RYUK disabled) exhausts Docker and cascades into
    // "context load threshold exceeded" failures, even though each class passes in isolation.
    //
    // Determinism strategy:
    //   * maxParallelForks = 1  → never more than one test JVM, so containers never start in parallel.
    //   * forkEvery = 12        → recycle the JVM every 12 classes; each fork's shutdown hooks reap
    //                             any leaked containers (Testcontainers registers them even with RYUK
    //                             off), bounding how many Postgres instances can accumulate.
    //   * testcontainers.reuse.enable → lets classes that adopt AbstractIntegrationTest reuse a single
    //                             singleton Postgres across the run (see that base class). Reuse across
    //                             JVMs additionally needs ~/.testcontainers.properties, but the in-JVM
    //                             singleton already collapses adopters onto one container + one context.
    // Raising maxParallelForks is only safe once more classes adopt AbstractIntegrationTest; otherwise
    // parallel forks each spin their own container and re-exhaust Docker.
    maxParallelForks = 1
    setForkEvery(12)
    systemProperty("testcontainers.reuse.enable", "true")
}
