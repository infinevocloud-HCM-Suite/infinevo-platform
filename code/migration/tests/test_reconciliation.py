"""W-70 Task 3 — reconciliation over the whole pipeline (W-70 Task 3 rows 1-4, ledger).

The full fixture runs through every step in tests/pipeline.ORDER (load_documents needs storage
and is not in ORDER) plus the LOP log, then reconciliation.financial_reconciliation runs:
on the untouched target it must be clean; one changed or deleted target value must surface as
exactly one variance naming the employee. LOP log rows 5 and 6 are in tests/test_lop_mismatch.py.
"""
import json
import subprocess
import sys
from pathlib import Path

import pytest

from common.ids import child_id, det_id
from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT, LIVE_TENANTS
from tests.pipeline import ORDER, run_steps

MIGRATION_DIR = Path(__file__).resolve().parents[1]


@pytest.fixture
def migrated(legacy_fixture, query, monkeypatch):
    """The whole pipeline over the full fixture; returns the staging directory."""
    monkeypatch.setenv("MIGRATION_KEYCLOAK", "off")  # load_users quarantines users instead of calling Keycloak
    monkeypatch.setenv("MIGRATION_FY", "2025-26")    # the tax fixture's financial year (fx_tax.py)
    run_steps(ORDER + ["reconciliation.lop_mismatch_generator"])
    return legacy_fixture


def _reconcile(staging_dir):
    """run() and the newest result file of each tenant."""
    from reconciliation import financial_reconciliation as fr

    ok = fr.run()
    results = {}
    for tid in LIVE_TENANTS:
        files = sorted(staging_dir.glob(f"reconciliation_{tid}_*.json"))
        assert files, f"no result file for {tid}"
        results[tid] = json.loads(files[-1].read_text(encoding="utf-8"))
    return ok, results


def _real(result, reason=None):
    return [v for v in result["per_employee_variances"]
            if not v["expected"] and (reason is None or v["reason"] == reason)]


def test_clean_fixture_reconciles_to_zero(migrated):
    ok, results = _reconcile(migrated)
    for tid, r in results.items():
        assert r["tenant_id"] == tid
        assert [c for c in r["counts"] if c["delta"] != 0] == [], tid
        assert {k: r["totals"][k]["delta"] for k in ("gross", "deductions", "net")} == \
            {"gross": "0.00", "deductions": "0.00", "net": "0.00"}, tid
        assert r["totals"]["header_vs_lines"] == []
        assert _real(r) == [], tid
        assert r["clean"] is True
    assert ok is True
    # the LOP log is finance's (W-67 R4), counted here: run 1 deducted a day of employee 1002 (E002)
    # that HRMS never recorded (fx_payruns.py lines[1]); a LOP variance is not a migration variance
    assert {t: r["lop_entries"] for t, r in results.items()} == {FOUNDER_TENANT: 1, CUSTOMER_TENANT: 0}
    founder = {c["table"]: c for c in results[FOUNDER_TENANT]["counts"]}
    # the counts are real comparisons, not 0 == 0
    assert founder["core.employee"]["actual"] == 20
    assert founder["payroll.employee_payrun"]["actual"] == 17
    assert founder["core.notification"]["actual"] == 5
    # an employee the loader quarantined is an expected miss, never a variance
    customer = results[CUSTOMER_TENANT]
    assert [v["reason"] for v in customer["per_employee_variances"] if v["expected"]] == ["NO_EMPLOYEE"]
    # nothing that identifies a person beyond the employee number
    text = json.dumps(results)
    assert "@" not in text and "First" not in text and "ABCPE" not in text


def test_tds_keyed_by_organisation_code_reconciles(legacy_fixture, query, monkeypatch):
    """Prod employee_tds.organization_id holds the org code; reconciliation must place those rows
    as load_tax does, or every loaded TDS row reads as an unexpected count and TAX_MISMATCH."""
    from tests.fixtures.fx_org import org_code

    p = legacy_fixture / "stg_payroll_employee_tds.json"
    rows = json.loads(p.read_text(encoding="utf-8"))
    for r in rows:
        r["organization_id"] = org_code(r["organization_id"])
    p.write_text(json.dumps(rows), encoding="utf-8")
    monkeypatch.setenv("MIGRATION_KEYCLOAK", "off")
    monkeypatch.setenv("MIGRATION_FY", "2025-26")
    run_steps(ORDER + ["reconciliation.lop_mismatch_generator"])
    ok, results = _reconcile(legacy_fixture)
    tds = {tid: next(c for c in r["counts"] if c["table"] == "payroll.employee_tds")
           for tid, r in results.items()}
    assert tds[FOUNDER_TENANT]["actual"] > 0
    assert {tid: c["delta"] for tid, c in tds.items()} == {FOUNDER_TENANT: 0, CUSTOMER_TENANT: 0}
    assert ok is True and all(r["clean"] for r in results.values())


def test_net_pay_off_by_one_paisa(migrated, query):
    ep = det_id(FOUNDER_TENANT, "employee_payruns", 101)  # run 1 (April 2025), employee 1001 = E001
    query("UPDATE payroll.employee_payrun SET net_pay = net_pay + 0.01 WHERE id = %s", (ep,))
    ok, results = _reconcile(migrated)
    assert ok is False
    r = results[FOUNDER_TENANT]
    assert r["clean"] is False
    pay = _real(r)
    assert len(pay) == 1
    assert pay[0]["reason"] == "PAY_MISMATCH"
    assert (pay[0]["employee_number"], pay[0]["period"], pay[0]["field"]) == ("E001", "2025-04", "net_pay")
    assert (pay[0]["legacy"], pay[0]["target"]) == ("45000.25", "45000.26")
    assert r["totals"]["net"]["delta"] == "0.01"
    assert results[CUSTOMER_TENANT]["clean"] is True


def test_changed_earning_line_is_a_ctc_mismatch(migrated, query):
    line = det_id(FOUNDER_TENANT, "employee_earnings", 9004)  # CTC 501, employee 1001, Fixed Allowance
    query("UPDATE payroll.employee_earning SET monthly_amount = monthly_amount - 100 WHERE id = %s", (line,))
    ok, results = _reconcile(migrated)
    assert ok is False
    real = _real(results[FOUNDER_TENANT])
    assert [(v["reason"], v["employee_number"], v["field"]) for v in real] == \
        [("CTC_MISMATCH", "E001", "monthly_identity")]
    assert (real[0]["legacy"], real[0]["target"]) == ("50000.00", "49900.00")


def test_deleted_leave_consumption_is_a_leave_mismatch(migrated, query):
    alloc = det_id(FOUNDER_TENANT, "leave_balances.ANNUAL", 1)  # E001, 2024: 24 allotted, 3 used
    assert query("DELETE FROM core.leave_consumption WHERE id = %s", (child_id(alloc, "used"),)) == []
    ok, results = _reconcile(migrated)
    assert ok is False
    real = _real(results[FOUNDER_TENANT])
    assert [(v["reason"], v["employee_number"], v["field"]) for v in real] == \
        [("LEAVE_MISMATCH", "E001", "remaining_days")]
    assert (real[0]["legacy"], real[0]["target"]) == ("21.00", "24.00")


def test_ledger_has_one_reconcile_row_per_tenant(migrated, query):
    _reconcile(migrated)
    _reconcile(migrated)  # a second run re-reads the target (force) and keeps one row per tenant
    rows = query("SELECT tenant_id::text, status FROM core.migration_ledger WHERE step_name = 'reconcile' "
                 "ORDER BY 1")
    assert rows == sorted((t, "DONE") for t in LIVE_TENANTS)


def test_process_exit_code(migrated, query, target_db):
    """__main__ exits 0 when clean and 1 on a variance."""
    def main():
        return subprocess.run([sys.executable, "-m", "reconciliation.financial_reconciliation"],
                              cwd=MIGRATION_DIR, capture_output=True, text=True).returncode

    assert main() == 0
    query("UPDATE payroll.employee_payrun SET net_pay = net_pay + 0.01 WHERE id = %s",
          (det_id(FOUNDER_TENANT, "employee_payruns", 101),))
    assert main() == 1


def test_no_float_in_reconciliation():
    for path in (MIGRATION_DIR / "reconciliation").glob("*.py"):
        assert "float(" not in path.read_text(encoding="utf-8"), path
