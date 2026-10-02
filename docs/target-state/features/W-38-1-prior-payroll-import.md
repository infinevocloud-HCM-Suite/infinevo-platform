# Feature: Prior payroll import — template, dry run, load

| Field | Value |
|---|---|
| **Feature ID** | `W-38.1` · from ticket #50 (`W-38`) · `PAY-17` |
| **Promoted to** | `docs/target-state/features/W-38-1-prior-payroll-import.md` — **`W-38-1` with hyphens**, never `W-38.1`; `guard-edit` blocks the dotted form |
| **Owner** | krushna |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (honoured), DEBT-007 (honoured), DEBT-008 (honoured), DEBT-018 (honoured), DEBT-022 (honoured) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | nothing. `W-29.1` (`payroll.payrun`) is on `main` `7dfd74e`; `W-21` (`core.document`) is on `main` `b6e6012`; `W-16.4b`, the pattern, is on `main` `a53e311` |
| **Size** | M |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V140` — one script, two tables: `payroll.prior_payroll_month`, `payroll.prior_payroll_import_log` | 1 |
| Externally testable behaviour | an officer uploads a file of earlier months, sees bad rows in an error file with line numbers, and the good rows are saved; a month that already has a real pay run is refused, and a real run is refused for a month already imported | 1 |
| Frontend area | none — the screen is `W-47.6` | 1 |

Within cap. `W-38` was split on 2026-10-02 (founder): `W-38.1` loads the months, `W-38.2`
makes monthly TDS and Form 16 count the imported tax, `W-38.3` adds the setup step,
`W-47.6` is the screen and the mid-year warning.

---

## 1. Problem

A company that moves onto the platform in October has paid six months of salary and tax
somewhere else. Nothing on the platform can hold those months.

- **Legacy has no import.** The frozen system carries a flag and nothing behind it:
  `isPriorPayrollSetup` (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/OrgSetupSteps.java:18`)
  is set when any pay run exists (`.../serviceimpl/OrgSetupStepsServiceImpl.java:75`), and the
  "Configure Prior Payroll" card is commented out
  (`legacy/Payroll-Fend-react/src/pages/mainPages/dashboardPage/onboardingDashboard.js:95-104`).
  There is no logic to port
- **No table was designed.** `02-data-model.md:355` and `09-build-order.md:237` both leave it open
- **Monthly TDS and Form 16 read only this platform's own runs.** Year-to-date is the sum of `TAX`
  lines (`W-36-1-employee-tds.md:84-96`); Form 16 quarters sum `TAX` lines of `PAID` runs
  (`W-36-4-form16-statement.md:67-68`). A mid-year tenant starts at 0, so October's deduction is
  the whole year's tax over six months. `W-38.2` fixes the readers; this ticket gives them
  something to read
- Source format settled 2026-09-25: a fixed spreadsheet, one row per employee per month,
  through `W-16.4b`'s import pattern (`12-core-contracts.md:181`)

## 2. Scope

**In scope**

- `payroll.prior_payroll_month` — one row per employee per month: gross, EPF, ESI, PT, TDS, net
- `payroll.prior_payroll_import_log` — one row per import, the `core.leave_import_log` shape
  (`V134__leave_import_log.sql:4-21`)
- The template: `GET …/template` returns the CSV header row
- Import from a `core.document` the client already uploaded, with `dryRun`, partial success and
  an error file — the `W-16.4b` service as built (`core/.../leave/LeaveImportServiceImpl.java:53-212`)
- Reading the imported months, deleting one wrong row
- The mid-year status the `W-47.6` warning reads
- **Two-way guard:** an import row for a month with a real regular run is refused; creating a
  regular run for a month with imported rows is refused
- The `payroll.prior_payroll` menu item

**Out of scope**

- TDS and Form 16 reading the rows — `W-38.2`
- The setup checklist step — `W-38.3`
- Screens and the warning banner — `W-47.6`
- Excel. CSV only, as `W-16.4b` decision 1 (`W-16-4b-leave-import.md` §13). The template is
  opened and saved in Excel as CSV
- Employer contributions (EPF and ESI employer share), components line by line, arrears.
  Six figures per month, founder 2026-10-02
- Feeding imported gross into the tax calculator's salary projection — not this ticket (§13
  decision 6)
- Leavers who are not in `core.employee`. A row needs an employee on the platform

## 3. Flow

```
[officer] GET  /api/v1/payroll/prior-payroll/template                 --> CSV header row
[officer] POST /api/v1/documents  (kind EMPLOYEE_DOCUMENT, no employee) --> documentId      (W-21, as is)
[officer] POST /api/v1/payroll/prior-payroll-imports {documentId, financialYear, dryRun}
   --> PriorPayrollImportService.importFile
       log    = INSERT prior_payroll_import_log  PENDING
       rows   = parse CSV, line numbers kept                               bad shape → INVALID_FORMAT
       checks = PriorPayrollRowValidator.validate(tenant, fy, rows)       §4 reasons
       dryRun → write nothing
       else   → each valid row INSERT prior_payroll_month in its own transaction (REQUIRES_NEW)
                 unique violation → ALREADY_IMPORTED, the rest carry on
       errors → CSV error file, DocumentKind.EXPORT, id on the log
       log    → COMPLETED or COMPLETED_WITH_ERRORS, counts
   --> 200 result

[officer] POST /api/v1/payroll/payruns {period}                         (W-29.1, changed)
   --> PayRunServiceImpl.create: imported rows exist for period         → 409 PRIOR_PAYROLL_EXISTS
```

## 4. Backend changes

All new under `code/backend/payroll/src/main/java/com/infinevo/payroll/priorpayroll/`, except
the two changes marked.

| Layer | File | Change |
|---|---|---|
| Entity | `PriorPayrollMonth.java` | `@Table(name = "prior_payroll_month", schema = "payroll")`, `@Audited`; `UUID id`, `UUID tenantId`, `UUID employeeId`, `String period`, `BigDecimal grossEarnings`, `epfEmployee`, `esiEmployee`, `professionalTax`, `tds`, `netPay`, `UUID importId`, audit columns |
| Entity | `PriorPayrollImportLog.java` | `@Table(name = "prior_payroll_import_log", schema = "payroll")`; the `LeaveImportLog` fields with `financialYear` for `leaveYear` |
| Enumeration | `PriorPayrollImportStatus.java` | `PENDING`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, `FAILED` — the `core/.../leave/ImportStatus.java` values. Not shared: `payroll` does not import a `core` leave class |
| Record | `PriorPayrollRow.java`, `PriorPayrollRowError.java` | line number plus the eight raw strings; line, employee number, period, reason |
| Repository | `PriorPayrollMonthRepository.java` | `existsByTenantIdAndPeriod`, `existsByTenantId`, `findByTenantIdAndPeriodBetween(…, Pageable)`, `findDistinctPeriods(tenantId, from, to)`, `findByIdAndTenantId`. Every finder takes `tenantId` (DEBT-022) |
| Repository | `PriorPayrollImportLogRepository.java` | `findByTenantIdAndId`, `findByTenantIdOrderByStartedAtDesc` |
| Validator | `PriorPayrollRowValidator.java` | the checks below, one reason each; employee lookups cached per file |
| Helper | `PriorPayrollImportWriter.java` | `saveLog` and `insertOne` in `REQUIRES_NEW`, the `LeaveImportAllocationHelper` split (`core/.../leave/LeaveImportAllocationHelper.java`) |
| Service / ServiceImpl | `PriorPayrollImportService`, `…Impl` | `importFile(documentId, fy, dryRun)`, `getImport`, `listImports`, `template()`. **The impl carries no `@Transactional`** (`LeaveImportServiceImpl.java:27-31`) |
| Service / ServiceImpl | `PriorPayrollService`, `…Impl` | `months(fy, employeeId?, page)`, `delete(id)`, `status(fy)`, `hasImportedRows(period)` |
| Controller | `PriorPayrollController.java` | the endpoints below, `status` / `message` / `data` envelope (`CONVENTIONS.md` §3) |
| Navigation (change) | `payroll/navigation/PayrollNavigation.java` | add `PRIOR_PAYROLL`: `payroll.prior_payroll`, `nav.payroll.prior_payroll`, `/payroll/prior-payroll`, target `/api/v1/payroll/prior-payroll-imports`, `PAYROLL`, `payroll.run.read`; `items()` returns both |
| Service (change) | `payroll/payrun/PayRunServiceImpl.java:156-165` | after the duplicate check, `priorPayrollService.hasImportedRows(period)` → throw `PriorPayrollExistsException` (`409`, mapped in `PayRunController`'s handler list at `:144`) |

**The template.** A UTF-8 CSV with this header and no rows:

```
employee_number,period,gross_earnings,epf_employee,esi_employee,professional_tax,tds,net_pay
```

`period` is `YYYY-MM`. Amounts are rupees, up to 2 decimals, no thousands separator. A cell
left empty in the four deduction columns is `0`; `gross_earnings` and `net_pay` are required.

**Row checks, each a reason in the error file**

| Reason | Rule |
|---|---|
| `INVALID_FORMAT` | fewer than 8 cells |
| `UNKNOWN_EMPLOYEE` | no `core.employee` with this number in the tenant, not deleted (`core/.../employee/EmployeeRepository.java:70`) |
| `INVALID_PERIOD` | not `YYYY-MM` |
| `OUTSIDE_YEAR` | period not in the request's `financialYear` (`payroll/taxdeclaration/FinancialYear.java:29`) |
| `NOT_PAST` | period is the current month or later, today taken in `core.tenant.timezone` (as `W-47-2-pay-run-screens.md` §4) |
| `BEFORE_JOINING` | period ends before the employee's `date_of_joining` |
| `REAL_RUN_EXISTS` | a `REGULAR` run not `CANCELLED` exists for the period (`payroll/payrun/PayRunRepository.java:21`) |
| `INVALID_AMOUNT` | not a number, negative, or more than 2 decimals |
| `NET_TOO_HIGH` | `net_pay > gross_earnings − (epf + esi + pt + tds)`. Lower is allowed: other deductions existed |
| `DUPLICATE_IN_FILE` | the same employee and period twice in the file; every copy after the first |
| `ALREADY_IMPORTED` | the row exists from an earlier import (the unique index answers, at write) |

A correction is: delete the wrong row, import a file with the right one. No update in place.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/payroll/prior-payroll/template` | — | `200 text/csv`, the header row, `Content-Disposition: attachment; filename=prior-payroll-template.csv` | `payroll.run.read` |
| POST | `/api/v1/payroll/prior-payroll-imports` | `{documentId, financialYear, dryRun}` | `200` the log: `id`, `status`, `is_dry_run`, `rows_total`, `rows_imported`, `rows_failed`, `error_document_id` | `payroll.run.execute` |
| GET | `/api/v1/payroll/prior-payroll-imports/{id}` | — | the log, or `404` | `payroll.run.read` |
| GET | `/api/v1/payroll/prior-payroll-imports` | `?page=&size=` | logs, newest first | `payroll.run.read` |
| GET | `/api/v1/payroll/prior-payroll?fy=&employeeId=&page=&size=` | — | the rows, period then employee number | `payroll.run.read` |
| DELETE | `/api/v1/payroll/prior-payroll/{id}` | — | `204`, or `404` | `payroll.run.execute` |
| GET | `/api/v1/payroll/prior-payroll/status?fy=` | — | `financial_year`, `first_regular_run_period` (in `fy`, or `null`), `imported_periods[]`, `missing_periods[]` | `payroll.run.read` |

`missing_periods` is every month from April of `fy` up to, not including, the first regular
run's period in `fy` — or the current month when `fy` has no regular run yet — that has no
imported row. A tenant whose first run is April has none. `W-47.6` shows its warning when the
list is not empty.

No new action codes. `payroll.run.read` and `payroll.run.execute` exist
(`reference/V020__action.sql:117-118`). Loading months that sit beside the runs is run work.

`dryRun` writes no `prior_payroll_month` row and still writes the log and the error file.

## 5. Frontend changes

None. The screen and the warning are `W-47.6` against §4.

## 6. Database changes

> Flyway only. Never `ddl-auto`. Every statement names its schema (`code/backend/migration/README.md`).

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V140__prior_payroll.sql` | `payroll.prior_payroll_import_log`, `payroll.prior_payroll_month` | yes | additive |

`V140` reserved 2026-10-02, above `dev-devashis`'s `V135`–`V138`.

`payroll.prior_payroll_import_log` — the `V134__leave_import_log.sql:4-21` columns, with
`financial_year VARCHAR(9) NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$')` in place of
`leave_year`. Indexes `(tenant_id, started_at DESC)`, `(tenant_id, status)`.

`payroll.prior_payroll_month`

| Column | Type |
|---|---|
| `id` | `UUID PK DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL REFERENCES core.tenant(tenant_id)` |
| `employee_id` | `UUID NOT NULL REFERENCES core.employee(id)` |
| `period` | `CHAR(7) NOT NULL CHECK (period ~ '^[0-9]{4}-(0[1-9]\|1[0-2])$')` — the `payroll.payrun` form (`V055__payrun.sql:7`) |
| `gross_earnings` | `NUMERIC(19,4) NOT NULL CHECK (>= 0)` |
| `epf_employee`, `esi_employee`, `professional_tax`, `tds` | `NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (>= 0)` |
| `net_pay` | `NUMERIC(19,4) NOT NULL CHECK (net_pay >= 0)` |
| `import_id` | `UUID NOT NULL REFERENCES payroll.prior_payroll_import_log(id)` |
| `created_at`, `created_by`, `updated_at`, `updated_by` | as `V134` |

Table `CHECK (net_pay <= gross_earnings - epf_employee - esi_employee - professional_tax - tds)`.

Indexes: `uk_prior_payroll_month_tenant_employee_period UNIQUE (tenant_id, employee_id, period)`
— one row per employee per month is a database fact; `idx_prior_payroll_month_tenant_period
(tenant_id, period)` for the run guard and the status; `idx_prior_payroll_month_tenant_employee
(tenant_id, employee_id, period)` for `W-38.2`'s per-employee sums.

Both tables: `ENABLE ROW LEVEL SECURITY`, policy `tenant_isolation` in the exact `CASE` form
(`V134__leave_import_log.sql:26-35`). Grants to `app_user`: `SELECT, INSERT, UPDATE` on the log;
`SELECT, INSERT, DELETE` on the month table.

- [x] `tenant_id` present on every new table (`CONVENTIONS.md` rule 7)
- [x] Index on `tenant_id` plus lookup columns (DEBT-018)
- [x] Money `NUMERIC(19,4)`, `BigDecimal` in the entities
- [x] Expand / contract — new tables only

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../priorpayroll/PriorPayrollRowValidatorTest.java` | one row per reason in §4, each rejected with that reason and its line number; an empty deduction cell reads as `0`; `120000.5` passes, `120000.555` is `INVALID_AMOUNT` |
| Unit | `payroll/.../priorpayroll/PriorPayrollStatusTest.java` | fy `2026-2027`: first run `2026-10`, imports for `04`–`07` ⇒ missing `08`, `09`; first run `2026-04` ⇒ none; no run, today `2026-10-15` ⇒ `04`–`09` less imported |
| Integration | `payroll/.../priorpayroll/PriorPayrollImportIT.java` | **the acceptance test.** A file of 6 good and 2 bad rows ⇒ 6 rows saved, log `COMPLETED_WITH_ERRORS` 6/2, the error file has the 2 line numbers; the same file as a dry run first ⇒ 0 rows, same 2 errors; importing it again ⇒ 0 new, 6 `ALREADY_IMPORTED` |
| Integration | `payroll/.../priorpayroll/PriorPayrollPartialSuccessIT.java` | a good row **after** a bad row is committed, not rolled back (the `LeaveImportPartialSuccessIT` case) |
| Integration | `payroll/.../priorpayroll/PriorPayrollRunGuardIT.java` | a `REGULAR` run for `2026-08` ⇒ an `2026-08` row is `REAL_RUN_EXISTS`; a `CANCELLED` run does not block; rows for `2026-09` ⇒ `POST /payruns {2026-09}` is `409 PRIOR_PAYROLL_EXISTS`; delete them ⇒ the run is created; an off-cycle run is not blocked |
| Integration | `payroll/.../priorpayroll/PriorPayrollRlsIT.java` | as `app_user`, tenant A cannot read tenant B's rows or logs, cannot import tenant B's document; a raw `INSERT` with tenant B's id under A's context is refused |
| Integration | `payroll/.../priorpayroll/PriorPayrollGuardIT.java` | without `payroll.run.execute`, `POST` import and `DELETE` are `403`; without `payroll.run.read`, every `GET` is `403`; the template is `text/csv` with the 8 headers |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres azurite
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relname, relrowsecurity FROM pg_class WHERE oid IN ('payroll.prior_payroll_month'::regclass, 'payroll.prior_payroll_import_log'::regclass);"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexdef FROM pg_indexes WHERE schemaname='payroll' AND tablename='prior_payroll_month' AND indexname LIKE 'uk_%';"
grep -rn "@Transactional" code/backend/payroll/src/main/java/com/infinevo/payroll/priorpayroll/PriorPayrollImportServiceImpl.java || echo "no transaction on the import loop"
grep -rn "Double\|double\|float" code/backend/payroll/src/main/java/com/infinevo/payroll/priorpayroll/ || echo "no floating point"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | two rows, both `t` |
| Index | one row, `UNIQUE … (tenant_id, employee_id, period)` |
| Import loop | `no transaction on the import loop` |
| Floating point | `no floating point` |
| Suite | green, no skips; `PriorPayrollImportIT` and `PriorPayrollRunGuardIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| One `@Transactional` on the loop restores all-or-nothing | high — it is one annotation | `PriorPayrollPartialSuccessIT`; the §8 grep |
| A month is counted twice, once imported and once run | medium | the guard both ways, in `PriorPayrollRunGuardIT`; the unique index for re-imports |
| Import and run creation race for the same month | low | both check before writing; a cutover imports first, then runs. Accepted |
| A wrong file is loaded in full and the officer must delete row by row | medium | the screen dry-runs first (`W-47.6`); delete stays per row in this ticket |
| A cell starting `=` in the error file runs as a formula in Excel | low | escape as `LeaveImportServiceImpl.java:306-318` |

## 10. Rollback

Nothing is deployed. `V140` is additive and forward-only. Wrong rows are deleted through the
`DELETE` endpoint; the log stays as the record of what was loaded.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | both tables, same script |
| Flyway only, `ddl-auto` nowhere | `V140` |
| `Money`/`BigDecimal` for money | `NUMERIC(19,4)`, `BigDecimal` |
| Index on `tenant_id` plus lookup columns | five indexes, `tenant_id` leading |
| Expand / contract | new tables only |
| No module references another module | `payroll` uses `core` (`EmployeeRepository`, `DocumentService`, tenant timezone) and `shared` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Honoured** — RLS on both tables, `PriorPayrollRlsIT` |
| DEBT-007 no `/api/v1` | **Honoured** |
| DEBT-008 hand-built envelope | **Honoured** |
| DEBT-018 missing indexes | **Honoured** |
| DEBT-022 unscoped finders | **Honoured** |

## 13. Decisions — founder, 2026-10-02

| # | Question | Answer |
|---|---|---|
| 1 | New table or rows shaped like pay runs? | **A separate table.** Imported months never mix with real runs. `W-38.2` makes the two readers add it |
| 2 | Which figures per row? | **Gross, EPF, ESI, PT, TDS, net.** Employee share only |
| 3 | A month with a real run? | **Refused, both ways.** A month is imported or run, never both |
| 4 | Mandatory? | **No.** The setup step can be skipped with a reason (`W-38.3`); `W-47.6` warns a mid-year tenant that has imported nothing |
| 5 | Which document kind is the upload? | `EMPLOYEE_DOCUMENT` with no employee, the only uploadable kind that fits (`core/.../document/DocumentKind.java:16-22`). A dedicated `IMPORT` kind is a `W-21` change later, for this and `W-16.4b` both |
| 6 | Does the tax calculator use the imported gross? | **Not in `W-38`.** `SalaryProjection` projects from salary versions from April (`payroll/taxcalc/engine/SalaryProjection.java:53-56`); a mid-year tenant whose first salary version starts in October under-projects the year. Open for the founder |
