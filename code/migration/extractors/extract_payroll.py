"""
Payroll MySQL extractor (W-68.1).

Copies every carried Payroll table (mapping .claude/outputs/2026-10-16-legacy-target-field-mapping.md
§3, rows marked P) from the legacy MySQL database into staging/stg_payroll_<table>.json, in a
read-only session, then writes staging/organisations.md for the founder's keep/drop pass
(W-67 R5). Configuration: LEGACY_PAYROLL_DB_HOST, _PORT (default 3306), _NAME, _USER,
_PASSWORD. A missing variable exits 2.

Payroll sets PhysicalNamingStrategyStandardImpl, so a table is @Table(name) if present,
else the class name verbatim, and a column is @Column(name) else the Java field name.
Entities are under legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/.
"""
from __future__ import annotations

import sys
from collections import Counter
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from common import staging  # noqa: E402
from common.legacy import truthy  # noqa: E402
from extractors.common_extract import run as _run  # noqa: E402

SOURCE = "payroll"

# Every carried Payroll table plus the element-collection / join tables of carried entities.
TABLES_TO_EXTRACT: tuple[str, ...] = (
    # §3.1 Tenant and organisation
    "organization",                          # organization/Organization.java
    "incomeTaxDetails",                      # organization/IncomeTaxDetails.java
    "department",                            # organization/Department.java
    "designation",                           # organization/Designation.java
    "workLocations",                         # organization/WorkLocation.java
    "paySchedule",                           # PaySchedule.java
    "payScheduleWorkingDays",                # PaySchedule.java @CollectionTable
    # §3.2 Users and access
    "companyUser",                           # CompanyUser.java
    "organizationUserMapping",               # OrganizationUserMapping.java
    "organizationUserRoleMapping",           # OrganizationUserRoleMapping.java
    "organizationRole",                      # organization/OrganizationRole.java (role names for role_map.json)
    "userInvitations",                       # organization/UserInvitation.java
    "employeeInvitation",                    # employee/EmployeeInvitation.java
    # §3.3 Employee master
    "employee",                              # employee/BasicDetails.java
    "employee_tags",                         # employee/BasicDetails.java @CollectionTable
    "employee_personal_detail",              # employee/EmployeePersonalDetail.java
    "employee_bank_detail",                  # employee/EmployeeBankDetail.java
    # §3.4 Salary components and structure
    "earnings",                              # salarycomponents/Earning.java
    "deductions",                            # salarycomponents/Deduction.java
    "benefits",                              # salarycomponents/Benefit.java
    "reimbursements",                        # salarycomponents/Reimbursement.java
    "FBP",                                   # claimsanddeclarations/FBP.java (no @Table)
    "ctc_structure",                         # employee/CtcStructure.java
    "employee_earnings",                     # employee/EmployeeEarning.java
    "employee_benefits",                     # employee/EmployeeBenefit.java
    "employee_reimbursements",               # employee/EmployeeReimbursement.java
    "employee_fbp_components",               # employee/FbpComponent.java
    "employee_variable_earnings",            # employee/VariableEarning.java
    "ctc_epf_components",                    # employee/CtcEpfComponent.java
    "ctc_esi_components",                    # employee/CtcEsiComponent.java
    "one_time_payout",                       # payRun/oneTimePayout/OneTimePayout.java
    # §3.5 Statutory settings
    "epf",                                   # statutorycomponents/Epf.java
    "esi",                                   # statutorycomponents/Esi.java
    "professionalTax",                       # statutorycomponents/ProfessionalTax.java
    "slabRateConfiguration",                 # statutorycomponents/SlabRateConfiguration.java
    "slabDetail",                            # statutorycomponents/SlabDetail.java
    "slabDeductionMonths",                   # statutorycomponents/SlabDetail.java @CollectionTable
    "org_pt_override",                       # statutorycomponents/OrgPTOverride.java
    # §3.6 Pay runs and history (and claims)
    "payruns",                               # payruns/PayRun.java
    "employee_payruns",                      # payruns/EmployeePayRun.java
    "off_cycle_pay_run",                     # payRun/offCyclePayrun/OffCyclePayRun.java
    "off_cycle_payrun_employee",             # payRun/offCyclePayrun/OffCyclePayrunEmployee.java
    "off_cycle_payrun_employee_earnings",    # payRun/offCyclePayrun/OffCyclePayrunEmployeeEarning.java
    "off_cycle_payrun_employee_deductions",  # payRun/offCyclePayrun/OffCyclePayrunEmployeeDeduction.java
    "employee_reimbursement_request",        # employeereimbursement/EmployeeReimbursementRequest.java
    "employee_deduction",                    # SalaryDeduction.java
    # §3.7 Tax declarations and proofs
    "IncomeTaxDeclaration",                  # claimsanddeclarations/IncomeTaxDeclaration.java (no @Table)
    "ProofOfInvestment",                     # claimsanddeclarations/ProofOfInvestment.java (no @Table)
    "employee_investment_declaration",       # EmployeeITDeclaration/EmployeeInvestmentDeclaration.java
    "employee_inv_house_rent",               # EmployeeITDeclaration/EmployeeInvHouseRent.java
    "employee_inv_home_loan",                # EmployeeITDeclaration/EmployeeInvHomeLoan.java
    "employee_inv_let_out_property",         # EmployeeITDeclaration/EmployeeInvLetOutProperty.java
    "employee_inv_let_out_property_detail",  # EmployeeITDeclaration/EmployeeInvLetOutPropertyDetail.java
    "employee_inv_other_income",             # EmployeeITDeclaration/EmployeeInvOtherIncome.java
    "employee_inv_pre_tax_deduction",        # EmployeeITDeclaration/EmployeeInvPreTaxDeduction.java
    "employee_inv_prev_employment",          # EmployeeITDeclaration/EmployeeInvPrevEmployment.java
    "employee_inv_section6a",                # EmployeeITDeclaration/EmployeeInvSection6A.java
    "employee_inv_tax_summary",              # EmployeeITDeclaration/EmployeeInvTaxSummary.java
    "employee_tds",                          # employeeTDS/EmployeeTds.java
    "employee_proof_of_investment",          # EmployeeITDeclaration/poi/EmployeeProofOfInvestment.java
    "employee_poi_item",                     # EmployeeITDeclaration/poi/EmployeePOIItem.java
    "employee_poi_item_comment",             # EmployeeITDeclaration/poi/EmployeePOIItemComment.java
    "employee_poi_document",                 # EmployeeITDeclaration/poi/EmployeePOIDocument.java
    "employee_poi_property_detail",          # EmployeeITDeclaration/poi/EmployeePOIPropertyDetail.java
    # §3.8 Leave, holidays, attendance
    "leave_type",                            # leaveAndAttedance/LeaveType.java
    "leave_type_genders",                    # leaveAndAttedance/LeaveType.java @CollectionTable
    "leave_type_departments",                # leaveAndAttedance/LeaveType.java @JoinTable
    "leave_type_designations",               # leaveAndAttedance/LeaveType.java @JoinTable
    "leave_type_work_locations",             # leaveAndAttedance/LeaveType.java @JoinTable
    "employee_leave_allocation",             # leave/EmployeeLeaveAllocation.java
    "employee_leave_balance_consumption",    # leave/EmployeeLeaveBalanceConsumption.java
    "holidays",                              # leaveAndAttedance/holiday/Holiday.java
    "holiday_locations",                     # leaveAndAttedance/holiday/Holiday.java @CollectionTable
    "attendance_preferences",                # leaveAndAttedance/attendance/AttendancePreference.java
)

# §3.7 superseded proof table: counted in the summary, no file written.
COUNT_ONLY: tuple[str, ...] = (
    "employee_investment_proof",             # taxCalculator/EmployeeInvestmentProof.java
)

# Secrets never reach staging: login passwords and legacy invitation tokens are dropped
# (mapping §3.2) and load_users issues new tokens.
EXCLUDED_COLUMNS: dict[str, tuple[str, ...]] = {
    "companyUser": ("password",),
    "userInvitations": ("acceptance_token",),
    "employeeInvitation": ("acceptance_token",),
}


def _cell(value) -> str:
    text = "" if value is None else str(value)
    return text.replace("|", "\\|").replace("\r", " ").replace("\n", " ")


def write_organisations() -> Path:
    """staging/organisations.md: one row per legacy organization (W-67 R5). Counts use the
    organizationId join column on BasicDetails.java and PayRun.java (-> organization.id)."""
    orgs = staging.load("stg_payroll_organization.json")
    employees = Counter(str(r.get("organizationId")) for r in staging.load("stg_payroll_employee.json"))
    payruns = Counter(str(r.get("organizationId")) for r in staging.load("stg_payroll_payruns.json"))
    lines = [
        "# Legacy Payroll organisations",
        "",
        "Mark each one keep or drop in `config/org_mapping.json` (keyed by legacy id). "
        "An organisation missing from the map is skipped by every loader (W-67 R5).",
        "",
        "| Name | Legacy id | Created | Email | Active | Deleted | Employees | Pay runs |",
        "|---|---|---|---|---|---|---|---|",
    ]

    def _id(r):
        try:
            return int(r.get("id"))
        except (TypeError, ValueError):
            return 0

    for r in sorted(orgs, key=_id):
        oid = str(r.get("id"))
        lines.append("| " + " | ".join([
            _cell(r.get("organizationName")),
            _cell(oid),
            _cell(r.get("createdDate")),
            _cell(r.get("email")),
            "yes" if truthy(r.get("isOrgActive"), False) else "no",
            "yes" if truthy(r.get("isDeleted"), False) else "no",
            str(employees.get(oid, 0)),
            str(payruns.get(oid, 0)),
        ]) + " |")
    lines.append("")
    lines.append(f"{len(orgs)} organisations.")
    path = staging.stg_path("organisations.md")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def run() -> dict:
    summary = _run(SOURCE, "PAYROLL", TABLES_TO_EXTRACT, COUNT_ONLY, EXCLUDED_COLUMNS)
    write_organisations()
    return summary


def main() -> int:
    run()
    return 0


if __name__ == "__main__":
    sys.exit(main())
