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
| **Status** | Draft (Revised following spec review findings F-1 through F-18) |
| **Approved by** | *(founder approval is required before implementation — `CONVENTIONS.md` rule 1)* |
| **Approved on** | |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

In the legacy codebase, two MySQL databases (`HRMS_Backend` and `Payroll-Bend-SBoot`) relied on Hibernate `spring.jpa.hibernate.ddl-auto=update` without any migration framework (`BUG-004`, `DEBT-002`), evidenced at [legacy/HRMS_Backend/src/main/resources/application.properties:10](file:///d:/Workspace/infinevo-platform/legacy/HRMS_Backend/src/main/resources/application.properties#L10) and [legacy/Payroll-Bend-SBoot/src/main/resources/application.properties:5](file:///d:/Workspace/infinevo-platform/legacy/Payroll-Bend-SBoot/src/main/resources/application.properties#L5). Schema alterations occurred silently at application boot, leading to uncontrolled schema drift across environments, lack of DDL version control, and zero rollback capability.

`W-05` delivered the database foundation by establishing four isolated schemas (`reference`, `core`, `hrms`, `payroll`) owned by `migration_user`, creating `db/migration/{reference,core,hrms,payroll}/` placeholder directories on `main`, and strictly prohibiting `app_user` from executing DDL.

However, database migration execution infrastructure remains incomplete:
1. **Missing Migration Runner Infrastructure:** Neither `code/backend/app` nor `code/backend/worker` has a configured Flyway runner to execute versioned DDL scripts.
2. **Single Script Set Architectural Violation (F-1):** `docs/target-state/02-data-model.md:21` specifies *"One numbered script set covering all four schemas, applied together"*, restated at `docs/target-state/03-code-structure.md:142`. Running four separate Flyway executions with per-schema history tables violates this design.
3. **Runtime Credentials Security Hazard (F-2):** `docs/target-state/02-data-model.md:380` limits `migration_user` to the pipeline migration step only. `docs/target-state/05-azure-architecture.md:126` requires migration as a separate pipeline step before revisions take traffic. Base `application.yml` files must NOT contain `migration_user` credentials, or runtime containers would gain DDL rights, destroying the security boundary established in `W-05`.
4. **Lack of Automated Test Assertions (F-6):** No integration test asserts that Flyway executes cleanly, creates `flyway_schema_history` owned by `migration_user`, or applies migrations under Testcontainers.

---

## 2. Scope

**In scope**

- **Single Global Script Set Convention (F-1, F-9):** Establish a single global version sequence (`V001__<slug>.sql`, `V002__<slug>.sql`) across all schemas, tracked by a single `flyway_schema_history` table in the primary schema (`core`), owned by `migration_user`.
- **Flyway Runner Configuration (F-2):** Configure Flyway dependencies in `code/backend/pom.xml` and `code/backend/migration`. Configure Flyway to run using `migration_user` credentials in the local profile (`application-local.yml`) and CI/CD pipeline migration step. Base `application.yml` connects runtime applications as `app_user` with DDL denied.
- **Integration Test Harness Integration (F-6, F-10):** Update `PostgresTestContainerInitializer` in `code/backend/shared` to run Flyway migrations as `migration_user`. Extend [DatabasePrivilegesIT.java](file:///d:/Workspace/infinevo-platform/code/backend/shared/src/test/java/com/infinevo/shared/test/DatabasePrivilegesIT.java) and add `FlywayMigrationIT.java` to verify `flyway_schema_history` creation, ownership, and DDL refusal for `app_user`.
- **Script Immutability & Forward-Only Policy (F-14):** Enforce that applied migration scripts are immutable (`03-code-structure.md:141`) and schema changes follow expand/contract forward-only rules (`05-azure-architecture.md:130-135`).
- **Task Decomposition by Module (F-8):** Break down implementation into per-module tasks in strict dependency order.

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
│              Executes Single Global Script Set (V001__..., V002__...)          │
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

## 4. Task Decomposition by Module (F-8)

To support module-confined development, the work is divided into five sequential module tasks:

### Task 1: Root Build Configuration (`code/backend/pom.xml`)
- Add `flyway-core` and `flyway-database-postgresql` version management under `<dependencyManagement>`.

### Task 2: Migration Module (`code/backend/migration/`)
- Update `code/backend/migration/pom.xml` to include `flyway-core` and `flyway-database-postgresql`.
- Retain existing `src/main/resources/db/migration/{reference,core,hrms,payroll}/` directory structure (F-12) for organizing single-set migration scripts.

### Task 3: Shared Test Harness (`code/backend/shared/`)
- Update `PostgresTestContainerInitializer.java` to execute Flyway migrations as `migration_user` after running SQL bootstrap scripts.
- Add `FlywayMigrationIT.java` asserting `core.flyway_schema_history` exists, is owned by `migration_user`, and contains applied scripts.
- Extend `DatabasePrivilegesIT.java` (F-10) asserting `app_user` is refused DDL and DML write access on `core.flyway_schema_history`.

### Task 4: Web Application (`code/backend/app/`)
- Add Flyway spring boot starter to `code/backend/app/pom.xml`.
- Configure `application-local.yml` with `migration_user` credentials and local Flyway execution.
- Maintain `application.yml` connecting as `app_user` without embedded DDL owner credentials (F-2). Set `spring.jpa.hibernate.ddl-auto=none` (F-3).

### Task 5: Worker Application (`code/backend/worker/`)
- Add Flyway spring boot starter to `code/backend/worker/pom.xml`.
- Configure `application-local.yml` matching `app`.
- Maintain `application.yml` connecting as `app_user` with `spring.jpa.hibernate.ddl-auto=none` (F-3).

---

## 5. Database Changes & Script Conventions

### Script Naming & Versioning Conventions (F-9)
- **Pattern:** `V<NNN>__<slug>.sql` (e.g., `V001__init_schema_history.sql`, `V002__tenant_baseline.sql`).
- **Global Sequence:** One single global version sequence shared across all schemas (`02-data-model.md:21`). Script order and dependencies are governed strictly by version numbers, not by location directory lists.
- **Single History Table (F-1):** Tracked by a single history table: `core.flyway_schema_history` owned by `migration_user`.
- **Immutability & Forward-Only Rule (F-14):** Once a script is merged into `main`, it must NEVER be edited (`03-code-structure.md:141`). Schema evolution follows expand/contract forward-only patterns (`05-azure-architecture.md:130-135`).

### TEMPLATE §6 Checklist Restoration (F-13)

- [ ] `tenant_id` present on every new table — *N/A: W-06 creates no domain tables*
- [ ] Index on `tenant_id` plus lookup columns (`DEBT-018`) — *N/A: W-06 creates no domain tables*
- [ ] Money columns are `BigDecimal` with explicit precision and scale — *N/A: W-06 creates no domain tables*
- [x] Expand / contract sequencing — no destructive step (`05-azure-architecture.md:130-135`)
- [x] `ddl-auto` set to `none` (or omitted) across active application profiles (F-3)

---

## 6. Tests (F-10, F-15)

| Type | File | Covers |
|---|---|---|
| **Unit** | `code/backend/migration/src/test/java/com/infinevo/migration/FlywayConfigurationTest.java` | Asserts Flyway script naming format and classpath resource resolving. |
| **Integration** | `code/backend/shared/src/test/java/com/infinevo/shared/test/FlywayMigrationIT.java` | **New.** Asserts `core.flyway_schema_history` exists, is owned by `migration_user`, and records applied migrations cleanly against Testcontainers. |
| **Integration** | `code/backend/shared/src/test/java/com/infinevo/shared/test/DatabasePrivilegesIT.java` | **Extended (F-10).** Asserts `app_user` is denied DDL/DML on `core.flyway_schema_history` while `migration_user` retains ownership. |

---

## 7. Verification (F-4, F-5, F-6, F-7)

> **Validation Design Note (F-6):** Criterion 1 fails on `main` today because `FlywayMigrationIT` does not exist. Criterion 3 fails on `main` today because `core.flyway_schema_history` is absent. Both pass green once W-06 is implemented.

Run by the **verifier** on a clean checkout with Docker active.

### Execution Commands

```bash
# 1 - Verify clean Maven build, Spotless formatting, and new Flyway integration tests
cd code/backend && ./mvnw clean verify -Dtest=FlywayMigrationIT,DatabasePrivilegesIT

# 2 - Start local Postgres container (required by smoke.sh per F-5)
cd ../.. && docker compose -f infra/docker/compose.yml up -d postgres

# 3 - Run repository smoke script to verify ddl-auto absence
bash infra/docker/smoke.sh

# 4 - Verify single history table existence and migration_user ownership in container
docker exec -i infinevo-postgres psql -U app_user -d infinevo -c "SELECT count(*) FROM core.flyway_schema_history;"
```

### Verification Results Matrix

| Check | Command / Assertion | Expected Output | Result |
|---|---|---|---|
| **Integration Tests** | `./mvnw clean verify -Dtest=FlywayMigrationIT,DatabasePrivilegesIT` | `BUILD SUCCESS` with green test pass | |
| **Local Docker Bootstrap** | `docker compose up -d postgres` | Container `infinevo-postgres` running healthy | |
| **ddl-auto Absence** | `bash infra/docker/smoke.sh` | `PASS ddl-auto is set nowhere` *(exact string from `smoke.sh:81` per F-4)* | |
| **Schema History Table** | `psql -U app_user -d infinevo -c "SELECT count(*)..."` | Returns row count of applied scripts in `core.flyway_schema_history` | |
| **app_user DDL Refusal** | `psql -U app_user -d infinevo -c "DROP TABLE core.flyway_schema_history;"` | `ERROR: permission denied for table flyway_schema_history` | |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Runtime containers assigned `migration_user` credentials in base configuration (F-2) | Medium | Restrict `migration_user` credentials strictly to local development profile (`application-local.yml`) and pipeline migration step. Base `application.yml` runs as `app_user`. |
| Developers create separate per-schema history tables (F-1) | Low | Enforce single history table configuration (`core.flyway_schema_history`) and global script versioning sequence (`V001__...`). |

---

## 9. Rollback & Forward Migration Policy (F-14)

1. **W-06 Feature Rollback:** If W-06 configuration changes break build or startup before production deployment, `git revert` the merge commit and wipe local test volumes via `docker compose down -v`.
2. **Production Migration Policy (F-14):** Once Flyway is active in production, schema rollbacks via destructive `DROP` or `DOWN` migrations are strictly prohibited (`05-azure-architecture.md:130-135`). Schema changes must be applied forward using expand/contract patterns.

---

## 10. Done When

1. `flyway-core` and `flyway-database-postgresql` dependency versions managed in root `code/backend/pom.xml`.
2. Global single-set migration script naming convention (`V<NNN>__<slug>.sql`) established.
3. Flyway configured to use single history table `core.flyway_schema_history` owned by `migration_user`.
4. Runtime `application.yml` profiles in `app` and `worker` connect as `app_user` with `ddl-auto=none` (F-2, F-3).
5. `PostgresTestContainerInitializer` executes Flyway migrations as `migration_user` in integration test harness.
6. `FlywayMigrationIT` added and `DatabasePrivilegesIT` extended in `shared` module, passing green in CI.
7. `smoke.sh` prints `PASS ddl-auto is set nowhere` with local postgres container running.
8. `./mvnw clean verify` passes green across all backend modules.
