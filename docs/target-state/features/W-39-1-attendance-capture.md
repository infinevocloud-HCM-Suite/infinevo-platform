# Feature: Attendance capture (basic)

| Field | Value |
|---|---|
| **Feature ID** | `W-39.1` · from ticket `W-39` · `CORE-20` |
| **Promoted to** | `docs/target-state/features/W-39-1-attendance-capture.md` on branch `W-39-1-attendance-capture` — **`W-39-1` with hyphens**, never `W-39.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-018 (honoured), DEBT-013 (discounted) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-24 |
| **Blocked by** | nothing — `W-13.1` is on main (`7d0bab6`) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 1 script, one table — `core.attendance` | 1 |
| Externally testable behaviour | attendance an administrator records for a date range reads back correctly, and only in its own tenant | 1 |
| Frontend area | none | 1 |

Within cap. `W-39` was split on 2026-09-24: overtime capture (`core.overtime_request`, writes to
the pay input ledger) is `W-39.2` and waits on `W-19`.

---

## 1. Problem

A Payroll-only customer has no way to record who was present, absent or on a half day. `D-35`
puts that basic record in Core, entered by an administrator (`07-decisions.md:47`,
`01-platform-shape.md:81`, `02-data-model.md:104`). Nothing in `code/` holds attendance today.

The only source is HRMS, and it cannot be ported as it stands:

- **No tenant.** `Attendance.java:12-48` has no tenant or organisation column — `BUG-002`
  (`GAP_INVENTORY.md:28`).
- **Employee as a string, name copied in.** `employee_id` is a `String` and `employee_name` a
  copy of the name (`Attendance.java:20-24`). A renamed employee keeps the old name on every past day.
- **One day is a timestamp, not a date.** A day is found by `DATE(a.inTime)`
  (`AttendanceRepository.java:232,240`), so the record only exists if someone clocked in.
  There is nothing an administrator can enter directly.
- **Status is derived from clock hours** — 9 hours present, 4.5 half day
  (`AttendanceService.java:143-149`). That is the clock-based experience, which stays in HRMS (`W-40`).

What moves across is the status set only: `PRESENT`, `HALF_DAY`, `ABSENT`
(`AttendanceStatus.java:3-7`).

## 2. Scope

**In scope**

- `core.attendance`: one row per employee per date, with status, source and remarks
- Bulk upsert by an administrator (a month's grid in one call), list by date range, delete one row
- Audit capture through `@Audited`, the same way as the employee sections (`EmployeePersonal.java:42`)
- A read method inside `core` for `W-18`'s loss-of-pay policy to call, the only consumer (`D-34`)

**Out of scope**

- Clock in and out, sessions, regularization and preferences — `W-40`, HRMS
- Overtime — `W-39.2`
- Employee self-service view of own attendance — `W-25` / `W-40`
- Loss-of-pay derivation — `W-18`
- Screens — `W-46`
- Import from a spreadsheet — not in `CORE-20`; the bulk endpoint is enough for now

## 3. Flow

```
Administrator --> PUT /api/v1/attendance --> AttendanceService.upsert --> core.attendance
Administrator --> GET /api/v1/attendance?from&to[&employeeId] --> AttendanceService.list
W-18 (later)  --> AttendanceQuery.days(employeeId, from, to)   --> core.attendance
```

## 4. Backend changes

Package `com.infinevo.core.attendance`, following `com.infinevo.core.org`.

| Layer | File | Change |
|---|---|---|
| Controller | `AttendanceController.java` | New. Three endpoints below |
| Service | `AttendanceService.java`, `AttendanceServiceImpl.java` | New. Upsert, list, delete, validation |
| Service | `AttendanceQuery.java` | New. Read-only interface for `W-18`: `List<AttendanceDay> days(UUID employeeId, LocalDate from, LocalDate to)` |
| Entity | `Attendance.java` | New. `@Audited` |
| Repository | `AttendanceRepository.java` | New. Find by `(employeeId, date)`, by date range, by employee and range |
| DTO | `AttendanceEntry.java`, `AttendanceResponse.java` | New records |
| Enumeration | `AttendanceStatus.java` | `PRESENT`, `HALF_DAY`, `ABSENT` |
| Enumeration | `AttendanceSource.java` | `ADMIN` now; `CLOCK` is reserved for `W-40` |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `PUT` | `/api/v1/attendance` | `[{employeeId, date, status, remarks?}]`, 1–1000 entries | `200`, the saved rows | `hrms.attendance.manage` |
| `GET` | `/api/v1/attendance?from=&to=&employeeId=` | `from`, `to` required, span ≤ 93 days; `employeeId` optional | `200`, rows ordered by date desc, employee | `hrms.attendance.read` |
| `DELETE` | `/api/v1/attendance/{id}` | — | `204` | `hrms.attendance.manage` |

**Rules the service enforces**

| Rule | Refusal |
|---|---|
| Same `(employeeId, date)` twice in one request | `400 VALIDATION_FAILED` |
| Employee not found in this tenant, or `is_deleted` | `400 VALIDATION_FAILED`, naming the entry |
| Date in the future | `400 VALIDATION_FAILED` |
| `from` after `to`, or span over 93 days | `400 VALIDATION_FAILED` |
| Existing row for `(employeeId, date)` | Updated, not duplicated — the call is idempotent |
| One bad entry | Whole request rejected, nothing saved |

**Decision — the permission names.** The catalogue already holds `hrms.attendance.read` and
`hrms.attendance.manage` (`V020__action.sql:92-93`), granted to `hr`, `payroll-officer` (read)
and `tenant-admin` (`V022__role_action.sql:114-116,136`). This ticket uses them rather than
adding `core.attendance.*`, which would need a second migration and a rewrite of
`core.seed_system_roles`. The prefix is only a name: entitlement is checked by
`@RequiresModule` on the controller (`W-12-2-entitlement-enforcement.md` §2), and this
controller carries none, so a Payroll-only tenant reaches it. Leave and holiday have the same
misnamed prefix (`V020__action.sql`, `hrms.leave.*`, `hrms.holiday.*`). Renaming all three is one later catalogue ticket.

## 5. Frontend changes

None. Screens are `W-46`.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0nn__attendance.sql` — next free version at build time (`V025` on main today) | `core.attendance` | yes | forward-only; a new table, nothing destroyed |

```sql
CREATE TABLE core.attendance (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id     UUID NOT NULL REFERENCES core.employee(id),
    attendance_date DATE NOT NULL,
    status          VARCHAR(16) NOT NULL,
    source          VARCHAR(16) NOT NULL DEFAULT 'ADMIN',
    remarks         VARCHAR(255),
    created_at / created_by / updated_at / updated_by   -- as V013__work_location.sql:18-21
    CONSTRAINT attendance_status_check CHECK (status IN ('PRESENT','HALF_DAY','ABSENT')),
    CONSTRAINT attendance_source_check CHECK (source IN ('ADMIN','CLOCK'))
);
CREATE UNIQUE INDEX uk_attendance_tenant_employee_date ON core.attendance (tenant_id, employee_id, attendance_date);
CREATE INDEX idx_attendance_tenant_date ON core.attendance (tenant_id, attendance_date DESC);
-- + ENABLE ROW LEVEL SECURITY and the tenant_isolation policy, CASE form, copied from V013__work_location.sql:51-60
```

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | `core.attendance` has both, in the same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| Money as `Money` / `BigDecimal` | no money column |
| Index on `tenant_id` plus lookup columns (`DEBT-018`) | both indexes lead with `tenant_id`; the FK `employee_id` is covered by the unique index; the date index is descending, as `migration/README.md` rule 4 requires of a table that grows with time |
| Expand / contract | new table only |
| No module references another | `core` only. `W-40` (HRMS) will write `source = 'CLOCK'` through a `core` service, never the table |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../attendance/AttendanceServiceTest.java` | every rule in §4, including all-or-nothing on one bad entry |
| Integration | `core/.../attendance/AttendanceRlsIT.java` | tenant A's rows invisible to tenant B through the API and the repository |
| Integration | `core/.../attendance/AttendanceUpsertIT.java` | second `PUT` of the same day updates, row count unchanged; an audit row is written for the change |
| Integration | `core/.../attendance/AttendancePermissionIT.java` | `403` without `hrms.attendance.manage` on `PUT`; `payroll-officer` can `GET` but not `PUT` |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.attendance'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexname FROM pg_indexes WHERE schemaname='core' AND tablename='attendance' ORDER BY 1;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on `core.attendance` | `t` |
| Indexes | `attendance_pkey`, `idx_attendance_tenant_date`, `uk_attendance_tenant_employee_date` |
| Suite | green, no skips; the four test classes in §7 run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `W-40` later wants a second row per day for clock data | medium | Clock sessions live in `hrms.clock_session` (`02-data-model.md:143`); `W-40` writes the day's result here with `source = 'CLOCK'`. One row per day stays the rule |
| Admin and clock both write the same day | low until `W-40` | `W-40` decides precedence; this ticket only records `source` so the choice is possible |
| Reusing `hrms.*` permission names for a Core endpoint confuses a reader | medium | Stated in §4; a later catalogue ticket renames leave, holiday and attendance together |

## 10. Rollback

Revert the application commit. The table stays and is unused; Flyway is forward-only, so no
script is reverted.
