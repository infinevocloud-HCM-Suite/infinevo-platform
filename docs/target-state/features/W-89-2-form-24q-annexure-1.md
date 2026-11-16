# Feature: Form 24Q Quarterly Deductee Mapping & Annexure I Export

| Field | Value |
|---|---|
| **Feature ID** | `W-89.2` · `PAYROLL-STATUTORY` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/backend/payroll` |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready for CA / founder review |
| **Blocked by** | `W-89.1` (TDS Challans) |

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | Payroll officer generates and exports quarterly Form 24Q Annexure I (Challan + Deductee mapping) in CSV and NSDL e-TDS text format | 1 |
| Frontend area | none (UI in `W-89.4`) | 1 |

---

## 2. Problem & Context

Under Indian Income Tax rules, Form 24Q must be submitted every quarter (Q1: Apr–Jun, Q2: Jul–Sep, Q3: Oct–Dec, Q4: Jan–Mar). **Annexure I** is mandatory for all four quarters. It links government deposit challans (Record Type `CD`) to individual employee salary TDS deductions (Record Type `DD`).

Currently:
- Tax deductor details are stored in `payroll.tax_deductor` (`W-36.3`).
- Monthly employee TDS lines are calculated in `payroll.pay_figure` (`W-29.2`).
- Employee PAN and names are stored in `core.employee` and `core.employee_statutory`.
- **There is no service or export engine to assemble quarterly TDS deductee lines, reconcile them against deposited challans (`W-89.1`), and generate the official Form 24Q Annexure I file.**

---

## 3. Scope

**In scope:**
- Backend quarterly aggregation service (`Form24QAnnexure1Service`).
- Aggregates all closed pay runs (`APPROVED`, `PAID`) for the 3 months of the specified quarter.
- Validates deductee information:
  - Valid PAN format (`^[A-Z]{5}[0-9]{4}[A-Z]{1}$`).
  - Flags non-PAN deductees (deduction at higher rate under Sec 206AA).
  - Tax regime flag: Section `92B` (Regular / Old regime) vs Section `92B(1A)` (New tax regime under Section 115BAC).
- Assembles Annexure I datasets:
  - **Challan Detail (`CD`):** BSR code, deposit date, challan serial no, minor head (`200`/`400`), tax, cess, total deposited.
  - **Deductee Detail (`DD`):** Employee PAN, name, date of payment, gross amount paid, date of tax deduction, tax deducted, tax deposited, reason flag for non/lower deduction.
- Exports format:
  - **Standard CSV:** Structured for CA review and direct upload into tax filing software (ClearTax, Winman, CompuTax).
  - **e-TDS Text (`.txt`):** NSDL RPU/FVU compatible structured text with `FH`, `BH`, `CD`, `DD` record hierarchy.
- Guarded by `@RequiresAction("payroll.tax.view")`.

**Out of scope:**
- Challan entry & table creation (handled in `W-89.1`).
- Annexure II annual salary compute (handled in `W-89.3`).
- Direct submission via Income Tax e-filing API.

---

## 4. Flow

```
Payroll Officer --> GET /api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1/export?format=CSV|TXT
                --> Form24QController.exportAnnexure1
                --> Form24QAnnexure1ServiceImpl.generateAnnexure1
                --> Loads TaxDeductor (W-36.3) + TdsChallans (W-89.1) + PayFigures for Quarter
                --> Assembles CD & DD records --> Generates CSV / NSDL FVU Text
```

---

## 5. Backend changes

| Layer | File | Change |
|---|---|---|
| DTO | `payroll/compliance/form24q/Annexure1PreviewResponse.java` | DTO representing summary counts, total gross, total TDS, and challan reconciliation status |
| DTO | `payroll/compliance/form24q/ChallanDetailRecord.java` | DTO for Record Type `CD` |
| DTO | `payroll/compliance/form24q/DeducteeDetailRecord.java` | DTO for Record Type `DD` |
| Service | `payroll/compliance/form24q/Form24QAnnexure1Service.java` | Service interface for preview and file compilation |
| ServiceImpl | `payroll/compliance/form24q/Form24QAnnexure1ServiceImpl.java` | Implements quarterly aggregation, PAN validation, section code mapping, and file formatters |
| Formatter | `payroll/compliance/form24q/Annexure1CsvFormatter.java` | Formats the dataset into CSV with standard statutory column headers |
| Formatter | `payroll/compliance/form24q/NsdlFvuTextFormatter.java` | Formats the dataset into NSDL e-TDS text specification format |
| Controller | `payroll/compliance/form24q/Form24QController.java` | REST endpoints for preview and download |

### API Contract

| Method | Path | Params | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1` | — | `Annexure1PreviewResponse` (JSON metadata, reconciliation status, missing PAN list) | `payroll.tax.view` |
| `GET` | `/api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1/export` | `format=CSV` or `format=TXT` | Binary file download (`text/csv` or `text/plain`) with `Content-Disposition: attachment; filename="24Q_{fy}_{quarter}_Annexure1.[csv|txt]"` | `payroll.tax.view` |

---

## 6. Open Questions for CA Review

1. **Section Code for 115BAC:** Should employees on the New Tax Regime (Section 115BAC) be flagged specifically with code `92B(1A)` or standard `92B` with a New Regime remark in Annexure I?
2. **Lower Deduction Certificate (Section 197):** If an employee has a lower/nil deduction certificate from the IT department, does the CA require entering the 10-character certificate number on the employee tax profile?
3. **Shortfall Handling:** If total TDS deducted in a quarter exceeds total challan amounts deposited, should the export generate with a warning header, or block export until fully reconciled?

---

## 7. Verification

```bash
mvnw clean test -pl payroll -am -Dtest="*Form24QAnnexure1*"
```
- Integration test `Form24QAnnexure1ExportIT`: tests generating Annexure 1 for Q1 across 3 pay runs, verifying:
  - Correct BSR code, serial number, and deposit amounts in `CD` records.
  - Correct employee PAN, gross pay, and TDS deduction amounts in `DD` records.
  - Proper handling of non-PAN employees (higher deduction rate flag).
  - Validation that unapproved/draft pay runs are excluded.
