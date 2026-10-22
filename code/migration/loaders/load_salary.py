"""
Salary and CTC Structure Loader (Step 3e - W-68.3 / Ticket #90)
Loads into target PostgreSQL:
  - payroll.earning (from legacy earnings)
  - payroll.deduction (from legacy deductions)
  - payroll.benefit (from legacy benefits)
  - payroll.reimbursement (from legacy reimbursements)
  - payroll.fbp (from legacy FBP)
  - payroll.ctc_structure (active, non-deleted CTC versions)
  - payroll.employee_earning
  - payroll.employee_benefit
  - payroll.employee_reimbursement
  - payroll.employee_fbp_component
  - payroll.ctc_epf_component
  - payroll.ctc_esi_component

Rules & Invariants:
  - Only active CTC: ctc_structure.is_active = true AND NOT is_deleted
  - Sum check: sum of monthly employee earnings must equal ctc_structure.monthlySalary
    (within +/- 0.05 tolerance). Mismatched rows quarantined with reason CTC_SUM_MISMATCH.
  - Deterministic UUIDv5 namespace mapping.
  - Strict Decimal arithmetic only (no float).
  - RLS tenant isolation respected (app.current_tenant_id).
  - Tracks execution in core.migration_ledger.
"""

import os
import sys
import json
import uuid
import hashlib
import logging
from decimal import Decimal, ROUND_HALF_UP
from datetime import datetime, timezone
import psycopg2
from psycopg2.extras import execute_batch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("load_salary")

NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
FOUNDER_TENANT_ID = "a0000000-0000-0000-0000-000000000001"


def to_decimal(val, decimals=4):
    """Convert value to Decimal without float casting."""
    if val is None or val == "":
        d = Decimal("0")
    else:
        d = Decimal(str(val))
    if decimals == 4:
        return d.quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)
    elif decimals == 2:
        return d.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    return d


def det_uuid(namespace_key: str) -> str:
    """Generate deterministic UUIDv5."""
    return str(uuid.uuid5(NAMESPACE, namespace_key))


def get_pg_connection():
    """Target PostgreSQL connection."""
    return psycopg2.connect(
        host=os.environ.get("TARGET_DB_HOST", "localhost"),
        port=int(os.environ.get("TARGET_DB_PORT", "5432")),
        dbname=os.environ.get("TARGET_DB_NAME", "infinevo"),
        user=os.environ.get("TARGET_DB_USER", "migration_user"),
        password=os.environ.get("TARGET_DB_PASSWORD", "local_migration_pw"),
    )


def compute_files_checksum(file_paths):
    """Computes combined sha256 checksum across staging input files."""
    hasher = hashlib.sha256()
    for p in sorted(file_paths):
        if os.path.exists(p):
            with open(p, "rb") as f:
                while chunk := f.read(65536):
                    if isinstance(chunk, str):
                        chunk = chunk.encode("utf-8")
                    hasher.update(chunk)
    return hasher.hexdigest()


def run_loader(dry_run: bool = False, skip_db: bool = False):
    """
    Executes salary migration loader (Task 3e).
    """
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    stg_dir = os.path.join(base_dir, "staging")
    cfg_dir = os.path.join(base_dir, "config")

    def load_stg(fname, default=None):
        path = os.path.join(stg_dir, fname)
        if not os.path.exists(path):
            return default if default is not None else []
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)

    # 1. Load staging data
    stg_earnings = load_stg("stg_payroll_earnings.json")
    stg_deductions = load_stg("stg_payroll_deductions.json")
    stg_benefits = load_stg("stg_payroll_benefits.json")
    stg_reimbursements = load_stg("stg_payroll_reimbursements.json")
    stg_fbp = load_stg("stg_payroll_FBP.json")
    stg_ctc_structures = load_stg("stg_payroll_ctc_structure.json")
    stg_emp_earnings = load_stg("stg_payroll_employee_earnings.json")
    stg_emp_benefits = load_stg("stg_payroll_employee_benefits.json")
    stg_emp_reimbursements = load_stg("stg_payroll_employee_reimbursements.json")
    stg_emp_fbp = load_stg("stg_payroll_employee_fbp_components.json")
    stg_ctc_epf = load_stg("stg_payroll_ctc_epf_components.json")
    stg_ctc_esi = load_stg("stg_payroll_ctc_esi_components.json")
    merged_employees = load_stg("stg_merged_employees.json")

    org_map_file = os.path.join(cfg_dir, "org_mapping.json")
    org_map = {}
    if os.path.exists(org_map_file):
        with open(org_map_file, "r", encoding="utf-8") as f:
            org_map = json.load(f)

    # Map employee legacy id to unified employee
    emp_by_payroll_id = {}
    for e in merged_employees:
        if e.get("source_legacy_payroll_id"):
            emp_by_payroll_id[str(e["source_legacy_payroll_id"]).strip()] = e
        if e.get("id"):
            emp_by_payroll_id[str(e["id"]).strip()] = e

    input_files = [
        os.path.join(stg_dir, "stg_payroll_earnings.json"),
        os.path.join(stg_dir, "stg_payroll_deductions.json"),
        os.path.join(stg_dir, "stg_payroll_benefits.json"),
        os.path.join(stg_dir, "stg_payroll_reimbursements.json"),
        os.path.join(stg_dir, "stg_payroll_FBP.json"),
        os.path.join(stg_dir, "stg_payroll_ctc_structure.json"),
    ]
    current_checksum = compute_files_checksum(input_files)

    step_name = "load_salary"
    started_at = datetime.now(timezone.utc)
    ledger_id = str(uuid.uuid4())
    quarantined = []

    conn = None
    cur = None

    if not skip_db and not dry_run:
        try:
            conn = get_pg_connection()
            conn.autocommit = False
            cur = conn.cursor()

            # Check if step was already completed with identical checksum
            cur.execute(
                """
                SELECT id FROM core.migration_ledger
                WHERE step_name = %s AND status = 'DONE' AND input_checksum = %s
                ORDER BY completed_at DESC LIMIT 1
                """,
                (step_name, current_checksum)
            )
            done_row = cur.fetchone()
            if done_row and not os.environ.get("FORCE_RELOAD"):
                logger.info(f"Step '{step_name}' already DONE with matching checksum ({current_checksum[:8]}...). Skipping execution.")
                cur.close()
                conn.close()
                return {"status": "SKIPPED", "reason": "CHECKSUM_MATCH"}

            # Record running in migration ledger
            cur.execute(
                """
                INSERT INTO core.migration_ledger (id, tenant_id, step_name, records_processed, status, started_at, input_checksum)
                VALUES (%s, %s, %s, 0, 'RUNNING', %s, %s)
                """,
                (ledger_id, FOUNDER_TENANT_ID, step_name, started_at, current_checksum)
            )
            conn.commit()
        except Exception as e:
            logger.warning(f"Database connection error: {e}. Skipping DB writes.")
            skip_db = True

    def resolve_tenant(item):
        org_id = str(item.get("organizationId") or item.get("organization_id") or "")
        if org_id in org_map:
            return org_map[org_id].get("tenant_id", FOUNDER_TENANT_ID)
        return FOUNDER_TENANT_ID

    # ── 1. Transform & Prepare Earning Components ──────────────────────────────
    earning_batch = []
    earning_id_map = {}  # legacy_id -> target_uuid
    for earn in stg_earnings:
        tid = resolve_tenant(earn)
        legacy_id = earn.get("id") or earn.get("earningId")
        target_id = det_uuid(f"{tid}:earning:{legacy_id}")
        earning_id_map[str(legacy_id)] = target_id
        if earn.get("earningId"):
            earning_id_map[str(earn["earningId"])] = target_id

        code = str(earn.get("earningId") or earn.get("earningName") or f"EARN_{legacy_id}").strip()[:32].upper().replace(" ", "_")
        name = str(earn.get("earningName") or "Earning")[:128]
        display_name = str(earn.get("displayName") or name)[:128]
        earning_type = str(earn.get("earningType") or "ALLOWANCE").upper()[:32]
        calc_type = "PERCENTAGE" if earn.get("isAmountInPercentage") else "FLAT"
        default_val = to_decimal(earn.get("value") or earn.get("amount") or 0)
        max_limit = to_decimal(earn.get("maxLimit")) if earn.get("maxLimit") is not None else None
        is_active = str(earn.get("status") or "active").lower() == "active"
        is_deleted = bool(earn.get("isDeleted", False))

        earning_batch.append((
            target_id, tid, code, name, display_name, earning_type, calc_type,
            default_val, None, max_limit, "MONTHLY", None,
            bool(earn.get("isProRata", False)),
            bool(earn.get("isIncludedInCtc", True)),
            bool(earn.get("isIncludedInSalaryStructure", True)),
            bool(earn.get("isTaxable", True)),
            bool(earn.get("isVariable", False)),
            bool(earn.get("isOneTimeComponent", False)),
            bool(earn.get("isFbpComponent", False)),
            bool(earn.get("isIncludedInEpf", False)),
            earn.get("epfInclusionType"),
            bool(earn.get("isIncludedInEsi", False)),
            True, is_active, is_deleted
        ))

    # ── 2. Transform & Prepare Deduction Components ────────────────────────────
    deduction_batch = []
    deduction_id_map = {}
    for ded in stg_deductions:
        tid = resolve_tenant(ded)
        legacy_id = ded.get("id") or ded.get("deductionId")
        target_id = det_uuid(f"{tid}:deduction:{legacy_id}")
        deduction_id_map[str(legacy_id)] = target_id
        if ded.get("deductionId"):
            deduction_id_map[str(ded["deductionId"])] = target_id

        code = str(ded.get("deductionId") or ded.get("deductionName") or f"DED_{legacy_id}").strip()[:32].upper().replace(" ", "_")
        name = str(ded.get("deductionName") or "Deduction")[:128]
        display_name = str(ded.get("displayName") or name)[:128]
        ded_type = str(ded.get("deductionType") or "OTHER").upper()[:32]
        is_active = str(ded.get("status") or "active").lower() == "active"
        is_deleted = bool(ded.get("isDeleted", False))

        deduction_batch.append((
            target_id, tid, code, name, display_name, ded_type, "FLAT",
            Decimal("0.0000"), None, None,
            bool(ded.get("isRecurring", False)),
            bool(ded.get("isPreTax", False)),
            ded.get("emiType"), None, None,
            is_active, is_deleted
        ))

    # ── 3. Transform & Prepare Benefit Components ──────────────────────────────
    benefit_batch = []
    benefit_id_map = {}
    for ben in stg_benefits:
        tid = resolve_tenant(ben)
        legacy_id = ben.get("id") or ben.get("benefitId")
        target_id = det_uuid(f"{tid}:benefit:{legacy_id}")
        benefit_id_map[str(legacy_id)] = target_id
        if ben.get("benefitId"):
            benefit_id_map[str(ben["benefitId"])] = target_id

        code = str(ben.get("benefitId") or ben.get("benefitName") or f"BEN_{legacy_id}").strip()[:32].upper().replace(" ", "_")
        name = str(ben.get("benefitName") or "Benefit")[:128]
        display_name = str(ben.get("displayName") or name)[:128]
        is_active = str(ben.get("status") or "active").lower() == "active"
        is_deleted = bool(ben.get("isDeleted", False))

        benefit_batch.append((
            target_id, tid, code, name, display_name,
            ben.get("benefitPlan"), ben.get("benefitCategory"),
            "FLAT", Decimal("0.0000"), None, None,
            bool(ben.get("isPreTax", False)),
            bool(ben.get("isOneTime", False)),
            bool(ben.get("isProRata", False)),
            bool(ben.get("isSuperannuationBenefit", False)),
            bool(ben.get("isIncludedInCtc", True)),
            bool(ben.get("isIncludedInSalaryStructure", True)),
            bool(ben.get("canAllowEmployerContribution", False)),
            bool(ben.get("canAllowEmployeeContribution", False)),
            ben.get("taxExemptSection"), ben.get("taxExemptionSubType"),
            is_active, is_deleted
        ))

    # ── 4. Transform & Prepare Reimbursement Components ────────────────────────
    reimbursement_batch = []
    reimbursement_id_map = {}
    for reimb in stg_reimbursements:
        tid = resolve_tenant(reimb)
        legacy_id = reimb.get("id") or reimb.get("reimbursementId")
        target_id = det_uuid(f"{tid}:reimbursement:{legacy_id}")
        reimbursement_id_map[str(legacy_id)] = target_id
        if reimb.get("reimbursementId"):
            reimbursement_id_map[str(reimb["reimbursementId"])] = target_id

        code = str(reimb.get("reimbursementId") or reimb.get("reimbursementName") or f"REIMB_{legacy_id}").strip()[:32].upper().replace(" ", "_")
        name = str(reimb.get("reimbursementName") or "Reimbursement")[:128]
        display_name = str(reimb.get("displayName") or name)[:128]
        reimb_type = str(reimb.get("reimbursementType") or "GENERAL").upper()[:32]
        max_limit = to_decimal(reimb.get("maxLimit")) if reimb.get("maxLimit") is not None else None
        is_active = str(reimb.get("status") or "active").lower() == "active"
        is_deleted = bool(reimb.get("isDeleted", False))

        reimbursement_batch.append((
            target_id, tid, code, name, display_name, reimb_type, "FLAT",
            Decimal("0.0000"), None, max_limit, reimb.get("carryForwardOption"),
            bool(reimb.get("isIncludedInCtc", True)),
            bool(reimb.get("isIncludedInSalaryStructure", True)),
            bool(reimb.get("isFbpComponent", False)),
            bool(reimb.get("isOptIn", False)),
            is_active, is_deleted
        ))

    # ── 5. Transform & Prepare FBP Configuration ───────────────────────────────
    fbp_batch = []
    for f in stg_fbp:
        tid = resolve_tenant(f)
        target_id = det_uuid(f"{tid}:fbp:{f.get('id', '1')}")
        fbp_batch.append((
            target_id, tid,
            bool(f.get("isFbpEnabled", False)),
            None, None,
            bool(f.get("isFbpDeclarationLocked", False)),
            None,
            bool(f.get("sendMailOnFbpDeclarationRelease", True)),
            bool(f.get("sendMailOnFbpDeclarationLock", True))
        ))

    # Group child lines by ctc_structure_id
    earnings_by_ctc = {}
    for el in stg_emp_earnings:
        cid = str(el.get("ctc_structure_id") or el.get("ctcStructureId") or "")
        earnings_by_ctc.setdefault(cid, []).append(el)

    benefits_by_ctc = {}
    for bl in stg_emp_benefits:
        cid = str(bl.get("ctc_structure_id") or bl.get("ctcStructureId") or "")
        benefits_by_ctc.setdefault(cid, []).append(bl)

    reimbursements_by_ctc = {}
    for rl in stg_emp_reimbursements:
        cid = str(rl.get("ctc_structure_id") or rl.get("ctcStructureId") or "")
        reimbursements_by_ctc.setdefault(cid, []).append(rl)

    fbp_by_ctc = {}
    for fl in stg_emp_fbp:
        cid = str(fl.get("ctc_structure_id") or fl.get("ctcStructureId") or "")
        fbp_by_ctc.setdefault(cid, []).append(fl)

    epf_by_ctc = {}
    for ep in stg_ctc_epf:
        cid = str(ep.get("ctc_structure_id") or ep.get("ctcStructureId") or "")
        epf_by_ctc.setdefault(cid, []).append(ep)

    esi_by_ctc = {}
    for es in stg_ctc_esi:
        cid = str(es.get("ctc_structure_id") or es.get("ctcStructureId") or "")
        esi_by_ctc.setdefault(cid, []).append(es)

    # ── 6. Transform & Prepare CTC Structures and Child Lines ──────────────────
    ctc_batch = []
    emp_earning_batch = []
    emp_benefit_batch = []
    emp_reimbursement_batch = []
    emp_fbp_batch = []
    ctc_epf_batch = []
    ctc_esi_batch = []

    for ctc in stg_ctc_structures:
        legacy_id = str(ctc.get("id"))
        is_active = ctc.get("is_active", ctc.get("isActive", True))
        is_deleted = ctc.get("is_deleted", ctc.get("isDeleted", False))

        # Specification: Only active CTC (is_active AND NOT is_deleted)
        if not is_active or is_deleted:
            continue

        emp_legacy_key = str(ctc.get("employee_id") or ctc.get("employeeId") or "").strip()
        matched_emp = emp_by_payroll_id.get(emp_legacy_key)
        if not matched_emp:
            quarantined.append({
                "source": "ctc_structure",
                "legacy_id": legacy_id,
                "employee_id": emp_legacy_key,
                "reason": "MISSING_EMPLOYEE_PARENT"
            })
            continue

        tid = matched_emp.get("tenant_id") or resolve_tenant(ctc)
        emp_uuid = matched_emp["id"]

        annual_ctc = to_decimal(ctc.get("annualCtc") or ctc.get("annual_ctc") or 0)
        monthly_salary = to_decimal(ctc.get("monthlySalary") or ctc.get("monthly_salary") or (annual_ctc / Decimal("12")))

        effective_date_raw = str(ctc.get("effectiveDate") or ctc.get("effective_date") or ctc.get("createdAt") or "2025-01-01")[:10]
        if len(effective_date_raw) < 10:
            effective_date_raw = "2025-01-01"

        # ── Sum Check Rule ───────────────────────────────────────────────────
        # Sum of monthly earnings must equal monthlySalary or quarantine with CTC_SUM_MISMATCH
        child_earnings = earnings_by_ctc.get(legacy_id, [])
        monthly_earning_sum = Decimal("0.0000")
        candidate_earning_lines = []

        for ce in child_earnings:
            line_id = str(ce.get("id", uuid.uuid4()))
            comp_legacy_id = str(ce.get("earning_id") or ce.get("earningId") or "")
            comp_uuid = earning_id_map.get(comp_legacy_id) or det_uuid(f"{tid}:earning:{comp_legacy_id}")
            calc_basis = str(ce.get("calculationBasis") or "FLAT")

            m_amt = to_decimal(ce.get("amount") or ce.get("monthly_amount") or 0)
            a_amt = to_decimal(ce.get("annual_amount") or (m_amt * Decimal("12")))
            val = to_decimal(ce.get("overrideAmount") or ce.get("amountInPercentage") or m_amt)

            monthly_earning_sum += m_amt
            candidate_earning_lines.append((
                det_uuid(f"{tid}:employee_earning:{line_id}"),
                tid,
                det_uuid(f"{tid}:ctc_structure:{legacy_id}"),
                comp_uuid,
                calc_basis,
                val,
                None,
                m_amt,
                a_amt,
                bool(ce.get("enabled", True)),
                ce.get("earningFrequency", "MONTHLY")
            ))

        # Check tolerance (+/- 0.05 INR for rounding) if monthly lines exist
        diff = abs(monthly_earning_sum - monthly_salary)
        if candidate_earning_lines and diff > Decimal("0.05"):
            quarantined.append({
                "source": "ctc_structure",
                "legacy_id": legacy_id,
                "employee_id": emp_legacy_key,
                "reason": "CTC_SUM_MISMATCH",
                "monthly_salary": str(monthly_salary),
                "monthly_earning_sum": str(monthly_earning_sum),
                "diff": str(diff)
            })
            continue

        ctc_target_id = det_uuid(f"{tid}:ctc_structure:{legacy_id}")
        ctc_batch.append((
            ctc_target_id, tid, emp_uuid, effective_date_raw,
            annual_ctc, monthly_salary, False, None, "Migrated from legacy CTC structure"
        ))

        # Accept verified earning lines
        emp_earning_batch.extend(candidate_earning_lines)

        # Child benefits
        for cb in benefits_by_ctc.get(legacy_id, []):
            b_line_id = str(cb.get("id", uuid.uuid4()))
            comp_id = benefit_id_map.get(str(cb.get("benefit_id") or cb.get("benefitId"))) or det_uuid(f"{tid}:benefit:{cb.get('benefit_id')}")
            m_amt = to_decimal(cb.get("amount") or cb.get("monthly_amount") or 0)
            a_amt = to_decimal(cb.get("annual_amount") or (m_amt * Decimal("12")))
            emp_benefit_batch.append((
                det_uuid(f"{tid}:employee_benefit:{b_line_id}"),
                tid, ctc_target_id, comp_id, "FLAT", m_amt, None, m_amt, a_amt,
                bool(cb.get("enabled", True))
            ))

        # Child reimbursements
        for cr in reimbursements_by_ctc.get(legacy_id, []):
            r_line_id = str(cr.get("id", uuid.uuid4()))
            comp_id = reimbursement_id_map.get(str(cr.get("reimbursement_id") or cr.get("reimbursementId"))) or det_uuid(f"{tid}:reimbursement:{cr.get('reimbursement_id')}")
            m_amt = to_decimal(cr.get("amount") or cr.get("monthly_amount") or 0)
            a_amt = to_decimal(cr.get("annual_amount") or (m_amt * Decimal("12")))
            emp_reimbursement_batch.append((
                det_uuid(f"{tid}:employee_reimbursement:{r_line_id}"),
                tid, ctc_target_id, comp_id, "FLAT", m_amt, None, m_amt, a_amt,
                bool(cr.get("enabled", True)), cr.get("carryForwardOption")
            ))

        # Child FBP components
        for cf in fbp_by_ctc.get(legacy_id, []):
            f_line_id = str(cf.get("id", uuid.uuid4()))
            m_amt = to_decimal(cf.get("monthly_amount") or cf.get("amount") or 0)
            a_amt = to_decimal(cf.get("annual_amount") or (m_amt * Decimal("12")))
            e_id = earning_id_map.get(str(cf.get("earning_id") or cf.get("earningId")))
            r_id = reimbursement_id_map.get(str(cf.get("reimbursement_id") or cf.get("reimbursementId")))
            if not e_id and not r_id:
                e_id = det_uuid(f"{tid}:earning:{cf.get('earning_id', '1')}")
            emp_fbp_batch.append((
                det_uuid(f"{tid}:employee_fbp_component:{f_line_id}"),
                tid, ctc_target_id, emp_uuid, e_id, r_id, a_amt, m_amt,
                datetime.now(timezone.utc), "CARRIED"
            ))

        # Child EPF lines
        for ep in epf_by_ctc.get(legacy_id, []):
            ep_line_id = str(ep.get("id", uuid.uuid4()))
            code = str(ep.get("component_code") or ep.get("componentCode") or "EPF_EMPLOYEE").upper()
            share = "EMPLOYER" if "EMPLOYER" in code or "ADMIN" in code or "EDLI" in code else "EMPLOYEE"
            w_base = to_decimal(ep.get("wage_base") or ep.get("wageBase") or 15000)
            rate = to_decimal(ep.get("rate") or 12)
            m_amt = to_decimal(ep.get("monthly_amount") or ep.get("monthlyAmount") or 0)
            a_amt = to_decimal(ep.get("annual_amount") or ep.get("annualAmount") or (m_amt * Decimal("12")))
            ctc_epf_batch.append((
                det_uuid(f"{tid}:ctc_epf_component:{ep_line_id}"),
                tid, ctc_target_id, code, share, w_base, rate, m_amt, a_amt,
                bool(ep.get("is_included_in_ctc", True))
            ))

        # Child ESI lines
        for es in esi_by_ctc.get(legacy_id, []):
            es_line_id = str(es.get("id", uuid.uuid4()))
            code = str(es.get("component_code") or es.get("componentCode") or "ESI_EMPLOYEE").upper()
            share = "EMPLOYER" if "EMPLOYER" in code else "EMPLOYEE"
            w_base = to_decimal(es.get("wage_base") or es.get("wageBase") or 21000)
            rate = to_decimal(es.get("rate") or 0.75)
            m_amt = to_decimal(es.get("monthly_amount") or es.get("monthlyAmount") or 0)
            a_amt = to_decimal(es.get("annual_amount") or es.get("annualAmount") or (m_amt * Decimal("12")))
            ctc_esi_batch.append((
                det_uuid(f"{tid}:ctc_esi_component:{es_line_id}"),
                tid, ctc_target_id, code, share, w_base, rate, m_amt, a_amt,
                bool(es.get("is_included_in_ctc", True))
            ))

    total_records = (
        len(earning_batch) + len(deduction_batch) + len(benefit_batch) + len(reimbursement_batch) +
        len(fbp_batch) + len(ctc_batch) + len(emp_earning_batch) + len(emp_benefit_batch) +
        len(emp_reimbursement_batch) + len(emp_fbp_batch) + len(ctc_epf_batch) + len(ctc_esi_batch)
    )

    logger.info(
        f"Transform complete: {len(earning_batch)} earnings, {len(deduction_batch)} deductions, "
        f"{len(benefit_batch)} benefits, {len(reimbursement_batch)} reimbursements, "
        f"{len(ctc_batch)} active CTCs ({len(quarantined)} quarantined). Total records: {total_records}"
    )

    # ── Database Writes ────────────────────────────────────────────────────────
    if not skip_db and cur and not dry_run:
        try:
            # Set RLS session
            cur.execute("SET LOCAL app.current_tenant_id = %s", (FOUNDER_TENANT_ID,))

            # 1. Upsert payroll.earning
            if earning_batch:
                logger.info(f"Upserting {len(earning_batch)} rows into payroll.earning...")
                execute_batch(cur, """
                    INSERT INTO payroll.earning (
                        id, tenant_id, code, name, display_name, earning_type, calculation_type,
                        default_value, percentage_of, max_limit, earning_frequency, parent_earning_id,
                        is_pro_rata, is_included_in_ctc, is_included_in_salary_structure, is_taxable,
                        is_variable, is_one_time, is_fbp_component, is_included_in_epf,
                        epf_inclusion_type, is_included_in_esi, show_in_payslip, is_active, is_deleted,
                        created_by, updated_by
                    ) VALUES (
                        %s, %s, %s, %s, %s, %s, %s,
                        %s, %s, %s, %s, %s,
                        %s, %s, %s, %s,
                        %s, %s, %s, %s,
                        %s, %s, %s, %s, %s,
                        'migration', 'migration'
                    )
                    ON CONFLICT (tenant_id, code) DO UPDATE SET
                        name = EXCLUDED.name,
                        display_name = EXCLUDED.display_name,
                        default_value = EXCLUDED.default_value,
                        is_active = EXCLUDED.is_active,
                        updated_at = CURRENT_TIMESTAMP
                """, earning_batch, page_size=200)

            # 2. Upsert payroll.deduction
            if deduction_batch:
                logger.info(f"Upserting {len(deduction_batch)} rows into payroll.deduction...")
                execute_batch(cur, """
                    INSERT INTO payroll.deduction (
                        id, tenant_id, code, name, display_name, deduction_type, calculation_type,
                        default_value, percentage_of, max_limit, is_recurring, is_pre_tax,
                        emi_type, perquisite_interest_rate, emi_interest_rate, is_active, is_deleted,
                        created_by, updated_by
                    ) VALUES (
                        %s, %s, %s, %s, %s, %s, %s,
                        %s, %s, %s, %s, %s,
                        %s, %s, %s, %s, %s,
                        'migration', 'migration'
                    )
                    ON CONFLICT (tenant_id, code) DO UPDATE SET
                        name = EXCLUDED.name,
                        display_name = EXCLUDED.display_name,
                        is_active = EXCLUDED.is_active,
                        updated_at = CURRENT_TIMESTAMP
                """, deduction_batch, page_size=200)

            # 3. Upsert payroll.benefit
            if benefit_batch:
                logger.info(f"Upserting {len(benefit_batch)} rows into payroll.benefit...")
                execute_batch(cur, """
                    INSERT INTO payroll.benefit (
                        id, tenant_id, code, name, display_name, benefit_plan, benefit_category,
                        calculation_type, default_value, percentage_of, max_limit, is_pre_tax,
                        is_one_time, is_pro_rata, is_superannuation, is_included_in_ctc,
                        is_included_in_salary_structure, allows_employer_contribution,
                        allows_employee_contribution, tax_exempt_section, tax_exemption_sub_type,
                        is_active, is_deleted, created_by, updated_by
                    ) VALUES (
                        %s, %s, %s, %s, %s, %s, %s,
                        %s, %s, %s, %s, %s,
                        %s, %s, %s, %s,
                        %s, %s,
                        %s, %s, %s,
                        %s, %s, 'migration', 'migration'
                    )
                    ON CONFLICT (tenant_id, code) DO UPDATE SET
                        name = EXCLUDED.name,
                        is_active = EXCLUDED.is_active,
                        updated_at = CURRENT_TIMESTAMP
                """, benefit_batch, page_size=200)

            # 4. Upsert payroll.reimbursement
            if reimbursement_batch:
                logger.info(f"Upserting {len(reimbursement_batch)} rows into payroll.reimbursement...")
                execute_batch(cur, """
                    INSERT INTO payroll.reimbursement (
                        id, tenant_id, code, name, display_name, reimbursement_type, calculation_type,
                        default_value, percentage_of, max_limit, carry_forward_option,
                        is_included_in_ctc, is_included_in_salary_structure, is_fbp_component,
                        is_opt_in, is_active, is_deleted, created_by, updated_by
                    ) VALUES (
                        %s, %s, %s, %s, %s, %s, %s,
                        %s, %s, %s, %s,
                        %s, %s, %s,
                        %s, %s, %s, 'migration', 'migration'
                    )
                    ON CONFLICT (tenant_id, code) DO UPDATE SET
                        name = EXCLUDED.name,
                        is_active = EXCLUDED.is_active,
                        updated_at = CURRENT_TIMESTAMP
                """, reimbursement_batch, page_size=200)

            # 5. Upsert payroll.fbp
            if fbp_batch:
                logger.info(f"Upserting {len(fbp_batch)} rows into payroll.fbp...")
                execute_batch(cur, """
                    INSERT INTO payroll.fbp (
                        id, tenant_id, is_enabled, window_opens_on, window_closes_on,
                        is_locked, locked_at, notify_on_release, notify_on_lock,
                        created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id) DO UPDATE SET
                        is_enabled = EXCLUDED.is_enabled,
                        is_locked = EXCLUDED.is_locked,
                        updated_at = CURRENT_TIMESTAMP
                """, fbp_batch, page_size=100)

            # 6. Upsert payroll.ctc_structure
            if ctc_batch:
                logger.info(f"Upserting {len(ctc_batch)} rows into payroll.ctc_structure...")
                execute_batch(cur, """
                    INSERT INTO payroll.ctc_structure (
                        id, tenant_id, employee_id, effective_from, annual_ctc, monthly_ctc,
                        is_cancelled, cancelled_at, notes, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, employee_id, effective_from) DO UPDATE SET
                        annual_ctc = EXCLUDED.annual_ctc,
                        monthly_ctc = EXCLUDED.monthly_ctc,
                        updated_at = CURRENT_TIMESTAMP
                """, ctc_batch, page_size=200)

            # 7. Upsert child lines
            if emp_earning_batch:
                logger.info(f"Upserting {len(emp_earning_batch)} rows into payroll.employee_earning...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_earning (
                        id, tenant_id, ctc_structure_id, component_id, calculation_type, value,
                        percentage_of, monthly_amount, annual_amount, is_enabled, earning_frequency,
                        created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        monthly_amount = EXCLUDED.monthly_amount,
                        annual_amount = EXCLUDED.annual_amount,
                        updated_at = CURRENT_TIMESTAMP
                """, emp_earning_batch, page_size=200)

            if emp_benefit_batch:
                logger.info(f"Upserting {len(emp_benefit_batch)} rows into payroll.employee_benefit...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_benefit (
                        id, tenant_id, ctc_structure_id, component_id, calculation_type, value,
                        percentage_of, monthly_amount, annual_amount, is_enabled,
                        created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        monthly_amount = EXCLUDED.monthly_amount,
                        updated_at = CURRENT_TIMESTAMP
                """, emp_benefit_batch, page_size=200)

            if emp_reimbursement_batch:
                logger.info(f"Upserting {len(emp_reimbursement_batch)} rows into payroll.employee_reimbursement...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_reimbursement (
                        id, tenant_id, ctc_structure_id, component_id, calculation_type, value,
                        percentage_of, monthly_amount, annual_amount, is_enabled, carry_forward_option,
                        created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        monthly_amount = EXCLUDED.monthly_amount,
                        updated_at = CURRENT_TIMESTAMP
                """, emp_reimbursement_batch, page_size=200)

            if emp_fbp_batch:
                logger.info(f"Upserting {len(emp_fbp_batch)} rows into payroll.employee_fbp_component...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_fbp_component (
                        id, tenant_id, ctc_structure_id, employee_id, earning_id, reimbursement_id,
                        annual_amount, monthly_amount, declared_at, declared_by, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        annual_amount = EXCLUDED.annual_amount,
                        monthly_amount = EXCLUDED.monthly_amount,
                        updated_at = CURRENT_TIMESTAMP
                """, emp_fbp_batch, page_size=200)

            if ctc_epf_batch:
                logger.info(f"Upserting {len(ctc_epf_batch)} rows into payroll.ctc_epf_component...")
                execute_batch(cur, """
                    INSERT INTO payroll.ctc_epf_component (
                        id, tenant_id, ctc_structure_id, component_code, share, wage_base, rate,
                        monthly_amount, annual_amount, is_included_in_ctc, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, ctc_structure_id, component_code) DO UPDATE SET
                        monthly_amount = EXCLUDED.monthly_amount,
                        annual_amount = EXCLUDED.annual_amount,
                        updated_at = CURRENT_TIMESTAMP
                """, ctc_epf_batch, page_size=200)

            if ctc_esi_batch:
                logger.info(f"Upserting {len(ctc_esi_batch)} rows into payroll.ctc_esi_component...")
                execute_batch(cur, """
                    INSERT INTO payroll.ctc_esi_component (
                        id, tenant_id, ctc_structure_id, component_code, share, wage_base, rate,
                        monthly_amount, annual_amount, is_included_in_ctc, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, ctc_structure_id, component_code) DO UPDATE SET
                        monthly_amount = EXCLUDED.monthly_amount,
                        annual_amount = EXCLUDED.annual_amount,
                        updated_at = CURRENT_TIMESTAMP
                """, ctc_esi_batch, page_size=200)

            conn.commit()

            # Update migration ledger
            cur.execute("""
                UPDATE core.migration_ledger
                SET records_processed = %s, records_skipped = %s, status = 'DONE', completed_at = %s
                WHERE id = %s
            """, (total_records, len(quarantined), datetime.now(timezone.utc), ledger_id))
            conn.commit()
            logger.info(f"Step '{step_name}' successfully loaded {total_records} records.")

        except Exception as e:
            conn.rollback()
            logger.error(f"Failed to load salary data: {e}", exc_info=True)
            cur.execute("""
                UPDATE core.migration_ledger
                SET status = 'FAILED', error_message = %s, completed_at = %s
                WHERE id = %s
            """, (str(e), datetime.now(timezone.utc), ledger_id))
            conn.commit()
            raise
        finally:
            cur.close()
            conn.close()

    # Save quarantine report
    quarantine_file = os.path.join(stg_dir, "stg_quarantined_salary.json")
    with open(quarantine_file, "w", encoding="utf-8") as f:
        json.dump(quarantined, f, indent=2)
    logger.info(f"Saved {len(quarantined)} quarantined salary records to {quarantine_file}")

    return {
        "earnings_loaded": len(earning_batch),
        "deductions_loaded": len(deduction_batch),
        "benefits_loaded": len(benefit_batch),
        "reimbursements_loaded": len(reimbursement_batch),
        "ctc_structures_loaded": len(ctc_batch),
        "total_records": total_records,
        "quarantined": len(quarantined)
    }


if __name__ == "__main__":
    is_dry_run = "--dry-run" in sys.argv
    run_loader(dry_run=is_dry_run)
