# Feature: Ad-hoc salary deductions — enter, list, reverse, feed the pay run

| Field | Value |
|---|---|
| **Feature ID** | `W-35.2` · from ticket #47 (`W-35`) · `PAY-13` |
| **Promoted to** | `docs/target-state/features/W-35-2-ad-hoc-deductions.md` on the developer's `dev-<name>` branch — **`W-35-2` with hyphens**, never `W-35.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-004 (discounted), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-011 (fixed), DEBT-018 (honoured), DEBT-022 (fixed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | none. `W-19` (ledger) is on `main`. No approval: an officer's entry is final, as legacy |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V101` — one table. `V100` is the action-code seed in `reference`, the `V052` shape; no schema | 1 |
| Externally testable behaviour | a payroll officer enters a batch of one-off deductions and each one is an `AD_HOC_DEDUCTION` row in the ledger for its period; reversing one nets it out | 1 |
| Frontend area | none — screens are `W-47.4` | 1 |

Within cap. `W-35` was split on 2026-09-29: `W-35.1` is the reimbursement claim, this is the
deduction.

---

## 1. Problem

The frozen Payroll backend has a working one-off deduction feature
(`legacy/docs/FEATURE_MAP.md:348-363`). It is a port, and three things must not come across.

- **The pay run reads the deduction table.** `EmployeePayRunServiceImpl.java:335-348` sums
  `ACTIVE` rows by month; `:1208-1221` flips them to `INPAYRUN`;
  `PayRunServiceImpl.java:697-709`, `:753-761`, `:868-876` flip them to `PROCESSED` or back
  to `ACTIVE` on approve, reject and delete. The target pay run reads only `core.pay_input`
  (`W-29-3-pay-run-lop-and-inputs.md:36`, `:94`)
- **Edit and delete are blocked by status, not by design.** Once `INPAYRUN` a row cannot be
  changed (`FEATURE_MAP.md:363`); before that it can be edited in place with no history.
  The ledger's rule is the opposite: never edit, always reverse (`W-19` §4)
- **Proofs are public Cloudinary URLs.** `SalaryDeduction.java:63-66`,
  `SalaryDeductionController.java:54-121` — DEBT-011
- `deduction_type` is free text (`SalaryDeduction.java:35`) and `employee_id` is a Keycloak
  string (`:19`)

## 2. Scope

**In scope**

- `payroll.employee_deduction` — one row per deduction, `tenant_id`, RLS
- Batch entry by the payroll officer, 1–500 rows, all-or-nothing, mirroring the legacy grid
  (`SalaryDeductionController.java:38-52`); each row writes one `AD_HOC_DEDUCTION` ledger
  row in the same transaction
- Reverse one deduction: a reversal row on the ledger, status `REVERSED` here
- Officer list with filters; employee reads own
- Optional proof as a `document_id` uploaded through `W-21`
- Three action codes with grants for new and existing tenants

**Out of scope**

- Editing a deduction. Reverse and re-enter
- Recurring deductions, loans with instalments. Legacy has none; `W-26` structural
  deductions cover the standing kind
- Any status beyond `POSTED` / `REVERSED`. `INPAYRUN` and `PROCESSED` were the run's
  bookkeeping; the period lock is that now
- Screens — `W-47.4`

## 3. Flow

```
[payroll officer] --> POST /api/v1/payroll/employee-deductions  [ {employee_id, period, deduction_type, amount, reason, remarks?, document_id?} x N ]
                  --> EmployeeDeductionServiceImpl.enter   (one transaction)
                      for each line:
                        --> INSERT payroll.employee_deduction  status=POSTED
                        --> PayInputService.record(employee, period, AD_HOC_DEDUCTION,
                                amount, source "payroll", ref "employee_deduction:{id}")
                        --> row.pay_input_id, row.posted_period = response.postedPeriod
                  --> 201 with every row, or 400 with the failing line index and nothing written

[payroll officer] --> DELETE /api/v1/payroll/employee-deductions/{id}?reason=...
                  --> PayInputService.reverse(pay_input_id, reason)
                  --> status=REVERSED, reversal_pay_input_id, reversed_at, reversed_by

[employee]        --> GET /api/v1/me/employee-deductions

[pay run, W-29.3] --> PayInputService.forPeriod --> DEDUCTION line per employee
```

A locked period is the ledger's problem, not this ticket's: `PayInputService.record` posts to
the next open period and reports it (`core/.../payinput/PayInputService.java:21-27`), and a
reversal of a row in a locked period is redirected the same way (`:58-71`) — the money comes
back on the next payslip, which is the right answer for a deduction already taken.

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/deduction/`.

| Layer | File | Change |
|---|---|---|
| Entity | `EmployeeDeduction.java` | new, `@Table(name = "employee_deduction", schema = "payroll")`; `UUID id`, `UUID tenantId`, `UUID employeeId`, `String period`, `DeductionType deductionType`, `BigDecimal amount`, `String reason`, `String remarks`, `UUID documentId`, `DeductionState status`, `UUID payInputId`, `String postedPeriod`, `UUID reversalPayInputId`, `Instant reversedAt`, `UUID reversedBy`, audit columns |
| Enumeration | `DeductionType.java` | new — `ADVANCE_RECOVERY`, `LOAN_RECOVERY`, `DAMAGE`, `PENALTY`, `EXCESS_PAYMENT`, `OTHER`. Legacy free text (`SalaryDeduction.java:35`) becomes a closed list; `reason` keeps the words |
| Enumeration | `DeductionState.java` | new — `POSTED`, `REVERSED` |
| Repository | `EmployeeDeductionRepository.java` | new — `findByTenantIdAndId`, `findByTenantIdAndEmployeeIdOrderByPeriodDesc`, a paged filtered finder. Every finder takes `tenantId` (DEBT-022) |
| Service / ServiceImpl | `EmployeeDeductionService`, `EmployeeDeductionServiceImpl` | new — `enter(List)`, `reverse(id, reason)`, `get`, `list`, `listOwn` |
| Controller | `EmployeeDeductionController.java` | new |
| DTO | `EmployeeDeductionLineRequest`, `EmployeeDeductionResponse`, `EmployeeDeductionBatchResponse` | new; `status` / `message` / `data` envelope (`CONVENTIONS.md` §3) |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/payroll/employee-deductions` | array of 1–500 lines: `employee_id`, `period` (`YYYY-MM`), `deduction_type`, `amount`, `reason`, `remarks?`, `document_id?` | `201`, every row with `id`, `posted_period`; `400` with `line` index on the first failure, nothing written | `payroll.employee_deduction.manage` |
| GET | `/api/v1/payroll/employee-deductions` | `employeeId`, `period`, `status`, `deductionType`, `page`, `size` (25, clamped at 100) | paged rows in the tenant | `payroll.employee_deduction.read` |
| GET | `/api/v1/payroll/employee-deductions/{id}` | — | one row | `payroll.employee_deduction.read` |
| DELETE | `/api/v1/payroll/employee-deductions/{id}` | `reason` query param, required | `200`, the row with `status = REVERSED` and `reversal_pay_input_id`; `409` if already reversed | `payroll.employee_deduction.manage` |
| GET | `/api/v1/me/employee-deductions` | — | own rows, newest period first, including reversed | `payroll.employee_deduction.read_own` |

`/me/*` resolves the employee with `EmployeeService.currentEmployee()` as `W-27.2` does
(`payroll/.../fbp/FbpDeclarationServiceImpl.java:84`); a login linked to no employee gets `403`.

**Validation per line, all `400` naming the line**

- `employee_id` is an active employee of the bound tenant (`EmployeeService`)
- `period` parses as `YearMonth`, not more than one month in the future
- `amount > 0`, scale at most 2
- `deduction_type` one of the enum
- `reason` 1–255 characters; `remarks` at most 500
- `document_id`, when given, a document in the bound tenant of kind `EMPLOYEE_DOCUMENT`
  whose employee is `employee_id`

Two identical lines in one batch are accepted — a second advance is a second advance. A
retried batch posts twice: the row id is minted per call, so the ledger's
`(source_module, source_ref)` cannot catch it. Same open point as `W-39.2`'s overtime POST
(`active-work.md`, 2026-09-29); the screen disables the button on submit and the founder has
accepted the exposure. No idempotency key in this ticket.

**Reverse**

`reverse` calls `PayInputService.reverse(pay_input_id, reason)`, stores the returned id as
`reversal_pay_input_id`, sets `REVERSED`, `reversed_at`, `reversed_by`. A row already
`REVERSED` is `409`. The ledger decides the period of the reversal; this row does not
second-guess it.

## 5. Frontend changes

None. `W-47.4` builds the grid, the list and the employee's own view against this contract.

## 6. Database changes

> Flyway only. Never `ddl-auto`. Every statement names its schema
> (`code/backend/migration/README.md`).

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `reference/V100__employee_deduction_actions.sql` | `reference.action` rows; `core.seed_system_roles` redefined; grants to existing tenants — the `V052` shape | grants are per tenant | additive |
| `payroll/V101__employee_deduction.sql` | `payroll.employee_deduction` | yes | additive |

`V099` is not free: it is the annual-update test fixture
(`code/backend/migration/src/test/resources/db/migration-annual/reference/V099__test_annual_update.sql`).

`payroll.employee_deduction`

| Column | Type | Note |
|---|---|---|
| `id` | `UUID PK DEFAULT gen_random_uuid()` | |
| `tenant_id` | `UUID NOT NULL REFERENCES core.tenant(tenant_id)` | |
| `employee_id` | `UUID NOT NULL REFERENCES core.employee(id)` | legacy `VARCHAR` Keycloak id (`SalaryDeduction.java:19`) |
| `period` | `CHAR(7) NOT NULL CHECK (period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$')` | legacy `deduction_month DATE` first-of-month (`legacy/docs/DB_SCHEMA.md:1076-1095`); `core.pay_input.period` is `CHAR(7)` (`V031:11`) |
| `deduction_type` | `VARCHAR(32) NOT NULL CHECK (IN (...six values...))` | |
| `amount` | `NUMERIC(19,4) NOT NULL CHECK (> 0)` | legacy `DECIMAL(15,2)` |
| `reason` | `VARCHAR(255) NOT NULL` | |
| `remarks` | `VARCHAR(500)` | |
| `document_id` | `UUID NULL REFERENCES core.document(id)` | replaces `proof_url`, `proof_public_id` |
| `status` | `VARCHAR(16) NOT NULL CHECK (IN ('POSTED','REVERSED'))` | |
| `pay_input_id` | `UUID NOT NULL` | no FK; the ledger is append-only |
| `posted_period` | `CHAR(7) NOT NULL` | what the ledger took, may differ from `period` |
| `reversal_pay_input_id` | `UUID NULL` | |
| `reversed_at` | `TIMESTAMPTZ NULL` | |
| `reversed_by` | `UUID NULL` | |
| `created_at`, `created_by`, `updated_at`, `updated_by` | as `V051` | |

Indexes: `idx_emp_deduction_tenant_employee_period (tenant_id, employee_id, period DESC)`,
`idx_emp_deduction_tenant_period_status (tenant_id, period, status)`,
`uk_emp_deduction_tenant_pay_input (tenant_id, pay_input_id)`.
RLS policy `tenant_isolation` copied from `V051__fbp.sql:26-33`.

- [x] `tenant_id` present on every new table (`CONVENTIONS.md` rule 7)
- [x] Index on `tenant_id` plus lookup columns (DEBT-018)
- [x] Money column `NUMERIC(19,4)`, `BigDecimal` in the entity, `Money` on the ledger call
- [x] Expand / contract — new table only

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../deduction/EmployeeDeductionRulesTest.java` | zero and negative amount refused; bad period string refused; period two months ahead refused; unknown type refused; empty reason refused; batch of 501 refused; the failing line index is reported |
| Integration | `payroll/.../deduction/EmployeeDeductionIT.java` | a batch of three lines for two employees leaves three rows and three `core.pay_input` rows of kind `AD_HOC_DEDUCTION`, `source_ref = "employee_deduction:" + id`; a batch whose third line is invalid leaves zero rows in both tables; `DELETE` writes one reversal row with `reverses_id` set and the row is `REVERSED`; a second `DELETE` is `409`; `GET /me/...` shows only the caller's rows |
| Integration | `payroll/.../deduction/EmployeeDeductionLockedPeriodIT.java` | period locked via `PayInputService.lock`; a line for it posts to the next period and `posted_period` says so; reversing a row whose period is locked posts the reversal to the next open period |
| Integration | `payroll/.../deduction/EmployeeDeductionRlsIT.java` | as `app_user`, tenant A cannot read or reverse tenant B's row; a raw-SQL `INSERT` with tenant B's id under tenant A's context is refused |
| Integration | `payroll/.../deduction/EmployeeDeductionActionSeedIT.java` | the three codes exist; `payroll-officer` holds `read` and `manage`; `finance` holds `read`; `employee` holds `read_own`; a tenant provisioned after the migration holds the same |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. Payroll ITs use the
existing test stand-in for `EmployeeService`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='payroll.employee_deduction'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type, numeric_precision, numeric_scale FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='employee_deduction'
      AND column_name IN ('amount','period','posted_period','pay_input_id');"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexname FROM pg_indexes WHERE schemaname='payroll' AND tablename='employee_deduction';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT code FROM reference.action WHERE code LIKE 'payroll.employee_deduction.%' ORDER BY code;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Types | `amount` `numeric` 19,4; `period` and `posted_period` `character`; `pay_input_id` `uuid` |
| Indexes | the three named in §6 plus the PK |
| Actions | `manage`, `read`, `read_own` |
| Suite | green, no skips; `EmployeeDeductionIT` and `EmployeeDeductionLockedPeriodIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A `PUT` to edit amount or period is added, as legacy allowed before `INPAYRUN` | high — it is the port habit | §2 rules it out; the reviewer checks the controller has five mappings and no `PUT` |
| The ledger row is written after the batch commits, so a failed line leaves orphan ledger rows | medium | `enter` is one `@Transactional` method; `EmployeeDeductionIT` asserts zero rows in both tables after a bad third line |
| `period` is stored as a `DATE` first-of-month, as legacy | medium | `CHAR(7)` with the regex check; the psql check in §8 shows `character` |
| Reverse deletes the row | medium | `DELETE` is a verb, not a row delete; the IT asserts the row survives as `REVERSED` |
| The reversal of a locked-period deduction is refused instead of redirected | low | That is the ledger's documented behaviour (`PayInputService.java:62-75`); `EmployeeDeductionLockedPeriodIT` covers it |

## 10. Rollback

Nothing is deployed. Both scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `payroll.employee_deduction` |
| Flyway only, `ddl-auto` nowhere | `V100` (seed), `V101` (table) |
| `Money`/`BigDecimal` for money | `NUMERIC(19,4)`, `BigDecimal`, `Money.of` on the ledger call |
| Index on `tenant_id` plus lookup columns | three indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `payroll` uses `core` (`PayInputService`, `DocumentService`, `EmployeeService`) and `shared` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for this table** |
| DEBT-004 Cloudinary keys in properties | **Discounted** — no Cloudinary |
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-008 hand-built envelope | **Fixed** |
| DEBT-011 public Cloudinary URLs | **Fixed** — `document_id` only |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | When is the ledger row written? | **On entry, in the same transaction.** There is no approval; the officer's entry is the decision, as legacy (`SalaryDeductionController.java:38-52`) |
| 2 | Edit? | **Never. Reverse and re-enter.** The ledger has no edit (`W-19` §4) and a deduction with a silent history is exactly what an employee disputes |
| 3 | Status set? | **`POSTED` and `REVERSED` only.** Legacy `ACTIVE` / `INPAYRUN` / `PROCESSED` (`DeductionStatus.java:3-7`) was the run's bookkeeping; the period lock is that now |
| 4 | Type: free text or closed list? | **Closed list of six.** Free text (`SalaryDeduction.java:35`) cannot be reported on; `OTHER` plus `reason` keeps the escape hatch |
| 5 | Proof document kind? | **`EMPLOYEE_DOCUMENT`.** Adding a `DEDUCTION_PROOF` kind is a `core` change for one optional field; not worth a cross-module edit |
| 6 | Retried batch posts twice? | **Accepted for now**, as `W-39.2`. If it bites, an `Idempotency-Key` header on the batch is a one-ticket fix |
