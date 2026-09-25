# W-19 Pay Input Ledger — Evidence Pass

| Item | Status | Scope |
|---|---|---|
| HRMS→Payroll integration traced | ✓ | Endpoint exists, payload identified, consumer located |
| Pay-affecting inputs catalogued | ✓ | LOP, reimbursements, deductions, one-time payouts, overtime (HRMS only) |
| Period locking today | ✓ | `isClaimsAndDeclarationsLockedForAllEmployees` only; no general input freeze |
| Pay run input assembly | ✓ | `EmployeePayRunServiceImpl` reads LOP via HRMS call + local reimbursement/deduction tables |

---

## 1. HRMS→Payroll Integration End-to-End

**Caller:** `EmployeePayRunServiceImpl.java:1091` (legacy/Payroll-Bend-SBoot)  
Collects employee emails and pass period from the pay run object, then invokes the fetch.

**Endpoint:** `POST /public/get-employee-leaves` (legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/controller/IntegrateWithPayroll.java:64)  
Accepts `EmployeeLeaveRequestDTO` with employee emails and pay period string ("July 2025" format). Security: requires MD5-hashed `X-API-KEY` header.

**Payload:** `EmployeeLeaveRequestDTO` contains list of employee emails and a pay period string.

**Consumer:** `IntegrateWithHrmsServiceImpl.fetchLeaves()` (legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/IntegrateWithHrmsServiceImpl.java:33)  
Uses Spring WebClient (configured in `WebClientConfig.java`) to POST the request and receive a `LeaveResponseDTO[]` array. Maps email → totalLeaves into a `Map<String, Double>`.

**Query in HRMS:** Queries `EmployeeMonthlyLop` by employee ID, year, and month (lines 167–177 in IntegrateWithPayroll.java), sums `lopDays`.

---

## 2. Pay-Affecting Inputs Across Both Products

### LOP (Loss-of-Pay) Days — HRMS
- **Storage:** `entity/EmployeeMonthlyLop.java` in HRMS. Populated based on approved leave requests vs. leave balance.
- **How it reaches pay:** HRMS `IntegrateWithPayroll.java:167–179` exposes it via `/public/get-employee-leaves`. Payroll fetches it at `EmployeePayRunServiceImpl.java:1091` and applies as deduction: `(monthlySalary / paidDays) * lopDays`.
- **Citation:** Feature map item 11 (HRMS_Backend/controller/IntegrateWithPayroll.java:162), item 17 (Payroll: EmployeePayRunServiceImpl.java:633 — now found at line 1091).

### Reimbursements (Approved)
- **Storage:** `entity/employeereimbursement/EmployeeReimbursementRequest.java` in Payroll DB, table `employee_reimbursement_request`.
- **How it reaches pay:** `EmployeePayRunServiceImpl.java:354` queries approved/partially approved reimbursements and adds `approvedAmount` to net pay.
- **Status filter:** `ReimbursementStatus.APPROVED` or `PARTIALLY_APPROVED`.

### Ad-Hoc Salary Deductions
- **Storage:** `entity/SalaryDeduction.java` in Payroll DB, table `employee_deduction`.
- **How it reaches pay:** `EmployeePayRunServiceImpl.java:337` queries deductions by employee and pay run period, sums `deductionAmount` as a line item.
- **Status control:** `DeductionStatus.INPAYRUN` is set after consumption, blocking further edits (feature map item 27, line 363).

### One-Time Payouts
- **Storage:** `entity/payRun/oneTimePayout/OneTimePayout.java` exists in Payroll codebase (feature map item 17).
- **How it reaches pay:** No evidence found of direct integration into standard pay run calculation. Appears to be a separate off-cycle feature.

### Overtime Requests (HRMS only)
- **Storage:** `entity/OvertimeRequest.java` in HRMS (feature map item 6).
- **How it reaches pay:** Not integrated into Payroll. No query in `EmployeePayRunServiceImpl` for overtime data.

---

## 3. Period Locking Today

**No general period lock exists** for preventing value changes after pay run submission/approval.

**Claim-and-Declaration Locking:** `PayRun.java:140` has `isClaimsAndDeclarationsLockedForAllEmployees` Boolean flag. This is a narrative lock for the UI only; no enforcement in the service.

**Deduction Status Lock:** `SalaryDeduction` moves to status `INPAYRUN` once a pay run consumes it, which prevents further deletes/edits (per feature map item 27, line 363: "Status moves to `INPAYRUN` once a pay run consumes the row, blocking further edits and deletes").

**No Retroactive Prevention:** A pay run computed for July can be modified and values changed retroactively. A new pay run for July can be created. No date-based or pay-run-based lock prevents the April input from being re-edited in June.

---

## 4. How the Current Pay Run Reads Its Inputs

**Method:** `EmployeePayRunServiceImpl` (60KB, feature map item 17) is the primary net-pay calculation engine.

**Steps:**
1. **LOP Days:** Line 1091 calls `integrateWithHrmsService.fetchLeaves(employeeEmails, payPeriod)` → receives `Map<String, Double>` keyed by work email.
2. **Reimbursements:** Line 354 queries `employeeReimbursementRequestRepository.findByEmployeeIdAndOrganizationIdAndMonthAndYear(...)` filtered by status `APPROVED` / `PARTIALLY_APPROVED`.
3. **Ad-Hoc Deductions:** Line 337 queries `salaryDeductionRepository.findByEmployeeIdAndOrganizationIdAndMonthAndYear(...)`.
4. **Assembly:** Each `EmployeePayRun` DTO is built from `BasicDetails`, salary structure, and these external inputs, then persisted to `employee_payruns` table.

**No single ledger entity:** Inputs come from three sources (HRMS API, reimbursement table, deduction table) and are summed on-the-fly during pay run creation. No unified input table exists today.
