"""
Loss-of-Pay (LOP) Mismatch Log Generator (W-70 Task 2)
Computes variance between HRMS recorded leave requests and Payroll processed LOP deductions.
Inserts discrepancies (where variance_days > 0) into core.lop_mismatch_log for Finance sign-off.
- Uses deterministic uuid5 IDs for idempotency.
- ON CONFLICT DO UPDATE preserves existing status (never resets APPROVED_BY_FINANCE).
- Strictly uses Python Decimal (no floating point conversions).
- Exports CSV to staging/lop_mismatch_<tenant>.csv for Finance review.
"""

import os
import sys
import json
import uuid
import csv
import logging
from decimal import Decimal, ROUND_HALF_UP
from datetime import datetime, timezone
from collections import defaultdict
import psycopg2
from psycopg2.extras import execute_batch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("lop_mismatch_generator")

NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")

def get_pg_connection():
    host = os.environ.get("TARGET_DB_HOST", "localhost")
    port = int(os.environ.get("TARGET_DB_PORT", "5432"))
    database = os.environ.get("TARGET_DB_NAME", "infinevo")
    user = os.environ.get("TARGET_DB_USER", "migration_user")
    password = os.environ.get("TARGET_DB_PASSWORD", "local_migration_pw")
    
    return psycopg2.connect(
        host=host,
        port=port,
        dbname=database,
        user=user,
        password=password
    )

def to_decimal(val, decimals=1):
    if val is None or val == "":
        val = 0
    d = Decimal(str(val))
    if decimals == 1:
        return d.quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)
    elif decimals == 2:
        return d.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    return d

def run_generator():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    staging_dir = os.path.join(base_dir, "staging")
    
    logger.info("=== Starting LOP Mismatch Log Generation (W-70 Task 2) ===")
    
    def load_stg(fname, default=None):
        fpath = os.path.join(staging_dir, fname)
        if not os.path.exists(fpath):
            return default if default is not None else []
        with open(fpath, "r", encoding="utf-8") as f:
            return json.load(f)

    merged_employees = load_stg("stg_merged_employees.json")
    emp_payruns = load_stg("stg_payroll_employee_payruns.json")
    payruns = load_stg("stg_payroll_payruns.json")
    leave_requests = load_stg("stg_hrms_leave_requests.json")

    # Map payrun id -> pay_period (YYYY-MM)
    payrun_periods = {}
    for pr in payruns:
        pr_id = pr.get("id") or pr.get("payrunId")
        start_date = pr.get("payPeriodStartDate")
        if pr_id and start_date and len(str(start_date)) >= 7:
            payrun_periods[str(pr_id)] = str(start_date)[:7]

    emp_by_num = {}
    for e in merged_employees:
        if e.get("employee_number"):
            emp_by_num[str(e["employee_number"]).strip()] = e

    # 1. Aggregate Payroll LOP by (employee_number, work_email, tenant_id, pay_period)
    payroll_lop = defaultdict(lambda: {"payroll_lop_days": Decimal("0.0"), "payroll_deduction": Decimal("0.00")})
    
    for ep in emp_payruns:
        pr_id = str(ep.get("payrunId") or "")
        pay_period = payrun_periods.get(pr_id)
        if not pay_period:
            continue
            
        emp_num = str(ep.get("employeeNumber") or "").strip()
        emp_record = emp_by_num.get(emp_num)
        
        work_email = emp_record.get("work_email") if emp_record else "unknown@infinevocloud.com"
        tenant_id = emp_record.get("tenant_id") if emp_record else "a0000000-0000-0000-0000-000000000001"
        emp_number = emp_record.get("employee_number") if emp_record else (emp_num or f"PAY-{ep.get('id')}")
        
        raw_lop = ep.get("LOP") or ep.get("lwp_days") or 0
        raw_ded = ep.get("lwp_amount") or 0
        
        lop_days = to_decimal(raw_lop, 1)
        deduction = to_decimal(raw_ded, 2)
        
        key = (emp_number, work_email, tenant_id, pay_period)
        payroll_lop[key]["payroll_lop_days"] += lop_days
        payroll_lop[key]["payroll_deduction"] += deduction

    # 2. Aggregate HRMS Unpaid / LOP Leave Requests by (employee, pay_period)
    hrms_lop = defaultdict(lambda: Decimal("0.0"))
    for lr in leave_requests:
        from_date = lr.get("from_date")
        if not from_date or len(str(from_date)) < 7:
            continue
        pay_period = str(from_date)[:7]
        
        emp_id = str(lr.get("employee_id") or "").strip()
        emp_record = emp_by_num.get(emp_id)
        
        work_email = emp_record.get("work_email") if emp_record else "unknown@infinevocloud.com"
        tenant_id = emp_record.get("tenant_id") if emp_record else "a0000000-0000-0000-0000-000000000001"
        emp_number = emp_record.get("employee_number") if emp_record else (emp_id or "HRMS-UNKNOWN")
        
        days = to_decimal(lr.get("total_days") or 0, 1)
        key = (emp_number, work_email, tenant_id, pay_period)
        hrms_lop[key] += days

    # 3. Compute Mismatches (Filter strictly to variance_days > 0)
    all_keys = set(payroll_lop.keys()).union(set(hrms_lop.keys()))
    mismatch_rows = []
    csv_rows_by_tenant = defaultdict(list)
    matching_count = 0
    
    for key in sorted(all_keys):
        emp_number, work_email, tenant_id, pay_period = key
        p_info = payroll_lop.get(key, {"payroll_lop_days": Decimal("0.0"), "payroll_deduction": Decimal("0.00")})
        p_days = p_info["payroll_lop_days"]
        p_ded = p_info["payroll_deduction"]
        h_days = hrms_lop.get(key, Decimal("0.0"))
        
        variance = (h_days - p_days).copy_abs()
        
        if variance > Decimal("0.0"):
            # Task 2: Deterministic uuid5 for idempotency
            row_id = str(uuid.uuid5(NAMESPACE, f"{tenant_id}:{emp_number}:{pay_period}"))
            mismatch_rows.append((
                row_id,
                tenant_id,
                emp_number,
                work_email,
                pay_period,
                h_days,
                p_days,
                variance,
                p_ded,
                "PENDING_FINANCE_SIGN_OFF"
            ))
            csv_rows_by_tenant[tenant_id].append({
                "employee_number": emp_number,
                "work_email": work_email,
                "pay_period": pay_period,
                "hrms_unpaid_days": str(h_days),
                "payroll_lop_days": str(p_days),
                "variance_days": str(variance),
                "payroll_processed_deduction": str(p_ded),
                "status": "PENDING_FINANCE_SIGN_OFF"
            })
        else:
            matching_count += 1

    logger.info(f"Identified {len(mismatch_rows)} variance rows (>0 days) and {matching_count} matching rows.")
    
    # 4. Insert into core.lop_mismatch_log with ON CONFLICT DO UPDATE (Preserving status)
    conn = get_pg_connection()
    conn.autocommit = False
    cur = conn.cursor()
    
    ledger_id = str(uuid.uuid4())
    started_at = datetime.now(timezone.utc)
    tenant_id_primary = "a0000000-0000-0000-0000-000000000001"
    
    try:
        # Record start in migration_ledger
        cur.execute(
            """
            INSERT INTO core.migration_ledger (id, tenant_id, step_name, records_processed, status, started_at)
            VALUES (%s, %s, %s, %s, %s, %s)
            """,
            (ledger_id, tenant_id_primary, "lop_mismatch", 0, "RUNNING", started_at)
        )
        conn.commit()
        
        if mismatch_rows:
            execute_batch(
                cur,
                """
                INSERT INTO core.lop_mismatch_log (
                    id, tenant_id, employee_number, work_email, pay_period,
                    hrms_unpaid_days, payroll_lop_days, variance_days,
                    payroll_processed_deduction, status
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (id) DO UPDATE SET
                    hrms_unpaid_days = EXCLUDED.hrms_unpaid_days,
                    payroll_lop_days = EXCLUDED.payroll_lop_days,
                    variance_days = EXCLUDED.variance_days,
                    payroll_processed_deduction = EXCLUDED.payroll_processed_deduction
                """,
                mismatch_rows,
                page_size=200
            )
            conn.commit()
            
        completed_at = datetime.now(timezone.utc)
        cur.execute(
            """
            UPDATE core.migration_ledger
            SET records_processed = %s, status = %s, completed_at = %s
            WHERE id = %s
            """,
            (len(mismatch_rows), "DONE", completed_at, ledger_id)
        )
        conn.commit()
        logger.info(f"✓ Saved {len(mismatch_rows)} LOP Mismatch entries to core.lop_mismatch_log.")

        # 5. Export CSV per tenant for Finance review
        for tid, rows in csv_rows_by_tenant.items():
            csv_path = os.path.join(staging_dir, f"lop_mismatch_{tid}.csv")
            fieldnames = [
                "employee_number", "work_email", "pay_period",
                "hrms_unpaid_days", "payroll_lop_days", "variance_days",
                "payroll_processed_deduction", "status"
            ]
            with open(csv_path, "w", newline="", encoding="utf-8") as f:
                writer = csv.DictWriter(f, fieldnames=fieldnames)
                writer.writeheader()
                writer.writerows(rows)
            logger.info(f"✓ Exported Finance review CSV to {csv_path}")
        
    except Exception as e:
        conn.rollback()
        logger.error(f"LOP Mismatch generation failed: {e}", exc_info=True)
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
    run_generator()
