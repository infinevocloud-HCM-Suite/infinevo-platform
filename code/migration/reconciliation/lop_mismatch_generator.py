"""
Loss-of-Pay (LOP) Mismatch Log Generator (W-70 / W-68.2)
Computes variance between HRMS recorded leave requests and Payroll processed LOP deductions.
Inserts discrepancies into core.lop_mismatch_log for Finance sign-off.
Strictly uses Python Decimal for currency and days calculations.
"""

import os
import sys
import json
import uuid
import logging
from decimal import Decimal, ROUND_HALF_UP
from datetime import datetime, timezone
from collections import defaultdict
import psycopg2
from psycopg2.extras import execute_batch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("lop_mismatch_generator")

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

def run_generator():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    staging_dir = os.path.join(base_dir, "staging")
    
    logger.info("=== Starting LOP Mismatch Log Generation (W-70) ===")
    
    # Load staged datasets
    with open(os.path.join(staging_dir, "stg_merged_employees.json"), "r", encoding="utf-8") as f:
        merged_employees = json.load(f)
        
    with open(os.path.join(staging_dir, "stg_payroll_employee_payruns.json"), "r", encoding="utf-8") as f:
        emp_payruns = json.load(f)
        
    with open(os.path.join(staging_dir, "stg_payroll_payruns.json"), "r", encoding="utf-8") as f:
        payruns = json.load(f)
        
    with open(os.path.join(staging_dir, "stg_hrms_leave_requests.json"), "r", encoding="utf-8") as f:
        leave_requests = json.load(f)
        
    # Map payrun id -> pay_period (YYYY-MM)
    payrun_periods = {}
    for pr in payruns:
        pr_id = pr.get("id") or pr.get("payrunId")
        start_date = pr.get("payPeriodStartDate")
        if pr_id and start_date and len(str(start_date)) >= 7:
            payrun_periods[str(pr_id)] = str(start_date)[:7]

    # Index merged employees by email and employee_number
    emp_by_email = {}
    emp_by_num = {}
    for e in merged_employees:
        if e.get("work_email"):
            emp_by_email[e["work_email"].lower().strip()] = e
        if e.get("employee_number"):
            emp_by_num[e["employee_number"]] = e

    # 1. Aggregate Payroll LOP by (employee, pay_period)
    # Key: (employee_number, work_email, tenant_id, pay_period) -> {"payroll_lop_days": Decimal, "payroll_deduction": Decimal}
    payroll_lop = defaultdict(lambda: {"payroll_lop_days": Decimal("0.0"), "payroll_deduction": Decimal("0.00")})
    
    for ep in emp_payruns:
        pr_id = str(ep.get("payrunId") or "")
        pay_period = payrun_periods.get(pr_id)
        if not pay_period:
            continue
            
        emp_num = ep.get("employeeNumber")
        emp_record = emp_by_num.get(emp_num)
        
        work_email = emp_record.get("work_email") if emp_record else "unknown@infinevocloud.com"
        tenant_id = emp_record.get("tenant_id") if emp_record else "00000000-0000-0000-0000-000000000001"
        emp_number = emp_record.get("employee_number") if emp_record else (emp_num or f"PAY-{ep.get('id')}")
        
        raw_lop = ep.get("LOP") or ep.get("lwp_days") or 0.0
        raw_ded = ep.get("lwp_amount") or 0.0
        
        lop_days = Decimal(str(raw_lop)).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)
        deduction = Decimal(str(raw_ded)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
        
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
        
        emp_id = lr.get("employee_id")
        emp_record = emp_by_num.get(emp_id)
        
        work_email = emp_record.get("work_email") if emp_record else "unknown@infinevocloud.com"
        tenant_id = emp_record.get("tenant_id") if emp_record else "00000000-0000-0000-0000-000000000001"
        emp_number = emp_record.get("employee_number") if emp_record else (emp_id or "HRMS-UNKNOWN")
        
        days = Decimal(str(lr.get("total_days") or 0.0)).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)
        key = (emp_number, work_email, tenant_id, pay_period)
        hrms_lop[key] += days

    # 3. Compute Mismatches & Variances
    all_keys = set(payroll_lop.keys()).union(set(hrms_lop.keys()))
    mismatch_rows = []
    
    for key in all_keys:
        emp_number, work_email, tenant_id, pay_period = key
        p_info = payroll_lop.get(key, {"payroll_lop_days": Decimal("0.0"), "payroll_deduction": Decimal("0.00")})
        p_days = p_info["payroll_lop_days"]
        p_ded = p_info["payroll_deduction"]
        h_days = hrms_lop.get(key, Decimal("0.0"))
        
        variance = (h_days - p_days).copy_abs()
        
        # Log records where variance exists or LOP deduction was processed
        if variance > Decimal("0.0") or p_days > Decimal("0.0") or h_days > Decimal("0.0"):
            mismatch_rows.append((
                str(uuid.uuid4()),
                tenant_id,
                emp_number,
                work_email,
                pay_period,
                float(h_days),
                float(p_days),
                float(variance),
                float(p_ded),
                "PENDING_FINANCE_SIGN_OFF"
            ))

    logger.info(f"Identified {len(mismatch_rows)} Loss-of-Pay audit entries across pay periods.")
    
    # 4. Insert into core.lop_mismatch_log
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
            (ledger_id, tenant_id_primary, "LOP_MISMATCH_RECONCILIATION", 0, "RUNNING", started_at)
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
                """,
                mismatch_rows,
                page_size=200
            )
            
        completed_at = datetime.now(timezone.utc)
        cur.execute(
            """
            UPDATE core.migration_ledger
            SET records_processed = %s, status = %s, completed_at = %s
            WHERE id = %s
            """,
            (len(mismatch_rows), "SUCCESS", completed_at, ledger_id)
        )
        conn.commit()
        logger.info(f"=== Successfully Created {len(mismatch_rows)} LOP Mismatch Log Entries! Ledger Updated ===")
        
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
