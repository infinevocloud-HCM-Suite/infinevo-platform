# Feature: PF ECR File (Electronic Challan cum Return)

| Field | Value |
|---|---|
| **Feature ID** | `W-87` · `PAY-STATUTORY-PF-ECR` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Apps touched** | `code/backend/payroll`, `code/frontend` (`src/payroll/payrun`) |
| **Related gaps** | `DEV-TRACKER.md` row `W-87`; legacy generated raw salary reports but lacked standard EPFO ECR v2.0 file export (`legacy/Payroll-Bend-SBoot/.../PayRunExportController.java` generated only internal CSVs) |
| **Status** | Ready |
| **Blocked by** | nothing |

---

## 1. What and why

Every Indian employer registered under the *Employees' Provident Funds and Miscellaneous Provisions Act, 1952* is statutorily mandated to file monthly returns and remit provident fund contributions by the 15th of the subsequent month via the **EPFO Unified Employer Portal** (`unifiedportal-emp.epfindia.gov.in`).

The EPFO portal does not accept arbitrary spreadsheets or PDFs; it strictly mandates an **Electronic Challan cum Return (ECR 2.0)** text file formatted with the exact delimiter `#~#` (hash-tilde-hash), containing 11 specific columns without headers, keyed by each employee's Universal Account Number (UAN).

Today in `infinevo-platform`:
- The payroll engine calculates EPF lines (`EPF_EMPLOYEE`, `EPF_EMPLOYER`, `EPS_EMPLOYER`, `EDLI`, `EPF_ADMIN`) during pay run computation ([`StatutoryLineContributor.java:180-237`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun/StatutoryLineContributor.java#L180-L237)).
- `W-95` delivered ESI and state-wise Professional Tax (PT) return reports ([`StatutoryReportController.java`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/report/StatutoryReportController.java)).
- However, generating the official monthly EPFO ECR file requires manual extraction, calculation of capped wages vs. earned gross, counting non-contributing period (NCP/LOP) days, and re-formatting into EPFO's `#~#` string syntax.

This feature delivers **PF ECR File Generation**:
1. Direct generation of the official EPFO ECR v2.0 `.txt` upload file from any `COMPUTED` or `PAID` pay run with single-click download.
2. In-app audit preview and validation (flagging employees with missing UANs or wage cap anomalies before filing).
3. Complementary audit exports in Excel (`.xlsx`) and CSV formats for internal finance and compliance records.

---

## 2. Size Cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` (reuses `core.employee` and `payroll.payrun`) | 1 |
| Flyway migration | **none** (reads existing `payroll.employee_payrun`, `payroll.employee_payrun_line`, `payroll.employee_statutory_profile`, and `core.employee`) | 1 |
| Externally testable behaviour | Finance officer previews EPF monthly summary for a pay run and exports standard EPFO ECR 2.0 text file (`#~#` delimited), Excel, and CSV files | 1 |
| Frontend area | `code/frontend/src/payroll/payrun` (`PfEcrDrawer.jsx`, `pfEcrService.js`, integrated into `RunPage.jsx` statutory returns menu) | 1 |

---

## 3. Scope

### In Scope
1. **EPFO ECR 2.0 Text File Export (`GET /api/v1/payroll/runs/{id}/statutory/pf-ecr/export?format=TXT`):**
   - Strictly conforms to EPFO's official 11-column format delimited with `#~#` without header rows.
   - Text file download named `ECR_{ESTABLISHMENT_CODE}_{YYYY-MM}.txt`.
2. **Tabular Audit Export (`GET /api/v1/payroll/runs/{id}/statutory/pf-ecr/export?format=XLSX|CSV`):**
   - Human-readable workbook with formatted columns, employee details, and grand totals row for financial reconciliation.
3. **In-App Preview API (`GET /api/v1/payroll/runs/{id}/statutory/pf-ecr/preview`):**
   - Aggregates contribution totals across EPFO accounts:
     - Account 01: Employee PF (12%) + Employer PF (3.67%)
     - Account 02: EPFO Admin Charges (0.50%)
     - Account 10: Employer EPS Pension (8.33%)
     - Account 21: EDLI Insurance (0.50%)
     - Account 22: EDLI Admin Charges (0.00% / statutory waiver)
   - Flags validation warnings (e.g., active PF contributor missing UAN).
4. **Statutory Wage Calculations:**
   - **Gross Wages:** Total gross earnings in the pay period (`EmployeePayRun.getGrossEarnings()`).
   - **EPF Wages:** Earned basic/qualifying wage capped at ₹15,000 (if ceiling restriction is on in `EpfSetting`) or actual earned basic.
   - **EPS Wages:** Restricted to ₹15,000 ceiling. Capped at 0 if employee is $\ge 58$ years old or opted out of EPS.
   - **EDLI Wages:** Restricted to ₹15,000 ceiling.
   - **NCP Days:** Non-Contributing Period days (`EmployeePayRun.getLopDays()`).
5. **Frontend UI Integration (`RunPage.jsx`):**
   - Adds "PF ECR File" option to the Statutory Returns dropdown on `RunPage.jsx`.
   - Opens `PfEcrDrawer.jsx` displaying summary account cards, validation warnings, and employee breakdown with export triggers.

### Out of Scope
1. **Direct API Filing to EPFO Portal:** EPFO does not offer an open public REST API for direct third-party return filing; file generation for manual portal upload is the statutory standard.
2. **Challan Payment Receipt Reconciliation (TRRN):** Reconciliation of TRRN (Temporary Return Reference Number) after government banking payment belongs to future payment tracking (`W-88`).
3. **Arrears ECR / Supplementary ECR:** This spec covers the primary regular monthly ECR return. Supplementary/arrears ECR files will be handled as an extension when arrears pay runs are introduced.

---

## 4. EPFO ECR 2.0 File Specification & Column Mapping

The official EPFO Electronic Challan cum Return (ECR 2.0) file structure consists of 11 fields per line separated by `#~#`. No header or trailer rows are allowed.

### Column Mapping Table

| Col # | EPFO Field Name | Data Type | Required | Source / Calculation in Infinevo |
|---|---|---|---|---|
| **1** | **UAN** | Numeric (12 digits) | **Yes** | `EmployeeStatutoryProfile.uan` (clean digits, exactly 12 chars). |
| **2** | **Member Name** | Text (max 80 chars) | **Yes** | `Employee.firstName` + space + `Employee.lastName` (uppercase, clean special chars). |
| **3** | **Gross Wages** | Numeric (integer) | **Yes** | `EmployeePayRun.grossEarnings` rounded to whole rupee (`RoundingMode.HALF_UP`). |
| **4** | **EPF Wages** | Numeric (integer) | **Yes** | EPF calculation wage base for the employee (capped at ₹15,000 if restricted, or actual earned basic). |
| **5** | **EPS Wages** | Numeric (integer) | **Yes** | EPS calculation wage base (capped at ₹15,000; 0 if age $\ge 58$ or not EPS eligible). |
| **6** | **EDLI Wages** | Numeric (integer) | **Yes** | EDLI wage base (min(earnedBasic, ₹15,000); 0 if exempt). |
| **7** | **EE Share Remitted** | Numeric (integer) | **Yes** | Employee PF contribution (12% of Col 4), rounded to nearest rupee. From line `EPF_EMPLOYEE`. |
| **8** | **EPS Contribution** | Numeric (integer) | **Yes** | Employer Pension contribution (8.33% of Col 5), rounded to nearest rupee. From line `EPS_EMPLOYER`. 0 if age $\ge 58$. |
| **9** | **ER Share Remitted** | Numeric (integer) | **Yes** | Employer PF contribution (Col 7 − Col 8, or Col 4 $\times$ 3.67%). From line `EPF_EMPLOYER`. If age $\ge 58$, full 12% goes here. |
| **10** | **NCP Days** | Numeric (integer) | **Yes** | Non-Contributing Period days. Integer count of LOP / unpaid days from `EmployeePayRun.lopDays`. Max = total days in month. |
| **11** | **Refund of Advances** | Numeric (integer) | **Yes** | Standard `0` (or recovered loan/advance if tagged). |

### Sample ECR Text File Output
```text
100123456789#~#ASHA RAO#~#65000#~#15000#~#15000#~#15000#~#1800#~#1250#~#550#~#0#~#0
100123456790#~#BOB SMITH#~#14000#~#14000#~#14000#~#14000#~#1680#~#1166#~#514#~#2#~#0
100123456791#~#RAMESH PATEL#~#50000#~#15000#~#0#~#15000#~#1800#~#0#~#1800#~#0#~#0
```
*(Line 3 shows an employee aged 59: EPS wages and EPS contribution are 0, and the entire 12% employer share ₹1,800 is remitted to EPF Account 01).*

---

## 5. Backend Architecture

### Package: `com.infinevo.payroll.statutory.ecr`

| Layer | Class / Record | Responsibility |
|---|---|---|
| Controller | `PfEcrController.java` | REST endpoints for ECR preview and file export (`.txt`, `.xlsx`, `.csv`) |
| Service | `PfEcrService.java` | Interface defining `getEcrPreview(payrunId)` and `exportEcrFile(payrunId, format)` |
| ServiceImpl | `PfEcrServiceImpl.java` | Aggregates pay run lines, loads statutory profiles, validates UANs, formats `#~#` lines, and generates Excel/CSV |
| DTO (Preview) | `PfEcrPreviewResponse.java` | Pay run metadata, establishment code, total challan summary (Accounts 1, 2, 10, 21, 22), validation warnings, and employee row list |
| DTO (Row) | `PfEcrRowResponse.java` | Single employee ECR 11-column record with formatting helper |
| DTO (Account Summary) | `PfChallanAccountSummary.java` | Sums for EPFO Accounts 01, 02, 10, 21, and total challan remittance amount |

---

### API Contracts

#### 1. In-App Preview API
```http
GET /api/v1/payroll/runs/{payrunId}/statutory/pf-ecr/preview
```
* **Authorization:** `@RequiresAction("payroll.statutory_report.read")`
* **Module Guard:** `@RequiresModule(PlatformModule.PAYROLL)`
* **Response `200 OK`:**
```json
{
  "status": 200,
  "message": "PF ECR preview generated successfully",
  "data": {
    "payrunId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "period": "2026-10",
    "establishmentCode": "DLCPM1234567000",
    "totalMembers": 42,
    "totalGrossWages": 3520000,
    "totalEpfWages": 630000,
    "totalEpsWages": 615000,
    "totalEdliWages": 630000,
    "totalEeShare": 75600,
    "totalEpsShare": 51230,
    "totalErShare": 24370,
    "totalAdminCharges": 3150,
    "totalEdliCharges": 3150,
    "grandTotalChallan": 157500,
    "warnings": [
      {
        "employeeId": "c4b3a1d2-...",
        "employeeNumber": "EMP-045",
        "employeeName": "Vikram Singh",
        "warningCode": "MISSING_UAN",
        "message": "Active PF contributor has no UAN recorded in statutory profile"
      }
    ],
    "rows": [
      {
        "uan": "100123456789",
        "memberName": "ASHA RAO",
        "grossWages": 65000,
        "epfWages": 15000,
        "epsWages": 15000,
        "edliWages": 15000,
        "eeShare": 1800,
        "epsShare": 1250,
        "erShare": 550,
        "ncpDays": 0,
        "refundOfAdvances": 0
      }
    ]
  }
}
```

#### 2. ECR File Export API
```http
GET /api/v1/payroll/runs/{payrunId}/statutory/pf-ecr/export?format=TXT
```
* **Parameters:** `format=TXT` (default), `format=XLSX`, `format=CSV`.
* **Headers:**
  - Content-Type: `text/plain; charset=utf-8` (for TXT), `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (for XLSX), `text/csv` (for CSV).
  - Content-Disposition: `attachment; filename="ECR_DLCPM1234567000_2026_10.txt"`
* **Response Body (`TXT` format):**
  Raw string lines separated by CRLF (`\r\n`), matching EPFO ECR upload specifications.

---

## 6. Frontend Architecture

### File: `code/frontend/src/payroll/payrun/PfEcrDrawer.jsx`

- Reusable slide-over drawer opened from `RunPage.jsx`.
- **Top Summary Cards:**
  - Total Members & Grand Total Challan Remittance (₹).
  - Breakdown by EPFO Account: Account 01 (EPF EE + ER), Account 10 (EPS Pension), Account 02 (Admin Charges), Account 21 (EDLI).
- **Validation Alert Banner:**
  - If any employee in the run has EPF deductions but missing/invalid UAN, displays a warning with direct links to edit employee statutory profile (`/payroll/employees/:id/salary`).
- **Interactive Data Table:**
  - Columns: UAN, Member Name, Gross Wages, EPF Wages, EPS Wages, EE Share (₹), EPS Share (₹), ER Share (₹), NCP Days.
- **Export Action Buttons:**
  - Primary button: **"Download ECR File (.txt)"** (for EPFO upload).
  - Secondary button: **"Export Excel (.xlsx)"** / **"Export CSV"** (for accounting and audit).

### File: `code/frontend/src/payroll/payrun/pfEcrService.js`
- `getPreview(payrunId)`: calls `GET /api/v1/payroll/runs/{id}/statutory/pf-ecr/preview`.
- `downloadFile(payrunId, format)`: triggers blob download with proper filename.

### Integration in `RunPage.jsx`
- Added to the "Statutory returns" dropdown button (alongside ESI Return and PT Return introduced in `W-95`):
  ```jsx
  {
    key: 'pf-ecr',
    label: 'PF ECR File',
    icon: <SafetyCertificateOutlined />,
    onClick: () => setShowPfEcrDrawer(true),
  }
  ```

---

## 7. Security and Entitlements

- **Tenant Isolation:** Enforces `UUID tenantId = TenantContext.require()`. Cross-tenant queries are structurally impossible.
- **Permissions:**
  - Preview endpoint: requires `payroll.statutory_report.read` (granted to `payroll-officer`, `finance`, and `tenant-admin`).
  - Export endpoint: requires `payroll.statutory_report.export` (granted to `payroll-officer`, `finance`, and `tenant-admin`).
- **Audit Logging:** Export action triggers a statutory export audit log entry via `@Audited` / logger with tenant, payrun ID, actor, and format.

---

## 8. Edge Cases & Validation Rules

1. **Missing or Malformed UAN:**
   - EPFO strictly mandates a 12-digit numeric UAN.
   - If an employee has no UAN, the preview flags `MISSING_UAN`. In the generated `.txt` file, the row is output with placeholder or empty string if configured, or excluded with a clear notification so the file won't be rejected by EPFO portal validations.
2. **Senior Citizen Employee (Age $\ge$ 58):**
   - Under EPS rules, pension contribution stops at 58.
   - Column 5 (EPS Wages) = `0`.
   - Column 8 (EPS Share) = `0`.
   - Column 9 (ER Share) = Full 12% remitted to Account 01.
3. **Loss of Pay (LOP) / NCP Days:**
   - NCP days must be integer $\ge 0$ and $\le$ total days in the month. Fractional days are rounded down to whole days or clamped.
4. **Rounding Consistency:**
   - EPFO requires rupee-level rounding (HALF_UP).
   - EE share (12%) + ER share (3.67%) + EPS share (8.33%) sum must match total statutory deductions recorded in the pay run lines.

---

## 9. Impact on Standing Rules

- **`tenant_id` & RLS:** Creates no new tables. Reads existing tenant-isolated tables (`payroll.payrun`, `payroll.employee_payrun`, `payroll.employee_payrun_line`, `payroll.employee_statutory_profile`, `core.employee`). Tenant isolation is strictly enforced via `TenantContext.require()`.
- **Flyway Migrations:** None required. Operates entirely over computed pay run lines and stored statutory profiles. Never uses `ddl-auto`.
- **Precision:** All monetary amounts use integer or `BigDecimal` with half-up rounding to the nearest rupee as required by EPFO. Never uses floating-point arithmetic.
- **Indexes:** Reuses existing indexed lookups on `tenant_id` and foreign keys (`payrun_id`, `employee_id`).
- **Expand / Contract:** No schema modifications; non-breaking and non-destructive.
- **Module Boundaries:** `payroll` reads `core.employee` only through existing clean service/client interfaces (`EmployeeService.get()`), strictly adhering to modular architecture.

---

## 10. Automated Tests

| Test Class | Scope | What It Proves |
|---|---|---|
| `PfEcrServiceTest.java` (Unit) | Backend | Verifies 11-column calculation logic: gross, wage ceiling caps, age 58 pension zeroing, NCP day conversion, and `#~#` formatting. |
| `PfEcrControllerTest.java` (Unit) | Backend | Verifies `GET /preview` returns 200 with summary and warnings; verifies `GET /export` streams file with correct media type and header. |
| `PfEcrIT.java` (Integration) | Backend | End-to-end test with seeded pay run, employee statutory profiles, and pay lines: verifies file generation and tenant isolation. |
| `PfEcrDrawer.test.jsx` (Component) | Frontend | Verifies drawer renders summary cards, displays UAN warnings, and clicking download triggers blob export. |
| `RunPage.test.jsx` (Integration) | Frontend | Verifies "PF ECR File" option appears in statutory dropdown and opens `PfEcrDrawer`. |

---

## 11. Verification Commands

```bash
# 1. Backend build, spotless and tests
cd code/backend
mvn spotless:check -pl payroll
mvn test -pl payroll "-Dtest=PfEcrServiceTest,PfEcrControllerTest,PfEcrIT"

# 2. Frontend lint and component tests
cd ../frontend
npm run lint
npm test -- src/payroll/payrun/PfEcrDrawer.test.jsx
```

