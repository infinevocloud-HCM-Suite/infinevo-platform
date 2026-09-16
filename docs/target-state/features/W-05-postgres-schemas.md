# W-05 — Postgres & Schemas

| Field | Value |
|---|---|
| **Work item** | `W-05` · issue [#6](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/6) |
| **Kind** | **Data / Infra** — database server, schema isolation, security roles |
| **Stream / track** | Stream A — Foundation · Track P |
| **Wave** | 1 — Foundations |
| **Size / skill** | S · DATA |
| **Owner** | SayInfi |
| **Blocked by** | — (`W-01` merged) |
| **Blocks** | `W-06` Flyway migrations · `W-07` Tenant & RLS foundation · `W-08`–`W-13` feature tables |
| **Capabilities** | `PLAT-11` build & deploy pipeline |
| **Decisions** | `D-08` reference schema · `D-09` no ddl-auto / Flyway only · `D-38` Java 21 |
| **Gaps addressed** | `DEBT-002` ddl-auto |
| **Status** | **Merged 2026-09-16 — #110** |
| **Approved by** | Founder |
| **Approved on** | 2026-09-15 |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

In the legacy codebase, two MySQL databases (`HRMS_Backend` and `Payroll-Bend-SBoot`) ran with Hibernate `spring.jpa.hibernate.ddl-auto=update` without database migration tooling (`DEBT-002`). Applications connected as database superuser/owner, exposing table structures to application-level DDL queries and uncontrolled schema drift.

Today, local development (`infra/docker/postgres/00-bootstrap.sql`, built in `W-02`) creates four schemas (`core`, `hrms`, `payroll`, `reference`) and three roles (`app_user`, `migration_user`, `readonly_user`).

However, four gaps remain to complete the data foundation:
1. **Unconsolidated Bootstrap Scripts:** Bootstrap logic is embedded in local Docker compose initializers rather than a canonical, shared SQL script set usable by Testcontainers (`W-04`) and production deployment (`W-50`).
2. **Keycloak Superuser Access:** Keycloak connects to its PostgreSQL database using superuser privileges instead of a dedicated non-superuser role (`keycloak_user`).
3. **Implicit PUBLIC Privileges:** PostgreSQL's default `PUBLIC` role retains connection privileges on the `infinevo` database.
4. **Missing Automated Privileges Test:** There is no integration test (`DatabasePrivilegesIT`) verifying that `app_user` is refused DDL, `readonly_user` is refused writes, and `migration_user` possesses schema ownership.

> **Superseded by this ticket.** `00-bootstrap.sql` no longer exists — `W-05` replaced it with
> `infra/docker/postgres/00-bootstrap.sh`, which delegates to `infra/postgres/provision.sh`.
> The measurement below is the pre-`W-05` state, kept as the baseline it was taken as.

**Baseline, measured 2026-09-15 on `main` at `infra/docker/postgres/00-bootstrap.sql`:**

| Command / Check | Exit | Output / Result |
|---|---|---|
| Local Docker Postgres bootstrap (`00-bootstrap.sql`) | 0 | 4 schemas (`core`, `hrms`, `payroll`, `reference`), 3 roles (`app_user`, `migration_user`, `readonly_user`) created |
| `app_user` DDL prohibition | 1 | `ERROR: permission denied for schema core` (verified in local Docker container) |
| Shared SQL script location (`infra/postgres/`) | N/A | Missing — currently lives under `infra/docker/postgres/` |
| `keycloak_user` dedicated DB role | N/A | Missing — Keycloak connects as Postgres superuser |
| `DatabasePrivilegesIT` | N/A | Missing |

---

## 2. Scope

**In scope**

- **Canonical Postgres Script Organization:** Move and consolidate database initialization scripts into `infra/postgres/` for shared consumption by Docker Compose (`W-02`), Testcontainers (`W-04`), and Azure Flexible Server (`W-50`).
- **Schema Ownership Model:** Assign `migration_user` as the explicit owner of the four platform schemas (`core`, `hrms`, `payroll`, `reference`).
- **Dedicated Keycloak Security Role:** Create `keycloak_user` owning the `keycloak` database so Keycloak no longer operates as superuser.
- **Revocation of PUBLIC Connection Privileges:** Revoke default `CONNECT` privileges on `infinevo` from `PUBLIC`.
- **Testcontainers Initializer Integration:** Update `PostgresTestContainerInitializer` (`shared` module) to execute the canonical `infra/postgres/` SQL scripts during integration tests.
- **Database Privileges Integration Test:** Add `DatabasePrivilegesIT` in `shared` module to assert that `app_user` is denied DDL, `readonly_user` is denied writes, and `migration_user` can execute DDL.
- **W-50 Handoff Specification:** Record Azure PostgreSQL Flexible Server specifications (§3b) and post-deployment script handoff requirements for `W-50`.

**Out of scope**

- **Azure Infrastructure-as-Code (Bicep/Terraform).** Owned by `W-50`.
- **Flyway runner configuration & migration scripts.** Owned by `W-06` (#7).
- **Tenant entity, RLS policies, and session variables.** Owned by `W-07` (#8).
- **Employee, Payroll, or HRMS table migrations.** Owned by `W-08` through `W-13`.

---

## 3. What Gets Built

### 3a. File Changes

```
infra/postgres/
  ├── provision.sh         # Runs the three scripts in order; the one entry point
  ├── 01-roles.sql         # Creates migration_user, app_user, readonly_user, keycloak_user
  ├── 02-schemas.sql       # Creates core, hrms, payroll, reference owned by migration_user
  ├── 03-grants.sql        # Grants, default privileges, and an in-script security self-check
  └── README.md            # What each script does and the order they run in
```

| File | Change |
|---|---|
| `infra/postgres/provision.sh` | **New.** The single entry point. Runs `01`, `02`, `03` in order, passing each role password as a psql variable. Omits `-h` unless `PGHOST` is set, so it works over the unix socket during container init. |
| `infra/postgres/01-roles.sql` | **New.** Creates `migration_user`, `app_user`, `readonly_user`, and `keycloak_user`. Passwords are set by `ALTER ROLE` outside the `DO` block, where psql interpolates variables. |
| `infra/postgres/02-schemas.sql` | **New.** Creates `core`, `hrms`, `payroll`, `reference` and sets ownership to `migration_user` idempotently. |
| `infra/postgres/03-grants.sql` | **New.** Grants, default privileges, `PUBLIC` connect revocation, and a self-check that raises if any role is superuser or `BYPASSRLS`. |
| `infra/docker/postgres/00-bootstrap.sh` | **Replaces `00-bootstrap.sql`.** Calls `provision.sh`, then creates the isolated `keycloak` database. The `.sql` file built in `W-02` is deleted. |
| `code/backend/shared/src/test/java/com/infinevo/shared/test/PostgresTestContainerInitializer.java` | **Modified.** Executes `01-roles.sql`, `02-schemas.sql` and `03-grants.sql` over JDBC from the `db/provision/` test classpath; creates no role in Java. |
| `code/backend/shared/src/test/java/com/infinevo/shared/test/DatabasePrivilegesIT.java` | **New.** Integration test verifying `app_user`, `readonly_user`, and `migration_user` security boundaries. |

### 3b. Azure PostgreSQL Flexible Server Requirements (W-50 Handoff)

> **Note:** Compute sizes (`B1ms` for dev, `D2ds_v5` for prod) are architectural suggestions; exact SKU selections and IaC deployment are owned by `W-50`.

- **Engine:** PostgreSQL 16 Flexible Server.
- **Database Name:** `infinevo` (Application) and `keycloak` (Identity).
- **Post-Deploy Execution:** `W-50` pipeline runs `infra/postgres/provision.sh` using Key Vault credentials after provisioning. It executes `01-roles.sql`, `02-schemas.sql` and `03-grants.sql` in that order; set `PGHOST` for a TCP connection to the Flexible Server.

---

## 4. Database Roles & Security Boundaries

Three application roles and one identity role enforce separation of duties.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                               DATABASE ROLES                                    │
├───────────────────┬───────────────────────────────┬─────────────────────────────┤
│  migration_user   │           app_user            │        readonly_user        │
│  (Schema Owner)   │     (Application Runtime)     │    (Reporting & Analytics)  │
├───────────────────┼───────────────────────────────┼─────────────────────────────┤
│ • Owns schemas    │ • Used by web/worker apps     │ • Read-only across all      │
│ • Runs Flyway DDL │ • DML only on tenant schemas  │   four schemas              │
│ • Bypasses RLS    │ • Read-only on reference      │ • DENIED DDL and writes     │
│   during DDL      │ • DENIED ALL DDL              │ • RLS Enforced per tenant   │
└───────────────────┴───────────────────────────────┴─────────────────────────────┘
```

### Role Specifications

1. `migration_user` **(Schema Owner & Migration Execution Role)**
   - **Responsibility:** Owns `core`, `hrms`, `payroll`, and `reference` schemas. Executes Flyway DDL migrations in CI/CD (`W-06`).
   - **Rights:** Full DDL (`CREATE`, `ALTER`, `DROP`) and DML privileges.

2. `app_user` **(Application Runtime Role)**
   - **Responsibility:** Used by `app` (web), `worker` (batch), and integration tests.
   - **Rights:** DML (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) on `core`, `hrms`, `payroll`; read-only (`SELECT`) on `reference`. Strictly **DENIED DDL**.

3. `readonly_user` **(Reporting & Replica Role)**
   - **Responsibility:** Read-only access for reporting services and database replicas.
   - **Rights:** `SELECT` only across all four schemas. Strictly **DENIED DDL and DML writes**.

4. `keycloak_user` **(Keycloak Identity Role)**
   - **Responsibility:** Dedicated non-superuser role owning and managing the isolated `keycloak` database.

---

## 5. Permissions Matrix

| Schema | Role | SELECT | INSERT | UPDATE | DELETE | CREATE (DDL) | ALTER (DDL) | DROP (DDL) |
|---|---|---|---|---|---|---|---|---|
| `core`, `hrms`, `payroll` | `migration_user` (Owner) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `core`, `hrms`, `payroll` | `app_user` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `core`, `hrms`, `payroll` | `readonly_user` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `reference` | `migration_user` (Owner) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `reference` | `app_user` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `reference` | `readonly_user` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

---

## 6. Gap Disposition & Future Integrations

### Gap Disposition

| Gap | Disposition |
|---|---|
| `DEBT-002` ddl-auto | **Fixed forward.** `app_user` is refused DDL permanently; schema changes require Flyway (`W-06`). |
| `DEBT-003` no tests | **Fixed forward in part.** `W-04` established test harness; `W-05` adds `DatabasePrivilegesIT`. |
| `DEBT-004` secrets in properties | **Deferred to `W-50`.** Local credentials use placeholders; production credentials managed via Azure Key Vault. |
| `DEBT-018` no indexes | **Discounted.** Legacy payroll debt; new schema indexes enforced starting in `W-07`. |
| `DEBT-021` unlocked schedulers | **Discounted.** Legacy payroll scheduler debt; addressed in worker batch tasks. |

### Integration Roadmap

- `W-06` (Flyway): Attaches Flyway migration runner using `migration_user`.
- `W-07` (Tenant & RLS): Applies Row-Level Security policies to `core`, `hrms`, `payroll` for `app_user`.
- `W-13` (Employee Entity): Migrates employee master tables into `core` schema using `migration_user`.

---

## 7. Proving the Security Boundaries

`DatabasePrivilegesIT` verifies the following privilege assertions against Testcontainers PostgreSQL:

| # | Action / Execution | Expected Result |
|---|---|---|
| 1 | `app_user` executes `CREATE TABLE core.test_ddl (id INT);` | **FAILURE** — `ERROR: permission denied for schema core` |
| 2 | `app_user` executes `INSERT INTO reference.country VALUES ('XX', 'Test');` | **FAILURE** — `ERROR: permission denied for table country` |
| 3 | `readonly_user` executes `INSERT INTO core.tenant VALUES ('t1');` | **FAILURE** — `ERROR: permission denied for table tenant` |
| 4 | `migration_user` executes `CREATE TABLE core.test_migration (id INT); DROP TABLE core.test_migration;` | **SUCCESS** — Table created and dropped cleanly |

> **Note on Local Execution:** `DatabasePrivilegesIT` uses `@EnabledIfDockerAvailable` (`W-04`) and skips silently on local machines without Docker Desktop. CI is authoritative because CI has Docker; a green local run without Docker proves nothing.

---

## 8. Verification

Run by the **verifier** on a clean checkout with Docker active.

```bash
# 1 - Verify clean Maven build and integration test execution
# -Dit.test, not -Dtest: Surefire excludes **/*IT.java and Failsafe owns ITs
cd code/backend && ./mvnw clean verify -Dit.test=DatabasePrivilegesIT

# 2 - Verify local Docker Compose postgres bootstrap
cd ../.. && docker compose -f infra/docker/compose.yml up -d postgres
docker exec -it infinevo-postgres-1 psql -U app_user -d infinevo -c "CREATE TABLE core.should_fail(id int);" || echo "DDL_BLOCKED_SUCCESS"
# Expected output: ERROR: permission denied for schema core / DDL_BLOCKED_SUCCESS
```

| Check | Expected Output | Result |
|---|---|---|
| `DatabasePrivilegesIT` | Tests pass green when Docker available | |
| `app_user` DDL Refusal | `ERROR: permission denied for schema core` | |
| `readonly_user` Write Refusal | `ERROR: permission denied for table ...` | |
| `keycloak_user` DB Isolation | Keycloak owns `keycloak` DB, non-superuser | |

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| SKU sizing specified in design docs treated as rigid requirements | Medium | Documented that `B1ms`/`D2ds_v5` compute sizes in §3b are suggestions; `W-50` owns cost and SKU decisions. |
| Developer runs tests without Docker and assumes privileges are validated | Medium | Documented explicitly in §7 that local test skips without Docker; CI pipeline is authoritative. |

---

## 10. Rollback

If configuration or script changes fail:
1. `git revert` the merge commit.
2. Run `docker compose -f infra/docker/compose.yml down -v` locally to wipe volumes and re-bootstrap.
No production database exists; rollback is non-destructive.

---

## 11. Done When

1. Canonical SQL scripts created under `infra/postgres/`: `01-roles.sql`, `02-schemas.sql`, `03-grants.sql`, run in order by `provision.sh`.
2. `migration_user` owns schemas `core`, `hrms`, `payroll`, `reference`.
3. `app_user` has DML on tenant schemas, read-only on `reference`, and is refused DDL.
4. `readonly_user` has read-only access across all four schemas.
5. `keycloak_user` created owning `keycloak` database (Keycloak off superuser).
6. `PUBLIC` connect privilege revoked on `infinevo` database.
7. `PostgresTestContainerInitializer` updated to use `infra/postgres/` scripts.
8. `DatabasePrivilegesIT` added in `shared` module, passing green in CI.
9. `./mvnw clean verify` passes green across all backend modules.

---

## 12. Decisions Needed Before Implementation

### Q1 — Azure PostgreSQL Flexible Server provisioning → **(a) Defer to W-50**
Record requirements in §3b; `W-05` delivers the canonical SQL scripts that `W-50` runs post-deployment.

### Q2 — Schema ownership → **(a) migration_user owns all four schemas**
`migration_user` owns `core`, `hrms`, `payroll`, `reference` to execute Flyway DDL migrations seamlessly.

### Q3 — Keycloak database access → **(a) Add keycloak_user**
Keycloak connects using dedicated `keycloak_user` owning `keycloak` DB, removing superuser connection requirement.

### Q4 — Testcontainers script source → **(a) Shared infra/postgres/ scripts**
`PostgresTestContainerInitializer` executes `infra/postgres/*.sql` scripts to guarantee identical security boundaries in tests and Docker Compose.
