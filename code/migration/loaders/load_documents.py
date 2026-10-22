"""W-69 — legacy files to the private Blob container, one core.document row per verified file.

  HRMS employee_document.file_url               -> core.document EMPLOYEE_DOCUMENT (employee_id, label)
  Payroll employee_poi_document.document_url     -> core.document INVESTMENT_PROOF
                                                    + payroll.employee_proof_item_document
  HRMS leave_documents.file_url                  -> core.document LEAVE_ATTACHMENT + core.leave_request_document
  Payroll employee_reimbursement_request.attachment_url
                                                 -> core.document REIMBURSEMENT_RECEIPT
                                                    -> payroll.employee_reimbursement_request.document_id
  Payroll employee_deduction.proof_url           -> core.document EMPLOYEE_DOCUMENT (label OTHER)
                                                    -> payroll.employee_deduction.document_id
  Payroll organization.fileUrl (logo)            -> core.document TENANT_LOGO -> core.tenant.logo_document_id

Kinds follow the core.document CHECK, which wins over the spec's names: the spec's
CLAIM_ATTACHMENT is REIMBURSEMENT_RECEIPT (the kind ReimbursementClaimServiceImpl demands),
and it has no DEDUCTION_PROOF - EmployeeDeductionServiceImpl.requireEmployeeDocument
accepts only an EMPLOYEE_DOCUMENT of the same employee, so a deduction proof is one.

Per file: GET the legacy URL (three tries with back-off; a 404 quarantines), refuse over
10 MB or outside pdf/jpg/png/xlsx/csv or whose bytes do not start the way that type must
(DocumentType.java signatures, so the app would accept it), sha256 + size, upload to
documents/{tenantId}/{employeeId|tenant}/{kind}/{docId} (DocumentServiceImpl.blobPath),
read it back and compare sha256 (mismatch: blob deleted, row quarantined), and only then
write the row. A document whose row already carries a checksum is not downloaded again.
A storage failure is not quarantined: it fails the tenant, so the next run retries it.

A real run without storage credentials raises before touching anything: rows without
uploads would be skipped as "done" by every later run.
"""
from __future__ import annotations

import hashlib
import logging
import os
import re
import sys
import time
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass

import requests

from common import db, staging
from common.ids import child_id, det_id
from common.idmap import IdMap
from common.ledger import StepResult, run_per_tenant
from common.legacy import text
from common.quarantine import Quarantine
from common.tenants import Tenant, Tenants

STEP = "load_documents"
BLOB_CONTAINER = "documents"
MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024
MAX_WORKERS = 8
RETRIES = 3
BACKOFF_SECONDS = 0.5
TIMEOUT_SECONDS = 30

HRMS_DOCS = "stg_hrms_employee_document.json"
POI_DOCS = "stg_payroll_employee_poi_document.json"
LEAVE_DOCS = "stg_hrms_leave_documents.json"
CLAIMS = "stg_payroll_employee_reimbursement_request.json"
DEDUCTIONS = "stg_payroll_employee_deduction.json"
ORGS = "stg_payroll_organization.json"
# The parents and id-map entries this step resolves come from the last four.
INPUTS = [HRMS_DOCS, POI_DOCS, LEAVE_DOCS, CLAIMS, DEDUCTIONS, ORGS, "stg_payroll_employee_poi_item.json",
          "stg_hrms_leave_requests.json", "stg_merged_employees.json", "stg_payroll_employee.json"]

# extension -> (content type stored, leading bytes the file must have; None = text, no NUL)
TYPES = {
    "pdf": ("application/pdf", b"%PDF-"),
    "jpg": ("image/jpeg", b"\xff\xd8\xff"),
    "jpeg": ("image/jpeg", b"\xff\xd8\xff"),
    "png": ("image/png", b"\x89PNG\r\n\x1a\n"),
    "xlsx": ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", b"PK\x03\x04"),
    "csv": ("text/csv", None),
}
TEXT_SNIFF_BYTES = 8192

# HRMS DocumentType -> core.document label (ID_PROOF, ADDRESS_PROOF, OFFER_LETTER, CONTRACT, CERTIFICATE, OTHER)
LABELS = {
    **dict.fromkeys(("AADHAR_CARD", "OTHER_IDENTIFICATION", "PAN_CARD", "EPIC_CARD", "OTHER_GOVERNMENT_ID",
                     "PASSPORT"), "ID_PROOF"),
    **dict.fromkeys(("RENT_AGREEMENT", "LIGHT_BILL", "BANK_STATEMENT", "OTHER_ADDRESS_PROOF"), "ADDRESS_PROOF"),
    **dict.fromkeys(("TENTH_CERTIFICATE", "TWELFTH_CERTIFICATE_DIPLOMA", "DEGREE_CERTIFICATE",
                     "POST_GRADUATION_CERTIFICATE", "OTHER_QUALIFICATION", "EXPERIENCE_LETTER",
                     "RELIEVING_LETTER"), "CERTIFICATE"),
    "APPOINTMENT_LETTER": "OFFER_LETTER",
}
# id-map key prefix per legacy table; documents go under "<system>.<legacy_table>.document"
SYSTEM = {"employee_document": "hrms", "leave_documents": "hrms", "employee_poi_document": "payroll",
          "employee_reimbursement_request": "payroll", "employee_deduction": "payroll", "organization": "payroll"}
# Payroll POI uploads: .../upload/v<n>/<folder>/<employee UUID>/<file> (branch load_documents.py:156)
POI_URL = re.compile(r"/upload/v\d+/([^/]+)/([^/]+)/")
log = logging.getLogger(STEP)


@dataclass
class Job:
    source: str          # legacy table, for the quarantine
    legacy_id: object
    tenant_id: str
    doc_id: str
    employee_id: str | None
    kind: str
    label: str | None
    file_name: str
    url: str
    link: tuple = ()     # ("proof_item", id) | ("leave_request", id) | ("claim", id) | ("deduction", id) | ("logo",)

    @property
    def blob_path(self) -> str:
        return f"{self.tenant_id}/{self.employee_id or 'tenant'}/{self.kind}/{self.doc_id}"


class TransferRefused(Exception):
    """A file that goes to quarantine: reason code plus ids-only detail."""

    def __init__(self, reason: str, **detail):
        super().__init__(reason)
        self.reason = reason
        self.detail = detail


def container_client():
    """The documents container: Azure with DefaultAzureCredential (AZURE_STORAGE_ACCOUNT_URL),
    or Azurite / a connection string (AZURE_STORAGE_CONNECTION_STRING). No credentials: raise."""
    from azure.storage.blob import BlobServiceClient

    conn_str = os.environ.get("AZURE_STORAGE_CONNECTION_STRING")
    account_url = os.environ.get("AZURE_STORAGE_ACCOUNT_URL")
    if conn_str:
        service = BlobServiceClient.from_connection_string(conn_str)
    elif account_url:
        from azure.identity import DefaultAzureCredential

        service = BlobServiceClient(account_url, credential=DefaultAzureCredential())
    else:
        raise RuntimeError(
            "No Azure storage credentials (AZURE_STORAGE_ACCOUNT_URL or AZURE_STORAGE_CONNECTION_STRING); "
            "refusing to write document rows without uploads.")
    container = service.get_container_client(BLOB_CONTAINER)
    if conn_str:  # Azurite starts empty; in Azure the container is Bicep's (storage.bicep)
        try:
            if not container.exists():
                container.create_container()
        except Exception as exc:  # noqa: BLE001 - the upload will report a real problem
            log.info("container check: %s", type(exc).__name__)
    return container


def extension(file_name: str | None, url: str | None) -> str:
    for candidate in (file_name, (url or "").split("?")[0]):
        base = os.path.basename(candidate or "")
        if "." in base:
            return base.rsplit(".", 1)[1].lower()
    return ""


def fetch(url: str) -> bytes:
    """The file's bytes; TransferRefused when it cannot or must not be had."""
    reason, detail = "DOWNLOAD_FAILED", {}
    for attempt in range(RETRIES):
        if attempt:
            time.sleep(BACKOFF_SECONDS * (2 ** (attempt - 1)))
        try:
            resp = requests.get(url, timeout=TIMEOUT_SECONDS, stream=True)
        except requests.RequestException as exc:
            reason, detail = "DOWNLOAD_FAILED", {"error": type(exc).__name__}
            continue
        try:
            if resp.status_code == 404:
                raise TransferRefused("NOT_FOUND", http_status=404)
            if resp.status_code != 200:
                reason, detail = "HTTP_ERROR", {"http_status": resp.status_code}
                continue
            buf = bytearray()
            for chunk in resp.iter_content(chunk_size=65536):
                buf += chunk
                if len(buf) > MAX_FILE_SIZE_BYTES:
                    raise TransferRefused("TOO_LARGE", max_bytes=MAX_FILE_SIZE_BYTES)
            return bytes(buf)
        finally:
            resp.close()
    raise TransferRefused(reason, **detail)


def transfer(job: Job, container) -> dict:
    """Download, check, upload, verify; the core.document row for a verified file."""
    ext = extension(job.file_name, job.url)
    if ext not in TYPES:
        raise TransferRefused("TYPE_NOT_ALLOWED", extension=ext[:10] or None)
    content_type, signature = TYPES[ext]
    content = fetch(job.url)
    if not content or (signature is not None and not content.startswith(signature)) or \
            (signature is None and b"\x00" in content[:TEXT_SNIFF_BYTES]):
        raise TransferRefused("CONTENT_MISMATCH", extension=ext)
    sha = hashlib.sha256(content).hexdigest()

    from azure.storage.blob import ContentSettings

    blob = container.get_blob_client(job.blob_path)
    blob.upload_blob(content, overwrite=True, content_settings=ContentSettings(content_type=content_type))
    if hashlib.sha256(blob.download_blob().readall()).hexdigest() != sha:
        try:
            blob.delete_blob()
        finally:
            raise TransferRefused("CHECKSUM_MISMATCH")
    return {
        "id": job.doc_id, "tenant_id": job.tenant_id, "employee_id": job.employee_id, "kind": job.kind,
        "label": job.label, "file_name": job.file_name[:255], "content_type": content_type,
        "size_bytes": len(content), "blob_container": BLOB_CONTAINER, "blob_path": job.blob_path,
        "checksum_sha256": sha, "created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER,
    }


def _file_name(name, url, fallback: str) -> str:
    return os.path.basename(text(name) or "") or os.path.basename((url or "").split("?")[0]) or fallback


def _ids(values) -> list[str]:
    return sorted({str(v) for v in values if v})


def run(conn=None) -> bool:
    container = container_client()  # before anything else: no storage, no run
    tenants = Tenants.load()
    idmap = IdMap.load()
    q = Quarantine(STEP)
    jobs = defaultdict(list)  # tenant_id -> [Job]
    pre_skipped = defaultdict(int)

    def refuse(reason, source, lid, tid=None, **detail):
        q.add(reason, source, lid, tid, **detail)
        if tid:
            pre_skipped[tid] += 1

    def payroll_employee(uuid_ref):
        emp = idmap.get("payroll.employee.uuid", uuid_ref)
        return (emp, idmap.get("employee.tenant", emp)) if emp else (None, None)

    # HRMS employee documents: the employee by HRMS id; HRMS data is the founder's (W-67 R5).
    for d in staging.load(HRMS_DOCS):
        emp = idmap.get("hrms.employee", d.get("employee_id"))
        tid = idmap.get("employee.tenant", emp) if emp else (tenants.founder.tenant_id if tenants.founder else None)
        if not emp:
            refuse("EMPLOYEE_NOT_MIGRATED", "employee_document", d.get("id"), tid)
            continue
        if not text(d.get("file_url")):
            refuse("NO_URL", "employee_document", d.get("id"), tid)
            continue
        jobs[tid].append(Job("employee_document", d.get("id"), tid, det_id(tid, "employee_document", d.get("id")),
                             emp, "EMPLOYEE_DOCUMENT", LABELS.get(text(d.get("document_type")) or "", "OTHER"),
                             _file_name(d.get("file_name"), d.get("file_url"), f"document-{d.get('id')}"),
                             d.get("file_url")))

    # Claim receipts and deduction proofs: the row's employee; the row itself must have loaded (checked in work).
    for source, rows, url_key, name_key in (("employee_reimbursement_request", staging.load(CLAIMS),
                                             "attachment_url", "attachment_file_name"),
                                            ("employee_deduction", staging.load(DEDUCTIONS), "proof_url", None)):
        for r in rows:
            url = text(r.get(url_key))
            if not url:
                continue
            emp, tid = payroll_employee(r.get("employee_id"))
            if not emp:
                refuse("EMPLOYEE_NOT_MIGRATED", source, r.get("id"))
                continue
            lid = r.get("id")
            if source == "employee_reimbursement_request":
                job = Job(source, lid, tid, det_id(tid, f"{source}.attachment", lid), emp, "REIMBURSEMENT_RECEIPT",
                          None, _file_name(r.get(name_key), url, f"receipt-{lid}"), url,
                          ("claim", idmap.get(f"payroll.{source}", lid) or det_id(tid, source, lid)))
            else:
                job = Job(source, lid, tid, det_id(tid, f"{source}.proof", lid), emp, "EMPLOYEE_DOCUMENT", "OTHER",
                          _file_name(None, url, f"deduction-proof-{lid}"), url,
                          ("deduction", idmap.get(f"payroll.{source}", lid) or det_id(tid, source, lid)))
            jobs[tid].append(job)

    # Organisation logos, for organisations that became tenants.
    for o in staging.load(ORGS):
        t = tenants.for_org(o.get("id"))
        url = text(o.get("fileUrl"))
        if t is None or not url:
            continue
        jobs[t.tenant_id].append(Job("organization", o.get("id"), t.tenant_id,
                                     det_id(t.tenant_id, "organization.logo", o.get("id")), None, "TENANT_LOGO",
                                     None, _file_name(o.get("fileName"), url, f"logo-{o.get('id')}"), url, ("logo",)))

    poi_docs = staging.load(POI_DOCS)
    leave_docs = staging.load(LEAVE_DOCS)

    own = conn is None
    conn = conn or db.connect()
    try:
        # POI: the proof item says whose it is. Find the loaded items, tenant by tenant (read only).
        items = {}  # legacy poi_item_id -> (tenant_id, item_id, employee_id)
        with conn.cursor() as cur:
            cur.execute("SELECT tenant_id::text FROM core.tenant")
            existing = {r[0] for r in cur.fetchall()}
            live = [t for t in tenants.all() if t.tenant_id in existing]
            legacy_items = {str(d.get("poi_item_id")) for d in poi_docs if d.get("poi_item_id") is not None}
            for t in live:
                db.set_tenant(cur, t.tenant_id)
                cand = {}
                for li in legacy_items:
                    for c in (idmap.get("payroll.employee_poi_item", li), det_id(t.tenant_id, "employee_poi_item", li)):
                        if c:
                            cand[c] = li
                cur.execute(
                    "SELECT i.id::text, p.employee_id::text FROM payroll.employee_proof_item i "
                    "JOIN payroll.employee_proof_of_investment p ON p.id = i.proof_id AND p.tenant_id = i.tenant_id "
                    "WHERE i.tenant_id = %s AND i.id = ANY(%s::uuid[])", (t.tenant_id, list(cand)))
                for item_id, emp in cur.fetchall():
                    items[cand[item_id]] = (t.tenant_id, item_id, emp)
        conn.rollback()

        for d in poi_docs:
            lid, url = d.get("id"), text(d.get("document_url"))
            found = items.get(str(d.get("poi_item_id")))
            if found:
                tid, item_id, emp = found
                link = ("proof_item", item_id)
            else:
                m = POI_URL.search(url or "")
                emp, tid = payroll_employee(m.group(2)) if m else (None, None)
                link = ()
                if emp:
                    # The document still belongs to the employee; only the link to the item is missing.
                    q.add("LINK_SKIPPED_PROOF_ITEM_NOT_LOADED", "employee_poi_document", lid, tid,
                          poi_item_id=d.get("poi_item_id"), note="document loaded without the proof item link")
            if not emp:
                refuse("EMPLOYEE_NOT_RESOLVED", "employee_poi_document", lid)
                continue
            if not url:
                refuse("NO_URL", "employee_poi_document", lid, tid)
                continue
            jobs[tid].append(Job("employee_poi_document", lid, tid, det_id(tid, "employee_poi_document", lid), emp,
                                 "INVESTMENT_PROOF", None, _file_name(d.get("document_name"), url, f"proof-{lid}"),
                                 url, link))

        def work(cur, t: Tenant) -> StepResult:
            tid = t.tenant_id
            mine = list(jobs.get(tid, []))
            before = q.count(tid) - pre_skipped[tid]  # pre-work notes cancel out; pre-work refusals count

            # Leave attachments (founder tenant): the leave request gives the employee.
            if tenants.founder and tid == tenants.founder.tenant_id and leave_docs:
                cand = {}
                for d in leave_docs:
                    li = d.get("leave_request_id")
                    cand[str(d.get("id"))] = idmap.get("hrms.leave_requests", li) or det_id(tid, "leave_requests", li)
                cur.execute("SELECT id::text, employee_id::text FROM core.leave_request "
                            "WHERE tenant_id = %s AND id = ANY(%s::uuid[])", (tid, _ids(cand.values())))
                loaded = dict(cur.fetchall())
                for d in leave_docs:
                    lid, lr = d.get("id"), cand[str(d.get("id"))]
                    if lr not in loaded:
                        q.add("PARENT_NOT_LOADED", "leave_documents", lid, tid, leave_request_id=d.get("leave_request_id"))
                    elif not text(d.get("file_url")):
                        q.add("NO_URL", "leave_documents", lid, tid)
                    else:
                        mine.append(Job("leave_documents", lid, tid, det_id(tid, "leave_documents", lid), loaded[lr],
                                        "LEAVE_ATTACHMENT", None,
                                        _file_name(d.get("file_name"), d.get("file_url"), f"leave-{lid}"),
                                        d.get("file_url"), ("leave_request", lr)))

            # Parents written by earlier steps must be there: employees, claims, deductions.
            cur.execute("SELECT id::text FROM core.employee WHERE tenant_id = %s AND id = ANY(%s::uuid[])",
                        (tid, _ids(j.employee_id for j in mine)))
            employees = {r[0] for r in cur.fetchall()}
            parents = {}
            for kind, table in (("claim", "payroll.employee_reimbursement_request"),
                                ("deduction", "payroll.employee_deduction")):
                cur.execute(f"SELECT id::text FROM {table} WHERE tenant_id = %s AND id = ANY(%s::uuid[])",
                            (tid, _ids(j.link[1] for j in mine if j.link[:1] == (kind,))))
                parents[kind] = {r[0] for r in cur.fetchall()}
            ready = []
            for j in mine:
                if j.employee_id and j.employee_id not in employees:
                    q.add("EMPLOYEE_NOT_MIGRATED", j.source, j.legacy_id, tid)
                elif j.link[:1] in (("claim",), ("deduction",)) and j.link[1] not in parents[j.link[0]]:
                    q.add("PARENT_NOT_LOADED", j.source, j.legacy_id, tid)
                else:
                    ready.append(j)

            # Idempotent by checksum: a row that already holds one is not fetched again.
            cur.execute("SELECT id::text, checksum_sha256 FROM core.document WHERE tenant_id = %s", (tid,))
            done = {i for i, c in cur.fetchall() if c and re.fullmatch(r"[0-9a-f]{64}", c)}
            todo = [j for j in ready if j.doc_id not in done]

            def one(j: Job):
                try:
                    return j, transfer(j, container), None
                except TransferRefused as refused:
                    return j, None, refused

            rows = []
            with ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
                for j, row, refused in pool.map(one, todo):
                    if refused:
                        q.add(refused.reason, j.source, j.legacy_id, tid, **refused.detail)
                    else:
                        rows.append(row)
            db.upsert(cur, "core.document", rows, conflict=("tenant_id", "blob_path"), keep_on_update=("id",))
            stored = done | {r["id"] for r in rows}

            # Fill the links the parent loaders left empty.
            linked = [j for j in ready if j.doc_id in stored]
            for j in linked:
                idmap.put(f"{SYSTEM[j.source]}.{j.source}.document", j.legacy_id, j.doc_id)
            db.upsert(cur, "payroll.employee_proof_item_document",
                      [{"id": child_id(j.doc_id, "proof_item"), "tenant_id": tid, "item_id": j.link[1],
                        "document_id": j.doc_id, "created_by": db.AUDIT_USER}
                       for j in linked if j.link[:1] == ("proof_item",)],
                      conflict=("tenant_id", "item_id", "document_id"), keep_on_update=("id",))
            db.upsert(cur, "core.leave_request_document",
                      [{"id": child_id(j.doc_id, "leave_request"), "tenant_id": tid, "leave_request_id": j.link[1],
                        "document_id": j.doc_id, "created_by": db.AUDIT_USER, "updated_by": db.AUDIT_USER}
                       for j in linked if j.link[:1] == ("leave_request",)],
                      conflict=("tenant_id", "leave_request_id", "document_id"), keep_on_update=("id", "updated_by"))
            for kind, table in (("claim", "payroll.employee_reimbursement_request"),
                                ("deduction", "payroll.employee_deduction")):
                for j in linked:
                    if j.link[:1] == (kind,):
                        cur.execute(f"UPDATE {table} SET document_id = %s, updated_by = %s, updated_at = now() "
                                    "WHERE tenant_id = %s AND id = %s AND document_id IS DISTINCT FROM %s",
                                    (j.doc_id, db.AUDIT_USER, tid, j.link[1], j.doc_id))
            for j in linked:
                if j.link == ("logo",):
                    cur.execute("UPDATE core.tenant SET logo_document_id = %s, updated_by = %s, updated_at = now() "
                                "WHERE tenant_id = %s AND logo_document_id IS DISTINCT FROM %s",
                                (j.doc_id, db.AUDIT_USER, tid, j.doc_id))
            return StepResult(processed=len(linked), skipped=q.count(tid) - before)

        summary = run_per_tenant(conn, STEP, tenants.all(), staging.checksum(INPUTS), work)
    finally:
        if own:
            conn.close()
    idmap.save()
    q.write()
    return summary.ok


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    sys.exit(0 if run() else 1)
