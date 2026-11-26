# Feature: Form 24Q Annexure II Annual Salary & Tax Statement (Q4)

| Field | Value |
|---|---|
| **Feature ID** | `W-89.3` · `PAYROLL-STATUTORY` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/backend/payroll` |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready for CA / founder review |
| **Blocked by** | `W-89.2` (Form 24Q Core & Annexure I) |

## 0. As built — Claude rebuild, 2026-11-25

See `W-89-1-tds-challans.md` §0 for the decisions (Form 138 instead of Form 24Q, quarter by payment date, whole-rupee challans, allocation rules, filing and the problems list). Where §0 there and the sections below disagree, §0 is what the code does.

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | Payroll officer generates and exports Form 24Q Annexure II (Annual employee salary, exemptions, Chapter VI-A deductions, and net tax computation) for Q4 | 1 |
| Frontend area | none (UI in `W-89.4`) | 1 |

---

## 2. Problem & Context

Under Indian Income Tax rules, the final quarterly return (Quarter 4, covering January 1 to March 31) must include **Annexure II** in addition to Annexure I. 

Annexure II contains the complete annual tax computation sheet for every employee who worked for the tenant during the financial year, detailing:
- Total gross salary and perquisites.
- Allowable Section 10 exemptions (HRA, LTA, leave encashment).
- Section 16 deductions (Standard deduction, Professional tax).
- Chapter VI-A deductions (80C, 80D, 80CCD, etc.).
- Tax computed under the employee's chosen regime (Old Regime vs Section 115BAC New Regime).
- Total tax payable vs total TDS deducted.

While `W-36.4` (`Form16Statement`) produces an individual employee statement, **there is currently no batch annual compilation service that aggregates this data across all employees into the official Form 24Q Annexure II return dataset (Record Type `SD`).**

---

## 3. Scope

**In scope:**
- Backend annual aggregation service (`Form24QAnnexure2Service`).
- Applicable strictly for Quarter 4 of a financial year (rejected with `400 Bad Request` if requested for Q1, Q2, or Q3).
- Evaluates every employee employed during the FY (both active and exited).
- Reuses the existing annual compute pipeline:
  - `Form16Assembler` / `Form16Statement` (`W-36.4`).
  - `TaxRecalculationService` (`W-33.3`).
  - Prior payroll imported records (`W-38.1`, `W-38.2`).
  - Employee tax declarations (`W-32`).
- Assembles Annexure II `SD` (Salary Detail) records:
  - Date from / Date to (period of employment in FY).
  - Total salary (Gross pay + perquisites under Sec 17(2) + profits in lieu of salary Sec 17(3)).
  - Total Section 10 exemptions (broken down by 10(13A) HRA, 10(5) LTA, etc.).
  - Deductions under Section 16: Standard deduction (₹50,000 / ₹75,000 depending on FY & regime) and Professional tax.
  - Income/loss from house property (Sec 24(b)) and other reported income.
  - Chapter VI-A deductions (80C, 80CCC, 80CCD(1), 80CCD(1B), 80CCD(2), 80D, 80E, 80G, 80TTA/TTB).
  - Tax computed on total taxable income.
  - Section 87A rebate and 4% Health & Education Cess.
  - Section 89 relief.
  - Total tax payable vs total TDS deducted (current employer + previous employer).
- Exports:
  - **Standard CSV:** Complete audit table for CA review.
  - **e-TDS Text (`.txt`):** NSDL FVU `SD` record structure.
- Guarded by `@RequiresAction("payroll.tax.view")`.

**Out of scope:**
- Challan entry & allocations (handled in `W-89.1`).
- Annexure I quarterly deductee export (handled in `W-89.2`).
- Frontend UI screens (handled in `W-89.4`).

---

## 4. Flow

```
Payroll Officer --> GET /api/v1/payroll/compliance/24q/{fy}/annexure-2/export?format=CSV|TXT
                --> Form24QController.exportAnnexure2
                --> Form24QAnnexure2ServiceImpl.generateAnnexure2
                --> Batches all employees for FY --> Reuses Form16Assembler & TaxRecalculation
                --> Generates SD records --> Outputs CSV / NSDL FVU Text
```

---

## 5. Backend changes

| Layer | File | Change |
|---|---|---|
| DTO | `payroll/compliance/form24q/SalaryDetailRecord.java` | DTO representing Record Type `SD` with all statutory salary & tax fields |
| DTO | `payroll/compliance/form24q/Annexure2PreviewResponse.java` | Summary metrics (Total employees, Total gross, Total taxable, Total tax payable, Total TDS) |
| Service | `payroll/compliance/form24q/Form24QAnnexure2Service.java` | Interface for Annexure II annual batch computation |
| ServiceImpl | `payroll/compliance/form24q/Form24QAnnexure2ServiceImpl.java` | Implements batch employee aggregation, regime separation, Chapter VI-A caps, and formatting |
| Formatter | `payroll/compliance/form24q/Annexure2CsvFormatter.java` | Formats Annexure II dataset into CSV with statutory column headers |
| Controller | `payroll/compliance/form24q/Form24QController.java` | Adds Annexure II preview and export endpoints |

### API Contract

| Method | Path | Params | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/payroll/compliance/24q/{fy}/annexure-2` | — | `Annexure2PreviewResponse` (JSON summary, total tax deducted, discrepancies) | `payroll.tax.view` |
| `GET` | `/api/v1/payroll/compliance/24q/{fy}/annexure-2/export` | `format=CSV` or `format=TXT` | Binary file download (`text/csv` or `text/plain`) with `Content-Disposition: attachment; filename="24Q_{fy}_Q4_Annexure2.[csv|txt]"` | `payroll.tax.view` |

---

## 6. Open Questions for CA Review

1. **FY 2024-25 & 2025-26 Budget Changes:** For FY 2024-25 / 2025-26 under the New Tax Regime (Section 115BAC), standard deduction increased to ₹75,000 and the tax slabs were revised. Does the CA confirm using the exact slab definitions already encoded in `TaxCalculatorNewRegime` (`W-33.1`)?
2. **Prior Employer Figures:** If an employee joined mid-year with Form 12B details entered under `W-38.1` (Prior Payroll), should their prior salary and prior TDS be reported under the dedicated "Salary from previous employer" columns of Annexure II?
3. **Employees with Nil Tax:** Should employees whose taxable income is below the taxable threshold (or reduced to zero via 87A rebate) be included in Annexure II? *(Statutory standard: Yes, all employees must be listed in Q4 Annexure II regardless of whether tax was deducted)*.

---

## 7. Verification

```bash
mvnw clean test -pl payroll -am -Dtest="*Form24QAnnexure2*"
```
- Integration test `Form24QAnnexure2ExportIT`: tests generating Q4 Annexure II for a full financial year across multiple employees:
  - Both Old Regime and New Regime employees.
  - Verification that HRA, standard deduction, 80C, and 80D match Form 16 statements.
  - Rejection with `400` if requested for non-Q4 periods.
  - CSV and TXT structure validation.
