# DB SCHEMA — Infinevo Cloud HRMS + Payroll Suite

> Last updated: 2026-08-12
> Strategy: `spring.jpa.hibernate.ddl-auto=update` (Hibernate manages schema evolution)
> No Flyway/Liquibase migration scripts — schema is code-first via JPA annotations.

---

## Database Overview

| Database | Host | Service |
|---|---|---|
| `hrmstestdb` | `64.227.128.119:3306` | HRMS Backend |
| `payrollDB` | `157.245.96.121:3306` | Payroll Backend (dev) |
| `payroll_test_db` | `64.227.128.119:3306` | Payroll Backend (prod) |

---

## Table naming rules (important)

The two services resolve table names **differently**. Names in this document are the
real names Hibernate creates.

| Service | Setting | Effect |
|---|---|---|
| **Payroll** | `spring.jpa.hibernate.naming.physical-strategy=PhysicalNamingStrategyStandardImpl` | No snake_case conversion. An entity without `@Table` becomes a table named **exactly** like the class — e.g. `FBP`, `IncomeTaxDeclaration`, `ProofOfInvestment`. Explicit `@Table` names are mixed style: `workLocations`, `paySchedule`, `hraRuleMaster` |
| **HRMS** | *(no strategy set — Spring Boot default)* | CamelCase → snake_case. `EmployeeDocument` → `employee_document`, `PasswordResetToken` → `password_reset_token` |

> Earlier revisions of this document listed idealised snake_case names
> (`our_users`, `pay_run`, `work_location`). Those tables do not exist under those
> names. 38 headings were corrected on 2026-09-11.

---

# HRMS Database — `hrmstestdb`

Entity source: `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/`

---

## Core User & Auth Tables

### `ourusers`
Entity: `entity/OurUsers.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `email` | VARCHAR | Unique, login credential |
| `password` | VARCHAR | BCrypt hashed |
| `emp_id` | VARCHAR | Links to employee record |
| `name` | VARCHAR | Display name |
| `city` | VARCHAR | |
| `role` | VARCHAR | Legacy single-role field |
| `roles` | JSON / collection | Multi-role support |

> **Relations**: One user → many `UserActionMapping`, Many users → many `Role` (via role table)

### `password_reset_token`
Entity: `entity/PasswordResetToken.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `token` | VARCHAR | UUID reset token |
| `user_id` | BIGINT FK → `our_users.id` | |
| `expiry_date` | DATETIME | Token expiry |

---

## User Access & RBAC Tables

### `role` (useraccess)
Entity: `entity/useraccess/Role.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `role_name` | VARCHAR | e.g. ADMIN, HR, MANAGER |
| `description` | VARCHAR | |

### `action` (useraccess)
Entity: `entity/useraccess/Action.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `action_name` | VARCHAR | e.g. MANAGE_EMPLOYEES, ADD_ENTRY |
| `description` | VARCHAR | |

### `user_action_mapping`
Entity: `entity/useraccess/UserActionMapping.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` | BIGINT FK → `our_users.id` | |
| `action_id` | BIGINT FK → `action.id` | |

---

## Employee Profile Tables

### `employee`
Entity: `entity/Employee.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `emp_id` | VARCHAR | Unique employee ID |
| `name`, `email` | VARCHAR | |
| `department`, `designation` | VARCHAR | |
| `joining_date` | DATE | |
| `employment_type` | VARCHAR | |
| Various profile fields | | |

### `personal`
Entity: `entity/personal.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `emp_id` | VARCHAR FK | |
| `date_of_birth`, `gender`, `nationality` | | |
| `marital_status` | VARCHAR | |

### `contact`
Entity: `entity/Contact.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `emp_id` | VARCHAR FK | |
| `mobile`, `alternate_mobile` | VARCHAR | |
| `personal_email` | VARCHAR | |
| Address fields (permanent, current) | | |

### `identification`
Entity: `entity/Identification.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `emp_id` | VARCHAR FK | |
| `aadhar_number`, `pan_number` | VARCHAR | |
| `passport_number`, `uan_number` | VARCHAR | |
| Cloudinary URL fields for document images | | |

### `work`
Entity: `entity/Work.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `emp_id` | VARCHAR FK | |
| Work-related fields (reporting manager, location, etc.) | | |

### `employee_document`
Entity: `entity/EmployeeDocument.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `emp_id` | VARCHAR FK | |
| `document_name` | VARCHAR | |
| `document_url` | VARCHAR | Cloudinary URL |
| `upload_date` | DATE | |

---

## Attendance Tables

### `attendance`
Entity: `entity/Attendance.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `emp_id` | VARCHAR FK | |
| `date` | DATE | |
| `status` | VARCHAR | PRESENT / ABSENT / etc. |
| `clock_in`, `clock_out` | DATETIME | |
| `total_hours` | DECIMAL | |

### `clock_sessions`
Entity: `entity/ClockSession.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `attendance_id` | BIGINT FK → `attendance.id` | |
| `clock_in_time`, `clock_out_time` | DATETIME | |

---

## Leave Management Tables

### `leave_types`
Entity: `entity/LeaveType.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `type_name` | VARCHAR | e.g. Annual Leave, Sick Leave |
| `max_days`, `carry_forward_days` | INT | |
| `is_paid` | BOOLEAN | |

### `leave_requests` (newer)
Entity: `entity/LeaveRequests.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `leave_type_id` | BIGINT FK → `leave_type.id` | |
| `from_date`, `to_date` | DATE | |
| `reporting_manager_status` | ENUM(LeaveRequestStatus) | |
| `hr_status` | ENUM(LeaveRequestStatus) | |
| `manual_days_allocation` | JSON | Map<leaveTypeId, days> |
| `documents` | JSON / collection | |

> **Dual entity note**: `LeaveRequest.java` and `LeaveRequests.java` both exist. `LeaveRequests.java` is the newer, active implementation.

### `leave_request` (older)
Entity: `entity/LeaveRequest.java` — legacy, may be deprecated

### `leave_balances`
Entity: `entity/LeaveBalance.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `leave_type_id` | BIGINT FK | |
| `total_days`, `used_days`, `remaining_days` | INT | |

### `employee_leave_balances`
Entity: `entity/EmployeeLeaveBalance.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `leave_type_id` | BIGINT FK | |
| `remaining_days` | DECIMAL | |

### `employee_monthly_lop`
Entity: `entity/EmployeeMonthlyLop.java`
> **Key Integration Table** — read by Payroll service during pay run
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `year` | INT | |
| `month` | INT | |
| `lop_days` | DOUBLE | Loss-of-Pay days for the month |

### `leave_documents`
Entity: `entity/LeaveDocument.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `leave_request_id` | BIGINT FK | |
| `document_url` | VARCHAR | Cloudinary URL |

### `holidays`
Entity: `entity/Holiday.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `holiday_name` | VARCHAR | |
| `holiday_date` | DATE | |
| `day_of_week` | VARCHAR | |

---

## Overtime Table

### `overtime_requests`
Entity: `entity/OvertimeRequest.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `date` | DATE | |
| `hours_requested` | DECIMAL | |
| `status` | VARCHAR | |

---

## Timesheet Tables

### `timesheet` (older)
Entity: `entity/Timesheet.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `week_start_date`, `week_end_date` | DATE | |
| `status` | ENUM(TimesheetStatus) | |
| Projects, tasks, hours entries | JSON / nested | |

### `timesheets` (newer — active)
Entity: `entity/timesheet/Timesheets.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `week_start_date`, `week_end_date` | DATE | |
| `status` | VARCHAR | |
| `total_hours` | DECIMAL | |

### `project_entry`
Entity: `entity/timesheet/ProjectEntry.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `timesheet_id` | BIGINT FK → `timesheets.id` | |
| `project_id` | BIGINT FK → `project.id` | |

### `day_entry`
Entity: `entity/timesheet/DayEntry.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `project_entry_id` | BIGINT FK → `project_entry.id` | |
| `date` | DATE | |
| `hours` | DECIMAL | |

### `task_entry`
Entity: `entity/timesheet/TaskEntry.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `project_entry_id` | BIGINT FK | |
| `task_id` | BIGINT FK → `task.id` | |

### `timesheet_notifications`
Entity: `entity/timesheet/TimesheetsNotification.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `notification_type` | VARCHAR | |
| `sent_at` | DATETIME | |

---

## Project & Task Tables

### `projects`
Entity: `entity/Project.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `project_name` | VARCHAR | |
| `description` | VARCHAR | |
| `start_date`, `end_date` | DATE | |
| `status` | VARCHAR | |

### `tasks`
Entity: `entity/Task.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `project_id` | BIGINT FK → `project.id` | |
| `task_name` | VARCHAR | |
| `assigned_to` | VARCHAR (emp_id) | |

### `assignments`
Entity: `entity/Assignment.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `project_id` | BIGINT FK | |
| `emp_id` | VARCHAR FK | |

---

## Notification Config Tables

### `role_reminder_config`
Entity: `entity/RoleReminderConfig.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `role_name` | VARCHAR | |
| Configuration fields for reminder timing | | |

### `approval_reminders`, `employee_reminders`, `escalation_reminders`, `hr_reminders`, `supervisor_reminders`
Entity: `entity/notificationconfig/*.java`
Each stores reminder schedule config for that role/scenario.

### `notifications`
Entity: `entity/Notification.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `recipient_emp_id` | VARCHAR FK | |
| `message` | TEXT | |
| `type` | VARCHAR | |
| `sent_at` | DATETIME | |
| `is_read` | BOOLEAN | |

---

## Report Table

### `report`
Entity: `entity/Report.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `generated_by` | VARCHAR | |
| `report_type` | VARCHAR | |
| `generated_at` | DATETIME | |
| `report_data` | BLOB / TEXT | |

---

# Payroll Database — `payrollDB` / `payroll_test_db`

Entity source: `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/`

---

## Core User & Organization Tables

### `companyUser`
Entity: `entity/CompanyUser.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `keycloak_user_id` | VARCHAR | Keycloak UUID |
| `email` | VARCHAR | Unique |
| `first_name`, `last_name` | VARCHAR | |

### `organization`
Entity: `entity/organization/Organization.java` (12KB — extensive)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_name` | VARCHAR | |
| `gstin`, `pan` | VARCHAR | |
| `registered_address` | VARCHAR | |
| Many statutory + branding fields | | |
| `created_by` | VARCHAR (keycloak_user_id) | |

### `department`
Entity: `entity/organization/Department.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK → `organization.id` | |
| `department_name` | VARCHAR | |

### `designation`
Entity: `entity/organization/Designation.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `designation_name` | VARCHAR | |

### `workLocations`
Entity: `entity/organization/WorkLocation.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `location_name`, `address` | VARCHAR | |
| `city`, `state`, `country`, `pincode` | VARCHAR | |

### `organizationUserMapping`
Entity: `entity/OrganizationUserMapping.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `company_user_id` | BIGINT FK | |

### `organizationUserRoleMapping`
Entity: `entity/OrganizationUserRoleMapping.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `company_user_id` | BIGINT FK | |
| `role_id` | BIGINT FK → `organization_role.id` | |

### `organizationRole`
Entity: `entity/organization/OrganizationRole.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `role_name` | VARCHAR | |

### `orgSetupSteps`
Entity: `entity/OrgSetupSteps.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| Step completion boolean flags (org profile, salary components, etc.) | | |

### `paySchedule`
Entity: `entity/PaySchedule.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `frequency` | VARCHAR | MONTHLY / BIWEEKLY |
| `pay_day` | INT | Day of month to process payroll |
| `processing_period` | VARCHAR | e.g. "July 2025" |

---

## Employee Tables (Payroll)

### `employee`
Entity: `entity/employee/BasicDetails.java` (13KB — primary employee record)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR | Unique, mirrors HRMS emp_id |
| `employee_number` | VARCHAR | |
| `work_mail` | VARCHAR | Used to cross-reference with HRMS |
| `org_id` | BIGINT FK | |
| `department_id` | BIGINT FK | |
| `designation_id` | BIGINT FK | |
| `work_location_id` | BIGINT FK | |
| `joining_date`, `confirmation_date` | DATE | |
| `employment_type`, `employee_status` | VARCHAR | |

### `employee_personal_detail`
Entity: `entity/employee/EmployeePersonalDetail.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `date_of_birth`, `gender`, `nationality` | | |
| `pan_number`, `aadhar_number` | VARCHAR | |

### `ResidentialAddress` — ⚠️ NOT a table (`@Embeddable`, columns inline in `employee_personal_detail`)
Entity: `entity/employee/ResidentialAddress.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `personal_detail_id` | BIGINT FK | |
| Address fields | | |

### `employee_bank_detail`
Entity: `entity/employee/EmployeeBankDetail.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `account_number`, `ifsc_code` | VARCHAR | |
| `bank_name`, `branch_name` | VARCHAR | |

### `ctc_structure`
Entity: `entity/employee/CtcStructure.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `annual_ctc` | DECIMAL | |
| `monthly_salary` | DECIMAL | |
| Earning/deduction component fields | | |

### `ctc_epf_components`, `ctc_esi_components`
Entities: `entity/employee/CtcEpfComponent.java`, `CtcEsiComponent.java`
Store EPF/ESI configuration baked into each employee's CTC.

### `employee_earnings`, `employee_benefits`, `employee_reimbursements`
Component mappings between employees and configured salary components.

### `employeeInvitation`
Entity: `entity/employee/EmployeeInvitation.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `email` | VARCHAR | |
| `org_id` | BIGINT FK | |
| `status` | VARCHAR | PENDING / ACCEPTED |
| `token` | VARCHAR | Invite token |

---

## Salary Components Tables

### `earnings`
Entity: `entity/salarycomponents/Earning.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `earning_name` | VARCHAR | e.g. Basic, HRA, LTA |
| `calculation_type` | VARCHAR | PERCENTAGE / FIXED |
| `percentage_of` | VARCHAR | e.g. BASIC, GROSS |
| `amount_or_percentage` | DECIMAL | |
| `is_taxable` | BOOLEAN | |
| Many configuration flags | | |

### `deductions`
Entity: `entity/salarycomponents/Deduction.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `deduction_name` | VARCHAR | |
| `amount` | DECIMAL | |

### `benefits`
Entity: `entity/salarycomponents/Benefit.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `benefit_name` | VARCHAR | |

### `reimbursements`
Entity: `entity/salarycomponents/Reimbursement.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `reimbursement_name` | VARCHAR | |
| `max_amount` | DECIMAL | |

---

## Statutory Components Tables

### `epf`
Entity: `entity/statutorycomponents/Epf.java` (11KB)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `employee_contribution_rate` | DECIMAL | Default 12% |
| `employer_contribution_rate` | DECIMAL | |
| `admin_charges_rate` | DECIMAL | |
| Many EPF configuration flags | | |

### `esi`
Entity: `entity/statutorycomponents/Esi.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `employee_rate`, `employer_rate` | DECIMAL | |
| `wage_ceiling` | DECIMAL | |

### `professionalTax`
Entity: `entity/statutorycomponents/ProfessionalTax.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `state` | VARCHAR | PT is state-specific in India |

### `slabRateConfiguration`
Entity: `entity/statutorycomponents/SlabRateConfiguration.java`
PT slabs per state. Linked to `ProfessionalTax`.

### `slabDetail`
Entity: `entity/statutorycomponents/SlabDetail.java`
Individual slab rows (income range → tax amount).

### `org_pt_override`
Entity: `entity/statutorycomponents/OrgPTOverride.java`
Org-level PT overrides per state.

---

## Pay Run Tables

### `payruns`
Entity: `entity/payruns/PayRun.java` (17KB)
| Column | Type | Notes |
|---|---|---|
| `payrun_id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `processing_period` | VARCHAR | e.g. "July 2025" |
| `status` | VARCHAR | DRAFT / PROCESSING / COMPLETED |
| `total_employees` | INT | |
| `total_net_pay` | DECIMAL | |
| Various summary fields | | |

### `employee_payruns`
Entity: `entity/payruns/EmployeePayRun.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `payrun_id` | BIGINT FK → `pay_run.payrun_id` | |
| `employee_id` | VARCHAR FK | |
| `monthly_salary` | DECIMAL | |
| `total_earnings` | DECIMAL | |
| `total_deductions` | DECIMAL | |
| `total_taxes` | DECIMAL | |
| `total_benefits` | DECIMAL | |
| `total_reimbursements` | DECIMAL | |
| `lop` | DECIMAL | Loss-of-Pay deduction from HRMS |
| `total_no_of_leaves` | INT | LOP leave days from HRMS |
| `paid_days` | DECIMAL | Adjusted for LOP |
| `net_pay` | DECIMAL | Final computed value |
| `payment_status` | VARCHAR | YET_TO_PAY / PAID |

---

## Tax & Investment Tables

### `proof_of_investment_document`
Entity: `entity/taxCalculator/ProofOfInvestmentDocument.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `declaration_id` | BIGINT FK | |
| `section`, `sub_section` | VARCHAR | 80C, 80D, etc. |
| `declared_amount` | DECIMAL | |
| `document_url` | VARCHAR | Cloudinary URL |


---

## Claims & Declarations Tables

### `IncomeTaxDeclaration`
Entity: `entity/claimsanddeclarations/IncomeTaxDeclaration.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| `financial_year` | VARCHAR | |
| Income declarations by section | DECIMAL | |

### `ProofOfInvestment`
Entity: `entity/claimsanddeclarations/ProofOfInvestment.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| Investment proof details | | |

### `FBP` (Flexible Benefit Plan)
Entity: `entity/claimsanddeclarations/FBP.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR FK | |
| FBP component allocations | | |

### `employee_reimbursement_request`
Entity: `entity/employeereimbursement/EmployeeReimbursementRequest.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `employee_id` | VARCHAR | |
| `organization_id` | VARCHAR | Scoped multi-tenancy |
| `reimbursement_type` | VARCHAR | Enum: `ReimbursementType` |
| `requested_amount` | DECIMAL | Precision 12, scale 2 |
| `approved_amount` | DECIMAL | Precision 12, scale 2 |
| `bill_date` | DATE | |
| `description` | VARCHAR | |
| `attachment_url` | VARCHAR | Cloudinary image/PDF link |
| `attachment_public_id` | VARCHAR | Cloudinary public ID |
| `attachment_file_name` | VARCHAR | Original file name |
| `status` | VARCHAR | Enum: `ReimbursementStatus` |
| `remarks` | VARCHAR | Admin review remarks |
| `payment_status` | VARCHAR | Enum: `ReimbursementPaymentStatus` |
| `reimbursement_month` | VARCHAR | |
| `payrun_id` | VARCHAR | Associated payrun |
| `created_at` | TIMESTAMP | Audit creation timestamp |
| `updated_at` | TIMESTAMP | Audit update timestamp |
| `approved_by` | VARCHAR | |
| `approved_at` | TIMESTAMP | |

---

## Leave & Attendance Tables (Payroll)

### `leave_type` (Payroll)
Entity: `entity/leaveAndAttedance/LeaveType.java` (14KB — very extensive)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `leave_name` | VARCHAR | |
| `max_days_per_year` | INT | |
| `carry_forward_enabled` | BOOLEAN | |
| Many policy configuration fields | | |

### `employee_leave_import`
Entity: `entity/leaveAndAttedance/leaveImport/EmployeeLeaveImport.java`
Tracks bulk CSV-imported leave balance data.

### `attendance_preferences`
Entity: `entity/leaveAndAttedance/attendance/AttendancePreference.java`
Org-level attendance configuration.

### `holidays` (Payroll)
Entity: `entity/leaveAndAttedance/holiday/Holiday.java`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | |
| `org_id` | BIGINT FK | |
| `holiday_name` | VARCHAR | |
| `date` | DATE | |

### `onboarding_status`
Entity: `entity/leaveAndAttedance/onboarding/OnboardingStatus.java`
Tracks per-employee onboarding completion steps.

---

## Key Cross-Service Relationship

```
HRMS: our_users.email ←→ BasicDetails.work_mail (Payroll)
HRMS: our_users.emp_id ←→ BasicDetails.employee_id (Payroll)
HRMS: employee_monthly_lop → [via REST API] → employee_pay_run.lop (Payroll)
```

---

# Appendix A — Tables added 2026-09-11

52 tables that existed in code but were absent from this document, plus 2 disabled
entities kept for reference. Columns are
derived directly from the JPA entities; `Java type` shows the field type as declared
(a relation type such as `BasicDetails` indicates a FK to that entity's table).

### `EmployeeInvestmentProof`
Entity: `com/itsdev/payroll/entity/taxCalculator/EmployeeInvestmentProof.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `employee_id` | BasicDetails | FK/relation  |
| `organization_id` | String | NOT NULL  |
| `financial_year` | Integer | NOT NULL  |
| `reviewed_date` | LocalDateTime |  |
| `reviewed_by` | String |  |
| `reject_reason` | String |  |
| `status` | DocumentStatus | NOT NULL  |
| `created_date` | LocalDateTime |  |
| `modified_date` | LocalDateTime |  |
| `files` | List<EmployeeInvestmentProofFile> | FK/relation  |

### `EmployeeInvestmentProofFile`
Entity: `com/itsdev/payroll/entity/taxCalculator/EmployeeInvestmentProofFile.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `proof_id` | EmployeeInvestmentProof | FK/relation  |
| `declared_item_name` | String | NOT NULL  |
| `document_type` | String | NOT NULL  |
| `file_name` | String | NOT NULL  |
| `file_url` | String | NOT NULL  |
| `public_id` | String | NOT NULL  |
| `file_size` | Long |  |
| `content_type` | String |  |

### `employee_leave_allocation`
Entity: `com/itsdev/payroll/entity/leave/EmployeeLeaveAllocation.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `organization_id` | String | NOT NULL  |
| `employee_id` | String | NOT NULL  |
| `leave_type` | String | NOT NULL  |
| `year` | String |  |
| `annual_days` | Integer | NOT NULL  |
| `carried_forward_days` | Integer | NOT NULL  |
| `consumed_days` | Integer | NOT NULL  |
| `monthly_lwp_breakdown` | String |  |
| `monthly_lop_breakdown` | String |  |
| `monthly_breakdown` | String |  |
| `leave_month` | String |  |
| `lwp` | Integer |  |
| `lop_days` | Integer |  |
| `expiration_date` | LocalDate | NOT NULL  |
| `carry_forward` | Boolean |  |
| `created_by` | String |  |
| `created_at` | LocalDateTime |  |
| `updated_at` | LocalDateTime |  |

### `employee_leave_balance_consumption`
Entity: `com/itsdev/payroll/entity/leave/EmployeeLeaveBalanceConsumption.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `leave_id` | String | unique  |
| `allocation_id` | Long |  |
| `organization_id` | String | NOT NULL  |
| `employee_id` | String | NOT NULL  |
| `leave_type` | String | NOT NULL  |
| `year` | String |  |
| `consumed_days` | Integer | NOT NULL  |
| `balance_days` | Integer | NOT NULL  |
| `balance_after` | Integer |  |
| `running_ytd` | Integer |  |
| `reason` | String |  |
| `leave_month` | String |  |
| `monthly_breakdown` | String |  |
| `monthly_lop_breakdown` | String |  |
| `monthly_lwp_breakdown` | String |  |
| `monthly_entries` | String |  |
| `lwp` | Integer |  |
| `lop_days` | Integer |  |
| `created_by` | String |  |
| `created_at` | LocalDateTime |  |
| `updated_at` | LocalDateTime |  |

### `employee_proof_of_investment`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/poi/EmployeeProofOfInvestment.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `declaration_id` | EmployeeInvestmentDeclaration | FK/relation  |
| `organization_id` | Organization | FK/relation  |
| `employee_id` | BasicDetails | FK/relation  |
| `fiscal_year` | Integer | NOT NULL  |
| `tax_regime_at_submission` | String |  |
| `status` | PayRunStatus | NOT NULL  |
| `submitted_by` | String |  |
| `submitted_date` | LocalDateTime |  |
| `approved_by` | String |  |
| `approved_date` | LocalDateTime |  |
| `considered_for_it` | Boolean | NOT NULL  |
| `considered_by` | String |  |
| `considered_date` | LocalDateTime |  |
| `final_annual_tax` | BigDecimal |  |
| `tax_regime_at_consideration` | String |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |
| `poiItems` | List<EmployeePOIItem> | FK/relation  |

### `EmployeeTaxCalculationResult` - DISABLED, not a live table (`@Entity` and `@Table` are commented out)
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/EmployeeTaxCalculationResult.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|

### `EmployeeTaxRecalculation` - DISABLED, not a live table (`@Entity` and `@Table` are commented out)
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/EmployeeTaxRecalculation.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|

### `new_tax_calculation`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/NewTaxCalculation.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `organizationId` | String | NOT NULL  |
| `employeeId` | String | NOT NULL  |
| `financialYear` | Integer | NOT NULL  |
| `grossIncome` | BigDecimal | NOT NULL  |
| `incomeFromSalary` | BigDecimal | NOT NULL  |
| `incomeFromHouseProperty` | BigDecimal |  |
| `incomeFromOtherSources` | BigDecimal |  |
| `standardDeduction` | BigDecimal |  |
| `taxableIncome` | BigDecimal | NOT NULL  |
| `taxBeforeRebate` | BigDecimal | NOT NULL  |
| `rebateAmount` | BigDecimal |  |
| `surcharge` | BigDecimal |  |
| `cess` | BigDecimal |  |
| `taxPayable` | BigDecimal | NOT NULL  |
| `taxPerMonth` | BigDecimal |  |
| `remainingMonths` | Integer |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |

### `old_tax_calculation`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/OldTaxCalculation.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `organizationId` | String | NOT NULL  |
| `employeeId` | String | NOT NULL  |
| `financialYear` | Integer | NOT NULL  |
| `grossIncome` | BigDecimal | NOT NULL  |
| `incomeFromSalary` | BigDecimal | NOT NULL  |
| `incomeFromHouseProperty` | BigDecimal |  |
| `incomeFromOtherSources` | BigDecimal |  |
| `hraExemption` | BigDecimal |  |
| `totalChapterVIA` | BigDecimal |  |
| `taxableIncome` | BigDecimal | NOT NULL  |
| `taxBeforeRebate` | BigDecimal | NOT NULL  |
| `rebateAmount` | BigDecimal |  |
| `surcharge` | BigDecimal |  |
| `cess` | BigDecimal |  |
| `taxPayable` | BigDecimal | NOT NULL  |
| `taxPerMonth` | BigDecimal |  |
| `remainingMonths` | Integer |  |
| `sectionWiseDeductions` | List<OldTaxSectionDeduction> | FK/relation  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |
| `standard_deduction` | BigDecimal |  |

### `old_tax_calculation_revision`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/revision/OldTaxCalculationRevision.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `organization_id` | String | NOT NULL  |
| `employee_id` | String | NOT NULL  |
| `financial_year` | Integer | NOT NULL  |
| `base_old_tax_calculation_id` | Long |  |
| `gross_income` | BigDecimal |  |
| `income_from_salary` | BigDecimal |  |
| `income_from_house_property` | BigDecimal |  |
| `income_from_other_sources` | BigDecimal |  |
| `hra_exemption` | BigDecimal |  |
| `standard_deduction` | BigDecimal |  |
| `total_chapter_via` | BigDecimal |  |
| `taxable_income` | BigDecimal |  |
| `tax_before_rebate` | BigDecimal |  |
| `rebate_amount` | BigDecimal |  |
| `surcharge` | BigDecimal |  |
| `cess` | BigDecimal |  |
| `tax_payable` | BigDecimal |  |
| `revision_reason` | String |  |
| `revision_effective_from` | LocalDate |  |
| `calculated_at` | LocalDateTime | NOT NULL  |
| `sectionWiseDeductions` | List<OldTaxRevisionSectionDeduction> | FK/relation  |

### `ReimbursementClaim`
Entity: `com/itsdev/payroll/entity/claimsanddeclarations/ReimbursementClaim.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `sendMailOnReimbursementClaimRelease` | boolean |  |
| `sendMailOnReimbursementClaimLockAndRelease` | boolean |  |
| `sendMailOnReimbursementClaimLock` | boolean |  |
| `lastDateForReimbursementClaim` | int |  |
| `isPayscheduleConfigured` | boolean |  |
| `isReimbursementEnabled` | boolean |  |
| `sendMailOnReimbursementClaimDateChange` | boolean |  |
| `isAnyReminderBeforeLockdateEnabled` | boolean |  |
| `organizationId` | Organization | FK/relation  |
| `reminders` | List<Reminder> | FK/relation  |

### `TaxSlabDetailHistory`
Entity: `com/itsdev/payroll/entity/statutorycomponents/TaxSlabDetailHistory.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `taxId` | String |  |
| `oldStartAmount` | Double |  |
| `oldEndAmount` | Double |  |
| `oldPayAmount` | Double |  |
| `startAmount` | Double |  |
| `endAmount` | Double |  |
| `payAmount` | Double |  |
| `organizationId` | String |  |
| `DELETE` | String |  |
| `changedAt` | LocalDateTime |  |
| `optional` | String |  |

### `cess_surcharge_rule_master`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/CessSurchargeRuleMaster.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `SURCHARGE` | String | NOT NULL  |
| `BOTH` | String |  |
| `cess` | BigDecimal |  |
| `incomeTo` | BigDecimal |  |
| `4` | BigDecimal | NOT NULL  |
| `effectiveFrom` | LocalDate |  |
| `effectiveTo` | LocalDate |  |
| `isActive` | Boolean |  |
| `remarks` | String |  |

### `employee_deduction`
Entity: `com/itsdev/payroll/entity/SalaryDeduction.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `employee_id` | String | NOT NULL  |
| `organization_id` | String | NOT NULL  |
| `deduction_amount` | BigDecimal | NOT NULL  |
| `deduction_month` | LocalDate | NOT NULL  |
| `deduction_type` | String |  |
| `reason` | String | NOT NULL  |
| `remarks` | String |  |
| `status` | DeductionStatus | NOT NULL  |
| `created_by` | String | NOT NULL  |
| `created_at` | LocalDateTime | NOT NULL  |
| `updated_at` | LocalDateTime |  |
| `employee_id` | BasicDetails | FK/relation  |
| `proof_url` | String |  |
| `proof_public_id` | String |  |

### `employee_fbp_components`
Entity: `com/itsdev/payroll/entity/employee/FbpComponent.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `componentCode` | String |  |
| `enabled` | Boolean |  |
| `amount` | Double |  |
| `amount_in_percentage` | Double |  |
| `max_limit` | Double |  |
| `ctc_structure_id` | CtcStructure | FK/relation  |
| `organizationId` | Organization | FK/relation  |

### `employee_inv_home_loan`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvHomeLoan.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `declaration_id` | EmployeeInvestmentDeclaration | FK/relation  |
| `principal_paid` | BigDecimal |  |
| `interest_paid` | BigDecimal |  |
| `lender_name` | String |  |
| `lender_pan` | String |  |
| `item_id_external` | String |  |

### `employee_inv_house_rent`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvHouseRent.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `declaration_id` | EmployeeInvestmentDeclaration | FK/relation  |
| `from_month` | String |  |
| `to_month` | String |  |
| `address` | String |  |
| `landlord_name` | String |  |
| `landlord_pan` | String |  |
| `is_metro` | Boolean |  |
| `amount_per_month` | BigDecimal |  |
| `currency` | String |  |
| `item_id_external` | String |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |

### `employee_inv_let_out_property`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvLetOutProperty.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `declaration_id` | EmployeeInvestmentDeclaration | FK/relation  |
| `property_name` | String |  |
| `address` | String |  |
| `net_income_loss` | BigDecimal |  |
| `item_id_external` | String |  |
| `propertyDetails` | List<EmployeeInvLetOutPropertyDetail> | FK/relation  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |

### `employee_inv_let_out_property_detail`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvLetOutPropertyDetail.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `let_out_property_id` | EmployeeInvLetOutProperty | FK/relation  |
| `type` | String |  |
| `amount` | BigDecimal |  |
| `name_of_lender` | String |  |
| `pan_of_lender` | String |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |

### `employee_inv_other_income`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvOtherIncome.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `declaration_id` | EmployeeInvestmentDeclaration | FK/relation  |
| `type` | String |  |
| `type_formatted` | String |  |
| `name` | String |  |
| `amount` | BigDecimal |  |
| `declared_amount` | BigDecimal |  |
| `can_edit_in_portal` | Boolean |  |
| `name_of_lender` | String |  |
| `pan_of_lender` | String |  |
| `item_id_external` | String |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |

### `employee_inv_pre_tax_deduction`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvPreTaxDeduction.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `declaration_id` | EmployeeInvestmentDeclaration | FK/relation  |
| `code_string` | String |  |
| `category` | String |  |
| `category_formatted` | String |  |
| `type` | String |  |
| `type_formatted` | String |  |
| `amount` | BigDecimal |  |
| `amount_formatted` | String |  |
| `investment_amount` | BigDecimal |  |
| `investment_amount_formatted` | String |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |

### `employee_inv_prev_employment`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvPrevEmployment.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `declaration_id` | EmployeeInvestmentDeclaration | FK/relation  |
| `type` | String |  |
| `name` | String |  |
| `type_formatted` | String |  |
| `amount` | BigDecimal |  |
| `declared_amount` | BigDecimal |  |
| `can_edit_in_portal` | Boolean |  |
| `item_id_external` | String |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |

### `employee_inv_section6a`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvSection6A.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `declaration_id` | EmployeeInvestmentDeclaration | FK/relation  |
| `section6a_item_id` | Long |  |
| `category` | String |  |
| `type` | String |  |
| `amount` | BigDecimal |  |
| `category_formatted` | String |  |
| `type_formatted` | String |  |
| `item_id_external` | String |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |

### `employee_inv_tax_summary`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvTaxSummary.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `declaration_id` | EmployeeInvestmentDeclaration | FK/relation  |
| `regime` | String |  |
| `tax_year_start` | Integer |  |
| `tax_year_end` | Integer |  |
| `taxable_income` | BigDecimal |  |
| `net_taxable_income` | BigDecimal |  |
| `tax_on_taxable_income` | BigDecimal |  |
| `tax_ytd_amount` | BigDecimal |  |
| `tax_to_be_paid` | BigDecimal |  |
| `tds_through_payroll` | BigDecimal |  |
| `tds_previous_employer` | BigDecimal |  |
| `tds_other_income` | BigDecimal |  |
| `other_sources_income` | BigDecimal |  |
| `exemption_under_section10` | BigDecimal |  |
| `exemption_under_section6a` | BigDecimal |  |
| `no_of_remaining_months` | Integer |  |
| `taxable_income_formatted` | String |  |
| `net_taxable_income_formatted` | String |  |
| `tax_on_taxable_income_formatted` | String |  |
| `tax_ytd_amount_formatted` | String |  |
| `tax_to_be_paid_formatted` | String |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |

### `employee_investment_declaration`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvestmentDeclaration.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `organizationId` | Organization | FK/relation  |
| `employee_id` | BasicDetails | FK/relation  |
| `fiscal_year` | Integer |  |
| `declaration_tax_year_start` | String |  |
| `declaration_tax_year_end` | String |  |
| `current_tax_year_start` | String |  |
| `current_tax_year_end` | String |  |
| `tax_regime` | String |  |
| `tax_regime_formatted` | String |  |
| `is_multiple_tax_regimes_applicable` | Boolean |  |
| `is_lender_pan_mandatory` | Boolean |  |
| `can_change_tax_regime` | Boolean |  |
| `can_allow_edit` | Boolean |  |
| `is_staying_in_rented_house` | Boolean |  |
| `is_repaying_self_occupied_loan` | Boolean |  |
| `has_let_out_property` | Boolean |  |
| `status` | String |  |
| `status_formatted` | String |  |
| `message_types` | String |  |
| `tax_plan_count` | Integer |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |
| `houseRents` | List<EmployeeInvHouseRent> | FK/relation  |
| `otherIncomes` | List<EmployeeInvOtherIncome> | FK/relation  |
| `letOutProperties` | List<EmployeeInvLetOutProperty> | FK/relation  |
| `section6aDeclarations` | List<EmployeeInvSection6A> | FK/relation  |
| `prevEmploymentDeclarations` | List<EmployeeInvPrevEmployment> | FK/relation  |
| `preTaxDeductions` | List<EmployeeInvPreTaxDeduction> | FK/relation  |
| `taxSummaries` | List<EmployeeInvTaxSummary> | FK/relation  |
| `homeLoans` | List<EmployeeInvHomeLoan> | FK/relation  |

### `employee_poi_document`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/poi/EmployeePOIDocument.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `poi_item_id` | EmployeePOIItem | FK/relation  |
| `document_name` | String |  |
| `document_url` | String |  |
| `uploaded_by` | String |  |
| `uploaded_time` | LocalDateTime |  |

### `employee_poi_item`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/poi/EmployeePOIItem.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `poi_id` | EmployeeProofOfInvestment | FK/relation  |
| `investment_type` | String |  |
| `section6a_item_id` | Long |  |
| `declared_amount` | BigDecimal |  |
| `actual_amount` | BigDecimal |  |
| `approved_amount` | BigDecimal |  |
| `status` | PayRunStatus | NOT NULL  |
| `admin_comment` | String |  |
| `item_id_external` | String |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |
| `documents` | List<EmployeePOIDocument> | FK/relation  |
| `comments` | List<EmployeePOIItemComment> | FK/relation  |
| `propertyDetails` | List<EmployeePOIPropertyDetail> | FK/relation  |
| `admin_adjusted` | Boolean |  |
| `amount_adjusted_by` | String |  |
| `amount_adjusted_date` | LocalDateTime |  |

### `employee_poi_item_comment`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/poi/EmployeePOIItemComment.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `poi_item_id` | EmployeePOIItem | FK/relation  |
| `comment` | String | NOT NULL  |
| `commented_by_employee_id` | BasicDetails | FK/relation  |
| `commented_by_admin` | String |  |
| `created_time` | LocalDateTime |  |
| `response_to_comment_id` | EmployeePOIItemComment | FK/relation  |

### `employee_poi_property_detail`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/poi/EmployeePOIPropertyDetail.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `poi_item_id` | EmployeePOIItem | FK/relation  |
| `type` | String |  |
| `amount` | BigDecimal |  |
| `name_of_lender` | String |  |
| `pan_of_lender` | String |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |

### `employee_tds`
Entity: `com/itsdev/payroll/entity/employeeTDS/EmployeeTds.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `employee_id` | String | NOT NULL  |
| `organization_id` | String | NOT NULL  |
| `fiscal_year` | Integer | NOT NULL  |
| `tds_source_type` | TdsSourceType | NOT NULL  |
| `poi_id` | Long |  |
| `tax_regime` | String | NOT NULL  |
| `annual_gross_salary` | BigDecimal | NOT NULL  |
| `annual_taxable_income` | BigDecimal | NOT NULL  |
| `final_annual_tax` | BigDecimal | NOT NULL  |
| `effective_from_month` | String | NOT NULL  |
| `is_active` | Boolean | NOT NULL  |
| `created_at` | LocalDateTime |  |

### `employee_variable_earnings`
Entity: `com/itsdev/payroll/entity/employee/VariableEarning.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `variableCode` | String | NOT NULL  |
| `enabled` | Boolean |  |
| `value` | Double |  |
| `amount_in_percentage` | Double |  |
| `editable` | Boolean |  |
| `ctc_structure_id` | CtcStructure | FK/relation  |
| `organizationId` | Organization | FK/relation  |

### `home_loan_rule_master`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/HomeLoanRuleMaster.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `80EEA` | String | NOT NULL  |
| `sectionName` | String | NOT NULL  |
| `INTEREST` | String | NOT NULL  |
| `BOTH` | String |  |
| `NULL` | BigDecimal |  |
| `maxLimitFormatted` | String |  |
| `loanSanctionFrom` | LocalDate |  |
| `loanSanctionTo` | LocalDate |  |
| `isFirstTimeBuyer` | Boolean |  |
| `isActive` | Boolean |  |
| `remarks` | String |  |

### `hraRuleMaster`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/HraRuleMaster.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `financialYear` | String |  |
| `taxRegime` | String |  |
| `metroPercentageOfBasic` | Integer |  |
| `nonMetroPercentageOfBasic` | Integer |  |
| `rentMinusBasicPercentage` | Integer |  |
| `panMandatoryThreshold` | Integer |  |
| `isMonthWiseCalculation` | Boolean |  |
| `isActive` | Boolean |  |

### `incomeTaxDetails`
Entity: `com/itsdev/payroll/entity/organization/IncomeTaxDetails.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `tanNumber` | String |  |
| `panNumber` | String |  |
| `tdsCircle` | String |  |
| `authorizedPersonName` | String |  |
| `authorizedPersonParent` | String |  |
| `authorizedPersonDesignation` | String |  |
| `depositSchedule` | String |  |
| `employeeId` | String |  |
| `organizationId` | Organization | FK/relation  |

### `letOutPropertyRuleMaster`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/LetOutPropertyRuleMaster.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `financialYear` | String |  |
| `taxRegime` | String |  |
| `standardDeductionPercentage` | Integer |  |
| `maxLossSetOffAgainstSalary` | Integer |  |
| `isHomeLoanInterestAllowed` | Boolean |  |
| `isLossCarryForwardAllowed` | Boolean |  |
| `isActive` | Boolean |  |

### `masterConfig`
Entity: `com/itsdev/payroll/entity/MasterConfig.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `componentName` | String |  |
| `JSON` | JsonNode |  |

### `off_cycle_pay_run`
Entity: `com/itsdev/payroll/entity/payRun/offCyclePayrun/OffCyclePayRun.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `id)` | String | unique NOT NULL  |
| `payDate` | LocalDate | NOT NULL  |
| `status` | String | NOT NULL  |
| `statusFormatted` | String |  |
| `type` | String | NOT NULL  |
| `notes` | String |  |
| `organizationId` | Organization | FK/relation  |
| `employees` | List<OffCyclePayrunEmployee> | FK/relation  |
| `createdAt` | LocalDateTime | NOT NULL  |

### `off_cycle_payrun_employee`
Entity: `com/itsdev/payroll/entity/payRun/offCyclePayrun/OffCyclePayrunEmployee.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `payroll_run_id` | OffCyclePayRun | FK/relation  |
| `employee_id` | BasicDetails | FK/relation  |
| `organization_id` | Organization | FK/relation  |
| `earnings` | List<OffCyclePayrunEmployeeEarning> | FK/relation  |
| `deductions` | List<OffCyclePayrunEmployeeDeduction> | FK/relation  |
| `lopAdjustmentDetails` | String |  |
| `taxes` | String |  |
| `createdAt` | LocalDateTime | NOT NULL  |
| `updatedAt` | LocalDateTime | NOT NULL  |

### `off_cycle_payrun_employee_deductions`
Entity: `com/itsdev/payroll/entity/payRun/offCyclePayrun/OffCyclePayrunEmployeeDeduction.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `off_cycle_employee_id` | OffCyclePayrunEmployee | FK/relation  |
| `deduction_id` | Deduction | FK/relation  |
| `amount` | BigDecimal |  |

### `off_cycle_payrun_employee_earnings`
Entity: `com/itsdev/payroll/entity/payRun/offCyclePayrun/OffCyclePayrunEmployeeEarning.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `off_cycle_employee_id` | OffCyclePayrunEmployee | FK/relation  |
| `earning_id` | Earning | FK/relation  |
| `amount` | BigDecimal |  |
| `days` | Integer |  |

### `old_tax_revision_section_deduction`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/revision/OldTaxRevisionSectionDeduction.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `section_code` | String | NOT NULL  |
| `amount` | BigDecimal | NOT NULL  |
| `revision_id` | OldTaxCalculationRevision | FK/relation  |

### `old_tax_section_deduction`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/OldTaxSectionDeduction.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `etc.` | String | NOT NULL  |
| `amount` | BigDecimal | NOT NULL  |
| `old_tax_id` | OldTaxCalculation | FK/relation  |

### `one_time_payout`
Entity: `com/itsdev/payroll/entity/payRun/oneTimePayout/OneTimePayout.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `earning_id` | Earning | FK/relation  |
| `employee_id` | BasicDetails | FK/relation  |
| `organizationId` | Organization | FK/relation  |
| `earningAmount` | BigDecimal |  |
| `payDate` | LocalDate | NOT NULL  |
| `taxes` | String |  |
| `createdAt` | LocalDateTime | NOT NULL  |
| `days` | Integer |  |

### `organization_role_action`
Entity: `com/itsdev/payroll/entity/auth/OrganizationRoleAction.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `organization_role_id` | OrganizationRole | FK/relation  |
| `action_id` | Action | FK/relation  |
| `createdBy` | String |  |
| `createdAt` | Instant |  |

### `otherIncomeRuleMaster`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/OtherIncomeRuleMaster.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `sectionCode` | String |  |
| `sectionName` | String |  |
| `ruleType` | String |  |
| `taxRegime` | String |  |
| `financialYear` | String |  |
| `maxLimit` | Integer |  |
| `deductionPercentage` | Integer |  |
| `isProofRequired` | Boolean |  |
| `isConditional` | Boolean |  |
| `isAllowed` | Boolean |  |
| `isActive` | Boolean |  |

### `preferences`
Entity: `com/itsdev/payroll/entity/leaveAndAttedance/preferences/Preferences.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | UUID | PK  |
| `organizationId` | Organization | FK/relation  |
| `end_day` | String | NOT NULL  |
| `payroll_report_day` | String | NOT NULL  |
| `is_leave_encashment_enabled` | Boolean | NOT NULL  |

### `pt_history`
Entity: `com/itsdev/payroll/entity/statutorycomponents/PTHistory.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `organizationId` | String | NOT NULL  |
| `state` | String | NOT NULL  |
| `oldJson` | JsonNode |  |
| `newJson` | JsonNode |  |
| `"DELETE"` | String |  |
| `changedAt` | LocalDateTime |  |
| `changedBy` | String |  |

### `reminder`
Entity: `com/itsdev/payroll/entity/claimsanddeclarations/Reminder.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `reminder_id` | String | unique NOT NULL  |
| `is_enabled` | boolean |  |
| `number_of_days` | int |  |
| `fbp_id` | FBP | FK/relation  |
| `reimbursement_claim_id` | ReimbursementClaim | FK/relation  |
| `income_tax_declaration_id` | IncomeTaxDeclaration | FK/relation  |
| `proof_of_investment_id` | ProofOfInvestment | FK/relation  |
| `employee_id` | BasicDetails | FK/relation  |
| `organization_id` | Organization | FK/relation  |
| `fiscal_year` | Integer |  |
| `sent_date` | LocalDateTime |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |
| `organization,` | static |  |

### `section6a_item_master`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/Section6AItemMaster.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `category` | String |  |
| `category_formatted` | String |  |
| `type` | String |  |
| `type_formatted` | String |  |
| `max_limit` | BigDecimal |  |
| `max_limit_formatted` | String |  |
| `is_80c` | Boolean |  |
| `is_80d` | Boolean |  |
| `is_other_section` | Boolean |  |
| `is_active` | Boolean |  |
| `created_time` | LocalDateTime |  |
| `updated_time` | LocalDateTime |  |

### `section87a_rebate_rule_master`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/Section87ARebateRuleMaster.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `NEW` | String | NOT NULL  |
| `allowed` | BigDecimal | NOT NULL  |
| `allowed` | BigDecimal | NOT NULL  |
| `true` | Boolean | NOT NULL  |
| `effectiveFrom` | LocalDate |  |
| `effectiveTo` | LocalDate |  |
| `isActive` | Boolean |  |
| `remarks` | String |  |

### `standardDeductionRuleMaster`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/StandardDeductionRuleMaster.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `financialYear` | String | NOT NULL  |
| `taxRegime` | String | NOT NULL  |
| `amount` | BigDecimal | NOT NULL  |
| `description` | String |  |
| `isActive` | Boolean |  |

### `taxSlabMaster`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/TaxSlabMaster.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `financialYear` | String |  |
| `taxRegime` | String |  |
| `slabJson` | String |  |
| `isActive` | Boolean |  |

### `taxSlabMasterHistory`
Entity: `com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/TaxSlabMasterHistory.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `taxSlabMasterId` | Long |  |
| `financialYear` | String |  |
| `NEW` | String |  |
| `oldSlabJson` | String |  |
| `newSlabJson` | String |  |
| `actionType` | String |  |
| `changedBy` | String |  |
| `changedAt` | LocalDateTime |  |

### `userInvitations`
Entity: `com/itsdev/payroll/entity/organization/UserInvitation.java` · App: Payroll-Bend-SBoot

| Column | Java type | Notes |
|---|---|---|
| `id` | Long | PK  |
| `userId` | String | NOT NULL  |
| `roleId` | String |  |
| `name` | String |  |
| `email` | String | NOT NULL  |
| `mobile` | String |  |
| `invitationType` | String |  |
| `isSuperAdmin` | Boolean |  |
| `status` | String |  |
| `userRole` | String |  |
| `organizationId` | Organization | FK/relation  |
| `createdAt` | LocalDateTime |  |
| `isDeleted` | Boolean | NOT NULL  |
| `isEditable` | Boolean |  |
| `isInvitationAccepted` | Boolean |  |
| `acceptance_token` | String | unique  |
| `rejection_reason` | String |  |
| `rejection_date` | LocalDateTime |  |

