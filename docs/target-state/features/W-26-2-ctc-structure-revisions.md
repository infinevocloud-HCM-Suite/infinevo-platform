# Feature: CTC structure and effective-dated revisions

| Field | Value |
|---|---|
| **Feature ID** | `W-26.2` · from ticket #30 (`W-26`) · `PAY-01` |
| **Promoted to** | `docs/target-state/features/W-26-2-ctc-structure-revisions.md` on the developer's `dev-<name>` branch — **`W-26-2` with hyphens**, never `W-26.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for these tables), DEBT-018 (honoured), DEBT-022 (fixed), DEBT-027 (fixed — the calculation moves server-side), DEBT-002 / DEBT-003 (discounted) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-26.1` — a structure is built from catalogue rows that do not exist until then. `W-13.1` (`7d0bab6`) is on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | 5 scripts, one table each — aggregate exception, same as `W-14.1` | 1 — exception granted 2026-09-25 |
| Externally testable behaviour | an employee's salary is assigned, and a revision creates a new dated version while the old one stays readable | 1 |
| Frontend area | none | 1 |

Within cap. `W-26` was split on 2026-09-25; `W-26.1` is the catalogue.

---

## 1. Problem

The frozen Payroll backend already versions a CTC by effective date, so the idea is a port.
The shape is not.

- **Three competing amount fields.** `EmployeeEarning` carries `amount` (`:24`),
  `amountInPercentage` (`:25`) and `overrideAmount` (`:38`), all `Double`, plus a
  `calculationBasis` enum (`:44`) — `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/EmployeeEarning.java`. No rule says which one the pay run reads
- **Floating-point money.** `CtcStructure.annualCtc` is a `Double` (`CtcStructure.java:32`)
  beside a `BigDecimal monthlySalary` (`:70`); `EmployeeBenefit.amount` (`:23`),
  `EmployeeReimbursement.amount` (`:23`) and `VariableEarning.amount` (`:20`) are `Double`
- **Updates overwrite.** `CtcStructureServiceImpl.update()` (`:288-321`) mutates the version
  in place; only `/revise` (`:828`) creates a row. `09-build-order.md:213`: *"Done when: a
  revision creates a version rather than overwriting"*
- **The pay run mutates the structure.** `EmployeePayRunServiceImpl.java:1057-1066` flips
  `isActive` and `appliedInPayrun` on the salary row while computing pay. Two runs for one
  period, or a rerun, leaves the flags wrong
- **The split is computed in the browser.** `buildCalcTypePatch` and friends are copy-pasted
  across three screens (DEBT-027, `legacy/docs/GAP_INVENTORY.md:77`)
- **Statutory eligibility has no home.** `W-13.1` decision 1 sent the PF, PT, LWF, ESI, EPS
  and higher-wages flags (`BasicDetails.java:79-94,137`) to *"a `payroll`-schema table under
  `PAY-01`"* (`W-13-1-employee-record.md:197`). This is that table

## 2. Scope

**In scope**

- `payroll.ctc_structure` — one row per **version** of an employee's salary
- `payroll.employee_earning`, `payroll.employee_benefit`, `payroll.employee_reimbursement` —
  components of a version, referencing `W-26.1` rows
- `payroll.employee_statutory_profile` — the eligibility flags from `W-13.1` decision 1
- Create, revise, read as of a date, list history, correct or cancel a **future-dated**
  version; the amount split computed and stored server-side

**Out of scope**

- The per-employee provident fund and state insurance lines
  (`ctc_epf_components`, `ctc_esi_components`) — `W-31`, which owns the rates they derive from. See §13, decision 1
- Flexible benefit declarations — `W-27`
- Arrears when a revision is back-dated (`paymentMonth`, `CtcStructure.java:108`) — `W-29`
- Approval of a revision (`revisionStatus`, `:119`) — `W-15`; every version here is in force on its date
- Locking a version once a pay run has consumed it — `W-29`, through `W-19`'s period lock
- Screens — `W-47`

## 3. Flow

```
[payroll officer] --> [EmployeeSalaryController] --> permission payroll.salary.manage
   --> EmployeeSalaryServiceImpl: validates employee (core EmployeeService.get), validates
       components against W-26.1 catalogue, computes monthly and annual amounts
   --> [payroll.ctc_structure v1] + [employee_earning | employee_benefit | employee_reimbursement]

revise --> a NEW ctc_structure row with a later effective_from; components copied then edited
read as of date D --> the row with the greatest effective_from <= D, not cancelled
W-29 pay run --> reads by date. It never writes to these tables
```

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/salary/`.

| Layer | File | Change |
|---|---|---|
| Entity | `CtcStructure.java`, `EmployeeEarning.java`, `EmployeeBenefit.java`, `EmployeeReimbursement.java`, `EmployeeStatutoryProfile.java` | new, `@Table(schema = "payroll")`, `UUID` ids |
| Repository | five | new, every finder takes `tenantId` |
| Service / ServiceImpl | `EmployeeSalaryService`, `EmployeeSalaryServiceImpl`, `EmployeeStatutoryProfileService(Impl)` | new |
| Service | `SalarySplitCalculator.java` | new — the one place `PERCENTAGE` becomes an amount, using `Money` (`shared/.../money/Money.java:26-36`) |
| Controller | `EmployeeSalaryController.java`, `EmployeeStatutoryProfileController.java` | new |
| DTO | `SalaryVersionRequest`, `SalaryComponentRequest`, `SalaryVersionResponse`, `StatutoryProfileRequest/Response` | new; `status` / `message` / `data` envelope |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/payroll/employees/{employeeId}/salary` | annual_ctc, effective_from, earnings[], benefits[], reimbursements[] | `201` first version; `409` if one exists | `payroll.salary.manage` |
| POST | `/api/v1/payroll/employees/{employeeId}/salary/revisions` | same | `201` new version | `payroll.salary.manage` |
| GET | `/api/v1/payroll/employees/{employeeId}/salary?asOf=` | date, default today | version in force, with components and resolved amounts | `payroll.salary.read` |
| GET | `/api/v1/payroll/employees/{employeeId}/salary/versions` | — | all versions, newest first, cancelled included and marked | `payroll.salary.read` |
| PUT | `/api/v1/payroll/employees/{employeeId}/salary/versions/{id}` | same as POST | `200`; `409` unless `effective_from` is after today | `payroll.salary.manage` |
| DELETE | `/api/v1/payroll/employees/{employeeId}/salary/versions/{id}` | — | `204` cancels; `409` unless future-dated | `payroll.salary.manage` |
| GET / PUT | `/api/v1/payroll/employees/{employeeId}/statutory-profile` | flags and numbers | one row, upsert | `payroll.salary.read` / `.manage` |

Permission codes exist: `payroll.salary.read`, `payroll.salary.manage` —
`reference/V020__action.sql:113-114`.

A component in the request is `{component_id, calculation_type, value, percentage_of}`.
The service resolves `monthly_amount` and `annual_amount` and stores them. **The client
never sends a resolved amount** — that is the fix for the three fields and for DEBT-027.

Validation, all `400` unless noted: employee exists in the tenant (`core` `EmployeeService.get`,
`404`); every `component_id` is an active `W-26.1` row; `value` non-negative; `percentage_of`
present iff `PERCENTAGE`; `BASIC` as a basis requires an earning of type `BASIC` in the same
version; no two versions of one employee share an `effective_from` (`409`); the annual sum of
components flagged `is_included_in_ctc` equals `annual_ctc` at scale 2 (`400`, with the
difference in the message).

Legacy's rule of one unprocessed revision at a time (`CtcStructureServiceImpl.java:835-843`)
is dropped: several future versions may be scheduled, on distinct dates.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V046__ctc_structure.sql` | `payroll.ctc_structure` | yes | additive |
| `payroll/V047__employee_earning.sql` | `payroll.employee_earning` | yes | additive |
| `payroll/V048__employee_benefit.sql` | `payroll.employee_benefit` | yes | additive |
| `payroll/V049__employee_reimbursement.sql` | `payroll.employee_reimbursement` | yes | additive |
| `payroll/V050__employee_statutory_profile.sql` | `payroll.employee_statutory_profile` | yes | additive |

From the payroll lane's reserved block `V042`–`V052` (`W-26.1` §6). One table per script.

**Common:** `id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` · four audit columns.

**`ctc_structure`**, from `CtcStructure.java:32,70,79`:
`employee_id uuid NOT NULL REFERENCES core.employee(id)` · `effective_from date NOT NULL` ·
`annual_ctc numeric(19,4) NOT NULL` · `monthly_ctc numeric(19,4) NOT NULL` ·
`is_cancelled boolean NOT NULL DEFAULT false` · `cancelled_at timestamptz` ·
`notes varchar(500)`.

**Deliberately absent:** `is_active`, `is_revision`, `applied_in_payrun`, `revision_status`,
`change_in_percent`, `payment_month` (`CtcStructure.java:88-129`). The version in force is a
query by date, not a flag; the change percentage is computed in the response; the rest is
`W-15` and `W-29`.

**`employee_earning` / `employee_benefit` / `employee_reimbursement`**, one shape:
`ctc_structure_id uuid NOT NULL REFERENCES payroll.ctc_structure` ·
`component_id uuid NOT NULL REFERENCES payroll.<earning|benefit|reimbursement>` ·
`calculation_type varchar(16) NOT NULL` · `value numeric(19,4) NOT NULL` ·
`percentage_of varchar(16)` · `monthly_amount numeric(19,4) NOT NULL` ·
`annual_amount numeric(19,4) NOT NULL` · `is_enabled boolean NOT NULL DEFAULT true`.
`employee_reimbursement` adds `carry_forward_option varchar(32)` (`EmployeeReimbursement.java:26`).
`employee_earning` adds `earning_frequency varchar(16)` (`EmployeeEarning.java:40`).

`value` + `calculation_type` + `percentage_of` is the rule; `monthly_amount` is the result.
That is one input where legacy had three.

**`employee_statutory_profile`**, from `BasicDetails.java:79-115,137`, one row per employee:
`employee_id uuid NOT NULL REFERENCES core.employee(id)` · `is_eligible_for_pf` ·
`is_eligible_for_pt` · `is_eligible_for_lwf` · `is_eligible_for_esi` · `is_eligible_for_eps` ·
`contributes_eps_on_higher_wages` · `is_director` — all `boolean NOT NULL DEFAULT false` ·
`pf_account_number varchar(32)` · `uan varchar(16)` · `esi_number varchar(32)`.

- [x] `tenant_id` on all five, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `uk_ctc_structure_tenant_employee_effective (tenant_id, employee_id, effective_from)` unique · `idx_ctc_structure_tenant_employee_cancelled (tenant_id, employee_id, is_cancelled, effective_from DESC)` — the as-of read · `idx_<child>_tenant_structure (tenant_id, ctc_structure_id)` and `idx_<child>_tenant_component (tenant_id, component_id)` on each child · `uk_employee_statutory_profile_tenant_employee (tenant_id, employee_id)` unique
- [x] Money columns `numeric(19,4)`; nothing floating
- [x] Expand / contract — five new tables, no destructive step; `W-26.1` tables untouched

Cross-schema foreign keys to `core.employee` are fine: one database, and `payroll` may
depend on `core` (`payroll/pom.xml:19`). No `hrms` reference anywhere.

RLS and `tenant_isolation` in the exact `CASE` form in every script — `migration/README.md`
§row-level security.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../salary/SalarySplitCalculatorTest.java` | `FLAT` passes through; `PERCENTAGE` of `CTC`, `BASIC`, `GROSS` resolves at scale 4 with `HALF_UP`; `BASIC` basis without a basic earning refused; sum-equals-CTC check with the difference reported |
| Unit | `payroll/.../salary/SalaryVersionRulesTest.java` | edit or cancel refused once `effective_from` is today or past; duplicate `effective_from` refused; second first-version refused |
| Integration | `payroll/.../salary/SalaryVersionIT.java` | **the acceptance test**: create v1 on 2026-01-01, revise on 2026-04-01; as-of 2026-03-31 returns v1, as-of 2026-04-01 returns v2; v1 row unchanged; history lists both |
| Integration | `payroll/.../salary/SalaryRlsIT.java` | as `app_user`, tenant A cannot read tenant B's versions, nor build a version on tenant B's employee or component |
| Integration | `payroll/.../salary/StatutoryProfileIT.java` | upsert is one row per employee; second PUT updates, never inserts |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`SalaryRlsIT` matters because a foreign key alone does not stop a cross-tenant reference
— `migration_user` bypasses RLS, so the check runs as `app_user`, as in `W-14.1`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in ctc_structure employee_earning employee_benefit employee_reimbursement employee_statutory_profile; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='payroll.$t'::regclass;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='ctc_structure'
      AND column_name IN ('is_active','applied_in_payrun','revision_status','payment_month');"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) FROM information_schema.columns
    WHERE table_schema='payroll' AND data_type IN ('double precision','real');"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on all five | `t` five times |
| Legacy flags absent | zero rows |
| Floating-point columns in `payroll` | `0` |
| Suite | green, no skips; `SalaryVersionIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `overrideAmount` comes back as "just one nullable column" | **medium — it is the legacy habit** | `monthly_amount` is computed, never accepted; the request DTO has no such field |
| A revision is implemented as an in-place `PUT` | medium | `PUT` refuses once in force; `SalaryVersionIT` reads the old version after the new one exists |
| The sum-equals-CTC rule blocks real data where legacy auto-balanced a special allowance | medium | The rule stands; the balancing component is a screen concern for `W-47`, sent as an ordinary `FLAT` earning |
| `W-29` later adds an "active" flag it writes during a run | medium | Named here: the pay run reads by date and writes nothing to these tables |
| Statutory flags drift from `W-31`'s needs | low | Ported one to one from `BasicDetails.java`; `W-31` adds columns, never re-homes them |

## 10. Rollback

Nothing is deployed. All five scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | all five, each in its own script |
| Flyway only, `ddl-auto` nowhere | five scripts |
| `Money`/`BigDecimal` for money | every amount `numeric(19,4)`; `SalarySplitCalculator` uses `Money` |
| Index on `tenant_id` plus lookup columns | see §6; the as-of index is ordered `effective_from DESC` |
| Expand / contract | new tables only |
| No module references another module | `payroll` → `core` (`EmployeeService`) and `shared` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for these tables** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** — every finder takes `tenantId` |
| DEBT-027 split computed in three browser copies | **Fixed** — one server-side calculator; the screens in `W-47` display, they do not compute |
| DEBT-002 / DEBT-003 | **Discounted** — Flyway and tests are the platform norm already |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | `ctc_epf_component` and `ctc_esi_component` here, as `02-data-model.md:339` lists? | **`W-31`.** They are derived from PF and ESI rates that do not exist until `W-31`, and legacy stores their percentage as a `String` (`CtcEpfComponent.java:27`). Building them without the rates means storing typed-in numbers. `02-data-model.md` §4 and the `PAY-01` / `PAY-08` rows updated 2026-09-25 |
| 2 | A status flag for the version in force? | **No.** Legacy's `isActive` was written by the pay run (`EmployeePayRunServiceImpl.java:1057-1066`) and is the reason a rerun corrupts it. As-of is a query |
| 3 | Approval of a revision? | **Not here.** `W-15` owns approvals; `revisionStatus` (`CtcRevisionStatus.java`) is not ported |
| 4 | Where does the pay run read the salary? | `GET .../salary?asOf=` semantics, through a service method `versionInForce(tenantId, employeeId, date)`. `W-29` calls the service, not the table |
