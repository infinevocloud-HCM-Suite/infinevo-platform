# Feature: Leave types and policy

| Field | Value |
|---|---|
| **Feature ID** | `W-16.1` · ticket #17 · `CORE-07` |
| **Promoted to** | `docs/target-state/features/W-16-1-leave-types-policy.md` on branch `W-16-1-leave-types-policy` — **`W-16-1` with hyphens**, never `W-16.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-013 (discounted), DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-15.2` per the ticket; in practice only `W-14.1`, since eligibility references departments, designations and work locations; `W-11.3` for the `core.leave_type.*` permission codes (`12-core-contracts.md` §4) |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 rows 3, 23 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 2 scripts, one table each — aggregate exception | 1 — exception granted 2026-09-22 |
| Externally testable behaviour | a leave type with a policy is defined, and an ineligible employee is refused it | 1 |
| Frontend area | none | 1 |

Within cap. `leave_policy` is a satellite of `leave_type` and has no life without it.

---

## 1. Problem

Both products have a leave type. One is a label, the other is a policy engine with nothing
behind it.

- HRMS `LeaveType.java:14-57` has seven columns — name, `default_days`, a carry-forward boolean, validity dates, an apply-to-all flag and a set of employee ids. No accrual, no encashment, no eligibility, no paid/unpaid distinction
- HRMS hard-codes the actual entitlements as column defaults on the balance row: annual 24, sick 12, casual 8, maternity 90, paternity 10 — `LeaveBalance.java:9-72`. Changing a tenant's annual allowance means changing a Java default
- Payroll `LeaveType.java:1-169` is the opposite: accrual frequency and units (`:89-96`), reset (`:102-106`), carry-forward with a cap (`:109-113`), encashment (`:116-120`), past and future booking limits (`:123-133`), weekend and holiday inclusion (`:136-146`), an exceed-balance mode (`:149-153`), pro-rating and a per-application maximum. **Encashment is the one item not carried forward** — decision 2
- **Almost none of it runs.** No accrual engine exists in either codebase. `expiration_date` on an allocation is never enforced. `pro_rate_is_enabled` has no implementation. Encashment has none

So the target is not a merge of two working systems. It is Payroll's model, kept, with the
parts that were never wired left clearly unwired rather than half-built.

`legacy/docs/DB_SCHEMA.md:196` lists an `is_paid` column on the HRMS leave type that the
entity does not have. Trust the entity.

## 2. Scope

**In scope**

- `core.leave_type` — identity: name, code, paid or unpaid, unit, half-day allowed, validity window
- `core.leave_policy` — the configuration matrix, one current policy per type
- Eligibility by gender, department, designation and work location
- An eligibility check any later ticket can call
- `requires_document`, so `W-16.3` can refuse a request that needs a certificate and has none

**Out of scope**

- Allocation and balances — `W-16.2`
- Requests and approval — `W-16.3`
- Consumption and loss of pay — `W-16.4a`
- **Running the accrual engine** — the policy records the frequency; `W-16.2` implements it
- **Encashment entirely.** Payroll has settings and no behaviour; neither is carried across (decision 2)

## 3. Flow

```
[admin] --> [LeaveTypeController] --> [LeaveTypeService]
   --> TenantContext bound by W-08 --> [core.leave_type, core.leave_policy under RLS]

[W-16.3, later] --> [LeaveEligibilityService.isEligible(employee, type)] --> boolean
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../leave/LeaveTypeController.java` | new |
| Service | `core/.../leave/LeaveTypeService.java` | new |
| Service | `core/.../leave/LeaveEligibilityService.java` | new — the seam `W-16.3` plugs into |
| Entity | `core/.../leave/LeaveType.java`, `LeavePolicy.java` | new, each `@Table(schema="core")` |
| Repository | `core/.../leave/LeaveTypeRepository.java`, `LeavePolicyRepository.java` | new |
| Enumeration | `core/.../leave/LeaveUnit.java`, `AccrualFrequency.java`, `ResetFrequency.java`, `ExceedBalanceMode.java` | new — `AccrualFrequency` is `MONTHLY, YEARLY` (`legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/leaveAttendence/addLeaveTypes.js:403-404`); `ResetFrequency` is `YEARLY, MONTHLY, QUARTERLY, HALF_YEARLY` (`:471-474`); `ExceedBalanceMode` is `NO_LIMIT, YEAR_END_LIMIT, MARK_AS_LOP` (`:591-593`), stored as the legacy strings `noLimit`, `yearEndLimit`, `markAsLOP` |
| DTO | `core/.../leave/*Request.java`, `*Response.java` | new |

The package is `core/.../leave/`. The frozen tree's `leaveAndAttedance/` typo (DEBT-013) is
not carried forward and the frozen package is not renamed.

**API contract**

| Method | Path | Request | Response | `@RequiresAction` |
|---|---|---|---|---|
| POST | `/api/v1/leave-types` | name, code, paid, unit, half-day, validity | `201` | `core.leave_type.manage` |
| GET | `/api/v1/leave-types` | `?activeOn=` | list with current policy | `core.leave_type.read` |
| PUT | `/api/v1/leave-types/{id}` | same | `200` | `core.leave_type.manage` |
| PUT | `/api/v1/leave-types/{id}/policy` | the policy matrix | `200` | `core.leave_type.manage` |
| GET | `/api/v1/leave-types/eligible?employeeId=` | — | the types that employee may request | `core.leave_type.read` |

Every endpoint is tenant bound and carries the code shown — `EndpointGuardCoverageTest` fails
otherwise (`12-core-contracts.md` §2). The codes arrive with `W-11.3`; `core.leave_type.read`
is added there alongside the renamed `core.leave_type.manage` (`12-core-contracts.md` §4).

The policy `PUT` rejects `exceedBalanceMode = yearEndLimit` without `exceedBalanceLimitDays`,
the same rule the frozen screen enforces (`addLeaveTypes.js:195-196`).

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__leave_type.sql` | `core.leave_type` | yes | additive |
| `core/V0NN__leave_policy.sql` | `core.leave_policy` | yes | additive |

Version numbers assigned when the branch is cut — `migration/README.md:17-31`.

`leave_type`: `id uuid` · `tenant_id uuid NOT NULL` · `code varchar(32) NOT NULL` ·
`name varchar(128) NOT NULL` · `is_paid boolean NOT NULL` ·
`unit varchar(16) NOT NULL` — day-based only for now · `allow_half_day boolean NOT NULL` ·
`valid_from date NOT NULL` · `valid_to date NULL` · `is_active boolean NOT NULL DEFAULT true` ·
four audit columns.

`leave_policy`: `id uuid` · `tenant_id uuid NOT NULL` ·
`leave_type_id uuid NOT NULL REFERENCES core.leave_type(id)` ·
`annual_days numeric(10,2) NOT NULL` · `accrual_enabled boolean` ·
`accrual_frequency varchar(16) CHECK IN ('monthly','yearly')` ·
`accrual_units numeric(10,2)` · `reset_enabled boolean` ·
`reset_frequency varchar(16) CHECK IN ('yearly','monthly','quarterly','halfYearly')` ·
`carry_forward_enabled boolean` · `carry_forward_cap numeric(10,2)` · `carry_forward_expires_after_months int` ·
`requires_document boolean NOT NULL DEFAULT false` ·
`past_booking_limit_days int` · `future_booking_limit_days int` ·
`include_weekend boolean` · `include_holiday boolean` ·
`exceed_balance_mode varchar(16) NOT NULL CHECK IN ('noLimit','yearEndLimit','markAsLOP')` ·
`exceed_balance_limit_days numeric(10,2) NULL` — required when the mode is `yearEndLimit`, a
`CHECK` enforces the pair · `pro_rate_enabled boolean` ·
`max_days_per_application numeric(10,2)` · `gender varchar(32) NULL` — restricts the type to
one gender, NULL means any · `effective_from date NOT NULL` · four audit columns.

The frequency vocabularies are the frozen screen's (`addLeaveTypes.js:403-404`, `:471-474`),
and the three exceed-balance modes are its `negativeBalanceType` options (`:591-593`). Payroll's
entity stores the mode but not the limit (`LeaveType.java:152-153`), so `exceed_balance_limit_days`
is new. What each mode means downstream: `noLimit` lets the balance go negative with no
consequence; `yearEndLimit` lets it go negative up to the limit, and `W-16.3` refuses a request
past it; **only `markAsLOP` produces loss of pay** in `W-16.4a` (`12-core-contracts.md` §5 row 2).

Eligibility is a third structure, `core.leave_policy_eligibility`, holding one row per
(policy, dimension, value). **That makes three tables.** See decision 1.

`leave_policy_eligibility`: `id uuid` · `tenant_id uuid NOT NULL` ·
`policy_id uuid NOT NULL REFERENCES core.leave_policy(id)` ·
`dimension varchar(32) NOT NULL CHECK IN ('department','designation','work_location','employment_type')` ·
`value_id uuid NOT NULL` — the id in the dimension's master table (`core.department`,
`core.designation`, `core.work_location` from `W-14.1`, `M/core/V011`–`V013`) · four audit
columns. Unique `(tenant_id, policy_id, dimension, value_id)`. `employment_type` is in the
vocabulary for the contract (`12-core-contracts.md` §5 row 3) but no built table carries one —
`core.employee` (`V010`) and `core.employee_employment` (`V018:4-17`) have no such column — so
the API rejects that dimension until a master for it exists. Gender is not a dimension here: it
is `core.employee.gender` (`V010__employee.sql:11`), a `varchar`, and `LeaveEligibilityService`
checks it from a nullable `gender varchar(32)` column on `core.leave_policy` instead.

- [x] `tenant_id` on every table, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, code)` unique on type, `(tenant_id, leave_type_id, effective_from DESC)` on policy, `(tenant_id, policy_id, dimension, value_id)` unique on eligibility
- [x] **Money columns — none.** Day counts are `numeric(10,2)`, not money and not integers
- [x] Expand / contract — new tables only

**Day counts are `numeric(10,2)`, deliberately** — the shape `CONVENTIONS.md:37` prescribes
for leave days. Payroll stores allocation days as `Integer`
— `EmployeeLeaveAllocation.java:78` — while HRMS supports half-days through
`is_half_day` and `half_day_period` on the request. An integer column is where half a day gets
lost, and that is BUG-003's whole shape. Two decimal places carry it from the first table.

**`effective_from` on the policy, not on the type.** A tenant changing its annual allowance
mid-year must not rewrite history, and a balance calculated last March must still be
explainable. One row per change, the current one selected by date.

Each script carries its own RLS and `tenant_isolation` policy in the exact `CASE` form —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../leave/LeaveEligibilityServiceTest.java` | each dimension filters; no eligibility rows means everyone is eligible; combined dimensions are AND, not OR |
| Unit | `core/.../leave/LeavePolicyVersionTest.java` | the policy in force on a given date is the latest with `effective_from <= date` |
| Integration | `core/.../leave/LeaveTypeRlsIT.java` | tenant A cannot read or write tenant B's types or policies as `app_user` |
| Integration | `core/.../leave/LeaveTypeHalfDayIT.java` | `2.5` days survives a write and read on every day-count column |
| Unit | `core/.../leave/LeavePolicyValidationTest.java` | `yearEndLimit` without a limit is refused; `noLimit` and `markAsLOP` with a limit are refused; an accrual or reset frequency outside its vocabulary is refused |
| Integration | `core/.../leave/LeavePolicyEligibilityIT.java` | a second row for the same `(policy, dimension, value_id)` is refused by the unique constraint; a `dimension` outside the four is refused by the `CHECK` |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in leave_type leave_policy leave_policy_eligibility; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type, numeric_precision, numeric_scale FROM information_schema.columns
    WHERE table_schema='core' AND table_name='leave_policy' AND column_name LIKE '%days%' ORDER BY 1;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on all three | `t` three times |
| Day-count columns | `numeric`, precision 10, scale 2 — never `integer` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Day counts become integers because the legacy allocation used them | **high — this is the BUG-003 shape** | `numeric(10,2)` everywhere; `LeaveTypeHalfDayIT` asserts `2.5` round-trips |
| The accrual engine gets built here because the columns are here | medium | Named in **Out of scope**; `W-16.2` owns it, and this ticket stores configuration only |
| HRMS's hard-coded 24/12/8/90/10 are seeded as if they were a standard | medium | They are one employer's numbers (`LeaveBalance.java:9-72`); `W-12.1` seeds nothing, and a new tenant defines its own |
| Policy is edited in place and last year's balance cannot be explained | medium | `effective_from` versioning, asserted by `LeavePolicyVersionTest` |
| Encashment columns are added back "while we are here" | medium | Decision 2 removed them deliberately; a column no code reads is a promise the product does not keep |

## 10. Rollback

Nothing is deployed. Scripts are additive and forward-only — `migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | all three tables, each in its own script |
| Flyway only, `ddl-auto` nowhere | two or three scripts; none added |
| `Money`/`BigDecimal` for money | creates no money column; day counts are `numeric(10,2)`, never float |
| Index on `tenant_id` plus lookup columns | `tenant_id` leads every index |
| Expand / contract | new tables only |
| No module references another module | `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-003 half-day LOP precision (`GAP_INVENTORY.md:29`) | **Fixed at the source.** Every day count is `numeric(10,2)` from this table onward. The inventory records it resolved on the HRMS side while Payroll still truncates — this removes the truncating side |
| DEBT-013 package typo `leaveAndAttedance/` (`:51`) | **Discounted.** New code is `core/.../leave/`; the frozen package is untouched |
| DEBT-018 missing tenant indexes | **Honoured** |
| `expiration_date` never enforced (`EmployeeLeaveAllocation.java:79`) | **Deferred to `W-16.2`**, which owns balances. The policy here records the window; enforcing it needs an allocation |

## 13. Decisions — settled 2026-09-22

| # | Question | Decision |
|---|---|---|
| 1 | Eligibility as a third table in this ticket | **Yes**, aggregate exception, as with `W-13.2` and `W-17` |
| 2 | Carry Payroll's encashment settings forward | **No — leave them out entirely.** Against my recommendation |
| 3 | Hour-based leave | **Days only**; the `unit` column exists and the API rejects anything but day-based |
| 4 | Mandatory documents per leave type | **Yes** — `requires_document` on the policy, added by `W-16.3`'s need and specified here |

**Decision 2 removes `encashment_enabled` and `encashment_cap` from the table.** The reasoning
against carrying them: a column that no code reads and no screen offers is a claim the product
does not honour, and someone will eventually build against it assuming it works. The cost is
that adding encashment later needs a migration rather than a code change — accepted
deliberately.

Nothing else changes. Payroll's encashment configuration is not ported, and `W-67` has no
encashment data to migrate because the feature was never implemented there either.
