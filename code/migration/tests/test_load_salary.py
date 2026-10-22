"""3e load_salary against a real migrated Postgres."""
import json
from decimal import Decimal

import pytest

from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT
from tests.pipeline import run_base_and

TABLES = ["payroll.earning", "payroll.deduction", "payroll.benefit", "payroll.reimbursement", "payroll.fbp",
          "payroll.ctc_structure", "payroll.employee_earning", "payroll.employee_benefit",
          "payroll.employee_reimbursement", "payroll.employee_fbp_component", "payroll.ctc_epf_component",
          "payroll.ctc_esi_component"]


@pytest.fixture
def component_map(tmp_path, monkeypatch):
    p = tmp_path / "component_map.json"
    p.write_text(json.dumps({"148": {"earnings": {"fixed allowance": "FIXED"}}}), encoding="utf-8")
    monkeypatch.setenv("MIGRATION_COMPONENT_MAP", str(p))
    return p


def _emp(query, legacy_payroll_id, staging):
    idmap = json.loads((staging / "id_map.json").read_text())
    return idmap["payroll.employee"][str(legacy_payroll_id)]


def _quarantine(staging):
    return json.loads((staging / "stg_quarantined_load_salary.json").read_text())


def test_component_masters(legacy_fixture, component_map, query):
    run_base_and("loaders.load_salary")
    earn = {r[0]: r[1:] for r in query(
        "SELECT code, name, earning_type, calculation_type, default_value, percentage_of, max_limit, "
        "earning_frequency, epf_inclusion_type, is_active, is_deleted, is_variable "
        "FROM payroll.earning WHERE tenant_id = %s", (FOUNDER_TENANT,))}
    assert set(earn) == {"BASIC", "HOUSE_RENT_ALLOWANCE", "CONVEYANCE_ALLOWANCE", "FIXED", "BONUS",
                         "SPECIAL_ALLOWANCE", "OLD_ALLOWANCE"}
    assert earn["BASIC"][2:5] == ("PERCENTAGE", Decimal("50.0000"), "CTC")
    assert earn["BASIC"][7] == "ALWAYS"
    # flat master: default from `amount`, max limit (legacy text) to the paisa
    assert earn["CONVEYANCE_ALLOWANCE"][2:6] == ("FLAT", Decimal("1600.0000"), None, Decimal("1600.5100"))
    assert earn["BONUS"][6] == "YEARLY" and earn["BONUS"][10] is True
    assert earn["SPECIAL_ALLOWANCE"][7] == "WHEN_PF_WAGE_BELOW_15000" and earn["SPECIAL_ALLOWANCE"][8] is False
    assert earn["OLD_ALLOWANCE"][9] is True
    assert earn["FIXED"][0] == "Fixed Allowance"  # component_map named the code
    parent = query("SELECT p.code FROM payroll.earning c JOIN payroll.earning p ON p.id = c.parent_earning_id "
                   "WHERE c.tenant_id = %s AND c.code = 'SPECIAL_ALLOWANCE'", (FOUNDER_TENANT,))
    assert parent == [("FIXED",)]
    # the customer's map has no entry, so its Fixed Allowance keeps the slug
    assert {r[0] for r in query("SELECT code FROM payroll.earning WHERE tenant_id = %s", (CUSTOMER_TENANT,))} == \
        {"BASIC", "FIXED_ALLOWANCE"}

    ded = {r[0]: r[1:] for r in query("SELECT code, deduction_type, is_pre_tax, emi_interest_rate, is_active "
                                      "FROM payroll.deduction WHERE tenant_id = %s", (FOUNDER_TENANT,))}
    assert ded["CANTEEN"][:2] == ("POST_TAX", False)
    assert ded["LOAN_RECOVERY"] == ("PRE_TAX", True, Decimal("8.5000"), False)
    assert query("SELECT code, benefit_plan, tax_exempt_section, is_pre_tax FROM payroll.benefit") == \
        [("HEALTH_INSURANCE", "Health", "80D", True)]

    reimb = {r[0]: r[1:] for r in query(
        "SELECT code, reimbursement_type, carry_forward_option, max_limit, created_by, name "
        "FROM payroll.reimbursement WHERE tenant_id = %s", (FOUNDER_TENANT,))}
    # 'Fuel' landed on the FUEL row the tenant trigger seeded (created_by stays 'template')
    assert reimb["FUEL"] == ("FUEL", "CARRY_FORWARD", Decimal("3000.5000"), "template", "Fuel")
    assert reimb["TELEPHONE"][:2] == ("TELEPHONE", "MONTHLY_ENCASH")
    assert "GYM" not in reimb

    fbp = dict((t, rest) for t, *rest in query(
        "SELECT tenant_id::text, is_enabled, window_closes_on::text, is_locked, reminder_days_before_close "
        "FROM payroll.fbp"))
    assert fbp[FOUNDER_TENANT] == [True, "2025-01-31", False, [5, 1]]
    assert fbp[CUSTOMER_TENANT] == [False, None, True, []]

    idmap = json.loads((legacy_fixture / "id_map.json").read_text())
    assert idmap["payroll.earnings"]["4"] == idmap["payroll.earnings.uuid"]["ern-4"]
    assert "7" not in idmap["payroll.earnings"]


def test_active_structures_and_lines(legacy_fixture, query):
    run_base_and("loaders.load_salary")
    rows = query("SELECT e.employee_number, c.effective_from::text, c.annual_ctc, c.monthly_ctc, c.notes "
                 "FROM payroll.ctc_structure c JOIN core.employee e ON e.id = c.employee_id ORDER BY 1")
    by_emp = {r[0]: r[1:] for r in rows}
    # 1001=E001, 1002=E002, 1006=E006 (founder); 1101=C001 (customer)
    assert set(by_emp) == {"E001", "E002", "E006", "C001"}
    assert by_emp["E001"][:3] == ("2024-04-01", Decimal("600000.0000"), Decimal("50000.0000"))
    assert "revision_status=APPROVED" in by_emp["E001"][3]
    assert by_emp["E006"][0] == "2024-10-01"  # the newer of two active structures

    earn = query("SELECT x.code, l.calculation_type, l.value, l.percentage_of, l.monthly_amount, l.annual_amount, "
                 "l.earning_frequency FROM payroll.employee_earning l JOIN payroll.earning x ON x.id = l.component_id "
                 "JOIN payroll.ctc_structure c ON c.id = l.ctc_structure_id JOIN core.employee e ON e.id = c.employee_id "
                 "WHERE e.employee_number = 'E001' ORDER BY x.code, l.monthly_amount")
    lines = {(r[0], r[4]): r for r in earn}
    assert lines[("BASIC", Decimal("25000.0000"))][1:4] == ("PERCENTAGE", Decimal("50.0000"), "CTC")
    assert lines[("HOUSE_RENT_ALLOWANCE", Decimal("12500.0000"))][1:4] == ("PERCENTAGE", Decimal("50.0000"), "BASIC")
    # a legacy "percentage" conveyance line is an amount: carried FLAT
    assert lines[("CONVEYANCE_ALLOWANCE", Decimal("1600.0000"))][1:4] == ("FLAT", Decimal("1600.0000"), None)
    # yearly bonus: annual as entered, monthly = annual / 12
    assert lines[("BONUS", Decimal("5000.0000"))][5:] == (Decimal("60000.0000"), "YEARLY")
    assert len(earn) == 6  # five earnings + the variable earning (BONUS by code)
    # E002's fixed allowance 5899.995 rounds to the paisa
    assert query("SELECT l.monthly_amount FROM payroll.employee_earning l JOIN payroll.earning x ON x.id = l.component_id "
                 "WHERE x.code = 'FIXED_ALLOWANCE' AND l.tenant_id = %s ORDER BY 1", (FOUNDER_TENANT,))[0][0] \
        == Decimal("5900.0000")

    assert query("SELECT monthly_amount, annual_amount FROM payroll.employee_benefit") == \
        [(Decimal("500.0000"), Decimal("6000.0000"))]
    assert query("SELECT monthly_amount, carry_forward_option FROM payroll.employee_reimbursement") == \
        [(Decimal("2000.0000"), "CARRY_FORWARD")]
    assert query("SELECT r.code, f.annual_amount, f.monthly_amount, f.declared_by, f.earning_id IS NULL "
                 "FROM payroll.employee_fbp_component f JOIN payroll.reimbursement r ON r.id = f.reimbursement_id") == \
        [("TELEPHONE", Decimal("12000.0000"), Decimal("1000.0000"), "CARRIED", True)]
    epf = query("SELECT component_code, share, wage_base, rate, monthly_amount, is_included_in_ctc "
                "FROM payroll.ctc_epf_component ORDER BY 1")
    assert epf == [("EDLI", "EMPLOYER", Decimal("25000.0000"), Decimal("0.5000"), Decimal("75.0000"), False),
                   ("EPF_EMPLOYER", "EMPLOYER", Decimal("25000.0000"), Decimal("12.0000"), Decimal("1800.0000"), True)]
    esi = query("SELECT tenant_id::text, component_code, wage_base, monthly_amount, is_included_in_ctc "
                "FROM payroll.ctc_esi_component")
    assert esi == [(CUSTOMER_TENANT, "ESI_EMPLOYER", Decimal("24187.0000"), Decimal("813.0000"), True)]

    q = _quarantine(legacy_fixture)
    got = {(i["reason"], str(i["legacy_id"])) for i in q}
    for expected in [("CTC_SUM_MISMATCH", "504"), ("UNMAPPED_COMPONENT", "505"), ("UNMAPPED_STATUTORY_CODE", "507"),
                     ("SECOND_ACTIVE_CTC", "508"), ("EMPLOYEE_NOT_MIGRATED", "506"), ("ORG_NOT_MIGRATED", "700"),
                     ("NO_EARNING_TYPE", "7"), ("NO_REIMBURSEMENT_TYPE", "3"), ("ORG_NOT_MIGRATED", "20"),
                     ("FBP_LINE_DISABLED", "9402")]:
        assert expected in got, expected
    mismatch = next(i for i in q if i["reason"] == "CTC_SUM_MISMATCH")
    assert mismatch["monthly_salary"] == "40000.00" and mismatch["monthly_lines"] == "30000.00"
    # revisions (500) and deleted rows (502) are not carried and not reported
    assert not any(str(i["legacy_id"]) in ("500", "502") and i["legacy_table"] == "ctc_structure" for i in q)
    allowed = {"reason", "legacy_table", "legacy_id", "tenant_id", "organization_id", "employee_id", "line_table",
               "line_id", "component_id", "code", "kept", "monthly_salary", "monthly_lines", "ctc_structure_id", "note"}
    assert all(set(i) <= allowed for i in q)


def test_tenant_isolation(legacy_fixture, query):
    run_base_and("loaders.load_salary")
    for t in TABLES:
        bad = query(f"SELECT count(*) FROM {t} x WHERE NOT EXISTS "
                    f"(SELECT 1 FROM core.tenant ct WHERE ct.tenant_id = x.tenant_id)")
        assert bad == [(0,)], t
    # every line belongs to a structure of its own tenant, every structure to an employee of its tenant
    assert query("SELECT count(*) FROM payroll.employee_earning l JOIN payroll.ctc_structure c "
                 "ON c.id = l.ctc_structure_id WHERE c.tenant_id <> l.tenant_id") == [(0,)]
    assert query("SELECT count(*) FROM payroll.employee_earning l JOIN payroll.earning x "
                 "ON x.id = l.component_id WHERE x.tenant_id <> l.tenant_id") == [(0,)]
    assert query("SELECT count(*) FROM payroll.ctc_structure c JOIN core.employee e ON e.id = c.employee_id "
                 "WHERE e.tenant_id <> c.tenant_id") == [(0,)]
    assert query("SELECT count(*) FROM payroll.ctc_structure WHERE tenant_id = %s", (CUSTOMER_TENANT,)) == [(1,)]


def test_second_run_changes_nothing(legacy_fixture, query):
    run_base_and("loaders.load_salary")
    for t in TABLES:
        assert query(f"SELECT count(*) FROM {t}")[0][0] > 0, t
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    run_base_and("loaders.load_salary")
    after = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    assert before == after
    assert query("SELECT tenant_id::text, status, count(*) FROM core.migration_ledger "
                 "WHERE step_name = 'load_salary' GROUP BY 1, 2 ORDER BY 1") == \
        sorted([(FOUNDER_TENANT, "DONE", 1), (CUSTOMER_TENANT, "DONE", 1)])


def test_forced_rerun_is_still_a_no_op(legacy_fixture, query):
    """Bypass the ledger skip: the upserts themselves must change nothing."""
    from loaders import load_salary

    run_base_and("loaders.load_salary")
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    query("DELETE FROM core.migration_ledger WHERE step_name = 'load_salary'")
    assert load_salary.run()
    after = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    assert before == after
