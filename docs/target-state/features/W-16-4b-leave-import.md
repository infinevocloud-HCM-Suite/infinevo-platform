# Feature: Leave bulk import

| Field | Value |
|---|---|
| **Feature ID** | `W-16.4b` · from ticket #20 · `CORE-07` |
| **Promoted to** | `docs/target-state/features/W-16-4b-leave-import.md` on branch `W-16-4b-leave-import` — **`W-16-4b` with hyphens**, never `W-16.4b`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-013 (discounted), DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-16.2` (allocations to import into), `W-21` (the uploaded file), `W-11.3` (the `core.leave_balance.manage` code, `12-core-contracts.md` §4) |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 rows 5, 23 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one script, one table — `core.leave_import_log` | 1 |
| Externally testable behaviour | a file of opening balances is imported, and a file with bad rows reports which and imports the rest | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

The existing import is a JSON endpoint with no validation and no result.

- `POST /api/employee-leave-imports/imports` takes a `List<EmployeeLeaveImportDTO>` — `EmployeeLeaveImportController.java:97`
- The service maps the DTOs to entities and saves them — `EmployeeLeaveImportServiceImpl.java:93`
- **No CSV parsing.** The caller has already turned a file into JSON somewhere off-stage
- **No validation.** Nothing checks the employee exists, nothing checks the leave type exists, nothing checks for duplicates
- **All or nothing.** The method is `@Transactional`, so one bad row in five thousand rolls back the lot
- **No result.** Nothing records what was imported, by whom, or what failed

The import table itself is four columns — `count`, `leave_date`, `employee_number`,
`leave_type`, plus the organisation — `EmployeeLeaveImport.java:8-78`. It records rows, not
an import.

That matters most at cutover. Opening balances arrive in a spreadsheet once per tenant, and
if half of them silently fail the tenant starts with wrong entitlements and no record of why.

## 2. Scope

**In scope**

- `core.leave_import_log` — one row per import run, with counts and the outcome
- CSV upload, parsed server-side, from a `core.document` the client has already uploaded
- Row-level validation: employee exists, leave type exists and is active, days parse as a number, no duplicate for the same employee, type and year
- **Partial success**: valid rows are applied, invalid rows are reported with their line numbers and reasons
- A downloadable error report, itself a `core.document`

**Out of scope**

- Importing leave *requests* or historical consumption — opening allocations only
- Employee import — that is `W-13`'s area if it is ever needed
- Any format but CSV. See decision 1
- Scheduled or recurring import

## 3. Flow

```
[admin uploads CSV via W-21] --> document id
   --> POST /api/v1/leave-imports {documentId}
   --> [LeaveImportService] parse, validate row by row
   --> valid rows --> [core.leave_allocation] (W-16.2)
   --> invalid rows --> error report document
   --> [core.leave_import_log] one row, with counts and both document ids
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../leave/LeaveImportController.java` | new |
| Service | `core/.../leave/LeaveImportService.java` | new |
| Service | `core/.../leave/LeaveImportRowValidator.java` | new |
| Entity | `core/.../leave/LeaveImportLog.java` | new, `@Table(schema="core")` |
| Repository | `core/.../leave/LeaveImportLogRepository.java` | new |
| Enumeration | `core/.../leave/ImportStatus.java` | new — pending, completed, completed with errors, failed |
| DTO | `core/.../leave/LeaveImportRow.java`, `LeaveImportResultResponse.java` | new |

**API contract**

| Method | Path | Request | Response | `@RequiresAction` |
|---|---|---|---|---|
| POST | `/api/v1/leave-imports` | `{documentId, leaveYear, dryRun}` | `202` + import id | `core.leave_balance.manage` |
| GET | `/api/v1/leave-imports/{id}` | — | status, counts, error report document id | `core.leave_balance.manage` |
| GET | `/api/v1/leave-imports` | `?page=` | history | `core.leave_balance.manage` |

Every endpoint is tenant bound and carries the code shown — `EndpointGuardCoverageTest` fails
otherwise (`12-core-contracts.md` §2). The code is renamed from `hrms.leave_balance.manage` by
`W-11.3` (`12-core-contracts.md` §4). An import writes allocations, so it is the same permission
as the manual allocation endpoint in `W-16.2`.

`dryRun` validates and reports without writing an allocation. For a cutover import of
several thousand opening balances, being able to see the errors before committing is the
difference between one attempt and several.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__leave_import_log.sql` | `core.leave_import_log` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` ·
`source_document_id uuid NOT NULL REFERENCES core.document(id)` ·
`error_document_id uuid NULL REFERENCES core.document(id)` ·
`leave_year varchar(9) NOT NULL` · `status varchar(24) NOT NULL` ·
`is_dry_run boolean NOT NULL DEFAULT false` ·
`rows_total int NOT NULL DEFAULT 0` · `rows_imported int NOT NULL DEFAULT 0` ·
`rows_failed int NOT NULL DEFAULT 0` ·
`started_at timestamptz NOT NULL` · `finished_at timestamptz NULL` ·
four audit columns.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, started_at DESC)` and `(tenant_id, status)`
- [x] **Money columns — none.** Imported day counts land in `core.leave_allocation` as `numeric(10,2)` (`CONVENTIONS.md:37`)
- [x] Expand / contract — new table only

**No row-level import table.** The frozen design stores every imported row forever —
`EmployeeLeaveImport.java:8-78` — which duplicates the allocation it produced. The rows live
in the source document; the log records what happened to them.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../leave/LeaveImportRowValidatorTest.java` | unknown employee, unknown type, inactive type, unparseable days, duplicate within the file — each rejected with its own reason |
| Unit | `core/.../leave/LeaveImportServiceTest.java` | a file of 5 good and 2 bad rows imports 5 and reports 2; a dry run imports 0 and reports the same 2 |
| Integration | `core/.../leave/LeaveImportPartialSuccessIT.java` | the good rows are committed **after** a bad row is encountered, not rolled back with it |
| Integration | `core/.../leave/LeaveImportRlsIT.java` | tenant A cannot read tenant B's import logs, and cannot import against tenant B's document |
| Integration | `core/.../leave/LeaveImportGuardIT.java` | a caller without `core.leave_balance.manage` gets `403` on all three endpoints; a row of `12.5` days lands as `12.50`, not `12` |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`LeaveImportPartialSuccessIT` is the one that matters, because partial success is the whole
difference from the frozen behaviour, and it is easy to lose by wrapping the loop in one
`@Transactional`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres azurite
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.leave_import_log'::regclass;"
cd code/backend && mvn -q -pl core -Dit.test=LeaveImportPartialSuccessIT verify
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Partial success test | green — 5 allocations exist, log reports 5 imported and 2 failed |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| One `@Transactional` around the loop restores all-or-nothing | **high — it is one annotation** | The partial-success test asserts committed rows after a failure |
| A 5000-row file is read into memory and the container dies | medium | Rows streamed from the blob, committed in batches; the log carries running counts |
| An import is run twice and doubles opening balances | medium | The unique constraint on `(tenant_id, employee_id, leave_type_id, leave_year)` from `W-16.2` refuses the second; the row is reported, not swallowed |
| Errors reported without line numbers, so a 5000-row file cannot be fixed | medium | The row validator carries the source line number into the error report |
| Someone imports against another tenant's document id | low | FK plus RLS; asserted in `LeaveImportRlsIT` |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only —
`migration/README.md:135-143`. An import that produced wrong allocations is corrected through
`W-16.2`'s allocation endpoints, with the log left in place as the record of what happened.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.leave_import_log` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | no money column |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| No validation on import (`EmployeeLeaveImportServiceImpl.java:93`) | **Fixed.** Five row-level checks, each with its own reason |
| All-or-nothing rollback | **Fixed.** Partial success, proved by an integration test |
| No import result recorded | **Fixed.** `core.leave_import_log` plus an error report document |
| DEBT-013 package typo `leaveAndAttedance/` (`:51`) | **Discounted.** New code is `core/.../leave/` |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **CSV only, or Excel too?** Tenants will send `.xlsx`, because that is what payroll teams use. **Recommend** CSV only in this ticket, with a clear error telling the user to save as CSV, and Excel as its own ticket if it becomes a real complaint — parsing spreadsheets brings a library and a set of edge cases that do not belong in the first version.
2. **Does an import need a dry run before it commits?** This spec makes `dryRun` a flag the caller sets. **Recommend** the screen always dry-runs first and shows the result, but the API allows a direct commit so a scripted cutover is not forced through two calls.
