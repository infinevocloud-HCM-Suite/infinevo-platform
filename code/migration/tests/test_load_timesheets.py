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
                     ("EMPLOYEE_NOT_FOUND", "assignments"), ("MERGED_INTO_WEEK", "timesheets"),
                     ("WEEK_NOT_MONDAY", "timesheets"),
                     ("TASK_NOT_LOADED", "task_entry"), ("HOURS_OUT_OF_RANGE", "day_entry"),
                     ("DUPLICATE_DAY", "day_entry"), ("ZERO_HOURS_NOT_LOADED", "day_entry")]:
        assert expected in reasons, expected
    # 11 refused or folded rows + 1 zero-hour day; MANAGER_NOT_FOUND is a note on a loaded row. The folded
    # week's project line joins week 1's Apollo line, so no project line is refused any more.
    assert ("TIMESHEET_NOT_LOADED", "project_entry") not in reasons
    assert query("SELECT records_processed, records_skipped FROM core.migration_ledger "
                 "WHERE step_name = 'load_timesheets'") == [(17, 12)]


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


def test_repeated_task_rows_and_a_folded_week_keep_every_entry(legacy_fixture, query):
    """Founder decision 2026-10-26, as in legacy HRMS: a task on two rows keeps both rows (V185), and a
    second live timesheet of a week folds into the week's timesheet with all its rows."""
    def add(name, extra):
        p = legacy_fixture / name
        p.write_text(json.dumps(json.loads(p.read_text(encoding="utf-8")) + extra), encoding="utf-8")

    add("stg_hrms_task_entry.json", [
        {"id": 5, "task_id": 1, "task_name": "Design", "project_entry_id": 3},  # on the folded DRAFT week
        {"id": 6, "task_id": 1, "task_name": "Design", "project_entry_id": 1},  # Design again on week 1
    ])
    add("stg_hrms_day_entry.json", [
        {"id": 7, "date": "2024-02-08", "day_name": "THURSDAY", "hours": 2.0, "description": "draft row",
         "task_entry_id": 5},
        {"id": 8, "date": "2024-02-05", "day_name": "MONDAY", "hours": 1.5, "description": "afternoon",
         "task_entry_id": 6},
    ])
    run_base_and("loaders.load_timesheets")

    rows = query("SELECT t.status, k.title, d.work_date::text, d.hours FROM hrms.timesheet_day_entry d "
                 "JOIN hrms.timesheet_task_entry te ON te.id = d.task_entry_id "
                 "JOIN hrms.timesheet_project_entry pe ON pe.id = te.project_entry_id "
                 "JOIN hrms.timesheet t ON t.id = pe.timesheet_id JOIN hrms.task k ON k.id = te.task_id "
                 "JOIN core.employee e ON e.id = t.employee_id WHERE e.employee_number = 'E002' ORDER BY 3, 4")
    assert rows == [("APPROVED", "Design", "2024-02-05", Decimal("1.50")),
                    ("APPROVED", "Design", "2024-02-05", Decimal("8.00")),
                    ("APPROVED", "Design", "2024-02-06", Decimal("7.50")),
                    ("APPROVED", "Design", "2024-02-08", Decimal("2.00"))]
    assert query("SELECT count(*) FROM hrms.timesheet_task_entry te JOIN hrms.task k ON k.id = te.task_id "
                 "WHERE k.title = 'Design'") == [(3,)]
    assert query("SELECT count(*) FROM hrms.timesheet WHERE week_start_date = '2024-02-05'") == [(2,)]


def test_folded_week_line_takes_the_kept_status_and_a_rerun_can_switch_the_kept_sheet(legacy_fixture, query):
    """Review of 788e7a30: a folded week's line on a project the kept sheet lacks must not stay DRAFT inside
    an APPROVED sheet; and a re-run that keeps a different sheet of the week must not meet the week index."""
    def edit(name, fn):
        p = legacy_fixture / name
        p.write_text(json.dumps(fn(json.loads(p.read_text(encoding="utf-8")))), encoding="utf-8")

    edit("stg_hrms_tasks.json", lambda rows: rows + [
        {"id": 5, "title": "Deploy", "description": None, "project_id": 2, "assignee_id": None,
         "due_date": None, "priority": "LOW", "status": "TODO", "estimated_hours": None}])
    # Zeus leaves E002's first sheet, and appears only on the folded DRAFT copy of the week.
    edit("stg_hrms_project_entry.json", lambda rows: [{**r, "timesheet_id": 2} if r["id"] == 4 else r for r in rows]
         + [{"id": 5, "project_id": 2, "project_name": "Zeus", "rejection_reason": "old", "status": "DRAFT",
             "timesheet_id": 3}])
    edit("stg_hrms_task_entry.json", lambda rows: rows + [
        {"id": 7, "task_id": 5, "task_name": "Deploy", "project_entry_id": 5}])
    edit("stg_hrms_day_entry.json", lambda rows: rows + [
        {"id": 9, "date": "2024-02-09", "day_name": "FRIDAY", "hours": 3.0, "description": "zeus", "task_entry_id": 7}])

    def e002():
        lines = query("SELECT p.name, pe.status, pe.rejection_reason FROM hrms.timesheet_project_entry pe "
                      "JOIN hrms.project p ON p.id = pe.project_id JOIN hrms.timesheet t ON t.id = pe.timesheet_id "
                      "JOIN core.employee e ON e.id = t.employee_id WHERE e.employee_number = 'E002' ORDER BY 1")
        hours = query("SELECT sum(d.hours) FROM hrms.timesheet_day_entry d JOIN hrms.timesheet_task_entry te "
                      "ON te.id = d.task_entry_id JOIN hrms.timesheet_project_entry pe ON pe.id = te.project_entry_id "
                      "JOIN hrms.timesheet t ON t.id = pe.timesheet_id JOIN core.employee e ON e.id = t.employee_id "
                      "WHERE e.employee_number = 'E002'")[0][0]
        sheets = query("SELECT t.status FROM hrms.timesheet t JOIN core.employee e ON e.id = t.employee_id "
                       "WHERE e.employee_number = 'E002'")
        return lines, hours, sheets

    run_base_and("loaders.load_timesheets")
    lines, hours, sheets = e002()
    assert lines == [("Apollo", "APPROVED", None), ("Zeus", "APPROVED", None)]  # not DRAFT, old reason dropped
    assert (hours, sheets) == (Decimal("18.50"), [("APPROVED",)])

    # Next extract: the DRAFT copy was approved and the first sheet reopened. The kept sheet switches.
    edit("stg_hrms_timesheets.json", lambda rows: [
        {**r, "status": {1: "SUBMITTED", 3: "APPROVED"}.get(r["id"], r["status"])} for r in rows])
    query("DELETE FROM core.migration_ledger WHERE step_name = 'load_timesheets'")
    run_base_and("loaders.load_timesheets")
    lines, hours, sheets = e002()
    assert (hours, sheets) == (Decimal("18.50"), [("APPROVED",)])  # same work, one sheet, no index clash
    assert {l[0] for l in lines} == {"Apollo", "Zeus"}


def test_kept_sheet_line_keeps_its_id_when_a_folded_line_comes_first(legacy_fixture, query):
    """Prod re-run 2026-10-26 met uk_tpe_tenant_timesheet_project: a folded week's line read before the kept
    sheet's own line for the same project took its place under another id. The kept line must keep its id."""
    from common.ids import det_id

    def edit(name, fn):
        p = legacy_fixture / name
        p.write_text(json.dumps(fn(json.loads(p.read_text(encoding="utf-8")))), encoding="utf-8")

    # Week 1's own Apollo line gets a higher id than the folded DRAFT copy's Apollo line (3).
    edit("stg_hrms_project_entry.json", lambda rows: [{**r, "id": 10} if r["id"] == 1 else r for r in rows])
    edit("stg_hrms_task_entry.json", lambda rows: [
        {**r, "project_entry_id": 10} if r["project_entry_id"] == 1 else r for r in rows])
    run_base_and("loaders.load_timesheets")
    ids = query("SELECT pe.id::text FROM hrms.timesheet_project_entry pe JOIN hrms.project p ON p.id = pe.project_id "
                "JOIN hrms.timesheet t ON t.id = pe.timesheet_id JOIN core.employee e ON e.id = t.employee_id "
                "WHERE e.employee_number = 'E002' AND p.name = 'Apollo'")
    assert ids == [(det_id(FOUNDER_TENANT, "project_entry", 10),)]
