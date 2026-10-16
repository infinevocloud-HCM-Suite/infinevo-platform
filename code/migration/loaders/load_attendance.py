"""
Attendance & Clock Session Loader (Step 3k - W-68.3 / W-67 Rule R3)
Loads into target PostgreSQL:
  - core.attendance      (daily attendance per employee per date)
  - hrms.clock_session   (clock-in / clock-out sessions)
  - core.overtime_request (historical overtime requests if present)
Logs to core.migration_ledger
"""

import os
import sys
import json
import uuid
import logging
from datetime import datetime, timezone, date
from decimal import Decimal
import psycopg2
from psycopg2.extras import execute_batch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("load_attendance")

NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
FOUNDER_TENANT_ID = "a0000000-0000-0000-0000-000000000001"

def get_conn():
    return psycopg2.connect(
        host=os.environ.get("TARGET_DB_HOST", "localhost"),
        port=int(os.environ.get("TARGET_DB_PORT", "5432")),
        dbname=os.environ.get("TARGET_DB_NAME", "infinevo"),
        user=os.environ.get("TARGET_DB_USER", "migration_user"),
        password=os.environ.get("TARGET_DB_PASSWORD", "local_migration_pw")
    )

def det_uuid(namespace_key: str) -> str:
    return str(uuid.uuid5(NAMESPACE, namespace_key))

def parse_date(val):
    if not val:
        return None
    s = str(val)[:10]
    try:
        return date.fromisoformat(s)
    except Exception:
        return None

def parse_timestamp(val):
    if not val:
        return None
    s = str(val).strip()
    try:
        # Format: 2025-10-17 11:42:25 or ISO
        dt = datetime.fromisoformat(s)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt
    except Exception:
        return None

def run_loader():
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    stg = os.path.join(base, "staging")

    def load_json(fname, default=None):
        fpath = os.path.join(stg, fname)
        if not os.path.exists(fpath):
            return default if default is not None else []
        with open(fpath, "r", encoding="utf-8") as f:
            return json.load(f)

    attendance_raw     = load_json("stg_hrms_attendance.json")
    clock_sessions_raw = load_json("stg_hrms_clock_sessions.json")
    overtime_raw       = load_json("stg_hrms_overtime_requests.json", [])
    merged_employees   = load_json("stg_merged_employees.json")

    logger.info(f"Staging loaded: {len(attendance_raw)} attendance rows, "
                f"{len(clock_sessions_raw)} clock sessions, {len(overtime_raw)} overtime rows")

    emp_by_hrms_id = {}
    for e in merged_employees:
        if e.get("source_legacy_hrms_id"):
            emp_by_hrms_id[str(e["source_legacy_hrms_id"]).strip()] = e
        if e.get("employee_number"):
            emp_by_hrms_id[str(e["employee_number"]).strip()] = e

    tid = FOUNDER_TENANT_ID

    conn = get_conn()
    conn.autocommit = False
    cur = conn.cursor()

    step_name = "load_attendance"
    started_at = datetime.now(timezone.utc)
    ledger_id = str(uuid.uuid4())

    try:
        # ── Ledger: start ──────────────────────────────────────────────────────
        cur.execute(
            """
            INSERT INTO core.migration_ledger (id, tenant_id, step_name, records_processed, status, started_at)
            VALUES (%s, %s, %s, 0, 'RUNNING', %s)
            """,
            (ledger_id, tid, step_name, started_at)
        )
        conn.commit()

        total = 0

        # ── 1. Attendance Records ──────────────────────────────────────────────
        logger.info(f"Loading {len(attendance_raw)} attendance records...")
        att_batch = []
        att_id_map = {}  # legacy_attendance_id -> (target_uuid, employee_uuid, attendance_date)
        seen_emp_dates = set()

        STATUS_MAP = {
            "PRESENT": "PRESENT",
            "ABSENT": "ABSENT",
            "HALF_DAY": "HALF_DAY",
            "HALFDAY": "HALF_DAY",
        }

        # Check what source constraint is on core.attendance
        # Default to 'CLOCK' with remarks 'MIGRATED' to safely satisfy CHECK (source IN ('ADMIN','CLOCK'))
        source_val = "CLOCK"

        for a in attendance_raw:
            emp = emp_by_hrms_id.get(str(a.get("employee_id") or "").strip())
            if not emp:
                continue
            emp_uuid = emp["id"]

            att_date = parse_date(a.get("in_time")) or parse_date(a.get("created_at"))
            if not att_date:
                continue

            # Ensure unique per employee per date
            if (emp_uuid, att_date) in seen_emp_dates:
                continue
            seen_emp_dates.add((emp_uuid, att_date))

            att_uuid = det_uuid(f"{tid}:attendance:{a['id']}")
            att_id_map[a["id"]] = (att_uuid, emp_uuid, att_date)

            status = STATUS_MAP.get(str(a.get("status") or "").strip().upper(), "PRESENT")
            remarks = "MIGRATED"

            att_batch.append((
                att_uuid, tid, emp_uuid, att_date, status, source_val, remarks
            ))

        if att_batch:
            execute_batch(cur,
                """
                INSERT INTO core.attendance
                    (id, tenant_id, employee_id, attendance_date, status, source, remarks,
                     created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (tenant_id, employee_id, attendance_date) DO UPDATE SET
                    status = EXCLUDED.status, remarks = EXCLUDED.remarks, updated_at = CURRENT_TIMESTAMP
                """, att_batch, page_size=200)
            conn.commit()
            total += len(att_batch)
        logger.info(f"✓ {len(att_batch)} attendance records inserted.")

        # ── 2. Clock Sessions ──────────────────────────────────────────────────
        logger.info(f"Loading {len(clock_sessions_raw)} clock sessions...")
        cs_batch = []
        open_session_employees = set()

        for cs in clock_sessions_raw:
            att_info = att_id_map.get(cs.get("attendance_id"))
            if not att_info:
                # Try finding employee from attendance raw row
                continue
            _, emp_uuid, att_date = att_info

            clock_in = parse_timestamp(cs.get("in_time"))
            if not clock_in:
                continue
            clock_out = parse_timestamp(cs.get("out_time"))

            # Check constraint: clock_out_at IS NULL OR clock_out_at > clock_in_at
            if clock_out and clock_out <= clock_in:
                clock_out = None

            # Unique index: uk_clock_session_tenant_employee_open WHERE clock_out_at IS NULL AND voided_at IS NULL
            # Only one open session per employee allowed
            voided_at = None
            void_reason = None
            if clock_out is None:
                if emp_uuid in open_session_employees:
                    voided_at = clock_in
                    void_reason = "NOT_CLOCKED_OUT"
                else:
                    open_session_employees.add(emp_uuid)

            cs_uuid = det_uuid(f"{tid}:clock_session:{cs['id']}")
            cs_batch.append((
                cs_uuid, tid, emp_uuid, att_date, clock_in, clock_out,
                "CLOCK", voided_at, void_reason
            ))

        if cs_batch:
            execute_batch(cur,
                """
                INSERT INTO hrms.clock_session
                    (id, tenant_id, employee_id, attendance_date, clock_in_at, clock_out_at,
                     origin, voided_at, void_reason, created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO UPDATE SET
                    clock_out_at = EXCLUDED.clock_out_at, updated_at = CURRENT_TIMESTAMP
                """, cs_batch, page_size=200)
            conn.commit()
            total += len(cs_batch)
        logger.info(f"✓ {len(cs_batch)} clock sessions inserted.")

        # ── 3. Overtime Requests (if present) ──────────────────────────────────
        if overtime_raw:
            logger.info(f"Loading {len(overtime_raw)} overtime requests...")
            ot_batch = []
            for ot in overtime_raw:
                emp = emp_by_hrms_id.get(str(ot.get("employee_id") or "").strip())
                if not emp:
                    continue
                emp_uuid = emp["id"]
                ot_date = parse_date(ot.get("start_time")) or parse_date(ot.get("date")) or parse_date(ot.get("created_at"))
                if not ot_date:
                    continue

                hours = Decimal(str(ot.get("hours") or 2.0))
                if hours <= 0 or hours > 24:
                    hours = Decimal("2.00")

                ot_uuid = det_uuid(f"{tid}:overtime:{ot.get('id', uuid.uuid4())}")
                ot_batch.append((
                    ot_uuid, tid, emp_uuid, ot_date, hours, None,
                    "APPROVED", "ADMIN", "MIGRATED"
                ))

            if ot_batch:
                execute_batch(cur,
                    """
                    INSERT INTO core.overtime_request
                        (id, tenant_id, employee_id, overtime_date, hours, amount, status, source, remarks,
                         created_by, updated_by)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                    ON CONFLICT (id) DO NOTHING
                    """, ot_batch, page_size=100)
                conn.commit()
                total += len(ot_batch)
                logger.info(f"✓ {len(ot_batch)} overtime requests inserted.")

        # ── Ledger: complete ──────────────────────────────────────────────────
        cur.execute(
            """
            UPDATE core.migration_ledger
            SET records_processed = %s, status = %s, completed_at = %s
            WHERE id = %s
            """,
            (total, "DONE", datetime.now(timezone.utc), ledger_id)
        )
        conn.commit()
        logger.info(f"=== ATTENDANCE LOAD COMPLETE — {total} total records ===")

    except Exception as e:
        conn.rollback()
        logger.error(f"Attendance load failed: {e}", exc_info=True)
        cur.execute(
            """
            UPDATE core.migration_ledger
            SET status = %s, error_message = %s, completed_at = %s
            WHERE id = %s
            """,
            ("FAILED", str(e), datetime.now(timezone.utc), ledger_id)
        )
        conn.commit()
        raise
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    run_loader()
