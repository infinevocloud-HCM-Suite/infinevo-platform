# Feature: Audit retention sweep

| Field | Value |
|---|---|
| **Feature ID** | `W-22.2` · from ticket #26 · `CORE-14` |
| **Promoted to** | `docs/target-state/features/W-22-2-audit-retention.md` on branch `W-22-2-audit-retention` — **`W-22-2` with hyphens**, never `W-22.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/worker`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-22.1` — there must be an audit log to retain |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `worker` | 1 |
| Flyway migration | one script, one table — `core.retention_run` | 1 |
| Externally testable behaviour | rows older than the tenant's window are removed from both targets, and nothing newer is | 1 |
| Frontend area | none | 1 |

Within cap. This is the half of `W-22` that could not ride with capture: it lives in a
different module and has its own observable behaviour.

---

## 1. Problem

`W-22.1` writes an audit row for every change and never removes one.

- `core.audit_log` carries `old_values` and `new_values` as `jsonb`, so a row is large relative to the record it describes
- Every write to every audited table produces one. A payroll batch touching 5000 employees produces 5000
- Nothing in `docs/target-state/` states a retention window — the evidence pass confirmed no statement about retention, deletion or archival anywhere in `02-data-model.md`

Left alone, the table grows without bound and the first symptom is a slow query on the
busiest month of the year.

**Seven years** was settled by the founder on 2026-09-22. It has no citation in the design
because the design never named one; `sync-docs` records it in `02-data-model.md` when this
ticket merges.

## 2. Scope

**In scope**

- `core.retention_run` — a record of each sweep, so deletion is itself accountable
- A `@Scheduled` + `@SchedulerLock` job on `worker` — using `W-52`'s ShedLock — deleting rows past the tenant's window from **two targets**: `core.audit_log` and `core.notification` (decision 4)
- A per-tenant window, defaulting to seven years
- Batched deletion that can be interrupted and resumed
- A dry-run mode reporting what would be deleted

**Out of scope**

- Archiving to cold storage before deletion — see decision 2
- Retention for anything but `core.audit_log` and `core.notification`. Documents, payslips and leave records have their own statutory windows and are not swept by this job
- The capture mechanism — `W-22.1`
- The scheduler infrastructure itself — `W-20.2` builds it; this job registers with it

## 3. Flow

```
[scheduler, W-20.2] --> [worker: AuditRetentionJob]
   --> for each tenant: window = tenant setting or 7 years
   --> delete from core.audit_log where occurred_at < cutoff, in batches
   --> [core.retention_run] one row per sweep, per tenant
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Job | `worker/.../retention/AuditRetentionJob.java` | new |
| Service | `worker/.../retention/AuditRetentionService.java` | new |
| Entity | `worker/.../retention/RetentionRun.java` | new, `@Table(schema="core")` |
| Repository | `worker/.../retention/RetentionRunRepository.java` | new |
| Config | `worker/src/main/resources/application.yml` | change — batch size, default window |

Everything lands in `worker`. The `app` container must not run this job: two replicas would
sweep the same rows, and `D-48` selects the role by `INFINEVO_ROLE`.

**No API.** A sweep is triggered by the scheduler or by an operator running the job, not by
an HTTP call from a tenant.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__retention_run.sql` | `core.retention_run` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` · `target_table varchar(64) NOT NULL` ·
`cutoff_date date NOT NULL` · `rows_deleted bigint NOT NULL DEFAULT 0` ·
`is_dry_run boolean NOT NULL DEFAULT false` · `status varchar(16) NOT NULL` ·
`started_at timestamptz NOT NULL` · `finished_at timestamptz NULL` ·
four audit columns.

The tenant's windows are columns on `core.tenant`, added here as
`audit_retention_months int NOT NULL DEFAULT 84` and
`notification_retention_months int NOT NULL DEFAULT 12` — a notification has no statutory
value after a year, so it is swept far sooner than an audit row — nullable-then-backfilled is unnecessary
because the default is the policy. That is an `ALTER` on an existing table, so it is a second
statement in the same script, not a second table.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, started_at DESC)`; and `W-22.1`'s `(tenant_id, occurred_at DESC)` is what makes the delete efficient
- [x] Money columns — none
- [x] Expand / contract — a new table and an additive column with a default; no destructive step

**`core.retention_run` is itself never swept.** A record of deletion that gets deleted is
worth nothing.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

**The job connects as a new `retention_user`, settled by the founder on 2026-09-22.**
`W-22.1` grants `app_user` only `SELECT, INSERT` on `core.audit_log`, and the alternative was
to add `DELETE` to it — which would have let the application remove any audit row, defeating
most of what the audit trail protects against.

So this ticket adds a **fourth database role**:

| Role | Grants |
|---|---|
| `retention_user` | `SELECT, DELETE` on `core.audit_log` and `core.notification` only. Not superuser, not `BYPASSRLS`, no `UPDATE`, no access to any other table |

That touches `infra/postgres/` — the three SQL scripts and `provision.sh` that local Docker,
Testcontainers and Azure all run, which `W-05` made canonical — and `DatabasePrivilegesIT` in
`shared`, which asserts the role matrix in both directions and now has a fourth row to assert.
`app_user` keeps `SELECT, INSERT` and gains nothing.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `worker/.../retention/AuditRetentionServiceTest.java` | cutoff arithmetic for 84 months; a tenant override is honoured; a dry run deletes nothing |
| Integration | `worker/.../retention/AuditRetentionIT.java` | with rows at 6 years and 8 years old, only the 8-year rows go |
| Integration | `worker/.../retention/AuditRetentionBatchIT.java` | an interrupted sweep resumes and does not double-count `rows_deleted` |
| Integration | `worker/.../retention/AuditRetentionRlsIT.java` | a sweep bound to tenant A deletes no row belonging to tenant B |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`AuditRetentionRlsIT` is not a formality. A sweep runs without a request behind it, so if the
job forgets to bind a tenant the RLS policy maps to `NULL` and it deletes nothing — which is
the safe direction, and the test proves it stays that way rather than being "fixed" by
binding a superuser.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.retention_run'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, column_default FROM information_schema.columns
    WHERE table_schema='core' AND table_name='tenant' AND column_name='audit_retention_months';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT grantee, privilege_type FROM information_schema.table_privileges
    WHERE table_name='audit_log' AND grantee IN ('app_user','retention_user') ORDER BY 1,2;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname='retention_user';"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on `core.retention_run` | `t` |
| `audit_retention_months` default | `84` |
| `app_user` on `audit_log` | `INSERT`, `SELECT` — **no `DELETE`, no `UPDATE`** |
| `retention_user` on `audit_log` | `DELETE`, `SELECT` — **no `UPDATE`** |
| `retention_user` role flags | `f`, `f` — not superuser, not `BYPASSRLS` |
| Suite | green, no skips |

`UPDATE` staying absent for both is the point. Deleting an expired row is retention; editing
one is tampering, and nothing in the platform may do it.

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Application code removes inconvenient audit rows | low, now | **Settled** — `app_user` never gains `DELETE`; only `retention_user` holds it, and only on `core.audit_log` |
| The `retention_user` credential is reused by `app` for convenience | medium | It is a separate secret in Key Vault (`W-56`), injected only into the `worker` role selected by `INFINEVO_ROLE` (`D-48`) |
| A cutoff bug deletes recent rows and there is no way back | medium, irreversible | Dry run first, `rows_deleted` recorded, and the integration test asserts the 6-year rows survive |
| The sweep locks the table during a payroll run | medium | Batched deletes with a bounded batch size, scheduled outside business hours |
| Two workers sweep at once and double-count | medium | `@SchedulerLock` from `W-52`; `core.retention_run` records each sweep so a double run would be visible |
| A tenant sets a window shorter than a statutory requirement | medium | A floor on the setting — see decision 3 |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only —
`migration/README.md:135-143`. **Deleted audit rows cannot be recovered**, which is why the
dry run exists and why the first production sweep should be run with it.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.retention_run` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | one new index, `tenant_id` leading |
| Expand / contract | new table plus an additive column with a default |
| No module references another module | `worker` and `core` only; `hrms` and `payroll` untouched |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-018 missing tenant indexes | **Honoured** |
| Unbounded audit growth introduced by `W-22.1` | **Fixed.** That is the whole ticket |

## 13. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | Who may delete expired audit rows? | **A dedicated `retention_user` role** — settled 2026-09-22. `app_user` keeps `SELECT, INSERT` and never gains `DELETE` |
| 2 | Archive before deleting? | **No archive** — settled 2026-09-23. Deletion is final, and §10 says so plainly |
| 3 | Is there a floor on the tenant's window? | **Twelve months minimum** — settled 2026-09-23, so nobody disables their own audit trail by setting retention to zero |
| 4 | Does `W-20.1`'s notification table get swept too? | **Yes** — settled 2026-09-22. `core.notification` is a second target of this sweep rather than a second sweep of its own |
