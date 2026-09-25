# Feature: Pay run — earnings and deductions computation

| Field | Value |
|---|---|
| **Feature ID** | `W-29.2` · from ticket #34 · `PAY-05` part 2 of 4 |
| **Promoted to** | `docs/target-state/features/W-29-2-pay-run-computation.md` on the developer's `dev-<name>` branch — **`W-29-2` with hyphens**, never `W-29.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-011 (proposed, fixed here), BUG-012–BUG-014 (proposed below), DEBT-008, DEBT-018, DEBT-022 |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-29.1` — the run and its rows · `W-26.2` — `versionInForce` · `W-27.2` — the FBP declared amounts on that read |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V057` — one new table, `payroll.employee_payrun_line`, plus the money columns added to `W-29.1`'s two tables in the same script (expand only) | 1 |
| Externally testable behaviour | computing a locked run writes every included employee's salary lines and net pay, and the figures match a hand calculation to the rupee | 1 |
| Frontend area | none — `W-47` | 1 |

Within cap. One script; the `ALTER`s are the columns `W-29.1` §6 said this ticket would add.

## What this part owns, and what it leaves

This part turns a `LOCKED` run into a `COMPUTED` one by reading each included employee's
salary version and writing **lines**. It computes **full-month structure amounts only**.
Everything that changes those amounts is a later contributor, plugged into the same loop:

| Contributor | Source tag | Ticket |
|---|---|---|
| Salary structure — earnings, benefits, reimbursements, FBP | `STRUCTURE` | **this ticket** |
| Payable-days scaling for joiners and leavers, loss of pay, `W-19` pay inputs | `LOP`, `PAY_INPUT` | `W-29.3` |
| Provident fund, state insurance, professional tax | `STATUTORY` | `W-31` |
| Tax deducted at source | `TAX` | `W-36` (with `W-33`) |

**The loop is the design.** `PayRunComputationService` runs every `PayLineContributor` bean
in `@Order`, then sums. A later ticket adds a bean; it never edits this service.

---

## 1. Problem

The frozen computation is one 230-line method, `mapToPayRunDTO`
(`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/payruns/EmployeePayRunServiceImpl.java:211-437`).
It is where the money goes wrong.

- **Every figure ends as `double`.** Computed in `BigDecimal`, then `.doubleValue()` into
  `EmployeePayRun` (`:409-431`; entity `.../entity/payruns/EmployeePayRun.java:39-102`). BUG-011
- **No lines are stored, only totals.** A payslip re-reads the CTC at view time
  (`buildEarnings`, `:1672-1700`), so a salary revision rewrites every past payslip
- **The employer's PF share is deducted from the employee.** `calculateEpfEmployer` sums
  `EPF_EMPLOYER` (`:948-988`) and the result becomes `totalDeductions` (`:298`)
- **Employer benefits are paid out.** `netPay = earnings + benefits + reimbursements + claims − deductions`
  (`:393-395`) — `totalBenefits` is the employer-contribution side of the structure
- **The bonus is computed and never paid.** `bonusAmount` (`:379-390`) is stored (`:417`) but
  absent from the net-pay line (`:393`)
- **Paid days are the literal `30.0`** (`:419`)

## 2. Scope

**In scope**

- `payroll.employee_payrun_line` — one row per component per employee per run, with the
  code and name as they were on the day
- Money columns on `employee_payrun` and `payrun`, `numeric(19,4)`
- `POST /payruns/{id}/compute`, synchronous, `LOCKED | COMPUTED | FAILED → COMPUTING → COMPUTED | FAILED`
- The `STRUCTURE` contributor: fixed earnings, periodic variable earnings, benefits,
  reimbursements, FBP declared and unallocated split
- The `PayLineContributor` port and the summation rule

**Out of scope**

- Anything in the contributor table above marked for another ticket
- Pro-rating by payable days — `W-29.3`; here every amount is the full month
- Running on the worker — `W-29.4`; this endpoint blocks until done, which for 100
  employees is seconds
- Arrears for a back-dated revision (`W-26.2` §2) — not in `W-29` at all; raised as its own ticket when `W-30` is scoped
- Screens — `W-47`

## 3. Flow

```
[payroll officer] --> POST /payruns/{id}/compute --> permission payroll.run.execute
   --> status LOCKED|COMPUTED|FAILED, else 409 --> status COMPUTING
   --> delete this run's lines (a recompute starts clean)
   --> for each employee_payrun INCLUDED, in one transaction per employee:
         version = EmployeeSalaryService.versionInForce(tenant, employee, period_end)   (W-26.2 + W-27.2)
         for each PayLineContributor in @Order: lines += contributor.contribute(ctx)
         gross_earnings      = Σ EARNING
         total_reimbursements= Σ REIMBURSEMENT
         total_benefits      = Σ BENEFIT          (reported, never paid)
         total_deductions    = Σ DEDUCTION
         net_pay             = (gross_earnings + total_reimbursements − total_deductions).toAmount()
   --> payrun totals = Σ over rows; status COMPUTED, computed_at
   --> any employee throws --> that row keeps computation_error; run status FAILED after the loop
```

**The `STRUCTURE` contributor**, replacing `:240-300` and `:372-390`:

| Version line | Rule | Legacy |
|---|---|---|
| earning, `is_enabled`, not variable | `EARNING`, `monthly_amount` | `:251-254` |
| earning, variable, `earning_frequency` | `EARNING`, the periodic rule below, **and it counts in gross** | `:379-390` computed, `:393` never paid |
| earning flagged FBP (`W-27.2`) | two `EARNING` lines: `declared_monthly_amount` with `is_taxable = false`, and the unallocated remainder with `is_taxable = true` | `W-27.2` §13 decision 3 |
| benefit, `is_enabled` | `BENEFIT`, `monthly_amount` — summed into `total_benefits`, **not into net** | `:263-266`, added to net at `:393` |
| reimbursement, `is_enabled` | `REIMBURSEMENT`, `monthly_amount`; FBP split as for earnings | `:274-277` |

**Periodic rule** for a variable earning, from `getPeriodicBonusAmount` (`:449-520`), with
`months_completed` = whole months from `date_of_joining` to the period:

| `earning_frequency` | Paid in | Amount | Unless |
|---|---|---|---|
| `MONTHLY` | every period | `monthly_amount` | — |
| `QUARTERLY` | Jan, Apr, Jul, Oct | `annual_amount / 4` | `months_completed < 3` |
| `HALF_YEARLY` | Jan, Jul | `annual_amount / 2` | `months_completed < 6` |
| `YEARLY` | Jan | `annual_amount` | `months_completed < 12` |

`MONTHLY` is new: legacy returned zero for it (`:518`), the only frequency the screen never
offered (`legacy/Payroll-Fend-react/src/pages/mainPages/employee/editSalaryDetails.js:686`).
Division uses `Money.divide` at scale 4; the row's `net_pay` is the only value rounded to 2.

**Status transitions added:** `LOCKED → COMPUTING`, `COMPUTED → COMPUTING`, `FAILED → COMPUTING`,
`COMPUTING → COMPUTED`, `COMPUTING → FAILED`. No new status; `W-29.1` §3 fixed the vocabulary.

## 4. Backend changes

All under `code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/`.

| Layer | File | Change |
|---|---|---|
| Entity | `EmployeePayRunLine.java` | new, `@Table(schema = "payroll")`, `@Audited`; `Money`-typed amount via the `shared` converter |
| Entity (change) | `EmployeePayRun.java`, `PayRun.java` (`W-29.1`) | the money columns and `computed_at` |
| Repository | `EmployeePayRunLineRepository.java` | new; `deleteByTenantIdAndPayrunId`, `findByTenantIdAndEmployeePayrunId` |
| Port | `PayLineContributor.java` | new — `List<PayLine> contribute(PayRunEmployeeContext ctx)`; `ctx` carries the run, the row, the `SalaryVersionResponse`, the period |
| Contributor | `StructureLineContributor.java` | new, `@Order(100)`; the table in §3 |
| Service | `PayRunComputationService`, `PayRunComputationServiceImpl` | new — the loop in §3, `REQUIRES_NEW` per employee |
| Service (change) | `PayRunService` | `compute(id)` |
| Enumeration | `LineKind`, `LineSource`, `EarningFrequency` | new |
| Controller (change) | `PayRunController.java` | one endpoint |
| DTO | `EmployeePayRunLineResponse`; `EmployeePayRunResponse` gains the five totals; `PayRunResponse` gains three | change |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/payroll/payruns/{id}/compute` | — | `200` the run, `COMPUTED` or `FAILED`, with totals; `409` from `DRAFT`, `COMPUTING`, `APPROVED`, `PAID`, `CANCELLED` | `payroll.run.execute` |
| GET | `/api/v1/payroll/payruns/{id}/employees/{employeeId}/lines` | — | the lines, in `sort_order`, with `computation_error` if any | `payroll.run.read` |

`W-29.1`'s `GET .../employees` row response gains `gross_earnings`, `total_deductions`,
`net_pay`. Permission codes exist (`V025__catalogue_correction.sql:198-199`).

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V057__employee_payrun_line.sql` | new `payroll.employee_payrun_line`; `ALTER` `payroll.employee_payrun`, `payroll.payrun` | yes | additive |

`V057` extends the payroll lane's block by one (`DEV-TRACKER.md` § lanes).

**`employee_payrun_line`** — new; legacy had no such table (§1):
`id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`employee_payrun_id uuid NOT NULL REFERENCES payroll.employee_payrun(id)` ·
`payrun_id uuid NOT NULL REFERENCES payroll.payrun(id)` — denormalised for the run-wide delete ·
`line_kind varchar(16) NOT NULL CHECK (line_kind IN ('EARNING','DEDUCTION','BENEFIT','REIMBURSEMENT'))` ·
`source varchar(16) NOT NULL CHECK (source IN ('STRUCTURE','LOP','PAY_INPUT','STATUTORY','TAX'))` ·
`component_id uuid` — no FK: it points into one of four catalogue tables ·
`component_code varchar(64) NOT NULL` · `component_name varchar(120) NOT NULL` — **snapshots**, so a
catalogue rename does not rewrite a paid month ·
`amount numeric(19,4) NOT NULL CHECK (amount >= 0)` — the kind carries the sign, `W-19`'s rule ·
`is_taxable boolean NOT NULL` · `sort_order int NOT NULL` · four audit columns.

**`employee_payrun` gains:** `gross_earnings`, `total_reimbursements`, `total_benefits`,
`total_deductions`, `net_pay` — each `numeric(19,4) NOT NULL DEFAULT 0` ·
`computed_at timestamptz` · `computation_error varchar(500)`.

**`payrun` gains:** `total_gross`, `total_deductions`, `total_net_pay` — `numeric(19,4) NOT NULL DEFAULT 0` ·
`computed_at timestamptz` · `failure_reason varchar(500)`.

| Legacy field | Here |
|---|---|
| `totalEarnings`, `totalDeductions`, `totalTaxes`, `totalBenefits`, `totalReimbursements`, `netPay` as `Double` (`EmployeePayRun.java:39-54`) | the five `numeric` totals; `totalTaxes` is a `STATUTORY`/`TAX` line sum, `W-31`/`W-36` |
| `monthlySalary` (`:57`) | not stored — it is the version's `monthly_ctc` |
| `bonus`, `bonusEarningExistForEmployee` (`:66-69`) | an `EARNING` line from a variable component |
| `monthlyTds`, `claimDeduction`, `claimReimbursement` and their statuses (`:96-108`) | `W-36`, `W-29.3` |
| `paidDays`, `totalNoOfLeaves`, `LOP` (`:60`, `:78-80`) | `W-29.3` |
| `PayRun.payrollTotal`, `totalNetPay`, `totalDeductions`, `totalPayrollCost` (`PayRun.java:50-87`) | the three run totals; the rest derive |

- [x] `tenant_id`, leading index column, on the new table
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `idx_employee_payrun_line_tenant_row (tenant_id, employee_payrun_id, sort_order)` · `idx_employee_payrun_line_tenant_run (tenant_id, payrun_id)` · `uk_employee_payrun_line_tenant_row_source_code UNIQUE (tenant_id, employee_payrun_id, source, component_code, is_taxable)` — the FBP split is two rows of one code, told apart by `is_taxable`
- [x] Money columns `numeric(19,4)`, `CHECK (amount >= 0)`; nothing floating — BUG-011 fixed for these tables
- [x] Expand / contract — one new table, columns added with defaults, nothing dropped

RLS and `tenant_isolation` in the exact `CASE` form — `migration/README.md` §row-level security.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../payrun/PeriodicEarningRuleTest.java` | every row of the periodic table; a joiner two months before July gets no quarterly line; `MONTHLY` pays every month; `annual_amount / 4` at scale 4, not 2 |
| Unit | `payroll/.../payrun/StructureLineContributorTest.java` | a version with three fixed earnings, one variable, one benefit, one reimbursement, one FBP earning declared at half its pool produces the expected lines; the benefit is in `total_benefits` and not in `net_pay`; a disabled line produces nothing |
| Unit | `payroll/.../payrun/PayRunSummationTest.java` | **the hand calculation:** the worked example in §8 to the rupee; rounding happens once, on `net_pay` |
| Integration | `payroll/.../payrun/PayRunComputeIT.java` | lock then compute: status `COMPUTED`, lines present, row and run totals equal the sums; compute again replaces lines (same count, no duplicates); compute from `DRAFT` is `409` |
| Integration | `payroll/.../payrun/PayRunComputeFailureIT.java` | one of three employees has a version whose component was deleted after inclusion: the other two compute, that row carries `computation_error`, the run is `FAILED`; a fix and a recompute reach `COMPUTED` |
| Integration | `payroll/.../payrun/PayRunLineSnapshotIT.java` | rename a catalogue component after compute; the line still shows the old name |
| Integration | `payroll/.../payrun/PayRunLineRlsIT.java` | as `app_user`, tenant A reads none of tenant B's lines |
| Integration | `payroll/.../payrun/PayRunComputeQueryCountIT.java` | 20 employees compute in a bounded number of statements — one version read per employee, one batch insert of lines (`W-55`) |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

The worked example the summation test and the founder both check. One employee, July,
`annual_ctc` 6,00,000 with a `YEARLY` variable earning:

| Line | Kind | Amount |
|---|---|---|
| Basic | EARNING | 25,000.0000 |
| HRA | EARNING | 10,000.0000 |
| Special allowance | EARNING | 7,500.0000 |
| Meal card (FBP, pool 2,500, declared 1,500) | EARNING, non-taxable | 1,500.0000 |
| Meal card, unallocated | EARNING, taxable | 1,000.0000 |
| Annual bonus, `YEARLY`, in July | EARNING | 0 — no line |
| Employer PF | BENEFIT | 1,800.0000 |
| Fuel reimbursement | REIMBURSEMENT | 2,000.0000 |
| **gross_earnings** | | **45,000.0000** |
| **total_reimbursements** | | **2,000.0000** |
| **total_benefits** | | **1,800.0000** — not in net |
| **net_pay** | | **47,000.00** |

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='payroll.employee_payrun_line'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT table_name, column_name, data_type, numeric_precision, numeric_scale
     FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name IN ('payrun','employee_payrun','employee_payrun_line')
      AND data_type IN ('double precision','real','numeric') ORDER BY 1,2;"
cd code/backend && mvn -q verify
grep -rn "doubleValue\|double \|float " code/backend/payroll/src/main/java/com/infinevo/payroll/payrun | wc -l
grep -rn "implements PayLineContributor" code/backend/payroll/src/main/java | wc -l
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Money columns | nine rows, every one `numeric` `19,4`; no `double precision` |
| Suite | green, no skips; `PayRunSummationTest` asserts `47000.00` |
| Floating point | `0` |
| Contributors | `1` — `StructureLineContributor` only |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| PF, PT or TDS get computed here "because legacy did" | **high** — they are in the same legacy method | The contributor count check expects exactly one; the `source` `CHECK` reserves the tags for the tickets that own them |
| Benefits added to net, as `:393` | medium | `StructureLineContributorTest` and the worked example both assert `total_benefits` outside `net_pay` |
| Amounts rounded to 2 at every line, so totals drift by paise | medium | `numeric(19,4)` and `Money` scale 4 everywhere; `net_pay` alone calls `toAmount()`; `PayRunSummationTest` checks the sum of 4-scale lines |
| A recompute leaves yesterday's lines beside today's | medium | Delete by `(tenant_id, payrun_id)` first, inside the run transaction; `PayRunComputeIT` counts lines after two computes |
| One bad employee fails the whole run silently | medium | `REQUIRES_NEW` per employee, `computation_error` on the row, run `FAILED` with the count in `failure_reason` |
| Lines point at `component_id` with an FK, then a catalogue soft-delete breaks history | low | No FK, snapshot code and name; `PayRunLineSnapshotIT` |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only. A `COMPUTED` run recomputes
from `LOCKED` data; nothing here is irreversible.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `payroll.employee_payrun_line` |
| Flyway only, `ddl-auto` nowhere | one script, `V057` |
| `Money`/`BigDecimal` for money | `Money` in the service, `numeric(19,4)` in the tables; §8 greps for floating point |
| Index on `tenant_id` plus lookup columns | three indexes, §6 |
| Expand / contract | new table and added columns with defaults; nothing dropped or renamed |
| No module references another module | `payroll` and `shared` only; the salary read is `payroll`'s own `EmployeeSalaryService` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-011 money as `Double` (proposed, `W-29.1` §12) | **Fixed** for `payrun`, `employee_payrun`, `employee_payrun_line` |
| DEBT-008 hand-built envelope | **Fixed** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** — every finder takes `tenantId` |
| BUG-003 half-day LOP | **Not touched** — `W-29.3` |
| BUG-005 HRMS coupling | **Discounted** — no HRMS call |

**Proposed GAP entries** — not yet in `legacy/docs/GAP_INVENTORY.md`, evidence in §1:

| ID | Category | Finding |
|---|---|---|
| BUG-012 | Payroll / Pay run | Employer PF share deducted from the employee's net: `calculateEpfEmployer` (`EmployeePayRunServiceImpl.java:948-988`) becomes `totalDeductions` (`:298`) |
| BUG-013 | Payroll / Pay run | Employer benefits paid out: `totalBenefits` added into `netPay` (`:393`) |
| BUG-014 | Payroll / Pay run | Variable earnings computed (`:379-390`) and stored (`:417`) but never added to `netPay` (`:393`); a `monthly` frequency always yields zero (`:518`) |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Store lines, or totals and re-read the structure like legacy? | **Lines, with code and name snapshots.** A payslip is a record of what was paid, not a view over today's structure |
| 2 | Where do PF, PT and TDS go? | **Their own contributors, in `W-31` and `W-36`.** This ticket builds the loop and the first contributor. Porting `calculateProfessionalTax` (`:785-850`) here would build the slab logic twice |
| 3 | One table for lines, or one per kind as `off_cycle_payrun_employee_earning` / `_deduction` suggest (`02-data-model.md:168`)? | **One table, `line_kind` column.** `W-30` should reuse it; `02-data-model.md` §4 is to be updated when `W-30` is scoped |
| 4 | Full-month amounts here, and scale in `W-29.3`? | **Yes.** Payable days need `W-18.1`'s basis and the leave consumption; that is one ticket's worth of joins. This ticket's figures are exact for anyone present all month, which is the hand-calculation check |
| 5 | Synchronous endpoint now? | **Yes.** `10-scoping.md:192`: get the calculation right before distributing it. `W-29.4` moves the same service behind the queue and this endpoint starts returning `202` |
| 6 | `numeric(19,4)` or `(19,2)`? | **`(19,4)`**, as `W-26.2`'s tables, so lines and structure agree. `net_pay` is rounded to 2 once |
