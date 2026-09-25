# Feature: Flexible benefit plan — employee declaration

| Field | Value |
|---|---|
| **Feature ID** | `W-27.2` · from ticket #31 (`W-27`) · `PAY-03` |
| **Promoted to** | `docs/target-state/features/W-27-2-fbp-declaration.md` on the developer's `dev-<name>` branch — **`W-27-2` with hyphens**, never `W-27.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-018 (honoured), DEBT-022 (fixed), DEBT-027 (honoured — nothing computed in a browser) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-27.1` — the window · `W-26.2` — the salary version the declaration sits inside · `W-13.4` — `EmployeeService.currentEmployee()`, the caller's own employee row (D-6) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V052` action codes (rows, no table) + `V053` one table — same shape as `W-11.3`'s `V025` | 1 — exception granted 2026-09-25 |
| Externally testable behaviour | an employee declares their split while the window is open, and the salary version in force shows it | 1 |
| Frontend area | none | 1 |

`W-27` was split on 2026-09-25: `W-27.1` is the plan, this is the declaration.

---

## 1. Problem

Today the declaration does not exist. What exists is an admin typing FBP amounts into a
salary structure that payroll then ignores.

- **No employee-side flow.** FBP rows are written only inside the admin's CTC create and
  update — `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/employee/CtcStructureServiceImpl.java:221-227`
  (create) and `:430-442` (update). The frontend tab is commented out
  (`legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/claimsAndDeclaration/itDeclaration.js:63`)
- **Nothing reads it.** `serviceimpl/payruns/` has zero references to `FbpComponent` or
  `fbp`; the rows never reach a payslip
- **Floating-point money.** `FbpComponent.amount`, `amountInPercentage`, `maxLimit` are all
  `Double` (`entity/employee/FbpComponent.java:20,23,26`)
- **A free-text key.** `componentCode` is a nullable `String` (`:15-16`) that references no
  catalogue row, so a typo silently makes a new component
- **Two amount fields again.** `amount` and `amountInPercentage` (`:20,23`), with no rule
  for which is read — the same defect `W-26.2` fixed on `EmployeeEarning`

`09-build-order.md:215`: *"Done when: a declaration flows into the structure. Watch: sits
inside the salary structure, not beside it."*

## 2. Scope

**In scope**

- `payroll.employee_fbp_component` — one row per FBP component per **salary version**, the
  employee's chosen annual amount
- The employee submits and re-submits while the window is open; reads their own declaration
- A payroll officer reads any employee's declaration and may set it outside the window
- The declaration carried forward when `W-26.2` creates a new version
- The version-in-force response (`W-26.2` §4, `GET .../salary?asOf=`) shows, on every FBP
  line, `declared_annual_amount` and `declared_monthly_amount`, plus a `fbp` summary block
- Three action codes: `payroll.fbp.read`, `payroll.fbp.read_own`, `payroll.fbp.declare_own`

**Out of scope**

- Paying the declared amounts and the unallocated remainder — `W-29`, through the same
  `versionInForce` read (`W-26.2` §13 decision 4). See §13, decision 3
- Tax treatment of each component — `W-33`
- Proof of spend for a benefit — `W-34`
- Approval of a declaration — none; legacy has none, and the window is the control
- Mails on submit — `W-20.1`
- Screens — `W-47` (admin), `W-25` (the `/me` panel, gated by `payroll.fbp.read_own`)

## 3. Flow

```
[employee] --> [FbpDeclarationController /me/fbp-declaration] --> permission payroll.fbp.declare_own
   --> EmployeeService.currentEmployee() (core, W-13.4) --> FbpPlanService.isWindowOpen(today) (W-27.1)
   --> EmployeeSalaryService.versionInForce(tenant, employee, today) (W-26.2)
   --> validate each line: component is an FBP line of that version; 0 <= annual <= line annual_amount
   --> replace the rows for (tenant, employee, ctc_structure_id) --> [payroll.employee_fbp_component]

[payroll officer] --> PUT /employees/{id}/fbp-declaration --> payroll.salary.manage --> same service, window ignored

W-26.2 revise --> FbpDeclarationService.carryForward(oldVersion, newVersion): copy rows, cap each at the new line
W-29 pay run  --> versionInForce(...) — the FBP lines carry the declared amounts. It never writes here
```

## 4. Backend changes

New, under `code/backend/payroll/src/main/java/com/infinevo/payroll/fbp/`, plus one
change inside `W-26.2`'s salary package.

| Layer | File | Change |
|---|---|---|
| Entity | `EmployeeFbpComponent.java` | new, `@Table(schema = "payroll")`, `UUID` ids, `BigDecimal(precision = 19, scale = 4)` |
| Repository | `EmployeeFbpComponentRepository.java` | new, every finder takes `tenantId`; `findByTenantIdAndCtcStructureId`, `deleteByTenantIdAndCtcStructureId` |
| Service / ServiceImpl | `FbpDeclarationService`, `FbpDeclarationServiceImpl` | new — `declareOwn`, `readOwn`, `read(employeeId)`, `set(employeeId, …)`, `carryForward(old, new)`, `summary(version)` |
| Controller | `FbpDeclarationController.java` | new |
| DTO | `FbpDeclarationRequest`, `FbpDeclarationLineRequest`, `FbpDeclarationResponse` | new; `status` / `message` / `data` envelope |
| Service (change) | `salary/EmployeeSalaryServiceImpl.java` (`W-26.2`) | `revise` calls `carryForward`; `versionInForce` and the `GET ?asOf=` response attach the declared amounts and the `fbp` summary |
| DTO (change) | `salary/SalaryVersionResponse.java` (`W-26.2`) | each earning and reimbursement line gains `is_fbp`, `declared_annual_amount`, `declared_monthly_amount`; the response gains `fbp: {pool_annual, declared_annual, unallocated_annual}` |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me/fbp-declaration` | — | the caller's declaration against the version in force, with `window_open` and the pool | `payroll.fbp.read_own` |
| PUT | `/api/v1/me/fbp-declaration` | lines[] of `{kind, component_id, annual_amount}` | `200` replaced; `409` `WINDOW_CLOSED` when `isWindowOpen(today)` is false; `404` when the caller has no salary version in force | `payroll.fbp.declare_own` |
| GET | `/api/v1/payroll/employees/{employeeId}/fbp-declaration?asOf=` | date, default today | that employee's declaration on the version in force on that date | `payroll.fbp.read` |
| PUT | `/api/v1/payroll/employees/{employeeId}/fbp-declaration` | same lines[] | `200`; window ignored | `payroll.salary.manage` |

`kind` is `EARNING` or `REIMBURSEMENT` — the two `W-26.1` catalogues that carry
`is_fbp_component` (`W-26-1-salary-component-catalogue.md` §6).

Validation, all `400` unless noted: every `(kind, component_id)` is a line of the version
in force whose catalogue row is flagged `is_fbp_component` (else `400` naming the id); no
duplicate line; `annual_amount >= 0`; `annual_amount <= ` that line's `annual_amount`
(`W-26.2` §6) — the line is the ceiling, the catalogue `max_limit` is not consulted (§13,
decision 2); a line of the version omitted from the request is stored as `0`.

The service derives `monthly_amount = annual_amount / 12` at scale 4, `HALF_UP`, through
`Money` (`shared/.../money/Money.java:26-36`). **The client never sends a monthly amount.**

`summary(version)`: `pool_annual` = sum of the version's FBP lines' `annual_amount`;
`declared_annual` = sum of this table's rows for the version; `unallocated_annual` = the
difference. All three at scale 2 in the response.

`carryForward(old, new)`: for each row on `old` whose `(kind, component_id)` is also an FBP
line on `new`, insert a row on `new` with `min(old.annual_amount, new line annual_amount)`;
lines new to the version start at `0`. Called by `EmployeeSalaryServiceImpl.revise` after the
new version is saved, in the same transaction.

The `/me` paths follow `W-13.4` §2 and `W-25` §4: the employee is
`EmployeeService.currentEmployee()`, never a path id; no linked employee is `403`
(`W-13-4-employee-login-link.md:96`).

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `reference/V052__fbp_actions.sql` | rows in `reference.action`, `core.role_action` — no table | n/a | additive |
| `payroll/V053__employee_fbp_component.sql` | `payroll.employee_fbp_component` | yes | additive |

`V052` is the last number of the payroll block; `V053` extends the block by one — the
tracker's lane row is updated 2026-09-25.

**`V052`** — three rows, in the `V020__action.sql` style:

| Code | Name | Granted to (`V025__catalogue_correction.sql:126-134` roles) |
|---|---|---|
| `payroll.fbp.read` | View FBP declarations | `payroll-officer` |
| `payroll.fbp.read_own` | View own FBP declaration | `employee` |
| `payroll.fbp.declare_own` | Declare own FBP | `employee` |

The `role_action` grants follow `V025:137-148` — every existing tenant's system roles, not
only future ones.

**`employee_fbp_component`**, from `FbpComponent.java:15-35` with the fixes above:
`id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`ctc_structure_id uuid NOT NULL REFERENCES payroll.ctc_structure` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` — denormalised from the version for
the own-read index ·
`earning_id uuid REFERENCES payroll.earning` · `reimbursement_id uuid REFERENCES payroll.reimbursement` ·
`annual_amount numeric(19,4) NOT NULL` · `monthly_amount numeric(19,4) NOT NULL` ·
`declared_at timestamptz NOT NULL` · `declared_by varchar(16) NOT NULL` (`EMPLOYEE` / `OFFICER` / `CARRIED`) ·
four audit columns as `V010__employee.sql:19-22`.

`CHECK ((earning_id IS NULL) <> (reimbursement_id IS NULL))` — exactly one of the two.
That replaces the free-text `componentCode` with two real foreign keys.

**Deliberately absent:** `enabled` (`FbpComponent.java:18`) — a `0` amount is "not taken";
`amount_in_percentage` (`:23`) — a declaration is an amount; `max_limit` (`:26`) — the
ceiling is the salary line, read at validation, not copied.

- [x] `tenant_id`, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `uk_employee_fbp_component_tenant_structure_earning (tenant_id, ctc_structure_id, earning_id)` unique where `earning_id IS NOT NULL` · the same for `reimbursement_id` · `idx_employee_fbp_component_tenant_employee (tenant_id, employee_id)`
- [x] Money columns `numeric(19,4)`; nothing floating
- [x] Expand / contract — one new table, rows added to two existing ones; `W-26.2`'s tables untouched

Cross-schema keys to `core.employee` are fine (`W-26.2` §6). RLS and `tenant_isolation` in
the exact `CASE` form — `migration/README.md` §row-level security.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../fbp/FbpDeclarationRulesTest.java` | line above the salary line refused with the ceiling in the message; non-FBP line refused; duplicate refused; omitted line stored as `0`; monthly is annual/12 at scale 4 `HALF_UP`; `carryForward` caps at the new line and zeroes new lines |
| Integration | `payroll/.../fbp/FbpDeclarationIT.java` | **the acceptance test**: plan window open, employee has a version with two FBP lines; `PUT /me/fbp-declaration` succeeds; `GET .../salary?asOf=today` shows `declared_annual_amount` on both lines and `unallocated_annual` = pool − declared; window closed ⇒ `409 WINDOW_CLOSED`, rows unchanged; officer `PUT` succeeds with the window closed |
| Integration | `payroll/.../fbp/FbpCarryForwardIT.java` | revise on a later date; the new version carries the declaration capped at its lines; the old version's rows are unchanged |
| Integration | `payroll/.../fbp/FbpDeclarationRlsIT.java` | as `app_user`, tenant A cannot read tenant B's rows, nor declare against tenant B's version |
| Integration | `payroll/.../fbp/FbpActionSeedIT.java` | the three codes exist in `reference.action`; `payroll-officer` holds `payroll.fbp.read`; `employee` holds the two `_own` codes, in a tenant provisioned before `V052` ran |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='payroll.employee_fbp_component'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT code FROM reference.action WHERE code LIKE 'payroll.fbp.%' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT conname FROM pg_constraint WHERE conrelid='payroll.employee_fbp_component'::regclass AND contype='c';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) FROM information_schema.columns
    WHERE table_schema='payroll' AND data_type IN ('double precision','real');"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Action codes | three rows: `declare_own`, `read`, `read_own` |
| Check constraint | one row, the exactly-one-of-two rule |
| Floating-point columns in `payroll` | `0` |
| Suite | green, no skips; `FbpDeclarationIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The declaration is built as a new salary version | **medium** — "flows into the structure" invites it | §13 decision 1: it is an overlay keyed to the version; `FbpCarryForwardIT` proves the old version is untouched |
| `W-26.2` is built without an FBP hook and `revise` is not reopened | medium | `carryForward` is named in §4 as a change to `EmployeeSalaryServiceImpl`; `FbpCarryForwardIT` fails without it |
| The pool is read from the catalogue `max_limit`, not the salary line | medium | decision 2; `FbpDeclarationRulesTest` uses a line below `max_limit` and asserts the line wins |
| `/me` endpoints take an employee id from the path | low | none exists in the contract; `W-13.4` §2 is the pattern |
| A `V052` seed only reaches tenants provisioned after it | medium — `V025` had the same trap | `FbpActionSeedIT` provisions a tenant **before** running the grant, as `V025:137-148` handles |

## 10. Rollback

Nothing is deployed. Both scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `payroll.employee_fbp_component` |
| Flyway only, `ddl-auto` nowhere | two scripts, one table |
| `Money`/`BigDecimal` for money | `annual_amount`, `monthly_amount` `numeric(19,4)`; the split through `Money` |
| Index on `tenant_id` plus lookup columns | three indexes, `tenant_id` leading |
| Expand / contract | new table and new rows only |
| No module references another module | `payroll` → `core` (`EmployeeService.currentEmployee`, `W-13.4`) and `shared` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for this table** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** — every finder takes `tenantId` |
| DEBT-027 split computed in the browser | **Honoured** — monthly and the pool are computed server-side; the client sends annual amounts only |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Does a declaration create a new salary version? | **No.** Versions are the officer's act (`W-26.2`); a declaration is the employee's overlay on the version in force, keyed by `ctc_structure_id`. "Inside the structure" is met by the version read carrying the declared amounts, which is what `W-29` consumes |
| 2 | What is the ceiling per component? | **The salary line's `annual_amount`** on the version in force. The officer sets the basket when building the structure; the catalogue `max_limit` is a hint for that officer, not a rule here — `W-26.1` §6 gives it no period, so it cannot be compared |
| 3 | What happens to the unallocated part? | **Not here.** The response reports `unallocated_annual`; `W-29` pays it as the taxable remainder of the line. That rule is named in `W-29`'s spec, not built twice |
| 4 | Port `enabled` and `amount_in_percentage`? | **No.** A `0` amount is "not taken"; a declaration is an amount, never a percentage |
| 5 | Who may declare outside the window? | **The officer**, under `payroll.salary.manage` — the legacy capability (`CtcStructureServiceImpl.java:430-442`), kept for joiners and corrections. The row records `declared_by = OFFICER` |

## 14. Founder decisions — 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | The size-cap exception: `V052` action rows beside `V053`'s table | **Granted.** Two scripts, one table, as `W-11.3` did with `V025` |
| 2 | Ceiling per component: the salary line or the catalogue `max_limit`? | **The salary line.** Confirms §13 decision 2 |
