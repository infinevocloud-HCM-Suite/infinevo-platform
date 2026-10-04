# Feature: HRMS dashboard screen

| Field | Value |
|---|---|
| **Feature ID** | `W-48.6` · screen for `W-44` (ticket #68) · `HRMS-11` |
| **Promoted to** | `docs/target-state/features/W-48-6-hrms-dashboard-screen.md` — **hyphens**, never `W-48.6` |
| **Owner** | *(unassigned)* |
| **Apps touched** | `code/frontend/src/hrms/dashboard` (new); `code/backend/hrms` — one menu item |
| **Related gaps** | DEBT-028 (no fallback figures), as `W-47.5` |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-04 |
| **Blocked by** | `W-44` — Ready to merge on `dev-claude` `bf3ac296`; build after it is on `main` |
| **Size** | S |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` — one menu item | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an employee opens the dashboard and sees their own cards; a manager also sees the team cards | 1 |
| Frontend area | `src/hrms/dashboard` | 1 |

---

## 1. Problem

`W-44` gives `GET /api/v1/hrms/dashboard` and no screen (`W-44-hrms-dashboard.md` §5). `HrmsNavigation.java` has no `hrms.dashboard` item, so the menu cannot show it. The six legacy dashboards were one page per role (`legacy/HRMS_Frontend/src/App.jsx:80-97`, MUI); here the roles do not exist and the reply already decides what the caller sees: a block they cannot see is `null` (`W-44` §3).

## 2. Scope

**In scope**

- `hrms`: menu item `hrms.dashboard`
- `/hrms/dashboard` — cards from the one reply, each rendered only when its block is not `null`

**Out of scope**

- Any change to the reply — `W-44` §4 is the contract; regularization and overtime counts come when `W-44` grows a block
- Links to screens that do not exist yet: a card links only where `W-48.1`–`W-48.4` give a path

## 3. Flow

```
[anyone holding one of W-44's five actions] /hrms/dashboard
   --> GET /v1/hrms/dashboard        envelope: res.data.data (HrmsDashboardController.java:40, ApiResponse.success)
   me.today       card  "Today"           clocked in since · worked h:mm            → /hrms/attendance
   me.timesheets  card  "Timesheets"      this week and last: status Tag, hours     → /hrms/timesheets/week/{week_start}
   me.projects    card  "My projects"     active count, ≤5 rows                     → /hrms/projects/{project_id}
   me.tasks       card  "My tasks"        open · overdue (red) · due this week; ≤5 next   → /hrms/my-work
   team.projects  card  "Projects I manage"   managed · by status; ≤5 rows with team, open, overdue → /hrms/projects/{id}
   team.approvals card  "Waiting for me"  count; ≤5 oldest with employee, project, week → /hrms/timesheet-review/entries/{project_entry_id}
   team.reports   card  "My team today"   reports · clocked in · late last week; ≤5 late names
```

`team` is `null` when the caller manages nothing, approves nothing and has no reports; the whole section is then absent. A `null` `me` block hides its card. Everything on screen is the server's figure; the page adds nothing up (DEBT-028). `as_of` is shown as "As of {date}"; the browser's date is never used.

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Navigation | `navigation/HrmsNavigation.java` | add `hrms.dashboard` → `/hrms/dashboard`, `GET /api/v1/hrms/dashboard`, action `hrms.project.read_own` — every seeded `employee`, `manager` and `hr` holds it, so the item appears for all three (`W-44` §4 names it as the controller's `value`) |

No other change. The catalogue check only needs the `GET`, which `W-44` ships.

## 5. Frontend changes

`W-45` contract, as `src/payroll/dashboard` (`W-47.5` §5): `apiClient` from `@shared/api/client`, reply unwrapped as `res.data.data`, a failed load shows `Result status="error"` with Retry, no constants and no fallback figures.

| File | Change |
|---|---|
| `src/hrms/dashboard/hrmsDashboardService.js` | **new** — `summary()` |
| `src/hrms/dashboard/HrmsDashboardPage.jsx` | **new** — loads once; "As of" line; `Row`/`Col` of the `me` cards, then a "Team" `Divider` and the `team` cards when `team` is not `null`; each card component receives its block and renders nothing for `null` |
| `src/hrms/dashboard/TodayCard.jsx` | **new** — `clocked_in` as a green or grey `Tag`, `clocked_in_at` as a time, `worked_minutes` as h:mm (reuse `../attendance/format.js`) |
| `src/hrms/dashboard/TimesheetsCard.jsx` | **new** — two rows, this week and last; `status` `Tag` or "Not started" when `timesheet_id` is null; `hours` as sent; row links to the week grid |
| `src/hrms/dashboard/MyProjectsCard.jsx`, `MyTasksCard.jsx` | **new** — counts as `Statistic`s, then the `items` / `next` table; overdue tasks (`due_date` before `as_of`) in red; task row links to `/hrms/my-work` |
| `src/hrms/dashboard/ManagedProjectsCard.jsx` | **new** — `managed`, `by_status`; table of `items` with `team_size`, `open_tasks`, `overdue_tasks` |
| `src/hrms/dashboard/ApprovalsCard.jsx` | **new** — `waiting` as a `Statistic`; `oldest` as a list of `employee_name` · `project_name` · week, each linking to the entry view; "Go to approvals" to `/approvals` |
| `src/hrms/dashboard/ReportsCard.jsx` | **new** — `reports`, `clocked_in_today`, `late_last_week`; the `late` names |
| `src/hrms/index.js` | `routes` gains `/hrms/dashboard` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/hrms/dashboard` | `HrmsDashboardPage` | `AppShell`; feed carries `hrms.dashboard`; a `403` from the endpoint shows `NotEntitled` |

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `HrmsNavigationTest.java` (extend) | the item's `targetEndpoint` has a `GET` |
| Unit | `hrmsDashboardService.test.js` | hits `/v1/hrms/dashboard`, unwraps `data` |
| Component | `HrmsDashboardPage.test.jsx` | employee reply (`team: null`) renders four cards and no Team section; manager reply renders all seven; a `null` `me.today` hides that card only; `as_of` shown; failure shows Retry; `403` shows `NotEntitled` |
| Component | `MyTasksCard.test.jsx` | a task due before `as_of` is red, one due after is not; counts are the reply's, not the rows counted |
| Component | `ApprovalsCard.test.jsx` | each row links to `/hrms/timesheet-review/entries/{project_entry_id}` |

## 8. Verification

```bash
cd code/backend && ./mvnw -q -pl hrms -am spotless:check test -Dtest='HrmsNavigationTest'
cd code/frontend && npm run lint && npx vitest run src/hrms/dashboard
```

| Check | Expected |
|---|---|
| backend, lint, tests | `BUILD SUCCESS`; clean |
| employee login | menu shows Dashboard; four cards, no Team section |
| manager login | Team section with the three cards; "Waiting for me" excludes drafts (the server's rule) |
| `check-done.mjs W-48.6` | no new table, no `double`, no `ddl-auto` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `W-44` reply changes before this builds | low — it is Ready to merge | build after `/merge W-44` |
| The screen re-counts rows and disagrees with the server | medium — the usual dashboard mistake | the test asserts counts come from the reply, as `W-47.5` |

## 10. Rollback

Revert the branch. No data changes.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | none |
| `Money` / `BigDecimal` | `hours` shown as sent; no money |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/hrms` imports `@shared/*` and `@shell/screens` only; `hrms` backend adds one navigation constant |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-028 fallback figures | **honoured** — none |

## 13. Decisions — founder, 2026-10-04

| # | Question | Answer |
|---|---|---|
| 1 | Which action on the menu item? | **`hrms.project.read_own`** — held by every seeded role that can see anything on the dashboard |
| 2 | Hide or grey out a block the caller cannot see? | **Hide.** `null` means not theirs, not "empty" |
