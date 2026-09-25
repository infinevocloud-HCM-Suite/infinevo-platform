# Feature: Loss-of-pay and working-day policy

| Field | Value |
|---|---|
| **Feature ID** | `W-18.1` · from ticket #22 · `CORE-09` |
| **Promoted to** | `docs/target-state/features/W-18-1-lop-policy.md` on branch `W-18-1-lop-policy` — **`W-18-1` with hyphens**, never `W-18.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-17` (the holiday calendar a working-day basis reads), `W-16.4a` (the loss-of-pay days it applies to), `W-11.3` (the `core.lop_policy.read/manage` codes). `W-28` supplies the weekday-set bean but is not a block — see §4 |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 rows 10, 23 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one script, one table — `core.lop_policy` | 1 |
| Externally testable behaviour | two tenants on different settings derive different working days and loss-of-pay figures from identical data | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

The divisor that decides what a day of pay is worth is hard-coded, and the fields that were
meant to configure it are ignored.

- Per-day pay is monthly salary divided by **calendar days in the period** — `legacy/Payroll-Bend-SBoot/.../serviceimpl/payruns/EmployeePayRunServiceImpl.java:1165`. No weekday filtering, no holidays, no configuration
- Loss of pay is then `lopAmount = (monthlySalary / totalPeriodDays) * lopDays`, subtracted from prorated net pay
- **The configuration exists and nothing reads it.** `AttendancePreference` carries `canIncludeWeekendsForPay`, `canIncludeHolidaysForPay` and `canIncludeLeavesForPay` at `:27-34`. `PaySchedule` carries `workingDays`, `noOfWorkingDays` and `workingDaysCalculationType` at `:36-50`. The pay-run logic reads none of them

`01-platform-shape.md:63` describes `CORE-09` as a **new build** *"seeded from the pay
schedule and attendance-preference flags that exist but are ignored today"* — the evidence
confirms both halves of that sentence exactly.

The practical consequence: a February absence costs more than a July one for the same
employee, because the divisor changes with month length, and no employer chose that. Two
employers who genuinely want different bases cannot have them.

## 2. Scope

**In scope**

- `core.lop_policy` — per tenant, effective-dated
- The working-day basis, three values (`12-core-contracts.md:147`): `ACTUAL_DAYS` — the calendar days in the period, the legacy divisor; `FIXED_30`; `ORG_DAYS(n)` — the organisation's working days, `n` configured or counted from the pay schedule's weekday set
- Whether weekends and holidays count as payable
- The derivation rule: how a loss-of-pay day becomes a fraction of monthly pay, rounded per `lop_rounding` (default `HALF_UP_2`)
- A calculator any consumer calls: given a period and an employee, how many payable days, and what is one day worth as a fraction — and **no answer at all** when the tenant has no policy

**Out of scope**

- **Stamping the policy onto a pay figure** — `W-18.2`, in the `payroll` module
- Computing money. This ticket produces a divisor and a day count, never an amount
- The pay schedule itself — `W-28`
- Attendance capture — `CORE-20`

## 3. Flow

```
[tenant admin] --> [LopPolicyController] --> [core.lop_policy under RLS]

[W-18.2 / W-29, later] --> [WorkingDayBasisCalculator.basisFor(tenantId, period, employeeId)]
   --> policy in force? no --> NoLopPolicyException (the caller fails that employee, W-18.2)
   --> yes --> weekday set from [WorkingWeekSource bean, supplied by W-28]
           --> holidays from [HolidayQueryService.holidaysBetween(locationId, from, to), W-17]
           --> {payableDays, divisor, policyId}
```

The calculator returns the `policyId` alongside the numbers. That is what makes `W-18.2`'s
stamp possible, and it is why the two tickets must agree before either is built. The name is
`WorkingDayBasisCalculator` everywhere — `12-core-contracts.md:97`; the first draft used two
names for it.

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../lop/LopPolicyController.java` | new |
| Service | `core/.../lop/LopPolicyService.java` | new |
| Service | `core/.../lop/WorkingDayBasisCalculator.java` | new — the seam `W-18.2` and `W-29` use |
| Entity | `core/.../lop/LopPolicy.java` | new, `@Table(schema="core")` |
| Repository | `core/.../lop/LopPolicyRepository.java` | new |
| Enumeration | `core/.../lop/WorkingDayBasis.java` | new — `ACTUAL_DAYS`, `ORG_DAYS`, `FIXED_30`; `ORG_DAYS` carries `n` in `configured_days_per_month` |
| Enumeration | `core/.../lop/LopRounding.java` | new — `HALF_UP_2` (default), `HALF_UP_0`, `NONE` |
| Port | `core/.../lop/WorkingWeekSource.java` | new — `weekdaysFor(tenantId, employeeId)` → `Set<DayOfWeek>`; **`W-28` implements it** in `payroll` from the pay schedule's weekday list (legacy `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/PaySchedule.java:35-38`). `core` never reads `payroll.pay_schedule` |
| Exception | `core/.../lop/NoLopPolicyException.java` | new — thrown when no policy is in force; there is no fallback |
| DTO | `core/.../lop/LopPolicy*.java`, `WorkingDayBasisResponse.java` | new |

`W-16.3` already has a `WorkingDayCalculator` for counting a leave request's working days.
This one answers a different question — what a *pay* day is worth — and the two must not be
merged: a tenant may count leave in working days while paying on a fixed thirty.

**How each basis is computed** (`D-27`, `12-core-contracts.md:147`):

| Basis | `payableDays` | `divisor` | Needs the weekday set |
|---|---|---|---|
| `ACTUAL_DAYS` | calendar days in the period, minus weekend days if `weekends_payable = false`, minus holidays if `holidays_payable = false` | same as `payableDays` | only when `weekends_payable = false` |
| `FIXED_30` | 30 | 30 | no |
| `ORG_DAYS(n)` | `n` when `configured_days_per_month` is set; otherwise the period's days in the weekday set, minus holidays if `holidays_payable = false` | same as `payableDays` | when `n` is not set |

A basis that needs the weekday set and finds no `WorkingWeekSource` bean throws
`NoLopPolicyException` too — an unanswerable question is refused, never guessed. Holidays come
from `HolidayQueryService.holidaysBetween(locationId, from, to)` for the employee's work
location. All day counts are `numeric(10,2)` / `BigDecimal` (`CONVENTIONS.md:37`).

**API contract**

| Method | Path | Request | Response | `@RequiresAction` |
|---|---|---|---|---|
| GET | `/api/v1/lop-policy` | `?asOf=` | the policy in force | `core.lop_policy.read` |
| PUT | `/api/v1/lop-policy` | basis, flags, rule, effectiveFrom | `200` — a new version | `core.lop_policy.manage` |
| GET | `/api/v1/lop-policy/basis` | `?period=&employeeId=` | payable days, divisor, policy id; `409` when no policy is in force | `core.lop_policy.read` |

All Bearer, tenant bound. Codes per `12-core-contracts.md:63`, added by `W-11.3`
(`12-core-contracts.md:128`).

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__lop_policy.sql` | `core.lop_policy` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` ·
`working_day_basis varchar(24) NOT NULL CHECK (working_day_basis IN ('ACTUAL_DAYS','ORG_DAYS','FIXED_30'))` ·
`configured_days_per_month numeric(10,2) NULL` — the `n` of `ORG_DAYS(n)`, null otherwise ·
`weekends_payable boolean NOT NULL DEFAULT true` ·
`holidays_payable boolean NOT NULL DEFAULT true` ·
`lop_rounding varchar(16) NOT NULL DEFAULT 'HALF_UP_2'` · `effective_from date NOT NULL` ·
four audit columns.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, effective_from DESC)`
- [x] **Money columns — none, deliberately.** This table holds a basis and a divisor. `W-18.2` and `W-29` turn them into rupees, with `numeric(19,4)` per `CONVENTIONS.md` §2
- [x] Expand / contract — new table only

`configured_days_per_month` is `numeric(10,2)` — the day-count type `CONVENTIONS.md:37` and
`12-core-contracts.md:160` fix for every day column — because 26.5 is a real answer in some
establishments, and an integer would force a choice nobody made.

**`effective_from` versioning**, for the same reason as `W-16.1` and `W-15.1`: a payslip
issued in March must stay explainable after the policy changes in April. `W-18.2` depends on
this — a stamp pointing at a mutable row explains nothing.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../lop/WorkingDayBasisCalculatorTest.java` | each of the three bases over the same February and July; `ORG_DAYS` with `n` set and with `n` counted from a stub `WorkingWeekSource` (Mon–Fri, then Mon–Sat); weekends and holidays toggled, holidays from a stub `holidaysBetween`; rounding applied, `HALF_UP_2` when the policy says nothing; **a tenant with no policy throws `NoLopPolicyException` — there is no fallback**; a basis needing the weekday set with no bean present throws too |
| Unit | `core/.../lop/LopPolicyVersionTest.java` | the policy in force on a date is the latest with `effective_from <= date` |
| Integration | `core/.../lop/TwoTenantBasisIT.java` | **two tenants, identical employee and period, different bases, different payable days and divisors** |
| Integration | `core/.../lop/LopPolicyRlsIT.java` | tenant A cannot read or edit tenant B's policy |
| Integration | `core/.../lop/LopPolicyGuardIT.java` | `PUT` is `403` without `core.lop_policy.manage`; `GET /basis` with no policy is `409`, not `200` with a default |

`TwoTenantBasisIT` is the build order's own acceptance test — *"two tenants on different
settings produce correctly different figures from identical data"* — reduced to the part this
ticket owns. The money half is `W-18.2`'s.

The February-versus-July case in the unit test is the defect it exists to prevent returning.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.lop_policy'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type FROM information_schema.columns
    WHERE table_schema='core' AND table_name='lop_policy' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_default FROM information_schema.columns
    WHERE table_schema='core' AND table_name='lop_policy' AND column_name='lop_rounding';"
cd code/backend && mvn -q -pl core -Dit.test=TwoTenantBasisIT verify
cd code/backend && mvn -q verify

# one name for the calculator, and no fallback basis
grep -rln 'WorkingDayCalculator\b' core/src/main/java/com/infinevo/core/lop/ \
  && echo "REVIEW: old name survives" || echo "one name"
grep -rn 'CALENDAR_DAYS\|fallback' core/src/main/java/com/infinevo/core/lop/ \
  && echo "REVIEW: a fallback basis exists" || echo "no fallback"
```

| Check | Expected |
|---|---|
| RLS | `t` |
| No `double precision` column | none present; `configured_days_per_month` is `numeric` |
| `lop_rounding` default | `'HALF_UP_2'::character varying` |
| `TwoTenantBasisIT` | green — the two tenants differ |
| Name and fallback greps | `one name`, `no fallback` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The calculator returns an amount and money logic lands in `core` | medium | It returns days and a divisor; no money column, and `payroll` owns rupees |
| It is merged with `W-16.3`'s leave working-day calculator | medium | Different questions; a tenant may legitimately answer them differently |
| The policy is edited in place and old payslips become unexplainable | **medium — and it defeats `W-18.2` entirely** | `effective_from` versioning, asserted by a test |
| A tenant with no policy is silently paid on a guessed divisor | **medium — the frozen system's exact defect** | There is no fallback: `basisFor` throws, `W-18.2` fails that employee's figure and the run reports it (`12-core-contracts.md:147`); `W-12.1` seeds a policy at tenant creation so this is rare |
| `core` reads `payroll.pay_schedule` to get the weekday set | medium | It cannot: `WorkingWeekSource` is a port in `core` that `W-28` implements; `maven-enforcer` blocks the shortcut |
| Rounding differences produce one-rupee disputes | medium | `lop_rounding` is explicit on the policy, defaults to `HALF_UP_2`, and is stamped by `W-18.2` |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only —
`migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.lop_policy` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | creates no money column; day counts are `numeric`, never float |
| Index on `tenant_id` plus lookup columns | one index, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `core` only. **The rule is why this is a separate ticket from `W-18.2`** |

## 12. Gap inventory

| ID | Decision |
|---|---|
| Hard-coded calendar-day divisor (`EmployeePayRunServiceImpl.java:1165`) | **Fixed by replacement.** The basis is configuration |
| `AttendancePreference` flags never read (`:27-34`) | **Fixed.** `weekends_payable` and `holidays_payable` carry the intent and are read |
| `PaySchedule.workingDays` and `workingDaysCalculationType` never read (`PaySchedule.java:35-38`, `:36-50`) | **Fixed here**, through `WorkingWeekSource`; `W-28` wires the schedule side |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

| # | Question | Decision |
|---|---|---|
| 1 | A tenant with no policy | ~~Fall back to calendar days silently~~ — **superseded 2026-09-25 by `12-core-contracts.md:147`: no policy means the employee fails the run, never a null or guessed stamp.** `basisFor` throws `NoLopPolicyException` |
| 2 | The seeded default basis | **`ACTUAL_DAYS` — calendar days in the period.** Against my recommendation of fixed 30 |

**Decision 2 still delivers what decision 1 was for:** the frozen system divides by calendar
days (`EmployeePayRunServiceImpl.java:1165`), and `W-12.1` seeds an `ACTUAL_DAYS` policy at
tenant creation, so **payslip amounts do not change at cutover** for every tenant that exists.
What changes is the failure mode: a tenant whose policy is missing is told, per employee, per
run, instead of being paid on a divisor nobody chose — which was the frozen system's defect.

One consequence the implementer must carry:

- **The same absence costs more in February than in July** under `ACTUAL_DAYS`, because the divisor is the month's length. This is inherited behaviour, not a new bug, and `W-18.2`'s stamp is what lets support explain it to an employee who notices
