"""
Statutory Settings Loader (Step 3f - W-68.3 / Ticket #90)
Loads into target PostgreSQL:
  - payroll.epf_setting (provident fund settings per tenant)
  - payroll.esi_setting (state insurance settings per tenant)
  - payroll.org_pt_override (professional tax overrides where slabs differ from national reference)
  - payroll.org_pt_override_slab (custom slabs for overrides)

Rules & Invariants:
  - One epf_setting and esi_setting row per tenant (ON CONFLICT (tenant_id) DO UPDATE).
  - Write PT override slabs ONLY where the slabs differ from reference.pt_slab for that state.
  - Deterministic UUIDv5 namespace mapping.
  - Strict Decimal arithmetic only (no floats).
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
logger = logging.getLogger("load_statutory")

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


def slabs_differ_from_reference(configured_slabs, ref_slabs):
    """
    Compares configured PT slabs against reference.pt_slab.
    Returns True if configured slabs differ from standard reference.
    """
    if not ref_slabs:
        # If no standard slabs exist in reference for this state, any configured slab is an override
        return bool(configured_slabs)

    if len(configured_slabs) != len(ref_slabs):
        return True

    # Compare sorted by from_amount
    cfg_sorted = sorted(configured_slabs, key=lambda s: s["from_amount"])
    ref_sorted = sorted(ref_slabs, key=lambda s: s["from_amount"])

    for c, r in zip(cfg_sorted, ref_sorted):
        if c["from_amount"] != r["from_amount"]:
            return True
        if c.get("to_amount") != r.get("to_amount"):
            return True
        if c["amount"] != r["amount"]:
            return True

    return False


def run_loader(dry_run: bool = False, skip_db: bool = False):
    """
    Executes statutory migration loader (Task 3f).
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
    stg_epf = load_stg("stg_payroll_epf.json")
    stg_esi = load_stg("stg_payroll_esi.json")
    stg_pt = load_stg("stg_payroll_professionalTax.json")
    stg_pt_slabs = load_stg("stg_payroll_slabDetail.json")
    stg_slab_configs = load_stg("stg_payroll_slabRateConfiguration.json")
    stg_org_pt = load_stg("stg_payroll_org_pt_override.json")

    org_map_file = os.path.join(cfg_dir, "org_mapping.json")
    org_map = {}
    if os.path.exists(org_map_file):
        with open(org_map_file, "r", encoding="utf-8") as f:
            org_map = json.load(f)

    def resolve_tenant(item):
        org_id = str(item.get("organizationId") or item.get("organization_id") or "")
        if org_id in org_map:
            return org_map[org_id].get("tenant_id", FOUNDER_TENANT_ID)
        return FOUNDER_TENANT_ID

    input_files = [
        os.path.join(stg_dir, "stg_payroll_epf.json"),
        os.path.join(stg_dir, "stg_payroll_esi.json"),
        os.path.join(stg_dir, "stg_payroll_professionalTax.json"),
        os.path.join(stg_dir, "stg_payroll_slabDetail.json"),
    ]
    current_checksum = compute_files_checksum(input_files)

    step_name = "load_statutory"
    started_at = datetime.now(timezone.utc)
    ledger_id = str(uuid.uuid4())
    quarantined = []

    conn = None
    cur = None
    ref_pt_slabs = {}  # state_code -> list of {from_amount, to_amount, amount}

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

            # Record running
            cur.execute(
                """
                INSERT INTO core.migration_ledger (id, tenant_id, step_name, records_processed, status, started_at, input_checksum)
                VALUES (%s, %s, %s, 0, 'RUNNING', %s, %s)
                """,
                (ledger_id, FOUNDER_TENANT_ID, step_name, started_at, current_checksum)
            )
            conn.commit()

            # Pre-fetch national reference PT slabs for comparison
            cur.execute("""
                SELECT state_code, from_amount, to_amount, amount, sort_order
                FROM reference.pt_slab
                ORDER BY state_code, sort_order
            """)
            for row in cur.fetchall():
                sc = str(row[0]).strip().upper()
                ref_pt_slabs.setdefault(sc, []).append({
                    "from_amount": to_decimal(row[1]),
                    "to_amount": to_decimal(row[2]) if row[2] is not None else None,
                    "amount": to_decimal(row[3]),
                    "sort_order": int(row[4])
                })

        except Exception as e:
            logger.warning(f"Database connection error or ref table fetch failed: {e}. Skipping DB writes.")
            skip_db = True

    # ── 1. Transform EPF Settings (One row per tenant) ─────────────────────────
    epf_by_tenant = {}
    for ep in stg_epf:
        tid = resolve_tenant(ep)
        epf_id = det_uuid(f"{tid}:epf_setting")
        is_enabled = bool(ep.get("isEpfEnabled", ep.get("is_enabled", True)))
        reg_num = ep.get("epfNumber") or ep.get("registration_number")
        reg_date = str(ep.get("registrationDate") or ep.get("registration_date") or "")[:10] or None

        emp_rate = to_decimal(ep.get("employeeContributionRate") or ep.get("employee_rate") or 12)
        empr_rate = to_decimal(ep.get("employerContributionRate") or ep.get("employer_rate") or 12)
        eps_rate = to_decimal(ep.get("epsRate") or ep.get("eps_rate") or 8.33)
        edli_rate = to_decimal(ep.get("edliRate") or ep.get("edli_rate") or 0.50)
        admin_rate = to_decimal(ep.get("adminChargeRate") or ep.get("admin_charge_rate") or 0.50)
        wage_ceiling = to_decimal(ep.get("wageCeiling") or ep.get("wage_ceiling") or 15000)

        epf_by_tenant[tid] = (
            epf_id, tid, is_enabled, reg_num, reg_date, "MONTHLY",
            emp_rate, empr_rate, eps_rate, edli_rate, admin_rate, wage_ceiling,
            bool(ep.get("restrictEmployeeToCeiling", ep.get("restrict_employee_to_ceiling", False))),
            bool(ep.get("restrictEmployerToCeiling", ep.get("restrict_employer_to_ceiling", False))),
            bool(ep.get("prorateRestrictedWage", ep.get("prorate_restricted_wage", False))),
            bool(ep.get("considerEarnedWage", ep.get("consider_earned_wage", True))),
            int(ep.get("epsSeniorAge", ep.get("eps_senior_age", 58))),
            bool(ep.get("includeEmployerInCtc", ep.get("include_employer_in_ctc", False))),
            bool(ep.get("includeEdliAdminInCtc", ep.get("include_edli_admin_in_ctc", False))),
            bool(ep.get("includeEmployerInStructure", ep.get("include_employer_in_structure", False))),
            bool(ep.get("includeEdliAdminInStructure", ep.get("include_edli_admin_in_structure", False))),
            bool(ep.get("abryScheme", ep.get("abry_scheme", False)))
        )

    # ── 2. Transform ESI Settings (One row per tenant) ─────────────────────────
    esi_by_tenant = {}
    for es in stg_esi:
        tid = resolve_tenant(es)
        esi_id = det_uuid(f"{tid}:esi_setting")
        is_enabled = bool(es.get("isEsiEnabled", es.get("is_enabled", True)))
        reg_num = es.get("esiNumber") or es.get("registration_number")
        reg_date = str(es.get("registrationDate") or es.get("registration_date") or "")[:10] or None

        emp_rate = to_decimal(es.get("employeeContributionRate") or es.get("employee_rate") or 0.75)
        empr_rate = to_decimal(es.get("employerContributionRate") or es.get("employer_rate") or 3.25)
        wage_ceiling = to_decimal(es.get("wageCeiling") or es.get("wage_ceiling") or 21000)

        esi_by_tenant[tid] = (
            esi_id, tid, is_enabled, reg_num, reg_date, "MONTHLY",
            emp_rate, empr_rate, wage_ceiling,
            bool(es.get("includeEmployerInCtc", es.get("include_employer_in_ctc", False))),
            bool(es.get("includeInStructure", es.get("include_in_structure", False)))
        )

    # ── 3. Transform Professional Tax Overrides & Slabs ────────────────────────
    # Group configured slabs by pt header / state
    slabs_by_config = {}
    for sd in stg_pt_slabs:
        cid = str(sd.get("slabRateConfigurationId") or sd.get("slab_rate_configuration_id") or sd.get("pt_id") or "")
        slabs_by_config.setdefault(cid, []).append(sd)

    pt_override_batch = []
    pt_override_slab_batch = []
    skipped_identical_count = 0

    for pt in stg_pt:
        tid = resolve_tenant(pt)
        legacy_pt_id = str(pt.get("id"))
        state_code = str(pt.get("state") or pt.get("stateCode") or pt.get("state_code") or "KA").strip().upper()[:10]
        reg_num = pt.get("registrationNumber") or pt.get("ptNumber") or pt.get("registration_number")
        eff_from = str(pt.get("effectiveFrom") or pt.get("effective_from") or "2024-04-01")[:10]

        # Gather configured slabs for this PT setup
        child_slabs_raw = slabs_by_config.get(legacy_pt_id, [])
        configured_slabs = []
        for idx, cs in enumerate(child_slabs_raw, start=1):
            f_amt = to_decimal(cs.get("startRange") or cs.get("from_amount") or 0)
            t_raw = cs.get("endRange") or cs.get("to_amount")
            t_amt = to_decimal(t_raw) if t_raw is not None and str(t_raw).strip() != "" and str(t_raw) != "0" else None
            amt = to_decimal(cs.get("monthlyTax") or cs.get("amount") or 0)
            configured_slabs.append({
                "from_amount": f_amt,
                "to_amount": t_amt,
                "amount": amt,
                "is_female_exempt": bool(cs.get("isFemaleExempt", cs.get("is_female_exempt", False))),
                "deduction_months": cs.get("deductionMonths", cs.get("deduction_months")),
                "sort_order": int(cs.get("sortOrder", cs.get("sort_order", idx)))
            })

        # Check against reference.pt_slab
        ref_slabs = ref_pt_slabs.get(state_code, [])
        if not slabs_differ_from_reference(configured_slabs, ref_slabs):
            logger.info(f"PT slabs for tenant {tid[:8]} state {state_code} match national reference; skipping override per W-68 rule.")
            skipped_identical_count += 1
            continue

        # Slabs differ: create override header and slabs
        override_id = det_uuid(f"{tid}:org_pt_override:{state_code}")
        pt_override_batch.append((
            override_id, tid, state_code, reg_num, eff_from
        ))

        for slab in configured_slabs:
            slab_id = det_uuid(f"{tid}:org_pt_override_slab:{state_code}:{slab['sort_order']}")
            pt_override_slab_batch.append((
                slab_id, tid, override_id, slab["from_amount"], slab["to_amount"],
                slab["amount"], slab["is_female_exempt"], slab["deduction_months"], slab["sort_order"]
            ))

    total_records = (
        len(epf_by_tenant) + len(esi_by_tenant) +
        len(pt_override_batch) + len(pt_override_slab_batch)
    )

    logger.info(
        f"Transform complete: {len(epf_by_tenant)} EPF settings, {len(esi_by_tenant)} ESI settings, "
        f"{len(pt_override_batch)} PT overrides ({len(pt_override_slab_batch)} custom slabs, "
        f"{skipped_identical_count} identical to reference skipped). Total records: {total_records}"
    )

    # ── Database Writes ────────────────────────────────────────────────────────
    if not skip_db and cur and not dry_run:
        try:
            # Set RLS session
            cur.execute("SET LOCAL app.current_tenant_id = %s", (FOUNDER_TENANT_ID,))

            # 1. Upsert payroll.epf_setting
            if epf_by_tenant:
                logger.info(f"Upserting {len(epf_by_tenant)} rows into payroll.epf_setting...")
                execute_batch(cur, """
                    INSERT INTO payroll.epf_setting (
                        id, tenant_id, is_enabled, registration_number, registration_date, deduction_cycle,
                        employee_rate, employer_rate, eps_rate, edli_rate, admin_charge_rate, wage_ceiling,
                        restrict_employee_to_ceiling, restrict_employer_to_ceiling, prorate_restricted_wage,
                        consider_earned_wage, eps_senior_age, include_employer_in_ctc, include_edli_admin_in_ctc,
                        include_employer_in_structure, include_edli_admin_in_structure, abry_scheme,
                        created_by, updated_by
                    ) VALUES (
                        %s, %s, %s, %s, %s, %s,
                        %s, %s, %s, %s, %s, %s,
                        %s, %s, %s,
                        %s, %s, %s, %s,
                        %s, %s, %s,
                        'migration', 'migration'
                    )
                    ON CONFLICT (tenant_id) DO UPDATE SET
                        is_enabled = EXCLUDED.is_enabled,
                        registration_number = EXCLUDED.registration_number,
                        employee_rate = EXCLUDED.employee_rate,
                        employer_rate = EXCLUDED.employer_rate,
                        wage_ceiling = EXCLUDED.wage_ceiling,
                        updated_at = CURRENT_TIMESTAMP
                """, list(epf_by_tenant.values()), page_size=100)

            # 2. Upsert payroll.esi_setting
            if esi_by_tenant:
                logger.info(f"Upserting {len(esi_by_tenant)} rows into payroll.esi_setting...")
                execute_batch(cur, """
                    INSERT INTO payroll.esi_setting (
                        id, tenant_id, is_enabled, registration_number, registration_date, deduction_cycle,
                        employee_rate, employer_rate, wage_ceiling, include_employer_in_ctc,
                        include_in_structure, created_by, updated_by
                    ) VALUES (
                        %s, %s, %s, %s, %s, %s,
                        %s, %s, %s, %s,
                        %s, 'migration', 'migration'
                    )
                    ON CONFLICT (tenant_id) DO UPDATE SET
                        is_enabled = EXCLUDED.is_enabled,
                        registration_number = EXCLUDED.registration_number,
                        employee_rate = EXCLUDED.employee_rate,
                        employer_rate = EXCLUDED.employer_rate,
                        wage_ceiling = EXCLUDED.wage_ceiling,
                        updated_at = CURRENT_TIMESTAMP
                """, list(esi_by_tenant.values()), page_size=100)

            # 3. Upsert payroll.org_pt_override
            if pt_override_batch:
                logger.info(f"Upserting {len(pt_override_batch)} rows into payroll.org_pt_override...")
                execute_batch(cur, """
                    INSERT INTO payroll.org_pt_override (
                        id, tenant_id, state_code, registration_number, effective_from,
                        created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (tenant_id, state_code) DO UPDATE SET
                        registration_number = EXCLUDED.registration_number,
                        effective_from = EXCLUDED.effective_from,
                        updated_at = CURRENT_TIMESTAMP
                """, pt_override_batch, page_size=100)

            # 4. Upsert payroll.org_pt_override_slab
            if pt_override_slab_batch:
                logger.info(f"Upserting {len(pt_override_slab_batch)} rows into payroll.org_pt_override_slab...")
                execute_batch(cur, """
                    INSERT INTO payroll.org_pt_override_slab (
                        id, tenant_id, override_id, from_amount, to_amount, amount,
                        is_female_exempt, deduction_months, sort_order,
                        created_by, updated_by
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
                    ON CONFLICT (id) DO UPDATE SET
                        from_amount = EXCLUDED.from_amount,
                        to_amount = EXCLUDED.to_amount,
                        amount = EXCLUDED.amount,
                        updated_at = CURRENT_TIMESTAMP
                """, pt_override_slab_batch, page_size=200)

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
            logger.error(f"Failed to load statutory data: {e}", exc_info=True)
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
    quarantine_file = os.path.join(stg_dir, "stg_quarantined_statutory.json")
    with open(quarantine_file, "w", encoding="utf-8") as f:
        json.dump(quarantined, f, indent=2)

    return {
        "epf_settings_loaded": len(epf_by_tenant),
        "esi_settings_loaded": len(esi_by_tenant),
        "pt_overrides_loaded": len(pt_override_batch),
        "pt_override_slabs_loaded": len(pt_override_slab_batch),
        "skipped_identical_count": skipped_identical_count,
        "total_records": total_records,
        "quarantined": len(quarantined)
    }


if __name__ == "__main__":
    is_dry_run = "--dry-run" in sys.argv
    run_loader(dry_run=is_dry_run)
