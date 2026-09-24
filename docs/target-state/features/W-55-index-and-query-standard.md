# W-55 — Index & Query Standard

> Tenant-leading index conventions, related-data fetching in one query (N+1 elimination), and HikariCP connection pooling calibration.
> Derived from `docs/target-state/features/TEMPLATE-INFRA.md` and `TEMPLATE.md`.

| Field | Value |
|---|---|
| **Work item** | `W-55` · issue [#75](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/75) |
| **Kind** | Data / Infra |
| **Stream / track** | Stream G — Infrastructure · Track P |
| **Wave** | Wave 3 — Identity and tenancy |
| **Size / skill** | M · DATA |
| **Owner** | BirenGit |
| **Blocked by** | `W-07` ([#8](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/8)) |
| **Blocks** | `W-13.3` (Employee search & listing) · `W-19` (Pay input ledger) · `W-29` (Pay run execution) |
| **Capabilities** | `PLAT-06` — Index & query performance (`docs/target-state/08-work-plan.md:139`) |
| **Decisions** | `D-09` (Postgres, 4 schemas) · `D-10` (Azure Container Apps) · `D-18` (India region) · `D-19` (10 × 100 scale) · `D-46` (No `ddl-auto`) · `D-57` (Lazy statement-level tenant binding) |
| **Gaps addressed** | `DEBT-018` (Zero `@Index` declarations across 99 entities) · `DEBT-019` (N+1 query cascades across 77/78 repositories) |
| **Status** | **Approved** |
| **Approved by** | Founder |
| **Approved on** | 2026-09-23 |

> **Hard rule 1:** No code is written until this spec is approved by the founder. (Approved 2026-09-23)

---

## 1. Problem

The legacy system suffered from two pervasive database performance anti-patterns alongside uncalibrated connection management:

1. **`DEBT-018` — Zero `@Index` declarations across 99 entities (`legacy/docs/GAP_INVENTORY.md:68`)**:
   In legacy `Payroll-Bend-SBoot`, there were zero secondary `@Index` declarations across all 99 entities and only 7 `@UniqueConstraint`s. 17 entities held `organizationId` as an unindexed plain `String`, causing full table scans on every request (e.g. `OrganizationRoleInterceptor`). In the target PostgreSQL schema, `02-data-model.md:387` mandates that `tenant_id` must be the leading column of every index on a tenant-scoped table, but without an automated convention and enforcement test, new tables and migrations risk omitting `tenant_id` or omitting high-growth indexes (`09-build-order.md:275`).
2. **`DEBT-019` — Systemic N+1 query loops (`legacy/docs/GAP_INVENTORY.md:69`)**:
   Only 1 repository of 78 used `JOIN FETCH`, with 61 entities declaring `FetchType.LAZY`. Loading list screens (e.g. employee directory, pay run generation, attendance lines) triggered 1 query for the parent list plus N queries for child associations. In `code/backend`, `open-in-view` is correctly set to `false` (`code/backend/app/src/main/resources/application.yml:44`), but without explicit batch fetching or single-query fetch standards (`@EntityGraph`, `JOIN FETCH`, DTO projections), service-layer listing will trigger N+1 query loops.
3. **Uncalibrated Connection Pooling**:
   Both `code/backend/app/src/main/resources/application.yml:37-39` and `code/backend/worker/src/main/resources/application.yml:36-39` configure only `maximum-pool-size` (10 for `app`, 5 for `worker`) and `connection-timeout: 5000`. Critical production connection pool parameters are missing:
   - `minimum-idle` (unset, defaults to `maximum-pool-size` in Hikari)
   - `idle-timeout` (unset, defaults to 600,000 ms)
   - `max-lifetime` (unset, defaults to 1,800,000 ms)
   - `leak-detection-threshold` (unset, defaults to 0 / disabled; connections leaked by slow queries or forgotten streams fail silently until the pool is starved)
   - Explicit pool names (`pool-name`) for distinct JMX / log tracing between `app` and `worker`
   - Interoperability with `TenantBindingDataSourceProxy` (`code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantBindingDataSourceProxy.java:35-40`) must be preserved under lazy statement-level binding (`D-57`).

**Baseline — measured on `main` before this ticket opens:**

| Check | Target | Current State |
|---|---|---|
| Index convention enforcement | `code/backend/shared` | Absent (no automated test asserting `tenant_id` is leading across PostgreSQL indexes) |
| Batch fetch size (`hibernate.default_batch_fetch_size`) | `application.yml` (`app` and `worker`) | Absent (unset, Hibernate defaults to single-record fetch, triggering N+1 on lazy collections) |
| Connection leak detection (`leak-detection-threshold`) | `application.yml` (`app` and `worker`) | Absent (`0` / disabled) |
| Hikari pool names (`pool-name`) | `application.yml` (`app` and `worker`) | Absent (anonymous default `HikariPool-1`) |
| Existing entity composite index coverage | `core.employee` (`V010__employee.sql:47-49`) | Covers `(tenant_id, employee_number)`, `(tenant_id, work_email)`, `(tenant_id, status)`. Missing soft-delete composite index `(tenant_id, is_deleted, status)` per `02-data-model.md:391` |

---

## 2. Scope

### In Scope

1. **Tenant-Leading Index Conventions & Documentation (`PLAT-06`, `DEBT-018`)**:
   - Codify index conventions in `code/backend/migration/README.md`:
     - Every index on tables in `core`, `hrms`, `payroll` must start with `tenant_id` as the leading column (`02-data-model.md:387`).
     - Standard naming convention: `idx_<table/feature>_tenant_<columns>` or `uk_<table/feature>_tenant_<columns>`.
     - Tables that grow with time (`audit_log`, `job_status`, `attendance`, `pay_input_ledger`) must index `(tenant_id, ...)` with timestamp ordering (`occurred_at DESC`, `created_at DESC`).
     - Tables using soft delete (`is_deleted`) must include `is_deleted` in query-covering indexes (`02-data-model.md:391`).
   - Create migration `code/backend/migration/src/main/resources/db/migration/core/V024__index_standard_optimizations.sql`:
     - Add composite index `idx_employee_tenant_active_status ON core.employee (tenant_id, is_deleted, status)`.
     - Add composite index `idx_job_status_tenant_status ON core.job_status (tenant_id, status, created_at DESC)`.
2. **Automated Catalog Enforcement Test (`DatabaseIndexConventionIT`)**:
   - Integration test in `code/backend/shared` querying PostgreSQL `pg_catalog.pg_indexes` and `pg_catalog.pg_attribute`.
   - Verifies that 100% of secondary indexes in `core`, `hrms`, and `payroll` schemas have `tenant_id` as their leading column (column position 1).
   - Exempts primary key indexes (`_pkey`), which index surrogate UUID/bigserial keys, and exempts the `reference` schema tables (`02-data-model.md:358-364`).
3. **Single-Query Related-Data Fetching Standard & N+1 Prevention (`DEBT-019`)**:
   - Standardize query patterns:
     - `@EntityGraph(attributePaths = {...})` or JPQL `JOIN FETCH` for eager association retrieval in a single SQL query.
     - Spring Data DTO projection interfaces / records for list screens to read only required scalar fields without loading full entities.
   - Configure global batch fetching in `application.yml` (`app` and `worker`):
     - `spring.jpa.properties.hibernate.default_batch_fetch_size: 25`.
     - Reduces unavoidable lazy collection traversals from $N$ queries to $1 + \lceil N / 25 \rceil$ queries.
   - Create automated query-count assertion utility & test (`QueryCountAssertions` and `QueryCountIT`) verifying that listing operations execute in $O(1)$ queries (e.g. 1 or 2 queries), never $N+1$.
4. **HikariCP Connection Pool Calibration (`D-10`, `D-18`, `D-19`, `D-57`)**:
   - Tune `code/backend/app/src/main/resources/application.yml` (and `application-local.yml`):
     - `maximum-pool-size: 10`
     - `minimum-idle: 5`
     - `idle-timeout: 300000` (5 minutes)
     - `max-lifetime: 1800000` (30 minutes)
     - `connection-timeout: 5000` (5 seconds)
     - `leak-detection-threshold: 30000` (30 seconds)
     - `pool-name: InfinevoAppHikariPool`
   - Tune `code/backend/worker/src/main/resources/application.yml`:
     - `maximum-pool-size: 5`
     - `minimum-idle: 2`
     - `idle-timeout: 300000` (5 minutes)
     - `max-lifetime: 1800000` (30 minutes)
     - `connection-timeout: 5000` (5 seconds)
     - `leak-detection-threshold: 30000` (30 seconds)
     - `pool-name: InfinevoWorkerHikariPool`
   - Verify non-interference with `TenantBindingDataSourceProxy` (`shared` module) to ensure lazy statement-level binding (`D-57`) remains intact.

### Out of Scope

- Search and listing UI endpoints for employees (owned by `W-13.3`).
- Pay run calculation batch query optimization (owned by `W-29`).
- Redis query caching (owned by `W-53`).
- Modifying tables in `legacy/` (frozen, prohibited by `guard-edit` and Hard Rule 5).

---

## 3. What Gets Built

### Implementer Tasks & Modules Touched

| Module | Task ID | File / Package | Change Description |
|---|---|---|---|
| `code/backend/migration` | **T1** | `code/backend/migration/README.md` | Document tenant-leading index rules, naming conventions, and soft-delete index standard |
| `code/backend/migration` | **T1** | `db/migration/core/V024__index_standard_optimizations.sql` | Add composite indexes on `core.employee` and `core.job_status` |
| `code/backend/app` | **T2** | `code/backend/app/src/main/resources/application.yml` | Configure Hikari pool parameters and `hibernate.default_batch_fetch_size: 25` |
| `code/backend/app` | **T2** | `code/backend/app/src/main/resources/application-local.yml` | Align local profile Hikari pool parameters |
| `code/backend/worker` | **T2** | `code/backend/worker/src/main/resources/application.yml` | Configure worker Hikari pool parameters and `hibernate.default_batch_fetch_size: 25` |
| `code/backend/shared` | **T3** | `com.infinevo.shared.db.DatabaseIndexConventionIT` | Automated integration test asserting all indexes in `core`, `hrms`, `payroll` lead with `tenant_id` |
| `code/backend/shared` | **T4** | `com.infinevo.shared.pool.HikariPoolCalibrationTest` | Unit test asserting HikariDataSource pool properties match calibrated standards |
| `code/backend/shared` | **T4** | `com.infinevo.shared.query.QueryCountIT` | Integration test proving single-query fetching avoids N+1 loops |

### What is NOT Touched

- `code/frontend/**` — no UI changes.
- `infra/azure/**` — no Bicep infrastructure changes.
- `legacy/**` — frozen production archive.

---

## 4. Database Changes

> Flyway only. Never `ddl-auto`. See `CONVENTIONS.md` rule 4.

### Migration Script: `core/V024__index_standard_optimizations.sql`

```sql
-- Migration: V024__index_standard_optimizations.sql
-- Description: Composite index optimizations adhering to W-55 standards (DEBT-018, PLAT-06)

-- 1. core.employee: Active status listing index covering tenant_id and soft-delete flag (02-data-model.md:391)
CREATE INDEX idx_employee_tenant_active_status
    ON core.employee (tenant_id, is_deleted, status);

-- 2. core.job_status: Polling and listing index for queue jobs ordered by recency
CREATE INDEX idx_job_status_tenant_status
    ON core.job_status (tenant_id, status, created_at DESC);
```

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V024__index_standard_optimizations.sql` | `core.employee`, `core.job_status` | yes | yes (`DROP INDEX`) |

- [x] `tenant_id` present on every new index leading column (`02-data-model.md:387`)
- [x] Index on `tenant_id` plus lookup columns (`DEBT-018`)
- [x] Money columns are `BigDecimal` with explicit precision and scale (n/a — no new columns)
- [x] Expand / contract sequencing — non-destructive additions only

---

## 5. Proving It

An infra deliverable must fail when constraints are violated:

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Create a test table in schema `core` with an index that does NOT lead with `tenant_id` (e.g. `CREATE INDEX ON core.test (status, tenant_id)`) | `DatabaseIndexConventionIT` detects the index and fails with violation message |
| 2 | Execute a list operation fetching parent entities with un-batched lazy collections | `QueryCountIT` query-counter catches query count $> 2$ and fails |
| 3 | Configure `leak-detection-threshold: 0` or omit `minimum-idle` | `HikariPoolCalibrationTest` fails property assertions |

---

## 6. Verification

Exact commands to run on a clean checkout:

```bash
# 1. Run new shared index, query, and pool tests
./mvnw test -pl shared -Dtest=DatabaseIndexConventionIT,HikariPoolCalibrationTest,QueryCountIT

# 2. Run full backend verification
./mvnw verify
```

| Check | Expected | Result |
|---|---|---|
| `DatabaseIndexConventionIT` | Passes: all secondary indexes in `core` lead with `tenant_id` | Pass |
| `HikariPoolCalibrationTest` | Passes: pool size, idle, lifetime, and leak-detection match spec | Pass |
| `QueryCountIT` | Passes: related data fetched in $O(1)$ queries | Pass |
| `./mvnw verify` | Build succeeds with zero test failures | Pass |

---

## 7. Gap Disposition

| Gap | Disposition |
|---|---|
| `DEBT-018` — zero `@Index` declarations across 99 entities | **Fixed.** Tenant-leading index standard established in `migration/README.md`, automated catalog assertion test `DatabaseIndexConventionIT` active, and `V024` optimizations applied. |
| `DEBT-019` — N+1 query loops across 77/78 repositories | **Fixed.** Global `default_batch_fetch_size: 25` configured, `@EntityGraph` / `JOIN FETCH` standard established, and verified via `QueryCountIT`. |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Over-indexing write-heavy tables reduces insert throughput | Low | Scale is calibrated to $10 \times 100$ (`D-19`). Indexes are restricted to lookup columns on query paths and time-growing tables (`audit_log`, `job_status`). |
| Inadvertent Cartesian product when using `JOIN FETCH` on multiple collections | Medium | Spec mandates `@EntityGraph` or batch fetching (`default_batch_fetch_size: 25`) rather than multiple parallel `JOIN FETCH` clauses. |
| Connection leak in long-running transactions starving pool | Low | `leak-detection-threshold: 30000` (30 seconds) alerts in logs immediately when a connection is checked out beyond 30s. |

---

## 9. Rollback

If rolled back:
1. Revert git commit.
2. Drop indexes added in `V024__index_standard_optimizations.sql` (`DROP INDEX IF EXISTS core.idx_employee_tenant_active_status; DROP INDEX IF EXISTS core.idx_job_status_tenant_status;`).
3. No cloud resources or schema data altered.

---

## 10. Done When

1. Tenant-leading index conventions and naming standards documented in `code/backend/migration/README.md`.
2. Flyway migration `V024__index_standard_optimizations.sql` applied with composite indexes on `core.employee` and `core.job_status`.
3. `DatabaseIndexConventionIT` runs in CI and asserts that 100% of secondary indexes in tenant-scoped schemas (`core`, `hrms`, `payroll`) have `tenant_id` in position 1.
4. `application.yml` for both `app` and `worker` configures `spring.jpa.properties.hibernate.default_batch_fetch_size: 25`.
5. HikariCP connection pool settings calibrated with `minimum-idle`, `idle-timeout`, `max-lifetime`, `leak-detection-threshold: 30000`, and named pools in `app` and `worker`.
6. `QueryCountIT` and `HikariPoolCalibrationTest` pass cleanly.
7. Full backend build `./mvnw verify` passes.

---

## Decisions Needed Before Implementation

**1. Hibernate Batch Fetch Size Calibration**
- **Option (a):** `default_batch_fetch_size: 25` (Recommended). Matches page sizes (20–25 rows per list screen) and prevents large `IN (...)` SQL clause overhead.
- **Option (b):** `default_batch_fetch_size: 50` or higher. Slightly fewer queries on 100-row batch runs, but higher memory footprint per query batch.
- **Recommend (a)** because our target scale is 10 tenants × 100 employees (`D-19`), where list screens page at 20–25 rows.

**2. Automated Index Convention Assertion Mechanism**
- **Option (a):** Database integration test (`DatabaseIndexConventionIT`) querying PostgreSQL `pg_indexes` / `pg_attribute` catalog (Recommended). Validates the true runtime database schema after Flyway migrations execute.
- **Option (b):** Static analysis of Flyway SQL files via ArchUnit or regex. Faster, but cannot verify effective index column ordering or indexes generated by constraints.
- **Recommend (a)** because verifying against the live PostgreSQL catalog guarantees that what is in the database strictly obeys `tenant_id` leading.
