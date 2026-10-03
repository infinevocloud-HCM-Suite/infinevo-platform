# Feature: Timesheet review lists

| Field | Value |
|---|---|
| **Feature ID** | `W-42.4` · ticket #54 · `HRMS-09` |
| **Promoted to** | `docs/target-state/features/W-42-4-timesheet-review-lists.md` |
| **Owner** | devashis |
| **Apps touched** | `code/backend/hrms` |
| **Related gaps** | BUG-002 (no new table), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-022 (fixed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | `W-42.1` (the tables). It does not need `W-42.3`: the tests set `SUBMITTED`, `APPROVED` and `REJECTED` rows directly |
| **Closes** | ticket #54 (`W-42`), with `W-42.1`–`W-42.3` |
| **Size** | S |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` | 1 |
| Flyway migration | none: the indexes needed came with `W-42.1` | 1 |
| Externally testable behaviour | a reviewer sees the submitted weeks they are entitled to, and no others | 1 |
| Frontend area | none (`W-48`) | 1 |

The founder split this out of `W-42.3` on 2026-10-02.

---

## 1. Problem

Legacy has three read-only review lists and two matching detail reads:

| Who | Legacy list | Legacy detail | Sees |
|---|---|---|---|
| Project manager | `GET /api/timesheets/manager/timesheets` (`TimesheetsController.java:288-326`) | `GET /manager/{id}` (`:328-388`) | weeks that touch their projects, **trimmed to their projects** (`TimesheetServiceImpl.java:433-443`) |
| Reporting manager | `GET /reporting-manager/timesheets` (`:510-550`) | — | the whole week of each direct report (`TimesheetServiceImpl.java:968-995`) |
| HR | `GET /non-drafts` (`:442-451`) | `GET /non-drafts/{id}` (`:453-508`) | every non-draft week |

All paths are under
`legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/controller/timesheet/`
and `serviceimpl/timeshhet/`.

These faults must not come across:

- **HR's list has no guard and no tenant.** `/non-drafts` checks no role and filters only
  `status <> DRAFT` (`:442-451`, `TimesheetServiceImpl.java:774-785`); BUG-002.
- **Filters run in memory.** The reporting manager's status and week filters load every
  timesheet, then filter the stream (`TimesheetsController.java:535-546`).
- **No paging** on any of the three.
- **Inconsistent draft rule.** The project-manager and reporting-manager lists drop `DRAFT`
  and `CANCELLED` (`TimesheetServiceImpl.java:426-429`, `:984-987`). HR's list drops only
  `DRAFT` (`:778`).

## 2. Scope

**In scope**

- Three paged lists and the matching detail reads, read-only, drafts never shown to anyone but the owner

**Out of scope**

- Deciding: `core`'s decide endpoint, `W-42.3`
- The list of employees with draft timesheets (`TimesheetsController.java:570-576`): reminders, `W-43`
- Export (`hrms.timesheet.export`, granted to `hr`): not in legacy's timesheet controllers, so no ticket yet
- Menu items for these lists: `W-48` owns the labels. This follows `W-41` §5 and `NavigationCatalogue.java:15-19`. **Record it in `W-48`.**
- Dashboards and counts: `W-44`

## 3. Flow

```
[project manager]   GET /timesheets/managed  → projects where manager_employee_id = me (W-41)
                                              → entries on them, status <> DRAFT → weeks, trimmed
[reporting manager] GET /timesheets/team     → core ReportingLineRepository.findDirectReports(me, today)
                                              → their weeks, status <> DRAFT, whole week
[hr]                GET /timesheets          → all weeks, status <> DRAFT
[any of them]       GET /timesheets/{id}     → W-42.1's read, widened by the same three rules
```

## 4. Backend changes

All under `code/backend/hrms/src/main/java/com/infinevo/hrms/timesheet/`.

| Layer | File | Change |
|---|---|---|
| Service | `TimesheetReviewService` + `Impl` | new; the three lists and the access rule for `GET /{id}` |
| Access | `TimesheetAccessResolver.java` | new; one place deciding, for a caller and a week, whether they see none, part or all of it. Copy the shape of `hrms/.../project/ProjectAccessResolver.java:28-112` |
| Repository | `TimesheetRepository` (`W-42.1`) | paged queries below, every one taking `tenantId`; the status filter goes into SQL |
| Controller | `TimesheetController.java` (`W-42.1`) | three `GET`s added; `GET /{id}` widened |

**The lookups it uses:**
- **Project manager:** `ProjectRepository.searchProjects(tenantId, all statuses, false, me, "%")` (`hrms/.../project/ProjectRepository.java:34-50`). Pass every status, because the query binds no nulls (`:28-33`).
- **Reporting manager:** `core`'s `ReportingLineRepository.findDirectReports(tenantId, me, today)` (`core/.../org/ReportingLineRepository.java:58`), as `core/.../leave/LeaveConsumptionController.java:90-93` uses it. Only direct reports and only the primary line, as legacy.
- **The caller:** `EmployeeService.currentEmployee()`. With no employee, `403`, as `W-42.1`.

**API contract.** Common query parameters, all optional:
- `from` and `to`: the week start falls between them, both inclusive.
- `status`: one of `SUBMITTED`, `APPROVED`, `REJECTED`. `DRAFT` is `400`.
- `page` and `size`, with `size` at most 100, as `ApprovalController.java:44-48`.
- Lists are sorted newest week first, then by employee id.

| Method | Path | Extra filters | Returns | Auth |
|---|---|---|---|---|
| GET | `/api/v1/hrms/timesheets/managed` | `projectId` (must be one they manage, else `400`) | weeks with at least one entry on a project the caller manages; **only those entries** in each week; the week's own status shown as is | `hrms.timesheet.approve` |
| GET | `/api/v1/hrms/timesheets/team` | `employeeId` (must be a direct report, else `400`) | the whole week of each direct report | `hrms.timesheet.read_team` |
| GET | `/api/v1/hrms/timesheets` | `employeeId`, `projectId` | every non-draft week in the tenant | `hrms.timesheet.read` |
| GET | `/api/v1/hrms/timesheets/{id}` | — | `W-42.1`'s read, now also: HR whole week; reporting manager of the owner whole week; project manager **only their entries**. A draft is the owner's only. Everyone else gets `404` | owner: `read_own`; others: as the list they would see it in |

All carry `@RequiresModule(PlatformModule.HRMS)`. The response shape is `W-42.1`'s
`TimesheetResponse` with `employeeId`. Names are not copied (`W-41` decision 6). The caller
resolves them with `GET /api/v1/employees`.

**One week, one row.** Fetch a page of week ids first, then load their entries in one query
per level. Never load per row. Legacy maps each timesheet separately
(`TimesheetServiceImpl.java:436-444`).

## 5. Frontend changes

None (`W-48`).

## 6. Database changes

**Creates no table and no migration.** `W-42.1`'s indexes serve every query:

| List | Index |
|---|---|
| managed | `idx_tpe_tenant_project_status (tenant_id, project_id, status)` |
| team | `uk_timesheet_tenant_employee_week (tenant_id, employee_id, week_start_date)` |
| HR | `idx_timesheet_tenant_status (tenant_id, status)` |

- [x] `tenant_id` + RLS: no new table
- [x] Index: covered above (DEBT-018)
- [x] No money
- [x] Expand / contract: no schema change

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `TimesheetAccessResolverTest.java` | owner sees a draft, nobody else does; HR sees all of a submitted week; reporting manager sees all of a direct report's week and none of an indirect report's; project manager sees only their project's entry of a two-project week; an unrelated manager sees nothing |
| Integration | `TimesheetReviewListIT.java` | seed one tenant: two projects with managers M1 and M2, employee E reporting to R, weeks in every status. `managed` for M1 returns only project A's entries; `team` for R returns E's non-draft weeks; HR returns every non-draft week; `status=DRAFT` is `400`; a `projectId` M1 does not manage is `400`; paging returns `size` rows and a total |
| Integration | `TimesheetReviewRlsIT.java` | tenant B's HR sees none of tenant A's weeks; the no-tenant connection sees zero rows |
| Integration | `TimesheetReviewPermissionIT.java` | an `employee` gets `403` on all three lists; R reading a non-report's week by id gets `404`; a Payroll-only tenant gets the module refusal |

Statuses are written straight into the rows in test setup, so this ticket does not wait on
`W-42.3`. Reuse `HrmsTestApp` and the `W-42.1` fixtures.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
cd code/backend && mvn -q verify
node .claude/scripts/check-done.mjs
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "EXPLAIN SELECT id FROM hrms.timesheet_project_entry
   WHERE tenant_id = gen_random_uuid() AND project_id = gen_random_uuid() AND status <> 'DRAFT';"
```

| Check | Expected |
|---|---|
| Suite | green, no skips |
| `check-done.mjs` | 5/5 |
| `EXPLAIN` | an index scan on `idx_tpe_tenant_project_status`, not a sequential scan, once the table has rows |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A project manager sees a colleague's hours on other projects | medium; the untrimmed week is the easy query | trim in the service; `TimesheetAccessResolverTest`, `TimesheetReviewListIT` |
| One query per week per level, slow at month end | medium; it is the legacy shape | §4 "one week, one row"; paged |
| A reporting manager sees indirect reports | low | `findDirectReports` only; test for an indirect report |
| HR list unguarded, as legacy | low | `hrms.timesheet.read`; `TimesheetReviewPermissionIT` |

## 10. Rollback

Nothing is deployed and there is no migration. Read-only endpoints; removing them changes
no data.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | creates no table |
| Flyway only, `ddl-auto` nowhere | no migration |
| `Money`/`BigDecimal` for money | no money |
| Index on `tenant_id` plus lookup columns | `W-42.1`'s three indexes, mapped in §6 |
| Expand / contract | no schema change |
| No module references another module | `hrms` uses `core` (`ReportingLineRepository`, `EmployeeService`) and `shared` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 | no new table; RLS from `W-42.1` covers every row read |
| DEBT-007 | **Fixed**: `/api/v1` |
| DEBT-008 | **Fixed**: one envelope, paged |
| DEBT-022 | **Fixed**: every finder takes `tenantId`; filters in SQL, not in memory |

## 13. Decisions — 2026-10-02

| # | Question | Answer |
|---|---|---|
| 1 | Split the lists from submit and approve | **Yes** (founder) |
| 2 | Project manager sees the whole week or only their projects? | **Only their projects**, as legacy (`TimesheetServiceImpl.java:433-443`) (spec's choice) |
| 3 | Reporting manager: direct reports or the whole chain below? | **Direct reports only**, as legacy (`:972-976`) (spec's choice) |
| 4 | Does anyone but the owner see drafts? | **No.** HR in legacy saw `CANCELLED` too; there is no `CANCELLED` row in the new model (`W-42.1` decision 3) (spec's choice) |
| 5 | Menu items for the lists | **None here.** `W-48` adds them with their labels (spec's choice, as `W-41` §5) |

## 14. As built (devashish, 2026-10-02)

Where the build differs from, or settles a point left open in, the sections above:

- The three lists answer `{"status", "message", "data": {"content", "page", "size", "total_elements", "total_pages"}}` (`TimesheetPage`), snake_case like the rest of the timesheet API.
- `size` above 100 is cut to 100, as `ApprovalController` does; a negative `page` or a `size` under 1 is `400`. `DRAFT` and `CANCELLED` are `400` on `status`.
- Optional filters are a flag plus an always-bound value, as `ProjectRepository` does, because an untyped null does not bind on PostgreSQL. The week ids come first, in a page; the weeks load in one query that brings their project lines, and tasks and days load in batches of 50.
- `GET /{id}` accepts any of `read_own`, `read_team`, `approve` and `read` at the door (`@RequiresAction(anyOf=...)`), then `TimesheetAccessResolver` decides what the caller sees; a week they see none of is `404`. The owner needs `read_own` to see their own draft: `approve` alone does not open it.
- A project manager's trimmed week also drops any line still in `DRAFT`. The week's own status and dates are shown as stored.
- `HrmsTestApp` gains a mocked `ReportingLineRepository`, because the test application does not load core's reporting lines; the tests say who a manager's direct reports are by stubbing `findDirectReports`.
- `HrmsNavigationTest` now also accepts a bare `@GetMapping`, which HR's list is.
- No menu items, as the spec says. **`W-48` owns the three labels** and must add them with their endpoints: `/api/v1/hrms/timesheets/managed` (`hrms.timesheet.approve`), `/team` (`read_team`) and `/api/v1/hrms/timesheets` (`read`).
