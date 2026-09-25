# Feature: Pay run — creation, employee inclusion, locking

| Field | Value |
|---|---|
| **Feature ID** | `W-29.1` · from ticket #33 · `PAY-05` part 1 of 4 |
| **Promoted to** | `docs/target-state/features/W-29-1-pay-run-creation.md` on the developer's `dev-<name>` branch — **`W-29-1` with hyphens**, never `W-29.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration`, one read-only method in `code/backend/core` (§4, exception) |
| **Related gaps** | BUG-005 (discounted), BUG-010 and BUG-011 (proposed in `.claude/outputs/2026-09-25-analyze-w29-pay-run.md`), DEBT-007, DEBT-008, DEBT-018, DEBT-022 |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-28` — `PayPeriodService.periodFor` gives the run its dates · `W-26.2` — `versionInForce` decides who has a salary · `W-19` — `PayInputService.lock` is what locking means. `W-52` is on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll`, plus **one read-only method on `core.employee.EmployeeService`** — no table, no migration, no endpoint. Exception noted 2026-09-25: the seam `12-core-contracts.md` §3 requires but nothing has added yet | 1 |
| Flyway migration | **2 scripts, one table each** — `payroll.payrun`, `payroll.employee_payrun`. Exception noted 2026-09-25, same shape as `W-19` | 1 |
| Externally testable behaviour | an officer creates the run for a month, sees who is in it and why anyone is out, locks it, and cannot create a second run for that month | 1 |
| Frontend area | none — `W-47` builds the pay run screens | 1 |

Within cap with the two exceptions. `employee_payrun` is a child of `payrun` and cannot exist
without it. The `core` method is a list read that the rest of `W-29` and `W-36` will also use.

## What the four parts share

`W-29.1` creates the run and its rows. `W-29.2` adds the money columns and computes them.
`W-29.3` applies loss of pay and collects `W-19` inputs. `W-29.4` moves the computation onto the
worker. **This part owns the table shape and the status vocabulary**; the later parts add
columns by expand-only scripts and add transitions, never new statuses.

---

## 1. Problem

The frozen Payroll backend has a pay run, and it is a port. Four things are wrong with it.

- **Two runs for one month are possible.** The check
  `existsByOrganizationAndPayPeriodStartDateAndPayPeriodEndDate`
  (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/repository/payruns/PayRunRepository.java:35-36`)
  is declared and never called; `createPayRun` saves without looking
  (`legacy/.../serviceimpl/payruns/PayRunServiceImpl.java:413-433`). `09-build-order.md:219`:
  *"Two runs for one period is the failure that costs real money."*
- **Nothing locks.** `isClaimsAndDeclarationsLockedForAllEmployees` (`.../entity/payruns/PayRun.java:141`)
  drives a screen and nothing else. `W-19` §1 says the same
- **Who is in and who is out is invisible.** Inclusion is a stream filter — status `ACTIVE`, a
  CTC in force, a bank row, a personal row (`.../serviceimpl/payruns/EmployeePayRunServiceImpl.java:158-191`).
  Anyone who fails it vanishes; the run keeps only a count, `noOfSkippedEmployees`
  (`PayRun.java:101-102`). An employee who left mid-month is dropped entirely, unpaid for the days worked
- **Any field can be set to anything.** `PUT /api/payruns/{id}` copies `status` straight from
  the request (`PayRunServiceImpl.java:639-640`), so a client can mark a run `COMPLETED`.
  A random 10-digit `payrunId` (`:425`), `processingPeriod` as `"July 2025"` text (`PayRun.java:47-48`),
  `baseDays` as `Double` (`:98-99`), no `/api/v1` — DEBT-007, DEBT-008

## 2. Scope

**In scope**

- `payroll.payrun` — one row per tenant per period, dates copied from `W-28`
- `payroll.employee_payrun` — one row per employee the run looked at, `INCLUDED` or `SKIPPED`
  with the reason
- Create, read, list, lock, cancel. Lock calls `PayInputService.lock(period)`
- The status vocabulary for all of `W-29`, with a `CHECK`
- The unique rule: one non-cancelled run per tenant and period, as an index

**Out of scope**

- Any money: earnings, deductions, net, totals — `W-29.2` adds the columns
- Loss of pay, pay inputs, reimbursements, ad-hoc deductions — `W-29.3`
- Running on the worker, progress — `W-29.4`
- Approval through `ApprovalFlowType.PAY_RUN` (`W-15.2`), payment and payslips (`W-36`) —
  their transitions land when they land; the statuses exist from day one
- Off-cycle runs (`W-30`): `run_type` has one value and a `CHECK`
- Screens — `W-47`

## 3. Flow

```
[payroll officer] --> POST /payruns {period} --> permission payroll.run.execute
   --> PayPeriodService.periodFor(period)            (W-28; 409 when no schedule)
   --> EmployeeService.listEmployedBetween(start,end) (core, the one new method)
   --> per employee: EmployeeSalaryService.versionInForce(tenant, employee, end)  (W-26.2)
                     EmployeeBankService.get(employee)                            (core, built)
   --> [payroll.payrun DRAFT] + [payroll.employee_payrun INCLUDED | SKIPPED reason]
   --> unique index refuses a second run for the period --> 409

[payroll officer] --> POST /payruns/{id}/lock --> DRAFT only, else 409
   --> PayInputService.lock(period)   (W-19; idempotent)
   --> status LOCKED, locked_at, locked_by

[payroll officer] --> POST /payruns/{id}/cancel --> DRAFT or LOCKED, else 409
   --> status CANCELLED; the period lock stays (W-19 §6: a lock is never undone)
```

**Inclusion rule**, replacing `EmployeePayRunServiceImpl.java:158-191`:

| Test | Result | Legacy |
|---|---|---|
| not soft-deleted, `date_of_joining <= period_end`, and either `ACTIVE`, or `TERMINATED` with `termination_date >= period_start` | considered | `ACTIVE` only (`:154-159`) — leavers were dropped |
| `SUSPENDED`, or terminated before the period, or joined after it | **not a row at all** | — |
| no salary version in force at `period_end` | `SKIPPED`, `NO_SALARY` | dropped silently (`:181-186`) |
| no bank section | `SKIPPED`, `NO_BANK_DETAILS` | dropped silently (`:189`) |
| both present | `INCLUDED`, `salary_version_id` recorded | — |

The personal-details test (`:190`) is **not ported**: nothing in the pay calculation reads it.
Skip reasons are an enum; `W-29.2` may add `NO_STATUTORY_PROFILE` to it.

**Status vocabulary** (`PayRunStatus`): `DRAFT`, `LOCKED`, `COMPUTING`, `COMPUTED`, `FAILED`,
`APPROVED`, `PAID`, `CANCELLED`. This ticket implements `DRAFT → LOCKED`, `DRAFT → CANCELLED`,
`LOCKED → CANCELLED`. Every other transition throws `IllegalPayRunTransitionException` (`409`)
until its ticket arrives. Legacy's seven (`.../enumeration/payruns/PayRunStatus.java:3-10`)
map: `DRAFT`→`DRAFT`, `SUBMITTED`/`APPROVAL_PENDING`→`COMPUTED`, `APPROVED`→`APPROVED`,
`COMPLETED`→`PAID`, `REJECTED`→`LOCKED` (a rejected run is recomputed, not rebuilt), `READY` unused.

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/`, except the last row.

| Layer | File | Change |
|---|---|---|
| Entity | `PayRun.java`, `EmployeePayRun.java` | new, `@Table(schema = "payroll")`, `UUID id`, `UUID tenantId`, `@Audited` |
| Repository | `PayRunRepository.java`, `EmployeePayRunRepository.java` | new; every finder takes `tenantId` |
| Service / ServiceImpl | `PayRunService`, `PayRunServiceImpl` | new — `create(YearMonth)`, `get`, `list`, `employees`, `lock`, `cancel` |
| Service | `PayRunInclusionService`, `PayRunInclusionServiceImpl` | new — the inclusion rule, one method, returns the rows to write; pure enough to unit-test |
| Enumeration | `PayRunStatus`, `PayRunType`, `InclusionStatus`, `SkipReason` | new |
| Exception | `DuplicatePayRunException` (`409`), `IllegalPayRunTransitionException` (`409`) | new, envelope per `CONVENTIONS.md` §3 |
| Controller | `PayRunController.java` | new |
| DTO | `CreatePayRunRequest`, `PayRunResponse`, `EmployeePayRunResponse` | new; `status` / `message` / `data` envelope |
| **core** | `core/.../employee/EmployeeService.java`, `EmployeeServiceImpl`, `EmployeeRepository` | **change** — add `List<EmployeeResponse> listEmployedBetween(LocalDate start, LocalDate end)`: tenant-bound, not deleted, the "considered" row of the table above, ordered by `employee_number`. One JPQL query. Nothing else in `core` changes |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/payroll/payruns` | `{period: "YYYY-MM"}` | `201` the run with counts; `409` `DuplicatePayRun` when a non-cancelled run exists; `409` when no pay schedule (`W-28`); `400` period before `first_period_start` | `payroll.run.execute` |
| GET | `/api/v1/payroll/payruns` | `?status=&page=&size=` | page, newest period first | `payroll.run.read` |
| GET | `/api/v1/payroll/payruns/{id}` | — | the run, counts of included and skipped | `payroll.run.read` |
| GET | `/api/v1/payroll/payruns/{id}/employees` | `?inclusion=INCLUDED\|SKIPPED&page=&size=` | rows with `employee_id`, `employee_number`, `inclusion_status`, `skip_reason` | `payroll.run.read` |
| POST | `/api/v1/payroll/payruns/{id}/lock` | — | `200` `LOCKED`; `409` unless `DRAFT` | `payroll.run.execute` |
| POST | `/api/v1/payroll/payruns/{id}/cancel` | — | `200` `CANCELLED`; `409` unless `DRAFT` or `LOCKED` | `payroll.run.execute` |

Permission codes exist: `payroll.run.read`, `payroll.run.execute`
(`code/backend/migration/src/main/resources/db/migration/core/V025__catalogue_correction.sql:198-199`).
`lock` also needs `core.pay_input.lock` (`:61`) on the caller — `W-19` §4 guards its service by
that code; the `payroll-officer` role must hold it, which `W-19` grants. No new code.

Not ported: `PUT /api/payruns/{id}` (`PayRunController.java:57`, the free update),
`DELETE` (`:126` — cancel instead, the row stays), `GET /completed` (`:69` — a `status` filter).

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V055__payrun.sql` | `payroll.payrun` | yes | additive |
| `payroll/V056__employee_payrun.sql` | `payroll.employee_payrun` | yes | additive |

`V055`–`V056` extend the payroll lane's block by two (`DEV-TRACKER.md` § lanes). Use the
lane's number at branch time if it has moved.

**`payrun`**, from `PayRun.java:19-152` keeping only what part 1 needs:
`id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`period char(7) NOT NULL CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$')` ·
`period_start date NOT NULL` · `period_end date NOT NULL` · `cutoff_date date NOT NULL` ·
`pay_date date NOT NULL` — all four copied from `periodFor` (`W-28` §13 decision 3: a run keeps the date it was paid on) ·
`run_type varchar(16) NOT NULL DEFAULT 'REGULAR' CHECK (run_type IN ('REGULAR'))` ·
`status varchar(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','LOCKED','COMPUTING','COMPUTED','FAILED','APPROVED','PAID','CANCELLED'))` ·
`included_count int NOT NULL DEFAULT 0` · `skipped_count int NOT NULL DEFAULT 0` ·
`locked_at timestamptz` · `locked_by varchar(100)` · `cancelled_at timestamptz` · `cancelled_by varchar(100)` ·
four audit columns as `V010__employee.sql:19-22`.

**`employee_payrun`**, from `EmployeePayRun.java:18-108` keeping only identity and inclusion:
`id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`payrun_id uuid NOT NULL REFERENCES payroll.payrun(id)` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`salary_version_id uuid REFERENCES payroll.ctc_structure(id)` — set when `INCLUDED` ·
`inclusion_status varchar(16) NOT NULL CHECK (inclusion_status IN ('INCLUDED','SKIPPED'))` ·
`skip_reason varchar(32) CHECK ((inclusion_status = 'SKIPPED') = (skip_reason IS NOT NULL))` ·
four audit columns.

| Legacy field | Here |
|---|---|
| `payrunId` random 10-digit (`PayRun.java:21-22`, `PayRunServiceImpl.java:425`) | **dropped** — `id uuid` |
| `processingPeriod` `"July 2025"` (`:47-48`) | `period` `'2025-07'`, `CHECK`, the `W-19` shape |
| `payPeriodStartDate`, `payPeriodEndDate`, `payDate` (`:38-45`) | kept, plus `cutoff_date`, all from `W-28` |
| `status` seven values (`PayRunStatus.java:3-10`) | eight values, `CHECK`, mapping in §3 |
| `noOfEmployees`, `noOfSkippedEmployees` (`:56-57`, `:101-102`) | `included_count`, `skipped_count`, and **the skipped rows themselves** |
| 13 `BigDecimal` totals, `baseDays Double` (`:50-120`) | **`W-29.2`** — no money column here |
| `isClaimsAndDeclarationsLockedForAllEmployees` (`:141`) | `status = LOCKED` + `locked_at/by`; the real lock is `core.pay_input_period_lock` |
| `employeeNumber`, `employeeName`, `fullName` snapshots (`EmployeePayRun.java:24-30`) | **not stored** — read from `core.employee`; `W-36` decides whether a payslip needs a name snapshot |
| `paymentStatus`, `paymentMode` (`:33-36`) | **`W-36`** |
| 15 `Double` money and day fields (`:39-102`) | **`W-29.2`**, as `numeric` |

- [x] `tenant_id`, leading index column, on both tables
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — **`uk_payrun_tenant_period UNIQUE (tenant_id, period) WHERE status <> 'CANCELLED'`** — the BUG-010 fix, a partial unique index, not a service check · `idx_payrun_tenant_status (tenant_id, status)` · `uk_employee_payrun_tenant_run_employee UNIQUE (tenant_id, payrun_id, employee_id)` · `idx_employee_payrun_tenant_run_inclusion (tenant_id, payrun_id, inclusion_status)`
- [x] No money column created. `included_count` and `skipped_count` are `int` counts of rows, not day counts
- [x] Expand / contract — two new tables, no destructive step

RLS and `tenant_isolation` in the exact `CASE` form on both — `migration/README.md` §row-level security.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../payrun/PayRunInclusionRulesTest.java` | each row of the §3 table; a leaver on `period_start` is considered, one on the day before is not; the `salary_version_id` on an `INCLUDED` row |
| Unit | `payroll/.../payrun/PayRunTransitionsTest.java` | the three allowed transitions; every other pair throws; `cancel` twice throws |
| Integration | `payroll/.../payrun/PayRunCreateIT.java` | create returns `201` with the four dates equal to `periodFor`; a second create for the period is `409`; after cancel a new create for the same period succeeds; **two concurrent creates for one period leave exactly one non-cancelled row** — the index, not the service, decides |
| Integration | `payroll/.../payrun/PayRunLockIT.java` | lock writes one `core.pay_input_period_lock` row for `(tenant, period)`; a `PayInputService.record` for that period afterwards posts to the next period; lock on a `LOCKED` run is `409`; cancel after lock leaves the lock row |
| Integration | `payroll/.../payrun/PayRunInclusionIT.java` | five employees: included; no salary; no bank; terminated last month; joined next month — rows are `INCLUDED`, `SKIPPED NO_SALARY`, `SKIPPED NO_BANK_DETAILS`, absent, absent; counts on the run match |
| Integration | `payroll/.../payrun/PayRunRlsIT.java` | as `app_user`, tenant A cannot read, lock or cancel tenant B's run; tenant B may create a run for the period tenant A already has |
| Integration | `payroll/.../payrun/PayRunGuardIT.java` | `payroll.run.read` alone gets `403` on create, lock and cancel |
| Integration | `core/.../employee/EmployeeListEmployedBetweenIT.java` | the new method returns the "considered" set, one statement (`QueryCountIT` style, `W-55`), never a soft-deleted row |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. The ITs write their
schedule through `W-28`'s service and their salaries through `W-26.2`'s, which is why both block.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in payrun employee_payrun; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT relname, relrowsecurity FROM pg_class WHERE oid='payroll.$t'::regclass;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexdef FROM pg_indexes WHERE schemaname='payroll' AND indexname='uk_payrun_tenant_period';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name IN ('payrun','employee_payrun')
      AND data_type IN ('double precision','real','numeric');"
cd code/backend && mvn -q verify
git diff --stat main -- code/backend/core
```

| Check | Expected |
|---|---|
| RLS | `t` for both tables |
| Index | one row, `UNIQUE ... (tenant_id, period) WHERE ((status)::text <> 'CANCELLED'::text)` |
| Money columns | `0` — none exist yet |
| Suite | green, no skips; `PayRunCreateIT` concurrent case passes |
| `core` diff | three files only: `EmployeeService`, `EmployeeServiceImpl`, `EmployeeRepository`, plus the one new IT |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The money columns are added "while we are here" | **high** — every legacy field is in front of the implementer | §6 says `W-29.2` four times; the verification counts numeric columns and expects zero |
| The duplicate check is written as `findBy…isPresent()` and two officers race | medium | The partial unique index; `PayRunCreateIT` runs two creates concurrently and asserts one row |
| Cancel undoes the `W-19` period lock | medium | `W-19` §6: never undone by the application. `PayRunLockIT` asserts the lock row survives cancel |
| Status set from the request body, as `PayRunServiceImpl.java:639-640` | medium | No status in any request DTO; transitions are methods; `PayRunTransitionsTest` |
| `payroll` reads `core.employee` with its own query instead of the seam | medium | The `core` diff check in §8, and `grep -rn "core.employee" code/backend/payroll/src/main` returns only `EmployeeService` and `EmployeeBankService` imports |
| The `core` method drifts into a general search | low | One JPQL query, two date parameters, no paging; `W-13.3` owns search |

## 10. Rollback

Nothing is deployed. Both scripts are additive and forward-only. A run in `DRAFT` or
`LOCKED` is cancelled, never deleted.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `payroll.payrun`, `payroll.employee_payrun` |
| Flyway only, `ddl-auto` nowhere | two scripts, `V055`–`V056` |
| `Money`/`BigDecimal` for money | no money column created; counts are `int` |
| Index on `tenant_id` plus lookup columns | four indexes, §6 |
| Expand / contract | new tables only; `W-29.2` adds columns, drops none |
| No module references another module | `payroll` references `core` services (`EmployeeService`, `EmployeeBankService`, `PayInputService`) and `shared` only. `EmployeeSalaryService` and `PayPeriodService` are `payroll`'s own |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-005 pay run fails when HRMS is down | **Discounted** — no HRMS call exists; LOP is `W-29.3`'s and reads platform tables |
| BUG-010 duplicate runs (proposed) | **Fixed** — `uk_payrun_tenant_period` |
| BUG-011 money as `Double` (proposed) | **Deferred to `W-29.2`** — no money column here |
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-008 hand-built envelope (`PayRunController.java:42`) | **Fixed** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders (`findByPayrunId`) | **Fixed** — every finder takes `tenantId` |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Who is in the run? | Everyone employed on any day of the period, then salary and bank decide `INCLUDED` or `SKIPPED`. Legacy took `ACTIVE` only and dropped leavers unpaid; that is a defect, not a rule |
| 2 | Where do skipped employees go? | **Into `employee_payrun` with a reason.** A count (`PayRun.java:101`) tells the officer nothing they can act on |
| 3 | What does "lock" mean? | `PayInputService.lock(period)` plus `status = LOCKED`. Not a flag on the run — `W-19` §6 |
| 4 | Can a locked run be cancelled? | **Yes**, and the period lock stays. The next run for the period reads the same frozen inputs; `W-19`'s lock is idempotent |
| 5 | Status values for all four parts now, or grow them? | **All eight now, with the `CHECK`.** Later parts add transitions; a status added by `ALTER … CHECK` on a live table is a lock on the hottest payroll table |
| 6 | Where does the employee list come from? | One new read on `core` `EmployeeService`. `W-13.3`'s search is paged and free-text, the wrong shape for a batch that must be complete |
| 7 | Snapshot employee name and number on the row? | **No.** Nothing in parts 1–4 needs it; `W-36` decides for payslips |
