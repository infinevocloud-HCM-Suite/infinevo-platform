# Feature: Payroll dashboard — run status and summary widgets, read-only

| Field | Value |
|---|---|
| **Feature ID** | `W-37` · from ticket #49 · `PAY-16` |
| **Promoted to** | `docs/target-state/features/W-37-payroll-dashboard.md` on the developer's `dev-<name>` branch |
| **Owner** | biren |
| **Apps touched** | `code/backend/payroll` only |
| **Related gaps** | DEBT-028 (fixed for new code), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-022 (fixed), BUG-011 (fixed for the response), DEBT-001 / DEBT-031 (discounted) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | `W-29.2` — run totals and the line table · `W-36.2` — `PAID` transition and `paid_on`. The statutory and tax tiles read `W-31.4` and `W-36.1` lines and show zero until those merge; they do not block |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | **none** — `02-data-model.md:354`: `PAY-16` owns no tables | 1 |
| Externally testable behaviour | an officer opens the dashboard and every figure on it matches the pay run tables for their tenant | 1 |
| Frontend area | none — `W-47.5` builds the screen against §4 | 1 |

Within cap.

---

## 1. Problem

The frozen system computes a real summary and never shows it. All citations are `legacy/`.

- **The screen is fake.** Every tile, the recent-runs list and the upcoming payments are
  constants (`Payroll-Fend-react/src/pages/mainPages/dashboardPage/index.js:15-34`). The only
  live call is the run list (`:120-128`). Users see invented EPF/ESI/TDS figures — DEBT-028
- **The backend is real but unused.** `GET /api/dashboard/summary`, `/statutory-summary`,
  `/tds-summary` (`Payroll-Bend-SBoot/.../controller/dashboard/DashboardController.java:25-116`)
  are never called. `DashboardServiceImpl.java:75-208` counts active employees and those with
  no CTC or bank row, lists `DRAFT`/`READY` runs newest first, flags bank mismatches, and sums
  the April-to-March months
- **What it does is loose.** The statutory sum reads every `EmployeeEarning` in the database
  and filters by organisation in Java (`DashboardServiceImpl.java:225-232`) — DEBT-022; it
  sums declared earnings, not what a run deducted, and the employer share is hard-coded zero
  (`:243`). TDS is a `Double` (`:262`) — BUG-011. No `/api/v1`, a hand-built `Map` envelope
  (`DashboardController.java:47-50`) — DEBT-007, DEBT-008
- `09-build-order.md:235`: *"today's dashboard is hard-coded. Do not port that."* The logic
  in the service is worth keeping; the screen is not.

## 2. Scope

**In scope**

- One read-only endpoint returning the whole dashboard for the bound tenant
- Employee readiness: active headcount today, and who the last run skipped and why
- The current run: the newest non-cancelled run with status, counts, progress and totals
- Recent runs: the last six non-cancelled runs
- Financial year: one row per month with gross, deductions, net and tax, and the year totals
- Statutory tiles for the year: EPF, ESI, professional tax, TDS, each split employee / employer
  where the split exists

**Out of scope**

- Any table, any write, any cache
- The screen — `W-47.5`
- The onboarding checklist (`dashboardPage/onboardingDashboard.js:18-104`) — it is tenant
  setup state, `W-12` / `W-47.5`, not payroll
- "Upcoming payments" (`index.js:31-34`) — invented data with no source; the next `pay_date`
  is on the current run already
- The bank-mismatch flag (`DashboardServiceImpl.java:151-171`) — `W-29.1` records
  `NO_BANK_DETAILS` per employee, which is the same fact with a name on it
- HRMS dashboards — `W-44`

## 3. Flow

```
[payroll officer | finance] --> GET /api/v1/payroll/dashboard?fy=2026 --> payroll.run.read
   tenant = TenantContext (shared/.../tenant/TenantContext.java:30); never a header
   fy     = the financial year starting 1 April of ?fy, default the one containing today
            (DashboardServiceImpl.java:174-177 — the April rule, ported)

   employees.active_today       = EmployeeService.listEmployedBetween(today, today).size()   (core seam, W-29.1 §4)
   employees.as_at_run          = latest non-cancelled payrun: included_count, skipped_count,
                                  skipped grouped by skip_reason                              (payroll.employee_payrun, W-29.1)
   current_run                  = that run: id, period, status, pay_date, paid_on,
                                  progress_done / progress_total (W-29.4), total_gross,
                                  total_deductions, total_net_pay (W-29.2)
   recent_runs[6]               = non-cancelled runs, newest period first, same shape minus progress
   months[Apr..Mar]             = payrun rows with period in fy and status in (COMPUTED, APPROVED, PAID):
                                  period, status, total_gross, total_deductions, total_net_pay,
                                  tax = Σ employee_payrun_line.amount where source = 'TAX'
   year_totals                  = Σ of the PAID months only
   statutory[EPF, ESI, PT, TDS] = Σ employee_payrun_line.amount over PAID runs in fy,
                                  grouped by component_code (W-31.4 codes) and line_kind:
                                  DEDUCTION = employee share, BENEFIT = employer share
```

Every query is one JPQL or native statement bound to `tenant_id`; nothing is filtered in
Java after a `findAll` (the DEBT-022 shape at `DashboardServiceImpl.java:225`). Money is
`BigDecimal`, scale 2 in the response, from `numeric(19,4)` columns. A tenant with no runs gets
zeros and empty lists, not `404`.

## 4. Backend changes

All new under `code/backend/payroll/src/main/java/com/infinevo/payroll/dashboard/`.

| Layer | File | Change |
|---|---|---|
| Controller | `PayrollDashboardController.java` | new — one `GET`, `@RequiresAction("payroll.run.read")` |
| Service / ServiceImpl | `PayrollDashboardService`, `PayrollDashboardServiceImpl` | new — `summary(Integer fy)`; `@Transactional(readOnly = true)` |
| Repository | `DashboardQueryRepository.java` | new — the aggregate queries: months, statutory by code, skipped by reason. Reads `payroll.payrun`, `payroll.employee_payrun`, `payroll.employee_payrun_line`; **no new finder on `W-29`'s repositories** so the two tickets do not collide |
| DTO | `PayrollDashboardResponse` with nested `Employees`, `RunCard`, `MonthRow`, `StatutoryTile` | new — `status` / `message` / `data` envelope, `CONVENTIONS.md` §3 |
| Unit helper | `FinancialYear.java` | new — `of(int startYear)`, `containing(LocalDate)`, `start()`, `end()`, `months()`; pure, no Spring |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/payroll/dashboard` | `?fy=` optional, the year the financial year starts (`2026` = Apr 2026 to Mar 2027); `400` outside 2000–2100 | `200` `PayrollDashboardResponse` | `payroll.run.read` |

`PayrollDashboardResponse`:

| Field | Shape | Source |
|---|---|---|
| `financial_year` | `{start: "2026-04-01", end: "2027-03-31"}` | computed |
| `employees` | `{active_today, as_at_run: {payrun_id, period, included, skipped, skipped_by_reason: {NO_SALARY: n, NO_BANK_DETAILS: n, …}}}` — `as_at_run` null when no run | core seam; `employee_payrun` |
| `current_run` | `RunCard` or null | `payrun` |
| `recent_runs` | `RunCard[]`, at most 6 | `payrun` |
| `months` | `MonthRow[]` — `{period, status, gross, deductions, tax, net_pay}`, only months with a run | `payrun`, `employee_payrun_line` |
| `year_totals` | `{gross, deductions, tax, net_pay, paid_runs}` over `PAID` | derived |
| `statutory` | `{epf: {employee, employer}, esi: {employee, employer}, professional_tax: {employee}, tds: {employee}}` | `employee_payrun_line` |

`RunCard`: `{payrun_id, period, status, pay_date, paid_on, included, skipped, progress_done,
progress_total, gross, deductions, net_pay}`. Progress fields are null unless `COMPUTING`.

Statutory codes: `EPF_EMPLOYEE`, `EPF_EMPLOYER`, `EPS_EMPLOYER`, `EDLI`, `EPF_ADMIN` sum into
`epf` (`W-31-3-…md:140`); `ESI_EMPLOYEE`, `ESI_EMPLOYER` into `esi` (`:141`);
`PROFESSIONAL_TAX` into `professional_tax` (`W-31-4-…md:79`); every `source = 'TAX'` line into
`tds` (`W-36-1-…md:278`). Unknown statutory codes are ignored, not an error.

Permission codes exist: `payroll.run.read` is held by `payroll-officer` and `finance`
(`code/backend/migration/src/main/resources/db/migration/core/V025__catalogue_correction.sql:198`, `:212`).
No new code.

Not ported: the three legacy endpoints as three calls (one screen, one round trip);
`firstPayrollCompleted` (`DashboardResponse.java:10` — never set); `paymentDue`
(`DashboardServiceImpl.java:136` — `pay_date < today` and not `PAID`, which the screen can
compute from the card).

## 5. Frontend changes

None. `W-47.5` builds the screen against §4.

## 6. Database changes

None. Creates no table, no column, no index, no Flyway script.

- [x] no new table, so nothing to carry `tenant_id` or RLS; every read is bound to the tenant
  and runs under the existing `payroll.*` policies as `app_user`
- [x] no schema change, no Flyway script
- [x] `BigDecimal` throughout; nothing floating (BUG-011 fixed for the response)
- [x] Index on `tenant_id` plus lookup columns (DEBT-018): the reads use
  `idx_payrun_tenant_status` (`W-29.1` §6) and `idx_employee_payrun_line_tenant_run`
  (`W-29.2` §6). The month and statutory queries filter by `payrun.period` after the tenant
  index; add an index only if the query plan on 36 months shows a sequential scan
- [x] expand / contract — nothing to sequence

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../dashboard/FinancialYearTest.java` | 31 March 2026 is FY 2025; 1 April 2026 is FY 2026; `months()` is April to March in order; `fy=1999` and `fy=2101` are rejected |
| Unit | `payroll/.../dashboard/PayrollDashboardServiceTest.java` | statutory grouping: five EPF codes sum by share, unknown code ignored, `BENEFIT` is employer; `year_totals` counts `PAID` only; no runs gives zeros and empty lists, not null |
| Integration | `payroll/.../dashboard/PayrollDashboardIT.java` | **the acceptance test**: two runs in one tenant, one `PAID` and one `COMPUTED`, one skipped employee with `NO_BANK_DETAILS`; `GET` returns `current_run` = the newest, two `months`, `year_totals` = the `PAID` run alone, `employees.as_at_run.skipped_by_reason.NO_BANK_DETAILS = 1`; a second tenant's run never appears (`RlsIT` pattern, cross-tenant read) |
| Integration | same file | `finance` role gets `200`; `employee` role gets `403`; a cancelled run is in neither `recent_runs` nor `months` |

## 8. Verification

```bash
cd code/backend && ./mvnw -q -pl payroll -am spotless:check test -Dtest='FinancialYearTest,PayrollDashboardServiceTest'
cd code/backend && ./mvnw -q -pl payroll -am verify -Dit.test=PayrollDashboardIT
```

| Check | Expected | Result |
|---|---|---|
| unit and IT above | `BUILD SUCCESS`, the IT green | |
| `curl -s -H "Authorization: Bearer $OFFICER" localhost:8080/api/v1/payroll/dashboard \| jq .data.current_run.status` | the newest run's status, e.g. `"PAID"` | |
| same call with an `employee` token | `403` | |
| `?fy=2101` | `400` with the validation envelope | |
| `check-done.mjs` | no new table, no `double`, no `ddl-auto` | |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `W-29.2`'s column names move before this branch starts | medium | the queries name only `total_gross`, `total_deductions`, `total_net_pay`, `source`, `component_code`, `line_kind`, `amount`, `skip_reason`; read `main` at branch time |
| Statutory tiles read zero until `W-31.4` and `W-36.1` merge | certain, short-lived | the response shape is fixed now; the IT asserts the `epf` sum with hand-inserted `STATUTORY` lines so the query is proven without `W-31.4` |
| `active_today` lists every employee to count them | low at current tenant sizes | one call, one query; a `count` seam on `EmployeeService` is a one-line `core` change if a tenant passes a few thousand employees |
| The screen shows a stale year after 1 April | low | the default is computed per request from today; nothing is cached |

## 10. Rollback

Nothing is deployed and nothing is stored. Remove the package; no data changes.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | creates no table |
| Flyway only, `ddl-auto` nowhere | no script |
| `Money`/`BigDecimal` for money | response amounts `BigDecimal` scale 2 |
| Index on `tenant_id` plus lookup columns | no new lookup; existing `W-29` indexes reused |
| Expand / contract | nothing to sequence |
| No module references another module | `payroll` reads `core` through `EmployeeService.listEmployedBetween` only; `shared` for `TenantContext` and the envelope |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-028 fake tiles, summary never called | **Fixed for new code** — one endpoint the screen must call; `W-47.5` has no constants to fall back on |
| DEBT-022 unscoped finders | **Fixed** — every query binds `tenant_id` |
| BUG-011 money as `Double` | **Fixed** for the response |
| DEBT-007 no `/api/v1` · DEBT-008 hand-built envelope | **Fixed** |
| DEBT-001, DEBT-031 dead `dashboardcopy.js` | **Discounted** — frozen tree, nothing ported from it |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | One endpoint or three, as legacy? | **One.** One screen, one round trip; the three legacy calls share the same year window anyway |
| 2 | Where does "employee not ready" come from? | **The last run's `SKIPPED` rows**, which `W-29.1` already records with a reason. Legacy re-derived it by looping every employee and calling two repositories each (`DashboardServiceImpl.java:90-115`). A joiner after the last run shows in `active_today` and not in `as_at_run` until the next run; the response says which run it is |
| 3 | Which runs count towards the year? | **`PAID` for totals; `COMPUTED`, `APPROVED`, `PAID` shown as months** with their status. Legacy summed every `employee_payrun` row whatever the status (`:178`), so a draft looked like money paid |
| 4 | Statutory figures from declared earnings or from the run? | **From the run's lines.** Legacy summed declared earnings flagged EPF/ESI (`:225-240`), which is a base, not a contribution, and set the employer share to zero (`:243`) |
| 5 | Cache? | **No.** Six small tenant-bound queries; caching would be the first thing to hide a wrong number |
| 6 | `readonly_user`? | **Not for this ticket.** `W-23.2`'s report path does not yet use it either (`active-work.md`, 2026-09-29); when the platform routes reads to the replica, this endpoint is the first candidate |
