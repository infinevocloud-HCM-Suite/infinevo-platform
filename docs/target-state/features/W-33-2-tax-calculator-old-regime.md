# Feature: Tax calculator — old regime with exemptions and section deductions

| Field | Value |
|---|---|
| **Feature ID** | `W-33.2` · from ticket #44 (`W-33`) · `PAY-10` part 2 of 3 |
| **Promoted to** | `docs/target-state/features/W-33-2-tax-calculator-old-regime.md` on the developer's `dev-<name>` branch — **`W-33-2` with hyphens**, never `W-33.2`; `guard-edit` blocks the dotted form |
| **Owner** | mohit |
| **Apps touched** | `code/backend/payroll` |
| **Related gaps** | BUG-002 (fixed), DEBT-019 (fixed), DEBT-022 (fixed); the analysis report's proposed entries (slab lookup ignores FY and age, caps hard-coded, pipeline copied five times) fixed by design |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | `W-33.1` — the engine and `RegimeCalculator` · `W-32.2` — housing rows · `W-32.3` — section 6A, pre-tax, previous employment rows and `Section6AItemReader` · `W-32.4` — other income rows. `W-31.3`'s EPF employee line and `W-31.2`'s `resolve` are read when present (§ 3, pre-tax precedence) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an employee on the old regime computes their tax for the year with every declared exemption and deduction applied at the statutory cap | 1 |
| Frontend area | none | 1 |

Within cap. Sized L (`10-scoping.md:203`): "old regime is materially harder than new. Do
not assume symmetry." This spec is one calculator class plus five pure section calculators
plugged into `.1`'s engine.

---

## 1. Problem

All citations are `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/employeeitdeclaration/taxCalculator/OldTaxCalculationServiceImpl.java`, code being replaced.

- **The slab lookup ignores the year and the age.** `findByTaxRegimeAndIsActiveTrue("OLD")…findFirst()` (`:1172-1177`). Whichever `OLD` row is first wins; a senior citizen gets the general slab
- **Caps are constants although the rule tables hold them.** Standard deduction ₹50,000 (`:649`), let-out 30 % (`:589-592`), 80TTA ₹10,000 and 80TTB ₹50,000 (`:902-916`), 80C group ₹1,50,000, 80D ₹25,000 self and ₹50,000 parents (`:949-956`). A Finance Act change is a code change
- **The same pipeline is written five times** — `:131`, `:1707`, `:2347`, `:3240`, `:4498` — one per entry point, 5,583 lines in all. They have already drifted (the Chapter VI-A block at `:691-810` is a commented-out earlier version of `:880-960`)
- **House-property loss is not combined.** Self-occupied interest (`:1011-1012`) and the let-out loss (`:522-629`) are capped separately; the law caps the *total* loss set off against salary
- **What is right and ported as-is:** HRA month by month over each rent period (`:340-400`), 24(b)/80EE/80EEA from the home-loan rule rows (`:1011-1012`, `:2617-2618`), per-item caps from the section 6A master (`:928-929`)

## 2. Scope

**In scope**

- `OldRegimeCalculator` implementing `.1`'s `RegimeCalculator` for `OLD`
- Five pure section calculators: `HraExemption`, `HousePropertyIncome`, `OtherIncomeAndInterest`, `ChapterViaDeductions`, `SalaryDeductions` (standard deduction and professional tax under section 16)
- `TaxInput.gather` extended to load the `W-32.2`–`.4` sections and the statutory figures
- `TaxComputation` extended with the section breakdown; `TaxSummaryFiguresMapper` fills `exemption_under_section10`, `exemption_under_section6a`, `other_sources_income`
- Slabs by `(fy, OLD, ageCategory)` — the `V027` rows finally read (`active-work.md`, 2026-09-26: "the `V099` annual-update fixture gains the age pair when `W-33` first reads them" — done here)

**Out of scope**

- Anything the new regime needs — `.1`
- History and triggers — `.3`
- 80D's senior-citizen split, 80DDB's senior ceiling, 80G's qualifying-amount rules — the item master carries one `max_limit` per item (`V005__reference_tax_seed.sql:205-207`) and no age or donee columns; § 14
- Loss carry-forward — `is_loss_carry_forward_allowed` is seeded `FALSE` (`V005:167`); the calculator refuses nothing and carries nothing
- Screens — `W-47.3b`

## 3. Flow

`OldRegimeCalculator.compute(input, fy)`, each step a pure function over `Money` with the rule rows handed in:

| Step | Rule | Source rows | Ported from |
|---|---|---|---|
| 1 | `salary` from `.1`'s `SalaryProjection`; `basicAnnual` and `hraReceivedAnnual` are the same projection restricted to earnings of type `BASIC` and `HRA` (`W-26-1-…md:128`) | CTC versions | `:473-493` |
| 2 | **HRA exemption**, per rent row, per month in the row's `[from_month, to_month]` ∩ the projected months: `min(hraMonth, max(0, rent − basicMonth × basic_da_percent_threshold/100), basicMonth × (is_metro ? metro_percent : non_metro_percent)/100)`; sum over months. Zero when the header's `is_staying_in_rented_house` is false or there is no `HRA` earning | `hra_rule_master (fy, OLD)` · `employee_inv_house_rent` | `:340-400` |
| 3 | **Salary deductions (section 16)**: `standardDeduction = min(rule.amount, salary − HRA exemption)`; `professionalTax` = § 3a | `standard_deduction_rule_master (fy, OLD)` — 50,000 | `:641-661`, constant replaced |
| 4 | `incomeFromSalary = salary − hraExemption − standardDeduction − professionalTax + prevEmployment.INCOME` | | |
| 5 | **House property**: `selfOccupiedInterest = min(Σ home_loan.interest_paid, rule 24B SELF_OCCUPIED max_limit)` when `is_repaying_self_occupied_loan`; `letOutNet = Σ employee_inv_let_out_property.net_income_loss` (already net of 30 % and interest, `W-32-2-…md:121`); `houseProperty = letOutNet − selfOccupiedInterest`; if negative, `max(houseProperty, −max_loss_setoff_limit)` | `home_loan_rule_master (fy, '24B', INTEREST, SELF_OCCUPIED)` · `let_out_property_rule_master (fy, OLD)` | `:1011-1012`, `:522-629`; combined cap is the fix in § 1 |
| 6 | **Other sources**: `otherIncome = Σ employee_inv_other_income.amount` | `employee_inv_other_income` | `:668-686` |
| 7 | `grossTotalIncome = max(0, incomeFromSalary + houseProperty + otherIncome)` | | |
| 8 | **Chapter VI-A** (§ 3b): per item `min(Σ rows, item.max_limit)`, then per `category_group_code` `min(Σ items, groupCap)`; **80TTA/80TTB** on interest: `GENERAL` ⇒ `min(SAVINGS_INTEREST, 80TTA.max_limit)`; `SENIOR`/`SUPER_SENIOR` ⇒ `min(SAVINGS_INTEREST + FD_INTEREST, 80TTB.max_limit)`; **80EE/80EEA**: interest above the 24(b) cap, `min(excess, rule.max_limit)`, only when `is_first_time_buyer` and `loan_sanctioned_on` in `[loan_sanction_from, loan_sanction_to]`; `chapterVia = min(Σ, grossTotalIncome)` | `section6a_item_master` via `Section6AItemReader.groupCap` (`W-32-3-…md:100`) · `other_income_rule_master (fy, section_code)` · `home_loan_rule_master (fy, '80EE'/'80EEA')` | `:880-960`, `:2617-2618` |
| 9 | `taxableIncome = grossTotalIncome − chapterVia`, rounded down to the rupee | | |
| 10 | Slabs by `(fy, OLD, input.ageCategory)`, then rebate, surcharge with marginal relief, cess, minus previous-employer TDS — **`.1`'s functions unchanged** | `tax_slab_master` + `V027` rows · `section87a_rebate_rule_master (fy, OLD)` — 12,500 up to 5,00,000 · surcharge `OLD` top band 37 % | `.1` § 3 steps 4–8 |

**3a. Pre-tax figures — precedence** (`W-32-3-…md:238`, decision 2: "`W-33`'s to write"):

| Figure | First | Fallback |
|---|---|---|
| Employee PF (80C group) | Σ over projected months of the version's `ctc_epf_component` `EPF_EMPLOYEE` `monthly_amount` (`W-31-3-…md:135-140`) | `employee_inv_pre_tax_deduction` `EMPLOYEE_PF` |
| Professional tax (section 16(iii)) | Σ over projected months of `ProfessionalTaxService.resolve(gross, gender, period)` (`W-31-2-…md`) | `employee_inv_pre_tax_deduction` `PROFESSIONAL_TAX` |
| VPF (80C group) | declared `VPF` row | — |
| Employee NPS | declared `NPS_EMPLOYEE`: first `min(amount, 80CCD(1B).max_limit)` under 80CCD(1B), the remainder into the 80C group as 80CCD(1) | — |

"Present" means the bean exists and returns a figure; both `W-31` services are optional
dependencies (`ObjectProvider`), as `W-32.4` did for its sections.

**3b. Chapter VI-A mechanics.** Home-loan `principal_paid` (`W-32.2`) is an 80C item and joins
the 80C group sum. Items whose `is_allowed_in_new_regime` is false are the old regime's
business — nothing is filtered here; `W-32.3` already refused rows the header's regime does
not allow. An item with `max_limit NULL` (80E, 80G, 80GGC) is taken at the declared amount;
`W-34` verifies the proof.

## 4. Backend changes

All under `code/backend/payroll/src/main/java/com/infinevo/payroll/taxcalc/`.

| Layer | File | Change |
|---|---|---|
| Calculator | `OldRegimeCalculator` | new, `@Component`, § 3 |
| Section calculators | `HraExemption`, `HousePropertyIncome`, `OtherIncomeAndInterest`, `ChapterViaDeductions`, `SalaryDeductions` | new, pure; each returns a small record with the figure and its lines so the response can show the working |
| Input (change) | `TaxInput`, `TaxInputGatherer` | fills the `Optional` sections `.1` left empty: `houseRent[]`, `homeLoans[]`, `letOutNet`, `section6a[]` (with the item's `section_code`, `category_group_code`, `max_limit`), `preTax` (map by kind), `otherIncome` (map by kind), `epfEmployeeAnnual?`, `professionalTaxAnnual?`, the header's three flags |
| Reference reads (change) | `TaxRuleReader` | gains `hra(fy)`, `homeLoan(fy, section, component, propertyType)`, `letOut(fy)`, `otherIncomeRule(fy, section)` |
| Result (change) | `TaxComputation` | gains `hraExemption`, `professionalTax`, `houseProperty` (with `selfOccupiedInterest`, `letOutNet`, `lossCapApplied`), `otherIncome`, `chapterVia` (lines per item and group with declared, allowed, cap), `interestDeduction` (80TTA or 80TTB), `additionalHomeLoanInterest` (80EE/80EEA) |
| Mapper (change) | `TaxSummaryFiguresMapper` | `exemption_under_section10 = hraExemption`; `exemption_under_section6a = chapterVia + interestDeduction + additionalHomeLoanInterest`; `other_sources_income = otherIncome` |

No new endpoints. `.1`'s `GET …/tax?regime=OLD` stops returning `409 REGIME_NOT_AVAILABLE`
and `POST …/tax/compute` fills both summary rows.

## 5. Frontend changes

None.

## 6. Database changes

None. Every rule read here is on `main` in `reference` (`V004`, `V005`, `V027`); every
declaration table is `W-32.2`–`.4`'s.

- [x] `tenant_id` — no new table
- [x] Index — none
- [x] Money — `Money` throughout; two rupee roundings, `.1` § 3
- [x] Expand / contract — nothing

## 7. Tests

Every hand calculation below uses FY 2025-2026 rows as seeded.

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../taxcalc/HraExemptionTest.java` | basic 50,000/month, HRA 20,000/month, rent 18,000/month metro, 12 months ⇒ per month `min(20,000, 13,000, 25,000) = 13,000` ⇒ **1,56,000**; non-metro ⇒ `min(20,000, 13,000, 20,000)`; rent period Oct–Mar only ⇒ 6 months; rent below 10 % of basic ⇒ 0; flag false ⇒ 0; two overlapping-free periods add |
| Unit | `payroll/.../taxcalc/HousePropertyIncomeTest.java` | self-occupied interest 2,50,000 ⇒ −2,00,000; let-out net −1,50,000 plus self-occupied 1,00,000 ⇒ −2,50,000 capped to **−2,00,000** with `lossCapApplied`; let-out net +60,000 minus 2,00,000 ⇒ −1,40,000 uncapped; flag false ⇒ let-out only |
| Unit | `payroll/.../taxcalc/ChapterViaDeductionsTest.java` | 80C rows 1,20,000 + PF 40,000 + loan principal 30,000 ⇒ group capped **1,50,000**; 80CCD(1B) 70,000 ⇒ 50,000 under 1B and 20,000 into the 80C group; 80D 30,000 ⇒ 30,000 (item cap 1,00,000); 80E 3,00,000 ⇒ 3,00,000 (no cap); 80TTA on savings 14,000 ⇒ 10,000; `SENIOR` with savings 14,000 + FD 60,000 ⇒ 50,000 under 80TTB; 80EEA on interest 3,20,000, first-time buyer, sanctioned 2020-06-01 ⇒ 1,20,000; same loan sanctioned 2023-01-01 ⇒ 0; total capped at gross total income |
| Unit | `payroll/.../taxcalc/OldRegimeCalculatorTest.java` | **the hand calculation**: salary 12,00,000 (basic 6,00,000, HRA 2,40,000), rent 20,000/month metro ⇒ HRA exemption `min(20,000, 15,000, 25,000) × 12 = 1,80,000`; standard deduction 50,000; PT 2,400; 80C 1,50,000; 80D 25,000 ⇒ taxable **7,92,600** ⇒ slab tax 12,500 + 58,520 = 71,020; cess 2,840.80 ⇒ **annual tax 73,861**; the same on a `SENIOR` ⇒ slab tax 10,000 + 58,520 = 68,520, cess 2,740.80 ⇒ **71,261**; taxable 4,80,000 ⇒ tax 11,500, rebate 11,500 ⇒ **0** |
| Unit | `payroll/.../taxcalc/PreTaxPrecedenceTest.java` | EPF line present and declared row present ⇒ the line wins; no line ⇒ the row; neither ⇒ 0 with an assumption line |
| Integration | `payroll/.../taxcalc/OldRegimeComputeIT.java` | **the acceptance test**: the `OldRegimeCalculatorTest` employee built through the real APIs — CTC (`W-26.2`), header `OLD` with the three flags, rent (`W-32.2`), 80C and 80D rows (`W-32.3`), PT declared (`W-32.3`); `GET …/tax?regime=OLD` ⇒ `annual_tax 73,861.00` and `exemption_under_section10 1,80,000.00`; `POST …/tax/compute` ⇒ both summary rows filled, `OLD` and `NEW` differ; date of birth moved to 1960 ⇒ `71,261.00` |
| Integration | `payroll/.../taxcalc/AgeSlabIT.java` | `TaxRuleReader.slabs(2025-2026, OLD, SENIOR)` returns one master with four brackets whose first `to_amount` is 3,00,000; `SUPER_SENIOR` 5,00,000; `GENERAL` 2,50,000 — the `V027` rows read for the first time |
| Fixture (change) | `migration/.../db/migration-annual/reference` (`V099` simulation) | gains the `SENIOR` and `SUPER_SENIOR` pair, closing the `W-09.1` deferral (`W-09-1-…md:163`) |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT m.age_category, min(d.to_amount) FROM reference.tax_slab_master m
     JOIN reference.tax_slab_detail_history d ON d.slab_master_id = m.id
    WHERE m.financial_year='2025-2026' AND m.regime='OLD' GROUP BY 1 ORDER BY 1;"
grep -rn "50000\|150000\|25000\|10000\|BigDecimal.valueOf(30)" code/backend/payroll/src/main/java/com/infinevo/payroll/taxcalc/ || echo "no hard-coded caps"
grep -rn "findByTaxRegimeAndIsActiveTrue\|findFirst" code/backend/payroll/src/main/java/com/infinevo/payroll/taxcalc/ || echo "no first-row lookups"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| Old-regime exemption limits | `GENERAL 250000` · `SENIOR 300000` · `SUPER_SENIOR 500000` |
| Hard-coded caps in the calculator | `no hard-coded caps` (the literals live in tests only) |
| First-row lookups | `no first-row lookups` |
| Suite | green, no skips; `OldRegimeComputeIT` passing with `73,861.00` and `71,261.00` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A cap typed into the code "for now" | **high** — legacy did it six times | § 8's grep is a gate; every cap in § 3 names its rule row |
| HRA computed on annual figures instead of month by month, so a half-year rent period is over-exempted | high | `HraExemptionTest` Oct–Mar case |
| Self-occupied and let-out losses capped separately, as legacy | medium | `HousePropertyIncomeTest` combined case |
| The senior slab never selected because the reader is called with `GENERAL` | medium | `AgeSlabIT` and the `OldRegimeComputeIT` date-of-birth case |
| The calculator grows a second copy of the pipeline for "with proofs", as legacy `:2347` | medium | proofs are `W-34`'s; verified amounts replace declared amounts in `TaxInput`, one pipeline |
| `W-31.2`/`W-31.3` not on `main` when this is built | medium | optional beans; the fallback rows are the declared figures; `PreTaxPrecedenceTest` covers all three cases |

## 10. Rollback

Nothing is deployed and nothing is migrated.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | no new table |
| Flyway only, `ddl-auto` nowhere | no schema change (one test fixture) |
| `Money`/`BigDecimal` for money | `Money` end to end |
| Index on `tenant_id` plus lookup columns | none needed |
| Expand / contract | nothing |
| No module references another module | `payroll` → `core`, `shared` only; `W-31` services are in `payroll` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 | **Fixed** — bound tenant only |
| DEBT-019 N+1 | **Fixed** — one read per section table per compute |
| DEBT-022 unscoped finders | **Fixed** |
| Proposed: slab lookup ignores FY and age · caps hard-coded · five copies | **Fixed by design** — § 3, § 8 |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | House-property loss: cap each piece or the total? | **The total**, at `max_loss_setoff_limit`. That is section 71(3A); legacy capped the pieces |
| 2 | 80TTB base | **Savings plus fixed-deposit interest**, for `SENIOR` and above. 80TTB covers all deposit interest; legacy took savings only (`:915-916`) |
| 3 | Employee NPS split | **80CCD(1B) first up to its cap, remainder into the 80C group.** That is the employee's better outcome and what a reviewer expects |
| 4 | Pre-tax precedence | **Structure and statutory service first, declared row second** — `W-32.3` decision 2 |
| 5 | Verified proofs | **Not here.** `W-34` writes verified amounts; `TaxInputGatherer` will prefer them when that column exists. One pipeline, not a second "with POI" copy |
| 6 | 80D senior split | **Not modelled** — the item master has one cap. § 14 |

## 14. Open for the founder

| # | Question | Proposed |
|---|---|---|
| 1 | 80D (`V005:205`) is seeded as one item, cap 1,00,000, while the Act gives 25,000 self + 25,000/50,000 parents by age; 80DDB and 80U likewise have age or severity tiers | **Accept the single cap for FY 2025-26** and open a `W-09.x` seed ticket that splits the items; the calculator picks up new rows without a code change |
| 2 | Annual `V099` fixture change is a test-only file under `migration` — a second module in the branch | **Grant** — no production script |
