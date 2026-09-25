# Feature: Policy stamp on every pay figure

| Field | Value |
|---|---|
| **Feature ID** | `W-18.2` · from ticket #22 · `CORE-09` |
| **Promoted to** | `docs/target-state/features/W-18-2-policy-stamp.md` on branch `W-18-2-policy-stamp` — **`W-18-2` with hyphens**, never `W-18.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-18.1` (a versioned policy to point at), `W-29` (the pay figures to stamp), `W-11.3` (transitively — `W-18.1`'s codes) |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 rows 10, 23 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | one script — additive columns on `payroll.employee_payrun` | 1 |
| Externally testable behaviour | every pay figure records the policy, basis and divisor that produced it | 1 |
| Frontend area | none | 1 |

Within cap. **This is a separate ticket from `W-18.1` because `maven-enforcer` forbids one
module referencing another** — the policy lives in `core`, the stamp lives in `payroll`.

---

## 1. Problem

A payslip today records numbers and nothing about how they were reached.

- `EmployeePayRun` stores `paidDays`, `lop` and `netPay` — numeric columns only
- **No divisor, no basis, no policy reference, no rounding rule** is recorded anywhere on the row
- The divisor itself was hard-coded to calendar days — `EmployeePayRunServiceImpl.java:1165` — so it was never a value anyone could have stored

`09-build-order.md:198` is unusually firm: *"policy stamped on every pay figure ... **the
stamp is not optional**. Without it a disputed payslip cannot be explained later."*

The practical case is an employee in March asking why two days of absence cost what they did.
Today the only answer is to re-run the calculation with whatever the code does now — which may
have changed, and which certainly changed if the tenant edited a setting. `W-18.1` versioned
the policy precisely so this ticket has something stable to point at.

`01-platform-shape.md:250-251` sharpens it: a Payroll-only tenant has no approval trail behind
its absences at all, so the audit of *how a figure was derived* matters more in that tier,
not less.

## 2. Scope

**In scope**

- Additive columns on `payroll.employee_payrun` recording the policy id, basis, divisor, payable days and rounding actually used
- The pay-run calculation calling `W-18.1`'s calculator instead of dividing by calendar days
- An explain endpoint returning the stamp alongside the figure
- **Failing the employee when no policy resolves.** `W-18.1`'s calculator throws `NoLopPolicyException` rather than falling back (`12-core-contracts.md:147`), so this ticket catches it per employee, writes **no figure** for them, marks them failed on the run with the reason, and completes the rest (decision 1). There is never a null stamp and never a guessed one

**Out of scope**

- The policy model — `W-18.1`
- The pay run itself — `W-29` owns creation, inclusion and computation; this ticket changes what it records
- Payslip rendering — `W-36`
- Re-stamping historical figures. There are none; nothing is in production

## 3. Flow

```
[W-29 pay run computes a figure]
  --> [WorkingDayBasisCalculator.basisFor(tenantId, period, employeeId) (W-18.1)]
  --> NoLopPolicyException --> this employee FAILED on the run, no row written, run continues
  --> {payableDays, divisor, policyId}
  --> figure computed from that divisor, rounded per the policy's lop_rounding
  --> stamp written on the same row, same transaction

[employee or support] --> GET /payruns/{id}/employees/{id}/explain
  --> figure + policy id + basis + divisor + payable days + rounding
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Entity | `payroll/.../payrun/EmployeePayRun.java` | change — five stamp fields |
| ServiceImpl | `payroll/.../payrun/EmployeePayRunServiceImpl.java` | change — divisor from the calculator, not calendar days |
| Controller | `payroll/.../payrun/PayRunExplainController.java` | new |
| DTO | `payroll/.../payrun/PayFigureExplanationResponse.java` | new |

**`payroll` calls `core`, which is allowed** — `CLAUDE.md` permits a module to reference
`core`, and forbids only module-to-module references. That is the whole reason `W-18.1` put
the policy in `core`.

**API contract**

| Method | Path | Request | Response | `@RequiresAction` |
|---|---|---|---|---|
| GET | `/api/v1/payruns/{payrunId}/employees/{employeeId}/explain` | — | figure, policy id, basis, divisor, payable days, rounding | `payroll.run.read`; or `payroll.payslip.read_own` when `{employeeId}` is the caller (decision 2) |

Bearer, tenant bound, `@RequiresModule(PAYROLL)`. Both codes are already in the catalogue —
`M/reference/V020__action.sql:117,121` — so this endpoint needs nothing from `W-11.3`
directly. `EndpointGuardCoverageTest` fails it without a code (`12-core-contracts.md:45-46`).

## 5. Frontend changes

None. `W-36` surfaces the explanation on a payslip.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V0NN__employee_payrun_policy_stamp.sql` | `payroll.employee_payrun` — columns only | yes, already | additive only |

Version number assigned when the branch is cut; the sequence is global and `payroll` numbers
above `core` within a batch — `migration/README.md:17-31`.

Columns added, all nullable:
`lop_policy_id uuid NULL` · `working_day_basis varchar(24) NULL` — `ACTUAL_DAYS`, `ORG_DAYS`, `FIXED_30` ·
`pay_divisor numeric(10,2) NULL` · `payable_days numeric(10,2) NULL` ·
`lop_rounding varchar(16) NULL` — `HALF_UP_2` and the rest of `W-18.1`'s `LopRounding`.

- [x] `tenant_id` — already present on `payroll.employee_payrun` from `W-29`; this ticket adds no table
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, lop_policy_id)` so every figure produced by a policy is findable when one is questioned
- [x] **Money columns — none added.** `pay_divisor` and `payable_days` are day counts, not amounts, and are `numeric(10,2)` — the day-count type `CONVENTIONS.md:37` and `12-core-contracts.md:160` fix. The money columns they divide are `W-29`'s, already `numeric(19,4)` per `CONVENTIONS.md` §2
- [x] Expand / contract — columns added nullable; no destructive step

**No foreign key to `core.lop_policy`.** A `payroll` table holding a database-level constraint
on a `core` table couples the schemas in a way the module boundary is meant to prevent, and
`W-18.1` never deletes a policy version. The id is stored and resolved through the service.

**Nullable, then enforced in code.** The columns must be nullable to be additive, so the
service refuses to write a figure without them rather than the database refusing — see the
test.

RLS already applies to `payroll.employee_payrun`; adding columns does not change the policy.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../payrun/PayFigureStampTest.java` | a figure computed without a resolvable policy is refused, not defaulted; `NoLopPolicyException` becomes a per-employee failure, not a run failure |
| Unit | `payroll/.../payrun/LopAmountTest.java` | the amount uses the calculator's divisor, not the period's calendar days; the same LOP days under `ACTUAL_DAYS`, `ORG_DAYS(26)` and `FIXED_30` give three different amounts; rounding follows the stamped `lop_rounding` |
| Integration | `payroll/.../payrun/StampCompletenessIT.java` | **after a pay run, no `employee_payrun` row has a null in any of the five stamp columns** — `lop_policy_id` included |
| Integration | `payroll/.../payrun/NoPolicyFailsEmployeeIT.java` | a run over a tenant with no policy completes with every employee marked failed and the reason recorded, and writes **zero** `employee_payrun` rows; one employee with no work location on an `ORG_DAYS` tenant fails alone while the rest complete |
| Integration | `payroll/.../payrun/TwoTenantFigureIT.java` | two tenants, identical data, different policies, different loss-of-pay amounts — and each row's stamp explains its own figure |
| Integration | `payroll/.../payrun/ExplainEndpointIT.java` | the explanation matches the stored stamp exactly; an employee with only `payroll.payslip.read_own` reads their own and is `403` on a colleague's; `payroll.run.read` reads either |

`StampCompletenessIT` is the test that makes "not optional" real. Nullable columns plus a
service-level rule decay quietly; a test asserting zero nulls after a run does not.

`TwoTenantFigureIT` completes the build order's acceptance criterion that `W-18.1` could only
half-prove.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type, is_nullable FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='employee_payrun'
      AND column_name IN ('lop_policy_id','working_day_basis','pay_divisor','payable_days','lop_rounding')
    ORDER BY 1;"

cd code/backend && mvn -q -pl payroll -Dit.test=StampCompletenessIT,NoPolicyFailsEmployeeIT,TwoTenantFigureIT verify
cd code/backend && mvn -q verify

# the hard-coded divisor must not survive, and no fallback basis may exist
grep -rn 'totalPeriodDays\|ChronoUnit.DAYS.between' payroll/src/main/java/com/infinevo/payroll/payrun/ \
  && echo "REVIEW: calendar-day divisor still present" || echo "divisor comes from policy"
grep -rn 'CALENDAR_DAYS\|WorkingDayCalculator\b' payroll/src/main/java/com/infinevo/payroll/payrun/ \
  && echo "REVIEW: fallback or old calculator name present" || echo "no fallback"
```

| Check | Expected |
|---|---|
| Five stamp columns | present, all nullable, `pay_divisor` and `payable_days` are `numeric(10,2)` |
| `StampCompletenessIT` | green — zero null stamps after a run, `lop_policy_id` included |
| `NoPolicyFailsEmployeeIT` | green — failed employees, zero rows, run completes |
| Fallback grep | `no fallback` |
| `TwoTenantFigureIT` | green — different figures, each explained |
| Divisor grep | `divisor comes from policy` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Rows written without a stamp because the columns are nullable | **high — nullable is an invitation** | `StampCompletenessIT` asserts all five columns, `lop_policy_id` included, are non-null on every row after a run. No figure exists without a policy, so a null anywhere is a defect |
| A missing policy is quietly defaulted, repeating the defect this ticket exists to fix | **medium** | There is no fallback in `W-18.1` or here (`12-core-contracts.md:147`): the employee fails the run with a reason, `NoPolicyFailsEmployeeIT` asserts zero rows, and the grep refuses a `CALENDAR_DAYS` constant |
| `payroll` imports `core`'s entity instead of calling its service | medium | Allowed by the module rule either way, but the id is stored and resolved through the service; no FK, no shared entity |
| The stamp records the policy but not the version actually used | medium | `W-18.1` rows are immutable per `effective_from`; the stamped id identifies one version |
| Rounding disputes remain unexplainable | low | `lop_rounding` is stamped with the rest |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only —
`migration/README.md:135-143`. Dropping the explain endpoint leaves the stamps in place, which
is the useful half.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | **creates no table**; the columns land on a table that already has both |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | adds no money column; `pay_divisor` and `payable_days` are `numeric(10,2)`, never float |
| Index on `tenant_id` plus lookup columns | one new index, `tenant_id` leading |
| Expand / contract | nullable columns added; no destructive step |
| No module references another module | `payroll` → `core` only, which is permitted. **This constraint is why `W-18` was split** |

## 12. Gap inventory

| ID | Decision |
|---|---|
| No policy recorded on a pay figure (`EmployeePayRun` numeric columns only) | **Fixed.** This is the ticket |
| Hard-coded calendar-day divisor (`EmployeePayRunServiceImpl.java:1165`) | **Fixed.** Replaced by the calculator, and the grep proves it is gone |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **What does the pay run do when one employee's policy cannot resolve?** **Recommend** failing that employee's figure and completing the rest, with the run reporting which employees failed — a 500-person run should not abort over one, and a silently defaulted figure is exactly what this ticket exists to prevent. **Confirmed by `12-core-contracts.md:147`**, which also removed `W-18.1`'s silent fallback, so the "stamp the fallback" bullet this spec once carried is gone.
2. **Who may call the explain endpoint?** **Recommend** the employee for their own figure, plus anyone holding the payroll-read action — a disputed payslip is usually raised by the employee, and making them ask an administrator adds a step with no security benefit. Codes: `payroll.payslip.read_own` for self, `payroll.run.read` otherwise (`V020__action.sql:117,121`).
