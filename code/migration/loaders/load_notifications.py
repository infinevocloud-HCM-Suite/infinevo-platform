"""3m — in-app notifications (W-68 Task 3, mapping §3.10; carried under "carry all history").

  HRMS notifications, timesheet_notifications -> core.notification (channel IN_APP)

HRMS has no organisation column, so every row belongs to the founder tenant (W-67 R5).

Recipient. Legacy recipientId is the user's emp_id code (NotificationController.java:30
reads it from user.getEmpId()) or the literal "admin" for the admin inbox
(NotificationService.java getUnreadNotificationsForAdmin). The target addresses an in-app
notification to an employee (recipient_employee_id; the inbox query is
NotificationRepository.findByTenantIdAndRecipientEmployeeIdAndChannel), not to a user
account, so the code is resolved through the id-map to the employee. "admin" has no
employee: ADMIN_RECIPIENT. An unknown code: EMPLOYEE_NOT_MIGRATED.

Event. W-68 names event 'LEGACY'. The column has no CHECK, but the app reads it into the
closed enum NotificationEvent, which has no LEGACY - a LEGACY row would fail every inbox
read for its recipient. The legacy type is mapped onto the nearest real event instead
(SUBMISSION -> APPROVAL_PENDING, APPROVAL/REJECTION -> APPROVAL_DECIDED, REMINDER ->
TIMESHEET_REMINDER, ALERT -> TIMESHEET_ESCALATION); another type -> EVENT_UNMAPPED.

Status. W-68 names 'DELIVERED'; the CHECK allows QUEUED, SENDING, SENT, FAILED. An in-app
notification is SENT once stored (NotificationServiceImpl, "delivered by being stored").
queued_at = legacy createdAt. Legacy keeps a read flag, not a read time: a read row gets
read_at = createdAt, an unread one NULL.
"""
from __future__ import annotations

import logging
import sys

from common import db, staging
from common.ids import det_id
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text, to_timestamp, truthy
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_notifications"
SOURCES = (("notifications", "stg_hrms_notifications.json"),
           ("timesheet_notifications", "stg_hrms_timesheet_notifications.json"))
# The id-map's employee entries come from these files.
INPUTS = [f for _, f in SOURCES] + ["stg_merged_employees.json", "stg_hrms_personal.json"]
EVENTS = {"SUBMISSION": "APPROVAL_PENDING", "APPROVAL": "APPROVAL_DECIDED", "REJECTION": "APPROVAL_DECIDED",
          "REMINDER": "TIMESHEET_REMINDER", "ALERT": "TIMESHEET_ESCALATION"}
log = logging.getLogger(STEP)


def run(conn=None) -> bool:
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    founder = tenants.founder

    def work(cur, t: Tenant) -> StepResult:
        if founder is None or t.tenant_id != founder.tenant_id:
            return StepResult()
        tid = t.tenant_id
        rows, skipped = [], 0
        for table, fname in SOURCES:
            for r in staging.load(fname):
                lid = r.get("id")
                ref = text(r.get("recipient_id"))
                emp = None if ref is None else idmap.get("hrms.emp_code", ref) or idmap.get("hrms.employee", ref)
                event = EVENTS.get((text(r.get("type")) or "").upper())
                created = to_timestamp(r.get("created_at"))
                body = text(r.get("message"))
                problem = (
                    "ADMIN_RECIPIENT" if (ref or "").lower() == "admin" else
                    "EMPLOYEE_NOT_MIGRATED" if emp is None else
                    "EMPLOYEE_IN_OTHER_TENANT" if idmap.get("employee.tenant", emp) != tid else
                    "EVENT_UNMAPPED" if event is None else
                    "NO_BODY" if body is None else
                    "NO_CREATED_AT" if created is None else None
                )
                if problem:
                    q.add(problem, table, lid, tid,
                          type=text(r.get("type"), 32) if problem == "EVENT_UNMAPPED" else None)
                    skipped += 1
                    continue
                nid = det_id(tid, table, lid)
                rows.append({
                    "id": nid, "tenant_id": tid, "recipient_employee_id": emp, "recipient_email": None,
                    "event": event, "channel": "IN_APP", "subject": None, "body": body, "status": "SENT",
                    "template_id": None, "queued_at": created,
                    "read_at": created if truthy(r.get("is_read"), False) else None,
                    "subject_ref": None, "created_at": created,
                    "created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER,
                })
                idmap.put(f"hrms.{table}", lid, nid)
        db.upsert(cur, "core.notification", rows)
        return StepResult(processed=len(rows), skipped=skipped)

    own = conn is None
    conn = conn or db.connect()
    try:
        summary = run_per_tenant(conn, STEP, tenants.all(), staging.checksum(INPUTS), work)
    finally:
        if own:
            conn.close()
    idmap.save()
    q.write()
    return summary.ok


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    sys.exit(0 if run() else 1)
