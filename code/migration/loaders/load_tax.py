"""
Income Tax & Investment Declarations Loader (Step 3h - W-68.3 / Ticket #90)
Loads into target PostgreSQL:
  - payroll.income_tax_declaration (tenant declaration window)
  - payroll.employee_investment_declaration (employee annual declaration)
  - 9 child investment tables:
      - payroll.employee_inv_house_rent
      - payroll.employee_inv_home_loan
      - payroll.employee_inv_let_out_property
      - payroll.employee_inv_let_out_property_line
      - payroll.employee_inv_section6a
      - payroll.employee_inv_pre_tax_deduction
      - payroll.employee_inv_prev_employment
      - payroll.employee_inv_other_income
      - payroll.employee_inv_tax_summary
  - payroll.employee_tds (annual tax projection/records)
  - payroll.employee_proof_of_investment
  - payroll.employee_proof_item

Rules & Invariants:
  - Current financial year only: fiscal_year = FY of the run date, parameter MIGRATION_FY.
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
logger = logging.getLogger("load_tax")

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


def get_current_fy():
    """Returns the current financial year in format 'YYYY-YYYY'."""
    fy_env = os.environ.get("MIGRATION_FY")
    if fy_env:
        return fy_env.strip()
    now = datetime.now(timezone.utc)
    # Indian FY starts in April (month >= 4)
    if now.month >= 4:
        return f"{now.year}-{now.year + 1}"
    else:
        return f"{now.year - 1}-{now.year}"


def run_loader(dry_run: bool = False, skip_db: bool = False):
    """
    Executes income tax & investment declarations migration loader (Task 3h).
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
    stg_window = load_stg("stg_payroll_IncomeTaxDeclaration.json")
    stg_declarations = load_stg("stg_payroll_employee_investment_declaration.json")
    stg_house_rent = load_stg("stg_payroll_employee_inv_house_rent.json")
    stg_home_loan = load_stg("stg_payroll_employee_inv_home_loan.json")
    stg_let_out = load_stg("stg_payroll_employee_inv_let_out_property.json")
    stg_let_out_detail = load_stg("stg_payroll_employee_inv_let_out_property_detail.json")
    stg_sec6a = load_stg("stg_payroll_employee_inv_section6a.json")
    stg_pre_tax = load_stg("stg_payroll_employee_inv_pre_tax_deduction.json")
    stg_prev_emp = load_stg("stg_payroll_employee_inv_prev_employment.json")
    stg_other_inc = load_stg("stg_payroll_employee_inv_other_income.json")
    stg_tax_summary = load_stg("stg_payroll_employee_inv_tax_summary.json")
    stg_emp_tds = load_stg("stg_payroll_employee_tds.json")
    stg_poi = load_stg("stg_payroll_ProofOfInvestment.json")
    stg_emp_poi = load_stg("stg_payroll_employee_proof_of_investment.json")
    stg_poi_items = load_stg("stg_payroll_employee_poi_item.json")
    merged_employees = load_stg("stg_merged_employees.json")

    org_map_file = os.path.join(cfg_dir, "org_mapping.json")
    org_map = {}
    if os.path.exists(org_map_file):
        with open(org_map_file, "r", encoding="utf-8") as f:
            org_map = json.load(f)

    # Build employee lookup tables
    emp_by_payroll_id = {}
    for e in merged_employees:
        if e.get("source_legacy_payroll_id"):
            emp_by_payroll_id[str(e["source_legacy_payroll_id"]).strip()] = e
        if e.get("id"):
            emp_by_payroll_id[str(e["id"]).strip()] = e

    current_fy = get_current_fy()
    logger.info(f"Target Financial Year for tax migration: {current_fy}")

    def resolve_tenant(item):
        org_id = str(item.get("organizationId") or item.get("organization_id") or "")
        if org_id in org_map:
            return org_map[org_id].get("tenant_id", FOUNDER_TENANT_ID)
        return FOUNDER_TENANT_ID

    input_files = [
        os.path.join(stg_dir, "stg_payroll_IncomeTaxDeclaration.json"),
        os.path.join(stg_dir, "stg_payroll_employee_investment_declaration.json"),
        os.path.join(stg_dir, "stg_payroll_employee_inv_house_rent.json"),
        os.path.join(stg_dir, "stg_payroll_employee_tds.json"),
    ]
    current_checksum = compute_files_checksum(input_files)

    step_name = "load_tax"
    started_at = datetime.now(timezone.utc)
    ledger_id = str(uuid.uuid4())
    quarantined = []

    conn = None
    cur = None
    section6a_item_master = {}  # code/id -> uuid

    if not skip_db and not dry_run:
        try:
            conn = get_pg_connection()
            conn.autocommit = False
            cur = conn.cursor()

            # Check if step already DONE with same checksum
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

            # Pre-load reference.section6a_item_master
            cur.execute("SELECT id, section_code FROM reference.section6a_item_master")
            for r in cur.fetchall():
                section6a_item_master[str(r[1]).strip().upper()] = str(r[0])
                section6a_item_master[str(r[0])] = str(r[0])

        except Exception as e:
            logger.warning(f"Database connection error: {e}. Skipping DB writes.")
            skip_db = True

    # ── 1. Tenant Declaration Window (payroll.income_tax_declaration) ──────────
    window_batch = []
    seen_windows = set()
    for w in stg_window:
        tid = resolve_tenant(w)
        fy = str(w.get("financialYear") or w.get("financial_year") or current_fy).strip()
        if fy != current_fy:
            continue
        if (tid, fy) in seen_windows:
            continue
        seen_windows.add((tid, fy))

        win_id = det_uuid(f"{tid}:income_tax_declaration:{fy}")
        opens_on = str(w.get("windowOpensOn") or w.get("window_opens_on") or f"{fy[:4]}-04-01")[:10]
        closes_on = str(w.get("windowClosesOn") or w.get("window_closes_on") or f"{fy[-4:]}-03-31")[:10]
        regime = str(w.get("defaultTaxRegime") or w.get("default_tax_regime") or "NEW").upper()
        if regime not in ("OLD", "NEW"):
            regime = "NEW"

        window_batch.append((
            win_id, tid, fy, opens_on, closes_on,
            bool(w.get("isLocked", False)),
            regime,
            bool(w.get("canChangeTaxRegime", True)),
            bool(w.get("panRequiredForRentOverThreshold", True)),
            bool(w.get("notifyOnLock", False)),
            bool(w.get("notifyOnRelease", False))
        ))

    # Ensure anchor tenant has window for current FY
    if (FOUNDER_TENANT_ID, current_fy) not in seen_windows:
        win_id = det_uuid(f"{FOUNDER_TENANT_ID}:income_tax_declaration:{current_fy}")
        window_batch.append((
            win_id, FOUNDER_TENANT_ID, current_fy,
            f"{current_fy[:4]}-04-01", f"{current_fy[-4:]}-03-31",
            False, "NEW", True, True, False, False
        ))

    # ── 2. Employee Investment Declarations ────────────────────────────────────
    decl_batch = []
    decl_id_map = {}  # legacy_id -> target_id
    decl_by_emp_fy = {}  # (tid, emp_uuid, fy) -> target_id

    for d in stg_declarations:
        legacy_id = str(d.get("id"))
        emp_key = str(d.get("employee_id") or d.get("employeeId") or "").strip()
        matched_emp = emp_by_payroll_id.get(emp_key)
        if not matched_emp:
            quarantined.append({
                "source": "employee_investment_declaration",
                "legacy_id": legacy_id,
                "employee_id": emp_key,
                "reason": "MISSING_EMPLOYEE_PARENT"
            })
            continue

        fy = str(d.get("financialYear") or d.get("financial_year") or current_fy).strip()
        # Specification: Current financial year only
        if fy != current_fy:
            continue

        tid = matched_emp.get("tenant_id") or resolve_tenant(d)
        emp_uuid = matched_emp["id"]

        decl_target_id = det_uuid(f"{tid}:employee_investment_declaration:{legacy_id}")
        decl_id_map[legacy_id] = decl_target_id
        decl_by_emp_fy[(tid, emp_uuid, fy)] = decl_target_id

        regime = str(d.get("taxRegime") or d.get("tax_regime") or "NEW").upper()
        if regime not in ("OLD", "NEW"):
            regime = "NEW"

        status = str(d.get("status") or "DRAFT").upper()
        if status not in ("DRAFT", "SUBMITTED"):
            status = "DRAFT"

        decl_batch.append((
            decl_target_id, tid, emp_uuid, fy, regime, status,
            bool(d.get("isStayingInRentedHouse", d.get("is_staying_in_rented_house", False))),
            bool(d.get("isRepayingSelfOccupiedLoan", d.get("is_repaying_self_occupied_loan", False))),
            bool(d.get("hasLetOutProperty", d.get("has_let_out_property", False))),
            bool(d.get("isLocked", d.get("is_locked", False))),
            datetime.now(timezone.utc) if status == "SUBMITTED" else None,
            None
        ))

    # ── 3. Child Investment Declarations (9 tables) ───────────────────────────
    # Helper to resolve declaration_id
    def resolve_decl_id(item):
        raw_decl_id = str(item.get("declaration_id") or item.get("declarationId") or "")
        if raw_decl_id in decl_id_map:
            return decl_id_map[raw_decl_id]
        emp_key = str(item.get("employee_id") or item.get("employeeId") or "").strip()
        matched = emp_by_payroll_id.get(emp_key)
        if matched:
            tid = matched.get("tenant_id", FOUNDER_TENANT_ID)
            return decl_by_emp_fy.get((tid, matched["id"], current_fy))
        return None

    # a. House Rent
    house_rent_batch = []
    for hr in stg_house_rent:
        decl_id = resolve_decl_id(hr)
        if not decl_id:
            continue
        tid = resolve_tenant(hr)
        hr_id = det_uuid(f"{tid}:house_rent:{hr.get('id', uuid.uuid4())}")
        from_m = str(hr.get("fromMonth") or hr.get("from_month") or f"{current_fy[:4]}-04-01")[:10]
        to_m = str(hr.get("toMonth") or hr.get("to_month") or f"{current_fy[-4:]}-03-31")[:10]
        amt = to_decimal(hr.get("amountPerMonth") or hr.get("amount_per_month") or hr.get("rentAmount") or 0)
        house_rent_batch.append((
            hr_id, tid, decl_id, from_m, to_m,
            str(hr.get("address") or "Rented accommodation")[:1000],
            str(hr.get("landlordName") or hr.get("landlord_name") or "Landlord")[:150],
            hr.get("landlordPan") or hr.get("landlord_pan"),
            bool(hr.get("isMetro", hr.get("is_metro", False))),
            amt
        ))

    # b. Home Loan
    home_loan_batch = []
    for hl in stg_home_loan:
        decl_id = resolve_decl_id(hl)
        if not decl_id:
            continue
        tid = resolve_tenant(hl)
        hl_id = det_uuid(f"{tid}:home_loan:{hl.get('id', uuid.uuid4())}")
        prin = to_decimal(hl.get("principalPaid") or hl.get("principal_paid") or 0)
        intr = to_decimal(hl.get("interestPaid") or hl.get("interest_paid") or 0)
        sanct = str(hl.get("loanSanctionedOn") or hl.get("loan_sanctioned_on") or "")[:10] or None
        home_loan_batch.append((
            hl_id, tid, decl_id,
            str(hl.get("lenderName") or hl.get("lender_name") or "Bank")[:150],
            hl.get("lenderPan") or hl.get("lender_pan"),
            prin, intr,
            bool(hl.get("isFirstTimeBuyer", hl.get("is_first_time_buyer", False))),
            sanct
        ))

    # c. Let Out Property & Property Lines
    let_out_batch = []
    let_out_lines_batch = []
    for lp in stg_let_out:
        decl_id = resolve_decl_id(lp)
        if not decl_id:
            continue
        tid = resolve_tenant(lp)
        lp_id = det_uuid(f"{tid}:let_out:{lp.get('id', uuid.uuid4())}")
        net_inc = to_decimal(lp.get("netIncomeLoss") or lp.get("net_income_loss") or 0)
        let_out_batch.append((
            lp_id, tid, decl_id,
            str(lp.get("propertyName") or lp.get("property_name") or "Property")[:150],
            str(lp.get("address") or "")[:1000] or None,
            net_inc
        ))

    for lod in stg_let_out_detail:
        raw_prop_id = str(lod.get("property_id") or lod.get("propertyId") or "")
        tid = resolve_tenant(lod)
        prop_uuid = det_uuid(f"{tid}:let_out:{raw_prop_id}")
        decl_id = resolve_decl_id(lod) or det_uuid(f"{tid}:employee_investment_declaration:1")
        line_type = str(lod.get("lineType") or lod.get("line_type") or "ANNUAL_RENT").upper()
        if line_type not in ('ANNUAL_RENT', 'MUNICIPAL_TAX', 'LOAN_INTEREST'):
            line_type = 'ANNUAL_RENT'
        amt = to_decimal(lod.get("amount") or 0)
        line_id = det_uuid(f"{tid}:let_out_line:{prop_uuid}:{line_type}")
        let_out_lines_batch.append((
            line_id, tid, decl_id, prop_uuid, line_type, amt,
            lod.get("lenderName") or lod.get("lender_name"),
            lod.get("lenderPan") or lod.get("lender_pan")
        ))

    # d. Section 6A Lines
    sec6a_batch = []
    for s6 in stg_sec6a:
        decl_id = resolve_decl_id(s6)
        if not decl_id:
            continue
        tid = resolve_tenant(s6)
        code_key = str(s6.get("sectionCode") or s6.get("section_code") or s6.get("code") or "80C").strip().upper()
        item_id = section6a_item_master.get(code_key) or det_uuid(f"section6a_item:{code_key}")
        s6_id = det_uuid(f"{tid}:sec6a:{s6.get('id', uuid.uuid4())}")
        amt = to_decimal(s6.get("amount") or 0)
        desc = str(s6.get("description") or f"Investment under {code_key}")[:150]
        sec6a_batch.append((
            s6_id, tid, decl_id, item_id, desc, amt
        ))

    # e. Pre-tax Deductions
    pre_tax_batch = []
    seen_pre_tax = set()
    for pt in stg_pre_tax:
        decl_id = resolve_decl_id(pt)
        if not decl_id:
            continue
        tid = resolve_tenant(pt)
        kind = str(pt.get("kind") or pt.get("type") or "EMPLOYEE_PF").upper()
        if kind not in ('EMPLOYEE_PF', 'VPF', 'PROFESSIONAL_TAX', 'NPS_EMPLOYEE'):
            kind = 'EMPLOYEE_PF'
        if (tid, decl_id, kind) in seen_pre_tax:
            continue
        seen_pre_tax.add((tid, decl_id, kind))
        pt_id = det_uuid(f"{tid}:pre_tax:{decl_id}:{kind}")
        amt = to_decimal(pt.get("amount") or 0)
        pre_tax_batch.append((
            pt_id, tid, decl_id, kind, amt
        ))

    # f. Previous Employment
    prev_emp_batch = []
    seen_prev_emp = set()
    for pe in stg_prev_emp:
        decl_id = resolve_decl_id(pe)
        if not decl_id:
            continue
        tid = resolve_tenant(pe)
        kind = str(pe.get("kind") or "INCOME").upper()
        if kind not in ('INCOME', 'INCOME_TAX_DEDUCTED', 'PROFESSIONAL_TAX', 'EMPLOYEE_PF', 'LEAVE_ENCASHMENT'):
            kind = 'INCOME'
        if (tid, decl_id, kind) in seen_prev_emp:
            continue
        seen_prev_emp.add((tid, decl_id, kind))
        pe_id = det_uuid(f"{tid}:prev_emp:{decl_id}:{kind}")
        amt = to_decimal(pe.get("amount") or 0)
        entered_by = str(pe.get("enteredBy") or pe.get("entered_by") or "EMPLOYEE").upper()
        if entered_by not in ('EMPLOYEE', 'OFFICER'):
            entered_by = 'EMPLOYEE'
        prev_emp_batch.append((
            pe_id, tid, decl_id, kind, amt,
            str(pe.get("employerName") or pe.get("employer_name") or "Previous Employer")[:150],
            pe.get("employerTan") or pe.get("employer_tan"),
            entered_by
        ))

    # g. Other Income
    other_inc_batch = []
    seen_other_inc = set()
    for oi in stg_other_inc:
        decl_id = resolve_decl_id(oi)
        if not decl_id:
            continue
        tid = resolve_tenant(oi)
        kind = str(oi.get("kind") or "SAVINGS_INTEREST").upper()
        if kind not in ('SAVINGS_INTEREST', 'FD_INTEREST', 'NSC_INTEREST', 'OTHER'):
            kind = 'OTHER'
        if (tid, decl_id, kind) in seen_other_inc:
            continue
        seen_other_inc.add((tid, decl_id, kind))
        oi_id = det_uuid(f"{tid}:other_inc:{decl_id}:{kind}")
        amt = to_decimal(oi.get("amount") or 0)
        other_inc_batch.append((
            oi_id, tid, decl_id, kind,
            str(oi.get("description") or f"Income from {kind}")[:150],
            amt
        ))

    # h. Annual Tax Summary
    tax_summary_batch = []
    seen_summary = set()
    for ts in stg_tax_summary:
        decl_id = resolve_decl_id(ts)
        if not decl_id:
            continue
        tid = resolve_tenant(ts)
        regime = str(ts.get("regime") or "NEW").upper()
        if regime not in ("OLD", "NEW"):
            regime = "NEW"
        if (tid, decl_id, regime) in seen_summary:
            continue
        seen_summary.add((tid, decl_id, regime))
        ts_id = det_uuid(f"{tid}:tax_summary:{decl_id}:{regime}")
        tax_summary_batch.append((
            ts_id, tid, decl_id, regime,
            to_decimal(ts.get("taxableIncome") or ts.get("taxable_income")),
            to_decimal(ts.get("netTaxableIncome") or ts.get("net_taxable_income")),
            to_decimal(ts.get("taxOnTaxableIncome") or ts.get("tax_on_taxable_income")),
            to_decimal(ts.get("taxYtdAmount") or ts.get("tax_ytd_amount")),
            to_decimal(ts.get("taxToBePaid") or ts.get("tax_to_be_paid")),
            to_decimal(ts.get("tdsThroughPayroll") or ts.get("tds_through_payroll")),
            to_decimal(ts.get("tdsPreviousEmployer") or ts.get("tds_previous_employer")),
            to_decimal(ts.get("tdsOtherIncome") or ts.get("tds_other_income")),
            to_decimal(ts.get("otherSourcesIncome") or ts.get("other_sources_income")),
            to_decimal(ts.get("exemptionUnderSection10") or ts.get("exemption_under_section10")),
            to_decimal(ts.get("exemptionUnderSection6a") or ts.get("exemption_under_section6a")),
            int(ts.get("remainingMonths") or ts.get("remaining_months") or 12),
            datetime.now(timezone.utc)
        ))

    # ── 4. Annual Employee TDS Records (payroll.employee_tds) ──────────────────
    tds_batch = []
    seen_tds = set()
    for tds in stg_emp_tds:
        emp_key = str(tds.get("employee_id") or tds.get("employeeId") or "").strip()
        matched_emp = emp_by_payroll_id.get(emp_key)
        if not matched_emp:
            continue

        fy = str(tds.get("financialYear") or tds.get("financial_year") or current_fy).strip()
        if fy != current_fy:
            continue

        tid = matched_emp.get("tenant_id") or resolve_tenant(tds)
        emp_uuid = matched_emp["id"]

        if (tid, emp_uuid, fy) in seen_tds:
            continue
        seen_tds.add((tid, emp_uuid, fy))

        tds_id = det_uuid(f"{tid}:employee_tds:{tds.get('id', uuid.uuid4())}")
        regime = str(tds.get("regime") or "NEW").upper()
        if regime not in ("OLD", "NEW"):
            regime = "NEW"
        source = str(tds.get("source") or "DECLARATION").upper()
        if source not in ("DECLARATION", "OFFICER"):
            source = "DECLARATION"

        decl_id = decl_by_emp_fy.get((tid, emp_uuid, fy))
        eff_period = str(tds.get("effectiveFromPeriod") or tds.get("effective_from_period") or f"{fy[:4]}-04")[:7]

        tds_batch.append((
            tds_id, tid, emp_uuid, fy, regime, source, decl_id,
            to_decimal(tds.get("annualGross") or tds.get("annual_gross") or 0),
            to_decimal(tds.get("annualTaxableIncome") or tds.get("annual_taxable_income") or 0),
            to_decimal(tds.get("annualTax") or tds.get("annual_tax") or 0),
            eff_period, True, None, "Migrated TDS record"
        ))

    # ── 5. Proof of Investment & Items (payroll.employee_proof_*) ─────────────
    proof_batch = []
    proof_id_map = {}
    proof_items_batch = []

    combined_poi = stg_poi + stg_emp_poi
    for p in combined_poi:
        decl_id = resolve_decl_id(p)
        if not decl_id:
            continue
        tid = resolve_tenant(p)
        legacy_id = str(p.get("id"))
        proof_id = det_uuid(f"{tid}:proof_of_investment:{legacy_id}")
        proof_id_map[legacy_id] = proof_id

        emp_key = str(p.get("employee_id") or p.get("employeeId") or "").strip()
        emp_uuid = emp_by_payroll_id[emp_key]["id"] if emp_key in emp_by_payroll_id else det_uuid(f"{tid}:emp:1")

        raw_st = str(p.get("status") or "DRAFT").upper()
        status = raw_st if raw_st in ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED') else 'DRAFT'

        proof_batch.append((
            proof_id, tid, emp_uuid, decl_id, current_fy, status,
            None, p.get("reviewerNote") or p.get("reviewer_note"),
            datetime.now(timezone.utc) if status != 'DRAFT' else None,
            datetime.now(timezone.utc) if status in ('APPROVED', 'REJECTED') else None
        ))

    for pi in stg_poi_items:
        proof_legacy_id = str(pi.get("proof_id") or pi.get("proofId") or "")
        proof_uuid = proof_id_map.get(proof_legacy_id)
        if not proof_uuid:
            continue
        tid = resolve_tenant(pi)
        pi_id = det_uuid(f"{tid}:proof_item:{pi.get('id', uuid.uuid4())}")

        kind = str(pi.get("sourceKind") or pi.get("source_kind") or "SECTION_6A").upper()
        if kind not in ('HOUSE_RENT', 'HOME_LOAN_PRINCIPAL', 'HOME_LOAN_INTEREST', 'LET_OUT_PROPERTY', 'SECTION_6A', 'PREV_EMPLOYMENT'):
            kind = 'SECTION_6A'

        source_line_id = det_uuid(f"{tid}:proof_line:{pi.get('sourceLineId') or pi.get('source_line_id', uuid.uuid4())}")
        status = str(pi.get("status") or "PENDING").upper()
        if status not in ('PENDING', 'APPROVED', 'DISALLOWED', 'RETURNED'):
            status = 'PENDING'

        decl_amt = to_decimal(pi.get("declaredAmount") or pi.get("declared_amount") or 0)
        claim_amt = to_decimal(pi.get("claimedAmount") or pi.get("claimed_amount")) if pi.get("claimedAmount") is not None else decl_amt
        appr_amt = to_decimal(pi.get("approvedAmount") or pi.get("approved_amount")) if pi.get("approvedAmount") is not None else None

        proof_items_batch.append((
            pi_id, tid, proof_uuid, kind, source_line_id,
            str(pi.get("description") or "Proof documentation item")[:150],
            decl_amt, claim_amt, appr_amt, status,
            pi.get("employeeNote") or pi.get("employee_note"),
            pi.get("reviewerNote") or pi.get("reviewer_note")
        ))

    total_records = (
        len(window_batch) + len(decl_batch) + len(house_rent_batch) + len(home_loan_batch) +
        len(let_out_batch) + len(let_out_lines_batch) + len(sec6a_batch) + len(pre_tax_batch) +
        len(prev_emp_batch) + len(other_inc_batch) + len(tax_summary_batch) + len(tds_batch) +
        len(proof_batch) + len(proof_items_batch)
    )

    logger.info(
        f"Transform complete: {len(window_batch)} windows, {len(decl_batch)} declarations, "
        f"{len(tds_batch)} TDS records, {len(proof_batch)} proofs ({len(quarantined)} quarantined). "
        f"Total records: {total_records}"
    )

    # ── Database Writes ────────────────────────────────────────────────────────
    if not skip_db and cur and not dry_run:
        try:
            # Set RLS session
            cur.execute("SET LOCAL app.current_tenant_id = %s", (FOUNDER_TENANT_ID,))

            # 1. Upsert payroll.income_tax_declaration
            if window_batch:
                logger.info(f"Upserting {len(window_batch)} rows into payroll.income_tax_declaration...")
                execute_batch(cur, """
                    INSERT INTO payroll.income_tax_declaration (
                        id, tenant_id, financial_year, window_opens_on, window_closes_on,
                        is_locked, default_tax_regime, can_change_tax_regime,
                        pan_required_for_rent_over_threshold, notify_on_lock, notify_on_release,
                        created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, financial_year) DO UPDATE SET
                        is_locked = EXCLUDED.is_locked,
                        default_tax_regime = EXCLUDED.default_tax_regime,
                        updated_at = CURRENT_TIMESTAMP
                """, window_batch, page_size=100)

            # 2. Upsert payroll.employee_investment_declaration
            if decl_batch:
                logger.info(f"Upserting {len(decl_batch)} rows into payroll.employee_investment_declaration...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_investment_declaration (
                        id, tenant_id, employee_id, financial_year, tax_regime, status,
                        is_staying_in_rented_house, is_repaying_self_occupied_loan, has_let_out_property,
                        is_locked, submitted_at, locked_at, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, employee_id, financial_year) DO UPDATE SET
                        tax_regime = EXCLUDED.tax_regime,
                        status = EXCLUDED.status,
                        is_staying_in_rented_house = EXCLUDED.is_staying_in_rented_house,
                        updated_at = CURRENT_TIMESTAMP
                """, decl_batch, page_size=200)

            # 3. Upsert House Rent
            if house_rent_batch:
                logger.info(f"Upserting {len(house_rent_batch)} rows into payroll.employee_inv_house_rent...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_inv_house_rent (
                        id, tenant_id, declaration_id, from_month, to_month, address,
                        landlord_name, landlord_pan, is_metro, amount_per_month, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        amount_per_month = EXCLUDED.amount_per_month,
                        updated_at = CURRENT_TIMESTAMP
                """, house_rent_batch, page_size=200)

            # 4. Upsert Home Loan
            if home_loan_batch:
                logger.info(f"Upserting {len(home_loan_batch)} rows into payroll.employee_inv_home_loan...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_inv_home_loan (
                        id, tenant_id, declaration_id, lender_name, lender_pan, principal_paid,
                        interest_paid, is_first_time_buyer, loan_sanctioned_on, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        principal_paid = EXCLUDED.principal_paid,
                        interest_paid = EXCLUDED.interest_paid,
                        updated_at = CURRENT_TIMESTAMP
                """, home_loan_batch, page_size=200)

            # 5. Upsert Let Out Property & Lines
            if let_out_batch:
                logger.info(f"Upserting {len(let_out_batch)} rows into payroll.employee_inv_let_out_property...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_inv_let_out_property (
                        id, tenant_id, declaration_id, property_name, address, net_income_loss,
                        created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        net_income_loss = EXCLUDED.net_income_loss,
                        updated_at = CURRENT_TIMESTAMP
                """, let_out_batch, page_size=200)

            if let_out_lines_batch:
                logger.info(f"Upserting {len(let_out_lines_batch)} rows into payroll.employee_inv_let_out_property_line...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_inv_let_out_property_line (
                        id, tenant_id, declaration_id, property_id, line_type, amount,
                        lender_name, lender_pan, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, property_id, line_type) DO UPDATE SET
                        amount = EXCLUDED.amount,
                        updated_at = CURRENT_TIMESTAMP
                """, let_out_lines_batch, page_size=200)

            # 6. Upsert Section 6A
            if sec6a_batch:
                logger.info(f"Upserting {len(sec6a_batch)} rows into payroll.employee_inv_section6a...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_inv_section6a (
                        id, tenant_id, declaration_id, section6a_item_id, description, amount,
                        created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        amount = EXCLUDED.amount,
                        updated_at = CURRENT_TIMESTAMP
                """, sec6a_batch, page_size=200)

            # 7. Upsert Pre-tax Deductions
            if pre_tax_batch:
                logger.info(f"Upserting {len(pre_tax_batch)} rows into payroll.employee_inv_pre_tax_deduction...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_inv_pre_tax_deduction (
                        id, tenant_id, declaration_id, kind, amount, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, declaration_id, kind) DO UPDATE SET
                        amount = EXCLUDED.amount,
                        updated_at = CURRENT_TIMESTAMP
                """, pre_tax_batch, page_size=200)

            # 8. Upsert Previous Employment
            if prev_emp_batch:
                logger.info(f"Upserting {len(prev_emp_batch)} rows into payroll.employee_inv_prev_employment...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_inv_prev_employment (
                        id, tenant_id, declaration_id, kind, amount, employer_name, employer_tan,
                        entered_by, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, declaration_id, kind) DO UPDATE SET
                        amount = EXCLUDED.amount,
                        employer_name = EXCLUDED.employer_name,
                        updated_at = CURRENT_TIMESTAMP
                """, prev_emp_batch, page_size=200)

            # 9. Upsert Other Income
            if other_inc_batch:
                logger.info(f"Upserting {len(other_inc_batch)} rows into payroll.employee_inv_other_income...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_inv_other_income (
                        id, tenant_id, declaration_id, kind, description, amount,
                        created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, declaration_id, kind) DO UPDATE SET
                        amount = EXCLUDED.amount,
                        updated_at = CURRENT_TIMESTAMP
                """, other_inc_batch, page_size=200)

            # 10. Upsert Tax Summary
            if tax_summary_batch:
                logger.info(f"Upserting {len(tax_summary_batch)} rows into payroll.employee_inv_tax_summary...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_inv_tax_summary (
                        id, tenant_id, declaration_id, regime, taxable_income, net_taxable_income,
                        tax_on_taxable_income, tax_ytd_amount, tax_to_be_paid, tds_through_payroll,
                        tds_previous_employer, tds_other_income, other_sources_income,
                        exemption_under_section10, exemption_under_section6a, remaining_months,
                        computed_at, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, declaration_id, regime) DO UPDATE SET
                        net_taxable_income = EXCLUDED.net_taxable_income,
                        tax_to_be_paid = EXCLUDED.tax_to_be_paid,
                        updated_at = CURRENT_TIMESTAMP
                """, tax_summary_batch, page_size=200)

            # 11. Upsert Employee TDS
            if tds_batch:
                logger.info(f"Upserting {len(tds_batch)} rows into payroll.employee_tds...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_tds (
                        id, tenant_id, employee_id, financial_year, regime, source, declaration_id,
                        annual_gross, annual_taxable_income, annual_tax, effective_from_period,
                        is_active, superseded_at, note, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        annual_gross = EXCLUDED.annual_gross,
                        annual_taxable_income = EXCLUDED.annual_taxable_income,
                        annual_tax = EXCLUDED.annual_tax,
                        updated_at = clock_timestamp()
                """, tds_batch, page_size=200)

            # 12. Upsert Proof of Investment
            if proof_batch:
                logger.info(f"Upserting {len(proof_batch)} rows into payroll.employee_proof_of_investment...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_proof_of_investment (
                        id, tenant_id, employee_id, declaration_id, financial_year, status,
                        approval_instance_id, reviewer_note, submitted_at, decided_at,
                        created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, declaration_id) DO UPDATE SET
                        status = EXCLUDED.status,
                        reviewer_note = EXCLUDED.reviewer_note,
                        updated_at = CURRENT_TIMESTAMP
                """, proof_batch, page_size=100)

            # 13. Upsert Proof Items
            if proof_items_batch:
                logger.info(f"Upserting {len(proof_items_batch)} rows into payroll.employee_proof_item...")
                execute_batch(cur, """
                    INSERT INTO payroll.employee_proof_item (
                        id, tenant_id, proof_id, source_kind, source_line_id, description,
                        declared_amount, claimed_amount, approved_amount, status,
                        employee_note, reviewer_note, created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, proof_id, source_kind, source_line_id) DO UPDATE SET
                        claimed_amount = EXCLUDED.claimed_amount,
                        approved_amount = EXCLUDED.approved_amount,
                        status = EXCLUDED.status,
                        updated_at = CURRENT_TIMESTAMP
                """, proof_items_batch, page_size=200)

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
            logger.error(f"Failed to load tax data: {e}", exc_info=True)
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
    quarantine_file = os.path.join(stg_dir, "stg_quarantined_tax.json")
    with open(quarantine_file, "w", encoding="utf-8") as f:
        json.dump(quarantined, f, indent=2)

    return {
        "windows_loaded": len(window_batch),
        "declarations_loaded": len(decl_batch),
        "house_rent_loaded": len(house_rent_batch),
        "tds_loaded": len(tds_batch),
        "proofs_loaded": len(proof_batch),
        "proof_items_loaded": len(proof_items_batch),
        "total_records": total_records,
        "quarantined": len(quarantined)
    }


if __name__ == "__main__":
    is_dry_run = "--dry-run" in sys.argv
    run_loader(dry_run=is_dry_run)
