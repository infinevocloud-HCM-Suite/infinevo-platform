"""3g load_payruns against a real migrated Postgres (fixture: tests/fixtures/fx_payruns.py)."""
import json
from decimal import Decimal

import psycopg2

from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT
from tests.pipeline import BASE, run_base_and, run_step, run_steps

TABLES = ["payroll.payrun", "payroll.employee_payrun", "payroll.employee_payrun_line",
          "payroll.scheduled_earning"]
EARNING_ID = "e0000000-0000-0000-0000-000000000007"


def _seed_earning(target_db, staging_dir):
    """Stand-in for load_salary: legacy earning 5 (Bonus) of org 148 as a payroll.earning, in the id-map."""
    conn = psycopg2.connect(**target_db)
    with conn, conn.cursor() as cur:
        cur.execute("SELECT set_config('app.current_tenant_id', %s, true)", (FOUNDER_TENANT,))
        cur.execute("INSERT INTO payroll.earning (id, tenant_id, code, name, earning_type, is_one_time) "
                    "VALUES (%s, %s, 'BONUS_ONE_TIME', 'One-time bonus', 'BONUS', true)",
                    (EARNING_ID, FOUNDER_TENANT))
    conn.close()
    p = staging_dir / "id_map.json"
    data = json.loads(p.read_text())
    data.setdefault("payroll.earnings", {})["5"] = EARNING_ID
    p.write_text(json.dumps(data))


def _run_with_earning(target_db, staging_dir):
    run_steps(BASE)
    _seed_earning(target_db, staging_dir)
    run_step("loaders.load_payruns")


def test_runs_lines_and_totals(legacy_fixture, query):
    run_base_and("loaders.load_payruns")
    runs = {r[0]: r[1:] for r in query(
        "SELECT period || ':' || run_type, status, total_gross, total_deductions, total_net_pay, included_count, "
        "paid_on::text, period_start::text, period_end::text FROM payroll.payrun WHERE tenant_id = %s",
        (FOUNDER_TENANT,))}
    assert set(runs) == {"2025-04:REGULAR", "2025-05:REGULAR", "2025-06:REGULAR", "2025-05:OFF_CYCLE"}
    # header recomputed from the employee rows, to the paisa
    assert runs["2025-04:REGULAR"][:5] == ("PAID", Decimal("260002.75"), Decimal("10001.00"),
                                           Decimal("235001.25"), 5)
    assert runs["2025-04:REGULAR"][5] == "2025-04-30"
    assert runs["2025-06:REGULAR"][0] == "APPROVED" and runs["2025-06:REGULAR"][5] is None
    # off-cycle: sums of the earnings and deductions child tables
    assert runs["2025-05:OFF_CYCLE"][:5] == ("PAID", Decimal("3500.00"), Decimal("100.25"), Decimal("3399.75"), 2)
    # the customer run had no period dates: derived from processingPeriod
    assert query("SELECT period, period_start::text, period_end::text, pay_date::text FROM payroll.payrun "
                 "WHERE tenant_id = %s", (CUSTOMER_TENANT,)) == [("2025-04", "2025-04-01", "2025-04-30", "2025-05-07")]
    assert query("SELECT count(*) FROM payroll.employee_payrun")[0][0] == 21
    # net_pay sum equals the fixture (loaded rows) to the paisa
    assert query("SELECT sum(net_pay) FROM payroll.employee_payrun WHERE tenant_id = %s",
                 (CUSTOMER_TENANT,))[0][0] == Decimal("114000.00")
    lines = dict(query("SELECT component_code || ':' || line_kind || ':' || source, count(*) "
                       "FROM payroll.employee_payrun_line GROUP BY 1"))
    assert lines == {"TDS:DEDUCTION:TAX": 19, "LOP:DEDUCTION:LOP": 1, "BONUS:EARNING:PAY_INPUT": 1,
                     "CLAIM_DEDUCTION:DEDUCTION:PAY_INPUT": 1, "CLAIM_REIMBURSEMENT:REIMBURSEMENT:PAY_INPUT": 1}
    lop = query("SELECT l.amount, ep.lop_days, ep.paid_days FROM payroll.employee_payrun_line l "
                "JOIN payroll.employee_payrun ep ON ep.id = l.employee_payrun_id WHERE l.component_code = 'LOP'")
    assert lop == [(Decimal("1666.68"), Decimal("1.00"), Decimal("29.00"))]


def test_no_prior_month_where_a_migrated_run_exists(legacy_fixture, query):
    """Every PAID legacy month here has a migrated REGULAR run, whose TDS line the target
    already counts as prior tax. A prior_payroll_month for the same month would count it
    twice, and the target's own importer refuses one (REAL_RUN_EXISTS)."""
    run_base_and("loaders.load_payruns")
    assert query("SELECT count(*) FROM payroll.prior_payroll_month")[0][0] == 0
    assert query("SELECT count(*) FROM payroll.prior_payroll_import_log")[0][0] == 0
    assert query("SELECT count(*) FROM core.document")[0][0] == 0
    # the tax is carried once, on the run's TDS line
    assert query("SELECT l.amount FROM payroll.employee_payrun_line l JOIN payroll.employee_payrun ep "
                 "ON ep.id = l.employee_payrun_id JOIN payroll.payrun r ON r.id = ep.payrun_id "
                 "JOIN core.employee e ON e.id = ep.employee_id WHERE e.employee_number = 'E002' "
                 "AND r.period = '2025-04' AND r.run_type = 'REGULAR' AND l.component_code = 'TDS'") ==         [(Decimal("3000.10"),)]


def test_quarantine_reasons(legacy_fixture, query):
    run_base_and("loaders.load_payruns")
    q = json.loads((legacy_fixture / "stg_quarantined_load_payruns.json").read_text())
    got = {(i["reason"], i["legacy_table"], str(i["legacy_id"])) for i in q}
    expected = {
        ("PAYRUN_STATUS_UNMAPPED", "payruns", "4"),
        ("DUPLICATE_REGULAR_PERIOD", "payruns", "5"),
        ("ORG_NOT_MIGRATED", "payruns", "7"),
        ("PAYRUN_NOT_LOADED", "employee_payruns", "201"),
        ("PAYRUN_NOT_LOADED", "employee_payruns", "202"),
        ("PAYRUN_NOT_LOADED", "employee_payruns", "401"),
        ("PAYRUN_NOT_FOUND", "employee_payruns", "203"),
        ("EMPLOYEE_NOT_MIGRATED", "employee_payruns", "305"),
        ("NEGATIVE_LINE_AMOUNT", "employee_payruns", "103"),
        ("OFF_CYCLE_STATUS_UNMAPPED", "off_cycle_pay_run", "2"),
        ("EARNING_NOT_MIGRATED", "one_time_payout", "1"),  # no load_salary in this test
        ("EARNING_NOT_MIGRATED", "one_time_payout", "2"),
        ("NO_EMPLOYEE", "one_time_payout", "3"),
    }
    assert got == expected
    assert next(i for i in q if i["reason"] == "PAYRUN_STATUS_UNMAPPED")["status"] == "READY"
    assert "@" not in json.dumps(q)


def test_one_time_payout_needs_the_earning(legacy_fixture, target_db, query):
    _run_with_earning(target_db, legacy_fixture)
    assert query("SELECT amount, first_period::text, instalments, paid_instalments, last_paid_period::text, "
                 "status, earning_id::text FROM payroll.scheduled_earning") == [
        (Decimal("5000.75"), "2025-06-01", 1, 1, "2025-06-01", "PAID", EARNING_ID)]
    q = json.loads((legacy_fixture / "stg_quarantined_load_payruns.json").read_text())
    assert [i["legacy_id"] for i in q if i["reason"] == "EARNING_NOT_MIGRATED"] == [2]


def test_customer_tenant_isolated(legacy_fixture, query):
    run_base_and("loaders.load_payruns")
    for t in ("payroll.employee_payrun", "payroll.prior_payroll_month", "payroll.scheduled_earning"):
        assert query(f"SELECT count(*) FROM {t} x JOIN core.employee e ON e.id = x.employee_id "
                     f"WHERE e.tenant_id <> x.tenant_id")[0][0] == 0, t
    assert query("SELECT count(*) FROM payroll.employee_payrun x JOIN payroll.payrun r ON r.id = x.payrun_id "
                 "WHERE r.tenant_id <> x.tenant_id")[0][0] == 0
    assert query("SELECT count(*) FROM payroll.payrun WHERE tenant_id = %s", (CUSTOMER_TENANT,))[0][0] == 1
    assert query("SELECT count(*) FROM payroll.employee_payrun WHERE tenant_id = %s", (CUSTOMER_TENANT,))[0][0] == 4
    # every line belongs to a run of its own tenant
    assert query("SELECT count(*) FROM payroll.employee_payrun_line l JOIN payroll.payrun r ON r.id = l.payrun_id "
                 "WHERE r.tenant_id <> l.tenant_id")[0][0] == 0


def test_second_run_is_a_no_op(legacy_fixture, target_db, query):
    _run_with_earning(target_db, legacy_fixture)
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    assert all(c > 0 for c, _ in before.values()), before
    # unchanged inputs: the ledger skips both tenants
    run_step("loaders.load_payruns")
    # a changed input checksum makes every tenant re-run its upserts: still nothing changes
    (legacy_fixture / "stg_payroll_earnings.json").write_text("[]")
    run_step("loaders.load_payruns")
    after = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    assert before == after
    ledger = query("SELECT tenant_id::text, status FROM core.migration_ledger WHERE step_name = 'load_payruns'")
    assert sorted(ledger) == sorted([(FOUNDER_TENANT, "DONE"), (CUSTOMER_TENANT, "DONE")])
