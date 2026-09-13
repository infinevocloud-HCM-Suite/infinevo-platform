# FEATURE MAP — Infinevo Cloud HRMS + Payroll Suite

> ⚠️ **LEGACY — frozen reference.** This describes the four applications being
> replaced, as frozen on 2026-09-13. It is **not** a specification for new work.
> Build against `docs/target-state/`. See `docs/legacy/README.md`.

> Last updated: 2026-09-11
> Source roots: `legacy/HRMS_Backend/`, `legacy/HRMS_Frontend/`, `legacy/Payroll-Bend-SBoot/`, `legacy/Payroll-Fend-react/`

---

## HRMS Backend (`HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/`)

### 1. Authentication & Password Management
| Item | Detail |
|---|---|
| **Feature** | JWT login, logout, password reset via email link |
| **Controller** | `controller/AuthController.java` |
| **Controller** | `controller/PasswordResetController.java` |
| **Service** | `service/UsersManagementService.java` |
| **Entity** | `entity/OurUsers.java`, `entity/PasswordResetToken.java` |
| **Repository** | `repository/UsersRepo.java`, `repository/PasswordResetTokenRepository.java` |
| **Config** | `config/JWTAuthFilter.java`, `config/SecurityConfig.java`, `config/HashUtil.java` |
| **Responsibilities** | Login with credentials → generate JWT access/refresh tokens; forgot-password flow sends reset link via email; `HashUtil.md5()` secures the cross-service API key. |

---

### 2. User Management & Role-Based Access Control (RBAC)
| Item | Detail |
|---|---|
| **Feature** | Create/update/delete users, manage roles, manage actions, assign role-action mappings |
| **Controller** | `controller/UserManagementController.java` |
| **Controllers** | `controller/useraccess/RoleController.java`, `controller/useraccess/ActionController.java`, `controller/useraccess/UserController.java` |
| **Services** | `service/useraccess/RoleService.java`, `service/useraccess/ActionService.java`, `service/useraccess/UserService.java` |
| **ServiceImpls** | `serviceimpl/useraccess/RoleServiceImpl.java`, `serviceimpl/useraccess/ActionServiceImpl.java`, `serviceimpl/useraccess/UserServiceImpl.java` |
| **Entities** | `entity/useraccess/Role.java`, `entity/useraccess/Action.java`, `entity/useraccess/UserActionMapping.java`, `entity/OurUsers.java` |
| **Migration** | `migration/UserRoleMigration.java` — seeds initial role/action data |
| **Bootstrap** | `bootstrap/` — startup data seeding |
| **Frontend** | `HRMS_Frontend/src/components/userManagement/` |
| **Frontend Routes** | `/user-management`, `/register`, `/update-user/:userId`, `/user-mapping/:userId/:name`, `/create-action`, `/list-actions`, `/edit-action/:actionId`, `/create-role`, `/list-roles`, `/roles/mapping/:roleId`, `/edit-role/:roleId` |
| **Responsibilities** | Fine-grained RBAC: users are assigned roles, roles are mapped to named action strings (e.g. `MANAGE_EMPLOYEES`, `ADD_ENTRY`). `ActionProtectedRoute` on the frontend enforces these. |

---

### 3. Employee Profile Management
| Item | Detail |
|---|---|
| **Feature** | Full employee lifecycle: create, view, edit, employee detail tabs (personal, contact, work, identification, documents) |
| **Controller** | `controller/EmployeeController.java` (35KB — primary) |
| **Service** | `service/UsersManagementService.java` |
| **Entities** | `entity/Employee.java`, `entity/OurUsers.java`, `entity/personal.java`, `entity/Contact.java`, `entity/Identification.java`, `entity/Work.java`, `entity/EmployeeDocument.java` |
| **Repositories** | `repository/EmployeeRepository.java`, `repository/UsersRepo.java` |
| **Cloudinary** | Documents and profile images are uploaded via Cloudinary |
| **Frontend** | `HRMS_Frontend/src/components/employee/` — `Employee.jsx`, `AddEmployee.jsx`, `EditEmployee.jsx`, `EmployeeDetails.jsx` |
| **Frontend Routes** | `/employees`, `/add-employee`, `/edit-employee/:id`, `/employee-details/:id` |
| **Responsibilities** | Manages the full employee profile including personal details, contact info, identification documents, and work information. Employee has an associated `OurUsers` login account. |

---

### 4. Attendance Management (Clock In / Clock Out)
| Item | Detail |
|---|---|
| **Feature** | Employee clock-in/out, daily attendance tracking |
| **Controller** | `controller/AttendanceController.java` |
| **Service** | `service/UsersManagementService.java` |
| **Entities** | `entity/Attendance.java`, `entity/ClockSession.java` |
| **Frontend** | `HRMS_Frontend/src/components/EmployeeDashboard/UserAttendence.jsx` |
| **Frontend (Admin)** | `HRMS_Frontend/src/components/adminDashboard/AdminAttendance.jsx` |
| **Frontend Routes** | `/attendance`, `/my-attendance` |
| **Responsibilities** | Tracks daily attendance with clock-in/out sessions. Admin can view all employee attendance records. |

---

### 5. Leave Management
| Item | Detail |
|---|---|
| **Feature** | Apply leaves, approve/reject (manager & HR dual-approval), leave balance management, leave types, holidays |
| **Controller** | `controller/LeaveRequestController.java` (14KB) |
| **Controller** | `controller/LeaveTypeController.java`, `controller/LeaveBalanceController.java`, `controller/HolidayController.java` |
| **Service** | `service/leaverequest/LeaveRequestsService.java` |
| **ServiceImpl** | `serviceimpl/leaverequest/LeaveRequestServiceImpl.java` (37KB — primary business logic) |
| **Entities** | `entity/LeaveRequests.java` (8.5KB), `entity/LeaveRequest.java`, `entity/LeaveType.java`, `entity/LeaveBalance.java`, `entity/EmployeeLeaveBalance.java`, `entity/EmployeeMonthlyLop.java`, `entity/LeaveDocument.java`, `entity/Holiday.java` |
| **Repositories** | `repository/leaverequest/LeaveRequestRepo.java`, `repository/EmployeeLeaveBalanceRepo.java`, `repository/EmployeeMonthlyLopRepository.java` |
| **Frontend (Employee)** | `HRMS_Frontend/src/components/Leaves/Leave.jsx`, `LeaveBalance.jsx`, `HolidayUser.jsx`, `OverTimeForm.jsx` |
| **Frontend (Admin)** | `HRMS_Frontend/src/components/adminDashboard/LeaveRequest.jsx`, `AdminLeaveBalance.jsx`, `EmployeeLeaves.jsx`, `EmployeeLeaveDetails.jsx`, `LeaveType.jsx` |
| **Frontend Routes** | `/leaves`, `/leaves/:id`, `/leaves-type`, `/leave-balance`, `/apply-leaves`, `/my-leave-balance`, `/employee-leave-details`, `/employee-leaves/:employeeId` |
| **Responsibilities** | Two-stage approval (Reporting Manager → HR). Dual-entity design (`LeaveRequest` / `LeaveRequests`). LOP days accumulated per employee-month in `EmployeeMonthlyLop` table, which is queried by the Payroll integration API. |

---

### 6. Overtime Request Management
| Item | Detail |
|---|---|
| **Feature** | Employee overtime requests, approval workflow |
| **Controller** | `controller/OvertimeRequestController.java` |
| **Entity** | `entity/OvertimeRequest.java` |
| **Frontend** | `HRMS_Frontend/src/components/adminDashboard/OvertimeRequest.jsx`, `HRMS_Frontend/src/components/Leaves/OverTimeForm.jsx` |
| **Frontend Routes** | `/overtime-request`, `/overtime-form` |

---

### 7. Timesheet Management (HRMS Module)
| Item | Detail |
|---|---|
| **Feature** | Employee timesheet entry (project/task hours), submit, approve/reject by manager, notifications |
| **Controllers** | `controller/TimesheetController.java` (12KB), `controller/timesheet/TimesheetsController.java` (28KB — primary), `controller/timesheet/TimesheetsNotificationController.java` |
| **Service** | `service/TimesheetService.java`, `service/timesheet/TimesheetService.java` |
| **ServiceImpls** | `serviceimpl/timeshhet/TimesheetServiceImpl.java` (45KB — main logic), `serviceimpl/timeshhet/TimesheetsNotificationServiceImpl.java` |
| **Entities** | `entity/Timesheet.java` (8KB), `entity/timesheet/Timesheets.java`, `entity/timesheet/DayEntry.java`, `entity/timesheet/ProjectEntry.java`, `entity/timesheet/TaskEntry.java`, `entity/timesheet/TimesheetsNotification.java` |
| **Repositories** | `repository/TimesheetRepo.java`, `repository/timesheet/TimesheetsRepo.java` |
| **Frontend (Employee)** | `HRMS_Frontend/src/components/EmployeeDashboard/TimesheetForm.jsx`, `TimesheetDetailPage.jsx`, `TimesheetViewPage.jsx`, `TimesheetEditForm.jsx` |
| **Frontend (Admin/Manager)** | `HRMS_Frontend/src/components/adminDashboard/AdminTimesheetManagement.jsx`, `TimesheetDetailView.jsx` |
| **Frontend (Manager)** | `HRMS_Frontend/src/components/managerDashboard/ManagerTimesheetManagement.jsx`, `ManagerTimesheetDetailView.jsx` |
| **Frontend (Reporting Mgr)** | `HRMS_Frontend/src/components/reportingmanagerDashboard/ReportingManagerTimesheetManagement.jsx`, `ReportingManagerTimesheetDetailView.jsx` |
| **Frontend Routes** | `/my-timesheet`, `/my-timesheet-detail`, `/my-timesheet-view`, `/edit-my-timesheet/:timesheetId`, `/timesheets`, `/timesheets/:id`, `/my-team-timesheets`, `/my-reporting-team-timesheets` |
| **Responsibilities** | Dual-implementation pattern (older `Timesheet.*` and newer `Timesheets.*`). Newer implementation uses nested `ProjectEntry → DayEntry → TaskEntry` composite structure. |

---

### 8. Timesheet Notification & Reminder Settings
| Item | Detail |
|---|---|
| **Feature** | Configurable email reminders for timesheet submission (per role), escalation, approval reminders |
| **Controller** | `controller/NotificationController.java`, `controller/notificationconfig/NotificationSettingsController.java`, `controller/RoleReminderConfigController.java` |
| **Services** | `service/notificationconfig/NotificationSettingsService.java`, `service/schedular/NotificationSchedularService.java` |
| **ServiceImpls** | `serviceimpl/notificationconfig/NotificationSettingsServiceImpl.java`, `serviceimpl/schedular/NotificationSchedularServiceImpl.java` (30KB) |
| **Entities** | `entity/notificationconfig/ApprovalReminder.java`, `EmployeeReminder.java`, `EscalationReminder.java`, `HrReminder.java`, `SupervisorReminder.java`, `entity/RoleReminderConfig.java`, `entity/Notification.java` |
| **Scheduler** | `scheduler/` — Spring `@Scheduled` tasks trigger email notifications |
| **Frontend** | `HRMS_Frontend/src/components/adminDashboard/NotificationSettings.jsx` |
| **Frontend Route** | `/timesheets/notifications` |

---

### 9. Project & Task Management
| Item | Detail |
|---|---|
| **Feature** | Create/manage projects, assign tasks, employee self-service view |
| **Controllers** | `controller/ProjectController.java`, `controller/TaskController.java`, `controller/AssignmentController.java` |
| **Service** | `service/ProjectService.java`, `service/TaskService.java` |
| **Entities** | `entity/Project.java`, `entity/Task.java`, `entity/Assignment.java` |
| **Frontend** | `HRMS_Frontend/src/components/Projects/Project.jsx`, `Task.jsx` |
| **Frontend (Employee)** | `HRMS_Frontend/src/components/EmployeeDashboard/MyProject.jsx`, `MyTask.jsx` |
| **Frontend Routes** | `/projects`, `/projects/tasks`, `/myproject`, `/mytask` |

---

### 10. Dashboards & Reporting
| Item | Detail |
|---|---|
| **Feature** | Role-based dashboards: Admin, HR, Manager, Supervisor, Reporting Manager, Employee |
| **Controllers** | `controller/HrDashboardController.java`, `controller/ManagerDashboardController.java`, `controller/EmployeeDashboardController.java` |
| **Frontend** | `HRMS_Frontend/src/components/adminDashboard/Dashboard.jsx`, `hrDashboard/HrDashboard.jsx`, `managerDashboard/ManagerDashboard.jsx`, `supervisorDashboard/SupervisorDashboard.jsx`, `reportingmanagerDashboard/ReportingManagerDashboard.jsx`, `EmployeeDashboard/EmployeeDashboard.jsx` |
| **Role Routing** | `HRMS_Frontend/src/App.jsx` — `DashboardWrapper` component dynamically renders dashboard by `activeRole` from context |
| **Report Entity** | `entity/Report.java` |

---

### 11. HRMS → Payroll Integration API (Cross-Service)
| Item | Detail |
|---|---|
| **Feature** | Expose LOP (Loss-of-Pay) leave data to Payroll service for net-pay deduction calculation |
| **Controller** | `controller/IntegrateWithPayroll.java` — `POST /public/get-employee-leaves` |
| **Security** | `X-API-KEY` header required; value must equal MD5 hash of `security.api.key` (`12345AB`) |
| **Logic** | Accepts list of employee emails + pay period string (e.g. `"July 2025"`). Queries `EmployeeMonthlyLop` by employee, year, month. Returns `totalLopDays` per employee. |
| **Entities** | `entity/EmployeeMonthlyLop.java`, `entity/OurUsers.java` (email → empId lookup) |
| **Config** | `config/HashUtil.java` (MD5 utility) |

---

## Payroll Backend (`Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/`)

### 12. Payroll Authentication (Keycloak)
| Item | Detail |
|---|---|
| **Feature** | OAuth2/Keycloak JWT resource server, Keycloak Admin API for user creation |
| **Config** | `config/SecurityConfig.java`, `config/WebClientConfig.java`, `config/KeycloakAdminConfig.java`, `config/KeycloakClientProvider.java` |
| **Service** | `service/keycloak/KeycloakUserService.java`, `serviceimpl/keycloak/KeycloakUserServiceImpl.java` |
| **Entity** | `entity/CompanyUser.java`, `entity/OrganizationUserMapping.java`, `entity/OrganizationUserRoleMapping.java` |
| **Controller** | `controller/CompanyUserController.java`, `controller/OrganizationUserRoleMappingController.java`, `controller/RoleActionController.java` |
| **Properties** | `keycloak.admin.*` in `application.properties`; realm = `HRMS`; client = `hrms-payroll-backend` |

---

### 13. Organization Setup
| Item | Detail |
|---|---|
| **Feature** | Create organization, departments, designations, work locations, income tax details, role management |
| **Controllers** | `controller/organization/OrganizationController.java` (7.7KB), `DepartmentController.java`, `DesignationController.java`, `WorkLocationController.java`, `OrganizationRoleController.java`, `UserInvitationController.java`, `IncomeTaxDetailsController.java` |
| **Services** | `service/organization/OrganizationService.java`, `DepartmentService.java`, etc. |
| **Entities** | `entity/organization/Organization.java` (12KB — primary, many fields), `Department.java`, `Designation.java`, `WorkLocation.java`, `OrganizationRole.java`, `UserInvitation.java`, `IncomeTaxDetails.java` |
| **Setup Steps** | `entity/OrgSetupSteps.java` — tracks onboarding setup completion per organization |
| **Controller** | `controller/OrgSetupStepsController.java`, `controller/PayScheduleController.java` |
| **Frontend** | `Payroll-Fend-react/src/pages/mainPages/organizationRegister/` — `index.js` (create), `setupNewOrganization.js` (wizard), `manageOrganization.js` (switch/delete) |
| **Frontend (Settings)** | `Payroll-Fend-react/src/pages/mainPages/allSettingsPages/profile.js`, `workLocations.js`, `designations/` |
| **Frontend Routes** | `/all-settings`, `work-locations`, `work-locations/new`, `work-locations/edit/:workLocationId`, `designations`, `/create-new-organization`, `/setup-new-organization/:organizationId`, `/manage-organization` |

---

### 14. Employee Onboarding & Profiles (Payroll)
| Item | Detail |
|---|---|
| **Feature** | Add employee basic details, personal details, bank details, CTC structure, send invitations |
| **Controllers** | `controller/employee/BasicDetailsController.java` (14KB), `CtcStructureController.java`, `EmployeeBankDetailController.java`, `EmployeePersonalDetailController.java`, `EmployeeInvitationController.java`, `EmployyePortalContoller.java` |
| **Preview** | `controller/employee/preview/OrgStatutoryController.java` |
| **Services** | `service/employee/BasicDetailsService.java`, etc. |
| **Entities** | `entity/employee/BasicDetails.java` (13KB — primary), `CtcStructure.java`, `CtcEpfComponent.java`, `CtcEsiComponent.java`, `EmployeeBankDetail.java`, `EmployeePersonalDetail.java`, `ResidentialAddress.java`, `EmployeeInvitation.java`, `FbpComponent.java`, `EmployeeEarning.java`, `EmployeeBenefit.java`, `EmployeeReimbursement.java`, `VariableEarning.java` |
| **Frontend** | `Payroll-Fend-react/src/pages/mainPages/dashboardPage/index.js` (largest file — 271KB) |
| **Onboarding Status** | `entity/leaveAndAttedance/onboarding/OnboardingStatus.java`, `controller/leaveAndAttendance/onboarding/OnboardingStatusController.java` |

---

### 15. Salary Components (Payroll)
| Item | Detail |
|---|---|
| **Feature** | Configure earnings, deductions, benefits, and reimbursements at org level |
| **Controllers** | `controller/salarycomponents/EarningController.java`, `DeductionController.java`, `BenefitController.java`, `ReimbursementController.java` |
| **Entities** | `entity/salarycomponents/Earning.java` (12KB), `Deduction.java`, `Benefit.java`, `Reimbursement.java` |
| **Services** | `service/salarycomponents/EarningService.java`, etc. |

---

### 16. Statutory Components (Payroll)
| Item | Detail |
|---|---|
| **Feature** | Configure EPF, ESI, Professional Tax (slab-based), manage history and org-level overrides |
| **Controllers** | `controller/statutorycomponents/EpfController.java`, `EsiController.java`, `ProfessionalTaxController.java` |
| **Entities** | `entity/statutorycomponents/Epf.java` (11KB), `Esi.java`, `ProfessionalTax.java`, `SlabDetail.java`, `SlabRateConfiguration.java`, `OrgPTOverride.java`, `PTHistory.java`, `TaxSlabDetailHistory.java` |
| **Services** | `service/statutorycomponents/EpfService.java`, `EsiService.java`, `ProfessionalTaxService.java` |

---

### 17. Pay Run Processing (Payroll)
| Item | Detail |
|---|---|
| **Feature** | Create and process monthly pay runs, calculate net pay per employee including LOP deductions from HRMS |
| **Controllers** | `controller/payruns/PayRunController.java` (6KB), `EmployeePayRunController.java` (7KB) |
| **Services** | `service/payruns/PayRunService.java`, `EmployeePayRunService.java` |
| **ServiceImpl** | `serviceimpl/payruns/EmployeePayRunServiceImpl.java` (60KB — primary net-pay calculation logic) |
| **Entities** | `entity/payruns/PayRun.java` (17KB), `EmployeePayRun.java` (6.5KB) |
| **Integration Point** | `EmployeePayRunServiceImpl.java:633` — calls `IntegrateWithHrmsService.fetchLeaves()` to get LOP data before calculating net pay. Deduction = `(monthlySalary / paidDays) * lopDays`. |
| **Off-Cycle Pay Run** | `controller/payRun/offCyclePayrun/OffCyclePayRunController.java`, `entity/payRun/offCyclePayrun/OffCyclePayRun.java` |
| **One-Time Payout** | `controller/payRun/oneTimePayout/OneTimePayoutController.java`, `entity/payRun/oneTimePayout/OneTimePayout.java` |
| **Pay Schedule** | `entity/PaySchedule.java`, `controller/PayScheduleController.java` |

---

### 18. Tax Calculation & Proof of Investment (Payroll)
| Item | Detail |
|---|---|
| **Feature** | Income tax calculation, employee proof-of-investment submission and review |
| **Controllers** | `controller/taxCalculator/POIController.java` (12.5KB — main), `EmployeeInvestmentProofController.java` |
| **Entities** | `entity/taxCalculator/ProofOfInvestmentDocument.java`, `EmployeeInvestmentProof.java`, `EmployeeInvestmentProofFile.java` |

---

### 19. Claims & Declarations (Payroll)
| Item | Detail |
|---|---|
| **Feature** | Flexible Benefit Plan (FBP), Income Tax Declaration, Proof of Investment, Reimbursement Claims |
| **Controllers** | `controller/claimsanddeclarations/FBPController.java`, `IncomeTaxDeclarationController.java`, `ProofOfInvestmentController.java`, `ReimbursementClaimController.java` |
| **Entities** | `entity/claimsanddeclarations/FBP.java`, `IncomeTaxDeclaration.java`, `ProofOfInvestment.java`, `ReimbursementClaim.java`, `Reminder.java` |

---

### 20. Leave & Attendance Settings (Payroll)
| Item | Detail |
|---|---|
| **Feature** | Configure leave types, holidays, attendance preferences, import leave data via CSV |
| **Controllers** | `controller/leaveAndAttendance/LeaveTypeController.java`, `attendance/AttendancePreferenceController.java`, `holiday/HolidayController.java`, `leaveImport/EmployeeLeaveImportController.java`, `preferences/PreferencesController.java` |
| **Entities** | `entity/leaveAndAttedance/LeaveType.java` (14KB), `attendance/AttendancePreference.java`, `holiday/Holiday.java`, `leaveImport/EmployeeLeaveImport.java`, `preferences/Preferences.java` |
| **CSV Import** | Apache Commons CSV used for bulk leave data import |

---

### 21. Payroll → HRMS Integration (Cross-Service)
| Item | Detail |
|---|---|
| **Feature** | Payroll service calls HRMS to fetch employee LOP data before processing pay runs |
| **Controller** | `controller/IntegrateWithHrms.java` — `GET /auth/test-payroll` (health check) |
| **Service** | `service/IntegrateWithHrmsService.java` |
| **ServiceImpl** | `serviceimpl/IntegrateWithHrmsServiceImpl.java` — uses Spring WebClient |
| **Config** | `config/WebClientConfig.java` — configures WebClient with base URL (`api.base.url`) and attaches MD5-hashed `X-API-KEY` header automatically |
| **Called From** | `serviceimpl/payruns/EmployeePayRunServiceImpl.java` — during pay run finalization |

---

### 22. Dashboard (Payroll)
| Item | Detail |
|---|---|
| **Feature** | Payroll summary statistics |
| **Controller** | `controller/dashboard/DashboardController.java` |
| **Frontend** | `Payroll-Fend-react/src/pages/mainPages/dashboardPage/index.js` |
| **Frontend Route** | `/dashboard` |

---

### 23. Reimbursement Claims Workflow (FEAT-001)
| Item | Detail |
|---|---|
| **Feature** | Submit reimbursement request, upload receipt to Cloudinary, search/filter, and approve/reject claims |
| **Controllers** | `controller/employeereimbursement/EmployeeReimbursementController.java`, `AdminReimbursementController.java` |
| **Service** | `service/employeereimbursement/EmployeeReimbursementService.java` |
| **ServiceImpl** | `serviceimpl/employeereimbursement/EmployeeReimbursementServiceImpl.java` |
| **Entity** | `entity/employeereimbursement/EmployeeReimbursementRequest.java` |
| **Enumerations** | `enumeration/employeereimbursement/ReimbursementStatus.java`, `ReimbursementPaymentStatus.java`, `ReimbursementType.java` |
| **Repository** | `repository/employeereimbursement/EmployeeReimbursementRequestRepository.java` |
| **Frontend** | `Payroll-Fend-react/src/pages/mainPages/userPortal/Reimbursement/`, `adminReimbursement/` |
| **Frontend Routes** | `/reimbursement`, `/admin-reimbursements` |
| **Responsibilities** | Handles employee reimbursement claims lifecycle. Integrates with Cloudinary for file upload and storage, uses enum mapping for status and types, and scopes requests by tenant `organizationId`. |

---

### 24. Employee Ad-Hoc Deduction Ledger (FEAT-002)
| Item | Detail |
|---|---|
| **Status** | **Superseded — see #27.** This entry previously listed the *planned* design (`controller/claimsanddeclarations/EmployeeAdHocDeductionController.java`, `entity/claimsanddeclarations/EmployeeAdHocDeduction.java`, `pages/mainPages/deductions/AdminDeductionLedger.js`, route `/admin/ad-hoc-deductions`). **None of those files or routes exist.** The feature shipped as `SalaryDeduction*` under `controller/employee/`, table `employee_deduction`, screens under `pages/mainPages/deduction/`, routes `/employee-deductions` and `/employee-deductions/bulk-add`. Corrected 2026-09-11. |

---

### 25. Reimbursement & Deduction Payroll Integration (FEAT-003 & FEAT-004)
| Item | Detail |
|---|---|
| **Feature** | Sync approved claims as non-taxable earnings and net ledger deductions during active pay run calculation |
| **Trigger Point** | `serviceimpl/payruns/EmployeePayRunServiceImpl.java` — inside pay run generation and net pay routines |
| **Data Scope** | Queries `employee_reimbursement_request` (status: approved/partially approved) and `employee_deduction` matching active pay run month and orgId. |
| **Responsibilities** | Subtracts ad-hoc deductions and adds approved reimbursements to determine final net pay value before finalization. |


---

### 26. Leave Allocation & Consumption (Leave Management)
| Item | Detail |
|---|---|
| **Feature** | Per-employee annual leave entitlement by leave type and year, monthly accrual breakdown, expiry, Excel/CSV bulk import, and consumption ("mark leave taken") that derives LOP/LWP |
| **Controllers** | `controller/leave/EmployeeLeaveAllocationController.java`, `controller/leave/EmployeeLeaveConsumptionController.java` |
| **Service** | `service/leave/EmployeeLeaveAllocationService.java`, `service/leave/EmployeeLeaveConsumptionService.java` |
| **ServiceImpl** | `serviceimpl/leave/EmployeeLeaveAllocationServiceImpl.java` (1,878 lines), `serviceimpl/leave/EmployeeLeaveConsumptionServiceImpl.java` |
| **Entity** | `entity/leave/EmployeeLeaveAllocation.java`, `entity/leave/EmployeeLeaveBalanceConsumption.java` |
| **Tables** | `employee_leave_allocation` (unique on `organization_id`, `employee_id`, `leave_type`, `year`), `employee_leave_balance_consumption` |
| **Repository** | `repository/leave/EmployeeLeaveAllocationRepository.java`, `EmployeeLeaveBalanceConsumptionRepository.java` |
| **DTOs** | `dto/leave/` — `BulkLeaveAllocationRequestDTO`, `EmployeeLeaveAllocationRequestDTO` / `ResponseDTO`, `EmployeeLeaveConsumptionRequestDTO` / `ResponseDTO`, `LeaveAllocationImportResultDTO`, `ImportRowErrorDTO`, `LeaveAllocationItemDTO`, `LeaveEntryDTO` |
| **API** | `/api/leave-allocation` (+ `/bulk`, `/import`, `/{employeeId}`, `/{employeeId}/type/{leaveType}`), `/api/leave-consumption` (+ `/{employeeId}`, `/{employeeId}/entry/{entryId}`) |
| **Frontend** | `Payroll-Fend-react/src/pages/mainPages/leaveManagement/leaveAllocation/leaveAllocation.jsx`, `markLeaves/markLeavesOverviewPage.jsx`, `markLeaves/markLeavesTakenPage.jsx`, `markLeaves/markLeaveAddEmploy.jsx`, `shared/services/leaveStore.js` |
| **Frontend Routes** | `/leave-allocation`, `/mark-leaves`, `/mark-leaves-taken`, `/mark-leave-taken`, `/mark-leave-add-employee`, `/markleaveaddemploy` |
| **Tests** | `src/test/java/com/itsdev/payroll/service/LeaveAllocationImportTest.java` — the only automated test in the platform |
| **Responsibilities** | Source of truth for LOP. `recalculateConsumptionBalances()` re-derives consumed / remaining / LOP across all months after any change. Pay run reads `employee_leave_balance_consumption` for LOP days, replacing the earlier HRMS `fetchLeaves` path. |

### 27. Employee Ad-Hoc Salary Deductions
| Item | Detail |
|---|---|
| **Feature** | One-off deductions (recovery, advance, damages) outside the CTC, added per employee via a bulk grid with optional proof upload, consumed by the pay run |
| **Controller** | `controller/employee/SalaryDeductionController.java` |
| **Service** | `service/employee/SalaryDeductionService.java` |
| **ServiceImpl** | `serviceimpl/employee/SalaryDeductionServiceImpl.java` |
| **Entity** | `entity/SalaryDeduction.java` |
| **Table** | `employee_deduction` |
| **Enumeration** | `enumeration/DeductionStatus.java` (includes `INPAYRUN`) |
| **Mapper** | `mapper/employee/SalaryDeductionMapper.java` |
| **Repository** | `repository/employee/SalaryDeductionRepository.java` |
| **API** | `/api/employee-deductions` (+ `/grid`, `/upload`, `/{id}`, `/my-deductions`) |
| **Frontend** | `Payroll-Fend-react/src/pages/mainPages/deduction/deduction.js`, `deduction/gridDeduction.js`, `userPortal/myDeductions/myDeductions.jsx` |
| **Frontend Routes** | `/employee-deductions`, `/employee-deductions/bulk-add`, `/my-deductions` |
| **Responsibilities** | Proof files go to Cloudinary via `CloudinaryServiceImpl.uploadFile()`. `validateEmployeeBelongsToOrg()` enforces tenant scoping on every write. Status moves to `INPAYRUN` once a pay run consumes the row, blocking further edits and deletes. |

---

---

## HRMS Frontend (`HRMS_Frontend/src/`)
| Folder | Role |
|---|---|
| `components/auth/` | Login, registration forms |
| `components/context/` | `ContextProvider.jsx` (global auth context, roles, activeRole), `ProtectedRoute.jsx`, `ActionProtectedRoute.jsx` |
| `components/Sidebar/` | Sidebar navigation |
| `components/adminDashboard/` | All admin-accessible management screens |
| `components/hrDashboard/` | HR-specific dashboard |
| `components/managerDashboard/` | Manager dashboard + team timesheets |
| `components/supervisorDashboard/` | Supervisor dashboard |
| `components/reportingmanagerDashboard/` | Reporting manager dashboard |
| `components/EmployeeDashboard/` | Employee self-service: timesheet, attendance |
| `components/employee/` | Employee CRUD screens |
| `components/Leaves/` | Leave application, balance, holidays |
| `components/Projects/` | Project/task management |
| `components/userManagement/` | RBAC management |
| `components/service/` | API service layer (Axios calls) |
| `components/config/` | API base URL configuration |
| `components/common/` | Shared UI components |
| `axiosInterceptor.js` | Global Axios interceptor (adds JWT token to headers) |
| `pages/ChooseRole.jsx` | Multi-role selector page (shown when user has multiple roles) |

---

---

## Payroll Frontend (`Payroll-Fend-react/src/`)

> Added 2026-09-11. This application was absent from earlier revisions of this document.
> React 18 (CRA) · Ant Design 5 · Redux Toolkit · Keycloak SSO · ECharts · Formik/Yup.
> 216 js/jsx files, ~116k LOC, 158 route entries, all declared in `pages/pageLayouts/router.js`.

### Folder roles
| Folder | Role |
|---|---|
| `pages/authPages/login/` | Login, employee-portal login, create account, forgot / new password |
| `pages/mainPages/acceptInvite/` | Public invitation accept / reject page |
| `pages/mainPages/dashboardPage/` | Admin dashboard (`index.js`) and onboarding checklist (`onboardingDashboard.js`) |
| `pages/mainPages/organizationRegister/` | Create org, setup wizard, manage / switch organizations |
| `pages/mainPages/employee/` | Employee list, 4-step add wizard, detail tabs, section edits, imports, salary revision |
| `pages/mainPages/payRuns/` | Pay run list, preview, summary, payslip, off-cycle, one-time payout |
| `pages/mainPages/approval/` | POI review, salary-revision approval queue, unsubmitted chase list |
| `pages/mainPages/leaveManagement/`, `pages/mainPages/markLeaves/` | Leave allocation and mark-leave-taken screens |
| `pages/mainPages/deduction/` | Ad-hoc deduction list and bulk-add grid |
| `pages/mainPages/adminReimbursement/` | Admin reimbursement approval queue |
| `pages/mainPages/taxesAndForms/` | Tax calculator, Form 16 |
| `pages/mainPages/allSettingsPages/` | Org profile, work locations, departments, designations, salary components, statutory components, leave / attendance, users & roles, pay schedules, claims & declarations |
| `pages/mainPages/userPortal/` | Employee self-service: home, profile, salary, payslips, reimbursement, deductions, investments, POI, tax calculator |
| `pages/userProfileSettings/` | Account and security tabs |
| `pages/pageLayouts/` | `router.js` plus four layouts: `sidebarLayout/` (admin), `settingsLayout/`, `employeeLayout/` (portal), `authLayout/` |
| `shared/guards/` | `authGuard.js`, `loginGuard.js`, `organizationGuard.js` |
| `shared/helpers/` | `axiosInterceptor.js`, `rootLoader.js`, `tokenHelper.js`, `msgHelper.js`, `resolveAdminLandingPath.js`, `addEmployeeDraft.js` |
| `shared/services/` | `authService.js`, `invitationService.js`, `reimbursementService.js`, `leaveStore.js` |
| `shared/redux/` | Store plus `authReducer`, `globalReducer`, `settingsModalReducer` |
| `shared/organization/` | `OrganizationContext.js`, `useOrganizationResolver.js` |
| `shared/appConfig/` | `globalConst.js` (API / auth URLs, Keycloak realm `HRMS`, client `react-app`), `keycloak.js`, static country / state / timezone reference data |
| `shared/components/` | Loaders, no-data states, settings modal, reimbursement widgets |

### Route groups (guard → layout)
| Guard | Layout | Routes |
|---|---|---|
| `AuthGuard(admin)` → `OrganizationGuard` | `sidebarLayout` | `/dashboard`, `employees*`, `/payruns`, `/preview/:payrunId`, `/summary/:payrunId`, `/view-payslip/:payrunId/:employeeId`, `/leave-allocation`, `/mark-leaves*`, `/employee-deductions*`, `/admin-reimbursements`, `/proof-of-investment*`, `/salary-revision-approvals`, `/form16s*`, `/tax-calculator` |
| `AuthGuard(admin)` → `OrganizationGuard` | `settingsLayout` | `/all-settings`, `organisation-profile`, `work-locations*`, `departments*`, `designations*`, `salary-components*`, `statutory-components*`, `/leave-types*`, `/holidays*`, `/attendance`, `users*`, `roles*`, `pay-schedules*`, `/it-declaration` |
| `AuthGuard(admin)` | org layout | `/create-new-organization`, `/manage-organization`, `/setup-new-organization/:organizationId` |
| `AuthGuard(employee)` | `employeeLayout` | `/home`, `/userProfile`, `/user-salary-details`, `/payslips/:payrunId`, `/reimbursement`, `/my-deductions`, `/user-investment*`, `/user-poi`, `/user-tax-calculator`, `/change-password` |
| `LoginGuard` | `authLayout` | `/login`, `/employeePortalLogin`, `/forgot-password`, `/new-password`, `/create-new-account` |
| *(none)* | plain | `/accept-invite`, `/public/payslips/:payrunId/:employeeId` |

### Frontend conventions
| Item | Detail |
|---|---|
| **API access** | No service layer for most modules — screens call `axios` directly against `GlobalConst.API_URL`, reading `organizationId` and the token `__t` from `localStorage` inline. Only `authService`, `invitationService`, `reimbursementService` and `leaveStore` are abstracted |
| **Forms** | Formik + Yup throughout |
| **Notifications** | SweetAlert2 via `shared/helpers/msgHelper.js` (`successMsg` / `errorMsg`), not Ant Design `message` |
| **Auth** | Keycloak token attached by `shared/helpers/axiosInterceptor.js`; silent refresh via `getNewToken()` |
| **Lint** | No `lint` script in `package.json` — CRA's `eslintConfig` is embedded. Use `npx eslint src --ext .js,.jsx` |
