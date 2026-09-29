# Feature: Form 16 Part A — upload the TRACES ZIP, file each certificate against its employee

| Field | Value |
|---|---|
| **Feature ID** | `W-36.5` · from ticket #48 (`W-36`) · `PAY-15` |
| **Promoted to** | `docs/target-state/features/W-36-5-form16-part-a-upload.md` — **`W-36-5` with hyphens**, never `W-36.5`; `guard-edit` blocks the dotted form |
| **Owner** | mohit |
| **Apps touched** | `code/backend/payroll`, `code/backend/core` (one enum constant, one lookup), `code/backend/migration` |
| **Related gaps** | DEBT-011 (fixed for Part A), BUG-002, DEBT-018, DEBT-019 (honoured) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | `W-21` — `DocumentService.storeFile` is on `main` (`core/.../document/DocumentService.java:42`) · `W-32.1` — `FinancialYear` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll`, plus two small seams in `core` | 1 — **exception requested** |
| Flyway migration | `V108` (`core` — widen the `document.kind` `CHECK`) and `V109` (`payroll.form16_part_a`) | 1 — **exception requested** |
| Externally testable behaviour | an officer uploads the Part A ZIP for a year; each certificate is filed against the employee whose PAN it names; the employee downloads their own | 1 |
| Frontend area | none — the upload screen is a `W-47` ticket | 1 |

**Why the exception.** The core change is one enum constant, its `CHECK` value and one
read-only lookup. Splitting them into their own ticket would make a ticket with no behaviour
of its own. `W-36.2` added a constant to `shared` on the same basis. If the founder refuses the
exception, the core half becomes `W-36.5a` (`V108`, the constant and the lookup), and this
ticket keeps `V109` and waits on it.

---

## 1. Problem

The frozen screen asks for "the Form 16 – Part A ZIP file that you've downloaded from TRACES
utility application" (`legacy/Payroll-Fend-react/.../form16/generateForm16.js:69`). Its upload
handler is a `TODO` that logs the file to the console (`:38-43`). There is no backend.

Part A is the government's certificate of tax deposited. The employer cannot generate it, only
download it from TRACES and hand it to each employee. The target's statement (`W-36.4`) is
Part B. The two together are Form 16.

The document store refuses a ZIP (`application.yml` `allowed-types: pdf,jpg,png,xlsx,csv`;
`W-21-document-store.md:51`). It also has no kind for a Part A certificate
(`core/.../document/DocumentKind.java:17-22`).

## 2. Scope

**In scope**

- `POST /api/v1/payroll/form16/{fy}/part-a`, a multipart ZIP of at most 50 MB, unpacked in `payroll`
- Each `.pdf` entry is matched to an employee by the first PAN (`[A-Z]{5}[0-9]{4}[A-Z]`) in its file name
- A matched PDF is stored through `DocumentService.storeFile(FORM16_PART_A, employeeId, name, path)`, which applies the system 50 MB cap
- `payroll.form16_part_a` links `(tenant, employee, fy)` to the `document_id`. A re-upload supersedes the old link and soft-deletes the old document
- The response reports matched, unmatched (the file name) and skipped (not a PDF, or a duplicate PAN) entries
- Reads: an officer list for a year, and the employee's own under `/me`. Both return a `W-21` signed link
- `core`: `DocumentKind.FORM16_PART_A`, a system kind so no client can upload it through `/documents`, and `EmployeeIdentificationService.employeeIdsByPan(Set<String>)`, which does one query

**Out of scope**

- A password-protected ZIP. `java.util.zip` cannot open one, so it returns `400` `ZIP_ENCRYPTED` with the message "extract and re-zip without a password". See §13, open question 1
- Checking the PDF's digital signature or contents. The file is filed as downloaded
- Combining Part A and Part B into one document
- Screens (`W-47`)

## 3. Flow

```
[officer] POST /api/v1/payroll/form16/{fy}/part-a  (multipart zip)   payroll.statutory_report.generate
   --> Form16PartAService.upload(fy, zip)
       stream entries to a temp dir; stop at 2,000 entries or 200 MB unpacked (zip-bomb guard)
       pans      = first PAN in each .pdf entry name
       employees = EmployeeIdentificationService.employeeIdsByPan(pans)   one query
       for each matched entry, one transaction per entry:
           id = DocumentService.storeFile(FORM16_PART_A, employeeId, "Form16-PartA-<fy>.pdf", path)
           supersede the active form16_part_a row (soft-delete its document), insert the new row
   --> 200 { matched: n, unmatched: [names], skipped: [names] }    temp dir deleted in finally

[officer]  GET /api/v1/payroll/form16/{fy}/part-a                 → [{ employee_id, document_id, uploaded_at }]
[employee] GET /api/v1/me/form16/{fy}/part-a                      → { document_id, link } or 404
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Enumeration (core) | `core/.../document/DocumentKind.java` | add `FORM16_PART_A` and put it in `SYSTEM_GENERATED` (`:32`) |
| Service (core) | `core/.../employee/detail/EmployeeIdentificationService.java` | add `Map<String, UUID> employeeIdsByPan(Set<String> pans)`. It is tenant-bound and covers live employees only. A PAN held by two employees is left out of the map and reported by the caller as skipped |
| Repository (core) | `EmployeeIdentificationRepository.java` | `findByTenantIdAndPanNumberIn(tenantId, pans)`, which uses the existing `idx_employee_identification_tenant_pan` (`EmployeeIdentification.java:48`) |
| Entity | `payroll/.../form16/Form16PartA.java` | `@Table(name = "form16_part_a", schema = "payroll")`, `@Audited` |
| Repository | `Form16PartARepository.java` | `findActive(tenantId, employeeId, fy)`, `findActiveByTenantIdAndFinancialYear(tenantId, fy)` |
| Service / ServiceImpl | `Form16PartAService`, `Form16PartAServiceImpl` | `upload(fy, MultipartFile)`, `list(fy)`, `own(fy)` |
| Controller | `Form16PartAController.java` | the three endpoints |
| DTO | `PartAUploadResult`, `PartAResponse` | the `status` / `message` / `data` envelope |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/payroll/form16/{fy}/part-a` | multipart `file` (`application/zip`) | `200` result · `400` not a ZIP, `ZIP_ENCRYPTED`, or over the entry limits · `413` over 50 MB | `payroll.statutory_report.generate` |
| GET | `/api/v1/payroll/form16/{fy}/part-a` | — | the active rows for the year | `payroll.statutory_report.read` |
| GET | `/api/v1/me/form16/{fy}/part-a` | — | own `document_id` and a signed link, or `404` | `payroll.payslip.read_own` |

There are no new action codes (`V020__action.sql:121`, `:132-133`, granted in `V025__catalogue_correction.sql:204-205`, `:229`).

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V108__document_kind_form16.sql` | `core.document` — drop and re-add `document_kind_check` with `FORM16_PART_A` added (`V037__document.sql:26`) | yes | widening only — every existing row still passes |
| `payroll/V109__form16_part_a.sql` | `payroll.form16_part_a` | yes | additive |

`V108`–`V109` were reserved 2026-09-29, above `W-36.3`'s `V107`.

`payroll.form16_part_a`

| Column | Type | Note |
|---|---|---|
| `id` | `UUID PK DEFAULT gen_random_uuid()` | |
| `tenant_id` | `UUID NOT NULL REFERENCES core.tenant(tenant_id)` | |
| `employee_id` | `UUID NOT NULL REFERENCES core.employee(id)` | |
| `financial_year` | `VARCHAR(9) NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$')` | the `W-32.1` label |
| `document_id` | `UUID NOT NULL REFERENCES core.document(id)` | |
| `source_file_name` | `VARCHAR(255) NOT NULL` | the entry name inside the ZIP, for the officer |
| `is_active` | `BOOLEAN NOT NULL DEFAULT true` | |
| `superseded_at` | `TIMESTAMPTZ NULL CHECK ((is_active) = (superseded_at IS NULL))` | the `W-36.1` shape |
| `created_at`, `created_by` | as `V051` | |

Indexes: `uk_form16_part_a_tenant_employee_fy_active UNIQUE (tenant_id, employee_id, financial_year) WHERE is_active`;
`idx_form16_part_a_tenant_fy (tenant_id, financial_year, is_active)`; `idx_form16_part_a_tenant_document (tenant_id, document_id)`.
The last index covers the FK. RLS `tenant_isolation` uses the exact `CASE` form.

- [x] `tenant_id` present, and it leads every index
- [x] Index on `tenant_id` plus lookup columns (DEBT-018)
- [x] Money — none
- [x] Expand / contract — `V108` only widens a `CHECK`, and `V109` is a new table

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../form16/PartAEntryParserTest.java` | `ABCDE1234F_2027-28.pdf` ⇒ PAN `ABCDE1234F`; `form16_abcde1234f.pdf` (lower case) ⇒ matched after uppercasing; `readme.txt` ⇒ skipped; `__MACOSX/._x.pdf` ⇒ skipped; two entries for one PAN ⇒ both skipped as duplicates; entry `../../evil.pdf` ⇒ skipped (path traversal) |
| Unit | `core/.../document/DocumentKindTest.java` (extend) | `FORM16_PART_A.isUploadable()` is `false` |
| Integration | `payroll/.../form16/Form16PartAIT.java` | **the acceptance test**: a ZIP of three PDFs, two PANs known and one unknown ⇒ `matched 2`, `unmatched [name]`; each employee's `/me` returns a link that downloads the same bytes; a re-upload leaves one active row and soft-deletes the old document; an encrypted ZIP ⇒ `400 ZIP_ENCRYPTED`; 2,001 entries ⇒ `400`; `POST /api/v1/documents` with kind `FORM16_PART_A` ⇒ `400` |
| Integration | `payroll/.../form16/Form16PartARlsIT.java` | a PAN that belongs to tenant B is `unmatched` for tenant A; as `app_user`, tenant A cannot read tenant B's rows; two active rows for one `(tenant, employee, fy)` are refused by the index |
| Integration | `core/.../employee/detail/EmployeeIdentificationPanLookupIT.java` | one query for 500 PANs (Hibernate statistics); soft-deleted employees are excluded |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres blob
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='document_kind_check';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='payroll.form16_part_a'::regclass;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| Kind check | the definition lists seven kinds, `FORM16_PART_A` among them |
| RLS | `t` |
| Suite | green with no skips, and `Form16PartAIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Zip bomb or path traversal from an uploaded archive | medium | the entry-count and unpacked-size limits, entry names that are never used as paths, and the `../` unit test |
| A certificate filed against the wrong person | medium | a PAN matches only in the bound tenant. A PAN on two employees, or twice in one ZIP, is skipped, not guessed |
| The real TRACES file names carry no PAN | medium | §13, open question 2. The match falls back to "unmatched" and never guesses. The officer sees the list |
| The temp files are left on disk | low | deleted in `finally`; `Form16PartAIT` asserts the directory is empty afterwards |

## 10. Rollback

Nothing is deployed yet. `V108` is a widened `CHECK` that no existing row depends on. `V109` is
additive. A wrong upload is corrected by a re-upload, which supersedes the old one.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `payroll.form16_part_a` |
| Flyway only, `ddl-auto` nowhere | `V108`, `V109` |
| `Money`/`BigDecimal` for money | no money column |
| Index on `tenant_id` plus lookup columns | three indexes, `tenant_id` leading |
| Expand / contract | a widened `CHECK` and a new table |
| No module references another module | `payroll` calls `core` (`DocumentService`, `EmployeeIdentificationService`), and `core` does not know `payroll` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-011 Cloudinary public URLs | **Fixed for Part A** — private blob, signed link |
| BUG-002 cross-tenant exposure | **Honoured** — the PAN lookup is tenant-bound, and `Form16PartARlsIT` checks it |
| DEBT-018 no indexes | **Honoured** |
| DEBT-019 N+1 | **Honoured** — one PAN query per upload |

## 13. Decisions and open questions

| # | Question | Answer |
|---|---|---|
| 1 | Password-protected ZIP? | **Open — founder.** v1 refuses it and tells the officer to re-zip. Opening encrypted ZIPs needs a library decision (for example `zip4j`) |
| 2 | Match on what? | **The first PAN in the entry's file name.** Open for the founder to confirm the real TRACES naming with one sample download before `/develop`; if it carries no PAN, the match reads the PDF text instead, which is a library decision |
| 3 | Where is the file? | **`W-21` document store**, kind `FORM16_PART_A`, marked system-generated so it cannot be forged through `/documents` |
| 4 | Re-upload? | **Supersedes.** The old document is soft-deleted, and the link row is kept with `superseded_at` |
