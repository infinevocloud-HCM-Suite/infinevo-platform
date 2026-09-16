# Feature: W-02 — Local development stack

| Field | Value |
|---|---|
| **Work item** | `W-02` · issue [#3](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/3) |
| **Stream / track** | Stream A — Foundation · Track P |
| **Wave** | 1 — Foundations |
| **Size / skill** | M · INFRA |
| **Blocked by** | — (`W-01` merged) |
| **Blocks** | Nothing formally. In practice every developer after it |
| **Decisions** | `D-09` Postgres · `D-10` Container Apps · `D-19` 10 tenants × 100 · `D-21` one realm |
| **Gaps addressed** | `DEBT-002` (no Flyway) — see §4 Q1 · `DEBT-020`, `DEBT-021` — discounted, §11 |
| **Status** | **Approved 2026-09-13 — in progress** |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

A developer can clone and build the platform, but cannot **run** it. There is no
database, no login server, no queue, no object store. `code/backend/app` starts and
serves a health endpoint against nothing.

Every ticket from `W-05` onwards needs somewhere to run. Without this, each developer
invents their own local Postgres and Keycloak, and "works on my machine" becomes the
normal state rather than the exception.

**Done means one command on a clean machine.** Not one command plus four README steps.

---

## 2. Scope

**In scope**

- `infra/docker/compose.yml` — nine containers, wired, one command
- Schema bootstrap: the four schemas and three database roles exist at start
- **Seed data: two tenants holding different module sets**
- Mail catcher, so notifications are visible and nothing is ever sent
- A minimal Keycloak realm sufficient to log in, replaced by `W-10`
- `infra/docker/README.md` — start, stop, reset, what each container is for

**Out of scope**

| Not here | Belongs to |
|---|---|
| Dockerfiles for a *deployable* image (multi-stage, slim, non-root) | `W-49` |
| Flyway runner and migration conventions | `W-06` |
| The managed Postgres server and its real roles | `W-05` |
| The real Keycloak realm export and theme | `W-10` |
| Any Azure resource | `W-50` |
| Queue abstraction and job dispatch in code | `W-52` |
| Cache abstraction in code | `W-53` |

> This ticket makes things **run locally**. It does not make them **deployable**. Those
> are `W-49` and `W-54`, and conflating them is how a compose file quietly becomes the
> production build.

---

## 3. What gets built

```
infra/docker/
├── compose.yml                 nine services
├── README.md                   start · stop · reset · what each one is
├── dev.Dockerfile.backend      dev-mode only; W-49 writes the real one
├── dev.Dockerfile.frontend     dev-mode only; W-49 writes the real one
├── postgres/
│   └── 00-bootstrap.sql        four schemas, three roles, grants  (replaced by 00-bootstrap.sh in W-05)
├── keycloak/
│   └── dev-realm.json          minimal realm; W-10 replaces it
└── seed/
    └── 01-tenants.sql          two tenants, different module sets
```

### The nine containers

| Container | Image | Stands in for | Port |
|---|---|---|---|
| `app` | built from `code/backend` | itself, web role | 8080 |
| `worker` | same image, `worker` profile | itself, batch role | — |
| `web` | built from `code/frontend` | itself | 5173 |
| `keycloak` | `quay.io/keycloak/keycloak` | itself | 8081 |
| `postgres` | `postgres:16` | Azure Database for PostgreSQL | 5432 |
| `redis` | `redis:7` | Azure Cache for Redis | 6379 |
| `queue` | see §4 Q2 | Azure Service Bus | — |
| `blob` | `mcr.microsoft.com/azure-storage/azurite` | Azure Blob Storage | 10000 |
| `mail` | `axllent/mailpit` | Nothing — a catcher, so mail is never sent | 8025 UI |

`postgres` carries **two databases**: the platform (four schemas) and Keycloak's own.
That mirrors production, where one server holds both (`05` §3).

### Database roles, created at bootstrap

Mirrors `02-data-model.md` §9, because a local stack that connects as owner teaches the
wrong habit and hides row-level security bugs until `W-07`.

| Role | Used by | Rights |
|---|---|---|
| `app_user` | `app`, `worker` | Read/write `core` `hrms` `payroll`; read `reference` |
| `migration_user` | The migration step only | DDL on all schemas |
| `readonly_user` | Reporting | Read only |

**`app` and `worker` connect as `app_user`, never as owner.** This is the one thing in
this ticket that is load-bearing for correctness later.

### Seed data — two tenants

| Tenant | Modules | Purpose |
|---|---|---|
| `acme-payroll` | **Payroll only** | Every developer sees what a Payroll-only customer sees |
| `globex-full` | **HRMS + Payroll** | The combined experience |

> **Two tenants is not optional.** Seed one tenant holding everything and every developer
> builds against a customer who bought both. Entitlement bugs then surface after a real
> customer buys one module — which is the worst possible time to find them. This is the
> stated trap in `09-build-order.md` §3.

Seed runs **only** in the local stack. It is not a Flyway migration and must never be
reachable from a deployed environment.

---

## 4. Decisions — confirmed by the founder 2026-09-13

### Q1 — How do the schemas get created, given `W-06` does not exist? → **A, plain SQL bootstrap**

`ddl-auto` is banned permanently (`D-09`, `DEBT-002`). Flyway arrives in `W-06`.

| Option | |
|---|---|
| **A. Plain SQL bootstrap now** *(recommended)* | `00-bootstrap.sql` mounted into the Postgres container's init directory. Creates schemas, roles, grants — no tables. `W-06` adds Flyway on top and the file shrinks to roles only |

> **Superseded by `W-05` (#110, merged 2026-09-16).** `00-bootstrap.sql` no longer exists. The init directory now mounts `infra/docker/postgres/00-bootstrap.sh`, which delegates to the canonical `infra/postgres/provision.sh`. The decision recorded above is what `W-02` chose at the time; `W-05` kept the approach and moved the scripts.
| B. Take `W-05` and `W-06` first | Strictly correct order, delays a usable stack by two tickets, and neither is blocked by this one anyway |

A creates **no tables**, so it cannot conflict with Flyway later. Schemas and roles are
infrastructure, not schema evolution.

### Q2 — What stands in for Azure Service Bus? → **A, RabbitMQ**

There is no faithful local emulator with the licence terms we want.

| Option | |
|---|---|
| **A. RabbitMQ** *(recommended)* | Mature, trivial in compose. `W-52` builds a queue abstraction anyway, so the local implementation differs from production by one adapter — which is the point of the abstraction |
| B. Azure Service Bus emulator | Closer to production, but Docker-only with licence terms to read, and heavier |
| C. Postgres-backed queue | Fewest containers, but tests nothing about real queue behaviour |

### Q3 — How real should the Keycloak realm be? → **A, minimal dev realm**

| Option | |
|---|---|
| **A. Minimal dev realm** *(recommended)* | One realm, one client, two users — one per seeded tenant. Enough to log in. `W-10` replaces it wholesale |
| B. Wait for `W-10` | The stack has no login, so the frontend cannot be exercised at all |

### Q4 — Does `app` actually connect to the database in this ticket? → **A, wire the datasource**

Today `code/backend` has no datasource, no JPA, no driver. Adding them is arguably
`W-05`'s work.

| Option | |
|---|---|
| **A. Yes, wire the datasource** *(recommended)* | Add the Postgres driver and a datasource pointing at `app_user`. `app` proves the connection through its health check. Without this the stack starts nine containers that never talk to each other, and "working platform" is not true |
| B. No, containers only | Smaller ticket, but nothing is verified end to end and the done-when is not met |

**A is the difference between a stack that runs and a stack that is proven.** It adds
one dependency and no entities.

---

## 5. Backend changes

Only if **Q4 = A**:

| File | Change |
|---|---|
| `code/backend/app/pom.xml` | Add `spring-boot-starter-data-jpa`, `postgresql` driver |
| `code/backend/worker/pom.xml` | Same |
| `code/backend/app/src/main/resources/application-local.yml` | Datasource for `app_user`, from environment variables. **No `ddl-auto`, no secret values** |
| `code/backend/worker/.../application-local.yml` | Same, `worker` profile |

No controller, no entity, no endpoint.

## 6. Frontend changes

One: `VITE_API_BASE_URL` resolved from the environment so `web` reaches `app` by service
name inside the compose network. No screen, no route.

## 7. Database changes

**No tables, no Flyway scripts.** Schemas, roles and grants only — see Q1.

- [x] No tables created, so the `tenant_id` checklist does not apply
- [x] `ddl-auto` appears in no file, including the new `application-local.yml`
- [x] Seed data is local-only and cannot run in a deployed environment

---

## 8. Tests

An infrastructure ticket's test is that a clean machine works. Automated where possible:

| Type | What |
|---|---|
| Script | `infra/docker/smoke.sh` — waits for health, asserts each container is up, asserts both tenants exist, asserts `app_user` **cannot** run DDL |
| Manual, once | Full clean-machine run, §9 step 1 |

The `app_user` DDL assertion matters: if it succeeds, the roles are wrong and row-level
security will not be a boundary when `W-07` lands.

---

## 9. Verification

```bash
# 1. THE ACCEPTANCE TEST — from a genuinely clean state
docker compose -f infra/docker/compose.yml down -v --remove-orphans
docker compose -f infra/docker/compose.yml up -d

# 2. All nine up
docker compose -f infra/docker/compose.yml ps

# 3. Both roles reachable
curl -fsS http://localhost:8080/actuator/health     # {"status":"UP"}
curl -fsS http://localhost:5173                     # HTML
curl -fsS http://localhost:8081/realms/infinevo     # realm JSON
curl -fsS http://localhost:8025                     # mailpit UI

# 4. Four schemas
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -U postgres -d infinevo -c "\dn"             # core hrms payroll reference

# 5. Two tenants, different module sets
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -U postgres -d infinevo -c "select slug, modules from core.tenant order by slug;"

# 6. app_user cannot run DDL  — MUST FAIL
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -U app_user -d infinevo -c "create table core.should_fail(id int);"

# 7. No ddl-auto anywhere
grep -rn "ddl-auto" code/ infra/ ; echo "expect: no matches"
```

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | `up -d` from `down -v` | All nine healthy, **no manual step** | ✅ **114 seconds**, zero errors |
| 2 | `app` health | `{"status":"UP"}` | ✅ `db: UP`, PostgreSQL |
| 3 | `web` responds | HTML | ✅ Vite dev server |
| 4 | Keycloak realm | JSON, login works | ✅ all three seeded users obtained a token |
| 5 | Four schemas | `core` `hrms` `payroll` `reference` | ✅ plus the `keycloak` database |
| 6 | Tenant query | Two rows, different module sets | ⚠️ **deferred to `W-07`** — see §13 |
| 7 | `app_user` DDL | **PERMISSION DENIED** | ✅ `ERROR: permission denied for schema core`, exit 1, 0 tables created |
| 8 | `ddl-auto` set anywhere | No matches | ✅ none. Check proven against a deliberate violation |
| 9 | `smoke.sh` | All pass | ✅ **23 of 23** |
| 10 | `app` connects as `app_user` | Not as owner | ✅ `pg_stat_activity` shows `app_user` |
| 11 | `worker` stays up | Health endpoint only | ✅ healthy on 8082 |

**Checks 1 and 7 are the definition of done.** One command, and the application cannot
change the schema. Check 6 is deferred — see §13.

---

## 10. Rollback

Nothing is deployed and no real data exists. `git revert` the merge, and
`docker compose down -v` on each developer's machine.

The only lasting effect is `code/backend` gaining a database dependency (Q4). That is
reverted with the merge.

---

## 11. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| One seeded tenant, or two with identical modules | **Medium, and it is the stated trap** | Check 6 asserts the module sets differ |
| `app` connects as owner "just to get it working" | **Medium** | Check 7 fails the build if it can run DDL |
| The dev Dockerfiles drift into being the production build | Medium | Named `dev.Dockerfile.*` and documented as dev-only; `W-49` writes the real ones |
| Nine containers too heavy on a developer laptop | Low | Roughly 2 GB. Document a `--profile minimal` for `postgres` + `app` only |
| Compose works for the author only | Medium | Check 1 must be run by a second person before merge |

**Discounted, with reasons:** `DEBT-020` in-process permission cache and `DEBT-021`
unlocked schedulers both block horizontal scaling, and `redis` and the `worker` role
address them — but the code that uses them is `W-52` and `W-53`. This ticket provides the
containers, nothing more. `DEBT-003` no tests is `W-04`. `DEBT-018` no indexes is `W-55`.

---

## 12. Done when

| # | | |
|---|---|---|
| 1 | One command on a clean machine, no manual step | ✅ 114s |
| 2 | All nine containers healthy | ✅ |
| 3 | `app` serves the API; `worker` serves health only | ✅ 8080 / 8082 |
| 4 | Four schemas and three roles exist | ✅ |
| 5 | Two tenants with different module sets | ⚠️ **deferred, §13** |
| 6 | `app_user` cannot run DDL | ✅ |
| 7 | Login through Keycloak works | ✅ three users, tokens issued |
| 8 | Mail caught, never sent | ✅ mailpit |
| 9 | `ddl-auto` set in no file | ✅ |
| 10 | `README.md` documents start, stop, reset | ✅ |
| 11 | **A second person has run it on their own machine** | ❌ **outstanding** |
| 12 | Spec updated to match what was built | ✅ this section |

---

## 13. What was deferred, and two things found while building

### Deferred — the two-tenant seed

**The tenant table does not exist.** `core.tenant` is created by `W-07`, four tickets
away. This spec asserted `select slug, modules from core.tenant`, which could never
have passed: `W-02` was specified as though the table already existed.

The mechanism ships and works — `infra/docker/seed/seed.sh` runs, is idempotent, and
the two inserts are written out in `01-tenants.sql` ready to be uncommented. Only the
content waits.

> The reason the two tenants matter is unchanged and must not be lost: seed one tenant
> holding everything and entitlement bugs stay invisible until a real customer buys a
> single module. **`W-07` must finish this.**

### Found — the worker exited immediately

`W-01` set `spring.main.web-application-type: none` on the worker. It started, connected
to the database, found no non-daemon thread, and shut down cleanly — so the container
would never stay up.

That also contradicted the design: `03-code-structure.md` §4 says the worker serves a
**health endpoint only**, which an orchestrator needs for its liveness probe. Corrected
here: the worker serves health on 8082 and nothing else.

### Found — host port collisions

Port 6379 was already held on the build machine by an unrelated Redis. Every host port
is now overridable through `infra/docker/.env`, with `.env.example` committed and `.env`
ignored. Container-to-container communication is unaffected.

### Three defects in my own first draft, for the record

| | Fixed |
|---|---|
| The Keycloak realm carried `_comment` fields. Keycloak rejects unknown properties outright, so the import failed and the container exited | Comments removed; the explanation moved to `keycloak/README.md`, since JSON has no comment syntax |
| `spring-boot:run` ran with `-am`, so it executed against the parent POM — *"Unable to find a suitable main class"* | The image installs the reactor at build time; the container runs one module with `-pl` |
| `smoke.sh` grepped for `ddl-auto` including inside comments, so it failed on the comments saying there must never be one | Matches an actual setting on a non-comment line. Proven by introducing a real violation and watching it fail |

---

## Related

Issue [#3](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/3) ·
`04-runtime-containers.md` §6 · `05-azure-architecture.md` §2 ·
`02-data-model.md` §9 · `09-build-order.md` §3 · `07-decisions.md`
