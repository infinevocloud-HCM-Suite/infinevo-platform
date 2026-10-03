# Feature: Timesheet entry screens

| Field | Value |
|---|---|
| **Feature ID** | `W-48.2` · screens for `W-42.1`, `W-42.3` (ticket #68) · `HRMS-*` |
| **Promoted to** | `docs/target-state/features/W-48-2-timesheet-entry-screens.md` — **hyphens**, never `W-48.2` |
| **Owner** | *(unassigned)* |
| **Apps touched** | `code/frontend/src/hrms/timesheet` (new); one line of wiring in `src/shell/portal/PortalLayout.jsx` |
| **Related gaps** | none |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-03 |
| **Blocked by** | nothing — `W-42.1`, `W-42.3` on `main` `2780d098`. Uses `W-48.1`'s `project_name` when present, works without it |
| **Size** | M |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none — the `hrms.timesheets` menu item exists (`hrms/.../navigation/HrmsNavigation.java:17-23`) | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an employee fills a week of hours, saves it, submits it, and resubmits the projects a manager rejected | 1 |
| Frontend area | `src/hrms/timesheet`, plus the portal's panel table | 1 |

---

## 1. Problem

The API is on `main`; the menu item points at `/hrms/timesheets` and no route answers it (`src/hrms/index.js:3`).
The `/me` portal panel is still `W-25`'s placeholder, "currently in development"
(`src/shell/portal/panels/MyTimesheet.jsx:36`), and wins over any module panel with the same code
(`src/shell/portal/PortalLayout.jsx:37`, `:143`).

The frozen system has four MUI pages for this (`legacy/HRMS_Frontend/src/components/EmployeeDashboard/`
`TimesheetForm.jsx` 877 lines, `TimesheetEditForm.jsx` 930, `TimesheetDetailPage.jsx` 1,054,
`TimesheetViewPage.jsx` 608). One week grid replaces create, edit and view.

## 2. Scope

**In scope**

- `/hrms/timesheets` — the caller's weeks, newest first, status per week and per project
- `/hrms/timesheets/week/:weekStart` — one week as a grid: open, fill, save draft, submit, delete draft;
  read-only once submitted; rejected projects editable and resubmitted with their reasons shown
- The `/me` timesheet panel: this week's status, hours, and a link to the grid

**Out of scope**

- Review and approval — `W-48.3`
- Any backend change

## 3. Flow

```
[employee] /hrms/timesheets
   --> GET /v1/hrms/timesheets/mine?from=&to=      last 12 weeks by default
[employee] /hrms/timesheets/week/2026-09-28
   week      --> GET /v1/me/timesheet?weekStart=2026-09-28   data null = no timesheet yet
   rows      --> GET /v1/hrms/projects/mine; per project GET /v1/hrms/projects/{id}/tasks
   save      --> POST /v1/hrms/timesheets (first save) | PUT /v1/hrms/timesheets/{id} (DRAFT)
   submit    --> save, then PUT /v1/hrms/timesheets/{id}/submit
   resubmit  --> PUT /v1/hrms/timesheets/{id} with the REJECTED projects only (W-42-3…md:60, :141)
   delete    --> DELETE /v1/hrms/timesheets/{id}   (DRAFT only)
[employee] /me → Timesheet panel --> GET /v1/me/timesheet
```

Rules shown in the grid are the server's (`W-42-1-timesheet-entry.md:134-138`): projects the caller is assigned
to; hours above 0 and at most 24, two decimals; a day's total at most 24; one timesheet per week; a week starts on
Monday. The grid checks them before sending and shows the server's field errors when it refuses.

## 4. Backend changes

None. Endpoints used, from `W-42-1-timesheet-entry.md` §4 and `W-42-3-timesheet-submit-approve.md` §4:

| Method | Path | Action |
|---|---|---|
| GET | `/api/v1/hrms/timesheets/mine` | `hrms.timesheet.read_own` (`TimesheetController.java:93-94`) |
| GET | `/api/v1/me/timesheet?weekStart=` | `hrms.timesheet.read_own` (`portal/MyTimesheetController.java:43-44`) |
| POST · PUT · DELETE | `/api/v1/hrms/timesheets`, `/{id}` | `hrms.timesheet.submit` (`TimesheetController.java:55-88`) |
| PUT | `/api/v1/hrms/timesheets/{id}/submit` | `hrms.timesheet.submit` (`:70-71`) |
| GET | `/api/v1/hrms/projects/mine`, `/projects/{id}/tasks` | `hrms.project.read_own` (`ProjectController.java:72`, `TaskController.java:63-64`) |

Request bodies are snake_case (`timesheet/TimesheetRequest.java`): `week_start_date`,
`projects[{project_id, tasks[{task_id, days[{date, hours, description}]}]}]`.

## 5. Frontend changes

`W-45` contract, as `src/payroll/priorpayroll`.

| File | Change |
|---|---|
| `src/hrms/timesheet/timesheetService.js` | **new** — `mine(from, to)`, `week(weekStart)`, `create(body)`, `replace(id, body)`, `submit(id)`, `remove(id)`, `myProjects()`, `tasks(projectId)` |
| `src/hrms/timesheet/weekGrid.js` | **new, pure** — response → grid rows and back to the request body; day and row totals; validation (the four rules in §3); Monday of a date |
| `src/hrms/timesheet/TimesheetList.jsx` | **new** — `Table` of weeks (week, total hours, status `Tag`, per-project status), "Open this week" button, range `DatePicker` |
| `src/hrms/timesheet/TimesheetWeek.jsx` | **new** — week switcher (previous / next / pick Monday); rows are project → task, columns Mon–Sun with `InputNumber` (0–24, step 0.25, two decimals); a note per cell in a `Popover`; "Add row" picks project then task; row and day totals, a day over 24 in error. Buttons: Save draft, Submit (confirm), Delete draft (confirm). `SUBMITTED` and `APPROVED` read-only; `REJECTED` projects editable with their `rejection_reason` in an `Alert`, others locked, and "Resubmit" sends those projects only |
| `src/hrms/timesheet/MyTimesheetPanel.jsx` | **new** — this week: status, total hours, per-project status, "Open week" link |
| `src/hrms/index.js` | `routes` gains `/hrms/timesheets`, `/hrms/timesheets/week/:weekStart`; exports `portalPanels = [{ code: 'timesheet', component: MyTimesheetPanel }]` |
| `src/shell/portal/PortalLayout.jsx` | merges `hrms`'s `portalPanels` with `payroll`'s (`:18`, `:40`) and drops the shell's `timesheet` entry (`:17`, `:37`) so the module panel renders |
| `src/shell/portal/panels/MyTimesheet.jsx` and its test | **deleted** — the placeholder |

Hours are `BigDecimal` on the server; the grid holds them as strings with two decimals and sums in hundredths, so
`7.25 + 0.75` shows `8.00`, never `7.999…`.

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/hrms/timesheets` | `TimesheetList` | `AppShell`; feed carries `hrms.timesheets` |
| `/hrms/timesheets/week/:weekStart` | `TimesheetWeek` | beneath `/hrms/timesheets`; a non-Monday date redirects to its Monday |

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `weekGrid.test.js` | round trip response → grid → body; totals in hundredths; a day of 24.25 fails; hours 0 dropped from the body; Monday of a Sunday is the previous Monday |
| Unit | `timesheetService.test.js` | every function hits its path and unwraps `data` |
| Component | `TimesheetWeek.test.jsx` | no timesheet: Save sends `POST`; draft: `PUT`; Submit saves then submits; submitted week has no inputs; rejected week: only rejected projects editable and Resubmit sends only them |
| Component | `MyTimesheetPanel.test.jsx` | `data: null` shows "Not started" and the link |
| Component | `PortalLayout.test.jsx` (extend) | the `timesheet` panel renders `MyTimesheetPanel`, not the placeholder |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d    # browser: an Acme employee assigned to a project
```

| Check | Expected |
|---|---|
| lint, tests | clean |
| fill and save | draft appears in the list; reopening shows the same hours |
| a day over 24 | blocked in the grid before sending |
| submit | status `SUBMITTED`, grid read-only; the project manager has an approval in `/approvals` |
| reject one project, then resubmit | only that project editable; after resubmit the week is `SUBMITTED` |
| `/me` | Timesheet panel shows this week, not "in development" |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Floating-point hour totals | medium | sums in hundredths; the unit test |
| Submit after an unsaved edit sends stale hours | medium | Submit always saves first |
| `PortalLayout.jsx` edited by another ticket at the same time | low | two-line change; merge `main` before push |

## 10. Rollback

Revert the branch; the placeholder panel returns.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | none |
| `Money` / `BigDecimal` | no money; hours kept exact |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/hrms` imports `@shared/*` and `@shell/screens`; the shell imports the module's `portalPanels` as it already does `payroll`'s |

## 12. Gap inventory

None.

## 13. Decisions — founder, 2026-10-03

| # | Question | Answer |
|---|---|---|
| 1 | Separate create, edit and view pages, as legacy? | **One week grid** for all three |
| 2 | Which week does the grid open on? | **The URL's Monday**; the list's button opens this week |
| 3 | Can a submitted week be recalled? | **No.** `W-42.3` has no recall; a rejection reopens the project |
