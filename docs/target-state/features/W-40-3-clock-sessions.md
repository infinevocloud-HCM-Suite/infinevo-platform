# Feature: Clock in and out

| Field | Value |
|---|---|
| **Feature ID** | `W-40.3` · from ticket `W-40` (#51–52) · `HRMS-02` |
| **Promoted to** | `docs/target-state/features/W-40-3-clock-sessions.md` on branch `dev-sayeed` — **`W-40-3` with hyphens** |
| **Owner** | sayeed, branch `dev-sayeed` |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-007 (fixed), DEBT-018 (honoured), DEBT-022 (fixed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-30 |
| **Blocked by** | `W-40.1` (the thresholds it reads) and `W-40.2` (`recordFromClock`, `TenantClock`). `W-13.4` (`currentEmployee()`) is on `main` (`e699699`). The tracker's wait on `W-16` is kept until the founder lifts it |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` | 1 |
| Flyway migration | 1 script, one table — `hrms.clock_session` | 1 |
| Externally testable behaviour | an employee clocks in and out several times in a day and the day's status in `core.attendance` follows the hours worked | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

HRMS has a clock and it is being ported (`01-platform-shape.md:106`, `02-data-model.md:143`).
Five things in it must not come across.

- **The browser sets the time.** All three endpoints build the timestamp from `date` and
  `time` in the request body —
  `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/controller/AttendanceController.java:62-64,166-168,191-193`.
- **A "day" is not a date.** A new attendance record starts when none exists, when end-day was
  pressed, or when more than 9 hours passed since the first clock-in —
  `.../service/AttendanceService.java:71-100`. `core.attendance` is one row per employee per
  calendar date (`M/core/V030__attendance.sql:20`).
- **Anyone can clock anyone out.** Clock-out takes an attendance id from the path and never
  checks it is the caller's — `AttendanceController.java:160-172`, `AttendanceService.java:238-246`.
- **Two sessions can be open at once.** Clock-in adds a session without looking for an open one
  (`AttendanceService.java:52-56`); clock-out closes whichever it finds first (`:249-252`).
- **No tenant, and the employee is a string with the name copied in** —
  `.../entity/Attendance.java:19-23`, `BUG-002`.

What moves across: many sessions per day (`.../entity/ClockSession.java:9-25`), and the rule
that only closed sessions count (`AttendanceService.java:129-138`).

## 2. Scope

**In scope**

- `hrms.clock_session`: one row per clock-in, closed by a clock-out
- Clock in, clock out, "where am I today", my sessions, all sessions (HR)
- Deriving the day's status from its sessions and the tenant's preferences, and writing it to
  `core.attendance` through `AttendanceService.recordFromClock`
- A `rederive(employeeId, date)` seam inside `hrms` — `W-40.4` calls it after a correction

**Out of scope**

- Regularization — `W-40.4`. The `origin`, `voided_at` and `void_reason` columns are created
  here so `W-40.4` needs no `ALTER` for them
- End-day (`AttendanceController.java:180-201`) — not ported; a calendar date ends by itself
- A manager's team view — no "direct reports" read exists in `core` (`W-40-2-core-clock-seams.md` §2)
- Location, IP or device capture; shifts; a scheduled job that closes forgotten sessions
- Screens — Stream F

## 3. Flow

```
[employee] --> POST /api/v1/hrms/attendance/clock-in  --> ClockService.clockIn()  --> hrms.clock_session (open)
[employee] --> POST /api/v1/hrms/attendance/clock-out --> ClockService.clockOut() --> hrms.clock_session (closed)
                                                       --> ClockDayService.rederive(employee, date)
                                                       --> AttendanceService.recordFromClock --> core.attendance
[employee] --> GET  /api/v1/hrms/attendance/today, /sessions/mine
[HR]       --> GET  /api/v1/hrms/attendance/sessions
   all: TenantContext --> @RequiresModule(HRMS) --> @RequiresAction --> RLS
   who: EmployeeService.currentEmployee() — never an id from the request
   when: Instant.now(clock) on the server; date = TenantClock.dateOf(instant)
```

## 4. Backend changes

All new, under `code/backend/hrms/src/main/java/com/infinevo/hrms/attendance/`.

| Layer | File | Change |
|---|---|---|
| Controller | `ClockController.java` | New. `@RequiresModule(PlatformModule.HRMS)` on the class |
| Service / ServiceImpl | `ClockService.java`, `ClockServiceImpl.java` | New. Clock in, clock out, reads. Takes a `java.time.Clock` so tests control time, as `core/.../overtime/OvertimeServiceImpl.java:44-53` |
| Service / ServiceImpl | `ClockDayService.java`, `ClockDayServiceImpl.java` | New. `rederive(UUID employeeId, LocalDate date)` — the derivation below |
| Entity | `ClockSession.java` | New. `@Table(schema = "hrms")`. Not `@Audited` — the table is itself the event record and grows by several rows per employee per day |
| Repository | `ClockSessionRepository.java` | New. Every finder takes `tenantId` |
| DTO | `ClockSessionResponse.java`, `ClockDayResponse.java`, `TodayResponse.java` | New records |
| Enumeration | `SessionOrigin.java` (`CLOCK`, `REGULARIZATION`), `VoidReason.java` (`NOT_CLOCKED_OUT`, `REGULARIZED`) | New. Only `CLOCK` and `NOT_CLOCKED_OUT` are written here |

`hrms` holds `employeeId` as a `UUID`, no JPA join to `core.employee`
(`W-41-projects-tasks-assignments.md:97-101`).

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `POST` | `/api/v1/hrms/attendance/clock-in` | no body | `201`, the open session | `hrms.attendance.mark` |
| `POST` | `/api/v1/hrms/attendance/clock-out` | no body | `200`, the closed session and the day: `date`, `workedMinutes`, `status`, `writtenToAttendance` | `hrms.attendance.mark` |
| `GET` | `/api/v1/hrms/attendance/today` | — | `200`: `date`, the open session or null, today's sessions, `workedMinutes` | `hrms.attendance.mark` |
| `GET` | `/api/v1/hrms/attendance/sessions/mine?from=&to=` | span ≤ 93 days | `200`, the caller's sessions, newest first, voided ones included and marked | `core.attendance.read_own` |
| `GET` | `/api/v1/hrms/attendance/sessions?from=&to=&employeeId=` | span ≤ 93 days; `employeeId` optional | `200`, as above for the tenant | `core.attendance.read` |

All three codes are on `main`: `hrms.attendance.mark` and `core.attendance.read_own` held by
`employee`, `core.attendance.read` by `hr` (`M/core/V025__catalogue_correction.sql:169,224-225`).

**Rules the service enforces**

| Rule | Result |
|---|---|
| Caller's login is not linked to an employee | Refused the same way `payroll/.../reimbursement/ReimbursementClaimServiceImpl.java:64` refuses it — read it and match |
| Clock-in while a session from **today** is open | `409 CONFLICT` |
| Clock-in while a session from an **earlier date** is open | That session is voided (`void_reason = NOT_CLOCKED_OUT`) and the new one opens. It counted zero anyway (`AttendanceService.java:135-137`); the employee regularizes that day |
| Clock-out with no open session | `409 CONFLICT` |
| Clock-out more than 24 hours after the clock-in | `409 CONFLICT`, session left open — it is voided at the next clock-in |
| `from` after `to`, or span over 93 days | `400 VALIDATION_FAILED` |
| Session's date | `TenantClock.dateOf(clock_in_at)`. A night shift that ends after midnight belongs to the date it started |

**Deriving the day** — `ClockDayService.rederive`, run at every clock-out

| Step | Rule |
|---|---|
| 1 | Take the date's sessions that are closed and not voided |
| 2 | `EVERY_SESSION`: sum of each session's minutes. `FIRST_IN_LAST_OUT`: last `clock_out_at` minus first `clock_in_at` |
| 3 | Compare whole minutes to `AttendancePreferenceService.current()`: ≥ full-day minimum is `PRESENT`; ≥ half-day minimum is `HALF_DAY`; otherwise `ABSENT` |
| 4 | `AttendanceService.recordFromClock(employeeId, date, status)`; pass its `written` back as `writtenToAttendance` |

Nothing is written to `core.attendance` at clock-in. The legacy system marks the day `ABSENT`
the moment a person clocks in (`AttendanceService.java:62`, `Attendance.java:33`); here the
day has no row until the first clock-out, so an employee who is at work is never recorded absent.
Minutes are integers; no `double` hours (`AttendanceService.java:140`).

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `hrms/V123__clock_session.sql` — reserved for `W-40.3`, 2026-09-30 | `hrms.clock_session` | yes | forward-only; a new table |

```sql
CREATE TABLE hrms.clock_session (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id     UUID NOT NULL REFERENCES core.employee(id),
    attendance_date DATE NOT NULL,
    clock_in_at     TIMESTAMPTZ NOT NULL,
    clock_out_at    TIMESTAMPTZ,
    origin          VARCHAR(16) NOT NULL DEFAULT 'CLOCK',
    voided_at       TIMESTAMPTZ,
    void_reason     VARCHAR(24),
    created_at / created_by / updated_at / updated_by   -- as core/V030__attendance.sql:12-15
    CONSTRAINT clock_session_order_check  CHECK (clock_out_at IS NULL OR clock_out_at > clock_in_at),
    CONSTRAINT clock_session_origin_check CHECK (origin IN ('CLOCK','REGULARIZATION')),
    CONSTRAINT clock_session_void_check   CHECK ((voided_at IS NULL) = (void_reason IS NULL)
        AND (void_reason IS NULL OR void_reason IN ('NOT_CLOCKED_OUT','REGULARIZED')))
);
CREATE UNIQUE INDEX uk_clock_session_tenant_employee_open
    ON hrms.clock_session (tenant_id, employee_id) WHERE clock_out_at IS NULL AND voided_at IS NULL;
CREATE INDEX idx_clock_session_tenant_employee_date ON hrms.clock_session (tenant_id, employee_id, attendance_date DESC);
CREATE INDEX idx_clock_session_tenant_date          ON hrms.clock_session (tenant_id, attendance_date DESC);
-- + ENABLE ROW LEVEL SECURITY and the tenant_isolation policy, CASE form, copied from core/V030__attendance.sql:23-32
```

The partial unique index is what makes "one open session per employee" a database fact, so two
clock-ins racing each other cannot both succeed; the loser is `409`.

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | `hrms.clock_session` has both, in the same script |
| Flyway only, `ddl-auto` nowhere | one script; every statement names `hrms.` |
| Money as `Money` / `BigDecimal` | no money column; worked time is whole minutes |
| Index on `tenant_id` plus lookup columns (`DEBT-018`) | three indexes lead with `tenant_id`; the FK `employee_id` is covered; date indexes are descending, as `migration/README.md` rule 4 asks of a table that grows with time |
| Expand / contract | new table only |
| No module references another | `hrms` calls `core` (`AttendanceService`, `TenantClock`, `EmployeeService`) and `shared`. It never touches `core.attendance` directly |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/.../attendance/ClockDayDerivationTest.java` | sessions 09:00–13:00 and 14:00–19:00: `EVERY_SESSION` is 540 minutes and `PRESENT`; `FIRST_IN_LAST_OUT` is 600; 270 minutes is `HALF_DAY`, 269 is `ABSENT`; an open or voided session adds nothing; thresholds come from the preference, not constants |
| Unit | `hrms/.../attendance/ClockServiceTest.java` | every rule in §4 with a fixed `Clock`; a 23:30 clock-in in `Asia/Kolkata` closed at 06:00 belongs to the first date |
| Integration | `hrms/.../attendance/ClockFlowIT.java` | in, out, in, out → two sessions and one `core.attendance` row with `source = CLOCK`; second clock-in while open is `409`; after an admin `PUT` for the date, clock-out returns `writtenToAttendance = false` and the row stays `ADMIN`; no request field can set the time or the employee |
| Integration | `hrms/.../attendance/ClockSessionRlsIT.java` | as `app_user`, tenant A's sessions invisible to tenant B; no tenant bound sees zero rows |
| Integration | `hrms/.../attendance/ClockGuardIT.java` | `403` without `hrms.attendance.mark`; `403 MODULE_NOT_ENTITLED` for a Payroll-only tenant on every path; `/sessions` is `403` for `employee` |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='hrms.clock_session'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexname FROM pg_indexes WHERE schemaname='hrms' AND tablename='clock_session' ORDER BY 1;"
cd code/backend && mvn -q verify
node .claude/hooks/check-done.mjs W-40.3
```

| Check | Expected |
|---|---|
| RLS on `hrms.clock_session` | `t` |
| Indexes | `clock_session_pkey`, `idx_clock_session_tenant_date`, `idx_clock_session_tenant_employee_date`, `uk_clock_session_tenant_employee_open` |
| Suite | green, no skips; the five test classes in §7 run |
| `check-done.mjs` | 5/5 |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Someone ports the 9-hour rule or the request-body time because the legacy screen sends them (`legacy/HRMS_Frontend/src/components/EmployeeDashboard/UserAttendence.jsx:157,192`) | medium | §1 names both; `ClockFlowIT` asserts the endpoints take no body |
| A forgotten clock-out leaves a day with no `core.attendance` row | certain | By design: no row is not `ABSENT`. The employee regularizes (`W-40.4`) or HR enters the day. What a missing day means for pay is `W-18`'s |
| A preference change mid-month makes two days with the same hours differ | low | Days are derived once, at clock-out (`W-40-1-attendance-preferences.md` §2) |
| Night shifts longer than 24 hours between in and out | low | Refused at clock-out; regularization is the route |

## 10. Rollback

Revert the application commit. The table stays and is unused. `core.attendance` rows already
written with `source = CLOCK` stay; an administrator can overwrite or delete them.

## 11. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 no tenant column | **Fixed for this table** |
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** — the legacy `findTopByEmployeeIdOrderByInTimeDesc` (`AttendanceService.java:32-34`) becomes tenant-scoped finders behind RLS |
| Legacy `System.out.println` on every call (`AttendanceService.java:29-30`) | **Not carried** |

## 12. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `code/backend/migration` | `hrms/V123__clock_session.sql` |
| 2 | `code/backend/hrms` | entity, repository, two services, controller, DTOs, tests |

## 13. Decisions — settled 2026-09-30

| # | Question | Answer |
|---|---|---|
| 1 | What is a day | **A calendar date in the tenant's timezone**, the date of the clock-in. The 9-hour rule and end-day are dropped |
| 2 | Who sets the time | **The server.** The endpoints take no body |
| 3 | Who is clocking | **The caller**, from `currentEmployee()`. No id in the path |
| 4 | Forgotten clock-out | **Counts zero, as legacy**, and is voided at the next clock-in so it never blocks the employee |
| 5 | When is `core.attendance` written | **At each clock-out**, never at clock-in |
| 6 | Audit | **Not `@Audited`** — the session rows are the record |
