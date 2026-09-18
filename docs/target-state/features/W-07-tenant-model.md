# W-07 — Tenant model

| Field | Value |
|---|---|
| **Work item** | `W-07` · issue [#8](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/8) |
| **Kind** | Data / Infra — tenant isolation mechanism, no end-user capability |
| **Stream / track** | Stream B — Data foundation · Track P |
| **Wave** | 2 — Data platform |
| **Size / skill** | L · DATA |
| **Owner** | unassigned — claimable (#107) |
| **Blocked by** | `W-06` #7 (merged 2026-09-18) |
| **Blocks** | `W-08` · `W-09` reference schema & seed · every table from `W-13` on |
| **Capabilities** | `CORE-01` Tenant registry |
| **Decisions** | `D-08` reference schema · `D-09` Postgres with Flyway · `D-45` migration schema · `D-46` ddl-auto absent · §13 R1/R5/R6 from W-06 · **open decisions at bottom** |
| **Gaps addressed** | `BUG-002` — dispositioned in §6 |
| **Status** | **Draft — not approved** |
| **Approved by** | |
| **Approved on** | |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

`W-06` delivered the migration runner and Flyway mechanism. Four application schemas
exist (`core`, `hrms`, `payroll`, `reference`), owned by `migration_user`. **Zero
application tables exist.** Nothing enforces the platform's primary isolation contract.

Three things are missing and each makes the others worthless:

| Gap | Why it matters |
|---|---|
| No `tenant_id` column standard | Future developers invent their own type, nullability, and index — or omit it entirely |
| No row-level security policies | `app_user` would read every tenant's data. The grant chain from `W-06` gives `app_user` `SELECT` on every row; RLS is what makes that safe |
| No build check | A convention with no enforcement decays within a month. `W-13` onwards will add ~115 tables; a check absent at `W-13` will be retrofitted at `W-95` if at all |

`W-07 → W-08` is the one genuinely rigid chain in the project
(`docs/target-state/09-build-order.md:66`). This ticket is the second link.

**BUG-002** (`legacy/docs/GAP_INVENTORY.md:28`): HRMS carries no tenant column on any
entity — 0 files matching `organizationId|tenant` across the frozen backend. That defect
cannot be fixed until the target schema exists with enforcement in place. W-07 is the
prerequisite; the actual fix is `W-13` onwards.

**W-06 §13 R7** explicitly deferred to this ticket: *"mechanical enforcement [of schema
qualification] moves to W-07, which already builds a check over migration scripts."*
That check is now owed.

**Legacy source citation**: `core.tenant` is ported from `Organization` in
`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/Organization.java:28`.
In legacy Payroll, `Organization` stored both multi-tenant identity (`organizationId`, `organizationName`)
and company metadata (`addressLine1`, `city`, `state`, `pinCode`, statutory details). In target architecture,
`core.tenant` is stripped to pure root tenant identity (`id`, `tenant_id`, `name`, `created_at`, `created_by`, `updated_at`, `updated_by`),
while organization address/tax metadata (GSTIN/PAN) is deferred to `core.organization_details` in Wave 3 (`W-13`).

**Baseline** — measured on `main` at commit `365a319`, 2026-09-18.

| Element | Status on `main` | Location | Notes |
|---|---|---|---|
| `core.tenant` Flyway DDL | Absent | `code/backend/migration/.../core/` | Zero shipped migration scripts in `core/` |
| RLS policy on `core.tenant` | Absent | PostgreSQL schema | Zero RLS policies defined |
| `tenant_id` standard | **Present** | `code/backend/migration/README.md:52-72` | Documented in W-06; enforced here |
| `TenantContext.java` | **Present** | `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantContext.java:30` | Exists in `shared`; adding `setForConnection` method |
| `TenantContextTest.java` | **Present** | `code/backend/shared/src/test/java/com/infinevo/shared/tenant/TenantContextTest.java` | Exists in `shared` tests |
| CI Gate A & B | Absent | `.github/workflows/ci.yml` | Gates owed by W-06 §13 R7 |
| `TenantIsolationIT` | Absent | `code/backend/migration/src/test/java/...` | Owed integration test |

---

## 2. Scope

**In scope**

- The **`core.tenant` table** — the first shipped Flyway migration script (`V001__tenant.sql`
  in `core/`). Every subsequent table carries a `tenant_id` column that refers to a value
  in this table's `tenant_id` column.
- The **`tenant_id` column standard**: exact type, nullability, default, index placement,
  and the `tenant_id`-as-leading-index rule. Documented in `migration/README.md` (W-06) and
  enforced by the CI check.
- **Row-level security on `core.tenant`**: `ENABLE ROW LEVEL SECURITY`, `CREATE POLICY`,
  and the `app.current_tenant_id` session variable as the isolation key. Establishes the
  pattern every future migration script must follow.
- **`TenantContext` enhancement** — class `TenantContext.java` in `code/backend/shared`
  gains `setForConnection(Connection)` utility executing `SET LOCAL app.current_tenant_id = ?`.
- **CI build check — two gates added to `.github/workflows/ci.yml`**:
  1. Every `CREATE TABLE` in `db/migration/core/`, `db/migration/hrms/`, or
     `db/migration/payroll/` must reference `tenant_id`. If any do not — fail.
  2. Every `CREATE TABLE` in those same directories must name its schema (the table name
     contains a `.`). If any do not — fail. (W-06 §13 R7 owed check.)
- **Integration test (`TenantIsolationIT`)** in the `migration` module: provisions a
  Testcontainer, seeds two tenants (Tenant A and Tenant B), runs `V001__tenant.sql`, and proves RLS isolation.
- **`migration/README.md` additions**: append the row-level security section verbatim from §3a.

**Out of scope**

- All domain tables — `W-13` onwards.
- The 15 `reference` tables and seed data — **`W-09`**. `reference` is exempt from
  `tenant_id` (`D-08`, `02-data-model.md:16`, W-06 §13 R6).
- Application-layer request filter that reads tenant from the Keycloak JWT and calls
  `TenantContext` — deferred to the first feature ticket that needs it (`W-08` or later).
- Foreign-key constraint from domain tables to `core.tenant(tenant_id)` — each domain
  migration script adds its own FK as it is written (`W-13` onwards).
- Production deployment of the migration job — **`W-54`**.
- The three `docs/` rewordings owed by W-06 §13 — they travel their own `sync-docs`
  pull requests before `W-07` starts (condition of W-06 approval).

---

## 3. What gets built

```
 app_user sets: SET LOCAL app.current_tenant_id = '<uuid>'
       |
       v  per transaction
  PostgreSQL RLS policy:
      USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
       |
       v
  core.tenant — the root entity. Every other table in core/hrms/payroll
                carries tenant_id referencing this table's tenant_id column.
```

### 3a. The standards that go into `migration/README.md`

These two sections are copied verbatim into `migration/README.md`.

#### `tenant_id` column standard

Every table in `core`, `hrms`, and `payroll` carries this column, defined exactly:

```sql
tenant_id uuid NOT NULL
```

No default value — the inserting code supplies the tenant UUID from the current
session context. No exceptions, no nullable variant, no `bigint` surrogate.

The index that accompanies it, on every table, with `tenant_id` as the leading column:

```sql
CREATE INDEX ON <schema>.<table> (tenant_id, id);
```

Add lookup columns after `id` when that table's list screens filter by them.

**`reference` is the one exemption.** Tables in the `reference` schema carry no
`tenant_id` — they hold national data identical for every tenant (`D-08`,
`02-data-model.md:16`).

**Reviewing a migration script?** Find every `CREATE TABLE`. If the table is outside
`reference` and the column list contains no `tenant_id`, stop and ask.

#### Row-level security

Every table in `core`, `hrms`, and `payroll` enables RLS and carries exactly one
isolation policy. Add both in the same migration script that creates the table:

```sql
ALTER TABLE <schema>.<table> ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON <schema>.<table>
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
```

`migration_user` owns the table and bypasses RLS automatically (PostgreSQL table-owner
rule). `app_user` and `readonly_user` are subject to the policy; they see only rows
where `tenant_id` matches the value set at transaction start by
`SET LOCAL app.current_tenant_id = '<uuid>'`.

The `true` flag in `current_setting(..., true)` makes the function return `NULL`
(instead of raising) when the variable is not set. The `::uuid` cast then fails, and
the USING clause evaluates to `NULL` — which PostgreSQL treats as `false`. A connection
that never sets the variable sees zero rows. This is the intended fail-safe.

**Reviewing a migration script?** For every `CREATE TABLE` outside `reference`,
confirm `ENABLE ROW LEVEL SECURITY` and `CREATE POLICY tenant_isolation` appear in
the same script, with the exact USING clause above.

### 3b. Files & Implementer Task Breakdown

Work is split across three distinct tasks (one module/area per task, adhering to implementer scoping rules):

**Task 1: Shared module (`code/backend/shared`)**
- `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantContext.java` — **Modified.** Existing class updated to add `setForConnection(Connection conn)` method that executes `SET LOCAL app.current_tenant_id = ?`.
- `code/backend/shared/src/test/java/com/infinevo/shared/tenant/TenantContextTest.java` — **Modified.** Existing test updated to cover `setForConnection` and connection pool boundary assertions.

**Task 2: Migration module (`code/backend/migration`)**
- `code/backend/migration/src/main/resources/db/migration/core/V001__tenant.sql` — **New.** Creates `core.tenant`, unique index on `tenant_id`, composite index `(tenant_id, id)`, enables RLS, and creates isolation policy `tenant_isolation`.
- `code/backend/migration/src/test/java/com/infinevo/migration/TenantIsolationIT.java` — **New.** Integration test. Provisions Testcontainer, seeds two tenants (Tenant A and Tenant B per `active-work.md:182`), runs `V001__tenant.sql`, and asserts RLS isolation probes (breaks 3, 4, 5).
- `code/backend/migration/README.md` — **Modified.** Appends the row-level security section from §3a verbatim.

**Task 3: CI workflows (`.github/workflows`)**
- `.github/workflows/ci.yml` — **Modified.** Two new `static` steps: `tenant_id present in all non-reference CREATE TABLE` and `schema qualifier present in all CREATE TABLE` (§3d).

**Not touched.** `infra/postgres/` — roles, schemas, and grants are correct and complete from W-05 and W-06. `infra/docker/compose.yml` — no change.

### 3c. `V001__tenant.sql` — exact structure

```sql
-- W-07: core.tenant — root of every tenant relationship.
-- Every table in core, hrms, payroll carries a tenant_id column referencing
-- this table's tenant_id column. migration/README.md §tenant_id column standard.

CREATE TABLE core.tenant (
    id          bigserial    NOT NULL,
    tenant_id   uuid         NOT NULL,
    name        text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  text         NOT NULL DEFAULT 'system',
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  text         NOT NULL DEFAULT 'system',
    CONSTRAINT tenant_pkey PRIMARY KEY (id)
);

-- tenant_id is the external identifier shared with all other tables.
CREATE UNIQUE INDEX ON core.tenant (tenant_id);

-- Standard tenant-scoped index (migration/README.md §tenant_id column standard).
CREATE INDEX ON core.tenant (tenant_id, id);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.tenant ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.tenant
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
```

### 3d. CI check steps

Both steps go in the `static` job in `.github/workflows/ci.yml`, beside the existing `ddl-auto set nowhere` and `spring.flyway confined to migration module` steps.

**Step A — tenant_id present in all non-reference CREATE TABLE statements**

```yaml
- name: tenant_id present in all non-reference CREATE TABLE statements
  if: ${{ !cancelled() }}
  run: |
    bad=$(find code/backend/migration/src/main/resources/db/migration/core \
               code/backend/migration/src/main/resources/db/migration/hrms \
               code/backend/migration/src/main/resources/db/migration/payroll \
          -name '*.sql' 2>/dev/null \
        | xargs -r grep -lE 'CREATE[[:space:]]+TABLE' 2>/dev/null \
        | xargs -r grep -LE 'tenant_id' 2>/dev/null || true)
    if [ -n "$bad" ]; then
      echo "::error::Migration script(s) create a table in core/hrms/payroll without tenant_id (W-07 §tenant_id column standard):"
      printf '%s\n' "$bad"
      exit 1
    fi
    echo "OK - all non-reference CREATE TABLE statements include tenant_id"
```

**Step B — schema qualifier present in all CREATE TABLE statements**

```yaml
- name: schema qualifier present in all CREATE TABLE statements
  if: ${{ !cancelled() }}
  run: |
    bad=$(find code/backend/migration/src/main/resources/db/migration/ \
          -name '*.sql' 2>/dev/null \
        | xargs -r grep -nE 'CREATE[[:space:]]+TABLE([[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS)?[[:space:]]+[a-zA-Z0-9_]+[[:space:](]' 2>/dev/null || true)
    if [ -n "$bad" ]; then
      echo "::error::Unqualified CREATE TABLE found — every table must be schema-qualified (W-06 §13 R7, W-07 CI gate):"
      echo "$bad"
      exit 1
    fi
    echo "OK - all CREATE TABLE statements are schema-qualified"
```

---

## 4. Proving it

The deliverable is a constraint. The tests that matter are the ones that break it.

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Add a script `core/V999__no_tenant.sql` containing `CREATE TABLE core.missing_tenant (id bigserial PRIMARY KEY)` — no tenant_id | CI Step A fails: "Migration script(s) create a table in core/hrms/payroll without tenant_id" |
| 2 | Add a script `core/V999__unqualified.sql` containing `CREATE TABLE employee (id bigserial PRIMARY KEY, tenant_id uuid NOT NULL)` — no dot | CI Step B fails: "Unqualified CREATE TABLE found" |
| 3 | Connect as `app_user` to test Testcontainer WITHOUT calling `TenantContext.setForConnection(conn)` and `SELECT * FROM core.tenant` | Returns 0 rows — the RLS policy's NULL cast evaluates to false |
| 4 | Connect as `app_user`, call `setForConnection` with tenant UUID A, insert two rows for A and one for B (as migration_user), then SELECT | Returns exactly 2 rows — only tenant A's rows visible |
| 5 | Connect as `migration_user` and `SELECT * FROM core.tenant` with no SET LOCAL | Returns all rows — table owner bypasses RLS by PostgreSQL default |

Prove breaks 1 and 2 by running CI on a throwaway branch. Prove breaks 3–5 in `TenantIsolationIT`. Never merge the break branches.

---

## 5. Database changes

| Migration Script | Target Schema | Description |
|---|---|---|
| `V001__tenant.sql` | `core` | Creates `core.tenant` table, indexes, enables RLS, and creates `tenant_isolation` RLS policy |

---

## 6. Verification

Exact commands for the **verifier**. Clean checkout, Docker active.

```bash
# 1 — build and run all tests
cd code/backend && ./mvnw clean verify -pl migration,shared -am
# Expected: BUILD SUCCESS
# Expected: TenantIsolationIT in Failsafe summary, tests run > 0
# Expected: TenantContextTest in Surefire summary, tests run > 0

# 2 — confirm V001 ran and core.tenant exists
docker compose -f infra/docker/compose.yml down -v
docker compose -f infra/docker/compose.yml up -d postgres migrate
docker compose -f infra/docker/compose.yml wait migrate
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -tAU postgres -d infinevo \
  -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='core' AND table_name='tenant'"
# Expected: 1

# 3 — confirm RLS is enabled on core.tenant
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -tAU postgres -d infinevo \
  -c "SELECT rowsecurity FROM pg_class WHERE relname='tenant' AND relnamespace='core'::regnamespace"
# Expected: t

# 4 — confirm app_user sees nothing without SET LOCAL (using SET ROLE via postgres superuser)
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -tAU postgres -d infinevo \
  -c "SET ROLE app_user; SELECT count(*) FROM core.tenant;"
# Expected: 0

# 5 — CI Step A clean on shipped scripts
find code/backend/migration/src/main/resources/db/migration/core \
     code/backend/migration/src/main/resources/db/migration/hrms \
     code/backend/migration/src/main/resources/db/migration/payroll \
     -name '*.sql' 2>/dev/null \
  | xargs -r grep -lE 'CREATE[[:space:]]+TABLE' 2>/dev/null \
  | xargs -r grep -LE 'tenant_id' 2>/dev/null
# Expected: no output (exit 0 from xargs on empty input)

# 6 — CI Step B clean on shipped scripts
find code/backend/migration/src/main/resources/db/migration/ \
     -name '*.sql' 2>/dev/null \
  | xargs -r grep -nE 'CREATE[[:space:]]+TABLE([[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS)?[[:space:]]+[a-zA-Z0-9_]+[[:space:](]'
# Expected: no output
```

| Check | Expected |
|---|---|
| `TenantIsolationIT` passes | Breaks 3, 4, 5 from §4 pass as positive assertions (including two-tenant seed) |
| `TenantContextTest` passes | Unit tests for ThreadLocal get/set/clear and setForConnection |
| `core.tenant` exists | `count = 1` |
| RLS enabled on `core.tenant` | `rowsecurity = t` |
| `app_user` sees 0 rows without context | `count = 0` |
| CI Step A passes on V001 | No output |
| CI Step B passes on V001 | No output |
| CI Step A fails on break 1 | Error message, exit 1 (linked from PR) |
| CI Step B fails on break 2 | Error message, exit 1 (linked from PR) |

---

## 7. Gap disposition

| Gap | Disposition |
|---|---|
| `BUG-002` — HRMS carries no tenant column on any entity (`legacy/docs/GAP_INVENTORY.md:28`) | **Prerequisite fixed.** The enforcement mechanism (column standard + RLS + CI check) is delivered by W-07. The actual HRMS entities are ported by `W-13` onwards. BUG-002 is fully closed only when every table in `core`, `hrms`, and `payroll` exists and passes the CI check |
| `DEBT-018` — Missing composite index on tenant_id plus lookup columns (`legacy/docs/GAP_INVENTORY.md:142`) | **Pattern established.** `V001__tenant.sql` adds composite index `(tenant_id, id)` and documents the composite indexing standard in `migration/README.md` |

---

## 8. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `current_setting('app.current_tenant_id', true)` returns NULL on a connection that was not SET LOCAL'd — silent empty result instead of error | High — by design | Documented in §3a as the intended fail-safe. Break 3 of §4 proves it. Application filter (future ticket) must always call SET LOCAL |
| CI grep for `CREATE TABLE` misses multi-line DDL | Low | Scripts are reviewed like code. CI is the first line; review the second |
| A future script passes CI by naming the column `tenant_uuid` instead of `tenant_id` | Low | The grep is literal. Any deviation fails it. The README standard names the column |
| `migration_user` accidentally used in application — bypasses RLS | Low | W-06 CI gate already blocks `migration_user` credential in `app`/`worker` (W-06 §12 item 16) |

---

## 9. Rollback

Nothing is in production. Rollback is `git revert` of the pull request.

| Left behind by a revert | How to clear it |
|---|---|
| `core.tenant` table, indexes, RLS policy in a developer's local volume | `docker compose -f infra/docker/compose.yml down -v && up -d`. With scripts reverted, Flyway applies nothing |
| Testcontainer state | None — disposable per run |
| `TenantContext` class in `shared` | Reverted to pre-W-07 state |
| CI check steps | Removed by the revert |

**After the first real row is inserted into `core.tenant`, rollback is no longer free.**

---

## 10. Done when

| # | Criterion | Matching Verification Command |
|---|---|---|
| 1 | `./mvnw clean verify -pl migration,shared -am` is BUILD SUCCESS and `TenantIsolationIT` ran with tests > 0 | §6 Command 1 |
| 2 | `TenantContextTest` ran with tests > 0 | §6 Command 1 |
| 3 | `core.tenant` exists in Docker Postgres with `rowsecurity = t` | §6 Commands 2 & 3 |
| 4 | `app_user` with no SET LOCAL sees 0 rows from `core.tenant` | §6 Command 4 |
| 5 | `app_user` with `SET LOCAL app.current_tenant_id = '<uuid>'` sees only rows for that UUID | §6 Command 1 (`TenantIsolationIT`) |
| 6 | CI Step A fails on a script without `tenant_id` — break 1 reproduced, output linked from PR, branch not merged | §6 Break 1 test |
| 7 | CI Step B fails on an unqualified `CREATE TABLE` — break 2 reproduced, output linked from PR, branch not merged | §6 Break 2 test |
| 8 | CI Step A passes on the shipped `V001__tenant.sql` | §6 Command 5 |
| 9 | CI Step B passes on the shipped `V001__tenant.sql` | §6 Command 6 |
| 10 | `migration/README.md` carries both sections from §3a verbatim — `grep -q 'tenant_id column standard'` and `grep -q 'Row-level security'` return 0 | §6 README check |
| 11 | `git grep -n migration_user -- code/backend/app code/backend/worker` exits 1 | §6 Credential check |
| 12 | `ddl-auto` set nowhere; `spring.flyway` nowhere outside the `migration` module — existing CI gates still green | §6 CI check |

---

## Decisions needed before implementation

**D-1. RLS policy enforcement scope — Table Owner Bypass vs Forced RLS**

Should the RLS policy use standard `ENABLE ROW LEVEL SECURITY` (allowing table owner `migration_user` to bypass RLS for administrative DDL/seeds) or `FORCE ROW LEVEL SECURITY`?

```sql
-- Option A: Standard RLS (Recommended)
ALTER TABLE core.tenant ENABLE ROW LEVEL SECURITY;

-- Option B: Forced RLS for all users including table owner
ALTER TABLE core.tenant FORCE ROW LEVEL SECURITY;
```

**(a) Standard RLS (Recommended)** — `migration_user` bypasses RLS automatically (PostgreSQL table-owner default). Allows Flyway migrations to run DDL and seed data across tenants without needing `SET LOCAL` during migration runs. `app_user` and `readonly_user` remain strictly bound by RLS.

**(b) Forced RLS** — Forces RLS even on `migration_user`. Requires seed migrations to set `app.current_tenant_id`.

**Recommend (a)** — standard PostgreSQL isolation pattern.

---

**D-2. TenantContext wiring — filter vs DataSource wrapper**

`TenantContext.setForConnection(Connection)` executes `SET LOCAL app.current_tenant_id = ?`. `SET LOCAL` applies only for the duration of the current transaction. Two options:

**(a) Application filter / interceptor** — deferred to the first feature ticket with an authenticated endpoint (`W-08` or later). W-07 ships the class and utility method; wiring is a later ticket.

**(b) DataSource wrapper** — a `DataSource` decorator that intercepts every `getConnection()` call.

**Recommend (a) for W-07** (explicitly deferred) and **(b) as the long-term target.**

---

**D-3. FK from domain tables to core.tenant(tenant_id)**

When `W-13` creates `core.employee(tenant_id uuid NOT NULL)`, should it add:

**(a) A foreign key constraint** `REFERENCES core.tenant(tenant_id)` — database enforces every row belongs to a known tenant.

**(b) UUID convention only** — `tenant_id uuid NOT NULL`, no FK.

**Recommend (a)** — the FK is self-documenting and catches orphaned rows at insert time.

