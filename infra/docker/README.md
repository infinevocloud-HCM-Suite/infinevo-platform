# Local development stack

One command gives you a working platform. Nine containers.

```bash
docker compose -f infra/docker/compose.yml up -d
```

First run takes a few minutes — it builds the backend and installs frontend
dependencies. After that it is seconds.

| | |
|---|---|
| Frontend | http://localhost:5173 |
| API | http://localhost:8080 · health at `/actuator/health` |
| Worker | http://localhost:8082/actuator/health — health only, by design |
| Login | http://localhost:8081 · admin `admin` / `local_keycloak_pw` |
| **Mail** | http://localhost:8025 — every notification lands here, **nothing is ever sent** |
| Queue UI | http://localhost:15672 · `infinevo` / `local_queue_pw` |
| Postgres | `localhost:5432` · database `infinevo` |

Sign in as `admin.acme`, `admin.globex` or `employee.globex`, password `local_dev_pw`.

---

## Commands

```bash
# start / stop
docker compose -f infra/docker/compose.yml up -d
docker compose -f infra/docker/compose.yml down

# full reset — deletes the database volume, re-runs the bootstrap
docker compose -f infra/docker/compose.yml down -v
docker compose -f infra/docker/compose.yml up -d

# what is running
docker compose -f infra/docker/compose.yml ps

# logs
docker compose -f infra/docker/compose.yml logs -f app

# a psql shell
docker compose -f infra/docker/compose.yml exec postgres psql -U postgres -d infinevo

# prove it all works
infra/docker/smoke.sh
```

**Too heavy?** Nine containers need roughly 2 GB. For backend-only work:

```bash
docker compose -f infra/docker/compose.yml up -d postgres app
```

---

## What each container is

Five run in production. The other four stand in for managed Azure services, so you
develop against the same shapes you deploy to.

| Container | Stands in for | Port |
|---|---|---|
| `app` | itself — web role, serves the API | 8080 |
| `worker` | itself — batch role. Serves **health only** | 8082 |
| `web` | itself — the React frontend | 5173 |
| `keycloak` | itself — login | 8081 |
| `postgres` | Azure Database for PostgreSQL | 5432 |
| `redis` | Azure Cache for Redis | 6379 |
| `queue` | Azure Service Bus (RabbitMQ locally) | 5672 · 15672 |
| `blob` | Azure Blob Storage (Azurite) | 10000 |
| `mail` | nothing — a catcher | 1025 · 8025 |

`postgres` holds two databases, as production does: `infinevo` with the four schemas,
and `keycloak` for the identity server's own tables.

---

## Three things that are deliberate

**`app` connects as `app_user`, never as the owner.** `app_user` is the application's
login role and holds the grants and the RLS policies; `worker_user` is the worker's own
login and inherits them through membership. Both have read/write on `core`, `hrms` and
`payroll`, read on `reference`, and **no DDL at all**. Try it:

```bash
docker compose -f infra/docker/compose.yml exec postgres \
  psql -U app_user -d infinevo -c "create table core.nope(id int);"
# permission denied for schema core
```

That refusal is what makes row-level security a real boundary when `W-07` lands,
rather than a suggestion. If it ever succeeds, something is wrong — say so.

**No `ddl-auto`, anywhere, ever.** The schema is owned by Flyway (`W-06`). Hibernate
never creates or alters a table. If you find `ddl-auto` in a configuration file, it is
a defect, not a shortcut.

**Mail is caught, never sent.** Everything goes to mailpit at
http://localhost:8025. You cannot accidentally email a real person from a laptop.

---

## These are development images

`dev.Dockerfile.backend` and `dev.Dockerfile.frontend` run the apps from source with
hot reload. They carry the whole build toolchain and run as root.

**They are not deployable.** `W-49` writes the real images — multi-stage, slim runtime,
non-root. Do not promote these.

---

## Current limitations

| | |
|---|---|
| **No tables** | `W-06` adds Flyway and the migrations. The four schemas exist and are empty |
| **No seed tenants yet** | The `core.tenant` table is `W-07`. The loader is ready and the content is written out in `seed/01-tenants.sql` — see `seed/README.md` |
| Throwaway Keycloak realm | `W-10` replaces it with the real export, roles and theme |
| Queue and cache not used by code | `W-52` and `W-53` build the abstractions |

---

## When something is wrong

| Symptom | Cause |
|---|---|
| `app` unhealthy, logs show a connection refused | Postgres was not ready. `down` then `up -d` again |
| `app` starts then exits | It needs the `local` profile for its datasource — the compose file sets it |
| Keycloak restarts repeatedly | Its database was not created. Full reset with `down -v` |
| Port already in use | Something else holds the port. Copy `.env.example` to `.env` and override just that one — see below |
| Changes not picked up | The backend hot-reloads on rebuild, not on file save. Restart that service |

### A port is already taken

Common — developers already run a Postgres or a Redis. Only the **host** side changes;
containers always talk to each other on standard ports inside the compose network.

```bash
cp infra/docker/.env.example infra/docker/.env
# uncomment and change the one you need, e.g.
#   REDIS_PORT=6380
```

Find what holds it: `netstat -ano | findstr :6379` on Windows, `lsof -i :6379` on macOS.

Everything here is disposable. `down -v` and start again costs a couple of minutes and
fixes most things.

---

## Proven

From a completely clean state — `down -v` then `up -d` — **all nine containers healthy in
114 seconds**, and `smoke.sh` passes 23 of 23. No manual step.
