# Feature: Bank Salary Payment File

| Field | Value |
|---|---|
| **Feature ID** | `W-88` · `PAY-BANK-FILE` |
| **Owner** | mohit (`dev-mohit`) |
| **Apps touched** | `code/backend/payroll`, `code/frontend` (`src/payroll/payrun`) |
| **Related gaps** | `DEV-TRACKER.md` row `W-88`; missing bank payment file export from finalized pay runs |
| **Status** | Proposed — Ready for Founder Approval |
| **Blocked by** | nothing |

---

## 1. What and why

Once a monthly or off-cycle pay run is approved and marked paid (`W-29.1`, `W-30.2`, `W-36.2`), the employer must disburse salaries to employees by uploading a bulk salary disbursement file to the company's corporate net banking portal.

Previously, payroll officers had to manually compile bank account numbers, IFSC codes, and net pay amounts or use generic pay run Excel exports, which risked data entry errors, leaked confidential salary lines to unauthorized staff, and lacked proper payment mode and bank account validation.

This feature introduces a dedicated bank salary payment file generation and download service for **`APPROVED`** and **`PAID`** pay runs, guarded by the dedicated permission action `payroll.bank_file.export`.

---

## 2. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | **none** (reads existing `payroll.payrun`, `payroll.employee_payrun`, `core.employee`, and `core.employee_bank`) | 1 |
| Externally testable behaviour | An officer with `payroll.bank_file.export` downloads the bank salary CSV for an approved/paid pay run; omitted employees (missing bank, non-bank mode, invalid IFSC/account, zero net pay) are reported | 1 |
| Frontend area | `src/payroll/payrun` (`RunPage.jsx`) | 1 |

Within cap. No new database tables or Flyway schema migrations required.

---

## 3. Bank File Specifications

### 3.1 Data Source & Column Layout (Standard Generic Corporate Format)

The default implementation generates an RFC 4180 compliant CSV with UTF-8 BOM (`\uFEFF`) and CSV formula injection protection:

| Column # | Header Name | Data Source / Mapping | Validation / Formatting |
|:---:|---|---|---|
| 1 | **Sr No** | Sequential counter (`1, 2, ...`) | Non-empty |
| 2 | **Employee Number** | `core.employee.employee_number` | Preserves leading zeroes, sorted ascending |
| 3 | **Employee Name** | `core.employee` (first + middle + last name) | Trimmed |
| 4 | **Account Holder Name** | `core.employee_bank.account_holder_name` | Name on beneficiary account |
| 5 | **Bank Account Number** | `core.employee_bank.bank_account_number` | Text string, preserves leading zeroes |
| 6 | **IFSC Code** | `core.employee_bank.ifsc_code` | 11-character Indian Financial System Code |
| 7 | **Bank Name** | `core.employee_bank.bank_name` | Beneficiary bank name |
| 8 | **Account Type** | `core.employee_bank.bank_account_type` | `SAVINGS`, `CURRENT`, or `SALARY` |
| 9 | **Net Pay (INR)** | `payroll.employee_payrun.net_pay` | Formatted to 2 decimal places (`HALF_UP`) |
| 10 | **Narration** | `"Salary " + payRun.getPeriod()` | e.g. `"Salary 2026-11"` |

### 3.2 Omission & Filtering Rules

Employees included in the pay run must satisfy all of the following criteria to be written into the bank file:
1. **Bank details exist:** The employee has a record in `core.employee_bank`.
2. **Payment mode is electronic:** `core.employee_bank.payment_mode == PaymentMode.BANK_TRANSFER` (employees set to `CASH` or `CHEQUE` are excluded).
3. **Valid account number:** `bank_account_number` is not null and not blank.
4. **Valid IFSC code:** `ifsc_code` is not null and not blank.
5. **Positive net pay:** `employee_payrun.net_pay > 0` (zero net pay or negative net pay are excluded).

---

## 4. Omission Reporting Mechanism (Points 5 & 6 Proposal)

To ensure the Payroll Officer and Finance team are fully aware when the bank file total does not equal the pay run's total net pay:

### 4.1 Backend HTTP Response Headers
When `GET /api/v1/payroll/payruns/{id}/bank-file` executes, the response streams the CSV file and exposes the following custom response headers:
* `X-BankFile-Total-Employees`: Total included employees in the pay run.
* `X-BankFile-Exported-Employees`: Count of employee rows written into the file.
* `X-BankFile-Omitted-Employees`: Count of employees excluded from the file.
* `X-BankFile-File-Total`: Sum of net pay included in the CSV file.
* `X-BankFile-Run-Net-Pay`: Total net pay computed for the entire pay run.
* `X-BankFile-Omitted-Reasons`: JSON or delimited summary of omissions (e.g. `{"missingBank": 2, "nonBankMode": 1, "zeroNetPay": 1, "invalidDetails": 0}`).
* `Access-Control-Expose-Headers` includes all `X-BankFile-*` headers for frontend client accessibility.

### 4.2 Backend Logging
The service logs a structured warning (`WARN`) detailing the pay run ID, omitted count, total variance amount, and list of omitted employee numbers with reasons.

### 4.3 Frontend UI Notification
When the officer clicks "Download Bank File":
* If `omittedCount == 0`: A success message is shown: *"Bank salary payment file downloaded successfully."*
* If `omittedCount > 0`: The file downloads, and an Ant Design warning notification (`notification.warning`) or modal alert is surfaced:
  > **Bank File Downloaded with Omissions**  
  > **X employee(s)** were omitted from the payment file (e.g. missing bank details, non-bank payment mode, or zero net pay).  
  > **File Total:** ₹A vs **Pay Run Total:** ₹B (Difference: ₹C).  
  > Please disburse omitted employees via alternate modes (cash/cheque) or update bank profiles.

---

## 5. Architecture Changes

### 5.1 Backend Changes (`code/backend/payroll`)
1. **Single Constructor Injection:**
   `PayRunController` retains a single constructor injecting dependencies:
   ```java
   public PayRunController(PayRunService payRunService, BankFileService bankFileService) {
       this.payRunService = Objects.requireNonNull(payRunService);
       this.bankFileService = Objects.requireNonNull(bankFileService);
   }
   ```
2. **Dedicated Authorization Action:**
   Guarded with `@RequiresAction("payroll.bank_file.export")` (already seeded in Keycloak and DB for `payroll-officer` and `finance` roles).
3. **Batch Query for Bank Records:**
   `EmployeeBankRepository.findByTenantIdAndEmployeeIdIn(tenantId, employeeIds)` loads all bank records in **one SQL query** for the entire pay run rather than 1-by-1 in a loop.
4. **Typed Status Exception:**
   Remove `IllegalStateException` from `PayRunController`'s 409 exception handler. Throw `BankFileInvalidStatusException` when attempted on non-`APPROVED`/`PAID` runs, mapped to `409 Conflict`.
5. **Full Application Context Integration Test:**
   Add `BankFileExportIT` in `code/backend/payroll` or `code/backend/app` that boots the Spring application context with `@SpringBootTest` and verifies the endpoint through `MockMvc`, proving constructor injection and bean wiring succeed in runtime.

### 5.2 Frontend Changes (`code/frontend`)
1. **Permission Check:**
   `const canExportBankFile = useCan('payroll.bank_file.export');`
   The "Download Bank File" button is only rendered if `canExportBankFile && (status === 'APPROVED' || status === 'PAID')`.
2. **Service & Notification:**
   `payrunService.exportBankFile(id)` returns both blob data and response headers so `RunPage.jsx` can trigger the warning alert when omissions exist.

---

## 6. Open Questions for Founder Decision

> [!IMPORTANT]
> **Bank File Layout Format Decision:**  
> Indian banks each provide proprietary CMS (Cash Management System) bulk upload formats:
> 1. **Option 1 (Current Default):** Standard CSV format (Sr No, Employee Number, Employee Name, Account Holder Name, Bank Account Number, IFSC Code, Bank Name, Account Type, Net Pay, Narration). Accepted by most bank corporate portals (HDFC Corporate NetBanking bulk upload, ICICI generic, etc.).
> 2. **Option 2 (HDFC Enet CMS):** Specific fixed-width or delimiter layout required for HDFC Enet batch payments.
> 3. **Option 3 (ICICI CIB Bulk Pay-link):** ICICI Bank Corporate Internet Banking format.
> 4. **Option 4 (Multi-Bank Template):** Tenant settings option allowing each company to select their primary disbursement bank template.
> 
> *Founder decision required on whether Option 1 suffices for general launch or if a specific bank template (Option 2/3/4) must be generated.*

---

## 7. Automated Tests

| Test Class | Scope | What It Proves |
|---|---|---|
| `BankFileServiceTest` (Unit) | Backend | Validates sorting by employee number, 2-decimal formatting, CSV escaping, exclusion of non-bank mode, exclusion of blank IFSC/account, exclusion of zero net pay, and rejection of draft/computing runs. |
| `BankFileExportIT` (Integration) | Backend | Boots Spring container; tests `GET /api/v1/payroll/payruns/{id}/bank-file` through the real application context; validates constructor injection, `@RequiresAction("payroll.bank_file.export")`, and HTTP 200 response. |
| `PayRunGuardIT` (Unit) | Backend | Verifies `PayRunController` constructor wiring and action guards. |
| `OffCyclePayRunGuardIT` (Unit) | Backend | Verifies `PayRunController` constructor wiring. |
| `RunPage.test.jsx` (Component) | Frontend | Verifies "Download Bank File" button appears only when user holds `payroll.bank_file.export` and run is `APPROVED` or `PAID`. |
| `payrunService.test.js` (Unit) | Frontend | Verifies `exportBankFile` client calls endpoint with `responseType: 'blob'`. |

---

## 8. Verification Steps

1. **Rebase and Build:**
   ```bash
   git rebase origin/main
   cd code/backend && ./mvnw test -pl payroll -am -Dtest=BankFileServiceTest,BankFileExportIT,PayRunGuardIT,OffCyclePayRunGuardIT
   cd ../frontend && npm test -- RunPage.test.jsx payrunService.test.js
   ```
2. **Download Verification:**
   * Approve a pay run with test employees.
   * Verify download yields valid CSV with UTF-8 BOM.
   * Verify employees with cash/cheque or zero net pay are excluded and reported in notification.

