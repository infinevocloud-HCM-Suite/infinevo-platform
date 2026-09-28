# Feature: Pay run screens

| Field | Value |
|---|---|
| **Feature ID** | `W-47.2` · from ticket #64 · `PAY-05`, `PAY-06` |
| **Spec file** | `docs/target-state/features/W-47-2-pay-run-screens.md` |
| **Owner** | unassigned |
| **Apps touched** | `code/frontend/src/payroll/payrun` only. No backend, no migration |
| **Related gaps** | DEBT-008 (closed), BUG-006 (deferred) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-28 |
| **Blocked by** | `W-45`, then `W-29.1`, `W-29.2`, `W-29.3`, `W-29.4`, `W-30.2`. All Ready specs; none on `main`. Payslips wait on `W-36`, which has no spec, and are out of scope here |
| **Size** | **L** |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | a payroll officer creates a run for a period, sees who is in and who was skipped and why, computes it and watches progress, reads every employee's lines, locks or cancels it, and does the same for an off-cycle run, all from the browser | 1 |
| Frontend area | `src/payroll/payrun` | 1 |

Within cap. Approve, pay and payslip are not in the `W-29.x` contracts, so they are not
here either (§14).

---

## 1. Problem

The pay run is specified in four parts plus the off-cycle run and nothing renders any of it.
The navigation catalogue already reserves the item (`NavigationCatalogue.java:18-20`,
`payroll.runs` → `GET /api/v1/payroll/runs`, "add after `W-29.1` ships").

The frozen screens are the largest in the Payroll app and are ported in shape only:

| Frozen screen (legacy) | Lines | Why not ported as-is |
|---|---|---|
| `legacy/Payroll-Fend-react/src/pages/mainPages/payRuns/index.js:640-682` | list: pay date, type, details, status, net pay, employees, actions | columns kept; status vocabulary changes to `W-29.1`'s eight values |
| `legacy/Payroll-Fend-react/src/pages/mainPages/payRuns/preview.js:153-418` | 1,374 | inclusion, delete and approve in one file; approve (`:403`) has no target endpoint yet |
| `legacy/Payroll-Fend-react/src/pages/mainPages/payRuns/summary.js:92-518` | 1,712 | download, reject, delete, payment in one file; reject-by-query-string (`:457`) and payment (`:518`) have no target endpoint |
| `legacy/Payroll-Fend-react/src/pages/mainPages/payRuns/viewPayslip.js:421-580` | payslip layout | `W-36` owns payslips; the layout is reused then, not now |
| `legacy/Payroll-Fend-react/src/pages/mainPages/payRuns/addOffCycleDetails.js` | off-cycle form | `W-30.2` replaces it with named employees and tagged inputs (`W-30-2-off-cycle-pay-run.md:159-160`) |

## 2. Scope

**In scope**

- Run list: paged, newest period first, filters on status and `runType`; "New run" (period picker) and "New off-cycle run"
- Run page: header (period, type, status `Tag`, counts included and skipped, totals when computed), progress bar while `COMPUTING` (poll every 3 s, stop on any other status), actions Compute, Lock, Cancel gated by `useCan('payroll.run.execute')` and by the status rules of `W-29.1` §4
- Employees table on the run page: filter `INCLUDED` / `SKIPPED`, skip reason shown plainly; a row opens the lines drawer
- Lines drawer: earnings, deductions, benefits, reimbursements in `sort_order`, with `computation_error` when present
- Off-cycle create: pay date, employee multi-select, notes; then the inputs grid (employee, kind, amount, reference) posted to `/inputs`, with per-row result shown (`id` or `DUPLICATE`)
- Registering routes and the `payrun` slice from `src/payroll/index.js`

**Out of scope**

- Approve, pay, reject, payment file download — not in `W-29.x`; a later `W-29` or `W-36` ticket adds the endpoints and this screen gains buttons
- Payslip view, public payslip link, portal payslips panel — `W-36` (`W-25` §4 already names the panel)
- One-time payout and bonus forms — `W-19` and `W-29.x` inputs, per the `W-30.2` tracker row (`DEV-TRACKER.md:319`)
- Prior payroll import — `W-38`
- Any backend change

## 3. Flow

```
[officer] --> /payroll/runs                 --> payrunService.list({status, runType, page, size})
          --> "New run" period 2026-10      --> payrunService.create({period})            --> /payroll/runs/:id
          --> /payroll/runs/:id             --> payrunService.get(id) + employees(id, {inclusion, page})
          --> Compute                       --> payrunService.compute(id)  202 {job_id}   --> poll get(id) until status != COMPUTING
          --> row                           --> payrunService.lines(id, employeeId)
          --> Lock / Cancel                 --> payrunService.lock(id) / cancel(id)
          --> "New off-cycle run"           --> payrunService.createOffCycle({payDate, employeeIds, notes}) --> inputs grid
          --> Save inputs                   --> payrunService.addInputs(id, rows[])
```

## 4. Backend changes

None.

| Method | Path | Action | Spec |
|---|---|---|---|
| POST | `/api/v1/payroll/payruns` `{period}` | `payroll.run.execute` | `W-29-1-pay-run-creation.md:136` |
| GET | `/api/v1/payroll/payruns?status=&runType=&page=&size=` | `payroll.run.read` | `W-29-1-pay-run-creation.md:137`, `W-30-2-off-cycle-pay-run.md:161` |
| GET | `/api/v1/payroll/payruns/{id}` (with `job_id`, `compute_attempt`, `progress_done`, `progress_total`) | `payroll.run.read` | `W-29-1-pay-run-creation.md:138`, `W-29-4-pay-run-async-worker.md:112` |
| GET | `/api/v1/payroll/payruns/{id}/employees?inclusion=&page=&size=` | `payroll.run.read` | `W-29-1-pay-run-creation.md:139` |
| POST | `/api/v1/payroll/payruns/{id}/lock`, `/cancel` | `payroll.run.execute` | `W-29-1-pay-run-creation.md:140-141` |
| POST | `/api/v1/payroll/payruns/{id}/compute` → `202` | `payroll.run.execute` | `W-29-4-pay-run-async-worker.md:111` |
| GET | `/api/v1/payroll/payruns/{id}/employees/{employeeId}/lines` | `payroll.run.read` | `W-29-2-pay-run-computation.md:149` |
| POST | `/api/v1/payroll/payruns/off-cycle` | `payroll.run.execute` | `W-30-2-off-cycle-pay-run.md:159` |
| POST | `/api/v1/payroll/payruns/{id}/inputs` | `payroll.run.execute` + `core.pay_input.write` | `W-30-2-off-cycle-pay-run.md:160` |

Status vocabulary displayed: `DRAFT`, `LOCKED`, `COMPUTING`, `COMPUTED`, `FAILED`,
`APPROVED`, `PAID`, `CANCELLED` (`W-29.1` §6). Line kinds: `EARNING`, `DEDUCTION`,
`BENEFIT`, `REIMBURSEMENT` (`W-29-2-pay-run-computation.md:170`).

## 5. Frontend changes

Follows the `W-45` contract (`W-45-frontend-shell.md` §5, §5b).

| File | Change |
|---|---|
| `src/payroll/payrun/payrunService.js` | **new.** `list`, `get`, `create`, `createOffCycle`, `employees`, `lines`, `compute`, `lock`, `cancel`, `addInputs`; one function per row of §4 |
| `src/payroll/payrun/payrunSlice.js` | **new.** `{ byId, polling: {id, timer} }`. `startPolling(id)` re-fetches every 3 s while `status === 'COMPUTING'` and stops itself; unmount clears it |
| `src/payroll/payrun/RunList.jsx` | **new.** Ant `Table`: period, pay date, type `Tag` (`REGULAR` / `OFF_CYCLE`), status `Tag`, included / skipped counts, net total when computed, opened by row click. Filters: status `Select`, type `Segmented`. Buttons "New run" (`DatePicker.MonthPicker` in a modal) and "New off-cycle run", both `useCan('payroll.run.execute')`. A `409 DuplicatePayRun` and the no-schedule `409` are shown verbatim with a link to `/payroll/settings/pay-schedule` |
| `src/payroll/payrun/RunPage.jsx` | **new.** Header card plus `Progress` when `COMPUTING` (`progress_done / progress_total`, attempt number). Action bar: Compute (enabled in `LOCKED`, `COMPUTED`, `FAILED`, and `COMPUTING` only when stale per `W-29.4` §3), Lock (`DRAFT`), Cancel (`DRAFT`, `LOCKED`), each with confirm. A `409` from any action re-fetches and shows the envelope |
| `src/payroll/payrun/RunEmployees.jsx` | **new.** Server-paged table: number, name, inclusion `Tag`, skip reason as a sentence (`NO_SALARY` → "no salary structure in force", `NO_BANK_DETAILS` → "no bank details"; unknown codes shown raw). Row opens `LinesDrawer` |
| `src/payroll/payrun/LinesDrawer.jsx` | **new.** Four sections by `line_kind`, rows code, name, amount; `computation_error` as an `Alert` at the top. Totals come from the run, not summed here |
| `src/payroll/payrun/OffCycleCreate.jsx` | **new.** Step 1 form: `payDate`, employee `Select` multiple (searches `GET /v1/employees?q=`, the `W-46.1` service pattern, called by path not by import), notes. A `400` listing employees not considered renders each name. Step 2 `InputsGrid`: editable rows employee, `kind` (the `W-19` kinds minus `LOP_DAYS`), amount, `sourceRef`; save posts the array and paints each row's result |
| `src/payroll/index.js` | `routes` gains the four paths; `reducers` gains `payrun` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/payroll/runs` | `RunList` | inside `AppShell`; present when the feed carries `payroll.runs` (the catalogue item `W-29.1` adds, `NavigationCatalogue.java:18-20`) |
| `/payroll/runs/new-off-cycle` | `OffCycleCreate` | same; button hidden without `payroll.run.execute` |
| `/payroll/runs/:id` | `RunPage` | same |

**Ported logic.** The list columns (`payRuns/index.js:640-682`) and the "employees in this
run" table shape (`preview.js:184`) are the sources. Approve, reject, payment and download
are not ported: no target endpoint exists.

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payrunService.test.js` | every verb hits its path; `compute` accepts `202`; `addInputs` posts an array |
| Unit | `payrunSlice.test.js` | polling starts on `COMPUTING`, stops on `COMPUTED` and on `FAILED`, clears on unmount (fake timers) |
| Component | `RunList.test.jsx` | filters send `status` and `runType`; "New run" posts `{period}`; `409` renders with the settings link |
| Component | `RunPage.test.jsx` | button enablement per status, all eight statuses; progress bar shows `3 / 10`; stale `COMPUTING` enables Compute; a `409` on Lock re-fetches |
| Component | `RunEmployees.test.jsx` | `SKIPPED` filter sent; reason sentence for the two known codes; unknown code shown raw |
| Component | `LinesDrawer.test.jsx` | four sections; `computation_error` alert; no client-side total |
| Component | `OffCycleCreate.test.jsx` | `400` names each employee; inputs grid posts `[{employeeId, kind, amount, sourceRef}]`; a `DUPLICATE` row is marked |
| Lint | existing `lint-rules.test.js` | unchanged |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d
# browser: log in as admin@acme.local, with W-47.1a/.1b data in place
```

| Check | Expected |
|---|---|
| lint, tests | clean |
| create | "New run" for the current period; run page shows `DRAFT`, included and skipped counts matching `psql: select inclusion_status, count(*) from payroll.employee_payrun where payrun_id = ...` |
| lock, compute | Lock → `LOCKED`; Compute → progress bar moves, ends `COMPUTED`; totals shown equal `GET /payruns/{id}` |
| lines | open an included employee: BASIC and HRA earnings, PF deduction (if `W-31.4` is on `main`), no error |
| skipped | an employee with no salary structure appears under `SKIPPED` with "no salary structure in force" |
| off-cycle | create for two named employees, add one `BONUS` input each, save: two ids; save again: two `DUPLICATE` |
| cancel | Cancel a `DRAFT` run; list shows `CANCELLED`; "New run" for the same period succeeds |
| isolation | admin@globex-full.local sees only Globex's runs; an Acme run id in the URL renders the not-found envelope |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `preview.js` and `summary.js` come back as two 1,500-line files | medium | six files, one concern each; `RunPage` composes and holds no table |
| Polling never stops and hammers the API | medium | the slice test with fake timers; stop on every non-`COMPUTING` status and on unmount |
| Approve and pay buttons are added "for completeness" against endpoints that do not exist | medium | §2 out of scope; the boot-time catalogue check and the `RunPage` test enumerate exactly three actions |
| Five backend tickets land over weeks | high | build list and run page against `W-29.1` first; compute, lines, off-cycle as each lands |

## 10. Rollback

Frontend only. Revert the branch.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money` / `BigDecimal` | amounts displayed from the response at scale 4 and formatted for display only; the screen sums nothing |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/payroll/payrun` imports `@shared/*` and `@shell/screens` only; employee search is an HTTP call to a `core` path |
| No write to `legacy/` | read only |

## 12. Gap inventory

| ID | Overlap | Decision |
|---|---|---|
| DEBT-008 (`payrollRun` vs `data` envelope keys, `PayRunController`) | responses | **closed** by the `W-29.1` DTOs and the error envelope |
| BUG-006 (bundle) | the largest screen set | **first candidate for `React.lazy`**: `src/payroll/index.js` may export this area's routes lazily; measure after merge |

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `src/payroll/payrun` | service, slice with polling, tests |
| 2 | `src/payroll/payrun` | `RunList`, `RunPage`, tests |
| 3 | `src/payroll/payrun` | `RunEmployees`, `LinesDrawer`, tests |
| 4 | `src/payroll/payrun` | `OffCycleCreate` and the inputs grid, tests |
| 5 | `src/payroll/index.js` | routes, reducer |

## 14. Decisions and open questions

| # | Question | Answer |
|---|---|---|
| 1 | Approve, pay, reject: the frozen screens have them (`preview.js:418`, `summary.js:457,518`), `W-29.x` has `APPROVED` and `PAID` statuses but no endpoint | **Not built.** The founder decides where the transition endpoints live (`W-29.5` or `W-36`); this screen adds two buttons then |
| 2 | Route is `/payroll/runs`, catalogue comment says `GET /api/v1/payroll/runs` but `W-29.1` serves `/api/v1/payroll/payruns` | **Follow `W-29.1`.** The catalogue line `W-29.1` adds should point at `/api/v1/payroll/payruns`; noted for its implementer. Doc drift, not this ticket's |
| 3 | Employee names on the run page: `W-29.1` returns `employee_number` only | the screen shows number and name; name resolved through one `GET /v1/employees?q=` per page **only if** `W-29.1`'s row lacks it. Recommendation to `W-29.1`'s implementer: add `employee_name` to the row |
