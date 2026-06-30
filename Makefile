.PHONY: db-up db-down db-reset dev build test lint kill

# Local infra (Postgres :5201, NATS :4222, Redis :6379)
db-up:
	docker compose up -d

db-down:
	docker compose down

db-reset:
	docker compose down -v && docker compose up -d postgres

# Run the API on :4201 in the dev profile (boots without a live Qeet ID)
dev:
	SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun

build:
	./gradlew bootJar

# Integration-test-first against a real Postgres (Testcontainers needs a Docker engine).
# On a very new Docker Desktop engine, prefix with the env from CLAUDE.md (Gotchas).
test:
	./gradlew test

lint:
	./gradlew check -x test

kill:
	-lsof -ti:4201 | xargs kill -9
