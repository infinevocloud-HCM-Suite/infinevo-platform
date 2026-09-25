# Feature: Leave allocation and balance calculation

| Field | Value |
|---|---|
| **Feature ID** | `W-16.2` · ticket #18 · `CORE-07` |
| **Promoted to** | `docs/target-state/features/W-16-2-allocation-balance.md` on branch `W-16-2-allocation-balance` — **`W-16-2` with hyphens**, never `W-16.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | BUG-003 (fixed), DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-16.1` — an allocation needs a type and a policy; `W-11.3` for the `core.leave_balance.manage` and `core.leave.read*` codes (`12-core-contracts.md` §4) |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 rows 3, 23 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one script, one table — `core.leave_allocation` | 1 |
| Externally testable behaviour | an employee's balance for a type and a year is correct, including accrual and carry-forward | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

Neither product calculates a balance. One stores a number an administrator typed; the other
recomputes a number from rows that keep moving.

- HRMS keeps two parallel balance tables. `LeaveBalance.java:9-72` holds per-year totals with the entitlements as Java defaults — annual 24, sick 12, casual 8, maternity 90, paternity 10 — and `EmployeeLeaveBalance.java:8-97` holds a per-type `remaining_days` with no breakdown behind it. `02-data-model.md:263` records that nobody has yet proved which of the pair is authoritative
- Payroll's allocation is one row per organisation, employee, type and year — `EmployeeLeaveAllocation.java:9-228` — with `annual_days` and `carried_forward_days` as **integers**
- The balance is re-derived after every change by `recalculateConsumptionBalances()` — `EmployeeLeaveAllocationServiceImpl.java:666-718` — walking every consumption row and accumulating a running total
- **The accrual configuration is never read.** `LeaveType.java:89-96` in Payroll defines frequency and units; no code accrues anything. Allocations are snapshots, typed or imported
- `expiration_date` exists on the allocation — `EmployeeLeaveAllocation.java:79` — and is never enforced

So a balance today is whatever the last recalculation wrote, an integer, with no record of how
it got there and no accrual behind it.

## 2. Scope

**In scope**

- `core.leave_allocation` — one row per tenant, employee, leave type and leave year
- Opening balance, accrued to date, carried forward, and the carry-forward expiry the policy defines
- The accrual engine the policy has been describing since `W-16.1` — `monthly` or `yearly`, the two frequencies the frozen screen offers (`legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/leaveAttendence/addLeaveTypes.js:403-404`)
- The reset the policy describes — `yearly`, `monthly`, `quarterly` or `halfYearly` (`addLeaveTypes.js:471-474`): at each reset boundary the unused balance is dropped, or carried forward up to `carry_forward_cap` when carry-forward is enabled
- Pro-rating for an employee who joins or leaves mid-year
- A balance read that explains itself: entitlement, accrued, carried forward, consumed, remaining

**Out of scope**

- Consumption rows — `W-16.4a` owns `leave_consumption`; this ticket reads a total from it and treats zero as valid until it exists
- Requests — `W-16.3`
- Loss of pay — `W-16.4a`
- Bulk import of opening balances — `W-16.4b`

Encashment is not mentioned here on purpose: `W-16.1` decision 2 carries no encashment column,
so there is no flag for this ticket to leave alone.

## 3. Flow

```
[scheduled accrual, or a policy change] --> [LeaveAccrualService]
   --> [core.leave_allocation, one row per employee/type/year]

[anyone] --> [LeaveBalanceService.balanceOf(employee, type, asOf)]
   --> entitlement + accrued + carried_forward - consumed = remaining
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../leave/LeaveAllocationController.java` | new |
| Service | `core/.../leave/LeaveAllocationService.java` | new |
| Service | `core/.../leave/LeaveAccrualService.java` | new — the engine, `monthly` and `yearly` |
| Service | `core/.../leave/LeaveResetService.java` | new — applies `reset_frequency` at each boundary, with carry-forward |
| Job | `worker/.../leave/LeaveAccrualJob.java` | new — `@Scheduled` + `@SchedulerLock`, calls accrual then reset |
| Service | `core/.../leave/LeaveBalanceService.java` | new — the read side `W-16.3` uses |
| Entity | `core/.../leave/LeaveAllocation.java` | new, `@Table(schema="core")` |
| Repository | `core/.../leave/LeaveAllocationRepository.java` | new |
| DTO | `core/.../leave/LeaveBalanceResponse.java` | new |

**API contract**

| Method | Path | Request | Response | `@RequiresAction` |
|---|---|---|---|---|
| GET | `/api/v1/employees/{id}/leave-balances` | `?asOf=` | balance per type, with the working shown | `core.leave.read`; `core.leave.read_own` when `{id}` is the caller, `core.leave.read_team` when `{id}` reports to the caller |
| POST | `/api/v1/leave-allocations` | employeeId, typeId, year, openingDays | `201` — manual allocation | `core.leave_balance.manage` |
| POST | `/api/v1/leave-allocations/accrue` | `?asOf=` | `200` + count accrued | `core.leave_balance.manage` |

Every endpoint is tenant bound and carries the code shown — `EndpointGuardCoverageTest` fails
otherwise (`12-core-contracts.md` §2). The codes arrive with `W-11.3` (`12-core-contracts.md` §4).

The accrual endpoint is callable by hand so it can be tested and re-run. Scheduling it is
a `@Scheduled` + `@SchedulerLock` job on `worker`, using `W-52`'s ShedLock (`core.shedlock`,
`M/core/V006`) — not a timer inside `app`, which would fire once per replica, and not `W-20.2`'s
reminder scheduler, which owns notifications and nothing else (`12-core-contracts.md` §5 row 3).

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__leave_allocation.sql` | `core.leave_allocation` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`leave_type_id uuid NOT NULL REFERENCES core.leave_type(id)` ·
`leave_year varchar(9) NOT NULL` — `2026` or `2026-27` · `year_start_date date NOT NULL` ·
`year_end_date date NOT NULL` · `entitlement_days numeric(10,2) NOT NULL` ·
`accrued_days numeric(10,2) NOT NULL DEFAULT 0` · `carried_forward_days numeric(10,2) NOT NULL DEFAULT 0` ·
`carry_forward_expires_on date NULL` · `pro_rate_factor numeric(5,4) NOT NULL DEFAULT 1` ·
`last_accrued_on date NULL` · `last_reset_on date NULL` ·
`policy_id uuid NOT NULL REFERENCES core.leave_policy(id)` · four audit columns.

Day counts are `numeric(10,2)` — `CONVENTIONS.md:37`, and `12-core-contracts.md` §5 row 23.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, employee_id, leave_type_id, leave_year)` unique, `(tenant_id, last_accrued_on)` for the accrual sweep
- [x] **Money columns — none.** Every day count is `numeric(10,2)`; `pro_rate_factor` is `numeric(5,4)`
- [x] Expand / contract — new table only

**No `consumed_days` column, deliberately.** Payroll keeps a `consumed_days` integer on the
allocation *and* recomputes it from consumption rows — `EmployeeLeaveAllocationServiceImpl.java:666-718`
— which is two sources for one number. Consumption is summed from `core.leave_consumption`
when `W-16.4a` creates it; until then the sum is zero.

**`policy_id` is stored on the row, and it moves when the policy changes.** Decision 2 settled
that a policy change applies to the leave year in progress, so the allocation recalculates and
`policy_id` points at the new version. The previous entitlement survives only in the audit
trail (`W-22.1`) — which makes that audit row the record of what an employee was originally
told, and therefore not optional.

**`leave_year` is a string with explicit start and end dates.** HRMS hard-codes a calendar
year; Payroll stores a year as a `String` and never distinguishes financial from calendar.
An employer on April-to-March is the normal case in India, and neither product supports it.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../leave/LeaveAccrualServiceTest.java` | `monthly` and `yearly` frequencies; re-running for the same period accrues nothing twice |
| Unit | `core/.../leave/LeaveResetServiceTest.java` | `yearly`, `monthly`, `quarterly` and `halfYearly` boundaries; unused balance drops at the boundary, or carries forward up to the cap; re-running for the same boundary resets nothing twice (`last_reset_on`) |
| Unit | `core/.../leave/LeaveProRateTest.java` | joining mid-year and leaving mid-year; a full year is a factor of exactly 1 |
| Unit | `core/.../leave/LeaveBalanceServiceTest.java` | remaining = entitlement + accrued + carried forward − consumed; half-days survive |
| Integration | `core/.../leave/LeaveAllocationRlsIT.java` | tenant A cannot read tenant B's allocations as `app_user` |
| Integration | `core/.../leave/LeaveCarryForwardExpiryIT.java` | carried-forward days stop counting after `carry_forward_expires_on` |
| Integration | `core/.../leave/MidYearPolicyChangeIT.java` | cutting entitlement mid-year recalculates every current-year allocation, reports who becomes over-drawn **before** applying, and writes an audit row carrying the previous entitlement |
| Integration | `core/.../leave/LeaveAccrualJobIT.java` | the `worker` job takes a `core.shedlock` row; a second instance started concurrently accrues nothing |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`LeaveAccrualServiceTest`'s idempotence case is the important one. `last_accrued_on` is what
stops a re-run doubling an employee's entitlement, and a re-run is how the job recovers from
a failure.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.leave_allocation'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type, numeric_scale FROM information_schema.columns
    WHERE table_schema='core' AND table_name='leave_allocation'
      AND column_name LIKE '%days%' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name FROM information_schema.columns
    WHERE table_schema='core' AND table_name='leave_allocation' AND column_name='consumed_days';"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Day columns | `numeric`, scale 2 |
| `consumed_days` | **no rows** — the column must not exist |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Accrual runs twice and doubles entitlement | **high — the obvious failure of any sweep** | `last_accrued_on` plus the idempotence test; `D-50` already warns that Storage Queue does not guarantee ordering |
| A `consumed_days` column is added back "for performance" | medium | Verification asserts the column does not exist |
| Carry-forward expiry is stored and never enforced, as in Payroll | medium | `LeaveCarryForwardExpiryIT` asserts the balance, not the column |
| Integer day counts creep back through a DTO or a mapper | medium | Every field is `BigDecimal`; the half-day case is asserted in the balance test |
| The financial-year model is built and every consumer assumes calendar | medium | `year_start_date` and `year_end_date` are on the row; no consumer derives a year from a date |
| Pro-rating disagrees with payroll's own proration | medium | Payroll prorates salary from date of joining — `EmployeePayRunServiceImpl.java:1132-1151`. Leave proration is a separate factor and must not be reused for pay |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only — `migration/README.md:135-143`.
Accrual can be re-derived from the policy; nothing here is destructive.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.leave_allocation` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | no money column; every day count is `BigDecimal` / `numeric(10,2)` |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-003 half-day precision (`GAP_INVENTORY.md:29`) | **Fixed.** No integer day count exists anywhere in this table |
| `expiration_date` never enforced (`EmployeeLeaveAllocation.java:79`) | **Fixed.** `carry_forward_expires_on` is enforced by the balance calculation and asserted by a test |
| Two HRMS balance tables, neither proved authoritative (`02-data-model.md:263`) | **Resolved by replacement.** One allocation row is the only source; which legacy table to migrate from is `W-67`'s problem |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

| # | Question | Decision |
|---|---|---|
| 1 | Who runs accrual? | ~~**`W-20.2`'s shared scheduler**~~ — **corrected 2026-09-25: a `@Scheduled` + `@SchedulerLock` job on `worker` under `W-52`'s ShedLock** (`12-core-contracts.md` §5 row 3). Not a timer in `app`, and not the reminder scheduler |
| 2 | A policy changes mid-year | **It applies immediately, to the year in progress.** Against my recommendation of deferring to the next leave year |

**Decision 2 changes this spec's shape.** The allocation cannot freeze `entitlement_days` at
year start:

- On a policy change, every allocation for the current leave year **recalculates** its entitlement, accrual rate and carry-forward cap from the new policy
- `policy_id` is updated to the new version, and the recalculation writes an audit row through `W-22.1` — under this choice the audit trail is the only record of what the entitlement used to be
- **A consequence to surface in the UI:** an employee who has already taken more than the new entitlement becomes over-drawn, and — when the policy's `exceed_balance_mode` is `markAsLOP`, the only mode that produces loss of pay (`W-16.1` §6, `12-core-contracts.md` §5 row 2) — the excess becomes loss of pay through `W-16.4a`. That can turn an already-approved absence into an unpaid one, which is a real outcome the admin making the change should be warned about before confirming

The recalculation is therefore not silent: the policy-change endpoint returns how many
employees would become over-drawn, and by how much, before the change is applied.
