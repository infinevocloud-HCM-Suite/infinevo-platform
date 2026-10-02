# Feature: Imported tax counts as already deducted — monthly TDS and Form 16

| Field | Value |
|---|---|
| **Feature ID** | `W-38.2` · from ticket #50 (`W-38`) · `PAY-17` |
| **Promoted to** | `docs/target-state/features/W-38-2-prior-tax-counted.md` — **`W-38-2` with hyphens**, never `W-38.2`; `guard-edit` blocks the dotted form |
| **Owner** | krushna |
| **Apps touched** | `code/backend/payroll` |
| **Related gaps** | DEBT-019 (honoured) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | `W-38.1` (`payroll.prior_payroll_month`) · `W-36.1` (`TaxLineContributor`, `EmployeeTdsService.yearToDate`, karma) · `W-36.4` (`Form16Service`, mohit) |
| **Size** | S |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | tax deducted in an imported month counts as tax already deducted for the year: the monthly TDS line is smaller by it, and Form 16 shows it in its quarter | 1 |
| Frontend area | none | 1 |

Within cap. One rule, read in two places.

---

## 1. Problem

Both readers of "tax already deducted this year" look only at this platform's own runs. All
citations are `docs/target-state/` specs; neither class is on `main` yet.

- **Monthly TDS.** `ytd = Σ TAX lines on this tenant's other runs in fy`;
  `remaining = annual_tax − ytd`, spread over the months left (`W-36-1-employee-tds.md:84-93`).
  A tenant that starts in October with 60,000 of 120,000 already deducted elsewhere gets
  `120,000 / 6 = 20,000` a month instead of `10,000`
- **Form 16.** `quarters = Σ TAX line amount per quarter over PAID runs in fy`
  (`W-36-4-form16-statement.md:67-68`); a month with no `PAID` run adds nothing (`:108-109`).
  Q1 and Q2 print 0 and `balance` shows the 60,000 as still owed
- **`final` never turns true** for that year: it needs a `PAID` run for every month April to
  March (`W-36-4-form16-statement.md:48`, `:72`)
- `W-38.1` stores the earlier months in `payroll.prior_payroll_month` with a `tds` column
  (`W-38-1-prior-payroll-import.md` §6)

## 2. Scope

**In scope**

- `PriorPayrollTaxQuery` — the imported TDS for one employee and year, in total and by period
- `TaxLineContributor` and `EmployeeTdsService.yearToDate`: `ytd` adds the imported TDS
- `Form16Service.render`: each quarter adds its imported months; `final` treats an imported month
  as covered

**Out of scope**

- Imported gross in the tax calculator's salary projection (`W-38-1-…md` §13 decision 6)
- Imported EPF, ESI and PT in any report or dashboard. `W-37`'s tiles stay run-only; a later
  ticket if the founder wants them
- Payslip year-to-date figures — payslips carry none (`W-36.2`)

## 3. Flow

```
[pay run, W-29.2 loop] --> TaxLineContributor.contribute(ctx)                       (W-36.1, changed)
   ytd = Σ TAX lines on other runs in fy (as W-36.1)
       + PriorPayrollTaxQuery.total(tenant, employee, fy)                            new
   remaining, months, amount — unchanged

[officer / employee] --> Form16Service.render(employeeId, fy)                         (W-36.4, changed)
   quarters[q] = Σ TAX lines of PAID runs in q (as W-36.4)
               + Σ prior_payroll_month.tds for the employee in q's months            new
   final       = every month April..March has a PAID regular run
                 or an imported row for the tenant                                    changed
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Query | `payroll/.../priorpayroll/PriorPayrollTaxQuery.java` | new. `BigDecimal total(tenantId, employeeId, FinancialYear fy)` and `Map<String, BigDecimal> byPeriod(tenantId, employeeId, fy)`, one aggregate query each (DEBT-019); `0` and an empty map when nothing is imported |
| Query | `PriorPayrollMonthRepository.java` (`W-38.1`) | `sumTds(tenantId, employeeId, periodFrom, periodTo)`, `sumTdsByPeriod(…)`, `findDistinctPeriods(tenantId, from, to)` — the last exists from `W-38.1` |
| Contributor (change) | `payroll/.../tds/TaxLineContributor.java` (`W-36.1`) | `ytd` adds `PriorPayrollTaxQuery.total` |
| Service (change) | `payroll/.../tds/EmployeeTdsServiceImpl.java` (`W-36.1`) | `yearToDate` adds the same, so `GET …/tds/{fy}` and `/me/tds/{fy}` show the figure the line used |
| Service (change) | `payroll/.../form16/Form16ServiceImpl.java` (`W-36.4`) | quarters add `byPeriod`, grouped by `W-36.4`'s quarter rule (§4 there); `final` reads imported periods as covered |

No endpoint, no response field and no table change. `year_to_date` on the TDS response and
`quarters[]`, `totalDeducted`, `balance`, `final` on Form 16 simply carry the larger, correct
numbers.

The query is in `payroll`, next to both readers. Neither reader writes to the imported table.

## 5. Frontend changes

None.

## 6. Database changes

None. The reads use `W-38.1`'s `idx_prior_payroll_month_tenant_employee (tenant_id, employee_id, period)`.

- [x] `tenant_id` — no new table
- [x] Index — `W-38.1`'s; the developer confirms with `EXPLAIN` and adds none
- [x] Money — `BigDecimal` sums, scale 4, as the lines
- [x] Expand / contract — nothing

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../tds/TaxLineContributorTest.java` (extend) | annual tax 120,000; imported TDS 10,000 for each of `2026-04`–`2026-09`; period `2026-10`, no runs ⇒ ytd 60,000 ⇒ 6 months ⇒ `10,000.0000`. Imported 70,000 ⇒ `8,333.3333`. Imported ≥ annual ⇒ no line |
| Unit | `payroll/.../form16/Form16AssemblerTest.java` (extend, `W-36-4-form16-statement.md:131`) | imported `04`–`09` at 10,000, `PAID` runs `10`–`03` at 10,000 ⇒ quarters `30,000 / 30,000 / 30,000 / 30,000`, `balance` 0, `final` true. A month neither imported nor paid ⇒ `final` false |
| Integration | `payroll/.../priorpayroll/PriorTaxCountedIT.java` | **the acceptance test.** Import `2026-04`–`2026-09` with 10,000 TDS each through `W-38.1`; record annual tax 120,000; create, lock and compute `2026-10` ⇒ one `TAX` line of `10,000.0000`; `GET …/tds/2026-2027` ⇒ `year_to_date` 60,000 before the run; tenant B's imports are never counted for tenant A |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. `PriorTaxCountedIT` reuses
`PayRunTaxLineIT`'s run setup (`W-36.1`).

## 8. Verification

```bash
grep -rn "PriorPayrollTaxQuery" code/backend/payroll/src/main/java | grep -v "priorpayroll/" | wc -l
grep -rn "Double\|double\|float" code/backend/payroll/src/main/java/com/infinevo/payroll/priorpayroll/ || echo "no floating point"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| Callers | `3` — the contributor, the TDS service, the Form 16 service |
| Floating point | `no floating point` |
| Suite | green, no skips; `PriorTaxCountedIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The contributor adds the imported TDS but `yearToDate` does not, so the screen and the line disagree | medium | both call one query; the IT checks the `GET` and the line |
| Imported months counted twice when a run also exists for them | low | `W-38.1` refuses that both ways |
| A quarter boundary off by a month | low | the unit test's four equal quarters |

## 10. Rollback

Revert the branch. Nothing is stored by this ticket; the next compute and the next Form 16
read go back to run-only figures.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table; the query runs under the tenant's RLS context and takes `tenantId` |
| Flyway | none |
| `Money` / `BigDecimal` | `BigDecimal` sums |
| Index | `W-38.1`'s |
| Expand / contract | nothing |
| No module references another | `payroll` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-019 N+1 | **Honoured** — one aggregate query per reader, not a row scan |

## 13. Decisions — founder, 2026-10-02

| # | Question | Answer |
|---|---|---|
| 1 | Does imported TDS reduce the monthly deduction? | **Yes.** It is tax already deducted this year |
| 2 | Does Form 16 show it? | **Yes, in the quarter of its month** |
| 3 | Is a Form 16 `final` without runs for the imported months? | **Yes.** An imported month counts as covered |
