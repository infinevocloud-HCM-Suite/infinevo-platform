# Legacy → target field mapping, every table

Built 2026-10-16 from the frozen Java entities (`legacy/HRMS_Backend/.../entity`, `legacy/Payroll-Bend-SBoot/.../entity`), the branch's extracted data (`origin/core-migration-non-prod-data`, `code/migration/staging/`) and the Flyway DDL on `main` (`code/backend/migration/.../db/migration`, V001–V176).

Status words: **Carry** = migrate (branch does it or spec orders it) · **Add** = carry, needs a new loader · **Seed** = target fills it itself (Flyway or tenant setup) · **Drop** = not migrated, legacy stays readable 90 days · **Decide** = founder decision, numbered X-nn in §2.

> **Founder decision 2026-10-16: carry all history.** Every row marked Add or Decide in §3 is
> now **Carry**, with the recommendation column of §2 as the answer. Timesheets (X-11) and
> attendance (X-12) are carried too. `W-67` R2, R3 and R6 and `W-68` Task 3 reflect this.

## 1. Totals (after the decision)

| | HRMS | Payroll | Legacy total | Target |
|---|---|---|---|---|
| Tables in the frozen code | 39 | 97 | 136 | 129 on `main` + 2 from `W-68` |
| Carried | 30 | 61 | **91** | **61** filled |
| Not carried | 9 | 36 | 45 | 70 stay empty or seeded |

Not carried, by reason: duplicate twins 3 (`leave_request`, `employee_leave_balances`, `timesheet`) · logins and permissions 7 · reminder and setup config 12 · tax calculation results 7 · tax law masters 11 · superseded proof tables 3 · logs 2.

## 2. Decisions — all taken 2026-10-16 as recommended

| # | Question | Options | Recommendation |
|---|---|---|---|
| X-01 | HRMS `leave_requests` has **no leave type column** (entity `LeaveRequests`, staging keys). `core.leave_request.leave_type_id` is NOT NULL | a) load under a tenant leave type `LEGACY` · b) skip requests, carry balances only · c) infer from `reason` text | **a**. History visible, nothing invented |
| X-02 | HRMS `leave_balances` is five fixed columns (annual, sick, casual, maternity, paternity), not rows per type | seed those five `core.leave_type` codes for the founder tenant and split each row into five `core.leave_allocation` rows | yes |
| X-03 | Leave source per tenant. HRMS has leave for the founder's tenant only. The 151 other tenants have leave only in Payroll `leave_type` and `employee_leave_allocation`, whose shape the target copied | founder tenant from HRMS; every other tenant from Payroll | yes, amends `W-67` R2 |
| X-04 | Holidays, same split: HRMS `holidays` (founder) vs Payroll `holidays` per org | as X-03 | yes |
| X-05 | Salary structure: Payroll `ctc_structure` + `employee_earnings/benefits/reimbursements/fbp/variable/epf/esi` → `payroll.ctc_structure` + `employee_*`. Needs the component masters first (`earnings`, `deductions`, `benefits`, `reimbursements` → `payroll.earning` …) | carry the **active** structure per employee and the masters; drop revisions | **carry**. Re-entering CTC for 629 employees by hand is slower |
| X-06 | Payslip history. Legacy keeps **totals only** per employee per run (`employee_payruns`), no component lines. Target has `payroll.prior_payroll_month` built for exactly this (`W-38.1`), plus `payroll.payrun` / `employee_payrun` for runs | a) `payrun` + `employee_payrun` totals (branch) · b) also `prior_payroll_month` per employee per month so TDS projection and Form 16 see prior income (`W-38.2`) | **a + b**. Lines cannot be carried because legacy never stored them |
| X-07 | Tax declarations and proofs. Legacy `employee_investment_declaration` + 8 `employee_inv_*` and `employee_proof_of_investment` + `employee_poi_*` are **near 1:1** with the target tables | carry current financial year only if cutover falls inside it; otherwise drop | carry current FY; depends on cutover date |
| X-08 | `employee_tds` → `payroll.employee_tds` (1:1) | with X-07 | with X-07 |
| X-09 | Bank: `employee_bank_detail` → `core.employee_bank` 1:1 | carry | **carry** |
| X-10 | Org structure: `department`, `designation`, `workLocations` → `core.department`, `core.designation`, `core.work_location` 1:1. **But** `core.employee` has `department_id` only, no designation or work-location column | carry the three masters; `W-68`'s Flyway `V177` adds nullable `designation_id` and `work_location_id` to `core.employee` | carry all, columns added in `W-68` |
| X-11 | Timesheets, projects, tasks: HRMS `projects/tasks/assignments/timesheets/project_entry/task_entry/day_entry` are 1:1 with `hrms.*` (the target was modelled on the newer HRMS system) | drop (`W-67` R3) or carry since the shape matches | drop timesheets, **carry projects/tasks/assignments** (5 + 2 + 5 rows, trivial) |
| X-12 | Attendance: HRMS `attendance` + `clock_sessions` → `core.attendance` + `hrms.clock_session` 1:1 | drop or carry | drop, 9 rows of test data |
| X-13 | Reporting managers: HRMS `report` → `core.reporting_line` | carry | **carry**, approvals need managers |
| X-14 | Open reimbursement claims and salary deductions: `employee_reimbursement_request`, `employee_deduction` with status not PAID | carry open ones | carry open ones, drop paid |

## 3. Field mapping

### 3.1 Tenant and organisation

| Legacy table.field | Target table.field | Status / note |
|---|---|---|
| P `organization.id` | `core.tenant.tenant_id` via `org_mapping.json` | Carry |
| P `organization.organizationName` | `core.tenant.name` | Carry |
| P `organization.addressLine1/2, city, state, pinCode, email, timezone, industry, businessLocation` | — | Drop. `core.tenant` has no address; filing address goes to `core.work_location.is_filing_address` |
| P `organization.fileUrl, fileName, filePublicId` (logo) | `core.tenant.logo_document_id` via `core.document` | Add with `W-69` |
| P `organization.isOrgActive, isDeleted, hasRunPayroll` | `core.subscription.status` ACTIVE; deleted orgs skipped | Carry. X-23 duplicate names in `W-67` R5 |
| P `organization.createdBy, createdDate, updatedBy, updatedDate` | audit columns `system` | Carry as system |
| P `incomeTaxDetails.tanNumber, panNumber, tdsCircle, authorizedPersonName, authorizedPersonParent, authorizedPersonDesignation` | `payroll.tax_deductor.tan, pan, tds_circle, signatory_name, signatory_parent_name, signatory_designation` | Add, 1:1 |
| P `incomeTaxDetails.depositSchedule, employeeId` | `payroll.tax_deductor.signatory_employee_id` | Add |
| P `department.id, name, departmentCode, description, status` | `core.department.id, name, code, is_active` | Decide X-10 |
| P `designation.id, name, status` | `core.designation.id, name, is_active`; `code` = slug of name | Decide X-10 |
| P `workLocations.workLocationName, streetAddress1/2, city, state, zipCode, country, isFilingAddress, status` | `core.work_location.name, city, state, state_code, zip_code, country_code, is_filing_address, is_active`; `code` = slug | Decide X-10. Street lines dropped, no column |
| P `paySchedule.payScheduleType, payDay, workingDays, noOfWorkingDays, includeHolidays, includeWeekends, workingDaysCalculationType, payPeriodStartDate` | `payroll.pay_schedule.frequency, pay_day_of_month, working_days, pay_day_rule, first_period_start`; weekend/holiday flags → `core.lop_policy.weekends_payable, holidays_payable, working_day_basis` | Add. One row per tenant |
| P `preferences.end_day, payroll_report_day, is_leave_encashment_enabled` | — | Drop |
| P `orgSetupSteps.*` (10 flags) | `core.tenant_setup_step.step_code, completed_at` | Seed. Target recomputes from data |
| P `onboarding_status.*` | `core.tenant_setup_step` | Seed |
| P `masterConfig.componentName, configData` | — | Drop |
| P `organizationRole.roleName, accessType, isDefault, roleDescription, status` | `core.role.code, name, is_system` | Seed. `W-67` R6 |
| P `action.code, label, category, entity_key` | `reference.action.code, name, module` | Seed |
| P `organization_role_action.organization_role_id, action_id` | `core.role_action.role_id, action_code` | Seed |

### 3.2 Users and access

| Legacy table.field | Target table.field | Status / note |
|---|---|---|
| H `ourusers.email, name` ; P `companyUser.userEmail, firstName, lastName, phoneNumber` | Keycloak user + `core.user_account.email, first_name, last_name, phone_number, keycloak_user_id` | Carry via `load_keycloak.py` |
| H `ourusers.password`, P `companyUser.password` | — | Drop. Users reset password from the invitation |
| H `ourusers.emp_id, employee_id` ; P `employee.hr_user` | `core.employee.user_account_id` | Carry |
| H `ourusers.role, roles, city, blacklisted_token, is_non_blocked` | — | Drop |
| H `user_roles.user_id, role_id` ; P `organizationUserRoleMapping.userId, roleId, isEmployeePortalEnable` | `core.user_role.user_account_id, role_id` ; `core.employee.is_portal_enabled` | Carry roles by code match (ADMIN→tenant_admin, HR→hr, …) |
| H `user_action_mapping.*` (3,149 rows) | — | Seed. Actions come from the role |
| H `role.*`, `action.*` | — | Seed |
| P `organizationUserMapping.userId, organizationId` | `core.user_tenant.user_id, tenant_id` | Carry |
| P `companyUser.companyName, country, states, toc` | — | Drop |
| P `userInvitations.email, roleId, status, acceptance_token, expiry_date, isInvitationAccepted` | `core.user_invitation.email, status, expires_at, accepted_at` + `core.user_invitation_role.role_id` | Add, pending ones only; new `token_hash` |
| P `employeeInvitation.email, employeeId, isPortalEnabled, isInvitationAccepted, acceptance_token, expiry_date, rejection_reason` | `core.employee_invitation.email, employee_id, status, expires_at, accepted_at, decline_reason` | Carry (branch creates fresh invitations for cohort 2; legacy pending ones map here) |
| H `password_reset_token.*` | — | Drop, Keycloak |

### 3.3 Employee master

| Legacy table.field | Target table.field | Status / note |
|---|---|---|
| H `personal.emp_id` / P `employee.employee_number` | `core.employee.employee_number` | Carry, `W-67` R1 |
| H `personal.first_name, middle_name, last_name, gender` / P same | `core.employee.first_name, middle_name, last_name, gender` | Carry, HRMS wins |
| H `work.doj` / P `employee.date_of_joining` | `core.employee.date_of_joining` | Carry, no fallback (`W-68`) |
| H `work.termination_date` | `core.employee.termination_date` | Add |
| H `contact.work_email` / P `employee.work_mail` | `core.employee.work_email` | Carry, match key |
| H `contact.mobile_number` / P `employee.mobile` | `core.employee.mobile` | Carry |
| P `employee.employee_status, is_portal_enabled, is_deleted` | `core.employee.status, is_portal_enabled, is_deleted` | Carry |
| P `employee.department_id` ; H `work.department` (text) | `core.employee.department_id` | Decide X-10; HRMS text matched by name |
| P `employee.designation_id, work_location_id` | — | Decide X-10, no column |
| P `employee.employeeId` (UUID), `employee_unique_id`, `tag`, `amount_in_percentage`, `salaryStructure` | — | Drop. Legacy ids kept in the run's id-map file in Blob |
| H `personal.employment_status` | `core.employee_employment.employment_type` | Add |
| H `personal.date_of_birth, marital_status, nationality, ethnicity` | `core.employee_personal.date_of_birth, marital_status, nationality, ethnicity` | Carry; ethnicity Add |
| P `employee_personal_detail.date_of_birth, father_name, differently_abled_type, is_eligible_for_full_income_tax_exemption` | `core.employee_personal.date_of_birth (fallback), father_name, differently_abled_type, is_eligible_for_full_tax_exemption` | Add. **Table not extracted by the branch** |
| P `employee_personal_detail.personal_mail, presentResidentialAddress (embedded), custom_fields, pan` | `core.employee_contact.personal_email (fallback)`, city/state/zip (fallback); `pan` fallback to identification | Add |
| H `contact.personal_email, residential_address, city, state, country, postal_code` | `core.employee_contact.personal_email, city, state, zip_code` | Carry. `residential_address`, `country` have no column → Drop |
| H `contact.permanent_address` | `core.employee_contact.permanent_city, permanent_state, permanent_zip_code` | Add, only if parseable; else Drop |
| H `contact.primary_emergency_contact_name/number, relationship_to_primary_emergency_contact` | `core.employee_contact.emergency_contact_name, emergency_contact_number, emergency_contact_relationship` | Add, 1:1 |
| H `contact.secondary_emergency_contact_name/number, relationship_to_secondary_emergency_contact` | `core.employee_contact.secondary_emergency_contact_*` | Add, 1:1 |
| H `contact.family_doctor_name, family_doctor_contact_number` | `core.employee_contact.family_doctor_name, family_doctor_contact_number` | Add, 1:1 |
| H `identification.pan_card_number, aadhar_card_number` | `core.employee_identification.pan_number, aadhaar_number` | Carry |
| H `identification.immigration_status, personal_tax_id, social_insurance, id_proof, document_name, document_number, address_proof, address_document_name, address_document_number` | `core.employee_identification.immigration_status, personal_tax_id, social_insurance_number, id_proof_type, id_document_name, id_document_number, address_proof_type, address_document_name, address_document_number` | Add, 1:1 |
| H `work.job_title` | `core.designation` by name → X-10 | Decide |
| H `work.pay_grade, workstation_id, time_zone, shift_start_time, shift_end_time` | `core.employee_employment.pay_grade, workstation_id, time_zone, shift_start_time, shift_end_time` | Add, 1:1 |
| H `report.reporting_manager_id, indirect_manager, first/second/third_level_approver, note` | `core.reporting_line.manager_id, kind (DIRECT, INDIRECT, APPROVER_1..3)`; `core.employee_employment.note` | Decide X-13 |
| P `employee.is_eligible_for_pf/pt/lwf/esi/eps, can_contribute_to_eps_on_higher_wages, is_director, pf_account_number, uan, esi_number` | `payroll.employee_statutory_profile.*` same names | Carry |
| P `employee_bank_detail.payment_mode, accountHolderName, bank_name, ifsc_code, bank_account_number, bank_account_type` | `core.employee_bank.payment_mode, account_holder_name, bank_name, ifsc_code, bank_account_number, bank_account_type` | Decide X-09, 1:1 |
| H `employee.*` (link ids only) | — | Join table, nothing to carry |

### 3.4 Salary components and structure

| Legacy table.field | Target table.field | Status / note |
|---|---|---|
| P `earnings.earningName, displayName, earningType, valueType, value, amount, maxLimit, isProRata, isIncludedInCtc, isIncludedInSalaryStructure, isTaxable, isVariable, isOneTimeComponent, isFbpComponent, isIncludedInEpf, epfInclusionType, isIncludedInEsi, showInPayslip, isScheduledEarning, earningFrequency, parentEarningId, status, isDeleted` | `payroll.earning.name, display_name, earning_type, calculation_type, default_value, percentage_of, max_limit, is_pro_rata, is_included_in_ctc, is_included_in_salary_structure, is_taxable, is_variable, is_one_time, is_fbp_component, is_included_in_epf, epf_inclusion_type, is_included_in_esi, show_in_payslip, is_scheduled_earning, earning_frequency, parent_earning_id, is_active, is_deleted`; `code` = slug | Decide X-05, 1:1 |
| P `earnings.*Formatted, can*, isUserConfigurable, isAssociatedWithEmployee, gratuityExemptionRuleDetails, formulaBasedOn` | — | Drop, UI flags |
| P `deductions.deductionName, deductionType, isRecurring, perquisiteInterestRate, emiInterestRate, emiType, status, isDeleted` | `payroll.deduction.name, deduction_type, is_recurring, perquisite_interest_rate, emi_interest_rate, emi_type, is_active, is_deleted` | Decide X-05 |
| P `benefits.benefitName, benefitPlan, benefitCategory, isPreTax, isOneTime, isProRata, isSuperannuationBenefit, isIncludedInCtc, isIncludedInSalaryStructure, taxExemptSection, taxExemptionSubType, canAllowEmployer/EmployeeContribution, isDeleted, status` | `payroll.benefit.*` same meaning, snake_case | Decide X-05 |
| P `reimbursements.reimbursementName, reimbursementType, displayName, maxLimit, isIncludedInCtc, isIncludedInSalaryStructure, isFbpComponent, isOptIn, carryForwardOption, isDeleted, status` | `payroll.reimbursement.*` same, snake_case | Decide X-05 |
| P `ctc_structure.annualCtc, monthlySalary, effective_date, is_active, payment_month, revision_status, is_revision, change_in_percent, applied_in_payrun, is_deleted` | `payroll.ctc_structure.annual_ctc, monthly_ctc, effective_from, is_cancelled, notes` | Decide X-05. Active row only; revision fields → `notes` |
| P `employee_earnings.earning_id, enabled, amount, amountInPercentage, calculation_basis, earningFrequency, overrideAmount` | `payroll.employee_earning.component_id, is_enabled, value, calculation_type, percentage_of, earning_frequency, monthly_amount, annual_amount` | Decide X-05 |
| P `employee_benefits.benefit, enabled, amount, amount_in_percentage` | `payroll.employee_benefit.component_id, is_enabled, value, calculation_type` | Decide X-05 |
| P `employee_reimbursements.reimbursement, enabled, amount, carryForwardOption` | `payroll.employee_reimbursement.component_id, is_enabled, value, carry_forward_option` | Decide X-05 |
| P `employee_fbp_components.componentCode, enabled, amount, amount_in_percentage, max_limit` | `payroll.employee_fbp_component.earning_id or reimbursement_id, annual_amount, monthly_amount` | Decide X-05 |
| P `employee_variable_earnings.variableCode, enabled, amount, amount_in_percentage` | `payroll.employee_earning` with `is_variable` earning | Decide X-05 |
| P `ctc_epf_components.componentCode, componentLabel, percentage, calculationType, monthlyAmount, annualAmount` | `payroll.ctc_epf_component.component_code, share, rate, wage_base, monthly_amount, annual_amount` | Decide X-05 |
| P `ctc_esi_components.*` | `payroll.ctc_esi_component.*` | Decide X-05 |
| P `FBP.isFbpEnabled, lastDateForFbpDeclaration, isFbpDeclarationLocked, sendMailOnFbpDeclarationRelease/Lock, isAnyReminderBeforeLockdateEnabled` | `payroll.fbp.is_enabled, window_closes_on, is_locked, notify_on_release, notify_on_lock, reminder_days_before_close` | Add, one row per tenant |
| P `one_time_payout.earning_id, employee_id, earningAmount, payDate, days, taxes` | `payroll.scheduled_earning` or `core.pay_input` | Drop history; open ones → X-14 |

### 3.5 Statutory settings

| Legacy table.field | Target table.field | Status / note |
|---|---|---|
| P `epf.isActive, registrationNumber, registrationDate, deductionCycle, epfEmployeeContribution, epfEmployerContribution, epsEmployerContribution, edliEmployerContribution, epfAdminChargesEmployerContribution, isEmployeeRestrictedBasicEnabled, isEmployerRestrictedBasicEnabled, canProRateRestrictedBasic, considerEarnedSalaryForEpf, epsSeniorCategoryAge, isEmployerContributionIncludedCtc, isAdminChargesIncludedCtc, isEdliIncludedCtc, isEmployerContributionIncludedSalaryStructure, isEdliIncludedSalaryStructure, isEligibleForAbryScheme` | `payroll.epf_setting.is_enabled, registration_number, registration_date, deduction_cycle, employee_rate, employer_rate, eps_rate, edli_rate, admin_charge_rate, restrict_employee_to_ceiling, restrict_employer_to_ceiling, prorate_restricted_wage, consider_earned_wage, eps_senior_age, include_employer_in_ctc, include_edli_admin_in_ctc, include_edli_in_ctc, include_employer_in_structure, include_edli_admin_in_structure, abry_scheme` | Add, 1:1 per tenant |
| P `esi.isActive, employeeContribution, employerContribution, registrationNumber, registrationDate, deductionCycle, canEnableEmployerEsiInCtc, isIncludedInSalaryStructure` | `payroll.esi_setting.is_enabled, employee_rate, employer_rate, registration_number, registration_date, deduction_cycle, include_employer_in_ctc, include_in_structure` | Add, 1:1 |
| P `professionalTax.state, stateCode, registrationNumber, effectiveFrom, deductionFrequency` + `slabRateConfiguration.*` + `slabDetail.startAmount, endAmount, payAmount, isFemaleExempted, deductionMonth` | `payroll.org_pt_override.state_code, registration_number, effective_from` + `payroll.org_pt_override_slab.from_amount, to_amount, amount, is_female_exempt, deduction_months` only where the org changed the state default; otherwise `reference.pt_slab` | Add. Compare to reference, write overrides only |
| P `org_pt_override.state, overrideJson, effectiveFrom, registrationNumber` | same as above | Add |
| P `pt_history.*`, `TaxSlabDetailHistory.*` | `payroll.pt_history` | Drop, audit |
| P `taxSlabMaster, taxSlabMasterHistory, hraRuleMaster, homeLoanRuleMaster, letOutPropertyRuleMaster, otherIncomeRuleMaster, standardDeductionRuleMaster, cessSurchargeRuleMaster, section87a_rebate_rule_master, section6a_item_master` | `reference.tax_slab_master, tax_slab_master_history, tax_slab_detail_history, hra_rule_master, home_loan_rule_master, let_out_property_rule_master, other_income_rule_master, standard_deduction_rule_master, cess_surcharge_rule_master, section` | Seed. National law, Flyway owns it (`D-08`) |

### 3.6 Pay runs and history

| Legacy table.field | Target table.field | Status / note |
|---|---|---|
| P `payruns.payPeriodStartDate, payPeriodEndDate, payDate, processingPeriod, type, status, approvedDate, noOfEmployees, noOfSkippedEmployees, totalPayrollCost, totalDeductions, totalNetPay, rejectedReason` | `payroll.payrun.period_start, period_end, pay_date, period, run_type, status, approved_at, included_count, skipped_count, total_gross, total_deductions, total_net_pay, notes`; `paid_on` = pay_date when PAID | Carry |
| P `payruns.totalTaxes, totalBenefits, totalBonus, totalClaimDeduction, totalClaimReimbursement, totalEpfContribution, totalEsiContribution, totalEdliContribution, totalEpfAdminCharges, totalDonations` | — | Drop at run level; per-employee totals carry below |
| P `payruns.earning, deductions, variablePayEarningsListJson, expenseBatchesDetailsJson, processingExtraJson, approvalDetails, statusInfo, is*/can*/has* flags, timeLeftForPayrollApprovalInMillis, compensationName, baseDays` | — | Drop |
| P `employee_payruns.payrun_ref_id, employee_ref_id` | `payroll.employee_payrun.payrun_id, employee_id` | Carry |
| P `employee_payruns.totalEarnings, totalReimbursements, totalBenefits, totalDeductions, netPay, paidDays, lwp_days, totalNoOfLeaves` | `payroll.employee_payrun.gross_earnings, total_reimbursements, total_benefits, total_deductions, net_pay, paid_days, lop_days, unpaid_days` | Carry |
| P `employee_payruns.totalTaxes, monthlyTds, LOP, lwp_amount, bonus, claim_deduction, claim_reimbursement, monthlySalary` | `payroll.employee_payrun_line` as synthetic lines `TDS`, `LOP_DEDUCTION`, `BONUS`, `CLAIM_DEDUCTION`, `CLAIM_REIMBURSEMENT` (`line_kind`, `component_code`, `amount`) | Decide X-06. Legacy has no real lines |
| P `employee_payruns.totalEarnings, totalTaxes, netPay` + `payruns.processingPeriod` | `payroll.prior_payroll_month.period, gross_earnings, tds, net_pay`; `epf_employee, esi_employee, professional_tax` **unknown in legacy → 0** | Decide X-06 |
| P `employee_payruns.paymentStatus, paymentMode, employeeStatus, employeeHavingHoldSalary, taxOverridden, bonusEarningExistForEmployee, claim_*_status, employeeName, fullName, employeeNumber` | — | Drop |
| P `off_cycle_pay_run.payDate, status, type, notes` + `off_cycle_payrun_employee.*` + `_earnings`, `_deductions` | `payroll.payrun` with `run_type = OFF_CYCLE` + `employee_payrun` totals | Add if any rows; sums from the two child tables |
| P `employee_reimbursement_request.reimbursement_type, requested_amount, approved_amount, bill_date, description, status, remarks, approved_by, approved_at, attachment_url` | `payroll.employee_reimbursement_request.reimbursement_id, requested_amount, approved_amount, bill_date, description, status, remarks, approved_by, approved_at, document_id` (file via `W-69`) | Decide X-14 |
| P `employee_deduction.deduction_amount, deduction_month, deduction_type, reason, remarks, status, proof_url` | `payroll.employee_deduction.amount, period, deduction_type, reason, remarks, status, document_id` | Decide X-14 |

### 3.7 Tax declarations and proofs

| Legacy table.field | Target table.field | Status / note |
|---|---|---|
| P `IncomeTaxDeclaration.lastDateForItDeclaration, isItDeclarationLocked, canChangeTaxRegimeIt, default_tax_regime, isPanMandatoryForAnnualRentOverOneLakh, sendMailOnItDeclarationLock/Release` | `payroll.income_tax_declaration.window_closes_on, is_locked, can_change_tax_regime, default_tax_regime, pan_required_for_rent_over_threshold, notify_on_lock, notify_on_release` | Add, one row per tenant per FY |
| P `ProofOfInvestment.lastDateForPoi, isPoiLocked, monthToConsiderPoi` | `payroll.income_tax_declaration.poi_opens_on` | Add |
| P `employee_investment_declaration.employee_id, fiscal_year, tax_regime, status, is_staying_in_rented_house, is_repaying_self_occupied_loan, has_let_out_property` | `payroll.employee_investment_declaration.employee_id, financial_year, tax_regime, status, is_staying_in_rented_house, is_repaying_self_occupied_loan, has_let_out_property` | Decide X-07, 1:1 |
| P `employee_inv_house_rent.from_month, to_month, address, landlord_name, landlord_pan, is_metro, amount_per_month` | `payroll.employee_inv_house_rent.*` same names | Decide X-07, 1:1 |
| P `employee_inv_home_loan.principal_paid, interest_paid, lender_name, lender_pan` | `payroll.employee_inv_home_loan.*` same | Decide X-07 |
| P `employee_inv_let_out_property.property_name, address, net_income_loss` + `_detail.type, amount, name_of_lender, pan_of_lender` | `payroll.employee_inv_let_out_property.*` + `_line.line_type, amount, lender_name, lender_pan` | Decide X-07 |
| P `employee_inv_other_income.type, name, amount` | `payroll.employee_inv_other_income.kind, description, amount` | Decide X-07 |
| P `employee_inv_pre_tax_deduction.type, amount` | `payroll.employee_inv_pre_tax_deduction.kind, amount` | Decide X-07 |
| P `employee_inv_prev_employment.type, name, amount` | `payroll.employee_inv_prev_employment.kind, employer_name, amount` | Decide X-07 |
| P `employee_inv_section6a.category, type, amount, section6a_item_id` | `payroll.employee_inv_pre_tax_deduction` or `reference.section` lines | Decide X-07, shape differs |
| P `employee_inv_tax_summary.regime, taxable_income, net_taxable_income, tax_on_taxable_income, tax_ytd_amount, tax_to_be_paid, tds_through_payroll, tds_previous_employer, tds_other_income, other_sources_income, no_of_remaining_months` | `payroll.employee_inv_tax_summary.*` same names, `remaining_months` | Decide X-07, 1:1 |
| P `employee_tds.fiscal_year, tds_source_type, poi_id, tax_regime, annual_gross_salary, annual_taxable_income, final_annual_tax, effective_from_month, is_active` | `payroll.employee_tds.financial_year, source, declaration_id, regime, annual_gross, annual_taxable_income, annual_tax, effective_from_period, is_active` | Decide X-08 |
| P `employee_proof_of_investment.declaration_id, employee_id, fiscal_year, status, submitted_date, approved_date` | `payroll.employee_proof_of_investment.declaration_id, employee_id, financial_year, status, submitted_at, decided_at` | Decide X-07 |
| P `employee_poi_item.investment_type, declared_amount, actual_amount, approved_amount, status, admin_comment` | `payroll.employee_proof_item.source_kind, declared_amount, claimed_amount, approved_amount, status, reviewer_note` | Decide X-07 |
| P `employee_poi_item_comment.comment, commented_by_employee_id, commented_by_admin` | `payroll.employee_proof_item_comment.body, author_employee_id, author_role` | Decide X-07 |
| P `employee_poi_document.document_name, document_url` | `core.document` + `payroll.employee_proof_item_document.document_id` | Carry (`W-69`) |
| P `employee_poi_property_detail.*` | `payroll.employee_inv_let_out_property_line` | Decide X-07 |
| P `employee_investment_proof.*`, `employee_investment_proof_file.*`, `proof_of_investment_document.*` | — | Drop. Older POI design, superseded by `employee_proof_*` in legacy itself |
| P `new_tax_calculation.*`, `old_tax_calculation.*`, `old_tax_section_deduction.*`, `old_tax_calculation_revision.*`, `old_tax_revision_section_deduction.*`, `employee_tax_calculation_result.*`, `employee_tax_recalculation.*` | `payroll.tax_computation` | Drop. Recomputed by the target from the declaration |
| P `ReimbursementClaim.*` (org flags) | `payroll.reimbursement` flags | Drop |
| P `reminder.*` | `core.reminder_rule` | Seed |

### 3.8 Leave, holidays, attendance

| Legacy table.field | Target table.field | Status / note |
|---|---|---|
| H `leave_types.name, description, default_days, is_active, start_date, end_date, leave_carried_forward` | `core.leave_type.name, is_active, valid_from, valid_to`; `core.leave_policy.annual_days, carry_forward_enabled` | Carry, founder tenant (X-03) |
| H `leave_types.employee_id, apply_to_all_employees` | `core.leave_policy_eligibility` | Drop |
| P `leave_type.name, code, description, type, unit, status, allow_half_day, validity_from, validity_to, gender` | `core.leave_type.name, code, is_paid (type=PAID), unit, is_active, allow_half_day, valid_from, valid_to`; `core.leave_policy.gender` | Decide X-03, 1:1 |
| P `leave_type.accrual_is_enabled, accrual_frequency, accrual_units, reset_is_enabled, reset_frequency, carry_forward_is_enabled, carry_forward_units, past_booking_limit_days, future_booking_limit_days, include_weekend_is_enabled, include_holiday_is_enabled, exceed_balance_is_enabled, exceed_balance_mode, pro_rate_is_enabled, max_leave_per_application` | `core.leave_policy.accrual_enabled, accrual_frequency, accrual_units, reset_enabled, reset_frequency, carry_forward_enabled, carry_forward_cap, past_booking_limit_days, future_booking_limit_days, include_weekend, include_holiday, exceed_balance_mode, exceed_balance_limit_days, pro_rate_enabled, max_days_per_application` | Decide X-03, 1:1 |
| P `leave_type.departments, designations, workLocations` | `core.leave_policy_eligibility.dimension, value_id` | Decide X-03 |
| P `leave_type.encashment_*, effective_after_*, include_*_min_days, accrual_regular_hours` | — | Drop |
| H `leave_balances.year, annual/sick/casual/maternity/paternity _allotted, _used` | `core.leave_allocation.leave_year, leave_type_id (five seeded types), entitlement_days`; used → `core.leave_consumption.consumed_days` one row per type | Decide X-02 |
| H `employee_leave_balances.leave_type_id, remaining_days` | — | Drop, `W-67` R2 |
| P `employee_leave_allocation.leave_type, year, annual_days, carried_forward_days, consumed_days, expiration_date` | `core.leave_allocation.leave_type_id, leave_year, entitlement_days, carried_forward_days, carry_forward_expires_on`; consumed → `core.leave_consumption` | Decide X-03 |
| P `employee_leave_allocation.monthly_*_breakdown, lwp, lop_days, leave_month` | — | Drop, derived |
| P `employee_leave_balance_consumption.leave_id, allocation_id, consumed_days, leave_month, reason, lop_days, lwp` | `core.leave_consumption.leave_request_id, allocation_id, consumed_days, period, reason`; lop → `core.leave_monthly_lop.lop_days` | Decide X-03 |
| H `leave_requests.employee_id, from_date, to_date, is_half_day, half_day_period, total_days, reason, hr_status, reporting_manager_status, hr_updated_at` | `core.leave_request.employee_id, from_date, to_date, is_half_day, half_day_period, working_days, reason, status, decided_at` | Carry |
| H `leave_requests.leave_type_id` | `core.leave_request.leave_type_id` | **Does not exist in legacy** → Decide X-01 |
| H `leave_requests.medical_certificate_id` + `leave_documents.file_name, file_type, file_url` | `core.leave_request_document.document_id` via `core.document` | Add with `W-69` |
| H `leave_requests.hr_comment, reporting_manager_comment, late_reason, days_allocated, leave_balance_deducted, leave_balance_recredited, lop_generated, lop_days, selected_date` | `core.approval_step.comment` for the two comments; rest Drop | Add comments, Drop rest |
| H `leave_request` (older) | — | Drop, count logged (`W-67` R2) |
| H `employee_monthly_lop.year, month, lop_days, leave_type_id, payroll_processed` | `core.lop_mismatch_log.hrms_unpaid_days` only | Carry into the mismatch log, not into `core.leave_monthly_lop` (`W-67` R4) |
| H `holidays.name, date` | `core.holiday.name, from_date, to_date` under one default `core.holiday_calendar` | Carry, founder tenant |
| P `holidays.name, from_date, to_date, description, is_restricted_holiday, location, status` | `core.holiday.name, from_date, to_date, description, is_restricted` + `core.holiday_calendar_location.work_location_id` | Decide X-04 |
| H `attendance.employee_id, in_time, out_time, status, end_day` + `clock_sessions.in_time, out_time` | `core.attendance.employee_id, attendance_date, status, source` + `hrms.clock_session.clock_in_at, clock_out_at` | Decide X-12 |
| P `attendance_preferences.full_day_minimum_hours, half_day_minimum_hours, calculation_on_firstin_lastout, regularization.*, maximum_requests_allowed, request_days_buffer` | `hrms.attendance_preference.full_day_minimum_hours, half_day_minimum_hours, hours_calculation, regularization_window_days, max_regularizations_per_month` | Add, one row per tenant |
| P `attendance_preferences.can_include_holidays/leaves/weekends_for_pay` | `core.lop_policy.holidays_payable, weekends_payable` | Add |
| H `overtime_requests.employee_id, start_time, end_time, category, notes, hr_status, manager_status, comp_off_created` | `core.overtime_request.employee_id, overtime_date, hours, remarks, status` | Drop history; pending → Add |
| P `employee_leave_import.*` | `core.leave_import_log` | Drop, import audit |

### 3.9 Timesheets, projects, tasks

| Legacy table.field | Target table.field | Status / note |
|---|---|---|
| H `projects.name, category, description, start_date, end_date, priority, budget, status, progress, manager_id` | `hrms.project.name, category, description, start_date, end_date, priority, budget, status, progress, manager_employee_id` | Decide X-11, 1:1 |
| H `tasks.title, description, project_id, assignee_id, due_date, priority, status, estimated_hours` | `hrms.task.*` same names, `assignee_employee_id` | Decide X-11, 1:1 |
| H `assignments.project_id, emp_id` | `hrms.assignment.project_id, employee_id` | Decide X-11 |
| H `timesheets.week_start_date, week_end_date, status, submitted_at, employee_id` | `hrms.timesheet.*` same | Decide X-11 (recommend Drop) |
| H `project_entry.project_id, status, rejection_reason` | `hrms.timesheet_project_entry.project_id, status, rejection_reason` | with timesheets |
| H `task_entry.task_id` | `hrms.timesheet_task_entry.task_id` | with timesheets |
| H `day_entry.date, hours, description` | `hrms.timesheet_day_entry.work_date, hours, description` | with timesheets |
| H `timesheet` (older, 0 rows in non-prod) | — | Drop |
| H `timesheet_notifications.*`, `notifications.*` | `core.notification` | Drop |

### 3.10 Documents

| Legacy table.field | Target table.field | Status / note |
|---|---|---|
| H `employee_document.file_name, file_type, document_type, file_url, employee_id` | `core.document.file_name, content_type, kind, blob_path, size_bytes, checksum_sha256, employee_id` | Carry (`W-69`) |
| H `employee_document.document_content (blob), public_id, company_index` | — | Drop; file comes from `file_url` |
| P `employee_poi_document.*` | see §3.7 | Carry |
| H `leave_documents.*` | see §3.8 | Add |
| P `employee_reimbursement_request.attachment_url, attachment_file_name` ; `employee_deduction.proof_url` | `core.document` + `document_id` | with X-14 |
| H `identification` Cloudinary URL fields | `core.document kind = ID_PROOF` | Add if the columns hold URLs |

### 3.11 Reminders, reports, platform

| Legacy table.field | Target table.field | Status / note |
|---|---|---|
| H `role_reminder_config`, `approval_reminders`, `employee_reminders`, `escalation_reminders`, `hr_reminders`, `supervisor_reminders` (`enabled, day, time, level, email`) | `core.reminder_rule.event, audience, day_of_week, send_at_local_time, is_active` | Seed from the country template |
| H `report.*` | see §3.3 (reporting managers) | Decide X-13 |
| — | `core.audit_log, job_status, shedlock, notification_template, report_definition, report_schedule, retention_run, impersonation_session, approval_*, subscription_module, tenant_template_applied, pay_input, pay_input_period_lock, migration_ledger, lop_mismatch_log` | Target-only, filled by the app or by the migration itself |
| — | `reference.country, state, currency, bank, country_template, action, pt_state, pt_slab` | Flyway seed |

## 4. Target tables that receive nothing (70)

`reference.*` (18) · `core.audit_log, job_status, shedlock, notification, notification_template, report_definition, report_schedule, retention_run, impersonation_session, approval_definition, approval_instance, approval_step, approval_delegation, subscription_module, tenant_setup_step, tenant_template_applied, pay_input, pay_input_period_lock, reminder_rule, leave_import_log, leave_monthly_lop, user_invitation (unless X), overtime_request (unless pending)` · `hrms.attendance_regularization, timesheet*, clock_session, attendance_preference (unless Add)` · `payroll.tax_computation, pt_history, prior_payroll_import_log, scheduled_earning, employee_payrun_line (unless X-06)`.
