"""
Timesheet & Project Loader (Step 3j - W-68.3 / W-67 Rule R3)
Loads into target PostgreSQL:
  - hrms.project                 (projects from HRMS)
  - hrms.task                    (tasks per project)
  - hrms.assignment              (project employee assignments)
  - hrms.timesheet               (weekly timesheets)
  - hrms.timesheet_project_entry (project lines on timesheets)
  - hrms.timesheet_task_entry    (task lines on project entries)
  - hrms.timesheet_day_entry     (daily hours entries where hours > 0)
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
logger = logging.getLogger("load_timesheets")

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

def run_loader():
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    stg = os.path.join(base, "staging")

    def load_json(fname, default=None):
        fpath = os.path.join(stg, fname)
        if not os.path.exists(fpath):
            return default if default is not None else []
        with open(fpath, "r", encoding="utf-8") as f:
            return json.load(f)

    projects_raw     = load_json("stg_hrms_projects.json")
    tasks_raw        = load_json("stg_hrms_tasks.json")
    assignments_raw  = load_json("stg_hrms_assignments.json")
    timesheets_raw   = load_json("stg_hrms_timesheets.json")
    task_entry_raw   = load_json("stg_hrms_task_entry.json")
    day_entry_raw    = load_json("stg_hrms_day_entry.json")
    merged_employees = load_json("stg_merged_employees.json")

    logger.info(f"Staging loaded: {len(projects_raw)} projects, {len(tasks_raw)} tasks, "
                f"{len(assignments_raw)} assignments, {len(timesheets_raw)} timesheets, "
                f"{len(task_entry_raw)} task_entries, {len(day_entry_raw)} day_entries")

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

    step_name = "load_timesheets"
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

        # ── 1. Projects ────────────────────────────────────────────────────────
        logger.info(f"Loading {len(projects_raw)} projects...")
        proj_batch = []
        proj_id_map = {}  # legacy_id -> target uuid
        used_names = set()

        for p in projects_raw:
            raw_name = (p.get("name") or f"Project_{p['id']}").strip()
            # Ensure unique name per tenant for non-deleted projects
            base_name = raw_name
            suffix = 1
            while base_name.lower() in used_names:
                base_name = f"{raw_name} ({suffix})"
                suffix += 1
            used_names.add(base_name.lower())

            p_uuid = det_uuid(f"{tid}:project:{p['id']}")
            proj_id_map[p["id"]] = p_uuid

            mgr_emp = emp_by_hrms_id.get(str(p.get("manager_id") or "").strip())
            mgr_uuid = mgr_emp["id"] if mgr_emp else None

            s_date = parse_date(p.get("start_date"))
            e_date = parse_date(p.get("end_date"))
            priority = (p.get("priority") or "MEDIUM").strip().upper()
            if priority not in ("LOW", "MEDIUM", "HIGH", "CRITICAL"):
                priority = "MEDIUM"
            status = (p.get("status") or "STARTED").strip().upper()
            budget = Decimal(str(p["budget"])) if p.get("budget") is not None else None

            proj_batch.append((
                p_uuid, tid, base_name, p.get("category"),
                p.get("description"), s_date, e_date,
                priority, status, 0, budget, mgr_uuid, False
            ))

        if proj_batch:
            execute_batch(cur,
                """
                INSERT INTO hrms.project
                    (id, tenant_id, name, category, description, start_date, end_date,
                     priority, status, progress, budget, manager_employee_id, is_deleted,
                     created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status, budget = EXCLUDED.budget, updated_at = CURRENT_TIMESTAMP
                """, proj_batch, page_size=100)
            conn.commit()
            total += len(proj_batch)
        logger.info(f"✓ {len(proj_batch)} projects inserted.")

        # ── 2. Tasks ───────────────────────────────────────────────────────────
        logger.info(f"Loading {len(tasks_raw)} tasks...")
        task_batch = []
        task_id_map = {}  # legacy_task_id -> (target uuid, project_uuid)

        for t in tasks_raw:
            p_uuid = proj_id_map.get(t.get("project_id"))
            if not p_uuid:
                continue
            t_uuid = det_uuid(f"{tid}:task:{t['id']}")
            task_id_map[t["id"]] = (t_uuid, p_uuid)

            assignee_emp = emp_by_hrms_id.get(str(t.get("assignee_id") or "").strip())
            assignee_uuid = assignee_emp["id"] if assignee_emp else None

            priority = (t.get("priority") or "MEDIUM").strip().upper()
            if priority not in ("LOW", "MEDIUM", "HIGH", "CRITICAL"):
                priority = "MEDIUM"
            status = (t.get("status") or "TODO").strip().upper()
            due_date = parse_date(t.get("due_date"))
            est_hours = int(t.get("estimated_hours") or 0)

            task_batch.append((
                t_uuid, tid, p_uuid, t.get("title") or f"Task_{t['id']}",
                t.get("description"), assignee_uuid, due_date,
                priority, status, est_hours, False
            ))

        if task_batch:
            execute_batch(cur,
                """
                INSERT INTO hrms.task
                    (id, tenant_id, project_id, title, description, assignee_employee_id,
                     due_date, priority, status, estimated_hours, is_deleted, created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP
                """, task_batch, page_size=100)
            conn.commit()
            total += len(task_batch)
        logger.info(f"✓ {len(task_batch)} tasks inserted.")

        # ── 3. Assignments ─────────────────────────────────────────────────────
        logger.info(f"Loading {len(assignments_raw)} project assignments...")
        assign_batch = []
        assigned_pairs = set()

        for a in assignments_raw:
            p_uuid = proj_id_map.get(a.get("project_id"))
            emp = emp_by_hrms_id.get(str(a.get("emp_id") or "").strip())
            if not p_uuid or not emp:
                continue
            emp_uuid = emp["id"]
            if (p_uuid, emp_uuid) in assigned_pairs:
                continue
            assigned_pairs.add((p_uuid, emp_uuid))

            a_uuid = det_uuid(f"{tid}:assignment:{p_uuid}:{emp_uuid}")
            assign_batch.append((a_uuid, tid, p_uuid, emp_uuid, date(2025, 10, 1), False))

        if assign_batch:
            execute_batch(cur,
                """
                INSERT INTO hrms.assignment
                    (id, tenant_id, project_id, employee_id, assigned_on, is_deleted, created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO NOTHING
                """, assign_batch, page_size=100)
            conn.commit()
            total += len(assign_batch)
        logger.info(f"✓ {len(assign_batch)} project assignments inserted.")

        # ── 4. Timesheets ──────────────────────────────────────────────────────
        logger.info(f"Loading {len(timesheets_raw)} timesheets...")
        ts_batch = []
        ts_id_map = {}  # legacy_ts_id -> target uuid
        seen_weeks = set()  # (employee_uuid, week_start_date) -> keep latest, cancel duplicates

        STATUS_MAP = {
            "APPROVED": "APPROVED", "SUBMITTED": "SUBMITTED",
            "DRAFT": "DRAFT", "REJECTED": "REJECTED", "CANCELLED": "CANCELLED"
        }

        # Sort so we see entries in order
        sorted_timesheets = sorted(timesheets_raw, key=lambda x: x.get("id", 0))

        for ts in sorted_timesheets:
            emp = emp_by_hrms_id.get(str(ts.get("employee_id") or "").strip())
            if not emp:
                continue
            emp_uuid = emp["id"]

            w_start = parse_date(ts.get("week_start_date"))
            w_end = parse_date(ts.get("week_end_date"))
            if not w_start or not w_end:
                continue

            # Ensure week starts on Monday
            dow = w_start.isoweekday()
            if dow != 1:
                # Adjust to Monday of that week
                from datetime import timedelta
                w_start = w_start - timedelta(days=(dow - 1))
                w_end = w_start + timedelta(days=6)

            ts_uuid = det_uuid(f"{tid}:timesheet:{ts['id']}")
            ts_id_map[ts["id"]] = ts_uuid

            status = STATUS_MAP.get(str(ts.get("status") or "").strip().upper(), "SUBMITTED")
            # If an active timesheet already exists for this week, mark duplicate as CANCELLED
            if (emp_uuid, w_start) in seen_weeks and status != "CANCELLED":
                status = "CANCELLED"
            if status != "CANCELLED":
                seen_weeks.add((emp_uuid, w_start))

            sub_at = parse_date(ts.get("submitted_at"))
            ts_batch.append((ts_uuid, tid, emp_uuid, w_start, w_end, status, sub_at))

        if ts_batch:
            execute_batch(cur,
                """
                INSERT INTO hrms.timesheet
                    (id, tenant_id, employee_id, week_start_date, week_end_date, status, submitted_at,
                     created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP
                """, ts_batch, page_size=100)
            conn.commit()
            total += len(ts_batch)
        logger.info(f"✓ {len(ts_batch)} timesheets inserted.")

        # ── 5. Timesheet Project & Task Entries ────────────────────────────────
        logger.info("Loading timesheet project and task entries...")
        pe_batch = []
        te_batch = []
        seen_tpe = set()
        pe_id_map = {}  # legacy_project_entry_id -> target pe_uuid

        # Map each task entry to its timesheet and project
        for te in task_entry_raw:
            pe_legacy_id = te.get("project_entry_id")
            ts_uuid = ts_id_map.get(pe_legacy_id)
            task_info = task_id_map.get(te.get("task_id"))
            if not ts_uuid or not task_info:
                # Fallback project if task_id not found
                first_proj_uuid = list(proj_id_map.values())[0] if proj_id_map else None
                task_uuid = det_uuid(f"{tid}:task:{te.get('task_id')}")
                p_uuid = first_proj_uuid
            else:
                task_uuid, p_uuid = task_info

            if not p_uuid:
                continue

            # Project entry
            pe_uuid = det_uuid(f"{tid}:tpe:{ts_uuid}:{p_uuid}")
            pe_id_map[pe_legacy_id] = pe_uuid

            if (ts_uuid, p_uuid) not in seen_tpe:
                seen_tpe.add((ts_uuid, p_uuid))
                pe_batch.append((pe_uuid, tid, ts_uuid, p_uuid, "SUBMITTED", None))

            # Task entry
            te_uuid = det_uuid(f"{tid}:tte:{te['id']}")
            te_batch.append((te_uuid, tid, pe_uuid, task_uuid))

        if pe_batch:
            execute_batch(cur,
                """
                INSERT INTO hrms.timesheet_project_entry
                    (id, tenant_id, timesheet_id, project_id, status, rejection_reason, created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO NOTHING
                """, pe_batch, page_size=100)
            conn.commit()
            total += len(pe_batch)

        if te_batch:
            execute_batch(cur,
                """
                INSERT INTO hrms.timesheet_task_entry
                    (id, tenant_id, project_entry_id, task_id, created_by, updated_by)
                VALUES (%s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO NOTHING
                """, te_batch, page_size=100)
            conn.commit()
            total += len(te_batch)
        logger.info(f"✓ {len(pe_batch)} project entries, {len(te_batch)} task entries inserted.")

        # ── 6. Timesheet Day Entries (hours > 0 only) ──────────────────────────
        logger.info(f"Loading day entries from {len(day_entry_raw)} raw rows...")
        de_batch = []
        seen_tde = set()

        for de in day_entry_raw:
            raw_hours = Decimal(str(de.get("hours") or 0))
            if raw_hours <= 0 or raw_hours > 24:
                continue

            te_legacy_id = de.get("task_entry_id")
            te_uuid = det_uuid(f"{tid}:tte:{te_legacy_id}")
            w_date = parse_date(de.get("date"))
            if not w_date:
                continue

            if (te_uuid, w_date) in seen_tde:
                continue
            seen_tde.add((te_uuid, w_date))

            de_uuid = det_uuid(f"{tid}:tde:{de['id']}")
            desc = (de.get("description") or "").strip() or None

            de_batch.append((de_uuid, tid, te_uuid, w_date, raw_hours, desc))

        if de_batch:
            execute_batch(cur,
                """
                INSERT INTO hrms.timesheet_day_entry
                    (id, tenant_id, task_entry_id, work_date, hours, description, created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO UPDATE SET
                    hours = EXCLUDED.hours, description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP
                """, de_batch, page_size=200)
            conn.commit()
            total += len(de_batch)
        logger.info(f"✓ {len(de_batch)} day entries inserted (filtered hours > 0).")

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
        logger.info(f"=== TIMESHEET LOAD COMPLETE — {total} total records ===")

    except Exception as e:
        conn.rollback()
        logger.error(f"Timesheet load failed: {e}", exc_info=True)
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
