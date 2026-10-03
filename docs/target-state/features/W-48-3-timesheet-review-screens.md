# Feature: Timesheet review screens

| Field | Value |
|---|---|
| **Feature ID** | `W-48.3` · screens for `W-42.3`, `W-42.4` (ticket #68) · `HRMS-*` |
| **Promoted to** | `docs/target-state/features/W-48-3-timesheet-review-screens.md` — **hyphens**, never `W-48.3` |
| **Owner** | *(unassigned)* |
| **Apps touched** | `code/frontend/src/hrms/timesheetreview` (new), one line in `src/core/approvals/itemRoutes.js`; `code/backend/hrms` — names on timesheet responses, one menu item |
| **Related gaps** | none |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-03 |
| **Blocked by** | nothing — `W-42.3`, `W-42.4` on `main` `2780d098`; `W-46.4` inbox on `main` `11ec157` |
| **Size** | M |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` — additive name fields, one menu item | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | a project manager, a reporting manager and HR each see the submitted weeks they are entitled to, with names, and open one entry from the approvals inbox | 1 |
| Frontend area | `src/hrms/timesheetreview`, plus the one line `itemRoutes.js` reserves for it (`:3`, `:12`) | 1 |

---

## 1. Problem

`W-42.4` gives three review lists and `W-42.3` an entry read for the approver; no screen calls them. The inbox can
decide a timesheet step but cannot open it: `TIMESHEET: null` (`src/core/approvals/itemRoutes.js:12`).

The lists name nobody. `TimesheetResponse` carries `employee_id` and per project `project_id` only
(`hrms/.../timesheet/TimesheetResponse.java`); `TimesheetProjectEntryResponse.java:16,19` the same. A manager
cannot resolve the ids: the employee search needs `core.employee.read`
(`core/.../employee/EmployeeController.java:71-72`), which `manager` does not hold
(`migration/.../core/V139__hrms_request_seed_roles.sql:66-77`).

The frozen system has three role copies of the same list and detail, all MUI
(`legacy/HRMS_Frontend/src/components/managerDashboard/ManagerTimesheetManagement.jsx` 1,507 lines and
`ManagerTimesheetDetailView.jsx` 798; `reportingmanagerDashboard/ReportingManagerTimesheetManagement.jsx` 1,265 and
`…DetailView.jsx` 524; `adminDashboard/AdminTimesheetManagement.jsx` 1,605 and `TimesheetDetailView.jsx` 582).

## 2. Scope

**In scope**

- `hrms`: `employee_name` on timesheet and project-entry responses, `project_name` on project lines, `task_title`
  on task lines; menu item `hrms.timesheet_review`
- `/hrms/timesheet-review` — one page, a tab per list the caller may see: **My projects** (`/managed`),
  **My team** (`/team`), **All** (`/`); week range, status, project or employee filters; paged
- `/hrms/timesheet-review/:id` — one week, read-only, trimmed by the server to what the caller may see
- `/hrms/timesheet-review/entries/:entryId` — one project entry, opened from the inbox
- `itemRoutes.TIMESHEET` → the entry page

**Out of scope**

- Approve and reject — the inbox decides (`W-46.4`); the entry page links back to it
- Export (`hrms.timesheet.export`) — no endpoint
- The employee's own weeks — `W-48.2`

## 3. Flow

```
[manager | hr] /hrms/timesheet-review
   tab My projects [useCan hrms.timesheet.approve]   --> GET /v1/hrms/timesheets/managed?from=&to=&status=&projectId=&page=&size=
   tab My team     [useCan hrms.timesheet.read_team] --> GET /v1/hrms/timesheets/team?…&employeeId=
   tab All         [useCan hrms.timesheet.read]      --> GET /v1/hrms/timesheets?…&employeeId=&projectId=
   row             --> /hrms/timesheet-review/{id}   --> GET /v1/hrms/timesheets/{id}
[approver] /approvals → TIMESHEET row "Open"
   --> /hrms/timesheet-review/entries/{entryId}      --> GET /v1/hrms/timesheets/project-entries/{entryId}
```

Status filter offers `SUBMITTED`, `APPROVED`, `REJECTED` only: the server refuses `DRAFT`
(`TimesheetReviewServiceImpl.java`, `Filters.of`). Default view: `SUBMITTED`, last four weeks.

## 4. Backend changes

All in `code/backend/hrms`. Fields added only.

| Layer | File | Change |
|---|---|---|
| DTO | `timesheet/TimesheetResponse.java` | add `employee_name`; on each project line `project_name`; on each task line `task_title` |
| DTO | `timesheet/TimesheetProjectEntryResponse.java` | add `employee_name`, `project_name`, `task_title` per task |
| Service | `TimesheetReviewServiceImpl`, `TimesheetProjectEntryServiceImpl`, `TimesheetServiceImpl` | fill names with one batch read per page — employees through `core`'s `EmployeeService`, projects and tasks from `hrms`; never one read per row (the page already loads in fixed queries, `TimesheetReviewServiceImpl` class comment) |
| Navigation | `navigation/HrmsNavigation.java` | add `hrms.timesheet_review` → `/hrms/timesheet-review`, `GET /api/v1/hrms/timesheets/managed`, action `hrms.timesheet.approve` |

`hrms.timesheet.approve` is the menu action because `hr` and `manager` both hold it (`V139…sql:58,73`).

## 5. Frontend changes

`W-45` contract, as `src/payroll/priorpayroll`.

| File | Change |
|---|---|
| `src/hrms/timesheetreview/reviewService.js` | **new** — `managed(filters)`, `team(filters)`, `all(filters)`, `get(id)`, `entry(entryId)` |
| `src/hrms/timesheetreview/ReviewPage.jsx` | **new** — `Tabs`, one per list the caller may see (none: `NotEntitled`); shared filter bar (week range `RangePicker`, status `Select`, project or employee `Select` from the rows already loaded); `Table` (employee, week, total hours, status `Tag`, per-project status, submitted at), server paging |
| `src/hrms/timesheetreview/WeekView.jsx` | **new** — read-only grid: project → task rows, Mon–Sun columns, totals, rejection reasons |
| `src/hrms/timesheetreview/EntryView.jsx` | **new** — one project's tasks by day, totals, status, "Back to approvals" link to `/approvals` |
| `src/hrms/index.js` | `routes` gains the three paths |
| `src/core/approvals/itemRoutes.js` | `TIMESHEET: (itemId) => (itemId ? \`/hrms/timesheet-review/entries/${itemId}\` : null)` — the approval subject is the project entry (`TimesheetSubmitService.java:16`) |

Hours displayed as sent, summed in hundredths as in `W-48.2`.

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/hrms/timesheet-review` | `ReviewPage` | `AppShell`; feed carries `hrms.timesheet_review` |
| `/hrms/timesheet-review/:id` | `WeekView` | beneath it |
| `/hrms/timesheet-review/entries/:entryId` | `EntryView` | beneath it |

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `TimesheetReviewListIT.java` (extend) | each list carries `employee_name`, `project_name`, `task_title`; a manager's `/managed` row still trimmed to their projects |
| Integration | `TimesheetProjectEntryReadIT.java` (extend) | the entry carries the three names |
| Unit | `HrmsNavigationTest.java` (extend) | the new item's endpoint has a `GET` |
| Unit | `reviewService.test.js` | every function hits its path with its filters; unwraps `data` |
| Component | `ReviewPage.test.jsx` | tabs follow `useCan` (manager: My projects, My team; HR: My projects, All); status never offers `DRAFT`; paging sends `page` |
| Component | `EntryView.test.jsx` | renders tasks by day and the back link |
| Component | `itemRoutes.test.js` (extend) | `TIMESHEET` builds the entry path; null id gives null |

## 8. Verification

```bash
cd code/backend && ./mvnw -q -pl hrms -am spotless:check verify -Dtest='HrmsNavigationTest' -Dit.test='TimesheetReviewListIT,TimesheetProjectEntryReadIT'
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d    # browser: a project manager, a reporting manager, HR
```

| Check | Expected |
|---|---|
| backend, lint, tests | `BUILD SUCCESS`; clean |
| project manager | My projects lists submitted weeks on their projects with names; another project's hours are not shown |
| reporting manager | My team lists direct reports' weeks; no drafts |
| HR | All lists every submitted week |
| inbox | a `TIMESHEET` row has "Open" and lands on the entry page |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Names re-read per row | low | one batch read per page, as the list already loads its rows |
| A manager who is also HR sees the same week twice across tabs | certain, harmless | tabs are separate lists, as `W-42.4` defines them |

## 10. Rollback

Revert the branch. Fields are additive; no data changes.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | none |
| `Money` / `BigDecimal` | no money; hours exact |
| Index | none new |
| Expand / contract | fields added only |
| No module references another | `hrms` reads `core` only; `src/hrms` imports `@shared/*` and `@shell/screens`; `itemRoutes.js` holds a path string, no import |

## 12. Gap inventory

None.

## 13. Decisions — founder, 2026-10-03

| # | Question | Answer |
|---|---|---|
| 1 | Three role pages, as legacy? | **One page, a tab per list** the caller is entitled to |
| 2 | Decide on this page? | **No.** The inbox decides; this page reads. One place to approve |
| 3 | Names in the API or the screen? | **In the API**, batched — managers cannot look employees up |
