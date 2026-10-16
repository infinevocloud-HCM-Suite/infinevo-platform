# W-69 — Document migration

| Field | Value |
|---|---|
| **Work item** | `W-69` · issue [#91](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/91) |
| **Kind** | Data |
| **Stream / track** | I — Migration |
| **Size / skill** | S · DATA |
| **Owner** | sayeed |
| **Blocked by** | `W-21` (done), `W-68` |
| **Blocks** | `W-71` |
| **Decisions** | `W-21` §6 (no URL column, private container, `blob_path` shape) |
| **Gaps addressed** | `DEBT-003` fixed, Incident 3 (public document URLs) honoured |
| **Status** | Ready |

---

## 1. Problem

Legacy documents live on Cloudinary as public URLs. HRMS `employee_document` has 205
rows with `file_url`, `file_name`, `file_type`, `document_type`
(`legacy/docs/DB_SCHEMA.md:152`). Payroll `employee_poi_document` has 73 rows with
`document_url`, `document_name` (`legacy/docs/DB_SCHEMA.md:1311`).

The branch loader `code/migration/loaders/load_documents.py` writes the `core.document`
row but not the file. It stores `size_bytes = 0`, a fake checksum built from the legacy
id (`load_documents.py:11-14`), and the container name `infinevo-documents`
(`load_documents.py:31`) where Azure has `documents` (`infra/azure/modules/storage.bicep:69-70`).
The build order says done means every document opens from Blob, verified by opening
files, not counting rows (`docs/target-state/09-build-order.md:309`).

**Baseline** (branch)

| Command | Output |
|---|---|
| `psql -c "select count(*), sum(size_bytes) from core.document"` after `load_documents.py` | 275 rows, sum 0 |

## 2. Scope

**In scope**

- Extend `load_documents.py`: download each Cloudinary file, upload to the private `documents` container at the `W-21` path, write real `size_bytes` and `checksum_sha256`.
- Verify by reading back from Blob.
- Container name fixed to `documents`.
- Every legacy file reference, per `W-67` R6 (2026-10-16):

| Legacy source | Rows (non-prod) | Target row | Link column filled |
|---|---|---|---|
| HRMS `employee_document.file_url` | 205 | `core.document kind EMPLOYEE_DOCUMENT` | `employee_id` |
| Payroll `employee_poi_document.document_url` | 73 | `core.document kind INVESTMENT_PROOF` | `payroll.employee_proof_item_document.document_id` |
| HRMS `leave_documents.file_url` | small | `core.document kind LEAVE_ATTACHMENT` | `core.leave_request_document.document_id` |
| Payroll `employee_reimbursement_request.attachment_url` | small | `core.document kind CLAIM_ATTACHMENT` | `payroll.employee_reimbursement_request.document_id` |
| Payroll `employee_deduction.proof_url` | small | `core.document kind DEDUCTION_PROOF` | `payroll.employee_deduction.document_id` |
| Payroll `organization.fileUrl` (logo) | ≤152 | `core.document kind TENANT_LOGO`, no employee | `core.tenant.logo_document_id` |

`W-68` loads the parent rows with the link column NULL and records the legacy URL in `staging/id_map.json`; this ticket fills the link after the verified upload.

**Out of scope**

- Deleting from Cloudinary — never. Old system stays readable (`09-build-order.md:315`).
- Running in Azure — `W-71`.

## 3. Flow

```
staging/stg_hrms_employee_document.json ─┐
staging/stg_payroll_employee_poi_document.json ─┤
                                          ▼
   load_documents.py: match employee → GET file_url → sha256 + size
        → upload documents/{tenantId}/{employeeId}/{kind}/{docId}
        → GET blob back, compare sha256
        → INSERT core.document (real size, real checksum)
        → ledger row  step_name = 'load_documents'
```

## 4. Implementer tasks

### Task 1 — `code/migration/loaders/load_documents.py`

| Change | Detail |
|---|---|
| Container | `BLOB_CONTAINER = "documents"` (`load_documents.py:31`) |
| Fetch | `requests.get(file_url, timeout=30, stream=True)` for HRMS (`file_url`) and POI (`document_url`). Over 10 MB or a content type outside `pdf jpg png xlsx csv` goes to the quarantine file with the reason; `W-21` refuses those at upload (`W-21-document-store.md:51`), so the migration must too |
| Checksum, size | `hashlib.sha256` over the bytes; `len(bytes)`. Replace `det_checksum` and the `0` placeholder (`load_documents.py:137-145`, `:170-180`) |
| Upload | `azure.storage.blob.BlobClient` (already in `requirements.txt`) with `DefaultAzureCredential`; env `AZURE_STORAGE_ACCOUNT_URL`. Locally, Azurite via `AZURE_STORAGE_CONNECTION_STRING`. `overwrite=True`, so a re-run is safe |
| Verify | After upload, `download_blob().readall()`, sha256 again, must equal. A mismatch deletes the blob and quarantines the row |
| Row | Insert only after the verified upload. Keep `ON CONFLICT (tenant_id, blob_path)` and extend it to update `size_bytes`, `checksum_sha256` |
| Ledger | One `core.migration_ledger` row, `records_processed`, `records_skipped` (quarantined), `DONE` or `FAILED`. Re-run skips documents whose row already carries a matching checksum |
| Resilience | Retry a Cloudinary `GET` three times with back-off; a 404 quarantines, does not fail the run |
| Parallel | `concurrent.futures.ThreadPoolExecutor(max_workers=8)` for fetch and upload; 278 files, under 5 minutes |

### Task 2 — `code/migration/tests/test_load_documents.py`

Testcontainers Postgres plus Azurite (`mcr.microsoft.com/azure-storage/azurite`), and
`responses` to mock Cloudinary.

| Assertion |
|---|
| 3 HRMS and 2 POI fixtures: 5 blobs exist at `{tenantId}/{employeeId}/{kind}/{docId}` in `documents` |
| `core.document.size_bytes` equals the fixture byte length, `checksum_sha256` equals its sha256 |
| A 404 fixture lands in quarantine, run status `DONE`, `records_skipped = 1` |
| An 11 MB fixture is quarantined, not uploaded |
| Second run: blob count unchanged, no row `updated_at` changes |

## 5. Standing rules

| Rule | Impact |
|---|---|
| New table | None. `core.document` exists from `W-21` with `tenant_id`, RLS, `(tenant_id, blob_path)` unique |
| Flyway | None |
| Money | None |
| Index | Present (`W-21-document-store.md:129`) |
| Expand / contract | Rows only |
| Module boundary | Python only; no Java change. The Java `AzureBlobStorage` is not reused, the loader talks to Blob directly |

## 6. Gaps

| Gap | Treatment |
|---|---|
| Incident 3, public document URLs | Honoured — no URL stored; `file_name` keeps the original name, not the Cloudinary URL the branch stored as an audit trail |
| `DEBT-003` | Fixed — Task 2 |

## 7. Verification

```bash
cd code/migration && python -m pytest tests/test_load_documents.py -q    # expected: 5 passed
grep -n "infinevo-documents" loaders/load_documents.py                     # expected: no output
# against the compose stack with Azurite, after W-68 loaders:
python loaders/load_documents.py
psql -c "select count(*), count(*) filter (where size_bytes=0), count(*) filter (where checksum_sha256 like 'placeholder%') from core.document"
                                                   # expected: 278 | 0 | 0   (minus quarantined)
az storage blob list --container-name documents --account-name stinfinevodev --auth-mode login --query "length(@)"
                                                   # expected: equals the row count
```

| Check | Expected |
|---|---|
| Open 10 random documents through `GET /api/v1/documents/{id}/download` as tenant HR | each returns the file, bytes equal the Cloudinary original |
| `stg_quarantined_documents.json` | every entry has a reason; founder reads it |

## 8. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Cloudinary links expired or private | Medium | Quarantine with reason; a count over 5 % stops `W-71` rehearsal sign-off |
| POI URL pattern does not match (`load_documents.py:156`) | Medium | Already quarantined by the branch; test covers it |
| Files over 10 MB | Low | Quarantined; founder decides per file |

## 9. Rollback

Blobs under a tenant prefix are deleted with `az storage blob delete-batch --pattern
"{tenantId}/*"`; rows go with `infra/postgres/purge-tenant.sql`. Cloudinary is untouched.
