# W-05 — Postgres & schemas

> Drafted 2026-09-15 in `.claude/outputs/` because `guard-edit` blocks `docs/`.
> On approval it is copied unchanged to `docs/target-state/features/W-05-postgres-schemas.md`
> on the ticket branch, which is the only `docs/` path gate 5 lets a `W-05` pull request carry.

| Field | Value |
|---|---|
| **Work item** | `W-05` · issue [#6](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/6) |
| **Kind** | Data |
| **Stream / track** | Stream B — Data foundation · Track P |
| **Wave** | 1 — Foundations |
| **Size / skill** | S · DATA |
| **Owner** | SayInfi (assignee on #6) |
| **Blocked by** | — (`W-01` merged) |
| **Blocks** | `W-06` Flyway, and through it `W-07` → `W-08`, `W-09` |
| **Capabilities** | — (`08-work-plan.md:46` lists none) |
| **Decisions** | `D-08` reference schema · `D-09` Postgres, Flyway, no `ddl-auto` · `D-10` Container Apps, managed Postgres · `D-18` India region · `D-19` 10 × 100 scale |
| **Gaps addressed** | `DEBT-002` no Flyway · `DEBT-003` no tests · `DEBT-004` secrets in files · `DEBT-018` no indexes · `DEBT-021` unlocked schedulers — each in §6 |
| **Status** | **Draft — not approved** |
| **Approved by** | |
| **Approved on** | |

> Hard rule 1: no code is written until this spec is approved.
>
> The `infra-task` skill names the gaps as `DEBT-001` ddl-auto and `DEBT-002` secrets.
> `GAP_INVENTORY.md:39-42` numbers them `DEBT-002` ddl-auto and `DEBT-004` secrets;
> `DEBT-001` is a dead frontend file. This spec uses the inventory's numbers.

---

## 1. Problem

`09-build-order.md:171` says: *build server, four schemas, three roles; done when
`app_user` cannot run DDL.* `W-02` already delivered the schemas and roles **for one
environment, the laptop**, and `W-04` delivered a second, different copy for the test
container. There is now no single definition of the database that every environment runs.

| Where | What it creates | Evidence |
|---|---|---|
| `infra/docker/postgres/00-bootstrap.sql` | 4 schemas, 3 roles, grants, `keycloak` database | mounted only into the compose container, `compose.yml:31` |
| `PostgresTestContainerInitializer.java:60-78` | `app_user` only, on the **`public`** schema, no `migration_user`, no `readonly_user`, no four schemas | 24 tests run against a database shaped unlike the design |
| Azure | nothing | `infra/azure/` is empty |

Five defects in what exists, all measured below:

1. **Passwords are literals** in `00-bootstrap.sql:40-46`, while its own comment at
   `:34-36` claims they come from environment variables. Reusing the file anywhere but a
   laptop would commit the wrong habit (`DEBT-004`).
2. **Both databases accept connections from any role.** `pg_database.datacl` is empty
   for `infinevo` and `keycloak`, so `PUBLIC` holds `CONNECT`; `readonly_user` can open
   the Keycloak database (probe G).
3. **Keycloak connects as the superuser** — `compose.yml:101-103` gives it
   `postgres` / `local_postgres_pw`. Same wrong habit as an app connecting as owner.
4. **Role attributes are defaults, not declarations.** `NOBYPASSRLS` and `NOSUPERUSER`
   are true only because `CREATE ROLE` defaults them; nothing asserts it, and a later
   `ALTER ROLE` would pass every check we have.
5. **The four schemas are owned by `postgres`**, so `migration_user` has `CREATE` but
   cannot alter or drop what it did not create, and `ALTER DEFAULT PRIVILEGES` without
   `FOR ROLE` binds to whichever admin ran the file — on Azure Flexible Server there is
   no `postgres` superuser, so the file is not portable as written.

**Baseline** — measured 2026-09-15 against `postgres:16-alpine` (server 16.15) from
`down -v` / `up -d postgres`:

| # | Command | Exit | Output |
|---|---|---|---|
| A | `psql -U app_user -d infinevo -c "create table core.w05_probe(id int)"` | 1 | `permission denied for schema core` ✅ |
| B | same, `public.w05_probe` | 1 | `permission denied for schema public` ✅ |
| C | `migration_user` creates + inserts; `app_user` and `readonly_user` count; `readonly_user` inserts | 0/0/0/1 | 1 · 1 · `permission denied for table` ✅ |
| D | `select rolsuper, rolbypassrls … from pg_roles` for the three roles | 0 | all `f` — by default, not by declaration |
| E | `select datname, datacl from pg_database` | 0 | `infinevo` and `keycloak` both `NULL` = `PUBLIC` may connect ❌ |
| F | `select nspname, nspowner from pg_namespace` | 0 | all four owned by `postgres` |
| G | `psql -U readonly_user -d keycloak -c "select 1"` | 0 | `1` — a platform role inside Keycloak's database ❌ |
| H | `grep -rEn '^[^#]*ddl-auto\s*[:=]' code infra` | 1 | no matches ✅ |
| I | `find code/backend -name '*IT.java'` | 0 | none — Failsafe's `**/*IT.java` matches nothing (`pom.xml:135`) |

The done-when already holds locally (A, B). What is missing is the **one definition** that
makes it hold in the test container and in Azure too, and the hardening in E, F, G.

## 2. Scope

**In scope**

- One provisioning script set, `infra/postgres/`, that creates the four schemas, grants
  and default privileges identically wherever it runs. Plain SQL, no `psql` meta-commands,
  so three consumers can execute it: the compose init, the Testcontainers initializer, and
  the post-deploy step `W-50` will run against Azure.
- Role creation separated from grants, with passwords supplied by `psql` variables from
  the environment. No password literal in any file under `infra/postgres/`.
- Hardening: `CONNECT` revoked from `PUBLIC` on both databases and granted explicitly;
  role attributes declared (`NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS`); a
  self-check block that raises if any platform role is superuser or bypasses RLS.
- `infra/docker/` re-pointed at the shared scripts; `00-bootstrap.sql` shrinks to what is
  laptop-only (the `keycloak` database and local role passwords).
- `PostgresTestContainerInitializer` runs the same schema and grant scripts, so integration
  tests see four schemas and three roles — see Q4.
- `DatabasePrivilegesIT` in `shared`: the first `*IT`, proving the privilege matrix under
  Failsafe (baseline I).
- The Azure server requirements `W-50` must meet, recorded in §3b as a checklist.
- `smoke.sh` extended with the two new negative checks (E, G).

**Out of scope**

| Not here | Owner |
|---|---|
| Azure Flexible Server Bicep and its deployment | `W-50` — see **Q1** |
| Managed identity → Postgres role mapping (`pgaadauth_create_principal`) | `W-51` |
| Flyway runner, history table, script conventions | `W-06` |
| Any table, including `core.tenant` and the two-tenant seed | `W-06`, `W-07` |
| Row-level security policies, `FORCE ROW LEVEL SECURITY` | `W-07` |
| Indexes, connection pooling | `W-55` |
| Backups, restore rehearsal | `W-62` |
| The real Keycloak realm | `W-10` |

## 3. What gets built

```
infra/postgres/                          NEW — the one definition of the database
├── README.md                            what each file is, who runs it, in what order
├── 01-roles.sql                         CREATE ROLE ×3 with declared attributes;
│                                        passwords via :'app_pw' :'migration_pw' :'readonly_pw'
│                                        (psql variables — never a literal). Local + UAT-style
│                                        password environments only; Azure prod uses W-51's
│                                        identity mapping and skips this file
├── 02-schemas.sql                       four schemas, idempotent, AUTHORIZATION per Q2
├── 03-grants.sql                        CONNECT revoked from PUBLIC and granted explicitly;
│                                        USAGE/CREATE per role; ALTER DEFAULT PRIVILEGES
│                                        FOR ROLE migration_user; self-check DO block
└── provision.sh                         psql -v ON_ERROR_STOP=1, runs 01→02→03 against
                                         $PGHOST/$PGDATABASE with passwords from env.
                                         Idempotent: second run exits 0, changes nothing

infra/docker/postgres/
└── 00-bootstrap.sh                      REPLACES 00-bootstrap.sql. Creates the keycloak
                                         database and keycloak_user (Q3), then calls
                                         provision.sh with the local passwords from the
                                         container environment
infra/docker/compose.yml                 mounts ../postgres at /provision:ro; postgres service
                                         gets APP_PW/MIGRATION_PW/READONLY_PW/KEYCLOAK_PW env;
                                         keycloak service connects as keycloak_user (Q3)
infra/docker/smoke.sh                    + "PUBLIC cannot connect", + "readonly_user refused
                                         on keycloak db", + role-attribute assertion
infra/README.md                          + row for postgres/

code/backend/shared/pom.xml              testResources: ../../../infra/postgres copied to the
                                         test classpath under db/provision/  (Q4)
code/backend/shared/src/test/java/.../PostgresTestContainerInitializer.java
                                         creates the three roles with test passwords, then
                                         executes 02-schemas.sql and 03-grants.sql verbatim
                                         over JDBC  (Q4)
code/backend/shared/src/test/java/.../DatabasePrivilegesIT.java
                                         NEW — the privilege matrix as a Failsafe test
```

### The privilege matrix `03-grants.sql` encodes, and `DatabasePrivilegesIT` asserts

| Role | `core` `hrms` `payroll` | `reference` | DDL | Connect to `keycloak` db | Superuser / BYPASSRLS |
|---|---|---|---|---|---|
| `app_user` | SELECT INSERT UPDATE DELETE; sequences | SELECT | **refused** | refused | no / no |
| `migration_user` | all, owns what it creates | all | allowed | refused | no / no |
| `readonly_user` | SELECT | SELECT | refused | refused | no / no |
| `PUBLIC` | nothing | nothing | refused | refused | — |

Unchanged from `02-data-model.md` §9 and `05` §5. What changes is that it is **declared
once and asserted three ways**: `smoke.sh` locally, `DatabasePrivilegesIT` in CI, and the
same SQL run by `W-50` after the server exists.

### Constraints on `02-schemas.sql` and `03-grants.sql`

- Plain SQL only. No `\set`, `\gexec`, `\i`. The JDBC consumer would fail on them.
- Idempotent: `IF NOT EXISTS`, `DO` blocks guarded by `pg_roles` / `pg_namespace` lookups.
- Reference roles by name only. Never create them — that is `01-roles.sql`'s job, or
  `W-51`'s on Azure, or the test initializer's.
- `ALTER DEFAULT PRIVILEGES` always `FOR ROLE migration_user`. Never for the running admin.

### 3b. Azure server requirements — recorded here, delivered by `W-50` (per Q1)

| Setting | Value | Why |
|---|---|---|
| Service | Azure Database for PostgreSQL, **Flexible Server** | `05` §3 |
| Version | **16** | matches `postgres:16-alpine` in compose and tests; nothing else is tested |
| Region | Central India | `D-18`; primary and backups in-jurisdiction |
| Databases | `infinevo`, `keycloak` | `05` §3, one server two databases |
| Network | VNet-integrated private access, **no public endpoint** | `05` §4; `W-51` done-when |
| Auth | Entra ID enabled, password auth **off** in prod, on in dev/uat until `W-51` | managed identity, `05` §4 |
| HA | zone-redundant, **prod only** | `05` §5 |
| Backup | PITR, 7 days dev · 35 days prod | `W-62` rehearses the restore |
| Compute | Burstable `B1ms` dev · General Purpose `D2ds_v5` prod | `D-19`: ~1,000 employees. Do not size up |
| Post-deploy | run `infra/postgres/02-schemas.sql` then `03-grants.sql` as the server admin | this ticket's scripts, unchanged |

**Not touched:** every file under `code/backend/{app,worker,core,hrms,payroll,migration}`,
`application*.yml`, `.github/workflows/`, `code/frontend/`, `infra/keycloak/`,
`infra/docker/seed/`, `infra/docker/keycloak/`, the dev Dockerfiles, `legacy/`, `docs/`
except this spec.

## 4. Proving it

On a throwaway branch, never merged. Evidence linked from the pull request.

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Add `GRANT CREATE ON SCHEMA core TO app_user` to `03-grants.sql` | `smoke.sh` fails "app_user is refused DDL"; `DatabasePrivilegesIT` fails; CI `backend` job red |
| 2 | `ALTER ROLE app_user BYPASSRLS` in `01-roles.sql` | the self-check `DO` block in `03-grants.sql` raises, `provision.sh` exits non-zero, the container never becomes healthy |
| 3 | Delete the `reference` block from `02-schemas.sql` | `smoke.sh` "schema reference exists" fails; IT fails on schema count |
| 4 | Remove `REVOKE CONNECT ON DATABASE infinevo FROM PUBLIC` | new smoke check and IT case "PUBLIC cannot connect" fail |
| 5 | Put `\set x 1` into `02-schemas.sql` | `DatabasePrivilegesIT` fails at initializer with a syntax error — proves the plain-SQL constraint is enforced, not just documented |
| 6 | Run `provision.sh` twice against the same server | second run exits 0, `pg_roles` / `pg_namespace` / ACLs identical before and after |
| 7 | Point `KC_DB_USERNAME` back at `postgres` | smoke check "keycloak connects as keycloak_user" fails (Q3) |

## 5. Verification

```bash
# 1. Clean state, database only — no image build needed for this ticket
docker compose -f infra/docker/compose.yml down -v --remove-orphans
docker compose -f infra/docker/compose.yml up -d postgres keycloak
docker compose -f infra/docker/compose.yml ps        # both healthy

# 2. The done-when: app_user cannot run DDL          — MUST FAIL, exit 1
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -U app_user -d infinevo -c "create table core.should_fail(id int);"

# 3. Nobody but the three roles may connect; no platform role reaches keycloak's db
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -U postgres -tAc "select datname, datacl from pg_database where datname in ('infinevo','keycloak');"
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -U readonly_user -d keycloak -c "select 1;"    # MUST FAIL: permission denied for database

# 4. Attributes are declared and true
docker compose -f infra/docker/compose.yml exec -T postgres psql -U postgres -tAc \
  "select rolname, rolsuper, rolbypassrls, rolcreaterole, rolcreatedb from pg_roles where rolname in ('app_user','migration_user','readonly_user','keycloak_user') order by 1;"

# 5. Schemas exist and are owned per Q2
docker compose -f infra/docker/compose.yml exec -T postgres psql -U postgres -d infinevo -tAc \
  "select nspname, pg_get_userbyid(nspowner) from pg_namespace where nspname in ('core','hrms','payroll','reference') order by 1;"

# 6. Idempotent
docker compose -f infra/docker/compose.yml exec -T -e PGPASSWORD=local_postgres_pw postgres \
  sh -c 'APP_PW=local_app_pw MIGRATION_PW=local_migration_pw READONLY_PW=local_readonly_pw /provision/provision.sh'
echo "exit=$?"                                        # 0

# 7. The whole smoke suite
infra/docker/smoke.sh

# 8. The first Failsafe test actually runs (Docker required; skipped silently without it)
cd code/backend && ./mvnw -B -pl shared verify
ls shared/target/failsafe-reports/*DatabasePrivilegesIT*.xml

# 9. No secret value, no ddl-auto
grep -rEn "PASSWORD\s+'" infra/postgres/ ; echo "expect: no matches"
grep -rEn '^[^#]*ddl-auto[[:space:]]*[:=]' code infra --include='*.yml' --include='*.yaml' --include='*.properties' ; echo "expect: no matches"
```

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | `postgres` and `keycloak` healthy from `down -v` | both `healthy`, keycloak as `keycloak_user` | |
| 2 | `app_user` DDL | `permission denied for schema core`, exit 1 | |
| 3 | `datacl` on both databases | non-null, no `=c/` entry for PUBLIC; readonly refused on `keycloak` | |
| 4 | Role attributes | every column `f` for all four roles | |
| 5 | Schema owners | per Q2 | |
| 6 | `provision.sh` second run | exit 0 | |
| 7 | `smoke.sh` | all pass, count ≥ 26 (23 today + 3 new) | |
| 8 | `DatabasePrivilegesIT` | present in `failsafe-reports`, `Tests run` ≥ 8, `Failures: 0` | |
| 9 | Password literals in `infra/postgres/`; `ddl-auto` anywhere | no matches, no matches | |

## 6. Gap disposition

| Gap | Disposition |
|---|---|
| `DEBT-002` no Flyway, `ddl-auto` everywhere | **Partly fixed, structurally.** Even if someone sets `ddl-auto`, the role the application runs as cannot execute it (checks 2, 8). The runner itself is `W-06` |
| `DEBT-003` no tests | **Fixed for this ticket.** `DatabasePrivilegesIT` is the first Failsafe test in the repository; baseline I becomes non-zero |
| `DEBT-004` secrets in files | **Fixed for database provisioning.** No password literal under `infra/postgres/`; laptop values stay in `compose.yml` where `W-02` put them, and are not secrets. Managed identity, which removes the password entirely, is `W-51` |
| `DEBT-018` no indexes | **Discounted.** No table exists to index. `W-55` |
| `DEBT-021` unlocked schedulers | **Discounted.** Nothing here runs a scheduler. `W-52` |

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A fourth copy of the definition appears — someone hand-edits the initializer instead of the script | Medium | The initializer executes the files from `infra/postgres/` verbatim; there is nothing schema-shaped left in Java to edit |
| Testcontainers `ScriptUtils` mis-splits `DO $$ … $$` blocks | Medium | Do not use `withInitScript`. Execute each file as one JDBC statement; pg JDBC accepts multi-statement text. Break 5 proves the failure mode is loud |
| Azure Flexible Server admin is not a superuser, so something in `03-grants.sql` fails there | Medium | Nothing in 02/03 needs superuser: no extensions, no `ALTER SYSTEM`, no ownership of `public`. `W-50` runs the files as its first post-deploy step and reports |
| `migration_user` owning the schemas lets Flyway drop a schema | Low | It could already drop every table it owns. Forward-only migrations reviewed like code (`03` §6) is the control, not ownership |
| Renaming `00-bootstrap.sql` to `.sh` breaks a developer's cached volume | Certain, once | `down -v` is already the documented reset; README says so |

## 8. Rollback

Nothing is deployed. `git revert` the merge; every developer runs `down -v` and `up -d`.

What a revert does **not** remove: nothing. No cloud resource, image, registry entry or
secret is created — if Q1 is (a). Roles created inside a Testcontainer die with it. If Q1
is (b), a compiled Bicep file is just a file. If Q1 is (c), a Flexible Server exists in a
subscription and must be deleted by hand, which is one reason (c) is not recommended.

## 9. Done when

1. `infra/postgres/` holds `01-roles.sql`, `02-schemas.sql`, `03-grants.sql`,
   `provision.sh`, `README.md`; 02 and 03 contain no `psql` meta-command.
2. `grep -rEn "PASSWORD\s+'" infra/postgres/` returns nothing.
3. `app_user` is refused DDL in every schema including `public` (check 2).
4. `PUBLIC` has no `CONNECT` on `infinevo` or `keycloak`; each platform role connects only
   to `infinevo` (check 3).
5. All platform roles are declared `NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS`, and
   `03-grants.sql` raises if that is ever untrue (check 4, break 2).
6. Keycloak connects as its own role, not as the server admin (Q3, check 1).
7. `provision.sh` is idempotent (check 6).
8. `PostgresTestContainerInitializer` runs the shared scripts; integration tests see four
   schemas and three roles (Q4, check 8).
9. `DatabasePrivilegesIT` runs under Failsafe in CI and encodes the §3 matrix (check 8).
10. `smoke.sh` gains the three negative checks and passes (check 7).
11. Every break in §4 was reproduced on a throwaway branch and linked from the PR.
12. §3b is complete enough that `W-50` needs no further database decision.
13. This spec updated to match what was built; the docs listed below queued for `sync-docs`.

**Docs that will need `sync-docs` after merge:** `04-runtime-containers.md` §6 row
"plain SQL bootstrap"; `infra/README.md` table; `W-04-test-foundation.md:94` ("`W-06`
must switch the initializer"); `active-work.md` constraint row on `app_user`; `02-data-model.md`
§9 and `05` §5 if Q3 adds `keycloak_user`.

---

## Decisions needed before implementation

**1. What does "server provisioning" mean for this ticket?**
The ticket lists it as a feature. `W-02`'s spec sends "the managed Postgres server and its
real roles" here, but `08-work-plan.md:134`, `09-build-order.md:265` and `infra/README.md`
all give Postgres Bicep to `W-50`, the whole Azure track is sequenced after `W-49`, and no
subscription or resource group exists yet.
**(a)** Defer the server to `W-50`. This ticket delivers the database-level definition
`W-50` runs after deploy, plus the §3b requirements checklist so `W-50` makes no database
decision of its own.
**(b)** Also write `infra/azure/modules/postgres.bicep` here, compiled with `az bicep build`
but not deployed. Adds a Bicep toolchain to laptops and CI for a file nothing exercises.
**(c)** Provision a real dev server now. Needs a subscription, a resource group, a VNet and
Entra setup that are `W-50` and `W-51`, and creates cost and a manual-delete rollback.
**Recommend (a).** A Bicep file that has never been deployed is a gate only ever observed
passing. `W-50`'s done-when is "build dev and rebuild it twice", which is the right place to
prove the server. Size S survives only under (a).

**2. Who owns the four schemas?**
**(a)** `migration_user` (`CREATE SCHEMA … AUTHORIZATION migration_user`). Flyway then owns
everything it creates and can alter it; `W-07`'s `ENABLE ROW LEVEL SECURITY` needs table
ownership, which it gets for free.
**(b)** The server admin owns them, `migration_user` has `CREATE` only — today's state.
Portable only if every environment's admin is named the same, which Azure's is not.
**Recommend (a).** It is what "DDL on all schemas" in `02` §9 means in practice, and it is
the only option that runs unchanged on a laptop, in Testcontainers and on Flexible Server.

**3. Does Keycloak get its own database role?**
**(a)** Yes: `keycloak_user`, owner of the `keycloak` database, no rights on `infinevo`.
Keycloak stops connecting as the server superuser. A fourth role, but for the *other*
database — `02` §9's three remain the platform's three.
**(b)** Leave Keycloak on the admin account until `W-10`.
**Recommend (a).** It is four lines, it closes baseline defect 3, and it is the same
principle as the ticket's own done-when applied to the second database.

**4. Does the test initializer switch to the shared scripts now, or in `W-06`?**
`W-04-test-foundation.md:94` says `W-06` switches it "when the four schemas arrive". They
arrive here.
**(a)** Switch it in this ticket. `shared/pom.xml` copies `infra/postgres/*.sql` to the test
classpath; the initializer creates the three roles with test passwords and executes 02 and
03 verbatim. `DatabasePrivilegesIT` then tests the real definition, not a Java paraphrase.
**(b)** Leave it, and `DatabasePrivilegesIT` asserts against a hand-written copy of the
grants inside the initializer, which `W-06` then throws away.
**Recommend (a).** Otherwise this ticket's test proves a copy, and the point of the ticket
is that there is no copy.

---

## Related

Issue [#6](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/6) ·
`02-data-model.md` §1, §9 · `05-azure-architecture.md` §3–§6 · `09-build-order.md` §3 ·
`W-02-local-stack.md` §3, §9 · `W-04-test-foundation.md` §4 · `07-decisions.md`
