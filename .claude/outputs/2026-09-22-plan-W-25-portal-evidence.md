# W-25 Employee Self-Service Portal — Evidence Pass

| Aspect | Payroll | HRMS | Gating |
|---|---|---|---|
| Backend controller | `EmployyePortalContoller.java` | N/A | API key for Payroll; RBAC actions for HRMS |
| Frontend layout | `employeeLayout` routes | N/A (single role "user") | `AuthGuard(employee)` |
| Access control | `portalEnabled` flag + org check | Not applicable | Server-side: `/my-organizations` endpoint |
| Panels available | 7 (profile, salary, payslips, tax, POI, investments, reimbursement, deductions) | 4 (timesheet, attendance, leaves, balance) | Per-feature in config |

---

## Part A — Payroll Employee Portal

### 1. Backend & Frontend Implementation

**Backend Controller:** `EmployyePortalContoller.java` (legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/employee/EmployyePortalContoller.java, lines 1–150)  
Note: Typo in class name `EmployyePortalContoller` (not `EmployeePortalController`). Endpoint base path: `/api/employees-portal` (line 17).

**Endpoints:**
- `GET /api/employees-portal/employee-profile` (line 28) — returns `EmployeeFullProfileDTO` with basic details, CTC, personal, bank details.
- `POST /api/employees-portal/{employeeId}/enable-portal` (line 116) — sets `portalEnabled=true` on `BasicDetails` and sends invitation email.
- `POST /api/employees-portal/{employeeId}/resend-invitation` (line 133) — resends invitation if portal is enabled.
- `PUT /api/employees-portal/{employeeId}/activate` (line 50) — sets employee status to `ACTIVE`.
- `PUT /api/employees-portal/{employeeId}/deactivate` (line 73) — sets employee status to `INACTIVE`.
- `DELETE /api/employees-portal/{employeeId}/soft-delete` (line 96) — marks employee as deleted.

**Frontend Layout & Routes** (legacy/Payroll-Fend-react/src/pages/pageLayouts/router.js, lines 935–985):
- Layout: `<ThemedEmployeeLayout />` with guard `AuthGuard({ allowedRole: 'employee' })` (line 936).
- Routes under `/`:
  - `/home` → `<Home />` (line 938)
  - `/userProfile` → `<MyProfile />` (line 939) — employee's own profile
  - `/user-salary-details` → `<MySalaryDetails />` (line 940) — CTC and salary info
  - `/payslips/:payrunId` → `<PayslipGenerator />` (line 945) — view/download payslip
  - `/user-tax-calculator` → `<UserTaxCalculator />` (line 947)
  - `/user-poi` → `<UserPOI />` (line 950) — Proof of Investment submission
  - `/user-investment` → `<UserInvestment />` (line 961) — investment declaration
  - `/reimbursement` → `<ReimbursementPage />` (line 979) — submit/view reimbursement claims
  - `/my-deductions` → `<MyDeductions />` (line 982) — view applied ad-hoc deductions

**Panels/Data Available to Employee:**
1. Home dashboard
2. Personal profile (basic + CTC active revision)
3. Salary details (CTC structure, earnings)
4. Payslips (per pay run)
5. Tax calculator (self-service income tax estimation)
6. Proof of Investment (submit documents, view status)
7. Investment declaration (declare investments for tax purposes)
8. Reimbursement claims (submit, view approval status)
9. Ad-hoc deductions (view applied deductions)

---

### 2. How Portal Access Is Decided

**Flag:** `BasicDetails.portalEnabled` (Boolean, default `false`).

**Who Sets It:** `EmployyePortalServiceImpl.enablePortal()` (line 273)  
Sets `employee.setPortalEnabled(true)` and triggers invitation email. Requires an admin action: `/api/employees-portal/{employeeId}/enable-portal`.

**Invitation Requirement:** Once enabled, an `EmployeeInvitation` record is created/updated (line 289) with an acceptance token. Employee receives an email with the token.

**Citation:** `EmployyePortalServiceImpl.java:280, 324, 325` (portal enable/disable checks).

---

### 3. Server-Side vs. Frontend Gating

**Server-Side Gating (Primary Security):**

Employee portal login (legacy/Payroll-Fend-react/src/pages/authPages/login/employeePortalLogin.js, lines 73–95):
1. User authenticates via Keycloak (line 56).
2. Frontend calls `/api/organization-user-role-mapping/my-organizations` (line 74) — **this is a backend endpoint that returns only organizations where the current user has employee-portal access**.
3. If the list is empty, the frontend blocks login with error "This account does not have Employee Portal access" (lines 87–94).
4. **This is a backend filter:** The endpoint does not return orgs without `employeePortalEnabled=true`.

**Evidence:** Comment at line 85: "An admin-only account has no organization with `employeePortalEnable=true`, so `my-organizations` comes back empty => block employee portal access."

**Frontend Mitigation:** `localStorage` is set only after the backend check passes (line 98: `localStorage.setItem("__t", accessToken)`). The `AuthGuard` component (legacy/Payroll-Fend-react/src/shared/guards/authGuard.js, line 26) reads `userType` from localStorage and enforces role-based route access (lines 39–40), but this is a **second line of defense**.

**Conclusion:** Gating is **server-side primary.** The `/my-organizations` endpoint filters organizations by `employeePortalEnabled`, ensuring only authorized users can obtain a token for the employee portal. Frontend checks are redundant safeguards.

---

## Part B — HRMS Employee Self-Service

**No dedicated employee portal in HRMS.** Employees access self-service features via role-based routes within the main application.

**Employee Routes** (legacy/HRMS_Frontend/src/App.jsx, lines 304–400):

1. **Timesheet:**
   - `/my-timesheet` (line 304, requires `ADD_ENTRY` action) — submit timesheet
   - `/my-timesheet-detail` (line 310, requires `MANAGE_TIMESHEET`) — detailed view
   - `/my-timesheet-view` (line 316, requires `VIEW_TIMESHEET`) — read-only view
   - `/edit-my-timesheet/:timesheetId` (line 322, requires `ADD_ENTRY`) — edit existing timesheet

2. **Attendance:**
   - `/my-attendance` (line 335, requires `VIEW_ATTENDANCE` action) — view own clock-in/out records

3. **Leave Management:**
   - `/apply-leaves` (line 390, requires `APPLY_LEAVE` action) — submit leave request
   - `/my-leave-balance` (line 396, requires `VIEW_LEAVE_BALANCE` action) — view leave balance and history
   - These routes also render leave-related data: holidays, overtime forms (feature map items 5–6)

**Gating Mechanism:** All employee self-service routes in HRMS use `ActionProtectedRoute` (line 54: `import ActionProtectedRoute`), which enforces RBAC based on the required action string. Users must be granted that action in their role to access the route (feature map item 2, line 41: "ActionProtectedRoute on the frontend enforces these").

**Data Available to Employee:**
- Submitted timesheets and history
- Daily attendance (clock-in/out times)
- Leave balances per leave type
- Apply for leave requests
- View company holidays
- Overtime request form (feature map item 6: `OverTimeForm.jsx`)

**No cross-service integration:** HRMS does not expose payroll data (payslips, reimbursements) and Payroll does not expose HRMS data (timesheets, leave). Only the LOP-days integration exists (W-19).
