# Feature: Income tax declaration — section 6A, pre-tax deductions, previous employment

| Field | Value |
|---|---|
| **Feature ID** | `W-32.3` · from ticket #41 (`W-32`) · `PAY-09` part 3 of 4 |
| **Promoted to** | `docs/target-state/features/W-32-3-tax-declaration-deductions.md` on the developer's `dev-<name>` branch — **`W-32-3` with hyphens**, never `W-32.3`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for these tables), DEBT-018 (honoured), DEBT-022 (fixed), DEBT-027 (fixed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-32.1` — the header and `editable()`. `reference.section6a_item_master` (`W-09`, `V004:165-184`) is on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V077`–`V079`, one table each — section 6A, pre-tax deduction, previous employment. Aggregate exception, same as `W-26.2` | 1 — **exception requested**, see §14 |
| Externally testable behaviour | an employee declares Chapter VI-A investments against the shared catalogue, and what a previous employer paid and deducted this year; a line over the catalogue limit is refused with the limit | 1 |
| Frontend area | none | 1 |

Part 3 of the `W-32` split. These three are together because they are all **amount against a
catalogue code**, and because previous employment is the one section whose rows the
employee cannot edit once the officer has entered them (§13, decision 3).

---

## 1. Problem

- **Section 6A rows carry the catalogue on the row.** `EmployeeInvSection6A` stores
  `section6a_item_id` **and** `category`, `type`, `category_formatted`, `type_formatted`
  (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvSection6A.java:22-38`)
  — five columns that drift from the one master row they copy
- **The 80C/80D limits are checked in the browser.** `validateAmountAgainstMaxLimit`
  (`legacy/Payroll-Fend-react/src/pages/mainPages/userPortal/userInvestment/userInvestmentDeclaration.js:230`)
  reads the master's `maxLimit`; the server accepts any number
- **Pre-tax deductions have two amounts and four display strings.** `amount`,
  `investment_amount`, both `numeric(15,3)`, each with a `_formatted` twin
  (`EmployeeInvPreTaxDeduction.java:22-47`); which of the two the calculator reads is not
  stated
- **Previous employment is a typed list with a per-row edit flag.** `type` is one of
  `income`, `income_tax`, `professional_tax`, `employee_pf`, `leave_encashment`
  (`userInvestmentDeclaration.js:1108-1160`), stored as free text, with `declared_amount`
  beside `amount` and `can_edit_in_portal` (`EmployeeInvPrevEmployment.java:22-38`)
- **The master lives in the tenant database.** `Section6AItemMaster` is one table per
  org (`EmployeeInvestmentDeclarationServiceImpl.java:149-154`); `W-09` already moved it to
  `reference.section6a_item_master`, shared, with `max_limit numeric(19,4)`,
  `is_allowed_in_new_regime` and `is_active` (`V004__reference_tax_masters.sql:165-184`)

## 2. Scope

**In scope**

- `payroll.employee_inv_section6a` — one row per investment declared: a catalogue item, a
  short description ("LIC policy 4471"), an amount; several rows per item allowed. The
  catalogue is `reference.section6a_item_master` by `section6a_item_id`
- `payroll.employee_inv_pre_tax_deduction` — one row per kind: `EMPLOYEE_PF`, `VPF`,
  `PROFESSIONAL_TAX`, `NPS_EMPLOYEE` (§13, decision 2)
- `payroll.employee_inv_prev_employment` — one row per kind from the previous employer this
  year: `INCOME`, `INCOME_TAX_DEDUCTED`, `PROFESSIONAL_TAX`, `EMPLOYEE_PF`, `LEAVE_ENCASHMENT`
- Three `PUT`s that replace a section whole, one read of the three, and the catalogue read
  the screen needs (`GET .../section6a-items?fy=&regime=`)
- Server-side rules: item active; item allowed in the header's regime; the sum per item
  `<=` its `max_limit`; **the sum across a `category_group_code` `<=` the group's cap**

**Out of scope**

- Applying the deductions to income — `W-33`
- Proofs — `W-34`
- Editing the catalogue — `reference` changes by release only (`D-08`)
- Screens — `W-25`, `W-47`

## 3. Flow

```
[employee] --> GET /me/tax-declaration/{fy}/section6a-items --> the active items allowed in the header's regime, with max_limit and group

[employee] --> PUT /me/tax-declaration/{fy}/section6a          --> payroll.tax_declaration.declare_own
   --> TaxDeclarationService.require(currentEmployee, fy); editable(id) else 409
   --> each row: item exists, is_active, allowed in regime; per-item sums <= max_limit; per-group sums <= group cap
   --> replace rows for (tenant, declaration) --> [employee_inv_section6a]

[employee] --> PUT .../pre-tax-deductions   --> [employee_inv_pre_tax_deduction]
[employee] --> PUT .../previous-employment  --> [employee_inv_prev_employment]  (409 OFFICER_ENTERED when any row is officer-entered)
[officer]  --> same three under payroll.tax_declaration.manage, ignoreWindow; previous-employment rows stamped entered_by = OFFICER
[anyone with read] --> GET .../deductions --> the three sections
W-33 --> reads the three tables; W-34 reads section6a for the proof list
```

## 4. Backend changes

Under `code/backend/payroll/src/main/java/com/infinevo/payroll/taxdeclaration/deductions/`.

| Layer | File | Change |
|---|---|---|
| Entity | `EmployeeInvSection6A`, `EmployeeInvPreTaxDeduction`, `EmployeeInvPrevEmployment` | new, `@Table(schema = "payroll")`, `UUID` ids, `BigDecimal(19,4)` |
| Enumeration | `PreTaxDeductionKind`, `PrevEmploymentKind`, `EnteredBy { EMPLOYEE, OFFICER }` | new |
| Repository | one per entity | new; every finder takes `tenantId` |
| Reference read | `Section6AItemReader` | new, read-only over `reference.section6a_item_master`; `active(regime)`, `require(id)`, `groupCap(groupCode, fy)` |
| Service / ServiceImpl | `DeductionDeclarationService`, `…Impl` | new — `items(fy, regime)`, `read(declarationId)`, `replaceSection6A`, `replacePreTax`, `replacePrevEmployment` |
| Controller | `MyDeductionDeclarationController`, `DeductionDeclarationController` | new |
| DTO | `Section6ALineRequest`, `PreTaxDeductionRequest`, `PrevEmploymentRequest`, `DeductionDeclarationResponse`, `Section6AItemResponse` | new; envelope as `W-32.1` |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me/tax-declaration/{fy}/section6a-items` | — | items[] of `{id, section_code, category, name, max_limit, category_group_code, is_80c, is_80d}` filtered to `is_active` and the header's regime | `payroll.tax_declaration.read_own` |
| GET | `/api/v1/me/tax-declaration/{fy}/deductions` | — | `section6a[]` (with the item's code and name joined for display), `pre_tax_deductions[]`, `previous_employment[]`, `entered_by` | same |
| PUT | `/api/v1/me/tax-declaration/{fy}/section6a` | rows[] of `{section6a_item_id, description, amount}` | `200`; `400` naming the row and the limit; `409 NOT_EDITABLE` | `payroll.tax_declaration.declare_own` |
| PUT | `/api/v1/me/tax-declaration/{fy}/pre-tax-deductions` | rows[] of `{kind, amount}` | `200` | same |
| PUT | `/api/v1/me/tax-declaration/{fy}/previous-employment` | rows[] of `{kind, amount, employer_name, employer_tan}` | `200`; `409 OFFICER_ENTERED` | same |
| GET / PUT | `/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/…` | as above | window ignored; previous employment rows written as `OFFICER` | `payroll.tax_declaration.read` / `.manage` |

Validation, all `400` naming the row index: item id exists and `is_active`; item allowed
in the regime (`is_allowed_in_new_regime` when the header is `NEW`); `amount >= 0`;
`description` present; `Σ amount` per item `<= max_limit` when the item has one;
`Σ amount` per `category_group_code` `<= groupCap(group)`; one row per `kind` in the other
two sections; `employer_tan`, when present, matches `^[A-Z]{4}[0-9]{5}[A-Z]$`.

The seed is one row per **section**, not per instrument: `80C` is one item covering LIC,
PPF, ELSS and tuition together (`V005__reference_tax_seed.sql:200`), which is why a row
carries a `description` and several rows per item are allowed. `80C`, `80CCC` and
`80CCD(1)` share `category_group_code = '80C_GROUP'`, each with `max_limit` 1,50,000
(`V005:200-202`); `groupCap(group)` is the `max_limit` of the group's members, which the
seed keeps equal — the reader asserts that and fails loudly if a release breaks it.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V077__employee_inv_section6a.sql` | `payroll.employee_inv_section6a` | yes | additive |
| `payroll/V078__employee_inv_pre_tax_deduction.sql` | `payroll.employee_inv_pre_tax_deduction` | yes | additive |
| `payroll/V079__employee_inv_prev_employment.sql` | `payroll.employee_inv_prev_employment` | yes | additive |

Every table: `id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`declaration_id uuid NOT NULL REFERENCES payroll.employee_investment_declaration ON DELETE CASCADE`
· four audit columns. Then:

| Table | From | Columns |
|---|---|---|
| `employee_inv_section6a` | `EmployeeInvSection6A.java:22-32` | `section6a_item_id uuid NOT NULL REFERENCES reference.section6a_item_master` · `description varchar(150) NOT NULL` · `amount numeric(19,4) NOT NULL CHECK (amount >= 0)` |
| `employee_inv_pre_tax_deduction` | `EmployeeInvPreTaxDeduction.java:22-44` | `kind varchar(24) NOT NULL CHECK (kind IN ('EMPLOYEE_PF','VPF','PROFESSIONAL_TAX','NPS_EMPLOYEE'))` · `amount numeric(19,4) NOT NULL CHECK (amount >= 0)` |
| `employee_inv_prev_employment` | `EmployeeInvPrevEmployment.java:22-38` | `kind varchar(24) NOT NULL CHECK (kind IN ('INCOME','INCOME_TAX_DEDUCTED','PROFESSIONAL_TAX','EMPLOYEE_PF','LEAVE_ENCASHMENT'))` · `amount numeric(19,4) NOT NULL CHECK (amount >= 0)` · `employer_name varchar(150)` · `employer_tan varchar(10)` · `entered_by varchar(8) NOT NULL CHECK (entered_by IN ('EMPLOYEE','OFFICER'))` |

**Deliberately absent:** `category`, `type`, `*_formatted` on section 6A (`:25-38`) — the
master row has them; `code_string`, `investment_amount`, four `_formatted` on pre-tax
(`:22-47`) — one amount, display is the client's; `declared_amount`, `can_edit_in_portal`,
`type_formatted` on previous employment (`:28-38`) — `entered_by` is the rule;
`item_id_external` everywhere.

- [x] `tenant_id`, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `idx_employee_inv_section6a_tenant_declaration (tenant_id, declaration_id)` · `uk_employee_inv_pre_tax_deduction_tenant_declaration_kind (tenant_id, declaration_id, kind)` · `uk_employee_inv_prev_employment_tenant_declaration_kind (tenant_id, declaration_id, kind)` · `idx_employee_inv_section6a_tenant_item (tenant_id, section6a_item_id)` — the FK index
- [x] Money columns `numeric(19,4)`; nothing floating
- [x] Expand / contract — three new tables

RLS and `tenant_isolation` in the exact `CASE` form on all three. The cross-schema key to
`reference.section6a_item_master` is fine — `reference` is shared by design (`D-08`).

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../taxdeclaration/deductions/DeductionRulesTest.java` | inactive item refused; item not allowed in `NEW` refused when the header is `NEW`, accepted when `OLD`; two `80C` rows summing over 1,50,000 refused with the limit in the message; `80C` 1,00,000 plus `80CCC` 1,00,000 refused on the group; missing description refused; second row of one `kind` refused; TAN format; employee write refused when any previous-employment row is `OFFICER` |
| Integration | `payroll/.../taxdeclaration/deductions/DeductionDeclarationIT.java` | **the acceptance test**: `DRAFT` header, `OLD` regime; `GET …/section6a-items` lists active items with `max_limit`; `PUT` two `80C` rows and one `80D` under the limits ⇒ `200`; the group over ⇒ `400` naming it; `PUT` pre-tax and previous employment ⇒ `200`; `GET …/deductions` returns all three with item code and name joined; header `SUBMITTED` ⇒ `409` on all three; officer `PUT` previous employment ⇒ rows `OFFICER`, employee `PUT` ⇒ `409 OFFICER_ENTERED` |
| Integration | `payroll/.../taxdeclaration/deductions/DeductionRlsIT.java` | as `app_user`, tenant A cannot read or replace tenant B's rows on any of the three tables |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relname, relrowsecurity FROM pg_class
    WHERE relname IN ('employee_inv_section6a','employee_inv_pre_tax_deduction','employee_inv_prev_employment')
      AND relnamespace='payroll'::regnamespace ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT conname FROM pg_constraint
    WHERE conrelid='payroll.employee_inv_section6a'::regclass AND contype='f';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT section_code, max_limit FROM reference.section6a_item_master
    WHERE category_group_code = '80C_GROUP' ORDER BY 1;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | three rows, all `t` |
| Foreign keys on section 6A | three: tenant, declaration, `section6a_item_master` |
| 80C group in the seed | three rows, `80C`, `80CCC`, `80CCD(1)`, all `150000.0000` |
| Suite | green, no skips; `DeductionDeclarationIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The catalogue is copied onto the row again | **medium** — the legacy DTO shape invites it | the entity has one FK and one amount; the read joins for display |
| The 80C cap is applied per row, not per item or per group | medium | `DeductionRulesTest` uses two rows each under the limit whose sum crosses it, and two items whose sum crosses the group |
| Regime filter forgotten, so a `NEW`-regime header declares an `OLD`-only item | medium | asserted both ways in the rules test |
| Previous employment editable by the employee after the officer enters it | low | `entered_by` and the `409`; asserted in the IT |

## 10. Rollback

Nothing is deployed. All three scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | three tables |
| Flyway only, `ddl-auto` nowhere | three scripts, one table each |
| `Money`/`BigDecimal` for money | three `amount` columns `numeric(19,4)`; group sums through `Money` |
| Index on `tenant_id` plus lookup columns | four indexes, `tenant_id` leading |
| Expand / contract | new tables only |
| No module references another module | `payroll` → `core` (via `W-32.1`) and `shared`; `reference` read directly |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for these tables** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** |
| DEBT-027 computed in the browser | **Fixed** — limit and group-cap checks move to the server |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Copy the catalogue's category and name onto the row? | **No.** One FK to `reference.section6a_item_master` plus the employee's own `description`; the read joins. A renamed item renames everywhere |
| 2 | What are "pre-tax deductions"? | **Amounts the employee pays before tax that payroll does not already know**: VPF and employee NPS entered here; employee PF and professional tax are also allowed so a mid-year joiner can declare what the previous structure deducted. `W-33` prefers the pay-run figure where one exists (`W-31.4`) and falls back to this row — that precedence is `W-33`'s to write |
| 3 | Who enters previous employment? | **Either**, but the officer's entry wins: rows stamped `OFFICER` lock the section for the employee. Legacy's per-row `can_edit_in_portal` was the same idea, set by nothing |
| 4 | Enforce `max_limit` here? | **Yes** — per item and per group. It is a declaration ceiling, not the tax computation; `W-33` still reads the reference rule when it computes |

## 14. Open for the founder

| # | Question | Proposed |
|---|---|---|
| 1 | The size-cap exception: three scripts, one table each | **Grant** — same shape as `W-31.2` |
