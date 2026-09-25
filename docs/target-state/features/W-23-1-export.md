# Feature: Report definitions and one export path

| Field | Value |
|---|---|
| **Feature ID** | `W-23.1` · from ticket #27 · `CORE-16` |
| **Promoted to** | `docs/target-state/features/W-23-1-export.md` on branch `W-23-1-export` — **`W-23-1` with hyphens**, never `W-23.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-13.1` — the first thing anyone exports is employees · `W-21` — the file is stored as `DocumentKind.EXPORT` · `W-11.3` — adds `core.report.read/manage` to the catalogue |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 row 18 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one script, one table — `core.report_definition` | 1 |
| Externally testable behaviour | three different screens export through one path, and the output is tenant-scoped | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

Every export is written by hand, none share a path, and all of them build the whole file in
memory.

- About five export endpoints exist, all in Payroll: pay-run CSV at `EmployeePayRunController.java:159-173`, two Excel pay-run reports at `PayRunReportController.java:29-71` and `:77-107`, and a salary-revision export at `CtcStructureController.java:328-344`
- Two libraries — Apache POI for Excel, Apache Commons CSV for CSV — on two separate code paths
- **Every one builds the file in memory.** No streaming anywhere. A 5000-employee export is a heap spike on a container with a fixed memory limit
- **No report definition exists.** Every export's columns are coded, so a new column is a release
- HRMS has no export at all

`09-build-order.md:207` sets the bar as *"three screens use one export path."*

**A correction to the data model.** `02-data-model.md:123` maps `core.report_definition` to
"HRMS `report`". HRMS's `report` table is the **employee reporting-hierarchy** entity —
`reportingManagerId`, `indirectManager`, three approver levels — which `W-14.2` consumes. It
has nothing to do with reports. There is no source table for `report_definition`; it is a new
build, and the mapping line is wrong.

## 2. Scope

**In scope**

- `core.report_definition` — a named, tenant-scoped definition: source, columns, filters, format
- One export service producing CSV and Excel from a definition
- **Streaming**, so memory does not scale with row count
- Tenant scoping through RLS, so an export cannot outrun the isolation the rest of the platform has
- Wiring three consumers: employees, leave balances and pay inputs

**Out of scope**

- **Scheduled and emailed reports** — `W-23.2`, on `worker`
- PDF. Neither product produces one today, and payslip rendering is `W-36`
- A report builder UI
- Migrating the five Payroll exports — they die with the frozen system

## 3. Flow

```
[user] --> POST /api/v1/exports {definitionId, filters}
   --> [ExportService] --> definition --> query under RLS
   --> [ReportSource bean for definition.source].rows(filters)
   --> stream rows --> CSV or Excel --> DocumentService.store(EXPORT, null, filename, stream) (W-21)
   --> response: document id + DocumentLinkService.signedLink(id, 15 min)
```

The file lands in the document store rather than streaming down the HTTP response, so a large
export does not hold a request thread open and the link can be re-fetched.

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../report/ReportDefinitionController.java`, `ExportController.java` | new |
| Service | `core/.../report/ExportService.java` | new |
| Service | `core/.../report/CsvWriter.java`, `XlsxStreamingWriter.java` | new |
| Entity | `core/.../report/ReportDefinition.java` | new, `@Table(schema="core")` |
| Repository | `core/.../report/ReportDefinitionRepository.java` | new |
| Enumeration | `core/.../report/ExportFormat.java` | new |
| Interface | `core/.../report/ReportSource.java` | new — **a bean interface, not an enum** (corrected 2026-09-25, `12-core-contracts.md:108`) |
| Source | `core/.../report/source/EmployeeReportSource.java`, `LeaveBalanceReportSource.java`, `PayInputReportSource.java` | new — the three Core consumers, each a `@Component` |

**`ReportSource`** — the seam a module implements to make its data exportable without `core`
knowing the module exists:

```java
public interface ReportSource {
    String code();                                   // stored in report_definition.source
    List<ReportColumn> columns();                    // the allow-list: name, label, type
    Stream<Map<String, Object>> rows(ReportFilters filters);  // under RLS, tenant already bound
}
```

`ExportService` finds the bean by `code()` from the Spring context. A `payroll` source is a
`payroll` bean; `core` never imports it. `rows` returns a `Stream`, so the writer pulls and the
source never materialises the set — the streaming guarantee starts here, not at the writer.

`XlsxStreamingWriter` uses POI's `SXSSFWorkbook`, which keeps a bounded window of rows in
memory. The frozen code uses the in-memory workbook, and that is the whole difference.

**Where the file goes.** `DocumentService.store(DocumentKind.EXPORT, null, filename, stream)`
— no employee, because an export belongs to the tenant (`W-21`, `12-core-contracts.md:107`).
The response link is `DocumentLinkService.signedLink(id, Duration.ofMinutes(15))`.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/report-definitions` | — | the tenant's definitions the caller may run | `@RequiresAction("core.report.read")` |
| PUT | `/api/v1/report-definitions/{id}` | name, source, columns, format, required_action | `200` | `@RequiresAction("core.report.manage")` |
| POST | `/api/v1/exports` | definitionId, filters | `201` + document id and link | `core.report.read` **and** the definition's `required_action`, checked by `PermissionService.require` |

`core.report.read` and `core.report.manage` arrive with `W-11.3` (`12-core-contracts.md:128`).
The per-definition `required_action` is the second gate on `POST /exports`; a caller who lacks
it gets `403`, and `GET /report-definitions` hides the definition from them.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__report_definition.sql` | `core.report_definition` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` · `code varchar(64) NOT NULL` ·
`name varchar(128) NOT NULL` · `source varchar(32) NOT NULL` ·
`columns jsonb NOT NULL` · `default_filters jsonb NULL` ·
`format varchar(8) NOT NULL` · `required_action varchar(64) NOT NULL` ·
`is_system boolean NOT NULL DEFAULT false` · four audit columns.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, code)` unique
- [x] **Money columns — none.** Exported amounts are formatted from `numeric(19,4)` sources; no amount is stored here
- [x] Expand / contract — new table only

**`source` is a `ReportSource.code()`, not free SQL.** A definition names a bean the code
knows how to query — employees, leave balances, pay inputs — and chooses columns from that
bean's `columns()` allow-list. `PUT` refuses a code with no registered bean. Storing a query
would be a report builder and an injection surface in one column.

**`required_action` is on the definition.** An export of salary data must not be reachable by
someone who cannot see salary data on screen. `ExportService` checks it through
`PermissionService.require` (`12-core-contracts.md:87`) after the `core.report.read` guard.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../report/ExportServiceTest.java` | a column outside the source's `columns()` refused; format honoured; empty result produces a header-only file, not an error; an unknown `source` code refused; the file is stored with `DocumentKind.EXPORT` and a null employee |
| Unit | `core/.../report/ReportSourceRegistryTest.java` | a test `ReportSource` bean registered in the context is found by `code()`; two beans with one code fail startup |
| Integration | `core/.../report/ExportGuardIT.java` | `POST /exports` returns `403` without `core.report.read`; returns `403` with it but without the definition's `required_action`; `GET /report-definitions` omits that definition; `PUT` needs `core.report.manage` |
| Unit | `core/.../report/CsvWriterTest.java` | commas, quotes and newlines in values escaped; a value beginning `=` is not written as a formula |
| Integration | `core/.../report/ExportStreamingIT.java` | **10 000 rows export with bounded heap** — the writer never holds the whole set |
| Integration | `core/.../report/ExportRlsIT.java` | an export run by tenant A contains no tenant B row, including in the row count |
| Integration | `core/.../report/ThreeConsumersIT.java` | employees, leave balances and pay inputs all export through the one service |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`CsvWriterTest`'s formula case is not pedantry: a leave reason beginning with `=` becomes a
live formula when the file opens in Excel, and that is a real path from user-supplied text to
code execution on someone's laptop.

`ThreeConsumersIT` is the build order's acceptance criterion, asserted directly.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres azurite
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.report_definition'::regclass;"

cd code/backend && mvn -q -pl core -Dit.test=ExportStreamingIT,ThreeConsumersIT verify
cd code/backend && mvn -q verify

# the in-memory workbook must not be used
grep -rn 'new XSSFWorkbook' core/src/main/java/com/infinevo/core/report/ \
  && echo "REVIEW: in-memory workbook" || echo "streaming workbook only"
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Streaming test | green — heap bounded across 10 000 rows |
| Three consumers | green |
| Workbook grep | `streaming workbook only` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A large export exhausts the container's memory, as the frozen code would | **high without streaming** | `SXSSFWorkbook` and a streamed CSV; the grep and the 10 000-row test |
| `source` becomes free SQL because a definition cannot express something | medium | Enumerated sources with allow-listed columns; a new source is a code change, deliberately |
| An export bypasses the action check and leaks salary data | medium | `required_action` on the definition, checked by `PermissionService.require`; `ExportGuardIT` |
| CSV injection through employee-supplied text | medium | Values beginning `=`, `+`, `-` or `@` are prefixed; unit-tested |
| An export runs long and ties up a request thread | medium | The file goes to the document store; large exports become `W-23.2`'s asynchronous path |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only —
`migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.report_definition` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | creates no money column; exported amounts format from `numeric(19,4)`, never float |
| Index on `tenant_id` plus lookup columns | one index, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `core` only; a `payroll` source is a `payroll` bean implementing `core`'s `ReportSource`, discovered by `code()` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| Five hand-written exports, two code paths | **Fixed by replacement.** One service, one path |
| All exports in memory | **Fixed.** Streaming, asserted at 10 000 rows |
| No report definition exists | **Fixed.** This ticket creates the concept |
| `02-data-model.md:123` maps `report_definition` to HRMS `report` | **Wrong.** HRMS `report` is the reporting hierarchy; `sync-docs` should correct the line |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Can a tenant define its own reports, or only use seeded ones?** **Recommend** seeded system definitions plus tenant-created ones limited to the same sources and columns — a full report builder is a product of its own.
2. **Does an export land in the document store or stream to the browser?** This spec says the store, so the file survives a dropped connection and can be re-downloaded. **Recommend** that, accepting a signed link instead of an immediate download.
