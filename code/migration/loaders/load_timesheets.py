"""3j — projects and timesheets, HRMS -> founder tenant (W-68 Task 3, W-67 R3, mapping §3.9, X-11).

  projects     -> hrms.project                  tasks       -> hrms.task
  assignments  -> hrms.assignment               timesheets  -> hrms.timesheet
  project_entry -> hrms.timesheet_project_entry task_entry  -> hrms.timesheet_task_entry
  day_entry    -> hrms.timesheet_day_entry

Column for column (the target was modelled on these tables). HRMS has no organisation column,
so everything belongs to the founder tenant (W-67 R5). Migrated timesheets are history: status
as it was, no pay input is raised from them (R3).

Nothing is reshaped to fit a target rule: a week that does not start on a Monday, a second
live timesheet for the same week, a second project of the same name, a value outside the
target's enum all go to quarantine with a reason. Zero-hour day entries (the legacy grid saves
every day of the week) carry no work and are counted, not loaded.
"""
from __future__ import annotations

import logging
import sys
from datetime import datetime, timedelta, timezone
from decimal import Decimal

from common import db, staging
from common.ids import det_id
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_date, to_timestamp
from common.money import to_decimal
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_timesheets"
TABLES = ["projects", "tasks", "assignments", "timesheets", "project_entry", "task_entry", "day_entry"]
INPUTS = [f"stg_hrms_{t}.json" for t in TABLES] + ["stg_merged_employees.json"]
log = logging.getLogger(STEP)

# Target enums: code/backend/hrms/.../project/{Priority,ProjectStatus,TaskStatus}.java,
# hrms.timesheet / timesheet_project_entry CHECK constraints.
PRIORITY = {"LOW", "MEDIUM", "HIGH"}
PROJECT_STATUS = {"STARTED", "COMPLETED"}
TASK_STATUS = {"TODO", "IN_PROGRESS", "IN_REVIEW", "COMPLETED"}
SHEET_STATUS = {"DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "CANCELLED"}
BUDGET_PLACES = Decimal("0.0001")  # hrms.project.budget NUMERIC(19,4)


def priority_of(value) -> str | None:
    """A blank priority is MEDIUM (founder decision 2026-10-26: two prod projects had none, and
    every timesheet line under them was dropped with them); any other unknown value stays None."""
    return "MEDIUM" if not text(value) else enum(value, PRIORITY)


def enum(value, allowed: set[str]) -> str | None:
    """'In Progress' -> 'IN_PROGRESS' when the target allows it, else None."""
    s = (text(value) or "").upper().replace(" ", "_").replace("-", "_")
    return s if s in allowed else None


def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    run_start = datetime.now(timezone.utc)
    audit = {"created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}
    src = {t: sorted(staging.load(f"stg_hrms_{t}.json"), key=lambda r: int(r.get("id") or 0)) for t in TABLES}
    log.info("not migrated (W-67 R3, older twin): stg_hrms_timesheet.json rows=%d",
             len(staging.load("stg_hrms_timesheet.json")))

    def work(cur, t: Tenant) -> StepResult:
        tid = t.tenant_id
        cur.execute("SELECT id FROM core.employee WHERE tenant_id = %s", (tid,))
        loaded = {str(r[0]) for r in cur.fetchall()}
        skipped = 0

        def emp(ref) -> str | None:
            ref = text(ref)
            if not ref:
                return None
            eid = idmap.get("hrms.emp_code", ref) or idmap.get("hrms.employee", ref)
            return eid if eid in loaded else None

        def drop(reason, table, legacy_id, **kw):
            nonlocal skipped
            q.add(reason, table, legacy_id, tid, **kw)
            skipped += 1

        # Projects (name unique per tenant, case-insensitive, among live projects)
        projects, project_ids, names = [], {}, set()
        for p in src["projects"]:
            pid = p.get("id")
            name = text(p.get("name"), 128)
            priority, status = priority_of(p.get("priority")), enum(p.get("status"), PROJECT_STATUS)
            if not name:
                drop("NO_NAME", "projects", pid); continue
            if name.lower() in names:
                drop("DUPLICATE_PROJECT_NAME", "projects", pid); continue
            if priority is None:
                drop("PRIORITY_UNMAPPED", "projects", pid, value=text(p.get("priority"))); continue
            if status is None:
                drop("STATUS_UNMAPPED", "projects", pid, value=text(p.get("status"))); continue
            progress = p.get("progress")
            progress = int(progress) if progress is not None else 0
            if not 0 <= progress <= 100:
                drop("PROGRESS_OUT_OF_RANGE", "projects", pid); continue
            manager = emp(p.get("manager_id"))
            if text(p.get("manager_id")) and manager is None:
                q.add("MANAGER_NOT_FOUND", "projects", pid, tid, note="loaded without a manager")
            names.add(name.lower())
            project_ids[str(pid)] = det_id(tid, "projects", pid)
            projects.append({"id": project_ids[str(pid)], "tenant_id": tid, "name": name,
                             "category": text(p.get("category"), 64), "description": text(p.get("description")),
                             "start_date": to_date(p.get("start_date")), "end_date": to_date(p.get("end_date")),
                             "priority": priority, "status": status, "progress": progress,
                             "budget": to_decimal(p.get("budget"), BUDGET_PLACES), "manager_employee_id": manager,
                             "is_deleted": False, **audit})
        db.upsert(cur, "hrms.project", projects)

        # Tasks
        tasks, task_ids = [], {}
        for k in src["tasks"]:
            kid = k.get("id")
            project = project_ids.get(str(k.get("project_id")))
            title = text(k.get("title"), 128)
            priority, status = priority_of(k.get("priority")), enum(k.get("status"), TASK_STATUS)
            if project is None:
                drop("PROJECT_NOT_LOADED", "tasks", kid); continue
            if not title:
                drop("NO_TITLE", "tasks", kid); continue
            if priority is None:
                drop("PRIORITY_UNMAPPED", "tasks", kid, value=text(k.get("priority"))); continue
            if status is None:
                drop("STATUS_UNMAPPED", "tasks", kid, value=text(k.get("status"))); continue
            assignee = emp(k.get("assignee_id"))
            if text(k.get("assignee_id")) and assignee is None:
                q.add("ASSIGNEE_NOT_FOUND", "tasks", kid, tid, note="loaded unassigned")
            est = k.get("estimated_hours")
            task_ids[str(kid)] = (det_id(tid, "tasks", kid), project)
            tasks.append({"id": task_ids[str(kid)][0], "tenant_id": tid, "project_id": project, "title": title,
                          "description": text(k.get("description")), "assignee_employee_id": assignee,
                          "due_date": to_date(k.get("due_date")), "priority": priority, "status": status,
                          "estimated_hours": int(est) if est is not None else None, "is_deleted": False, **audit})
        db.upsert(cur, "hrms.task", tasks)

        # Assignments (legacy records no date: assigned_on keeps the target default, the load date)
        assigns, pairs = [], set()
        for a in src["assignments"]:
            aid = a.get("id")
            project, employee = project_ids.get(str(a.get("project_id"))), emp(a.get("emp_id"))
            if project is None:
                drop("PROJECT_NOT_LOADED", "assignments", aid); continue
            if employee is None:
                drop("EMPLOYEE_NOT_FOUND", "assignments", aid); continue
            if (project, employee) in pairs:
                drop("DUPLICATE_ASSIGNMENT", "assignments", aid); continue
            pairs.add((project, employee))
            assigns.append({"id": det_id(tid, "assignments", aid), "tenant_id": tid, "project_id": project,
                            "employee_id": employee, "is_deleted": False, **audit})
        db.upsert(cur, "hrms.assignment", assigns)

        # Timesheets (one live sheet per employee per week, week Monday..Sunday)
        sheets, sheet_ids, weeks = [], {}, set()
        for s in src["timesheets"]:
            sid = s.get("id")
            employee = emp(s.get("employee_id"))
            start, end = to_date(s.get("week_start_date")), to_date(s.get("week_end_date"))
            status = enum(s.get("status"), SHEET_STATUS)
            if employee is None:
                drop("EMPLOYEE_NOT_FOUND", "timesheets", sid); continue
            if start is None or end is None:
                drop("NO_WEEK", "timesheets", sid); continue
            if start.isoweekday() != 1:
                drop("WEEK_NOT_MONDAY", "timesheets", sid); continue
            if end != start + timedelta(days=6):
                drop("WEEK_NOT_SEVEN_DAYS", "timesheets", sid); continue
            if status is None:
                drop("STATUS_UNMAPPED", "timesheets", sid, value=text(s.get("status"))); continue
            if status != "CANCELLED":
                if (employee, start) in weeks:
                    drop("DUPLICATE_WEEK", "timesheets", sid); continue
                weeks.add((employee, start))
            sheet_ids[str(sid)] = det_id(tid, "timesheets", sid)
            sheets.append({"id": sheet_ids[str(sid)], "tenant_id": tid, "employee_id": employee,
                           "week_start_date": start, "week_end_date": end, "status": status,
                           "submitted_at": to_timestamp(s.get("submitted_at")),
                           "created_at": to_timestamp(s.get("created_at")) or run_start, **audit})
        db.upsert(cur, "hrms.timesheet", sheets)

        # Project lines
        lines, line_ids, line_keys = [], {}, set()
        for e in src["project_entry"]:
            eid = e.get("id")
            sheet, project = sheet_ids.get(str(e.get("timesheet_id"))), project_ids.get(str(e.get("project_id")))
            status = enum(e.get("status"), SHEET_STATUS)
            if sheet is None:
                drop("TIMESHEET_NOT_LOADED", "project_entry", eid); continue
            if project is None:
                drop("PROJECT_NOT_LOADED", "project_entry", eid); continue
            if status is None:
                drop("STATUS_UNMAPPED", "project_entry", eid, value=text(e.get("status"))); continue
            if (sheet, project) in line_keys:
                drop("DUPLICATE_PROJECT_LINE", "project_entry", eid); continue
            line_keys.add((sheet, project))
            line_ids[str(eid)] = (det_id(tid, "project_entry", eid), project)
            lines.append({"id": line_ids[str(eid)][0], "tenant_id": tid, "timesheet_id": sheet, "project_id": project,
                          "status": status, "rejection_reason": text(e.get("rejection_reason"), 1000), **audit})
        db.upsert(cur, "hrms.timesheet_project_entry", lines)

        # Task lines
        task_lines, task_line_ids, task_keys = [], {}, set()
        for e in src["task_entry"]:
            eid = e.get("id")
            line, task = line_ids.get(str(e.get("project_entry_id"))), task_ids.get(str(e.get("task_id")))
            if line is None:
                drop("PROJECT_LINE_NOT_LOADED", "task_entry", eid); continue
            if task is None:
                drop("TASK_NOT_LOADED", "task_entry", eid); continue
            if task[1] != line[1]:
                drop("TASK_NOT_IN_PROJECT", "task_entry", eid); continue
            if (line[0], task[0]) in task_keys:
                drop("DUPLICATE_TASK_LINE", "task_entry", eid); continue
            task_keys.add((line[0], task[0]))
            task_line_ids[str(eid)] = det_id(tid, "task_entry", eid)
            task_lines.append({"id": task_line_ids[str(eid)], "tenant_id": tid, "project_entry_id": line[0],
                               "task_id": task[0], **audit})
        db.upsert(cur, "hrms.timesheet_task_entry", task_lines)

        # Day entries
        days, day_keys, zero = [], set(), 0
        for d in src["day_entry"]:
            did = d.get("id")
            task_line = task_line_ids.get(str(d.get("task_entry_id")))
            work_date = to_date(d.get("date"))
            hours = to_decimal(d.get("hours"))
            if task_line is None:
                drop("TASK_LINE_NOT_LOADED", "day_entry", did); continue
            if work_date is None:
                drop("NO_DATE", "day_entry", did); continue
            if hours is None or hours == 0:
                zero += 1; skipped += 1; continue
            if not Decimal(0) < hours <= Decimal(24):
                drop("HOURS_OUT_OF_RANGE", "day_entry", did); continue
            if (task_line, work_date) in day_keys:
                drop("DUPLICATE_DAY", "day_entry", did); continue
            day_keys.add((task_line, work_date))
            days.append({"id": det_id(tid, "day_entry", did), "tenant_id": tid, "task_entry_id": task_line,
                         "work_date": work_date, "hours": hours, "description": text(d.get("description"), 500),
                         **audit})
        if zero:
            q.add("ZERO_HOURS_NOT_LOADED", "day_entry", None, tid, rows=zero)
        db.upsert(cur, "hrms.timesheet_day_entry", days)

        processed = sum(map(len, (projects, tasks, assigns, sheets, lines, task_lines, days)))
        return StepResult(processed, skipped)

    founder = [tenants.founder] if tenants.founder else []
    if not founder:
        log.warning("no founder tenant in the org map: HRMS projects and timesheets have no tenant")
    own = conn is None
    conn = conn or db.connect()
    try:
        summary = run_per_tenant(conn, STEP, founder, staging.checksum(INPUTS), work)
    finally:
        if own:
            conn.close()
    q.write()
    return summary.ok


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    sys.exit(0 if run() else 1)
