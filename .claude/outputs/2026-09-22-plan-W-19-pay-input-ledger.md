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
| **Blocked by** | `W-13.1` — an input names an employee |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one script, one table — `core.pay_input` | 1 |
| Externally testable behaviour | a module writes a value, the pay run reads it, and neither can change it once the period is locked | 1 |
| Frontend area | none | 1 |

Within cap.

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
- A write API any module calls, naming employee, period, kind and amount
- A read API the pay run calls for one employee and period
- Period locking: once locked, no write for that tenant, employee and period succeeds

**Out of scope**

- Any calculation. `09-build-order.md:200`: *"keep it dumb. It is a ledger, not a calculation engine"*
- The pay run reading it — `W-29`
- Overtime capture that produces a value — `CORE-21`
- Deleting the legacy HTTP integration — that dies with the frozen system, not here
- Approval of an input before it counts — `W-15`

## 3. Flow

```
[hrms or core module] --> [PayInputService.record(...)] --> [core.pay_input, append only]
[payroll pay run]     --> [PayInputService.read(employee, period)] --> [sum by kind]
[payroll pay run]     --> [PayInputService.lock(period)] --> further writes refused
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
| Entity | `core/.../payinput/PayInput.java` | new, `@Table(schema="core")` |
| Repository | `core/.../payinput/PayInputRepository.java` | new |
| Enumeration | `core/.../payinput/PayInputKind.java` | new — LOP days, overtime, reimbursement, ad-hoc deduction, one-time payout |
| DTO | `core/.../payinput/PayInputRequest.java`, `PayInputResponse.java` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/pay-inputs` | employeeId, period, kind, quantity, amount, sourceRef | `201` | Bearer, tenant bound |
| GET | `/api/v1/pay-inputs?employeeId=&period=` | — | list plus totals by kind | Bearer, tenant bound |
| POST | `/api/v1/pay-inputs/periods/{period}/lock` | — | `200` | Bearer, tenant bound |
| POST | `/api/v1/pay-inputs/{id}/reverse` | reason | `201` — a **new** offsetting row | Bearer, tenant bound |

There is no `PUT` and no `DELETE`. A wrong input is reversed by a second row, never edited.
That is what makes the ledger explainable afterwards.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__pay_input.sql` | `core.pay_input` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` · `period char(7) NOT NULL` —
`YYYY-MM` · `kind varchar(32) NOT NULL` · `quantity numeric(9,2) NULL` ·
`amount numeric(19,4) NULL` · `source_module varchar(16) NOT NULL` ·
`source_ref varchar(64) NULL` · `reverses_id uuid NULL REFERENCES core.pay_input(id)` ·
`locked boolean NOT NULL DEFAULT false` · four audit columns.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, employee_id, period, kind)` and `(tenant_id, period, locked)`
- [x] **Money columns are `numeric(19,4)`** — `amount`, per `CONVENTIONS.md` §2. `quantity` is `numeric(9,2)` so a half-day of loss of pay survives, which it does not today
- [x] Expand / contract — new table only

`quantity` at two decimal places is a deliberate correction. HRMS records LOP as a `Float` and
Payroll consumes it as an `Integer`, so half-days are lost on the way across — BUG-003 is
recorded as resolved on the HRMS side while the Payroll side still truncates.

**The lock is a database constraint, not a service check.** A partial unique index and a
trigger refuse an insert for a locked (tenant, employee, period). A service-level lock is a
race, and two pay runs for one period is the failure that costs real money —
`09-build-order.md:218`.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`. `app_user` is granted `SELECT, INSERT` and **not** `UPDATE` or
`DELETE`, the same posture as `core.audit_log`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../payinput/PayInputServiceImplTest.java` | totals by kind; a reversal nets to zero; quantity keeps two decimals |
| Integration | `core/.../payinput/PayInputLockIT.java` | an insert after lock is refused **by the database**, with the service bypassed |
| Integration | `core/.../payinput/PayInputRlsIT.java` | tenant A cannot read or write tenant B's inputs as `app_user` |
| Integration | `core/.../payinput/PayInputImmutabilityIT.java` | `app_user` is refused `UPDATE` and `DELETE` |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`PayInputLockIT` goes around the service on purpose. A lock only the service honours is not a
lock.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.pay_input'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type, numeric_precision, numeric_scale
     FROM information_schema.columns
    WHERE table_schema='core' AND table_name='pay_input' AND column_name IN ('amount','quantity');"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT privilege_type FROM information_schema.table_privileges
    WHERE grantee='app_user' AND table_name='pay_input' ORDER BY 1;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| `amount` | `numeric`, precision 19, scale 4 — never `double precision` |
| `quantity` | `numeric`, scale 2 |
| Grants | `INSERT` and `SELECT` only |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The ledger grows a calculation — proration, rounding, "just a helper" | **high — this is the named trap** | No arithmetic beyond summing by kind; the unit test asserts the service exposes no rate, no rule and no policy |
| The lock is enforced only in the service and two runs race | medium, expensive | Enforced by a database constraint, proved by a test that bypasses the service |
| Half-days keep being truncated because the caller passes an integer | medium | `quantity` is `numeric(9,2)`; the unit test asserts `0.5` survives a round trip |
| A module writes directly to the table instead of through the API | medium | Only `core` owns the entity; `maven-enforcer` stops `hrms` and `payroll` referencing it |
| Reversal is used as a delete and the original is hidden | low | Both rows are returned by the read API; the reversal carries a reason |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only — `migration/README.md:135-143`.
An unlocked period can be re-derived; a locked one is meant to be immutable and is not rolled
back.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.pay_input` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | `amount numeric(19,4)`, entity field `BigDecimal`; no float anywhere |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | the point of the ticket — both modules talk to `core`, never to each other |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-033 MD5 `X-API-KEY` between the two backends (`GAP_INVENTORY.md:83`) | **Fixed by replacement.** The ledger removes the reason for the call |
| Incident 2 — unauthenticated `POST /public/get-employee-leaves` | **Fixed by replacement** in the new platform. The frozen endpoint still needs checking for internet reachability — that is operational |
| BUG-003 half-day LOP precision | **Fixed.** `quantity numeric(9,2)` on a single column both sides read |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Does an input need approval before it counts?** This spec says no — a module writes what it already approved elsewhere, and `W-15` owns approval. **Recommend** keeping it dumb, per the build order.
2. **Who may lock a period?** **Recommend** only the pay run, in `W-29`, with the manual endpoint above restricted to a tenant administrator for the case where a run is abandoned.
3. **Period as `YYYY-MM`, or a foreign key to a pay schedule?** A schedule does not exist until `W-28`. **Recommend** the string now and a nullable FK added later by `W-28`, expand-and-contract.
