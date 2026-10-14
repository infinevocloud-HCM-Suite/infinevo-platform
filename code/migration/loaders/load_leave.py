"""
Leave & Holiday Loader (Step 2 - W-68.3)
Loads into target PostgreSQL:
  - core.holiday_calendar  (one default calendar per tenant)
  - core.holiday           (15 company holidays)
  - core.leave_type        (8 leave types, tenant-scoped)
  - core.leave_policy      (one policy per leave type)
  - core.leave_allocation  (10 employee leave balances)
  - core.leave_request     (12 historical leave requests)
Logs to core.migration_ledger
"""

import os, sys, json, uuid, logging
from datetime import datetime, timezone, date
from decimal import Decimal
import psycopg2
from psycopg2.extras import execute_batch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("load_leave")

NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")

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

    def load(fname):
        with open(os.path.join(stg, fname), "r", encoding="utf-8") as f:
            return json.load(f)

    leave_types_raw   = load("stg_hrms_leave_types.json")
    leave_reqs_raw    = load("stg_hrms_leave_requests.json")
    leave_bals_raw    = load("stg_hrms_leave_balances.json")
    holidays_raw      = load("stg_hrms_holidays.json")
    merged_employees  = load("stg_merged_employees.json")
    org_map           = load(os.path.join(base, "config", "org_mapping.json").replace(stg, "").lstrip(os.sep))
    with open(os.path.join(base, "config", "org_mapping.json"), "r", encoding="utf-8") as f:
        org_map = json.load(f)

    logger.info(f"Staging loaded: {len(leave_types_raw)} leave_types, {len(leave_reqs_raw)} leave_requests, "
                f"{len(leave_bals_raw)} leave_balances, {len(holidays_raw)} holidays")

    # Build employee lookup by HRMS employee_id string (like 'P001', 'EMP000')
    emp_by_hrms_id = {}
    emp_by_payroll_id = {}
    for e in merged_employees:
        if e.get("source_legacy_hrms_id"):
            emp_by_hrms_id[str(e["source_legacy_hrms_id"])] = e
        if e.get("source_legacy_payroll_id"):
            emp_by_payroll_id[str(e["source_legacy_payroll_id"])] = e

    # All unique tenants from merged employees
    all_tenant_ids = list(set(e["tenant_id"] for e in merged_employees))
    # Use first real customer tenant for system-wide holidays
    # All tenants get their own default calendar
    logger.info(f"Total unique tenants to seed calendars: {len(all_tenant_ids)}")

    conn = get_conn()
    conn.autocommit = False
    cur = conn.cursor()

    step_name = "LEAVE_HOLIDAY_LOAD"
    started_at = datetime.now(timezone.utc)
    ledger_id = str(uuid.uuid4())
    anchor_tenant = all_tenant_ids[0]

    try:
        # ── Ledger: start ──────────────────────────────────────────────────────
        cur.execute(
            "INSERT INTO core.migration_ledger (id,tenant_id,step_name,records_processed,status,started_at) "
            "VALUES (%s,%s,%s,%s,%s,%s)",
            (ledger_id, anchor_tenant, step_name, 0, "RUNNING", started_at)
        )
        conn.commit()

        total = 0

        # ── 1. Holiday Calendars (one default per tenant) ──────────────────────
        logger.info(f"Creating {len(all_tenant_ids)} default holiday calendars...")
        cal_batch = []
        for tid in all_tenant_ids:
            cal_id = det_uuid(f"{tid}:holiday_calendar:default")
            cal_batch.append((cal_id, tid, "India Public Holidays 2026", True))

        execute_batch(cur,
            """
            INSERT INTO core.holiday_calendar (id, tenant_id, name, is_default, created_by, updated_by)
            VALUES (%s,%s,%s,%s,'system','system')
            ON CONFLICT DO NOTHING
            """, cal_batch, page_size=100)
        conn.commit()
        total += len(cal_batch)
        logger.info(f"✓ {len(cal_batch)} holiday calendars inserted.")

        # ── 2. Holidays (same 15 records cloned into every tenant's calendar) ──
        logger.info(f"Loading {len(holidays_raw)} holidays for all tenants...")
        hol_batch = []
        for tid in all_tenant_ids:
            cal_id = det_uuid(f"{tid}:holiday_calendar:default")
            for h in holidays_raw:
                hol_id  = det_uuid(f"{tid}:holiday:{h['id']}")
                h_date  = parse_date(h.get("date"))
                if not h_date:
                    continue
                hol_batch.append((hol_id, tid, cal_id, h["name"], h_date, h_date, False))

        execute_batch(cur,
            """
            INSERT INTO core.holiday (id, tenant_id, calendar_id, name, from_date, to_date, is_restricted,
                                      created_by, updated_by)
            VALUES (%s,%s,%s,%s,%s,%s,%s,'system','system')
            ON CONFLICT DO NOTHING
            """, hol_batch, page_size=200)
        conn.commit()
        total += len(hol_batch)
        logger.info(f"✓ {len(hol_batch)} holiday records inserted.")

        # ── 3. Leave Types (8 types per tenant) ───────────────────────────────
        logger.info(f"Loading {len(leave_types_raw)} leave types for all tenants...")
        lt_batch = []
        lt_id_map = {}   # (tenant_id, legacy_lt_id) -> target uuid

        STANDARD_CODES = {
            "annual leave": "ANNUAL", "casual leave": "CASUAL",
            "sick leave": "SICK", "maternity leave": "MATERNITY",
            "paternity leave": "PATERNITY", "unpaid leave": "UNPAID",
            "compensatory leave": "COMP", "bereavement leave": "BEREAVEMENT",
        }

        for tid in all_tenant_ids:
            for lt in leave_types_raw:
                raw_name = (lt.get("name") or "").strip()
                code = STANDARD_CODES.get(raw_name.lower(), raw_name.upper().replace(" ", "_")[:32])
                lt_uuid = det_uuid(f"{tid}:leave_type:{lt['id']}")
                lt_id_map[(tid, lt["id"])] = lt_uuid
                valid_from = parse_date(lt.get("start_date")) or date(2025, 1, 1)
                valid_to   = parse_date(lt.get("end_date"))
                lt_batch.append((lt_uuid, tid, code, raw_name or f"LeaveType_{lt['id']}",
                                  True, "DAYS", True, valid_from, valid_to, True))

        execute_batch(cur,
            """
            INSERT INTO core.leave_type
                (id, tenant_id, code, name, is_paid, unit, allow_half_day, valid_from, valid_to,
                 is_active, created_by, updated_by)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'system','system')
            ON CONFLICT (tenant_id, code) DO UPDATE SET
                name=EXCLUDED.name, valid_to=EXCLUDED.valid_to, updated_at=CURRENT_TIMESTAMP
            """, lt_batch, page_size=200)
        conn.commit()
        total += len(lt_batch)
        logger.info(f"✓ {len(lt_batch)} leave type records inserted.")

        # ── 4. Leave Policies (one per leave type per tenant) ─────────────────
        logger.info("Creating leave policies...")
        pol_batch = []
        for tid in all_tenant_ids:
            for lt in leave_types_raw:
                lt_uuid  = lt_id_map[(tid, lt["id"])]
                pol_uuid = det_uuid(f"{tid}:leave_policy:{lt['id']}")
                annual_days = Decimal(str(lt.get("default_days") or 12))
                pol_batch.append((
                    pol_uuid, tid, lt_uuid,
                    annual_days,
                    False, None, None,  # accrual
                    False, None,        # reset
                    False, None, None,  # carry forward
                    False,              # requires_document
                    None, None,         # booking limits
                    False, False,       # include_weekend, include_holiday
                    "noLimit", None,    # exceed_balance
                    False, None,        # pro_rate, max_days
                    None,               # gender
                    date(2025, 1, 1)    # effective_from
                ))

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
            VALUES (%s,%s,%s,%s, %s,%s,%s, %s,%s, %s,%s,%s, %s,%s,%s, %s,%s, %s,%s, %s,%s,%s,%s, 'system','system')
            ON CONFLICT DO NOTHING
            """, pol_batch, page_size=100)
        conn.commit()
        total += len(pol_batch)
        logger.info(f"✓ {len(pol_batch)} leave policy records inserted.")

        # ── 5. Leave Allocations from stg_hrms_leave_balances ─────────────────
        logger.info(f"Loading {len(leave_bals_raw)} leave balance allocations...")
        alloc_batch = []
        alloc_skip  = 0

        for bal in leave_bals_raw:
            emp_id_str = str(bal.get("employee_id") or "").strip()
            emp = emp_by_hrms_id.get(emp_id_str)
            if not emp:
                logger.warning(f"  Alloc skip: employee_id='{emp_id_str}' not in merged employees.")
                alloc_skip += 1
                continue
            tid      = emp["tenant_id"]
            emp_uuid = emp["id"]
            year     = int(bal.get("year") or 2025)
            yr_start = date(year, 1, 1)
            yr_end   = date(year, 12, 31)
            leave_year_str = f"{year}-{year}"

            # Map each balance column to a leave type if it exists
            balance_cols = [
                ("annual",     "annual_allotted",    "annual_used"),
                ("casual",     "casual_allotted",    "casual_used"),
                ("sick",       "sick_allotted",      "sick_used"),
                ("maternity",  "maternity_allotted",  "maternity_used"),
                ("paternity",  "paternity_allotted",  "paternity_used"),
            ]
            for lt_name, allotted_col, used_col in balance_cols:
                allotted = Decimal(str(bal.get(allotted_col) or 0))
                used     = Decimal(str(bal.get(used_col) or 0))
                if allotted == 0:
                    continue

                # Find the matching leave type & policy for this tenant
                # Match by code prefix
                cur.execute(
                    "SELECT lt.id, lp.id FROM core.leave_type lt "
                    "JOIN core.leave_policy lp ON lp.leave_type_id=lt.id AND lp.tenant_id=lt.tenant_id "
                    "WHERE lt.tenant_id=%s AND lower(lt.code) LIKE %s LIMIT 1",
                    (tid, f"{lt_name.upper()}%")
                )
                row = cur.fetchone()
                if not row:
                    continue
                lt_uuid, pol_uuid = row

                alloc_uuid = det_uuid(f"{tid}:{emp_uuid}:alloc:{lt_name}:{year}")
                accrued = allotted - used
                alloc_batch.append((
                    alloc_uuid, tid, emp_uuid, lt_uuid,
                    leave_year_str, yr_start, yr_end,
                    allotted, accrued,
                    Decimal("0.00"), None,  # carry_forward
                    Decimal("1.0000"), None, None,  # pro_rate, last_accrued, last_reset
                    pol_uuid
                ))

        if alloc_batch:
            execute_batch(cur,
                """
                INSERT INTO core.leave_allocation
                    (id, tenant_id, employee_id, leave_type_id, leave_year, year_start_date, year_end_date,
                     entitlement_days, accrued_days, carried_forward_days, carry_forward_expires_on,
                     pro_rate_factor, last_accrued_on, last_reset_on, policy_id, created_by, updated_by)
                VALUES (%s,%s,%s,%s,%s,%s,%s, %s,%s,%s,%s, %s,%s,%s,%s,'system','system')
                ON CONFLICT (tenant_id, employee_id, leave_type_id, leave_year) DO UPDATE SET
                    entitlement_days=EXCLUDED.entitlement_days,
                    accrued_days=EXCLUDED.accrued_days,
                    updated_at=CURRENT_TIMESTAMP
                """, alloc_batch, page_size=200)
            conn.commit()
        total += len(alloc_batch)
        logger.info(f"✓ {len(alloc_batch)} leave allocations inserted ({alloc_skip} skipped).")

        # ── 6. Leave Requests ─────────────────────────────────────────────────
        logger.info(f"Loading {len(leave_reqs_raw)} leave requests...")
        req_batch = []
        req_skip  = 0

        STATUS_MAP = {
            "APPROVED": "APPROVED", "REJECTED": "REJECTED",
            "PENDING":  "PENDING",  "CANCELLED": "CANCELLED",
            "APPROV":   "APPROVED", "REJECT": "REJECTED",
        }

        for req in leave_reqs_raw:
            emp_id_str = str(req.get("employee_id") or "").strip()
            emp = emp_by_hrms_id.get(emp_id_str)
            if not emp:
                logger.warning(f"  Req skip: employee_id='{emp_id_str}' not in merged employees.")
                req_skip += 1
                continue
            tid      = emp["tenant_id"]
            emp_uuid = emp["id"]

            from_date = parse_date(req.get("from_date"))
            to_date   = parse_date(req.get("to_date"))
            if not from_date or not to_date:
                req_skip += 1
                continue
            if to_date < from_date:
                to_date = from_date

            is_half   = parse_bit(req.get("is_half_day"))
            half_per  = req.get("half_day_period") if is_half else None
            if half_per and half_per not in ("first", "second"):
                half_per = "first"

            raw_status   = str(req.get("hr_status") or req.get("reporting_manager_status") or "PENDING").strip()
            final_status = STATUS_MAP.get(raw_status.upper(), "PENDING")

            working_days = Decimal(str(req.get("total_days") or "0.50" if is_half else "1.00"))

            # Pick any active leave type for this tenant (we have no leave_type FK in source)
            cur.execute(
                "SELECT id FROM core.leave_type WHERE tenant_id=%s AND is_active=TRUE LIMIT 1",
                (tid,)
            )
            lt_row = cur.fetchone()
            if not lt_row:
                req_skip += 1
                continue
            lt_uuid = lt_row[0]

            req_uuid = det_uuid(f"{tid}:leave_request:{req['id']}")
            req_batch.append((
                req_uuid, tid, emp_uuid, lt_uuid,
                from_date, to_date, is_half, half_per,
                working_days, req.get("reason"), final_status, False
            ))

        if req_batch:
            execute_batch(cur,
                """
                INSERT INTO core.leave_request
                    (id, tenant_id, employee_id, leave_type_id, from_date, to_date,
                     is_half_day, half_day_period, working_days, reason, status, on_behalf,
                     created_by, updated_by)
                VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'system','system')
                ON CONFLICT DO NOTHING
                """, req_batch, page_size=100)
            conn.commit()
        total += len(req_batch)
        logger.info(f"✓ {len(req_batch)} leave requests inserted ({req_skip} skipped).")

        # ── Ledger: complete ──────────────────────────────────────────────────
        cur.execute(
            "UPDATE core.migration_ledger SET records_processed=%s,status=%s,completed_at=%s WHERE id=%s",
            (total, "SUCCESS", datetime.now(timezone.utc), ledger_id)
        )
        conn.commit()
        logger.info(f"=== LEAVE & HOLIDAY LOAD COMPLETE — {total} total records ===")

    except Exception as e:
        conn.rollback()
        logger.error(f"Load failed: {e}", exc_info=True)
        cur.execute(
            "UPDATE core.migration_ledger SET status=%s,error_message=%s,completed_at=%s WHERE id=%s",
            ("FAILED", str(e), datetime.now(timezone.utc), ledger_id)
        )
        conn.commit()
        raise
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    run_loader()
