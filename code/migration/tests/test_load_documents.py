"""W-69 load_documents against a real migrated Postgres, with Cloudinary and Azure Blob faked
(no network). The fake HTTP layer serves each fixture URL; the fake container keeps blobs in
memory and reads them back the way BlobClient.download_blob().readall() does."""
import hashlib
import json
import threading
import uuid

import pytest

from common.ids import det_id
from tests.fixtures import CUSTOMER_TENANT, FOUNDER_TENANT
from tests.pipeline import BASE, run_step, run_steps
from tests.test_load_claims import _setup as claims_setup

STEP = "loaders.load_documents"
MB = 1024 * 1024
PNG = b"\x89PNG\r\n\x1a\n"
JPG = b"\xff\xd8\xff\xe0"


def body(url: str) -> bytes:
    name = url.rsplit("/", 1)[1]
    if name == "huge.pdf":
        return b"%PDF-" + b"0" * (11 * MB)
    if name == "fake.pdf":
        return b"hello, I am not a PDF"
    prefix = {"pdf": b"%PDF-1.4 ", "png": PNG, "jpg": JPG}[name.rsplit(".", 1)[1]]
    return prefix + url.encode()


class FakeResponse:
    def __init__(self, status: int, content: bytes = b""):
        self.status_code = status
        self._content = content

    def iter_content(self, chunk_size=65536):
        for i in range(0, len(self._content), chunk_size):
            yield self._content[i:i + chunk_size]

    def close(self):
        pass


class FakeHttp:
    """requests.get stand-in: photo.jpg is gone (404), offer.pdf fails once (503)."""

    def __init__(self):
        self.calls = []
        self._lock = threading.Lock()

    def get(self, url, timeout=None, stream=False):
        assert timeout and stream
        with self._lock:
            self.calls.append(url)
            first = self.calls.count(url) == 1
        name = url.rsplit("/", 1)[1]
        if name == "photo.jpg":
            return FakeResponse(404)
        if name == "offer.pdf" and first:
            return FakeResponse(503)
        return FakeResponse(200, body(url))


class FakeBlob:
    def __init__(self, store, path, corrupt):
        self.store, self.path, self.corrupt = store, path, corrupt

    def upload_blob(self, data, overwrite=False, content_settings=None):
        assert overwrite
        self.store[self.path] = (bytes(data), content_settings.content_type)

    def download_blob(self):
        data = self.store[self.path][0]
        data = data + b"!" if self.corrupt else data

        class _Stream:
            def readall(self_inner):
                return data
        return _Stream()

    def delete_blob(self):
        self.store.pop(self.path, None)


class FakeContainer:
    def __init__(self, corrupt=False):
        self.blobs = {}
        self.corrupt = corrupt

    def get_blob_client(self, path):
        return FakeBlob(self.blobs, path, self.corrupt)


@pytest.fixture
def storage(monkeypatch):
    from loaders import load_documents

    http, container = FakeHttp(), FakeContainer()
    monkeypatch.setattr(load_documents.requests, "get", http.get)
    monkeypatch.setattr(load_documents, "container_client", lambda: container)
    monkeypatch.setattr(load_documents, "BACKOFF_SECONDS", 0)
    return http, container


def _prepare(staging_dir, query):
    """Organisation logo, then base + claims (with their masters), then the parents the
    tax and leave steps would have loaded: one proof item (customer) and one leave request (founder)."""
    orgs_path = staging_dir / "stg_payroll_organization.json"
    orgs = json.loads(orgs_path.read_text(encoding="utf-8"))
    for o in orgs:
        if o["id"] == 201:
            o["fileUrl"] = "https://res.cloudinary.com/demo/image/upload/v1700000000/logos/acme.png"
            o["fileName"] = "acme.png"
    orgs_path.write_text(json.dumps(orgs), encoding="utf-8")
    claims_setup(staging_dir, query)          # runs the base steps and seeds component masters
    run_step("loaders.load_claims")

    c001 = query("SELECT id::text FROM core.employee WHERE employee_number = 'C001'")[0][0]
    decl, proof = str(uuid.uuid4()), str(uuid.uuid4())
    query("INSERT INTO payroll.employee_investment_declaration (id, tenant_id, employee_id, financial_year, tax_regime) "
          "VALUES (%s, %s, %s, '2024-25', 'NEW')", (decl, CUSTOMER_TENANT, c001))
    query("INSERT INTO payroll.employee_proof_of_investment (id, tenant_id, employee_id, declaration_id, financial_year) "
          "VALUES (%s, %s, %s, %s, '2024-25')", (proof, CUSTOMER_TENANT, c001, decl))
    query("INSERT INTO payroll.employee_proof_item (id, tenant_id, proof_id, source_kind, source_line_id, description, "
          "declared_amount) VALUES (%s, %s, %s, 'HOUSE_RENT', %s, 'Rent', 1000)",
          (det_id(CUSTOMER_TENANT, "employee_poi_item", 502), CUSTOMER_TENANT, proof, str(uuid.uuid4())))

    e001 = query("SELECT id::text FROM core.employee WHERE employee_number = 'E001'")[0][0]
    lt = str(uuid.uuid4())
    query("INSERT INTO core.leave_type (id, tenant_id, code, name, is_paid, unit, allow_half_day, valid_from) "
          "VALUES (%s, %s, 'LEGACY', 'Legacy', true, 'DAYS', false, '2020-01-01')", (lt, FOUNDER_TENANT))
    query("INSERT INTO core.leave_request (id, tenant_id, employee_id, leave_type_id, from_date, to_date, working_days, "
          "status) VALUES (%s, %s, %s, %s, '2024-06-10', '2024-06-10', 1, 'APPROVED')",
          (det_id(FOUNDER_TENANT, "leave_requests", 1), FOUNDER_TENANT, e001, lt))


def _quarantine(staging_dir):
    return json.loads((staging_dir / "stg_quarantined_load_documents.json").read_text(encoding="utf-8"))


def test_files_uploaded_verified_and_linked(legacy_fixture, query, storage):
    http, container = storage
    _prepare(legacy_fixture, query)
    run_step(STEP)

    docs = query("SELECT id::text, tenant_id::text, employee_id::text, kind, label, file_name, content_type, "
                 "size_bytes, blob_container, blob_path, checksum_sha256 FROM core.document")
    assert sorted(d[5] for d in docs) == sorted(["aadhar.pdf", "degree.png", "offer.pdf", "rent.jpg", "lic.pdf",
                                                 "medical.pdf", "bill-1.pdf", "bill-4.png", "proof-11.pdf",
                                                 "acme.png"])
    assert len(container.blobs) == len(docs) == 10
    for d in docs:
        doc_id, tid, emp, kind, _, _, ctype, size, cont, path, sha = d
        assert cont == "documents" and path == f"{tid}/{emp or 'tenant'}/{kind}/{doc_id}"
        data, blob_type = container.blobs[path]
        assert size == len(data) and sha == hashlib.sha256(data).hexdigest() and blob_type == ctype
        assert "cloudinary" not in path and "http" not in d[5]          # no URL stored (Incident 3)
    by_name = {d[5]: d for d in docs}
    assert by_name["aadhar.pdf"][3:5] == ("EMPLOYEE_DOCUMENT", "ID_PROOF")
    assert by_name["degree.png"][3:5] == ("EMPLOYEE_DOCUMENT", "CERTIFICATE")
    assert by_name["offer.pdf"][3:5] == ("EMPLOYEE_DOCUMENT", "OFFER_LETTER")      # after one 503
    assert by_name["degree.png"][6] == "image/png" and by_name["rent.jpg"][6] == "image/jpeg"
    assert by_name["rent.jpg"][3] == "INVESTMENT_PROOF" and by_name["rent.jpg"][1] == FOUNDER_TENANT
    assert by_name["proof-11.pdf"][3:5] == ("EMPLOYEE_DOCUMENT", "OTHER")
    assert by_name["acme.png"][1:5] == (CUSTOMER_TENANT, None, "TENANT_LOGO", None)

    # links the parent loaders left empty
    assert query("SELECT c.description, d.file_name, d.kind FROM payroll.employee_reimbursement_request c "
                 "JOIN core.document d ON d.id = c.document_id ORDER BY 1") == [
        ("Claim 1", "bill-1.pdf", "REIMBURSEMENT_RECEIPT"), ("Claim 4", "bill-4.png", "REIMBURSEMENT_RECEIPT")]
    assert query("SELECT d.file_name, d.employee_id = x.employee_id FROM payroll.employee_deduction x "
                 "JOIN core.document d ON d.id = x.document_id") == [("proof-11.pdf", True)]
    assert query("SELECT d.file_name, l.tenant_id::text FROM payroll.employee_proof_item_document l "
                 "JOIN core.document d ON d.id = l.document_id") == [("lic.pdf", CUSTOMER_TENANT)]
    assert query("SELECT d.file_name FROM core.leave_request_document l JOIN core.document d ON d.id = l.document_id") \
        == [("medical.pdf",)]
    assert query("SELECT d.file_name FROM core.tenant t JOIN core.document d ON d.id = t.logo_document_id") \
        == [("acme.png",)]

    q = _quarantine(legacy_fixture)
    got = {(i["legacy_table"], i["legacy_id"]): i["reason"] for i in q}
    assert got == {
        ("employee_document", 4): "NOT_FOUND", ("employee_document", 5): "TOO_LARGE",
        ("employee_document", 6): "TYPE_NOT_ALLOWED", ("employee_document", 7): "EMPLOYEE_NOT_MIGRATED",
        ("employee_document", 8): "CONTENT_MISMATCH", ("employee_poi_document", 3): "EMPLOYEE_NOT_RESOLVED",
        ("employee_poi_document", 1): "LINK_SKIPPED_PROOF_ITEM_NOT_LOADED", ("leave_documents", 2): "PARENT_NOT_LOADED",
    }
    text = json.dumps(q)
    assert "://" not in text and ".pdf" not in text and "cloudinary" not in text   # ids and reasons only
    assert not any("huge.pdf" in p or "run.exe" in p for p in container.blobs)
    assert not any(u.endswith(("run.exe", "x.pdf", "pan.pdf")) for u in http.calls)  # refused before any GET

    ledger = {r[0]: r[1:] for r in query("SELECT tenant_id::text, status, records_processed, records_skipped "
                                         "FROM core.migration_ledger WHERE step_name = 'load_documents'")}
    # founder: HRMS 4, 5, 6, 7, 8 and leave 2 skipped; the unlinked POI note is not a skip
    assert ledger == {FOUNDER_TENANT: ("DONE", 7, 6), CUSTOMER_TENANT: ("DONE", 3, 0)}


def test_second_run_downloads_nothing_and_changes_nothing(legacy_fixture, query, storage):
    http, container = storage
    _prepare(legacy_fixture, query)
    run_step(STEP)
    tables = ["core.document", "payroll.employee_reimbursement_request", "payroll.employee_deduction",
              "core.leave_request_document", "core.tenant"]
    snap = lambda: ({t: query(f"SELECT count(*), max(updated_at) FROM {t}")[0] for t in tables},  # noqa: E731
                    query("SELECT count(*) FROM payroll.employee_proof_item_document")[0][0], dict(container.blobs))
    before, calls = snap(), len(http.calls)
    run_step(STEP)                                        # ledger: both tenants skipped
    query("DELETE FROM core.migration_ledger WHERE step_name = 'load_documents'")
    run_step(STEP)                                        # forced: rows hold checksums, nothing fetched
    assert snap() == before
    fetched_again = http.calls[calls:]
    assert all(u.rsplit("/", 1)[1] in ("photo.jpg", "huge.pdf", "fake.pdf") for u in fetched_again)
    assert query("SELECT count(*) FROM core.migration_ledger WHERE step_name = 'load_documents' "
                 "AND status = 'DONE'")[0][0] == 2


def test_checksum_mismatch_deletes_the_blob(legacy_fixture, query, storage, monkeypatch):
    from loaders import load_documents

    corrupt = FakeContainer(corrupt=True)
    monkeypatch.setattr(load_documents, "container_client", lambda: corrupt)
    run_steps(BASE)
    run_step(STEP)
    assert query("SELECT count(*) FROM core.document")[0][0] == 0
    assert corrupt.blobs == {}
    reasons = {i["reason"] for i in _quarantine(legacy_fixture)}
    assert "CHECKSUM_MISMATCH" in reasons


def test_storage_failure_fails_the_tenant_not_the_file(legacy_fixture, query, storage, monkeypatch):
    from loaders import load_documents

    class Down(FakeContainer):
        def get_blob_client(self, path):
            raise ConnectionError("storage unreachable")

    monkeypatch.setattr(load_documents, "container_client", lambda: Down())
    run_steps(BASE)
    assert load_documents.run() is False
    assert query("SELECT count(*) FROM core.document")[0][0] == 0
    assert ("FAILED",) in query("SELECT status FROM core.migration_ledger WHERE step_name = 'load_documents'")


def test_real_run_without_storage_credentials_refuses(staging_dir, monkeypatch):
    from loaders import load_documents

    monkeypatch.delenv("AZURE_STORAGE_CONNECTION_STRING", raising=False)
    monkeypatch.delenv("AZURE_STORAGE_ACCOUNT_URL", raising=False)
    with pytest.raises(RuntimeError, match="refusing to write document rows"):
        load_documents.run()
