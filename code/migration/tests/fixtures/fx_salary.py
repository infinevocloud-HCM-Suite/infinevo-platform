"""Salary components, salary structures (3e) and statutory settings (3f). Invented values only.

Column names are the legacy Payroll MySQL ones (PhysicalNamingStrategyStandardImpl: the Java field
name, or @Column(name) / @JoinColumn(name) where one is given).

3e, founder org 148: earnings Basic, HRA, Conveyance, Fixed Allowance (component map -> FIXED), Bonus
(variable, yearly), Special Allowance (child of Fixed, inactive), Old Allowance (deleted), and one with
no earning type (quarantined). Reimbursement 'Fuel' lands on the FUEL row the tenant trigger seeded.
CTC 501 (employee 1001) balances: 25000 + 12500 + 1600 + 9100 + employer EPF 1800 = 50000.
503 (1002) balances; 504 (1003) does not (CTC_SUM_MISMATCH); 505 (1004) uses the quarantined earning;
507 (1005) carries an unknown ESI code; 508/509 (1006) are two active structures, 509 is newer.
Customer org 201: CTC 601 (1101) with employer ESI; 506 points at employee 1198, never migrated.

3f: org 148 EPF/ESI enabled; PT Maharashtra equal to reference.pt_slab (not carried), Karnataka
differs (override, active rate configuration), Tamil Nadu override from org_pt_override JSON.
Org 201: EPF with an unmappable deduction cycle, ESI with an unreadable rate, PT in an unknown state.
"""
import json

from .fx_org import org_code
from . import CUSTOMER_ORG, FOUNDER_ORG, UNMAPPED_ORG

F, C, U = int(FOUNDER_ORG), int(CUSTOMER_ORG), int(UNMAPPED_ORG)


def _earning(i, org, name, etype, pct=False, value=None, amount=None, **kw):
    row = {"id": i, "earningId": f"ern-{i}", "organizationId": org, "earningName": name, "earningType": etype,
           "earningTypeFormatted": etype, "displayName": name, "amount": amount, "amountFormatted": None,
           "value": value, "valueFormatted": None, "valueType": None, "maxLimit": None,
           "isAmountInPercentage": pct, "isProRata": True, "isIncludedInCtc": True,
           "isIncludedInSalaryStructure": True, "status": "active", "statusFormatted": "Active",
           "isFbpComponent": False, "isVariable": False, "canIncludeAsFbp": False, "componentType": None,
           "isOneTimeComponent": False, "isUserConfigurable": True, "isAssociatedWithEmployee": True,
           "canEditProrataConfiguration": True, "canChangeScheduleEarningConfiguration": True,
           "canChangePayType": True, "gratuityExemptionRuleDetails": None, "isIncludedInEpf": False,
           "epfInclusionType": None, "epfInclusionTypeFormatted": None, "canChangeEpfConfiguration": True,
           "canChangeEsiConfiguration": True, "isIncludedInEsi": True, "isTaxable": True, "showInPayslip": True,
           "canCalculateTaxWithoutProjection": False, "isOptIn": False, "parentEarningId": None,
           "parentEarningName": None, "isFormulaBasedCalculationSupported": False, "formulaBasedOn": None,
           "isScheduledEarning": False, "canChangeDefaultPercentageVariables": False,
           "earningFrequency": "monthly", "createdTime": "2024-03-01T09:00:00", "isDeleted": False}
    row.update(kw)
    return row


def _ctc(i, org, emp, annual, monthly, eff, active=True, deleted=False):
    return {"id": i, "annualCtc": annual, "organizationId": org, "employee_id": emp, "monthlySalary": monthly,
            "effective_date": eff, "is_active": active, "created_at": "2024-03-20T11:00:00", "updated_at": None,
            "payment_month": eff[:7], "is_deleted": deleted, "deleted_at": None,
            "revision_status": "APPROVED", "is_revision": False, "change_in_percent": None,
            "applied_in_payrun": True}


def _line(i, ctc, org, earning, amount, basis="FLAT", pct=None, freq="monthly", variable=False, enabled=True):
    return {"id": i, "earning_id": earning, "enabled": enabled, "amount": amount, "amountInPercentage": pct,
            "editable": True, "isVariable": variable, "ctc_structure_id": ctc, "organizationId": org,
            "overrideAmount": amount if basis == "FLAT" else None, "earningFrequency": freq,
            "calculation_basis": basis}


def _stat(i, ctc, org, code, pct, monthly, annual):
    return {"id": i, "ctc_structure_id": ctc, "organizationId": org, "componentCode": code,
            "componentLabel": code.replace("_", " ").title(), "percentage": pct, "calculationType": "PERCENTAGE",
            "monthlyAmount": monthly, "annualAmount": annual}


def _salary():
    earnings = [
        _earning(1, F, "Basic", "Basic", pct=True, value=50, isIncludedInEpf=True, epfInclusionType="Always"),
        _earning(2, F, "House Rent Allowance", "House Rent Allowance", pct=True, value=50),
        _earning(3, F, "Conveyance Allowance", "Conveyance Allowance", amount=1600, maxLimit="1600.505"),
        _earning(4, F, "Fixed Allowance", "Custom Allowance", value=0),
        _earning(5, F, "Bonus", "Bonus", isVariable=True, earningFrequency="yearly", isIncludedInEsi=False),
        _earning(6, F, "Special Allowance", "Custom Allowance", parentEarningId="ern-4", status="inactive",
                 isIncludedInEpf=True, epfInclusionType="Conditional"),
        _earning(7, F, "Mystery Pay", None),
        _earning(8, F, "Old Allowance", "Custom Allowance", isDeleted=True, createdTime=None),
        _earning(11, C, "Basic", "Basic", pct=True, value=40),
        _earning(12, C, "Fixed Allowance", "Custom Allowance", value=0),
        _earning(20, U, "Basic", "Basic", pct=True, value=50),
    ]
    deductions = [
        {"id": 1, "deductionId": "ded-1", "deductionName": "Canteen", "deductionType": "custom",
         "deductionTypeFormatted": "Custom Deduction", "status": "active", "statusFormatted": "Active",
         "isRecurring": True, "createdTime": "2024-03-02T09:00:00", "isUserConfigurable": True,
         "isAssociatedWithEmployee": False, "perquisiteInterestRate": None, "emiInterestRate": None,
         "emiType": None, "organizationId": F, "isDeleted": False},
        {"id": 2, "deductionId": "ded-2", "deductionName": "Loan Recovery", "deductionType": "Pre-Tax",
         "deductionTypeFormatted": None, "status": "inactive", "statusFormatted": None, "isRecurring": True,
         "createdTime": None, "isUserConfigurable": True, "isAssociatedWithEmployee": False,
         "perquisiteInterestRate": "4.5", "emiInterestRate": "8.5%", "emiType": "REDUCING",
         "organizationId": F, "isDeleted": False},
        {"id": 3, "deductionId": "ded-3", "deductionName": "Notice Pay", "deductionType": "custom",
         "deductionTypeFormatted": None, "status": "active", "statusFormatted": None, "isRecurring": False,
         "createdTime": None, "isUserConfigurable": True, "isAssociatedWithEmployee": False,
         "perquisiteInterestRate": None, "emiInterestRate": None, "emiType": None,
         "organizationId": C, "isDeleted": False},
    ]
    benefits = [
        {"id": 1, "benefitId": "ben-1", "benefitName": "Health Insurance", "benefitPlan": "Health",
         "benefitPlanNameFormatted": None, "benefitCategory": "Medical", "isPreTax": True, "employeeCount": 3,
         "status": "active", "isOneTime": False, "isUserConfigurable": True, "isProRata": False,
         "isSuperannuationBenefit": False, "isIncludedInCtc": False, "isIncludedInSalaryStructure": True,
         "taxExemptionSubType": None, "taxExemptionSubTypeFormatted": None, "taxExemptSection": "80D",
         "canAllowEmployerContribution": True, "canAllowEmployeeContribution": False, "isDeleted": False,
         "organizationId": F},
    ]
    reimbursements = [
        {"id": 1, "reimbursementId": "rmb-1", "reimbursementName": "Fuel", "reimbursementType": "fuel",
         "reimbursementTypeFormatted": None, "displayName": "Fuel Reimbursement", "maxLimit": "3000.50",
         "isIncludedInCtc": False, "isIncludedInSalaryStructure": True, "status": "active",
         "statusFormatted": None, "isFbpComponent": False, "isOptIn": False, "carryForwardOption": "carry_forward",
         "isAssociatedWithEmployee": True, "isDeleted": False, "organizationId": F},
        {"id": 2, "reimbursementId": "rmb-2", "reimbursementName": "Telephone", "reimbursementType": "telephone",
         "reimbursementTypeFormatted": None, "displayName": None, "maxLimit": "12000",
         "isIncludedInCtc": True, "isIncludedInSalaryStructure": True, "status": "active",
         "statusFormatted": None, "isFbpComponent": True, "isOptIn": True, "carryForwardOption": "monthly_encash",
         "isAssociatedWithEmployee": True, "isDeleted": False, "organizationId": F},
        {"id": 3, "reimbursementId": "rmb-3", "reimbursementName": "Gym", "reimbursementType": None,
         "reimbursementTypeFormatted": None, "displayName": None, "maxLimit": None,
         "isIncludedInCtc": False, "isIncludedInSalaryStructure": False, "status": "active",
         "statusFormatted": None, "isFbpComponent": False, "isOptIn": False, "carryForwardOption": None,
         "isAssociatedWithEmployee": False, "isDeleted": False, "organizationId": F},
    ]
    fbp = [
        {"id": 1, "lastDateForFbpDeclaration": "2025-01-31", "isFbpDeclarationLocked": False,
         "sendMailOnFbpDeclarationRelease": True, "sendMailOnFbpDeclarationLock": False,
         "isPayscheduleConfigured": True, "isFbpAssociatedWithEmployee": True, "isFbpEnabled": True,
         "isAnyReminderBeforeLockdateEnabled": True, "organizationId": F},
        {"id": 2, "lastDateForFbpDeclaration": None, "isFbpDeclarationLocked": True,
         "sendMailOnFbpDeclarationRelease": False, "sendMailOnFbpDeclarationLock": False,
         "isPayscheduleConfigured": False, "isFbpAssociatedWithEmployee": False, "isFbpEnabled": False,
         "isAnyReminderBeforeLockdateEnabled": False, "organizationId": C},
    ]
    ctc = [
        _ctc(500, F, 1001, 540000.0, 45000.00, "2023-04-01", active=False),   # old revision, dropped
        _ctc(501, F, 1001, 600000.0, 50000.00, "2024-04-01"),
        _ctc(502, F, 1002, 300000.0, 25000.00, "2024-01-01", deleted=True),  # deleted, dropped
        _ctc(503, F, 1002, 360000.0, 30000.00, "2024-04-01"),
        _ctc(504, F, 1003, 480000.0, 40000.00, "2024-04-01"),                # lines sum to 30000
        _ctc(505, F, 1004, 240000.0, 20000.00, "2024-04-01"),                # uses quarantined earning 7
        _ctc(507, F, 1005, 240000.0, 20000.00, "2024-04-01"),                # unknown ESI code
        _ctc(508, F, 1006, 228000.0, 19000.00, "2024-04-01"),                # superseded by 509
        _ctc(509, F, 1006, 240000.0, 20000.00, "2024-10-01"),
        _ctc(506, C, 1198, 240000.0, 20000.00, "2024-04-01"),                # employee never migrated
        _ctc(601, C, 1101, 300000.0, 25000.00, "2024-04-01"),
        _ctc(700, U, 1101, 300000.0, 25000.00, "2024-04-01"),                # org not migrated
    ]
    emp_earn = [
        _line(9001, 501, F, 1, 25000.0, "PERCENTAGE", 50.0),
        _line(9002, 501, F, 2, 12500.0, "PERCENTAGE", 50.0),
        _line(9003, 501, F, 3, 1600.0, "PERCENTAGE", 0.0),
        _line(9004, 501, F, 4, 9100.0),
        _line(9005, 501, F, 5, 60000.0, freq="yearly", variable=True),
        _line(9011, 503, F, 1, 15000.0, "PERCENTAGE", 50.0),
        _line(9012, 503, F, 2, 7500.0, "PERCENTAGE", 50.0),
        _line(9013, 503, F, 3, 1600.0),
        _line(9014, 503, F, 4, 5899.995),
        _line(9021, 504, F, 1, 20000.0, "PERCENTAGE", 50.0),
        _line(9022, 504, F, 2, 10000.0, "PERCENTAGE", 50.0),
        _line(9031, 505, F, 7, 20000.0),
        _line(9041, 507, F, 4, 20000.0),
        _line(9051, 508, F, 4, 19000.0),
        _line(9061, 509, F, 1, 10000.0, "PERCENTAGE", 50.0),
        _line(9062, 509, F, 4, 10000.0, "DIRECT"),
        _line(9071, 506, C, 12, 20000.0),
        _line(9081, 601, C, 11, 10000.0, "PERCENTAGE", 40.0),
        _line(9082, 601, C, 12, 14187.0),
    ]
    emp_ben = [{"id": 9101, "benefit_id": 1, "enabled": True, "amount": 500.0, "amount_in_percentage": None,
                "ctc_structure_id": 501, "organizationId": F}]
    emp_reimb = [{"id": 9201, "reimbursement_id": 1, "enabled": True, "amount": 2000.0,
                  "carryForwardOption": "carry_forward", "ctc_structure_id": 501, "organizationId": F}]
    emp_fbp = [
        {"id": 9401, "componentCode": "rmb-2", "enabled": True, "amount": 12000.0, "amount_in_percentage": None,
         "max_limit": 12000.0, "ctc_structure_id": 501, "organizationId": F},
        {"id": 9402, "componentCode": "CONVEYANCE_ALLOWANCE", "enabled": False, "amount": 0.0,
         "amount_in_percentage": None, "max_limit": None, "ctc_structure_id": 501, "organizationId": F},
    ]
    emp_var = [{"id": 9501, "variableCode": "BONUS", "enabled": True, "amount": 4000.0, "amount_in_percentage": None,
                "editable": True, "ctc_structure_id": 501, "organizationId": F}]
    epf = [_stat(9301, 501, F, "EPF_EMPLOYER", "12", 1800.00, 21600.00),
           _stat(9302, 501, F, "EDLI_EMPLOYER", "0.5", 75.00, 900.00)]
    esi = [_stat(9601, 601, C, "ESI_EMPLOYER", "3.25", 813.00, 9756.00),
           _stat(9602, 507, F, "ESI_SOMETHING", "3.25", 0.00, 0.00)]
    return {
        "stg_payroll_earnings.json": earnings, "stg_payroll_deductions.json": deductions,
        "stg_payroll_benefits.json": benefits, "stg_payroll_reimbursements.json": reimbursements,
        "stg_payroll_FBP.json": fbp, "stg_payroll_ctc_structure.json": ctc,
        "stg_payroll_employee_earnings.json": emp_earn, "stg_payroll_employee_benefits.json": emp_ben,
        "stg_payroll_employee_reimbursements.json": emp_reimb, "stg_payroll_employee_fbp_components.json": emp_fbp,
        "stg_payroll_employee_variable_earnings.json": emp_var, "stg_payroll_ctc_epf_components.json": epf,
        "stg_payroll_ctc_esi_components.json": esi,
    }


_ALL_MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September",
               "October", "November", "December"]
_NOT_FEB = [m for m in _ALL_MONTHS if m != "February"]


def _statutory():
    epf = [
        {"id": 1, "isAdminChargesIncludedCtc": False, "isEdliIncludedCtc": True, "considerEarnedSalaryForEpf": True,
         "registrationNumber": "MHBAN0012345000", "epfAdminChargesEmployerContribution": "0.5",
         "isEmployerContributionIncludedCtc": True, "isEmployeeRestrictedBasicEnabled": False,
         "isEmployerContributionIncludedSalaryStructure": True, "isEligibleForAbryScheme": False,
         "edliEmployerContribution": "0.50%", "epfEmployeeContribution": "12_actual",
         "canEnableEdliPfAdminChargesInSalaryStructure": True, "canProRateRestrictedBasic": True,
         "isAssociatedWithEmployee": True, "epsSeniorCategoryAge": 58, "isActive": True,
         "canOverrideRestrictedBasic": False, "epsEmployeeContribution": None,
         "epfAdminChargesEmployeeContribution": None, "isEdliIncludedSalaryStructure": True,
         "isAdminChargesIncludedSalaryStructure": True, "epsEmployerContribution": "8.33",
         "deductionCycleFormatted": "Monthly", "isSubsidyApplicableForBothContributions": False,
         "registrationDate": "2023-01-15", "registrationDateFormatted": None,
         "epsEmployerContributionForSeniorcategory": None, "name": "EPF", "isEmployerRestrictedBasicEnabled": None,
         "epfEmployerContribution": "restrict_15000", "canEnableEdliPfAdminChargesInCtc": True,
         "deductionCycle": "Monthly", "epfEmployerContributionForSeniorcategory": None,
         "edliEmployeeContribution": None, "organizationId": F},
        {"id": 2, "isActive": False, "deductionCycle": "weekly", "epfEmployeeContribution": "12_actual",
         "epfEmployerContribution": "12_actual", "organizationId": C},
    ]
    esi = [
        {"id": 1, "isActive": True, "employeeContribution": "0.75", "employerContribution": "3.25%",
         "registrationNumber": "31000123450001001", "registrationDate": "2023-02-01",
         "canEnableEmployerEsiInCtc": None, "isIncludedInSalaryStructure": True, "deductionCycle": "monthly",
         "deductionCycleFormatted": None, "registrationDateFormatted": None, "isIncludedInCtc": True,
         "name": "ESI", "isAssociatedWithEmployee": True, "organizationId": F},
        {"id": 2, "isActive": False, "employeeContribution": "n/a", "employerContribution": None,
         "registrationNumber": None, "registrationDate": None, "canEnableEmployerEsiInCtc": False,
         "isIncludedInSalaryStructure": False, "deductionCycle": None, "isIncludedInCtc": False,
         "organizationId": C},
        {"id": 3, "isActive": True, "employeeContribution": "0.75", "employerContribution": "3.25",
         "deductionCycle": "monthly", "organizationId": U},
    ]

    def pt(i, org, state, code, reg, eff):
        return {"id": i, "taxId": f"pt-{i}", "state": state, "stateCode": code, "locationName": state,
                "registrationNumber": reg, "registrationDate": None, "taxConfigurationFrequency": "monthly",
                "grossSalaryConfigurationFrequency": "monthly", "deductionFrequency": "monthly",
                "effectiveFrom": eff, "isProfessionalTaxSupported": True, "isReadOnly": False, "organizationId": org}

    pts = [pt(1, F, "Maharashtra", "MH", "PTMH001", "2024-04-01"),
           pt(2, F, "Karnataka", None, "PTKA009", "2023-04-01"),
           pt(3, C, "Atlantis", "AT", None, "2024-04-01")]
    configs = [
        {"id": 1, "taxRateSettingsId": "trs-1", "taxConfigurationFrequency": "monthly",
         "grossSalaryConfigurationFrequency": "monthly", "deductionFrequency": "monthly",
         "effectiveFrom": "2024-04-01", "effectiveTo": None, "isActive": True, "isEditable": True,
         "professionalTaxId": 2},
        {"id": 2, "taxRateSettingsId": "trs-2", "taxConfigurationFrequency": "monthly",
         "grossSalaryConfigurationFrequency": "monthly", "deductionFrequency": "monthly",
         "effectiveFrom": "2023-04-01", "effectiveTo": "2024-03-31", "isActive": False, "isEditable": False,
         "professionalTaxId": 2},
    ]

    def slab(i, pt_id, cfg, start, end, pay, female=False):
        return {"id": i, "startAmount": start, "endAmount": end, "payAmount": pay, "isFemaleExempted": female,
                "professionalTaxId": pt_id, "slabRateConfigId": cfg, "defaultFromMaster": cfg is None}

    slabs = [
        # Maharashtra, exactly reference.pt_slab MH (V064)
        slab(101, 1, None, 0.0, 7500.0, 0.0),
        slab(102, 1, None, 7500.0, 10000.0, 175.0, True),
        slab(103, 1, None, 10000.0, 25000.0, 200.0, True),
        slab(104, 1, None, 10000.0, 25000.0, 300.0, True),
        slab(105, 1, None, 25000.0, None, 200.0),
        slab(106, 1, None, 25000.0, None, 300.0),
        # Karnataka, active configuration 1 (250 instead of the reference 200)
        slab(201, 2, 1, 0.0, 24999.0, 0.0),
        slab(202, 2, 1, 24999.0, None, 250.0),
        # Karnataka, inactive configuration 2: ignored
        slab(211, 2, 2, 0.0, 15000.0, 0.0),
        slab(212, 2, 2, 15000.0, None, 200.0),
    ]
    months = ([{"slabId": 101, "deductionMonth": m} for m in _ALL_MONTHS]
              + [{"slabId": s, "deductionMonth": m} for s in (103, 105) for m in _NOT_FEB]
              + [{"slabId": s, "deductionMonth": "February"} for s in (104, 106)])
    overrides = [
        # organization_id is Organization.organizationId, the public 6-digit string.
        {"id": 1, "organization_id": org_code(F), "state": "Tamil Nadu",
         "overrideJson": json.dumps([
             {"startAmount": 0, "endAmount": 21000, "payAmount": 0, "isFemaleExempted": False,
              "deductionMonths": ["September", "March"]},
             {"startAmount": 21000, "endAmount": None, "payAmount": 1250, "isFemaleExempted": False,
              "deductionMonths": [3, 9]}]),
         "effectiveFrom": None, "changedAt": "2024-06-10T10:00:00", "registrationNumber": "PTTN777",
         "changedBy": "SYSTEM"},
        {"id": 2, "organization_id": "org-does-not-exist", "state": "Kerala", "overrideJson": "[]",
         "effectiveFrom": "2024-04-01", "changedAt": None, "registrationNumber": None, "changedBy": None},
    ]
    return {
        "stg_payroll_epf.json": epf, "stg_payroll_esi.json": esi, "stg_payroll_professionalTax.json": pts,
        "stg_payroll_slabRateConfiguration.json": configs, "stg_payroll_slabDetail.json": slabs,
        "stg_payroll_slabDeductionMonths.json": months, "stg_payroll_org_pt_override.json": overrides,
    }


def rows():
    return {**_salary(), **_statutory()}
