# Feature: W-06 — Flyway Migrations & Schema Runner Setup

| Field | Value |
|---|---|
| **Work item** | `W-06` · issue [#7](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/7) |
| **Kind** | **Data / Infra** — Flyway runner, script conventions, per-schema ordering, pipeline validation |
| **Stream / track** | Stream A — Foundation · Track P |
| **Wave** | Wave 1 — Foundations |
| **Size / skill** | S · DATA |
| **Owner** | |
| **Blocked by** | `W-05` (merged) |
| **Blocks** | `W-07` Tenant & RLS foundation · `W-08` Tenant binding filter · `W-09` Reference schema & seed · `W-10`..`W-13` |
| **Capabilities** | `PLAT-11` build & deploy pipeline · `PLAT-12` database schema evolution |
| **Decisions** | `D-08` reference schema · `D-09` Postgres with Flyway, ddl-auto disabled permanently |
| **Gaps addressed** | `BUG-004` uncontrolled schema drift · `DEBT-002` ddl-auto |
| **Status** | Draft |
| **Approved by** | *(founder approval is required before implementation — `CONVENTIONS.md` rule 1)* |
| **Approved on** | |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

In the legacy codebase, two MySQL databases (`HRMS_Backend` and `Payroll-Bend-SBoot`) relied on Hibernate `spring.jpa.hibernate.ddl-auto=update` without any migration framework (`BUG-004`, `DEBT-002`). Schema alterations occurred silently at startup, leading to uncontrolled schema drift across environments, lack of version control for DDL, and zero rollback capability.

`W-05` delivered the database foundation by establishing four isolated schemas (`reference`, `core`, `hrms`, `payroll`) owned by `migration_user` and strictly prohibiting `app_user` from executing DDL.

However, database migration execution infrastructure remains missing:
1. **No Migration Runner:** Neither `code/backend/app` nor `code/backend/worker` has a configured Flyway runner to execute versioned DDL scripts on startup or build time.
2. **Empty Migration Module:** The `code/backend/migration` module exists as a skeleton without directory conventions or schema migration location structure.
3. **Undefined Cross-Schema Ordering:** Flyway must process multi-schema migrations in strict dependency order (`reference` → `core` → `hrms` / `payroll`) to satisfy cross-schema foreign keys.
4. **Test Alignment:** `PostgresTestContainerInitializer` needs Flyway migration execution using `migration_user` so integration tests run against current schema migrations.

---

## 2. Scope

**In scope**

- **Flyway Runner Setup:** Configure Flyway in the Spring Boot backend (`code/backend/app`, `code/backend/worker`) and `code/backend/migration` module using `migration_user` credentials.
- **Per-Schema Directory Layout & Conventions:** Establish standardized location conventions under `code/backend/migration/src/main/resources/db/migration/` (`db/migration/reference`, `db/migration/core`, `db/migration/hrms`, `db/migration/payroll`).
- **Strict Migration Ordering:** Configure Flyway execution order to apply `reference` first, followed by `core`, then `hrms` and `payroll`.
- **Integration Test Integration:** Update `PostgresTestContainerInitializer` in `code/backend/shared` to run Flyway migrations after initial database bootstrap.
- **Static CI/CD Enforcement:** Verify and ensure that `spring.jpa.hibernate.ddl-auto` remains completely absent across all configuration files (`application.yml`, `application-local.yml`).

**Out of scope**

- **Tenant entity, RLS policies, and session variables.** Owned by `W-07` (#8).
- **Reference lookups and tax seed data.** Owned by `W-09`.
- **Employee, Payroll, or HRMS feature table migrations.** Owned by `W-08` through `W-13`.

---

## 3. Architecture & Migration Flow

```
[Spring Boot Startup / Testcontainers Init]
                  │
                  ▼
   Connect as `migration_user` (Schema Owner)
                  │
                  ▼
     ┌─────────────────────────┐
     │ 1. reference schema     │ (Tax master & shared lookups)
     └────────────┬────────────┘
                  │
                  ▼
     ┌─────────────────────────┐
     │ 2. core schema          │ (Tenants, identity, audit, approvals)
     └────────────┬────────────┘
                  │
        ┌─────────┴─────────┐
        ▼                   ▼
┌───────────────┐   ┌───────────────┐
│ 3. hrms       │   │ 4. payroll    │ (Domain modules)
└───────────────┘   └───────────────┘
                  │
                  ▼
   Switch Connection / Runtime to `app_user` (DML only, DDL Denied)
```

---

## 4. Backend Changes

| Layer | File | Change |
|---|---|---|
| Dependency / Build | `code/backend/pom.xml` | Manage `flyway-core` and `flyway-database-postgresql` versions. |
| Migration Module | `code/backend/migration/pom.xml` | Configure Flyway plugin / resources packaging. |
| Migration Directory | `code/backend/migration/src/main/resources/db/migration/` | Add `.gitkeep` placeholders for `reference/`, `core/`, `hrms/`, `payroll/`. |
| Application Config | `code/backend/app/src/main/resources/application.yml` | Configure Spring Flyway properties (`locations`, `schemas`, `user`, `password`). |
| Application Config | `code/backend/worker/src/main/resources/application.yml` | Configure Spring Flyway properties matching `app`. |
| Test Harness | `code/backend/shared/src/test/java/com/infinevo/shared/test/PostgresTestContainerInitializer.java` | Execute Flyway migrations using `migration_user` during test setup. |

---

## 5. Database Changes

> Flyway only. Never `ddl-auto`. See `CONVENTIONS.md` rule 4.

| Migration Location | Schema | Target Role | Order |
|---|---|---|---|
| `db/migration/reference` | `reference` | `migration_user` | 1 |
| `db/migration/core` | `core` | `migration_user` | 2 |
| `db/migration/hrms` | `hrms` | `migration_user` | 3 |
| `db/migration/payroll` | `payroll` | `migration_user` | 4 |

- [x] `ddl-auto` absent from all `application.yml` / `application-local.yml` files (`D-09`)
- [x] Flyway executes exclusively under `migration_user` privileges
- [x] Schema history tables (`flyway_schema_history`) created in respective schemas owned by `migration_user`

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `code/backend/shared/src/test/java/com/infinevo/shared/test/FlywayMigrationIT.java` | Verifies Flyway runs cleanly across all 4 schemas without errors. |
| Integration | `code/backend/shared/src/test/java/com/infinevo/shared/test/DatabasePrivilegesIT.java` | Verifies `migration_user` can modify `flyway_schema_history` while `app_user` cannot create DDL. |

---

## 7. Verification

Run by the **verifier** on a clean checkout with Docker active.

```bash
# 1 - Verify clean backend build, spotless linting, and integration tests
cd code/backend && ./mvnw clean verify

# 2 - Run smoke script to assert ddl-auto absence across repository
cd ../.. && bash infra/docker/smoke.sh
```

| Check | Expected Output | Result |
|---|---|---|
| Maven Build | `BUILD SUCCESS` across all backend modules | |
| `ddl-auto` Enforcement | `OK - ddl-auto is set nowhere` in smoke.sh output | |
| Flyway Execution | Flyway applies migrations across `reference`, `core`, `hrms`, `payroll` | |

---

## 8. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Out-of-order execution across schemas causes FK constraint failures | Medium | Enforce explicit schema execution ordering (`reference` → `core` → `hrms`/`payroll`) in Flyway configuration. |
| App attempts Flyway migration as `app_user` instead of `migration_user` | Low | Explicitly configure `spring.flyway.user` and `spring.flyway.password` to use `migration_user`. |

---

## 9. Rollback

If Flyway setup causes build or startup failures:
1. `git revert` the merge commit.
2. Wipe local volumes via `docker compose -f infra/docker/compose.yml down -v`.
No production database exists; rollback is completely non-destructive.

---

## 10. Done When

1. `flyway-core` and `flyway-database-postgresql` dependencies added to root `pom.xml`.
2. Directory structure created under `code/backend/migration/src/main/resources/db/migration/` for all 4 schemas.
3. Flyway runner configured in `app` and `worker` modules connecting via `migration_user`.
4. Execution ordering set to `reference` → `core` → `hrms` / `payroll`.
5. `PostgresTestContainerInitializer` executes Flyway migrations for Testcontainers integration tests.
6. `FlywayMigrationIT` added in `shared` module, passing green in CI.
7. `ddl-auto` confirmed absent from all configuration files (`smoke.sh` and CI static gate pass).
8. `./mvnw clean verify` passes green across all backend modules.
