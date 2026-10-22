"""3f load_statutory against a real migrated Postgres."""
import json
from decimal import Decimal

from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT
from tests.pipeline import run_base_and

TABLES = ["payroll.epf_setting", "payroll.esi_setting", "payroll.org_pt_override", "payroll.org_pt_override_slab"]


def _quarantine(staging):
    return json.loads((staging / "stg_quarantined_load_statutory.json").read_text())


def test_epf_and_esi_settings(legacy_fixture, query):
    run_base_and("loaders.load_statutory")
    epf = query("SELECT tenant_id::text, is_enabled, registration_number, registration_date::text, deduction_cycle, "
                "employee_rate, employer_rate, eps_rate, edli_rate, admin_charge_rate, wage_ceiling, "
                "restrict_employee_to_ceiling, restrict_employer_to_ceiling, prorate_restricted_wage, eps_senior_age, "
                "include_employer_in_ctc, include_edli_in_ctc, include_admin_in_ctc, include_edli_admin_in_ctc, "
                "include_edli_in_structure, include_admin_in_structure, include_edli_admin_in_structure "
                "FROM payroll.epf_setting")
    # one row: the customer's EPF has a deduction cycle the target cannot hold
    assert epf == [(FOUNDER_TENANT, True, "MHBAN0012345000", "2023-01-15", "MONTHLY",
                    Decimal("12.0000"), Decimal("12.0000"), Decimal("8.3300"), Decimal("0.5000"), Decimal("0.5000"),
                    Decimal("15000.0000"), False, True, True, 58, True, True, False, False, True, True, True)]
    esi = {r[0]: r[1:] for r in query(
        "SELECT tenant_id::text, is_enabled, registration_number, employee_rate, employer_rate, "
        "include_employer_in_ctc, include_in_structure FROM payroll.esi_setting")}
    assert esi[FOUNDER_TENANT] == (True, "31000123450001001", Decimal("0.7500"), Decimal("3.2500"), True, True)
    # unreadable legacy rates keep the statutory defaults
    assert esi[CUSTOMER_TENANT] == (False, None, Decimal("0.7500"), Decimal("3.2500"), False, False)
    q = {(i["reason"], i["legacy_table"], str(i["legacy_id"])) for i in _quarantine(legacy_fixture)}
    assert ("DEDUCTION_CYCLE_UNMAPPED", "epf", "2") in q
    assert ("ORG_NOT_MIGRATED", "esi", "3") in q


def test_pt_overrides_only_where_slabs_differ(legacy_fixture, query):
    run_base_and("loaders.load_statutory")
    ovr = query("SELECT state_code, registration_number, effective_from::text FROM payroll.org_pt_override "
                "WHERE tenant_id = %s ORDER BY 1", (FOUNDER_TENANT,))
    # Maharashtra equals reference.pt_slab, so it is not carried
    assert ovr == [("KA", "PTKA009", "2024-04-01"), ("TN", "PTTN777", "2024-06-10")]
    slabs = query("SELECT o.state_code, s.sort_order, s.from_amount, s.to_amount, s.amount, s.deduction_months "
                  "FROM payroll.org_pt_override_slab s JOIN payroll.org_pt_override o ON o.id = s.override_id "
                  "ORDER BY 1, 2")
    assert slabs == [
        ("KA", 1, Decimal("0.0000"), Decimal("24999.0000"), Decimal("0.0000"), None),
        ("KA", 2, Decimal("24999.0000"), None, Decimal("250.0000"), None),
        ("TN", 1, Decimal("0.0000"), Decimal("21000.0000"), Decimal("0.0000"), "3,9"),
        ("TN", 2, Decimal("21000.0000"), None, Decimal("1250.0000"), "3,9"),
    ]
    assert query("SELECT count(*) FROM payroll.org_pt_override WHERE tenant_id = %s", (CUSTOMER_TENANT,)) == [(0,)]
    q = _quarantine(legacy_fixture)
    got = {(i["reason"], i["legacy_table"], str(i["legacy_id"])) for i in q}
    assert ("PT_MATCHES_REFERENCE", "professionalTax", "1") in got
    assert ("PT_STATE_UNKNOWN", "professionalTax", "3") in got
    assert ("ORG_NOT_MIGRATED", "org_pt_override", "2") in got
    mh = next(i for i in q if i["reason"] == "PT_MATCHES_REFERENCE")
    assert mh["state_code"] == "MH" and mh["registration_number_dropped"] is True
    assert all("registration_number" not in i for i in q)


def test_tenant_isolation(legacy_fixture, query):
    run_base_and("loaders.load_statutory")
    assert query("SELECT count(*) FROM payroll.org_pt_override_slab s JOIN payroll.org_pt_override o "
                 "ON o.id = s.override_id WHERE o.tenant_id <> s.tenant_id") == [(0,)]
    assert dict(query("SELECT tenant_id::text, count(*) FROM payroll.esi_setting GROUP BY 1")) == \
        {FOUNDER_TENANT: 1, CUSTOMER_TENANT: 1}


def test_second_run_changes_nothing(legacy_fixture, query):
    from loaders import load_statutory

    run_base_and("loaders.load_statutory")
    for t in TABLES:
        assert query(f"SELECT count(*) FROM {t}")[0][0] > 0, t
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    run_base_and("loaders.load_statutory")
    after = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    assert before == after
    assert query("SELECT tenant_id::text, status, count(*) FROM core.migration_ledger "
                 "WHERE step_name = 'load_statutory' GROUP BY 1, 2 ORDER BY 1") == \
        sorted([(FOUNDER_TENANT, "DONE", 1), (CUSTOMER_TENANT, "DONE", 1)])
    # and with the ledger skip bypassed, the upserts themselves change nothing
    query("DELETE FROM core.migration_ledger WHERE step_name = 'load_statutory'")
    assert load_statutory.run()
    assert {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES} == before
