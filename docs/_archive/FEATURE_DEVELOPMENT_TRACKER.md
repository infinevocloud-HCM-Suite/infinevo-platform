# Feature Development Tracker — Reimbursements & Deductions (Payroll Only)

> **Document Version:** 1.0  
> **Last Updated:** 2026-08-12  
> **Branch Name (Backend):** `taxation`  
> **Branch Name (Frontend):** `employee`  
> **Status:** Code Complete (Pre-existing on Side Branches)

---

## 📋 Feature Release Board

| Feature ID | Title | Module | Scope | Status | Target Phase |
|---|---|---|---|---|---|
| **FEAT-001** | Reimbursement Claims Workflow | Module 1 | Employee submission, history, Admin approval/partial approval dashboard | 🟢 Code Complete | Phase 2 |
| **FEAT-002** | Employee Ad-Hoc Deduction Ledger | Module 2 | Admin ledger CRUD + history list | 🟢 Code Complete | Phase 2 |
| **FEAT-003** | Reimbursement Payroll Sync | Integration | Sync approved claims as non-taxable earnings in pay run | 🟢 Code Complete | Phase 2 |
| **FEAT-004** | Ad-Hoc Deduction Payroll Netting | Integration | Deduct active monthly ledger entries from net salary | 🟢 Code Complete | Phase 2 |

---

## 🛠️ Feature Specifications

### FEAT-001: Reimbursement Claims Workflow

```
[Employee Form] ──► Upload Receipt (S3) ──► Submit Claim (PENDING)
                                                │
                                                ▼
                                    [Admin Review Dashboard]
                             ├── Approve (requestedAmount = approvedAmount)
                             ├── Partially Approve (approvedAmount < requestedAmount)
                             └── Reject
```

#### Database Schema (`reimbursement_claim`)
```sql
CREATE TABLE reimbursement_claim (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id VARCHAR(50) NOT NULL,
    org_id BIGINT NOT NULL,
    reimbursement_type VARCHAR(100) NOT NULL,
    requested_amount DECIMAL(19, 4) NOT NULL,
    approved_amount DECIMAL(19, 4) DEFAULT 0.0000,
    bill_date DATE NOT NULL,
    description TEXT,
    receipt_url VARCHAR(512),
    status VARCHAR(50) DEFAULT 'PENDING',
    admin_remarks TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reimbursement_org FOREIGN KEY (org_id) REFERENCES organization(id),
    CONSTRAINT fk_reimbursement_emp FOREIGN KEY (employee_id) REFERENCES basic_details(employee_id)
);
```

#### API Specifications
* `POST /api/v1/reimbursement-claims`
  * Request Body: `{ reimbursementType, requestedAmount (BigDecimal), billDate, description, receiptUrl }`
* `GET /api/v1/reimbursement-claims/history`
  * Response: List of user's own claims scoped by tenant `orgId`
* `GET /api/v1/admin/reimbursement-claims?status=PENDING`
  * Response: Filterable list of claims inside the admin's organization
* `PUT /api/v1/admin/reimbursement-claims/{claimId}/review`
  * Request Body: `{ approvedAmount (BigDecimal), status, adminRemarks }`

---

### FEAT-002: Employee Ad-Hoc Deduction Ledger

#### Database Schema (`employee_ad_hoc_deduction`)
```sql
CREATE TABLE employee_ad_hoc_deduction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id VARCHAR(50) NOT NULL,
    org_id BIGINT NOT NULL,
    deduction_amount DECIMAL(19, 4) NOT NULL,
    deduction_month VARCHAR(7) NOT NULL, -- Format: YYYY-MM
    reason VARCHAR(100) NOT NULL, -- Asset Damage, Advance Recovery, etc.
    remarks TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_deduction_org FOREIGN KEY (org_id) REFERENCES organization(id),
    CONSTRAINT fk_deduction_emp FOREIGN KEY (employee_id) REFERENCES basic_details(employee_id)
);
```

#### API Specifications
* `POST /api/v1/admin/ad-hoc-deductions`
  * Request Body: `{ employeeId, deductionAmount (BigDecimal), deductionMonth, reason, remarks }`
* `GET /api/v1/admin/ad-hoc-deductions?month=2025-07`
  * Response: Filterable ledger list for the tenant
* `PUT /api/v1/admin/ad-hoc-deductions/{id}`
  * Request Body: `{ deductionAmount (BigDecimal), reason, remarks }`
* `DELETE /api/v1/admin/ad-hoc-deductions/{id}`

---

### FEAT-003: Reimbursement Payroll Sync

#### Execution Logic
During pay run finalization inside `EmployeePayRunServiceImpl.saveEmployeePayRun()`:
1. Identify target month and year of processing (e.g., "July 2025").
2. Query database for approved claims:
   ```sql
   SELECT SUM(approved_amount) FROM reimbursement_claim 
   WHERE employee_id = :employeeId 
     AND org_id = :orgId 
     AND status IN ('APPROVED', 'PARTIALLY_APPROVED')
     AND bill_date BETWEEN :startOfMonth AND :endOfMonth;
   ```
3. Set the total approved amount into `EmployeePayRun.totalReimbursements` (added as a non-taxable item).
4. Recalculate final payout.

---

### FEAT-004: Ad-Hoc Deduction Payroll Netting

#### Execution Logic
During pay run net pay processing inside `EmployeePayRunServiceImpl.saveEmployeePayRun()`:
1. Query database for active deductions for the pay month:
   ```sql
   SELECT SUM(deduction_amount) FROM employee_ad_hoc_deduction 
   WHERE employee_id = :employeeId 
     AND org_id = :orgId 
     AND deduction_month = :currentProcessingMonth; -- "2025-07"
   ```
2. Map total deduction value into `EmployeePayRun.adHocDeductions` column.
3. Update calculations:
   ```java
   BigDecimal totalDeductions = statutoryDeductions.add(adHocDeductions);
   BigDecimal netPay = grossSalary.subtract(totalDeductions).add(reimbursements);
   ```

---

## 🧪 Verification & Testing Plan

### 1. Backend Service Layer Tests (JUnit 5 + Mockito)
- **`ReimbursementClaimServiceTest`**
  - Verify exception raised if `requestedAmount` is null, zero, or negative.
  - Verify status transition: `requestedAmount = 5000`, `approvedAmount = 3000` ➡️ sets status to `PARTIALLY_APPROVED`.
  - Verify tenant isolation filter: User from `orgId=1` cannot retrieve claims from `orgId=2`.
- **`AdHocDeductionServiceTest`**
  - Verify deduction creation fails if `deductionMonth` format is invalid (must match `YYYY-MM`).
  - Verify edit and delete permissions validate the `orgId` boundary.

### 2. Frontend Integration Tests (Axios Mock + UI Test Cases)
- **Employee Reimbursement Flow:**
  - Enter invalid file format for receipt ➡️ assert UI warning appears.
  - Fill correct form, click submit ➡️ entry must appear immediately in the History table with status `PENDING`.
- **Admin Review Flow:**
  - Open review modal, select "Approve" ➡️ input field for approved amount is set to the requested value.
  - Select "Partially Approve" ➡️ input field becomes editable. Set a smaller value ➡️ verify correct payload is transmitted.

---

## 📝 Task Checklist

### FEAT-001 & FEAT-002: Base Workflows
- [x] Create database entities with `orgId` boundaries (`EmployeeReimbursementClaim`, `EmployeeAdHocDeduction`)
- [x] Setup repositories, service interfaces, and implementation classes
- [x] Build REST controllers with `BigDecimal` arithmetic
- [x] Create employee submission form, history table, and admin review views in React
- [x] Configure routes and Axios service API calls

### FEAT-003 & FEAT-004: Payroll Integration
- [x] Modify `EmployeePayRunServiceImpl.java` to fetch monthly ad-hoc deductions and approved claims
- [x] Update net salary pay run calculations
- [x] Verify compilation and prepare for UAT verification
