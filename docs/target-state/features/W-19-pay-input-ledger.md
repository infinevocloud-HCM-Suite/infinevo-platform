# Feature: Pay input ledger

| Field | Value |
|---|---|
| **Feature ID** | `W-19` · ticket #23 · `CORE-10` |
| **Promoted to** | `docs/target-state/features/W-19-pay-input-ledger.md` on branch `W-19-pay-input-ledger` |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-033 (fixed), incident 2 (fixed), DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-13.1` — an input names an employee; `W-11.3` — the `core.pay_input.read/write/lock` codes (`12-core-contracts.md` §4) |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 rows 1, 23 and §6 decisions 2–3 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | **2 scripts, one table each** — `core.pay_input`, `core.pay_input_period_lock` — aggregate exception | 1 — exception noted 2026-09-25, same shape as `W-16.1` and `W-17` |
| Externally testable behaviour | a module writes a value, the pay run reads it, and neither can change it once the period is locked | 1 |
| Frontend area | none | 1 |

Within cap. `pay_input_period_lock` is a satellite of `pay_input` and exists only because
`app_user` cannot `UPDATE` the ledger (`12-core-contracts.md` §5 row 1) — a lock has to be a
row of its own, not a flag on a row that is already immutable.

---

## 1. Problem

Today a pay-affecting value reaches payroll by one module calling the other over HTTP, and
that call is the whole mechanism.

- The pay run calls out to HRMS mid-calculation — `legacy/Payroll-Bend-SBoot/.../serviceimpl/payruns/EmployeePayRunServiceImpl.java:1091`
- It reaches `POST /public/get-employee-leaves` on HRMS — `legacy/HRMS_Backend/.../config/IntegrateWithPayroll.java:29,64`
- That endpoint has **no authentication** and returns employee leave data; it is incident 2 in `active-work.md`
- What stands in for authentication is `X-API-KEY: md5("12345AB")`, a shared secret committed in plaintext — `legacy/docs/GAP_INVENTORY.md:83` (DEBT-033)

The other inputs do not travel at all — they are read straight out of payroll's own tables at
run time.

| Input | Where it lives today | How it reaches a payslip |
|---|---|---|
| Loss-of-pay days | HRMS `employee_monthly_lop` | cross-service HTTP call at run time |
| Reimbursements | Payroll `employee_reimbursement_request` | direct query — `EmployeePayRunServiceImpl.java:354` |
| Ad-hoc deductions | Payroll `employee_deduction` | direct query — `EmployeePayRunServiceImpl.java:337` |
| One-time payouts | Payroll `one_time_payout` | no evidence it reaches a standard run |
| Overtime | HRMS only | **never reaches payroll at all** |

**Nothing locks.** The only thing resembling a lock is
`PayRun.isClaimsAndDeclarationsLockedForAllEmployees`, a flag driving a screen, and deductions
moving to status `INPAYRUN` after they are consumed — `EmployeePayRunServiceImpl.java:363`.
A reimbursement amended after a run is computed changes what the run was based on, and nothing
records that it happened.

So a payslip cannot be explained after the fact: the numbers behind it were read from tables
that have since moved on.

## 2. Scope

**In scope**

- `core.pay_input` — an append-only ledger of pay-affecting values
- A write API any module calls, naming employee, period, kind, quantity or amount, and a source reference
- A read API the pay run calls for one employee and period, and a batch read for a whole period
- Period locking: once locked, no write for that tenant and period succeeds — a row in `core.pay_input_period_lock`, refused by an insert trigger on the ledger
- Idempotency: unique `(tenant_id, source_module, source_ref)` on non-reversal rows, so a retried caller cannot post twice (`12-core-contracts.md` §6 decision 2)
- Late input: a write for a locked period is **not lost** — the service moves it to the next open period and says so in the response (`12-core-contracts.md` §6 decision 3)

**Out of scope**

- Any calculation. `09-build-order.md:200`: *"keep it dumb. It is a ledger, not a calculation engine"*
- The pay run reading it — `W-29`
- Overtime capture that produces a value — `CORE-21`
- Deleting the legacy HTTP integration — that dies with the frozen system, not here
- Approval of an input before it counts — `W-15`

## 3. Flow

```
[hrms or core module] --> [PayInputService.record(PayInputCommand)] --> period locked? --> next open period
                                                                    --> [core.pay_input, append only]
[payroll pay run]     --> [PayInputService.forEmployee(employee, period)] --> [sum by kind]
[payroll pay run]     --> [PayInputService.forPeriod(period)] --> every employee's inputs, one query
[payroll pay run]     --> [PayInputService.lock(period)] --> [core.pay_input_period_lock] --> trigger refuses further inserts
```

No module calls another. Both sides talk to `core`, which is exactly what
`09-build-order.md:200` means by *"HRMS overtime reaches a payslip without Payroll knowing
HRMS exists."*

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../payinput/PayInputController.java` | new |
| Service | `core/.../payinput/PayInputService.java` | new |
| ServiceImpl | `core/.../payinput/PayInputServiceImpl.java` | new |
| Entity | `core/.../payinput/PayInput.java`, `PayInputPeriodLock.java` | new, each `@Table(schema="core")` |
| Repository | `core/.../payinput/PayInputRepository.java`, `PayInputPeriodLockRepository.java` | new |
| Enumeration | `core/.../payinput/PayInputKind.java` | new — `LOP_DAYS, OVERTIME, REIMBURSEMENT, AD_HOC_DEDUCTION, ONE_TIME_PAYOUT` (`12-core-contracts.md` §3). The kind decides the sign: `LOP_DAYS` and `AD_HOC_DEDUCTION` reduce pay, the other three add to it |
| Command | `core/.../payinput/PayInputCommand.java` | new — `employeeId`, `YearMonth period`, `PayInputKind kind`, `BigDecimal quantity`, `Money amount`, `sourceModule`, `sourceRef` |
| DTO | `core/.../payinput/PayInputRequest.java`, `PayInputResponse.java` | new |

**The seam other modules call** (`12-core-contracts.md` §3):

| Method | Does |
|---|---|
| `record(PayInputCommand)` → `PayInputResponse` | inserts one row; `amount` and `quantity` must be positive — the kind carries the sign, the legacy convention at `EmployeePayRunServiceImpl.java:337-363` where deductions are stored positive and subtracted. If `period` is locked, writes to the next open period and the response carries `postedPeriod` ≠ `requestedPeriod` |
| `forEmployee(employeeId, YearMonth period)` | that employee's rows for the period, plus totals by kind |
| `forPeriod(YearMonth period)` | every employee's rows for the period in one query — the batch read the pay run needs, not N calls |
| `lock(YearMonth period)` | inserts the `core.pay_input_period_lock` row; a second lock of the same period is a no-op |

`period` is `java.time.YearMonth` in Java and `char(7)` `YYYY-MM` in SQL, converted at the
entity boundary — never a `String` in a service signature.

**API contract**

| Method | Path | Request | Response | `@RequiresAction` |
|---|---|---|---|---|
| POST | `/api/v1/pay-inputs` | employeeId, period, kind, quantity, amount, sourceRef | `201`; `postedPeriod` in the body | `core.pay_input.write` |
| GET | `/api/v1/pay-inputs?employeeId=&period=` | — | list plus totals by kind; `employeeId` optional ⇒ the whole period | `core.pay_input.read` |
| POST | `/api/v1/pay-inputs/periods/{period}/lock` | — | `200` | `core.pay_input.lock` |
| POST | `/api/v1/pay-inputs/{id}/reverse` | reason | `201` — a **new** offsetting row | `core.pay_input.write` |

Every endpoint is tenant bound and carries the code shown — `EndpointGuardCoverageTest` fails
otherwise (`12-core-contracts.md` §2). All three codes are added by `W-11.3`
(`12-core-contracts.md` §4). The controller's `sourceModule` is always `core` — a module that
writes through HTTP rather than the Java seam is not a module.

There is no `PUT` and no `DELETE`. A wrong input is reversed by a second row, never edited.
That is what makes the ledger explainable afterwards. A reversal of a row in a locked period
lands in the next open period, like any other late input.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__pay_input.sql` | `core.pay_input` | yes | additive only |
| `core/V0NN__pay_input_period_lock.sql` | `core.pay_input_period_lock` + the insert trigger on `core.pay_input` | yes | additive only |

Version numbers assigned when the branch is cut — `migration/README.md:17-31`. The lock
script runs second because its trigger references the ledger.

`pay_input`: `id uuid` · `tenant_id uuid NOT NULL` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` · `period char(7) NOT NULL` —
`YYYY-MM`, `CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$')` ·
`kind varchar(32) NOT NULL CHECK IN ('LOP_DAYS','OVERTIME','REIMBURSEMENT','AD_HOC_DEDUCTION','ONE_TIME_PAYOUT')` ·
`quantity numeric(10,2) NULL CHECK (quantity > 0)` · `amount numeric(19,4) NULL CHECK (amount > 0)` ·
`source_module varchar(16) NOT NULL` · `source_ref varchar(64) NULL` ·
`reverses_id uuid NULL REFERENCES core.pay_input(id)` · four audit columns.
**No `locked` column** — see below.

`pay_input_period_lock`: `id uuid` · `tenant_id uuid NOT NULL` · `period char(7) NOT NULL` ·
`locked_at timestamptz NOT NULL DEFAULT now()` · `locked_by varchar(100) NOT NULL` ·
unique `(tenant_id, period)`.

- [x] `tenant_id` present on both, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, employee_id, period, kind)` and `(tenant_id, period)` on the ledger; **unique partial `(tenant_id, source_module, source_ref) WHERE reverses_id IS NULL`** (`12-core-contracts.md` §1, §6 decision 2); unique `(tenant_id, period)` on the lock
- [x] **Money columns are `numeric(19,4)`** — `amount`, per `CONVENTIONS.md` §2. `quantity` is `numeric(10,2)` — the day-count shape at `CONVENTIONS.md:37` — so a half-day of loss of pay survives, which it does not today
- [x] Expand / contract — new tables only

`quantity` at two decimal places is a deliberate correction. HRMS records LOP as a `Float` and
Payroll consumes it as an `Integer`, so half-days are lost on the way across — BUG-003 is
recorded as resolved on the HRMS side while the Payroll side still truncates.

**Amounts are positive; the kind decides the sign.** Legacy stores a deduction as a positive
figure and subtracts it at run time (`EmployeePayRunServiceImpl.java:337-363`); the ledger
keeps that convention so a reader never has to know whether a negative reimbursement is a
correction or a mistake. A correction is a reversal row.

**The lock is a database constraint, not a service check.** `app_user` has no `UPDATE`, so a
`locked` flag on the ledger row could never be set (`12-core-contracts.md` §5 row 1). The lock
is a row in `core.pay_input_period_lock`, and a `BEFORE INSERT` trigger on `core.pay_input`
raises when a lock row exists for `(NEW.tenant_id, NEW.period)`. A service-level lock is a
race, and two pay runs for one period is the failure that costs real money —
`09-build-order.md:218`. The service checks the lock first so it can redirect a late input to
the next open period (§4); the trigger is what stops anything the service missed.

RLS and the `tenant_isolation` policy in the exact `CASE` form, each script —
`migration/README.md:76-123`. Each script ends with
`REVOKE UPDATE, DELETE ON core.<table> FROM app_user` — a `REVOKE`, not a narrower `GRANT`,
because `infra/postgres/03-grants.sql:29` already grants all four on every new `core` table by
default; `core.audit_log` explains and does exactly this (`M/core/V008:40-44`). A lock is never undone by the
application: an abandoned run is handled by a new period lock elsewhere, not by deleting this row.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../payinput/PayInputServiceImplTest.java` | totals by kind; a reversal nets to zero; quantity keeps two decimals; a zero or negative `amount` or `quantity` is refused; `period` is a `YearMonth` end to end |
| Unit | `core/.../payinput/PayInputLatePeriodTest.java` | `record` for a locked period posts to the next open period and the response says so; two consecutive locked periods skip both |
| Integration | `core/.../payinput/PayInputLockIT.java` | an insert after lock is refused **by the trigger**, with the service bypassed; `lock` twice for one period leaves one row |
| Integration | `core/.../payinput/PayInputIdempotencyIT.java` | a second `record` with the same `(source_module, source_ref)` is refused by the unique index; a reversal of that row is accepted because `reverses_id` is set |
| Integration | `core/.../payinput/PayInputForPeriodIT.java` | `forPeriod` returns every employee's rows for the period in one statement (`QueryCountIT` style, per `W-55`) |
| Integration | `core/.../payinput/PayInputRlsIT.java` | tenant A cannot read or write tenant B's inputs or locks as `app_user`; tenant A's lock does not stop tenant B's insert for the same period |
| Integration | `core/.../payinput/PayInputImmutabilityIT.java` | `app_user` is refused `UPDATE` and `DELETE` on both tables |
| Integration | `core/.../payinput/PayInputGuardIT.java` | `write`, `read` and `lock` each need their own code; a caller holding only `core.pay_input.read` gets `403` on lock |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`PayInputLockIT` goes around the service on purpose. A lock only the service honours is not a
lock.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in pay_input pay_input_period_lock; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT privilege_type FROM information_schema.table_privileges
      WHERE grantee='app_user' AND table_name='$t' ORDER BY 1;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type, numeric_precision, numeric_scale
     FROM information_schema.columns
    WHERE table_schema='core' AND table_name='pay_input' AND column_name IN ('amount','quantity','locked');"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexdef FROM pg_indexes WHERE schemaname='core' AND tablename='pay_input' AND indexdef LIKE '%source_ref%';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT tgname FROM pg_trigger WHERE tgrelid='core.pay_input'::regclass AND NOT tgisinternal;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` on both tables |
| `amount` | `numeric`, precision 19, scale 4 — never `double precision` |
| `quantity` | `numeric`, precision 10, scale 2 |
| `locked` | **no row** — the column must not exist |
| Unique index | one `UNIQUE` on `(tenant_id, source_module, source_ref) WHERE reverses_id IS NULL` |
| Trigger | one `BEFORE INSERT` trigger on `core.pay_input` |
| Grants | `INSERT` and `SELECT` only, both tables |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The ledger grows a calculation — proration, rounding, "just a helper" | **high — this is the named trap** | No arithmetic beyond summing by kind; the unit test asserts the service exposes no rate, no rule and no policy |
| The lock is enforced only in the service and two runs race | medium, expensive | Enforced by the insert trigger against `pay_input_period_lock`, proved by a test that bypasses the service |
| A late input is silently dropped because the period is locked | medium, expensive | Redirected to the next open period and reported (`12-core-contracts.md` §6 decision 3); `PayInputLatePeriodTest` |
| A retried caller posts the same input twice | medium | Unique `(tenant_id, source_module, source_ref)` on non-reversal rows; `PayInputIdempotencyIT` |
| A caller passes a negative amount to mean a deduction | medium | `CHECK (amount > 0)`; the kind carries the sign, as legacy does at `EmployeePayRunServiceImpl.java:337-363` |
| Half-days keep being truncated because the caller passes an integer | medium | `quantity` is `numeric(10,2)`; the unit test asserts `0.5` survives a round trip |
| A module writes directly to the table instead of through the API | medium | Only `core` owns the entity; `maven-enforcer` stops `hrms` and `payroll` referencing it |
| Reversal is used as a delete and the original is hidden | low | Both rows are returned by the read API; the reversal carries a reason |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only — `migration/README.md:135-143`.
An unlocked period can be re-derived; a locked one is meant to be immutable and is not rolled
back.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | both tables, each in its own script |
| Flyway only, `ddl-auto` nowhere | two scripts; none added |
| `Money`/`BigDecimal` for money | `amount numeric(19,4)`, entity field `Money`; `quantity numeric(10,2)` as `BigDecimal`; no float anywhere |
| Index on `tenant_id` plus lookup columns | four indexes, `tenant_id` leading |
| Expand / contract | new tables only |
| No module references another module | the point of the ticket — both modules talk to `core`, never to each other |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-033 MD5 `X-API-KEY` between the two backends (`GAP_INVENTORY.md:83`) | **Fixed by replacement.** The ledger removes the reason for the call |
| Incident 2 — unauthenticated `POST /public/get-employee-leaves` | **Fixed by replacement** in the new platform. The frozen endpoint still needs checking for internet reachability — that is operational |
| BUG-003 half-day LOP precision | **Fixed.** `quantity numeric(10,2)` on a single column both sides read |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Does an input need approval before it counts?** This spec says no — a module writes what it already approved elsewhere, and `W-15` owns approval. **Recommend** keeping it dumb, per the build order.
2. **Who may lock a period?** **Recommend** only the pay run, in `W-29`, with the manual endpoint above restricted to a tenant administrator for the case where a run is abandoned.
3. **Period as `YYYY-MM`, or a foreign key to a pay schedule?** A schedule does not exist until `W-28`. **Recommend** the string now and a nullable FK added later by `W-28`, expand-and-contract. In Java it is `YearMonth`, never a `String` (`12-core-contracts.md` §3).

**Added 2026-09-25, from `12-core-contracts.md` §6:**

4. **Idempotency** — decision 2: unique `(tenant_id, source_module, source_ref)` where `reverses_id IS NULL`; amounts positive, kind decides the sign.
5. **Late input** — decision 3: an input for a locked period goes to the next open period. An off-cycle run (`W-30`) collects only inputs tagged with its run id — the tag column is `W-30`'s to add, expand-and-contract.
