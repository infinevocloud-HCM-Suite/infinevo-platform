# Payroll-Bend-SBoot Core Capabilities Assessment — 2026-09-11

**Branch:** `taxation`  
**Repository:** `Payroll-Bend-SBoot`  
**Assessment date:** 2026-09-11  
**Total @Entity classes found:** 97

---

## Executive Summary

Payroll-Bend-SBoot implements 10 out of 15 "Core" capabilities at PRESENT level, 1 at PARTIAL level, and 4 at ABSENT level. The service is an **isolated payroll processor** that does not capture primary HR data (attendance, timesheets, approval workflows). It imports or receives leave/LOP data from HRMS and consumes salary components and employee master records.

Attendance capability is limited to configuration preferences only—no clock-in/out or attendance recording. Payroll has NO internal attendance capture system.

---

## Capability Assessment

| # | Capability | Status | Entity | Table | Controller | Service | Evidence |
|---|---|---|---|---|---|---|---|
| 1 | Tenant/organization registry | **PRESENT** | `Organization` | `organization` | `OrganizationController` | `OrganizationService` | `entity/organization/Organization.java:26-28` @Entity, `controller/organization/OrganizationController.java:24-25` REST mapping `/api/organizations` |
| 2 | Identity & authentication | **PRESENT** | `CompanyUser` | `companyUser` | `CompanyUserController` | `KeycloakUserService` | `entity/CompanyUser.java:5-6` @Entity, `config/SecurityConfig.java:31-33` OAuth2 Resource Server with Keycloak JWT, `serviceimpl/keycloak/KeycloakUserServiceImpl.java` (Keycloak Admin API) |
| 3 | Authorization / roles / permissions | **PRESENT** | `OrganizationRole`, `Action`, `OrganizationRoleAction` | `organizationRole`, `action`, `organization_role_action` | `OrganizationRoleController`, `ActionController`, `RoleActionController` | `OrganizationRoleService`, `ActionService` | `entity/auth/OrganizationRoleAction.java:8-10` @Entity, `entity/organization/OrganizationRole.java` (org-level roles), `entity/auth/Action.java` (fine-grained actions). Multi-tenant role mapping per organization. |
| 4 | Tenant scoping on queries | **PRESENT** | Multiple: `EmployeeLeaveAllocation`, `EmployeeReimbursementRequest`, `SalaryDeduction`, `OrgPTOverride`, `EmployeeInvestmentProof`, `EmployeeTds`, `OnboardingStatus` | All use `organization_id` column | Controllers accept `@RequestHeader("organizationId")` | Service impls validate org_id | `entity/leave/EmployeeLeaveAllocation.java:23` @Column "organization_id", `entity/employeereimbursement/EmployeeReimbursementRequest.java:34` @Column "organization_id", `controller/leaveAndAttendance/attendance/AttendancePreferenceController.java:23` request header org scoping. |
| 5 | Employee master record | **PRESENT** | `BasicDetails` | `employee` | `BasicDetailsController` | `BasicDetailsService` | `entity/employee/BasicDetails.java:12-14` @Entity table="employee", `controller/employee/BasicDetailsController.java` (14KB). Contains firstName, lastName, employeeId, joining date, department/designation/location FKs, organization FK. |
| 6 | Org structure: department, designation, work location, reporting manager | **PARTIAL** | `Department`, `Designation`, `WorkLocation` | `department`, `designation`, `workLocations` | `DepartmentController`, `DesignationController`, `WorkLocationController` | Respective services | `entity/organization/Department.java:26`, `entity/organization/Designation.java:27`, `entity/organization/WorkLocation.java:31` all @Entity. BasicDetails links to all three at `entity/employee/BasicDetails.java:60-70`. **NO reportingManager field or entity found** (grep: 0 results for "reportingManager", "reporting_manager", "ReportingManager"). |
| 7 | Leave engine: leave types, balances, requests, approvals, allocation | **PRESENT** | `LeaveType`, `EmployeeLeaveAllocation`, `EmployeeLeaveBalanceConsumption` | `leave_type`, `employee_leave_allocation`, `employee_leave_balance_consumption` | `LeaveTypeController`, `EmployeeLeaveAllocationController`, `EmployeeLeaveConsumptionController` | `EmployeeLeaveAllocationService`, `EmployeeLeaveConsumptionService` | `entity/leaveAndAttedance/LeaveType.java:13-14` @Entity, `entity/leave/EmployeeLeaveAllocation.java:7-16` @Entity with unique constraint on org+emp+type+year, `entity/leave/EmployeeLeaveBalanceConsumption.java:1-15` @Entity. Full allocation, consumption, and LOP/LWP tracking. |
| 8 | Holiday calendar | **PRESENT** | `Holiday` | `holidays` | `HolidayController` | `HolidayService` | `entity/leaveAndAttedance/holiday/Holiday.java:1` @Entity, `controller/leaveAndAttendance/holiday/HolidayController.java` API endpoints |
| 9 | Attendance capture | **ABSENT** | — | — | `AttendancePreferenceController` (config only) | `AttendancePreferenceService` (config only) | Only `AttendancePreference` entity exists (configuration), no entity for actual attendance records. Grep for "class.*Attendance" finds only `AttendancePreference.java`. No controller for recording/querying attendance entries. `AttendancePreferenceController.java:1-50` is configuration only (hours, regularization rules). **Payroll has ZERO attendance capture mechanism.** |
| 10 | Timesheets | **ABSENT** | — | — | — | — | Grep for "class.*Timesheet" returns 0 results. No timesheet entity, controller, or service in Payroll backend. Timesheets are HRMS-only feature. |
| 11 | Approval workflow (generic, reusable, vs hard-coded per screen) | **ABSENT** | — | — | — | — | Only hard-coded approval workflows per entity (e.g., reimbursement approve/reject in `AdminReimbursementController.java:1-50`). No generic `ApprovalWorkflow` or `WorkflowEngine` entity or service. Approval logic is inline in domain controllers. |
| 12 | Notifications / email | **ABSENT** | — | — | — | — | Grep for "@Entity" + "Notification\|Approval\|Audit" finds only `test/ExcelTestController.java` and `AdminReimbursementController.java` (the latter is approval, not notification). No `Notification`, `EmailTemplate`, `AuditLog`, or notification service. Payroll does NOT implement email notifications. |
| 13 | Document or file storage (payslips, investment proofs, attachments) | **PRESENT** | `EmployeeReimbursementRequest`, `EmployeeInvestmentProof`, `EmployeeInvestmentProofFile`, `ProofOfInvestmentDocument` | `employee_reimbursement_request`, `EmployeeInvestmentProof`, `EmployeeInvestmentProofFile` | — | `CloudinaryServiceImpl` | `entity/employeereimbursement/EmployeeReimbursementRequest.java:53-57` stores attachmentUrl/publicId/fileName (Cloudinary), `entity/taxCalculator/EmployeeInvestmentProof.java` and `EmployeeInvestmentProofFile.java` for POI file storage, `serviceimpl/CloudinaryServiceImpl.java` (1 file) handles all file operations |
| 14 | Audit trail (who changed what, when) | **ABSENT** | — | — | — | — | Grep for "AuditLog\|AuditTrail" returns 0 results. No audit entity or service. Some entities track `createdAt`, `updatedAt` timestamps (e.g., `OrganizationRoleAction.java:24-25`), but no WHO/WHAT change history. |
| 15 | Master/reference data (countries, states, banks, currencies, tax slabs) | **PRESENT** | `Epf`, `Esi`, `ProfessionalTax`, `SlabDetail`, `SlabRateConfiguration`, `TaxSlabMaster`, `TaxSlabMasterHistory`, `CessSurchargeRuleMaster`, `StandardDeductionRuleMaster`, `HraRuleMaster`, `LetOutPropertyRuleMaster`, `OtherIncomeRuleMaster`, `Section87ARebateRuleMaster`, `HomeLoanRuleMaster`, `IncomeTaxDetails` | Multiple statutory + tax slab tables | Controllers for EPF, ESI, PT, tax calculator | Statutory component services | `entity/statutorycomponents/Epf.java:1`, `ProfessionalTax.java:1`, `TaxSlabMaster.java:1`, `HraRuleMaster.java:1` all @Entity. Master data for tax regimes, deductions, surcharge rules. **Note:** Countries/states/currencies are hardcoded frontend config, not stored as entities in Payroll. Tax slab master table has historical tracking. |

---

## Key Cross-Service Notes

**Leave/LOP Source:**
- **Previously (before 2026-08-28):** Payroll called HRMS `/public/get-employee-leaves` REST endpoint to fetch `EmployeeMonthlyLop` data.
- **Now (2026-08-28 onwards):** Payroll manages its own leave allocation and consumption via `EmployeeLeaveAllocation` + `EmployeeLeaveBalanceConsumption`. LOP is derived locally and no longer pulled from HRMS.

**Attendance Source:**
- Payroll has NO attendance capture of its own.
- Payroll does NOT consume attendance data from HRMS for pay deductions.
- Loss-of-Pay is determined exclusively from leave allocation/consumption, not from attendance.

**Approval Workflows:**
- Hard-coded per domain (reimbursement, salary revision, POI).
- No generic reusable workflow engine.

---

## Entity Count Summary

| Category | Count |
|---|---|
| Total @Entity classes | **97** |
| Org/Auth/Access entities | 8 |
| Employee entities | 19 |
| Leave/Attendance entities | 7 |
| Salary component entities | 4 |
| Statutory component entities | 11 |
| Tax/IT Declaration entities | 32 |
| Pay Run entities | 6 |
| Claims/FBP entities | 3 |
| **Attendance capture entities** | **0** |
| **Timesheet entities** | **0** |
| **Notification entities** | **0** |
| **Approval workflow entities** | **0** |
| **Audit trail entities** | **0** |

---

## Critical Gaps (Relevant to GAP_INVENTORY.md)

1. **Attendance:** Payroll is entirely dependent on leave data for LOP calculation. No independent attendance validation.
2. **Reporting Manager:** Org structure is incomplete—no reportingManager field in BasicDetails. Cannot enforce manager approval workflows.
3. **Approval Workflow:** Hard-coded per screen, not reusable. Adding new approvals requires code change.
4. **Audit Trail:** No change history. Cannot answer "who approved when?" after the fact.
5. **Notifications:** No email alerts for pending approvals, rejected claims, or payslip readiness.

---

## Data Flow Summary

```
HRMS (leaves)
   ↓ (formerly /public/get-employee-leaves REST)
Payroll.EmployeeLeaveAllocation + EmployeeLeaveBalanceConsumption
   ↓ (LOP derived locally)
Pay Run Net Pay Calculation
   ↓ (LOP deduction applied)
EmployeePayRun.netPay
   ↓
Payslip
```

---

## References

- `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/` — all 97 @Entity classes
- `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/` — 61 controllers listed in FEATURE_MAP.md
- `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/config/SecurityConfig.java:31-33` — Keycloak OAuth2 setup
- `docs/DB_SCHEMA.md` (Appendix A) — 52 table definitions added 2026-09-11
- `docs/FEATURE_MAP.md` (§26-27) — Leave Allocation and Deduction features
