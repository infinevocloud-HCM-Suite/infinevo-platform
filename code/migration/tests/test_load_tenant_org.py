"""3a load_tenant and 3b load_org against a real migrated Postgres."""
import json

from tests.fixtures import CUSTOMER_TENANT, DELETED_TENANT, FOUNDER_TENANT


def _run_both():
    from loaders import load_org, load_tenant

    assert load_tenant.run()
    assert load_org.run()


def test_tenants_created_with_subscription_modules_and_roles(legacy_fixture, query):
    _run_both()
    tenants = dict(query("SELECT tenant_id::text, name FROM core.tenant"))
    assert tenants[FOUNDER_TENANT] == "Infinevo Founder Co"
    assert tenants[CUSTOMER_TENANT] == "Acme Customer Pvt Ltd"
    assert DELETED_TENANT not in tenants
    mods = query("SELECT tenant_id::text, module FROM core.subscription_module ORDER BY 1, 2")
    assert (FOUNDER_TENANT, "HRMS") in mods and (FOUNDER_TENANT, "PAYROLL") in mods
    assert (CUSTOMER_TENANT, "PAYROLL") in mods and (CUSTOMER_TENANT, "HRMS") not in mods
    # the core.tenant insert trigger seeded the system roles
    assert query("SELECT count(*) FROM core.role WHERE tenant_id = %s", (CUSTOMER_TENANT,))[0][0] > 0
    q = json.loads((legacy_fixture / "stg_quarantined_load_tenant.json").read_text())
    reasons = {(i["reason"], str(i["legacy_id"])) for i in q}
    assert ("ORG_DELETED", "202") in reasons and ("ORG_NOT_IN_MAPPING", "203") in reasons


def test_org_masters(legacy_fixture, query):
    _run_both()
    depts = query("SELECT tenant_id::text, code, name, is_active FROM core.department ORDER BY 1, 2")
    assert (FOUNDER_TENANT, "ENG", "Engineering", True) in depts
    assert (FOUNDER_TENANT, "HUMAN_RESOURCES", "Human Resources", True) in depts
    # two 'Finance' rows in one tenant get distinct codes
    assert {d[1] for d in depts if d[0] == CUSTOMER_TENANT} == {"FINANCE", "FINANCE_2"}
    locs = query("SELECT name, state_code, is_filing_address FROM core.work_location WHERE tenant_id = %s ORDER BY name",
                 (FOUNDER_TENANT,))
    assert locs == [("Bengaluru", "KA", False), ("Pune HQ", "MH", True)]
    sched = query("SELECT tenant_id::text, working_days, pay_day_rule, pay_day_of_month FROM payroll.pay_schedule ORDER BY 1")
    assert sched == [(FOUNDER_TENANT, [1, 2, 3, 4, 5], "LAST_WORKING_DAY", None),
                     (CUSTOMER_TENANT, [1, 2, 3, 4, 5, 6], "SPECIFIC_DAY", 7)]
    lop = dict((t, (b, d, w)) for t, b, d, w in query(
        "SELECT tenant_id::text, working_day_basis, configured_days_per_month, weekends_payable FROM core.lop_policy"))
    assert lop[FOUNDER_TENANT] == ("ACTUAL_DAYS", None, False)
    assert lop[CUSTOMER_TENANT][0] == "ORG_DAYS" and int(lop[CUSTOMER_TENANT][1]) == 26
    assert query("SELECT tenant_id::text, tan FROM payroll.tax_deductor") == [(FOUNDER_TENANT, "PNEA12345B")]
    q = json.loads((legacy_fixture / "stg_quarantined_load_org.json").read_text())
    assert any(i["reason"].startswith("TAN_INVALID") for i in q)
    assert any(i["reason"] == "SECOND_FILING_ADDRESS" for i in q)
    idmap = json.loads((legacy_fixture / "id_map.json").read_text())
    assert idmap["payroll.department"]["1"] and idmap["payroll.organization"]["148"] == FOUNDER_TENANT


def test_second_run_changes_nothing_and_skips_by_ledger(legacy_fixture, query):
    _run_both()
    tables = ["core.tenant", "core.subscription", "core.subscription_module", "core.department",
              "core.designation", "core.work_location", "payroll.pay_schedule", "core.lop_policy",
              "payroll.tax_deductor"]
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in tables}
    _run_both()
    after = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in tables}
    assert before == after
    ledger = query("SELECT step_name, status, count(*) FROM core.migration_ledger GROUP BY 1, 2 ORDER BY 1")
    assert ledger == [("load_org", "DONE", 2), ("load_tenant", "DONE", 2)]


def test_quarantine_survives_a_skipped_rerun(legacy_fixture, query):
    _run_both()
    path = legacy_fixture / "stg_quarantined_load_org.json"
    first = sorted(json.dumps(i, sort_keys=True) for i in json.loads(path.read_text()))
    _run_both()  # every tenant skipped as unchanged
    second = sorted(json.dumps(i, sort_keys=True) for i in json.loads(path.read_text()))
    assert first == second and any('"SECOND_FILING_ADDRESS"' in i for i in second)


def test_changed_input_is_reloaded(legacy_fixture, query):
    _run_both()
    p = legacy_fixture / "stg_payroll_department.json"
    rows = json.loads(p.read_text())
    rows[0]["name"] = "Engineering & Platform"
    p.write_text(json.dumps(rows))
    _run_both()
    assert query("SELECT name FROM core.department WHERE tenant_id = %s AND code = 'ENG'",
                 (FOUNDER_TENANT,)) == [("Engineering & Platform",)]
