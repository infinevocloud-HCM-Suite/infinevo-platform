# Feature: Annual tax statement (Form 16) — rendered from the year's record

| Field | Value |
|---|---|
| **Feature ID** | `W-36.4` · from ticket #48 (`W-36`) · `PAY-15` annual statements |
| **Promoted to** | `docs/target-state/features/W-36-4-form16-statement.md` — **`W-36-4` with hyphens**, never `W-36.4`; `guard-edit` blocks the dotted form |
| **Owner** | mohit |
| **Apps touched** | `code/backend/payroll` |
| **Related gaps** | BUG-002 (honoured), DEBT-007, DEBT-008, DEBT-019 (honoured) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | `W-33.3`, which supplies `payroll.tax_computation` (the Part B breakdown) · `W-36.1`, which supplies `employee_tds` and the `TAX` lines · `W-36.3`, which supplies the deductor · `W-29.2`, which supplies `employee_payrun_line` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an officer, or the employee under `/me`, reads one employee's Form 16 for a financial year: the deductor, the quarterly tax deducted, the Part B working, and what is still owed | 1 |
| Frontend area | none — the printed page is a `W-47` ticket | 1 |

This spec was split out of the first `W-36.3` on 2026-09-29. The deductor is `W-36.3` and the Part A upload is `W-36.5`.

---

## 1. Problem

Form 16 does not exist in the frozen system. There are two screens and no backend. All
citations are `legacy/`, code being replaced.

- The Form 16 page only loads the deductor. It has a hard-coded year picker and a
  "Generate Form 16" button that navigates away (`Payroll-Fend-react/.../taxesAndForms/form16/index.js:42-66`, `:88-91`, `:140`)
- The generate page's upload is a `TODO` that logs to the console (`.../form16/generateForm16.js:38-43`)
- There is no Form 16 controller or service in `Payroll-Bend-SBoot`, and `grep -ri form16` finds only the front end

Every figure the statement needs is already stored in the target: the annual tax and its
source (`W-36-1-employee-tds.md` §6), the computation breakdown (`W-33-3-tax-calculator-recalculation.md` §6),
and the monthly deductions as `TAX` lines (`W-36-1-employee-tds.md` §3). `02-data-model.md:353`
gives `PAY-15` no table. The statement is **rendered, not stored**, as the payslip is (`W-36-2-payslips-signed-link.md` §13 decision 2).

## 2. Scope

**In scope**

- `Form16Service.render(employeeId, fy)`, which returns a `Form16Statement` record
- An officer read and a `/me` read
- `final = true` only when every month of the year up to March has a `PAID` run for the tenant

**Out of scope**

- A PDF. The statement is JSON and the page prints it, as the payslip does (`W-36-2-…md` §13 decision 6)
- Part A (the TRACES certificate) is `W-36.5`. This statement carries no link to it
- A bulk "all employees" read, which is a later export through `W-23.1` if a tenant asks
- The employer's address, because `core.tenant` holds only `name` (`V001__tenant.sql:8`). It is printed blank until a tenant-address ticket exists
- Screens (`W-47`)

## 3. Flow

```
[officer]  GET /api/v1/payroll/employees/{employeeId}/form16/{fy}   payroll.statutory_report.read
[employee] GET /api/v1/me/form16/{fy}                               payroll.payslip.read_own
   --> Form16Service.render(employeeId, fy)
       deductor    = TaxDeductorService.current()                 none → 409 DEDUCTOR_NOT_SET
       tds         = EmployeeTdsService.active(employeeId, fy)     none → 404
       computation = latest payroll.tax_computation for (employee, fy)   may be absent
       quarters    = Σ TAX line amount per quarter over PAID runs in fy   (one grouped query)
       deducted    = Σ quarters
       balance     = tds.annual_tax − deducted                     > 0 still owed, < 0 excess
       breakdown   = computation, when computation.annual_tax = tds.annual_tax
                     else null, with breakdown_note = "OFFICER_OVERRIDE"
       final       = a PAID run exists for every period April..March of fy
   --> 200 Form16Statement
```

**Which figure wins.** `employee_tds.annual_tax` is the tax the runs deducted against, so it
is the statement's "tax payable". The `tax_computation` row supplies only the breakdown. When
an officer `PUT` has overridden the computed figure, the two differ. In that case the
statement prints the officer's figure, omits a breakdown that would not add up, and says why.

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/form16/`.

| Layer | File | Change |
|---|---|---|
| Record | `Form16Statement.java` | `financialYear`, `assessmentYear` (the next FY label), `final`, `deductor` (name = tenant name, `tan`, `pan`, `tdsCircle`, signatory name, parent name, designation), `employee` (name, `pan`, designation), `regime`, `quarters[4]` (`quarter`, `amountDeducted`), `totalDeducted`, `annualTax`, `balance`, `breakdown?` (the fourteen `tax_computation` money columns), `breakdownNote?`, `generatedAt`; all money is `BigDecimal` scale 2, rounded `HALF_UP` for print |
| Service / ServiceImpl | `Form16Service`, `Form16ServiceImpl` | `render(employeeId, fy)`, `renderOwn(fy)` — `renderOwn` resolves the caller through `EmployeeService.currentEmployee()` (`W-13.4`) |
| Repository (change) | `payrun/EmployeePayRunLineRepository.java` (`W-29.2`) | one query, `sumTaxLinesByPeriod(tenantId, employeeId, periodFrom, periodTo)`, grouped by `payrun.period`, filtered to `payrun.status = 'PAID'` |
| Repository (use) | `TaxComputationRepository` (`W-33.3`) | `findFirstByTenantIdAndEmployeeIdAndFinancialYearOrderByComputedAtDesc` — add it when `W-33.3` lacks it |
| Controller | `Form16Controller.java` | the two endpoints |
| DTO | `Form16Response` | the record in the `status` / `message` / `data` envelope |

Employee name, designation and PAN are read through `core` services: `EmployeeService` and
`EmployeeIdentificationService.get` (`core/.../employee/detail/EmployeeIdentificationService.java:12`).
PAN is masked as `ABCDE****F` in the officer read's logs and never logged at all under `/me`.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/payroll/employees/{employeeId}/form16/{fy}` | — | `200` statement · `404` no TDS record · `409` `DEDUCTOR_NOT_SET` | `payroll.statutory_report.read` |
| GET | `/api/v1/me/form16/{fy}` | — | the same response for the caller's own employee | `payroll.payslip.read_own` |

There are no new action codes. `V020__action.sql:121`, `:132` defines them. They are granted to
`payroll-officer`, `finance` and `employee` (`V025__catalogue_correction.sql:204`, `:215`, `:229`).

**Quarters.** Q1 is April–June, Q2 July–September, Q3 October–December, and Q4 January–March of `fy`.
A month with no `PAID` run adds nothing.

**Validation.** `fy` must be a valid `FinancialYear` label (`W-32.1`), or the call returns `400`. An
employee outside the tenant returns `404`, as does a `/me` login linked to no employee.

## 5. Frontend changes

None.

## 6. Database changes

None. `02-data-model.md:353` gives `PAY-15` no table, and nothing is stored.

- [x] `tenant_id` + RLS — no new table. Every read goes through RLS-protected tables
- [x] Index — the grouped sum must use `W-29.2`'s tenant-led indexes on `employee_payrun_line` and `payrun`. The developer confirms this with `EXPLAIN` and adds none unless it shows a sequential scan
- [x] Money — `BigDecimal` throughout, rounded only in the record
- [x] Expand / contract — nothing changes

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../form16/Form16AssemblerTest.java` | **the hand calculation**: annual tax 120,000, `PAID` TAX lines of 10,000 for April–December ⇒ Q1 30,000, Q2 30,000, Q3 30,000, Q4 0, deducted 90,000, balance 30,000, `final = false`; add January–March ⇒ Q4 30,000, balance 0, `final = true`; computation annual tax 118,000 against a record of 120,000 ⇒ `breakdown` null and note `OFFICER_OVERRIDE`; FY `2026-2027` ⇒ assessment year `2027-2028`; 1,234.565 prints 1,234.57 |
| Integration | `payroll/.../form16/Form16IT.java` | **the acceptance test**: deductor set, record written, twelve runs computed; nine paid, one `COMPUTED`, one `CANCELLED` ⇒ only the nine count; `GET /me/form16` for employee A's login returns A and never B; no deductor ⇒ `409`; no TDS record ⇒ `404`; without `payroll.statutory_report.read` the call is `403` |
| Integration | `payroll/.../form16/Form16RlsIT.java` | as `app_user` bound to tenant A, reading tenant B's employee returns `404`. Tenant B's `PAID` lines are never summed into tenant A's quarters |

## 8. Verification

```bash
cd code/backend && mvn -q verify
grep -rn "Double\|double\|float" code/backend/payroll/src/main/java/com/infinevo/payroll/form16/ || echo "no floating point"
grep -rln "CREATE TABLE" code/backend/migration/src/main/resources/db/migration | xargs grep -l "form16" || echo "no table"
grep -rn "log\.\(info\|debug\|warn\).*[pP]an" code/backend/payroll/src/main/java/com/infinevo/payroll/form16/ || echo "no PAN logged"
```

| Check | Expected |
|---|---|
| Suite | green with no skips, and `Form16IT` present and passing |
| Floating point | `no floating point` |
| Table | `no table` |
| PAN in logs | `no PAN logged` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The statement is stored as a file "for speed" | medium | §2 and `02-data-model.md:353` rule it out. The `no table` grep checks it |
| `COMPUTED` or `APPROVED` runs are counted as deducted, although the tax has not been paid over | medium | the `PAID` filter is in the query, and `Form16IT` computes one run without paying it |
| The breakdown and the payable figure disagree on the printed form | medium | the `OFFICER_OVERRIDE` rule in §3, and the unit test |
| N+1 — one sum query per month | low | one grouped query (DEBT-019) |

## 10. Rollback

Nothing is stored, so there is nothing to roll back. A wrong figure is corrected at its source (`W-36.1` `PUT` or a `W-33.3` recompute), and the next read shows it.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | creates no table |
| Flyway only, `ddl-auto` nowhere | no migration |
| `Money`/`BigDecimal` for money | `BigDecimal` throughout |
| Index on `tenant_id` plus lookup columns | uses existing indexes |
| Expand / contract | no schema change |
| No module references another module | `payroll` reads `core` (`EmployeeService`, `EmployeeIdentificationService`) only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Honoured** — the tenant comes from the bound context, and `Form16RlsIT` checks it |
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-008 hand-built envelope | **Fixed** |
| DEBT-019 N+1 | **Honoured** — one grouped query |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | Store or render? | **Render**, as the payslip does. `PAY-15` has no table |
| 2 | JSON or PDF? | **JSON** plus the printed page. A PDF is a later ticket with a real library decision |
| 3 | Which figure is "tax payable"? | **`employee_tds.annual_tax`**. The breakdown comes from `tax_computation` only when the two match |
| 4 | What counts as deducted? | **`TAX` lines of `PAID` runs** only |
| 5 | Before March is paid? | **Readable, with `final = false`**, so an employee can check it mid-year |
| 6 | Which action for the employee? | **`payroll.payslip.read_own`**. `PAY-15` is "payslips and annual statements", and the employee role already holds it |
