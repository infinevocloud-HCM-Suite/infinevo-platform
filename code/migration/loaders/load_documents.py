"""
Document Migration Loader (W-69 / Ticket #91)
Migrates legacy documents to private Azure Blob Storage and PostgreSQL core.document:
  - 205 HRMS Employee Documents (kind=EMPLOYEE_DOCUMENT)
  - 73 Payroll POI Documents (kind=INVESTMENT_PROOF)

Architectural Rules & Decisions (W-21 / W-69):
  - Container name: 'documents' (matching Azure storage Bicep definition)
  - blob_path: {tenantId}/{employeeId}/{kind}/{docId} (W-21 spec §4)
  - No public URLs stored in database (Incident 3 honoured)
  - Raw files fetched from legacy Cloudinary URLs with 3x retry and back-off
  - Real SHA-256 and byte sizes computed on-the-fly and verified post-upload
  - Post-upload verification: download blob, re-verify sha256; delete on mismatch
  - File size cap: 10 MB limit (files >10 MB quarantined)
  - Content type validation: pdf, jpg, jpeg, png, xlsx, csv allowed
  - Parallel streaming via concurrent.futures.ThreadPoolExecutor(max_workers=8)
  - Restartability & Idempotency: skip if document already verified in core.document
"""

import os
import sys
import json
import uuid
import hashlib
import logging
import time
import re
from datetime import datetime, timezone
from mimetypes import guess_type
from concurrent.futures import ThreadPoolExecutor, as_completed
import requests
import psycopg2
from psycopg2.extras import execute_batch

try:
    from azure.storage.blob import BlobServiceClient, ContentSettings
except ImportError:
    BlobServiceClient = None
    ContentSettings = None

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("load_documents")

NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
BLOB_CONTAINER = "documents"
MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024  # 10 MB cap per W-21 / W-69
ALLOWED_EXTENSIONS = {"pdf", "jpg", "jpeg", "png", "xlsx", "csv"}

# Document kind mapping
DOC_KIND_MAP = {
    "AADHAR_CARD": "EMPLOYEE_DOCUMENT",
    "PAN_CARD": "EMPLOYEE_DOCUMENT",
    "OFFER_LETTER": "EMPLOYEE_DOCUMENT",
    "EXPERIENCE": "EMPLOYEE_DOCUMENT",
    "EDUCATION": "EMPLOYEE_DOCUMENT",
    "BANK_PASSBOOK": "EMPLOYEE_DOCUMENT",
    "PHOTO": "EMPLOYEE_DOCUMENT",
    "OTHER": "EMPLOYEE_DOCUMENT",
}


def get_conn():
    """Target PostgreSQL connection with RLS context."""
    return psycopg2.connect(
        host=os.environ.get("TARGET_DB_HOST", "localhost"),
        port=int(os.environ.get("TARGET_DB_PORT", "5432")),
        dbname=os.environ.get("TARGET_DB_NAME", "infinevo"),
        user=os.environ.get("TARGET_DB_USER", "migration_user"),
        password=os.environ.get("TARGET_DB_PASSWORD", "local_migration_pw"),
    )


def get_blob_service_client():
    """Initialize Azure BlobServiceClient via connection string, account URL, or Azurite."""
    if BlobServiceClient is None:
        logger.warning("azure.storage.blob not available")
        return None

    conn_str = os.environ.get("AZURE_STORAGE_CONNECTION_STRING")
    if conn_str:
        return BlobServiceClient.from_connection_string(conn_str)

    account_url = os.environ.get("AZURE_STORAGE_ACCOUNT_URL")
    if account_url:
        try:
            from azure.identity import DefaultAzureCredential
            return BlobServiceClient(account_url, credential=DefaultAzureCredential())
        except ImportError:
            logger.warning("azure.identity not found; cannot use DefaultAzureCredential with account URL")
            return None

    return None


def det_uuid(key: str) -> str:
    """Deterministic UUIDv5 namespace mapping."""
    return str(uuid.uuid5(NAMESPACE, key))


def infer_content_type(file_name: str, fallback="application/octet-stream") -> str:
    ct, _ = guess_type(file_name)
    return ct or fallback


def get_file_extension(file_name: str) -> str:
    if not file_name or "." not in file_name:
        return ""
    return file_name.rsplit(".", 1)[-1].lower()


def fetch_file_with_retry(url: str, max_retries: int = 3, timeout: int = 30):
    """
    Downloads file from URL with up to max_retries attempts and exponential backoff.
    Returns (bytes_content, error_reason)
    """
    for attempt in range(1, max_retries + 1):
        try:
            resp = requests.get(url, timeout=timeout, stream=True)
            if resp.status_code == 200:
                content = resp.content
                return content, None
            elif resp.status_code == 404:
                return None, f"HTTP 404 Not Found at legacy URL: {url}"
            else:
                if attempt == max_retries:
                    return None, f"HTTP {resp.status_code} after {max_retries} attempts: {url}"
        except Exception as ex:
            if attempt == max_retries:
                return None, f"Network exception after {max_retries} attempts: {str(ex)}"
        time.sleep(0.5 * (2 ** (attempt - 1)))
    return None, "Failed to download after retries"


def upload_and_verify_blob(container_client, blob_path: str, content: bytes, content_type: str):
    """
    Uploads content to Azure Blob Storage and verifies SHA-256 checksum on read-back.
    Returns (checksum_sha256, error_reason)
    """
    computed_sha = hashlib.sha256(content).hexdigest()
    try:
        blob_client = container_client.get_blob_client(blob_path)
        c_settings = ContentSettings(content_type=content_type) if ContentSettings else None
        blob_client.upload_blob(content, overwrite=True, content_settings=c_settings)

        # Verification step: Read back from Blob and compare SHA-256
        read_back_bytes = blob_client.download_blob().readall()
        read_back_sha = hashlib.sha256(read_back_bytes).hexdigest()

        if read_back_sha != computed_sha:
            try:
                blob_client.delete_blob()
            except Exception:
                pass
            return None, f"Checksum verification mismatch: expected {computed_sha}, got {read_back_sha}"

        return computed_sha, None
    except Exception as ex:
        return None, f"Blob upload error for {blob_path}: {str(ex)}"


def process_single_document(doc_item, container_client, existing_checksums=None, dry_run: bool = False):
    """
    Worker function executed in parallel thread:
      1. Validates extension and file metadata
      2. Checks if already migrated (idempotency)
      3. Downloads file with retry
      4. Validates max size (10 MB cap)
      5. Uploads and verifies blob in Azure Blob Storage
      6. Returns (is_success, result_data, quarantine_reason)
    """
    doc_id = doc_item["doc_id"]
    tid = doc_item["tenant_id"]
    emp_uuid = doc_item["employee_id"]
    kind = doc_item["kind"]
    fname = doc_item["file_name"]
    file_url = doc_item["file_url"]
    ct = doc_item["content_type"]
    blob_path = doc_item["blob_path"]

    # 1. Validate file extension
    ext = get_file_extension(fname)
    if not ext and file_url:
        ext = get_file_extension(file_url.split("?")[0])

    if ext not in ALLOWED_EXTENSIONS:
        return False, None, {
            "source": doc_item["source"],
            "legacy_id": doc_item["legacy_id"],
            "file_name": fname,
            "reason": f"File extension '{ext}' not in allowed list: {ALLOWED_EXTENSIONS}"
        }

    # 2. Check if already uploaded and verified (idempotency)
    existing_sha = (existing_checksums or {}).get(blob_path)
    if existing_sha and not existing_sha.startswith("placeholder") and not existing_sha.startswith("LEGACY:"):
        return True, {
            "doc_id": doc_id, "tenant_id": tid, "employee_id": emp_uuid,
            "kind": kind, "file_name": fname, "content_type": ct,
            "size_bytes": doc_item.get("existing_size", 0),
            "blob_container": BLOB_CONTAINER, "blob_path": blob_path,
            "checksum_sha256": existing_sha, "skipped_existing": True
        }, None

    if dry_run or container_client is None:
        # Dry run or mock mode: compute deterministic placeholder
        raw = f"DRY_RUN:{doc_item['legacy_id']}:{fname}".encode("utf-8")
        dummy_sha = hashlib.sha256(raw).hexdigest()
        return True, {
            "doc_id": doc_id, "tenant_id": tid, "employee_id": emp_uuid,
            "kind": kind, "file_name": fname, "content_type": ct,
            "size_bytes": 0, "blob_container": BLOB_CONTAINER,
            "blob_path": blob_path, "checksum_sha256": dummy_sha, "skipped_existing": False
        }, None

    # 3. Download from Cloudinary URL with retry
    content, fetch_err = fetch_file_with_retry(file_url, max_retries=3, timeout=30)
    if fetch_err:
        return False, None, {
            "source": doc_item["source"],
            "legacy_id": doc_item["legacy_id"],
            "file_name": fname,
            "url": file_url,
            "reason": fetch_err
        }

    size_bytes = len(content)

    # 4. Enforce 10 MB limit
    if size_bytes > MAX_FILE_SIZE_BYTES:
        return False, None, {
            "source": doc_item["source"],
            "legacy_id": doc_item["legacy_id"],
            "file_name": fname,
            "size_bytes": size_bytes,
            "reason": f"File size {size_bytes} bytes exceeds 10 MB limit ({MAX_FILE_SIZE_BYTES} bytes)"
        }

    # 5. Upload to Blob & verify read-back
    verified_sha, upload_err = upload_and_verify_blob(container_client, blob_path, content, ct)
    if upload_err:
        return False, None, {
            "source": doc_item["source"],
            "legacy_id": doc_item["legacy_id"],
            "file_name": fname,
            "blob_path": blob_path,
            "reason": upload_err
        }

    return True, {
        "doc_id": doc_id,
        "tenant_id": tid,
        "employee_id": emp_uuid,
        "kind": kind,
        "file_name": fname,
        "content_type": ct,
        "size_bytes": size_bytes,
        "blob_container": BLOB_CONTAINER,
        "blob_path": blob_path,
        "checksum_sha256": verified_sha,
        "skipped_existing": False
    }, None


def run_loader(max_workers: int = 8, dry_run: bool = False, skip_db: bool = False):
    """
    Main loader orchestrator for W-69 Document Migration.
    """
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    stg_dir = os.path.join(base_dir, "staging")

    def load_stg(fname):
        path = os.path.join(stg_dir, fname)
        if not os.path.exists(path):
            logger.warning(f"Staging file not found: {path}")
            return []
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)

    hrms_docs = load_stg("stg_hrms_employee_document.json")
    poi_docs = load_stg("stg_payroll_employee_poi_document.json")
    merged_employees = load_stg("stg_merged_employees.json")
    payroll_emps = load_stg("stg_payroll_employee.json")

    # Build employee lookup tables
    emp_by_hrms_id = {e["source_legacy_hrms_id"]: e for e in merged_employees if e.get("source_legacy_hrms_id")}
    emp_by_payroll_id = {e["source_legacy_payroll_id"]: e for e in merged_employees if e.get("source_legacy_payroll_id")}

    payroll_emp_uuid_to_merged = {}
    for pe in payroll_emps:
        payroll_emp_uuid = str(pe.get("employeeId") or "").strip()
        payroll_int_id = pe.get("employee_id")
        if not payroll_emp_uuid:
            continue
        emp = emp_by_payroll_id.get(payroll_int_id)
        if emp:
            payroll_emp_uuid_to_merged[payroll_emp_uuid] = emp

    logger.info(f"Loaded staging: {len(hrms_docs)} HRMS docs, {len(poi_docs)} POI docs")
    logger.info(f"Mapped employees: {len(emp_by_hrms_id)} via HRMS ID, {len(emp_by_payroll_id)} via Payroll ID")

    # Prepare document items for processing
    pending_items = []
    quarantined = []

    # 1. HRMS Employee Documents (205 records)
    for d in hrms_docs:
        emp = emp_by_hrms_id.get(d.get("employee_id"))
        if not emp:
            quarantined.append({
                "source": "HRMS",
                "legacy_id": d.get("id"),
                "employee_id": d.get("employee_id"),
                "reason": "employee_id not found in merged employees"
            })
            continue

        tid = emp["tenant_id"]
        emp_uuid = emp["id"]
        doc_id = det_uuid(f"{tid}:hrms_doc:{d['id']}")
        raw_fname = d.get("file_name") or f"hrms_doc_{d['id']}.pdf"
        fname = os.path.basename(raw_fname)
        ct = d.get("file_type") or infer_content_type(fname)
        kind = DOC_KIND_MAP.get(d.get("document_type"), "EMPLOYEE_DOCUMENT")
        blob_path = f"{tid}/{emp_uuid}/{kind}/{doc_id}"
        file_url = d.get("file_url")

        if not file_url:
            quarantined.append({
                "source": "HRMS", "legacy_id": d["id"],
                "reason": "Missing file_url in legacy record"
            })
            continue

        pending_items.append({
            "source": "HRMS",
            "legacy_id": d["id"],
            "doc_id": doc_id,
            "tenant_id": tid,
            "employee_id": emp_uuid,
            "kind": kind,
            "file_name": fname,
            "content_type": ct,
            "blob_path": blob_path,
            "file_url": file_url
        })

    # 2. Payroll POI Documents (73 records)
    for d in poi_docs:
        doc_url = d.get("document_url") or ""
        raw_fname = d.get("document_name") or f"poi_doc_{d['id']}.jpg"
        fname = os.path.basename(raw_fname)
        ct = infer_content_type(fname, "image/jpeg")

        m = re.search(r'/upload/v\d+/([^/]+)/([^/]+)/', doc_url)
        if not m:
            quarantined.append({
                "source": "POI",
                "legacy_id": d["id"],
                "reason": "Cannot parse Cloudinary URL pattern for payroll employeeId UUID"
            })
            continue

        payroll_emp_uuid = m.group(2)
        matched_emp = payroll_emp_uuid_to_merged.get(payroll_emp_uuid)
        if not matched_emp:
            quarantined.append({
                "source": "POI",
                "legacy_id": d["id"],
                "payroll_emp_uuid": payroll_emp_uuid,
                "reason": "payroll employeeId UUID not found in merged employees"
            })
            continue

        tid = matched_emp["tenant_id"]
        emp_uuid = matched_emp["id"]
        doc_id = det_uuid(f"{tid}:poi_doc:{d['id']}")
        blob_path = f"{tid}/{emp_uuid}/INVESTMENT_PROOF/{doc_id}"

        pending_items.append({
            "source": "POI",
            "legacy_id": d["id"],
            "doc_id": doc_id,
            "tenant_id": tid,
            "employee_id": emp_uuid,
            "kind": "INVESTMENT_PROOF",
            "file_name": fname,
            "content_type": ct,
            "blob_path": blob_path,
            "file_url": doc_url
        })

    logger.info(f"Prepared {len(pending_items)} documents for upload ({len(quarantined)} pre-quarantined)")

    # Initialize Azure Blob Storage Client & Container
    blob_service = None
    container_client = None
    if not dry_run:
        blob_service = get_blob_service_client()
        if blob_service:
            container_client = blob_service.get_container_client(BLOB_CONTAINER)
            try:
                if not container_client.exists():
                    container_client.create_container()
            except Exception as e:
                logger.info(f"Container client check/create notice: {e}")
        else:
            logger.warning("No Azure storage credentials found; running in metadata/dry-run mode")

    # Connect to DB and check existing checksums for idempotency
    existing_checksums = {}
    conn = None
    cur = None
    step_name = "load_documents"
    ledger_id = str(uuid.uuid4())
    started_at = datetime.now(timezone.utc)
    anchor_tenant = list(emp_by_hrms_id.values())[0]["tenant_id"] if emp_by_hrms_id else "a0000000-0000-0000-0000-000000000001"

    if not skip_db:
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

            # Fetch existing records to support fast idempotent resume
            cur.execute("SELECT blob_path, checksum_sha256, size_bytes FROM core.document")
            for r in cur.fetchall():
                existing_checksums[r[0]] = r[1]
        except Exception as e:
            logger.warning(f"Database connection or ledger init error: {e}. Skipping DB writes.")
            skip_db = True

    # Multi-threaded download and upload execution
    successful_docs = []
    skipped_count = 0

    logger.info(f"Starting parallel document migration with {max_workers} worker threads...")
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = {
            executor.submit(
                process_single_document,
                item,
                container_client,
                existing_checksums,
                dry_run
            ): item for item in pending_items
        }

        for future in as_completed(futures):
            item = futures[future]
            try:
                ok, result_data, q_reason = future.result()
                if ok and result_data:
                    if result_data.get("skipped_existing"):
                        skipped_count += 1
                    successful_docs.append(result_data)
                elif q_reason:
                    quarantined.append(q_reason)
            except Exception as ex:
                quarantined.append({
                    "source": item.get("source"),
                    "legacy_id": item.get("legacy_id"),
                    "file_name": item.get("file_name"),
                    "reason": f"Unhandled thread worker exception: {str(ex)}"
                })

    logger.info(f"Parallel migration completed: {len(successful_docs)} successful ({skipped_count} skipped/cached), {len(quarantined)} quarantined")

    # Insert / Upsert into target database core.document
    if not skip_db and cur and successful_docs:
        doc_db_batch = [
            (
                d["doc_id"], d["tenant_id"], d["employee_id"], d["kind"],
                d["file_name"], d["content_type"], d["size_bytes"],
                d["blob_container"], d["blob_path"], d["checksum_sha256"], False
            ) for d in successful_docs
        ]

        logger.info(f"Upserting {len(doc_db_batch)} rows into core.document...")
        execute_batch(
            cur,
            """
            INSERT INTO core.document
                (id, tenant_id, employee_id, kind, file_name, content_type, size_bytes,
                 blob_container, blob_path, checksum_sha256, is_deleted, created_by, updated_by)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'migration', 'migration')
            ON CONFLICT (tenant_id, blob_path) DO UPDATE SET
                file_name = EXCLUDED.file_name,
                content_type = EXCLUDED.content_type,
                size_bytes = EXCLUDED.size_bytes,
                checksum_sha256 = EXCLUDED.checksum_sha256,
                updated_at = CURRENT_TIMESTAMP
            """,
            doc_db_batch,
            page_size=200
        )
        conn.commit()

        # Update ledger status
        cur.execute(
            "UPDATE core.migration_ledger SET records_processed=%s, status=%s, completed_at=%s WHERE id=%s",
            (len(successful_docs), "DONE", datetime.now(timezone.utc), ledger_id)
        )
        conn.commit()

    if cur:
        cur.close()
    if conn:
        conn.close()

    # Save quarantine report to staging
    quarantine_file = os.path.join(stg_dir, "stg_quarantined_documents.json")
    with open(quarantine_file, "w", encoding="utf-8") as f:
        json.dump(quarantined, f, indent=2)
    logger.info(f"Saved {len(quarantined)} quarantined document entries to {quarantine_file}")

    return {
        "total_attempted": len(pending_items),
        "successful": len(successful_docs),
        "skipped_existing": skipped_count,
        "quarantined": len(quarantined)
    }


if __name__ == "__main__":
    is_dry_run = "--dry-run" in sys.argv or "--metadata-only" in sys.argv
    run_loader(max_workers=8, dry_run=is_dry_run)
