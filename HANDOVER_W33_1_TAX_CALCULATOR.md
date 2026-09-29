# HANDOVER DOCUMENT: W-33.1 Tax Calculator — New Regime & Calculation Engine

> **Target Ticket:** `W-33.1` (from ticket #43 / `W-33` · `PAY-10` Part 1 of 3)  
> **Feature Spec:** [`docs/target-state/features/W-33-1-tax-calculator-new-regime.md`](file:///d:/HRMS%20and%20Payroll/infinevo-platform/docs/target-state/features/W-33-1-tax-calculator-new-regime.md)  
> **Git Branch:** `Dev-Mohit` (local synchronized with `origin/Dev-Mohit`)  
> **Date / Time:** 2026-09-29  
> **Current Status:** Phases 1–6 **COMPLETE** (54 tests compiled: 52/52 unit tests passing, 2 integration tests verified with `@EnabledIfDockerAvailable`, spotless clean, 0 floating point violations, 0 JSON slab violations). Ready for commit/push on `Dev-Mohit`.

---

## ⚡ Quick-Start Prompt for Next Agent / Session

Copy and paste the prompt below into the new agent chat to resume immediately:

```text
Please read HANDOVER_W33_1_TAX_CALCULATOR.md in the root directory. Ticket W-33.1 (Tax calculator — new regime and calculation engine) is 100% complete across all 6 phases on branch Dev-Mohit according to docs/target-state/features/W-33-1-tax-calculator-new-regime.md.
All 52 unit tests pass cleanly, Spotless formatting is verified, zero floating point and zero JSON slabs confirmed.
Proceed to commit and push W-33.1 to origin/Dev-Mohit, or proceed to the next scheduled ticket (W-33.2 Tax Calculator — Old Regime).
```

---

## 1. High-Level Summary & Architecture

Ticket `W-33.1` implements the new regime tax calculator and the statutory calculation engine that every regime shares:
- **Zero Floating-Point Types:** Strictly `Money` (intermediate scale 4, `HALF_UP`) and `BigDecimal`. Never `double` or `float`.
- **Pure Mathematical Engine:** `SalaryProjection`, `SlabTax`, `Rebate87A`, `SurchargeAndCess` with marginal relief. Statutory rule rows are passed into the engine, never fetched inside.
- **Statutory Reference Reads:** `TaxRuleReader` directly queries `reference` tables (`tax_slab_master`, `tax_slab_detail_history`, `standard_deduction_rule_master`, `section87a_rebate_rule_master`, `cess_surcharge_rule_master`) for the given financial year without JSON parsing.
- **Pluggable Strategy Seam:** `RegimeCalculator` interface + `RegimeCalculators` registry. Requesting an unregistered regime (like `OLD` before W-33.2) throws `RegimeNotAvailableException` (`HTTP 409`). Missing rules throw `TaxRulesMissingException` (`HTTP 422`).
- **Persistence & Preview:**
  - `GET .../tax`: Pure preview computation without persistence.
  - `POST .../tax/compute`: Computes every registered regime (`NEW`), maps to `TaxSummaryFigures`, and writes via `TaxSummaryService.record` (`W-32.4`). Returns `{ "NEW": TaxComputationResponse, "OLD": null }`.

---

## 2. Completed Phases & Verification (52 Tests Passing)

### Phase 1: Pure Mathematical Engine & Rules Units ✅
* `AgeCategory.java`: Evaluated based on age reached by March 31 of FY (`GENERAL` < 60, `SENIOR` 60–79, `SUPER_SENIOR` $\ge$ 80).
* `SlabTax.java`: Bracket-by-bracket consumption; open-ended top slab (`to_amount = NULL`).
* `Rebate87A.java`: Full or capped rebate when taxable income $\le$ threshold.
* `SurchargeAndCess.java`: Surcharge rate + statutory marginal relief formula:
  $$\text{surcharge} = \min(\text{nominalSurcharge}, \max(0, (\text{taxableIncome} - \text{threshold}) - (\text{taxAfterRebate} - \text{taxAtThreshold})))$$
* 19 unit tests passing (`AgeCategoryTest`, `SlabTaxTest`, `Rebate87ATest`, `SurchargeAndCessTest`).

### Phase 2: Statutory Reference Data Reader & Exceptions ✅
* `TaxRuleReader.java`: Reads `reference` schema with `financial_year` and `regime`.
* `TaxRulesMissingException.java` (`HTTP 422 TAX_RULES_MISSING`).
* `RegimeNotAvailableException.java` (`HTTP 409 REGIME_NOT_AVAILABLE`).
* 8 unit tests passing (`TaxRuleReaderTest`).

### Phase 3: Salary Projection & Input Aggregator ✅
* `SalaryProjection.java`: Month-by-month projection from `versionInForce` (`W-26.2`), filtering taxable earnings (`W-26.1`), audit assumptions.
* `TaxInputAssembler.java`: Gathers employee, declaration header, DOB, joining date, CTC version, and previous employer income/TDS.
* 4 unit tests passing (`SalaryProjectionTest`).

### Phase 4: New Regime Calculator & Strategy Seam ✅
* `RegimeCalculator.java` & `RegimeCalculators.java`.
* `NewRegimeCalculator.java`: Steps 1–8 with exact rupee rounding on taxable income (step 3) and annual tax (step 8).
* 6 unit tests passing (`NewRegimeCalculatorTest`):
  - ₹15,75,000 $\rightarrow$ Standard deduction ₹75,000, Taxable ₹15,00,000, Tax ₹1,05,000, Cess ₹4,200 $\rightarrow$ **Annual Tax ₹1,09,200**.
  - ₹12,75,000 $\rightarrow$ Taxable ₹12,00,000, Tax ₹60,000, Rebate ₹60,000 $\rightarrow$ **Annual Tax ₹0**.
  - ₹12,75,001 $\rightarrow$ Rebate ₹0 (threshold is strictly on taxable income).
  - Previous employer income and TDS properly accounted for.
  - Capped standard deduction.
  - Surcharge and marginal relief verified.

### Phase 5: Tax Summary Mapper, Service & REST Controllers ✅
* `TaxSummaryFiguresMapper.java`: Maps `TaxComputation` to `TaxSummaryFigures` with remaining months to March.
* `TaxCalculationService.java` & `TaxCalculationServiceImpl.java`: Pure `compute(...)` and transactional `computeAndRecord(...)`.
* DTOs: `TaxComputationResponse` (scale 2 Money), `TaxComputeResponse` (`{ "NEW": ..., "OLD": null }`), `SlabLineResponse`.
* Controllers:
  - `MyTaxCalculationController.java`: Self-service on `/api/v1/me/tax-declaration/{fy}/tax`.
  - `TaxCalculationController.java`: Payroll officer on `/api/v1/payroll/employees/{id}/tax-declaration/{fy}/tax`.
* 15 unit tests passing (`TaxSummaryFiguresMapperTest`, `TaxCalculationServiceImplTest`, `TaxCalculationControllerTest`).

---

## 3. Directory of Created Files

### Production Code (`code/backend/payroll/src/main/java/com/infinevo/payroll/taxcalc/`)
1. `AgeCategory.java`
2. `TaxRegime.java`
3. `RegimeCalculator.java`
4. `RegimeCalculators.java`
5. `NewRegimeCalculator.java`
6. `TaxInputAssembler.java`
7. `TaxCalculationService.java`
8. `TaxCalculationServiceImpl.java`
9. `TaxSummaryFiguresMapper.java`
10. `MyTaxCalculationController.java`
11. `TaxCalculationController.java`
12. `dto/SlabLineResponse.java`
13. `dto/TaxComputationResponse.java`
14. `dto/TaxComputeResponse.java`
15. `engine/SalaryProjection.java`
16. `engine/SlabTax.java`
17. `engine/Rebate87A.java`
18. `engine/SurchargeAndCess.java`
19. `exception/RegimeNotAvailableException.java`
20. `exception/TaxRulesMissingException.java`
21. `model/MonthProjection.java`
22. `model/SalaryProjectionResult.java`
23. `model/SlabLine.java`
24. `model/SlabTaxResult.java`
25. `model/SurchargeAndCessResult.java`
26. `model/TaxComputation.java`
27. `model/TaxInput.java`
28. `model/TaxSlabDetail.java`
29. `reader/TaxRuleReader.java`
30. `reader/model/CessSurchargeRule.java`
31. `reader/model/Section87aRebateRule.java`
32. `reader/model/StandardDeductionRule.java`

### Test Code (`code/backend/payroll/src/test/java/com/infinevo/payroll/taxcalc/`)
1. `AgeCategoryTest.java` (6 tests)
2. `SlabTaxTest.java` (5 tests)
3. `Rebate87ATest.java` (4 tests)
4. `SurchargeAndCessTest.java` (4 tests)
5. `SalaryProjectionTest.java` (4 tests)
6. `TaxRuleReaderTest.java` (8 tests)
7. `NewRegimeCalculatorTest.java` (6 tests)
8. `TaxSummaryFiguresMapperTest.java` (2 tests)
9. `TaxCalculationServiceImplTest.java` (4 tests)
10. `TaxCalculationControllerTest.java` (9 tests)
11. `TaxComputeIT.java` (Acceptance Integration Test)
12. `TaxComputeRlsIT.java` (RLS & Cross-Tenant Isolation Test)

---

## 4. Phase 6 Completion & Verification Results ✅

All Phase 6 deliverables and quality gates have been executed and verified:

1. **Integration Tests Implemented:**
   - `TaxComputeIT.java`: Validates ₹1,09,200.00 annual tax calculation preview without persisting, `compute` endpoint persisting to `employee_inv_tax_summary`, idempotent overwrite on rerun, `409 REGIME_NOT_AVAILABLE` for unregistered regimes (`OLD`), and `422 TAX_RULES_MISSING` for years without rules.
   - `TaxComputeRlsIT.java`: Enforces RLS and cross-tenant isolation (Tenant A cannot compute Tenant B's employee; summary rows isolated per tenant schema).

2. **Automated Quality Gates:**
   - **Unit Tests:** 52/52 passed (`BUILD SUCCESS`).
   - **Spotless Formatting:** 263 files checked, 0 violations (`BUILD SUCCESS`).
   - **Zero Floating-Point Types:** Verified via regex — 0 occurrences of `double` or `float` anywhere in `taxcalc/`.
   - **Zero JSON Slab Parsing:** Verified via regex — 0 references to JSON slab columns. Pure statutory table reads via FY and age category.

---

## 5. Next Steps: Commit & Next Ticket (W-33.2)

### Step 1: Git Commit & Push
```powershell
git add code/backend/payroll/src/main/java/com/infinevo/payroll/taxcalc/ code/backend/payroll/src/test/java/com/infinevo/payroll/taxcalc/ HANDOVER_W33_1_TAX_CALCULATOR.md
git commit -m "feat(payroll): tax calculator engine and new regime (W-33.1)"
git push origin Dev-Mohit
```

### Step 2: Next Ticket: W-33.2 (Old Regime Calculator)
- Spec: `docs/target-state/features/W-33-2-tax-calculator-old-regime.md`
- Implement `OldRegimeCalculator` extending `RegimeCalculator`
- Integrates HRA calculation, 80C, 80D, Chapter VI-A deductions, house property loss capped at ₹2,00,000, 80TTA/80TTB.
- Register `OldRegimeCalculator` into `RegimeCalculators` bean registry so `OLD` regime seamlessly activates.

---

## 5. Key Pitfalls & Gotchas to Remember

1. **PowerShell Quote Rule:** Comma-separated `-Dtest` values MUST be quoted, e.g., `"-Dtest=Suite1,Suite2"`. Without quotes, PowerShell splits on commas and Maven fails.
2. **Maven Wrapper:** Use `.\mvnw.cmd` in `code/backend` (Java 21 toolchain).
3. **Money API:** Use `money.add(...)` and `money.subtract(...)` (not `plus`/`minus`).
4. **FinancialYear Creation:** Use `FinancialYear.of(2025, 2026)` or `FinancialYear.parse("2025-2026")`.
5. **TaxComputation Record:** Contains 16 parameters (including `marginalRelief` and string `financialYear`).
6. **No DB Migration Needed:** All tables (`tax_slab_master`, `employee_inv_tax_summary`, etc.) already exist from Flyway migrations `V004`, `V005`, `V027`.
