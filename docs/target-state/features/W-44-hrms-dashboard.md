# Feature: HRMS dashboard — employee and manager views, read-only

| Field | Value |
|---|---|
| **Feature ID** | `W-44` · from ticket #56 · `HRMS-11` |
| **Owner** | *(unassigned)* |
| **Apps touched** | `code/backend/hrms` only |
| **Related gaps** | DEBT-007 (fixed), DEBT-008 (fixed), BUG-002 (fixed for new code), DEBT-018 (no new index needed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-03 |
| **Blocked by** | nothing — `W-40.3` clock, `W-41` projects and tasks, `W-42.1`–`.4` timesheets are on `main` `2780d098` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` | 1 |
| Flyway migration | **none** — `02-data-model.md:332`: `HRMS-11` owns no tables | 1 |
| Externally testable behaviour | a caller opens the dashboard and every figure matches the clock, project, task and timesheet tables for them | 1 |
| Frontend area | none — `W-48` builds the screen against §4 | 1 |

Within cap.

---

## 1. Problem

The frozen system has dashboards, built loosely. All citations are `legacy/HRMS_Backend/.../controller/`.

- **Three endpoints, three shapes, no envelope.** `/dashboard/manager` (`ManagerDashboardController.java:25,37`),
  `/employee-dashboard/dashboard-data` (`EmployeeDashboardController.java:24,51`), `/hrdashboard/*`
  (`HrDashboardController.java:22-130`). Each returns a hand-built `Map` — DEBT-007, DEBT-008.
- **The manager view sends whole lists, not figures.** Every project with its team, every task, every
  timesheet not yet approved or rejected (`ManagerDashboardController.java:45-91`). The screen counts them.
- **The manager sees drafts.** "Pending" is anything not `APPROVED` or `REJECTED` (`:86-88`), so a team member's
  unsubmitted draft shows as waiting for the manager.
- **The employee view sends their whole attendance history** (`EmployeeDashboardController.java:63`) and only the
  first project and task of each timesheet (`:85-100`).
- The roles behind the six legacy dashboards (`legacy/HRMS_Frontend/src/App.jsx:80-97`) do not exist here.
  Supervisor and reporting manager are not roles (`code/backend/migration/.../core/V022__role_action.sql:73-80`);
  what a caller sees follows the actions they hold.

## 2. Scope

**In scope**

- One read-only endpoint returning the dashboard for the caller, in the bound tenant
- **`me`** — the caller's own figures: today's clock, this and last week's timesheet, their projects, their tasks
- **`team`** — the manager's figures: the projects they manage, entries waiting for their approval, their direct
  reports' clock and timesheet state
- Each block appears only when the caller holds the action that guards the same data elsewhere

**Out of scope**

- Any table, any write, any cache
- The screen and its menu item — `W-48`
- Leave balance (legacy `EmployeeDashboardController.java:72`) — `core` data, already on the `/me` leave panel
  (`code/backend/core/.../portal/LeavePanelProvider.java:25`)
- The HR view (head count, leave, holidays — `HrDashboardController.java:37-130`) — `core` data, not `HRMS-11`
- Regularization and overtime counts — `W-40.4` and `W-40.6` are not built; each adds its own block when it lands
- Notifications (`HrDashboardController.java:129`)

## 3. Flow

```
[employee | manager] --> GET /api/v1/hrms/dashboard --> any of the actions in §4
   tenant = TenantContext; caller = EmployeeService.currentEmployee() — none is 403, never 500
   today  = TenantClock.today()            (core/.../tenant/TenantClock.java:60)
   week   = Monday of today                (TimesheetRules.java:34)

   me.today       [hrms.attendance.mark]    the caller's sessions today: clocked in now?, worked minutes
                                            (ClockService.today(), TodayResponse.java)
   me.timesheets  [hrms.timesheet.read_own] this week and last week: status (null = none), hours entered;
                                            CANCELLED counts as none
   me.projects    [hrms.project.read_own]   live assignments to live STARTED projects: count, first 5 by end_date
   me.tasks       [hrms.project.read_own]   live tasks assigned to the caller, not COMPLETED:
                                            count by status, overdue (due_date < today), due this week, next 5 by due_date

   team.projects  [hrms.project.read_team]  live projects the caller manages (managedProjectIds, TimesheetAccessResolver.java:120):
                                            count by status; STARTED ones, first 5 by end_date, each with
                                            team size, open tasks, overdue tasks
   team.approvals [hrms.timesheet.approve]  timesheet_project_entry rows SUBMITTED on those projects:
                                            count, oldest 5 by submitted_at with employee name, project, week
   team.reports   [hrms.timesheet.read_team] direct reports today (directReportIds, TimesheetAccessResolver.java:112):
                                            count, clocked in today, late for last week
                                            (TimesheetLateQuery.lateEmployees, filtered to the reports)
```

A block the caller cannot see is `null`, not absent and not `403`. `team` is `null` when all three of its blocks
are. Every query is bound to `tenant_id` and returns counts or at most 5 rows; nothing is loaded per row and nothing
is filtered in Java after a whole-table read.

## 4. Backend changes

All new under `code/backend/hrms/src/main/java/com/infinevo/hrms/dashboard/`.

| Layer | File | Change |
|---|---|---|
| Controller | `HrmsDashboardController.java` | new — one `GET`, `@RequiresModule(HRMS)`, `@RequiresAction("hrms.project.read_own", anyOf = {"hrms.timesheet.read_own", "hrms.project.read_team", "hrms.timesheet.read_team", "hrms.timesheet.approve"})` (`shared/.../authz/RequiresAction.java`) |
| Service / ServiceImpl | `HrmsDashboardService`, `HrmsDashboardServiceImpl` | new — `forCaller()`; `@Transactional(readOnly = true)`; decides each block with `PermissionService.holds` |
| Repository | `DashboardQueryRepository.java` | new — the aggregate JPQL: task counts by status, overdue, due-this-week, project counts, entries waiting, clocked-in reports. **No new finder on `W-40`, `W-41`, `W-42`'s repositories** so no ticket collides |
| DTO | `HrmsDashboardResponse` with `Me`, `Team` and their block records | new — in `ApiResponse` (`hrms/.../project/ApiResponse.java`) |

Reused as they are: `ClockService.today()`, `TimesheetAccessResolver.managedProjectIds` / `directReportIds`,
`TimesheetLateQuery.lateEmployees`, `TenantClock.today()`, `TimesheetRules` week start.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/hrms/dashboard` | none | `200` `HrmsDashboardResponse` | any of the five actions above; HRMS module on |

`HrmsDashboardResponse`:

| Field | Shape |
|---|---|
| `as_of` | `"2026-10-05"` — the tenant's today |
| `me.today` | `{clocked_in, clocked_in_at, worked_minutes}` |
| `me.timesheets` | `{this_week: Week, last_week: Week}`; `Week` = `{week_start, timesheet_id, status, hours}` |
| `me.projects` | `{active, items: [{project_id, name, status, progress, end_date}]}` |
| `me.tasks` | `{open, by_status: {TODO, IN_PROGRESS, IN_REVIEW}, overdue, due_this_week, next: [{task_id, project_id, project_name, title, status, priority, due_date}]}` |
| `team.projects` | `{managed, by_status: {STARTED, COMPLETED}, items: [{project_id, name, progress, end_date, team_size, open_tasks, overdue_tasks}]}` |
| `team.approvals` | `{waiting, oldest: [{timesheet_id, project_entry_id, employee_id, employee_name, project_name, week_start, submitted_at}]}` |
| `team.reports` | `{reports, clocked_in_today, late_last_week, late: [{employee_id, name}]}` (at most 5 in `late`) |

`hours` is `BigDecimal` scale 2, the sum of `timesheet_day_entry.hours` (`numeric(4,2)`, `TimesheetDayEntry.java:30`).

Not ported: the three legacy calls as three; whole lists the screen counts; attendance history; the first
project and task per timesheet.

## 5. Frontend changes

None. `W-48` builds the screen and adds the `hrms.dashboard` menu item through `HrmsNavigation`
(`hrms/.../navigation/HrmsNavigation.java:17-28`) — this endpoint is the `GET` its catalogue check needs.

## 6. Database changes

None. Creates no table, no column, no index, no Flyway script.

- [x] no new table, so nothing to carry `tenant_id` or RLS; every read is bound to the tenant and runs under the
  existing `hrms.*` and `core.*` policies as `app_user`
- [x] no schema change, no Flyway script
- [x] `BigDecimal` for hours; no money; nothing floating
- [x] Index on `tenant_id` plus lookup columns (DEBT-018): reads use the `W-40.3`, `W-41` and `W-42.1` tenant
  indexes; add one only if a plan shows a sequential scan
- [x] expand / contract — nothing to sequence

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/.../dashboard/HrmsDashboardServiceTest.java` | each block is `null` without its action; `team` `null` when all three are; no employee record is `403`; `CANCELLED` week reads as none |
| Integration | `hrms/.../dashboard/HrmsDashboardIT.java` | **the acceptance test**: a manager of one project with two members; one member has a `SUBMITTED` entry, the other a `DRAFT`; one overdue task; one member clocked in today. The manager sees `team.approvals.waiting = 1` (the draft is not counted), `team.projects.items[0].overdue_tasks = 1`, `team.reports.clocked_in_today = 1`. The member sees their own `me` with `team = null` |
| Integration | same file | a second tenant's project, task and timesheet never count (`ProjectIsolationIT` pattern); a manager does not see a project they do not manage; HRMS module off is `403` |

## 8. Verification

```bash
cd code/backend && ./mvnw -q -pl hrms -am spotless:check test -Dtest='HrmsDashboardServiceTest'
cd code/backend && ./mvnw -q -pl hrms -am verify -Dit.test=HrmsDashboardIT
```

| Check | Expected | Result |
|---|---|---|
| unit and IT above | `BUILD SUCCESS`, the IT green | |
| `curl -s -H "Authorization: Bearer $EMPLOYEE" localhost:8080/api/v1/hrms/dashboard \| jq '.data.team'` | `null` | |
| same with `$MANAGER`, `jq '.data.team.approvals.waiting'` | a number, drafts excluded | |
| a token holding none of the five actions | `403` | |
| `check-done.mjs` | no new table, no `double`, no `ddl-auto` | |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `W-40.4` / `W-40.6` change `ClockService` or the attendance tables while this is built | medium — karma's next tickets | read only `ClockService.today()` and `clock_session`; read `main` at branch time |
| `directReportIds` uses the server's date, not the tenant's (`TimesheetAccessResolver.java:113`) | low — differs only near midnight | reuse it as is; the dashboard is not where to change it |
| `TimesheetLateQuery` runs for the whole tenant to count a few reports | low at current sizes | one query; filter by report ids in the query if a tenant passes a few thousand employees |

## 10. Rollback

Nothing is stored. Remove the package; no data changes.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | creates no table |
| Flyway only, `ddl-auto` nowhere | no script |
| `Money`/`BigDecimal` | hours `BigDecimal`; no money |
| Index on `tenant_id` plus lookup columns | no new lookup; existing indexes reused |
| Expand / contract | nothing to sequence |
| No module references another module | `hrms` reads `core` only — `EmployeeService`, `TenantClock`, `PermissionService`, and `Employee` / `ReportingLine` in JPQL as `TimesheetLateQueryImpl.java:41-48` already does |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-007 no `/api/v1` · DEBT-008 hand-built envelope | **Fixed** |
| BUG-002 no tenant column on HRMS tables | **Fixed for new code** — every read binds `tenant_id` |
| DEBT-018 no indexes | **No change needed** — existing tenant indexes cover the reads |

## 13. Decisions — settled 2026-10-03

| # | Question | Answer |
|---|---|---|
| 1 | One endpoint or one per role, as legacy? | **One**, shaped by actions. The legacy roles do not exist; a manager is also an employee and sees both blocks |
| 2 | Figures or lists? | **Figures plus at most five rows** per block. The full lists already exist: `W-41`'s project and task lists, `W-42.4`'s review lists |
| 3 | What is "waiting for me"? | **Project entries `SUBMITTED` on projects I manage.** Never a draft — legacy counted them (`ManagerDashboardController.java:86-88`) |
| 4 | Who is "late"? | **`TimesheetLateQuery`'s rule**, so the dashboard and the reminder mail never disagree |
| 5 | HR view? | **Not here.** Its figures are `core` data; a `core` dashboard is its own ticket if wanted |
