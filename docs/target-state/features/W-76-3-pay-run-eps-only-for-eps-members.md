# Feature: Pay run statutory follows the company switches and employee ticks every month

| Field | Value |
|---|---|
| **Feature ID** | `W-76.3` · `PAY-STATUTORY` |
| **Owner** | claude |
| **Apps touched** | `code/backend/payroll` |
| **Related gaps** | found at the `W-76` merge review; founder rules 2026-11-01: "everything switch based, once switched on at company level and selected for the employee then only applied, checked every month during pay run"; EDLI / admin as legacy; employer PF / ESI as legacy |
| **Status** | **Done** — `aaf53c21` |
| **Blocked by** | nothing |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | Each pay run applies PF, EPS, EDLI, admin and ESI from the company settings and the employee's ticks at that moment, not from the salary as saved | 1 |
| Frontend area | none | 1 |

---

## 1. Problem

| Today | Evidence |
|---|---|
| The pay run applies only the statutory rows saved with the salary, with the rates saved then. Turning EPF / ESI off for the company, or unticking an employee, still deducts until the salary is revised; ticking an employee after the save never applies | `code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun/StatutoryLineContributor.java:138-246` reads `ctx.version().statutory()` only |
| EDLI and PF admin are always saved and so always paid, whatever their switch says | `statutory/lines/StatutoryLineDeriver.java:111-137` |
| EPS is computed from the saved row's rate even for an employee not in EPS | `StatutoryLineContributor.java:168-189`; the row is ₹0 with rate 8.33 (`StatutoryLineDeriver.java:76-86`) |

Legacy: employer PF and employer ESI always applied when EPF / ESI is on and the employee is ticked; their "include in salary structure" switches were not used by the salary. EDLI and admin were added only when their switch was on (`legacy/Payroll-Fend-react/src/pages/mainPages/employee/salaryDetails.js:617-659`). Legacy had no EPS split (`:248-263`).

## 2. Rule — checked at every regular pay run

| Item | Applied when | Rate / base from |
|---|---|---|
| Employee PF, employer PF | company EPF on **and** employee PF ticked | EPF settings now; Basic from the salary in force |
| EPS (part of employer PF) | the above **and** employee EPS ticked **and** under the EPS age on the period start (date of birth; unknown → under) | EPF settings |
| EDLI | EPF applies **and** "include EDLI in salary structure" on | EPF settings |
| PF admin | EPF applies **and** "include admin charges in salary structure" on | EPF settings |
| Employee and employer ESI | company ESI on **and** employee ESI ticked **and** the salary's monthly gross ≤ ESI ceiling | ESI settings; gross from the salary in force |
| PT | unchanged (employee PT ticked, state slab) | unchanged |

The employer PF / ESI "include in salary structure" switches do **not** control payment (display only, as legacy). Off-cycle runs are unchanged (no statutory lines).

**At salary save (`StatutoryLineDeriver`)**, so the saved view and CTC match the rule:

- EDLI / admin rows only when their structure switch is on; their "in CTC" flag counts only then ("in salary structure" off → not in the structure and not in CTC — founder 2026-11-01)
- No EPS row for an employee not in EPS or over the EPS age

Employer PF, EPS and ESI rows and their "in CTC" flags are unchanged.

## 3. Out of scope

- LWF — the tick exists, nothing calculates it; a separate feature
- Tax projection still reads the employee PF saved with the salary (`taxcalc/TaxInputAssembler.java:419-423, 492-530`): if PF is turned off after the save, TDS keeps counting 80C PF until the salary is revised. Follow-up ticket
- Basic is the earning coded or named Basic / Basic Salary (`taxcalc/EarningKinds`); a Basic under another code falls back to the PF wage base saved with the salary
- CTC of a salary already saved: the allowance that balances CTC is set at save; a later switch change shows in the pay run at once and in the CTC split at the next revision

## 4. Changes

| File | Change |
|---|---|
| `statutory/payrun/StatutoryLineContributor.java` | rule above; the saved rows are no longer read for amounts, rates or eligibility; date of birth via `EmployeePersonalService` |
| `statutory/lines/StatutoryLineDeriver.java` | EDLI / admin gated on their structure switch, CTC flag = structure and CTC; no EPS row when not eligible |

Creates no table, no column, no Flyway script. Money stays `Money` / `BigDecimal`.

## 5. Tests

| File | Covers |
|---|---|
| `StatutoryLineContributorTest.java` | company EPF off → no PF lines though rows were saved; employee PF unticked → none; PF ticked with no saved rows → PF applied; EPS not ticked / age 58 → whole 12% employer PF; EDLI / admin switch off → none, on → applied; ESI off / unticked / gross over ceiling → none; ticked, under → applied; rates follow the settings, not the saved row |
| `StatutoryLineDeriverTest.java` | EDLI / admin structure off → no row and not in CTC even with CTC on; on + CTC on → row in CTC; on + CTC off → row not in CTC; no EPS row when not eligible |
| `PayRunStatutoryIT` and the salary ITs | updated to the rule; green |

## 6. Verification

```bash
cd code/backend
./mvnw -B -pl payroll -am verify
```

| Check | Expected |
|---|---|
| Unit and integration tests | `Failures: 0, Errors: 0` |
| `node .claude/scripts/check-done.mjs W-76.3` | gates green |

## 7. Rollback

Revert the commit. No schema change.
