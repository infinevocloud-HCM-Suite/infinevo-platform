# Feature: Attendance preferences

| Field | Value |
|---|---|
| **Feature ID** | `W-40.1` · from ticket `W-40` (#51–52) · `HRMS-04` |
| **Promoted to** | `docs/target-state/features/W-40-1-attendance-preferences.md` on branch `dev-sayeed` — **`W-40-1` with hyphens**, never `W-40.1`; `guard-edit` blocks the dotted form |
| **Owner** | sayeed, branch `dev-sayeed` |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-007 (fixed), DEBT-013 (not carried), DEBT-018 (honoured), DEBT-022 (fixed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-30 |
| **Blocked by** | nothing in code — `W-12.2` (`@RequiresModule`) and `W-11.3` (the `core.attendance.*` codes) are on `main`. The tracker's wait on `W-16` is kept until the founder lifts it; this part reads no leave data |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` | 1 |
| Flyway migration | 1 script, one table — `hrms.attendance_preference` | 1 |
| Externally testable behaviour | a tenant's attendance settings save and read back, and a tenant with none gets the defaults | 1 |
| Frontend area | none (`10-scoping.md:117`, `BE`) | 1 |

Within cap. `W-40` was split on 2026-09-30 into six parts: `.1` preferences (this), `.2` Core
seams, `.3` clock sessions, `.4` regularization, `.5` overtime request states, `.6` overtime
request workflow.

---

## 1. Problem

The attendance settings live in the wrong application and nothing reads them.

- **Orphaned.** Payroll holds `attendance_preferences` and a settings screen, but Payroll has no
  clock. Only its own controller, service and repository reference the entity —
  `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/leaveAndAttendance/attendance/AttendancePreferenceController.java:11-60`.
  The design moves it to `hrms` (`02-data-model.md:147,278`).
- **The clock ignores it.** HRMS hard-codes 9 hours for present and 4.5 for a half day —
  `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/service/AttendanceService.java:143-149`.
- **Hours are strings.** `"08:00"` in a `String` column —
  `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/leaveAndAttedance/attendance/AttendancePreference.java:36-49`.
- **Many rows per organisation.** `POST` always inserts and the list endpoint returns them all
  (`.../serviceimpl/leaveAndAttendance/attendance/AttendancePreferenceServiceImpl.java:30-41,83-91`);
  the screen uses whichever comes first.
- **Half the fields are constants.** The screen sends fixed values for six of them —
  `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/leaveAttendence/attendence.js:114-123,128,131`.

## 2. Scope

**In scope**

- `hrms.attendance_preference`: one row per tenant
- Read the settings; save them (create or replace)
- A read seam inside `hrms` that returns the defaults when the tenant has no row — `W-40.3`
  and `W-40.4` call it

**Out of scope**

- Anything that *uses* the settings — clock sessions are `W-40.3`, regularization limits `W-40.4`
- The three pay flags (`canIncludeHolidaysForPay`, `canIncludeLeavesForPay`,
  `canIncludeWeekendsForPay`, `AttendancePreference.java:27-34`) — they are
  `core.lop_policy`'s, `W-18.1` (`02-data-model.md:278`, `W-28-pay-schedule.md:166`)
- Shift times (`checkInTime`, `checkOutTime`, `attendence.js:125-126`) — the entity has no such
  columns, so they were never stored
- Screens — Stream F
- Effective-dated history. A change applies to days derived after it; past days are not re-derived

## 3. Flow

```
[HR]      --> GET /api/v1/hrms/attendance/preferences --> AttendancePreferenceService.current() --> hrms.attendance_preference, or defaults
[HR]      --> PUT /api/v1/hrms/attendance/preferences --> AttendancePreferenceService.save()    --> hrms.attendance_preference (upsert)
[W-40.3, W-40.4] --> AttendancePreferenceService.current()
   all: TenantContext (W-08) --> @RequiresModule(HRMS) (W-12.2) --> @RequiresAction (W-11.2) --> RLS
```

## 4. Backend changes

All new, under `code/backend/hrms/src/main/java/com/infinevo/hrms/attendance/`. Package
convention from `code/backend/hrms/src/main/java/com/infinevo/hrms/package-info.java:7-9`.

| Layer | File | Change |
|---|---|---|
| Controller | `AttendancePreferenceController.java` | New. `@RequiresModule(PlatformModule.HRMS)` on the class, as `payroll/.../reimbursement/ReimbursementClaimController.java:36` does for `PAYROLL` |
| Service / ServiceImpl | `AttendancePreferenceService.java`, `AttendancePreferenceServiceImpl.java` | New. `current()`, `save(request)` |
| Entity | `AttendancePreference.java` | New. `@Audited`, `@Table(schema = "hrms")`, `UUID id`, `UUID tenantId` |
| Repository | `AttendancePreferenceRepository.java` | New. `findByTenantId` |
| DTO | `AttendancePreferenceRequest.java`, `AttendancePreferenceResponse.java` | New records. Response carries `isDefault` |
| Enumeration | `HoursCalculation.java` | `FIRST_IN_LAST_OUT`, `EVERY_SESSION` — the screen's two options (`attendence.js:92,113`) |
| `hrms/pom.xml` | dependencies | Add `spring-boot-starter-web`, `spring-boot-starter-data-jpa` if `W-41` has not already — the module has only `spring-boot-starter` today (`code/backend/hrms/pom.xml:24-27`) |

Success responses are the bare DTO and errors are `ApiErrorResponse`, as
`core/.../attendance/AttendanceController.java:40-84`.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/hrms/attendance/preferences` | — | `200`, the saved row, or the defaults with `isDefault: true` | `core.attendance.read` |
| `PUT` | `/api/v1/hrms/attendance/preferences` | the six fields below | `200`, the saved row | `core.attendance.manage` |

Both codes are on `main` and held by `hr` (`M/core/V025__catalogue_correction.sql:169-170`).
`core.attendance.manage` was written for this: "Correct attendance and approve regularisations"
(`M/reference/V020__action.sql:93`). No new code is needed. The module check, not the code
prefix, keeps a Payroll-only tenant out.

**Fields, their source and their default**

| Field | Type | Default when no row | Legacy source |
|---|---|---|---|
| `hoursCalculation` | enum | `EVERY_SESSION` | `calculationOnFirstInLastOut` (`AttendancePreference.java:18-19`). Default is what HRMS does today: it sums sessions (`AttendanceService.java:129-138`) |
| `fullDayMinimumHours` | decimal hours | `9.00` | `fullDayMinimumHours` (`:36-37`). Default from `AttendanceService.java:143` |
| `halfDayMinimumHours` | decimal hours | `4.50` | `halfDayMinimumHours` (`:42-43`). Default from `AttendanceService.java:145` |
| `regularizationWindowDays` | integer, nullable | `null` — any past date | `requestDaysBuffer` (`:71-72`); `null` is the screen's "anytime" (`attendence.js:129,132`) |
| `maxRegularizationsPerMonth` | integer, nullable | `null` — no limit | `maximumRequestsAllowed` (`:65-66`); the period is always monthly (`attendence.js:131`) |
| `allowRegularizationWithoutSession` | boolean | `true` | `canCreateNewEntries` (`:59-60`); the screen always sends `true` (`attendence.js:128`) |

**Rules the service enforces**

| Rule | Refusal |
|---|---|
| `fullDayMinimumHours` or `halfDayMinimumHours` missing, ≤ 0 or > 24 | `400 VALIDATION_FAILED` |
| `halfDayMinimumHours` ≥ `fullDayMinimumHours` | `400 VALIDATION_FAILED` |
| `regularizationWindowDays` present and < 0 or > 366 | `400 VALIDATION_FAILED` |
| `maxRegularizationsPerMonth` present and < 1 or > 31 | `400 VALIDATION_FAILED` |
| Second `PUT` | Updates the one row; the row count stays 1 |

## 5. Frontend changes

None. The navigation item lands with the screen ticket (`NavigationCatalogue.java:14-19`).

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `hrms/V121__attendance_preference.sql` — reserved for `W-40.1`, 2026-09-30, above `V120` | `hrms.attendance_preference` | yes | forward-only; a new table, nothing destroyed |

```sql
CREATE TABLE hrms.attendance_preference (
    id                                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                            UUID NOT NULL REFERENCES core.tenant(tenant_id),
    hours_calculation                    VARCHAR(24) NOT NULL DEFAULT 'EVERY_SESSION',
    full_day_minimum_hours               NUMERIC(4,2) NOT NULL DEFAULT 9.00,
    half_day_minimum_hours               NUMERIC(4,2) NOT NULL DEFAULT 4.50,
    regularization_window_days           INTEGER,
    max_regularizations_per_month        INTEGER,
    allow_regularization_without_session BOOLEAN NOT NULL DEFAULT true,
    created_at / created_by / updated_at / updated_by   -- as core/V030__attendance.sql:12-15
    CONSTRAINT attendance_preference_calc_check  CHECK (hours_calculation IN ('FIRST_IN_LAST_OUT','EVERY_SESSION')),
    CONSTRAINT attendance_preference_hours_check CHECK (half_day_minimum_hours > 0
        AND half_day_minimum_hours < full_day_minimum_hours AND full_day_minimum_hours <= 24)
);
CREATE UNIQUE INDEX uk_attendance_preference_tenant ON hrms.attendance_preference (tenant_id);
-- + ENABLE ROW LEVEL SECURITY and the tenant_isolation policy, CASE form, copied from core/V030__attendance.sql:23-32
```

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | `hrms.attendance_preference` has both, in the same script |
| Flyway only, `ddl-auto` nowhere | one script; every statement names `hrms.` (`migration/README.md` §always name the schema) |
| Money as `Money` / `BigDecimal` | no money column. Hours are `NUMERIC(4,2)` / `BigDecimal`, never the legacy `String` |
| Index on `tenant_id` plus lookup columns (`DEBT-018`) | one unique index on `tenant_id`; it is the only lookup and covers the FK |
| Expand / contract | new table only |
| No module references another | `hrms` uses `shared` only here. Nothing in `core` or `payroll` reads this table |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/.../attendance/AttendancePreferenceServiceTest.java` | every rule in §4; `current()` with no row returns `EVERY_SESSION`, `9.00`, `4.50`, `isDefault = true` |
| Integration | `hrms/.../attendance/AttendancePreferenceIT.java` | `GET` before any `PUT` is `200` with defaults; `PUT` then `GET` round-trips every field at scale 2; a second `PUT` leaves one row; an audit row is written |
| Integration | `hrms/.../attendance/AttendancePreferenceRlsIT.java` | as `app_user`, tenant A's row is invisible to tenant B; a connection with no tenant sees zero rows |
| Integration | `hrms/.../attendance/AttendancePreferenceGuardIT.java` | `403` on `PUT` without `core.attendance.manage`; `403 MODULE_NOT_ENTITLED` for a tenant without HRMS on both methods |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. `hrms` has no test
application yet: add `HrmsTestApp` and `HrmsTestSchema` the way `payroll` has
`PayrollTestApp` and `PayrollTestSchema` (`code/backend/payroll/src/test/java/com/infinevo/payroll/`),
unless `W-41` has already. Copy, do not share across modules.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='hrms.attendance_preference'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexname FROM pg_indexes WHERE schemaname='hrms' AND tablename='attendance_preference' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type, numeric_precision, numeric_scale FROM information_schema.columns
    WHERE table_schema='hrms' AND table_name='attendance_preference' AND column_name LIKE '%hours' ORDER BY 1;"
cd code/backend && mvn -q verify
node .claude/hooks/check-done.mjs W-40.1
```

| Check | Expected |
|---|---|
| RLS on `hrms.attendance_preference` | `t` |
| Indexes | `attendance_preference_pkey`, `uk_attendance_preference_tenant` |
| Hour columns | two rows, both `numeric`, 4, 2 |
| Suite | green, no skips; the four test classes in §7 run |
| `check-done.mjs` | 5/5 |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `W-41` and this ticket both add the `hrms` starters and test application | high — both are the module's first code | Whoever merges second keeps one copy; stated in §4 and §7 |
| A tenant moves from the defaults to the screen's old `8` and `4` and is surprised that past days do not change | medium | §2 says days are not re-derived; the screen ticket shows it |
| Someone ports the dropped fields "for completeness" | medium | §13 lists each with the line that shows nothing reads it |

## 10. Rollback

Revert the application commit. The table stays and is unused; Flyway is forward-only.

## 11. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 no tenant column | **Fixed for this table** — `tenant_id` and RLS |
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-013 typo package `leaveAndAttedance/` | **Not carried** — new package `com.infinevo.hrms.attendance` |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** — the one finder takes `tenantId`, RLS behind it |

## 12. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `code/backend/migration` | `hrms/V121__attendance_preference.sql` |
| 2 | `code/backend/hrms` | entity, repository, service, controller, DTOs, tests, test application if absent |

## 13. Decisions — settled 2026-09-30

| # | Question | Answer |
|---|---|---|
| 1 | Defaults when a tenant has saved nothing | **What HRMS does today**: sum of sessions, 9 hours, 4.5 hours (`AttendanceService.java:129-149`). Not the Payroll screen's `08:00` / `04:00` (`attendence.js:95-96`), which nothing ever applied |
| 2 | One row or many per tenant | **One.** `PUT` replaces it. The legacy `POST`, `DELETE` and list are not ported |
| 3 | Hours as `"08:00"` strings | **Decimal hours**, `NUMERIC(4,2)`. The screen converts |
| 4 | `minimumHoursRequired`, `maximumHoursRequired` | **Not ported** — the screen always sends `true` (`attendence.js:114-115`) |
| 5 | `fullDayMaximumHours`, `halfDayMaximumHours` | **Not ported** — nothing reads a maximum, and the half-day one is a copy of the full-day one (`attendence.js:120-122`). Founder may overturn; it would be one nullable column |
| 6 | `minimumHoursForOvertime` | **Not ported** — it is a copy of the full-day minimum (`attendence.js:123`), and overtime is independent of attendance (`D-28`, `07-decisions.md:40`) |
| 7 | `periodType` | **Not ported** — always `"monthly"` (`attendence.js:131`); the column name says it |
| 8 | New permission codes | **None.** `core.attendance.read` / `.manage`, gated by `@RequiresModule(HRMS)` |
