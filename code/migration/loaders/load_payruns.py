"""
PostgreSQL Historical Payruns & Employee Payruns Loader (Step 1)
Loads:
- payroll.payrun (155 staging + 7 synthesized historical containers for orphan lines)
- payroll.employee_payrun (664 lines)
- Updates core.migration_ledger
"""

import os
import sys
import json
import uuid
import logging
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from collections import defaultdict
import psycopg2
from psycopg2.extras import execute_batch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("load_payruns")

def get_pg_connection():
    host = os.environ.get("TARGET_DB_HOST", "localhost")
    port = int(os.environ.get("TARGET_DB_PORT", "5432"))
    database = os.environ.get("TARGET_DB_NAME", "infinevo")
    user = os.environ.get("TARGET_DB_USER", "migration_user")
    password = os.environ.get("TARGET_DB_PASSWORD", "local_migration_pw")
    
    logger.info(f"Connecting to PostgreSQL {host}:{port}/{database} as {user}...")
    return psycopg2.connect(
        host=host,
        port=port,
        dbname=database,
        user=user,
        password=password
    )

def to_decimal(val, decimals=4):
    if val is None:
        val = 0
    d = Decimal(str(val))
    if decimals == 4:
        return d.quantize(Decimal('0.0001'), rounding=ROUND_HALF_UP)
    elif decimals == 2:
        return d.quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
    return d

def parse_period(p):
    start_date = p.get('payPeriodStartDate')
    if start_date:
        return str(start_date)[:7]
    proc = p.get('processingPeriod')
    if proc:
        dt = datetime.strptime(proc.strip(), "%B %Y")
        return dt.strftime("%Y-%m")
    pay_date = p.get('payDate')
    if pay_date:
        return str(pay_date)[:7]
    return "2025-01"

def run_loader():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    payruns_file = os.path.join(base_dir, "staging", "stg_payroll_payruns.json")
    emp_payruns_file = os.path.join(base_dir, "staging", "stg_payroll_employee_payruns.json")
    merged_emp_file = os.path.join(base_dir, "staging", "stg_merged_employees.json")
    org_mapping_file = os.path.join(base_dir, "config", "org_mapping.json")
    
    with open(payruns_file, "r", encoding="utf-8") as f:
        stg_payruns = json.load(f)
    with open(emp_payruns_file, "r", encoding="utf-8") as f:
        stg_emp_payruns = json.load(f)
    with open(merged_emp_file, "r", encoding="utf-8") as f:
        merged_employees = json.load(f)
    with open(org_mapping_file, "r", encoding="utf-8") as f:
        org_map = json.load(f)
        
    payroll_to_emp = {e['source_legacy_payroll_id']: e for e in merged_employees if e.get('source_legacy_payroll_id')}
    logger.info(f"Loaded {len(stg_payruns)} payruns, {len(stg_emp_payruns)} employee payruns, {len(payroll_to_emp)} mapped employees.")

    # Group lines by payrun_ref_id
    lines_by_payrun = defaultdict(list)
    for l in stg_emp_payruns:
        lines_by_payrun[l.get('payrun_ref_id')].append(l)

    payrun_id_map = {} # legacy_id -> target_uuid
    payrun_tenant_map = {} # legacy_id -> tenant_id
    payrun_batch = []

    # 1. Process 155 staging payruns
    for p in stg_payruns:
        legacy_id = p['id']
        org_id = str(p.get('organizationId'))
        if org_id not in org_map:
            raise ValueError(f"Organization ID {org_id} not found in org_mapping.json")
        tenant_id = org_map[org_id]['tenant_id']
        period = parse_period(p)
        period_start = p.get('payPeriodStartDate')
        period_end = p.get('payPeriodEndDate')
        cutoff_date = period_end
        pay_date = p.get('payDate')

        legacy_status = p.get('status') or 'COMPLETED'
        if legacy_status == 'COMPLETED':
            status = 'PAID'
            paid_on = pay_date
            paid_at = datetime.fromisoformat(f"{pay_date}T12:00:00+00:00")
            approved_at = paid_at
        elif legacy_status == 'APPROVED':
            status = 'APPROVED'
            paid_on = None
            paid_at = None
            approved_at = datetime.fromisoformat(f"{p.get('approvedDate') or pay_date}T12:00:00+00:00")
        else:
            status = 'DRAFT'
            paid_on = None
            paid_at = None
            approved_at = None

        child_lines = lines_by_payrun.get(legacy_id, [])
        included_count = len(child_lines)
        skipped_count = 0

        if child_lines:
            tot_gross = sum(to_decimal(l.get('totalEarnings')) for l in child_lines)
            tot_ded = sum(to_decimal(l.get('totalDeductions')) for l in child_lines)
            tot_net = sum(to_decimal(l.get('netPay')) for l in child_lines)
            neg_count = sum(1 for l in child_lines if to_decimal(l.get('netPay')) < 0)
        else:
            tot_gross = to_decimal(p.get('totalPayrollCost') or 0)
            tot_ded = to_decimal(p.get('totalDeductions') or 0)
            tot_net = to_decimal(p.get('totalNetPay') or 0)
            neg_count = 0

        p_uuid = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{tenant_id}:payrun:{legacy_id}"))
        payrun_id_map[legacy_id] = p_uuid
        payrun_tenant_map[legacy_id] = tenant_id

        payrun_batch.append((
            p_uuid,
            tenant_id,
            period,
            period_start,
            period_end,
            cutoff_date,
            pay_date,
            'REGULAR',
            status,
            included_count,
            skipped_count,
            tot_gross,
            tot_ded,
            tot_net,
            neg_count,
            None, # notes
            approved_at,
            paid_at,
            paid_on
        ))

    # 2. Synthesize containers for the 7 unparented legacy payruns (17 orphan lines)
    orphan_meta = {
        291: {'period': '2026-04', 'start': '2026-04-01', 'end': '2026-04-29', 'pay': '2026-04-30'},
        292: {'period': '2026-04', 'start': '2026-04-01', 'end': '2026-04-29', 'pay': '2026-04-30'},
        296: {'period': '2026-05', 'start': '2026-05-01', 'end': '2026-05-31', 'pay': '2026-05-31'},
        315: {'period': '2025-03', 'start': '2025-03-01', 'end': '2025-03-31', 'pay': '2025-03-31'},
        351: {'period': '2024-09', 'start': '2024-09-01', 'end': '2024-09-30', 'pay': '2024-09-30'},
        352: {'period': '2024-10', 'start': '2024-10-01', 'end': '2024-10-31', 'pay': '2024-10-31'},
        353: {'period': '2024-11', 'start': '2024-11-01', 'end': '2024-11-30', 'pay': '2024-11-30'}
    }

    for ref_id, meta in orphan_meta.items():
        child_lines = lines_by_payrun.get(ref_id, [])
        if not child_lines:
            continue
        first_emp = payroll_to_emp[child_lines[0]['employee_ref_id']]
        tenant_id = first_emp['tenant_id']
        period = meta['period']
        period_start = meta['start']
        period_end = meta['end']
        cutoff_date = period_end
        pay_date = meta['pay']
        status = 'PAID'
        paid_on = pay_date
        paid_at = datetime.fromisoformat(f"{pay_date}T12:00:00+00:00")
        approved_at = paid_at
        notes = f"Synthesized historical container for legacy unparented employee payrun lines (Legacy Payrun Ref ID: {ref_id})"

        included_count = len(child_lines)
        skipped_count = 0
        tot_gross = sum(to_decimal(l.get('totalEarnings')) for l in child_lines)
        tot_ded = sum(to_decimal(l.get('totalDeductions')) for l in child_lines)
        tot_net = sum(to_decimal(l.get('netPay')) for l in child_lines)
        neg_count = sum(1 for l in child_lines if to_decimal(l.get('netPay')) < 0)

        p_uuid = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{tenant_id}:payrun:{ref_id}"))
        payrun_id_map[ref_id] = p_uuid
        payrun_tenant_map[ref_id] = tenant_id

        payrun_batch.append((
            p_uuid,
            tenant_id,
            period,
            period_start,
            period_end,
            cutoff_date,
            pay_date,
            'OFF_CYCLE',
            status,
            included_count,
            skipped_count,
            tot_gross,
            tot_ded,
            tot_net,
            neg_count,
            notes,
            approved_at,
            paid_at,
            paid_on
        ))

    logger.info(f"Prepared {len(payrun_batch)} payrun headers for insertion.")

    # 3. Process 664 employee payrun lines
    emp_payrun_batch = []
    for l in stg_emp_payruns:
        ref_id = l.get('payrun_ref_id')
        p_uuid = payrun_id_map.get(ref_id)
        if not p_uuid:
            raise ValueError(f"Payrun ref ID {ref_id} not mapped for line {l['id']}")

        emp_ref_id = l.get('employee_ref_id')
        emp = payroll_to_emp.get(emp_ref_id)
        if not emp:
            raise ValueError(f"Employee ref ID {emp_ref_id} not found in merged employees for line {l['id']}")

        employee_id = emp['id']
        tenant_id = emp['tenant_id']

        inclusion_status = 'INCLUDED'
        skip_reason = None

        gross_earnings = to_decimal(l.get('totalEarnings'))
        total_reimbursements = to_decimal(l.get('totalReimbursements'))
        total_benefits = to_decimal(l.get('totalBenefits'))
        total_deductions = to_decimal(l.get('totalDeductions'))
        net_pay = to_decimal(l.get('netPay'))

        paid_days = to_decimal(l.get('paidDays'), decimals=2)
        lop_days = to_decimal(l.get('lwp_days') or 0, decimals=2)
        unpaid_days = Decimal('0.00')

        line_uuid = str(uuid.uuid5(uuid.NAMESPACE_DNS, f"{tenant_id}:employee_payrun:{l['id']}"))

        emp_payrun_batch.append((
            line_uuid,
            tenant_id,
            p_uuid,
            employee_id,
            inclusion_status,
            skip_reason,
            gross_earnings,
            total_reimbursements,
            total_benefits,
            total_deductions,
            net_pay,
            lop_days,
            unpaid_days,
            paid_days
        ))

    logger.info(f"Prepared {len(emp_payrun_batch)} employee payrun lines for insertion.")

    # Execute DB operations
    conn = get_pg_connection()
    conn.autocommit = False
    cur = conn.cursor()

    step_name = "HISTORICAL_PAYRUN_LOAD"
    started_at = datetime.now(timezone.utc)
    ledger_id = str(uuid.uuid4())
    default_tenant = payrun_batch[0][1] if payrun_batch else "a0000000-0000-0000-0000-000000000001"

    try:
        # Record ledger start
        cur.execute(
            """
            INSERT INTO core.migration_ledger (id, tenant_id, step_name, records_processed, status, started_at)
            VALUES (%s, %s, %s, %s, %s, %s)
            """,
            (ledger_id, default_tenant, step_name, 0, "RUNNING", started_at)
        )
        conn.commit()

        # Insert payroll.payrun
        logger.info(f"Executing batch insert of {len(payrun_batch)} payruns into payroll.payrun...")
        execute_batch(
            cur,
            """
            INSERT INTO payroll.payrun (
                id, tenant_id, period, period_start, period_end, cutoff_date, pay_date,
                run_type, status, included_count, skipped_count, total_gross, total_deductions,
                total_net_pay, negative_net_count, notes, approved_at, paid_at, paid_on,
                created_by, updated_by
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s, %s, %s,
                'system', 'system'
            )
            ON CONFLICT (id) DO UPDATE SET
                total_gross = EXCLUDED.total_gross,
                total_deductions = EXCLUDED.total_deductions,
                total_net_pay = EXCLUDED.total_net_pay,
                included_count = EXCLUDED.included_count,
                status = EXCLUDED.status,
                paid_on = EXCLUDED.paid_on,
                updated_at = CURRENT_TIMESTAMP
            """,
            payrun_batch,
            page_size=100
        )

        # Insert payroll.employee_payrun
        logger.info(f"Executing batch insert of {len(emp_payrun_batch)} employee payrun lines into payroll.employee_payrun...")
        execute_batch(
            cur,
            """
            INSERT INTO payroll.employee_payrun (
                id, tenant_id, payrun_id, employee_id, inclusion_status, skip_reason,
                gross_earnings, total_reimbursements, total_benefits, total_deductions,
                net_pay, lop_days, unpaid_days, paid_days, created_by, updated_by
            ) VALUES (
                %s, %s, %s, %s, %s, %s,
                %s, %s, %s, %s,
                %s, %s, %s, %s,
                'system', 'system'
            )
            ON CONFLICT (tenant_id, payrun_id, employee_id) DO UPDATE SET
                gross_earnings = EXCLUDED.gross_earnings,
                total_reimbursements = EXCLUDED.total_reimbursements,
                total_benefits = EXCLUDED.total_benefits,
                total_deductions = EXCLUDED.total_deductions,
                net_pay = EXCLUDED.net_pay,
                lop_days = EXCLUDED.lop_days,
                paid_days = EXCLUDED.paid_days,
                updated_at = CURRENT_TIMESTAMP
            """,
            emp_payrun_batch,
            page_size=200
        )

        total_records = len(payrun_batch) + len(emp_payrun_batch)
        completed_at = datetime.now(timezone.utc)
        cur.execute(
            """
            UPDATE core.migration_ledger
            SET records_processed = %s, status = %s, completed_at = %s
            WHERE id = %s
            """,
            (total_records, "SUCCESS", completed_at, ledger_id)
        )
        conn.commit()
        logger.info(f"=== Successfully Loaded {len(payrun_batch)} Payruns and {len(emp_payrun_batch)} Employee Payruns! Total records: {total_records} ===")

    except Exception as e:
        conn.rollback()
        logger.error(f"Payrun load failed: {e}", exc_info=True)
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
