# Feature: Income tax declaration — window, submission and revision

| Field | Value |
|---|---|
| **Feature ID** | `W-32.1` · from ticket #39 (`W-32`) · `PAY-09` part 1 of 4 |
| **Promoted to** | `docs/target-state/features/W-32-1-tax-declaration-window.md` on the developer's `dev-<name>` branch — **`W-32-1` with hyphens**, never `W-32.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for these tables), DEBT-018 (honoured), DEBT-021 (removed — no scheduler), DEBT-022 (fixed), DEBT-026 (not ported), DEBT-027 (honoured) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-13.4` — `EmployeeService.currentEmployee()` for the `/me` paths. `core.employee` (`W-13.1`) and `reference.action` (`W-11.3`) are on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V070` action codes (rows, no table) + `V071` window table + `V072` declaration header — same shape as `W-27.2`'s `V052`–`V053` plus one | 1 — **exception requested**, see §14 |
| Externally testable behaviour | an employee opens a declaration for the year, submits it while the window is open, reopens and resubmits; after the window closes nothing moves | 1 |
| Frontend area | none — `W-47` (settings), `W-25` (the `/me` panel) | 1 |

`W-32` was split on 2026-09-25 (`10-scoping.md:194-198`): this part is the window and the
declaration's lifecycle; `.2` house rent, home loan, let-out property; `.3` section 6A,
pre-tax deductions, previous employment; `.4` other income and the tax summary. **Every
section table is a child of the header built here**, so `.2`–`.4` wait on this one and
then run in any order.

---

## 1. Problem

The frozen Payroll backend has the lifecycle in three places and owns it in none of them.

- **Submission belongs to another feature.** Nothing under `/api/employee-it-declarations`
  submits; the declaration becomes `SUBMITTED` as a side effect of a proof-of-investment
  upload — `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/employeeitdeclaration/EmployeeProofOfInvestmentServiceImpl.java:1275-1279`
  — and returns to `DRAFT` when the proof is withdrawn (`:1863-1870`)
- **The window is one deadline stored as text.** `IncomeTaxDeclaration.lastDateForItDeclaration`
  is a `String` (`entity/claimsanddeclarations/IncomeTaxDeclaration.java:19`); a nightly cron
  parses it and flips a boolean (`scheduler/ITDeclarationAutoLockScheduler.java:24-48`), on every
  replica (DEBT-021). There is no opening date and no financial year on the row
- **The lock is advisory.** `updateDeclaration` refuses `SUBMITTED` and `APPROVED`
  (`serviceimpl/employeeitdeclaration/EmployeeInvestmentDeclarationServiceImpl.java:314-320`)
  but then only *records* the org lock on the row (`:326`) — a closed window does not stop a
  write
- **Status is free text**, `"DRAFT"`, `"SUBMITTED"`, `"NOT_CREATED"`, with a parallel
  `status_formatted` display column (`entity/EmployeeITDeclaration/EmployeeInvestmentDeclaration.java:73-77`)
- **The financial year is hard-coded** as April to March by string arithmetic
  (`EmployeeInvestmentDeclarationServiceImpl.java:245-246`) and stored four times over as
  `varchar(7)` (`EmployeeInvestmentDeclaration.java:34-44`)
- **Two 2,600-line screens** carry the same form, one for the admin and one for the employee
  (`legacy/Payroll-Fend-react/src/pages/mainPages/employee/adminInvestmentDeclaration.js`,
  `.../userPortal/userInvestment/userInvestmentDeclaration.js`; DEBT-026)

`09-build-order.md:225`: *"Done when: an employee completes a full declaration. Watch: the
largest single area in the platform. Break it into the ten sections and build them
separately."*

## 2. Scope

**In scope**

- `payroll.income_tax_declaration` — one row per tenant **per financial year**: the window's
  two dates, a manual lock, the default regime, whether the employee may change regime, and
  the PAN-for-rent rule
- `payroll.employee_investment_declaration` — one row per employee per financial year: the
  regime chosen, the three "do you have…" flags, status, and the submit and lock stamps
- The lifecycle: create-or-read, save (regime and flags), **submit**, **reopen**, officer lock
  and unlock per employee
- `TaxDeclarationService.editable(declarationId)` — the one rule `.2`–`.4` call before any
  section write
- `FinancialYear` value type in `payroll`: `2025-2026` parses, formats, and gives
  `start()`/`end()` — the April to March rule written once
- Four action codes: `payroll.tax_declaration.read`, `read_own`, `declare_own`, `manage`

**Out of scope**

- The section tables — `.2`, `.3`, `.4`
- Computing tax from the declaration — `W-33`
- Proof of investment and its verification — `W-34`. It links to a `SUBMITTED` declaration;
  it never changes this table's status (§13, decision 1)
- Lock and release mails — the window row keeps the two flags; sending is `W-20.1`/`W-20.2`
- A per-employee auto-lock job — none (§13, decision 3)
- Screens — `W-47` (window), `W-25` (the `/me` panel)

## 3. Flow

```
[payroll officer] --> PUT /payroll/tax-declaration/settings/{fy} --> payroll.settings.manage --> [payroll.income_tax_declaration]

[employee] --> GET /me/tax-declaration/{fy} --> payroll.tax_declaration.read_own
   --> EmployeeService.currentEmployee() (core, W-13.4)
   --> find or create the header as DRAFT with the window's default regime --> [payroll.employee_investment_declaration]
   --> response carries window_open, editable, the flags, regime, status

[employee] --> PUT /me/tax-declaration/{fy}         (regime, flags)  --> editable() else 409
[employee] --> POST /me/tax-declaration/{fy}/submit                  --> DRAFT -> SUBMITTED, submitted_at
[employee] --> POST /me/tax-declaration/{fy}/reopen                  --> SUBMITTED -> DRAFT while the window is open

[officer]  --> same four on /payroll/employees/{id}/tax-declaration/{fy} under payroll.tax_declaration.manage — window ignored
[officer]  --> POST .../lock | .../unlock                             --> is_locked on the header

W-32.2/.3/.4 --> TaxDeclarationService.editable(id) before every section write
W-33         --> reads the header for regime and flags; W-34 links to a SUBMITTED header
```

`editable(id)` is true when the header is `DRAFT`, `header.is_locked` is false, and the
window for its financial year is open: `window.is_locked` false and
`window_opens_on <= today <= window_closes_on`. The officer path passes `ignoreWindow`,
which skips the window test only — a locked header stays locked until `unlock`.

## 4. Backend changes

New, under `code/backend/payroll/src/main/java/com/infinevo/payroll/taxdeclaration/`.

| Layer | File | Change |
|---|---|---|
| Value type | `FinancialYear.java` | new — `parse("2025-2026")`, `of(LocalDate)`, `start()` = 1 April, `end()` = 31 March, `label()`; refuses a non-consecutive pair |
| Entity | `IncomeTaxDeclarationWindow.java` | new, `@Table(schema = "payroll", name = "income_tax_declaration")` |
| Entity | `EmployeeInvestmentDeclaration.java` | new, `@Table(schema = "payroll")`, `status` as `enum DeclarationStatus { DRAFT, SUBMITTED }` |
| Repository | `IncomeTaxDeclarationWindowRepository`, `EmployeeInvestmentDeclarationRepository` | new; every finder takes `tenantId`: `findByTenantIdAndFinancialYear`, `findByTenantIdAndEmployeeIdAndFinancialYear` |
| Service / ServiceImpl | `TaxDeclarationWindowService`, `…Impl` | new — `get(fy)`, `upsert(fy, …)`, `isOpen(fy, today)` |
| Service / ServiceImpl | `TaxDeclarationService`, `…Impl` | new — `readOwn(fy)`, `saveOwn`, `submitOwn`, `reopenOwn`; `read(employeeId, fy)`, `save`, `submit`, `reopen`, `lock`, `unlock`; `editable(id)` and `require(id)` for `.2`–`.4` |
| Controller | `TaxDeclarationSettingsController`, `MyTaxDeclarationController`, `TaxDeclarationController` | new |
| DTO | `TaxDeclarationWindowRequest/Response`, `TaxDeclarationRequest`, `TaxDeclarationResponse` | new; `status` / `message` / `data` envelope |
| Exception | `DeclarationNotEditableException` → `409` with a reason code | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/payroll/tax-declaration/settings/{fy}` | — | the window row, or the defaults with `exists: false` | `payroll.settings.manage` |
| PUT | `/api/v1/payroll/tax-declaration/settings/{fy}` | window_opens_on, window_closes_on, is_locked, default_tax_regime, can_change_tax_regime, pan_required_for_rent_over_threshold, notify_on_lock, notify_on_release | `200` upsert | `payroll.settings.manage` |
| GET | `/api/v1/me/tax-declaration/{fy}` | — | header, created as `DRAFT` on first read; `window_open`, `editable` | `payroll.tax_declaration.read_own` |
| PUT | `/api/v1/me/tax-declaration/{fy}` | tax_regime, is_staying_in_rented_house, is_repaying_self_occupied_loan, has_let_out_property | `200`; `409 NOT_EDITABLE` | `payroll.tax_declaration.declare_own` |
| POST | `/api/v1/me/tax-declaration/{fy}/submit` | — | `200` `SUBMITTED`; `409 NOT_EDITABLE`; `409 ALREADY_SUBMITTED` | `payroll.tax_declaration.declare_own` |
| POST | `/api/v1/me/tax-declaration/{fy}/reopen` | — | `200` `DRAFT`; `409 WINDOW_CLOSED`; `409 LOCKED` | `payroll.tax_declaration.declare_own` |
| GET | `/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}` | — | as the `/me` read | `payroll.tax_declaration.read` |
| PUT / POST | `/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}` and `/submit`, `/reopen`, `/lock`, `/unlock` | as above | window ignored; `lock` and `unlock` flip `is_locked` and stamp `locked_at` | `payroll.tax_declaration.manage` |

`{fy}` is `2025-2026`; anything else is `400`. `tax_regime` is `OLD` or `NEW`; a change is
refused with `409 REGIME_CHANGE_NOT_ALLOWED` when the window's `can_change_tax_regime` is
false and the header already has a regime. Validation, all `400`: `window_closes_on >=
window_opens_on`; both inside the financial year.

The `/me` paths follow `W-13.4` §2 and `W-25` §4: the employee is
`EmployeeService.currentEmployee()`, never a path id; no linked employee is `403`
(`W-13-4-employee-login-link.md:96`).

**Vocabulary.** `DRAFT` and `SUBMITTED` only. Legacy's `NOT_CREATED` was a stub the read
returned before a row existed (`EmployeeInvestmentDeclarationServiceImpl.java:124`); here the
first read creates the row. `APPROVED` (`:315`) is `W-34`'s state on the proof, not this
table's.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `reference/V070__tax_declaration_actions.sql` | rows in `reference.action`, `core.role_action` — no table | n/a | additive |
| `payroll/V071__income_tax_declaration.sql` | `payroll.income_tax_declaration` | yes | additive |
| `payroll/V072__employee_investment_declaration.sql` | `payroll.employee_investment_declaration` | yes | additive |

`V070`–`V072` extend the payroll lane's block past `W-31.3`'s `V069` — tracker lane row
updated 2026-09-25. `W-32` as a whole holds `V070`–`V081`.

**`V070`** — four rows in the `V020__action.sql` style, granted as `V025:137-148` grants,
to every existing tenant's system roles:

| Code | Name | Granted to |
|---|---|---|
| `payroll.tax_declaration.read` | View tax declarations | `payroll-officer` |
| `payroll.tax_declaration.manage` | Edit, submit, lock any tax declaration | `payroll-officer` |
| `payroll.tax_declaration.read_own` | View own tax declaration | `employee` |
| `payroll.tax_declaration.declare_own` | Declare own tax savings | `employee` |

**`income_tax_declaration`**, from `IncomeTaxDeclaration.java:14-36` with the fixes below:
`id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` · `financial_year varchar(9) NOT NULL`
· `window_opens_on date NOT NULL` · `window_closes_on date NOT NULL` ·
`is_locked boolean NOT NULL DEFAULT false` · `default_tax_regime varchar(3) NOT NULL DEFAULT 'NEW'`
`CHECK (default_tax_regime IN ('OLD','NEW'))` · `can_change_tax_regime boolean NOT NULL DEFAULT true`
· `pan_required_for_rent_over_threshold boolean NOT NULL DEFAULT true` ·
`notify_on_lock boolean NOT NULL DEFAULT false` · `notify_on_release boolean NOT NULL DEFAULT false`
· four audit columns as `V010__employee.sql:19-22`.

| Legacy (`IncomeTaxDeclaration.java`) | Here |
|---|---|
| `lastDateForItDeclaration String` (`:19`) | `window_closes_on date`, with `window_opens_on` added — a window has two ends (as `W-27.1` §6) |
| `isItDeclarationLocked` (`:17`) | `is_locked` — the officer's manual lock; the date does the rest |
| `defaultTaxRegime` (`:36`), `canChangeTaxRegimeIt` (`:18`) | kept, `CHECK`ed |
| `isPanMandatoryForAnnualRentOverOneLakh` (`:26`) | `pan_required_for_rent_over_threshold`; the threshold itself is `reference.hra_rule_master.pan_mandatory_threshold` (`V004__reference_tax_masters.sql:78`), read by `.2` |
| `sendMailOn*` (`:20,22,24`) | the two `notify_*` flags |
| `canTdsExceedAnnualLimit`, `currentPayrun`, `isPayscheduleConfigured`, `isAnyReminderBeforeLockdateEnabled`, `reminders` (`:16,21,23,25,33`) | **not ported** — pay-run state on a settings row, and a reminder list `W-20.2`'s reminder rules replace |

**`employee_investment_declaration`**, from `EmployeeInvestmentDeclaration.java:14-91`:
`id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` · `financial_year varchar(9) NOT NULL` ·
`tax_regime varchar(3) NOT NULL CHECK (tax_regime IN ('OLD','NEW'))` ·
`status varchar(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED'))` ·
`is_staying_in_rented_house boolean NOT NULL DEFAULT false` ·
`is_repaying_self_occupied_loan boolean NOT NULL DEFAULT false` ·
`has_let_out_property boolean NOT NULL DEFAULT false` ·
`is_locked boolean NOT NULL DEFAULT false` · `submitted_at timestamptz` · `locked_at timestamptz` ·
four audit columns.

**Deliberately absent:** the four `*_tax_year_*` `varchar(7)` columns (`:34-44`) —
`FinancialYear` derives them; `*_formatted` columns (`:49,76`) — display is the client's;
`can_allow_edit` (`:61`) — derived by `editable()`, never stored; `message_types`,
`tax_plan_count` (`:79-83`) — UI state.

- [x] `tenant_id`, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `uk_income_tax_declaration_tenant_fy (tenant_id, financial_year)` · `uk_employee_investment_declaration_tenant_employee_fy (tenant_id, employee_id, financial_year)` · `idx_employee_investment_declaration_tenant_fy_status (tenant_id, financial_year, status)`
- [x] Money columns — none on these two tables
- [x] Expand / contract — two new tables, rows added to two existing ones

RLS and `tenant_isolation` in the exact `CASE` form — `migration/README.md` §row-level
security. A `CHECK` on `status`, unlike `core.employee` (`active-work.md`, "No database
`CHECK` on `status`").

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../taxdeclaration/FinancialYearTest.java` | `2025-2026` parses; `2025-2027` and `2025` refused; `of(2025-03-31)` is `2024-2025`, `of(2025-04-01)` is `2025-2026`; `start()`/`end()` |
| Unit | `payroll/.../taxdeclaration/TaxDeclarationRulesTest.java` | `editable`: false when `SUBMITTED`, when header locked, when window locked, before opens, after closes; true on both boundary days; `ignoreWindow` skips the date test only; regime change refused when the window forbids it; window closes-before-opens and outside-the-year refused |
| Integration | `payroll/.../taxdeclaration/TaxDeclarationLifecycleIT.java` | **the acceptance test**: officer sets the window; employee `GET /me/tax-declaration/2025-2026` creates a `DRAFT` with the default regime; `PUT` flags; `submit` ⇒ `SUBMITTED` with `submitted_at`; `PUT` ⇒ `409 NOT_EDITABLE`; `reopen` ⇒ `DRAFT`; window moved to the past ⇒ `reopen` `409 WINDOW_CLOSED`, `PUT` `409`; officer `PUT` still succeeds; officer `lock` ⇒ employee `reopen` `409 LOCKED` |
| Integration | `payroll/.../taxdeclaration/TaxDeclarationRlsIT.java` | as `app_user`, tenant A cannot read tenant B's window or header, nor create a header against tenant B's employee |
| Integration | `payroll/.../taxdeclaration/TaxDeclarationActionSeedIT.java` | the four codes exist; `payroll-officer` holds `read` and `manage`; `employee` holds the two `_own` codes, in a tenant provisioned before `V070` ran |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relname, relrowsecurity FROM pg_class
    WHERE oid IN ('payroll.income_tax_declaration'::regclass,'payroll.employee_investment_declaration'::regclass);"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT code FROM reference.action WHERE code LIKE 'payroll.tax_declaration.%' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT conname FROM pg_constraint
    WHERE conrelid='payroll.employee_investment_declaration'::regclass AND contype='c' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='income_tax_declaration'
      AND column_name IN ('window_opens_on','window_closes_on');"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | two rows, both `t` |
| Action codes | four rows: `declare_own`, `manage`, `read`, `read_own` |
| Check constraints | `status` and `tax_regime` |
| Window columns | both `date` |
| Suite | green, no skips; `TaxDeclarationLifecycleIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `.2`–`.4` write sections without calling `editable()` | **medium** — three tickets, three developers possible | each section spec names `TaxDeclarationService.editable(id)` in its flow and asserts the `409` in its IT |
| `W-34` flips `status` here, reproducing the legacy coupling | medium | decision 1; `W-34`'s spec links to a `SUBMITTED` header and gets `409 NOT_SUBMITTED` otherwise |
| The financial-year rule is written a second time in `W-33` | medium | `FinancialYear` is public in `payroll`; `W-33` reuses it |
| `/me` endpoints take an employee id from the path | low | none in the contract; `W-13.4` §2 |
| A `V070` seed only reaches tenants provisioned after it | medium — `V025` had the same trap | `TaxDeclarationActionSeedIT` provisions a tenant **before** the grant, as `V025:137-148` handles |

## 10. Rollback

Nothing is deployed. All three scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | both tables |
| Flyway only, `ddl-auto` nowhere | three scripts, two tables |
| `Money`/`BigDecimal` for money | no money columns here |
| Index on `tenant_id` plus lookup columns | three indexes, `tenant_id` leading |
| Expand / contract | new tables and new rows only |
| No module references another module | `payroll` → `core` (`EmployeeService.currentEmployee`, `PermissionService`) and `shared` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for these tables** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-021 scheduler on every replica | **Removed** — no scheduler; the window is a date comparison (decision 3) |
| DEBT-022 unscoped finders | **Fixed** — every finder takes `tenantId` |
| DEBT-026 duplicated screens | **Not ported** — one `/me` panel (`W-25`) and one officer screen (`W-47`) against one API |
| DEBT-027 computed in the browser | **Honoured** — `editable`, `window_open` and the financial year come from the server |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Who submits the declaration? | **The employee, here.** Legacy submits through the proof upload (`EmployeeProofOfInvestmentServiceImpl.java:1275-1279`), which made the declaration unusable without a proof. `W-34` links to a `SUBMITTED` header and never writes `status` |
| 2 | Is the window per year? | **Yes** — `(tenant_id, financial_year)`. Legacy holds one row per org with no year (`IncomeTaxDeclaration.java`), so last year's deadline blocked this year's declaration until someone edited it |
| 3 | Port the auto-lock job? | **No.** Closed is `today > window_closes_on`. The job existed to flip a flag the date already implies and to send a mail `W-20.2` reminder rules send |
| 4 | Where does the April–March rule live? | **`FinancialYear` in `payroll`**, one class. Reference tables already key on `financial_year varchar(10)` as `2025-2026` (`V004__reference_tax_masters.sql:73`); the same label is used here so `W-33` joins without conversion |
| 5 | May an employee reopen after submitting? | **Yes, while the window is open and the header is not locked.** Legacy allowed it through proof withdrawal (`:1863-1870`); making it explicit removes the detour |
| 6 | Where do the section tables hang? | **Off this header**, each with its own `tenant_id` and RLS, keyed `(tenant_id, declaration_id)`. `.2`–`.4` create them |

## 14. Open for the founder

| # | Question | Proposed |
|---|---|---|
| 1 | The size-cap exception: `V070` action rows beside two tables, `V071`–`V072` | **Grant.** Three scripts, one table each at most; same shape as `W-27.2` and `W-31.1` |
