# Feature: Pay schedule

| Field | Value |
|---|---|
| **Feature ID** | `W-28` · from ticket #32 · `PAY-04` |
| **Promoted to** | `docs/target-state/features/W-28-pay-schedule.md` on the developer's `dev-<name>` branch |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-018 (honoured), DEBT-022 (fixed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-18.1` — this ticket implements its `WorkingWeekSource` port and its tests call its `WorkingDayBasisCalculator` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V054` — one table | 1 |
| Externally testable behaviour | a tenant sets its work week and pay-day rule; the working-day basis for that tenant counts only those weekdays, and its pay run period, cut-off and pay date follow the rule; another tenant sees none of it | 1 |
| Frontend area | none — `W-47` builds the pay schedule page (`D-60`) | 1 |

Within cap.

---

## 1. Problem

The frozen Payroll backend has a pay schedule, and it is a port. Two things are wrong with it.

- **The working-day settings are stored and never read.** `workingDays`, `noOfWorkingDays`
  and `workingDaysCalculationType`
  (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/PaySchedule.java:35-50`)
  are written by the screen and read by nothing. Per-day pay is monthly salary divided by
  calendar days
  (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/payruns/EmployeePayRunServiceImpl.java:1165`).
  `09-build-order.md:217`: *"today these fields exist and are ignored. Wiring them is the work"*
- **The schedule is read once, then forgotten.** Only the first pay run takes its period and
  pay date from the schedule
  (`legacy/.../serviceimpl/payruns/PayRunServiceImpl.java:303-316`); every later run rolls
  forward from the last completed run (`:287-300`) and sets the pay date to the period's last
  day (`:299`), whatever the schedule says. There is no cut-off date anywhere
- **Two switches in two places.** `includeHolidays` and `includeWeekends` (`PaySchedule.java:43-47`)
  duplicate `AttendancePreference.canInclude*ForPay` (`.../attendance/AttendancePreference.java:27-34`);
  neither is read. `D-60` puts both on `core.lop_policy` (`W-18.1` §6) and nowhere else
- **Legacy shape.** A random 10-digit `payScheduleId` (`PayScheduleServiceImpl.java:29-37`),
  `payDay` as a `String` holding either `"last_working_day"` or a number
  (`legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/paySchedules.js:232`),
  weekdays as a `List<String>` of `"mon"`…`"sun"` (`:26-34`), `/api/paySchedule` with no
  version prefix (`PayScheduleController.java:10`) — DEBT-007, DEBT-008

## 2. Scope

**In scope**

- `payroll.pay_schedule` — one row per tenant: the work week, the pay-day rule, the input
  cut-off day, the first period
- Read and upsert of that row
- `WorkingWeekSource` implemented in `payroll`, so `W-18.1`'s calculator counts the tenant's
  weekdays under `ORG_DAYS` and `ACTUAL_DAYS` with `weekends_payable = false`
- `PayPeriodService.periodFor(YearMonth)` → start, end, cut-off date, pay date — the one
  place `W-29` asks for a period's dates, replacing the roll-forward at
  `PayRunServiceImpl.java:287-316`

**Out of scope**

- The working-day basis, `weekends_payable`, `holidays_payable`, days-per-month —
  `core.lop_policy`, `W-18.1` (`D-60`)
- Creating or locking a pay run, calling `PayInputService.lock` at the cut-off — `W-29`
- Screens — `W-47`
- Frequencies other than monthly. The legacy default is the only value ever set
  (`PayScheduleServiceImpl.java:54`, `paySchedules.js:227`); the column exists with a `CHECK`
  so a second value is a migration, not a rewrite

## 3. Flow

```
[payroll officer] --> [PayScheduleController] --> permission payroll.settings.manage
   --> PayScheduleServiceImpl.upsert --> [payroll.pay_schedule, one row per tenant under RLS]

[core WorkingDayBasisCalculator, W-18.1] --> [WorkingWeekSource bean = PayScheduleWorkingWeekSource]
   --> schedule for tenant? no --> NoPayScheduleException (the caller fails that employee)
   --> yes --> Set<DayOfWeek> from working_days

[W-29, later] --> PayPeriodService.periodFor(period) --> {start, end, cutoffDate, payDate}
```

`weekdaysFor(tenantId, employeeId)` ignores `employeeId` here: one schedule per tenant, the
legacy rule (`PaySchedule.java:52-54`, `PayScheduleServiceImpl.java:44-46`). The parameter
stays on the port so a per-location schedule later is an implementation change, not a
contract change.

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/schedule/`.

| Layer | File | Change |
|---|---|---|
| Entity | `PaySchedule.java` | new, `@Table(name = "pay_schedule", schema = "payroll")`, `UUID id`, `UUID tenantId` |
| Repository | `PayScheduleRepository.java` | new, `findByTenantId(UUID)` |
| Service / ServiceImpl | `PayScheduleService`, `PayScheduleServiceImpl` | new — `get`, `upsert` |
| Service | `PayPeriodService`, `PayPeriodServiceImpl` | new — `periodFor(YearMonth)`; refuses a period before `first_period_start` |
| Port impl | `PayScheduleWorkingWeekSource.java` | new — `implements com.infinevo.core.lop.WorkingWeekSource`, a `@Component`; the only bean of that type |
| Exception | `NoPayScheduleException.java` | new — `409`, same shape as `NoLopPolicyException` |
| Controller | `PayScheduleController.java` | new |
| Enumeration | `PayDayRule.java` | new — `LAST_DAY_OF_PERIOD`, `LAST_WORKING_DAY`, `SPECIFIC_DAY` |
| DTO | `PayScheduleRequest`, `PayScheduleResponse`, `PayPeriodResponse` | new; `status` / `message` / `data` envelope (`CONVENTIONS.md` §3) |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/payroll/pay-schedule` | — | the row, or defaults with `exists: false`; never writes | `payroll.structure.read` |
| PUT | `/api/v1/payroll/pay-schedule` | working_days[], pay_day_rule, pay_day_of_month, input_cutoff_day, first_period_start | `200`, upsert | `payroll.settings.manage` |
| GET | `/api/v1/payroll/pay-schedule/period` | `?period=YYYY-MM` | start, end, cutoff_date, pay_date; `409` when no schedule | `payroll.structure.read` |

Permission codes exist: `payroll.settings.manage`
(`code/backend/migration/src/main/resources/db/migration/core/V025__catalogue_correction.sql:193`),
`payroll.structure.read` (`:194`). No new code.

**How a period is derived**, replacing `PayRunServiceImpl.java:287-316`:

| Value | Rule |
|---|---|
| `start`, `end` | first and last calendar day of the month. Legacy did the same after the first run (`:293-298`) |
| `cutoff_date` | `input_cutoff_day` of that month, clamped to the month's length |
| `pay_date` | `LAST_DAY_OF_PERIOD`: `end`. `LAST_WORKING_DAY`: the last day of the month in `working_days`. `SPECIFIC_DAY`: `pay_day_of_month` of the **following** month, clamped |

Validation, all `400`: `working_days` non-empty, no duplicates (`paySchedules.js:197`);
`pay_day_of_month` 1–28 required when `SPECIFIC_DAY`, absent otherwise (`:180`);
`input_cutoff_day` 1–28; `first_period_start` is the first of a month.
`first_period_start` may not move earlier than a period that already has a pay run — `W-29`
adds that check when pay runs exist; here it is a comment naming `W-29`.

## 5. Frontend changes

None. The page is `W-47`'s, and per `D-60` it shows this row and `W-18.1`'s policy together.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V054__pay_schedule.sql` | `payroll.pay_schedule` | yes | additive |

`V054` extends the payroll lane's block by one (`DEV-TRACKER.md` § lanes).

**`pay_schedule`**, from `PaySchedule.java:17-54` with the fixes above:
`id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`frequency varchar(16) NOT NULL DEFAULT 'MONTHLY' CHECK (frequency IN ('MONTHLY'))` ·
`working_days smallint[] NOT NULL` — ISO day numbers 1 (Monday) to 7 (Sunday), `CHECK (cardinality(working_days) BETWEEN 1 AND 7)` ·
`pay_day_rule varchar(24) NOT NULL CHECK (pay_day_rule IN ('LAST_DAY_OF_PERIOD','LAST_WORKING_DAY','SPECIFIC_DAY'))` ·
`pay_day_of_month smallint CHECK (pay_day_of_month BETWEEN 1 AND 28)` ·
`input_cutoff_day smallint NOT NULL DEFAULT 25 CHECK (input_cutoff_day BETWEEN 1 AND 28)` ·
`first_period_start date NOT NULL` ·
four audit columns as `V010__employee.sql:19-22`.

| Legacy field | Here |
|---|---|
| `payScheduleId` random 10-digit string (`:17-18`, `PayScheduleServiceImpl.java:29-37`) | **dropped** — `id uuid` |
| `payScheduleType` `"monthly"` (`:20-21`) | `frequency`, `CHECK` |
| `payDay` string, `"last_working_day"` or `"1"`–`"28"` (`:23-24`, `paySchedules.js:232`) | `pay_day_rule` + `pay_day_of_month`; `LAST_DAY_OF_PERIOD` added because that is what every run after the first actually did (`PayRunServiceImpl.java:299`) |
| `payPeriodStartDate`, `payPeriodEndDate`, `payDate` (`:26-33`) | `first_period_start` only; end and pay date are derived (§4), never stored |
| `workingDays` `List<String>` in a collection table `payScheduleWorkingDays` (`:35-38`) | `working_days smallint[]` — one table, ISO numbers, so `Set<DayOfWeek>` is a direct map |
| `noOfWorkingDays` (`:40-41`) | **not ported** — `core.lop_policy.configured_days_per_month` (`W-18.1` §6, `D-60`) |
| `workingDaysCalculationType` `"actual_days"` / `"org_days"` (`:49-50`, `paySchedules.js:491,503`) | **not ported** — `core.lop_policy.working_day_basis` `ACTUAL_DAYS` / `ORG_DAYS` |
| `includeHolidays`, `includeWeekends` (`:43-47`) | **not ported** — `core.lop_policy.holidays_payable`, `weekends_payable` |
| — | `input_cutoff_day` **new** — the "cut-off" of `08-work-plan.md:87`; legacy had none |

- [x] `tenant_id`, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `uk_pay_schedule_tenant (tenant_id)` unique: one schedule per tenant, the same rule as `PaySchedule.java:53`
- [x] No money column. Day numbers are `smallint`; no day *count* is stored here — counts are `W-18.1`'s `numeric(10,2)`
- [x] Expand / contract — one new table, no destructive step

RLS and `tenant_isolation` in the exact `CASE` form — `migration/README.md` §row-level security.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../schedule/PayPeriodRulesTest.java` | each `pay_day_rule` over February and July; `LAST_WORKING_DAY` with Mon–Fri when the 31st is a Saturday; `SPECIFIC_DAY` 28 lands in the following month; cut-off 31 clamped to 28 in February; validation refusals |
| Unit | `payroll/.../schedule/PayScheduleWorkingWeekSourceTest.java` | `{1..5}` → `MONDAY..FRIDAY`; `{1..6}` includes `SATURDAY`; no schedule → `NoPayScheduleException` |
| Integration | `payroll/.../schedule/PayScheduleIT.java` | `GET` before any `PUT` returns `exists: false` and writes no row; `PUT` twice leaves one row; `period` for a month before `first_period_start` is `400` |
| Integration | `payroll/.../schedule/WorkingWeekBasisIT.java` | **the wiring the ticket exists for:** tenant A Mon–Fri, tenant B Mon–Sat, both on an `ORG_DAYS` policy with no `n`; `WorkingDayBasisCalculator.basisFor` returns different `payableDays` for the same July — 23 and 27 |
| Integration | `payroll/.../schedule/PayScheduleRlsIT.java` | as `app_user`, tenant A cannot read or upsert tenant B's schedule; `weekdaysFor(B)` under A's binding throws |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. `WorkingWeekBasisIT`
writes its two policies through `W-18.1`'s `LopPolicyService`, which is why that ticket blocks.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='payroll.pay_schedule'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='pay_schedule'
      AND column_name IN ('working_days','first_period_start','input_cutoff_day');"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexdef FROM pg_indexes WHERE schemaname='payroll' AND indexname='uk_pay_schedule_tenant';"
cd code/backend && mvn -q verify
grep -rn "WorkingWeekSource" code/backend/payroll/src/main/java | wc -l
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Types | `working_days` is `ARRAY`; `first_period_start` is `date`; `input_cutoff_day` is `smallint` |
| Index | one row, `UNIQUE ... (tenant_id)` |
| Suite | green, no skips; `WorkingWeekBasisIT` present and passing with 23 and 27 |
| Port | exactly one implementation in `payroll`; `grep -rn "pay_schedule" code/backend/core` is empty |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The legacy calculation-type and days-per-month fields are ported onto this table | **high — the screen posts them together** (`paySchedules.js:230-241`) | §6 says not ported, twice; the verification greps `core` for `pay_schedule` and the reverse is reviewed by name |
| `core` gains an import of `payroll` to reach the schedule | low | `maven-enforcer` fails the build; the port is the only path |
| The bean is missing at runtime, so every `ORG_DAYS` tenant fails its run | low | `@Component` in `payroll`, which `app` always loads; `WorkingWeekBasisIT` runs through the real bean |
| `GET` creates the row, as legacy `FBP` did | medium — the port habit | `PayScheduleIT` asserts zero rows after a `GET` |
| Two schedule rows for one tenant through a race | low | the unique index, not a `findBy…isPresent()` check (`PayScheduleServiceImpl.java:44`) |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `payroll.pay_schedule` |
| Flyway only, `ddl-auto` nowhere | one script, `V054` |
| `Money`/`BigDecimal` for money | no money column created |
| Index on `tenant_id` plus lookup columns | `uk_pay_schedule_tenant` |
| Expand / contract | new table only |
| No module references another module | `payroll` references `shared` and `core` (the port interface) only — the direction `12-core-contracts.md` §3 allows |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for this table** |
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-008 hand-built envelope | **Fixed** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** — the one finder takes `tenantId` |
| Working-day fields never read (`W-18.1` §12 row 3) | **Fixed** — `WorkingWeekBasisIT` is the proof |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Where do basis, days-per-month and the two payable flags live? | **`core.lop_policy`, not here** — `D-60`. This table holds only what a calendar needs: the weekdays, the pay-day rule, the cut-off, the first period |
| 2 | Store the next period, or derive it? | **Derive.** Legacy stored the first period and then ignored the row; `periodFor(YearMonth)` is the same answer for every month and needs no state to roll forward |
| 3 | Pay date on the schedule, or on the run? | **Derived here, copied onto the run by `W-29`.** A run keeps the date it was paid on even if the rule changes later |
| 4 | Cut-off as a date or a day of month? | **Day of month**, 1–28, default 25. It is the day `W-29` calls `PayInputService.lock(period)` (`W-19` §2) |
| 5 | Screen? | **`W-47`.** The frontend is an empty shell (`code/frontend/src/shell/routes.js:8-12`) until `W-45` |
