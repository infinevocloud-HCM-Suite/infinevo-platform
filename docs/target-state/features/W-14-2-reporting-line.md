# Feature: Reporting line and org chart

| Field | Value |
|---|---|
| **Feature ID** | `W-14.2` · from ticket #15 · `CORE-06` |
| **Promoted to** | `docs/target-state/features/W-14-2-reporting-line.md` on branch `W-14-2-reporting-line` — **`W-14-2` with hyphens**, never `W-14.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-14.1`, `W-11.3` (the `core.reporting_line.manage` code) |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §3 (`chainAbove` takes `asOf`) and §4 (permission codes) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one script, one table — `core.reporting_line` | 1 |
| Externally testable behaviour | a manager can be resolved for any employee, and a cycle is refused | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

The hierarchy exists in HRMS as six nullable strings on a row, with nothing checking any of
them.

- `Report.java` holds `reportingManagerId`, `reportingManagerName`, `indirectManager` and three approver levels — all `String`, all nullable
- **Nothing validates that a reporting manager is a real employee.** The id arrives from a DTO and is stored
- `reportingManagerName` is denormalised beside the id, so it goes stale on the first rename
- Traversal exists but is one level deep: `getEmployeeIdsByReportingManager()`, called from `LeaveRequestServiceImpl.java:807`. There is no chain walk and **no cycle detection**
- Payroll has no hierarchy at all

So a manager id can point at nobody, two employees can report to each other, and neither
condition is detected. `02-data-model.md:296` makes `reporting_line` a **new build**, and
`09-build-order.md:187` sets the bar: *"a manager can be resolved for any employee."*

This is also where the `W-13` split decision lands. Approving that split settled that
`Report.java`'s three approver levels come **here**, not to `core.employee_employment`, and
that `02-data-model.md:73` is wrong to merge them there. That correction is `sync-docs` work
and is flagged, not done here.

## 2. Scope

**In scope**

- `core.reporting_line` — who reports to whom, effective-dated
- Validation: the manager is an employee of the same tenant, active, and not the employee
- Cycle detection on write
- Chain resolution: manager, manager's manager, up to the top
- The org-chart read model: an employee's direct reports and their subtree
- The approver levels inherited from the `W-13` decision

**Out of scope**

- Approval routing — `W-15.2` consumes this and owns the routing
- Delegation when a manager is away — `W-15.3`
- Any UI or chart rendering
- Converting HRMS's existing manager strings — `W-67`

## 3. Flow

```
[tenant admin] --> [ReportingLineController] --> [ReportingLineService]
   --> validate manager exists, is active, no cycle
   --> [core.reporting_line under RLS]

[W-15.2, later] --> [ReportingLineService.chainAbove(employee, asOf)] --> ordered managers
```

`chainAbove(UUID employeeId, LocalDate asOf)` is the seam `12-core-contracts.md:91` names.
`asOf` is required, not defaulted: an approval that started in March must resolve March's
manager, which is the reason the table is effective-dated.

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../org/ReportingLineController.java` | new |
| Service | `core/.../org/ReportingLineService.java` | new |
| Service | `core/.../org/OrgChartService.java` | new — direct reports and subtree |
| Entity | `core/.../org/ReportingLine.java` | new, `@Table(schema="core")` |
| Repository | `core/.../org/ReportingLineRepository.java` | new |
| Enumeration | `core/.../org/ReportingLineKind.java` | new — primary, indirect, approver level 1/2/3 |
| DTO | `core/.../org/ReportingLine*.java`, `OrgChartNodeResponse.java` | new |

**API contract**

| Method | Path | Request | Response | `@RequiresAction` |
|---|---|---|---|---|
| PUT | `/api/v1/employees/{id}/reporting-line` | managerId, kind, effectiveFrom | `200` | `core.reporting_line.manage` |
| GET | `/api/v1/employees/{id}/reporting-line` | `?asOf=` | the lines in force | `core.org.read` |
| GET | `/api/v1/employees/{id}/manager-chain` | `?asOf=` | ordered, top-most last | `core.org.read` |
| GET | `/api/v1/org-chart` | `?rootEmployeeId=&depth=` | tree | `core.org.read` |

All Bearer, tenant bound. Codes per `12-core-contracts.md:58`; `core.reporting_line.manage`
arrives with `W-11.3` (`12-core-contracts.md:128`), `core.org.read` is already built
(`12-core-contracts.md:57`). `EndpointGuardCoverageTest` fails a controller method without a
code (`12-core-contracts.md:45-46`).

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__reporting_line.sql` | `core.reporting_line` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`manager_id uuid NOT NULL REFERENCES core.employee(id)` ·
`kind varchar(24) NOT NULL` · `effective_from date NOT NULL` · `effective_to date NULL` ·
four audit columns.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, employee_id, kind, effective_from DESC)`, `(tenant_id, manager_id)` for the reverse lookup
- [x] Money columns — none
- [x] Expand / contract — new table only

**One row per relationship, not six columns on the employee.** `Report.java`'s six string
columns cannot express a change of manager over time, and the effective dates are what let a
leave approved last March still show who approved it.

**No `manager_name` column.** The frozen row denormalises it and it goes stale —
`Report.java` carries `reportingManagerName` beside the id.

A check constraint refuses `employee_id = manager_id`. Longer cycles cannot be expressed as a
constraint and are refused in the service, with a test that walks the chain.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../org/ReportingLineServiceTest.java` | self-management refused; a two-step cycle refused; a three-step cycle refused; a diamond is allowed |
| Unit | `core/.../org/OrgChartServiceTest.java` | subtree depth honoured; an employee with no reports returns an empty list, not an error |
| Integration | `core/.../org/ReportingLineRlsIT.java` | a manager in another tenant is refused, as `app_user` |
| Integration | `core/.../org/ManagerChainIT.java` | a five-deep chain resolves in order; a manager change with effective dates gives different answers for different `asOf` values — asserted through `chainAbove(employee, asOf)` directly, as `W-15.2` will call it |
| Integration | `core/.../org/ReportingLineGuardIT.java` | `PUT` without `core.reporting_line.manage` is `403`; `GET` with `core.org.read` alone is `200` |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

The three-step cycle case is the one the frozen system would fail: nothing there detects a
cycle at any depth.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.reporting_line'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT conname FROM pg_constraint WHERE conrelid='core.reporting_line'::regclass AND contype='c';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name FROM information_schema.columns
    WHERE table_schema='core' AND table_name='reporting_line' AND column_name='manager_name';"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Check constraint | present — self-management refused at the database |
| `manager_name` | **no rows** — the column must not exist |
| `ReportingLineGuardIT` | green — every endpoint carries a `core.*` code |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A cycle is created and chain resolution loops forever | **high without detection — the frozen system has none** | Refused on write, and the chain walk has a depth bound as a second defence |
| A manager id pointing at nobody, as today | medium | Foreign key plus an active check in the service |
| The approver levels drift back to `employee_employment` because `02-data-model.md:73` still says so | **medium — the document currently contradicts this spec** | Flagged for `sync-docs`; the implementer follows the spec, not the stale line |
| Deep subtree queries are slow | low at this scale | `depth` is bounded and required; a recursive CTE with a depth limit, not an unbounded walk |
| Deactivating a manager orphans their reports silently | medium | Deactivation warns and lists the affected employees; it is not blocked, because a departure is a real event |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only —
`migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.reporting_line` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| No validation that a manager exists (`Report.java`) | **Fixed.** Foreign key plus an active check |
| No cycle detection anywhere | **Fixed.** Refused on write at every depth |
| Denormalised `reportingManagerName` | **Fixed.** The column does not exist |
| One-level traversal only (`LeaveRequestServiceImpl.java:807`) | **Fixed.** Full chain resolution |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Can an employee have more than one primary manager?** A matrix organisation says yes; payroll approval says pick one. **Recommend** exactly one `primary` in force at a time, with `indirect` unlimited — approval routing needs a single answer.
2. **What happens to approvals in flight when a manager changes?** **Recommend** an in-flight approval stays with the original approver, and only new requests route to the new manager — reassigning live approvals mid-flow is how an approval silently loses its audit trail.

## 14. Doc drift to correct

`02-data-model.md:73` merges HRMS `work` **and `report`** into `core.employee_employment`.
The approved `W-13` split sends `report`'s three approver levels here instead. The line needs
`sync-docs` when this ticket merges.
