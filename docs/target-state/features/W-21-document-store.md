# Feature: Document store

| Field | Value |
|---|---|
| **Feature ID** | `W-21` · ticket #25 · `CORE-13` |
| **Promoted to** | `docs/target-state/features/W-21-document-store.md` on branch `W-21-document-store` |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration`, `infra/azure` (config only) |
| **Related gaps** | Incident 3 (fixed), DEBT-004 (discounted), DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-13.1` — most documents hang off an employee · `W-11.3` — adds the four `core.document.*` codes to the catalogue |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 row 16 and §6 decision 8 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one script, one table — `core.document` | 1 |
| Externally testable behaviour | a file is uploaded, then downloaded only by someone holding a valid unexpired link | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

Files are scattered across two Cloudinary accounts and seven tables, and almost none of them
are access-controlled.

- Two separate Cloudinary configurations — `legacy/Payroll-Bend-SBoot/.../config/CloudinaryConfig.java:1-31` and `legacy/HRMS_Backend/.../config/CloudinaryConfig.java:1-33`
- Seven tables hold a URL and a public id: `employee_document`, `leave_documents`, `employee_reimbursement_request`, `employee_deduction`, `employee_investment_proof_file`, `proof_of_investment_document`, `employee_poi_document`
- **Cloudinary URLs are public.** Anyone holding the URL downloads the file, with no session and no tenant check. Only payslips use a signed link
- The one signed path leaks its own signature: `log.info(... token={} ...)` at `legacy/Payroll-Bend-SBoot/.../controller/payruns/PublicPayslipController.java:43-44`, at INFO level — incident 3 in `active-work.md`

The payslip signing itself is sound and is what `09-build-order.md:204` says to copy:
HMAC-SHA256 over `payrunId:employeeId:orgId`, Base64-URL encoded, verified by regeneration and
constant-time comparison — `legacy/Payroll-Bend-SBoot/.../serviceimpl/payruns/PayslipTokenServiceImpl.java:19-30,33-39`.

Two things about it must **not** be copied: the token never expires, and the secret has a
committed default — `${app.payslip.download.secret-key:default-payslip-secret-key-2026-xyz}`.

## 2. Scope

**In scope**

- `core.document` — one blob pointer for every file in the platform, **with or without an employee**: an export (`W-23.1`) belongs to the tenant, not a person
- Upload to Azure Blob Storage, private container, no public access — at most **10 MB**, content types `pdf`, `jpg`, `png`, `xlsx`, `csv` (contracts §5 row 16)
- Download by a **signed, expiring** link, tenant-scoped, and employee-scoped where the row has one
- The signing service, with the secret from Key Vault (`W-56`) and no default value
- A structured log that cannot contain a signature

**Out of scope**

- Migrating the existing Cloudinary files — `W-67`
- Payslip rendering — `W-36`; payslips become a consumer of this store
- Proof-of-investment workflow — `W-34`
- Virus scanning — `W-59` owns scanning
- Retention and lifecycle deletion — see decision 2

## 3. Flow

```
[client] --POST multipart--> [DocumentController] --> [DocumentService]
   --> [Blob container, private] --> [core.document row under RLS]

[client] --GET /documents/{id}/link--> [signed URL, expires]
   --> [Blob] directly, never through the app
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../document/DocumentController.java` | new |
| Service | `core/.../document/DocumentService.java` | new |
| Service | `core/.../document/DocumentLinkService.java` | new — signing and verification, ported from `PayslipTokenServiceImpl` |
| Entity | `core/.../document/Document.java` | new, `@Table(schema="core")` |
| Repository | `core/.../document/DocumentRepository.java` | new |
| Enumeration | `core/.../document/DocumentKind.java` | new — `EMPLOYEE_DOCUMENT, LEAVE_ATTACHMENT, REIMBURSEMENT_RECEIPT, INVESTMENT_PROOF, PAYSLIP, EXPORT` (`PAYSLIP` for `W-36`, `EXPORT` for `W-23.1`; contracts §1, §5 row 16) |
| Config | `app/src/main/resources/application.yml` | change — blob container and `document.link.secret` from environment, **no default**; `document.upload.max-bytes: 10485760`; the allowed content-type list |

**The seams** (`12-core-contracts.md:107`):

| Seam | Contract |
|---|---|
| `DocumentService.store(DocumentKind kind, UUID employeeId, String filename, InputStream content)` → `UUID` | `employeeId` **nullable**; writes the blob, then the row; refuses size over 10 MB and a content type outside the five |
| `DocumentLinkService.signedLink(UUID documentId, Duration ttl)` → `{url, expiresAt}` | the only way to obtain a URL; the caller chooses the lifetime, bounded at 7 days |

Two lifetimes, both settled here: **15 minutes** for an interactive download from a screen,
**7 days** for a link placed in an email (`W-23.2`'s scheduled report, `W-36`'s payslip;
contracts §6 decision 8). `signedLink` rejects a `Duration` above 7 days.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/documents` | multipart file + kind + employeeId (optional) | `201` + document id; `413` over 10 MB; `415` outside the five types | `@RequiresAction("core.document.upload")` |
| GET | `/api/v1/documents/{id}` | — | metadata, never the bytes | `core.document.read`, or `core.document.read_own` when `employee_id` is the caller |
| GET | `/api/v1/documents/{id}/link` | — | `{url, expiresAt}`, 15-minute link | same as the metadata read |
| DELETE | `/api/v1/documents/{id}` | — | `204`, soft delete | `@RequiresAction("core.document.delete")` |

The four codes arrive with `W-11.3` (`12-core-contracts.md:128`). A document with no
`employee_id` is never readable through `read_own`.

The bytes never pass through the application. The app issues a link; Blob serves the file.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__document.sql` | `core.document` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` · `employee_id uuid NULL REFERENCES core.employee(id)` ·
`kind varchar(32) NOT NULL` · `file_name varchar(255) NOT NULL` · `content_type varchar(128) NOT NULL` ·
`size_bytes bigint NOT NULL` · `blob_container varchar(64) NOT NULL` · `blob_path varchar(512) NOT NULL` ·
`checksum_sha256 char(64) NOT NULL` · `is_deleted boolean NOT NULL DEFAULT false` · four audit columns.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, employee_id, kind)` and `(tenant_id, blob_path)` unique
- [x] Money columns — none
- [x] Expand / contract — new table only

**No URL column.** The legacy tables store a full Cloudinary URL, which is what made every
file public. A path plus a container is not a credential; a URL is.

`blob_path` is `{tenantId}/{employeeId}/{kind}/{documentId}`, tenant first, and
`{tenantId}/tenant/{kind}/{documentId}` when there is no employee — an `EXPORT` lands under
the tenant, never under a null. The legacy layout led with `organizationId` too —
`CloudinaryServiceImpl.java:83` — and that part was right.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../document/DocumentLinkServiceTest.java` | a valid link verifies; a tampered one does not; an expired one does not; verification is constant-time; a `Duration` over 7 days is refused; the interactive default is 15 minutes |
| Unit | `core/.../document/DocumentServiceTest.java` | `store` with a null `employeeId` writes `{tenantId}/tenant/{kind}/{id}`; 10 MB + 1 byte is refused; `application/zip` is refused; each of the five types is accepted |
| Integration | `core/.../document/DocumentGuardIT.java` | each endpoint returns `403` without its code; `read_own` reads the caller's document and not a colleague's, and never a document with no `employee_id` |
| Unit | `core/.../document/DocumentLoggingTest.java` | no log statement in the package emits the signature — asserted against a captured appender |
| Integration | `core/.../document/DocumentRlsIT.java` | tenant A cannot read tenant B's document rows as `app_user` |
| Integration | `core/.../document/DocumentUploadIT.java` | upload then download round-trips against Azurite, checksum matches |

`DocumentLoggingTest` exists because incident 3 was a log line, not a crypto flaw. The
mechanism was fine and the logging undid it.

Azurite is already the local stand-in for Azure storage — `active-work.md` records the move
from RabbitMQ to Azurite's queue service for `D-50`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres azurite
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.document'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name FROM information_schema.columns
    WHERE table_schema='core' AND table_name='document' ORDER BY 1;"

cd code/backend && mvn -q verify
grep -rn 'log\.\(info\|debug\|warn\|error\).*\(token\|signature\|sas\)' core/src/main/java/com/infinevo/core/document/ || echo "no signature logged"
```

| Check | Expected |
|---|---|
| RLS on `core.document` | `t` |
| Column list | contains `blob_path`, contains **no** `url` column |
| Signature grep | `no signature logged` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A signature reaches a log again | **medium — it already happened once** | `DocumentLoggingTest` plus the grep in verification |
| The link secret ships with a committed default, as the payslip one did | medium | No default in `application.yml`; the app fails to start without it, which is the safe direction |
| Links never expire, as the payslip token does not | medium | `expiresAt` is inside the signed payload, not a query parameter beside it |
| A tenant's document id is guessed and fetched | low | The link is signed over tenant, document and expiry together; RLS covers the metadata read |
| The container is created public by mistake | low, severe | Verified in `W-50`'s Bicep, not here; this spec asserts the app never issues a public URL |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only — `migration/README.md:135-143`.
Withdrawing the endpoint leaves blobs in place and readable only by the platform identity.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.document` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `core` only; payslips consume it later from `payroll` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| Incident 3 — signed payslip token written to the logs (`PublicPayslipController.java:43-44`) | **Fixed for new code.** The frozen system still logs it; that is an operational rotation, not this ticket |
| Public Cloudinary URLs on six tables | **Fixed.** No URL is stored and no container is public |
| DEBT-004 secrets in `.properties` | **Discounted.** The link secret comes from Key Vault via `W-56`; no new secret is committed |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **How long is a download link valid?** The legacy payslip token never expires. **Recommend** fifteen minutes for an interactive download and a separate, longer-lived path for emailed payslips when `W-36` needs one. **Sharpened 2026-09-25:** the longer path is seven days, chosen by the caller through `signedLink(id, Duration)` — `12-core-contracts.md:173`.
2. **Who may delete a document?** **Recommend** soft delete only, by a tenant administrator, with the blob retained — an audit trail over a deleted proof of investment is worth more than the storage. Hard deletion becomes a retention concern alongside `W-22.2`.
