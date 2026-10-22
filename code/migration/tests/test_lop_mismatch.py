"""W-70 Task 2 — the loss-of-pay mismatch log (W-70 Task 3 rows 5 and 6)."""
import csv
import json

from tests.fixtures import FOUNDER_TENANT
from tests.pipeline import run_steps, BASE


def _write_lop_inputs(staging):
    hrms = [  # E001..E005 are the founder employees matched to Payroll 1001..1005
        {"id": 1, "employee_id": "E001", "year": 2024, "month": 4, "lop_days": 1.0},
        {"id": 2, "employee_id": "E002", "year": 2024, "month": 4, "lop_days": 2.0},
        {"id": 3, "employee_id": "E003", "year": 2024, "month": 4, "lop_days": 0.5},  # BUG-003 half day
        {"id": 4, "employee_id": "4", "year": 2024, "month": 5, "lop_days": 1.0},    # by HRMS employee id
        {"id": 5, "employee_id": "E005", "year": 2024, "month": 5, "lop_days": 0},
    ]
    payruns = [
        {"id": 71, "payrunId": "pr-71", "organizationId": 148, "payPeriodStartDate": "2024-04-01"},
        {"id": 72, "payrunId": "pr-72", "organizationId": 148, "payPeriodStartDate": "2024-05-01"},
    ]
    emp_payruns = [
        {"id": 1, "payrun_ref_id": 71, "payrunId": "pr-71", "employeeId": "pay-uuid-1001", "totalNoOfLeaves": 1.0, "LOP": "1000.00"},
        {"id": 2, "payrun_ref_id": 71, "payrunId": "pr-71", "employeeId": "pay-uuid-1002", "totalNoOfLeaves": 1.0, "LOP": "1200.50"},
        {"id": 3, "payrun_ref_id": 71, "payrunId": "pr-71", "employeeId": "pay-uuid-1003", "totalNoOfLeaves": 0, "LOP": "0"},
        {"id": 4, "payrun_ref_id": 72, "payrunId": "pr-72", "employeeId": "pay-uuid-1004", "totalNoOfLeaves": 1.0, "LOP": "900"},
        {"id": 5, "payrun_ref_id": 72, "payrunId": "pr-72", "employeeId": "pay-uuid-1005", "totalNoOfLeaves": 0, "LOP": "0"},
    ]
    for name, rows in (("stg_hrms_employee_monthly_lop.json", hrms), ("stg_payroll_payruns.json", payruns),
                       ("stg_payroll_employee_payruns.json", emp_payruns)):
        (staging / name).write_text(json.dumps(rows))


def test_only_mismatches_logged_and_idempotent(legacy_fixture, query):
    _write_lop_inputs(legacy_fixture)
    run_steps(BASE + ["reconciliation.lop_mismatch_generator"])
    rows = query("SELECT employee_number, pay_period, hrms_unpaid_days::text, payroll_lop_days::text, "
                 "variance_days::text, payroll_processed_deduction::text, status FROM core.lop_mismatch_log "
                 "ORDER BY employee_number")
    assert rows == [
        ("E002", "2024-04", "2.0", "1.0", "1.0", "1200.50", "PENDING_FINANCE_SIGN_OFF"),
        ("E003", "2024-04", "0.5", "0.0", "0.5", "0.00", "PENDING_FINANCE_SIGN_OFF"),
    ]
    summary = json.loads((legacy_fixture / f"lop_summary_{FOUNDER_TENANT}.json").read_text())
    assert summary == {"mismatches": 2, "matching": 3, "logged_total": 2}
    with open(legacy_fixture / f"lop_mismatch_{FOUNDER_TENANT}.csv", encoding="utf-8") as f:
        lines = list(csv.reader(f))
    assert len(lines) == 3 and "@" not in "".join(sum(lines, []))

    run_steps(["reconciliation.lop_mismatch_generator"])
    assert query("SELECT count(*) FROM core.lop_mismatch_log")[0][0] == 2


def test_finance_approval_survives_a_rerun(legacy_fixture, query):
    _write_lop_inputs(legacy_fixture)
    run_steps(BASE + ["reconciliation.lop_mismatch_generator"])
    query("UPDATE core.lop_mismatch_log SET status = 'APPROVED_BY_FINANCE' WHERE employee_number = 'E002'")
    # changed input forces a real re-run rather than a ledger skip
    p = legacy_fixture / "stg_payroll_employee_payruns.json"
    rows = json.loads(p.read_text())
    rows[1]["LOP"] = "1300.00"
    p.write_text(json.dumps(rows))
    run_steps(["reconciliation.lop_mismatch_generator"])
    assert query("SELECT status, payroll_processed_deduction::text FROM core.lop_mismatch_log "
                 "WHERE employee_number = 'E002'") == [("APPROVED_BY_FINANCE", "1300.00")]
