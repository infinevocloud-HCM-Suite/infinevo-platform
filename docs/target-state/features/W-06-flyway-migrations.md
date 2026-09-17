# Feature: W-06 — Flyway Migrations & Schema Runner Setup

| Field | Value |
|---|---|
| **Work item** | `W-06` · issue [#7](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/7) |
| **Kind** | **Data / Infra** — Flyway runner, single global script set, script naming conventions, pipeline validation |
| **Stream / track** | Stream B — Data foundation (`docs/target-state/08-work-plan.md:47`) |
| **Wave** | Wave 2 — Data platform |
| **Size / skill** | S · DATA |
| **Owner** | |
| **Blocked by** | `W-05` (merged) |
| **Blocks (Direct)** | `W-07` Tenant model · `W-09` Reference schema & seed |
| **Blocks (Transitive)** | `W-08` Tenant binding filter · `W-10` Identity · `W-11` Authorization · `W-12` Subscription · `W-13` Employee entity |
| **Capabilities** | `—` *(Infrastructure foundation; delivers no end-user capability directly per `08-work-plan.md:47`)* |
| **Decisions** | `D-08` reference schema · `D-09` Postgres with Flyway, ddl-auto disabled permanently |
| **Gaps addressed** | `BUG-004` uncontrolled schema drift · `DEBT-002` ddl-auto |
| **Status** | Draft (Revised following secondary spec review findings) |
| **Approved by** | *(founder approval is required before implementation — `CONVENTIONS.md` rule 1)* |
| **Approved on** | |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

In the legacy codebase, two MySQL databases (`HRMS_Backend` and `Payroll-Bend-SBoot`) relied on Hibernate `spring.jpa.hibernate.ddl-auto=update` without any migration framework (`BUG-004`, `DEBT-002`), evidenced at [legacy/HRMS_Backend/src/main/resources/application.properties:10](file:///d:/Workspace/infinevo-platform/legacy/HRMS_Backend/src/main/resources/application.properties#L10) and [legacy/Payroll-Bend-SBoot/src/main/resources/application.properties:5](file:///d:/Workspace/infinevo-platform/legacy/Payroll-Bend-SBoot/src/main/resources/application.properties#L5). Schema alterations occurred silently at application boot, leading to uncontrolled schema drift across environments, lack of DDL version control, and zero rollback capability.

`W-05` delivered the database foundation by establishing four isolated schemas (`reference`, `core`, `hrms`, `payroll`) owned by `migration_user`, creating `db/migration/{reference,core,hrms,payroll}/` placeholder directories on `main`, and strictly prohibiting `app_user` from executing DDL.

However, database migration execution infrastructure remains incomplete:
1. **Missing Migration Runner Infrastructure:** Neither `code/backend/app` nor `code/backend/worker` has a configured Flyway runner to execute versioned DDL scripts.
2. **Single Script Set Architectural Violation:** `docs/target-state/02-data-model.md:21` specifies *"One numbered script set covering all four schemas, applied together"*, restated at `docs/target-state/03-code-structure.md:142`. Running four separate Flyway executions with per-schema history tables violates this design.
3. **Runtime Credentials Security Hazard:** `docs/target-state/02-data-model.md:380` limits `migration_user` to the pipeline migration step only. `docs/target-state/05-azure-architecture.md:126` requires migration as a separate pipeline step before revisions take traffic. Base `application.yml` files must NOT contain `migration_user` credentials, or runtime containers would gain DDL rights, destroying the security boundary established in `W-05`.
4. **`ddl-auto` Policy Discrepancy (H-1):** `CONVENTIONS.md:20` rule 4 states `ddl-auto` moves to `validate`, whereas issue #7, `smoke.sh:76-81`, `ci.yml:170-178`, and `check-done.mjs:173-178` enforce that `ddl-auto` must be completely **absent**. `ddl-auto` is set nowhere in `code/`, and rule 4 requires a `sync-docs` amendment.
5. **Lack of Automated Test Assertions:** No integration test asserts that Flyway executes cleanly, creates `core.flyway_schema_history` owned by `migration_user`, or applies migrations under Testcontainers.

---

## 2. Scope

**In scope**

- **Single Global Script Set & Schema Placement (H-2, H-3):** Establish a single global version sequence (`V001__tenant.sql`, `V002__employee.sql`, matching `03-code-structure.md:132` 3-digit format) across all schemas in strict sequential order: `reference` → `core` → `hrms` → `payroll` (`03-code-structure.md:142`). The history table is explicitly designated as `core.flyway_schema_history` in the `core` schema, owned by `migration_user`.
- **Flyway Runner Configuration:** Configure Flyway dependencies in `code/backend/pom.xml` and `code/backend/migration`. Configure Flyway to run using `migration_user` credentials in the local profile (`application-local.yml`) and CI/CD pipeline migration step. Base `application.yml` connects runtime applications as `app_user` with DDL denied.
- **`ddl-auto` Complete Absence (H-1):** Ensure `spring.jpa.hibernate.ddl-auto` is completely absent from all active configuration files across `code/backend/`, satisfying static gates in `smoke.sh:76-81` and `ci.yml:170-178`. Document the required `sync-docs` amendment for `CONVENTIONS.md:20` rule 4.
- **Integration Test Harness Integration:** Update `PostgresTestContainerInitializer` in `code/backend/shared` to run Flyway migrations as `migration_user`. Extend [DatabasePrivilegesIT.java](file:///d:/Workspace/infinevo-platform/code/backend/shared/src/test/java/com/infinevo/shared/test/DatabasePrivilegesIT.java) and add `FlywayMigrationIT.java` to verify `core.flyway_schema_history` creation, ownership, and DDL refusal for `app_user`.
- **Script Immutability & Forward-Only Policy:** Enforce that applied migration scripts are immutable (`03-code-structure.md:141`) and schema changes follow expand/contract forward-only rules (`05-azure-architecture.md:130-135`).
- **Task Decomposition by Module:** Break down implementation into per-module tasks in strict dependency order.

**Out of scope**

- **Tenant entity, RLS policies, and session variables.** Owned by `W-07` (#8).
- **Reference lookups and tax seed data.** Owned by `W-09`.
- **Employee, Payroll, or HRMS feature table migrations.** Owned by `W-08` through `W-13`.

---

## 3. Architecture & Security Boundary Flow

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           PIPELINE / DEPLOYMENT STEP                            │
│                                                                                 │
│   [CI/CD Migration Step / Testcontainers Init / application-local.yml]         │
│                                     │                                           │
│                                     ▼                                           │
│                 Connect as `migration_user` (Schema Owner)                      │
│                                     │                                           │
│                                     ▼                                           │
│       Executes Single Global Script Set in Strict Schema Order:                 │
│         1. reference  -->  2. core  -->  3. hrms  -->  4. payroll               │
│                                     │                                           │
│                                     ▼                                           │
│              Updates single table: core.flyway_schema_history                   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                             APPLICATION RUNTIME                                 │
│                                                                                 │
│   [Spring Boot Container (app / worker) — Base profile: application.yml]        │
│                                     │                                           │
│                                     ▼                                           │
│                 Connect as `app_user` (Runtime Application)                     │
│                 • DML Only (SELECT, INSERT, UPDATE, DELETE)                     │
│                 • Strictly DENIED DDL & DENIED writing flyway_schema_history    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Task Decomposition by Module

To support module-confined development, the work is divided into five sequential module tasks:

### Task 1: Root Build Configuration (`code/backend/pom.xml`)
- Add `flyway-core` and `flyway-database-postgresql` version management under `<dependencyManagement>`.

### Task 2: Migration Module (`code/backend/migration/`)
- Update `code/backend/migration/pom.xml` to include `flyway-core` and `flyway-database-postgresql`.
- Retain existing `src/main/resources/db/migration/{reference,core,hrms,payroll}/` directory structure for organizing single-set migration scripts.

### Task 3: Shared Test Harness (`code/backend/shared/`)
- Update `PostgresTestContainerInitializer.java` to execute Flyway migrations as `migration_user` after running SQL bootstrap scripts.
- Add `FlywayMigrationIT.java` asserting `core.flyway_schema_history` exists, is owned by `migration_user`, and contains applied scripts.
- Extend `DatabasePrivilegesIT.java` asserting `app_user` is refused DDL and DML write access on `core.flyway_schema_history`.

### Task 4: Web Application (`code/backend/app/`)
- Add Flyway spring boot starter to `code/backend/app/pom.xml`.
- Configure `application-local.yml` with `migration_user` credentials, default schema `core`, and local Flyway execution.
- Maintain `application.yml` connecting as `app_user` without embedded DDL owner credentials. `ddl-auto` is completely absent (H-1).

### Task 5: Worker Application (`code/backend/worker/`)
- Add Flyway spring boot starter to `code/backend/worker/pom.xml`.
- Configure `application-local.yml` matching `app`.
- Maintain `application.yml` connecting as `app_user` with `ddl-auto` completely absent (H-1).

---

## 5. Database Changes & Script Conventions

### Script Naming & Versioning Conventions
- **Pattern:** `V<NNN>__<slug>.sql` (e.g., `V001__tenant.sql`, `V002__employee.sql`, matching `03-code-structure.md:132` 3-digit format).
- **Strict Sequential Order (H-2):** One single global version sequence applied in strict schema order: `reference` → `core` → `hrms` → `payroll` (`03-code-structure.md:142`).
- **Single History Table Location (H-3):** Tracked by a single history table: `core.flyway_schema_history` located in the `core` schema, owned by `migration_user`.
- **Immutability & Forward-Only Rule:** Once a script is merged into `main`, it must NEVER be edited (`03-code-structure.md:141`). Schema evolution follows expand/contract forward-only patterns (`05-azure-architecture.md:130-135`).

### TEMPLATE §6 Checklist Restoration

- [ ] `tenant_id` present on every new table — *N/A: W-06 creates no domain tables*
- [ ] Index on `tenant_id` plus lookup columns (`DEBT-018`) — *N/A: W-06 creates no domain tables*
- [ ] Money columns are `BigDecimal` with explicit precision and scale — *N/A: W-06 creates no domain tables*
- [x] Expand / contract sequencing — no destructive step (`05-azure-architecture.md:130-135`)
- [x] `ddl-auto` completely absent across all application profiles (H-1; issue #7, `smoke.sh:76-81`, `ci.yml:170-178`)

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| **Unit** | `code/backend/migration/src/test/java/com/infinevo/migration/FlywayConfigurationTest.java` | Asserts Flyway script naming format and classpath resource resolving. |
| **Integration** | `code/backend/shared/src/test/java/com/infinevo/shared/test/FlywayMigrationIT.java` | **New.** Asserts `core.flyway_schema_history` exists, is owned by `migration_user`, and records applied migrations cleanly against Testcontainers. |
| **Integration** | `code/backend/shared/src/test/java/com/infinevo/shared/test/DatabasePrivilegesIT.java` | **Extended.** Asserts `app_user` is denied DDL/DML on `core.flyway_schema_history` while `migration_user` retains ownership. |

---

## 7. Verification (H-6)

> **Validation Design Note:** Criterion 1 fails on `main` today because `FlywayMigrationIT` does not exist. Criterion 4 fails on `main` today because `core.flyway_schema_history` is absent. Both pass green once W-06 is implemented.

Run by the **verifier** on a clean checkout with Docker active.

### Execution Commands

```bash
# 1 - Verify clean Maven build, Spotless formatting, and new Flyway integration tests
cd code/backend && ./mvnw clean verify -Dtest=FlywayMigrationIT,DatabasePrivilegesIT

# 2 - Start local Postgres container via Docker Compose
cd ../.. && docker compose -f infra/docker/compose.yml up -d postgres

# 3 - Run repository smoke script to verify ddl-auto absence
bash infra/docker/smoke.sh

# 4 - Verify single history table existence and migration_user ownership in container (H-6: container-agnostic Compose exec)
docker compose -f infra/docker/compose.yml exec -T postgres psql -U app_user -d infinevo -c "SELECT count(*) FROM core.flyway_schema_history;"
```

### Verification Results Matrix

| Check | Command / Assertion | Expected Output | Result |
|---|---|---|---|
| **Integration Tests** | `./mvnw clean verify -Dtest=FlywayMigrationIT,DatabasePrivilegesIT` | `BUILD SUCCESS` with green test pass | |
| **Local Docker Bootstrap** | `docker compose -f infra/docker/compose.yml up -d postgres` | Container `postgres` service running healthy | |
| **ddl-auto Absence** | `bash infra/docker/smoke.sh` | `PASS ddl-auto is set nowhere` | |
| **Schema History Table** | `docker compose -f infra/docker/compose.yml exec -T postgres psql -U app_user -d infinevo -c "SELECT count(*)..."` | Returns row count of applied scripts in `core.flyway_schema_history` | |
| **app_user DDL Refusal** | `docker compose -f infra/docker/compose.yml exec -T postgres psql -U app_user -d infinevo -c "DROP TABLE core.flyway_schema_history;"` | `ERROR: permission denied for table flyway_schema_history` | |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Runtime containers assigned `migration_user` credentials in base configuration | Medium | Restrict `migration_user` credentials strictly to local development profile (`application-local.yml`) and pipeline migration step. Base `application.yml` runs as `app_user`. |
| Out-of-order migration execution across schemas | Low | Enforce strict sequential schema ordering (`reference` → `core` → `hrms` → `payroll`) and 3-digit global version naming (`V001__...`). |

---

## 9. Rollback & Forward Migration Policy

1. **W-06 Feature Rollback:** If W-06 configuration changes break build or startup before production deployment, `git revert` the merge commit and wipe local test volumes via `docker compose -f infra/docker/compose.yml down -v`.
2. **Production Migration Policy:** Once Flyway is active in production, schema rollbacks via destructive `DROP` or `DOWN` migrations are strictly prohibited (`05-azure-architecture.md:130-135`). Schema changes must be applied forward using expand/contract patterns.

---

## 10. Done When

1. `flyway-core` and `flyway-database-postgresql` dependency versions managed in root `code/backend/pom.xml`.
2. Global 3-digit single-set migration script naming convention (`V<NNN>__<slug>.sql`, e.g. `V001__tenant.sql`) established.
3. Flyway configured to use single history table `core.flyway_schema_history` in `core` schema, owned by `migration_user`.
4. Strict schema execution ordering (`reference` → `core` → `hrms` → `payroll`) configured.
5. Runtime `application.yml` profiles in `app` and `worker` connect as `app_user` with `ddl-auto` completely absent (H-1).
6. `PostgresTestContainerInitializer` executes Flyway migrations as `migration_user` in integration test harness.
7. `FlywayMigrationIT` added and `DatabasePrivilegesIT` extended in `shared` module, passing green in CI.
8. `smoke.sh` prints `PASS ddl-auto is set nowhere` with local postgres container running.
9. `./mvnw clean verify` passes green across all backend modules.
