# Feature: Income tax declaration — other income and tax summary

| Field | Value |
|---|---|
| **Feature ID** | `W-32.4` · from ticket #42 (`W-32`) · `PAY-09` part 4 of 4 |
| **Promoted to** | `docs/target-state/features/W-32-4-tax-declaration-other-income-summary.md` on the developer's `dev-<name>` branch — **`W-32-4` with hyphens**, never `W-32.4`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for these tables), DEBT-018 (honoured), DEBT-022 (fixed), DEBT-027 (fixed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-32.1` — the header and `editable()`. The declared-totals summary reads `.2` and `.3`'s tables when they exist and reports zero for a section not yet merged, so this part does not wait on them |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V080` other income, `V081` tax summary — one table each. Aggregate exception, same as `W-31.1` | 1 — **exception requested**, see §14 |
| Externally testable behaviour | an employee declares income from other sources and reads one summary of everything declared for the year, totalled by the server | 1 |
| Frontend area | none | 1 |

Part 4 of the `W-32` split, sized S (`10-scoping.md:198`). The summary table is here
because the data model places it under tax declaration (`02-data-model.md:170`); the
**computed** figures on it are written by `W-33`, not here (§13, decision 2).

---

## 1. Problem

- **Other income rows carry two amounts and a lender.** `amount` and `declared_amount`,
  plus `name_of_lender`, `pan_of_lender`, `can_edit_in_portal`
  (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvOtherIncome.java:22-47`);
  the lender columns belong to a housing loan and are never set for interest income
- **The type is free text** set by the screen: `savings_interest`, `fd_interest`,
  `nsc_interest`, `other_income`
  (`legacy/Payroll-Fend-react/src/pages/mainPages/userPortal/userInvestment/userInvestmentDeclaration.js:1049-1088`)
- **The tax summary is written by nobody.** `EmployeeInvTaxSummary` has 14 money columns
  at `numeric(18,3)` and five `_formatted` twins (`EmployeeInvTaxSummary.java:22-80`), mapped
  only from the update DTO (`mapper/employeeitdeclaration/EmployeeInvestmentDeclarationMapper.java`)
  — the browser sends the employee's own tax figures and the server stores them. No
  service under `serviceimpl/` writes the table
- **There is no declared-totals view.** To see "how much did I declare under 80C" the
  screen sums rows client-side (DEBT-027)

## 2. Scope

**In scope**

- `payroll.employee_inv_other_income` — one row per kind: `SAVINGS_INTEREST`,
  `FD_INTEREST`, `NSC_INTEREST`, `OTHER`, with a description for `OTHER`
- `payroll.employee_inv_tax_summary` — one row per declaration per regime, created empty by
  this ticket's summary read and **filled by `W-33`** through `TaxSummaryService.record(...)`
- `GET .../summary` — the declared totals per section computed server-side, plus the
  `W-33` figures when present
- One `PUT` for other income

**Out of scope**

- Any tax figure — taxable income, tax on it, TDS to date, months remaining. `W-33`
  computes and calls `record`; `W-36` reads the row for TDS
- The 80TTA/80TTB deduction on savings interest — `W-33`, from
  `reference.other_income_rule_master` (`V004__reference_tax_masters.sql:127-145`)
- Screens — `W-25`, `W-47`

## 3. Flow

```
[employee] --> PUT /me/tax-declaration/{fy}/other-income --> payroll.tax_declaration.declare_own
   --> TaxDeclarationService.require(currentEmployee, fy); editable(id) else 409
   --> one row per kind, amount >= 0, description required for OTHER --> replace --> [employee_inv_other_income]

[employee] --> GET /me/tax-declaration/{fy}/summary --> payroll.tax_declaration.read_own
   --> DeclaredTotals: sums over house rent (annual), home loan principal + interest, let-out net,
       section 6A per group, pre-tax, previous employment income and tax, other income
   --> find-or-create [employee_inv_tax_summary] for (declaration, regime) --> attach its computed columns, null until W-33 runs

[officer]  --> same two on /payroll/employees/{id}/tax-declaration/{fy}/... under .manage / .read
W-33 --> TaxSummaryService.record(declarationId, regime, TaxSummaryFigures) --> UPDATE the row's computed columns, computed_at
W-36 --> reads tds_through_payroll etc. by (tenant, declaration, regime)
```

## 4. Backend changes

Under `code/backend/payroll/src/main/java/com/infinevo/payroll/taxdeclaration/summary/`.

| Layer | File | Change |
|---|---|---|
| Entity | `EmployeeInvOtherIncome`, `EmployeeInvTaxSummary` | new, `@Table(schema = "payroll")`, `UUID` ids, `BigDecimal(19,4)` |
| Enumeration | `OtherIncomeKind { SAVINGS_INTEREST, FD_INTEREST, NSC_INTEREST, OTHER }` | new |
| Repository | one per entity | new; every finder takes `tenantId`; `findByTenantIdAndDeclarationIdAndRegime` |
| Service / ServiceImpl | `OtherIncomeService`, `…Impl` | new — `read`, `replace` |
| Service / ServiceImpl | `TaxSummaryService`, `…Impl` | new — `summary(declarationId)` (declared totals + the row), `record(declarationId, regime, TaxSummaryFigures)` for `W-33` |
| Record | `TaxSummaryFigures` | new — the eleven computed amounts plus `remaining_months`, all `BigDecimal`/`int`, the one type `W-33` builds |
| Controller | `MyOtherIncomeController`, `OtherIncomeController`, `MyTaxSummaryController`, `TaxSummaryController` | new |
| DTO | `OtherIncomeRequest`, `OtherIncomeResponse`, `TaxSummaryResponse` | new; envelope as `W-32.1` |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me/tax-declaration/{fy}/other-income` | — | rows[] | `payroll.tax_declaration.read_own` |
| PUT | `/api/v1/me/tax-declaration/{fy}/other-income` | rows[] of `{kind, description, amount}` | `200`; `409 NOT_EDITABLE`; `400` on a rule | `payroll.tax_declaration.declare_own` |
| GET | `/api/v1/me/tax-declaration/{fy}/summary` | — | `declared: {house_rent_annual, home_loan_principal, home_loan_interest, let_out_net, section6a_by_group{}, section6a_total, pre_tax_total, prev_employment_income, prev_employment_tax, other_income_total}` and `computed: {…11 figures, remaining_months, computed_at}` or `null` | `payroll.tax_declaration.read_own` |
| GET / PUT | `/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/other-income`, `/summary` | as above | window ignored | `payroll.tax_declaration.read` / `.manage` |

Validation, all `400`: one row per `kind`; `amount >= 0`; `description` required when
`kind = OTHER`, ignored otherwise.

`declared` is computed on every read from the section tables, never stored (§13,
decision 1). Each figure at scale 2 in the response, summed at scale 4 through `Money`.
`house_rent_annual` is `Σ amount_per_month × months` over `.2`'s rows;
`section6a_by_group` keys on `category_group_code`, with ungrouped items under their own
`section_code`. A section whose ticket is not merged yet reports `0`.

`record(...)` writes only the computed columns and `computed_at`; it never touches the
declaration's status. Called by `W-33` in its own transaction.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V080__employee_inv_other_income.sql` | `payroll.employee_inv_other_income` | yes | additive |
| `payroll/V081__employee_inv_tax_summary.sql` | `payroll.employee_inv_tax_summary` | yes | additive |

Both: `id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`declaration_id uuid NOT NULL REFERENCES payroll.employee_investment_declaration ON DELETE CASCADE`
· four audit columns. Then:

| Table | From | Columns |
|---|---|---|
| `employee_inv_other_income` | `EmployeeInvOtherIncome.java:22-38` | `kind varchar(24) NOT NULL CHECK (kind IN ('SAVINGS_INTEREST','FD_INTEREST','NSC_INTEREST','OTHER'))` · `description varchar(150)` · `amount numeric(19,4) NOT NULL CHECK (amount >= 0)` |
| `employee_inv_tax_summary` | `EmployeeInvTaxSummary.java:22-65` | `regime varchar(3) NOT NULL CHECK (regime IN ('OLD','NEW'))` · `taxable_income`, `net_taxable_income`, `tax_on_taxable_income`, `tax_ytd_amount`, `tax_to_be_paid`, `tds_through_payroll`, `tds_previous_employer`, `tds_other_income`, `other_sources_income`, `exemption_under_section10`, `exemption_under_section6a` — all `numeric(19,4)`, **nullable** · `remaining_months smallint` · `computed_at timestamptz` |

**Deliberately absent:** `declared_amount`, `can_edit_in_portal`, `name_of_lender`,
`pan_of_lender`, `type_formatted`, `item_id_external` on other income (`:25-47`); the five
`_formatted` columns and `tax_year_start`/`tax_year_end` on the summary (`:25-29,67-80`) —
the declaration's `financial_year` says which year.

The computed columns are nullable on purpose: a row exists from the first summary read so
the officer sees "not yet computed" rather than a missing row, and `W-33` fills it.

- [x] `tenant_id`, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `uk_employee_inv_other_income_tenant_declaration_kind (tenant_id, declaration_id, kind)` · `uk_employee_inv_tax_summary_tenant_declaration_regime (tenant_id, declaration_id, regime)`
- [x] Money columns `numeric(19,4)`; nothing floating; legacy's `(18,3)` and `(15,2)` both widen to `(19,4)`
- [x] Expand / contract — two new tables

RLS and `tenant_isolation` in the exact `CASE` form on both.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../taxdeclaration/summary/OtherIncomeRulesTest.java` | second row of one kind refused; `OTHER` without description refused; negative refused |
| Unit | `payroll/.../taxdeclaration/summary/DeclaredTotalsTest.java` | rent 20,000 × 12 months = 2,40,000; two rent periods add; `section6a_by_group` sums `80C` + `80CCC` under `80C_GROUP` and lists `80D` on its own; an absent section reports `0`; scale 2 in the response |
| Integration | `payroll/.../taxdeclaration/summary/TaxSummaryIT.java` | **the acceptance test**: `DRAFT` header; `PUT` other income (two kinds); `GET …/summary` returns `declared.other_income_total` and a `computed: null`; `record(...)` with figures ⇒ `GET` returns them with `computed_at`; a second `record` overwrites; header `SUBMITTED` ⇒ `PUT` `409`, `GET` still `200` |
| Integration | `payroll/.../taxdeclaration/summary/TaxSummaryRlsIT.java` | as `app_user`, tenant A cannot read tenant B's summary or other income, and `record` against tenant B's declaration affects zero rows |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. `DeclaredTotalsTest`
seeds `.2` and `.3` rows directly through their repositories if those tickets are merged,
and otherwise covers the zero case only — the developer notes which in the PR.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relname, relrowsecurity FROM pg_class
    WHERE relname IN ('employee_inv_other_income','employee_inv_tax_summary')
      AND relnamespace='payroll'::regnamespace ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) FILTER (WHERE is_nullable='YES') AS nullable_money
     FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='employee_inv_tax_summary' AND data_type='numeric';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) FROM information_schema.columns
    WHERE table_schema='payroll' AND data_type IN ('double precision','real');"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | two rows, both `t` |
| Nullable money columns on the summary | `11` |
| Floating-point columns in `payroll` | `0` |
| Suite | green, no skips; `TaxSummaryIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The summary read accepts computed figures from the client, as legacy does | **medium** | there is no `PUT` on `/summary`; `record` is a service method with no endpoint |
| `W-33` writes its own summary table instead of calling `record` | medium | `TaxSummaryFigures` and `record` are named here and in `W-33.1`'s blockers; `02-data-model.md:170` lists one summary table |
| Totals drift from the section tables because they are cached | low | never stored (decision 1) |
| `.4` merged before `.2`/`.3` and the totals code references entities that do not exist | medium | the totals service depends on the three section **repositories** through `Optional` beans (`ObjectProvider`); absent ⇒ `0`. `DeclaredTotalsTest` covers the absent case |

## 10. Rollback

Nothing is deployed. Both scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | two tables |
| Flyway only, `ddl-auto` nowhere | two scripts, one table each |
| `Money`/`BigDecimal` for money | twelve money columns `numeric(19,4)`; totals through `Money` |
| Index on `tenant_id` plus lookup columns | two unique indexes, `tenant_id` leading |
| Expand / contract | new tables only |
| No module references another module | `payroll` → `core` (via `W-32.1`) and `shared` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for these tables** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** |
| DEBT-027 computed in the browser | **Fixed** — declared totals come from the server; tax figures come from `W-33`, never the client |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Store the declared totals? | **No.** Computed on read from the section tables. A stored total is a second source of truth that goes stale on every section write |
| 2 | Who writes the tax summary row? | **`W-33`**, through `record`. Legacy stores whatever the browser sent (`EmployeeInvestmentDeclarationMapper.java`), which is the one thing a tax record must never do. This ticket creates the row and the API; the figures arrive when the calculator does |
| 3 | One summary per declaration or per regime? | **Per regime**, as legacy (`EmployeeInvTaxSummary.java:22-23`) — `W-33` computes both so the employee can compare before choosing, and `W-32.1`'s `can_change_tax_regime` decides whether they may switch |
| 4 | Port the lender columns on other income? | **No.** They were a copy of the home-loan columns on the wrong table; `.2` holds them |

## 14. Open for the founder

| # | Question | Proposed |
|---|---|---|
| 1 | The size-cap exception: two scripts, one table each | **Grant** — same shape as `W-31.1` |
