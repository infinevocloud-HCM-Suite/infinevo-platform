# Feature: Core seams for clock attendance

| Field | Value |
|---|---|
| **Feature ID** | `W-40.2` · from ticket `W-40` (#51–52) · supports `HRMS-02`, `HRMS-03`, `HRMS-12` |
| **Promoted to** | `docs/target-state/features/W-40-2-core-clock-seams.md` on branch `dev-sayeed` — **`W-40-2` with hyphens** |
| **Owner** | sayeed, branch `dev-sayeed` |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | none new — builds on `W-39.1` |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-30 |
| **Blocked by** | nothing in code — `W-39.1` (`3bf5b10`), `W-15` (`a34c14f`) and `W-12.1` (`b7d03ec`) are on `main`. The tracker's wait on `W-16` is kept until the founder lifts it |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 1 script, no table — two permission grants and one new code | 1 |
| Externally testable behaviour | a day written from the clock never overwrites a day an administrator entered | 1 |
| Frontend area | none | 1 |

Within cap. This part exists because `hrms` may not write a `core` table directly
(`W-39-1-attendance-capture.md:153`), and `core` today only accepts administrator entries.

---

## 1. Problem

Three things `hrms` needs from `core` are missing.

- **No way to write a clock day.** `AttendanceServiceImpl.upsert` stamps every row `ADMIN` —
  `code/backend/core/src/main/java/com/infinevo/core/attendance/AttendanceServiceImpl.java:102,109`.
  `CLOCK` is reserved but nothing can write it (`AttendanceSource.java:7-11`).
- **Precedence is undecided.** `W-39.1` left "admin and clock both write the same day" to
  `W-40` (`W-39-1-attendance-capture.md:187`).
- **No tenant clock.** "Today" is the server's date (`AttendanceServiceImpl.java:54`).
  `core.tenant.timezone` exists (`M/core/V033__tenant_locale_columns.sql:7`) but only the
  provisioning write and the worker's sweep read it (`core/.../tenant/TenantServiceImpl.java:31,62-72`,
  `worker/.../notification/ReminderEvaluator.java:85-93`).
- **Nobody but an administrator can approve.** `core.approval.decide` guards every decide
  endpoint (`core/.../approval/ApprovalController.java:43,51,75`), and no script grants it to
  `manager` or `hr` — the only match in the migrations is its definition
  (`M/core/V025__catalogue_correction.sql:47`). A reporting manager's step cannot be decided
  by the reporting manager.
- **`hrms.overtime.request` is named but not seeded.** `12-core-contracts.md:127` lists it;
  no migration inserts it.

## 2. Scope

**In scope**

- `AttendanceService.recordFromClock(employeeId, date, status)` — writes `source = CLOCK`
- The precedence rule, in one place
- `TenantClock` — the bound tenant's zone, today's date there, and the date of an instant there
- One reference script: the code `hrms.overtime.request` granted to `employee`;
  `core.approval.decide` granted to `manager` and `hr`

**Out of scope**

- Any endpoint. These are seams `W-40.3`, `W-40.4` and `W-40.6` call
- Moving `W-39.1`'s own future-date check to the tenant zone — a one-line follow-up, not this ticket
- A team read for managers. `core` has `chainAbove` and no "direct reports" read
  (`core/.../org/ReportingLineService.java:101`); `core.attendance.read_team` stays without an endpoint

## 3. Flow

```
W-40.3 clock-out / W-40.4 approval --> AttendanceService.recordFromClock --> core.attendance (source CLOCK)
                                                                         --> or nothing, when the day is ADMIN
W-40.3 / W-40.4 --> TenantClock.today(), .dateOf(instant) --> core.tenant.timezone
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Service | `core/.../attendance/AttendanceService.java`, `AttendanceServiceImpl.java` | Add `ClockDayResult recordFromClock(UUID employeeId, LocalDate date, AttendanceStatus status)` |
| DTO | `core/.../attendance/ClockDayResult.java` | New record: `written`, `status`, `source` — what the day holds after the call |
| Service | `core/.../tenant/TenantClock.java` | New. `ZoneId zone()`, `LocalDate today()`, `LocalDate dateOf(Instant)`. Reads `core.tenant.timezone` for `TenantContext.require()`; `null` or blank means `Asia/Kolkata`, the column default |

**The precedence rule**

| The day today | `recordFromClock` does | `written` |
|---|---|---|
| No row | Inserts with `source = CLOCK`, `remarks = null` | `true` |
| Row with `source = CLOCK` | Updates the status | `true` |
| Row with `source = ADMIN` | Nothing. Returns the row as it stands | `false` |

The administrator's `PUT /api/v1/attendance` is unchanged: it overwrites any row and stamps
`ADMIN` (`AttendanceServiceImpl.java:100-102`). So a person's correction always beats a
derived value, in both directions. Deleting the administrator's row (`DELETE`, `:134-145`)
hands the day back to the clock.

**Rules the service enforces**

| Rule | Refusal |
|---|---|
| Employee not in this tenant, or deleted | `IllegalArgumentException`, as `upsert` (`:84-87`) |
| `date` after `TenantClock.today()` | `IllegalArgumentException` |
| `status` null | `IllegalArgumentException` |

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `reference/V117__hrms_request_actions.sql` — reserved for `W-40.2`, 2026-09-30 | none. Inserts one `reference.action`, replaces `core.seed_system_roles`, backfills `core.role_action` | n/a | additive |

| Code | Status | Granted to |
|---|---|---|
| `hrms.overtime.request` — "Request overtime", module `hrms` | **new** | `employee` |
| `core.approval.decide` | exists (`V025:47`) | **adds** `manager`, `hr` |

Pattern: `M/reference/V097__reimbursement_claim_actions.sql:4-8` for the insert,
`:10-101` for the function, and the backfill that follows it.

**Copy the function body from the highest-numbered script on `main` that replaces
`core.seed_system_roles` on the day you build**, then add the three rows. Several lanes replace
this function; copying an older body silently drops another lane's grants for every new tenant.

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | creates no table |
| Flyway only, `ddl-auto` nowhere | one script; every statement names its schema |
| Money as `Money` / `BigDecimal` | no money |
| Index on `tenant_id` plus lookup columns | no new table or index |
| Expand / contract | inserts only; `ON CONFLICT DO NOTHING` |
| No module references another | `core` only. `hrms` calls these seams; `core` never imports `hrms` |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../attendance/AttendanceClockWriteTest.java` | the three rows of the precedence table; the three refusals |
| Integration | `core/.../attendance/AttendanceClockPrecedenceIT.java` | clock writes `PRESENT`; admin `PUT` sets `ABSENT`; a second clock write leaves `ABSENT` and `source = ADMIN`; after the admin `DELETE`, the clock write lands |
| Unit | `core/.../tenant/TenantClockTest.java` | an instant at 20:00 UTC is the next date in `Asia/Kolkata`; a null timezone falls back to `Asia/Kolkata` |
| Integration | `core/.../authz/ActionCatalogueIT.java` (existing) | extended: `hrms.overtime.request` exists and `employee` holds it; `manager` and `hr` hold `core.approval.decide`; **every grant present before this script is still present** for a tenant created after it |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT code, module FROM reference.action WHERE code = 'hrms.overtime.request';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT DISTINCT r.code FROM core.role_action ra JOIN core.role r ON r.id = ra.role_id
    WHERE ra.action_code = 'core.approval.decide' AND r.is_system ORDER BY 1;"
cd code/backend && mvn -q verify
node .claude/hooks/check-done.mjs W-40.2
```

| Check | Expected |
|---|---|
| Action row | `hrms.overtime.request`, `hrms` |
| Roles holding `core.approval.decide` | `hr`, `manager`, `platform-admin`, `tenant-admin` |
| Suite | green, no skips |
| `check-done.mjs` | 5/5 |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The seed function is copied from a stale script and drops another lane's grants | **high** — four scripts on `main` replace it (`V022`, `V025`, `V052`, `V097`) and `W-41`'s `V085` will | §6 instruction; `ActionCatalogueIT` asserts nothing was lost |
| A reporting manager who holds only the `employee` role still cannot approve | certain | Role assignment, not a code change. Recorded for the screen ticket `W-46.4` |
| `TenantClock` adds a query per clock call | low | One indexed read of `core.tenant`; cache it only if `W-63`'s load test says so |

## 10. Rollback

Revert the application commit. The grants stay; they are harmless without the endpoints.

## 11. Gap inventory

No legacy code is ported. BUG-002 and DEBT-018 were settled for `core.attendance` in `W-39.1`.

## 12. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `code/backend/migration` | `reference/V117__hrms_request_actions.sql` |
| 2 | `code/backend/core` | `recordFromClock`, `ClockDayResult`, `TenantClock`, tests |

## 13. Decisions — settled 2026-09-30

| # | Question | Answer |
|---|---|---|
| 1 | Admin row and clock row on the same date | **Admin wins**, both ways. `W-39.1` kept `source` so this was possible |
| 2 | Which clock is "today" | **The tenant's**, from `core.tenant.timezone` |
| 3 | Why is the permission script here and not in `W-40.6` | `W-40.4` needs the manager grant before `W-40.6` exists, and the catalogue is `core`'s |
