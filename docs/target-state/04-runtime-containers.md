# 04 — Runtime & Containers

> Target state. What runs, and in what. Stops at the image and the local stack.
> Where it runs in Azure is `05`.

---

## 1. Four Dockerfiles, five containers

The backend image runs in two roles, so there are fewer files than containers.

| Dockerfile | Produces | Runs as |
|---|---|---|
| `docker/backend.Dockerfile` | One Spring Boot jar containing Core, HRMS and Payroll | **`app`** and **`worker`** — two containers, one image |
| `docker/frontend.Dockerfile` | The unified React bundle | `web` |
| `docker/keycloak.Dockerfile` | Keycloak with realm and theme pre-built | `keycloak` |
| `docker/gateway.Dockerfile` | API gateway | `gateway` — **deferred** (`D-11`) |

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
| Spring profile | `web` | `worker` |
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
| 2 — build | Compile and package the jar |
| 3 — runtime | Slim JRE 21 base. Copy the jar. Non-root user. Expose the port. Health endpoint declared |

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

---

## 6. Local development stack

One command brings up the whole platform. Nine containers locally against five in
production, because managed services need local stand-ins.

| Container | Stands in for |
|---|---|
| `app`, `worker`, `web`, `keycloak` | themselves |
| `postgres` | Azure Database for PostgreSQL. Four schemas created at start |
| `redis` | Azure Cache for Redis |
| `queue` | Azure Service Bus |
| `blob` | Azure Blob Storage (emulator) |
| `mail` | A mail catcher, so notifications are visible and nothing is sent |

| Rule |
|---|
| Migrations run on start, so the database is always current |
| Seed data creates two tenants with different module sets, so entitlement is exercised locally |
| No real secrets. No connection to any shared environment |

**Seeding two tenants with different modules matters.** It is the only way entitlement bugs
surface during development rather than after a customer buys one module.

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
