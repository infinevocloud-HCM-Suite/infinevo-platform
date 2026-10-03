# Feature: Attendance screens — clock, history, HR log, settings

| Field | Value |
|---|---|
| **Feature ID** | `W-48.4` · screens for `W-40.1`, `W-40.3` (ticket #68) · `HRMS-*` |
| **Promoted to** | `docs/target-state/features/W-48-4-attendance-screens.md` — **hyphens**, never `W-48.4` |
| **Owner** | *(unassigned)* |
| **Apps touched** | `code/frontend/src/hrms/attendance` (new); `code/backend/hrms` — one added field, three menu items |
| **Related gaps** | none |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-03 |
| **Blocked by** | nothing — `W-40.1`, `W-40.3` on `main` `2780d098`. **Overlaps karma's `W-40.4`, `W-40.6`** in `hrms/attendance` — see §9 |
| **Size** | M |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` — one added field, three menu items | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an employee clocks in and out and sees their days; HR sees everyone's sessions and sets the attendance rules | 1 |
| Frontend area | `src/hrms/attendance` | 1 |

---

## 1. Problem

The clock and preference APIs are on `main` with no screen and no menu item (`hrms/.../navigation/HrmsNavigation.java:25-28`
lists only timesheets). The frozen clock lives inside the employee dashboard
(`legacy/HRMS_Frontend/src/components/EmployeeDashboard/EmployeeDashboard.jsx:350-394`), the history in
`EmployeeDashboard/UserAttendence.jsx` (493 lines, MUI) and the HR view in `adminDashboard/AdminAttendance.jsx`
(361). The preferences screen was Payroll's
(`legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/leaveAttendence/attendence.js`, per
`W-40-1-attendance-preferences.md:45`).

Two facts about the API the screens must follow, not fix:

- **No envelope, camelCase.** `ClockController` and `AttendancePreferenceController` return the record bare
  (`attendance/ClockController.java:38-68`, `AttendancePreferenceController.java:42-52`); the records have no
  `@JsonProperty`, so fields are `clockInAt`, `workedMinutes` (`ClockSessionResponse.java`, `TodayResponse.java`,
  `AttendancePreferenceResponse.java`). Projects and timesheets are snake_case in an envelope. Changing either
  breaks `W-40`'s tests and is not this ticket's.
- **The HR session list names nobody** — `employeeId` only (`ClockSessionResponse.java`).

## 2. Scope

**In scope**

- `hrms`: `employeeName` on the sessions list; menu items `hrms.attendance`, `hrms.attendance_log`,
  `hrms.attendance_settings`
- `/hrms/attendance` — clock in, clock out, today's sessions and worked time, my days for a month
- `/hrms/attendance-log` — HR: everyone's sessions for a date range, filter by employee
- `/hrms/attendance-settings` — HR: hours calculation, full- and half-day minimums, regularization window,
  monthly limit, regularization without a session

**Out of scope**

- Regularization and overtime requests — `W-48.5`, after `W-40.4`, `W-40.6`
- A manager's team attendance — no endpoint takes `core.attendance.read_team`
- Leave on the calendar — `core`, `W-46.2`
- Export (`core.attendance.export`) — no endpoint

## 3. Flow

```
[employee] /hrms/attendance
   today     --> GET  /v1/hrms/attendance/today            {date, openSession, sessions[], workedMinutes}
   clock in  --> POST /v1/hrms/attendance/clock-in          409 already in — show the message
   clock out --> POST /v1/hrms/attendance/clock-out         {session, date, workedMinutes, status, writtenToAttendance}
   month     --> GET  /v1/hrms/attendance/sessions/mine?from=&to=
[hr] /hrms/attendance-log
   --> GET /v1/hrms/attendance/sessions?from=&to=&employeeId=     both dates required (ClockController.java:64-69)
[hr] /hrms/attendance-settings
   --> GET /v1/hrms/attendance/preferences ; save PUT same path (core.attendance.manage)
```

All dates are the tenant's day, as the server computes them (`core/.../tenant/TenantClock.java:60`); the screen
shows the `date` the server returns and never derives "today" from the browser for a request.

## 4. Backend changes

All in `code/backend/hrms`.

| Layer | File | Change |
|---|---|---|
| DTO | `attendance/ClockSessionResponse.java` | add `employeeName` (camelCase, as the record) |
| Service | `ClockServiceImpl.allSessions` | fill it with one batch read through `core`'s `EmployeeService`; `mySessions` and `today` leave it `null` |
| Navigation | `navigation/HrmsNavigation.java` | add `hrms.attendance` → `/hrms/attendance`, `GET /api/v1/hrms/attendance/today`, `hrms.attendance.mark`; `hrms.attendance_log` → `/hrms/attendance-log`, `GET /api/v1/hrms/attendance/sessions`, `core.attendance.read`; `hrms.attendance_settings` → `/hrms/attendance-settings`, `GET /api/v1/hrms/attendance/preferences`, `core.attendance.manage` |

Who holds them: `employee` — `hrms.attendance.mark`; `hr` — `core.attendance.read`, `core.attendance.manage`;
`payroll-officer` — `core.attendance.read` (`migration/.../core/V139__hrms_request_seed_roles.sql:54-56,84,123`).
Settings is HR's menu item only; a payroll officer reads the log.

## 5. Frontend changes

`W-45` contract, except the reply is **not** unwrapped: these endpoints answer bare, so the service returns
`res.data`.

| File | Change |
|---|---|
| `src/hrms/attendance/attendanceService.js` | **new** — `today()`, `clockIn()`, `clockOut()`, `mySessions(from, to)`, `allSessions(from, to, employeeId)`, `preferences()`, `savePreferences(body)` |
| `src/hrms/attendance/ClockCard.jsx` | **new** — big Clock in / Clock out button by `openSession`; running time since `clockInAt`; today's sessions `Timeline`; worked time as h:mm from `workedMinutes`; after clock-out, the day `status` the server derived |
| `src/hrms/attendance/MyAttendance.jsx` | **new** — `ClockCard` on top; month `Calendar` or `Table` (date, first in, last out, worked, sessions, voided marked with `voidReason`) from `sessions/mine` |
| `src/hrms/attendance/AttendanceLog.jsx` | **new** — `RangePicker` (default this week, at most 31 days), employee `Select` built from the names in the rows; `Table` (employee, date, in, out, worked, origin, voided) |
| `src/hrms/attendance/AttendanceSettings.jsx` | **new** — Formik + Yup: `hoursCalculation` `Radio` (`FIRST_IN_LAST_OUT`, `EVERY_SESSION`); minimums `InputNumber` 0–24 step 0.25, half-day below full-day; window and monthly limit whole numbers ≥ 0; switch for without-session; "Using defaults" `Tag` when `isDefault`; Save by `useCan('core.attendance.manage')` |
| `src/hrms/index.js` | `routes` gains the three paths |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/hrms/attendance` | `MyAttendance` | `AppShell`; feed carries `hrms.attendance` |
| `/hrms/attendance-log` | `AttendanceLog` | feed carries `hrms.attendance_log` |
| `/hrms/attendance-settings` | `AttendanceSettings` | feed carries `hrms.attendance_settings` |

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `ClockFlowIT.java` (extend) | `/sessions` rows carry `employeeName`; `/sessions/mine` leaves it null |
| Unit | `HrmsNavigationTest.java` (extend) | the three items' endpoints have a `GET` |
| Unit | `attendanceService.test.js` | every function hits its path; returns `res.data` bare |
| Component | `ClockCard.test.jsx` | no open session shows Clock in; open session shows Clock out and running time; `409` shows the server message |
| Component | `AttendanceLog.test.jsx` | a range over 31 days is blocked; employee filter sends `employeeId` |
| Component | `AttendanceSettings.test.jsx` | half-day above full-day blocked; Save hidden without `core.attendance.manage` |

## 8. Verification

```bash
cd code/backend && ./mvnw -q -pl hrms -am spotless:check verify -Dtest='HrmsNavigationTest' -Dit.test='ClockFlowIT'
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d    # browser: an Acme employee, then HR
```

| Check | Expected |
|---|---|
| backend, lint, tests | `BUILD SUCCESS`; clean |
| employee clocks in, then out | session in today's list; worked time grows; day status shown |
| clock in twice in two tabs | second shows the `409` message |
| HR log | every employee's sessions with names; another tenant's never |
| HR settings | save, reload, values kept; payroll officer has no Settings item |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `W-40.4` / `W-40.6` (karma) change `ClockServiceImpl` or `ClockSessionResponse` at the same time | high | this ticket touches one field and one method; build after karma's branch merges, or take both in one developer's queue |
| Browser clock shows the wrong "today" near midnight | medium | dates come from the server's response; the running timer is display only |
| camelCase here, snake_case elsewhere confuses the next screen | certain | stated in §1 and the service comment; unifying it is its own ticket |

## 10. Rollback

Revert the branch. The field is additive; no data changes.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | none |
| `Money` / `BigDecimal` | no money; minimum hours sent as numbers with two decimals |
| Index | none new |
| Expand / contract | field added only |
| No module references another | `hrms` reads `core` only; `src/hrms` imports `@shared/*` and `@shell/screens` |

## 12. Gap inventory

None.

## 13. Decisions — founder, 2026-10-03

| # | Question | Answer |
|---|---|---|
| 1 | Clock on the dashboard, as legacy? | **On its own page.** `W-48.6`'s dashboard may link to it |
| 2 | Fix the camelCase and missing envelope while here? | **No.** It breaks `W-40`'s contract; the screens read what is there |
| 3 | Manager's team attendance? | **Not now.** No endpoint; raise it as its own ticket if wanted |
