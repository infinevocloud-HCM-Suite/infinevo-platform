# Feature: PF ECR File (Electronic Challan cum Return)

| Field | Value |
|---|---|
| **Feature ID** | `W-87` · `PAY-STATUTORY-PF-ECR` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration`, `code/frontend` (`src/payroll/payrun`) |
| **Related gaps** | `DEV-TRACKER.md` row `W-87`; legacy generated internal CSVs but lacked standard EPFO ECR v2.0 export |
| **Status** | Draft - In Review |
| **Blocked by** | Waits on CA review |

---

## 1. What and why

Every Indian employer registered under the *Employees' Provident Funds and Miscellaneous Provisions Act, 1952* is statutorily mandated to file monthly returns and remit provident fund contributions by the 15th of the subsequent month via the **EPFO Unified Employer Portal** (`unifiedportal-emp.epfindia.gov.in`).

The EPFO portal does not accept arbitrary spreadsheets or PDFs; it strictly mandates an **Electronic Challan cum Return (ECR 2.0)** text file formatted with the exact delimiter `#~#` (hash-tilde-hash), containing 11 specific columns without headers, keyed by each employee's Universal Account Number (UAN).

Today in `infinevo-platform`:
- The payroll engine calculates EPF lines (`EPF_EMPLOYEE`, `EPF_EMPLOYER`, `EPS_EMPLOYER`, `EDLI`, `EPF_ADMIN`) during pay run computation (`code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun/StatutoryLineContributor.java`).
- `W-95` delivered ESI and state-wise Professional Tax (PT) return reports (`code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/report/StatutoryReportController.java`).
- However, generating the official monthly EPFO ECR file requires actual calculated qualifying wage figures (EPF wages, EPS wages, EDLI wages) which previously were computed on-the-fly inside `StatutoryLineContributor` without being persisted in `payroll.employee_payrun`.
- **Founder Decision (2026-11-24):** Pay run computation must persist EPF, EPS, and EDLI wage figures directly into `payroll.employee_payrun` so that statutory exports read exact persisted figures rather than reverse-engineering or re-computing them.

This feature delivers **PF ECR File Generation**:
1. Persistence of `epf_wage`, `eps_wage`, and `edli_wage` in `payroll.employee_payrun` computed by `StatutoryLineContributor`.
2. Direct generation of the official EPFO ECR v2.0 `.txt` upload file from any `COMPUTED` or `PAID` regular pay run with single-click download.
3. In-app audit preview and validation (flagging employees with missing UANs or wage cap anomalies before filing).
4. Complementary audit exports in Excel (`.xlsx`) and CSV formats for internal finance and compliance records.

---

## 2. Size Cap & Database Changes

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` (reuses `core.employee` and `payroll.payrun`) | 1 |
| Flyway migrations | 2 migrations (next free versions at build time):<br>1. `payroll/V<N>__payrun_statutory_wages.sql`<br>2. `core/V<M>__statutory_ecr_permissions.sql` | 2 |
| Externally testable behaviour | Finance officer previews EPF monthly summary for a pay run and exports standard EPFO ECR 2.0 text file (`#~#` delimited), Excel, and CSV files | 1 |
| Frontend area | `code/frontend/src/payroll/payrun` (`PfEcrDrawer.jsx`, `pfEcrService.js`, integrated into `RunPage.jsx` statutory returns menu) | 1 |

### Database Schema Updates

#### Migration 1: `payroll/V<N>__payrun_statutory_wages.sql` (Next free version at build time)
Adds wage columns to persist computation bases during pay run execution:
```sql
ALTER TABLE payroll.employee_payrun
    ADD COLUMN IF NOT EXISTS epf_wage NUMERIC(14, 4) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS eps_wage NUMERIC(14, 4) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS edli_wage NUMERIC(14, 4) DEFAULT NULL;

COMMENT ON COLUMN payroll.employee_payrun.epf_wage IS 'Statutory EPF calculation wage base for the employee';
COMMENT ON COLUMN payroll.employee_payrun.eps_wage IS 'Statutory EPS calculation wage base capped at statutory ceiling';
COMMENT ON COLUMN payroll.employee_payrun.edli_wage IS 'Statutory EDLI calculation wage base capped at statutory ceiling';
```

#### Migration 2: `core/V<M>__statutory_ecr_permissions.sql` (Next free version at build time)
Follows the standard platform role grant pattern established in `code/backend/migration/src/main/resources/db/migration/core/V184__wfh_seed_roles.sql`:
1. Seeds `reference.action`:
   - `payroll.statutory_report.export` — "Export statutory compliance reports and ECR files"
2. Creates grant function `core.grant_statutory_report_actions(p_tenant_id UUID)` granting export to `platform-admin`, `tenant-admin`, `payroll-officer`, and `finance`.
3. Creates trigger `tenant_seed_system_roles_statutory_report` on `core.tenant`.
4. Executes idempotent backfill across all existing tenants.

---

## 3. Scope

### In Scope
1. **EPFO ECR 2.0 Text File Export (`GET /api/v1/payroll/payruns/{id}/reports/pf-ecr/export?format=TXT`):**
   - Strictly conforms to EPFO's official 11-column format delimited with `#~#` without header rows.
   - Text file download named `ECR_{ESTABLISHMENT_CODE}_{YYYY-MM}.txt`.
2. **Tabular Audit Export (`GET /api/v1/payroll/payruns/{id}/reports/pf-ecr/export?format=XLSX|CSV`):**
   - Human-readable workbook with formatted numeric columns, employee details, and grand totals row for financial reconciliation.
3. **In-App Preview API (`GET /api/v1/payroll/payruns/{id}/reports/pf-ecr/preview`):**
   - Aggregates contribution totals across EPFO accounts:
     - Account 01: Employee PF (12%) + Employer PF (3.67%)
     - Account 02: EPFO Admin Charges (0.50%)
     - Account 10: Employer EPS Pension (8.33%)
     - Account 21: EDLI Insurance (0.50%)
     - Account 22: EDLI Admin Charges (0.00% / statutory waiver)
   - Flags validation warnings (e.g., active PF contributor missing UAN).
4. **Persisted Wage Storage in Computation (`StatutoryLineContributor.java`):**
   - Persists `epf_wage`, `eps_wage`, and `edli_wage` on `EmployeePayRun`.
5. **Off-Cycle Run Guard:**
   - ECR export is disabled/blocked for off-cycle pay runs (`HTTP 400 Bad Request`), matching the statutory rule in `W-95`.
6. **Frontend UI Integration (`RunPage.jsx`):**
   - Adds "PF ECR File" option to the Statutory Returns dropdown on `RunPage.jsx` (enabled only for regular, computed/paid runs).
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
| **1** | **UAN** | Numeric (12 digits) | **Yes** | `EmployeeStatutoryProfile.uan` (clean digits, exactly 12 chars). Mandatory for portal upload. |
| **2** | **Member Name** | Text (max 80 chars) | **Yes** | `Employee.firstName` + space + `Employee.lastName` (uppercase, stripped of `#`, `~`, commas, and non-ASCII chars). |
| **3** | **Gross Wages** | Numeric (integer) | **Yes** | Actual earned gross in the pay period net of LOP deductions, rounded to nearest whole rupee (`RoundingMode.HALF_UP`). Read from `EmployeePayRun.grossEarnings`. |
| **4** | **EPF Wages** | Numeric (integer) | **Yes** | Persisted `EmployeePayRun.epfWage` (capped at ₹15,000 if restricted ceiling is enabled in `EpfSetting`, prorated ceiling if `prorateRestrictedWage` is enabled, or actual earned basic if unrestricted). Rounded to nearest rupee. |
| **5** | **EPS Wages** | Numeric (integer) | **Yes** | Persisted `EmployeePayRun.epsWage`, exactly as the pay run charged EPS on it. Capped at ₹15,000 (or the prorated ceiling) for an ordinary member. **A member who contributes EPS on higher wages is not capped by the pay run today** (`StatutoryLineContributor`, `epsBase = earnedBasic`), so the file shows the uncapped figure for them. How the ECR should show such members is a CA question (`OQ-W87-01`). Set to `0` if age $\ge 58$ or not EPS eligible. |
| **6** | **EDLI Wages** | Numeric (integer) | **Yes** | Persisted `EmployeePayRun.edliWage` (strictly capped at ₹15,000, or prorated ceiling; 0 if exempt). |
| **7** | **EE Share Remitted** | Numeric (integer) | **Yes** | Employee PF contribution (12% of Col 4), rounded to nearest rupee. From line `EPF_EMPLOYEE`. |
| **8** | **EPS Contribution** | Numeric (integer) | **Yes** | Employer Pension contribution (8.33% of Col 5), rounded to nearest rupee. From line `EPS_EMPLOYER`, as paid. ₹1,250 at most for an ordinary member; higher for a member on higher-wage EPS (`OQ-W87-01`). `0` if age $\ge 58$ or employee not eligible for EPS. |
| **9** | **ER Share Remitted** | Numeric (integer) | **Yes** | Employer PF contribution. Standardized universal EPFO formula: `Col 7 (EE Share Remitted) - Col 8 (EPS Contribution)`. From line `EPF_EMPLOYER`. If age $\ge 58$, Col 8 is 0, so Col 9 equals full 12% (Col 7). |
| **10** | **NCP Days** | Numeric (integer) | **Yes** | Non-Contributing Period days. Integer count of LOP / unpaid days within the employment period from `EmployeePayRun.lopDays`. Days before date of joining or after exit are not NCP days. Max = calendar days in month. |
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
| Controller | `PfEcrController.java` | REST endpoints for ECR preview and file export (`.txt`, `.xlsx`, `.csv`) under `/api/v1/payroll/payruns/{payrunId}/reports/pf-ecr` |
| Service | `PfEcrService.java` | Interface defining `getEcrPreview(payrunId)` and `exportEcrFile(payrunId, format)` |
| ServiceImpl | `PfEcrServiceImpl.java` | Loads pay run and persisted wage figures, resolves profiles including soft-deleted employees, validates UANs, formats `#~#` lines, and generates Excel/CSV |
| DTO (Preview) | `PfEcrPreviewResponse.java` | Pay run metadata, establishment code, total challan summary (Accounts 1, 2, 10, 21, 22), validation warnings, and employee row list |
| DTO (Row) | `PfEcrRowResponse.java` | Single employee ECR 11-column record with formatting helper |
| DTO (Account Summary) | `PfChallanAccountSummary.java` | Sums for EPFO Accounts 01, 02, 10, 21, and total challan remittance amount |

---

### API Contracts

#### 1. In-App Preview API
```http
GET /api/v1/payroll/payruns/{payrunId}/reports/pf-ecr/preview
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
GET /api/v1/payroll/payruns/{payrunId}/reports/pf-ecr/export?format=TXT
```
* **Authorization:** `@RequiresAction("payroll.statutory_report.export")`, the action Migration 2 seeds. No fallback: the migration ships in the same change.
* **Format:** `TXT` is this endpoint's own enum. Core's `ExportFormat` (CSV and XLSX, tied to a database check) is not widened.
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
- `getPreview(payrunId)`: calls `GET /api/v1/payroll/payruns/{id}/reports/pf-ecr/preview`.
- `downloadFile(payrunId, format)`: triggers blob download with proper filename.

### Integration in `RunPage.jsx`
- Added to the "Statutory returns" dropdown button (disabled for off-cycle runs):
  ```jsx
  {
    key: 'pf-ecr',
    label: 'PF ECR File',
    icon: <SafetyCertificateOutlined />,
    onClick: () => setShowPfEcrDrawer(true),
    disabled: !isRegularRun,
  }
  ```

---

## 7. Security and Entitlements

- **Tenant Isolation:** Enforces `UUID tenantId = TenantContext.require()`. Cross-tenant queries are structurally blocked.
- **Permissions:**
  - Preview endpoint: requires `payroll.statutory_report.read`.
  - Export endpoint: requires `payroll.statutory_report.export`.
- **Audit Logging:** Export action triggers a statutory export audit log entry via logger with tenant, payrun ID, actor, and format.

---

## 8. Acceptance Criteria

| # | Given | When | Then |
|---|---|---|---|
| **AC-1** | A regular pay run with EPF contributors is in `COMPUTED` or `PAID` state | User requests ECR preview via `GET /preview` | Returns 200 OK with total challan remittance, account splits (01, 02, 10, 21, 22), and 11-column employee rows |
| **AC-2** | A regular pay run is in `COMPUTED` state | User exports TXT format via `GET /export?format=TXT` | Returns text file delimited with `#~#`, CRLF line endings, no header/footer, named `ECR_{ESTABLISHMENT_CODE}_{YYYY_MM}.txt` |
| **AC-3** | An employee in the pay run has age $\ge 58$ | ECR file is generated | Col 5 (EPS Wages) is `0`, Col 8 (EPS Share) is `0`, and Col 9 (ER Share) equals full 12% (Col 7) |
| **AC-4** | An employee has basic wage of ₹65,000 and ceiling cap is enabled | ECR file is generated | Col 4 (EPF Wages) is 15,000, Col 5 (EPS Wages) is 15,000, Col 8 is 1,250, and Col 9 is 550 (`1800 - 1250`) |
| **AC-5** | An employee has gross 15,000, 10 LOP days, and ceiling cap enabled | ECR file is generated | Col 3 (Gross) shows earned gross net of LOP, Col 10 (NCP days) shows 10 |
| **AC-6** | An off-cycle pay run ID is provided | User requests preview or export | Returns `HTTP 400 Bad Request` with message indicating statutory ECR returns apply only to regular pay runs |
| **AC-7** | An employee contributing to PF was soft-deleted after pay run computation | User exports ECR file | Export succeeds using `EmployeeService.findByIdInIncludingDeleted()` without failing or rolling back |
| **AC-8** | A user without `payroll.statutory_report.read` calls preview or export | Request is executed | Server returns `HTTP 403 Forbidden` |

---

## 9. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| **EPFO portal rejects file due to invalid UAN or invalid characters in name** | Medium | Strip all `#`, `~`, commas, and non-ASCII characters from member name; validate UAN is 12 digits; surface preview warnings before download. |
| **Discrepancy between stored pay lines and ECR column 9 employer share** | Low | Universal formula `Col 7 (EE Share) - Col 8 (EPS Contribution)` is strictly applied across both pay lines and ECR export. |
| **Off-cycle run export causes erroneous government filing** | Medium | Server explicitly validates pay run type is `REGULAR` and returns `HTTP 400` on off-cycle runs; UI disables menu option. |
| **Pay run wages lost on historical re-computation** | Low | `epf_wage`, `eps_wage`, and `edli_wage` columns are persisted during run execution on `payroll.employee_payrun`. |

---

## 10. Automated Tests

| Test Class | Scope | What It Proves |
|---|---|---|
| `PfEcrServiceTest.java` (Unit) | Backend | Verifies 11-column calculation logic: gross, wage ceiling caps, age 58 pension zeroing, NCP day conversion, Col 9 formula (`Col 7 - Col 8`), and `#~#` formatting. |
| `PfEcrControllerTest.java` (Unit) | Backend | Verifies `GET /preview` returns 200 with summary and warnings; verifies `GET /export` streams file with correct media type and header; verifies 400 on off-cycle run. |
| `PfEcrIT.java` (Integration) | Backend | End-to-end test with seeded pay run, employee statutory profiles, persisted wages, and pay lines: verifies file generation, tenant isolation, and 403 authorization. |
| `PfEcrDrawer.test.jsx` (Component) | Frontend | Verifies drawer renders summary cards, displays UAN warnings, and clicking download triggers blob export. |
| `RunPage.test.jsx` (Integration) | Frontend | Verifies "PF ECR File" option appears in statutory dropdown, is disabled on off-cycle runs, and opens `PfEcrDrawer`. |

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
