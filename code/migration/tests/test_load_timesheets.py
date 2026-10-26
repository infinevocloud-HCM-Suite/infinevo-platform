"""3j load_timesheets against a real migrated Postgres (W-68 Task 4, W-67 R3)."""
import json
from decimal import Decimal

from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT
from tests.pipeline import run_base_and

TABLES = ["hrms.project", "hrms.task", "hrms.assignment", "hrms.timesheet", "hrms.timesheet_project_entry",
          "hrms.timesheet_task_entry", "hrms.timesheet_day_entry"]


def test_loaded_column_for_column(legacy_fixture, query):
    run_base_and("loaders.load_timesheets")
    counts = {t: query(f"SELECT count(*) FROM {t} WHERE tenant_id = %s", (FOUNDER_TENANT,))[0][0] for t in TABLES}
    assert counts == {"hrms.project": 2, "hrms.task": 2, "hrms.assignment": 2, "hrms.timesheet": 2,
                      "hrms.timesheet_project_entry": 3, "hrms.timesheet_task_entry": 3,
                      "hrms.timesheet_day_entry": 3}
    projects = {r[0]: r[1:] for r in query(
        "SELECT p.name, p.priority, p.status, p.progress, p.budget, m.employee_number, p.end_date::text "
        "FROM hrms.project p LEFT JOIN core.employee m ON m.id = p.manager_employee_id")}
    assert projects["Apollo"] == ("HIGH", "STARTED", 40, Decimal("150000.7500"), "E001", None)
    assert projects["Zeus"] == ("MEDIUM", "COMPLETED", 0, None, None, "2024-06-30")  # manager E999 not found
    tasks = {r[0]: r[1:] for r in query(
        "SELECT t.title, t.status, t.priority, a.employee_number, t.estimated_hours FROM hrms.task t "
        "LEFT JOIN core.employee a ON a.id = t.assignee_employee_id")}
    assert tasks == {"Design": ("IN_PROGRESS", "LOW", "E002", 10), "Build": ("TODO", "MEDIUM", None, None)}
    sheets = query("SELECT e.employee_number, t.week_start_date::text, t.week_end_date::text, t.status, "
                   "t.submitted_at IS NOT NULL FROM hrms.timesheet t JOIN core.employee e ON e.id = t.employee_id "
                   "ORDER BY 1")
    assert sheets == [("E002", "2024-02-05", "2024-02-11", "APPROVED", True),
                      ("E003", "2024-02-05", "2024-02-11", "SUBMITTED", True)]
    assert query("SELECT status, rejection_reason FROM hrms.timesheet_project_entry WHERE status = 'REJECTED'") == \
        [("REJECTED", "fix hours")]
    days = query("SELECT work_date::text, hours FROM hrms.timesheet_day_entry ORDER BY hours")
    assert days == [("2024-02-05", Decimal("4.25")), ("2024-02-06", Decimal("7.50")), ("2024-02-05", Decimal("8.00"))]
    # HRMS data never reaches a customer tenant
    for t in TABLES:
        assert query(f"SELECT count(*) FROM {t} WHERE tenant_id = %s", (CUSTOMER_TENANT,))[0][0] == 0, t

    q = json.loads((legacy_fixture / "stg_quarantined_load_timesheets.json").read_text())
    reasons = {(i["reason"], i["legacy_table"]) for i in q}
    for expected in [("DUPLICATE_PROJECT_NAME", "projects"), ("PRIORITY_UNMAPPED", "projects"),
                     ("MANAGER_NOT_FOUND", "projects"), ("PROJECT_NOT_LOADED", "tasks"),
                     ("STATUS_UNMAPPED", "tasks"), ("DUPLICATE_ASSIGNMENT", "assignments"),
                     ("EMPLOYEE_NOT_FOUND", "assignments"), ("DUPLICATE_WEEK", "timesheets"),
                     ("WEEK_NOT_MONDAY", "timesheets"), ("TIMESHEET_NOT_LOADED", "project_entry"),
                     ("TASK_NOT_LOADED", "task_entry"), ("HOURS_OUT_OF_RANGE", "day_entry"),
                     ("DUPLICATE_DAY", "day_entry"), ("ZERO_HOURS_NOT_LOADED", "day_entry")]:
        assert expected in reasons, expected
    # 12 refused rows + 1 zero-hour day; MANAGER_NOT_FOUND is a note on a loaded row
    assert query("SELECT records_processed, records_skipped FROM core.migration_ledger "
                 "WHERE step_name = 'load_timesheets'") == [(17, 13)]


def test_second_run_changes_nothing(legacy_fixture, query):
    run_base_and("loaders.load_timesheets")
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    run_base_and("loaders.load_timesheets")
    after = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    assert before == after
    assert query("SELECT tenant_id::text, status, count(*) FROM core.migration_ledger "
                 "WHERE step_name = 'load_timesheets' GROUP BY 1, 2") == [(FOUNDER_TENANT, "DONE", 1)]


def test_forced_rerun_updates_nothing(legacy_fixture, query, monkeypatch):
    run_base_and("loaders.load_timesheets")
    before = {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}
    from common import ledger
    from loaders import load_timesheets

    real = ledger.run_per_tenant
    monkeypatch.setattr(load_timesheets, "run_per_tenant", lambda *a, **k: real(*a, **{**k, "force": True}))
    assert load_timesheets.run()
    assert before == {t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in TABLES}


def test_blank_priority_is_medium(legacy_fixture, query):
    """Founder decision 2026-10-26: two prod projects had no priority and every timesheet line
    under them was dropped. A blank priority is MEDIUM; an unknown one is still refused."""
    p = legacy_fixture / "stg_hrms_projects.json"
    projects = json.loads(p.read_text(encoding="utf-8"))
    projects.append({**projects[0], "id": 90, "name": "No Priority", "priority": ""})
    p.write_text(json.dumps(projects), encoding="utf-8")
    run_base_and("loaders.load_timesheets")
    assert query("SELECT priority FROM hrms.project WHERE name = 'No Priority'") == [("MEDIUM",)]
    dropped = json.loads((legacy_fixture / "stg_quarantined_load_timesheets.json").read_text())
    assert [i["reason"] for i in dropped if i["legacy_table"] == "projects" and i.get("value") == "URGENT"] ==         ["PRIORITY_UNMAPPED"]
