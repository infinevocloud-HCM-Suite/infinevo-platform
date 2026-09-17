# W-49 — Containerisation

| Field | Value |
|---|---|
| **Work item** | `W-49` · issue [#69](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/69) |
| **Kind** | Infra |
| **Stream / track** | Stream G — Infrastructure · Track I |
| **Wave** | 1 — Foundations |
| **Size / skill** | M · INFRA |
| **Owner** | KarmaveerM |
| **Blocked by** | nothing — `W-01` merged |
| **Blocks** | `W-50` Azure IaC · `W-54` deployment pipeline · `W-59` scanning |
| **Capabilities** | `PLAT-11` build & deploy pipeline |
| **Decisions** | `D-01` modular monolith · `D-02` worker from same image · `D-10` Container Apps · `D-11` gateway deferred · `D-38` Java 21 · `D-42` Node 24 · deviation from `04` §3 (two jars + `INFINEVO_ROLE`) · unprivileged nginx on 8080 · base datasource & graceful shutdown in `application.yml` |
| **Gaps addressed** | `DEBT-004` (secrets in files) — fixed forward; `DEBT-020`, `DEBT-021` (two replicas) — deferred to `W-53` / `W-52` |
| **Status** | **Approved 2026-09-15 — ready for `/develop`** |
| **Approved by** | Founder |
| **Approved on** | 2026-09-15 · [#69 comment](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/69#issuecomment-5684855988) |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

The platform has no deployable container images. What exists today are two development
Dockerfiles shipped by `W-02`:

| File | What it does | Why it cannot be promoted |
|---|---|---|
| `infra/docker/dev.Dockerfile.backend` | Runs the backend from source via `mvn spring-boot:run` | Full JDK + Maven in the image (~800 MB), runs as root, build tooling at runtime |
| `infra/docker/dev.Dockerfile.frontend` | Runs Vite's dev server with HMR | No production build, no static file server, development-only |

Both say so explicitly in their headers: *"This is NOT the deployable image. W-49 writes
that one."* (`dev.Dockerfile.backend:2-4`, `dev.Dockerfile.frontend:2-4`).

The CI `images` job (`ci.yml:200-212`) builds these dev images as a provisional gate. It is
labelled `images (provisional)` and the comment says *"W-49 (#69) replaces these targets
with the production Dockerfiles"* (`:191-194`).

Additionally, the frontend hard-codes its API URL at build time via Vite's
`import.meta.env.VITE_API_BASE_URL` (`client.js:21`). This means a build for dev cannot
serve UAT or production — violating `04-runtime-containers.md:101`: *"the same image serves
every environment"*.

**Baseline, measured 2026-09-15:**

| Command | Exit | Output |
|---|---|---|
| `docker build -f infra/docker/dev.Dockerfile.backend -t test .` | 0 | ~800 MB image |
| `docker build -f infra/docker/dev.Dockerfile.frontend -t test .` | 0 | ~300 MB image (node + source) |
| `cd code/backend && ./mvnw -B clean verify` | 0 | BUILD SUCCESS, 25s |
| `cd code/frontend && npm run build` | 0 | 1443 modules, 432.55 kB, 9s |

## 2. Scope

**In scope**

- `infra/docker/backend.Dockerfile` — multi-stage, slim JRE runtime, non-root (`infinevo`), two jars
- `infra/docker/backend-entrypoint.sh` — role selector (`INFINEVO_ROLE=app|worker`), container memory sizing
- `infra/docker/frontend.Dockerfile` — multi-stage, `nginxinc/nginx-unprivileged:alpine` runtime, non-root (`101`) on port 8080
- `infra/docker/nginx/default.conf` — SPA routing, gzip, cache headers, health on port 8080
- `infra/docker/frontend-entrypoint.sh` — generates `env.js` from env vars at startup into writable webroot
- `infra/docker/keycloak.Dockerfile` — 2-stage build, `start --optimized`, `KC_HTTP_ENABLED=true`, `KC_PROXY_HEADERS=xforwarded` behind Front Door
- Backend `application.yml` updates: datasource configured from `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` without default secrets; `server.shutdown: graceful` on app and worker
- CI `images` job updated to build production Dockerfiles
- Minimal source change: `index.html` loads `env.js`, `client.js` reads `window.__ENV`

**Out of scope**

| Not here | Belongs to |
|---|---|
| Registry push | `W-50` Azure IaC |
| Deployment pipeline | `W-54` |
| Container scanning | `W-59` |
| Real Keycloak realm and theme | `W-10` |
| API gateway Dockerfile | `D-11` — deferred |
| Changes to `compose.yml` or dev Dockerfiles | Not needed — local dev continues with dev images |

## 3. What gets built

```
infra/docker/
├── backend.Dockerfile          NEW — 3-stage: deps → build → JRE-alpine runtime (non-root)
├── backend-entrypoint.sh       NEW — selects app.jar or worker.jar from INFINEVO_ROLE
├── frontend.Dockerfile         NEW — 2-stage: node build → unprivileged nginx runtime (port 8080)
├── frontend-entrypoint.sh      NEW — generates env.js from env vars at container start
├── keycloak.Dockerfile         NEW — 2-stage: kc.sh build → optimised runtime (Front Door proxy support)
├── nginx/
│   └── default.conf            NEW — SPA routing, gzip, cache, health endpoint on 8080
│
├── dev.Dockerfile.backend      UNCHANGED — compose.yml continues using it
├── dev.Dockerfile.frontend     UNCHANGED — compose.yml continues using it
├── compose.yml                 UNCHANGED
└── ...                         UNCHANGED
```

**Also changed:**

| File | Change |
|---|---|
| `code/frontend/index.html` | Add `<script src="/env.js"></script>` before the app bundle |
| `code/frontend/public/env.js` | Placeholder fallback for local development |
| `code/frontend/src/shared/api/client.js` | Read `window.__ENV?.API_BASE_URL` with fallback to `import.meta.env.VITE_API_BASE_URL` |
| `code/backend/app/src/main/resources/application.yml` | Add `${DB_URL}` datasource config and `server.shutdown: graceful` |
| `code/backend/worker/src/main/resources/application.yml` | Add `${DB_URL}` datasource config and `server.shutdown: graceful` |
| `.github/workflows/ci.yml` | `images` job builds production Dockerfiles, adds Keycloak, drops `(provisional)` label |

**Not touched:** any backend business logic, any `pom.xml`, `compose.yml`, dev Dockerfiles, anything under `legacy/`.

---

### Backend Dockerfile — three stages

```
┌─────────────────────────────────────────────────────┐
│ Stage 1: deps        maven:3.9-eclipse-temurin-21   │
│   Copy POMs only → mvn dependency:go-offline        │
│   (cached layer — code changes don't re-download)   │
├─────────────────────────────────────────────────────┤
│ Stage 2: build       same base                      │
│   Copy source → mvn -B package -DskipTests          │
│   Produces: app.jar + worker.jar                    │
├─────────────────────────────────────────────────────┤
│ Stage 3: runtime     eclipse-temurin:21-jre-alpine  │
│   Non-root user (infinevo, uid 1000)                │
│   Copy both jars + entrypoint.sh                    │
│   EXPOSE 8080                                       │
│   ENTRYPOINT: selects jar by INFINEVO_ROLE env var  │
└─────────────────────────────────────────────────────┘
```

**Two jars, one image (Deviation from `04` §3).**
`04` §3 originally assumed a single Spring Boot fat jar toggled between `web` and `worker`
roles via Spring profiles (`spring.profiles.active=web` vs `=worker`). In the actual
codebase, `code/backend/app` (packaging `InfinevoApplication`) and `code/backend/worker`
(packaging `InfinevoWorkerApplication`) are separate Maven packaging modules that each
produce a fat jar, both depending on the identical domain libraries (`core`, `hrms`,
`payroll`, `shared`).
Both jars are copied into the same runtime image, and `backend-entrypoint.sh` reads
`INFINEVO_ROLE` (default `app`) to run the appropriate jar.

*Consequence for W-52*: Schedulers, queue consumers, and batch processing are physically
isolated to `worker.jar`. The `app` role serves the web API and never executes batch jobs.
*Founder confirmation*: [confirmed on #69, 2026-09-15](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/69#issuecomment-5684855988).

**Runtime settings:**
- JVM flags: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` — heap scales with
  container memory limit, no fixed `-Xmx`
- Graceful shutdown: `server.shutdown: graceful` configured in `application.yml`
- Base datasource: `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` without default secret values
- No secrets in the image. All config from environment variables
- Health: `/actuator/health` (already configured in `application.yml`)

---

### Frontend Dockerfile — two stages (Unprivileged Nginx on 8080)

```
┌─────────────────────────────────────────────────────────┐
│ Stage 1: build       node:24-alpine                     │
│   npm ci → npm run build                                │
│   Produces: dist/                                       │
├─────────────────────────────────────────────────────────┤
│ Stage 2: runtime     nginxinc/nginx-unprivileged:alpine │
│   Non-root user (nginx, uid 101)                        │
│   Copy dist/ → /usr/share/nginx/html (chown 101:101)    │
│   Copy default.conf (port 8080, SPA routing, gzip)      │
│   Copy entrypoint.sh (generates env.js at start)        │
│   EXPOSE 8080                                           │
└─────────────────────────────────────────────────────────┘
```

**Non-root execution.** Standard `nginx:alpine` requires root to bind port 80. Switching to
`nginxinc/nginx-unprivileged:alpine` on port 8080 allows the entire container process to run
as an unprivileged user (`uid 101`). `/usr/share/nginx/html` is chowned to `101:101`, enabling
`frontend-entrypoint.sh` to generate `env.js` without root privileges.

**Runtime environment injection.** `frontend-entrypoint.sh` writes
`/usr/share/nginx/html/env.js` from environment variables before starting nginx.
`index.html` loads this script. The app reads `window.__ENV` first, falling back to
`import.meta.env` for local Vite dev. Result: one image, every environment.

**Nginx config (`default.conf`):**
- Listens on port 8080
- `gzip on` for JS, CSS, HTML, JSON
- `try_files $uri $uri/ /index.html` for SPA routing
- Hashed assets (`/assets/*`): `Cache-Control: public, max-age=31536000, immutable`
- `index.html` and `env.js`: `Cache-Control: no-cache` (so env changes take effect)
- `location /health`: returns 200 for Container Apps health probes

---

### Keycloak Dockerfile — two stages (Optimised Production Build)

```
┌─────────────────────────────────────────────────────┐
│ Stage 1: build       quay.io/keycloak/keycloak:25.0 │
│   Copy realm export                                 │
│   RUN /opt/keycloak/bin/kc.sh build                 │
│   (pre-compiles providers, DB driver and theme)     │
├─────────────────────────────────────────────────────┤
│ Stage 2: runtime     same base                      │
│   Copy built artifacts from stage 1                 │
│   KC_HTTP_ENABLED=true, KC_PROXY_HEADERS=xforwarded │
│   EXPOSE 8080                                       │
│   ENTRYPOINT: kc.sh start --optimized --import-realm│
└─────────────────────────────────────────────────────┘
```

**Production proxy & TLS configuration.**
Front Door terminates TLS (`05-azure-architecture.md:49,79`). Running `start --optimized`
in production mode requires:
- `KC_HTTP_ENABLED=true` to permit HTTP ingress between Front Door and the container
- `KC_PROXY_HEADERS=xforwarded` so Keycloak trusts `X-Forwarded-*` headers from Front Door
- Required runtime environment variables:
  - `KC_DB_URL`: JDBC URL (e.g. `jdbc:postgresql://<host>:5432/<db>`)
  - `KC_DB_USERNAME`: Database user
  - `KC_DB_PASSWORD`: Database password
  - `KEYCLOAK_ADMIN`: Initial admin username
  - `KEYCLOAK_ADMIN_PASSWORD`: Initial admin password
  - `KC_HOSTNAME`: Public hostname (e.g. `auth.infinevo.com` or `localhost`)

No secrets in the image layer. `W-10` replaces the dev realm export with the production export.

---

## 4. Proving it

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Remove `INFINEVO_ROLE` env var from a backend container | Container starts in `app` role (the default), `/actuator/health` returns 200 |
| 2 | Set `INFINEVO_ROLE=worker` | Container starts the worker jar on port 8082, `/actuator/health` returns 200 |
| 3 | Set `INFINEVO_ROLE=invalid` | Container exits with error, not a silent default |
| 4 | Run `whoami` inside the backend container (via `--entrypoint`) | Returns `infinevo` (uid 1000), not `root` |
| 5 | Run `whoami` inside the frontend container (via `--entrypoint`) | Returns non-root `nginx` / uid 101 |
| 6 | Start frontend with `API_BASE_URL=http://test:9999` | `env.js` contains `http://test:9999` |
| 7 | Start frontend without any env vars | `env.js` written with empty values, nginx starts |
| 8 | Start Keycloak with database env and Front Door proxy env | Reaches `/health/ready` on management port 9000 successfully |
| 9 | `docker history --no-trunc` on all three images | No literal password or secret value in any layer |
| 10 | On a throwaway branch add `RUN echo password='hunter2' > /x` to `frontend.Dockerfile` | §5 step 8 exits 1 naming the image; the Keycloak base image's `/etc/passwd` layer must **not** trip it |

---

## 5. Verification

Exact commands the **verifier** can run on a clean checkout, with expected output.
All assertions must exit non-zero on failure so CI can gate on them.

```bash
#!/usr/bin/env bash
set -euo pipefail

# ── 0. Shared throwaway Postgres ─────────────────────────────────────────────
# Both backend roles and Keycloak need a database. A temporary Postgres instance
# satisfies that without requiring the developer to run compose.
VERIFY_NET=w49-verify
docker network create "$VERIFY_NET" 2>/dev/null || true
docker run -d --name w49-pg --network "$VERIFY_NET" \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=verify_pw \
  -e POSTGRES_DB=infinevo \
  postgres:16-alpine
# Wait for Postgres to accept connections.
#
# pg_isready returns true DURING the postgres:16-alpine init bootstrap, on the socket
# the entrypoint uses before it restarts the server for real. The CREATE ROLE below then
# died with "the database system is shutting down" and `set -e` aborted the whole script
# at exit 2, so none of steps 1-10 ran on a clean checkout (verify F-1, 2 of 2 runs).
# Wait for the init process to report complete FIRST, then for readiness.
for i in $(seq 1 60); do
  docker logs w49-pg 2>&1 | grep -q "PostgreSQL init process complete" && break
  sleep 1
done
for i in $(seq 1 60); do
  docker exec w49-pg pg_isready -U postgres -d infinevo >/dev/null 2>&1 && break
  sleep 1
done
docker exec w49-pg pg_isready -U postgres -d infinevo >/dev/null 2>&1 \
  || { echo "FAIL: postgres never became ready"; docker logs w49-pg; exit 1; }

# Create non-owner application role (app_user) per 02-data-model.md §9:
# "The application never connects as an owner."
docker exec w49-pg psql -U postgres -d infinevo -c \
  "CREATE ROLE app_user LOGIN PASSWORD 'verify_app_pw'; GRANT CONNECT ON DATABASE infinevo TO app_user; GRANT USAGE ON SCHEMA public TO app_user;"

# Create dedicated keycloak database for Keycloak verification
docker exec w49-pg psql -U postgres -c "CREATE DATABASE keycloak;" 2>/dev/null || true

# ── 1. Build all three production images ─────────────────────────────────────
docker build -f infra/docker/backend.Dockerfile  -t infinevo-backend:test  .
docker build -f infra/docker/frontend.Dockerfile -t infinevo-frontend:test .
docker build -f infra/docker/keycloak.Dockerfile -t infinevo-keycloak:test .

# ── 2. Non-root verification (--entrypoint overrides the entrypoint script) ──
BE_USER=$(docker run --rm --entrypoint whoami infinevo-backend:test)
[ "$BE_USER" = "infinevo" ] || { echo "FAIL: backend runs as $BE_USER"; exit 1; }
FE_USER=$(docker run --rm --entrypoint whoami infinevo-frontend:test)
[ "$FE_USER" = "nginx" ] || { echo "FAIL: frontend runs as $FE_USER"; exit 1; }
echo "PASS: backend=$BE_USER, frontend=$FE_USER"

# ── 3. Backend: app role starts and is healthy ───────────────────────────────
# Connects as non-owner app_user (never as owner, per 02-data-model.md §9).
# Runs without SPRING_PROFILES_ACTIVE, verifying that base application.yml starts
# cleanly from environment variables alone.
docker run -d --name w49-app --network "$VERIFY_NET" \
  -e DB_URL=jdbc:postgresql://w49-pg:5432/infinevo \
  -e DB_USERNAME=app_user -e DB_PASSWORD=verify_app_pw \
  -p 18080:8080 infinevo-backend:test
for i in $(seq 1 30); do
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:18080/actuator/health 2>/dev/null) && [ "$HTTP" = "200" ] && break
  sleep 2
done
[ "$HTTP" = "200" ] || { echo "FAIL: app role health=$HTTP"; docker logs w49-app; exit 1; }
echo "PASS: app role healthy"
docker rm -f w49-app

# ── 4. Backend: worker role starts and is healthy ────────────────────────────
# Connects as non-owner app_user (never as owner, per 02-data-model.md §9).
# Runs without SPRING_PROFILES_ACTIVE, verifying that base application.yml starts
# cleanly from environment variables alone.
docker run -d --name w49-worker --network "$VERIFY_NET" \
  -e INFINEVO_ROLE=worker \
  -e DB_URL=jdbc:postgresql://w49-pg:5432/infinevo \
  -e DB_USERNAME=app_user -e DB_PASSWORD=verify_app_pw \
  -p 18082:8082 infinevo-backend:test
for i in $(seq 1 30); do
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:18082/actuator/health 2>/dev/null) && [ "$HTTP" = "200" ] && break
  sleep 2
done
[ "$HTTP" = "200" ] || { echo "FAIL: worker role health=$HTTP"; docker logs w49-worker; exit 1; }
echo "PASS: worker role healthy"
docker rm -f w49-worker

# ── 5. Backend: invalid role exits 1 ────────────────────────────────────────
if docker run --rm -e INFINEVO_ROLE=invalid infinevo-backend:test 2>/dev/null; then
  echo "FAIL: invalid role did not exit 1"; exit 1
fi
echo "PASS: invalid role rejected"

# ── 6. Frontend: env.js injection and /health on 8080 ────────────────────────
docker run -d --name w49-fe -e API_BASE_URL=http://test-api:8080 -p 18888:8080 infinevo-frontend:test
sleep 2
ENV_JS=$(curl -s http://localhost:18888/env.js)
echo "$ENV_JS" | grep -q 'http://test-api:8080' || { echo "FAIL: env.js missing API_BASE_URL"; exit 1; }
HTTP=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:18888/health)
[ "$HTTP" = "200" ] || { echo "FAIL: frontend /health=$HTTP"; exit 1; }
echo "PASS: frontend env.js and /health"
docker rm -f w49-fe

# ── 7. Keycloak: start --optimized and reach /health/ready on management port 9000 ──
docker run -d --name w49-kc --network "$VERIFY_NET" \
  -e KC_DB_URL=jdbc:postgresql://w49-pg:5432/keycloak \
  -e KC_DB_USERNAME=postgres -e KC_DB_PASSWORD=verify_pw \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=verify_admin_pw \
  -e KC_HOSTNAME=localhost \
  -p 18081:8080 -p 19000:9000 infinevo-keycloak:test
for i in $(seq 1 60); do
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:19000/health/ready 2>/dev/null) && [ "$HTTP" = "200" ] && break
  sleep 2
done
[ "$HTTP" = "200" ] || { echo "FAIL: keycloak /health/ready=$HTTP"; docker logs w49-kc; exit 1; }
echo "PASS: keycloak /health/ready on port 9000"
docker rm -f w49-kc

# ── 8. No literal secret values in any image layer ───────────────────────────
# The grep checks for literal password VALUES, not for environment variable NAMES
# like KEYCLOAK_ADMIN_PASSWORD (which are expected). It looks for strings that
# would indicate a hardcoded credential rather than a placeholder.
for img in infinevo-backend:test infinevo-frontend:test infinevo-keycloak:test; do
  # ── 8a. Build INSTRUCTIONS ──────────────────────────────────────────────────
  LAYERS=$(docker history "$img" --no-trunc --format '{{.CreatedBy}}')
  # Check for actual secret values, not env-var names. Env-var names like
  # KC_DB_PASSWORD are expected; literal values like 'my_password' are not.
  # Match a password VALUE assignment (password='x', PASSWORD "x", CREATE ROLE ... PASSWORD 'x'),
  # not the word "passwd": the Keycloak base image writes /etc/passwd in a RUN layer and
  # the previous pattern failed on it (review F-6).
  if echo "$LAYERS" | grep -vE '(\$\{|ENV |ARG )'        | grep -iE "passw(or)?d[[:space:]]*[=:][[:space:]]*['\"][^'\"]+['\"]|PASSWORD[[:space:]]+['\"][^'\"]+['\"]" >/dev/null 2>&1; then
    echo "FAIL: literal secret value found in $img build instructions"; exit 1
  fi

  # ── 8b. File CONTENTS ───────────────────────────────────────────────────────
  # `docker history` shows the instructions that built each layer and never what a COPY
  # brought IN. That is how infra/docker/keycloak/dev-realm.json — three accounts with
  # the literal password local_dev_pw — reached the production Keycloak image and still
  # scanned clean through every run of 8a (review F-1, F-2). 8a alone is a green-forever
  # check for any credential that arrives as a file.
  HITS=$(docker run --rm --entrypoint sh "$img" -c \
    'grep -rIl -E "local_dev_pw|BEGIN [A-Z ]*PRIVATE KEY" \
       /app /opt/keycloak/data /usr/share/nginx/html 2>/dev/null' || true)
  if [ -n "$HITS" ]; then
    echo "FAIL: credential found in $img file contents:"; echo "$HITS"; exit 1
  fi

  # ── 8c. No realm ships in the production Keycloak image ─────────────────────
  # W-10 supplies the production realm. Anything sitting in the import directory is
  # created on first boot against whatever database the image is pointed at.
  if docker run --rm --entrypoint sh "$img" \
       -c 'ls -A /opt/keycloak/data/import/ 2>/dev/null | grep -q .'; then
    echo "FAIL: a realm is baked into $img"; exit 1
  fi

  echo "PASS: no secrets in $img (instructions, file contents, import dir)"
done

# ── 9. Image sizes — programmatic threshold comparison ───────────────────────
# Measure is `docker image inspect .Size` (content size). `docker images` prints a
# larger virtual size on this Docker; do not compare the two. Measured 2026-09-15 on
# 263e36c: backend 154 MB, frontend 24 MB, keycloak 225 MB.
#
# This function was briefly replaced during implementation with one that read the
# `docker images` columns positionally (review F-3, F-4). It was reverted. `sed 's/MB//'`
# left a GB suffix intact, so `1.2GB` became `1` and an oversized image reported
# "1 MB, PASS" — blind in exactly the failure mode this gate exists to catch — and the
# positional read assumed a column layout that differs between storage drivers. Bytes
# from `docker image inspect` need no unit parsing, and match done-when item 11 as the
# founder approved it.
check_size() {
  local img="$1" max_mb="$2"
  local size_bytes
  size_bytes=$(docker image inspect "$img" --format '{{.Size}}')
  local size_mb=$((size_bytes / 1048576))
  if [ "$size_mb" -gt "$max_mb" ]; then
    echo "FAIL: $img is ${size_mb} MB, threshold is ${max_mb} MB"; exit 1
  fi
  echo "PASS: $img = ${size_mb} MB (< ${max_mb} MB)"
}
check_size infinevo-backend:test  200
check_size infinevo-frontend:test  40
check_size infinevo-keycloak:test 300

# ── 10. Backend and frontend source still build ──────────────────────────────
# Docker must be running: without it the integration tests are skipped silently (W-04).
(cd code/backend && ./mvnw -B clean verify)
(cd code/frontend && npm ci && npm run lint && npm run build)

# ── Cleanup ──────────────────────────────────────────────────────────────────
docker rm -f w49-pg 2>/dev/null || true
docker network rm "$VERIFY_NET" 2>/dev/null || true
echo "All checks passed."
```

| Check | Expected | Result |
|---|---|---|
> The Result column was filled in from a run that predates the fixes for review F-1 to
> F-13, and recorded "PASS on all three" for the secret check — a result step 8 could not
> support, since it never read file contents. It is blank again, as approved, and
> `/verify W-49` records the real results against the corrected images and checks.

| Three images build | Exit 0 | |
| Backend runs as non-root (`--entrypoint whoami`) | `infinevo` (uid 1000) | |
| Frontend runs as non-root (`--entrypoint whoami`) | `nginx` (uid 101) | |
| App role starts without profile, connects as `app_user`, `/actuator/health` returns 200 | 200 | |
| Worker role starts without profile, connects as `app_user`, `/actuator/health` returns 200 | 200 | |
| Invalid role exits non-zero | Exit 1 | |
| Frontend `env.js` generated with env value | Contains `API_BASE_URL` | |
| Frontend `/health` endpoint | 200 (on 8080) | |
| Keycloak `/health/ready` (port 9000) | 200 | |
| No secret values in image layers — instructions, file contents, and an empty Keycloak import dir | PASS on all three | |
| Image sizes within thresholds (`docker image inspect .Size`) | Backend < 200 MB, Frontend < 40 MB, Keycloak < 300 MB | |
| `./mvnw clean verify` | BUILD SUCCESS | |
| `npm run lint && npm run build` | Exit 0 | |

---

## 6. Gap disposition

| Gap | Disposition | Rationale |
|---|---|---|
| `DEBT-004` secrets in configuration | **Fixed forward** | Zero credentials baked into any image layer (`docker history --no-trunc`). Backend `application.yml` reads `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` without default secret values; frontend configuration is injected at container start via `env.js`; Keycloak admin credentials are provided via environment variables. |
| `DEBT-020` in-process permission cache | **Deferred to `W-53`** | Running multiple replicas in Azure Container Apps requires shared permission caching in Redis (`04-runtime-containers.md` §3). W-49 delivers the deployable container image that allows multiple replicas to be deployed, but the Redis cache implementation belongs to `W-53`. |
| `DEBT-021` unlocked `@Scheduled` jobs | **Deferred to `W-52`** | Multiple replicas running uncoordinated scheduled jobs causes duplicate execution. W-49 isolates batch execution to the `worker` container role (`D-02`), preventing `app` replicas from running scheduled jobs. Clustered locking (ShedLock/Redis) and queue abstraction belong to `W-52`. |

---

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Alpine JRE missing a native library a future dependency needs | Low | `eclipse-temurin:21-jre-alpine` is well-tested. Switch to `-jre-jammy` if needed |
| `env.js` exposes config to the browser | Certain — by design | Only non-secret values (URLs, realm name). Secrets never reach the frontend |
| Keycloak 25 `kc.sh build` changes behaviour in a minor release | Low | Pin the image tag to `25.0`. `W-10` will re-evaluate the Keycloak version |
| Two jars in one image increases backend image size | Low | Both jars are ~50 MB. Measured backend image is 154 MB by `docker image inspect` (472 MB virtual in `docker images`), well within Container Apps limits |
| CI `images` job duration | Medium | Three builds sequentially take ~2-3 min. Well within the 15-min timeout |

---

## 8. Rollback

Rollback is `git revert` of the merge commit. No registry entry, no cloud resource,
and no secret was created. The CI job falls back to building dev Dockerfiles (which still
exist unchanged). `compose.yml` is untouched and the local development stack continues
working.

---

## 9. Done when

1. `infra/docker/backend.Dockerfile` exists — multi-stage, JRE-alpine runtime, non-root user `infinevo`
2. `infra/docker/backend-entrypoint.sh` selects `app.jar` or `worker.jar` from `INFINEVO_ROLE`
3. The same backend image runs both roles: `INFINEVO_ROLE=app` starts the web role, `=worker` starts the batch role
4. Backend `application.yml` contains base datasource (`${DB_URL}`) without default secrets, and `server.shutdown: graceful`
5. `infra/docker/frontend.Dockerfile` exists — multi-stage, `nginxinc/nginx-unprivileged:alpine` on port 8080
6. Frontend runs as non-root (user `101`), `frontend-entrypoint.sh` writes `env.js` into writable `/usr/share/nginx/html/`
7. The same frontend image serves any environment — different `API_BASE_URL` produces different `env.js`
8. `infra/docker/keycloak.Dockerfile` exists — `kc.sh build` + `start --optimized`, with `KC_HTTP_ENABLED=true` and `KC_PROXY_HEADERS=xforwarded`
9. No secret appears in any image layer (`docker history --no-trunc`)
10. All three images run as non-root (`infinevo` for backend, `101` for frontend, `keycloak` for Keycloak)
11. Image size thresholds are met, measured by `docker image inspect .Size`: backend < 200 MB, frontend < 40 MB, Keycloak < 300 MB
12. CI `images` job builds all three production Dockerfiles (no push)
13. Dev Dockerfiles and `compose.yml` are untouched — local dev still works
14. `./mvnw clean verify` passes
15. `npm run lint && npm run build` passes

---

## Decisions confirmed by the founder

1. **Two jars + `INFINEVO_ROLE` entrypoint selector** (Deviation from `04` §3):
   The codebase reactor packages `app` and `worker` into separate fat jars (`app.jar` and `worker.jar`). Both jars are included in the backend image, and `INFINEVO_ROLE` launches the requested role. Consequence: batch execution and schedulers are physically isolated to `worker.jar`, while `app.jar` serves the web API. `W-52` (scheduler gating by role) depends on this shape.
   *Status*: [confirmed by the founder on #69, 2026-09-15](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/69#issuecomment-5684855988).

2. **Unprivileged Nginx on port 8080**:
   Standard `nginx:alpine` requires root on port 80. `nginxinc/nginx-unprivileged:alpine` on port 8080 runs as non-root (uid 101) and satisfies non-root compliance across all containers.
   *Status*: [confirmed by the founder on #69, 2026-09-15](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/69#issuecomment-5684855988).

3. **Base Datasource & Graceful Shutdown in `application.yml`**:
   `application.yml` reads `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` without default secrets or `ddl-auto`, allowing the same image to run in any environment. `server.shutdown: graceful` ensures in-flight requests complete before pod termination.
   *Status*: [confirmed by the founder on #69, 2026-09-15](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/69#issuecomment-5684855988).

4. **Optimised Keycloak build with Front Door proxy settings**:
   `start --optimized` runs with `KC_HTTP_ENABLED=true` and `KC_PROXY_HEADERS=xforwarded` for Front Door TLS termination (`05-azure-architecture.md:49,79`).
   *Status*: [confirmed by the founder on #69, 2026-09-15](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/69#issuecomment-5684855988).
