# Feature: Pay run — loss of pay and pay input collection

| Field | Value |
|---|---|
| **Feature ID** | `W-29.3` · from ticket #35 · `PAY-05` part 3 of 4 |
| **Promoted to** | `docs/target-state/features/W-29-3-pay-run-lop-and-inputs.md` on the developer's `dev-<name>` branch — **`W-29-3` with hyphens**, never `W-29.3`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-003 (fixed by `W-19`, honoured here), BUG-005 (fixed by replacement), DEBT-018, DEBT-022; BUG-015 proposed below |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-29.2` — the contributor loop and the line table · `W-18.1` — `WorkingDayBasisCalculator.basisFor` · `W-19` — `PayInputService.forPeriod`. `W-16.4a` and `W-39.2` are the writers of the inputs, and are **not** blockers: an empty ledger is a valid input |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V058` — three day-count columns on `payroll.employee_payrun`, expand only | 1 |
| Externally testable behaviour | an employee with loss-of-pay days, a mid-month joining date and pay inputs for the month gets a payslip that is the full-month figure scaled and adjusted, to the rupee | 1 |
| Frontend area | none — `W-47` | 1 |

Within cap.

## What this part owns, and what it leaves

Two contributors in `W-29.2`'s loop, and nothing else:

| Contributor | Source tag | Does |
|---|---|---|
| `LopLineContributor`, `@Order(200)` | `LOP` | one `DEDUCTION` line for the days not paid — loss of pay plus days outside the employment window |
| `PayInputLineContributor`, `@Order(300)` | `PAY_INPUT` | one line per ledger kind with money in it |

Left to others: the policy stamp columns and the explain endpoint (`W-18.2`), statutory
(`W-31`), tax (`W-36`), and **who writes the ledger** — leave (`W-16.4a`), overtime (`W-39.2`),
reimbursements and ad-hoc deductions (`W-35`). This ticket reads `core.pay_input` and no
module's tables.

---

## 1. Problem

Loss of pay in the frozen system is where the cross-service coupling and the money loss meet.

- **LOP days arrive over HTTP from HRMS, by email address.** `fetchLeaves(employeeEmails, payPeriod)`
  (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/payruns/EmployeePayRunServiceImpl.java:1091`)
  is a blocking call with no fallback — BUG-005. `legacy/docs/FEATURE_MAP.md:346` claims this
  was replaced by a local table; the code says otherwise (`.claude/outputs/2026-09-25-analyze-w29-pay-run.md` § Doc drift)
- **The divisor is calendar days, always.** `perDayPay = monthlySalary / totalPeriodDays` (`:1165`),
  which `W-18.1` replaces with a configured basis
- **Pro-rating is applied to net pay, not to earnings** (`:1172-1174`), so a joiner's statutory
  deductions and reimbursements are scaled along with their salary
- **Leavers are not pro-rated at all.** `effectiveStart` handles a joining date (`:1133-1140`);
  nothing handles a termination date, so a person who left on the 3rd is paid the month
- **A negative net is silently floored to zero** (`:1178-1180`), and the shortfall is never
  recovered
- **The bonus is added after LOP** (`:1176`) — so it escapes pro-rating, while `W-29.2` fixes the
  larger defect that it was never paid at all
- **Inputs are marked `INPAYRUN` on the source tables** (`:1213-1220`, `:1223-1238`) and unmarked
  on reject or delete (`PayRunServiceImpl.java:761`, `:876`) — state scattered across three
  tables that `W-19`'s period lock replaces

## 2. Scope

**In scope**

- `lop_days`, `unpaid_days`, `paid_days` on `payroll.employee_payrun`, `numeric(10,2)`
- The `LOP` contributor: the deduction line and the day figures
- The `PAY_INPUT` contributor: `OVERTIME`, `REIMBURSEMENT`, `AD_HOC_DEDUCTION`, `ONE_TIME_PAYOUT` as lines; `LOP_DAYS` as the day count the `LOP` contributor reads
- Reading `basisFor` once per employee and failing that employee when it throws
- Negative net pay kept as a negative number, counted on the run

**Out of scope**

- Recording the policy, basis, divisor, payable days and rounding on the row, and `/explain` — `W-18.2`; it reads the values this ticket already obtains
- Pricing an overtime row that carries hours and no amount (`W-39.2` §2 leaves this open) — **not paid here**, see §3
- Recovering a negative net from the next period — its own ticket when `W-30` is scoped
- Writing to `core.pay_input`, reversing an input, or marking a source row — `W-19` owns the ledger; the lock at `W-29.1` is the only "consumed" state

## 3. Flow

```
for each INCLUDED employee, inside W-29.2's loop, after STRUCTURE:
   basis   = WorkingDayBasisCalculator.basisFor(tenant, period, employee)   (W-18.1) -- throws => computation_error, next employee
   inputs  = the employee's slice of PayInputService.forPeriod(period)       (W-19, read once per run)
   lop_days      = Σ quantity of kind LOP_DAYS, min(basis.payableDays)
   unpaid_days   = lop_days + calendar days of the period outside [date_of_joining, termination_date]
   paid_days     = basis.payableDays − unpaid_days, floor 0
   LOP line      = DEDUCTION, source LOP, code LOP, amount = Σ(pro-rata STRUCTURE lines) ÷ basis.divisor × unpaid_days
   PAY_INPUT lines, one per kind:
       OVERTIME         → EARNING       Σ amount   (rows with amount null contribute nothing; counted as unpriced)
       ONE_TIME_PAYOUT  → EARNING       Σ amount
       REIMBURSEMENT    → REIMBURSEMENT Σ amount, is_taxable = false
       AD_HOC_DEDUCTION → DEDUCTION     Σ amount
then W-29.2 sums; net_pay may be negative; payrun.negative_net_count is reported
```

**Which lines are pro-rata:** a `STRUCTURE` line whose catalogue component has `is_pro_rata = true`
(`W-26.1` §6, `earning` and `benefit`). Benefits are scaled for the employer-cost figure but were
never in net (`W-29.2` §3). Reimbursements and variable earnings are never scaled — the
periodic rule already decides whether a bonus is due this month (`W-29.2` §3); legacy
agreed by adding the bonus after LOP (`:1176`).

**Rounding:** `Money` scale 4 throughout; the LOP line is rounded once, per the policy's
`lop_rounding` (`W-18.1` §6), `HALF_UP_2` by default; `net_pay` rounds as `W-29.2` §3.

**Employment window,** replacing `:1133-1140` and adding the leaver case: `date_of_joining`
and `termination_date` come from the `EmployeeResponse` that `W-29.1`'s inclusion already
holds. Days outside the window are **calendar** days, converted with the same `divisor` — the
legacy rule for joiners (`:1165`, `:1172`), now applied to leavers too. Under `ACTUAL_DAYS`
this is exact; under `ORG_DAYS` and `FIXED_30` it is the documented approximation
(decision 2).

**Overtime with hours and no amount** (`W-39.2` §4: `amount` may be null): no line, and the
row's response carries `unpriced_input_count`. A run does not silently pay zero for hours
someone approved, and it does not invent a rate.

## 4. Backend changes

All under `code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/`.

| Layer | File | Change |
|---|---|---|
| Contributor | `LopLineContributor.java` | new, `@Order(200)`; §3 |
| Contributor | `PayInputLineContributor.java` | new, `@Order(300)`; §3 |
| Service (change) | `PayRunComputationServiceImpl` (`W-29.2`) | calls `PayInputService.forPeriod(period)` **once** before the loop and puts each employee's slice on `PayRunEmployeeContext`; catches `NoLopPolicyException` as a per-employee failure like any other |
| Context (change) | `PayRunEmployeeContext` | gains `employee` (joining and termination dates), `basis`, `payInputs` |
| Entity (change) | `EmployeePayRun.java` | `lopDays`, `unpaidDays`, `paidDays` as `BigDecimal`; `unpricedInputCount int` |
| Entity (change) | `PayRun.java` | `negativeNetCount int` |
| Repository (change) | `W-26.1`'s `EarningRepository`, `BenefitRepository` | `findByTenantIdAndIdIn` for the `is_pro_rata` lookup, one query per run |
| DTO (change) | `EmployeePayRunResponse`, `PayRunResponse` | the new figures |

No new endpoint. `POST /compute` (`W-29.2`) now produces these lines.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V058__employee_payrun_days.sql` | `ALTER payroll.employee_payrun`, `ALTER payroll.payrun` | yes (existing tables) | additive |

`V058` extends the payroll lane's block by one (`DEV-TRACKER.md` § lanes).

**`employee_payrun` gains:** `lop_days numeric(10,2) NOT NULL DEFAULT 0` ·
`unpaid_days numeric(10,2) NOT NULL DEFAULT 0` · `paid_days numeric(10,2) NOT NULL DEFAULT 0` ·
`unpriced_input_count int NOT NULL DEFAULT 0`.
**`payrun` gains:** `negative_net_count int NOT NULL DEFAULT 0`.

Not added here, on purpose: `payable_days`, `pay_divisor`, `lop_policy_id`, `working_day_basis`,
`lop_rounding` — `W-18.2` §6 owns those five, and this ticket's contributor already has the
values in hand when `W-18.2` comes to store them.

| Legacy field | Here |
|---|---|
| `totalNoOfLeaves Double` (`EmployeePayRun.java:78`) | `lop_days numeric(10,2)` — half days survive (BUG-003) |
| `paidDays Double` (`:60`) — calendar days from joining (`:1141`) | `paid_days`, from the basis and the window |
| `LOP Double` (`:80`) | the `LOP` line in `employee_payrun_line` |
| `claimDeduction`, `claimReimbursement`, their statuses (`:99-108`) | `PAY_INPUT` lines; no status — the period lock is the state |
| `monthlyTds` (`:96`) | `W-36` |

- [x] No new table; `tenant_id` and RLS already on both
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — none added; the new columns are not looked up
- [x] Day counts are `numeric(10,2)` (`CONVENTIONS.md:37`); money stays on the line table at `numeric(19,4)`; nothing floating
- [x] Expand / contract — columns with defaults; nothing dropped

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../payrun/LopLineContributorTest.java` | full month, no LOP: no line, `paid_days = payableDays`; 1.5 LOP days under `FIXED_30`; joiner on the 16th under `ACTUAL_DAYS` in a 31-day month: `unpaid_days = 15`; leaver on the 10th: `unpaid_days = 21`; LOP capped at `payableDays`; only pro-rata lines in the base; benefit scaled, reimbursement not; rounding per `lop_rounding` |
| Unit | `payroll/.../payrun/PayInputLineContributorTest.java` | one line per kind; two `REIMBURSEMENT` rows sum to one line; a reversal pair nets to nothing; overtime with null amount gives no line and `unpriced_input_count = 1`; `LOP_DAYS` produces no money line |
| Unit | `payroll/.../payrun/PayRunHandCalculationTest.java` | **the worked example in §8**, to the rupee, through both contributors and `W-29.2`'s sum |
| Integration | `payroll/.../payrun/PayRunLopIT.java` | a real `W-18.1` policy, a `LOP_DAYS` row written through `PayInputService.record`, lock, compute: the `LOP` line and `lop_days` match; the same employee in a second tenant with a different basis gets a different line |
| Integration | `payroll/.../payrun/PayRunNoPolicyIT.java` | a tenant whose policy row is deleted: every included row carries `computation_error` naming `NoLopPolicyException`, the run is `FAILED`, no line is written for them |
| Integration | `payroll/.../payrun/PayRunNegativeNetIT.java` | an `AD_HOC_DEDUCTION` larger than the salary: `net_pay` is negative, stored as such, `negative_net_count = 1` |
| Integration | `payroll/.../payrun/PayRunInputsQueryCountIT.java` | 20 employees with inputs: `forPeriod` is called once; the pro-rata flag lookup is one query per catalogue table (`W-55`) |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

The worked example, continuing `W-29.2` §8's employee (gross 45,000, reimbursements 2,000,
benefits 1,800; Basic, HRA, Special allowance and the employer PF benefit are pro-rata; the
meal card lines are not). July, `ACTUAL_DAYS`, weekends payable: `payableDays = divisor = 31`.
Joined 11 July; 1.5 LOP days; a 3,000 overtime input and a 500 ad-hoc deduction.

| Figure | Value |
|---|---|
| days outside window (1–10 July) | 10 |
| `lop_days` | 1.50 |
| `unpaid_days` | 11.50 |
| `paid_days` | 19.50 |
| pro-rata base (25,000 + 10,000 + 7,500) | 42,500.0000 |
| LOP line = 42,500 ÷ 31 × 11.5 | 15,766.1290 → **15,766.13** |
| overtime line (EARNING) | 3,000.0000 |
| ad-hoc deduction line | 500.0000 |
| `gross_earnings` | 48,000.0000 |
| `total_deductions` | 16,266.1300 |
| `net_pay` = 48,000 + 2,000 − 16,266.13 | **33,733.87** |
| `total_benefits` (1,800 scaled, reported only) | 1,132.26 |

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type, numeric_precision, numeric_scale FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='employee_payrun'
      AND column_name IN ('lop_days','unpaid_days','paid_days','payable_days','pay_divisor');"
cd code/backend && mvn -q verify
grep -rn "implements PayLineContributor" code/backend/payroll/src/main/java | wc -l
grep -rn "hrms\.\|leave_monthly_lop\|overtime_request\|WebClient" code/backend/payroll/src/main/java | wc -l
```

| Check | Expected |
|---|---|
| Columns | three rows, each `numeric` `10,2`; **no** `payable_days` or `pay_divisor` row — those are `W-18.2`'s |
| Suite | green, no skips; `PayRunHandCalculationTest` asserts `33733.87` |
| Contributors | `3` — `STRUCTURE`, `LOP`, `PAY_INPUT` |
| Module reach | `0` — payroll reads the ledger through `PayInputService` and nothing else |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| LOP applied to net pay, as `:1172-1174` | **high — it is the legacy shape** | The base is the sum of pro-rata `STRUCTURE` lines; the unit test has a reimbursement that must survive unscaled |
| `forPeriod` called per employee — N queries on the hottest path | medium | Called once before the loop; `PayRunInputsQueryCountIT` |
| A negative net floored to zero, as `:1178` | medium | Stored negative; `PayRunNegativeNetIT`; the count on the run is what `W-37` shows |
| A tenant with no policy is paid on a guessed divisor | medium | `basisFor` throws and the employee fails (`W-18.1` §13 decision 1); `PayRunNoPolicyIT` |
| Hours-only overtime silently paid zero, or priced from a made-up rate | medium | No line, `unpriced_input_count` on the row; pricing is a named non-goal |
| The five stamp columns are added here "since we have the values" | medium | §6 lists them as not added; the verification asserts their absence |

## 10. Rollback

Nothing is deployed. The script is additive. A recompute (`W-29.2`) rebuilds every line
from the locked ledger and the policy in force.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | no new table |
| Flyway only, `ddl-auto` nowhere | one script, `V058` |
| `Money`/`BigDecimal` for money | `Money` in both contributors; day counts `BigDecimal` at `numeric(10,2)` |
| Index on `tenant_id` plus lookup columns | none needed; no new lookup |
| Expand / contract | columns with defaults only |
| No module references another module | `payroll` calls `core`'s `WorkingDayBasisCalculator` and `PayInputService`, and `shared`. It never sees `hrms`, which is the point of the ledger |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-003 half-day LOP lost | **Honoured** — `W-19` fixed the column; `lop_days` keeps two decimals end to end |
| BUG-005 run fails when HRMS is down | **Fixed by replacement** — no HTTP call; the ledger is local and locked |
| DEBT-018 no indexes | **Honoured** — nothing to index |
| DEBT-022 unscoped finders | **Fixed** — the two catalogue finders take `tenantId` |
| Hard-coded divisor (`W-18.1` §12) | **Fixed here** — the divisor comes from `basisFor` |

**Proposed GAP entry** — not yet in `legacy/docs/GAP_INVENTORY.md`:

| ID | Category | Finding |
|---|---|---|
| BUG-015 | Payroll / Pay run | Leavers are paid the full month: only the joining date shortens `eligibleDays` (`EmployeePayRunServiceImpl.java:1133-1140`); `termination` is never read. Pro-rating is applied to net pay (`:1172-1174`) so deductions scale too, and a negative result is floored to zero (`:1178-1180`) with no recovery |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Scale the whole net, as legacy, or only pro-rata components? | **Only components flagged `is_pro_rata`.** A fixed reimbursement or a due bonus is not smaller because someone joined on the 16th |
| 2 | Days outside the employment window: calendar days, or payable days in the window? | **Calendar days, converted with the policy divisor** — the legacy rule (`:1165`, `:1172`), now for leavers too. Exact under `ACTUAL_DAYS`; an approximation under the other two bases that `W-18.2`'s stamp makes explainable. A windowed `basisFor` is `W-18.1`'s to add if a customer on `ORG_DAYS` asks |
| 3 | One `LOP` line or one per component? | **One line.** The payslip shows "Loss of pay: 11.5 days"; the per-component split is derivable and nobody reads it |
| 4 | Negative net? | **Stored as negative, counted on the run.** Flooring hides a recovery the employer is owed; carrying it forward is a later ticket |
| 5 | Overtime with no amount? | **Not paid, counted as unpriced.** `W-39.2` stores what was typed; a rate policy does not exist and this ticket will not invent one |
| 6 | Where does the pay run learn that an input was consumed? | **It does not need to.** `W-29.1`'s lock freezes the period; the legacy `INPAYRUN` flags on three tables are not ported |
