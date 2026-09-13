# Code Map — Payroll Backend (`Payroll-Bend-SBoot`)

> Branch: `taxation` · HEAD `39b37d6` (2026-09-09)
> Java 17 · Spring Boot 3.2.5 · MySQL `payrollDB` · Keycloak OAuth2 · Port 3032 (dev) / 3029 (prod)
> 597 Java files · ~82k LOC · 61 controllers · 78 repositories · 99 entities · 92 tables
>
> Purpose of this document: understand **what each module does**, **which files carry the flow**,
> and **what each significant function is for** — so you can trace any feature end to end.

---

## 0. How to read this codebase

### 0.1 The one layering rule

Every feature follows the same five-hop path. Learn it once and every module reads the same way:

```
HTTP request
   │
   ▼
controller/<domain>/XController.java      ← @RestController, URL mapping, reads @RequestHeader("organizationId")
   │                                        wraps everything in ResponseEntity<Map<String,Object>>
   ▼
service/<domain>/XService.java            ← interface only (the contract; no logic)
   │
   ▼
serviceimpl/<domain>/XServiceImpl.java    ← ALL business logic lives here
   │
   ├─► mapper/<domain>/XMapper.java        ← entity ⇄ DTO conversion
   ├─► specs/BasicDetailsSpecs.java        ← dynamic JPA filters (only used for employee search)
   ▼
repository/<domain>/XRepository.java       ← Spring Data JPA
   │
   ▼
entity/<domain>/X.java                     ← @Entity → table in payrollDB
```

**Rule of thumb:** if you are looking for logic, open `serviceimpl/`. The controller is almost always
a thin pass-through; the service interface is almost always empty of meaning.

### 0.2 Conventions you will see everywhere

| Convention | What it means | Where it lives |
|---|---|---|
| `@RequestHeader("organizationId")` | Multi-tenancy. **Every** business endpoint is scoped to an org; there is no global data. | Every controller |
| `Map<String,Object>` response envelope | `{ statusCode, message, data }` hand-rolled per controller — not a shared wrapper class. | Every controller |
| `generateUniqueXxxId()` | Business IDs are random 10/12-digit strings, not DB PKs. DB PK (`Long id`) and business ID (`String organizationId`, `payrunId`, `employeeId`) coexist on most entities. | Each `*ServiceImpl` |
| `createDefaultXxx(org)` | Settings entities self-create with defaults on first GET rather than failing. | `claimsanddeclarations/*ServiceImpl` |
| Commented-out old controller | Several controllers keep the previous version commented above the live one (e.g. `BasicDetailsController` L22–L155). **Read from the bottom.** | `employee/`, `salarycomponents/` |
| `ddl-auto=update` | No migrations. Schema comes from `@Entity` annotations. Change an entity → the column appears on next boot. | `application.properties` |

### 0.3 Cross-cutting infrastructure

| Folder | File | Purpose |
|---|---|---|
| `config/` | `SecurityConfig.java` | Keycloak OAuth2 resource-server setup; route-level `permitAll` for `/api/public/**` and `/auth/**`. |
| `config/` | `OrganizationRoleInterceptor.java` | `preHandle()` — resolves the caller's role in the `organizationId` header before the controller runs. |
| `config/` | `KeycloakAdminConfig.java`, `KeycloakClientProvider.java` | Admin-API client used to create/enable/disable users in Keycloak. |
| `config/` | `WebClientConfig.java` | Non-blocking HTTP client — used **only** for the HRMS LOP call. |
| `config/` | `CloudinaryConfig.java` | Document/proof/logo storage credentials. |
| `config/` | `MasterDataInitializer.java` | Seeds tax rule masters (`section6a_item_master`, slab masters, etc.) at boot. |
| `config/` | `EmailTemplateConfig.java` | Maps `brevo.template.*` property keys to transactional email template IDs. |
| `scheduler/` | `ITDeclarationAutoLockScheduler.java` | Cron job — locks IT declaration windows and emails employees. |
| `scheduler/` | `POIReminderScheduler.java` | Cron job — chases employees who have not submitted proofs. |
| `exception/` | `GlobalExceptionHandler.java` | `@RestControllerAdvice` — turns exceptions into the same `Map` envelope. |
| `util/` | `HashUtil.java` | MD5 — used for the HRMS cross-service API key. |
| `util/` | `MonthUtil.java` | Pay-period ("Jan 2026") ⇄ `YearMonth` parsing used all over the payrun code. |
| `util/` | `ProfessionalTaxUtil.java`, `BenefitUtility.java` | PT slab resolution; benefit calculation helpers. |
| `util/` | `PasswordGeneratorUtil.java` | Temp passwords for invited users. |
| `event/` | `SalaryRevisionActivatedEvent.java` | Spring event — fired when a revision goes live so TDS can recalculate. |

### 0.4 Fastest way to trace any feature

1. Find the screen's URL in the frontend (`Payroll-Fend-react/src/pages/pageLayouts/router.js`).
2. Grep the screen file for `GlobalConst.API_URL` to get the endpoint path.
3. Grep the backend for that path: `grep -rn '"/api/<path>"' src/main/java --include=*Controller*.java`
4. From the controller, follow the injected service field into `serviceimpl/`.
5. In the `ServiceImpl`, the repository calls tell you which tables are touched.

---

## Module index

| # | Module | Primary flow | Tables |
|---|---|---|---|
| 1 | [Auth, Signup & Invitations](#1-auth-signup--invitations) | Register org admin → Keycloak user → invite users/employees → accept | `companyUser`, `userInvitations`, `employeeInvitation` |
| 2 | [Organization Setup](#2-organization-setup) | Create org → head office → work locations, departments, designations → pay schedule | `organization`, `workLocations`, `department`, `designation`, `paySchedule`, `orgSetupSteps` |
| 3 | [Users, Roles & Permissions](#3-users-roles--permissions) | Create role → assign actions → map user to role → authorize each call | `organizationRole`, `action`, `organization_role_action`, `organizationUserRoleMapping` |
| 4 | [Employee Management](#4-employee-management) | Basic → personal → payment → salary wizard, plus bulk import & portal access | `employee`, `employee_personal_detail`, `employee_bank_detail` |
| 5 | [CTC & Salary Revision](#5-ctc--salary-revision) | Build CTC structure → revise → approve → apply in payrun → recalc TDS | `ctc_structure`, `ctc_epf_components`, `ctc_esi_components` |
| 6 | [Salary Components](#6-salary-components) | Define org-level earnings/deductions/benefits/reimbursements → attach to employees | `earnings`, `deductions`, `benefits`, `reimbursements`, `employee_*` |
| 7 | [Statutory Components](#7-statutory-components) | Configure EPF / ESI / Professional Tax → consumed by payrun | `epf`, `esi`, `professionalTax`, `slabDetail`, `pt_history` |
| 8 | [Leave Management](#8-leave-management) | Allocate leave → mark leave taken → derive LOP → feed payrun | `employee_leave_allocation`, `employee_leave_balance_consumption`, `leave_type`, `holidays` |
| 9 | [Employee Deductions](#9-employee-deductions) | Grid-add ad-hoc deductions + proof → approve → subtract in payrun | `employee_deduction` |
| 10 | [Reimbursement Claims](#10-reimbursement-claims) | Employee claims with attachment → admin approves → paid in payrun | `employee_reimbursement_request` |
| 11 | [Pay Run](#11-pay-run) | Create payrun → generate per-employee lines → preview → approve → pay → payslips | `payruns`, `employee_payruns`, `off_cycle_pay_run`, `one_time_payout` |
| 12 | [Tax Engine](#12-tax-engine) | Old/new regime computation, slab masters, TDS scheduling | `taxSlabMaster`, `slabDetail`, `employee_tds`, `employee_tax_calculation_result` |
| 13 | [IT Declaration & Proof of Investment](#13-it-declaration--proof-of-investment) | Declare → upload proofs → admin item-level approve → consider for IT | `employee_investment_declaration`, `employee_poi_item`, `employee_poi_document` |
| 14 | [Dashboard & Reports](#14-dashboard--reports) | Org summary tiles, statutory/TDS summaries, Excel & CSV exports | (reads across modules) |
| 15 | [HRMS Integration](#15-hrms-integration) | Payroll pulls LOP days from HRMS at payrun time | — |

---

## 1. Auth, Signup & Invitations

### Functional flows

1. **Org-admin signup** — `POST /auth/register` creates a `CompanyUser`, provisions the same user in Keycloak, generates an `organizationId`, and lands the user on the org-setup wizard.
2. **Invite an admin user** — admin invites by email → a *temporary* Keycloak user + `userInvitations` row → invitee receives a link → accepts → temporary user is upgraded to a full account.
3. **Invite an employee to the self-service portal** — separate track using `employeeInvitation`, gated by a per-employee portal-access toggle.
4. **Forgot / update password** — handled against Keycloak, not the local DB.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/` | `CompanyUserController.java` | `registerUser(CompanyUserDTO)` | `POST /auth/register` — entry point for org-admin signup. |
| | | `registerOrganizationUser(...)` | `POST /auth/organization-user/register` — creates an admin user inside an existing org. |
| | | `forgotPassword(email)` | `POST /auth/forgot-password` — triggers the Keycloak reset mail. |
| | | `updatePassword(email, ...)` | `POST /auth/update-password` — sets a new password via Keycloak Admin API. |
| | | `isEmailExists(email)` / `isTemporaryUser(email)` | Pre-flight checks the login screen uses to branch between "sign in" and "finish setup". |
| | | `toggleEmployeePortalAccess(...)` | Enables/disables an employee's portal login. |
| | | `getHrUsers(...)` | Lists HR-role users — populates "Reporting to" dropdowns. |
| `serviceimpl/` | `CompanyUserServiceImpl.java` | `registerUser(dto)` — L117 | Validates email uniqueness, generates `organizationId`, creates the Keycloak user, persists `CompanyUser`, seeds a super-admin invitation. |
| | | `generateUniqueOrganizationId()` — L108 | Random ID, re-rolled until unused. |
| | | `organizationUserRegistration(UserInvitationDTO)` — L237 | Converts an accepted invitation into a real user; upgrades the temporary Keycloak account. |
| | | `assignOrUpdateRoleToUserInOrganization(dto)` — L334 | Writes/updates `organizationUserRoleMapping`. |
| | | `organizationEmployeeRegistration(BasicDetailsDTO)` — L545 | Creates the Keycloak account when an employee is granted portal access. |
| | | `toggleEmployeePortalAccess(dto)` — L679 | Flips `enabled` in Keycloak **and** the local flag together. |
| | | `getHrUsersByOrganization(orgId)` — L841 | HR-role user lookup. |
| `serviceimpl/keycloak/` | `KeycloakUserServiceImpl.java` | `createUserInKeycloak(dto)` — L101 | Admin-API create for a full user. |
| | | `createTemporaryUserInKeycloak(dto)` — L292 | Create for an invitee who has not accepted yet. |
| | | `upgradeTemporaryUserInKeycloak(dto)` — L384 | Promotes temporary → permanent on acceptance. |
| | | `createEmployeeUserInKeycloak(dto)` — L439 | Portal account for an employee. |
| | | `isTemporaryUser(email)` / `checkEmailInKeycloak(email)` | Identity lookups the login flow depends on. |
| | | `setUserEnabled(email, bool)` — L505 | Backs activate/deactivate across the app. |
| | | `getLastLoginTime(userId)` — L61 | Feeds the "Last login" column on the Users screen. |
| | | `updateUserPassword` / `updateUserEmail` | Credential maintenance. |
| `serviceimpl/organization/` | `UserInvitationServiceImpl.java` | `createInvitation(orgId, dto)` — L104 | Creates the invite row, the temp Keycloak user, and sends the Brevo email. |
| | | `getAllUsersWithLoginInfo(orgId, currentUserId)` — L365 | Joins invitations + Keycloak last-login into the Users grid model. |
| | | `createSuperAdminInvitation(...)` — L421 | Auto-invite for the org creator. |
| | | `markAsAccepted(...)` / `markAsRejected(...)` | Two overloads each — by (email, org) and by acceptance token. |
| | | `inactivateInvitation` / `reactivateInvitation` | Soft enable/disable of a user. |
| `serviceimpl/employee/` | `EmployeeInvitationServiceImpl.java` | `createInvitation`, `acceptInvitation`, `findByAcceptanceToken`, `markAsAccepted/Rejected` | Same lifecycle, employee-portal track. |
| `controller/publicapi/` | `InvitationAcceptanceController.java` | `getInvitationByEmail(...)` | `GET /api/public/invitation-by-email` — unauthenticated; the accept-invite page calls this first. |
| | | `processInvitation(...)` | `POST /api/public/process-invitation` — ACCEPT or REJECT; guarded by the `fed.secret` header rather than a JWT. |
| `serviceimpl/auth/` | `AuthzServiceImpl.java` | `getAllowedActionsForUser(userId, orgId)` — L45 | Resolves the caller's permitted action codes (cached). |
| | | `canPerform(userId, orgId, actionKey)` — L84 | The permission check itself. |
| | | `invalidateUserOrgCache(...)` — L92 | Called whenever role↔action mappings change. |

**Tables:** `companyUser`, `userInvitations`, `employeeInvitation`, `organizationUserMapping`, `organizationUserRoleMapping`.

---

## 2. Organization Setup

### Functional flows

1. **Create organization** → auto-creates a head-office `workLocation`, seeds default Professional Tax for the state, initialises `orgSetupSteps`.
2. **Complete the wizard** — work locations, departments, designations, pay schedule, statutory components, salary components. `orgSetupSteps` / `onboarding_status` drive the checklist UI.
3. **Set the filing address** — nominates one work location as the statutory filing address.
4. **Upload / delete the org logo** — via Cloudinary; the payslip header uses it.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/organization/` | `OrganizationController.java` | `create(dto)` | `POST /api/organizations/new`. |
| | | `updateOrganizationWithHeadOffice(...)` | `PUT /api/organizations/setup/{id}` — the wizard's "save org + head office" step. |
| | | `update(id, multipart)` | Profile edit including logo upload. |
| | | `setFilingAddress(orgId, workLocationId)` | Marks the statutory filing address. |
| | | `getOrganizationsForCurrentUser(jwt)` | `GET /api/organizations/active-organizations` — powers the org switcher. |
| | | `deleteOrganizationFile(orgId)` | Removes the logo from Cloudinary and clears the URL. |
| `serviceimpl/organization/` | `OrganizationServiceImpl.java` | `createOrganization(dto)` — L158 | Generates `organizationId`, creates the head-office work location, seeds default PT. |
| | | `updateOrganizationWithHeadOffice(...)` — L88 | Updates org + head-office location in one transaction. |
| | | `getActiveOrganizationsForUser(userId)` — L399 | Joins `organizationUserMapping` to list the user's orgs. |
| | | `generateUniqueOrganizationId()` / `generateUniqueWorkLocationId()` | ID minting. |
| | | `deleteOrganizationFile(orgId)` — L429 | Cloudinary delete + DB clear. |
| `controller/organization/` | `WorkLocationController.java` | `create` / `update` / `getById` / `getAllForOrg` / `delete` | `/api/worklocations` CRUD. |
| | | `bulkUploadWorkLocations(...)` | `POST /api/worklocations/imports` — CSV import. |
| `serviceimpl/organization/` | `WorkLocationServiceImpl.java` | `saveAll(orgId, list)` — L128 | The import path — batch insert. |
| `controller/organization/` | `DepartmentController.java`, `DesignationController.java` | `create`, `update`, `get`, `getAll`, `delete`, `bulkUpload*` | Identical CRUD + `/imports` shape for both. |
| `serviceimpl/organization/` | `DepartmentServiceImpl.java`, `DesignationServiceImpl.java` | `saveAll(orgId, list)` | Bulk import. |
| `controller/` | `PayScheduleController.java` | `create` / `get` / `update` | `/api/paySchedule` — one schedule per org (pay day, working-day basis, frequency). |
| `serviceimpl/` | `PayScheduleServiceImpl.java` | `create(orgId, dto)` — L40 | Guards against a second schedule for the same org. |
| `controller/` | `OrgSetupStepsController.java` | `getOrgSetupSteps(orgId)` | Returns which wizard steps are done. |
| `serviceimpl/` | `OrgSetupStepsServiceImpl.java` | `getOrgSetupSteps(orgId)` — L58 | Computes completion by probing each config table rather than trusting a stored flag. |
| `controller/leaveAndAttendance/onboarding/` | `OnboardingStatusController.java` | `getOnboardingStatus` / `updateOnboardingStatus` | `/api/v1/leaveandattendance` — leave-module onboarding checklist. |
| `serviceimpl/.../onboarding/` | `OnboardingStatusServiceImpl.java` | `getOrCreateByOrganizationId(orgId)` — L29 | Self-creating row. |
| | | `isAllCompleted(orgId)` — L76 | Gate that unlocks the main dashboard. |
| `controller/leaveAndAttendance/preferences/` | `PreferencesController.java` | `create`, `update`, `get`, `getPreferencesByOrganization` | Org-wide payroll preferences. |
| `controller/organization/` | `IncomeTaxDetailsController.java` | `updateIncomeTaxDetails` / `getIncomeTaxDetails` | TAN, PAN, TDS circle — printed on Form 16. |

**Tables:** `organization`, `workLocations`, `department`, `designation`, `paySchedule`, `orgSetupSteps`, `onboarding_status`, `preferences`, `incomeTaxDetails`.

---

## 3. Users, Roles & Permissions

### Functional flows

1. **Define a role** inside an org (`organizationRole`) — roles are per-org, not global.
2. **Attach actions** to that role (`organization_role_action` → `action`). Action codes are global, seeded by `MasterDataInitializer`.
3. **Map a user to a role** (`organizationUserRoleMapping`).
4. **Authorize** — `OrganizationRoleInterceptor` resolves the role per request; `AuthzServiceImpl.canPerform()` checks the action code.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/organization/` | `OrganizationRoleController.java` | `createRole`, `updateRole`, `getRole`, `getAllRoles`, `deleteRole` | `/api/roles` CRUD, org-scoped. |
| `serviceimpl/organization/` | `OrganizationRoleServiceImpl.java` | `generateUnique10DigitRoleId()` — L35 | Business role ID. |
| | | `updateRole(orgId, roleId, dto)` — L75 | Renames and re-points action mappings. |
| `controller/` | `ActionController.java` | `listActions()` / `createAction(dto)` | `/api/actions` — the global permission catalogue. |
| `serviceimpl/action/` | `ActionServiceImpl.java` | `listAllActive()`, `findByCode(code)` | Lookups used when rendering the permission matrix. |
| `controller/` | `RoleActionController.java` | `getRoleActions(orgId, roleId)` | `GET /api/organizations/{orgId}/roles/{roleId}/actions`. |
| | | `assignActions(...)` | Replaces the role's action set in one call. |
| `serviceimpl/action/` | `OrganizationRoleActionServiceImpl.java` | `assignActionsToRole(roleDbId, actionIds, performedBy)` — L34 | Diff-and-apply; returns the resulting action codes. |
| | | `getActionCodesForRole(roleDbId)` — L71 | Used by the interceptor and the frontend menu builder. |
| | | `removeActionFromRole(...)` — L81 | Single-permission revoke. |
| `controller/` | `OrganizationUserRoleMappingController.java` | `getMyRoleMapping(...)` | `GET /api/organization-user-role-mapping/my-role` — called on boot to build the sidebar. |
| | | `getMyOrganizations()` | Org list for the switcher. |
| `config/` | `OrganizationRoleInterceptor.java` | `preHandle(req, res, handler)` — L84 | Reads the `organizationId` header + JWT subject, resolves the role, rejects mismatches before the controller runs. |

**Tables:** `organizationRole`, `action`, `organization_role_action`, `organizationUserRoleMapping`, `organizationUserMapping`.

---

## 4. Employee Management

### Functional flows

1. **Add-employee wizard** — four sequential screens, each hitting a different endpoint:
   `basic details` → `personal details` → `payment (bank) details` → `salary details (CTC)`.
   The employee row is created at step 1; later steps update it. A `completionStatus` field tracks how far the profile got.
2. **Bulk import** — CSV upload for basic details, statutory details, bank details, personal details.
3. **Employee portal** — activate / deactivate / soft-delete / enable portal / resend invitation.
4. **Employee self-view** — `/api/employees-portal/employee-profile` assembles a full profile (basic + CTC + statutory) for the employee's own portal.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/employee/` | `BasicDetailsController.java` | `createBasicDetails(...)` — L168 | `POST /api/employees` — step 1 of the wizard. **Note:** an older, fully commented-out copy of this controller occupies L22–L155; the live class starts at L156. |
| | | `updateBasicDetails(employeeId, dto)` — L183 | Edit-basic screen. |
| | | `getAllBasicDetails(...)` — L246 | Paged + filtered employee list (uses `BasicDetailsSpecs`). |
| | | `getBasicDetails(employeeId)` — L199 | Single employee. |
| | | `deleteBasicDetails(employeeId)` — L287 | Hard delete. |
| | | `bulkUploadBasicDetails(...)` — L301 | `POST /api/employees/imports`. |
| | | `importStatutoryDetails(...)` — L335 | `POST /api/employees/imports/statutory`. |
| | | `getEmployeeByWorkMail(email)` — L316 | Used by the portal to resolve "who am I" from the JWT email. |
| `serviceimpl/employee/` | `BasicDetailsServiceImpl.java` (1765 lines) | `createBasicDetails(orgId, dto)` — L507 | The heavyweight: validates, generates employee number + unique ID, resolves department/designation/work-location/reporting-manager, optionally provisions the Keycloak portal user, creates the default TDS record. |
| | | `generateEmployeeNumber()` — L440 | Sequential employee number per org. |
| | | `generateEmployeeUniqueId()` — L981 | The `employeeId` business key. |
| | | `updateBasicDetails(...)` — L1058 | Update path; re-resolves references and re-syncs Keycloak email if it changed. |
| | | `getAllBasicDetails(orgId, pageable, ...)` — L1334 | Paged query with dynamic specs. |
| | | `mapToDto(entity)` — L1385 | Entity → DTO including derived `completionStatus`. |
| | | `saveAll(orgId, list)` — L1539 | Bulk import with per-row validation. |
| | | `getEmployeeByWorkMail(email)` — L1683 | Cross-module lookup (portal, payslip, reimbursement all use it). |
| | | `importStatutory(orgId, dtos)` — L1742 | Statutory-only import (PF/UAN/ESI numbers). |
| `specs/` | `BasicDetailsSpecs.java` | (static Specification builders) | Composes the department / designation / status / search-term filters for the employee list. |
| `controller/employee/` | `EmployeePersonalDetailController.java` | `create`, `update`, `getByEmployeeId`, `getAll`, `deleteByEmployeeId`, `createBulk` | `/api/employees/personal-details` — wizard step 2. Older commented copy at L16–L71. |
| `serviceimpl/employee/` | `EmployeePersonalDetailServiceImpl.java` | `create(...)` — L49 | Persists personal details plus the nested `ResidentialAddress`. |
| | | `mapToEntity/mapToDto(ResidentialAddress)` — L281/L292 | Nested address mapping. |
| `controller/employee/` | `EmployeeBankDetailController.java` | `createBankDetails`, `update`, `getByEmployeeId`, `getAll`, `delete`, `createBulkBankDetails` | `/api/v1/employees/bank-details` — wizard step 3. |
| `controller/employee/` | `EmployyePortalContoller.java` *(sic — typo in filename)* | `getEmployeeProfile(...)` | `GET /api/employees-portal/employee-profile` — the portal's home payload. |
| | | `activateEmployee` / `deactivateEmployee` / `softDeleteEmployee` | Lifecycle transitions, each also toggling Keycloak. |
| | | `enablePortal(employeeId)` | Creates the Keycloak account + sends credentials. |
| | | `resendInvitation(employeeId)` | Re-sends the portal invite email. |
| `serviceimpl/employee/` | `EmployyePortalServiceImpl.java` | `getEmployeeProfile(orgId, employeeId)` — L76 | Assembles basic + CTC + statutory into `EmployeeFullProfileDTO`. |
| | | `calculateTotalEarnings(ctcDTO)` — L153 | Sums the CTC earning lines for the profile header. |
| `controller/` | `SalaryPreviewController.java` | `getEmployeePreview(employeeId)` | `GET /api/employees/salary/preview/{id}` — the live salary breakdown shown while editing CTC. |
| `serviceimpl/` | `SalaryPreviewServiceImpl.java` | `getEmployeePreview(orgId, employeeId)` — L54 | Combines CTC + statutory config into a preview DTO. |
| `controller/employee/preview/` | `OrgStatutoryController.java` | `getOrgStatutoryConfig(orgId)` | `GET /api/employees/statutory/config` — tells the salary form whether EPF/ESI are enabled for this org. |

**Tables:** `employee`, `employee_personal_detail`, `residential_address`, `employee_bank_detail`, `employeeInvitation`.

---

## 5. CTC & Salary Revision

### Functional flows

1. **Define CTC** — annual CTC is split into Basic / HRA / other earnings, each either a % of CTC, a % of Basic, or a fixed amount. EPF and ESI components are derived and stored alongside.
2. **Revise salary** — a revision is a *new row* on the same `ctc_structure` lineage with an effective date and an approval status (`CtcRevisionStatus`).
3. **Approve / reject / process-later** — a revision approval queue; approved revisions become active on their effective date and fire `SalaryRevisionActivatedEvent`.
4. **TDS recalculation** — activating a revision re-runs the tax engine for the remaining months of the FY.
5. **Export** — revised CTCs export to Excel.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/employee/` | `CtcStructureController.java` | `create(...)` — L115 | `POST /api/v1/ctc-structures` — wizard step 4. (Older commented copy at L25–L87.) |
| | | `update(...)` — L132 | Edit-salary screen. |
| | | `get(id)`, `list()`, `getAllByEmployeeId(employeeId)` | Reads. |
| | | `getByWorkMail(email)` — L209 | Employee-portal "My salary details". |
| | | `revise(...)` — L230 | `POST /api/v1/ctc-structures/revise` — creates a revision. |
| | | `updateRevision(...)` / `deleteRevision(...)` / `getRevision(...)` | Revision maintenance. |
| | | `getAllRevisedCtcs(...)` — L303 | Paged revision-approval queue. |
| | | `exportRevisedCtcs(...)` — L329 | Excel download of the queue. |
| | | `processLater(...)` — L347 | Defers selected revisions to a later payrun. |
| `serviceimpl/employee/` | `CtcStructureServiceImpl.java` (1777 lines) | `update(orgId, dto)` — L288 | Create-or-update of the whole structure; recomputes derived components. |
| | | `getBasicAnnual(entity, newCtc)` — L514 | The core split rule — Basic as % of CTC. |
| | | `mapToDto(entity)` — L534 | Expands the stored structure into the full earnings breakdown the UI renders. |
| | | `getFiscalYear(effectiveDate)` — L729 | India FY (Apr–Mar) bucketing — used by every tax path. |
| | | `getSalaryStructureByWorkMail(email)` — L744 | Portal lookup. |
| | | `revise(orgId, dto)` — L828 | Builds the revision row, links it to the current structure, sets `CtcRevisionStatus`. |
| | | `updateRevision(...)` — L1048 | Edits a pending revision. |
| | | `deleteRevision(...)` — L1282 | Removes a pending revision. |
| | | `getAllRevisedCtcs(...)` — L1360 | The approval queue query. |
| | | `mapToRevisionListDto` / `mapToExportRow` — L1377 / L1423 | Grid and Excel row shapes. |
| | | `exportRevisedCtcs(orgId)` — L1542 | Builds the workbook bytes. |
| | | `recalculateTdsAfterSalaryChange(...)` — L1697 | Hands off to the TDS module. |
| | | `processLaterRevisions(...)` — L1725 | Bulk-defer. |
| `entity/` | `CtcStructure.java`, `CtcRevisionStatus.java`, `CtcEpfComponent.java`, `CtcEsiComponent.java` | — | The structure, its revision state, and the derived statutory component rows. |
| `event/` | `SalaryRevisionActivatedEvent.java` | — | Published when a revision goes live; the TDS listener consumes it. |
| `serviceimpl/employeeTDS/` | `TdsSalaryRevisionServiceImpl.java` | `handleSalaryRevisionTds(...)` — L51 | Recomputes remaining-month TDS after a revision. |
| `controller/employeeTDS/` | `EmployeeTdsController.java` | `recalculateTdsAfterSalaryRevision(...)` | `POST /api/v1/employee-tds/salary-revision` — manual trigger for the same. |
| `serviceimpl/employeeTDS/` | `DefaultTdsCreationServiceImpl.java` | `createDefaultTdsIfNotExists(...)` — L71 | Creates the baseline TDS record when an employee is onboarded. |
| | | `hasSalaryRevisionInCurrentFY(...)` — L269 | Guard so a revision isn't double-counted. |

**Tables:** `ctc_structure`, `ctc_epf_components`, `ctc_esi_components`, `employee_tds`.

---

## 6. Salary Components

### Functional flows

1. **Org-level catalogue** — the org defines reusable Earnings, Deductions, Benefits and Reimbursement types. Each has a calculation type (fixed / % of CTC / % of Basic), EPF/ESI/tax applicability, and an active flag.
2. **Soft lifecycle** — components are inactivated rather than deleted once used in a payrun (`inactivate*` / `reactivate*` endpoints).
3. **Attach to employee** — when the CTC structure is built, chosen components materialise as `employee_earnings`, `employee_benefits`, `employee_fbp_components`, `employee_variable_earnings`.
4. **FBP** — a flexible-benefit basket configured once per org.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/salarycomponents/` | `EarningController.java` | `createEarning`, `updateEarning`, `getEarning`, `getAllEarnings`, `deleteEarning` | `/api/earnings` CRUD. |
| | | `inactivateEarning` / `reactivateEarning` | `PUT /api/earnings/inactive|active/{id}` — the soft lifecycle. |
| `serviceimpl/salarycomponents/` | `EarningServiceImpl.java` | `create(orgId, dto)` — L46 | Validates the name/code is unique in the org, mints `generateEarningId()`. |
| | | `getAllEarnings(orgId)` — L110 | Returns the catalogue **plus** the system-default earnings the UI expects. |
| | | `delete(...)` — L192 | Refuses if the earning is referenced by any CTC structure. |
| `controller/salarycomponents/` | `DeductionController.java` | `create`, `update`, `get`, `getAll`, `delete`, `inactivateDeduction`, `reactivateDeduction` | `/api/deductions` — same shape. |
| `serviceimpl/salarycomponents/` | `DeductionServiceImpl.java` | `create` — L47, `getAll` — L105 | Same pattern as earnings. |
| `controller/salarycomponents/` | `BenefitController.java` | `createBenefit`, `updateBenefit`, `deleteBenefit`, `getBenefit`, `getAllBenefits`, `inactivateBenefit`, `reactivateBenefit` | `/api/benefits`. |
| | | `getUtility()` — L95 | Returns calculation-type options / dropdown metadata for the benefit form. |
| `serviceimpl/salarycomponents/` | `BenefitServiceImpl.java` | `getAllBenefits(orgId)` — L91 | Catalogue + defaults. |
| `util/` | `BenefitUtility.java` | (static helpers) | Benefit amount computation shared by CTC and payrun. |
| `controller/salarycomponents/` | `ReimbursementController.java` | `getAll`, `getById`, `create`, `update`, `delete`, `inactivate`, `reactivate` | `/api/reimbursements` — the *component type* catalogue (not the employee claim; see module 10). |
| `serviceimpl/salarycomponents/` | `ReimbursementServiceImpl.java` | `createReimbursement` — L42, `getAllReimbursements` — L78 | Same pattern. |
| `controller/claimsanddeclarations/` | `FBPController.java` | `getFBP(orgId)` / `updateFBP(...)` | `/api/fbp` — one FBP config per org. |
| `serviceimpl/claimsanddeclarations/` | `FBPServiceImpl.java` | `getFBP(orgId)` — L35 | Returns existing or calls `createDefaultFBP(org)`. |
| | | `createDefaultFBP(org)` — L45 | Seeds a sane default so the screen never 404s. |

**Tables:** `earnings`, `deductions`, `benefits`, `reimbursements`, `employee_earnings`, `employee_benefits`, `employee_fbp_components`, `employee_variable_earnings`.

---

## 7. Statutory Components

### Functional flows

1. **EPF** — enable/disable per org; set employer/employee contribution basis, wage ceiling, EDLI and admin charges.
2. **ESI** — enable/disable; set contribution rates and the eligibility wage ceiling.
3. **Professional Tax** — per-state slabs, defaulted on org creation from a state master, then editable. Every slab edit writes an audit row to `pt_history`; slabs can be reset to state defaults.
4. All three are read by the payrun engine when computing deductions.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/statutorycomponents/` | `EpfController.java` | `getEpf(orgId)` | `GET /api/epf`. |
| | | `createOrUpdateEpf(orgId, dto)` | `PUT /api/epf` — upsert. |
| | | `disableEpf(orgId)` | `POST /api/epf/disable`. |
| `serviceimpl/statutorycomponents/` | `EpfServiceImpl.java` | `getEpfByOrganizationId(orgId)` — L40 | Read, with defaults if unset. |
| | | `createOrUpdateEpf(...)` — L64 | Validates rates and ceilings before saving. |
| `controller/statutorycomponents/` | `EsiController.java` | `getEsi`, `updateEsi`, `disableEsi` | `/api/esi` — same shape. |
| `serviceimpl/statutorycomponents/` | `EsiServiceImpl.java` | `saveOrUpdateEsi(...)` — L59 | Upsert. |
| `controller/statutorycomponents/` | `ProfessionalTaxController.java` | `getAllProfessionalTaxes(orgId)` | `GET /api/professional-tax` — one PT config per state the org operates in. |
| | | `updateProfessionalTax(taxId, dto)` | Header-level edit (registration number, filing frequency). |
| | | `updateTaxSlabAndEffectiveDate(...)` | `PUT /api/professional-tax/{taxId}/slab` — slab edit with an effective date. |
| | | `resetToDefaultSlabs(taxId)` | `DELETE .../slab/reset` — restore state defaults. |
| `serviceimpl/statutorycomponents/` | `ProfessionalTaxServiceImpl.java` (959 lines) | `createDefaultTax(organization, state)` — L159 | Called during org creation; seeds state slabs. |
| | | `updateSlabAndEffectiveDate(...)` — L279 / L372 | Two overloads; applies the new slab set from an effective date. |
| | | `captureSlabChanges(tax, dto)` — L482 | Diffs old vs new slabs. |
| | | `saveInsertHistory` / `saveDeleteHistory` / `saveUpdateHistory` — L538 / L564 / L591 | Writes per-row audit into `pt_history`. |
| | | `savePTHistory(...)` — L459 | Whole-document audit (old JSON vs new JSON). |
| | | `resetToDefaultSlabs(...)` — L624 / L677 | **Duplicate method bodies exist** (L624 and L677); same for `getAllProfessionalTaxes` (L668 and L788) — dead-code left from a merge. |
| | | `convertSlabDetails(ArrayNode)` — L922 | JSON → `SlabDetailDTO`. |
| `util/` | `ProfessionalTaxUtil.java` | (static) | Resolves the applicable slab for a gross amount + state. |

**Tables:** `epf`, `esi`, `professionalTax`, `slabDetail`, `slabRateConfiguration`, `pt_history`, `org_pt_override`.

---

## 8. Leave Management

> **Newest module** (Aug–Sep 2026). It replaced the old HRMS-sourced LOP path: LOP for a payrun
> is now read from `employee_leave_balance_consumption` rather than from HRMS.

### Functional flows

1. **Allocate leave** — per employee, per leave type, per year: annual days, expiry date, and an optional monthly accrual breakdown. Unique on `(organization_id, employee_id, leave_type, year)`.
2. **Bulk allocate** — multi-employee grid save, or Excel/CSV import with per-row error reporting.
3. **Mark leave taken** — records consumption per month; the running balance is recalculated after each write.
4. **Derive LOP** — days consumed beyond the allocated quota (or against expired leave) become LOP/LWP.
5. **Feed the payrun** — the payrun reads consumption to compute the LOP deduction.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/leave/` | `EmployeeLeaveAllocationController.java` | `saveBulkAllocations(...)` — L61 | `POST /api/leave-allocation/bulk` — the grid save. |
| | | `updateEmployeeAllocation(employeeId, ...)` — L95 | `PUT /api/leave-allocation/{employeeId}`. |
| | | `getAllocations(...)` — L135 | `GET /api/leave-allocation` — paged + searchable list. |
| | | `getEmployeeAllocation(employeeId)` — L183 | Single employee's allocation card. |
| | | `deleteEmployeeAllocation(...)` — L217 | Removes all leave types for an employee/year. |
| | | `deleteSingleLeaveType(employeeId, leaveType)` — L249 | Removes one leave type. |
| | | `importNewAllocations(file)` — L280 | `POST /api/leave-allocation/import` — Excel or CSV. |
| `serviceimpl/leave/` | `EmployeeLeaveAllocationServiceImpl.java` (1878 lines) | `initSchemaIndexes()` — L98 | `@PostConstruct` — creates the unique index if `ddl-auto` missed it. |
| | | `parseMonthlyBreakdown(json)` / `serializeMonthlyBreakdown(map)` — L168 / L190 | The monthly accrual map is stored as a JSON string column. |
| | | `saveBulkAllocations(...)` — L216 | Upserts many employees' allocations in one transaction. |
| | | `updateEmployeeAllocation(...)` — L386 | Single-employee update; triggers balance recalculation. |
| | | `recalculateConsumptionBalances(...)` — L666 | **Core invariant** — after any allocation change, re-derives consumed/remaining/LOP across all months. |
| | | `buildLeaveAllocationItem(...)` — L722 | Assembles one leave-type row (allocated, consumed, remaining, expiry, LOP). |
| | | `getAllocations(orgId, year)` — L876 | Full list. |
| | | `getAllocationsPaginated(orgId, year, search, page, size)` — L1104 | The grid query; orders newest-first. |
| | | `importNewAllocations(orgId, file, createdBy)` — L1429 | Dispatches to the Excel or CSV parser; returns `LeaveAllocationImportResultDTO` with per-row errors. |
| | | `parseRowBasedExcel(...)` — L1506 | Apache POI reader. |
| | | `parseRowBasedCsv(...)` — L1594 | CSV reader. |
| | | `processSingleLeaveRow(...)` — L1670 | Validates and upserts one imported row. |
| | | `getSafeCellValue`, `parseLocalDate`, `splitCsvLine` — L1834+ | Parsing helpers. |
| `controller/leave/` | `EmployeeLeaveConsumptionController.java` | `updateEmployeeConsumption(employeeId, ...)` — L29 | `PUT /api/leave-consumption/{employeeId}` — the "mark leave taken" save. |
| | | `getConsumptions(...)` / `getEmployeeConsumption(...)` | Reads for the overview and detail screens. |
| | | `deleteEmployeeConsumption` / `deleteEmployeeEntry` / `deleteSingleLeaveTypeConsumption` | Three delete granularities: whole year, one entry, one leave type. |
| `serviceimpl/leave/` | `EmployeeLeaveConsumptionServiceImpl.java` | `updateEmployeeConsumption(...)` — L43 | Writes consumption entries, then calls the recalculation. |
| | | `recalculateRunningBalances(...)` — L135 | Re-derives month-by-month balances after an edit. |
| | | `buildEmployeeConsumptionDTO(...)` — L268 | Response shape for the leave cards. |
| | | `deleteEmployeeMonthConsumption(...)` — L228 | Month-level delete. |
| `entity/leave/` | `EmployeeLeaveAllocation.java` | — | `employee_leave_allocation`; unique key `uk_emp_leave_alloc`. |
| | `EmployeeLeaveBalanceConsumption.java` | — | `employee_leave_balance_consumption`; `leave_id` is a unique business key, `allocation_id` links back. |
| `dto/leave/` | `LeaveAllocationImportResultDTO`, `ImportRowErrorDTO`, `LeaveAllocationItemDTO`, `LeaveEntryDTO`, `BulkLeaveAllocationRequestDTO`, `EmployeeLeaveConsumption*DTO` | — | The import-result and grid contracts. |
| `controller/leaveAndAttendance/` | `LeaveTypeController.java` | `createLeaveType`, `updateLeaveType`, `getLeaveType`, `getAllLeaveTypes`, `deleteLeaveType`, `bulkUploadLeaveTypes`, `updateLeaveTypeStatus` | `/api/leave-types` — the leave-type catalogue. |
| `controller/leaveAndAttendance/holiday/` | `HolidayController.java` | `createHoliday`, `updateHoliday`, `deleteHoliday`, `getHoliday`, `getAllHolidays` | `/api/holidays`. |
| `controller/leaveAndAttendance/attendance/` | `AttendancePreferenceController.java` | `createAttendancePreference`, `update...`, `delete...`, `get...`, `getAll...` | `/api/attendance-preferences`. |
| `controller/leaveAndAttendance/leaveImport/` | `EmployeeLeaveImportController.java` | `createLeaveImport`, `updateLeaveImport`, `getLeaveImport`, `getAllLeaveImports`, `deleteLeaveImport`, `bulkUploadLeaveImports` | `/api/employee-leave-imports` — the **older** leave-balance import path, still live. |

**Tables:** `employee_leave_allocation`, `employee_leave_balance_consumption`, `leave_type`, `holidays`, `attendance_preferences`, `employee_leave_import`.

> ⚠️ Two leave-import mechanisms coexist: the new `POST /api/leave-allocation/import`
> (Excel + CSV, row-level errors) and the older `POST /api/employee-leave-imports/imports`.
> New work should use the former.

---

## 9. Employee Deductions

> Added Aug 2026. Ad-hoc, one-off deductions (recovery, advance, damages) that are **not** part of the CTC.

### Functional flows

1. **Grid add** — admin picks employees, adds one or more deduction rows each (type, amount, month, reason), optionally attaching a proof file.
2. **Proof upload** — file goes to Cloudinary; the returned URL is stored on the row.
3. **Lifecycle** — a deduction moves through `DeductionStatus`, including `INPAYRUN` once consumed by a payrun.
4. **Payrun consumption** — active deductions for the pay period are subtracted from net pay.
5. **Employee view** — the portal shows the employee their own deductions.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/employee/` | `SalaryDeductionController.java` | `createGridDeduction(...)` — L39 | `POST /api/employee-deductions/grid` — the bulk grid save. |
| | | `uploadProof(...)` — L55 | `POST /api/employee-deductions/upload` — multipart; returns the Cloudinary URL. |
| | | `getAllDeductions(...)` — L124 | `GET /api/employee-deductions` — paged admin list. |
| | | `updateDeduction(id, ...)` — L156 | Inline edit. |
| | | `deleteDeduction(id)` — L173 | Delete (blocked once `INPAYRUN`). |
| | | `getMyDeductions(...)` — L187 | `GET /api/employee-deductions/my-deductions` — employee portal. |
| `serviceimpl/employee/` | `SalaryDeductionServiceImpl.java` | `createGridDeduction(...)` — L44 | Validates each row's employee/org, resolves the deduction type, persists the batch. |
| | | `getAllDeductions(...)` — L129 | Paged query with filters. |
| | | `validateEmployeeBelongsToOrg(employeeId, orgId)` — L187 | Tenancy guard used by every write. |
| | | `updateDeduction(...)` — L201 | Guards status transitions. |
| | | `getMyDeductions(...)` — L276 | Self-service list. |
| `entity/` | `SalaryDeduction.java` | — | Table `employee_deduction`. |
| `enumeration/` | `DeductionStatus.java` | — | Lifecycle values including `INPAYRUN`. |
| `mapper/employee/` | `SalaryDeductionMapper.java` | — | Entity ⇄ `SalaryDeductionResponseDTO`. |
| `serviceimpl/` | `CloudinaryServiceImpl.java` | `uploadFile(file, companyUserId)` — L39 | The generic upload used by the proof endpoint. |

**Tables:** `employee_deduction`.

---

## 10. Reimbursement Claims

> Distinct from module 6's reimbursement *component types* — this is the employee claim workflow.

### Functional flows

1. **Employee submits a claim** — either multipart (with a receipt) or plain JSON. Type is validated against the org's reimbursement components.
2. **Admin reviews** — list → detail → approve (with an approved amount, which may differ from the claimed amount) or reject with a reason.
3. **Payrun payout** — approved claims move to `INPAYRUN` and are added to the payslip as a non-taxable payout.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/employeereimbursement/` | `EmployeeReimbursementController.java` | `createReimbursementMultipart(...)` — L71 | `POST /api/employee/reimbursements` (multipart) — claim + receipt. |
| | | `createReimbursementJson(...)` — L146 | Same path, `application/json` — claim without attachment. |
| | | `getEmployeeReimbursements(...)` — L182 | The employee's own claim list. |
| | | `getReimbursementDetails(id)` — L219 | Claim detail. |
| `controller/employeereimbursement/` | `AdminReimbursementController.java` | `getAdminReimbursements(...)` — L54 | `GET /admin/reimbursements` — org-wide queue. |
| | | `getAdminReimbursementById(id)` — L87 | Review detail. |
| | | `approveReimbursement(id, ...)` — L122 | `PUT /{id}/approve` — sets approved amount + status. |
| | | `rejectReimbursement(id, ...)` — L162 | `PUT /{id}/reject` — with reason. |
| `serviceimpl/employeereimbursement/` | `EmployeeReimbursementServiceImpl.java` | `createReimbursement(...)` — L65 / L74 | Two overloads (with / without file); uploads the attachment then persists. |
| | | `validateReimbursementType(method, type)` — L522 | Checks the claim type exists and is active for the org. |
| | | `getEmployeeReimbursements(...)` — L233 | Employee list. |
| | | `getAdminReimbursements(orgId)` — L299 | Admin queue with employee names joined. |
| | | `approveReimbursement(...)` — L377 | Sets `ReimbursementStatus`, records approver, emails the employee. |
| | | `rejectReimbursement(...)` — L453 | Reject path. |
| | | `formatEmployeeName(bd)` — L514 | Display-name helper. |
| `serviceimpl/` | `CloudinaryServiceImpl.java` | `uploadReimbursementAttachment(...)` — L134 | Receipt upload. |
| `enumeration/employeereimbursement/` | `ReimbursementStatus.java`, `ReimbursementPaymentStatus.java`, `ReimbursementType.java` | — | The claim state machine. |
| `mapper/employeereimbursement/` | `EmployeeReimbursementMapper.java` | — | Entity ⇄ DTO. |

**Tables:** `employee_reimbursement_request`.

**Reference docs in the repo:** `REIMBURSEMENT_BACKEND_DOCUMENTATION.md` and
`REIMBURSEMENT_FRONTEND_DOCUMENTATION.md` (in the frontend repo root) were written by the
feature author and go deeper than this section.

---

## 11. Pay Run

> The heart of the system. Three payrun types share most of the machinery:
> **regular** (`payruns`), **off-cycle** (`off_cycle_pay_run`), **one-time payout** (`one_time_payout`).

### Functional flows (regular payrun)

```
1. Create payrun for a period          PayRunController.createPayRun
        │  status = DRAFT
        ▼
2. Generate per-employee lines         EmployeePayRunController.generateEmployeePayRuns
        │  for each active employee: earnings, EPF, ESI, PT, TDS, LOP, deductions, reimbursements
        ▼
3. Preview / adjust                    EmployeePayRunController.getEmployeePayRunListByPayRun
        │  add / remove employees, review totals
        ▼
4. Approve (or reject)                 PayRunController.approvePayRun / rejectPayRun
        │  totals frozen, salary-slip emails queued
        ▼
5. Record payment                      PayRunController.completePayRunPayment
        │  status = PAID
        ▼
6. Payslips                            EmployeePayRunController.getPayslip
                                       PublicPayslipController.getPublicPayslip (tokenised link)
```

**Net pay composition** (as assembled in `mapToPayRunDTO`):

```
gross earnings   = Σ employee_earnings for the period (+ periodic bonus, + variable earnings)
– LOP            = (monthlySalary / paidDays) × lopDays      ← lopDays from employee_leave_balance_consumption
– EPF employee   – ESI employee   – professional tax   – monthly TDS
– salary deductions (employee_deduction, status active for this period)
+ approved reimbursements (employee_reimbursement_request)
= net pay
```

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/payruns/` | `PayRunController.java` | `getAllPayRuns(orgId)` | `GET /api/payruns` — the payrun list screen. |
| | | `getPayRun(orgId, payrunId)` | Single payrun header. |
| | | `createPayRun(orgId, dto)` | `POST /api/payruns` — opens a draft for a period. |
| | | `updatePayRun(...)` | Edit a draft. |
| | | `getAllCompletedPayRuns(orgId)` | Payroll-history tab. |
| | | `approvePayRun(payrunId, ...)` | `PUT /{id}/approve`. |
| | | `rejectPayRun(payrunId, reason)` | `PUT /{id}/reject`. |
| | | `completePayRunPayment(payrunId, employeeIds)` | `POST /{id}/payment` — marks paid. |
| | | `deletePayRun(payrunId)` | Draft-only delete. |
| `serviceimpl/payruns/` | `PayRunServiceImpl.java` (991 lines) | `generateUnique10DigitPayrunId()` — L109 | Business payrun ID. |
| | | `isProfileComplete(BasicDetails)` — L125 | Excludes half-onboarded employees from the run. |
| | | `getAllPayRunsStructuredResponse(orgId)` — L232 | Builds the grouped list (current period + upcoming + history) the payruns screen renders. |
| | | `createPayRun(orgId, dto)` — L413 | Creates the header, then triggers employee-line generation. |
| | | `calculateAndUpdatePayRunTotals(payRun, lines)` — L449 | Rolls per-employee lines up into payrun totals. |
| | | `calculateAndUpdateEpfAndEsiTotals(payRun, lines)` — L511 | Separate statutory rollup (employer + employee shares). |
| | | `approvePayRun(...)` — L681 | Freezes totals, sets status, triggers `sendSalarySlipReadyEmails`. |
| | | `rejectPayRun(...)` — L741 | Reverts to draft with a reason. |
| | | `completePayRunPayment(...)` — L799 | Marks selected employees paid. |
| | | `deletePayRun(...)` — L854 | Cascades the employee lines. |
| | | `getCurrentPayrunForItDeclaration(orgId)` — L903 | Tells the IT-declaration module which period is open. |
| | | `sendSalarySlipReadyEmails(...)` — L948 | Brevo template `brevo.template.salary.slip`. |
| `controller/payruns/` | `EmployeePayRunController.java` | `getEmployeePayRunList(...)` — L35 | `GET /api/payrun-employees/employee-list` — candidate employees for a period. |
| | | `generateEmployeePayRuns(payrunId)` — L57 | `POST /generate/{payrunId}` — the calculation trigger. |
| | | `getEmployeePayRunListByPayRun(payrunId)` — L79 | Preview / summary grid. |
| | | `getEmployeePayslips(...)` — L103 | Paged payslip list. |
| | | `getPayslip(payrunId, employeeId)` — L137 | One payslip payload. |
| | | `downloadEmployeePayRunCsv(payrunId)` — L160 | Bank-transfer CSV. |
| `serviceimpl/payruns/` | `EmployeePayRunServiceImpl.java` (1920 lines) | `getEmployeePayRunList(orgId, processingPeriod)` — L149 | Loads active, profile-complete employees for the period. |
| | | **`mapToPayRunDTO(emp, processingPeriod)` — L211** | **The single most important function in the backend.** Computes one employee's full pay line: earnings, bonus, LOP, EPF, ESI, PT, TDS, deductions, reimbursements, net pay. |
| | | `getFyStartDate(fy)` / `getFyEndDate(fy)` — L441 / L445 | India FY boundaries. |
| | | `getPeriodicBonusAmount(earning, periodYearMonth, doj)` — L449 | Spreads quarterly/annual bonuses onto the right month, pro-rated by joining date. |
| | | `resolveMonthlyTds(emp, processingPeriod)` — L531 | Pulls the scheduled monthly TDS from `employee_tds`. |
| | | `calculateEpfEmployer(emp, periodDate)` — L948 | Employer EPF (incl. EPS split and wage ceiling). |
| | | `generateEmployeePayRuns(orgId, payRun)` — L1003 | Persists a `employee_payruns` row per employee. |
| | | `getEmployeePayRunListByPayRun(orgId, payrunId)` — L1245 | Reads back the persisted lines. |
| | | `getEmployeePayslips(...)` — L1316 | Paged payslip index. |
| | | `toDTO(EmployeePayRun)` — L1356 | Persisted line → payslip DTO. |
| | | `getEmployeePayslip(orgId, employeeId, payRunId)` — L1450 | Full payslip incl. org logo, work location, amount-in-words inputs. |
| | | `buildEarnings(emp, periodDate)` — L1672 | Earning component breakdown for the payslip. |
| | | `generateEmployeePayRunCsv(...)` / `generateCsvData(...)` — L1718 / L1735 | Bank CSV writer. |
| `controller/payruns/` | `PublicPayslipController.java` | `getPublicPayslip(payrunId, employeeId)` | `GET /api/public/payslips/...` — tokenised, unauthenticated payslip link (used in the email). |
| | | `generateTestLink(...)` / `getLatestPayslipInfo()` | Dev helpers. |
| `serviceimpl/payruns/` | `PayslipTokenServiceImpl.java` | `generateToken(payrunId, employeeId, orgId)` — L19 | Signs the public link. |
| | | `verifyToken(...)` — L33 | Validates it. |
| `controller/payruns/` | `PayRunReportController.java` | `downloadPayRunReport(...)` — L30 | `GET /api/payruns/report/excel` — multi-payrun Excel. |
| | | `downloadSinglePayRunReport(payrunId)` — L78 | Single-payrun Excel. |
| `controller/payRun/offCyclePayrun/` | `OffCyclePayRunController.java` | `createDraftPayRun`, `updatePayRun`, `getPayRun`, `getAllPayRuns`, `deletePayRun` | `/api/off-cycle-payruns` — bonus / arrears / final-settlement runs. |
| | | `importEmployeesToPayRun(payrollRunId, ...)` — L80 | CSV import of participants. |
| | | `importReleaseWithheldSalary(...)` — L90 | Special import for releasing withheld salary. |
| `serviceimpl/payRun/offCyclePayrun/` | `OffCyclePayRunServiceImpl.java` | `createPayRun` — L57, `updatePayRun` — L139, `importEmployeesToPayRun` — L251, `importReleaseWithheldSalary` — L334 | The off-cycle equivalents. |
| `controller/payRun/oneTimePayout/` | `OneTimePayoutController.java` | `createDraftPayout`, `addEmployeesToPayout`, `getPayout`, `getAllPayouts`, `getPayoutsByEarning`, `getPayoutsByEmployee`, `updatePayout`, `deletePayout`, `importEmployeesToPayout` | `/api/onetime-payouts`. |
| `serviceimpl/payRun/oneTimePayout/` | `OneTimePayoutServiceImpl.java` | `addEmployeesToPayout(...)` — L79 | Attaches employees + amounts. |
| | | `calculateAmountFromDays(employee, org, days)` — L141 | Converts a day count to an amount using the org's paid-days basis. |
| | | `importEmployeesToPayout(...)` — L206 | CSV import. |
| `mapper/payruns/` | `PayRunMapper.java`, `EmployeePayRunMapper.java` | — | Entity ⇄ DTO. |
| `enumeration/payruns/` | `PayRunStatus.java`, `PayRunType.java` | — | DRAFT / APPROVED / PAID / REJECTED; REGULAR / OFF_CYCLE / ONE_TIME. |
| `util/` | `MonthUtil.java` | — | Pay-period string parsing used throughout this module. |

**Tables:** `payruns`, `employee_payruns`, `off_cycle_pay_run`, `off_cycle_payrun_employee`, `off_cycle_payrun_employee_earnings`, `off_cycle_payrun_employee_deductions`, `one_time_payout`, `paySchedule`.

---

## 12. Tax Engine

> The largest single file in the codebase is here: `OldTaxCalculationServiceImpl.java` at **5,583 lines**.

### Functional flows

1. **Slab masters** — admin maintains tax regimes and their slabs (`taxSlabMaster` + `slabDetail`), with history on every change.
2. **Rule masters** — HRA, home loan, let-out property, other income, Section 6A items, 87A rebate, standard deduction, cess/surcharge are all data-driven masters seeded by `MasterDataInitializer`.
3. **Old-regime calculation** — gross for FY → exemptions (HRA, LTA…) → Chapter VI-A deductions → taxable income → slab tax → rebate → cess/surcharge → annual tax → ÷ remaining months = monthly TDS.
4. **New-regime calculation** — same shape, fewer deductions, different slabs.
5. **Variants** — with declared investments, with **approved POI** amounts, and with a **revised salary**. Each is a separate entry point.
6. **TDS scheduling** — the annual figure is divided across the remaining months of the FY and stored in `employee_tds`, which the payrun reads.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/employeeitdeclaration/taxCalculator/` | `TaxCalculationController.java` | `calculateOldTax(employeeId, fy)` — L36 | `GET /tax/calculate/old/{employeeId}/{fy}` — preview, no persistence. |
| | | `calculateNewTax(employeeId, fy)` — L51 | New-regime preview. |
| | | `saveOldTaxCalculation(...)` — L58 | `POST /old/save/...` — persists the result + TDS schedule. |
| | | `saveNewTaxCalculation(...)` — L91 | New-regime save. |
| | | `calculateOldRegimeTaxUsingPOI(...)` — L133 | Recalculates using **approved proof** amounts instead of declared ones. |
| | | `calculateNewTaxWithPOI(...)` — L153 | Same for the new regime. |
| `serviceimpl/.../taxCalculator/` | `OldTaxCalculationServiceImpl.java` (5583 lines) | `calculateOldTax(...)` — L131 | The main old-regime computation. |
| | | `calculateGrossSalaryForFY(...)` — L1597 | Sums CTC earnings across the FY, respecting joining date and revisions. |
| | | `calculateRemainingMonths(...)` / `countMonths(from, to)` — L1657 / L1699 | TDS spreading. |
| | | `calculateAndSaveOldTax(...)` — L1707 | Compute + persist + write the TDS schedule. |
| | | `calculateOldTaxWithRevisedSalary(...)` — L3240 | Recompute after a salary revision. |
| | | `calculateOldTaxWithRevisedSalaryAndApprovedPOI(...)` — L4498 | Both at once — the year-end path. |
| | | `calculateActualAnnualAmount(...)` — L5174 | Normalises a declaration line to an annual figure. |
| | | `calculateMonthlyGrossFromEarnings(ctc)` — L5281 | Monthly gross from the CTC structure. |
| | | `calculateTotalFySalary(...)` — L5296 | Whole-FY salary including revisions. |
| | | `extractPreviousEmploymentSummary(...)` — L5448 | Folds in prior-employer income. |
| | | `hasSalaryRevisionInCurrentFY(...)` — L5514 | Branch selector. |
| | | `validateCtcExistsForFy(...)` — L5548 | Fails fast when no CTC covers the FY. |
| `serviceimpl/.../taxCalculator/` | `NewTaxCalculationServiceImpl.java` (901 lines) | `calculateNewTax(...)` — L67 | New-regime computation. |
| | | `calculateNewTaxWithPOI(...)` — L420 | Approved-proof variant. |
| | | `calculateAndSaveNewTax(...)` — L443 | Persist + TDS schedule. |
| | | `calculateNewTaxWithRevisedSalary(...)` — L552 | Post-revision recompute. |
| | | `computePreviousEmployment(...)` — L840 | Prior-employer income handling. |
| `controller/taxCalculator/` | `TaxSlabMasterController.java` | `getActiveTaxSlabs()` — L45 | `GET /api/tax-slabs`. |
| | | `createTaxRegime(...)` — L95 / `updateTaxSlabs(id, ...)` — L61 / `deleteTaxRegime(id)` — L126 | Regime + slab maintenance. |
| `serviceimpl/.../taxCalculator/` | `TaxSlabServiceImpl.java` | `getActiveSlabs()` — L63 | Active regimes with their slab rows. |
| | | `validateSlabs(rows)` — L203 | Rejects overlapping / non-contiguous slabs. |
| | | `writeAudit(saved, oldJson, newJson, changedBy)` — L309 | Writes `taxSlabMasterHistory`. |
| `serviceimpl/employeeTDS/` | `DefaultTdsCreationServiceImpl.java` | `createDefaultTdsIfNotExists(...)` — L71 | Baseline TDS at onboarding. |
| | `TdsSalaryRevisionServiceImpl.java` | `handleSalaryRevisionTds(...)` — L51 | Post-revision TDS reschedule. |
| `entity/taxCalculator/` (+ root) | `TaxSlabMaster`, `SlabDetail`, `SlabRateConfiguration`, `OldTaxCalculation`, `NewTaxCalculation`, `OldTaxCalculationRevision`, `EmployeeTds`, `EmployeeTaxCalculationResult`, `EmployeeTaxRecalculation` | — | Calculation inputs, outputs and history. |
| `entity/` (rule masters) | `HraRuleMaster`, `HomeLoanRuleMaster`, `LetOutPropertyRuleMaster`, `OtherIncomeRuleMaster`, `Section6AItemMaster`, `Section87ARebateRuleMaster`, `StandardDeductionRuleMaster`, `CessSurchargeRuleMaster` | — | Data-driven tax rules; changing a limit is a data change, not a code change. |
| `config/` | `MasterDataInitializer.java` | — | Seeds all of the above at boot. |

**Tables:** `taxSlabMaster`, `taxSlabMasterHistory`, `slabDetail`, `slabRateConfiguration`, `employee_tds`, `employee_tax_calculation_result`, `employee_tax_recalculation`, `old_tax_section_deduction`, `old_tax_revision_section_deduction`, plus the eight `*_rule_master` tables.

---

## 13. IT Declaration & Proof of Investment

### Functional flows

**A. Org settings** — the admin configures the declaration window, POI window, reminder cadence and lock/release behaviour (`IncomeTaxDeclarationController`, `ProofOfInvestmentController` under `claimsanddeclarations/`).

**B. Employee declares** — enters intended investments per section (80C, 80D, HRA, home loan, let-out property, other income, previous employment). Stored in `employee_investment_declaration` + the eight `employee_inv_*` child tables.

**C. Employee submits proofs** — POI is *initialised from* the declaration: `getOrInitializePoi` builds one `employee_poi_item` per declared line, then the employee uploads supporting documents per item.

**D. Admin reviews item by item** — approve / reject each item with an approved amount and a comment thread; then a final approve/reject on the whole submission.

**E. Consider for IT** — approved amounts are pushed into the tax engine, replacing declared amounts, and TDS is recalculated for the remaining months.

**F. Automation** — two schedulers chase and lock.

### Code flow

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/claimsanddeclarations/` | `IncomeTaxDeclarationController.java` | `getIncomeTaxDeclaration(orgId)` / `updateIncomeTaxDeclaration(...)` | `/api/income-tax-declarations` — org window + reminder settings. |
| `serviceimpl/claimsanddeclarations/` | `IncomeTaxDeclarationServiceImpl.java` | `createDefaultIncomeTaxDeclaration(org)` — L65 | Self-seeding settings row. |
| | | `updateIncomeTaxDeclaration(...)` — L106 | Applies window changes and re-schedules reminders. |
| `controller/claimsanddeclarations/` | `ProofOfInvestmentController.java` | `getProofOfInvestment(orgId)` / `updateProofOfInvestment(...)` | `/api/proof-of-investment` — POI window + reminder settings. |
| `serviceimpl/claimsanddeclarations/` | `ProofOfInvestmentServiceImpl.java` | `createDefaultProofOfInvestment(org)` — L52 | Self-seeding. |
| | | `updateReminders(...)` — L147 | Rewrites the `reminder` rows the scheduler reads. |
| `controller/employeeitdeclaration/` | `EmployeeInvestmentDeclarationController.java` | `createDeclaration(employeeId, fy)`, `getDeclaration`, `updateDeclaration`, `deleteDeclaration` | `/api/employee-it-declarations/{employeeId}/{fiscalYear}`. |
| `serviceimpl/employeeitdeclaration/` | `EmployeeInvestmentDeclarationServiceImpl.java` | `deleteDeclaration(...)` — L358 | Cascades the `employee_inv_*` children. |
| | | `getCurrentTaxYearRange()` — L380 | FY window helper. |
| `controller/employeeitdeclaration/` | `EmployeeProofOfInvestmentController.java` | `getOrInitializePoi(employeeId, fy)` — L46 | `GET /api/proof-of-investments/{employeeId}/{fy}` — creates the POI shell from the declaration on first call. |
| | | `updatePoiItem(...)` — L91 / `deletePoiItem(...)` — L138 | Per-line edits. |
| | | `uploadDocuments(...)` — L277 | Multipart upload against one POI item. |
| | | `deleteDocument(documentId)` — L365 | Removes a proof file. |
| | | `submitPoi(...)` — L408 / `withdrawPoi(...)` — L454 | Submit / recall. |
| | | `getDocumentDownloadUrl(documentId)` — L499 | Signed Cloudinary URL. |
| | | `getCommentsForMyItem` / `addCommentToMyItem` / `updateMyComment` / `deleteMyComment` | The employee side of the per-item comment thread. |
| | | `getPOISettings()` — L657 | Window + deadline the UI enforces. |
| `serviceimpl/employeeitdeclaration/` | `EmployeeProofOfInvestmentServiceImpl.java` (2131 lines) | `getOrInitializePoi(...)` — L539 | Loads or builds the POI; the builder methods below create one item per declared line. |
| | | `createPoiMaster(...)` — L648 | The parent `employee_proof_of_investment` row. |
| | | `buildSection6AItems` — L678, `buildHouseRentItems` — L716, `buildOtherIncomeItems` — L756, `buildLetOutPropertyItems` — L796, `buildPrevEmploymentItems` — L837, `buildPreTaxDeductionItems` — L875 | One builder per declaration section. |
| | | `updatePoiItem(...)` — L915 | Employee edits an item's actual amount. |
| | | `uploadDocuments(...)` — L1033 | Cloudinary upload + `employee_poi_document` rows. |
| | | `submitPoi(...)` — L1163 | Validates completeness, flips status, sends notifications. |
| | | `withdrawPoi(...)` — L1328 | Back to draft if the window is still open. |
| | | `getAllPOISubmissions(...)` — L1370 | The admin dashboard query. |
| | | `getPoiForReview(...)` — L1476 | Full submission for the review screen. |
| | | `approvePoiItem(...)` — L1537 / `rejectPoiItem(...)` — L1683 | Per-item decisions with an approved amount. |
| | | `finalApprovePoi(...)` — L1761 / `finalRejectPoi(...)` — L1830 | Whole-submission decision. |
| | | **`considerPOIForIT(...)` — L1906** | Pushes approved amounts into the tax engine and triggers TDS recalculation. The bridge between this module and module 12. |
| | | `validateAmounts(declared, actual, approved)` — L224 | Guards approved ≤ actual. |
| | | `checkDeadlineForEmployee` — L109, `validateLastDateForPoi` — L164, `validateEmployeeEdit` — L138, `validatePoiStatus` — L216 | The window/status guard rails. |
| | | `addAdminComment(...)` — L242, `addCommentToPoiItem(...)` — L275, `getCommentsForPoiItem(...)` — L371 | Comment thread. |
| | | `canEmployeeResubmit(poi)` — L2121 | Resubmission rule after a rejection. |
| | | `sendSubmissionNotifications(...)` — L1880 | Brevo `brevo.template.poi.submission`. |
| `controller/employeeitdeclaration/` | `AdminProofOfInvestmentController.java` | `getAllPOISubmissions(...)` — L37 | `GET /api/admin/proof-of-investments/dashboard`. |
| | | `getPoiForReview(org, employee, fy)` — L239 | Review screen payload. |
| | | `approvePoiItem` / `rejectPoiItem` / `finalApprovePoi` / `finalRejectPoi` / `bulkAction` | The admin decisions. |
| | | `getCommentsForItem` / `addCommentToItem` | Admin side of the thread. |
| | | `considerForIT(employeeId, fy)` — L597 | Triggers the tax push. |
| `controller/taxCalculator/` | `POIController.java` | `getOverview(...)` — L45, `uploadDocument(...)` — L71, `updateDocument`, `viewDocument`, `deleteDocument`, `getAllDocuments` (admin), `updateDocumentStatus`, `getDownloadUrl` | An **older, simpler** document-only POI flow on `/api/proof-of-investment`. Still mounted; note the path collides conceptually with the settings controller of the same base path. |
| `controller/taxCalculator/` | `EmployeeInvestmentProofController.java` | `uploadProof(...)` — L46, `getEmployeeInvestmentProof(employeeId)` — L69 | Another legacy proof path (`/api/employee-investment-proof`). |
| `scheduler/` | `POIReminderScheduler.java` | (cron) | Emails employees with outstanding proofs. |
| | `ITDeclarationAutoLockScheduler.java` | (cron) | Locks the declaration window and notifies. |
| `controller/test/` | `POIReminderTestController.java`, `IncomeTaxDeclarationTestController.java` | `triggerScheduler()`, `testEmail(...)`, `testReminderEmail(...)`, `testLockEmail(...)` etc. | **Dev-only** endpoints under `/api/test/**` for firing the schedulers and emails by hand. Should not be exposed in production. |

**Tables:** `employee_investment_declaration`, `employee_inv_house_rent`, `employee_inv_home_loan`, `employee_inv_let_out_property`, `employee_inv_let_out_property_detail`, `employee_inv_other_income`, `employee_inv_pre_tax_deduction`, `employee_inv_prev_employment`, `employee_inv_section6a`, `employee_inv_tax_summary`, `employee_investment_proof`, `employee_investment_proof_file`, `proof_of_investment_document`, `employee_poi_item`, `employee_poi_item_comment`, `employee_poi_document`, `employee_poi_property_detail`, `reminder`.

---

## 14. Dashboard & Reports

| Folder | File | Function | What it does |
|---|---|---|---|
| `controller/dashboard/` | `DashboardController.java` | `getDashboardSummary(orgId)` — L39 | `GET /api/dashboard/summary` — headcount, current payrun, payroll cost tiles. |
| | | `getStatutorySummary(orgId, type, from, to)` — L62 | EPF / ESI / PT totals over a date range. |
| | | `getTdsSummary(orgId, from, to)` — L95 | TDS totals over a range. |
| `serviceimpl/dashboard/` | `DashboardServiceImpl.java` | `getDashboardSummary(orgId)` — L75 | Aggregates across employees, payruns and statutory tables. |
| | | `getStatutorySummary(...)` — L212 / `getTdsSummary(...)` — L251 | The two range queries. |
| `controller/payruns/` | `PayRunReportController.java` | `downloadPayRunReport` / `downloadSinglePayRunReport` | Excel exports (see module 11). |
| `controller/test/` | `ExcelTestController.java` | `generateTestExcelReport()` | Dev-only workbook smoke test. |
| `service` (Excel) | `ExcelReportService` | — | Shared workbook builder used by the report endpoints. |

---

## 15. HRMS Integration

The only cross-service call in the platform.

| Folder | File | Function | What it does |
|---|---|---|---|
| `config/` | `WebClientConfig.java` | (bean) | Non-blocking `WebClient` so the payrun thread is not held during the call. |
| `controller/` | `IntegrateWithHrms.java` | `requestHRMS()` | `GET /auth/test-payroll` — connectivity smoke test. |
| `serviceimpl/` | `IntegrateWithHrmsServiceImpl.java` | `fetchLeaves(List<String> emails, String payPeriod)` — L33 | `POST` to HRMS `/public/get-employee-leaves` with header `X-API-KEY: md5("12345AB")`; returns `Map<email, lopDays>`. |
| | | `getResponseFromHRMS()` — L25 | Smoke-test helper. |
| `util/` | `HashUtil.java` | (MD5) | Hashes the shared secret. |

> **Current state:** since the Leave Management module landed, the payrun's LOP figure is read from
> `employee_leave_balance_consumption` inside Payroll. `fetchLeaves` is still wired up. Confirm which
> source is authoritative for your environment before relying on either — the two can disagree.
>
> Historic type mismatch: HRMS returns fractional days (`double`); older Payroll code read `Integer`.
> `fetchLeaves` now returns `Map<String, Double>`, so the truncation noted in `PROJECT_OVERVIEW.md` is fixed on this branch.

---

## Appendix A — Known rough edges

| Where | Issue |
|---|---|
| `serviceimpl/statutorycomponents/ProfessionalTaxServiceImpl.java` | `resetToDefaultSlabs` and `getAllProfessionalTaxes` each appear **twice** (L624/L677, L668/L788) — merge residue. |
| `controller/employee/BasicDetailsController.java`, `CtcStructureController.java`, `EmployeePersonalDetailController.java` | Entire previous controller versions retained as comments above the live class. Read from the bottom. |
| `controller/employee/EmployyePortalContoller.java` | Filename and class name are misspelled. |
| `controller/leaveAndAttendance/` | Package was `leaveAndAttedance` historically; check spelling when grepping. |
| `controller/test/**` | Three test controllers expose scheduler triggers and email sends on `/api/test/**` with no separate guard. |
| `/api/proof-of-investment` | Mapped by **two** controllers with different semantics (`claimsanddeclarations/ProofOfInvestmentController` = org settings; `taxCalculator/POIController` = documents). |
| Leave import | Two parallel import mechanisms (module 8). |
| Tests | `src/test/java/com/itsdev/payroll/service/LeaveAllocationImportTest.java` is the only real test in the repo. |
| `application.properties` | Keycloak secrets, Cloudinary keys, Brevo key and `fed.secret` are committed in plaintext. |

## Appendix B — Running it

```bash
./mvnw spring-boot:run
```

Requires: MySQL reachable at the configured datasource, a Keycloak realm `HRMS` with client `react-app`,
and valid Cloudinary + Brevo keys. `ddl-auto=update` will create/alter tables on first boot.
