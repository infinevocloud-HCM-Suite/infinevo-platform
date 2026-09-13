# Frontend Changes - Deduction & Reimbursement Module

---

## Part 1: Deduction Module (Core UI)

---

### 1.1 Component Architecture & Pages

#### A. Add / Edit Deduction (`addDeduction.js`)
* **Purpose**: Dedicated form component to define and configure new master deduction rules or update existing ones.
* **Form Inputs & Fields**:
  * **Deduction Name**: Text input for the title (e.g., *PF, ESI, Professional Tax, Health Insurance*).
  * **Deduction Code**: Unique shorthand identifier (e.g., `PF_01`, `ESI_01`).
  * **Deduction Type**: Dropdown selecting category (`Statutory`, `Voluntary`, `Tax / TDS`, `Loan Recovery`).
  * **Calculation Mode**: Toggle / Radio buttons between:
    * `Percentage (%)`: Renders percentage input and dynamic base selector (`Basic Pay`, `Gross Salary`).
    * `Fixed Amount (₹)`: Renders currency input field for flat deduction.
  * **Statutory Limit / Capping**: Optional max ceiling limit (e.g., wage ceiling limit).
  * **Status**: Toggle switch for `Active` / `Inactive`.
* **Validation & User Experience**:
  * Real-time validation preventing negative values and invalid percentage bounds (0% – 100%).
  * Dynamic field toggling based on selected calculation mode.

#### B. Deductions Management Table / List
* **Purpose**: Overview screen displaying all configured deductions with quick actions.
* **Key Features**:
  * Search, sort, and pagination.
  * Category and calculation type badges/chips for visual clarity.
  * Action triggers: **Edit**, **Deactivate/Delete**, and **Assign to Employees**.

#### C. Employee Deduction Assignment
* **Purpose**: Modal/Interface to assign deduction rules to individual employees with optional custom overrides and start/end dates.

---

### 1.2 Navigation & Routing Architecture

#### A. Route Registration (`router.js`)
Configured protected routes under the payroll management module:
* `/deduction/add` — Add new deduction rule (`addDeduction.js`).
* `/deduction/list` — Master deduction list.
* `/deduction/edit/:id` — Edit existing deduction configuration.

#### B. Navigation Menu (`sidebar.js`)
* Added **Deductions** under the Payroll / Compensation sidebar menu with direct navigation links.
* Controlled menu visibility using role-based permissions (Admin / HR / Payroll Manager).

---

### 1.3 API Integration & State Management

* **Axios Service Layer**:
  * `GET /api/deductions`: Fetches all master deductions to populate the table view and dropdowns.
  * `POST /api/deductions`: Sends validated payload to create a new deduction rule.
  * `PUT /api/deductions/{id}`: Updates existing deduction rules.
  * `DELETE /api/deductions/{id}`: Deactivates or removes a deduction.
  * `POST /api/deductions/assign`: Assigns a deduction to specific employees.
* **Asynchronous Feedback & Error Handling**:
  * Progress spinners during API submission.
  * Toast alerts on successful creation, updates, and error responses.

---

### 1.4 Summary of UI / UX Benefits
* **Intuitive Workflow**: Dynamic form adapts automatically based on whether the deduction is percentage-based or flat amount.
* **Clear Categorization**: Visual chips and badges differentiate statutory deductions from voluntary ones.
* **Responsive Layout**: Designed for seamless usage across desktop and mobile browsers.

---
---

## Part 2: Reimbursement Module (Core UI)

---

### 2.1 Employee Portal

| File | Purpose |
|------|---------|
| `pages/mainPages/userPortal/Reimbursement/ReimbursementPage.jsx` | Employee screen — summary counts by status, request history, entry point to raise new claim. |
| `pages/mainPages/userPortal/Reimbursement/ApplyReimbursementModal.jsx` | Submission form: type, amount, bill date, description, attachments. Validated before API call. |
| `pages/mainPages/userPortal/Reimbursement/ReimbursementTable.jsx` | Employee's own history showing requested vs approved amount (partial approval visible). |
| `pages/mainPages/userPortal/Reimbursement/reimbursementValidation.js` | Client-side field rules. |
| `pages/mainPages/userPortal/Reimbursement/reimbursementService.js` | Module-local API helper (superseded by shared service). |

---

### 2.2 Admin Portal

| File | Purpose |
|------|---------|
| `pages/mainPages/adminReimbursement/AdminReimbursementPage.jsx` | Admin screen — summary cards, status filter tabs, search, approve/reject handlers. |
| `pages/mainPages/adminReimbursement/AdminReimbursementTable.jsx` | Admin list with per-request review actions. |

---

### 2.3 Shared Components

| File | Purpose |
|------|---------|
| `shared/components/reimbursement/StatusBadge.jsx` | Shared status pill for request and payment status across both portals. |
| `shared/components/reimbursement/UploadField.jsx` | Shared attachment picker handling file type, size, listing, removal. |
| `shared/services/reimbursementService.js` | Single API layer for all reimbursement calls. First real service layer in this codebase. |

---

### 2.4 Routing & Navigation

| File | Change |
|------|--------|
| `pages/pageLayouts/router.js` | Registered employee and admin reimbursement routes behind correct route guards. |
| `pages/pageLayouts/employeeLayout/employeeSidebar.js` | Added Reimbursement entry to employee portal navigation. |
| `pages/pageLayouts/sidebarLayout/sidebar.js` | Added Reimbursement entry to admin navigation. |

---

### 2.5 API Integration

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/employee-reimbursements` | Submit new reimbursement request |
| GET | `/api/employee-reimbursements` | List own requests |
| GET | `/api/employee-reimbursements/{id}` | View single own request |
| GET | `/api/admin/reimbursements` | Admin lists all org requests |
| PUT | `/api/admin/reimbursements/{id}/approve` | Admin approves |
| PUT | `/api/admin/reimbursements/{id}/reject` | Admin rejects |

---
---

## Part 3: PayRun Integration (UI Changes)

---

### 3.1 PayRun Employee Table - Preview Page (`preview.js`)

| Change | Details |
|--------|---------|
| File | `src/pages/mainPages/payRuns/preview.js` |
| Data mapping | Added `claimDeduction: emp.claimDeduction || 0` and `claimReimbursement: emp.claimReimbursement || 0` to transformedEmployees |
| Deductions fix | Changed `deductions` field to use `emp.totalDeductions || 0` directly from backend instead of recalculating as EPF + PT |
| New columns | Added "CLAIM DEDUCTION" (width: 140px) and "CLAIM REIMBURSEMENT" (width: 160px) between LOP AMOUNT and NET PAY |
| Format | `₹{parseFloat(amount || 0).toLocaleString()}` |

---

### 3.2 PayRun Employee Table - Summary Page (`summary.js`)

| Change | Details |
|--------|---------|
| File | `src/pages/mainPages/payRuns/summary.js` |
| Data mapping | Added `claimDeduction: emp.claimDeduction || 0` and `claimReimbursement: emp.claimReimbursement || 0` to transformedEmployees |
| New columns | Added "CLAIM DEDUCTION" (width: 140px) and "CLAIM REIMBURSEMENT" (width: 160px) between LOP AMOUNT and NET PAY |
| Format | `₹{parseFloat(amount || 0).toLocaleString()}` |

---

### 3.3 Payslip - Admin View (`viewPayslip.js`)

| Change | Details |
|--------|---------|
| File | `src/pages/mainPages/payRuns/viewPayslip.js` |
| Destructuring | Added `claimDeduction` and `claimReimbursement` from payslipData |
| Variables | Added `claimDeductionFromBackend` and `claimReimbursementFromBackend` |
| Separate claims section | Added a new bordered section between "Gross Earnings / Total Deductions" summary and "TOTAL NET PAYABLE" |
| Claims (Additions) | Left column — shows "Claim Reimbursement" with amount (only if > 0) |
| Claims (Deductions) | Right column — shows "Claim Deduction" with amount (only if > 0) |
| Conditional rendering | Entire claims section hidden when both amounts are 0 |
| Net Pay formula text | Updated to "Gross Earnings + Claims (Additions) - Total Deductions" |

---

### 3.4 Payslip - Employee Portal (`payslipDocument.js`)

| Change | Details |
|--------|---------|
| File | `src/pages/mainPages/userPortal/components/payslipDocument.js` |
| Destructuring | Added `claimDeduction` and `claimReimbursement` from payslipData |
| Variables | Added `claimDeductionFromBackend` and `claimReimbursementFromBackend` |
| Separate claims section | Same layout as admin view — bordered section with CLAIMS (ADDITIONS) and CLAIMS (DEDUCTIONS) |
| Conditional rendering | Section hidden when both amounts are 0 |
| Net Pay formula text | Updated to "Gross Earnings + Claims (Additions) - Total Deductions" |

---

### 3.5 Payslip Layout (Both Admin & Employee Portal)

```
┌─────────────────────────────────────────────────────┐
│ EARNINGS                  │ DEDUCTIONS              │
│ Basic            ₹20,833  │ Income Tax        ₹0   │
│ HRA              ₹10,416  │ EPF Contribution  ₹2,500│
│ Conveyance       ₹0       │ Professional Tax  ₹200  │
│ Fixed Allowance  ₹7,916   │                         │
│ Bonus            ₹0       │                         │
├─────────────────────────────────────────────────────┤
│ Gross Earnings   ₹39,166  │ Total Deductions  ₹3,989│
├─────────────────────────────────────────────────────┤
│ CLAIMS (ADDITIONS)        │ CLAIMS (DEDUCTIONS)     │
│ Claim Reimbursement ₹289  │ Claim Deduction  ₹1,289│
├─────────────────────────────────────────────────────┤
│ TOTAL NET PAYABLE                         ₹35,466.67│
│ Gross Earnings + Claims (Additions) - Total Deductions│
└─────────────────────────────────────────────────────┘
```

**Note:** Claims section only appears when at least one claim > 0. If no claims exist for the employee, section is hidden.

---

### 3.6 Important Notes

- **Total Deductions** already includes Claim Deduction in the backend calculation (EPF + PT + TDS + Claim Deduction). The separate Claims section is for visibility/transparency only.
- **Claim Reimbursement** is NOT inside Gross Earnings — it's a separate addition to net pay, shown in its own section.
- **Net Pay** = Gross Earnings + Claim Reimbursement - Total Deductions
- Both payslip views (admin `viewPayslip.js` and employee `payslipDocument.js`) show the same data from the same API.

---

### 3.7 API Data Source

| UI Component | API Endpoint | Fields Used |
|-------------|-------------|-------------|
| PayRun employee table (preview/summary) | `GET /api/payrun-employees/list/{payrunId}` | `claimDeduction`, `claimReimbursement`, `totalDeductions` |
| Payslip detail view | `GET /api/payrun-employees/{payrunId}/{employeeId}` | `claimDeduction`, `claimReimbursement` |
| PayRun summary card | `GET /api/payruns/{payrunId}` | `totalClaimDeduction`, `totalClaimReimbursement` |
