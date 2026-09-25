# Core Capability Assessment — HRMS_Backend@main

**Date**: 2026-09-11  
**Scope**: `HRMS_Backend` on branch `main` only  
**Methodology**: Systematic grep and entity inventory, cross-referenced with FEATURE_MAP.md and DB_SCHEMA.md  

---

## Executive Summary

HRMS_Backend implements **9 of 15 core capabilities as PRESENT**, **3 as PARTIAL**, and **3 as ABSENT**. 

**Critical gaps**:
- No tenant/organization registry (ABSENT)
- No tenant scoping on queries (ABSENT)
- No audit trail or change tracking (ABSENT)
- No generic, reusable approval workflow (PARTIAL — hard-coded per feature)
- Org structure is partially string-based (PARTIAL — department/reporting manager fields exist as strings, not entities)
- Reference data (countries, states, banks, currencies) are embedded string fields, not separate lookup tables (PARTIAL)

**Entity count**: 39 `@Entity` classes.  
**organizationId or tenant mentions in code**: **0 files** (confirmed by ripgrep).

---

## Detailed Findings

### 1. Tenant/Organization Registry
**Status**: **ABSENT**

**Evidence**: 
- `grep -rli "organizationId\|tenant" HRMS_Backend/` returns **0 results** (CLAUDE.md GAP-002, verified 2026-09-11).
- No `Organization.java` entity exists in `entity/` directory.
- All entities (Employee, LeaveRequests, Attendance, etc.) lack a tenant discriminator or organization FK.

**Impact**: HRMS is single-tenant by design. Multi-tenant support would require schema migration and systematic tenant scoping across all queries.

---

### 2. Identity & Authentication
**Status**: **PRESENT**

**Entities**: 
- `OurUsers.java` — user login record with email, password (BCrypt), emp_id

**Controllers**:
- `AuthController.java:19` — `POST /auth/logout`, login token management

**Services**:
- `UsersManagementService.java` — primary auth logic
- `JWTUtils.java` — JWT token generation and validation

**Flow** (controller → service → entity):
1. AuthController validates credentials
2. UsersManagementService issues JWT via JWTUtils
3. JWTAuthFilter enforces tokens on protected routes

**Auth Method**: Custom JWT (`jjwt` 0.12.5), not Keycloak.

---

### 3. Authorization / Roles / Permissions
**Status**: **PRESENT**

**Entities**:
- `Role.java` (useraccess) — named roles (ADMIN, HR, MANAGER, etc.)
- `Action.java` (useraccess) — named actions (MANAGE_EMPLOYEES, ADD_ENTRY, etc.)
- `UserActionMapping.java` — link user ↔ action

**Controllers**:
- `RoleController.java` (useraccess) — role CRUD
- `ActionController.java` (useraccess) — action CRUD
- `UserController.java` (useraccess) — user-to-action assignment

**Services**:
- `RoleService.java` / `RoleServiceImpl.java`
- `ActionService.java` / `ActionServiceImpl.java`
- `UserService.java` / `UserServiceImpl.java`

**RBAC Model**: Fine-grained, action-based. Users assigned to roles; roles mapped to named action strings. Frontend enforces via `ActionProtectedRoute` (HRMS_Frontend).

---

### 4. Tenant Scoping on Queries
**Status**: **ABSENT**

**Evidence**:
- No `organizationId` or `tenant_id` columns on primary tables (Employee, LeaveRequests, Attendance, Timesheets, Projects).
- No Hibernate `@TenantId` filter on any repository.
- Cross-tenant reads possible: any endpoint can fetch any employee by ID.

**Gap**: BUG-002 in GAP_INVENTORY.md — core HRMS tables lack tenant discriminator.

---

### 5. Employee Master Record
**Status**: **PRESENT**

**Entity**: `Employee.java`

**Structure** (composite design):
- `Employee.java` — aggregates five sub-entities via `@OneToOne`:
  - `personal.java` — name, DOB, gender, marital status, nationality
  - `Identification.java` — Aadhar, PAN, Passport, UAN, identity doc URLs
  - `Work.java` — department, job title, pay grade, work location (as string), shift times
  - `Contact.java` — phone, email, permanent/current address
  - `Report.java` — reporting manager, indirect manager, multi-level approvers

**Controller**: `EmployeeController.java` (35KB — primary endpoint)

**Service**: `UsersManagementService.java`

**Timestamps**: `@CreationTimestamp createdAt`, `@UpdateTimestamp updatedAt` (temporal, not audit trail)

---

### 6. Org Structure: Department, Designation, Work Location, Reporting Manager
**Status**: **PARTIAL**

| Component | Status | Evidence |
|---|---|---|
| **Department** | PARTIAL | `Work.department` as String (line 18); no Department entity |
| **Designation** | ABSENT | Not modeled as entity; stored in `Work.jobTitle` as String |
| **Work Location** | ABSENT | Not modeled as entity; location details in `Contact` (address fields) and `Work.workstationId` (String) |
| **Reporting Manager** | PRESENT | `Report.reportingManagerId` (String, line 15), `Report.reportingManagerName` (String, line 18); also multi-level approvers (`indirectManager`, `firstLevelApprover`, etc., lines 21–24) |

**Key Limitation**: Org structure is denormalized (values embedded as strings rather than FKs to master entities). Payroll-Bend-SBoot defines proper entity versions (`Department.java`, `Designation.java`, `WorkLocation.java` under `organization/`), but HRMS does not.

---

### 7. Leave Engine: Types, Balances, Requests, Approvals, Allocation
**Status**: **PRESENT**

**Entities**:
- `LeaveRequests.java` — primary leave request (dual approval: `reportingManagerStatus`, `hrStatus` at lines 56–61)
- `LeaveType.java` — leave type master (e.g., Annual, Sick, Casual)
- `LeaveBalance.java` — leave balance summary
- `EmployeeLeaveBalance.java` — per-employee balance
- `EmployeeMonthlyLop.java` — monthly Loss-of-Pay days (key for Payroll integration, line 230 DB_SCHEMA.md)
- `LeaveDocument.java` — supporting docs (medical certificates, etc.)

**Controllers**:
- `LeaveRequestController.java` — request submission/approval
- `LeaveTypeController.java` — type management
- `LeaveBalanceController.java` — balance queries

**Service**:
- `LeaveRequestsService.java` / `LeaveRequestServiceImpl.java`

**Approval Flow**: Dual-stage (Manager → HR), stored as enum status on `LeaveRequests` entity.

---

### 8. Holiday Calendar
**Status**: **PRESENT**

**Entity**: `Holiday.java`

**Fields**: `holiday_name`, `holiday_date`, `day_of_week`

**Controller**: `HolidayController.java`

---

### 9. Attendance Capture
**Status**: **PRESENT**

**Entities**:
- `Attendance.java` — daily attendance record (`emp_id`, `date`, `status`, `clock_in`, `clock_out`, `total_hours`)
- `ClockSession.java` — individual clock-in/out session linked to `Attendance`

**Controller**: `AttendanceController.java`

**Service**: `AttendanceService.java`

---

### 10. Timesheets
**Status**: **PRESENT**

**Entities** (dual implementation — newer is active):
- **Older**: `Timesheet.java` — legacy, largely superseded
- **Newer** (active): 
  - `Timesheets.java` (timesheet/) — weekly timesheet record
  - `ProjectEntry.java` — project per timesheet
  - `DayEntry.java` — daily hours per project
  - `TaskEntry.java` — task breakdown per day
  - `TimesheetsNotification.java` — submission reminders

**Controllers**:
- `TimesheetController.java` — older routes
- `TimesheetsController.java` (timesheet/) — 28KB, primary active endpoint

**Services**:
- `TimesheetService.java` (older)
- `TimesheetService.java` (timesheet/) — active service
- `TimesheetReminderService.java`

**Note**: Dual-entity design (TIMESHEET vs TIMESHEETS) due to incomplete migration (BUG-007, GAP_INVENTORY.md).

---

### 11. Approval Workflow (Generic, Reusable)
**Status**: **PARTIAL**

**Finding**: No generic workflow engine. Approval logic is hard-coded per feature:

- **Leave approvals** (LeaveRequests.java:56–61): `reportingManagerStatus` + `hrStatus` enums → dual-stage fixed flow
- **Report entity** (Report.java:21–24): multi-level approver fields (`indirectManager`, `firstLevelApprover`, `secondLevelApprover`, `thirdLevelApprover`) — strings, not reusable
- **Timesheet approvals** (via integration with managers and HR)

**Gap**: No `WorkflowEngine`, `ApprovalRule`, or `WorkflowTransition` entity. Each domain hard-codes its approval stages.

**Closest**: `ApprovalReminder.java` (notificationconfig/) — reminder *scheduling* for approvals, not the approval engine itself.

---

### 12. Notifications / Email
**Status**: **PRESENT**

**Entities**:
- `Notification.java` — notification log (`recipient_emp_id`, `message`, `type`, `sent_at`, `is_read`)
- **Reminder configs** (notificationconfig/):
  - `ApprovalReminder.java`
  - `EmployeeReminder.java`
  - `EscalationReminder.java`
  - `HrReminder.java`
  - `SupervisorReminder.java`
  - `RoleReminderConfig.java`
- **Timesheet reminders**: `TimesheetsNotification.java`

**Controllers**:
- `NotificationController.java`
- `NotificationSettingsController.java` (notificationconfig/)
- `RoleReminderConfigController.java`

**Services**:
- `MailService.java` — SMTP integration
- `NotificationService.java`
- `NotificationSettingsService.java` / `NotificationSettingsServiceImpl.java`
- `NotificationSchedularService.java` / `NotificationSchedularServiceImpl.java` (30KB — scheduler logic)

**Scheduler**: Spring `@Scheduled` tasks in scheduler package trigger email notifications.

---

### 13. Document or File Storage
**Status**: **PRESENT**

**Entity**: `EmployeeDocument.java`

**Fields**: `document_name`, `document_url` (Cloudinary), `upload_date`

**External Service**: Cloudinary (media storage)

**Related Storage**: LeaveDocument.java also stores leave-related documents (medical certificates, etc.)

---

### 14. Audit Trail (Who Changed What, When)
**Status**: **ABSENT**

**Evidence**:
- No `AuditLog.java`, `Changelog.java`, or `AuditTrail.java` entity.
- No `createdBy`, `updatedBy`, `createdAt`, `updatedAt` fields on most entities.
- `Employee.java` has `@CreationTimestamp createdAt` and `@UpdateTimestamp updatedAt` (lines 54–58) — **timestamp only, not user tracking**.
- No Hibernate `@Audited` or Spring Data audit annotations observed.

**Gap**: BUG-002 vicinity — no change log captures *who* modified *what* data *when*.

---

### 15. Master / Reference Data (Countries, States, Banks, Currencies)
**Status**: **PARTIAL**

**Reference Data Type** | **Storage** | **Status**
---|---|---
Countries | `personal.nationality` (String field, lines 45–46) | String, not entity
States | `Contact.state` (String) | String, not entity
Banks | NOT FOUND | ABSENT
Currencies | NOT FOUND | ABSENT
Leave Types | `LeaveType.java` entity | Proper entity ✓
Holidays | `Holiday.java` entity | Proper entity ✓

**Finding**: Core demographic/location lookups are not modeled as reference entities. They are hardcoded strings in employee profiles, limiting reusability and governance.

---

## Entity Inventory

**Total @Entity classes**: **39**

### By Category

| Category | Count | Entities |
|---|---|---|
| **User & Auth** | 2 | OurUsers, PasswordResetToken |
| **RBAC** | 3 | Role, Action, UserActionMapping |
| **Employee Profile** | 6 | Employee, personal, Identification, Work, Contact, Report |
| **Document** | 2 | EmployeeDocument, LeaveDocument |
| **Leave** | 5 | LeaveRequests, LeaveRequest (legacy), LeaveType, LeaveBalance, EmployeeLeaveBalance, EmployeeMonthlyLop |
| **Attendance & Clock** | 2 | Attendance, ClockSession |
| **Timesheet** | 5 | Timesheet (legacy), Timesheets, ProjectEntry, DayEntry, TaskEntry |
| **Notification & Reminder** | 7 | Notification, ApprovalReminder, EmployeeReminder, EscalationReminder, HrReminder, SupervisorReminder, TimesheetsNotification |
| **Project & Task** | 3 | Project, Task, Assignment |
| **Overtime** | 1 | OvertimeRequest |
| **Configuration** | 1 | RoleReminderConfig |
| **Holiday** | 1 | Holiday |
| **Report** | 1 | Report (already counted under Employee) |

**Legend**:
- *Entities listed separately from the main categories in the count to avoid double-listing Report.*

---

## Cross-Capability Dependencies

```
Authentication (2) 
  ↓ (required by)
Authorization / RBAC (3)
  ↓ (governs access to)
Employee Profile (6) → Leave Engine (5) → Holiday Calendar (1)
                    → Attendance (2)
                    → Timesheets (5)
                    → Notifications (7)
```

**Missing Links**:
- No Tenant Registry → No multi-org isolation
- No Audit Trail → No compliance trail
- No generic Approval Workflow → Feature-specific approval logic only

---

## Summary Table: 15 Capabilities

| # | Capability | Status | Main Entity | Main Controller | Main Service |
|---|---|---|---|---|---|
| 1 | Tenant/organization registry | **ABSENT** | — | — | — |
| 2 | Identity & authentication | **PRESENT** | OurUsers | AuthController | UsersManagementService |
| 3 | Authorization / roles / permissions | **PRESENT** | Role, Action, UserActionMapping | RoleController | RoleService |
| 4 | Tenant scoping on queries | **ABSENT** | — | — | — |
| 5 | Employee master record | **PRESENT** | Employee | EmployeeController | UsersManagementService |
| 6 | Org structure (dept, desig, location, mgr) | **PARTIAL** | Work, Report | EmployeeController | UsersManagementService |
| 7 | Leave engine (types, balances, requests, approval, allocation) | **PRESENT** | LeaveRequests, LeaveType, EmployeeMonthlyLop | LeaveRequestController | LeaveRequestsService |
| 8 | Holiday calendar | **PRESENT** | Holiday | HolidayController | (direct JPA) |
| 9 | Attendance capture | **PRESENT** | Attendance, ClockSession | AttendanceController | AttendanceService |
| 10 | Timesheets | **PRESENT** | Timesheets, ProjectEntry, DayEntry | TimesheetsController | TimesheetService |
| 11 | Approval workflow (generic, reusable) | **PARTIAL** | — | — | — |
| 12 | Notifications / email | **PRESENT** | Notification, ApprovalReminder, … | NotificationController | MailService |
| 13 | Document / file storage | **PRESENT** | EmployeeDocument | EmployeeController | CloudinaryServiceImpl |
| 14 | Audit trail (who changed what, when) | **ABSENT** | — | — | — |
| 15 | Master/reference data (countries, states, banks, currencies) | **PARTIAL** | LeaveType, Holiday (partial) | — | — |

---

## Known Related GAP_INVENTORY IDs

- **BUG-002**: "Cross-tenant data exposure risk. Core HRMS tables have no tenant column." — Directly blocks capabilities 1, 4, and 14.
- **BUG-007**: "Duplicate entity definitions: LeaveRequest.java + LeaveRequests.java, Timesheet.java + Timesheets.java." — Technical debt affecting capabilities 7, 10.
- **DEBT-003**: "Effectively zero automated test coverage." — No tests exist to verify any capability.
- **DEBT-013**: "Package/class name typos: `timeshhet/`, `leaveAndAttedance/`, `EmployyePortalContoller.java`." — Signals code quality concerns.

---

## Glossary

| Term | Definition |
|---|---|
| **PRESENT** | Capability fully implemented; entities, controllers, and services exist and are actively used. |
| **PARTIAL** | Capability partially implemented; some aspects exist but others are missing, incomplete, or hard-coded per feature. |
| **ABSENT** | No implementation; entities or logic do not exist or are not integrated. |
| **Entity count** | Count of `@Entity` classes across all packages; does not include `@Embeddable`, `@MappedSuperclass`, or DTOs. |
| **Tenant scoping** | Filtering queries by an organization/tenant ID to isolate multi-tenant data. |

---

**Report generated**: 2026-09-11  
**Branch verified**: `HRMS_Backend@main`  
**Source**: Direct code inspection + FEATURE_MAP.md + DB_SCHEMA.md + GAP_INVENTORY.md
