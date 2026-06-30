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

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Migrations + driver
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

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
}
