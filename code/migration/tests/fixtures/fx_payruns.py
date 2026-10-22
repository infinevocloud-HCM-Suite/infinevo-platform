"""Pay runs and pay history (3g). Invented figures only; column names are the legacy
Payroll MySQL ones (PayRun.java, EmployeePayRun.java, OffCyclePayRun*.java, OneTimePayout.java).

Founder org 148: runs 1 (April 2025, COMPLETED), 2 (May 2025, COMPLETED), 3 (June 2025,
APPROVED) with 5 lines each for employees 1001-1005; run 4 READY (no target status); run 5 a
second REGULAR April run in DRAFT (duplicate period). Customer org 201: run 6 (April 2025,
COMPLETED, dated by processingPeriod only) with lines for 1101-1104 and 1198 (never migrated).
Org 203 (not in the map): run 7. One orphan line points at run 999.
Off-cycle: run 1 (org 148, completed) with two employees; run 2 with an unknown status.
One-time payouts: 1 (earning 7), 2 (earning 99, not migrated), 3 (no employee).
"""
from . import CUSTOMER_ORG, FOUNDER_ORG, UNMAPPED_ORG


def _run(rid, org, status, period_name, start=None, end=None, pay=None, rtype="REGULAR", **kw):
    row = {"id": rid, "payrunId": f"pr-{rid}", "type": rtype, "status": status, "isPaymentDue": False,
           "paymentStatus": None, "payPeriodStartDate": start, "payPeriodEndDate": end, "payDate": pay,
           "processingPeriod": period_name, "payrollTotal": None, "statusInfo": None, "noOfEmployees": 5,
           "approvalType": None, "approvalDetails": None, "approvedDate": None, "compensationName": None,
           "totalNetPay": 1.00, "totalTaxes": 0, "totalBenefits": 0, "totalDonations": 0,
           "totalDeductions": 1.00, "totalPayrollCost": 1.00, "totalBonus": 0, "totalClaimDeduction": 0,
           "totalClaimReimbursement": 0, "baseDays": 30.0, "noOfSkippedEmployees": 0,
           "rejectedReason": None, "organizationId": int(org)}
    row.update(kw)
    return row


def _line(lid, run, emp, gross, ded, taxes, net, **kw):
    row = {"id": lid, "employeeId": f"pay-uuid-{emp}", "employeeNumber": None, "employeeName": None,
           "fullName": None, "paymentStatus": "yet_to_pay", "paymentMode": "BANK_TRANSFER",
           "totalEarnings": gross, "totalDeductions": ded, "totalTaxes": taxes, "totalBenefits": 0.0,
           "totalReimbursements": 0.0, "netPay": net, "monthlySalary": gross, "paidDays": 30.0,
           "employeeStatus": "ACTIVE", "bonusEarningExistForEmployee": False, "bonus": None,
           "taxOverridden": False, "employeeHavingHoldSalary": False, "totalNoOfLeaves": 0.0, "LOP": None,
           "payrunId": f"pr-{run}", "payrun_ref_id": run, "employee_ref_id": emp, "monthlyTds": taxes,
           "claim_deduction": None, "claim_reimbursement": None, "claim_deduction_status": None,
           "claim_reimbursement_status": None}
    row.update(kw)
    return row


def _payruns():
    runs = [
        _run(1, FOUNDER_ORG, "COMPLETED", "April 2025", "2025-04-01", "2025-04-30", "2025-04-30",
             approvedDate="2025-04-28"),
        _run(2, FOUNDER_ORG, "COMPLETED", "May 2025", "2025-05-01", "2025-05-31", "2025-05-31"),
        _run(3, FOUNDER_ORG, "APPROVED", "June 2025", "2025-06-01", "2025-06-30", "2025-06-30",
             approvedDate="2025-06-27"),
        _run(4, FOUNDER_ORG, "READY", "July 2025", "2025-07-01", "2025-07-31", "2025-07-31"),
        _run(5, FOUNDER_ORG, "DRAFT", "April 2025", "2025-04-01", "2025-04-30", "2025-04-30"),
        _run(6, CUSTOMER_ORG, "COMPLETED", "April 2025", None, None, "2025-05-07", noOfEmployees=4),
        _run(7, UNMAPPED_ORG, "COMPLETED", "April 2025", "2025-04-01", "2025-04-30", "2025-04-30"),
    ]
    lines, lid = [], 100
    for run in (1, 2, 3):
        for k, emp in enumerate(range(1001, 1006)):
            lid += 1
            gross = 50000.55 + 1000 * k
            # net = gross - deductions - taxes, to the paisa
            lines.append(_line(lid, run, emp, gross, 2000.20, 3000.10, round(gross - 5000.30, 2)))
    # run 1, employee 1002: LOP and a bonus, claims; employee 1003: negative bonus (bad line)
    lines[1].update({"LOP": 1666.68, "totalNoOfLeaves": 1.0, "paidDays": 29.0, "bonus": 1500.00,
                     "claim_deduction": 250.25, "claim_reimbursement": 400.40})
    lines[2].update({"bonus": -10.0})
    # run 2, employee 1005: net above gross - tds (legacy added a bonus to net only)
    lines[9].update({"netPay": 60000.00})
    lines.append(_line(201, 4, 1001, 100.0, 0.0, 0.0, 100.0))       # run 4 not loaded
    lines.append(_line(202, 5, 1001, 100.0, 0.0, 0.0, 100.0))       # run 5 duplicate period
    lines.append(_line(203, 999, 1001, 100.0, 0.0, 0.0, 100.0))     # orphan
    for k, emp in enumerate((1101, 1102, 1103, 1104)):
        lines.append(_line(301 + k, 6, emp, 30000.10, 1000.05, 500.05, 28500.00))
    lines.append(_line(305, 6, 1198, 100.0, 0.0, 0.0, 100.0))      # employee never migrated (NO_DOJ)
    lines.append(_line(401, 7, 1101, 100.0, 0.0, 0.0, 100.0))      # org not migrated
    return runs, lines


def _off_cycle():
    runs = [
        {"id": 1, "payrollRunId": "oc-1", "payDate": "2025-05-15", "status": "completed",
         "statusFormatted": "Completed", "type": "off_cycle", "notes": "Festival advance",
         "organizationId": int(FOUNDER_ORG), "createdAt": "2025-05-10T11:00:00"},
        {"id": 2, "payrollRunId": "oc-2", "payDate": "2025-06-15", "status": "on_hold",
         "statusFormatted": "On hold", "type": "off_cycle", "notes": None,
         "organizationId": int(FOUNDER_ORG), "createdAt": "2025-06-10T11:00:00"},
    ]
    emps = [
        {"id": 11, "payroll_run_id": 1, "employee_id": 1001, "organization_id": int(FOUNDER_ORG),
         "lopAdjustmentDetails": None, "taxes": None, "createdAt": "2025-05-10T11:00:00",
         "updatedAt": "2025-05-10T11:00:00"},
        {"id": 12, "payroll_run_id": 1, "employee_id": 1002, "organization_id": int(FOUNDER_ORG),
         "lopAdjustmentDetails": None, "taxes": None, "createdAt": "2025-05-10T11:00:00",
         "updatedAt": "2025-05-10T11:00:00"},
    ]
    earnings = [
        {"id": 21, "off_cycle_employee_id": 11, "earning_id": 7, "amount": 1000.50, "days": None},
        {"id": 22, "off_cycle_employee_id": 11, "earning_id": 7, "amount": 499.50, "days": None},
        {"id": 23, "off_cycle_employee_id": 12, "earning_id": 7, "amount": 2000.00, "days": None},
    ]
    deductions = [{"id": 31, "off_cycle_employee_id": 11, "deduction_id": 3, "amount": 100.25}]
    return runs, emps, earnings, deductions


def _payouts():
    base = {"organizationId": int(FOUNDER_ORG), "taxes": None, "createdAt": "2025-06-01T09:00:00", "days": None}
    return [
        {"id": 1, "earning_id": 5, "employee_id": 1001, "earningAmount": 5000.75, "payDate": "2025-06-10", **base},
        {"id": 2, "earning_id": 99, "employee_id": 1002, "earningAmount": 100.00, "payDate": "2025-06-10", **base},
        {"id": 3, "earning_id": 7, "employee_id": None, "earningAmount": 100.00, "payDate": "2025-06-10", **base},
    ]


def rows():
    runs, lines = _payruns()
    oc_runs, oc_emps, oc_earn, oc_ded = _off_cycle()
    return {
        "stg_payroll_payruns.json": runs,
        "stg_payroll_employee_payruns.json": lines,
        "stg_payroll_off_cycle_pay_run.json": oc_runs,
        "stg_payroll_off_cycle_payrun_employee.json": oc_emps,
        "stg_payroll_off_cycle_payrun_employee_earnings.json": oc_earn,
        "stg_payroll_off_cycle_payrun_employee_deductions.json": oc_ded,
        "stg_payroll_one_time_payout.json": _payouts(),
    }
