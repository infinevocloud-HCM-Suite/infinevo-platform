"""
Leave & Holiday Loader (Step 3i - W-68.3 / W-67 Rule R2)
Loads into target PostgreSQL:
  - core.holiday_calendar           (one default calendar per tenant)
  - core.holiday                    (holidays from HRMS / Payroll)
  - core.leave_type                 (5 seeded codes + LEGACY + legacy types)
  - core.leave_policy               (policies per leave type)
  - core.leave_allocation           (allocations split by leave type)
  - core.leave_consumption          (consumption ledger from used balances & requests)
  - core.approval_instance & step   (approval audit trails for HRMS requests)
  - core.leave_request              (historical requests attached to LEGACY leave type)
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
logger = logging.getLogger("load_leave")

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

def parse_bit(val):
    """Convert MySQL BIT/b'\\x01' to bool."""
    if val is None:
        return False
    if isinstance(val, bool):
        return val
    s = str(val)
    return s in ("b'\\x01'", "b'\\x01'", "True", "1", "true")

def run_loader():
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    stg = os.path.join(base, "staging")

    def load_json(fname, default=None):
        fpath = os.path.join(stg, fname)
        if not os.path.exists(fpath):
            return default if default is not None else []
        with open(fpath, "r", encoding="utf-8") as f:
            return json.load(f)

    leave_types_raw   = load_json("stg_hrms_leave_types.json")
    leave_reqs_raw    = load_json("stg_hrms_leave_requests.json")
    leave_bals_raw    = load_json("stg_hrms_leave_balances.json")
    holidays_raw      = load_json("stg_hrms_holidays.json")
    merged_employees  = load_json("stg_merged_employees.json")

    # Payroll leave staging if present
    payroll_consumption = load_json("stg_payroll_employee_leave_balance_consumption.json", [])

    logger.info(f"Staging loaded: {len(leave_types_raw)} leave_types, {len(leave_reqs_raw)} leave_requests, "
                f"{len(leave_bals_raw)} leave_balances, {len(holidays_raw)} holidays, "
                f"{len(payroll_consumption)} payroll consumption records")

    emp_by_hrms_id = {}
    emp_by_payroll_id = {}
    for e in merged_employees:
        if e.get("source_legacy_hrms_id"):
            emp_by_hrms_id[str(e["source_legacy_hrms_id"]).strip()] = e
        if e.get("employee_number"):
            emp_by_hrms_id[str(e["employee_number"]).strip()] = e
        if e.get("source_legacy_payroll_id"):
            emp_by_payroll_id[str(e["source_legacy_payroll_id"]).strip()] = e

    all_tenant_ids = sorted(list(set(e["tenant_id"] for e in merged_employees if e.get("tenant_id"))))
    if FOUNDER_TENANT_ID not in all_tenant_ids:
        all_tenant_ids.append(FOUNDER_TENANT_ID)

    logger.info(f"Total unique tenants to seed: {len(all_tenant_ids)}")

    conn = get_conn()
    conn.autocommit = False
    cur = conn.cursor()

    step_name = "load_leave"
    started_at = datetime.now(timezone.utc)
    ledger_id = str(uuid.uuid4())
    anchor_tenant = FOUNDER_TENANT_ID if FOUNDER_TENANT_ID in all_tenant_ids else all_tenant_ids[0]

    try:
        # ── Ledger: start ──────────────────────────────────────────────────────
        cur.execute(
            """
            INSERT INTO core.migration_ledger (id, tenant_id, step_name, records_processed, status, started_at)
            VALUES (%s, %s, %s, 0, 'RUNNING', %s)
            """,
            (ledger_id, anchor_tenant, step_name, started_at)
        )
        conn.commit()

        total = 0

        # ── 1. Holiday Calendars (one default per tenant) ──────────────────────
        logger.info(f"Creating default holiday calendars for {len(all_tenant_ids)} tenants...")
        cal_batch = []
        for tid in all_tenant_ids:
            cal_id = det_uuid(f"{tid}:holiday_calendar:default")
            cal_batch.append((cal_id, tid, "India Public Holidays 2026", True))

        execute_batch(cur,
            """
            INSERT INTO core.holiday_calendar (id, tenant_id, name, is_default, created_by, updated_by)
            VALUES (%s, %s, %s, %s, 'system', 'system')
            ON CONFLICT DO NOTHING
            """, cal_batch, page_size=100)
        conn.commit()
        total += len(cal_batch)

        # ── 2. Holidays ────────────────────────────────────────────────────────
        logger.info(f"Loading {len(holidays_raw)} holidays for tenants...")
        hol_batch = []
        for tid in all_tenant_ids:
            cal_id = det_uuid(f"{tid}:holiday_calendar:default")
            for h in holidays_raw:
                hol_id = det_uuid(f"{tid}:holiday:{h['id']}")
                h_date = parse_date(h.get("date"))
                if not h_date:
                    continue
                hol_batch.append((hol_id, tid, cal_id, h.get("name") or "Holiday", h_date, h_date, False))

        if hol_batch:
            execute_batch(cur,
                """
                INSERT INTO core.holiday (id, tenant_id, calendar_id, name, from_date, to_date, is_restricted,
                                          created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT DO NOTHING
                """, hol_batch, page_size=200)
            conn.commit()
            total += len(hol_batch)
        logger.info(f"✓ {len(hol_batch)} holiday records processed.")

        # ── 3. Leave Types (Rule R2: 5 Seeded Codes + LEGACY for Founder Tenant) ───
        logger.info("Loading leave types and policies...")
        lt_batch = []
        pol_batch = []

        # 5 Seeded codes per Rule R2:
        SEEDED_LEAVE_TYPES = [
            ("ANNUAL", "Annual Leave", Decimal("12.00")),
            ("CASUAL", "Casual Leave", Decimal("12.00")),
            ("SICK", "Sick Leave", Decimal("12.00")),
            ("MATERNITY", "Maternity Leave", Decimal("180.00")),
            ("PATERNITY", "Paternity Leave", Decimal("15.00")),
            ("LEGACY", "Legacy leave (type not recorded)", Decimal("0.00"))
        ]

        for tid in all_tenant_ids:
            # Seed standard types
            for code, name, default_days in SEEDED_LEAVE_TYPES:
                lt_uuid = det_uuid(f"{tid}:leave_type:{code}")
                pol_uuid = det_uuid(f"{tid}:leave_policy:{code}")
                lt_batch.append((
                    lt_uuid, tid, code, name,
                    True, "DAYS", True, date(2024, 1, 1), None, True
                ))
                pol_batch.append((
                    pol_uuid, tid, lt_uuid, default_days,
                    False, None, None,
                    False, None,
                    False, None, None,
                    False, None, None,
                    False, False,
                    "noLimit", None,
                    False, None, None, date(2024, 1, 1)
                ))

            # Also seed any extra types from staging for founder tenant
            if tid == FOUNDER_TENANT_ID:
                for lt in leave_types_raw:
                    raw_name = (lt.get("name") or "").strip()
                    code = raw_name.upper().replace(" ", "_")[:32]
                    if code in [s[0] for s in SEEDED_LEAVE_TYPES]:
                        continue
                    lt_uuid = det_uuid(f"{tid}:leave_type:{lt['id']}")
                    pol_uuid = det_uuid(f"{tid}:leave_policy:{lt['id']}")
                    v_from = parse_date(lt.get("start_date")) or date(2024, 1, 1)
                    v_to = parse_date(lt.get("end_date"))
                    annual_days = Decimal(str(lt.get("default_days") or 12))
                    lt_batch.append((
                        lt_uuid, tid, code, raw_name or f"LeaveType_{lt['id']}",
                        True, "DAYS", True, v_from, v_to, True
                    ))
                    pol_batch.append((
                        pol_uuid, tid, lt_uuid, annual_days,
                        False, None, None,
                        False, None,
                        False, None, None,
                        False, None, None,
                        False, False,
                        "noLimit", None,
                        False, None, None, v_from
                    ))

        execute_batch(cur,
            """
            INSERT INTO core.leave_type
                (id, tenant_id, code, name, is_paid, unit, allow_half_day, valid_from, valid_to,
                 is_active, created_by, updated_by)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'system', 'system')
            ON CONFLICT (tenant_id, code) DO UPDATE SET
                name = EXCLUDED.name, valid_to = EXCLUDED.valid_to, updated_at = CURRENT_TIMESTAMP
            """, lt_batch, page_size=200)
        conn.commit()
        total += len(lt_batch)

        execute_batch(cur,
            """
            INSERT INTO core.leave_policy
                (id, tenant_id, leave_type_id, annual_days,
                 accrual_enabled, accrual_frequency, accrual_units,
                 reset_enabled, reset_frequency,
                 carry_forward_enabled, carry_forward_cap, carry_forward_expires_after_months,
                 requires_document, past_booking_limit_days, future_booking_limit_days,
                 include_weekend, include_holiday,
                 exceed_balance_mode, exceed_balance_limit_days,
                 pro_rate_enabled, max_days_per_application, gender, effective_from,
                 created_by, updated_by)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'system', 'system')
            ON CONFLICT DO NOTHING
            """, pol_batch, page_size=200)
        conn.commit()
        total += len(pol_batch)
        logger.info(f"✓ {len(lt_batch)} leave types and {len(pol_batch)} policies inserted.")

        # ── 4. Leave Allocations & Consumption (Rule R2: Split HRMS leave_balances) ───
        logger.info(f"Loading {len(leave_bals_raw)} HRMS leave balances...")
        alloc_batch = []
        consump_batch = []
        alloc_skip = 0

        balance_cols = [
            ("ANNUAL",     "annual_allotted",    "annual_used"),
            ("CASUAL",     "casual_allotted",    "casual_used"),
            ("SICK",       "sick_allotted",      "sick_used"),
            ("MATERNITY",  "maternity_allotted",  "maternity_used"),
            ("PATERNITY",  "paternity_allotted",  "paternity_used"),
        ]

        for bal in leave_bals_raw:
            emp_id_str = str(bal.get("employee_id") or "").strip()
            emp = emp_by_hrms_id.get(emp_id_str)
            if not emp:
                alloc_skip += 1
                continue
            tid = emp["tenant_id"]
            emp_uuid = emp["id"]
            year = int(bal.get("year") or 2025)
            yr_start = date(year, 1, 1)
            yr_end = date(year, 12, 31)
            leave_year_str = f"{year}-{year}"

            for code, allotted_col, used_col in balance_cols:
                allotted = Decimal(str(bal.get(allotted_col) or 0))
                used = Decimal(str(bal.get(used_col) or 0))
                if allotted == 0 and used == 0:
                    continue

                lt_uuid = det_uuid(f"{tid}:leave_type:{code}")
                pol_uuid = det_uuid(f"{tid}:leave_policy:{code}")
                alloc_uuid = det_uuid(f"{tid}:{emp_uuid}:alloc:{code}:{year}")
                accrued = allotted - used

                alloc_batch.append((
                    alloc_uuid, tid, emp_uuid, lt_uuid,
                    leave_year_str, yr_start, yr_end,
                    allotted, accrued,
                    Decimal("0.00"), None,
                    Decimal("1.0000"), None, None,
                    pol_uuid
                ))

                if used > 0:
                    consump_uuid = det_uuid(f"{tid}:{alloc_uuid}:consumption:{year}")
                    consump_batch.append((
                        consump_uuid, tid, emp_uuid, alloc_uuid, None,
                        used, date(year, 12, 31), f"{year}-12", None,
                        f"Legacy {code} leave consumption"
                    ))

        if alloc_batch:
            execute_batch(cur,
                """
                INSERT INTO core.leave_allocation
                    (id, tenant_id, employee_id, leave_type_id, leave_year, year_start_date, year_end_date,
                     entitlement_days, accrued_days, carried_forward_days, carry_forward_expires_on,
                     pro_rate_factor, last_accrued_on, last_reset_on, policy_id, created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (tenant_id, employee_id, leave_type_id, leave_year) DO UPDATE SET
                    entitlement_days = EXCLUDED.entitlement_days,
                    accrued_days = EXCLUDED.accrued_days,
                    updated_at = CURRENT_TIMESTAMP
                """, alloc_batch, page_size=200)
            conn.commit()
            total += len(alloc_batch)

        if consump_batch:
            execute_batch(cur,
                """
                INSERT INTO core.leave_consumption
                    (id, tenant_id, employee_id, allocation_id, leave_request_id,
                     consumed_days, consumed_on, period, reverses_id, reason, created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO NOTHING
                """, consump_batch, page_size=200)
            conn.commit()
            total += len(consump_batch)

        logger.info(f"✓ {len(alloc_batch)} leave allocations, {len(consump_batch)} consumption rows inserted.")

        # ── 5. Leave Requests & Two-Step Approvals (Rule R2: Map to LEGACY type) ───
        logger.info(f"Loading {len(leave_reqs_raw)} leave requests and approvals...")

        # Pre-seed approval definitions if needed
        for tid in all_tenant_ids:
            try:
                cur.execute("SELECT core.seed_approval_definitions(%s)", (tid,))
                conn.commit()
            except Exception:
                conn.rollback()

        # Cache approval definition id for LEAVE per tenant
        appr_def_by_tenant = {}
        for tid in all_tenant_ids:
            cur.execute(
                "SELECT id FROM core.approval_definition WHERE tenant_id = %s AND flow_type = 'LEAVE' "
                "ORDER BY effective_from DESC LIMIT 1",
                (tid,)
            )
            row = cur.fetchone()
            if row:
                appr_def_by_tenant[tid] = row[0]

        STATUS_MAP = {
            "APPROVED": "APPROVED", "APPROV": "APPROVED",
            "REJECTED": "REJECTED", "REJECT": "REJECTED",
            "PENDING":  "PENDING",  "CANCELLED": "CANCELLED",
            "WITHDRAWN": "WITHDRAWN", "DRAFT": "DRAFT"
        }

        req_batch = []
        appr_inst_batch = []
        appr_step_batch = []
        req_skip = 0

        for req in leave_reqs_raw:
            emp_id_str = str(req.get("employee_id") or "").strip()
            emp = emp_by_hrms_id.get(emp_id_str)
            if not emp:
                req_skip += 1
                continue
            tid = emp["tenant_id"]
            emp_uuid = emp["id"]

            from_date = parse_date(req.get("from_date"))
            to_date = parse_date(req.get("to_date"))
            if not from_date or not to_date:
                req_skip += 1
                continue
            if to_date < from_date:
                to_date = from_date

            is_half = parse_bit(req.get("is_half_day"))
            half_per = req.get("half_day_period") if is_half else None
            if half_per and half_per not in ("first", "second"):
                half_per = "first"
            if not is_half:
                half_per = None

            raw_hr = str(req.get("hr_status") or "").strip().upper()
            raw_mgr = str(req.get("reporting_manager_status") or "").strip().upper()

            # Rule R2: hr_status wins over reporting_manager_status
            final_status = STATUS_MAP.get(raw_hr) or STATUS_MAP.get(raw_mgr) or "PENDING"
            mgr_decision = STATUS_MAP.get(raw_mgr, "PENDING")
            hr_decision = STATUS_MAP.get(raw_hr, "PENDING")

            working_days = Decimal(str(req.get("total_days") or ("0.50" if is_half else "1.00")))
            decided_at = parse_date(req.get("hr_updated_at")) or parse_date(req.get("updated_at"))

            # Rule R2: Always attach HRMS requests to seeded LEGACY leave type
            lt_uuid = det_uuid(f"{tid}:leave_type:LEGACY")
            req_uuid = det_uuid(f"{tid}:leave_request:{req['id']}")
            appr_inst_uuid = det_uuid(f"{tid}:approval_instance:leave:{req['id']}")

            def_id = appr_def_by_tenant.get(tid)
            if def_id:
                appr_inst_batch.append((
                    appr_inst_uuid, tid, "LEAVE", def_id,
                    "core.leave_request", req_uuid, emp_uuid,
                    final_status, decided_at
                ))
                # Step 0: Reporting Manager
                step0_id = det_uuid(f"{appr_inst_uuid}:step:0")
                appr_step_batch.append((
                    step0_id, tid, appr_inst_uuid, 0,
                    "REPORTING_MANAGER", None, mgr_decision,
                    req.get("reporting_manager_comment"), None, decided_at
                ))
                # Step 1: HR Role
                step1_id = det_uuid(f"{appr_inst_uuid}:step:1")
                appr_step_batch.append((
                    step1_id, tid, appr_inst_uuid, 1,
                    "ROLE", None, hr_decision,
                    req.get("hr_comment"), None, decided_at
                ))

            req_batch.append((
                req_uuid, tid, emp_uuid, lt_uuid,
                from_date, to_date, is_half, half_per,
                working_days, req.get("reason"), final_status,
                appr_inst_uuid if def_id else None,
                False, decided_at
            ))

        if appr_inst_batch:
            execute_batch(cur,
                """
                INSERT INTO core.approval_instance
                    (id, tenant_id, flow_type, definition_id, subject_table, subject_id,
                     subject_employee_id, status, completed_at, created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status, completed_at = EXCLUDED.completed_at
                """, appr_inst_batch, page_size=100)
            conn.commit()
            total += len(appr_inst_batch)

        if appr_step_batch:
            execute_batch(cur,
                """
                INSERT INTO core.approval_step
                    (id, tenant_id, instance_id, step_index, approver_kind, assignee_employee_id,
                     decision, comment, approved_amount, decided_at, created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO UPDATE SET
                    decision = EXCLUDED.decision, comment = EXCLUDED.comment, decided_at = EXCLUDED.decided_at
                """, appr_step_batch, page_size=200)
            conn.commit()
            total += len(appr_step_batch)

        if req_batch:
            execute_batch(cur,
                """
                INSERT INTO core.leave_request
                    (id, tenant_id, employee_id, leave_type_id, from_date, to_date,
                     is_half_day, half_day_period, working_days, reason, status,
                     approval_instance_id, on_behalf, decided_at, created_by, updated_by)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'system', 'system')
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status, working_days = EXCLUDED.working_days, decided_at = EXCLUDED.decided_at
                """, req_batch, page_size=100)
            conn.commit()
            total += len(req_batch)

        logger.info(f"✓ {len(req_batch)} leave requests with approval audit steps inserted ({req_skip} skipped).")

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
        logger.info(f"=== LEAVE & HOLIDAY LOAD COMPLETE — {total} total records ===")

    except Exception as e:
        conn.rollback()
        logger.error(f"Load failed: {e}", exc_info=True)
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
