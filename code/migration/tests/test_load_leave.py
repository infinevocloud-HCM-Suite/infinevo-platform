"""3i load_leave against a real migrated Postgres (W-68 Task 4, W-67 R2)."""
import json
from decimal import Decimal

from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT
from tests.pipeline import run_base_and

TABLES = ["core.leave_type", "core.leave_policy", "core.leave_policy_eligibility", "core.leave_allocation",
          "core.leave_consumption", "core.leave_request", "core.approval_instance", "core.approval_step",
          "core.holiday_calendar", "core.holiday_calendar_location", "core.holiday",
          "hrms.attendance_preference"]


def _quarantine(staging):
    return json.loads((staging / "stg_quarantined_load_leave.json").read_text())


def _count(query, table, tenant):
    return query(f"SELECT count(*) FROM {table} WHERE tenant_id = %s", (tenant,))[0][0]


def test_every_table_written(legacy_fixture, query):
    run_base_and("loaders.load_leave")
    for t in TABLES:
        assert query(f"SELECT count(*) FROM {t}")[0][0] > 0, t


def test_founder_from_hrms(legacy_fixture, query):
    run_base_and("loaders.load_leave")
    types = dict(query("SELECT code, is_active FROM core.leave_type WHERE tenant_id = %s", (FOUNDER_TENANT,)))
    assert types == {"ANNUAL": True, "SICK": True, "CASUAL": True, "MATERNITY": True, "PATERNITY": True,
                     "LEGACY": False, "WORK_FROM_HOME": True}
    # An HRMS type named like a seeded code fills it: name, start date and default days carried
    assert query("SELECT t.name, t.valid_from::text, p.annual_days, p.carry_forward_enabled "
                 "FROM core.leave_type t JOIN core.leave_policy p ON p.leave_type_id = t.id "
                 "WHERE t.tenant_id = %s AND t.code = 'ANNUAL'", (FOUNDER_TENANT,)) == \
        [("Annual Leave", "2024-04-01", Decimal("18.00"), True)]
    # A seeded code with no HRMS type keeps the legacy entity default (LeaveBalance.java)
    assert query("SELECT p.annual_days FROM core.leave_policy p JOIN core.leave_type t ON t.id = p.leave_type_id "
                 "WHERE t.tenant_id = %s AND t.code = 'MATERNITY'", (FOUNDER_TENANT,)) == [(Decimal("90.00"),)]

    # Balances split per type; leave year starts April, label YYYY-YY
    allocs = query(
        "SELECT e.employee_number, t.code, a.leave_year, a.year_start_date::text, a.entitlement_days, "
        "a.accrued_days FROM core.leave_allocation a JOIN core.employee e ON e.id = a.employee_id "
        "JOIN core.leave_type t ON t.id = a.leave_type_id WHERE a.tenant_id = %s ORDER BY 1, 3, 2", (FOUNDER_TENANT,))
    assert len(allocs) == 9
    assert ("E001", "ANNUAL", "2024-25", "2024-04-01", Decimal("24.00"), Decimal("0.00")) in allocs
    assert ("E002", "ANNUAL", "2025-26", "2025-04-01", Decimal("24.00"), Decimal("0.00")) in allocs  # HRMS id "2"
    assert not [a for a in allocs if a[0] == "E001" and a[1] == "MATERNITY"]  # 0 allotted, 0 used
    used = query("SELECT t.code, c.consumed_days, c.period FROM core.leave_consumption c "
                 "JOIN core.leave_allocation a ON a.id = c.allocation_id JOIN core.leave_type t ON t.id = a.leave_type_id "
                 "JOIN core.employee e ON e.id = c.employee_id WHERE e.employee_number = 'E001' ORDER BY 1")
    assert used == [("ANNUAL", Decimal("3.00"), "2024-04"), ("PATERNITY", Decimal("2.00"), "2024-04"),
                    ("SICK", Decimal("1.00"), "2024-04")]
    assert _count(query, "core.leave_consumption", FOUNDER_TENANT) == 4

    # Requests: all under LEGACY, hr_status wins
    reqs = {r[0]: r[1:] for r in query(
        "SELECT e.employee_number, t.code, r.status, r.working_days, r.is_half_day, r.half_day_period, "
        "r.decided_at IS NOT NULL FROM core.leave_request r JOIN core.employee e ON e.id = r.employee_id "
        "JOIN core.leave_type t ON t.id = r.leave_type_id WHERE r.tenant_id = %s", (FOUNDER_TENANT,))}
    assert set(reqs) == {"E003", "E004", "E005", "E006"}
    assert {v[0] for v in reqs.values()} == {"LEGACY"}
    assert reqs["E003"][1:] == ("APPROVED", Decimal("2.00"), False, None, True)
    assert reqs["E004"][1:] == ("REJECTED", Decimal("0.50"), True, "first", True)
    assert reqs["E005"][1] == "PENDING" and not reqs["E005"][5]
    assert reqs["E006"][1] == "PENDING"  # manager rejected, HR pending: hr_status wins

    # Approval history: one instance, two steps, the app's subject table and definition
    inst = query("SELECT i.status, i.subject_table, d.flow_type, i.completed_at IS NOT NULL "
                 "FROM core.leave_request r JOIN core.approval_instance i ON i.id = r.approval_instance_id "
                 "JOIN core.approval_definition d ON d.id = i.definition_id JOIN core.employee e ON e.id = r.employee_id "
                 "WHERE e.employee_number = 'E004'")
    assert inst == [("REJECTED", "leave_request", "LEAVE", True)]
    steps = query("SELECT s.step_index, s.approver_kind, m.employee_number, s.decision, s.comment "
                  "FROM core.approval_step s JOIN core.leave_request r ON r.approval_instance_id = s.instance_id "
                  "JOIN core.employee e ON e.id = r.employee_id LEFT JOIN core.employee m ON m.id = s.assignee_employee_id "
                  "WHERE e.employee_number = 'E003' ORDER BY 1")
    assert steps == [(0, "REPORTING_MANAGER", "E001", "APPROVED", "ok"), (1, "ROLE", None, "APPROVED", "approved by hr")]
    assert query("SELECT count(*) FROM core.approval_step s JOIN core.approval_instance i ON i.id = s.instance_id "
                 "WHERE i.tenant_id = %s AND i.status = 'PENDING' AND s.decision IS NULL", (FOUNDER_TENANT,))[0][0] == 3

    # No decided request leaves an undecided step in the app's approval queue
    assert query("SELECT count(*) FROM core.approval_step s JOIN core.approval_instance i ON i.id = s.instance_id "
                 "WHERE i.status <> 'PENDING' AND s.decision IS NULL")[0][0] == 0

    # Holidays in one default calendar
    assert query("SELECT c.name, c.is_default, count(h.id) FROM core.holiday_calendar c "
                 "JOIN core.holiday h ON h.calendar_id = c.id WHERE c.tenant_id = %s GROUP BY 1, 2",
                 (FOUNDER_TENANT,)) == [("Holidays", True, 2)]

    reasons = {(i["reason"], i["legacy_table"]) for i in _quarantine(legacy_fixture)}
    for expected in [("EMPLOYEE_NOT_FOUND", "leave_balances"), ("DUPLICATE_BALANCE_YEAR", "leave_balances"),
                     ("APPROVED_OVERLAP", "leave_requests"), ("EMPLOYEE_NOT_FOUND", "leave_requests"),
                     ("NO_NAME", "holidays"), ("FOUNDER_LEAVE_FROM_HRMS", "leave_type"),
                     ("FOUNDER_LEAVE_FROM_HRMS", "employee_leave_allocation"),
                     ("FOUNDER_LEAVE_FROM_HRMS", "holidays")]:
        assert expected in reasons, expected


def test_customer_from_payroll(legacy_fixture, query):
    run_base_and("loaders.load_leave")
    pol = {r[0]: r[1:] for r in query(
        "SELECT t.code, t.is_paid, t.valid_from::text, p.annual_days, p.accrual_frequency, p.accrual_units, "
        "p.reset_frequency, p.carry_forward_cap, p.exceed_balance_mode, p.exceed_balance_limit_days, "
        "p.max_days_per_application, p.gender FROM core.leave_type t JOIN core.leave_policy p ON p.leave_type_id = t.id "
        "WHERE t.tenant_id = %s", (CUSTOMER_TENANT,))}
    assert set(pol) == {"CL", "LOP"}
    assert pol["CL"] == (True, "2024-01-01", Decimal("18.00"), "monthly", Decimal("1.50"), "yearly", Decimal("5.00"),
                         "yearEndLimit", Decimal("0.00"), Decimal("3.00"), "FEMALE")
    # no validity_from: the start of the earliest leave year it was allocated in; two genders -> none
    assert pol["LOP"] == (False, "2024-04-01", Decimal("0.00"), None, None, None, None, "markAsLOP", None, None, None)
    assert dict(query("SELECT dimension, count(*) FROM core.leave_policy_eligibility WHERE tenant_id = %s GROUP BY 1",
                      (CUSTOMER_TENANT,))) == {"department": 1, "designation": 1}

    allocs = query("SELECT e.employee_number, t.code, a.leave_year, a.entitlement_days, a.carried_forward_days, "
                   "a.carry_forward_expires_on::text FROM core.leave_allocation a JOIN core.employee e ON e.id = a.employee_id "
                   "JOIN core.leave_type t ON t.id = a.leave_type_id WHERE a.tenant_id = %s ORDER BY 1, 2", (CUSTOMER_TENANT,))
    assert allocs == [("C001", "CL", "2024-25", Decimal("18.00"), Decimal("2.00"), "2024-12-31"),
                      ("C001", "LOP", "2024-25", Decimal("0.00"), Decimal("0.00"), "2024-12-31"),
                      ("C002", "CL", "2024-25", Decimal("18.00"), Decimal("0.00"), "2024-12-31")]
    cons = query("SELECT consumed_days, period, consumed_on::text, leave_request_id FROM core.leave_consumption "
                 "WHERE tenant_id = %s ORDER BY period", (CUSTOMER_TENANT,))
    assert cons == [(Decimal("2.00"), "2024-05", "2024-05-01", None), (Decimal("1.00"), "2024-07", "2024-07-01", None)]

    cals = query("SELECT c.name, c.is_default, string_agg(h.name, ',' ORDER BY h.name), "
                 "count(l.id) > 0 FROM core.holiday_calendar c JOIN core.holiday h ON h.calendar_id = c.id "
                 "LEFT JOIN core.holiday_calendar_location l ON l.calendar_id = c.id "
                 "WHERE c.tenant_id = %s GROUP BY 1, 2 ORDER BY 2 DESC", (CUSTOMER_TENANT,))
    assert cals == [("Holidays", True, "New Year", False), ("Chennai holidays", False, "New Year,Pongal", True)]
    assert query("SELECT is_restricted, description FROM core.holiday WHERE tenant_id = %s AND name = 'Pongal'",
                 (CUSTOMER_TENANT,)) == [(True, "desc 2")]
    # The customer tenant got no HRMS data
    assert query("SELECT count(*) FROM core.leave_type WHERE tenant_id = %s AND code = 'LEGACY'", (CUSTOMER_TENANT,))[0][0] == 0
    assert _count(query, "core.leave_request", CUSTOMER_TENANT) == 0

    reasons = {(i["reason"], i["legacy_table"]) for i in _quarantine(legacy_fixture)}
    for expected in [("LEAVE_KIND_UNMAPPED", "leave_type"), ("ORG_NOT_MIGRATED", "leave_type"),
                     ("DEPARTMENT_NOT_FOUND", "leave_type_departments"),
                     ("WORK_LOCATION_NOT_FOUND", "leave_type_work_locations"),
                     ("LEAVE_TYPE_NOT_FOUND", "employee_leave_allocation"),
                     ("EMPLOYEE_NOT_FOUND", "employee_leave_allocation"),
                     ("ALLOCATION_NOT_FOUND", "employee_leave_balance_consumption"),
                     ("ZERO_DAYS", "employee_leave_balance_consumption"),
                     ("BAD_LEAVE_MONTH", "employee_leave_balance_consumption"),
                     ("HOLIDAY_INACTIVE", "holidays"), ("LOCATION_NOT_FOUND", "holiday_locations")]:
        assert expected in reasons, expected
    assert "@" not in json.dumps(_quarantine(legacy_fixture))


def test_attendance_preferences_and_lop_policy(legacy_fixture, query):
    run_base_and("loaders.load_leave")
    prefs = dict((t, rest) for t, *rest in query(
        "SELECT tenant_id::text, hours_calculation, full_day_minimum_hours, half_day_minimum_hours, "
        "regularization_window_days, max_regularizations_per_month FROM hrms.attendance_preference"))
    assert prefs[CUSTOMER_TENANT] == ["FIRST_IN_LAST_OUT", Decimal("8.50"), Decimal("4.00"), 5, 3]
    assert prefs[FOUNDER_TENANT] == ["EVERY_SESSION", Decimal("9.00"), Decimal("4.50"), None, None]
    # core.lop_policy stays load_org's single row; the founder's differing flag is reported
    assert query("SELECT tenant_id::text, count(*) FROM core.lop_policy WHERE tenant_id IN (%s, %s) GROUP BY 1 "
                 "ORDER BY 1", (FOUNDER_TENANT, CUSTOMER_TENANT)) == [(FOUNDER_TENANT, 1), (CUSTOMER_TENANT, 1)]
    q = [i for i in _quarantine(legacy_fixture) if i["reason"] == "LOP_POLICY_KEPT_FROM_PAY_SCHEDULE"]
    assert [i["tenant_id"] for i in q] == [FOUNDER_TENANT]


def test_holiday_location_by_work_location_uuid(legacy_fixture, query):
    """Payroll stores a holiday's and a leave type's locations as workLocationId (addHoliday.js:259)."""
    p = legacy_fixture / "stg_payroll_workLocations.json"
    rows = json.loads(p.read_text())
    for r in rows:
        r["workLocationId"] = f"wl-{r['id']}"
    p.write_text(json.dumps(rows))
    p = legacy_fixture / "stg_payroll_holiday_locations.json"
    p.write_text(json.dumps([{"holiday_id": 2, "location": "wl-3"}]))
    run_base_and("loaders.load_leave")
    assert query("SELECT count(*) FROM core.holiday_calendar_location WHERE tenant_id = %s", (CUSTOMER_TENANT,))[0][0] == 1
    assert dict(query("SELECT dimension, count(*) FROM core.leave_policy_eligibility WHERE tenant_id = %s GROUP BY 1",
                      (CUSTOMER_TENANT,)))["work_location"] == 1
    # Holiday 5 (location "wl-unknown") is no longer listed with a location: tenant-wide
    assert _count(query, "core.holiday", CUSTOMER_TENANT) == 5


def test_second_run_changes_nothing(legacy_fixture, query):
    run_base_and("loaders.load_leave")
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    run_base_and("loaders.load_leave")
    after = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    assert before == after
    assert query("SELECT tenant_id::text, status, count(*) FROM core.migration_ledger WHERE step_name = 'load_leave' "
                 "GROUP BY 1, 2 ORDER BY 1") == [(FOUNDER_TENANT, "DONE", 1), (CUSTOMER_TENANT, "DONE", 1)]


def test_forced_rerun_updates_nothing(legacy_fixture, query, monkeypatch):
    """Even when the ledger does not skip (changed input elsewhere), unchanged rows keep updated_at."""
    run_base_and("loaders.load_leave")
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    from common import ledger
    from loaders import load_leave

    real = ledger.run_per_tenant
    monkeypatch.setattr(load_leave, "run_per_tenant", lambda *a, **k: real(*a, **{**k, "force": True}))
    assert load_leave.run()
    after = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    assert before == after


def test_hrms_remaining_balances_replace_fixed_columns(legacy_fixture, query):
    """Founder decision 2026-10-26: prod leave_balances.*_used is 0 in every row; the HRMS screens
    read employee_leave_balances. Its remaining days become the opening balance of the current
    leave year, per real leave type, and replace what an earlier run made from leave_balances."""
    from datetime import date

    run_base_and("loaders.load_leave")
    assert _count(query, "core.leave_allocation", FOUNDER_TENANT) == 9
    rows = [
        {"id": 1, "employee_id": "E001", "leave_type": "Sick Leave", "leave_type_id": 2, "remaining_days": 1.5},
        {"id": 2, "employee_id": "E001", "leave_type": "Work From Home", "leave_type_id": 3, "remaining_days": -2},
        {"id": 3, "employee_id": "E002", "leave_type": "Annual Leave", "leave_type_id": 99, "remaining_days": 4},
        {"id": 4, "employee_id": "E002", "leave_type": "Gone Leave", "leave_type_id": 98, "remaining_days": 1},
        {"id": 5, "employee_id": "E999", "leave_type": "Sick Leave", "leave_type_id": 2, "remaining_days": 1},
        {"id": 6, "employee_id": "E001", "leave_type": "Sick Leave", "leave_type_id": 2, "remaining_days": 9},
    ]
    (legacy_fixture / "stg_hrms_employee_leave_balances.json").write_text(json.dumps(rows), encoding="utf-8")
    run_base_and("loaders.load_leave")

    today = date.today()
    y = today.year if today.month >= 4 else today.year - 1
    label = f"{y}-{(y + 1) % 100:02d}"
    remaining = query(
        "SELECT e.employee_number, t.code, a.entitlement_days - coalesce((SELECT sum(c.consumed_days) "
        "FROM core.leave_consumption c WHERE c.allocation_id = a.id), 0) FROM core.leave_allocation a "
        "JOIN core.employee e ON e.id = a.employee_id JOIN core.leave_type t ON t.id = a.leave_type_id "
        "WHERE a.tenant_id = %s AND a.leave_year = %s ORDER BY 1, 2", (FOUNDER_TENANT, label))
    assert remaining == [("E001", "SICK", Decimal("1.50")), ("E001", "WORK_FROM_HOME", Decimal("-2.00")),
                         ("E002", "ANNUAL", Decimal("4.00"))]  # a deleted type found by its name
    # The fixed-column allocations go, except the four with used days recorded against them.
    assert _count(query, "core.leave_allocation", FOUNDER_TENANT) == 3 + 4
    reasons = {i["legacy_id"]: i["reason"] for i in _quarantine(legacy_fixture)
               if i["legacy_table"] == "employee_leave_balances"}
    assert reasons == {4: "LEAVE_TYPE_NOT_FOUND", 5: "EMPLOYEE_NOT_FOUND", 6: "DUPLICATE_BALANCE_TYPE"}
