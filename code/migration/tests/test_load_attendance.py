"""3k load_attendance against a real migrated Postgres (W-68 Task 4, W-67 R3)."""
import json
from datetime import datetime, timedelta, timezone
from decimal import Decimal

from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT
from tests.pipeline import run_base_and

TABLES = ["core.attendance", "hrms.clock_session", "core.overtime_request", "core.approval_instance",
          "core.approval_step"]
IST = timezone(timedelta(hours=5, minutes=30))


def test_attendance_sessions_overtime(legacy_fixture, query):
    run_base_and("loaders.load_attendance")
    att = query("SELECT e.employee_number, a.attendance_date::text, a.status, a.source, a.remarks "
                "FROM core.attendance a JOIN core.employee e ON e.id = a.employee_id ORDER BY 1, 2")
    assert att == [("E002", "2024-03-04", "PRESENT", "CLOCK", "MIGRATED"),
                   ("E002", "2024-03-05", "HALF_DAY", "CLOCK", "MIGRATED"),
                   ("E002", "2024-03-06", "PRESENT", "CLOCK", "MIGRATED"),
                   ("E003", "2024-03-04", "ABSENT", "CLOCK", "MIGRATED")]

    sessions = query("SELECT s.attendance_date::text, s.clock_in_at, s.clock_out_at, s.voided_at, s.void_reason "
                     "FROM hrms.clock_session s ORDER BY s.clock_in_at")
    assert len(sessions) == 5
    # naive legacy times are IST
    assert sessions[0][1] == datetime(2024, 3, 4, 9, 0, tzinfo=IST)
    # the duplicate day's session is kept on that day
    assert ("2024-03-04", datetime(2024, 3, 4, 19, 0, tzinfo=IST)) in [(s[0], s[1]) for s in sessions]
    open_rows = [s for s in sessions if s[2] is None]
    # 03-05 open session voided at the next clock-in (as ClockServiceImpl.clockIn); the last one stays open
    assert [(s[0], s[3], s[4]) for s in open_rows] == [
        ("2024-03-05", datetime(2024, 3, 6, 9, 0, tzinfo=IST), "NOT_CLOCKED_OUT"), ("2024-03-06", None, None)]

    ots = {r[0]: r[1:] for r in query(
        "SELECT e.employee_number, o.overtime_date::text, o.hours, o.status, o.source, o.pay_input_id, o.remarks "
        "FROM core.overtime_request o JOIN core.employee e ON e.id = o.employee_id")}
    assert ots["E002"] == ("2024-03-04", Decimal("2.50"), "APPROVED", "REQUEST", None, "Release - note 1")
    assert ots["E003"][2] == "PENDING" and ots["E004"][2] == "REJECTED"
    steps = query("SELECT e.employee_number, i.status, s.step_index, s.approver_kind, m.employee_number, s.decision "
                  "FROM core.approval_instance i JOIN core.approval_step s ON s.instance_id = i.id "
                  "JOIN core.employee e ON e.id = i.subject_employee_id "
                  "LEFT JOIN core.employee m ON m.id = s.assignee_employee_id "
                  "WHERE i.subject_table = 'core.overtime_request' ORDER BY 1, 3")
    assert steps == [("E002", "APPROVED", 0, "REPORTING_MANAGER", "E001", "APPROVED"),
                     ("E002", "APPROVED", 1, "ROLE", None, "APPROVED"),
                     ("E003", "PENDING", 0, "REPORTING_MANAGER", "E001", None),
                     ("E003", "PENDING", 1, "ROLE", None, None),
                     ("E004", "REJECTED", 0, "REPORTING_MANAGER", "E001", "REJECTED"),
                     ("E004", "REJECTED", 1, "ROLE", None, "REJECTED")]  # closed with the outcome
    for t in TABLES:
        assert query(f"SELECT count(*) FROM {t} WHERE tenant_id = %s", (CUSTOMER_TENANT,))[0][0] == 0, t

    q = json.loads((legacy_fixture / "stg_quarantined_load_attendance.json").read_text())
    reasons = {(i["reason"], i["legacy_table"]) for i in q}
    for expected in [("DUPLICATE_ATTENDANCE_DAY", "attendance"), ("EMPLOYEE_NOT_FOUND", "attendance"),
                     ("STATUS_UNMAPPED", "attendance"), ("BAD_SESSION_TIMES", "clock_sessions"),
                     ("ATTENDANCE_NOT_LOADED", "clock_sessions"), ("BAD_OVERTIME_HOURS", "overtime_requests"),
                     ("EMPLOYEE_NOT_FOUND", "overtime_requests")]:
        assert expected in reasons, expected
    assert query("SELECT records_skipped FROM core.migration_ledger WHERE step_name = 'load_attendance'") == [(len(q),)]


def test_second_run_changes_nothing(legacy_fixture, query):
    run_base_and("loaders.load_attendance")
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    run_base_and("loaders.load_attendance")
    after = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    assert before == after
    assert query("SELECT tenant_id::text, status, count(*) FROM core.migration_ledger "
                 "WHERE step_name = 'load_attendance' GROUP BY 1, 2") == [(FOUNDER_TENANT, "DONE", 1)]


def test_forced_rerun_updates_nothing(legacy_fixture, query, monkeypatch):
    run_base_and("loaders.load_attendance")
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    from common import ledger
    from loaders import load_attendance

    real = ledger.run_per_tenant
    monkeypatch.setattr(load_attendance, "run_per_tenant", lambda *a, **k: real(*a, **{**k, "force": True}))
    assert load_attendance.run()
    assert before == {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
