"""
Notifications Migration Loader (W-68 Task 3m / Ticket #90)
Loads legacy notifications into target PostgreSQL:
  - notifications (HRMS) -> core.notification
  - timesheet_notifications (HRMS) -> core.notification

Rules and Architectural Constraints:
  - Spec W-68 Task 3m: event = 'LEGACY', channel = 'IN_APP', status = 'SENT', read_at
    (status 'SENT' satisfies constraint notification_status_check and represents delivered in-app notifications).
  - Deterministic UUIDs via uuid5(NAMESPACE, f"{tenant_id}:{table}:{legacy_id}")
  - RLS tenant isolation respected by setting app.current_tenant_id per tenant
  - Full idempotency with ON CONFLICT (id) DO UPDATE
  - Unresolvable recipient quarantined with reason in staging/stg_quarantined_notifications.json
  - Execution tracked in core.migration_ledger with step_name = 'load_notifications'
  - Restartability: if ledger has step_name = 'load_notifications' with status = 'DONE' and matching checksum, skips.
"""

import os
import sys
import json
import uuid
import hashlib
import logging
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional, Tuple
import psycopg2
from psycopg2.extras import execute_batch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("load_notifications")

NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
FOUNDER_TENANT_ID = "a0000000-0000-0000-0000-000000000001"


def det_uuid(key: str) -> str:
    """Deterministic UUIDv5 namespace mapping."""
    return str(uuid.uuid5(NAMESPACE, key))


def parse_timestamp(val: Any) -> Optional[datetime]:
    """Parse legacy datetime string or ISO format into timezone-aware UTC datetime."""
    if not val:
        return None
    s = str(val).strip()
    try:
        # Handles "YYYY-MM-DD HH:MM:SS" and ISO format
        dt = datetime.fromisoformat(s)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt
    except Exception:
        pass

    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d"):
        try:
            dt = datetime.strptime(s, fmt)
            return dt.replace(tzinfo=timezone.utc)
        except ValueError:
            continue
    return None


def compute_file_checksum(filepaths: List[str]) -> str:
    """Compute combined SHA-256 checksum over input staging files for ledger verification."""
    hasher = hashlib.sha256()
    for fp in sorted(filepaths):
        if os.path.exists(fp):
            with open(fp, "rb") as f:
                while chunk := f.read(65536):
                    hasher.update(chunk)
        else:
            hasher.update(b"MISSING")
    return hasher.hexdigest()


def get_conn():
    """Target PostgreSQL connection."""
    return psycopg2.connect(
        host=os.environ.get("TARGET_DB_HOST", "localhost"),
        port=int(os.environ.get("TARGET_DB_PORT", "5432")),
        dbname=os.environ.get("TARGET_DB_NAME", "infinevo"),
        user=os.environ.get("TARGET_DB_USER", "migration_user"),
        password=os.environ.get("TARGET_DB_PASSWORD", "local_migration_pw"),
    )


def build_employee_lookup(merged_employees: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
    """Build multi-key lookup map to resolve legacy employee references to target employee records."""
    emp_map: Dict[str, Dict[str, Any]] = {}
    for emp in merged_employees:
        target_id = emp.get("id") or emp.get("target_employee_id")
        if not target_id:
            continue
        record = {
            "id": str(target_id),
            "tenant_id": str(emp.get("tenant_id") or FOUNDER_TENANT_ID),
            "work_email": emp.get("work_email") or emp.get("email"),
            "employee_number": emp.get("employee_number"),
        }
        # Map by target id
        emp_map[str(target_id).strip().lower()] = record

        # Map by HRMS legacy id
        hrms_id = emp.get("source_legacy_hrms_id")
        if hrms_id is not None:
            emp_map[str(hrms_id).strip().lower()] = record

        # Map by Payroll legacy id
        payroll_id = emp.get("source_legacy_payroll_id")
        if payroll_id is not None:
            emp_map[str(payroll_id).strip().lower()] = record

        # Map by employee number
        emp_num = emp.get("employee_number")
        if emp_num:
            emp_map[str(emp_num).strip().lower()] = record

        # Map by email
        email = emp.get("work_email") or emp.get("email")
        if email:
            emp_map[str(email).strip().lower()] = record

    return emp_map


def resolve_recipient(
    recipient_ref: Any, emp_map: Dict[str, Dict[str, Any]]
) -> Tuple[Optional[str], Optional[str], Optional[str]]:
    """
    Resolve recipient reference into (tenant_id, recipient_employee_id, recipient_email).
    Returns (None, None, None) if recipient cannot be resolved.
    """
    if not recipient_ref:
        return None, None, None

    ref_str = str(recipient_ref).strip()
    ref_lower = ref_str.lower()

    # Direct match in employee lookup
    if ref_lower in emp_map:
        emp = emp_map[ref_lower]
        return emp["tenant_id"], emp["id"], emp.get("work_email")

    # If it looks like an email address
    if "@" in ref_str:
        return FOUNDER_TENANT_ID, None, ref_lower

    return None, None, None


def transform_notification_item(
    item: Dict[str, Any],
    emp_map: Dict[str, Dict[str, Any]],
    run_started_at: datetime
) -> Dict[str, Any]:
    """
    Transform single item from legacy `notifications` table into core.notification format.
    Raises ValueError on quarantine condition.
    """
    legacy_id = item.get("id")
    if legacy_id is None:
        raise ValueError("MISSING_LEGACY_ID")

    message = (item.get("message") or "").strip()
    if not message:
        raise ValueError("EMPTY_NOTIFICATION_BODY")

    recipient_ref = item.get("recipientId") or item.get("recipient_emp_id") or item.get("recipient_id")
    tenant_id, recipient_employee_id, recipient_email = resolve_recipient(recipient_ref, emp_map)

    if not recipient_employee_id and not recipient_email:
        raise ValueError(f"UNRESOLVED_RECIPIENT:{recipient_ref}")

    tenant_id = tenant_id or FOUNDER_TENANT_ID
    notif_id = det_uuid(f"{tenant_id}:notifications:{legacy_id}")

    # Timestamps
    created_at = parse_timestamp(item.get("createdAt") or item.get("created_at") or item.get("sent_at")) or run_started_at
    queued_at = created_at
    sent_at = parse_timestamp(item.get("sent_at")) or created_at

    is_read = bool(item.get("isRead") or item.get("is_read"))
    read_at = sent_at if is_read else None

    # Subject & subject_ref
    notif_type = (item.get("type") or "Notification").strip()
    subject = f"Notification: {notif_type}" if notif_type else "Notification"

    timesheet_id = item.get("timesheetId") or item.get("timesheet_id")
    subject_ref = f"timesheet:{timesheet_id}" if timesheet_id else None

    return {
        "id": notif_id,
        "tenant_id": tenant_id,
        "recipient_employee_id": recipient_employee_id,
        "recipient_email": recipient_email,
        "event": "LEGACY",
        "channel": "IN_APP",
        "subject": subject[:255] if subject else None,
        "body": message,
        "status": "SENT",
        "template_id": None,
        "queued_at": queued_at,
        "read_at": read_at,
        "subject_ref": subject_ref[:128] if subject_ref else None,
        "attempt_count": 1,
        "sent_at": sent_at,
        "created_at": created_at,
        "created_by": "migration",
        "updated_at": read_at or sent_at,
        "updated_by": "migration",
        "legacy_source": "notifications",
        "legacy_id": str(legacy_id)
    }


def transform_timesheet_notification_item(
    item: Dict[str, Any],
    emp_map: Dict[str, Dict[str, Any]],
    run_started_at: datetime
) -> Dict[str, Any]:
    """
    Transform single item from legacy `timesheet_notifications` table into core.notification format.
    Raises ValueError on quarantine condition.
    """
    legacy_id = item.get("id")
    if legacy_id is None:
        raise ValueError("MISSING_LEGACY_ID")

    message = (item.get("message") or item.get("rejectionReason") or "").strip()
    if not message:
        # Fallback to descriptive placeholder if type is present
        n_type = item.get("type") or item.get("notification_type") or "Timesheet"
        message = f"Timesheet notification: {n_type}"

    recipient_ref = item.get("recipientId") or item.get("employee_id") or item.get("recipient_id")
    tenant_id, recipient_employee_id, recipient_email = resolve_recipient(recipient_ref, emp_map)

    if not recipient_employee_id and not recipient_email:
        raise ValueError(f"UNRESOLVED_RECIPIENT:{recipient_ref}")

    tenant_id = tenant_id or FOUNDER_TENANT_ID
    notif_id = det_uuid(f"{tenant_id}:timesheet_notifications:{legacy_id}")

    # Timestamps
    created_at = parse_timestamp(item.get("createdAt") or item.get("created_at") or item.get("sent_at")) or run_started_at
    queued_at = created_at
    sent_at = parse_timestamp(item.get("sent_at")) or created_at

    is_read = bool(item.get("isRead") or item.get("is_read"))
    read_at = sent_at if is_read else None

    # Subject & subject_ref
    notif_type = (item.get("type") or item.get("notification_type") or "Timesheet").strip()
    subject = f"Timesheet: {notif_type}"

    timesheet_id = item.get("timesheetId") or item.get("timesheet_id")
    project_id = item.get("projectId") or item.get("project_id")
    if timesheet_id:
        subject_ref = f"timesheet:{timesheet_id}"
    elif project_id:
        subject_ref = f"project:{project_id}"
    else:
        subject_ref = None

    return {
        "id": notif_id,
        "tenant_id": tenant_id,
        "recipient_employee_id": recipient_employee_id,
        "recipient_email": recipient_email,
        "event": "LEGACY",
        "channel": "IN_APP",
        "subject": subject[:255] if subject else None,
        "body": message,
        "status": "SENT",
        "template_id": None,
        "queued_at": queued_at,
        "read_at": read_at,
        "subject_ref": subject_ref[:128] if subject_ref else None,
        "attempt_count": 1,
        "sent_at": sent_at,
        "created_at": created_at,
        "created_by": "migration",
        "updated_at": read_at or sent_at,
        "updated_by": "migration",
        "legacy_source": "timesheet_notifications",
        "legacy_id": str(legacy_id)
    }


def run_loader(conn=None, dry_run: bool = False) -> Dict[str, Any]:
    """
    Main loader execution for W-68 Task 3m (Notifications Loader).
    Supports dry-run for validation and tests.
    """
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    staging_dir = os.path.join(base_dir, "staging")

    notifications_file = os.path.join(staging_dir, "stg_hrms_notifications.json")
    ts_notifications_file = os.path.join(staging_dir, "stg_hrms_timesheet_notifications.json")
    employees_file = os.path.join(staging_dir, "stg_merged_employees.json")
    quarantine_file = os.path.join(staging_dir, "stg_quarantined_notifications.json")

    run_started_at = datetime.now(timezone.utc)
    checksum = compute_file_checksum([notifications_file, ts_notifications_file, employees_file])

    def load_json(path: str) -> List[Dict[str, Any]]:
        if not os.path.exists(path):
            logger.info(f"Staging file not found (will process 0 rows): {path}")
            return []
        try:
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            logger.error(f"Error loading {path}: {e}")
            return []

    notifications_raw = load_json(notifications_file)
    ts_notifications_raw = load_json(ts_notifications_file)
    merged_employees = load_json(employees_file)

    logger.info(
        f"Loaded staging: {len(notifications_raw)} notifications, "
        f"{len(ts_notifications_raw)} timesheet notifications, "
        f"{len(merged_employees)} merged employees."
    )

    emp_map = build_employee_lookup(merged_employees)

    transformed: List[Dict[str, Any]] = []
    quarantined: List[Dict[str, Any]] = []

    # Transform general notifications
    for item in notifications_raw:
        try:
            record = transform_notification_item(item, emp_map, run_started_at)
            transformed.append(record)
        except Exception as ex:
            quarantined.append({
                "source": "notifications",
                "raw_record": item,
                "reason": str(ex),
                "quarantined_at": run_started_at.isoformat()
            })

    # Transform timesheet notifications
    for item in ts_notifications_raw:
        try:
            record = transform_timesheet_notification_item(item, emp_map, run_started_at)
            transformed.append(record)
        except Exception as ex:
            quarantined.append({
                "source": "timesheet_notifications",
                "raw_record": item,
                "reason": str(ex),
                "quarantined_at": run_started_at.isoformat()
            })

    logger.info(f"Transform complete: {len(transformed)} valid notifications, {len(quarantined)} quarantined.")

    # Write quarantine output if any
    try:
        with open(quarantine_file, "w", encoding="utf-8") as f:
            json.dump(quarantined, f, indent=2)
        logger.info(f"Saved {len(quarantined)} quarantined notification records to {quarantine_file}")
    except Exception as e:
        logger.warning(f"Failed to write quarantine file: {e}")

    summary = {
        "status": "SUCCESS",
        "total_source_notifications": len(notifications_raw),
        "total_source_timesheet_notifications": len(ts_notifications_raw),
        "total_transformed": len(transformed),
        "total_quarantined": len(quarantined),
        "dry_run": dry_run,
        "input_checksum": checksum
    }

    if dry_run:
        logger.info("Dry-run mode active. Skipping database writes.")
        return summary

    # Group by tenant
    by_tenant: Dict[str, List[Dict[str, Any]]] = {}
    for notif in transformed:
        tid = notif["tenant_id"]
        by_tenant.setdefault(tid, []).append(notif)

    if not by_tenant:
        by_tenant[FOUNDER_TENANT_ID] = []

    owns_conn = False
    if conn is None:
        conn = get_conn()
        owns_conn = True

    try:
        conn.autocommit = False
        with conn.cursor() as cur:
            for tenant_id, notif_list in by_tenant.items():
                logger.info(f"Processing tenant {tenant_id}: {len(notif_list)} notifications...")

                # 1. Check ledger restartability
                cur.execute(
                    """
                    SELECT status FROM core.migration_ledger
                    WHERE tenant_id = %s AND step_name = 'load_notifications' AND status = 'DONE' AND input_checksum = %s
                    LIMIT 1;
                    """,
                    (tenant_id, checksum)
                )
                if cur.fetchone():
                    logger.info(f"Tenant {tenant_id}: step 'load_notifications' already DONE with identical checksum. Skipping.")
                    continue

                # 2. Record start in migration_ledger
                ledger_id = str(uuid.uuid4())
                cur.execute(
                    """
                    INSERT INTO core.migration_ledger (
                        id, tenant_id, step_name, records_processed, records_skipped, status, started_at, input_checksum
                    ) VALUES (%s, %s, 'load_notifications', 0, %s, 'RUNNING', %s, %s);
                    """,
                    (ledger_id, tenant_id, len(quarantined), run_started_at, checksum)
                )

                # 3. Set RLS tenant context
                cur.execute("SET LOCAL app.current_tenant_id = %s;", (tenant_id,))

                # 4. Insert / upsert notifications
                insert_sql = """
                    INSERT INTO core.notification (
                        id,
                        tenant_id,
                        recipient_employee_id,
                        recipient_email,
                        event,
                        channel,
                        subject,
                        body,
                        status,
                        template_id,
                        queued_at,
                        read_at,
                        subject_ref,
                        attempt_count,
                        sent_at,
                        created_at,
                        created_by,
                        updated_at,
                        updated_by
                    ) VALUES (
                        %(id)s,
                        %(tenant_id)s,
                        %(recipient_employee_id)s,
                        %(recipient_email)s,
                        %(event)s,
                        %(channel)s,
                        %(subject)s,
                        %(body)s,
                        %(status)s,
                        %(template_id)s,
                        %(queued_at)s,
                        %(read_at)s,
                        %(subject_ref)s,
                        %(attempt_count)s,
                        %(sent_at)s,
                        %(created_at)s,
                        %(created_by)s,
                        %(updated_at)s,
                        %(updated_by)s
                    )
                    ON CONFLICT (id) DO UPDATE SET
                        recipient_employee_id = EXCLUDED.recipient_employee_id,
                        recipient_email = EXCLUDED.recipient_email,
                        event = EXCLUDED.event,
                        channel = EXCLUDED.channel,
                        subject = EXCLUDED.subject,
                        body = EXCLUDED.body,
                        status = EXCLUDED.status,
                        read_at = EXCLUDED.read_at,
                        subject_ref = EXCLUDED.subject_ref,
                        updated_at = EXCLUDED.updated_at,
                        updated_by = EXCLUDED.updated_by;
                """
                if notif_list:
                    execute_batch(cur, insert_sql, notif_list, page_size=200)

                # 5. Record finish in migration_ledger
                finished_at = datetime.now(timezone.utc)
                cur.execute(
                    """
                    UPDATE core.migration_ledger
                    SET records_processed = %s,
                        status = 'DONE',
                        finished_at = %s
                    WHERE id = %s;
                    """,
                    (len(notif_list), finished_at, ledger_id)
                )

            conn.commit()
            logger.info("All notifications successfully loaded and committed.")

    except Exception as e:
        if conn:
            conn.rollback()
        logger.error(f"Migration failed for load_notifications: {e}")
        raise
    finally:
        if owns_conn and conn:
            conn.close()

    return summary


def main():
    dry_run = "--dry-run" in sys.argv
    try:
        res = run_loader(dry_run=dry_run)
        print(json.dumps(res, indent=2))
    except Exception as e:
        logger.error(f"Fatal error running load_notifications: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
