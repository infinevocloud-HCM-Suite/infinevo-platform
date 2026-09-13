# Backend Changes - Deduction & Reimbursement Module

---

## Part 1: Deduction Module (Core)

---

### 1.1 Database & Entity Architecture

#### A. Master Deduction Entity (`Deduction.java`)
Represents the master catalog of all deduction rules supported by the organization.
* **Fields**:
  * `id` (`Long`): Primary Key.
  * `deductionName` (`String`): Name of the deduction (e.g., *Provident Fund (PF)*, *ESI*, *Professional Tax (PT)*, *TDS / Income Tax*, *Salary Advance*, *Health Insurance*).
  * `deductionCode` (`String`): Unique identifier code (e.g., `PF_01`, `ESI_01`, `TDS_01`).
  * `deductionType` (`Enum`): Categorization (`STATUTORY`, `VOLUNTARY`, `TAX`, `LOAN_RECOVERY`).
  * `calculationType` (`Enum`): `PERCENTAGE` or `FLAT_AMOUNT`.
  * `rateOrAmount` (`BigDecimal`): Percentage rate (e.g., `12.00%`) or fixed amount (e.g., `₹500.00`).
  * `calculatedOn` (`Enum`): Base component for percentage calculation (`BASIC_PAY`, `GROSS_SALARY`, `CTC`).
  * `maxLimit` (`BigDecimal`): Ceiling limit for statutory capping (e.g., PF wage ceiling limit).
  * `isActive` (`Boolean`): Soft-delete flag for enabling/disabling deductions.
  * `createdAt` / `updatedAt` (`LocalDateTime`): Audit timestamps.

#### B. Employee-Deduction Mapping Entity (`EmployeeDeduction.java`)
Manages individual employee deduction assignments and overrides.
* **Fields**:
  * `id` (`Long`): Primary Key.
  * `employeeId` (`Long`): Foreign key referencing `Employee`.
  * `deductionId` (`Long`): Foreign key referencing `Deduction`.
  * `overrideAmount` (`BigDecimal`): Optional custom amount/percentage overriding master rule.
  * `effectiveFrom` (`LocalDate`): Start date of the deduction.
  * `effectiveTo` (`LocalDate`): Optional expiration/end date (useful for temporary loan recovery).
  * `status` (`Enum`): `ACTIVE`, `PAUSED`, `EXPIRED`.

---

### 1.2 Repository Layer

* **`DeductionRepository.java`**:
  * `findByIsActiveTrue()`: Retrieves all active master deductions.
  * `findByDeductionCode(String code)`: Lookup for duplicate prevention during creation.
* **`EmployeeDeductionRepository.java`**:
  * `findActiveByEmployeeId(Long employeeId, LocalDate payrollDate)`: Fetches all valid deductions applicable for an employee for the current payroll processing month.

---

### 1.3 Service Layer & Business Logic (`DeductionServiceImpl.java`)

#### A. Dynamic Calculation Engine
* **Percentage-Based Calculation**: Deduction Amount = Base Amount (Basic / Gross) x (Rate / 100). Applies capping if `maxLimit` is specified.
* **Flat-Amount Calculation**: Directly assigns the fixed configured rupee amount.

#### B. Statutory & Legal Limit Validations
* Ensures total combined deductions do not exceed statutory limits (e.g., maximum 50% of gross salary).
* Validates effective date ranges to automatically activate or expire recurring deductions.

#### C. Payroll Calculation Integration (`PayrollService.java`)
During monthly salary computation:
1. Calculates `Gross Salary = Basic + Allowances`.
2. Evaluates all active deductions from `EmployeeDeduction` mapping.
3. Computes `Total Deductions = Sum(Statutory + Voluntary + Loan Repayments)`.
4. Computes `Net Payable Salary = Gross Salary - Total Deductions`.
5. Logs line-item deductions into `SalarySlipDeduction` for payslip generation.

---

### 1.4 REST API Endpoints (`DeductionController.java`)

| HTTP Method | Endpoint | Description |
|-------------|----------|-------------|
| `POST` | `/api/deductions` | Create a new master deduction rule |
| `GET` | `/api/deductions` | Fetch all master deductions (with filter by active status) |
| `GET` | `/api/deductions/{id}` | Fetch a single deduction rule by ID |
| `PUT` | `/api/deductions/{id}` | Update deduction properties (rate, calculation type, limit) |
| `DELETE` | `/api/deductions/{id}` | Soft-delete / deactivate a deduction rule |
| `POST` | `/api/deductions/assign` | Assign a deduction to an employee (with optional override) |
| `GET` | `/api/deductions/employee/{empId}` | Get all active deductions assigned to a specific employee |
| `DELETE` | `/api/deductions/employee/{mappingId}` | Remove / terminate a deduction assigned to an employee |

---

### 1.5 Summary of Key Benefits
* **Configurable & Scalable**: New tax components or voluntary deductions can be added dynamically without altering database schemas.
* **Accurate Payslip Generation**: Complete audit trail of deduction line items stored per payroll cycle.
* **Safe Overrides**: Supports organization-wide defaults with employee-level customization.

---
---

## Part 2: Reimbursement Module (Core)

---

### 2.1 Domain Layer

| File | Purpose |
|------|---------|
| `entity/employeereimbursement/EmployeeReimbursementRequest.java` | Single table (`employee_reimbursement_request`) holding a request and its approval outcome — requested and approved amounts, bill date, attachment references, status, billing month and approver audit. |
| `enumeration/employeereimbursement/ReimbursementStatus.java` | Request lifecycle: PENDING, APPROVED, REJECTED. |
| `enumeration/employeereimbursement/ReimbursementPaymentStatus.java` | Tracks whether an approved amount has been paid, separate from approval status. |
| `enumeration/employeereimbursement/ReimbursementType.java` | Claim categories: MEDICAL, TRAVEL, FOOD, INTERNET, FUEL, OTHER. |

---

### 2.2 Contract Layer (DTOs)

| File | Purpose |
|------|---------|
| `dto/employeereimbursement/EmployeeReimbursementRequestDTO.java` | Inbound submission payload. Omits employee, organization, status and approval fields. |
| `dto/employeereimbursement/EmployeeReimbursementResponseDTO.java` | Employee-facing view. Excludes approver identity and internal identifiers. |
| `dto/employeereimbursement/AdminReimbursementResponseDTO.java` | Admin-facing view with employee name, number, and approval audit. |
| `dto/employeereimbursement/ApproveReimbursementRequestDTO.java` | Approval payload with approved amount, optional remarks and billing month. Enables partial approval. |
| `dto/employeereimbursement/RejectReimbursementRequestDTO.java` | Rejection payload with mandatory rejection reason. |

---

### 2.3 Persistence & Mapping

| File | Purpose |
|------|---------|
| `repository/employeereimbursement/EmployeeReimbursementRequestRepository.java` | Finders scoped by organization + employee for self-service reads, ensuring tenant isolation. |
| `mapper/employeereimbursement/EmployeeReimbursementMapper.java` | Converts between entity and DTOs. Applies creation defaults (PENDING, UNPAID, no approved amount). Withholds billing month until approved. |

---

### 2.4 Business & API Layer

| File | Purpose |
|------|---------|
| `service/employeereimbursement/EmployeeReimbursementService.java` | Service contract covering both employee and admin operations. |
| `serviceimpl/employeereimbursement/EmployeeReimbursementServiceImpl.java` | Business rules — field validation, attachment checks, upload with cleanup on failure, approve/partial-approve/reject logic (approved amount cannot exceed requested). |
| `controller/employeereimbursement/EmployeeReimbursementController.java` | Employee endpoints: submit, list own history, view single request. Identity from token. |
| `controller/employeereimbursement/AdminReimbursementController.java` | Admin endpoints: list all org requests, view one, approve, reject. |
| `exception/ResourceNotFoundException.java` | Custom exception for proper not-found responses. |

---

### 2.5 Modified Files (Existing)

| File | Change |
|------|--------|
| `config/OrganizationRoleInterceptor.java` | Added employee reimbursement path to employee-accessible allowlist. |
| `config/WebConfig.java` | Extended interception to cover `/admin/**` for admin controller. |
| `exception/GlobalExceptionHandler.java` | Added handling for not-found (404) and invalid argument/state (400). |
| `service/CloudinaryService.java` | Declares dedicated upload for reimbursement bills. |
| `serviceimpl/CloudinaryServiceImpl.java` | Implements upload under org/employee folder path. |

---

### 2.6 REST API Endpoints

| HTTP Method | Endpoint | Description |
|-------------|----------|-------------|
| `POST` | `/api/employee-reimbursements` | Employee submits a reimbursement request |
| `GET` | `/api/employee-reimbursements` | Employee lists own requests |
| `GET` | `/api/employee-reimbursements/{id}` | Employee views single request |
| `GET` | `/api/admin/reimbursements` | Admin lists all org requests |
| `GET` | `/api/admin/reimbursements/{id}` | Admin views single request |
| `PUT` | `/api/admin/reimbursements/{id}/approve` | Admin approves (full or partial) |
| `PUT` | `/api/admin/reimbursements/{id}/reject` | Admin rejects with reason |

---

### 2.7 Key Design Notes

- **Scope note:** This is separate from `salarycomponents.Reimbursement` (CTC salary component) and `claimsanddeclarations.ReimbursementClaim` (org-level claim settings).
- **Approval and Payment are separate statuses** — approval doesn't mean paid.
- **Billing month** assigned on approval, determines which payrun picks it up.
- **Approved amount can be less than requested** (partial approval supported).

---
---

## Part 3: PayRun Integration (Deduction & Reimbursement Claims)

---

### 3.1 Enum Changes

| File | Change |
|------|--------|
| `enumeration/DeductionStatus.java` | Added `INPAYRUN` between ACTIVE and PROCESSED |
| `enumeration/employeereimbursement/ReimbursementPaymentStatus.java` | Added `INPAYRUN` between PAID and UNPAID |

---

### 3.2 Repository Changes

| File | Method Added | Purpose |
|------|-------------|---------|
| `repository/employee/SalaryDeductionRepository.java` | `findByEmployeeIdAndOrganizationIdAndDeductionMonthAndStatus()` | Fetch ACTIVE deductions per employee for payrun calculation |
| `repository/employee/SalaryDeductionRepository.java` | `findByOrganizationIdAndDeductionMonthAndStatus()` | Bulk fetch deductions for approve/reject/delete status transitions |
| `repository/employeereimbursement/EmployeeReimbursementRequestRepository.java` | `findByEmployeeIdAndOrganizationIdAndReimbursementMonthAndStatusAndPaymentStatus()` | Fetch APPROVED+UNPAID reimbursements per employee for payrun calculation |
| `repository/employeereimbursement/EmployeeReimbursementRequestRepository.java` | `findByOrganizationIdAndReimbursementMonthAndPaymentStatus()` | Bulk fetch reimbursements for approve/reject/delete status transitions |

---

### 3.3 DTO Changes

| File | Fields Added |
|------|-------------|
| `dto/payruns/EmployeePayRunDTO.java` | `claimDeduction` (Double), `claimReimbursement` (Double), `claimDeductionStatus` (String), `claimReimbursementStatus` (String) |
| `dto/payruns/PayRunDTO.java` | `totalClaimDeduction` (BigDecimal), `totalClaimReimbursement` (BigDecimal) |
| `dto/payruns/PayslipResponseDTO.java` | `claimDeduction` (Double), `claimReimbursement` (Double) |

---

### 3.4 Entity Changes

| File | Fields Added |
|------|-------------|
| `entity/payruns/EmployeePayRun.java` | `claim_deduction` (Double), `claim_reimbursement` (Double), `claim_deduction_status` (String), `claim_reimbursement_status` (String) |
| `entity/payruns/PayRun.java` | `totalClaimDeduction` (BigDecimal), `totalClaimReimbursement` (BigDecimal) |

---

### 3.5 Mapper Changes

| File | Change |
|------|--------|
| `mapper/payruns/EmployeePayRunMapper.java` | Added mapping for `claimDeduction`, `claimReimbursement`, `claimDeductionStatus`, `claimReimbursementStatus` in `toDto()` |
| `mapper/payruns/PayRunMapper.java` | Added mapping for `totalClaimDeduction`, `totalClaimReimbursement` in `toDto()` |

---

### 3.6 Service Logic Changes

#### `EmployeePayRunServiceImpl.java`

| Method | Change |
|--------|--------|
| Constructor | Injected `SalaryDeductionRepository` and `EmployeeReimbursementRequestRepository` |
| `mapToPayRunDTO()` | After TDS calculation: fetches ACTIVE deductions (by deductionMonth as LocalDate first-of-month) and APPROVED+UNPAID reimbursements (by reimbursementMonth converted from "August 2026" to "2026-08"). Adds claim deduction to totalDeductions. Adds claim reimbursement directly to netPay. Sets claimDeduction, claimReimbursement, and their statuses on DTO. |
| `generateEmployeePayRuns()` | Persists claimDeduction, claimReimbursement, claimDeductionStatus, claimReimbursementStatus on EmployeePayRun entity. After save, bulk updates ACTIVE deductions to INPAYRUN and UNPAID reimbursements to INPAYRUN for the org+month. |
| `getEmployeePayslip()` | Sets claimDeduction and claimReimbursement on PayslipResponseDTO from EmployeePayRun entity. |

#### `PayRunServiceImpl.java`

| Method | Change |
|--------|--------|
| Constructor | Injected `SalaryDeductionRepository` and `EmployeeReimbursementRequestRepository` |
| `calculateAndUpdatePayRunTotals()` | Aggregates `totalClaimDeduction` and `totalClaimReimbursement` across all employees. Sets on PayRun entity. |
| `approvePayRun()` | After approval: updates INPAYRUN deductions to PROCESSED, INPAYRUN reimbursements to PAID. Updates employee_payruns claim statuses to PROCESSED/PAID. |
| `rejectPayRun()` | After rejection: reverts INPAYRUN deductions to ACTIVE, INPAYRUN reimbursements to UNPAID (clears payrunId). Updates employee_payruns claim statuses to ACTIVE/UNPAID. |
| `deletePayRun()` | Before deletion: same revert as reject. Then deletes payrun (cascade deletes employee_payruns). |

---

### 3.7 Entity Fix

| File | Change |
|------|--------|
| `entity/SalaryDeduction.java` | Added `@ForeignKey(name = "none", value = ConstraintMode.NO_CONSTRAINT)` on employee join to prevent incompatible FK error on startup |

---

### 3.8 Net Pay Calculation Formula

```
netPay = totalEarnings + totalBenefits + totalReimbursements + claimReimbursement - totalDeductions
```

Where `totalDeductions` = EPF + Professional Tax + TDS + claimDeduction

---

### 3.9 Month Format Matching

| Module | DB Format | ProcessingPeriod Format | Conversion |
|--------|-----------|------------------------|------------|
| Deduction | `2026-09-01` (LocalDate, first of month) | "September 2026" | `YearMonth.parse(processingPeriod).atDay(1)` |
| Reimbursement | `"2026-09"` (String) | "September 2026" | `ym.getYear() + "-" + String.format("%02d", ym.getMonthValue())` |

---

### 3.10 Status Transitions

#### Deduction (`employee_deduction.status`)

| Event | From | To |
|-------|------|-----|
| PayRun Created | ACTIVE | INPAYRUN |
| PayRun Approved | INPAYRUN | PROCESSED |
| PayRun Rejected | INPAYRUN | ACTIVE |
| PayRun Deleted | INPAYRUN | ACTIVE |

#### Reimbursement (`employee_reimbursement_request.payment_status`)

| Event | From | To |
|-------|------|-----|
| PayRun Created | UNPAID | INPAYRUN |
| PayRun Approved | INPAYRUN | PAID |
| PayRun Rejected | INPAYRUN | UNPAID |
| PayRun Deleted | INPAYRUN | UNPAID |

---

### 3.11 Database Changes Required (Manual)

```sql
ALTER TABLE employee_deduction MODIFY COLUMN status ENUM('ACTIVE','INPAYRUN','PROCESSED') NOT NULL;
ALTER TABLE employee_reimbursement_request MODIFY COLUMN payment_status ENUM('PAID','UNPAID','INPAYRUN') NOT NULL;
```

New columns auto-created by Hibernate (`ddl-auto=update`):
- `employee_payruns.claim_deduction`
- `employee_payruns.claim_reimbursement`
- `employee_payruns.claim_deduction_status`
- `employee_payruns.claim_reimbursement_status`
- `payruns.totalClaimDeduction`
- `payruns.totalClaimReimbursement`
