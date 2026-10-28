# Prod migration scope — the 28 matched employees, every table

> 2026-10-26. Source: `d:\tmp\prod-dump\` — `hrmstestdb` and `payroll_proddb` dumped 2026-10-25 from `64.227.128.119` (`prod-live-1`).
> Scope: Payroll org 8 "Infinevocloud Technology Solutions Pvt" (`W-67` R5, 2026-10-26), the 28 employees matched to HRMS by `W-67` R1.
> **Target rows are projected** from the mapping (`2026-10-16-legacy-target-field-mapping.md`) and the rules, not read from a migrated database. Counts only, no personal data.

| Legacy DB | Legacy table | Rows (all) | Rows (28 employees) | Target schema.table | Target rows (projected) | Rule |
|---|---|---|---|---|---|---|
| hrmstestdb | `action` | 89 | 89 | — | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `approval_reminder_recipients` | 3 | 3 | `core.reminder_rule` | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `approval_reminders` | 3 | 3 | `core.reminder_rule` | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `assignments` | 52 | 46 | `hrms.assignment` | 46 |  |
| hrmstestdb | `attendance` | 303 | 266 | `core.attendance` | 266 |  |
| hrmstestdb | `clock_sessions` | 319 | 282 | `hrms.clock_session` | 282 |  |
| hrmstestdb | `contact` | 91 | 28 | `core.employee_contact` | 28 |  |
| hrmstestdb | `day_entry` | 12,124 | 11,039 | `hrms.timesheet_day_entry` | 5,977 | Zero-hour days counted, not loaded |
| hrmstestdb | `employee` | 91 | 28 | `core.employee` | 28 | Merged with Payroll `employee`: one row per person |
| hrmstestdb | `employee_document` | 155 | 79 | `core.document` | 79 |  |
| hrmstestdb | `employee_leave_balances` | 61 | 54 | — | 0 | Not migrated (R6) |
| hrmstestdb | `employee_reminders` | 2 | 2 | `core.reminder_rule` | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `escalation_reminder_recipients` | 2 | 2 | `core.reminder_rule` | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `escalation_reminders` | 1 | 1 | `core.reminder_rule` | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `holidays` | 20 | 20 | `core.holiday` | 20 | Masters: all rows, founder tenant |
| hrmstestdb | `hr_reminder_recipients` | 2 | 2 | `core.reminder_rule` | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `hr_reminders` | 2 | 2 | `core.reminder_rule` | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `identification` | 91 | 28 | `core.employee_identification` | 28 |  |
| hrmstestdb | `leave_balances` | 53 | 41 | `core.leave_allocation` | 205 | Five allocation rows per balance row (R2) |
| hrmstestdb | `leave_documents` | 2 | 2 | `core.leave_request_document` | 2 |  |
| hrmstestdb | `leave_manual_days` | 115 | 100 | — | 0 | Not migrated (R6) |
| hrmstestdb | `leave_request_lop` | 2 | 1 | — | 0 | Not migrated (R6) |
| hrmstestdb | `leave_requests` | 106 | 91 | `core.leave_request` | 91 |  |
| hrmstestdb | `leave_selected_dates` | 45 | 45 | — | 0 | Not migrated (R6) |
| hrmstestdb | `leave_type_employee_ids` | 99 | 90 | — | 0 | Not migrated (R6) |
| hrmstestdb | `leave_types` | 36 | 36 | `core.leave_type` | 36 | Masters: all rows, founder tenant |
| hrmstestdb | `migration_audit` | 2 | 0 | — | 0 | Not migrated (R6) |
| hrmstestdb | `ourusers` | 40 | 25 | `core.user_account` | 29 | Merged by email with `companyUser`: 24 in both, 29 people |
| hrmstestdb | `overtime_requests` | 2 | 1 | `core.overtime_request` | 1 |  |
| hrmstestdb | `password_reset_token` | 1 | 0 | — | 0 | Not migrated (R6) |
| hrmstestdb | `personal` | 91 | 28 | `core.employee_personal` | 28 |  |
| hrmstestdb | `project_entry` | 1,220 | 1,074 | `hrms.timesheet_project_entry` | 1,074 |  |
| hrmstestdb | `projects` | 10 | 10 | `hrms.project` | 10 |  |
| hrmstestdb | `report` | 91 | 28 | `core.reporting_line` | 28 | One row per manager kind |
| hrmstestdb | `role` | 6 | 6 | — | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `role_action_mapping` | 196 | 196 | — | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `supervisor_reminder_recipients` | 2 | 2 | `core.reminder_rule` | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `supervisor_reminders` | 2 | 2 | `core.reminder_rule` | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `task_entry` | 1,732 | 1,577 | `hrms.timesheet_task_entry` | 1,577 |  |
| hrmstestdb | `tasks` | 347 | 347 | `hrms.task` | 347 |  |
| hrmstestdb | `timesheet_notifications` | 2,338 | 2,205 | `core.notification` | 2,205 | Recipient is one of the 28 (R6: notifications migrate) |
| hrmstestdb | `timesheets` | 1,186 | 1,057 | `hrms.timesheet` | 1,057 |  |
| hrmstestdb | `user_action_mapping` | 1,383 | 927 | — | 0 | Not migrated: seeded by the platform |
| hrmstestdb | `user_roles` | 55 | 38 | `core.user_role` | up to 66 | With `organizationUserRoleMapping`; duplicates by role code collapse |
| hrmstestdb | `work` | 91 | 28 | `core.employee_employment` | 28 |  |
| payroll_proddb | `benefits` | 14 | 2 | `payroll.benefit` | 2 |  |
| payroll_proddb | `companyUser` | 74 | 28 | `core.user_account` | (in the 29) | Merged by email with `ourusers` |
| payroll_proddb | `ctc_epf_components` | 82 | 59 | `payroll.ctc_epf_component` | 59 |  |
| payroll_proddb | `ctc_esi_components` | 11 | 1 | `payroll.ctc_esi_component` | 1 |  |
| payroll_proddb | `ctc_structure` | 96 | 69 | `payroll.ctc_structure` | 28 | Active structure only (R6) |
| payroll_proddb | `deductions` | 14 | 2 | `payroll.deduction` | 2 |  |
| payroll_proddb | `department` | 14 | 7 | `core.department` | 7 |  |
| payroll_proddb | `designation` | 22 | 12 | `core.designation` | 12 |  |
| payroll_proddb | `earnings` | 35 | 6 | `payroll.earning` | 6 |  |
| payroll_proddb | `employee` | 56 | 28 | `core.employee + payroll.employee_statutory_profile` | 28 + 28 | Merged with HRMS `employee` |
| payroll_proddb | `employee_bank_detail` | 50 | 28 | `core.employee_bank` | 28 |  |
| payroll_proddb | `employee_deduction` | 9 | 1 | `payroll.employee_deduction` | 1 |  |
| payroll_proddb | `employee_earnings` | 409 | 299 | `payroll.employee_earning` | 299 |  |
| payroll_proddb | `employee_payruns` | 357 | 290 | `payroll.employee_payrun` | 290 |  |
| payroll_proddb | `employee_personal_detail` | 50 | 28 | `core.employee_personal` | (in the 28) | Fills gaps in HRMS `personal` |
| payroll_proddb | `employee_reimbursement_request` | 6 | 5 | `payroll.employee_reimbursement_request` | 5 |  |
| payroll_proddb | `employee_tds` | 101 | 87 | `payroll.employee_tds` | 56 | Current FY 2026-27 only (R6) |
| payroll_proddb | `employeeInvitation` | 77 | 27 | `core.employee_invitation` | 20 | Pending only |
| payroll_proddb | `epf` | 7 | 1 | `payroll.epf_setting` | 1 |  |
| payroll_proddb | `esi` | 7 | 1 | `payroll.esi_setting` | 1 |  |
| payroll_proddb | `home_loan_rule_master` | 4 | 0 | `reference.*` | 0 | Not migrated: Flyway `reference.*` |
| payroll_proddb | `hraRuleMaster` | 1 | 0 | `reference.*` | 0 | Not migrated: Flyway `reference.*` |
| payroll_proddb | `IncomeTaxDeclaration` | 4 | 1 | `payroll.income_tax_declaration` | 1 |  |
| payroll_proddb | `incomeTaxDetails` | 7 | 1 | `payroll.tax_deductor` | 1 |  |
| payroll_proddb | `letOutPropertyRuleMaster` | 2 | 0 | `reference.*` | 0 | Not migrated: Flyway `reference.*` |
| payroll_proddb | `masterConfig` | 7 | 0 | — | 0 | Not migrated (R6) |
| payroll_proddb | `org_pt_override` | 5 | 1 | `payroll.org_pt_override` | 1 |  |
| payroll_proddb | `organization` | 18 | 1 | `core.tenant (+ core.subscription)` | 1 |  |
| payroll_proddb | `organizationRole` | 21 | 4 | `core.role` | 0 | Not migrated: seeded by the platform |
| payroll_proddb | `organizationUserMapping` | 75 | 28 | `core.user_tenant` | 28 |  |
| payroll_proddb | `organizationUserRoleMapping` | 75 | 28 | `core.user_role` | (in the 66) |  |
| payroll_proddb | `orgSetupSteps` | 12 | 1 | `core.tenant_setup_step` | 0 | Not migrated: seeded by the platform |
| payroll_proddb | `otherIncomeRuleMaster` | 15 | 0 | `reference.*` | 0 | Not migrated: Flyway `reference.*` |
| payroll_proddb | `payruns` | 24 | 19 | `payroll.payrun` | 19 |  |
| payroll_proddb | `paySchedule` | 7 | 1 | `payroll.pay_schedule` | 1 |  |
| payroll_proddb | `payScheduleWorkingDays` | 36 | 5 | `payroll.pay_schedule` | 5 | Folded into the row above |
| payroll_proddb | `professionalTax` | 4 | 1 | `payroll.org_pt_override` | 1 | Only where org 8 differs from the state default |
| payroll_proddb | `ProofOfInvestment` | 2 | 1 | `payroll.income_tax_declaration` | 1 | Folded into the row above |
| payroll_proddb | `pt_history` | 4 | 2 | — | 0 | Not migrated (R6) |
| payroll_proddb | `reimbursements` | 42 | 6 | `payroll.reimbursement` | 6 |  |
| payroll_proddb | `reminder` | 12 | 0 | `core.reminder_rule` | 0 | Not migrated: seeded by the platform |
| payroll_proddb | `section6a_item_master` | 13 | 0 | `reference.*` | 0 | Not migrated: Flyway `reference.*` |
| payroll_proddb | `section87a_rebate_rule_master` | 2 | 0 | `reference.*` | 0 | Not migrated: Flyway `reference.*` |
| payroll_proddb | `slabDeductionMonths` | 12 | 3 | `payroll.org_pt_override_slab` | 3 | Folded into the row above |
| payroll_proddb | `slabDetail` | 28 | 7 | `payroll.org_pt_override_slab` | 7 | Only where org 8 differs from the state default |
| payroll_proddb | `standardDeductionRuleMaster` | 4 | 0 | `reference.*` | 0 | Not migrated: Flyway `reference.*` |
| payroll_proddb | `taxSlabMaster` | 4 | 0 | `reference.*` | 0 | Not migrated: Flyway `reference.*` |
| payroll_proddb | `userInvitations` | 18 | 1 | `core.user_invitation` | 0 | Pending only; the one row is accepted |
| payroll_proddb | `workLocations` | 20 | 9 | `core.work_location` | 9 |  |

**Totals:** 45 HRMS and 49 Payroll tables hold rows; 24,633 rows in all, 21,032 in scope.

**Empty in prod (61 tables), nothing to migrate:** `employee_monthly_lop`, `leave_request`, `notifications`, `reminder_recipients`, `role_reminder_config`, `timesheet`, `action`, `attendance_preferences`, `cess_surcharge_rule_master`, `employee_benefits`, `employee_fbp_components`, `employee_inv_home_loan`, `employee_inv_house_rent`, `employee_inv_let_out_property`, `employee_inv_let_out_property_detail`, `employee_inv_other_income`, `employee_inv_pre_tax_deduction`, `employee_inv_prev_employment`, `employee_inv_section6a`, `employee_inv_tax_summary`, `employee_investment_declaration`, `employee_leave_allocation`, `employee_leave_balance_consumption`, `employee_leave_import`, `employee_poi_document`, `employee_poi_item`, `employee_poi_item_comment`, `employee_poi_property_detail`, `employee_proof_of_investment`, `employee_reimbursements`, `employee_tags`, `employee_variable_earnings`, `EmployeeInvestmentProof`, `EmployeeInvestmentProofFile`, `FBP`, `holiday_locations`, `holidays`, `leave_type`, `leave_type_departments`, `leave_type_designations`, `leave_type_genders`, `leave_type_work_locations`, `new_tax_calculation`, `off_cycle_pay_run`, `off_cycle_payrun_employee`, `off_cycle_payrun_employee_deductions`, `off_cycle_payrun_employee_earnings`, `old_tax_calculation`, `old_tax_calculation_revision`, `old_tax_revision_section_deduction`, `old_tax_section_deduction`, `onboarding_status`, `one_time_payout`, `organization_role_action`, `preferences`, `proof_of_investment_document`, `ReimbursementClaim`, `Reminder`, `slabRateConfiguration`, `TaxSlabDetailHistory`, `taxSlabMasterHistory`.

