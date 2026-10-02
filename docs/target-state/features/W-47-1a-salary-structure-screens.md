# Feature: Salary structure screens

| Field | Value |
|---|---|
| **Feature ID** | `W-47.1a` · from ticket #63 · `PAY-01`, `PAY-02` |
| **Spec file** | `docs/target-state/features/W-47-1a-salary-structure-screens.md` |
| **Owner** | karma, branch `dev-karma` (assigned 2026-10-02) |
| **Apps touched** | `code/frontend/src/payroll/salary` only. No backend, no migration |
| **Related gaps** | DEBT-008 (closed by the envelope), DEBT-026 (prevented), BUG-006 (deferred) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-28 |
| **Blocked by** | `W-45`, `W-46.1` (the employee page this adds a tab to). Every endpoint it calls is on `main` (`a3ad0a3`) |
| **Size** | **M** |

## Why `W-47.1` is two tickets

`W-47.1` "Salary structure" in the scoping split (`10-scoping.md:210`) covers the salary
catalogue, the employee's CTC, and five settings screens. The catalogue and CTC endpoints
are on `main`; the settings endpoints are five Ready specs with no code. One ticket would
wait on all of them. Split as `W-46.3` was: **`.1a` builds on what is on `main`, `.1b`
(payroll settings) waits on `W-28`, `W-31.1`, `W-31.2`, `W-27.1`, `W-27.2`, `W-18.1`.**

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an administrator defines salary components, gives an employee a CTC structure and revises it from the browser, and the split shown is the server's, never the browser's | 1 |
| Frontend area | `src/payroll/salary` | 1 |

Within cap.

---

## 1. Problem

`W-26.1` and `W-26.2` are on `main` and nothing renders them. Four component controllers
(`EarningController.java:42-82` and its three siblings), the salary versions
(`EmployeeSalaryController.java:46-95`) and the statutory profile
(`EmployeeStatutoryProfileController.java:39-50`) have no screen. The `W-46.1` employee
page reserves a Salary tab for this ticket (`W-46-1-employee-screens.md` §14 decision 2).

The frozen screens exist and are the source of the markup, not the logic:

| Frozen screen (legacy) | What it does | Why not ported as-is |
|---|---|---|
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/salaryComponents/index.js:249-381` | Ant table of earnings: name, type, calculation type, EPF, ESI, status, actions | columns kept; the four amount fields collapsed into `defaultValue` + `calculationType` by `W-26.1` |
| `legacy/Payroll-Fend-react/src/pages/mainPages/employee/salaryDetails.js` (1,345 lines) | one file: CTC entry, component split, preview, save | computes the split in the browser; the target split is server-side (`SalaryVersionResponse.java:16-24`) |
| `legacy/Payroll-Fend-react/src/pages/mainPages/employee/editStatutoryDetails.js:198-319` | eligible-for flags, PF number, UAN | fields kept one for one (`StatutoryProfileRequest.java:7-16`) |
| `legacy/Payroll-Fend-react/src/pages/mainPages/approval/salaryRevisionApproval.js:586-681` | revision approval queue | **no approval flow in `W-26.2`**; a revision is a dated version, created directly |
| `legacy/Payroll-Fend-react/src/pages/mainPages/userPortal/userSalaryDetails.js` | employee's own salary | the portal panel is `W-25`'s; the read endpoint has no `_own` action yet — see §14 |

## 2. Scope

**In scope**

- Salary components screen: four tabs (earnings, deductions, benefits, reimbursements), list with active toggle, create and edit drawers, soft delete with confirm
- Salary tab on the `W-46.1` employee page: version in force as of a date, all versions, create the first structure, revise (new dated version), edit a version, cancel a version
- Statutory profile section on the same tab: the eligibility flags, PF account number, UAN, ESI number
- The split shown is the server's response, re-fetched after every save. The screen never adds components up
- Registering routes, the tab and the `salary` slice from `src/payroll/index.js`
- The `payroll.structure` menu item is added to the catalogue by this ticket's backend-free means: **none** — see §14 decision 1

**Out of scope**

- Pay schedule, EPF, ESI, PT, FBP screens — `W-47.1b`
- Salary revision approval — no such flow in the target; `W-15` approvals may add one later
- Employee's own salary panel in the portal — `W-25` and a `payroll.salary.read_own` action that does not exist yet
- Any backend change. A missing field is a defect in `W-26.x`, not work here

## 3. Flow

```
[admin] --> /payroll/components            --> componentService.list(kind, {activeOnly})
        --> drawer create / edit           --> componentService.create(kind, body) / update(kind, id, body)
        --> toggle                         --> componentService.setActive(kind, id, active)
[admin] --> /employees/:id  tab Salary     --> salaryService.asOf(id, date) + salaryService.versions(id)
        --> "Create structure"             --> salaryService.create(id, body)      --> re-fetch asOf
        --> "Revise"                       --> salaryService.revise(id, body)      --> re-fetch versions
        --> "Edit" / "Cancel" on a version --> salaryService.update(id, vid, body) / cancel(id, vid)
        --> Statutory profile card         --> statutoryProfileService.get(id) / save(id, body)
```

## 4. Backend changes

None. Every call below exists on `main`.

**API contract used**

| Method | Path | Action | Evidence |
|---|---|---|---|
| GET/POST | `/api/v1/payroll/components/{earnings,deductions,benefits,reimbursements}` | `payroll.structure.read` / `.manage` | `EarningController.java:42-54` and siblings |
| GET/PUT/DELETE | `.../{kind}/{id}` | `payroll.structure.read` / `.manage` | `EarningController.java:56-82` |
| PUT | `.../{kind}/{id}/active` | `payroll.structure.manage` | `EarningController.java:68-75`, `ActiveUpdateRequest.java` |
| POST | `/api/v1/payroll/employees/{employeeId}/salary` | `payroll.salary.manage` | `EmployeeSalaryController.java:46-54` |
| POST | `.../salary/revisions` | `payroll.salary.manage` | `EmployeeSalaryController.java:56-64` |
| GET | `.../salary?asOf=` | `payroll.salary.read` | `EmployeeSalaryController.java:66-73` |
| GET | `.../salary/versions` | `payroll.salary.read` | `EmployeeSalaryController.java:75-79` |
| PUT/DELETE | `.../salary/versions/{id}` | `payroll.salary.manage` | `EmployeeSalaryController.java:81-95` |
| GET/PUT | `.../statutory-profile` | `payroll.salary.read` / `.manage` | `EmployeeStatutoryProfileController.java:39-50` |

## 5. Frontend changes

Follows the `W-45` contract: services over `apiClient`, slices and routes registered from
`src/payroll/index.js`, no `axios`, no storage, tokens from `theme.useToken()`, Formik +
Yup forms, outcomes through `msgHelper` (`W-45-frontend-shell.md` §5, §5b).

| File | Change |
|---|---|
| `src/payroll/salary/componentService.js` | **new.** `list(kind, params)`, `get(kind, id)`, `create(kind, body)`, `update(kind, id, body)`, `setActive(kind, id, active)`, `remove(kind, id)`; `kind` is one of the four path segments |
| `src/payroll/salary/salaryService.js` | **new.** `asOf(employeeId, date)`, `versions(employeeId)`, `create(employeeId, body)`, `revise(employeeId, body)`, `update(employeeId, versionId, body)`, `cancel(employeeId, versionId)` |
| `src/payroll/salary/statutoryProfileService.js` | **new.** `get(employeeId)`, `save(employeeId, body)` |
| `src/payroll/salary/salarySlice.js` | **new.** `{ components: {earnings, deductions, benefits, reimbursements}, loadedAt }`, the active catalogue cached for the session so the salary form's pickers do not refetch per open; invalidated on every component write |
| `src/payroll/salary/ComponentsScreen.jsx` | **new.** Ant `Tabs` × 4, each an Ant `Table`: name, code, calculation type, default value, `EPF` / `ESI` / `FBP` tags where the kind has them, active `Switch`, actions. Columns from `salaryComponents/index.js:249-381`. "Add" button when `useCan('payroll.structure.manage')`; the switch is read-only without it |
| `src/payroll/salary/ComponentDrawer.jsx` | **new.** One drawer driven by a field list per kind (`componentFields.js`): the `EarningRequest.java:10-30` fields for earnings, the sibling request records for the rest. `calculationType` `FLAT` or `PERCENTAGE` (`CalculationType.java:8`); `percentageOf` shown only for `PERCENTAGE` (`PercentageOf.java:7-8`). Money fields are string inputs validated by Yup as decimals with at most 4 places and sent as strings; never `parseFloat` |
| `src/payroll/salary/SalaryTab.jsx` | **new.** Registered into the `W-46.1` employee page tab list. Top: `DatePicker` "as of" (default today) and the version in force as `Descriptions` (annual CTC, monthly CTC, effective from, change %) plus three tables earnings, benefits, reimbursements from `SalaryVersionResponse.java:16-24`. Below: all versions table with cancelled rows struck through, actions Edit and Cancel. Buttons "Create structure" (no version yet) and "Revise" (has one), gated by `useCan('payroll.salary.manage')` |
| `src/payroll/salary/SalaryVersionForm.jsx` | **new.** Modal for create, revise and edit: annual CTC, effective from, notes, and one row per component chosen from the cached catalogue with its value (`SalaryVersionRequest.java:11-16`). No total is computed on the client; after `201`/`200` the tab re-fetches and the server's split is shown. A `400` from `SalaryComponentValidator` is shown verbatim from the envelope |
| `src/payroll/salary/StatutoryProfileCard.jsx` | **new.** Card on the Salary tab: the seven flags and three numbers of `StatutoryProfileRequest.java:7-16`; `GET` on open, `PUT` on save. Field names and labels from `editStatutoryDetails.js:198-319` |
| `src/payroll/index.js` | `routes` gains `/payroll/components`; `reducers` gains `salary`; `employeeTabs` exports the Salary tab for `W-46.1`'s page to pick up (see §14 decision 2) |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/payroll/components` | `ComponentsScreen` | inside `AppShell`; present when the feed carries `payroll.structure` — see §14 decision 1 |
| `/employees/:id` (tab `salary`) | `SalaryTab` | inside the `W-46.1` page; rendered when `useCan('payroll.salary.read')` |

**Ported logic.** The catalogue columns (`salaryComponents/index.js:249-381`) and the
statutory field list (`editStatutoryDetails.js:198-319`) are the sources. The browser-side
CTC arithmetic in `salaryDetails.js` is not ported: the server computes the split.

## 6. Database changes

None.

## 7. Tests

Vitest + jsdom, the runner `W-12.3` put in CI.

| Type | File | Covers |
|---|---|---|
| Unit | `src/payroll/salary/componentService.test.js` | each verb hits `/v1/payroll/components/{kind}...`; `setActive` sends `{active}`; no `axios` import |
| Unit | `src/payroll/salary/salaryService.test.js` | `asOf` passes `asOf`; `revise` posts to `/revisions`; `cancel` is `DELETE` on the version |
| Unit | `src/payroll/salary/salarySlice.test.js` | catalogue cached once; any component write invalidates |
| Component | `src/payroll/salary/ComponentsScreen.test.jsx` | four tabs; switch calls `setActive`; "Add" absent without `payroll.structure.manage` |
| Component | `src/payroll/salary/ComponentDrawer.test.jsx` | `percentageOf` hidden for `FLAT`; `"12.5"` is sent as a string; `"1.23456"` blocked |
| Component | `src/payroll/salary/SalaryTab.test.jsx` | no version shows "Create structure"; a version shows "Revise"; after save the displayed monthly CTC is the mocked server value, not a client sum; a `400` message renders |
| Component | `src/payroll/salary/StatutoryProfileCard.test.jsx` | loads on open, `PUT` sends all ten fields |
| Lint | existing `lint-rules.test.js` | unchanged; `npm run lint` covers `src/payroll/salary/**` |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d
# browser: log in as admin@acme.local
```

| Check | Expected |
|---|---|
| lint | zero warnings on `src/payroll/salary/**` |
| tests | green |
| components | `/payroll/components` lists the `W-26.1` seed rows; add an earning `HRA`, 40 % of `BASIC`; it appears; toggle it off, the switch persists after reload |
| salary | open an Acme employee, Salary tab: "Create structure", annual CTC 600000, BASIC + HRA; the split shown equals `GET .../salary` in the network tab; `psql`: one row in `payroll.ctc_structure` for that employee |
| revise | "Revise" with a later date and 660000; versions table shows two rows; `changeInPercent` is 10 |
| statutory | tick PF, enter a UAN, save, reload: persists |
| isolation | log in as admin@globex-full.local: `/payroll/components` shows Globex's rows only; the HRMS-only `W-02` seed tenant sees no `payroll.*` menu item and `/payroll/components` renders `NotEntitled` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The browser starts adding components up "for a live total" and drifts from the server | medium | the `SalaryTab` test asserts the displayed CTC is the mocked response; no arithmetic on money in `src/payroll/**` (a `parseFloat` on a money field is a review comment) |
| `salaryDetails.js` is re-ported as one 1,300-line file | medium | five files above, each under 300 lines; `SalaryVersionForm` is the only form |
| The Salary tab is built before the `W-46.1` tab-list seam exists | high if started early | blocked on `W-46.1` in the tracker |
| No `payroll.structure` menu item exists in the catalogue | certain | §14 decision 1 |

## 10. Rollback

Frontend only. Revert the branch.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money` / `BigDecimal` | money crosses the wire as JSON numbers with scale 4 (`SalaryVersionResponse.java:16-17`); the screen displays and forwards strings, computes nothing |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/payroll/salary` imports `@shared/*` and `@shell/screens` only; it never imports `@core/*` — the employee page discovers the tab through `src/payroll/index.js` (`W-45` §5b) |
| No write to `legacy/` | read only |

## 12. Gap inventory

| ID | Overlap | Decision |
|---|---|---|
| DEBT-008 (per-endpoint envelopes) | error display | **closed** by `ApiErrorResponse`; `W-26.2` returns bare DTOs on success, which the services pass through |
| DEBT-026 (admin/employee screen pairs) | statutory and salary forms | **prevented**: one `SalaryVersionForm` for create, revise, edit |
| BUG-006 (no code splitting) | bundle | **deferred**; `W-46.1` measures first |

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `src/payroll/salary` | three services, slice, `componentFields.js`, tests |
| 2 | `src/payroll/salary` | `ComponentsScreen`, `ComponentDrawer`, tests |
| 3 | `src/payroll/salary` | `SalaryTab`, `SalaryVersionForm`, `StatutoryProfileCard`, tests |
| 4 | `src/payroll/index.js` | routes, reducer, `employeeTabs` export |

## 14. Decisions and open questions

| # | Question | Answer |
|---|---|---|
| 1 | The catalogue has no `payroll.*` menu item (`NavigationCatalogue.java:52-84`), so `/payroll/components` is unreachable from the menu | **Open for the founder.** The item needs one `core` line (`payroll.structure` → `/payroll/components` → `GET /api/v1/payroll/components/earnings`, `payroll.structure.read`). This spec touches no backend; the recommendation is a one-line `core` change under `W-47.1b`, or under this ticket if the founder grants a second area |
| 2 | How does a `payroll` tab reach the `core` employee page without `core` importing `payroll`? | **The shell composes.** `src/payroll/index.js` exports `employeeTabs = [{ key: 'salary', label, action: 'payroll.salary.read', component }]`; `src/shell` passes every module's `employeeTabs` to the `W-46.1` page as a prop. `core` never imports `payroll`; `payroll` never imports `core`. `W-46.1` §14 decision 2 already says the page takes tabs from a list |
| 3 | Is the salary revision approval queue ported? | **No.** `W-26.2` has no approval state; a revision is a version. If the founder wants approval, it is a `W-15` flow type, not a screen |
| 4 | Employee's own salary view? | **Not here.** Needs a `payroll.salary.read_own` action and a `/me` endpoint, neither of which exists; belongs with `W-36`'s payslip panel |
