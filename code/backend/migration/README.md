# Migration scripts

This module contains Flyway migration scripts for the Infinevo platform database.
The migration runner (`MigrationApplication`) connects as `migration_user`, applies
the scripts, and exits — non-zero on failure.

---

## Script conventions

### Naming

`V<NNN>__<slug>.sql` — three-digit version, double underscore, lowercase slug.

Examples: `V001__country.sql`, `V002__tenant.sql`

### One global sequence across all four directories

Scripts are numbered in a **single global sequence** across
`reference/`, `core/`, `hrms/`, `payroll/`. Within each migration batch,
number `reference` below `core` below the modules:

```
V001__country.sql       → reference/
V002__tenant.sql        → core/
V003__employee.sql      → hrms/
V004__payroll_period.sql → payroll/
```

A duplicate version number is a loud Flyway failure (break 3 in spec §8).
`V001__` in the shipped sequence is unclaimed — `W-07` or `W-09` take it.

### Always name the schema

Every statement names its schema. No exceptions, including indexes and constraints.

```sql
CREATE TABLE core.employee (...)           -- yes
CREATE INDEX ON core.employee (tenant_id)  -- yes
CREATE TABLE employee (...)                -- NO
```

**Why this is not a style rule.** Flyway connects with a search path, so an unqualified
`CREATE TABLE` does not fail — it succeeds, in the wrong schema. The table then exists
outside `core`, `hrms` and `payroll`, which is where `W-07`'s tenant check looks. So it
never gets `tenant_id`, never gets a row-level security policy, and nothing reports it.
A missing `core.` is the one typo in this directory that is silent and permanent.

**Reviewing a migration script?** Read the first word after `CREATE`, `ALTER` or
`DROP`. If it has no dot in it, stop and ask.

### tenant_id on every table outside reference

Every table in `core`, `hrms`, and `payroll` must carry a `tenant_id uuid NOT NULL`
column as the **leading index column**. `reference` is the one exception — it holds
national data shared across all tenants, by design (`D-08`, `02-data-model.md:16`).

```sql
-- yes
CREATE TABLE core.employee (
    id        bigserial PRIMARY KEY,
    tenant_id uuid      NOT NULL,
    ...
);
CREATE INDEX ON core.employee (tenant_id, id);

-- no - table in core/hrms/payroll with no tenant_id
```

### Index on tenant_id plus lookup columns (DEBT-018)

Every table in `core`, `hrms`, `payroll` must have a composite index with `tenant_id`
as the leading column, followed by the columns used in typical WHERE clauses
(`02-data-model.md:363-372`).

### Row-level security

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

### One table creation per Flyway migration script

Every Flyway migration script under `db/migration/` creates at most one table. (Multi-line DDL within a single script is evaluated as 1 table per script).

### Money columns

Use `numeric(19,4)` or a named domain. Never `float`, `double`, or `real`.

### Forward-only — no destructive steps

Once a script is merged to `main`, it must **never be edited** (`03-code-structure.md:141`).
Schema evolution follows expand/contract patterns: add nullable columns or new tables,
backfill, then drop the old structure in a later release
(`05-azure-architecture.md:130-135`).

The previous release must still run against the new schema — a merged migration
cannot be rolled back by reverting its commit.

### Flyway must never run inside app or worker

`spring.flyway.*` config must not appear in `code/backend/app` or
`code/backend/worker`. A CI gate enforces this (`ci.yml` — "spring.flyway confined
to migration module"). Putting owner credentials in the application container
dissolves the security boundary (`02-data-model.md:383`).

---

## History table

Flyway tracks applied scripts in `migration.flyway_schema_history`, owned by
`migration_user`. `app_user` and `readonly_user` have no `USAGE` on the
`migration` schema and cannot query this table.

---

## Local development

```bash
# Run migrations against the local Compose stack
docker compose -f infra/docker/compose.yml up migrate

# Re-running is safe — Flyway skips already-applied scripts
docker compose -f infra/docker/compose.yml up migrate
```

See `infra/docker/compose.yml` for the `migrate` service definition and
`infra/docker/smoke.sh` for the post-run verification checks.
