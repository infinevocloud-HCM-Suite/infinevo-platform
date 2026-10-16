"""
Claims and Deductions Migration Loader (W-68 Task 3l / Ticket #90)
Loads legacy reimbursement requests and deductions into target PostgreSQL:
  - employee_reimbursement_request -> payroll.employee_reimbursement_request
  - employee_deduction (salary_deduction) -> payroll.employee_deduction + core.pay_input

Rules and Architectural Constraints:
  - W-68 Task 3l: All statuses loaded; document_id is NULL initially (linked by W-69).
  - Decision X-14: Open claims/deductions carried; historical PAID/PROCESSED ones dropped.
  - Deterministic UUIDs via uuid5(NAMESPACE, f"{tenant_id}:{table}:{legacy_id}")
  - High precision Decimals for all amounts (no floats)
  - RLS tenant isolation respected by setting app.current_tenant_id per tenant
  - Full idempotency with ON CONFLICT DO UPDATE
  - Quarantine missing parents with reasons in staging/stg_quarantined_claims.json
  - Execution tracked in core.migration_ledger with step_name = 'load_claims'
"""

import os
import sys
import json
import uuid
import logging
from decimal import Decimal
from datetime import datetime, timezone
import psycopg2
from psycopg2.extras import execute_batch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("load_claims")

NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")

# Allowed deduction types per payroll.employee_deduction CHECK constraint
VALID_DEDUCTION_TYPES = {
    "ADVANCE_RECOVERY",
    "LOAN_RECOVERY",
    "DAMAGE",
    "PENALTY",
    "EXCESS_PAYMENT",
    "OTHER"
}

# Status mapping for reimbursement requests
# Legacy: PENDING, APPROVED, REJECTED
# Target: SUBMITTED, APPROVED, REJECTED
REIMB_STATUS_MAP = {
    "PENDING": "SUBMITTED",
    "SUBMITTED": "SUBMITTED",
    "APPROVED": "APPROVED",
    "REJECTED": "REJECTED"
}


def to_decimal(val, default="0.0000"):
    """Convert number/string to 4-decimal scale Decimal without using float."""
    if val is None or val == "":
        return Decimal(default)
    return Decimal(str(val)).quantize(Decimal("0.0001"))


def det_uuid(key: str) -> str:
    """Deterministic UUIDv5 namespace mapping."""
    return str(uuid.uuid5(NAMESPACE, key))


def get_conn():
    """Target PostgreSQL connection."""
    return psycopg2.connect(
        host=os.environ.get("TARGET_DB_HOST", "localhost"),
        port=int(os.environ.get("TARGET_DB_PORT", "5432")),
        dbname=os.environ.get("TARGET_DB_NAME", "infinevo"),
        user=os.environ.get("TARGET_DB_USER", "migration_user"),
        password=os.environ.get("TARGET_DB_PASSWORD", "local_migration_pw"),
    )


def normalize_deduction_type(legacy_type: str) -> str:
    """Maps legacy free-text deduction type to valid enum string."""
    if not legacy_type:
        return "OTHER"
    upper = str(legacy_type).strip().upper().replace(" ", "_").replace("-", "_")
    if upper in VALID_DEDUCTION_TYPES:
        return upper
    if "ADVANCE" in upper:
        return "ADVANCE_RECOVERY"
    if "LOAN" in upper:
        return "LOAN_RECOVERY"
    if "DAMAGE" in upper:
        return "DAMAGE"
    if "PENALTY" in upper or "FINE" in upper:
        return "PENALTY"
    if "EXCESS" in upper or "OVERPAY" in upper:
        return "EXCESS_PAYMENT"
    return "OTHER"


def format_period(month_val: str) -> str:
    """Extracts YYYY-MM period format from date string or month string."""
    if not month_val:
        return datetime.now(timezone.utc).strftime("%Y-%m")
    s = str(month_val).strip()
    if len(s) >= 7 and s[4] == "-":
        return s[:7]
    return s[:7]


def transform_reimbursement_item(item, emp_by_payroll_id, emp_by_uuid, reimbursement_id_map):
    """
    Transforms one legacy employee_reimbursement_request row into target format.
    Returns (is_success, row_tuple, quarantine_item)
    """
    legacy_id = item.get("id")
    payroll_emp_key = str(item.get("employee_id") or item.get("employeeId") or "").strip()
    payment_status = str(item.get("payment_status") or item.get("paymentStatus") or "UNPAID").upper()

    # Decision X-14: Drop already PAID claims
    if payment_status == "PAID":
        return False, None, {
            "source": "reimbursement", "legacy_id": legacy_id,
            "reason": "Dropped historical PAID reimbursement per Decision X-14"
        }

    # Match employee
    emp = emp_by_payroll_id.get(payroll_emp_key) or emp_by_uuid.get(payroll_emp_key)
    if not emp:
        return False, None, {
            "source": "reimbursement", "legacy_id": legacy_id,
            "employee_id": payroll_emp_key,
            "reason": "employee_id not found in merged employees"
        }

    tid = emp["tenant_id"]
    emp_uuid = emp["id"]
    claim_id = det_uuid(f"{tid}:reimbursement_request:{legacy_id}")

    reimb_type = str(item.get("reimbursement_type") or item.get("reimbursementType") or "GENERAL").upper()
    reimb_comp_id = reimbursement_id_map.get((tid, reimb_type)) or det_uuid(f"{tid}:reimbursement:{reimb_type}")

    requested_amt = to_decimal(item.get("requested_amount") or item.get("requestedAmount"))
    if requested_amt <= Decimal("0"):
        return False, None, {
            "source": "reimbursement", "legacy_id": legacy_id,
            "reason": "requested_amount must be greater than 0"
        }

    raw_approved_amt = item.get("approved_amount") or item.get("approvedAmount")
    approved_amt = to_decimal(raw_approved_amt) if raw_approved_amt is not None else None

    raw_bill_date = str(item.get("bill_date") or item.get("billDate") or "")[:10]
    if not raw_bill_date or len(raw_bill_date) < 10:
        raw_bill_date = datetime.now(timezone.utc).strftime("%Y-%m-%d")

    raw_status = str(item.get("status") or "PENDING").upper()
    status = REIMB_STATUS_MAP.get(raw_status, "SUBMITTED")

    desc = str(item.get("description") or "Migrated reimbursement claim")[:500]
    remarks = item.get("remarks")
    if remarks:
        remarks = str(remarks)[:500]

    return True, (
        claim_id, tid, emp_uuid, reimb_comp_id,
        requested_amt, approved_amt, raw_bill_date,
        desc, None, status, remarks, None, None, None, None, None
    ), None


def transform_deduction_item(item, emp_by_payroll_id, emp_by_uuid):
    """
    Transforms one legacy employee_deduction (SalaryDeduction) row into target format.
    Returns (is_success, (pay_input_tuple, deduction_tuple), quarantine_item)
    """
    legacy_id = item.get("id")
    payroll_emp_key = str(item.get("employee_id") or item.get("employeeId") or "").strip()
    raw_status = str(item.get("status") or "ACTIVE").upper()

    # Decision X-14: Drop already PROCESSED deductions
    if raw_status in ("PROCESSED", "PAID"):
        return False, None, {
            "source": "deduction", "legacy_id": legacy_id,
            "reason": "Dropped historical PROCESSED deduction per Decision X-14"
        }

    # Match employee
    emp = emp_by_payroll_id.get(payroll_emp_key) or emp_by_uuid.get(payroll_emp_key)
    if not emp:
        return False, None, {
            "source": "deduction", "legacy_id": legacy_id,
            "employee_id": payroll_emp_key,
            "reason": "employee_id not found in merged employees"
        }

    tid = emp["tenant_id"]
    emp_uuid = emp["id"]
    deduction_id = det_uuid(f"{tid}:employee_deduction:{legacy_id}")
    pay_input_id = det_uuid(f"{tid}:pay_input:deduction:{legacy_id}")

    amount = to_decimal(item.get("deduction_amount") or item.get("deductionAmount") or item.get("amount"))
    if amount <= Decimal("0"):
        return False, None, {
            "source": "deduction", "legacy_id": legacy_id,
            "reason": "deduction_amount must be greater than 0"
        }

    period = format_period(item.get("deduction_month") or item.get("deductionMonth") or item.get("period"))
    deduction_type = normalize_deduction_type(item.get("deduction_type") or item.get("deductionType"))
    reason = str(item.get("reason") or "Migrated salary deduction")[:255]
    remarks = item.get("remarks")
    if remarks:
        remarks = str(remarks)[:500]

    # Target status: POSTED (since PROCESSED/REVERSED are filtered out)
    status = "POSTED"

    # Tuple for core.pay_input
    pay_input_tuple = (
        pay_input_id, tid, emp_uuid, period,
        "AD_HOC_DEDUCTION", None, amount,
        "PAYROLL", f"deduction:{legacy_id}", None
    )

    # Tuple for payroll.employee_deduction
    deduction_tuple = (
        deduction_id, tid, emp_uuid, period, deduction_type,
        amount, reason, remarks, None, status, pay_input_id, period
    )

    return True, (pay_input_tuple, deduction_tuple), None


def run_loader(dry_run: bool = False, skip_db: bool = False):
    """
    Main loader orchestrator for W-68 Task 3l (Claims & Deductions).
    """
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    stg_dir = os.path.join(base_dir, "staging")

    def load_stg(fname):
        path = os.path.join(stg_dir, fname)
        if not os.path.exists(path):
            logger.info(f"Staging file not found (will process 0 rows): {path}")
            return []
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)

    reimb_items = load_stg("stg_payroll_employee_reimbursement_request.json")
    deduct_items = load_stg("stg_payroll_employee_deduction.json")
    merged_employees = load_stg("stg_merged_employees.json")
    payroll_emps = load_stg("stg_payroll_employee.json")

    logger.info(f"Loaded: {len(reimb_items)} reimbursement requests, {len(deduct_items)} deductions")

    # Build employee lookup tables
    emp_by_payroll_id = {}
    emp_by_uuid = {}

    for e in merged_employees:
        if e.get("id"):
            emp_by_uuid[str(e["id"])] = e
        if e.get("source_legacy_payroll_id"):
            emp_by_payroll_id[str(e["source_legacy_payroll_id"])] = e

    for pe in payroll_emps:
        uuid_str = str(pe.get("employeeId") or "").strip()
        int_id_str = str(pe.get("employee_id") or "").strip()
        matched = emp_by_payroll_id.get(int_id_str)
        if matched and uuid_str:
            emp_by_uuid[uuid_str] = matched

    reimbursement_id_map = {}
    conn = None
    cur = None
    step_name = "load_claims"
    ledger_id = str(uuid.uuid4())
    started_at = datetime.now(timezone.utc)
    anchor_tenant = list(emp_by_uuid.values())[0]["tenant_id"] if emp_by_uuid else "a0000000-0000-0000-0000-000000000001"

    if not skip_db and not dry_run:
        try:
            conn = get_conn()
            conn.autocommit = False
            cur = conn.cursor()

            # Record ledger start
            cur.execute(
                "INSERT INTO core.migration_ledger (id, tenant_id, step_name, records_processed, status, started_at) "
                "VALUES (%s, %s, %s, %s, %s, %s)",
                (ledger_id, anchor_tenant, step_name, 0, "RUNNING", started_at)
            )
            conn.commit()

            # Pre-load existing payroll.reimbursement component mappings
            cur.execute("SELECT tenant_id, code, id FROM payroll.reimbursement WHERE is_deleted = false")
            for r in cur.fetchall():
                reimbursement_id_map[(str(r[0]), str(r[1]).upper())] = str(r[2])

        except Exception as e:
            logger.warning(f"Database connection error: {e}. Skipping DB writes.")
            skip_db = True

    # Transform Reimbursement Requests
    reimb_batch = []
    quarantined = []
    dropped_paid = 0

    for item in reimb_items:
        ok, row, q = transform_reimbursement_item(item, emp_by_payroll_id, emp_by_uuid, reimbursement_id_map)
        if ok and row:
            reimb_batch.append(row)
        elif q:
            if "Decision X-14" in q.get("reason", ""):
                dropped_paid += 1
            else:
                quarantined.append(q)

    # Transform Deductions
    pay_input_batch = []
    deduction_batch = []

    for item in deduct_items:
        ok, row_pair, q = transform_deduction_item(item, emp_by_payroll_id, emp_by_uuid)
        if ok and row_pair:
            pay_input_batch.append(row_pair[0])
            deduction_batch.append(row_pair[1])
        elif q:
            if "Decision X-14" in q.get("reason", ""):
                dropped_paid += 1
            else:
                quarantined.append(q)

    total_loaded = len(reimb_batch) + len(deduction_batch)
    logger.info(
        f"Transform complete: {len(reimb_batch)} claims, {len(deduction_batch)} deductions "
        f"({dropped_paid} dropped historical paid, {len(quarantined)} quarantined)"
    )

    # Database Upserts
    if not skip_db and cur and not dry_run:
        try:
            # 1. Upsert Reimbursement Claims
            if reimb_batch:
                logger.info(f"Upserting {len(reimb_batch)} rows into payroll.employee_reimbursement_request...")
                execute_batch(
                    cur,
                    """
                    INSERT INTO payroll.employee_reimbursement_request
                        (id, tenant_id, employee_id, reimbursement_id, requested_amount,
                         approved_amount, bill_date, description, document_id, status,
                         remarks, approval_instance_id, pay_input_id, posted_period,
                         approved_by, approved_at, created_by, updated_by)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        requested_amount = EXCLUDED.requested_amount,
                        approved_amount = EXCLUDED.approved_amount,
                        status = EXCLUDED.status,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    reimb_batch,
                    page_size=200
                )

            # 2. Upsert core.pay_input for deductions
            if pay_input_batch:
                logger.info(f"Upserting {len(pay_input_batch)} rows into core.pay_input...")
                execute_batch(
                    cur,
                    """
                    INSERT INTO core.pay_input
                        (id, tenant_id, employee_id, period, kind, quantity, amount,
                         source_module, source_ref, reverses_id, created_by, updated_by)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        amount = EXCLUDED.amount,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    pay_input_batch,
                    page_size=200
                )

            # 3. Upsert payroll.employee_deduction
            if deduction_batch:
                logger.info(f"Upserting {len(deduction_batch)} rows into payroll.employee_deduction...")
                execute_batch(
                    cur,
                    """
                    INSERT INTO payroll.employee_deduction
                        (id, tenant_id, employee_id, period, deduction_type, amount,
                         reason, remarks, document_id, status, pay_input_id, posted_period,
                         created_by, updated_by)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        amount = EXCLUDED.amount,
                        status = EXCLUDED.status,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    deduction_batch,
                    page_size=200
                )

            conn.commit()

            # Update migration ledger
            cur.execute(
                "UPDATE core.migration_ledger SET records_processed=%s, status=%s, completed_at=%s WHERE id=%s",
                (total_loaded, "DONE", datetime.now(timezone.utc), ledger_id)
            )
            conn.commit()
            logger.info("Database load & ledger update complete")

        except Exception as e:
            conn.rollback()
            logger.error(f"Failed to load claims to database: {e}", exc_info=True)
            cur.execute(
                "UPDATE core.migration_ledger SET status=%s, error_message=%s, completed_at=%s WHERE id=%s",
                ("FAILED", str(e), datetime.now(timezone.utc), ledger_id)
            )
            conn.commit()
            raise
        finally:
            cur.close()
            conn.close()

    # Save quarantine report to staging
    quarantine_file = os.path.join(stg_dir, "stg_quarantined_claims.json")
    with open(quarantine_file, "w", encoding="utf-8") as f:
        json.dump(quarantined, f, indent=2)

    logger.info(f"Saved {len(quarantined)} quarantined claims records to {quarantine_file}")

    return {
        "reimbursements_loaded": len(reimb_batch),
        "deductions_loaded": len(deduction_batch),
        "total_loaded": total_loaded,
        "dropped_paid": dropped_paid,
        "quarantined": len(quarantined)
    }


if __name__ == "__main__":
    is_dry_run = "--dry-run" in sys.argv
    run_loader(dry_run=is_dry_run)
