# Qeet Pay — Deployment

Production deployment assets for **Qeet Pay** (Java 21 / Spring Boot modular
monolith + TanStack Start console). Two supported targets:

- **Docker Compose** — base dev stack (`../docker-compose.yml`) + prod overlay
  (`docker-compose.prod.yml`).
- **Kubernetes** — plain manifests under [`k8s/`](k8s/).

Both build the same two images:

| Image | Built from | Port | Runs as |
| --- | --- | :--: | --- |
| backend (Spring Boot bootJar) | `../Dockerfile` | 4201 | non-root uid 10001 |
| console (Nitro node-server)   | `../apps/console/Dockerfile` | 3201 | non-root `node` uid 1000 |

Both images are multi-stage, run as a non-root user, expose their port, and ship
a container `HEALTHCHECK`. The backend hits `/actuator/health/readiness`; the
console hits `/`.

---

## 1. Build the images

```bash
# From the repo root.
# Backend:
docker build -t ghcr.io/qeetgroup/qeet-pay-backend:$(git rev-parse --short HEAD) .

# Console — VITE_API_URL is BAKED IN at build time (browser bundle), so it must be
# the PUBLIC URL the browser will use to reach the backend, not an internal name:
docker build -t ghcr.io/qeetgroup/qeet-pay-console:$(git rev-parse --short HEAD) \
  --build-arg VITE_API_URL=https://api.pay.qeet.in \
  apps/console
```

---

## 2. Run with Docker Compose

### Local / dev (the full stack on one network)

```bash
docker compose up --build          # postgres + nats + redis + backend + console
# console  -> http://localhost:3201
# backend  -> http://localhost:4201  (dev profile: boots without a live Qeet ID)
```

The base stack runs the backend in the **`dev`** profile so it boots without a
live Qeet ID, and Postgres as the **superuser** `qeet_pay` role. That is fine
locally but **bypasses Row-Level Security** — do not use it as-is in production.

### Production overlay

```bash
# Supply real secrets/hostnames via the environment (shell export, a .env file,
# or your secret manager), then layer the prod overlay on top of the base file:
export DATABASE_URL='jdbc:postgresql://db.internal:5432/qeet_pay'
export DATABASE_USER='qeet_pay_app'          # NOSUPERUSER role — see §4
export DATABASE_PASSWORD='********'
export ALLOWED_ORIGINS='https://pay.qeet.in'
export VITE_API_URL='https://api.pay.qeet.in'

docker compose \
  -f docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  up -d --build
```

The overlay ([`docker-compose.prod.yml`](docker-compose.prod.yml)):

- switches the backend to the **`prod`** profile (auth **required** — Qeet ID
  OIDC + `qp_live_`/`qp_test_` API keys — and ECS-JSON structured logs);
- injects DB creds / OIDC issuer / CORS origins from the environment;
- stops publishing the DB and internal service ports (`ports: !reset []` →
  `expose`) so only your ingress/LB is public;
- adds `restart: unless-stopped`, CPU/memory limits, and json-file log rotation.

> The `${VAR:-default}` placeholders keep `docker compose config` valid with no
> setup; the `*_REPLACE_ME` defaults are deliberately invalid so a real deploy
> must override them. For a managed database, point `DATABASE_URL` at it and skip
> the bundled `postgres` service (`--scale postgres=0`).

### Validate the compose files

```bash
docker compose -f docker-compose.yml config -q
docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml config -q
```

---

## 3. Run on Kubernetes

Manifests in [`k8s/`](k8s/) — apply in order (namespace → config/secret → workloads):

```bash
kubectl apply -f deploy/k8s/namespace.yaml
# Edit secret.yaml first (or generate it from your secret manager) — do NOT
# commit real credentials.
kubectl apply -f deploy/k8s/secret.yaml
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/backend-deployment.yaml
kubectl apply -f deploy/k8s/backend-service.yaml
kubectl apply -f deploy/k8s/backend-hpa.yaml
kubectl apply -f deploy/k8s/console-deployment.yaml
kubectl apply -f deploy/k8s/console-service.yaml
kubectl apply -f deploy/k8s/ingress.yaml
# ...or simply: kubectl apply -f deploy/k8s/
```

What you get:

- **backend** Deployment (2 replicas) with startup/readiness/liveness probes on
  the actuator health endpoints, a read-only root FS (+ writable `/tmp`), dropped
  capabilities, and Prometheus scrape annotations; a ClusterIP **Service**; and an
  **HPA** (2–10 replicas, CPU 70% / memory 80%).
- **console** Deployment (2 replicas) + ClusterIP Service.
- **Ingress** splitting `pay.qeet.in` → console and `api.pay.qeet.in` → backend
  (ingress-nginx + cert-manager assumed; adjust for your controller).
- A **ConfigMap** (non-secret config) + **Secret** template (DB + provider creds).

Prereqs: an ingress controller, cert-manager (or your own TLS), and
metrics-server (for the HPA). The bundled manifests do **not** include Postgres,
NATS, or Redis — point them at managed services or your own operators via the
ConfigMap/Secret.

---

## 4. Database, RLS & the `qeet_pay_app` role (read this)

Multi-tenancy is enforced by **Postgres Row-Level Security**, keyed off the
per-request GUC `app.current_merchant_id`. **RLS is silently bypassed by a
superuser or the table owner.** Therefore:

- Run **migrations / bootstrap** as the privileged owner role (Flyway V2 creates
  the schemas, RLS policies, and the `qeet_pay_app` role; the app auto-applies
  migrations on startup).
- Run the **application** connected as the **`NOSUPERUSER` `qeet_pay_app`** role
  (`DATABASE_USER=qeet_pay_app`). This is the only configuration under which
  tenant isolation actually holds (`RlsIsolationTest` proves the policy under
  that role). The ledger role is additionally granted SELECT/INSERT only — the
  ledger is append-only.

The dev compose intentionally uses the superuser `qeet_pay` role for
convenience; the prod overlay and the k8s Secret default `DATABASE_USER` to
`qeet_pay_app`.

---

## 5. Scaling & operations notes

- **Backend** is stateless (state lives in Postgres + the outbox) → scale
  horizontally via the HPA or `docker compose up --scale backend=N`. Flyway
  migrations run on startup; they are idempotent and safe under rolling updates,
  but avoid two *different* app versions applying migrations concurrently — do
  schema-changing releases as a controlled rollout.
- **Console** is a stateless SSR node-server → scale freely. Remember
  `VITE_API_URL` is build-time; changing the backend URL means rebuilding the
  console image.
- **NATS** outbox relay is off unless `QEETPAY_NATS_ENABLED=true`. Turn it on in
  prod once a durable NATS JetStream is available.
- **Health**: readiness (`/actuator/health/readiness`) gates traffic and only
  flips UP after Flyway + the datasource are ready; liveness
  (`/actuator/health/liveness`) restarts a wedged JVM. Both are `permitAll`.
- **Observability**: Prometheus metrics at `/actuator/prometheus`; the `prod`
  profile emits ECS-JSON logs to stdout for the Qeet Logs pipeline.
- **Resources**: backend limit 1.5Gi (JVM `MaxRAMPercentage=75%` → ~1.1Gi heap);
  tune with `JAVA_OPTS`. Console limit 512Mi.
