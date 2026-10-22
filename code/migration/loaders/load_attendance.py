"""3k — attendance, clock sessions and overtime, HRMS -> founder tenant (W-68 Task 3, W-67 R3,
mapping §3.8, X-12).

  attendance        -> core.attendance (one row per employee per day)
  clock_sessions    -> hrms.clock_session
  overtime_requests -> core.overtime_request (+ one core.approval_instance with a manager step and
                       an HR step, so a migrated pending request can still be decided in the app)

Source column: the spec asks for source = 'MIGRATED', but neither table allows it
(core.attendance CHECK source IN ('ADMIN','CLOCK'); core.overtime_request CHECK source IN
('ADMIN','REQUEST')). Legacy attendance was only ever written by clocking in, and legacy
overtime only by an employee's request, so attendance is 'CLOCK' with remarks 'MIGRATED' and
overtime is 'REQUEST'.

An open session (no clock-out) followed by a later session of the same employee is voided
NOT_CLOCKED_OUT at that later clock-in, which is what ClockServiceImpl.clockIn does in the app;
the employee's last open session stays open. Migrated overtime is history: no pay input (R3).
"""
from __future__ import annotations

import logging
import sys
from collections import defaultdict
from datetime import datetime, timezone
from decimal import Decimal

from common import db, staging
from common.ids import child_id, det_id
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_timestamp
from common.money import to_decimal
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_attendance"
INPUTS = ["stg_hrms_attendance.json", "stg_hrms_clock_sessions.json", "stg_hrms_overtime_requests.json",
          "stg_merged_employees.json", "stg_hrms_report.json"]
log = logging.getLogger(STEP)

ATTENDANCE_STATUS = {"PRESENT", "HALF_DAY", "ABSENT"}       # legacy AttendanceStatus = target CHECK
OVERTIME_STATUS = {"PENDING", "APPROVED", "REJECTED"}        # legacy OvertimeStatus
SECONDS_PER_HOUR = Decimal(3600)
CLOSED_BY_MIGRATION = "Closed at migration: legacy recorded no decision at this step"


def overtime_status(manager: str, hr: str) -> str:
    """Legacy acted on overtime only when both approved (OvertimeRequestService.java:83-85);
    either rejection ends it."""
    if "REJECTED" in (manager, hr):
        return "REJECTED"
    if manager == hr == "APPROVED":
        return "APPROVED"
    return "PENDING"


def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    run_start = datetime.now(timezone.utc)
    audit = {"created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}
    by_id = lambda name: sorted(staging.load(name), key=lambda r: int(r.get("id") or 0))  # noqa: E731
    attendance, sessions_in = by_id("stg_hrms_attendance.json"), by_id("stg_hrms_clock_sessions.json")
    overtime = by_id("stg_hrms_overtime_requests.json")

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

        # Attendance: one row per employee per day. A second legacy row for the same day is not
        # loaded, but its clock sessions are (a session belongs to the employee and the day).
        days, parent, seen = [], {}, set()
        for a in attendance:
            aid = a.get("id")
            employee = emp(a.get("employee_id"))
            clock_in = to_timestamp(a.get("in_time"))
            status = (text(a.get("status")) or "").upper()
            if employee is None:
                drop("EMPLOYEE_NOT_FOUND", "attendance", aid); continue
            if clock_in is None:
                drop("NO_IN_TIME", "attendance", aid); continue
            # The legacy day is the IST wall-clock date of the first clock-in.
            day = clock_in.date()
            parent[str(aid)] = (employee, day)
            if status not in ATTENDANCE_STATUS:
                drop("STATUS_UNMAPPED", "attendance", aid, value=status); continue
            if (employee, day) in seen:
                drop("DUPLICATE_ATTENDANCE_DAY", "attendance", aid, note="its clock sessions are loaded"); continue
            seen.add((employee, day))
            days.append({"id": det_id(tid, "attendance", aid), "tenant_id": tid, "employee_id": employee,
                         "attendance_date": day, "status": status, "source": "CLOCK", "remarks": "MIGRATED",
                         "created_at": to_timestamp(a.get("created_at")) or run_start, **audit})
        db.upsert(cur, "core.attendance", days, conflict=("tenant_id", "employee_id", "attendance_date"),
                  keep_on_update=("id",))

        # Clock sessions
        per_emp = defaultdict(list)
        for s in sessions_in:
            sid = s.get("id")
            owner = parent.get(str(s.get("attendance_id")))
            clock_in, clock_out = to_timestamp(s.get("in_time")), to_timestamp(s.get("out_time"))
            if owner is None:
                drop("ATTENDANCE_NOT_LOADED", "clock_sessions", sid); continue
            if clock_in is None:
                drop("NO_IN_TIME", "clock_sessions", sid); continue
            if clock_out is not None and clock_out <= clock_in:
                drop("BAD_SESSION_TIMES", "clock_sessions", sid); continue
            per_emp[owner[0]].append((clock_in, sid, owner[1], clock_out))
        sessions = []
        for employee, rows in per_emp.items():
            rows.sort()
            for i, (clock_in, sid, day, clock_out) in enumerate(rows):
                voided = rows[i + 1][0] if clock_out is None and i + 1 < len(rows) else None
                sessions.append({"id": det_id(tid, "clock_sessions", sid), "tenant_id": tid, "employee_id": employee,
                                 "attendance_date": day, "clock_in_at": clock_in, "clock_out_at": clock_out,
                                 "origin": "CLOCK", "voided_at": voided,
                                 "void_reason": "NOT_CLOCKED_OUT" if voided else None, **audit})
        db.upsert(cur, "hrms.clock_session", sessions)

        # Overtime, with its approval history
        cur.execute("SELECT id FROM core.approval_definition WHERE tenant_id = %s AND flow_type = 'OVERTIME' "
                    "AND is_active ORDER BY effective_from DESC LIMIT 1", (tid,))
        row = cur.fetchone()
        flow_def = str(row[0]) if row else None
        cur.execute("SELECT employee_id, manager_id FROM core.reporting_line WHERE tenant_id = %s AND kind = 'PRIMARY' "
                    "AND effective_to IS NULL ORDER BY effective_from", (tid,))
        managers = {str(e): str(m) for e, m in cur.fetchall()}
        ots, insts, steps = [], [], []
        for o in overtime:
            oid = o.get("id")
            employee = emp(o.get("employee_id"))
            start, end = to_timestamp(o.get("start_time")), to_timestamp(o.get("end_time"))
            mgr = (text(o.get("manager_status")) or "PENDING").upper()
            hr = (text(o.get("hr_status")) or "PENDING").upper()
            if employee is None:
                drop("EMPLOYEE_NOT_FOUND", "overtime_requests", oid); continue
            if start is None or end is None:
                drop("NO_TIMES", "overtime_requests", oid); continue
            hours = to_decimal(Decimal(int((end - start).total_seconds())) / SECONDS_PER_HOUR)
            if not Decimal(0) < hours <= Decimal(24):
                drop("BAD_OVERTIME_HOURS", "overtime_requests", oid); continue
            if mgr not in OVERTIME_STATUS or hr not in OVERTIME_STATUS:
                drop("STATUS_UNMAPPED", "overtime_requests", oid, manager=mgr, hr=hr); continue
            status = overtime_status(mgr, hr)
            ot_id = det_id(tid, "overtime_requests", oid)
            created = to_timestamp(o.get("created_at")) or run_start
            mgr_at, hr_at = to_timestamp(o.get("manager_updated_at")), to_timestamp(o.get("hr_updated_at"))
            remarks = " - ".join(x for x in (text(o.get("category")), text(o.get("notes"))) if x)[:255] or None
            ots.append({"id": ot_id, "tenant_id": tid, "employee_id": employee, "overtime_date": start.date(),
                        "hours": hours, "amount": None, "status": status, "source": "REQUEST", "remarks": remarks,
                        "created_at": created, **audit})
            if flow_def is None:
                q.add("NO_APPROVAL_DEFINITION", "overtime_requests", oid, tid, note="loaded without approval history")
                continue
            inst_id = det_id(tid, "approval_instance.core.overtime_request", ot_id)
            completed = max((d for d in (mgr_at, hr_at) if d), default=None) if status != "PENDING" else None
            insts.append({"id": inst_id, "tenant_id": tid, "flow_type": "OVERTIME", "definition_id": flow_def,
                          "subject_table": "core.overtime_request", "subject_id": ot_id,
                          "subject_employee_id": employee, "status": status, "started_at": created,
                          "completed_at": completed, "created_at": created, **audit})
            for i, (kind, assignee, decision, comment, at) in enumerate((
                    ("REPORTING_MANAGER", managers.get(employee), mgr, o.get("manager_comment"), mgr_at),
                    ("ROLE", None, hr, o.get("hr_comment"), hr_at))):
                comment = text(comment, 1000)
                if decision == "PENDING" and status != "PENDING":
                    # The app's queue is every undecided step: close it with the outcome, as the app
                    # closes the open steps of a rejected instance (ApprovalService.decide).
                    decision, comment, at = status, CLOSED_BY_MIGRATION, completed
                decided = decision != "PENDING"
                steps.append({"id": child_id(inst_id, f"step{i}"), "tenant_id": tid, "instance_id": inst_id,
                              "step_index": i, "approver_kind": kind, "assignee_employee_id": assignee,
                              "decision": decision if decided else None, "comment": comment,
                              "decided_at": at if decided else None, "created_at": created, **audit})
        db.upsert(cur, "core.overtime_request", ots)
        db.upsert(cur, "core.approval_instance", insts)
        db.upsert(cur, "core.approval_step", steps)
        return StepResult(len(days) + len(sessions) + len(ots) + len(insts) + len(steps), skipped)

    founder = [tenants.founder] if tenants.founder else []
    if not founder:
        log.warning("no founder tenant in the org map: HRMS attendance has no tenant")
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
