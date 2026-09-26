# Feature: Income tax declaration — house rent, home loan, let-out property

| Field | Value |
|---|---|
| **Feature ID** | `W-32.2` · from ticket #40 (`W-32`) · `PAY-09` part 2 of 4 |
| **Promoted to** | `docs/target-state/features/W-32-2-tax-declaration-housing.md` on the developer's `dev-<name>` branch — **`W-32-2` with hyphens**, never `W-32.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for these tables), DEBT-018 (honoured), DEBT-022 (fixed), DEBT-027 (honoured) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-32.1` — the header these rows hang off, and `editable()`. `reference.hra_rule_master` (`W-09`, `V004`) is on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V073`–`V076`, one table each — house rent, home loan, let-out property, let-out property line. Aggregate exception, same as `W-26.2` | 1 — **exception requested**, see §14 |
| Externally testable behaviour | an employee declares the housing part of the year — rent paid, loan repaid, property let out — and reads it back; a closed window refuses the write | 1 |
| Frontend area | none | 1 |

Part 2 of the `W-32` split (`W-32-1-tax-declaration-window.md`, size cap). The three
sections are together because they are the three answers to the header's three flags
(`is_staying_in_rented_house`, `is_repaying_self_occupied_loan`, `has_let_out_property`).

---

## 1. Problem

The frozen tables are close to right and the rules around them are missing.

- **Rent months are text.** `from_month`, `to_month` are `varchar(7)` `"YYYY-MM"`
  (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/EmployeeITDeclaration/EmployeeInvHouseRent.java:22-26`);
  nothing checks they fall inside the year or that `from <= to`
- **The PAN rule is a flag with no threshold.** The org row says PAN is mandatory over
  one lakh (`entity/claimsanddeclarations/IncomeTaxDeclaration.java:26`); the number is in
  the field name, and no server code enforces it — the 2,600-line screens do
- **Let-out property is untyped rows.** Each property has a `List` of `(type, amount,
  lender)` lines whose `type` is a free string the screen sets to `"annual_rent"`,
  `"municipal_tax"` (`legacy/Payroll-Fend-react/src/pages/mainPages/userPortal/userInvestment/userInvestmentDeclaration.js:1025,1033`);
  `net_income_loss` on the parent (`EmployeeInvLetOutProperty.java:30-31`) is sent by the
  browser, not derived
- **`currency` per rent row** (`EmployeeInvHouseRent.java:43-44`), always `INR`
- **`item_id_external`** on every row (`:46-47`) — an id from an earlier system, unused

## 2. Scope

**In scope**

- `payroll.employee_inv_house_rent` — one row per rented period; several allowed (a move
  mid-year)
- `payroll.employee_inv_home_loan` — one row per loan on a self-occupied property
- `payroll.employee_inv_let_out_property` and `payroll.employee_inv_let_out_property_line` —
  one row per property, its lines typed `ANNUAL_RENT`, `MUNICIPAL_TAX`, `LOAN_INTEREST`
- Three `PUT`s that replace a section whole, and a read of the three together
- Server-side rules: months inside the year, no overlapping rent periods, landlord PAN when
  the year's rent exceeds the reference threshold and the window says so, PAN format
- `net_income_loss` **derived**: `annual_rent − municipal_tax − 30 % of the remainder −
  loan_interest`, with the 30 % from `reference.let_out_property_rule_master.standard_deduction_percent`

**Out of scope**

- The HRA exemption, the section 24(b) cap and the loss set-off — `W-33` reads these rows
  and the reference rules; nothing is capped here (§13, decision 2)
- Proof of rent or of the loan — `W-34`
- Screens — `W-25`, `W-47`

## 3. Flow

```
[employee] --> PUT /me/tax-declaration/{fy}/house-rent      --> payroll.tax_declaration.declare_own
   --> TaxDeclarationService.require(currentEmployee, fy)   (W-32.1: the header)
   --> TaxDeclarationService.editable(header.id) else 409 NOT_EDITABLE
   --> validate rows (months, overlap, PAN) --> replace rows for (tenant, declaration) --> [employee_inv_house_rent]
   --> set header.is_staying_in_rented_house = rows non-empty

same for /home-loan --> [employee_inv_home_loan], flag is_repaying_self_occupied_loan
same for /let-out-property --> [employee_inv_let_out_property] + [_line], flag has_let_out_property;
   net_income_loss computed per property from its lines and the reference rule for {fy}

[officer]  --> same three on /payroll/employees/{id}/tax-declaration/{fy}/... under payroll.tax_declaration.manage, ignoreWindow
[anyone with read] --> GET .../housing --> the three sections in one response
W-33 --> reads the three tables by (tenant, declaration_id); writes nothing here
```

## 4. Backend changes

Under `code/backend/payroll/src/main/java/com/infinevo/payroll/taxdeclaration/housing/`.

| Layer | File | Change |
|---|---|---|
| Entity | `EmployeeInvHouseRent`, `EmployeeInvHomeLoan`, `EmployeeInvLetOutProperty`, `EmployeeInvLetOutPropertyLine` | new, `@Table(schema = "payroll")`, `UUID` ids, `BigDecimal(precision = 19, scale = 4)` |
| Enumeration | `LetOutPropertyLineType { ANNUAL_RENT, MUNICIPAL_TAX, LOAN_INTEREST }` | new |
| Repository | one per entity | new; every finder takes `tenantId`; `findByTenantIdAndDeclarationId`, `deleteByTenantIdAndDeclarationId` |
| Service / ServiceImpl | `HousingDeclarationService`, `…Impl` | new — `read(declarationId)`, `replaceHouseRent`, `replaceHomeLoans`, `replaceLetOutProperties`; `netIncomeLoss(property, rule)` |
| Controller | `MyHousingDeclarationController`, `HousingDeclarationController` | new |
| DTO | `HouseRentRequest`, `HomeLoanRequest`, `LetOutPropertyRequest` (+ `LineRequest`), `HousingDeclarationResponse` | new; envelope as `W-32.1` |
| Reference read | `HraRuleReader`, `LetOutPropertyRuleReader` | new, read-only over `reference.hra_rule_master` and `reference.let_out_property_rule_master` for `{fy}` and the header's regime |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me/tax-declaration/{fy}/housing` | — | `house_rent[]`, `home_loans[]`, `let_out_properties[]` each with `lines[]` and `net_income_loss` | `payroll.tax_declaration.read_own` |
| PUT | `/api/v1/me/tax-declaration/{fy}/house-rent` | rows[] of `{from_month, to_month, address, landlord_name, landlord_pan, is_metro, amount_per_month}` | `200` replaced; `409 NOT_EDITABLE`; `400` on a rule | `payroll.tax_declaration.declare_own` |
| PUT | `/api/v1/me/tax-declaration/{fy}/home-loan` | rows[] of `{lender_name, lender_pan, principal_paid, interest_paid, is_first_time_buyer, loan_sanctioned_on}` | `200` | same |
| PUT | `/api/v1/me/tax-declaration/{fy}/let-out-property` | rows[] of `{property_name, address, lines[] of {line_type, amount, lender_name, lender_pan}}` | `200`; `net_income_loss` in the response | same |
| GET / PUT | `/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/…` | as above | window ignored | `payroll.tax_declaration.read` / `.manage` |

Validation, all `400` naming the row index: `from_month <= to_month`, both inside
`FinancialYear.parse(fy)`; rent periods do not overlap; every amount `>= 0`; PAN, when
present, matches `^[A-Z]{5}[0-9]{4}[A-Z]$`; landlord PAN **required** when the window's
`pan_required_for_rent_over_threshold` is true and `Σ amount_per_month × months` for the
year exceeds `hra_rule_master.pan_mandatory_threshold` (`V004__reference_tax_masters.sql:78`)
— the sum across all rent rows, not per row; one line per `line_type` per property.

`from_month` and `to_month` are sent as `YYYY-MM` and stored as the first day of the month.
`months` between them is inclusive.

`net_income_loss = annual_rent − municipal_tax − (annual_rent − municipal_tax) ×
standard_deduction_percent / 100 − loan_interest`, scale 4 `HALF_UP` through `Money`
(`shared/.../money/Money.java`), negative allowed — a loss is the common case. Stored on the
property row so `W-33` and the read never recompute it; recomputed on every replace.

The section `PUT`s set the header's matching flag from whether rows exist (§13,
decision 1). The header is not otherwise touched.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V073__employee_inv_house_rent.sql` | `payroll.employee_inv_house_rent` | yes | additive |
| `payroll/V074__employee_inv_home_loan.sql` | `payroll.employee_inv_home_loan` | yes | additive |
| `payroll/V075__employee_inv_let_out_property.sql` | `payroll.employee_inv_let_out_property` | yes | additive |
| `payroll/V076__employee_inv_let_out_property_line.sql` | `payroll.employee_inv_let_out_property_line` | yes | additive |

Every table: `id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`declaration_id uuid NOT NULL REFERENCES payroll.employee_investment_declaration ON DELETE CASCADE`
· four audit columns as `V010__employee.sql:19-22`. Then:

| Table | From | Columns |
|---|---|---|
| `employee_inv_house_rent` | `EmployeeInvHouseRent.java:22-44` | `from_month date NOT NULL` · `to_month date NOT NULL` · `address varchar(1000) NOT NULL` · `landlord_name varchar(150) NOT NULL` · `landlord_pan varchar(10)` · `is_metro boolean NOT NULL DEFAULT false` · `amount_per_month numeric(19,4) NOT NULL` · `CHECK (from_month <= to_month)` · `CHECK (amount_per_month >= 0)` |
| `employee_inv_home_loan` | `EmployeeInvHomeLoan.java:24-34` | `lender_name varchar(150) NOT NULL` · `lender_pan varchar(10)` · `principal_paid numeric(19,4) NOT NULL DEFAULT 0` · `interest_paid numeric(19,4) NOT NULL DEFAULT 0` · `is_first_time_buyer boolean NOT NULL DEFAULT false` · `loan_sanctioned_on date` — the last two feed `home_loan_rule_master.is_first_time_buyer`, `loan_sanction_from/to` (`V004:97-99`) in `W-33` |
| `employee_inv_let_out_property` | `EmployeeInvLetOutProperty.java:24-31` | `property_name varchar(150) NOT NULL` · `address varchar(1000)` · `net_income_loss numeric(19,4) NOT NULL DEFAULT 0` |
| `employee_inv_let_out_property_line` | `EmployeeInvLetOutPropertyDetail.java:22-32` | `property_id uuid NOT NULL REFERENCES payroll.employee_inv_let_out_property ON DELETE CASCADE` · `line_type varchar(16) NOT NULL CHECK (line_type IN ('ANNUAL_RENT','MUNICIPAL_TAX','LOAN_INTEREST'))` · `amount numeric(19,4) NOT NULL DEFAULT 0` · `lender_name varchar(150)` · `lender_pan varchar(10)` |

**Deliberately absent:** `currency` (`EmployeeInvHouseRent.java:43`) — `INR` everywhere;
`item_id_external` (every legacy row) — no earlier system to point at; `created_time`/
`updated_time` `LocalDateTime` — the four audit columns instead.

- [x] `tenant_id`, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `idx_<table>_tenant_declaration (tenant_id, declaration_id)` on all four; `uk_employee_inv_let_out_property_line_tenant_property_type (tenant_id, property_id, line_type)`; `idx_…_line_tenant_property (tenant_id, property_id)`
- [x] Money columns `numeric(19,4)`; nothing floating
- [x] Expand / contract — four new tables

RLS and `tenant_isolation` in the exact `CASE` form on all four. `tenant_id` is carried on
the line table too, not inherited through the property — every table outside `reference`
carries its own (`migration/README.md`).

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../taxdeclaration/housing/HousingRulesTest.java` | month outside the year refused; `from > to` refused; overlapping periods refused; PAN format; PAN required only when the sum crosses the threshold and the window flag is on; `netIncomeLoss` for rent 3,00,000, tax 20,000, interest 3,00,000 at 30 % = **−1,04,000.0000**; two lines of one type refused |
| Integration | `payroll/.../taxdeclaration/housing/HousingDeclarationIT.java` | **the acceptance test**: `DRAFT` header, window open; `PUT` two rent periods, one loan, one property with three lines; `GET …/housing` returns all with `net_income_loss` and the header's three flags true; `PUT` an empty rent list ⇒ flag false; header `SUBMITTED` ⇒ every `PUT` `409 NOT_EDITABLE`; officer `PUT` with the window closed succeeds |
| Integration | `payroll/.../taxdeclaration/housing/HousingRlsIT.java` | as `app_user`, tenant A cannot read or replace tenant B's rows through any of the four tables |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relname, relrowsecurity FROM pg_class WHERE relname LIKE 'employee_inv_%'
     AND relnamespace='payroll'::regnamespace ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT table_name, column_name, data_type FROM information_schema.columns
    WHERE table_schema='payroll' AND column_name IN ('from_month','to_month');"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) FROM information_schema.columns
    WHERE table_schema='payroll' AND data_type IN ('double precision','real');"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | four `employee_inv_*` rows, all `t` (plus any from `.3`/`.4` if merged first) |
| Month columns | both `date` |
| Floating-point columns in `payroll` | `0` |
| Suite | green, no skips; `HousingDeclarationIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The HRA exemption or the 24(b) cap gets computed here | **medium** — the reference rules are right there | decision 2; `HousingRulesTest` asserts a loan interest above the cap is stored uncapped |
| `net_income_loss` accepted from the client | medium — legacy does | the request DTO has no such field; the IT asserts the stored value against the formula |
| Section writes skip `editable()` | medium | `HousingDeclarationIT` asserts `409` on a `SUBMITTED` header for all three `PUT`s |
| Rent threshold read for the wrong year or regime | low | `HraRuleReader` takes `(fy, regime)`; the rules test uses two years with different thresholds |

## 10. Rollback

Nothing is deployed. All four scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | four tables |
| Flyway only, `ddl-auto` nowhere | four scripts, one table each |
| `Money`/`BigDecimal` for money | seven money columns `numeric(19,4)`; the loss through `Money` |
| Index on `tenant_id` plus lookup columns | six indexes, `tenant_id` leading |
| Expand / contract | new tables only |
| No module references another module | `payroll` → `core` (via `W-32.1`'s service) and `shared`; `reference` read directly, as `W-31.2` does |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for these tables** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** |
| DEBT-027 computed in the browser | **Fixed** — the PAN rule and the property loss move to the server |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Who sets the header's three flags? | **The section write**, from whether rows exist. Legacy lets the screen set the flag and the rows independently, so "staying in rented house" with no rent rows is a stored state. `W-32.1`'s `PUT` still accepts the flags for the case where the employee has not filled the section yet; the section write wins |
| 2 | Apply the exemption caps here? | **No.** This table records what the employee declared; `W-33` applies `hra_rule_master`, `home_loan_rule_master` and `let_out_property_rule_master` when it computes. Capping at entry would hide the declared figure from the proof reviewer (`W-34`) |
| 3 | One home loan or many? | **Many.** Legacy maps a `List` (`EmployeeInvestmentDeclaration.java:119`) though the screen shows one; a second lender is real |
| 4 | Derive `net_income_loss` or accept it? | **Derive**, with the reference percentage, and store it — the one figure the calculator and the reviewer both need, computed once |
| 5 | Loan sanction date and first-time-buyer flag | **Added.** `home_loan_rule_master` keys sections 80EE/80EEA on them (`V004:97-99`); without them `W-33` cannot pick the section |

## 14. Open for the founder

| # | Question | Proposed |
|---|---|---|
| 1 | The size-cap exception: four scripts, one table each | **Grant** — same aggregate shape as `W-26.2` and `W-31.2` |
