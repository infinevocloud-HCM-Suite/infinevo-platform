# Feature: Tax deducted at source — the annual record and the pay run tax line

| Field | Value |
|---|---|
| **Feature ID** | `W-36.1` · from ticket #48 (`W-36`) · `PAY-14` · the `TAX` contributor `W-29.2` reserved |
| **Promoted to** | `docs/target-state/features/W-36-1-employee-tds.md` on the developer's `dev-<name>` branch — **`W-36-1` with hyphens**, never `W-36.1`; `guard-edit` blocks the dotted form |
| **Owner** | karma (from sayeed 2026-10-02) |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), BUG-011 (fixed), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-018 (honoured), DEBT-019 (fixed), DEBT-022 (fixed); proposed BUG-014 / DEBT-034 (`.claude/outputs/2026-09-29-analyze-w-36-tds-payslips.md`) belong to `W-36.2` |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | `W-29.2` — `PayLineContributor` and `employee_payrun_line` · `W-32.1` — `FinancialYear` in `payroll` (§13 decision 4 there). **Not** blocked by `W-33`: the calculator is one writer of the record, the officer is the other, and the line reads the record |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V102` — one table, `payroll.employee_tds` | 1 |
| Externally testable behaviour | a computed run carries one `TAX` deduction line per employee equal to the tax still owed for the financial year spread over the months left in it | 1 |
| Frontend area | none — the officer screen is a `W-47` ticket | 1 |

Within cap. `W-36` was split on 2026-09-29: `W-36.1` is the tax record and the run line,
`W-36.2` is approve, pay, the payslip and its signed link, `W-36.3` is the annual statement
(Form 16), whose spec waits on `W-33`.

---

## 1. Problem

The frozen Payroll backend has the right idea and the wrong plumbing. All citations are
`legacy/`, code being replaced.

- **One annual figure, prorated monthly.** `employee_tds` holds `final_annual_tax` per employee
  per fiscal year (`Payroll-Bend-SBoot/.../entity/employeeTDS/EmployeeTds.java:22-48`);
  `resolveMonthlyTds` subtracts what the year's runs already took and divides by the months
  left to March (`.../serviceimpl/payruns/EmployeePayRunServiceImpl.java:531-640`). That rule
  is correct and is ported as-is
- **The monthly amount lives on the pay run row as a `Double`** (`EmployeePayRun.java:95`),
  outside the line model — BUG-011. The target has `employee_payrun_line` with a `TAX` source
  reserved for exactly this (`W-29-2-pay-run-computation.md:36`, `:171`)
- **The record is written by the run itself.** A missing row is created mid-computation by
  `DefaultTdsCreationServiceImpl.createDefaultTdsIfNotExists` (`EmployeePayRunServiceImpl.java:557-563`),
  which runs the whole tax calculator inside the pay run. A pay run must never compute tax;
  it reads a figure someone else settled
- `employee_id` and `organization_id` are strings (`EmployeeTds.java:16-20`), `fiscal_year`
  an `Integer` (`:22`), money `DECIMAL(12,2)` (`:35-42`), no index (DEBT-018), and the
  repository finder takes the org as a string (DEBT-022)
- `is_active` with no end marker (`:47`): a superseded row is flipped in place, so nothing
  says what the run of a paid month read

## 2. Scope

**In scope**

- `payroll.employee_tds` — the annual record: one active row per employee per financial
  year, with the superseded rows kept
- `EmployeeTdsService.record(...)` — the one writer. Called by the officer through a `PUT`
  now, and by `W-33` when it computes a declaration
- `TaxLineContributor`, `@Order(500)`, after `STATUTORY` (400): one `DEDUCTION` line,
  `source = TAX`, `component_code = TDS`, per included employee with an active record
- Officer read of an employee's record, and the year-to-date figure the line was built from
- `employee.read_own` view of the same under `/me`

**Out of scope**

- Computing tax. `W-33` computes; this ticket stores what it is told
- A default estimate when there is no record (`DefaultTdsCreationServiceImpl.java:71`).
  No record, no line, and the run's response says so per employee. `W-33` decides whether
  a regime default is computed on declaration-window open
- Previous-employer TDS and other-income TDS. They reduce the annual figure inside `W-33`,
  which passes the net amount to `record`
- The annual statement — `W-36.3`
- Screens — `W-47`

## 3. Flow

```
[W-33 or payroll officer] --> EmployeeTdsService.record(employeeId, fy, TdsFigures)
   --> UPDATE the active row: superseded_at = now(), is_active = false
   --> INSERT payroll.employee_tds  is_active = true, effective_from_period = the request's, or the current period
   --> 200 with the new row

[pay run, W-29.2 loop] --> TaxLineContributor.contribute(ctx)     @Order(500)
   fy        = FinancialYear.of(ctx.period)                           (W-32.1, April–March)
   record    = active employee_tds for (tenant, employee, fy)        none → no line, note in employee_payrun.computation_note
   period < record.effective_from_period                              → no line
   ytd       = Σ amount of TAX lines on this tenant's other runs whose period is in fy, up to
               and including ctx.period, and whose status is COMPUTED, APPROVED or PAID
                                                                       (a FAILED or CANCELLED run counts nothing)
   remaining = record.annual_tax − ytd                                 ≤ 0 → no line
   months    = months from max(ctx.period, record.effective_from_period) to fy's March, inclusive
   amount    = Money.of(remaining).divide(months)                      scale 4, as every other line
   --> one PayLine: DEDUCTION, TAX, "TDS", "Tax deducted at source", amount, is_taxable = false
```

The year-to-date sum is derived from the lines, never stored. That is what makes a recompute
of an earlier month safe: the `W-29.2` loop deletes the run's own lines first, so the run
being computed never counts itself.

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/tds/`.

| Layer | File | Change |
|---|---|---|
| Entity | `EmployeeTds.java` | new, `@Table(name = "employee_tds", schema = "payroll")`, `@Audited`; `UUID id`, `UUID tenantId`, `UUID employeeId`, `String financialYear`, `TaxRegime regime`, `TdsSource source`, `UUID declarationId`, `BigDecimal annualGross`, `BigDecimal annualTaxableIncome`, `BigDecimal annualTax`, `String effectiveFromPeriod`, `boolean isActive`, `Instant supersededAt`, `String note`, audit columns |
| Enumeration | `TdsSource.java` | new — `DECLARATION` (written by `W-33`), `OFFICER` (the `PUT`). Legacy `POI_BASED` / `DEFAULT_REGIME` / `SALARY_REVISION` (`enumeration/TdsSourceType.java:5-7`) all collapse into `DECLARATION`; `W-33` records why in `note` |
| Record | `TdsFigures.java` | new — `regime`, `annualGross`, `annualTaxableIncome`, `annualTax`, `effectiveFromPeriod?`, `declarationId?`, `note?`; all money `BigDecimal` |
| Repository | `EmployeeTdsRepository.java` | new — `findActive(tenantId, employeeId, financialYear)`, `findByTenantIdAndEmployeeIdAndFinancialYearOrderByCreatedAtDesc`. Every finder takes `tenantId` (DEBT-022) |
| Repository (change) | `payrun/EmployeePayRunLineRepository.java` (`W-29.2`) | one query: `sumTaxLines(tenantId, employeeId, periodFrom, periodTo, excludingPayrunId)` joining `payrun.status IN ('COMPUTED','APPROVED','PAID')` |
| Service / ServiceImpl | `EmployeeTdsService`, `EmployeeTdsServiceImpl` | new — `record(employeeId, fy, TdsFigures)`, `active(employeeId, fy)`, `history(employeeId, fy)`, `yearToDate(employeeId, fy)` |
| Contributor | `TaxLineContributor.java` | new, `@Order(500)`, implements `W-29.2`'s `PayLineContributor`; §3 |
| Controller | `EmployeeTdsController.java` | new |
| DTO | `RecordTdsRequest`, `EmployeeTdsResponse` (adds `year_to_date`, `remaining`) | new; `status` / `message` / `data` envelope (`CONVENTIONS.md` §3) |

`FinancialYear` is `W-32.1`'s class (`W-32-1-tax-declaration-window.md:307`), label `2026-2027`.
If `W-32.1` is not on `main` when this branch is cut, the developer adds the class under
`payroll/tax/` with the same name and signature, and `W-32.1` keeps it.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| PUT | `/api/v1/payroll/employees/{employeeId}/tds/{fy}` | `regime`, `annual_gross`, `annual_taxable_income`, `annual_tax`, `effective_from_period?` (`YYYY-MM`), `note?` | `200`, the new active row with `year_to_date` and `remaining` | `payroll.tax_declaration.verify` |
| GET | `/api/v1/payroll/employees/{employeeId}/tds/{fy}` | — | the active row, `year_to_date`, `remaining`, or `404` | `payroll.tax_declaration.read` |
| GET | `/api/v1/payroll/employees/{employeeId}/tds/{fy}/history` | — | every row, newest first | `payroll.tax_declaration.read` |
| GET | `/api/v1/me/tds/{fy}` | — | own active row with `year_to_date`, or `404` | `payroll.tax_declaration.read_own` |

No new action codes: `payroll.tax_declaration.verify`, `.read`, `.read_own` exist
(`migration/.../reference/V020__action.sql:125-129`) and are granted in `V052__fbp_actions.sql:94-95`, `:123`.
A `PUT` by the officer is a tax figure set by the person who verifies declarations, which is
the right holder; a dedicated `payroll.tds.manage` code is a `W-11.3`-style change if the founder
wants one later.

**Validation, all `400`**

- `fy` matches `^\d{4}-\d{4}$` and the second year is the first plus one
- `employee_id` is an employee of the bound tenant (`EmployeeService`)
- `annual_tax >= 0`, `annual_gross >= annual_taxable_income >= 0`, scale at most 2
- `effective_from_period`, when given, is a month inside `fy`; default is the current
  period per the tenant's clock
- `regime` is `OLD` or `NEW` (`W-32.4`'s `CHECK`, `W-32-4-…md:137`)

**`record` is one transaction**: supersede then insert. A second `PUT` with identical figures
still inserts — the history is the point. `W-33` calls `record` in its own transaction after
`TaxSummaryService.record` (`W-32-4-…md:79`), with `source = DECLARATION` and the
declaration id.

## 5. Frontend changes

None. The officer's TDS panel and the employee's view are a `W-47` ticket against this contract.

## 6. Database changes

> Flyway only. Never `ddl-auto`. Every statement names its schema
> (`code/backend/migration/README.md`).

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V102__employee_tds.sql` | `payroll.employee_tds` | yes | additive |

`V102`–`V103` are reserved for `W-36.1`–`.2` on 2026-09-29, above `W-35.2`'s `V101`.

`payroll.employee_tds`, ported from `EmployeeTds.java:8-51` and `legacy/docs/DB_SCHEMA.md:1374-1391`

| Column | Type | Note |
|---|---|---|
| `id` | `UUID PK DEFAULT gen_random_uuid()` | legacy `Long` |
| `tenant_id` | `UUID NOT NULL REFERENCES core.tenant(tenant_id)` | legacy `organization_id VARCHAR` |
| `employee_id` | `UUID NOT NULL REFERENCES core.employee(id)` | legacy Keycloak string (`:16`) |
| `financial_year` | `VARCHAR(9) NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$')` | legacy `INTEGER` (`:22`); the `W-32.1` label |
| `regime` | `VARCHAR(3) NOT NULL CHECK (regime IN ('OLD','NEW'))` | legacy free `VARCHAR(20)` (`:32`) |
| `source` | `VARCHAR(16) NOT NULL CHECK (source IN ('DECLARATION','OFFICER'))` | legacy three values (`:26`) |
| `declaration_id` | `UUID NULL REFERENCES payroll.employee_investment_declaration(id)` | legacy `poi_id` (`:29`) |
| `annual_gross` | `NUMERIC(19,4) NOT NULL CHECK (annual_gross >= 0)` | legacy `DECIMAL(12,2)` |
| `annual_taxable_income` | `NUMERIC(19,4) NOT NULL CHECK (>= 0)` | |
| `annual_tax` | `NUMERIC(19,4) NOT NULL CHECK (annual_tax >= 0)` | legacy `final_annual_tax` |
| `effective_from_period` | `CHAR(7) NOT NULL CHECK (~ '^[0-9]{4}-(0[1-9]\|1[0-2])$')` | legacy month name text (`:44`) |
| `is_active` | `BOOLEAN NOT NULL DEFAULT true` | |
| `superseded_at` | `TIMESTAMPTZ NULL CHECK ((is_active) = (superseded_at IS NULL))` | new |
| `note` | `VARCHAR(255)` | new — why the figure changed |
| `created_at`, `created_by`, `updated_at`, `updated_by` | as `V051` | |

Indexes: `uk_employee_tds_tenant_employee_fy_active UNIQUE (tenant_id, employee_id, financial_year) WHERE is_active`
— the partial unique index that makes "one active row" a database fact, the `W-29.1`
`uk_payrun_tenant_period` shape; `idx_employee_tds_tenant_employee_fy (tenant_id, employee_id, financial_year, created_at DESC)`.
RLS policy `tenant_isolation` in the exact `CASE` form (`migration/README.md` §row-level security).

No change to `employee_payrun_line`: `source` already admits `'TAX'` (`W-29-2-…md:171`).

- [x] `tenant_id` present on every new table (`CONVENTIONS.md` rule 7)
- [x] Index on `tenant_id` plus lookup columns (DEBT-018)
- [x] Money columns `NUMERIC(19,4)`, `BigDecimal` in the entity, `Money.divide` for the line
- [x] Expand / contract — new table only

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../tds/TaxLineContributorTest.java` | **the hand calculation**: annual tax 120,000, ytd 0, period `2026-04` ⇒ 12 months ⇒ `10,000.0000`; period `2026-10`, ytd 60,000 ⇒ 6 months ⇒ `10,000.0000`; ytd 70,000 in `2026-10` ⇒ `8,333.3333`; ytd ≥ annual ⇒ no line; period before `effective_from_period` ⇒ no line; no active record ⇒ no line and a note; March with remaining 1,234.5 ⇒ one line of `1,234.5000` |
| Unit | `payroll/.../tds/EmployeeTdsRulesTest.java` | bad `fy` label; `annual_taxable_income > annual_gross`; negative tax; `effective_from_period` outside `fy`; regime not `OLD`/`NEW` |
| Integration | `payroll/.../tds/EmployeeTdsIT.java` | `PUT` twice leaves two rows, one active with `superseded_at` null and one with it set; `GET` returns the active one; `GET …/history` returns both newest first; `GET /me/tds/{fy}` for another employee's login is `404` |
| Integration | `payroll/.../tds/PayRunTaxLineIT.java` | **the acceptance test**: record annual tax 120,000 from `2026-04`; lock and compute the April run ⇒ one `TAX` `DEDUCTION` line of `10,000.0000` and `total_deductions` includes it; compute May ⇒ `10,000.0000` again (ytd 10,000, 11 months); recompute April ⇒ still `10,000.0000` (its own line was not counted); cancel May then compute June ⇒ ytd counts April only; `implements PayLineContributor` count is 5 |
| Integration | `payroll/.../tds/EmployeeTdsRlsIT.java` | as `app_user`, tenant A cannot read tenant B's row; a raw-SQL `INSERT` with tenant B's id under tenant A's context is refused; two active rows for one `(tenant, employee, fy)` are refused by the index |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. `PayRunTaxLineIT` reuses
`W-31.4`'s run setup (`PayRunStatutoryIT`).

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='payroll.employee_tds'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type, numeric_precision, numeric_scale FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='employee_tds'
      AND column_name IN ('annual_tax','financial_year','effective_from_period','superseded_at');"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexdef FROM pg_indexes WHERE schemaname='payroll' AND tablename='employee_tds' AND indexname LIKE 'uk_%';"
grep -rn "implements PayLineContributor" code/backend/payroll/src/main/java | wc -l
grep -rn "Double\|double\|float" code/backend/payroll/src/main/java/com/infinevo/payroll/tds/ || echo "no floating point"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Types | `annual_tax` `numeric` 19,4; `financial_year` `character varying`; `effective_from_period` `character`; `superseded_at` `timestamp with time zone` |
| Index | one row, `UNIQUE … (tenant_id, employee_id, financial_year) WHERE is_active` |
| Contributors | `5` — structure, LOP, pay input, statutory, tax |
| Floating point | `no floating point` |
| Suite | green, no skips; `PayRunTaxLineIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The contributor computes a default tax when there is no record, as legacy (`EmployeePayRunServiceImpl.java:557-563`) | high — it is the port habit | §2 rules it out; the reviewer checks `TaxLineContributor` has no dependency outside `EmployeeTdsService` and `EmployeePayRunLineRepository` |
| Year-to-date counts the run being computed, doubling the deduction on recompute | medium | `sumTaxLines` takes `excludingPayrunId`; `PayRunTaxLineIT` recomputes April |
| Year-to-date counts a `FAILED` or `CANCELLED` run | medium | the status filter is in the query; the IT cancels May |
| `remaining_months` counted from the record's effective month instead of the run's period, as one legacy branch does (`:602-607`) | low | the unit test's `2026-10` case |
| `W-33` writes `employee_tds` directly instead of calling `record` | medium | `record` is named here and must be named in `W-33.1`'s blockers; `02-data-model.md:352` lists one table |

## 10. Rollback

Nothing is deployed. `V102` is additive and forward-only. A wrong figure is corrected by
another `PUT`; the next computed run picks it up, and the paid months stay as they were.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `payroll.employee_tds` |
| Flyway only, `ddl-auto` nowhere | `V102` |
| `Money`/`BigDecimal` for money | `NUMERIC(19,4)`, `BigDecimal`, `Money.divide` for the line |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `payroll` uses `core` (`EmployeeService`) and `shared` (`Money`); `W-33` lives in `payroll` too |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for this table** |
| BUG-011 money as `Double` (`EmployeePayRun.java:95`) | **Fixed** — the amount is a line |
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-008 hand-built envelope | **Fixed** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-019 N+1 | **Fixed** — one aggregate query for ytd, not a scan of rows |
| DEBT-022 unscoped finders | **Fixed** |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | Where does the monthly amount live? | **An `employee_payrun_line`, `source = TAX`.** `02-data-model.md:352` gives `PAY-14` one table, and the line table already reserves the source (`W-29-2-…md:36`). No `monthly_tds` column anywhere |
| 2 | Is year-to-date stored? | **No, derived from the lines of `COMPUTED`, `APPROVED` and `PAID` runs.** Legacy sums the row column (`:576-584`); storing it would drift on every recompute |
| 3 | Who writes the annual record? | **`EmployeeTdsService.record`, called by `W-33` or by an officer `PUT`.** The pay run never computes tax. A run with no record gets no line and says so |
| 4 | Supersede or update in place? | **Supersede.** A paid month must be explainable by the row that was active when it ran. Partial unique index keeps one active |
| 5 | Which action guards the officer `PUT`? | **`payroll.tax_declaration.verify`**, an existing code. A new `payroll.tds.manage` is a one-line seed later if the founder wants a narrower holder |
| 6 | Financial year source? | **`W-32.1`'s `FinancialYear`**, April–March, label `2026-2027`. One class, not a second |
