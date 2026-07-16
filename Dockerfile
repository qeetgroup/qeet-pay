# syntax=docker/dockerfile:1

# ==============================================================================
# Qeet Pay — backend (Spring Boot 3.4 modular monolith, Java 21) production image.
#
# Multi-stage:
#   1. build   — Gradle 8.14 + JDK 21, produces the Spring Boot bootJar.
#   2. runtime — eclipse-temurin:21-jre, runs the fat jar as a NON-root user.
#
# API port 4201 ("pay" band, see CLAUDE.md). Default profile is `prod`
# (structured ECS-JSON logs; auth required — Qeet ID OIDC + qp_live_/qp_test_
# API keys). Override SPRING_PROFILES_ACTIVE=dev to boot without a live Qeet ID.
# ==============================================================================

# --- Stage 1: build the bootJar ----------------------------------------------
# Invocation kept identical to the original Dockerfile (same inputs + task) so
# this produces exactly the same artifact — only the runtime stage is hardened.
FROM gradle:8.14-jdk21 AS build
WORKDIR /src

# Copy only the Gradle build inputs so this layer caches unless they change.
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src ./src

# Produce build/libs/qeet-pay-<version>.jar. --no-daemon: one-shot build.
RUN gradle bootJar --no-daemon

# --- Stage 2: slim JRE runtime -----------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

# curl is used only by the HEALTHCHECK below; no recommended extras, cache purged.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Dedicated non-root system user/group (never run the JVM as root in prod).
RUN groupadd --system --gid 10001 qeetpay \
 && useradd  --system --uid 10001 --gid qeetpay --home-dir /app --shell /usr/sbin/nologin qeetpay

WORKDIR /app

# Copy only the built artifact — no sources, no Gradle cache — owned by the app user.
COPY --from=build --chown=qeetpay:qeetpay /src/build/libs/*.jar /app/app.jar

# Runtime defaults. SERVER_PORT / SPRING_PROFILES_ACTIVE are read by application.yml.
# JAVA_OPTS is container-aware: MaxRAMPercentage makes the JVM honour the cgroup
# memory limit instead of the host's total RAM (-XX:+UseContainerSupport is on by
# default in JDK 21). ExitOnOutOfMemoryError lets the orchestrator restart cleanly.
ENV SERVER_PORT=4201 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

USER qeetpay

EXPOSE 4201

# Readiness (Flyway + datasource up) is enabled in application.yml
# (management.endpoint.health.probes) and permitAll in SecurityConfig, so this
# works unauthenticated in every profile. start-period covers Flyway migration.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness" || exit 1

# exec form via sh so $JAVA_OPTS expands while the JVM stays PID 1 (receives
# SIGTERM for graceful shutdown).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]