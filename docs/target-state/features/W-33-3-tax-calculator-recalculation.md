# Feature: Tax calculator — revisions, recalculation and the computation history

| Field | Value |
|---|---|
| **Feature ID** | `W-33.3` · from ticket #45 (`W-33`) · `PAY-10` part 3 of 3 |
| **Promoted to** | `docs/target-state/features/W-33-3-tax-calculator-recalculation.md` on the developer's `dev-<name>` branch — **`W-33-3` with hyphens**, never `W-33.3`; `guard-edit` blocks the dotted form |
| **Owner** | mohit |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-018 (honoured), DEBT-021 (discounted — no scheduler), DEBT-022 (fixed); proposed "no recalculation on declaration edit" (analysis 2026-09-29) fixed |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | `W-33.1`, `W-33.2` — the calculators · `W-36.1` — `EmployeeTdsService.record(...)` (**unassigned as of 2026-09-29**; this ticket cannot start until it is on `main`) · `W-32.1` — the submit it listens to · `W-26.2` (on `main`) — the salary version write it listens to. `W-34` is not a blocker: it will publish the event named in § 3 when it exists |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V104` — one table, `payroll.tax_computation`; plus one `reference` seed for FY 2026-27 (added 2026-10-02, founder's exception — no table, rows only) | 1 |
| Externally testable behaviour | when a declaration is submitted or a salary version changes, the employee's TDS record and summary are recomputed and every computation is kept | 1 |
| Frontend area | none | 1 |

Within cap. Sized M (`10-scoping.md:204`).

---

## 1. Problem

All citations are `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/`, code being replaced.

- **Two of three triggers exist; the obvious one does not.** A salary revision recomputes
  (`serviceimpl/employee/CtcStructureServiceImpl.java:1713` → `serviceimpl/employeeTDS/TdsSalaryRevisionServiceImpl.java:110-150`)
  and a proof approval recomputes (`serviceimpl/employeeitdeclaration/EmployeeProofOfInvestmentServiceImpl.java:1989`, `:2045-2078`).
  Submitting or editing the declaration recomputes nothing — the figure the pay run uses stays whatever the last event left
- **The first pay run computes tax itself** when no record exists
  (`serviceimpl/payruns/EmployeePayRunServiceImpl.java:545-560` → `DefaultTdsCreationServiceImpl.java:108-120`). `W-36.1` § 2 rules that out and hands the decision here
- **A revision with no prior record is silently skipped** (`TdsSalaryRevisionServiceImpl.java:70-84`) — the employee's first salary never produces a TDS figure until someone approves a proof
- **The history is split across five tables** — `new_tax_calculation`, `old_tax_calculation`, `old_tax_calculation_revision`, `old_tax_section_deduction`, `old_tax_revision_section_deduction` (`legacy/docs/DB_SCHEMA.md:944-1010`) — with a `revision_reason` only on the old-regime copy (`entity/EmployeeITDeclaration/taxCalculator/revision/OldTaxCalculationRevision.java:90`). `EmployeeTaxRecalculation` was the intended replacement and is commented out entirely (`…/taxCalculator/EmployeeTaxRecalculation.java:1-162`)

## 2. Scope

**In scope**

- `payroll.tax_computation` — one immutable row per recorded computation: the trigger, the regime, every headline figure, the working as JSON
- `TaxRecalculationService.recalculate(employeeId, fy, trigger)` — compute under the header's regime, store the row, `TaxSummaryService.record`, `EmployeeTdsService.record`; one transaction
- Three triggers as Spring application events handled after commit: `DeclarationSubmittedEvent` (published by `W-32.1`'s submit — one line added there), `SalaryVersionChangedEvent` (published by `W-26.2`'s create, revise and cancel — one line each), `ProofVerifiedEvent` (the type is defined here; `W-34` publishes it)
- The **no-declaration default**: a salary event for an employee with no header for the year computes under the window's `default_tax_regime` from salary alone and records TDS with trigger `SALARY_DEFAULT`. This is legacy's `DefaultTdsCreationService` moved out of the pay run
- Officer `POST …/tax/recalculate`; history reads for the officer and under `/me`

**Added 2026-10-02 — founder decisions taken when `W-33.1` and `W-33.2` merged (`7907a6c`)**

- **FY 2026-27 tax rules seed.** No slab, rebate, surcharge, cess or standard-deduction row exists for 2026-27, so every compute for the current year fails with `TAX_RULES_MISSING`. One `reference` migration carries the FY 2025-26 rows forward unchanged, as `V105` and `V106` did for the declaration rules (§ 6)
- **Surcharge marginal relief is removed**, so surcharge behaves as legacy: the band rate on tax after rebate, in full (`W-33.1` § 3 step 6 and § 13 decision 4, amended the same day). `SurchargeAndCess` loses the relief branch and the threshold-tax calculator that fed it; `marginal_relief` stays on the response as zero, so no contract changes

**Out of scope**

- The monthly split and year-to-date — `W-36.1`'s `TaxLineContributor`
- A scheduled sweep — nothing here runs on a clock (DEBT-021 discounted)
- Recomputing on every section `PUT` while the declaration is `DRAFT` — the employee previews with `GET …/tax`; the record follows the submit (§ 13 decision 1)
- Form 16 — `W-36.3` reads this table and `employee_tds`

## 3. Flow

```
[W-32.1 submit]        --> publish DeclarationSubmittedEvent(tenant, employeeId, declarationId, fy)
[W-26.2 create/revise/cancel] --> publish SalaryVersionChangedEvent(tenant, employeeId, effectiveFrom)
[W-34 verify]          --> publish ProofVerifiedEvent(tenant, employeeId, declarationId, fy)
[officer]              --> POST /payroll/employees/{id}/tax-declaration/{fy}/tax/recalculate     payroll.tax_declaration.manage

TaxRecalculationListener  @TransactionalEventListener(phase = AFTER_COMMIT)  @Async("taxRecalc")
   SalaryVersionChangedEvent --> fy = every financial year the version touches from FinancialYear.of(effectiveFrom) to the current one
   --> TaxRecalculationService.recalculate(employeeId, fy, trigger)        own transaction, tenant bound from the event

recalculate(employeeId, fy, trigger):
   header  = declaration for (tenant, employee, fy)
   regime  = header != null ? header.tax_regime : window(fy).default_tax_regime     (W-32.1 settings; NEW when no window)
   trigger = header == null ? SALARY_DEFAULT : trigger
   result  = TaxCalculationService.compute(employeeId, fy, regime)                    (W-33.1 / .2)
   INSERT payroll.tax_computation (…result, trigger, computed_by)
   if header != null: TaxSummaryService.record(header.id, regime, figures)            (W-32.4)
   EmployeeTdsService.record(employeeId, fy, TdsFigures{regime, annualGross = result.grossTotalIncome,
                             annualTaxableIncome = result.taxableIncome, annualTax = result.annualTax,
                             declarationId = header?.id, note = trigger + " " + computation id})   (W-36.1, source DECLARATION)
   --> the next computed run picks the new annual figure up; paid months stay (W-36.1 § 3)
```

A failure inside `recalculate` is logged with the tenant, employee and trigger and does not
roll back the submit or the salary write that caused it — the officer sees the stale
`computed_at` on the summary and can `POST …/recalculate`. A `RegimeNotAvailableException`
(`.2` not merged) is handled the same way.

## 4. Backend changes

Under `code/backend/payroll/src/main/java/com/infinevo/payroll/taxcalc/recalc/`.

| Layer | File | Change |
|---|---|---|
| Entity | `TaxComputationRecord` | new, `@Table(name = "tax_computation", schema = "payroll")`, immutable — no setters, no update path |
| Enumeration | `TaxTrigger` | `DECLARATION_SUBMITTED`, `SALARY_REVISION`, `SALARY_DEFAULT`, `PROOF_VERIFIED`, `OFFICER` |
| Events | `DeclarationSubmittedEvent`, `SalaryVersionChangedEvent`, `ProofVerifiedEvent` | new records under `taxcalc/recalc/event/`; `W-32.1` and `W-26.2` gain one `publisher.publishEvent(...)` each inside their transaction |
| Listener | `TaxRecalculationListener` | new; after-commit, async on a named executor so a submit returns before the calculator runs |
| Repository | `TaxComputationRepository` | new — `findByTenantIdAndEmployeeIdAndFinancialYearOrderByComputedAtDesc`; every finder takes `tenantId` |
| Service / ServiceImpl | `TaxRecalculationService`, `…Impl` | `recalculate(employeeId, fy, trigger)`; `history(employeeId, fy)`; `historyOwn(fy)` |
| Controller | `TaxRecalculationController`, `MyTaxHistoryController` | new |
| DTO | `TaxComputationRecordResponse` | envelope as `W-32.1`; `working` returned as the stored JSON |
| Change (`W-32.1`) | `TaxDeclarationServiceImpl.submit`, `submitOwn` | publish the event after the status write |
| Change (`W-26.2`) | `SalaryVersionServiceImpl` create, revise, cancel | publish the event after the write |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/tax/recalculate` | — | `200` the new computation row; `409 REGIME_NOT_AVAILABLE`; `422 TAX_RULES_MISSING` | `payroll.tax_declaration.manage` |
| GET | `/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/tax/history` | — | rows newest first | `payroll.tax_declaration.read` |
| GET | `/api/v1/me/tax-declaration/{fy}/tax/history` | — | own rows | `payroll.tax_declaration.read_own` |

No new action codes. The officer `POST` runs synchronously and returns the row.

## 5. Frontend changes

None. `W-47.3b` shows the history and the recalculate button.

## 6. Database changes

> Flyway only. Never `ddl-auto`. Every statement names its schema (`code/backend/migration/README.md`).

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V104__tax_computation.sql` | `payroll.tax_computation` | yes | additive |

| `reference/V<next>__fy_2026_27_tax_rules.sql` | none — rows only | `reference`, no tenant | additive |

`V104` reserved 2026-09-29, above `W-36.2`'s `V103`. The seed takes the next number above
everything on `main` when the branch is cut (`V134` is the highest on 2026-10-02); reserve it in
the tracker first.

**The FY 2026-27 seed** copies, for financial year 2026-27, every FY 2025-26 row the
calculators read through `TaxRuleReader` and that `V105` and `V106` did not already carry:
the slab masters and their brackets for `NEW` `GENERAL` and `OLD` `GENERAL`, `SENIOR`,
`SUPER_SENIOR` (`V005`, `V027`), the 87A rebate rules, the surcharge and cess rules, and the
standard deduction rules. `INSERT … SELECT` from the 2025-26 rows, guarded by `NOT EXISTS`,
so it is safe on a database that already holds a 2026-27 row. Values are unchanged by the
founder's decision of 2026-10-02; the tax-rule owner re-checks them before the first real
pay run, as for `V105` and `V106`.

`payroll.tax_computation` — replaces legacy's five result tables (§ 1) with one; the
per-section detail that filled `old_tax_section_deduction` and its revision twin is the `working` document.

| Column | Type | Note |
|---|---|---|
| `id` | `UUID PK DEFAULT gen_random_uuid()` | |
| `tenant_id` | `UUID NOT NULL REFERENCES core.tenant(tenant_id)` | |
| `employee_id` | `UUID NOT NULL REFERENCES core.employee(id)` | |
| `declaration_id` | `UUID NULL REFERENCES payroll.employee_investment_declaration(id)` | null for `SALARY_DEFAULT` |
| `financial_year` | `VARCHAR(9) NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$')` | the `W-32.1` label |
| `regime` | `VARCHAR(3) NOT NULL CHECK (regime IN ('OLD','NEW'))` | |
| `trigger` | `VARCHAR(24) NOT NULL CHECK (trigger IN ('DECLARATION_SUBMITTED','SALARY_REVISION','SALARY_DEFAULT','PROOF_VERIFIED','OFFICER'))` | legacy `revision_reason` (`OldTaxCalculationRevision.java:90`) |
| `gross_total_income`, `hra_exemption`, `standard_deduction`, `professional_tax`, `house_property_income`, `other_income`, `chapter_via`, `taxable_income`, `tax_before_rebate`, `rebate`, `surcharge`, `cess`, `prev_employer_tds`, `annual_tax` | `NUMERIC(19,4) NOT NULL` | `house_property_income` may be negative; every other `CHECK (>= 0)` |
| `working` | `JSONB NOT NULL` | the full `TaxComputation` — slab lines, section lines, assumptions |
| `computed_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |
| `computed_by` | `UUID NULL` | the officer for `OFFICER`, else null |
| `created_at`, `created_by` | as `V051` | no `updated_*` — rows are never updated |

Indexes: `idx_tax_computation_tenant_employee_fy (tenant_id, employee_id, financial_year, computed_at DESC)`;
`idx_tax_computation_tenant_declaration (tenant_id, declaration_id)`. RLS `tenant_isolation`
in the exact `CASE` form. No `UPDATE` or `DELETE` grant to `app_user` on this table — the
history is append-only at the database.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018)
- [x] Money columns `NUMERIC(19,4)`, `BigDecimal` in the entity
- [x] Expand / contract — new table only

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../taxcalc/recalc/TaxRecalculationRulesTest.java` | no header ⇒ regime from the window, trigger becomes `SALARY_DEFAULT`; no window ⇒ `NEW`; `SalaryVersionChangedEvent` with `effective_from 2025-01-01` in FY 2025-2026 ⇒ recalculates 2024-2025 and 2025-2026; a calculator exception ⇒ logged, nothing thrown to the publisher |
| Integration | `payroll/.../taxcalc/recalc/RecalculationIT.java` | **the acceptance test**: employee with CTC 15,75,000, header `NEW`; `POST …/submit` (`W-32.1`) ⇒ within 5 s one `tax_computation` row `DECLARATION_SUBMITTED`, `employee_tds` active row `annual_tax 1,09,200` with `source DECLARATION` and the note naming the trigger, summary `computed_at` set; revise the salary to 18,00,000 from `2025-10-01` (`W-26.2`) ⇒ a second row `SALARY_REVISION`, a new active `employee_tds` and the old one `superseded_at` set; officer `POST …/recalculate` ⇒ third row `OFFICER` with `computed_by`; `GET …/history` ⇒ three rows newest first; `GET /me/…/history` for another employee's login ⇒ `404` |
| Integration | `payroll/.../taxcalc/recalc/SalaryDefaultIT.java` | employee with a CTC and **no** header, window `default_tax_regime = OLD`; create the first salary version ⇒ one row `SALARY_DEFAULT`, `declaration_id null`, `employee_tds` written under `OLD`; the first computed pay run (`W-36.1`'s `PayRunTaxLineIT` setup) carries a `TAX` line — the legacy default without the run computing anything |
| Integration | `payroll/.../taxcalc/recalc/TaxComputationRlsIT.java` | as `app_user`, tenant A cannot read tenant B's rows; raw-SQL `UPDATE` and `DELETE` on the table are refused; cross-tenant `INSERT` refused by RLS |

| Integration | `migration/.../ReferenceSchemaIT.java` (change) | after the seed, FY 2026-27 has one slab master per regime and age category with the same brackets as 2025-26, and a rebate, surcharge, cess and standard-deduction rule for each regime |
| Integration | `payroll/.../taxcalc/TaxComputeIT.java` (change) | a compute for FY 2026-27 against the real migrations returns a figure, not `TAX_RULES_MISSING` |
| Unit | `payroll/.../taxcalc/SurchargeAndCessTest.java`, `NewRegimeCalculatorTest.java` (change) | taxable 50,10,000 ⇒ surcharge is the full 10 % of tax after rebate and `marginal_relief` is 0; the relief cases added under `W-33.1` are replaced, not deleted without a successor |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. The async listener is
awaited with Awaitility in the ITs, never with a sleep.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='payroll.tax_computation'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT privilege_type FROM information_schema.role_table_grants
    WHERE table_schema='payroll' AND table_name='tax_computation' AND grantee='app_user' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='tax_computation' AND data_type='numeric' AND numeric_scale=4;"
grep -rn "publishEvent" code/backend/payroll/src/main/java/com/infinevo/payroll/taxdeclaration code/backend/payroll/src/main/java/com/infinevo/payroll/salary | wc -l
grep -rn "@Scheduled" code/backend/payroll/src/main/java/com/infinevo/payroll/taxcalc/ || echo "no scheduler"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| `app_user` grants | `INSERT` · `SELECT` — no `UPDATE`, no `DELETE` |
| Money columns at scale 4 | `14` |
| Event publishers | `5` — submit, submitOwn, create, revise, cancel |
| Scheduler | `no scheduler` |
| Suite | green, no skips; `RecalculationIT` and `SalaryDefaultIT` passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The listener runs inside the submit's transaction and a calculator failure rolls the submit back | **high** — the default `@EventListener` does exactly that | `AFTER_COMMIT` + `@Async`; the rules test asserts nothing propagates |
| The pay run grows a "compute if missing" branch again | high — the port habit | `SalaryDefaultIT` proves the record exists before the run; `W-36.1` § 9 row 1 |
| Two events for one employee run concurrently and record twice | medium | harmless — two history rows, and `W-36.1`'s `record` supersedes in order; the last write wins by `created_at` |
| A revision effective in a past year recomputes only the current year | medium | the listener walks every year from `effective_from`; rules test |
| `W-36.1` still unassigned when mohit reaches this ticket | **high** | named in the header; the founder assigns `W-36.1` before `.3` starts, or mohit takes it |
| The history table becomes a second source for the TDS figure | low | `W-36.1` reads `employee_tds` only; this table is read by history endpoints and `W-36.3` |

## 10. Rollback

Nothing is deployed. `V104` is additive and forward-only. A wrong recalculation is corrected
by the next event or an officer `POST`; every earlier row stays as evidence.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `payroll.tax_computation` |
| Flyway only, `ddl-auto` nowhere | `V104` |
| `Money`/`BigDecimal` for money | fourteen `NUMERIC(19,4)` columns; `BigDecimal` in the entity |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `payroll` only; the events are `payroll` types published by `payroll` services |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 | **Fixed for this table** |
| DEBT-018 | **Honoured** |
| DEBT-021 unlocked schedulers | **Discounted** — event-driven, no cron |
| DEBT-022 | **Fixed** |
| Proposed: no recalculation on declaration change | **Fixed** — the submit is a trigger |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | Recompute on every section `PUT`? | **No — on submit.** A draft changes many times; the employee previews with `GET …/tax`. The TDS record should move when the employee commits, when pay changes, or when a proof is verified |
| 2 | Events or direct calls? | **Events.** `W-32.1` and `W-26.2` must not depend on the calculator; a one-line publish keeps them ignorant of it, and `W-34` can publish without touching this ticket |
| 3 | One history table or legacy's five? | **One**, with the working as JSONB. `02-data-model.md:348` lists six `PAY-10` tables; that row should be corrected by `/sync-docs` once this merges |
| 4 | Where does the legacy "default TDS" go? | **Here, on the first salary event**, never in the pay run — `W-36.1` § 2 |
| 5 | Which years does a salary change touch? | **Every financial year from its `effective_from` to the current one** |
| 6 | FY 2026-27 tax rules (2026-10-02) | **Carry FY 2025-26 forward unchanged**, in this ticket |
| 7 | Surcharge marginal relief (2026-10-02) | **Removed, as legacy**, in this ticket. The 87A rebate has no relief either, also as legacy |
| 8 | Taxable income rounding (2026-10-02) | **Nearest rupee, half-up**, as built; `W-33.1` § 3 step 3 corrected |

## 14. Open for the founder

| # | Question | Proposed |
|---|---|---|
| 1 | `W-36.1` is a hard blocker and unassigned | **Assign it now** — to mohit ahead of `.3`, or to whoever finishes first in stream 7d |
| 2 | Append-only at the database means an officer cannot delete a mistaken computation | **Accept** — a new row supersedes; nothing reads the old one for money |
