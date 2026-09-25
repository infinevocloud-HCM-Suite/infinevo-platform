# Feature: Salary component catalogue

| Field | Value |
|---|---|
| **Feature ID** | `W-26.1` · from ticket #30 (`W-26`) · `PAY-02` |
| **Promoted to** | `docs/target-state/features/W-26-1-salary-component-catalogue.md` on the developer's `dev-<name>` branch — **`W-26-1` with hyphens**, never `W-26.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for these tables), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-018 (honoured), DEBT-022 (fixed), DEBT-029 (discounted) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | nothing — `W-13.1` (`7d0bab6`) and `W-11.3` (`3350cf2`) are on `main`. First real code in the `payroll` module |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | 4 scripts, one table each — aggregate exception, same as `W-14.1` | 1 — exception granted 2026-09-25 |
| Externally testable behaviour | a tenant defines the pay components it uses, and a component of one tenant is invisible to another | 1 |
| Frontend area | none | 1 |

Within cap. `W-26` was split on 2026-09-25: this is the catalogue, `W-26.2` is the
per-employee structure and its revisions.

---

## 1. Problem

Payroll already has an org-level catalogue of four component kinds — earnings, deductions,
benefits, reimbursements — and it is being ported, not invented. Three things are wrong with
it and must not be carried across.

- **Two amount fields and a flag on one row.** `Earning` holds `amount` (`:29`), `value`
  (`:32`) and `isAmountInPercentage` (`:37`) — `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/salarycomponents/Earning.java`. Nothing says which of `amount` and `value` is read
- **A money ceiling stored as text.** `Earning.maxLimit` is a `String` (`Earning.java:35`)
  while the same field on `Reimbursement` is a `BigDecimal` (`Reimbursement.java:24`)
- **Display strings persisted beside the data.** `amountFormatted`, `valueFormatted`,
  `earningTypeFormatted`, `statusFormatted`, `epfInclusionTypeFormatted` (`Earning.java:26,30,33,44,60`) — presentation stored in the database, so it drifts
- **No tenant isolation.** 57 repository methods are not org-scoped (DEBT-022), and the
  catalogue endpoints sit at `/api/earnings` with no version prefix (`EarningController.java:17`, DEBT-007)

`09-build-order.md:213` makes the money fix part of the port: *"the money-type fix is a
correctness change riding a port. Do not defer it."*

## 2. Scope

**In scope**

- `payroll.earning`, `payroll.deduction`, `payroll.benefit`, `payroll.reimbursement`
- CRUD for each, tenant-scoped, under `/api/v1/payroll/components/...`
- One way to express a default amount: `calculation_type` + `default_value` + `percentage_of`
- Activate and deactivate, soft delete

**Out of scope**

- Assigning a component to an employee — `W-26.2`
- Flexible benefit plan flags beyond the boolean already on the row — `W-27`
- Provident fund, state insurance and professional tax configuration — `W-31`
- A default component set for a new tenant — see §13, decision 2
- Screens — `W-47`

## 3. Flow

```
[payroll officer] --> [EarningController | DeductionController | BenefitController | ReimbursementController]
   --> TenantContext bound by W-08 --> permission payroll.structure.manage (W-11.2)
   --> [payroll.earning | deduction | benefit | reimbursement under RLS]
```

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/component/`.

| Layer | File | Change |
|---|---|---|
| Entity | `Earning.java`, `Deduction.java`, `Benefit.java`, `Reimbursement.java` | new, each `@Table(schema = "payroll")`, `UUID id`, `UUID tenantId` |
| Enumeration | `CalculationType.java` (`FLAT`, `PERCENTAGE`), `PercentageOf.java` (`CTC`, `BASIC`, `GROSS`) | new — ports `CalculationBasis.java:4-5` under the target name |
| Repository | four | new, every finder takes `tenantId` |
| Service / ServiceImpl | four pairs | new |
| Controller | four | new |
| DTO | `*Request.java`, `*Response.java` | new; `status` / `message` / `data` envelope (`CONVENTIONS.md` §3) |

**API contract** — shown for earnings; deductions, benefits and reimbursements are the same
six under `/deductions`, `/benefits`, `/reimbursements`.

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/payroll/components/earnings` | code, name, calculation_type, default_value, percentage_of, flags | `201` | `payroll.structure.manage` |
| GET | `/api/v1/payroll/components/earnings` | `?activeOnly=` | list | `payroll.structure.read` |
| GET | `/api/v1/payroll/components/earnings/{id}` | — | one | `payroll.structure.read` |
| PUT | `/api/v1/payroll/components/earnings/{id}` | same as POST | `200` | `payroll.structure.manage` |
| PUT | `/api/v1/payroll/components/earnings/{id}/active` | `{active: bool}` | `200` | `payroll.structure.manage` |
| DELETE | `/api/v1/payroll/components/earnings/{id}` | — | `204`, soft | `payroll.structure.manage` |

The permission codes exist already: `payroll.structure.read` and `payroll.structure.manage`
— `code/backend/migration/src/main/resources/db/migration/reference/V020__action.sql:111-112`.

Validation, all `400`: `code` unique within the tenant; `default_value` non-negative;
`percentage_of` required when `calculation_type = PERCENTAGE` and must be null when `FLAT`;
`max_limit` non-negative when present.

`DELETE` is a soft delete. `W-26.2` adds the refusal while a structure references the row;
until then nothing references it.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V042__earning.sql` | `payroll.earning` | yes | additive |
| `payroll/V043__deduction.sql` | `payroll.deduction` | yes | additive |
| `payroll/V044__benefit.sql` | `payroll.benefit` | yes | additive |
| `payroll/V045__reimbursement.sql` | `payroll.reimbursement` | yes | additive |

**`V042`–`V052` are reserved for the payroll lane**, decided 2026-09-25; `V046`–`V050` are
`W-26.2`'s. One table per script — `migration/README.md` §one table creation per script.

**Common to all four:** `id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`code varchar(32) NOT NULL` · `name varchar(128) NOT NULL` · `display_name varchar(128)` ·
`is_active boolean NOT NULL DEFAULT true` · `is_deleted boolean NOT NULL DEFAULT false` ·
four audit columns as `V010__employee.sql`.

**`earning`**, ported from `Earning.java:24-78` with the fixes above:
`earning_type varchar(32) NOT NULL` · `calculation_type varchar(16) NOT NULL` ·
`default_value numeric(19,4)` · `percentage_of varchar(16)` · `max_limit numeric(19,4)` ·
`earning_frequency varchar(16)` · `parent_earning_id uuid` (self FK, `:69`) ·
`is_pro_rata` · `is_included_in_ctc` · `is_included_in_salary_structure` · `is_taxable` ·
`is_variable` · `is_one_time` · `is_fbp_component` · `is_included_in_epf` ·
`epf_inclusion_type varchar(32)` · `is_included_in_esi` · `show_in_payslip` — all booleans
`NOT NULL DEFAULT false` except `show_in_payslip DEFAULT true`.

`default_value` is **one** column: an amount when `FLAT`, a percentage when `PERCENTAGE`.
That replaces `amount`, `value` and `isAmountInPercentage`. `formulaBasedOn` (`:73`) becomes
`percentage_of`.

**`deduction`**, from `Deduction.java:20-44`: `deduction_type varchar(32) NOT NULL` ·
`is_recurring boolean NOT NULL DEFAULT false` · `is_pre_tax boolean NOT NULL DEFAULT false` ·
`emi_type varchar(32)` · `perquisite_interest_rate numeric(7,4)` · `emi_interest_rate numeric(7,4)`
(both `String` at `:40,42`; rates, so `numeric`).

**`benefit`**, from `Benefit.java:17-34`: `benefit_plan varchar(64)` · `benefit_category varchar(32)` ·
`is_pre_tax` · `is_one_time` · `is_pro_rata` · `is_superannuation` · `is_included_in_ctc` ·
`is_included_in_salary_structure` · `allows_employer_contribution` · `allows_employee_contribution` ·
`tax_exempt_section varchar(16)` · `tax_exemption_sub_type varchar(32)`. `employeeCount` (`:22`)
is derived and not stored.

**`reimbursement`**, from `Reimbursement.java:19-33`: `reimbursement_type varchar(32) NOT NULL` ·
`max_limit numeric(19,4)` · `carry_forward_option varchar(32)` · `is_included_in_ctc` ·
`is_included_in_salary_structure` · `is_fbp_component` · `is_opt_in`.

- [x] `tenant_id` on all four, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `uk_<table>_tenant_code (tenant_id, code)` unique, `idx_<table>_tenant_deleted_active (tenant_id, is_deleted, is_active)` on each; `idx_earning_tenant_parent (tenant_id, parent_earning_id)`
- [x] Money columns `numeric(19,4)`; rates `numeric(7,4)`; nothing floating
- [x] Expand / contract — four new tables, no destructive step

RLS and `tenant_isolation` in the exact `CASE` form in each script — `migration/README.md`
§row-level security.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../component/ComponentValidationTest.java` | `PERCENTAGE` without `percentage_of` refused; `FLAT` with `percentage_of` refused; negative `default_value` and `max_limit` refused; duplicate code within a tenant refused, same code in another tenant allowed |
| Integration | `payroll/.../component/ComponentRlsIT.java` | as `app_user`, tenant A cannot read, update or deactivate tenant B's rows in any of the four tables |
| Integration | `payroll/.../component/ComponentCrudIT.java` | create, list with `activeOnly`, deactivate, soft delete; deleted rows absent from list; money round-trips at scale 4 |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`
(`code/backend/shared/src/test/java/com/infinevo/shared/test/`).

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in earning deduction benefit reimbursement; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='payroll.$t'::regclass;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT table_name, column_name, data_type FROM information_schema.columns
    WHERE table_schema='payroll' AND column_name IN ('default_value','max_limit') ORDER BY 1,2;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on all four | `t` four times |
| Money columns | every row `numeric`; none `double precision` |
| Suite | green, no skips; `check-done.mjs` money gate passes |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| All 84 fields of `Earning.java` are ported, including the `*Formatted` strings | **medium — a port invites copying** | §6 lists the columns; nothing else is added |
| `default_value` is read as an amount when it is a percentage | medium | `calculation_type` is `NOT NULL`; `ComponentValidationTest` covers both directions |
| A finder is written without `tenantId` (DEBT-022 repeats) | medium | RLS makes it return nothing rather than leak; `ComponentRlsIT` proves it |
| The `payroll` module reaches into `hrms` | low | `maven-enforcer` rejects the build; this ticket needs only `shared` |

## 10. Rollback

Nothing is deployed. All four scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | all four, each in its own script |
| Flyway only, `ddl-auto` nowhere | four scripts |
| `Money`/`BigDecimal` for money | `default_value`, `max_limit` are `numeric(19,4)`; entities use `BigDecimal(precision=19, scale=4)` |
| Index on `tenant_id` plus lookup columns | two per table, `tenant_id` leading |
| Expand / contract | new tables only |
| No module references another module | `payroll` uses `shared` only here |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for these tables** — `tenant_id` + RLS |
| DEBT-007 no `/api/v1` | **Fixed** — all paths versioned |
| DEBT-008 hand-built response maps | **Fixed** — one envelope |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** — every finder takes `tenantId`, RLS behind it |
| DEBT-029 dead add-earning screen | **Discounted** — frontend, `W-47` |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Random 10-digit business ids (`earningId`, `Earning.java:18`) or UUID? | **UUID `id`, plus a tenant-unique `code`.** Matches `core.employee`. The 10-digit id existed to hide the MySQL sequence; a UUID does that by itself |
| 2 | Seed a default set (Basic, HRA, …) for a new tenant? | **No seed here.** That is provisioning behaviour and belongs with the setup checklist, `W-24.1`. A tenant can define Basic in one call |
| 3 | Keep `status` as free text (`Earning.java:43`, `"active"`)? | **No — `is_active` boolean.** The only two values in use are active and inactive |
| 4 | Port `employee_variable_earning`? | **Not as a table.** A variable earning is an earning with `is_variable = true`. `02-data-model.md` §4 updated 2026-09-25 |
