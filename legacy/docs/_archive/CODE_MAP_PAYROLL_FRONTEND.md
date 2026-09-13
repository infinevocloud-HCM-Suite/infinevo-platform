# Code Map — Payroll Frontend (`Payroll-Fend-react`)

> Branch: `employee` · HEAD `053ca62` (2026-09-10)
> React 18 (Create React App) · Ant Design 5 · Redux Toolkit · Keycloak SSO · ECharts · Formik + Yup
> 216 js/jsx files · ~116k LOC · 158 route entries
>
> Companion to [CODE_MAP_PAYROLL_BACKEND.md](CODE_MAP_PAYROLL_BACKEND.md). Modules are numbered
> to match, so §11 here is the same feature as §11 there.

---

## 0. How to read this codebase

### 0.1 The shape of every screen

There is **no API service layer** for most modules. Screens call `axios` directly. Learn this
five-part anatomy and every one of the 150+ page files reads the same way:

```
pages/mainPages/<module>/<screen>.js
   │
   ├─ 1. localStorage reads      const organizationId = localStorage.getItem("organizationId")
   │                             const token          = localStorage.getItem("__t")
   │
   ├─ 2. fetchXxx()              axios.get(`${GlobalConst.API_URL}/api/...`,
   │      inside useEffect         { headers: { organizationId, Authorization: `Bearer ${token}` } })
   │
   ├─ 3. Formik + Yup            validationSchema = Yup.object().shape({...})
   │      (forms only)
   │
   ├─ 4. handleSubmit()          axios.post/put → msgHelper.successMsg → navigate(...)
   │
   └─ 5. columns[]               Ant Design <Table> column definitions, usually at the bottom
```

**Consequence:** to find where a screen gets its data, grep the screen file for `API_URL`. To find
which screen calls a backend endpoint, grep the whole `src/` tree for the path.

The four modules that *do* have a service layer are the newest ones — see §0.4.

### 0.2 Routing and layouts

All 158 routes live in one file: **`pages/pageLayouts/router.js`** (1041 lines, `createBrowserRouter`).
It is organised as nested guard → layout → children:

| Guard | Layout | Route group | Who sees it |
|---|---|---|---|
| `AuthGuard allowedRole="admin"` → `OrganizationGuard` | `ThemedSidebarLayout` (`sidebarLayout/`) | Dashboard, employees, payruns, leaves, deductions, approvals | Admin/HR inside a chosen org |
| `AuthGuard allowedRole="admin"` → `OrganizationGuard` | `ThemedSettingsLayout` (`settingsLayout/`) | Everything under settings: org profile, salary components, statutory, leave setup, users/roles | Admin/HR |
| `AuthGuard allowedRole="admin"` | `ThemedOrgLayout` | `/create-new-organization`, `/manage-organization`, `/setup-new-organization/:id` | Admin **before** picking an org |
| `AuthGuard allowedRole="employee"` | `ThemedEmployeeLayout` (`employeeLayout/`) | `/home`, `/userProfile`, `/reimbursement`, `/my-deductions`, POI, tax calculator | Employee self-service portal |
| `LoginGuard` | `AuthLayout` | `/login`, `/employeePortalLogin`, `/forgot-password`, `/new-password`, `/create-new-account` | Signed-out |
| none | `ThemedLayout` | `/accept-invite`, `/public/payslips/:payrunId/:employeeId` | Public |

Each layout is `header + sidebar + <Outlet/> + footer`:

| Folder | Files | Role |
|---|---|---|
| `pages/pageLayouts/sidebarLayout/` | `index.js`, `header.js` (586), `sidebar.js` (356), `footer.js` | Main admin chrome. `sidebar.js` → `menuLinks[]` is the admin navigation source of truth. |
| `pages/pageLayouts/settingsLayout/` | `index.js`, `header.js`, `sidebar.js` (473), `footer.js` | Settings chrome with its own nav tree. |
| `pages/pageLayouts/employeeLayout/` | `index.js`, `header.js`, `employeeSidebar.js` (238), `footer.js` | Employee-portal chrome. |
| `pages/pageLayouts/dashboardLayout/` | `index.js`, `header.js`, `header-ols.js`, `footer.js` | Older chrome; `header-ols.js` is dead weight. |
| `pages/pageLayouts/authLayout/` | `index.js` | Bare shell for signed-out pages. |

> **To add a screen:** create the file under `pages/mainPages/<module>/`, import it at the top of
> `router.js`, add a `{ path, element }` under the right guard/layout block, then add a link in the
> matching sidebar's `menuLinks[]`.

### 0.3 Cross-cutting shared layer

| Folder | File | Function | What it does |
|---|---|---|---|
| `shared/helpers/` | `axiosInterceptor.js` | `checkTokenExpiry(expiryValue)` — L39 | Decides whether the Keycloak token needs refreshing. |
| | | `getNewToken()` — L49 | Silent refresh; the request interceptor attaches the result as `Authorization`. |
| `shared/helpers/` | `tokenHelper.js` | `getUserInfoFromToken()` — L7 | Decodes the JWT for name/email/roles. |
| | | `getDecodedToken()` — L29 | Raw decoded claims. |
| `shared/helpers/` | `rootLoader.js` | `rootLoader()` — L10 | React Router **loader** run before the admin tree renders — fetches org + user bootstrap data. |
| `shared/helpers/` | `resolveAdminLandingPath.js` | `isOrgInactive(org)` — L1 | Org status check. |
| | | `resolveAdminLandingPath(organizations)` — L16 | Decides post-login destination: create-org / pick-org / dashboard. |
| | | `normalizeOrganizationsResponse(orgRes)` — L34 | Shapes the org list. |
| `shared/helpers/` | `msgHelper.js` | `errorMsg(title, desc, ...)` — L10, `successMsg(...)` — L27 | SweetAlert2 toasts used app-wide instead of AntD `message`. |
| `shared/helpers/` | `mainHelper.js` | `getName`, `getEmail`, `getAvatar`, `getUserCharacter`, `getTimeZone`, `getUserImagePath` | Display helpers for headers and avatars. |
| `shared/helpers/` | `addEmployeeDraft.js` | `saveAddEmployeeWizardDraft({employeeId, activeStep})` — L20 | Persists add-employee wizard progress to localStorage. |
| | | `readAddEmployeeWizardDraft()` — L33 / `clearAddEmployeeDraft()` — L10 | Resume / reset the wizard. |
| `shared/guards/` | `authGuard.js` | `AuthGuard({allowedRole})` — L20 | Requires a Keycloak session and the matching role; else redirects to login. |
| | `loginGuard.js` | `LoginGuard()` — L22 | Inverse — bounces a signed-in user away from `/login`. |
| | `organizationGuard.js` | `OrganizationGuard()` — L20 | Requires a selected `organizationId`; else routes to org selection/creation. |
| `shared/redux/` | `store.js`, `checkAuth.js` | `checkAuthToken()` — L5 | Boot-time auth resolution (also handles the `?logout=yes` param). |
| `shared/redux/reducers/` | `authReducer.js` | `updateToken` (createAsyncThunk) — L6 | Token state. |
| | `globalReducer.js`, `settingsModalReducer.js` | — | Global UI flags; settings-modal open state. |
| `shared/organization/` | `OrganizationContext.js` | `OrganizationProvider({mode, children})` — L8, `useOrganization()` — L18 | Current-org context; `mode` is `admin` or `employee`. |
| | `useOrganizationResolver.js` | `useOrganizationResolver(mode)` — L9 | Resolves which org the session is operating in. |
| `shared/appConfig/` | `globalConst.js` | `GlobalConst` | `API_URL`, `AUTH_URL`, `BASE_URL`, `CLIENT_ID: "react-app"`, `REALM: "HRMS"`. **Every axios call starts here.** |
| | `keycloak.js` | `keycloak` | The `Keycloak` instance. |
| | `countryStateList.js` (6359), `countryCodes.js` (1211), `countryStates.js`, `timezone.js`, `legalStructures.js`, `industryList.js`, `stateList.js` | — | Static reference data for org/employee forms. |
| | `errorConst.js` | `ERROR_CONST` | Message keys used by `msgHelper`. |
| `shared/components/` | `loaders/`, `noData/`, `settingsModal/`, `reimbursement/` | — | The small reusable set: spinners, empty states, the settings modal, reimbursement widgets. |

### 0.4 The service layer (newest modules only)

| File | Exports | Backing endpoints |
|---|---|---|
| `shared/services/authService.js` | `registerUser(userData)` | `/auth/register` |
| `shared/services/invitationService.js` | `getInvitationDetails({user,type,orgId})`, `processInvitation({...})` | `/api/public/invitation-by-email`, `/api/public/process-invitation` (sends the `fed.secret` header) |
| `shared/services/reimbursementService.js` | `getEmployeeReimbursements`, `getEmployeeReimbursementById`, `createEmployeeReimbursement`, `getAdminReimbursements`, `getAdminReimbursementById`, `approveAdminReimbursement`, `rejectAdminReimbursement` | `/api/employee/reimbursements`, `/admin/reimbursements` |
| `shared/services/leaveStore.js` (528) | See §8 — the leave module's data layer, combining API calls with a localStorage cache | `/api/leave-allocation`, `/api/leave-consumption` |

### 0.5 Environment

`.env` (dev):

```
PORT=3000
REACT_APP_API_URL=http://localhost:3032          # payroll backend
REACT_APP_AUTH_URL=https://authentication.infinevocloud.com   # Keycloak
REACT_APP_BASE_URL=http://localhost:3000
```

```bash
npm start      # Windows; use "npm run linuxstart" on Linux/macOS
npm run build  # Windows; "npm run linuxbuild" elsewhere (raises Node heap to 2GB)
```

---

## Module index

| # | Module | Folder | Routes |
|---|---|---|---|
| 1 | [Auth & Invitations](#1-auth--invitations) | `pages/authPages/login/`, `pages/mainPages/acceptInvite/` | `/login`, `/register`, `/accept-invite` … |
| 2 | [Organization Setup](#2-organization-setup) | `pages/mainPages/organizationRegister/`, `allSettingsPages/` | `/create-new-organization`, `/all-settings` … |
| 3 | [Users, Roles & Permissions](#3-users-roles--permissions) | `allSettingsPages/users/` | `users`, `roles` … |
| 4 | [Employee Management](#4-employee-management) | `pages/mainPages/employee/` | `employees`, `/employees/add/*`, `/employees/view/:id` … |
| 5 | [CTC & Salary Revision](#5-ctc--salary-revision) | `pages/mainPages/employee/`, `pages/mainPages/approval/` | `/employees/edit-revise-salary/:id`, `/salary-revision-approvals` … |
| 6 | [Salary Components](#6-salary-components) | `allSettingsPages/salaryComponents/` | `salary-components/*` |
| 7 | [Statutory Components](#7-statutory-components) | `allSettingsPages/statutoryComponents/` | `statutory-components/*` |
| 8 | [Leave Management](#8-leave-management) | `leaveManagement/`, `markLeaves/`, `allSettingsPages/leaveAttendence/` | `/leave-allocation`, `/mark-leaves` … |
| 9 | [Employee Deductions](#9-employee-deductions) | `pages/mainPages/deduction/`, `userPortal/myDeductions/` | `/employee-deductions`, `/my-deductions` |
| 10 | [Reimbursement Claims](#10-reimbursement-claims) | `adminReimbursement/`, `userPortal/Reimbursement/` | `/admin-reimbursements`, `/reimbursement` |
| 11 | [Pay Run](#11-pay-run) | `pages/mainPages/payRuns/` | `/payruns`, `/preview/:id`, `/summary/:id`, `/view-payslip/...` |
| 12 | [Tax & Forms](#12-tax--forms) | `taxesAndForms/`, `allSettingsPages/taxes/` | `/tax-calculator`, `/form16s`, `tax-details` |
| 13 | [IT Declaration & POI](#13-it-declaration--poi) | `approval/`, `employee/`, `userPortal/userInvestment/`, `userPortal/taxation/` | `/proof-of-investment`, `/user-investment`, `investments-and-proofs/*` |
| 14 | [Dashboard](#14-dashboard) | `dashboardPage/` | `/dashboard`, `onboarding-dashboard` |
| 15 | [Employee Self-Service Portal](#15-employee-self-service-portal) | `userPortal/` | `/home`, `/userProfile`, `/user-salary-details` … |

---

## 1. Auth & Invitations

### Functional flows

1. **Admin login** — Keycloak redirect → `LoginGuard` → `resolveAdminLandingPath()` decides: create an org, pick an org, or go straight to the dashboard.
2. **Employee portal login** — a separate screen (`/employeePortalLogin`) landing on the employee layout.
3. **Create account** — self-signup for a new org admin.
4. **Forgot password → new password** — Keycloak-backed reset.
5. **Accept invite** — public page; reads the invite by email, then accepts or rejects.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `pages/authPages/login/` | `index.js` (612) | `Login` (default) | Keycloak sign-in, plus the branch to the portal login. |
| | `employeePortalLogin.js` (246) | `EmployeePortalLogin` | Employee-side login. |
| | `createAccountPage.js` (530) | `CreateAccountPage` → `handleSubmit` | Formik + Yup signup → `authService.registerUser`. |
| | `forgotPasswordPage.js` (177) | `ForgotPasswordPage` | Triggers the reset email. |
| | `NewPassword.js` (273) | `NewPassword` | Sets the new password from the emailed link. |
| `pages/mainPages/acceptInvite/` | `index.js` (309) | `AcceptInvite` | Reads `?user&type&orgId`, calls `getInvitationDetails`, renders accept/reject, calls `processInvitation`. |
| `shared/services/` | `invitationService.js` | `getInvitationDetails`, `processInvitation` | The two public endpoints; sends `FED_SECRET_HEADER`. |
| `shared/guards/` | `authGuard.js`, `loginGuard.js`, `organizationGuard.js` | see §0.3 | Route protection. |
| `shared/helpers/` | `resolveAdminLandingPath.js` | `resolveAdminLandingPath(orgs)` | Post-login routing decision. |

**Backend:** module 1 — `CompanyUserController`, `InvitationAcceptanceController`.

---

## 2. Organization Setup

### Functional flows

1. **Create org** → **setup wizard** (`/setup-new-organization/:organizationId`) → head office + basics.
2. **Manage organizations** — switch between orgs, delete an org.
3. **Settings hub** (`/all-settings`) — the tile grid that links to every settings screen.
4. **Reference data** — work locations, departments, designations, each with a list, add, edit and CSV-import screen.
5. **Pay schedule** — view / edit the org's pay cycle.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `pages/mainPages/organizationRegister/` | `index.js` (512) | `OrganizationRegister` → `validationSchema` L45, `handleSubmit` L58 | Creates the org (`POST /api/organizations/new`). |
| | `setupNewOrganization.js` (576) | `SetupNewOrganization` → `handleSubmit` L129 | The wizard step (`PUT /api/organizations/setup/{id}`). |
| | `manageOrganization.js` (262) | `ManageOrganization` → `handleGoToOrganization` L104, `handleDeleteOrg` L68, `confirmDeleteOrg` L73 | Org switcher + delete. |
| `pages/mainPages/allSettingsPages/` | `allSettingsComponents.js` (286) | `AllSettingsComponents` | The settings tile grid — the index of everything in §2, §3, §6, §7, §8, §12. |
| | `profile.js` (1049) | `OrganisationForm` / `OrganisationProfile` | Org profile edit incl. logo upload (multipart). |
| `.../workLocations/` | `workLocations.js` (368) | `WorkLocations` | List. |
| | `addWorkLocations.js` (311) | `AddWorkLocation` | Create. |
| | `editWorkLocations.js` (320) | `EditWorkLocationForm` | Edit. |
| | `importWorkLocations.js` (387) | `ImportWorkLocations` | Dropzone CSV → `/api/worklocations/imports`. |
| `.../departments/` | `index.js` (463), `importDepartments.js` (360) | `Departments`, `ImportDepartments` | List + import. |
| `.../designations/` | `index.js` (416), `importDesignations.js` (351) | `Designations`, `ImportDesignations` | List + import. |
| `.../paySchedules/` | `paySchedules.js` (836) | `PaySchedules` | Pay-schedule form. |
| | `viewPaySchedules.js` (379) | `PayScheduleView` | Read-only view. |
| | `editPaySchedule.js` (931) | `EditPaySchedule` | Edit. |
| `.../employeePortal/` | `preference.js` (337) | `Preferences` | Employee-portal preferences. |

**Backend:** module 2.

---

## 3. Users, Roles & Permissions

| Folder | File | Function | What it does |
|---|---|---|---|
| `allSettingsPages/users/` | `index.js` (499) | `Users` (default) | The user grid. |
| | | `fetchUsers()` — L58 | `GET /api/invitations` — invitations joined with Keycloak last-login. |
| | | `formatUserType(userType)` — L42, `formatLastLogin(ts)` — L49 | Column formatting. |
| | | `handleToggleStatus(userId, current)` — L420 | Calls `/api/invitations/active|inactive/{id}`. |
| | | `showDeleteConfirm(...)` — L122, `handleDeleteUser(id)` — L141 | Delete with confirm. |
| | | `handleInviteUser` / `handleEditUser` / `handleViewUser` — L463/L455/L459 | Navigation. |
| | `inviteUser.js` (354) | `InviteUser` | Invite form → `POST /api/invitations`. |
| | `editUser.js` (377) | `EditUser` | Edit an invited user, reassign role. |
| | `roles.js` (268) | `Roles` | Role list → `/api/roles`. |
| | `newRole.js` (251) | `CreateNewRole` | Create role + pick actions. |
| | `editRole.js` (213) | `EditRole` | Edit role, re-assign actions via `/api/organizations/{orgId}/roles/{roleId}/actions`. |
| | `viewRole.js` (227) | `ViewRole` | Read-only permission matrix. |

**Backend:** module 3.

---

## 4. Employee Management

### Functional flows

1. **Employee list** (`employees`) — search, filter by department, activate/deactivate, delete, import.
2. **Add-employee wizard** — four screens in sequence, each posting to a different endpoint and advancing a localStorage draft:
   `basic-details` → `salary-details` → `personal-details` → `payment-details`.
3. **Employee detail** (`/employees/view/:id`) — a tabbed shell with nested routes.
4. **Edit** — one screen per section.
5. **Bulk import** — `importbasicdetails`.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `pages/mainPages/employee/` | `index.js` (958) | `Employees` (default) | The list screen. |
| | | `fetchEmployees()` — L116 | `GET /api/employees` (paged). |
| | | `departments` / `filteredEmployees` (useMemo) — L45/L56 | Client-side filtering. |
| | | `isProfileComplete(completionStatus)` — L213 | Drives the "incomplete profile" badge — an incomplete employee is excluded from payruns. |
| | | `toggleEmployeeStatus(employeeId, current)` — L260 | `/api/employees-portal/{id}/activate|deactivate`. |
| | | `deleteEmployee(id, name)` — L297 | Soft delete. |
| | | `showImportModal` / `handleImportProceed` — L223/L233 | Routes to the right import screen by type. |
| | | `renderMobileCard(record)` — L337, `columns[]` — L435 | Responsive table. |
| | `addEmployee.js` (311) | `AddEmployee` | Wizard shell — owns the stepper and the draft. |
| | `basicDetails.js` (1817) | `BasicDetails` (step 1) | |
| | | `fetchHrUsers` L66, `fetchDepartments` L87, `fetchDesignations` L108, `fetchWorkLocations` L129 | Dropdown sources. |
| | | `fetchOrgStatutoryConfig()` — L151 | `/api/employees/statutory/config` — decides whether PF/ESI fields show. |
| | | `validationSchema` — L178 | The largest Yup schema in the app. |
| | | `handleSubmit(values)` — L431 | `POST /api/employees`, stores the returned `employeeId` in the draft. |
| | `salaryDetails.js` (1345) | `SalaryDetails` (step 2) | See §5 — this is the CTC builder. |
| | `personalDetails.js` (563) | `PersonalDetails` (step 3) | `POST /api/employees/personal-details`. |
| | `paymentInformation.js` (493) | `PaymentInformation` (step 4) | `POST /api/v1/employees/bank-details`. |
| | `viewEmployee.js` (262) | `ViewEmployee` | Tab shell with `<Outlet/>` for the nested tabs. |
| | `OverviewTab.js` (396) | `OverviewTab` | Default tab. |
| | `SalaryDetailsTab.js` (825) | `SalaryDetailsTab` | Current CTC. |
| | `PayrollFormsTab.js` (385) | `PayrollFormsTab` | Form 16 / payslips for this employee. |
| | `LoanTab.js` (461) | `LoanTab` | Loan details. |
| | `InvestmentsAndProofsLayout.js` (55) | `InvestmentsAndProofsLayout` | Sub-layout for the declaration/proof tabs (see §13). |
| | `editBasicDetails.js` (838), `editPersonalDetails.js` (516), `editPaymentDetails.js` (657), `editStatutoryDetails.js` (358) | one component each | Section edits. |
| | `importBasicDetails.js` (406) | `ImportBasicDetails` | CSV import. |
| | `importEmployee.js` (256) | `ImportEmployee` | Import-type chooser. |
| | `addEmpOld.js` (2441) | — | **Dead code** — the pre-wizard single-page add-employee form. Not routed. |
| | `editEmployee.js` (0 bytes) | — | **Empty file.** |
| `shared/helpers/` | `addEmployeeDraft.js` | `saveAddEmployeeWizardDraft`, `readAddEmployeeWizardDraft`, `clearAddEmployeeDraft` | Wizard resume. |

**Backend:** module 4.

---

## 5. CTC & Salary Revision

### Functional flows

1. **Build CTC** — enter annual CTC; Basic / HRA / other components are derived by calculation type (% of CTC, % of Basic, fixed). Statutory (EPF/ESI) is previewed live. A reconciliation check makes the parts sum back to the CTC within a ₹2 tolerance.
2. **Revise salary** — a revision form with an effective date, showing old vs new.
3. **Approval queue** — admin approves / rejects / defers ("process later") / exports to Excel.
4. **View a revision** — read-only comparison with % change.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `pages/mainPages/employee/` | `salaryDetails.js` (1345) | `SalaryDetails` — L70 | The CTC builder (wizard step 2). |
| | | `toNum(v, fallback)` — L14, `sanitizeDecimal(raw)` — L20 | Numeric input hygiene. |
| | | `CTC_RECONCILE_TOLERANCE = 2` — L27 | The ₹2 rounding tolerance. |
| | | `buildCalcTypePatch(component, newType, sd)` — L32 | Converts a component between fixed / %-of-CTC / %-of-Basic **without changing its current value**. |
| | | `fetchEarnings()` — L175 | Loads the org's earning catalogue. |
| | | `fetchOrgStatutoryConfig()` — L201 | EPF/ESI applicability. |
| | | `calculateStatutoryBenefits(values)` — L241 | Live EPF/ESI preview. |
| | | `getCtcReconciliation(values)` — L279 | Returns the gap between the sum of components and the CTC. |
| | | `calculateSalaryDetails(sd, setFieldValue, calcTypes)` — L344 | The recalculation engine — re-derives every dependent field on any change. |
| | | `mapToBackendDTO(values, employeeId)` — L492 | Flattens the form into the `EmployeeCTCDTO` shape. |
| | | `handleSubmit(values)` — L431 | `POST /api/v1/ctc-structures`. |
| | `editSalaryDetails.js` (1694) | `EditSalaryDetails` | Same engine, update mode. |
| | `editReviseSalary.js` (1985) | `EditReviseSalary` — L83 | The revision form. |
| | | `getEarningAmount(earnings, code)` — L16 | Pulls a named component out of the earnings array. |
| | | `fetchSalaryData` L231, `fetchEarnings` L261, `fetchMasterEarnings` L285, `fetchOrgStatutoryConfig` L324, `fetchEmployeeBasicDetails` L345 | Five parallel loads before the form can render. |
| | | `calculateStatutoryBenefits` L376, `getCtcReconciliation` L439, `calculateSalaryDetails` L486, `buildCalcTypePatch` L50 | Same calculation engine as `salaryDetails.js` — **duplicated, not shared**. |
| | `editSalaryRevision.js` (1913) | `EditSalaryRevision` | Edit an already-created revision. |
| | `salaryRevisonDetails.js` (757) | `SalaryRevisionDetails` | Read-only revision detail *(note the filename typo: "Revison")*. |
| `pages/mainPages/approval/` | `salaryRevisionApproval.js` (1188) | `SalaryRevisionApproval` — L63 | The approval queue. |
| | | `fetchSalaryRevisions(page, pageSize)` — L108 | `GET /api/v1/ctc-structures/revision/list`. |
| | | `handleApprove(record)` — L454 / `handleReject(record)` — L494 | Decisions. |
| | | `handleProcessLater` L332 / `confirmProcessLater` L340 | `POST /revisions/process-later`. |
| | | `handleExport()` — L283 | `GET /revisions/export` → Excel. |
| | | `handleDelete` L408 / `confirmDelete` L416 | Delete a pending revision. |
| | | `getStatusConfig(status)` — L537 | Status badge mapping. |
| | | `rowSelection` — L266, `selectedCount` — L804 | Bulk-action support. |
| | `viewReviseSalary.js` (744) | `ViewReviseSalary` — L94 | Read-only revision view. |
| | | `fetchRevisionData()` — L106 | `GET /api/v1/ctc-structures/revision`. |
| | | `calculateTotalEarnings` L248, `calculateBasicPercentage` L253, `calculateHRAPercentage` L263 | Derived display figures. |
| | | `deleteRevision()` — L206 | Delete from the view screen. |
| | | `getEarningDisplayName(code)` — L80 | Code → label map. |

**Backend:** module 5.

> ⚠️ The CTC calculation engine (`buildCalcTypePatch`, `calculateSalaryDetails`,
> `getCtcReconciliation`, `calculateStatutoryBenefits`) is **copy-pasted across three files**:
> `salaryDetails.js`, `editSalaryDetails.js`, `editReviseSalary.js`. A change to the split rules
> must be made in all three.

---

## 6. Salary Components

| Folder | File | Function | What it does |
|---|---|---|---|
| `allSettingsPages/salaryComponents/` | `index.js` (606) | `SalaryComponents` (default) | The **earnings** list — the landing tab. |
| | | `fetchEarnings()` — L44 | `GET /api/earnings`. |
| | | `getCalculationType(earning)` — L113, `getEpfConsideration` — L122, `getEsiConsideration` — L129 | Column derivations. |
| | | `getStatusFromRecord(record)` — L134, `isActiveString(s)` — L20 | The backend returns status under several different keys; these normalise it. |
| | | `handleStatusToggle(record)` — L147 | `PUT /api/earnings/active|inactive/{id}`. |
| | | `showDeleteConfirm` L194 / `handleDelete` L212 | Delete with confirm. |
| | `deductions.js` (548) | `Deduction` | Deduction list (`/api/deductions`). |
| | `benifits.js` (498) *(sic)* | `Benefits` | Benefit list (`/api/benefits`). |
| | `reimbursement.js` (500) | `Reimbursement` | Reimbursement-type list (`/api/reimbursements`). |
| | `editEarning.js` (630), `editDeduction.js` (310), `editBenifits.js` (419), `editReimbursement.js` (324) | one component each | Edit forms. |
| `.../addComponents/` | `addNewEarning.js` (320) | `AddNewEarning` | Standard earning. |
| | `addNewCustomEarning.js` (668) | `AddNewCustomEarning` | Custom earning with full calculation-type control. |
| | `addNewDeduction.js` (268) | `AddNewDeduction` | |
| | `addNewBenefits.js` (421) | `AddNewBenefits` | |
| | `addNewReimbursement.js` (328) | `AddNewReimbursement` | |
| | `addNewCorrection.js` (310) | `AddNewCorrection` | Correction/arrear component. |

**Backend:** module 6.

---

## 7. Statutory Components

| Folder | File | Function | What it does |
|---|---|---|---|
| `allSettingsPages/statutoryComponents/` | `index.js` (563) | `StatutoryComponents` | Tabbed shell (EPF / ESI / PT) that also renders the EPF summary. |
| | | `fetchEpfData()` — L162 | `GET /api/epf`. |
| | | `normalizeEpfDtoToUi(dto)` — L47 | Backend DTO → form model. |
| | | `RATES` — L87 | Statutory constants: employee EPF, EPS wage cap, EDLI and admin-charge caps. |
| | | `buildSample(vals)` — L100 | Builds the worked example ("on a ₹15,000 wage you pay…") shown beside the form — where the EPS split, EDLI and admin charges are demonstrated. |
| | | `mapRawToSampleOptions(raw)` — L243, `formatRateDisplay(rate)` — L236, `rupee(v)` — L158 | Display helpers. |
| | | `handleEnableEpf` L199 / `handleEditEpf` L203 / `handleDeleteEpf` L207 | Enable / edit / disable. |
| | `epf.js` (639) | `EPF` | EPF detail view. |
| | `editEPF.js` (617) | `EditEPF` | EPF form. |
| | `EPFSampleModal.js` (148) | `EPFSampleModal` | The worked-example modal. |
| | `viewESI.js` (319) | `ViewESI` | ESI view. |
| | `formESI.js` (349) | `FormESI` | ESI create. |
| | `editESI.js` (358) | `EditESI` | ESI edit. |
| | `viewProfessionalTax.js` (565) | `ProfessionalTax` | PT list per state → `/api/professional-tax`. |
| | `ptDetails.js` (578) | `PtDetails` | Slab editor with effective date; also the reset-to-default action. |

**Backend:** module 7.

---

## 8. Leave Management

> The newest and most distinctive module — the only one with a real data layer
> (`shared/services/leaveStore.js`), and the only one that caches server state in localStorage.

### Functional flows

1. **Leave Allocation** (`/leave-allocation`) — grid of employees × leave types; set annual days, expiry, monthly breakdown. Add employees via a modal, save all at once, or import from Excel/CSV with a downloadable sample template.
2. **Mark Leaves overview** (`/mark-leaves`) — flat table of every leave entry, filterable by department/month; delete a single entry or a whole record.
3. **Mark Leaves Taken** (`/mark-leaves-taken`) — employee cards with allocated / consumed / balance / LOP; select employees to mark leave for.
4. **Mark Leave — add employees** (`/mark-leave-add-employee`) — the entry form: per employee, per leave type, days taken + LWP + reason, with **live balance and LOP computation as you type**.

### Code flow — the data layer

| Folder | File | Function | What it does |
|---|---|---|---|
| `shared/services/` | `leaveStore.js` (528) | `LEAVE_STORE_PREFIX` — L11 | localStorage key prefix, versioned (`..._v6`) so stale caches are discarded on change. |
| | | `getLeaveStoreKey(year)` — L30 | Per-year cache key. |
| | | `getStoredLeaveEmployees(year)` — L95 | Reads the cache. |
| | | `saveLeaveEmployees(employees, year, notify)` — L118 | Writes the cache and dispatches a custom DOM event so other mounted screens refresh. |
| | | `getAuthHeaders()` — L35 | `organizationId` + bearer token. |
| | | `isLeaveExpired(expirationDate)` — L20 | Expiry check — expired allocations stop counting toward balance. |
| | | `normalizeBalanceRecord(entry)` — L47 | Server row → UI card model (allocated / consumed / remaining / lop / expired). |
| | | `fetchLeaveAllocationsFromApi(year)` — L143 | `GET /api/leave-allocation` — the primary load. |
| | | `saveLeaveAllocationsToApi(cards, year)` — L385 | `POST /api/leave-allocation/bulk`. |
| | | `updateEmployeeLeaveAllocationApi(employeeId, entries, year, month)` — L356 | `PUT /api/leave-allocation/{id}`. |
| | | `updateEmployeeLeaveConsumptionApi(employeeId, entries, year, month)` — L316 | `PUT /api/leave-consumption/{id}` — the "mark leave taken" save. |
| | | `saveAllMarkedLeavesViaPutApi(cards, year, month)` — L364 | Batch of the above across cards. |
| | | `deleteEmployeeLeaveEntryApi(employeeId, year, entryId)` — L285 | Single-entry delete. |
| | | `deleteEmployeeMonthConsumptionApi(employeeId, year, month)` — L299 | Month delete. |
| | | `applyMarkLeaveCardsUpdates(cards, month, year)` — L427 | Merges saved cards back into the cache so the UI updates without a refetch. |

### Code flow — the screens

| Folder | File | Function | What it does |
|---|---|---|---|
| `leaveManagement/leaveAllocation/` | `leaveAllocation.jsx` (2290) | `LeaveAllocation` — L89 | The allocation grid. |
| | | `fetchAllocations(page, size, search)` — L282 | Paged server load with debounced search (`useEffect` debounce at L120). |
| | | `fetchActiveEmployees()` — L265 | Candidate employees for the add modal. |
| | | `getAllocationSortScore(item)` L58 / `compareAllocationsDesc(a,b)` L80 | Newest-first ordering. |
| | | `handleOpenAddModal` L378 / `handleConfirmAddEmployees` L386 | Employee-picker modal. |
| | | `handleAddLeaveType(employeeId)` L494 / `handleRemoveLeaveType` L521 / `handleUpdateLeaveType(...)` L541 | Per-row editing. |
| | | `handleSaveAll()` — L569 | The bulk save. |
| | | `handleImportAllocations()` — L152 | `POST /api/leave-allocation/import` (multipart); renders per-row errors. |
| | | `downloadSampleFile(type)` — L197 | Generates the CSV/Excel template client-side. |
| | | `handleResetImport()` — L146 | Clears the import panel. |
| | | `handleDeleteSavedEmployee(id, name)` — L703 | Delete an employee's allocation. |
| | | `formatRecordForEdit(record)` L431 / `handleEditSelectedEmployees()` L464 | Load saved rows back into the editable form. |
| `markLeaves/` | `markLeavesOverviewPage.jsx` (1049) | `MarkLeavesOverviewPage` — L69 | Flat entry list. |
| | | `loadEmployees(year)` — L94 (useCallback) | Via `leaveStore`. |
| | | `displayRows` (useMemo) — L134 | Flattens employee → leave-type → month entries into table rows. |
| | | `filteredRows` L307 / `departments` L334 | Filtering. |
| | | `handleDeleteClick(row)` — L339 | Branches between entry delete and whole-record delete. |
| | | `handleStorageChange` L114 / `handleCustomUpdate` L119 | Listens to the `leaveStore` events so the page live-updates. |
| | `markLeavesTakenPage.jsx` (1348) | `MarkLeavesTakenPage` — L49 | Employee cards with balances. |
| | | `computeEmployeeSummary(employee)` — L132 | Totals allocated / consumed / balance / LOP; flags fully- and partly-expired allocations. |
| | | `handleToggleSelectEmployee` L220 / `handleToggleSelectAll` L212 | Multi-select. |
| | | `handleNavigateToMarkLeave()` — L227 | Passes the selection to the entry form via router state; warns when every selected allocation is expired. |
| | | `getStatusBadge(status, type)` — L266 | Badge rendering. |
| | | `handleOpenReviewModal(employee)` — L121 | Per-employee detail modal. |
| | `markLeaveAddEmploy.jsx` (1327) | `MarkLeaveAddEmploy` — L216 | **The leave entry form.** |
| | | **`computeMonthBalanceState(b, cardMonth, newDaysTaken, isEdit)` — L67** | The module's core rule: given the allocation and every *other* month's consumption, works out this month's available quota, the resulting balance, and how much spills into **LOP**. Exported, so the other leave screens reuse it. |
| | | `buildCardForEmployee(emp, month, year, isEdit, entryId, leaveType)` — L112 | Builds one employee card, in create or edit mode. |
| | | `handleDaysTakenChange(cardId, rowId, val)` — L365 | Recomputes balance/LOP on every keystroke. |
| | | `handleLwpChange(...)` — L408 | Explicit leave-without-pay entry. |
| | | `handleCardMonthChange(cardId, monthName)` — L283 | Switching month re-derives the whole card. |
| | | `handleReasonChange` L430, `toggleCardCollapse` L353, `handleRemoveEmployeeCard` L360 | Card interactions. |
| | | `handleOpenAddEmployeeModal` L443 / `handleToggleModalEmployee` L449 / `handleSelectAllModal` L455 / `handleConfirmAddEmployees` L465 | Add more employees mid-form. |
| | | `MONTH_NAMES` — L51 (exported) | Shared month list. |
| `leaveManagement/markLeaveTaken/` | `markLeaveTaken.jsx` (29) | `MarkLeaveTaken` | Thin wrapper — routed at `/mark-leave-taken`. |
| `allSettingsPages/leaveAttendence/` | `leaveAttendenceSetup.js` (254) | `LeaveAttendanceSetup` | Leave-module onboarding checklist. |
| | `leaveTypes.js` (280) | `LeaveTypes` | Leave-type list. |
| | `addLeaveTypes.js` (968) | `AddLeaveType` | Create — accrual, carry-forward, encashment rules. |
| | `editLeaveType.js` (1055) | `EditLeaveType` | Edit. |
| | `holidays.js` (366), `addHoliday.js` (320) | `Holidays`, `AddHoliday` | Holiday calendar. |
| | `attendence.js` (631) *(sic)* | `Attendance` | Attendance screen. |
| | `attendencePreferences.js` (618) *(sic)* | `AttendancePreferences` | Attendance preferences. |
| | `importLeaveBalance.js` (385) | `ImportLeaveBalance` | The **older** leave-balance import (`/api/employee-leave-imports`). |

**Backend:** module 8.

> ⚠️ Two things to know before changing this module:
> 1. `leaveStore.js` keeps a **localStorage mirror** of server state keyed by year. If the cache
>    version (`_v6`) is not bumped when the record shape changes, users see stale cards.
> 2. Six routes point at only three components: `/mark-leaves-taken` and `/mark-leave-taken` both
>    render `MarkLeavesTakenPage`; `/markleaveaddemploy` and `/mark-leave-add-employee` both render
>    `MarkLeaveAddEmploy`. Duplicate aliases from the merge — pick the kebab-case ones.

---

## 9. Employee Deductions

| Folder | File | Function | What it does |
|---|---|---|---|
| `pages/mainPages/deduction/` | `deduction.js` (622) | `EmployeeDeductionList` | Admin list at `/employee-deductions`. |
| | | `fetchDeductions(page, size)` — L159 | `GET /api/employee-deductions` (paged; guards against invalid page/size). |
| | | `fetchEmployees()` — L211 | Employee dropdown for the edit modal. |
| | | `handleEditClick(record)` L57 / `handleEditSubmit(values)` L120 | Inline edit → `PUT /{id}`. |
| | | `handleDelete(id)` — L39 | `DELETE /{id}`. |
| | | `handleUpload({file, onSuccess, onError})` — L83 | Proof upload → `POST /api/employee-deductions/upload`. |
| | | `handlePageChange(page, size)` — L204, `formatCurrency(amount)` — L244 | Table plumbing. |
| | `gridDeduction.js` (561) | `GridDeduction` | The bulk-add grid at `/employee-deductions/bulk-add`. |
| | | `DEDUCTION_TYPES` — L22 | The type catalogue rendered in each row. |
| | | `addEmployeeBlock()` L85 / `removeEmployeeBlock(key)` L97 / `setBlockEmployee(key, id)` L102 | One "block" per employee. |
| | | `addRow(blockKey)` L109 / `removeRow(...)` L118 / `updateRow(blockKey, rowKey, field, value)` L129 | Multiple deduction rows per employee. |
| | | `createEmptyRow()` — L72 | Row factory. |
| | | `beforeUploadProof(file, block)` — L145 | Client-side validation: type whitelist + 5 MB cap. |
| | | `handleProofUpload(blockKey, rowKey, employeeId, file)` — L170 | Uploads and stores the returned URL on the row. |
| | | `removeProof(blockKey, rowKey)` — L217 | Detach a proof. |
| | | `handleSubmit()` — L236 | Flattens every block/row into one `POST /api/employee-deductions/grid`. |
| | | `disabledDate(current)` — L322 | Blocks back-dating before the current month. |
| `userPortal/myDeductions/` | `myDeductions.jsx` (335) | `MyDeductions` | Employee view at `/my-deductions`. |
| | | `fetchMyDeductions()` — L22 | `GET /api/employee-deductions/my-deductions`. |
| | | `filteredDeductions` L58, `totalAmount` L72, `activeCount` L76, `processedCount` L80 | Summary tiles. |

**Backend:** module 9.

---

## 10. Reimbursement Claims

| Folder | File | Function | What it does |
|---|---|---|---|
| `userPortal/Reimbursement/` | `ReimbursementPage.jsx` (247) | `ReimbursementPage` | Employee claims screen at `/reimbursement`. |
| | `ReimbursementTable.jsx` (638) | `ReimbursementTable` | Claim list with status badges. |
| | `ApplyReimbursementModal.jsx` (566) | `ApplyReimbursementModal` | The claim form + receipt upload (multipart). |
| | `reimbursementValidation.js` (89) | (Yup schema) | Claim validation rules. |
| | `reimbursementService.js` (77) | — | **Duplicate** of `shared/services/reimbursementService.js`; the shared one is canonical. |
| | `mockReimbursements.js` (70) | — | Mock data left in the tree. |
| `adminReimbursement/` | `AdminReimbursementPage.jsx` (385) | `AdminReimbursementPage` | Admin queue at `/admin-reimbursements`. |
| | `AdminReimbursementTable.jsx` (1260) | `AdminReimbursementTable` | Review table with approve/reject actions and the approved-amount input. |
| | `mockAdminReimbursements.js` (132) | — | Mock data **still imported by `AdminReimbursementPage.jsx`** — verify it is only a fallback before removing. |
| `shared/services/` | `reimbursementService.js` | `getEmployeeReimbursements`, `createEmployeeReimbursement` (handles both `FormData` and JSON), `getAdminReimbursements`, `approveAdminReimbursement`, `rejectAdminReimbursement` | The canonical API layer. |
| `shared/components/reimbursement/` | — | — | Shared widgets. |

**Backend:** module 10. Deeper notes in `REIMBURSEMENT_FRONTEND_DOCUMENTATION.md` at the repo root.

---

## 11. Pay Run

### Functional flows

```
/payruns                  list + create           payRuns/index.js
   │
   ▼
/preview/:payrunId        review, add/remove       payRuns/preview.js
   │                      employees, then approve
   ▼
/summary/:payrunId        totals, reject,          payRuns/summary.js
   │                      record payment, export
   ▼
/view-payslip/:payrunId/:employeeId                payRuns/viewPayslip.js
/public/payslips/:payrunId/:employeeId             userPortal/publicPayslipDownload.js
```

Two side flows: **one-time payout** (`/addOneTimePayoutDetails`, + `/import`) and
**off-cycle payrun** (`/addOffCycleDetails`).

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `pages/mainPages/payRuns/` | `index.js` (1202) | `PayRuns` | The payrun list. |
| | | `fetchPayRuns()` — L112 | `GET /api/payruns` — current + upcoming. |
| | | `fetchPayrollHistory()` — L196 | `GET /api/payruns/completed`. |
| | | `handleCreatePayrun(payRunData, payRun)` — L522 | `POST /api/payruns` then navigates to preview. |
| | | `handleOneTimePayoutSubmit(...)` — L395 / `handleOffCyclePayrunSubmit(...)` — L459 | The two side-flow creators, each with its own Yup schema (L381, L388). |
| | | `downloadPayrunReport(payrunId)` — L243 | `GET /api/payruns/{id}/report/excel`. |
| | | `getPayrollTypeDisplayName(type)` L314 / `getBackendPayrollType(name)` L331 | Two-way mapping between UI labels and backend enum values — **duplicated in `dashboardPage/index.js`**. |
| | | `getStatusColor(status)` L602, `formatCurrency` L621, `formatDate` L625, `isDueToday(date)` L631 | Presentation. |
| | | `PayRunCard({payRun})` — L724 | The card component. |
| | | `payrollHistoryColumns` L638, `onTabChange(key)` L992 | Tabs. |
| | `preview.js` (1374) | `Preview` | Pre-approval review. |
| | | `fetchPayRunData()` — L146 | Loads the payrun + its employee lines. |
| | | `handleSubmitAndApprove()` — L397 | `PUT /api/payruns/{id}/approve`. |
| | | `handleAddEmployeeSubmit(...)` — L270 | Add an employee to the run. |
| | | `handleRemoveEmployee` L446 / `confirmRemoveEmployee` L451 | Remove one. |
| | | `handleImportEmployees()` — L466 | Bulk add. |
| | | `handleDeletePayrun()` — L354 | Delete the draft. |
| | | `handleDownload()` — L349 | Export. |
| | | `getEmployeeActionItems(employee)` — L322, `rowSelection` L476, `componentBreakdown` L518, `tabList` L539 | Grid + tabs. |
| | `summary.js` (1712) | `Summary` | Post-approval summary. |
| | | `fetchPayRunData()` — L158 | Same load, approved state. |
| | | `handleRecordPayment()` L495 / `handleConfirmRecordPayment()` L499 | `POST /api/payruns/{id}/payment`. |
| | | `handleRejectPayroll()` L444 / `handleConfirmReject()` L448 | `PUT /{id}/reject`. |
| | | `handleDownload()` — L370 | Excel / bank CSV. |
| | | `taxDetails` L624, `benefitsData` L634, `componentBreakdown` L653 | The summary breakdown panels. |
| | `viewPayslip.js` (609) | `ViewPayslip` — L13 | The payslip render. |
| | | `fetchPayslipData()` — L28 | `GET /api/payrun-employees/{payrunId}/{employeeId}`. |
| | | `getAmountInWords(amount)` — L82 (with nested `convertToWords`) | Indian numbering → words for the payslip footer. |
| | | `epfContribution` L173, `professionalTaxFromBackend` L181, `monthlyTdsFromBackend` L182, `lopAmount` L195, `hasLOP` L201, `claimDeduction/claimReimbursement` L186–187 | Pulls each figure from the backend payload, with fallbacks to `payrollSummary.*`. |
| | | `getFullAddress(location)` — L203, `companyLogo` L219 | Payslip header from the org profile. |
| | `addOneTimePayoutDetails.js` (423) | `AddOneTimePayoutDetails` | One-time payout builder. |
| | | `getEmployeeOptions()` L45, `handleAddEmployeeSubmit(...)` L108, `handleDeleteEmployee(key)` L142 | Employee selection. |
| | `importOneTimePayrunData.js` (256) | `ImportOneTimePayrunData` | Dropzone CSV import (`Dropzone` init at L43). |
| | `addOffCycleDetails.js` (670) | `AddOffCycleDetails` | Off-cycle run builder. |
| | | `handleAddLopAdjustment` L180 / `handleRemoveLopAdjustment` L184 / `handleLopAdjustmentChange` L188 | Manual LOP adjustments. |
| | | `handleAddEarning` L194 / `handleRemoveEarning` L198 / `handleEarningChange` L202 | Ad-hoc earnings. |
| `userPortal/` | `payslipGenerator.js` (182) | `PayslipGenerator` | Employee's own payslip at `/payslips/:payrunId`. |
| | `publicPayslipDownload.js` (179) | `PublicPayslipDownload` | Tokenised public payslip — the only unauthenticated data screen. |
| | `components/payslipDocument.js` (487) | `PayslipDocument` | The shared printable payslip body. |

**Backend:** module 11.

---

## 12. Tax & Forms

| Folder | File | Function | What it does |
|---|---|---|---|
| `taxesAndForms/` | `taxCalculator.js` (454) | `TaxCalculator` | Admin tax calculator at `/tax-calculator`. |
| `taxesAndForms/form16/` | `index.js` (286) | `Form16` | Form 16 list at `/form16s`. |
| | `generateForm16.js` (226) | `GenerateForm16` | Generation form. |
| `allSettingsPages/taxes/` | `taxDetails.js` (957) | `TaxDetails` | Org income-tax details (TAN/PAN/TDS circle) — routed at both `tax-details` and `taxes`. |
| `allSettingsPages/claimsAndDeclaration/` | `taxSlabRegime.js` (518) | (tax-regime editor) | Slab/regime maintenance → `/api/tax-slabs`. |
| `userPortal/taxation/` | `userTaxCalculator.js` (454) | `UserTaxCalculator` | Employee-side calculator. |
| `userPortal/userInvestment/` | `compareTaxRegimes.js` (854) | `CompareTaxRegimes` | Old vs new regime side by side. |
| `employee/` | `adminCompareTaxRegime.js` (862) | `AdminCompareTaxRegimes` | The admin's copy of the same comparison. |

**Backend:** module 12.

---

## 13. IT Declaration & POI

> The largest module by line count and the one with the most duplication: the employee-side and
> admin-side screens are near-identical copies.

### Functional flows

1. **Org settings** — declaration window and POI window (`allSettingsPages/claimsAndDeclaration/`).
2. **Employee declares** — `/user-investment-declaration` (2589 lines) — one section per tax head.
3. **Employee uploads proofs** — `/user-investment/proof`, `/user-poi`, `/edit-user-poi`.
4. **Admin dashboard** — `/proof-of-investment` — who has submitted, filter by FY and tax regime.
5. **Admin reviews** — `/proof-of-investment/approval-view/:id` — per-item approve/reject with comments, then final approve and "consider for IT".
6. **Chasing** — `/proof-of-investment/unsubmitted-list` — send reminders.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `allSettingsPages/claimsAndDeclaration/` | `itDeclaration.js` (1060) | `ITDeclaration` | Declaration-window settings → `/api/income-tax-declarations`. |
| | `proofOfInvestment.js` (1051) | `ProofOfInvestmentSettings` | POI-window + reminder settings → `/api/proof-of-investment`. |
| `pages/mainPages/approval/` | `proofOfInvestment.js` (683) | `ProofOfInvestment` | The admin dashboard. |
| | | `fetchPOIData(financialYear)` — L130 | `GET /api/admin/proof-of-investments/dashboard`. |
| | | `getDeclarationFinancialYear()` L47 / `generateFinancialYears()` L63 / `formatFinancialYear(y)` L58 | India FY handling (Apr–Mar) — reimplemented in several files. |
| | | `mapFrontendStatusToBackend(s)` L255 / `mapBackendStatusToFrontend(s)` L269 | Status vocabulary differs between UI and API. |
| | | `getTaxRegimeFromResponse(item)` L288 / `handleTaxRegimeChange(v)` L304 | Regime filter. |
| | | `handleEmployeeClick(employee)` — L330 | Opens the review screen. |
| | | `handleReleasePOI()` L346, `handleDownload(doc)` L351, `handlePreview(doc)` L362 | Release window, download and preview proofs. |
| | | `navigateToUnsubmitted()` — L372 | To the chase list. |
| | `approvalView.js` (1467) | `ApprovalView` | **The review screen.** |
| | | `fetchEmployeePOI()` — L141 | `GET /api/admin/proof-of-investments/{org}/{emp}/{fy}`. |
| | | `handleApprove(investmentId)` — L409 / `handleReject(investmentId)` — L473 | Per-item decisions. |
| | | `handleApproveAll()` — L518 / `handleApproveAllConfirm()` — L111 | Bulk approve. |
| | | `handleEditAmount(inv)` L319 / `handleSaveAmount(id)` L325 / `handleRemoveAmount(id)` L378 | Setting the approved amount. |
| | | `handleConsiderForITConfirm()` — L124 | `POST .../consider-for-it` — pushes approved amounts into the tax engine. |
| | | `fetchComments(poiItemId)` — L235 | The per-item comment thread. |
| | | `mapStatus(backendStatus)` L296, `getApiErrorMessage(err, fallback)` L93, `getCurrentFinancialYear()` L52 | Helpers. |
| | `unsubmittedList.js` (299) | `UnsubmittedList` | Chase list. |
| | | `handleSelectAll(e)` L60 / `handleEmployeeSelect(id, checked)` L69 / `handleSendReminder()` L84 | Bulk reminder send. |
| `pages/mainPages/employee/` | `investmentDeclaration.js` (1085) | `InvestmentDeclaration` | Admin view of an employee's declaration. |
| | `adminInvestmentDeclaration.js` (2611) | `AdminInvestmentDeclaration` | Full admin declaration editor (tab `investments-and-proofs/declaration`). |
| | `adminInvestmentProofTab.js` (1327) | `AdminInvestmentProofTab` | Admin proof tab. |
| | `adminProofEdit.js` (2282) | `AdminProofEdit` | Admin proof editor. |
| | `InvestmentsTab.js` (1170) | `InvestmentsTab` / `AdminInvestment` | Investments overview tab. |
| `userPortal/userInvestment/` | `userInvestmentDeclaration.js` (2589) | `UserInvestmentDeclaration` | Employee declaration form — the counterpart of `adminInvestmentDeclaration.js`. |
| | `userInvestmentProofTab.js` (1328) | `UserInvestmentProof` | Counterpart of `adminInvestmentProofTab.js`. |
| | `userProofEdit.js` (2282) | `UserProofEdit` | **Byte-identical size to `adminProofEdit.js`** — the same file, forked. |
| | `index.js` (1173) | `UserInvestment` | Read-only summary; contains the section renderers `HouseRentDetails` L33, `HomeLoanDetails` L138, `Section80CInvestments` L209, `Section80DExemptions` L268, `OtherInvestments` L327 — each reading its limit from `section6aItems` (the backend rule master) rather than hard-coding it. |
| `userPortal/taxation/` | `userPOI.js` (708) | `UserPOI` | The older POI screen (`/user-poi`). |
| | `userPOIView.js` (641) | `OverviewPOI` | Read-only overview. |
| | `editUserPOI.js` (349) | `EditUserPOI` | Edit. |

**Backend:** module 13.

> ⚠️ Six admin/employee file pairs are forks of each other
> (`adminProofEdit.js` ↔ `userProofEdit.js`, `adminInvestmentProofTab.js` ↔ `userInvestmentProofTab.js`,
> `adminInvestmentDeclaration.js` ↔ `userInvestmentDeclaration.js`, `adminCompareTaxRegime.js` ↔ `compareTaxRegimes.js`).
> Roughly 12,000 lines duplicated. Any fix to declaration logic needs applying twice.

---

## 14. Dashboard

| Folder | File | Function | What it does |
|---|---|---|---|
| `dashboardPage/` | `index.js` (884) | `DashboardPage` | The admin dashboard at `/dashboard`. |
| | | `fetchPayRunsForDashboard()` — L115 | Payrun tiles. |
| | | `handleCreatePayrun(payRunData)` — L197 | Create straight from the dashboard. |
| | | `PayRunCard({payRun})` — L248 | Card component — **duplicated from `payRuns/index.js`**. |
| | | `getPayrollTypeDisplayName` L48 / `getBackendPayrollType` L62 / `getStatusColor` L76 / `formatCurrency` L95 / `formatDate` L101 / `isDueToday` L107 | All six also duplicated from `payRuns/index.js`. |
| | | `stats` L15, `recentPayRuns` L25, `upcomingPayments` L31 | **Hard-coded placeholder data** — these tiles are not wired to `/api/dashboard/summary`. |
| | `onboardingDashboard.js` (546) | `OnboardingDashboard` | The setup checklist shown until onboarding completes. |
| | `dashboardcopy.js` (2738) | — | **Dead code.** Not imported by `router.js`. This is the file behind the "271KB dashboard" note in `PROJECT_OVERVIEW.md`; the live dashboard is now 884 lines. |

**Backend:** module 14 — note `DashboardController` exposes `summary`, `statutory-summary` and
`tds-summary`, but the frontend currently only consumes payrun data here.

---

## 15. Employee Self-Service Portal

| Folder | File | Function | What it does |
|---|---|---|---|
| `userPortal/` | `home.js` (205) | `Home` | Portal landing at `/home`. |
| | `userProfile.js` (572) | `MyProfile` | `/userProfile` — reads `/api/employees-portal/employee-profile`. |
| | `userSalaryDetails.js` (962) | `MySalaryDetails` | `/user-salary-details` — reads `/api/v1/ctc-structures/by-email`. |
| | `ChangePassword.js` (254) | `ChangePassword` | `/change-password` via Keycloak. |
| | `payslipGenerator.js`, `publicPayslipDownload.js`, `components/payslipDocument.js` | see §11 | Payslips. |
| | `myDeductions/myDeductions.jsx` | see §9 | My deductions. |
| | `Reimbursement/*` | see §10 | My claims. |
| | `userInvestment/*`, `taxation/*` | see §13 | Declarations, proofs, tax calculator. |
| `userProfileSettings/` | `yourAccount.js` (202) | `YourAccount` | Account settings shell. |
| | `Tabs/editProfileDetailsTab.js` (214) | `EditProfileDetailsTab` | Profile tab. |
| | `Tabs/editsecurityDetailsTab.js` (179) | `EditSecurityDetailsTab` | Security tab. |
| `pageLayouts/employeeLayout/` | `employeeSidebar.js` (238) | `EmployeeSidebar` | The portal's `menuLinks[]` — the authoritative list of what an employee can reach. |

---

## Appendix A — Known rough edges

| Where | Issue |
|---|---|
| `dashboardPage/dashboardcopy.js` (2738) | Dead file, not routed. Safe to delete. |
| `employee/addEmpOld.js` (2441) | Dead file — the pre-wizard add-employee form. |
| `employee/editEmployee.js` | Zero bytes. |
| `pageLayouts/dashboardLayout/header-ols.js` (441) | Superseded by `header.js`. |
| `adminReimbursement/mockAdminReimbursements.js` | **Still imported** by `AdminReimbursementPage.jsx` — mock data on a live screen. Check what it is used for before deleting. |
| `userPortal/Reimbursement/mockReimbursements.js` | Dead — not imported anywhere. |
| `userPortal/Reimbursement/reimbursementService.js` | Duplicate of `shared/services/reimbursementService.js`. |
| Admin ↔ employee POI screens | ~12,000 duplicated lines across six file pairs (§13). |
| CTC calculation engine | Duplicated across three files (§5). |
| Payrun helpers | `getPayrollTypeDisplayName`, `getBackendPayrollType`, `getStatusColor`, `formatCurrency`, `formatDate`, `isDueToday`, `PayRunCard` duplicated between `payRuns/index.js` and `dashboardPage/index.js`. |
| Financial-year helpers | `getCurrentFinancialYear` / `getDeclarationFinancialYear` reimplemented in at least five files. |
| Route aliases | `/mark-leaves-taken` = `/mark-leave-taken`; `/markleaveaddemploy` = `/mark-leave-add-employee`; `tax-details` = `taxes`. |
| Auth token in `localStorage` | `__t` is read directly in ~100 files rather than going through the interceptor. |
| Folder spelling | `leaveAttendence`, `attendence.js`, `benifits.js`, `salaryRevisonDetails.js` — grep with care. |
| Tests | None. `react-scripts test` has nothing to run. |

## Appendix B — Tracing a feature end to end (worked example)

**"Where does the LOP figure on a payslip come from?"**

1. `pages/pageLayouts/router.js` → `/view-payslip/:payrunId/:employeeId` → `payRuns/viewPayslip.js`.
2. In `viewPayslip.js`, `lopAmount` (L195) reads `lop` from the response of `fetchPayslipData()` (L28).
3. That call hits `GET /api/payrun-employees/{payrunId}/{employeeId}`.
4. Backend: `controller/payruns/EmployeePayRunController.getPayslip` (L137) → `EmployeePayRunServiceImpl.getEmployeePayslip` (L1450).
5. The stored value came from `mapToPayRunDTO` (L211), which reads LOP days from `employee_leave_balance_consumption`.
6. Those rows are written by `serviceimpl/leave/EmployeeLeaveConsumptionServiceImpl.updateEmployeeConsumption` (L43) — i.e. by the **Mark Leave Taken** screen (`markLeaves/markLeaveAddEmploy.jsx`), where `computeMonthBalanceState` (L67) decided how many days spill into LOP.
