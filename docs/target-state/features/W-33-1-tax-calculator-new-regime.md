# Feature: Tax calculator — new regime and the calculation engine

| Field | Value |
|---|---|
| **Feature ID** | `W-33.1` · from ticket #43 (`W-33`) · `PAY-10` part 1 of 3 |
| **Promoted to** | `docs/target-state/features/W-33-1-tax-calculator-new-regime.md` on the developer's `dev-<name>` branch — **`W-33-1` with hyphens**, never `W-33.1`; `guard-edit` blocks the dotted form |
| **Owner** | mohit |
| **Apps touched** | `code/backend/payroll` |
| **Related gaps** | BUG-002 (fixed), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-019 (fixed), DEBT-022 (fixed); the four defects proposed in `.claude/outputs/2026-09-29-analyze-w33-tax-calculator.md` § "Proposed GAP entries" are fixed by design here and in `.2`/`.3` |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | `W-32.1` — the declaration header and `FinancialYear` · `W-32.4` — `TaxSummaryService.record(...)` · `W-26.2` (on `main`) — `versionInForce`. `W-32.3`'s previous-employment rows are read when present and treated as zero otherwise, as `W-32.4` does for its sections |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an employee on the new regime computes their tax for the year and the summary row shows the figures | 1 |
| Frontend area | none — the calculator screen is `W-47.3b` (`W-47-3-tax-declaration-screens.md:197`) | 1 |

Within cap. `W-33` was split into three (`10-scoping.md:200-204`): `.1` new regime **and the
engine every regime shares** (slabs, rebate, surcharge, cess, salary projection), `.2` old
regime, `.3` revisions and recalculation. Sized M.

---

## 1. Problem

All citations are `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/`, code being replaced.

- **The arithmetic is right but the tenant is a header string.** `GET /tax/calculate/new/{employeeId}/{fy}`
  takes `organizationId` from a request header (`controller/employeeitdeclaration/taxCalculator/TaxCalculationController.java:35-38`) — BUG-002
- **Marginal relief is seeded and never applied.** The rule row carries
  `is_marginal_relief_applicable` but the surcharge loop multiplies the band rate and stops
  (`serviceimpl/employeeitdeclaration/taxCalculator/NewTaxCalculationServiceImpl.java:316-335`).
  An employee just over ₹50 lakh pays more tax than one just under
- **Slabs are a JSON string on the row** (`entity/EmployeeITDeclaration/taxCalculator/TaxSlabMaster.java:29`), parsed on every call (`NewTaxCalculationServiceImpl.java:226-236`). The target has proper bracket rows (`V004__reference_tax_masters.sql:52-60`)
- **The financial year is an `Integer`** rebuilt into `2024-25` (`:138`); the reference tables key on `2025-2026`
- **The result is stored twice** — `new_tax_calculation` with a `tax_per_month` (`:513-514`) that the pay run then ignores in favour of `employee_tds` (`W-36-1-employee-tds.md` § 1). `W-36.1` owns the monthly figure; `.3` owns the history

## 2. Scope

**In scope**

- `TaxCalculationService.compute(employeeId, fy, regime)` — one call, one `TaxComputation` result with every intermediate figure
- The shared engine: `SalaryProjection` (taxable earnings for the year from the CTC versions), `SlabTax`, `Rebate87A`, `SurchargeAndCess` with marginal relief — all reading the `reference` rule tables by financial year
- `NewRegimeCalculator` — salary + previous employer − standard deduction → slabs → rebate → surcharge → cess − previous-employer TDS
- `POST …/tax/compute` — computes every regime the engine knows and writes each to the summary row through `TaxSummaryService.record`; `GET …/tax` previews without writing
- `RegimeCalculator` as the seam `.2` plugs `OLD` into. Asking for a regime with no calculator is `409 REGIME_NOT_AVAILABLE` until `.2` merges

**Out of scope**

- Old regime — `.2`. HRA, house property, Chapter VI-A, 80TTA/TTB are old-regime only
- Writing `employee_tds`, keeping a history, reacting to a salary change — `.3`
- Employer NPS (80CCD(2)) — allowed in the new regime (`V005__reference_tax_seed.sql:204`) but there is no benefit component flagged for it yet; § 14
- Mid-year exit — there is no exit date on `core.employee` yet; the projection runs to March (§ 9)
- Screens — `W-47.3b`

## 3. Flow

```
[employee] --> GET  /me/tax-declaration/{fy}/tax?regime=NEW         payroll.tax_declaration.read_own
           --> TaxDeclarationService.require(currentEmployee, fy)   (W-32.1; header exists or 404)
           --> TaxCalculationService.compute(employeeId, fy, NEW)   pure — nothing written
           --> 200 TaxComputation

[employee] --> POST /me/tax-declaration/{fy}/tax/compute             payroll.tax_declaration.declare_own
           --> for each regime with a RegimeCalculator bean: compute --> TaxSummaryService.record(declarationId, regime, figures)   (W-32.4)
           --> 200 { NEW: TaxComputation, OLD: TaxComputation | null }

[officer]  --> same two on /payroll/employees/{id}/tax-declaration/{fy}/tax under .read / .manage

compute(employeeId, fy, regime):
   input  = TaxInput.gather(tenant, employee, fy)
              salary        = SalaryProjection.annual(tenant, employee, fy)               § 4, from versionInForce month by month
              prevEmployment= Σ employee_inv_prev_employment by kind, 0 when W-32.3 absent
              age           = AgeCategory.at(dateOfBirth, fy.end())                       GENERAL when no date of birth
   result = RegimeCalculator.forRegime(regime).compute(input, fy)
```

`NewRegimeCalculator.compute`, ported from `NewTaxCalculationServiceImpl.java:122-395` with the fixes in § 1:

| Step | Rule | Reference row |
|---|---|---|
| 1 | `incomeFromSalary = salary + prevEmployment.INCOME` | — (`:155-163`) |
| 2 | `standardDeduction = min(rule.amount, incomeFromSalary)` | `standard_deduction_rule_master (fy, 'NEW')` — `V005:105` gives 75,000 for 2025-2026 |
| 3 | `taxableIncome = max(0, incomeFromSalary − standardDeduction)`, rounded down to the rupee | (`:177-181`) |
| 4 | `taxBeforeRebate = SlabTax.of(taxableIncome, slabs)` — consume the income bracket by bracket, `to_amount NULL` is open-ended | `tax_slab_master (regime, fy, age_category = GENERAL)` + `tax_slab_detail_history` ordered by `slab_order`. `NEW` has no age rows by design (`V027:8`) |
| 5 | `rebate = taxableIncome <= income_threshold ? (is_full_rebate ? taxBeforeRebate : min(taxBeforeRebate, max_rebate_amount)) : 0` | `section87a_rebate_rule_master (fy, regime)` — `V005:119` |
| 6 | `surcharge` = the one `SURCHARGE` band with `income_from <= taxableIncome < income_to` (or `income_to NULL`), rate on `taxAfterRebate`; **marginal relief** when the band says so: `surcharge = min(surcharge, max(0, (taxableIncome − income_from) − (taxAfterRebate − taxAtThreshold)))` where `taxAtThreshold` is steps 4–6 run at `income_from` | `cess_surcharge_rule_master (fy, rule_type, tax_regime IN (regime, 'BOTH'))` — `V005:228-233` |
| 7 | `cess = (taxAfterRebate + surcharge) × cess.rate / 100` when that base is positive | the `CESS` row, `V005:227` |
| 8 | `annualTax = max(0, taxAfterRebate + surcharge + cess − prevEmployment.INCOME_TAX_DEDUCTED)`, rounded to the rupee | (`:388-393`) |

Every intermediate is `Money` (scale 4, `HALF_UP`); the two roundings named above are the
only ones and happen on the rupee, as the Income-tax Rules require (`Money.of(x).raw().setScale(0, HALF_UP)`).

**`SalaryProjection.annual(tenant, employee, fy)`**, ported from `calculateTotalFySalary` (`:668-740`):
for each month `m` from `max(fy.start(), dateOfJoining)`'s month to March, take
`versionInForce(tenant, employee, first day of m)` (`W-26-2-…md:267`) and add the `monthly_amount`
of every enabled earning whose component `is_taxable` (`W-26-1-…md:131`). No version in force
for a month adds nothing. The projection returns the annual sum and the month list so `.3`
can show what it assumed.

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/taxcalc/`.

| Layer | File | Change |
|---|---|---|
| Interface | `RegimeCalculator` | `TaxRegime regime()`; `TaxComputation compute(TaxInput input, FinancialYear fy)`. One Spring bean per regime; `RegimeCalculators.forRegime(regime)` throws `RegimeNotAvailableException` (`409`) when none |
| Calculator | `NewRegimeCalculator` | `@Component`, § 3 table |
| Engine | `SalaryProjection`, `SlabTax`, `Rebate87A`, `SurchargeAndCess`, `AgeCategory` | pure functions over `Money`; the rule rows are handed in, never fetched inside |
| Reference reads | `TaxRuleReader` | read-only over the six `reference` tables used here, every finder by `(financial_year, regime)`; `slabs(fy, regime, ageCategory)`, `standardDeduction(fy, regime)`, `rebate(fy, regime)`, `surchargeBands(fy, regime)`, `cess(fy, regime)`. Missing rule for a year ⇒ `422 TAX_RULES_MISSING` naming the table |
| Input | `TaxInput` (record) | `employeeId`, `declarationId`, `salary` (`SalaryProjection.Result`), `prevEmployment` (map by kind), `ageCategory`, plus the `.2` sections as `Optional` fields it leaves empty |
| Result | `TaxComputation` (record) | `regime`, `financialYear`, `incomeFromSalary`, `prevEmploymentIncome`, `standardDeduction`, `grossTotalIncome`, `taxableIncome`, `taxBeforeRebate`, `rebate`, `surcharge`, `cess`, `prevEmploymentTds`, `annualTax`, `slabLines[]` (from, to, rate, taxable, tax), `assumptions[]` (months projected, versions used); `.2` adds its sections |
| Mapper | `TaxSummaryFiguresMapper` | `TaxComputation` → `W-32.4`'s `TaxSummaryFigures`: `taxable_income = grossTotalIncome`, `net_taxable_income = taxableIncome`, `tax_on_taxable_income = taxBeforeRebate − rebate + surcharge + cess`, `tds_previous_employer`, `tax_to_be_paid = annualTax`, `remaining_months` = months from the current period to March; `tds_through_payroll`, `tax_ytd_amount` from `EmployeeTdsService.yearToDate` when `W-36.1` is present, else `0` |
| Service / ServiceImpl | `TaxCalculationService`, `…Impl` | `compute(employeeId, fy, regime)` (pure), `computeAndRecord(employeeId, fy)` (every available regime, `record` each in one transaction) |
| Controller | `MyTaxCalculationController`, `TaxCalculationController` | new |
| DTO | `TaxComputationResponse`, `TaxComputeResponse` | `status` / `message` / `data` envelope (`CONVENTIONS.md` § 3); money at scale 2 |

`FinancialYear` is `W-32.1`'s (`W-32-1-…md:119`). `AgeCategory.at(dateOfBirth, asOf)` is
`GENERAL` under 60, `SENIOR` 60–79, `SUPER_SENIOR` 80 and over, on the age reached by
`fy.end()` (31 March) — § 13 decision 3. It is here, not `.2`, because the slab reader takes it.
Date of birth comes from `core`'s `EmployeePersonalService` (`V015__employee_personal.sql:8`);
`dateOfJoining` from `EmployeeService.get` (`EmployeeResponse.java:30`).

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me/tax-declaration/{fy}/tax` | `regime` query, default = the header's | `TaxComputation`; `404` no header; `409 REGIME_NOT_AVAILABLE`; `422 TAX_RULES_MISSING` | `payroll.tax_declaration.read_own` |
| POST | `/api/v1/me/tax-declaration/{fy}/tax/compute` | — | `{ NEW: …, OLD: … or null }`; the summary rows updated | `payroll.tax_declaration.declare_own` |
| GET / POST | `/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/tax`, `…/tax/compute` | as above | as above | `payroll.tax_declaration.read` / `.manage` |

No new action codes: the four `W-32.1` seeded (`W-32-1-…md:176-179`) cover reading and
declaring. Computing is derived from what the employee may already read.

`compute` never checks `editable()` — a `SUBMITTED` declaration can be computed; that is
the normal case. It never changes the header.

## 5. Frontend changes

None. `W-47.3b` builds the calculator and regime-compare screens on this contract.

## 6. Database changes

None. The six rule tables are on `main` (`V004`, `V005`, `V027`); the summary row is `W-32.4`'s.

- [x] `tenant_id` — no new table
- [x] Index — no new table
- [x] Money — `Money` throughout; the response renders scale 2
- [x] Expand / contract — nothing

Every repository call carries `tenantId` (DEBT-022); the reference reads carry `financial_year`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../taxcalc/SlabTaxTest.java` | FY 2025-2026 `NEW` on 10,00,000 ⇒ 40,000 (0 + 20,000 + 20,000); on 15,00,000 ⇒ 1,05,000; on 3,99,999 ⇒ 0; open-ended top bracket on 1,00,00,000 |
| Unit | `payroll/.../taxcalc/NewRegimeCalculatorTest.java` | **the hand calculation**: salary 15,75,000, no previous employer ⇒ standard deduction 75,000, taxable 15,00,000, tax 1,05,000, rebate 0, cess 4,200, **annual tax 1,09,200**; salary 12,75,000 ⇒ taxable 12,00,000, tax 60,000, full rebate ⇒ **0**; salary 12,75,001 ⇒ rebate 0 (threshold is on taxable income); previous-employer income 2,00,000 and TDS 10,000 added and subtracted; standard deduction never exceeds income; surcharge band 10 % on taxable 55,00,000 and marginal relief on 50,10,000 against the department's published figure for FY 2025-26, source URL in the test |
| Unit | `payroll/.../taxcalc/SalaryProjectionTest.java` | joined 2025-10-15, one version ⇒ 6 months; revision from 2026-01-01 ⇒ 3 + 3 months at the two rates; a non-taxable earning excluded; no version in force ⇒ 0 and an assumption line |
| Unit | `payroll/.../taxcalc/AgeCategoryTest.java` | born 1966-03-31 ⇒ `SENIOR` for 2025-2026; born 1966-04-01 ⇒ `GENERAL`; 80 on 2026-03-31 ⇒ `SUPER_SENIOR`; no date ⇒ `GENERAL` |
| Integration | `payroll/.../taxcalc/TaxComputeIT.java` | **the acceptance test**: tenant, employee with a flat taxable CTC of 15,75,000 from 2025-04-01, header `NEW`; `GET …/tax` ⇒ `annual_tax 1,09,200.00`, summary row still `computed: null`; `POST …/tax/compute` ⇒ `NEW` filled, `OLD` `null`, and `GET …/summary` (`W-32.4`) shows `tax_to_be_paid 1,09,200` with `computed_at`; a second `POST` overwrites; `GET …/tax?regime=OLD` ⇒ `409 REGIME_NOT_AVAILABLE`; a year with no rules ⇒ `422` |
| Integration | `payroll/.../taxcalc/TaxComputeRlsIT.java` | tenant A's officer computing tenant B's employee ⇒ `404`; `record` lands on tenant A's row only |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. The marginal-relief
case uses a published figure, not one invented here (`09-build-order.md:227`).

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT regime, age_category, count(*) FROM reference.tax_slab_master WHERE financial_year='2025-2026' GROUP BY 1,2 ORDER BY 1,2;"
grep -rn "double\|float\|Double\|Float" code/backend/payroll/src/main/java/com/infinevo/payroll/taxcalc/ || echo "no floating point"
grep -rn "slab_json\|slabJson" code/backend/payroll/src/main/java || echo "no json slabs"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| Slab masters for 2025-2026 | `NEW GENERAL 1` · `OLD GENERAL 1` · `OLD SENIOR 1` · `OLD SUPER_SENIOR 1` |
| Floating point | `no floating point` |
| JSON slabs | `no json slabs` |
| Suite | green, no skips; `TaxComputeIT` present and passing with `1,09,200.00` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The port copies the legacy surcharge loop and skips marginal relief | **high** — the legacy code looks complete | § 3 step 6 spells the formula; the unit test pins a published figure |
| Slab lookup by `(fy, regime)` alone returns three `OLD` rows and the first wins, as legacy (`OldTaxCalculationServiceImpl.java:1172`) and as `ReferenceSchemaIT` once did (`W-09-1-…md:161`) | high | `TaxRuleReader.slabs` takes `ageCategory` and asserts exactly one master row |
| Projection to March overstates tax for a leaver | medium | no exit date exists on `core.employee`; the assumption is listed in `assumptions[]`; `.3` recomputes when one arrives |
| Rounding at every step, or never | medium | two rupee roundings named in § 3; everything else `Money` scale 4 |
| The engine fetches rule rows inside the pure functions, making them untestable | medium | rows are constructor arguments; unit tests build them by hand |

## 10. Rollback

Nothing is deployed and nothing is migrated. A wrong figure is overwritten by the next `compute`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | no new table |
| Flyway only, `ddl-auto` nowhere | no schema change |
| `Money`/`BigDecimal` for money | `Money` end to end; rupee rounding twice, named |
| Index on `tenant_id` plus lookup columns | none needed |
| Expand / contract | nothing |
| No module references another module | `payroll` → `core` (`EmployeeService`, `EmployeePersonalService`) and `shared` (`Money`) |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed** — tenant from the bound context, never a header |
| DEBT-007 no `/api/v1` · DEBT-008 hand-built envelope | **Fixed** |
| DEBT-019 N+1 | **Fixed** — one `versionInForce` read per month, twelve at most; rule rows read once per compute |
| DEBT-022 unscoped finders | **Fixed** |
| Proposed (analysis 2026-09-29): slab lookup ignores FY and age; caps hard-coded; pipeline duplicated | **Fixed by design** — one reader keyed on year, regime and age; every figure from a rule row; one calculator per regime |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | Does `GET …/tax` write anything? | **No.** Preview is pure so the screen can show "what if" per regime. Only `compute` records, and only through `W-32.4`'s `record` |
| 2 | Where does the shared engine live? | **Here**, under `taxcalc/`, so `.2` adds one class and no plumbing |
| 3 | Age category rule | **Age reached by 31 March of the financial year.** A person who turns 60 during the year is a senior citizen for the whole year |
| 4 | Marginal relief | **Implemented**, driven by the rule row's flag; legacy skips it |
| 5 | Where does the income come from? | **Projected from the CTC versions, month by month**, as legacy. Actual pay-run lines are not used: the calculator must work before the first run of the year and for an employee whose run is not yet computed |
| 6 | Previous-employer TDS | **Reduces `annualTax` here** and is reported separately in the summary, as `W-36.1` § 2 expects |
| 7 | One summary table, one `record` call per regime | **Yes** — `W-32.4` decision 3 |

## 14. Open for the founder

| # | Question | Proposed |
|---|---|---|
| 1 | Employer NPS (80CCD(2)) in the new regime needs a benefit component flagged as NPS employer share — a `W-26.1` catalogue column | **Defer** to a `W-26.x` follow-up; until then the deduction is not applied and `assumptions[]` says so |
| 2 | An exit date on `core.employee` so the projection stops at the leaving month | **Defer**; note in `W-13.x` backlog |
