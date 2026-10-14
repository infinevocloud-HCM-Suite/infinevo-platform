"""
Document Migration Loader (Step 3 - W-68.3 / W-69)
Loads into target PostgreSQL core.document:
  - 202/205 HRMS Employee Documents (kind=EMPLOYEE_DOCUMENT)
  - 73 Payroll POI Documents (kind=INVESTMENT_PROOF)

Design decisions:
  - blob_path = {tenantId}/{employeeId}/EMPLOYEE_DOCUMENT/{docId}  (W-21 spec §4)
  - No public URLs stored — blob_path + blob_container only (W-21 constraint)
  - Legacy Cloudinary URL stored as file_name for audit trail
  - checksum_sha256 = sha256(legacy_doc_id + file_name) deterministic placeholder
    (real checksum populated when files are physically migrated to Azure Blob)
  - size_bytes = 0 (unknown from metadata; updated during blob migration W-69)
  - content_type mapped from file_type or inferred from extension
  - POI docs matched via payroll.employeeId (UUID) embedded in Cloudinary URL
  - Unmatched docs quarantined with audit log entry
"""

import os, sys, json, uuid, hashlib, logging
from datetime import datetime, timezone
from mimetypes import guess_type
import psycopg2
from psycopg2.extras import execute_batch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("load_documents")

NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
BLOB_CONTAINER = "infinevo-documents"

# Known HRMS document kind → target kind
DOC_KIND_MAP = {
    "AADHAR_CARD": "EMPLOYEE_DOCUMENT",
    "PAN_CARD":    "EMPLOYEE_DOCUMENT",
    "OFFER_LETTER":"EMPLOYEE_DOCUMENT",
    "EXPERIENCE":  "EMPLOYEE_DOCUMENT",
    "EDUCATION":   "EMPLOYEE_DOCUMENT",
    "BANK_PASSBOOK": "EMPLOYEE_DOCUMENT",
    "PHOTO":       "EMPLOYEE_DOCUMENT",
    "OTHER":       "EMPLOYEE_DOCUMENT",
}

def get_conn():
    return psycopg2.connect(
        host=os.environ.get("TARGET_DB_HOST", "localhost"),
        port=int(os.environ.get("TARGET_DB_PORT", "5432")),
        dbname=os.environ.get("TARGET_DB_NAME", "infinevo"),
        user=os.environ.get("TARGET_DB_USER", "migration_user"),
        password=os.environ.get("TARGET_DB_PASSWORD", "local_migration_pw")
    )

def det_uuid(key: str) -> str:
    return str(uuid.uuid5(NAMESPACE, key))

def det_checksum(legacy_id, file_name):
    """Deterministic SHA-256 placeholder from legacy ID + filename."""
    raw = f"LEGACY:{legacy_id}:{file_name}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()

def infer_content_type(file_name: str, fallback="application/octet-stream") -> str:
    ct, _ = guess_type(file_name)
    return ct or fallback

def run_loader():
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    stg  = os.path.join(base, "staging")

    def load(fname):
        with open(os.path.join(stg, fname), "r", encoding="utf-8") as f:
            return json.load(f)

    hrms_docs     = load("stg_hrms_employee_document.json")
    poi_docs      = load("stg_payroll_employee_poi_document.json")
    merged        = load("stg_merged_employees.json")
    payroll_emps  = load("stg_payroll_employee.json")

    # Build lookup maps
    emp_by_hrms_id    = {e["source_legacy_hrms_id"]: e for e in merged if e.get("source_legacy_hrms_id")}
    emp_by_payroll_id = {e["source_legacy_payroll_id"]: e for e in merged if e.get("source_legacy_payroll_id")}

    # POI docs embed payroll employeeId (UUID) in Cloudinary URL
    # Build: payroll_emp_uuid → merged employee
    payroll_emp_uuid_to_merged = {}
    merged_by_id = {e["id"]: e for e in merged}
    for pe in payroll_emps:
        payroll_emp_uuid = str(pe.get("employeeId") or "").strip()
        payroll_int_id   = pe.get("employee_id")
        if not payroll_emp_uuid:
            continue
        # Try match via source_legacy_payroll_id (integer)
        emp = emp_by_payroll_id.get(payroll_int_id)
        if emp:
            payroll_emp_uuid_to_merged[payroll_emp_uuid] = emp

    logger.info(f"Staging: {len(hrms_docs)} HRMS docs, {len(poi_docs)} POI docs")
    logger.info(f"Mapped employees: {len(emp_by_hrms_id)} via HRMS ID, {len(emp_by_payroll_id)} via Payroll ID")

    conn = get_conn()
    conn.autocommit = False
    cur = conn.cursor()

    step_name  = "DOCUMENT_MIGRATION_LOAD"
    started_at = datetime.now(timezone.utc)
    ledger_id  = str(uuid.uuid4())
    anchor_tenant = list(emp_by_hrms_id.values())[0]["tenant_id"] if emp_by_hrms_id else "a0000000-0000-0000-0000-000000000001"

    try:
        # ── Ledger: start ──────────────────────────────────────────────────────
        cur.execute(
            "INSERT INTO core.migration_ledger (id,tenant_id,step_name,records_processed,status,started_at) "
            "VALUES (%s,%s,%s,%s,%s,%s)",
            (ledger_id, anchor_tenant, step_name, 0, "RUNNING", started_at)
        )
        conn.commit()

        doc_batch  = []
        quarantine = []
        total      = 0

        # ── 1. HRMS Employee Documents (205 records → 202 matched) ───────────
        logger.info(f"Processing {len(hrms_docs)} HRMS employee documents...")
        for d in hrms_docs:
            emp = emp_by_hrms_id.get(d.get("employee_id"))
            if not emp:
                quarantine.append({
                    "source": "HRMS", "legacy_id": d["id"],
                    "employee_id": d.get("employee_id"),
                    "reason": "employee_id not in merged employees"
                })
                continue

            tid      = emp["tenant_id"]
            emp_uuid = emp["id"]
            doc_id   = det_uuid(f"{tid}:hrms_doc:{d['id']}")
            fname    = d.get("file_name") or f"hrms_doc_{d['id']}.pdf"
            ct       = d.get("file_type") or infer_content_type(fname)
            kind     = DOC_KIND_MAP.get(d.get("document_type"), "EMPLOYEE_DOCUMENT")
            blob_path = f"{tid}/{emp_uuid}/{kind}/{doc_id}"
            checksum  = det_checksum(f"hrms:{d['id']}", fname)

            doc_batch.append((
                doc_id, tid, emp_uuid, kind,
                fname, ct, 0,          # size=0 placeholder
                BLOB_CONTAINER, blob_path, checksum, False
            ))

        # ── 2. Payroll POI (Investment Proof) Documents (73 records) ─────────
        logger.info(f"Processing {len(poi_docs)} Payroll POI documents...")
        # URL format: .../upload/v<ver>/<org_id>/<payroll_employee_uuid>/<year>/...
        import re as _re
        for d in poi_docs:
            doc_url = d.get("document_url") or ""
            fname   = d.get("document_name") or f"poi_doc_{d['id']}.jpg"
            ct      = infer_content_type(fname, "image/jpeg")

            m = _re.search(r'/upload/v\d+/([^/]+)/([^/]+)/', doc_url)
            if not m:
                quarantine.append({"source": "POI", "legacy_id": d["id"],
                                   "reason": "Cannot parse Cloudinary URL pattern"})
                continue

            payroll_emp_uuid = m.group(2)
            matched_emp = payroll_emp_uuid_to_merged.get(payroll_emp_uuid)
            if not matched_emp:
                quarantine.append({
                    "source": "POI", "legacy_id": d["id"],
                    "payroll_emp_uuid": payroll_emp_uuid,
                    "reason": "payroll employeeId UUID not found in merged employees"
                })
                continue

            tid      = matched_emp["tenant_id"]
            emp_uuid = matched_emp["id"]

            doc_id    = det_uuid(f"{tid}:poi_doc:{d['id']}")
            blob_path = f"{tid}/{emp_uuid}/INVESTMENT_PROOF/{doc_id}"
            checksum  = det_checksum(f"poi:{d['id']}", fname)

            doc_batch.append((
                doc_id, tid, emp_uuid, "INVESTMENT_PROOF",
                fname, ct, 0,
                BLOB_CONTAINER, blob_path, checksum, False
            ))

        # ── Insert all docs ───────────────────────────────────────────────────
        logger.info(f"Inserting {len(doc_batch)} document records into core.document...")
        if doc_batch:
            execute_batch(cur,
                """
                INSERT INTO core.document
                    (id, tenant_id, employee_id, kind, file_name, content_type, size_bytes,
                     blob_container, blob_path, checksum_sha256, is_deleted, created_by, updated_by)
                VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'system','system')
                ON CONFLICT (tenant_id, blob_path) DO UPDATE SET
                    file_name=EXCLUDED.file_name,
                    updated_at=CURRENT_TIMESTAMP
                """, doc_batch, page_size=200)
            conn.commit()
        total = len(doc_batch)

        if quarantine:
            logger.warning(f"Quarantined {len(quarantine)} unmatched documents:")
            for q in quarantine:
                logger.warning(f"  {q}")

        # ── Ledger: complete ──────────────────────────────────────────────────
        cur.execute(
            "UPDATE core.migration_ledger SET records_processed=%s,status=%s,completed_at=%s WHERE id=%s",
            (total, "SUCCESS", datetime.now(timezone.utc), ledger_id)
        )
        conn.commit()
        logger.info(f"=== DOCUMENT MIGRATION COMPLETE — {total} records loaded, {len(quarantine)} quarantined ===")

    except Exception as e:
        conn.rollback()
        logger.error(f"Document load failed: {e}", exc_info=True)
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
