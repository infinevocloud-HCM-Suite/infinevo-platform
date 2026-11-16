# Feature: ESI and PT return reports

| Field | Value |
|---|---|
| **Feature ID** | `W-95` · `PAY-STATUTORY-REPORTS` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Apps touched** | `code/backend/payroll`, `code/frontend` (`src/payroll/payrun`) |
| **Related gaps** | `DEV-TRACKER.md` row `W-95`; legacy `legacy/Payroll-Bend-SBoot/.../ExcelReportService.java:225-248` (lump-sum totals only, no periodic statutory returns) |
| **Status** | Implemented — Ready for Review |
| **Blocked by** | nothing |

---

## 1. What and why

Today, `infinevo-platform` calculates employee and employer statutory contributions during monthly pay runs (`W-31.4`, [`StatutoryLineContributor.java:40-50`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun/StatutoryLineContributor.java#L40-L50)):
* `ESI_EMPLOYEE` (0.75% deduction) and `ESI_EMPLOYER` (3.25% benefit) per [`StatutoryComponentCode.java:12-13`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/lines/StatutoryComponentCode.java#L12-L13)
* `PROFESSIONAL_TAX` (state slab deduction per employee work location, [`ProfessionalTaxService.java:48`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/pt/ProfessionalTaxService.java#L48))

However, when a pay run is computed, the Payroll Officer has no automated way to generate periodic statutory returns. In the legacy application ([`legacy/Payroll-Bend-SBoot/.../ExcelReportService.java:225-248`](file:///d:/Workspace/infinevo-platform/legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/service/ExcelReportService.java#L225-L248)), the only export was a generic pay run workbook with lump-sum `totalESI` and `totalTaxes` ("Income tax + Professional tax"), forcing payroll officers to manually copy employee line items into external spreadsheets for ESIC filing and state Professional Tax returns.

This ticket delivers monthly **ESI Return** and state-wise **Professional Tax (PT) Return** exports directly from any completed pay run (`COMPUTED`, `APPROVED`, or `PAID`) in both Excel (`.xlsx`) and CSV (`.csv`) formats.

---

## 2. Size cap & Split Recommendation

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | **none** (read-only queries over existing `payroll.payrun`, `payroll.employee_payrun`, `payroll.employee_payrun_line`, `payroll.employee_statutory_profile`, and `payroll.org_pt_override`) | 1 |
| Externally testable behaviour | An officer downloads the monthly ESI return and state-wise PT return statement for a pay run in `.xlsx` or `.csv` | 1 |
| Frontend area | `src/payroll/payrun` (`RunPage.jsx`) | 1 |

> [!NOTE]
> **Split Recommendation:**  
> This spec covers both ESI and PT returns because both share the same pay run export trigger and require zero database migrations. However, if the founder prefers strict single-behavior tickets, it cleanly splits into:
> * **`W-95.1` (ESI return report):** ESIC monthly return CSV/XLSX export.
> * **`W-95.2` (PT return report):** State-wise PT return statement CSV/XLSX export with multi-state PTRC grouping.

---

## 3. Scope

### In Scope
1. **ESI Monthly Return Report:** Exporting a monthly contribution return file in `.xlsx` and `.csv` for all employees covered under ESI in a pay run (`COMPUTED`, `APPROVED`, or `PAID`).
2. **State-wise PT Return Report:** Exporting a state-wise Professional Tax schedule in `.xlsx` and `.csv`, grouped by employee work location state, displaying the state's PT Registration Certificate (PTRC) number, employee gross earnings, and PT deducted.
3. **Download Actions in Web UI:** Adding "Statutory Returns" dropdown options to `RunPage.jsx` for both `.xlsx` and `.csv` formats.
4. **Security & Tenant Isolation:** Guarded by seeded action `payroll.statutory_report.read` and isolated per tenant via `TenantContext`.

### Out of Scope
1. **Direct Portal API Upload:** Automatic HTTP push to the ESIC portal or state commercial tax portals (this generates files for manual portal upload/audit).
2. **Historical Work Location Snapshots:** Retrofitting historical work location tracking onto `payroll.employee_payrun`. PT grouping reads the employee's current work location state (see Section 6).
3. **Database Schema Alterations:** No new Flyway migration scripts or schema changes.
4. **Uncomputed Runs:** Runs in `DRAFT` or `LOCKED` (pre-computation) have no computed figures and will return `409 Conflict`.

---

## 4. Statutory Report Data Mapping

### A. ESI Monthly Return Report (Draft Layout — Pending CA Review)

> ⚠️ **CA Question (`OQ-W95-01`):** The exact column sequence and zero-working-day reason codes must be confirmed with the Chartered Accountant / ESIC filing specialist before calling this an official upload template.

Filter: Employees in the pay run where an `ESI_EMPLOYEE` line exists in `payroll.employee_payrun_line` (or where `is_eligible_for_esi = true` with zero paid days).

| Column # | Header Name | Data Source / Mapping | Exact Field Citation |
|:---:|---|---|---|
| 1 | **IP Number** | `payroll.employee_statutory_profile.esi_number` | [`EmployeeStatutoryProfile.java:70-71`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/salary/EmployeeStatutoryProfile.java#L70-L71) (`V050`) |
| 2 | **IP Name** | Employee full name (`firstName` + `lastName`) | `core.employee` via `EmployeeService.get()` |
| 3 | **No. of Days Worked** | `payroll.employee_payrun.paid_days` | [`EmployeePayRun.java:86-87`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/EmployeePayRun.java#L86-L87) (`V058`) |
| 4 | **Total Monthly Wages** | `payroll.employee_payrun.gross_earnings` | [`EmployeePayRun.java:65-66`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/EmployeePayRun.java#L65-L66) (`V057`) — see rule below |
| 5 | **IP Contribution** | Line item `ESI_EMPLOYEE` (`LineKind.DEDUCTION`) | [`StatutoryComponentCode.java:12`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/lines/StatutoryComponentCode.java#L12) |
| 6 | **Employer Contribution** | Line item `ESI_EMPLOYER` (`LineKind.BENEFIT`) | [`StatutoryComponentCode.java:13`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/lines/StatutoryComponentCode.java#L13) |
| 7 | **Total Contribution** | `IP Contribution` + `Employer Contribution` | Sum of EE (0.75%) + ER (3.25%) |
| 8 | **Reason Code for Zero Days** | Derived if `paid_days == 0` | Provisional: `1` = Leave without wages, `2` = Left service (subject to CA confirmation) |

#### Exact Rule for "Total Monthly Wages":
* `StatutoryLineContributor.java:241-249` tests structure monthly gross against the ESI wage ceiling (₹21,000) at compute time and scales it by `paid_days / payable_days`.
* Because `payroll.employee_payrun_line` records only the computed deduction amounts (`ESI_EMPLOYEE`, `ESI_EMPLOYER`) and not the intermediate base wage, **Total Monthly Wages is sourced directly from `payroll.employee_payrun.gross_earnings`** (`EmployeePayRun.getGrossEarnings()`) for employees participating in ESI.
* If `paid_days == 0`, Total Monthly Wages is `0.00`.

---

### B. State-wise Professional Tax (PT) Return Statement

Filter: Employees in the pay run where a `PROFESSIONAL_TAX` line item exists in `payroll.employee_payrun_line`.

| Column # | Header Name | Data Source / Mapping | Exact Field Citation |
|:---:|---|---|---|
| 1 | **State** | State from work location | `WorkLocationResponse.state()` via `WorkLocationService.get()` |
| 2 | **PTRC Number** | State PT Registration Number | [`OrgPtOverride.java:33-34`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/pt/OrgPtOverride.java#L33-L34) (`registration_number` on `payroll.org_pt_override`) |
| 3 | **Employee Code** | `core.employee.employee_number` | `EmployeeResponse.employeeNumber()` |
| 4 | **Employee Name** | Employee full name | `core.employee` |
| 5 | **Gross Salary** | `payroll.employee_payrun.gross_earnings` | [`EmployeePayRun.java:65-66`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/EmployeePayRun.java#L65-L66) |
| 6 | **PT Deducted** | Line item `PROFESSIONAL_TAX` (`LineKind.DEDUCTION`) | [`StatutoryLineContributor.java:278`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun/StatutoryLineContributor.java#L278) |

#### State Summary Section / Summary Sheet:
For each state represented in the pay run:
* **State Name & Code** (e.g. `Karnataka (KA)`, `Maharashtra (MH)`)
* **State PTRC Number** (from `payroll.org_pt_override.registration_number` for that state; falls back to `"NOT_REGISTERED"` if unset)
* **Total Employees Liable** (count of employees with PT > 0)
* **Total PT Deducted** (sum of `PROFESSIONAL_TAX` deductions for the state)

---

## 5. Backend Architecture

### Package: `com.infinevo.payroll.statutory.report`

| Layer | File | Responsibility |
|---|---|---|
| Controller | `StatutoryReportController.java` | Endpoints to stream ESI and PT returns in XLSX / CSV |
| Service | `StatutoryReportService.java` | Contract for generating report byte arrays or streams |
| ServiceImpl | `StatutoryReportServiceImpl.java` | Queries `EmployeePayRunRepository` and `EmployeePayRunLineRepository`, resolves PTRC numbers, writes via `SXSSFWorkbook` / `CsvWriter` |
| Record | `EsiReturnRow.java` | DTO representing one employee's ESI return row |
| Record | `PtReturnRow.java` | DTO representing one employee's PT statement row |
| Record | `PtStateSummary.java` | DTO representing state summary totals and PTRC registration number |

### API Contract

```
GET /api/v1/payroll/payruns/{id}/reports/esi?format={XLSX|CSV}
GET /api/v1/payroll/payruns/{id}/reports/pt?format={XLSX|CSV}&state={optionalStateCode}
```

* **Authorization:** `@RequiresAction("payroll.statutory_report.read")` (matches seeded permission used by Form 16, [`Form16Controller.java:43`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/form16/Form16Controller.java#L43)).
* **Tenant Isolation:** Enforced via `TenantContext` — all repository lookups include `tenantId`.
* **Pay Run Status Check:**
  * Allowed statuses: `COMPUTED`, `APPROVED`, `PAID` ([`PayRunStatus.java:19-22`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/PayRunStatus.java#L19-L22)).
  * Refused statuses: `DRAFT`, `LOCKED` (pre-computation; no figures exist) $\rightarrow$ returns `409 Conflict` with `PayFigureNotComputedException`.

### Efficient Single-Query Data Retrieval
To prevent $N+1$ queries, `EmployeePayRunLineRepository` gains one finder method:
```java
List<EmployeePayRunLine> findByTenantIdAndPayrunId(UUID tenantId, UUID payrunId);
```
All employee pay run rows are retrieved via existing:
```java
List<EmployeePayRun> findAllByTenantIdAndPayrunIdAndInclusionStatus(
        UUID tenantId, UUID payrunId, InclusionStatus.INCLUDED);
```
Line items for ESI (`ESI_EMPLOYEE`, `ESI_EMPLOYER`) and PT (`PROFESSIONAL_TAX`) are grouped by `employeePayrunId` in memory.

---

## 6. Known Limitations & Technical Decisions

### Work Location State Resolution:
* **Current state:** `payroll.employee_payrun` does not snapshot `work_location_id` or `state_code` at compute time (it stores only `employee_id`).
* **Decision:** Slicing PT by state resolves the employee's state from their current work location (`EmployeeResponse.workLocationId()` $\rightarrow$ `WorkLocationResponse.state()`), matching how `StatutoryLineContributor.java:265` resolves state during computation.
* **Consequence:** If an employee transfers across state offices mid-year, generating a historical return for an earlier month will group under their current work location state. Adding a historical work location timeline to `employee_payrun` is deferred to a future data-model ticket to preserve the 0-migration cap.

---

## 7. Frontend Architecture

### File: `code/frontend/src/payroll/payrun/RunPage.jsx`
* In the action toolbar of `RunPage.jsx` (beside the existing `Export` button):
  * Render a **"Statutory Returns"** dropdown button (Ant Design `Dropdown` with `DownOutlined`), active only when `status` is `COMPUTED`, `APPROVED`, or `PAID`:
    * 📄 **ESI Monthly Return (.xlsx)**
    * 📄 **ESI Monthly Return (.csv)**
    * 📄 **Professional Tax Return (.xlsx)**
    * 📄 **Professional Tax Return (.csv)**
* **Service:** In `code/frontend/src/payroll/payrun/payrunService.js`:
  ```javascript
  downloadEsiReturn: (payrunId, format = 'XLSX') => ...
  downloadPtReturn: (payrunId, format = 'XLSX', state) => ...
  ```
* Both `.xlsx` and `.csv` formats match the backend API parameter.

---

## 8. Database Changes

**None.** Purely read-only queries over existing tables:
* `payroll.payrun`
* `payroll.employee_payrun`
* `payroll.employee_payrun_line`
* `payroll.employee_statutory_profile`
* `payroll.org_pt_override`
* `reference.pt_state`

---

## 9. Risks and Rollback

### Risks
1. **Memory spikes on large exports:** Addressed by using streaming workbook `SXSSFWorkbook` (100-row memory window) rather than `XSSFWorkbook`, following precedent [`XlsxStreamingWriter.java:20-26`](file:///d:/Workspace/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/report/XlsxStreamingWriter.java#L20-L26).
2. **ESIC portal upload rejected due to unverified layout:** Mitigated by flagging the layout as draft pending CA confirmation (`OQ-W95-01`).
3. **Missing PTRC number:** If tenant has not configured their state PT registration number in `payroll.org_pt_override`, the column renders `"NOT_CONFIGURED"` rather than failing the export.

### Rollback
Since there are no database migrations, rollback is purely reverting code commits. No database down-scripts or data repairs required.

---

## 10. Automated Tests

| Test Class | Scope | What It Proves |
|---|---|---|
| `StatutoryReportServiceTest.java` (Unit) | Backend | Verifies ESI return extracts `esi_number`, `paid_days`, `gross_earnings`, `ESI_EMPLOYEE`, and `ESI_EMPLOYER`. Verifies PT return groups by state and includes PTRC numbers. |
| `StatutoryReportIT.java` (Integration) | Backend | HTTP GET returns `200 OK` with valid content types (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` and `text/csv`); enforces `payroll.statutory_report.read` action; refuses `DRAFT` and `LOCKED` runs with `409 Conflict`. |
| `RunPage.test.jsx` (Component) | Frontend | "Statutory Returns" dropdown renders all four download options on computed/locked runs, hides on drafts, and triggers file download. |

---

## 11. Verification Commands

```bash
# 1. Backend build, spotless and tests
cd code/backend
mvn spotless:check -pl payroll
mvn test -pl payroll -Dtest=StatutoryReportServiceTest,StatutoryReportIT

# 2. Frontend lint and component tests
cd ../frontend
npm run lint
npm test -- RunPage.test.jsx
```
