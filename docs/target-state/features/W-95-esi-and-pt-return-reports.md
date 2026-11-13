# Feature: ESI and PT return reports

| Field | Value |
|---|---|
| **Feature ID** | `W-95` · `PAY-STATUTORY-REPORTS` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Apps touched** | `code/backend/payroll`, `code/frontend` (`src/payroll/payrun`) |
| **Related gaps** | `DEV-TRACKER.md` row `W-95`; legacy `ExcelReportService.java:232-248` (lump-sum totals only, no official statutory returns) |
| **Status** | Proposed — Ready for Founder Approval |
| **Blocked by** | nothing |

---

## 1. What and why

Today, `infinevo-platform` calculates employee and employer statutory contributions during monthly pay runs (`W-31.4`, `StatutoryLineContributor.java`):
* `EMPLOYEE_ESI` (0.75% deduction) and `EMPLOYER_ESI` (3.25% benefit)
* `PROFESSIONAL_TAX` (state slab deduction per work location, `ProfessionalTaxService.java`)

However, when a pay run is completed, the Payroll Officer has no automated way to generate official statutory return files. In the legacy application (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/service/ExcelReportService.java`), the only available export was a generic pay run summary with aggregated numbers, forcing payroll officers to manually copy employee line items into external government spreadsheets for ESIC portal upload and state Professional Tax returns.

This ticket delivers dedicated **monthly ESI Return** and **State-wise Professional Tax (PT) Return** exports directly from any computed, locked, or paid pay run in Excel (`.xlsx`) and CSV (`.csv`) formats.

---

## 2. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | **none** (read-only queries over existing `payroll.payrun`, `payroll.employee_payrun`, `payroll.employee_payrun_line`, and `payroll.employee_statutory_profile`) | 1 |
| Externally testable behaviour | An officer downloads the monthly ESI return and state-wise PT return statement for a pay run in `.xlsx` or `.csv` | 1 |
| Frontend area | `src/payroll/payrun` (`RunPage.jsx`) | 1 |

Within cap. No new database tables or schema migrations required.

---

## 3. Statutory Report Specifications

### A. ESI Monthly Return Report (Official ESIC Return Format)
Standard monthly contribution return format required for ESIC compliance and portal submission:

| Column # | Header Name | Data Source / Mapping | Notes |
|:---:|---|---|---|
| 1 | **IP Number** | `payroll.employee_statutory_profile.insurance_number` | 10-digit ESIC Insurance Number |
| 2 | **IP Name** | `core.employee` (First + Last Name) | Employee name as on ESIC portal |
| 3 | **No. of Days Worked** | `payroll.employee_payrun.worked_days` | Number of days employee worked in pay period |
| 4 | **Total Monthly Wages** | Earned gross wages subject to ESI | Capped if tenant policy dictates; else earned gross |
| 5 | **IP Contribution** | Line item `EMPLOYEE_ESI` (`LineKind.DEDUCTION`) | 0.75% employee deduction |
| 6 | **Employer Contribution** | Line item `EMPLOYER_ESI` (`LineKind.BENEFIT`) | 3.25% employer contribution |
| 7 | **Total Contribution** | `IP Contribution` + `Employer Contribution` | 4.00% total contribution |
| 8 | **Reason Code for Zero Days** | Derived: if `worked_days == 0` | `1` = Leave without wages, `2` = Left service, `0` = N/A |

*Employees omitted from ESI report:* Employees where ESI is not applicable (`esi_eligible == false` or gross wage > ceiling upon joining per `W-76.2`).

---

### B. State-wise Professional Tax (PT) Return Statement
Periodic/monthly return schedule for state treasury remittance, segmented by employee's work location state:

| Column # | Header Name | Data Source / Mapping | Notes |
|:---:|---|---|---|
| 1 | **State / Location** | State from Core `WorkLocationResponse` | e.g. Karnataka, Maharashtra, Telangana |
| 2 | **PTRC Number** | State PT registration certificate number | From `payroll.pt_setting` / company statutory settings |
| 3 | **Employee Code** | `core.employee.employee_number` | Unique company employee ID |
| 4 | **Employee Name** | `core.employee` (First + Last Name) | Employee full name |
| 5 | **Gross Salary** | `payroll.employee_payrun.total_gross_pay` | Monthly gross pay evaluated for PT slab |
| 6 | **Applicable Slab** | Salary slab description | e.g. `₹15,000 – ₹24,999` |
| 7 | **PT Deducted** | Line item `PROFESSIONAL_TAX` (`LineKind.DEDUCTION`) | e.g. ₹200 (or ₹300 in Feb for Maharashtra) |

*State Summary Section / Sheet:*
At the top or summary sheet:
* Total employees liable for PT in state
* Total gross wages in state
* Total PT payable for state treasury remittance

---

## 4. Backend Architecture

### Package: `com.infinevo.payroll.statutory.report`

| Layer | File | Responsibility |
|---|---|---|
| Controller | `StatutoryReportController.java` | Endpoints to stream ESI and PT returns |
| Service | `StatutoryReportService.java` | Interface defining report extraction contracts |
| ServiceImpl | `StatutoryReportServiceImpl.java` | Aggregates pay run lines, profiles, and builds streaming Excel/CSV |
| Model | `EsiReturnRow.java` | Record representing a single ESIC return line item |
| Model | `PtReturnRow.java` | Record representing a single employee PT statement row |
| Model | `PtStateSummary.java` | Record representing state-wise PT remittance summary |

### API Endpoints

```
GET /api/v1/payroll/payruns/{id}/reports/esi?format={XLSX|CSV}
GET /api/v1/payroll/payruns/{id}/reports/pt?format={XLSX|CSV}&state={optionalStateCode}
```

* **Authentication & Authorization:** Require standard Bearer token with permission `payroll.payrun.read`.
* **Tenant Isolation:** Enforced via `TenantContext` — queries run strictly against current tenant session.
* **Pay Run Status Check:** Report generation is enabled for pay runs in `COMPUTED`, `LOCKED`, or `PAID` state. Returns `409 Conflict` if the run is still in raw `DRAFT` (not yet computed).

---

## 5. Frontend Architecture

### File: `code/frontend/src/payroll/payrun/RunPage.jsx`
* In the action toolbar of `RunPage.jsx` (beside the existing `Export` button):
  * Add a **"Statutory Returns"** dropdown button (Ant Design `Dropdown` with `DownOutlined`):
    * 📄 **ESI Monthly Return (.xlsx)**
    * 📄 **Professional Tax Return (.xlsx)**
* **Service:** Add helper methods to `code/frontend/src/payroll/payrun/payrunService.js`:
  * `downloadEsiReturn(payrunId, format)`
  * `downloadPtReturn(payrunId, format, state)`
* Automatically triggers browser file download with descriptive filenames:
  * `ESI_Monthly_Return_<Month>_<Year>.xlsx`
  * `PT_Return_<Month>_<Year>.xlsx`

---

## 6. Database Changes

**None.**  
Queries existing tables:
* `payroll.payrun`
* `payroll.employee_payrun`
* `payroll.employee_payrun_line`
* `payroll.employee_statutory_profile`
* `payroll.pt_setting`

---

## 7. Automated Tests

| Test Class | Scope | What It Proves |
|---|---|---|
| `StatutoryReportServiceTest.java` (Unit) | Backend | ESI wage capping, worked days mapping, zero-day reason codes, and multi-state PT grouping with correct totals. |
| `StatutoryReportIT.java` (Integration) | Backend | HTTP GET endpoints return `200 OK` with valid `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` content type; tenant boundary enforcement; refusal on uncomputed draft runs. |
| `RunPage.test.jsx` (Component) | Frontend | "Statutory Returns" dropdown renders on computed/locked runs, hides on uncomputed drafts, and invokes download handler when clicked. |

---

## 8. Verification Steps

1. **Backend Build & Spotless:**
   ```bash
   cd code/backend
   mvn spotless:check -pl payroll
   mvn test -pl payroll -Dtest=StatutoryReportServiceTest,StatutoryReportIT
   ```
2. **Frontend Test & Lint:**
   ```bash
   cd code/frontend
   npm run lint
   npm test -- RunPage.test.jsx
   ```
3. **Manual Flow Check:**
   * Open a computed or locked monthly pay run in `/payroll/payruns/:id`.
   * Click **Statutory Returns** $\rightarrow$ **ESI Monthly Return (.xlsx)**.
   * Verify that the downloaded file contains correct IP numbers, worked days, and 0.75% / 3.25% contributions matching the line items.
   * Click **Professional Tax Return (.xlsx)** and verify employees are grouped by work location state with matching PT slab totals.

