# 04 — Runtime & Containers

> Target state. What runs, and in what. Stops at the image and the local stack.
> Where it runs in Azure is `05`.

---

## 1. Four Dockerfiles, five containers

The backend image runs in two roles, so there are fewer files than containers.

> **These are the deployable images. `W-49` wrote them — merged 2026-09-17 (#116).**
> `infra/docker/dev.Dockerfile.backend` and `dev.Dockerfile.frontend` (`W-02`) still
> exist and are unchanged: they run the apps from source with the build toolchain in
> the image and no non-root user, they serve the local stack only, and they must not be
> promoted. CI builds both alongside the production images, so a break in either is
> caught rather than discovered by the next developer to run compose.

| Dockerfile | Produces | Runs as |
|---|---|---|
| `infra/docker/backend.Dockerfile` | **Two** fat jars — `app.jar` and `worker.jar` — in one image, each containing Core, HRMS and Payroll (`D-48`) | **`app`** and **`worker`** — two containers, one image |
| `infra/docker/frontend.Dockerfile` | The unified React bundle | `web` |
| `infra/docker/keycloak.Dockerfile` | Keycloak, pre-built with `kc.sh build` and run `--optimized`. **No realm and no theme are baked in** — `W-10` supplies the production realm, and CI fails the build if any file appears in the import directory | `keycloak` |
| `infra/docker/gateway.Dockerfile` | API gateway | `gateway` — **deferred** (`D-11`) |

---

## 2. Container inventory

| Container | Role | Schemas used | Scales on | Instances (prod) |
|---|---|---|---|---|
| `app` | Every API request | `core`, `hrms`, `payroll`, `reference` | Request concurrency | 2 minimum |
| `worker` | Pay runs, reports, scheduled jobs, imports | same | Queue depth | 1 minimum |
| `web` | Static frontend | none | Request concurrency | 2 minimum |
| `keycloak` | Identity | its own database | rarely | 1–2 |
| `gateway` | Entry, entitlement, rate limits | none | Request concurrency | deferred |

**Managed by Azure, not containerised in production:** Postgres, Redis, the queue, blob
storage. In local development they run as containers so a developer gets everything from one
command.

---

## 3. `app` and `worker` — the same image, twice

| | `app` | `worker` |
|---|---|---|
| Selected by | `INFINEVO_ROLE=app` (the default) | `INFINEVO_ROLE=worker` |
| Jar run | `app.jar` | `worker.jar` |
| HTTP | full API | health endpoint only |
| Queue consumer | no | yes |
| `@Scheduled` jobs | **disabled** | enabled, with cluster locking |
| Typical sizing | smaller, more instances | larger, fewer instances |
| Restart impact | a request fails and retries | a job resumes from the queue |

**Why this beats splitting Payroll into its own service.** All 58 Payroll tables reference
Core. A separate service would make a network call for employee and leave data on every
operation. The pay run's weight is a *batch* problem, not a request-throughput problem, and
a worker solves it directly. (`D-01`, `D-02`)

**Two correctness fixes come free with this shape:**

| Problem today | Fixed by |
|---|---|
| Two `@Scheduled` jobs fire twice if a second instance runs | Jobs only in `worker`, with a lock |
| Permission cache is in-process, so two instances disagree | Redis, shared |

Both currently prevent running more than one replica at all.

---

## 4. Backend Dockerfile shape

Multi-stage, so the runtime image carries no build tooling.

| Stage | Does |
|---|---|
| 1 — dependencies | Resolve Maven dependencies in a cached layer, so code changes do not re-download |
| 2 — build | Compile and package the reactor. Resolve each fat jar to a fixed name, asserting exactly one match per module |
| 3 — runtime | Slim JRE 21 base. Copy **both** jars. Non-root user `infinevo` (uid 1000). Expose 8080 (`app`) and 8082 (`worker`). Health endpoint declared. Entrypoint `exec`s, so `SIGTERM` reaches the JVM and graceful shutdown is real |

| Runtime setting | Value |
|---|---|
| Base | Slim JRE 21 (both modules compile on 21; Payroll targets 17 via the release flag) |
| User | Non-root |
| Memory | Container-aware heap sizing, not a fixed value |
| Config | Environment variables only. **No secrets baked into the image** |
| Health | Separate liveness and readiness endpoints |
| Shutdown | Graceful, so in-flight requests and jobs finish |

**The same image is promoted unchanged** from development to acceptance to production. A
rebuild between environments means you are no longer testing what you ship.

---

## 5. Frontend Dockerfile shape

| Stage | Does |
|---|---|
| 1 — build | Install dependencies, build the production bundle |
| 2 — runtime | Static file server, compression on, cache headers set, single-page-app fallback routing |

**Environment configuration is read at runtime, not baked at build**, so the same image
serves every environment. Today's frontends hard-code an API URL in a global constant,
which is why one build cannot serve two environments.

Implemented by `W-49`: `frontend-entrypoint.sh` writes `/usr/share/nginx/html/env.js`
at container start, `index.html` loads it before the app bundle, and the app reads
`window.__ENV` with `import.meta.env` as the fallback for local Vite development.
Values are escaped before interpolation — an unescaped `"` silently breaks
`window.__ENV`, and an unescaped `</script>` is an injection into every page. Keycloak
keys default to empty rather than to the local realm's names, so a missing variable
fails visibly at `W-10` instead of quietly pointing at a development realm.

---

## 6. Local development stack

One command brings up the whole platform. Nine containers locally against five in
production, because managed services need local stand-ins.

**Built and running — `W-02`.** `docker compose -f infra/docker/compose.yml up -d`,
all nine healthy in about two minutes on a clean machine. Ports, credentials and
troubleshooting are in `infra/docker/README.md`; they are not repeated here.

| Container | Stands in for | Locally |
|---|---|---|
| `app`, `web`, `keycloak` | themselves | |
| `worker` | itself | Serves **health only**, on its own port. Without HTTP the process has no non-daemon thread and exits |
| `postgres` | Azure Database for PostgreSQL | Two databases, as in production: the platform's four application schemas plus `migration` (`D-45`), and Keycloak's own |
| `redis` | Azure Cache for Redis | |
| `queue` | Azure Storage Queue (`D-50`) | **Azurite** — the emulator already serving `blob`, with its queue service switched on. Local and production then run the same API rather than differing by an adapter, which `D-44` accepted only because Service Bus has no faithful emulator. `compose.yml:73` starts Azurite as `azurite-blob` today; enabling the queue service and retiring the RabbitMQ container is `W-52`'s work |
| `blob` | Azure Blob Storage | **Azurite** |
| `mail` | Nothing — a catcher | **Mailpit.** Notifications are visible and nothing can ever be sent |

| Rule | Today |
|---|---|
| The application connects as `app_user` and **is refused DDL** | ✅ enforced, and `smoke.sh` fails if it ever succeeds |
| No real secrets. No connection to any shared environment | ✅ |
| `ddl-auto` set nowhere | ✅ checked |
| Migrations run on start, so the database is always current | ✅ **`W-06` merged, and `W-07`–`W-09` filled it.** Five scripts ship — `core/V001__tenant.sql`, `core/V002__user_tenant.sql` and `reference/V003`–`V005` — and `ShippedMigrationsIT` runs that exact tree through Flyway rather than a fixture |
| Seed data creates two tenants with different module sets | ⏳ **half done, `W-12` owes the rest.** `W-09` made the seed real: `infra/docker/seed/01-tenants.sql` inserts Acme and Globex with fixed UUIDs, idempotently. The **module asymmetry** is still missing — it needs `core.subscription`, which `W-12` creates, so both tenants currently hold the same nothing |

**Seeding two tenants with different modules matters.** It is the only way entitlement
bugs surface during development rather than after a customer buys one module. `W-02`
shipped the loader, `W-09` made it insert two real tenants — but **the part that catches
entitlement bugs is the asymmetry, and that is still missing.** Until `W-12` creates
`core.subscription`, the two tenants are indistinguishable in what they have bought and
an entitlement check has nothing to fail against.

**`app_user` is the one that is load-bearing.** The local stack creates the same three
roles as production (`02` §9) and the application connects as the restricted one. That
refusal is what makes row-level security a real boundary at `W-07` rather than a
convention everyone agrees to respect.

---

## 7. Scaling triggers

| Container | Scale out when | Scale in when | Floor |
|---|---|---|---|
| `app` | Concurrent requests per instance exceeds target, or latency rises | Sustained low traffic | 2, for availability |
| `worker` | Queue depth exceeds threshold | Queue drains | 1 |
| `web` | Concurrent requests | Low traffic | 2 |
| `keycloak` | Rarely. Login bursts only | | 1 prod, 1 elsewhere |

**Development and acceptance scale to zero when idle.** Production does not.

**Scaling will not help until `PLAT-06` is done.** The current bottleneck is missing indexes
and per-row related-data fetching, not server capacity. Adding instances to a slow query
multiplies the database load rather than the throughput.

---

## Related

- Azure resources: `05-azure-architecture.md` · Repository layout: `03-code-structure.md`
- Decisions: `07-decisions.md`
