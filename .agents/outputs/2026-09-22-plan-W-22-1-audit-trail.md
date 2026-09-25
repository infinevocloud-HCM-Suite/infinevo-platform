# Feature: Audit trail — change capture and query

| Field | Value |
|---|---|
| **Feature ID** | `W-22.1` · ticket #26 · `CORE-14` |
| **Promoted to** | `docs/target-state/features/W-22-1-audit-trail.md` on branch `W-22-1-audit-trail` — **`W-22-1` with hyphens**, never `W-22.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | new `code/backend/shared`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured), BUG-002 (discounted) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |

## Size cap — this ticket splits

| Axis | `W-22` as ticketed | Limit |
|---|---|---|
| Backend module | `shared` for capture, **`worker`** for retention | 1 — **breaks** |
| Flyway migration | `V008__audit_log.sql` | 1 |
| Externally testable behaviour | capture + query · **retention sweep** | 1 — **breaks** |
| Frontend area | none | 1 |

Retention is a scheduled sweep in a different module with its own observable behaviour, so
it cannot ride with capture. **Proposed split:**

| New ticket | Scope | Size |
|---|---|---|
| **`W-22.1`** — this spec | `core.audit_log`, capture, query endpoint | M |
| **`W-22.2`** — to be raised | retention sweep in `worker`, driven by a per-tenant window | S |

Capture and query stay together on purpose: an audit row nobody can read cannot be tested,
so they are one behaviour, not two.

---

## 1. Problem

Neither frozen product has an audit trail, and the new platform has no mechanism either.

- No `@EntityListeners`, `AuditingEntityListener`, `@EnableJpaAuditing` or Hibernate Envers anywhere in `legacy/` — searched across both backends
- The only history tables are tax-specific — `legacy/Payroll-Bend-SBoot/.../taxCalculator/TaxSlabMasterHistory.java`, `.../statutorycomponents/PTHistory.java`, `.../statutorycomponents/TaxSlabDetailHistory.java`
- What exists instead is scattered and manual: Payroll sets `createdBy`/`updatedBy` on some entities — `legacy/Payroll-Bend-SBoot/.../entity/organization/Organization.java:67-79` — and HRMS carries timestamps but no actor on about fourteen entities — `legacy/HRMS_Backend/.../entity/Employee.java:54-58`
- No `@EnableJpaAuditing` exists under `code/backend/` today

Timing is the whole argument. `09-build-order.md:205` says build it early, because retrofitting
audit backfills nothing and loses the first months. `01-platform-shape.md:250-251` adds that a
Payroll-only tenant's loss of pay rests on an administrator keying absences with no approval
trail behind it, so the audit trail matters **more** in that tier, not less.

## 2. Scope

**In scope**

- `core.audit_log`, one table, tenant-scoped, RLS-protected
- Automatic capture of insert, update and delete on any entity that opts in, via a Hibernate post-commit listener
- The actor: Keycloak subject when a request is in flight, a named system actor otherwise
- A read endpoint filtered by entity, actor and time window
- Opting in `core.tenant` and `core.user_tenant`, the only business tables that exist today, as the proof

**Out of scope**

- Retention, archival and deletion — `W-22.2`, to be raised
- Any UI — reporting and export is `W-23` (#27)
- Opting in salary and leave tables; they do not exist yet, and `W-16` and `W-26` opt themselves in
- Reconstructing history before this ticket — there is none to reconstruct

## 3. Flow

```
[any write] --> [Hibernate post-commit listener] --> [AuditWriter]
   --> reads tenant from TenantContext, actor from SecurityContextHolder
   --> [core.audit_log]

[GET /api/v1/audit] --> [AuditQueryService] --> [core.audit_log, RLS-scoped]
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Config | `shared/.../audit/AuditIntegratorConfig.java` | new — registers the listener with Hibernate |
| Listener | `shared/.../audit/AuditEventListener.java` | new — `PostInsert`/`PostUpdate`/`PostDelete`, skips non-audited entities |
| Annotation | `shared/.../audit/Audited.java` | new — the opt-in marker |
| Service | `shared/.../audit/AuditWriter.java` | new — builds the row, resolves actor and tenant |
| Service | `shared/.../audit/AuditQueryService.java` | new |
| Controller | `shared/.../audit/AuditController.java` | new |
| Entity | `shared/.../audit/AuditLog.java` | new |
| Repository | `shared/.../audit/AuditLogRepository.java` | new |

**Why a Hibernate listener and not Envers.** Envers writes a shadow table per audited
entity. The data model allots `CORE-14` exactly one table — `02-data-model.md:305` — so
Envers would contradict the design on its first migration. A post-commit listener writes
every entity into the single `core.audit_log`.

**Post-commit, not pre-commit.** A rolled-back transaction must leave no audit row.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/audit` | `?entity=&entityId=&actor=&from=&to=&page=&size=` | page of audit rows | Bearer, tenant bound |

## 5. Frontend changes

None. No file under `code/frontend/` is touched.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V008__audit_log.sql` | `core.audit_log` | yes | additive only |

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, entity_table, entity_id, occurred_at DESC)` and `(tenant_id, occurred_at DESC)`
- [x] Money columns are `BigDecimal` — none; a changed money value is carried inside `jsonb` as a string, never as a float
- [x] Expand / contract — new table only

Columns: `id uuid` · `tenant_id uuid NOT NULL` · `occurred_at timestamptz NOT NULL` ·
`actor_user_id uuid NULL` · `actor_label varchar(100) NOT NULL` · `operation varchar(10) NOT NULL` ·
`entity_schema varchar(32) NOT NULL` · `entity_table varchar(64) NOT NULL` · `entity_id varchar(64) NOT NULL` ·
`changed_columns text[] NULL` · `old_values jsonb NULL` · `new_values jsonb NULL` · `trace_id varchar(36) NULL`.

`actor_user_id` is nullable and `actor_label` is not, so a system write is still attributable
when no human is behind it. RLS and the `tenant_isolation` policy in the exact `CASE` form go
in the same script — `migration/README.md:76-123`. One table, one script —
`migration/README.md:127-129`.

**`app_user` may insert but not update or delete.** The grant is `SELECT, INSERT` only, so an
audit row cannot be edited by the application that wrote it. This is the one table where that
distinction is the point.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `shared/.../audit/AuditWriterTest.java` | actor resolution: JWT subject, then system fallback; changed-column diffing |
| Unit | `shared/.../audit/AuditEventListenerTest.java` | an entity without `@Audited` produces no row |
| Integration | `shared/.../audit/AuditCaptureIT.java` | insert, update and delete of `core.tenant` each write exactly one row with the right operation and diff |
| Integration | `shared/.../audit/AuditRollbackIT.java` | a rolled-back transaction leaves zero rows |
| Integration | `shared/.../audit/AuditRlsIT.java` | tenant A cannot read tenant B's rows, and `app_user` is refused `UPDATE` and `DELETE` |

All integration tests extend `AbstractIntegrationTest` and carry `@EnabledIfDockerAvailable`
— `code/backend/shared/src/test/java/com/infinevo/shared/test/EnabledIfDockerAvailable.java`
— so they fail loudly rather than skipping the way #117 did.

## 8. Verification

```bash
# 1. Table, RLS and the grant matrix
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.audit_log'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT privilege_type FROM information_schema.table_privileges
    WHERE grantee='app_user' AND table_name='audit_log' ORDER BY 1;"

# 2. A write really produces a row
cd code/backend && mvn -q -pl shared -Dit.test=AuditCaptureIT verify

# 3. Whole suite
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| 1 — RLS | `t` |
| 1 — grants | `INSERT` and `SELECT` only; no `UPDATE`, no `DELETE` |
| 2 — capture | green; three rows for insert, update, delete |
| 3 — suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The listener doubles every write and payroll batches slow down | medium | Opt-in by `@Audited`, never blanket; measure on the first batch ticket that opts in |
| `TenantContext` is unbound on a worker write and the insert fails the RLS policy | **high** | `AuditWriter` requires a bound tenant and fails the write loudly; a job that writes without binding a tenant is already a defect under `W-08` |
| Actor is unavailable because `W-10` has not merged | medium | Resolve from `SecurityContextHolder` JWT subject if present, else `actor_label='system'`; no dependency on `core.user_account` |
| `old_values`/`new_values` capture a password or token column | low, but serious | A deny-list of column names, asserted by a unit test, redacted to `"***"` before the row is built |
| Unbounded growth — no retention in this ticket | certain | This is exactly why `W-22.2` must be raised with it, not later |

## 10. Rollback

Nothing is deployed. `V007` is additive and forward-only — `migration/README.md:135-143`.
To stop capture after merge, remove `@Audited` from the entities; the table and endpoint stay
and remain readable.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.audit_log` has both, in `V007` — and this rule explicitly covers audit tables |
| Flyway for every schema change, `ddl-auto` nowhere | one script; no `ddl-auto` added |
| `Money`/`BigDecimal` for money | no money column; money inside `jsonb` is serialised as a string, never a float |
| Index on `tenant_id` plus lookup columns | two composite indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | everything lands in `shared` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-018 missing tenant indexes | **Honoured.** Both indexes lead with `tenant_id` |
| BUG-002 HRMS entities carry no tenant scope | **Discounted.** No HRMS table is touched; opting them in is `W-13` onward |

## 13. Decisions — settled at approval, 2026-09-22

| # | Question | Answer |
|---|---|---|
| 1 | Retention window | **Seven years**, and `W-22.2` is raised alongside this ticket rather than later |
| 2 | Opt-in or blanket capture | **Opt-in**, per entity, via `@Audited` |
| 3 | Order against `W-10` | **`W-10` first.** The actor is then a real `user_account` row rather than a bare JWT subject |

Seven years has no citation in `docs/target-state/` because nothing there states a retention
window at all. It is a founder decision taken on 2026-09-22, and `sync-docs` should record it
in `02-data-model.md` when `W-22.2` merges.
