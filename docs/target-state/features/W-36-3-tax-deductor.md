# Feature: Tax deductor details — the employer's TAN, PAN and signatory

| Field | Value |
|---|---|
| **Feature ID** | `W-36.3` · from ticket #48 (`W-36`) · `PAY-10` table `income_tax_detail` |
| **Promoted to** | `docs/target-state/features/W-36-3-tax-deductor.md` — **`W-36-3` with hyphens**, never `W-36.3`; `guard-edit` blocks the dotted form |
| **Owner** | sayeed |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-007, DEBT-008, DEBT-018 (honoured), DEBT-022 (fixed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | nothing. `core.employee` and `payroll.settings.manage` are on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V107` — one table, `payroll.tax_deductor` | 1 |
| Externally testable behaviour | an officer saves the tenant's TAN, PAN, TDS circle and signatory, and reads them back | 1 |
| Frontend area | none — the settings screen is a `W-47` ticket | 1 |

`W-36.3` as first tracked (the annual statement) was split on 2026-09-29 into three tickets:
`W-36.3` is this one, `W-36.4` is the statement, `W-36.5` is the Part A upload. The statement
cannot be issued without a TAN and a signatory, and no other ticket ports them.

---

## 1. Problem

Form 16 is issued by the deductor. It carries the employer's TAN and PAN and is signed by a
named person. The frozen Payroll backend keeps these on one row per organization. All
citations are `legacy/`, code being replaced.

- Table `incomeTaxDetails` holds `tanNumber`, `panNumber`, `tdsCircle`, `authorizedPersonName`,
  `authorizedPersonParent`, `authorizedPersonDesignation`, `depositSchedule` and `employeeId`
  (`Payroll-Bend-SBoot/.../entity/organization/IncomeTaxDetails.java:6-39`)
- `PUT` upserts the row for the organization, and `GET` returns an empty DTO when there is none
  (`.../serviceimpl/organization/IncomeTaxDetailsServiceImpl.java:26-47`, `:51-60`)
- TAN and PAN are only checked as ten alphanumerics (`.../dto/organization/IncomeTaxDetailsDTO.java:10-18`).
  That is weaker than the real formats. The TDS circle is `AAA/AA/000/00` (`:20-23`)
- The screen fixes the deposit schedule at `Monthly` (`Payroll-Fend-react/.../allSettingsPages/taxes/taxDetails.js:671-682`).
  The signatory is either an employee, whose id is stored, or a named outsider (`:69`, `:254-255`)
- The organization comes from a request header (`IncomeTaxDetailsController.java:27`, `:42`), so it is BUG-002 and DEBT-022
- No target ticket owns this table, even though `02-data-model.md:171` and `:348` list it under `PAY-10`

## 2. Scope

**In scope**

- `payroll.tax_deductor`: one row per tenant, upserted
- `PUT` and `GET` under `/api/v1/payroll/settings/tax-deductor`
- The real TAN, PAN and TDS-circle formats, checked on the server
- Signatory as an employee of the tenant, **or** a named person with no employee id

**Out of scope**

- Locking the details once a Form 16 is issued (legacy's screen note, `form16/index.js`). The
  statement is rendered on each request (`W-36.4`), so there is nothing to lock. Each change
  is audited (`@Audited`), and the history is the audit trail
- Deposit schedule. Legacy stores only `Monthly`, and nothing reads it. It is not ported
- Screens (`W-47`)

## 3. Flow

```
[payroll officer] --> PUT /api/v1/payroll/settings/tax-deductor     payroll.settings.manage
   --> TaxDeductorService.save(request)  validate → upsert on (tenant_id) → 200 with the row
[W-36.4 statement] --> TaxDeductorService.current()  → Optional<TaxDeductor>
```

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/taxdeductor/`.

| Layer | File | Change |
|---|---|---|
| Entity | `TaxDeductor.java` | `@Table(name = "tax_deductor", schema = "payroll")`, `@Audited` |
| Repository | `TaxDeductorRepository.java` | `findByTenantId(tenantId)` only (DEBT-022) |
| Service / ServiceImpl | `TaxDeductorService`, `TaxDeductorServiceImpl` | `save(request)`, `current()` — `current()` is the seam `W-36.4` calls |
| Controller | `TaxDeductorController.java` | the two endpoints below |
| DTO | `TaxDeductorRequest`, `TaxDeductorResponse` | the `status` / `message` / `data` envelope (`CONVENTIONS.md` §3) |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| PUT | `/api/v1/payroll/settings/tax-deductor` | `tan`, `pan`, `tds_circle?`, `signatory_employee_id?`, `signatory_name`, `signatory_parent_name`, `signatory_designation` | `200`, the row | `payroll.settings.manage` |
| GET | `/api/v1/payroll/settings/tax-deductor` | — | the row, or `404` | `payroll.settings.manage` or `payroll.statutory_report.read` |

There are no new action codes. Both codes are in `V020__action.sql:109`, `:132`, and are granted
to `payroll-officer` (`V025__catalogue_correction.sql:193`, `:204`).

**Validation — every failure is a `400`**

- `tan` matches `^[A-Z]{4}[0-9]{5}[A-Z]$`. It is uppercased before the check
- `pan` matches `^[A-Z]{5}[0-9]{4}[A-Z]$`. It is uppercased before the check
- `tds_circle`, when given, matches `^[A-Z]{3}/[A-Z]{2}/[0-9]{3}/[0-9]{2}$` (legacy `IncomeTaxDetailsDTO.java:21`)
- `signatory_employee_id`, when given, is a live employee of the bound tenant (`EmployeeService`)
- `signatory_name` and `signatory_designation` are 1–120 characters. `signatory_parent_name` is 0–120

A `GET` with no row returns `404`, not legacy's empty DTO. The statement treats a missing
row as "not set up".

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V107__tax_deductor.sql` | `payroll.tax_deductor` | yes | additive |

`V107` was reserved 2026-09-29, above `W-33.3`'s `V104`.

| Column | Type | Note |
|---|---|---|
| `id` | `UUID PK DEFAULT gen_random_uuid()` | legacy `Long` |
| `tenant_id` | `UUID NOT NULL REFERENCES core.tenant(tenant_id)` | legacy `organization` FK |
| `tan` | `CHAR(10) NOT NULL CHECK (tan ~ '^[A-Z]{4}[0-9]{5}[A-Z]$')` | |
| `pan` | `CHAR(10) NOT NULL CHECK (pan ~ '^[A-Z]{5}[0-9]{4}[A-Z]$')` | |
| `tds_circle` | `VARCHAR(13) NULL` | |
| `signatory_employee_id` | `UUID NULL REFERENCES core.employee(id)` | legacy `employeeId` string |
| `signatory_name` | `VARCHAR(120) NOT NULL` | |
| `signatory_parent_name` | `VARCHAR(120) NULL` | |
| `signatory_designation` | `VARCHAR(120) NOT NULL` | |
| `created_at`, `created_by`, `updated_at`, `updated_by` | as `V051` | |

Indexes: `uk_tax_deductor_tenant UNIQUE (tenant_id)` and `idx_tax_deductor_tenant_signatory (tenant_id, signatory_employee_id)`.
The second index covers the FK (`migration/README.md` rule 5). RLS policy `tenant_isolation` uses the exact `CASE` form.

- [x] `tenant_id` present, and it is the leading column of both indexes
- [x] Index on `tenant_id` plus lookup columns (DEBT-018)
- [x] Money — none
- [x] Expand / contract — new table only

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../taxdeductor/TaxDeductorRulesTest.java` | a lower-case TAN is uppercased and accepted; `ABCD1234E` (9 chars) is refused; a PAN in TAN shape is refused; TDS circle `MUM/TD/001/01` passes and `MUM-TD-001` fails; a blank signatory name is refused |
| Integration | `payroll/.../taxdeductor/TaxDeductorIT.java` | `GET` before any `PUT` returns `404`; `PUT` then `GET` round-trips; a second `PUT` still leaves one row; a signatory from another tenant is `400`; without `payroll.settings.manage` the call is `403` |
| Integration | `payroll/.../taxdeductor/TaxDeductorRlsIT.java` | as `app_user`, tenant A cannot read tenant B's row; a raw-SQL `INSERT` with tenant B's id under tenant A's context is refused; a second row for one tenant is refused by the unique index |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='payroll.tax_deductor'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexname FROM pg_indexes WHERE schemaname='payroll' AND tablename='tax_deductor' ORDER BY 1;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Indexes | `idx_tax_deductor_tenant_signatory`, `tax_deductor_pkey`, `uk_tax_deductor_tenant` |
| Suite | green with no skips, and all three test classes present |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The legacy ten-alphanumeric check is ported as-is, which lets a PAN be saved as a TAN | medium | the regexes are in §4 and in the table `CHECK`s. The unit test swaps them |
| The signatory leaves and the stored name goes stale | low | the name is stored, not read through the employee, so a statement keeps printing who signed. The officer changes it with `PUT` |

## 10. Rollback

Nothing is deployed yet. `V107` is additive, so a bad row is corrected with another `PUT`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `payroll.tax_deductor` |
| Flyway only, `ddl-auto` nowhere | `V107` |
| `Money`/`BigDecimal` for money | no money column |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `payroll` uses `core` (`EmployeeService`) only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for this table.** The tenant comes from the bound context, not a header |
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-008 hand-built envelope | **Fixed** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | Table name | **`payroll.tax_deductor`**. `02-data-model.md` calls it `income_tax_detail`, a legacy name that says nothing about what it holds. The schema (`payroll`) is unchanged |
| 2 | Lock after Form 16? | **No.** The statement is rendered, not stored. The audit trail shows what was in force on any date |
| 3 | Deposit schedule? | **Not ported.** It is fixed at `Monthly` and nothing reads it |
| 4 | Which action? | **`payroll.settings.manage`** to write it. Reading it also allows `payroll.statutory_report.read`, so the statement's reader can see who signs |
