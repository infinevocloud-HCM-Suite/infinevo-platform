# Feature: Payroll dashboard screen — run status, year figures, setup card

| Field | Value |
|---|---|
| **Feature ID** | `W-47.5` · screen for `W-37` (ticket #67) · `PAY-16` |
| **Promoted to** | `docs/target-state/features/W-47-5-payroll-dashboard-screens.md` — **`W-47-5` with hyphens**, never `W-47.5`; `guard-edit` blocks the dotted form |
| **Owner** | biren |
| **Apps touched** | `code/frontend/src/payroll/dashboard` (new); one menu item in `code/backend/payroll/.../navigation/PayrollNavigation.java` |
| **Related gaps** | DEBT-028 (fixed for new code), DEBT-001 / DEBT-031 (discounted) |
| **Status** | **Ready** — written against `W-37` § 4 |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | **`W-37`** (unassigned). Its endpoint is the only data source, and the menu item below fails the boot-time catalogue check until `GET /api/v1/payroll/dashboard` exists (`PayrollNavigation.java:10-11`). Links into run pages land when `W-47.2` (krushna) is on `main`; they do not block |
| **Size** | S |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` — one menu constant | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an officer opens the payroll dashboard and every figure on it is the figure `GET /api/v1/payroll/dashboard` returns for their tenant and the chosen year | 1 |
| Frontend area | `src/payroll/dashboard` | 1 |

Within cap.

---

## 1. Problem

`W-37` gives one read-only endpoint and no screen. Its spec says it has no screen of its own (`W-37-payroll-dashboard.md` § 5).

The frozen dashboard shows made-up figures. EPF, ESI and TDS are `"-"`, headcount and cost are constants, and the recent runs and upcoming payments are invented. Its only real call is the pay run list. This is DEBT-028.
- `legacy/Payroll-Fend-react/src/pages/mainPages/dashboardPage/index.js:15-34`
- `legacy/Payroll-Fend-react/src/pages/mainPages/dashboardPage/index.js:120`

The frozen onboarding checklist is six hard-coded cards over a call to `/api/org-setup-steps`.
- `legacy/Payroll-Fend-react/src/pages/mainPages/dashboardPage/onboardingDashboard.js:21-88`, `:158-175`

The target already has that checklist as `/setup`, with a "Payroll Setup" group fed by `GET /api/v1/setup-checklist`.
- `code/frontend/src/core/setup/SetupChecklist.jsx:130-134`
- `code/backend/core/src/main/java/com/infinevo/core/setup/SetupChecklistController.java:36-41`

## 2. Scope

**In scope**

- `/payroll/dashboard`, built from the `W-37` response alone
- A setup card that summarises the payroll steps of the existing checklist and links to `/setup`
- The `payroll.dashboard` menu item

**Out of scope**

- A second onboarding checklist. `/setup` is the checklist (§ 13 decision 1).
- Charts. The months are a table (§ 13 decision 2).
- Any figure the response does not carry: cost per employee, upcoming payments or a payslip percentage. All of these were invented in legacy.
- Any backend change beyond the menu constant.

## 3. Flow

```
[officer] /payroll/dashboard
   FY Select (current + 2 previous)  --> dashboardService.summary(fy)   GET /v1/payroll/dashboard?fy=2026
        <-- {financial_year, employees, current_run, recent_runs, months, year_totals, statutory}
   current_run.status = COMPUTING    --> poll summary(fy) every 10 s until it is not
   run card / recent row             --> /payroll/runs/:payrun_id   (W-47.2)
   SetupCard (useCan core.tenant.read) --> GET /v1/setup-checklist (bare, no envelope)
        payroll steps not done and not skipped > 0  --> "n of m payroll steps left" → /setup
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Navigation | `payroll/.../navigation/PayrollNavigation.java` | **add** `DASHBOARD` (`payroll.dashboard`, `nav.payroll.dashboard`, `/payroll/dashboard`, `/api/v1/payroll/dashboard`, `PAYROLL`, `payroll.run.read`), listed first in `items()` |

Endpoints used, none changed:

| Method | Path | Auth | Reply |
|---|---|---|---|
| GET | `/api/v1/payroll/dashboard?fy=` | `payroll.run.read` (`payroll-officer`, `finance`) | `{status, message, data}` — `W-37` § 4 |
| GET | `/api/v1/setup-checklist` | `core.tenant.read` | **bare** `SetupChecklistResponse`, no envelope (`SetupChecklistController.java:38`) |

## 5. Frontend changes

This ticket follows the `W-45` contract and the `payroll/tax` pattern. Calls go through `apiClient` from `@shared/api/client`. **The dashboard reply is unwrapped as `res.data.data`**, the fault that sent `W-47.2` back. The setup checklist reply is unwrapped as `res.data` (§ 4).
- `src/payroll/tax/taxSettingsService.js:18-19`
- `.claude/work/active-work.md:25`

| File | Change |
|---|---|
| `src/payroll/dashboard/dashboardService.js` | **new.** `summary(fy)` and `setupChecklist()` |
| `src/payroll/dashboard/financialYear.js` | **new.** The current FY's start year (April to March), `options()` gives the current year and two previous years, and `label(2026)` gives `2026-27`. Pure functions, and the test runs with a fixed date |
| `src/payroll/dashboard/DashboardPage.jsx` | **new.** The year `Select`, then the widgets below in order. When `current_run` is null and `recent_runs` is empty it shows an `Empty` "No pay run yet" with a link to `/payroll/runs`. When the load fails it shows `Result status="error"` with Retry. **There are no constants and no fallback figures** (DEBT-028) |
| `src/payroll/dashboard/CurrentRunCard.jsx` | **new.** Shows the period, status `Tag`, pay date, paid-on date, included, skipped, gross, deductions and net pay. When the status is `COMPUTING`, a `Progress` of `progress_done / progress_total`. When `pay_date` is before today and the status is not `PAID`, a "Payment due" warning, as `W-37` § 4 leaves to the screen. A link opens the run |
| `src/payroll/dashboard/HeadcountCard.jsx` | **new.** `active_today`. Under it, "At the last run ({period})": included, skipped, and `skipped_by_reason` as a list with readable reason labels. Hidden when `as_at_run` is null |
| `src/payroll/dashboard/StatutoryTiles.jsx` | **new.** Four tiles: EPF employee and employer, ESI employee and employer, professional tax, TDS. Each says "PAID runs, FY {label}". Zero shows as `0.00`, not as a dash |
| `src/payroll/dashboard/YearTable.jsx` | **new.** The months `Table` (period, status `Tag`, gross, deductions, tax, net pay) with a footer row from `year_totals` labelled "Paid ({paid_runs} runs)", and the recent runs `Table` (period, status, pay date, net pay), each row a link to `/payroll/runs/:payrun_id` |
| `src/payroll/dashboard/SetupCard.jsx` | **new.** Rendered only with `useCan('core.tenant.read')`. Counts the steps with `module = PAYROLL` that are neither `completed` nor `skipped`, and shows "{n} of {m} payroll setup steps left" linking to `/setup`. Renders nothing when n = 0, without the permission, or when the call fails. `W-38.3`'s prior payroll step appears here without change |
| `src/payroll/dashboard/labels.js` | **new.** Run status and skip reason labels |
| `src/payroll/index.js` | `routes` gains `/payroll/dashboard` |

Amounts are shown as sent, formatted for display only. The screen sums nothing: the footer is `year_totals`, never the column added up.

The poll stops when the page unmounts and when the status leaves `COMPUTING`.

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/payroll/dashboard` | `DashboardPage` | inside `AppShell`; present when the feed carries `payroll.dashboard` |

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | navigation catalogue boot test (existing) | `payroll.dashboard` resolves to `W-37`'s `GET` |
| Unit | `dashboardService.test.js` | `summary(2026)` sends `fy=2026` and unwraps `data.data`; `setupChecklist` unwraps `data` |
| Unit | `financialYear.test.js` | 31 March 2027 gives 2026; 1 April 2027 gives 2027; `label(2026)` is `2026-27` |
| Component | `DashboardPage.test.jsx` | renders every widget from a fixture of the `W-37` § 4 shape; changing the year refetches; the empty state; a failed load shows Retry and no figures; polls while `COMPUTING` and stops after |
| Component | `CurrentRunCard.test.jsx` | progress only when `COMPUTING`; "Payment due" when the pay date has passed and the run is not `PAID`, and not when it is |
| Component | `HeadcountCard.test.jsx`, `StatutoryTiles.test.jsx`, `YearTable.test.jsx` | reasons listed; zeros shown as `0.00`; footer from `year_totals` |
| Component | `SetupCard.test.jsx` | counts payroll steps only; skipped counts as done; nothing at 0, without the permission, or on error |
| Lint | existing `lint-rules.test.js` | `src/payroll` imports only `@shared/*` and `@shell/*` |

## 8. Verification

```bash
cd code/backend && ./mvnw -q -pl payroll -am spotless:check verify
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d
```

| Check | Expected |
|---|---|
| builds, lint, tests | clean |
| menu | as `admin@acme.local`, "Dashboard" is in the payroll menu; an `employee` login does not see it |
| figures | each figure on screen equals `curl … /api/v1/payroll/dashboard \| jq .data` for the same year |
| year switch | the previous FY shows its own months, or the empty state |
| computing | start a compute in `/payroll/runs`; the dashboard shows progress and settles on `COMPUTED` without a reload |
| setup card | with a payroll step open, the card shows the count and links to `/setup`; after skipping it, the card is gone |
| no fake data | stop the backend and reload: an error with Retry, no numbers |
| isolation | `admin@globex-full.local` sees none of Acme's runs |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `W-37` changes a field name while it is built | medium | one fixture of the § 4 shape in the tests; read `W-37` on `main` at branch time and fix the fixture first |
| The statutory tiles show zero until `W-31.4` and `W-36.1` lines exist | certain, short-lived | each tile says what it sums; zero is the true answer (`W-37` § 9) |
| Polling keeps going after the user leaves | low | cleared on unmount; the component test |
| Run links 404 before `W-47.2` merges | low | `W-47.2` is ahead of this in the build order; the links are plain paths |

## 10. Rollback

Revert the branch. No data and no migration.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table; the endpoint is tenant-bound (`W-37` § 11) |
| Flyway | none |
| `Money` / `BigDecimal` | amounts displayed as sent; nothing summed or computed on the client |
| Index | none |
| Expand / contract | one added menu constant |
| No module references another | `src/payroll` reaches `core`'s setup checklist over HTTP only, and does not import `src/core/setup` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-028 hard-coded dashboard tiles | **Fixed for new code**: every figure from `W-37`; an error state, never constants |
| DEBT-001, DEBT-031 dead `dashboardcopy.js` | **Discounted**: frozen tree, nothing ported from it |

## 13. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | What is the "onboarding checklist" of this ticket? | **A card that counts the open payroll steps of `/setup` and links there.** `W-46.6` built the checklist; a second one would drift from it |
| 2 | Chart or table for the months? | **Table.** Every figure readable and testable; a chart can follow on the same data |
| 3 | Is the dashboard the payroll landing page? | **No.** It is a menu item like the others. The landing route stays the shell's |
