# Feature: Timesheet entry

| Field | Value |
|---|---|
| **Feature ID** | `W-42.1` · ticket #54 · `HRMS-09` |
| **Promoted to** | `docs/target-state/features/W-42-1-timesheet-entry.md` |
| **Owner** | devashis |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for these tables), BUG-007 (settled by `D-24`), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-013 (not carried), DEBT-018 (honoured), DEBT-022 (fixed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | nothing — `W-41` is on `main` (`5fa04b1`), `W-25` is on `main` (`a53e311`) |
| **Followed by** | `W-42.2` approval engine change (`core`), then `W-42.3` submit and approve |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` | 1 |
| Flyway migration | 4 scripts, one table each — aggregate exception, same as `W-41` | 1 — exception, same grounds |
| Externally testable behaviour | an employee saves and edits a draft week of hours against their own projects and tasks, and only they can see it | 1 |
| Frontend area | none — screens are `W-48` | 1 |

W-42 was split on 2026-10-02: entry here, the approval engine change in `W-42.2`, submit and approve in `W-42.3`. Nothing in this
ticket submits or approves.

---

## 1. Problem

Two timesheet systems are live in the frozen HRMS. `D-24` keeps the richer one: a weekly
header with project, task and day entries (`docs/target-state/07-decisions.md:36`). The
older flat timesheet is not ported. Which system's data migrates is `W-67`, not this ticket
(`docs/target-state/02-data-model.md:151-156`).

The richer system is being ported (`08-work-plan.md:110`, `Port`). These faults must not
come across:

- **The employee comes from the request body.** Save takes `employeeId` and
  `employeeName` from the DTO, so anyone can write a timesheet for anyone.
  `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/serviceimpl/timeshhet/TimesheetServiceImpl.java:77-78`
- **Hours are a `Float` with no limit.**
  `legacy/HRMS_Backend/.../entity/timesheet/DayEntry.java:27-28`
- **The same week can be saved twice.** Save builds and inserts with no existence check.
  `TimesheetServiceImpl.java:71-125`
- **Names are copied** beside ids: employee, project and task.
  `entity/timesheet/Timesheets.java:40-41`, `ProjectEntry.java:23-24`, `TaskEntry.java:23-24`
- **Delete and cancel ignore status and owner.** Delete removes an approved timesheet;
  cancel cancels any state.
  `TimesheetServiceImpl.java:313-338`
- **Any project and task id is accepted**, not only those the employee is assigned to. The
  picker offers assigned projects (`TimesheetServiceImpl.java:140-148`), but save does not
  check them (`:83-115`).
- **No tenant column** (`legacy/docs/GAP_INVENTORY.md:28`, BUG-002).

## 2. Scope

**In scope**

- `hrms.timesheet`, `hrms.timesheet_project_entry`, `hrms.timesheet_task_entry`, `hrms.timesheet_day_entry`
- Create, replace, delete (draft only) and read the caller's own timesheets, under `/api/v1/hrms/timesheets`
- `/api/v1/me/timesheet` serves the current week, replacing the `W-25` placeholder
- The `hrms.timesheets` menu item, through an `hrms` `NavigationContributor`
- `W-41`'s refusal: a project or task on a live timesheet cannot be deleted (`W-41` §4, line 135-137)

**Out of scope**

- Submit, approve, reject, resubmit, manager and HR views, notifications — `W-42.3`
- Reminders for late timesheets — `W-43`
- Dashboards — `W-44`
- Screens — `W-48`
- Moving legacy rows — `W-67`

## 3. Flow

```
[employee] --> TimesheetController --> TimesheetService --> hrms.timesheet
                                           |                  └ timesheet_project_entry (FK hrms.project)
                                           |                      └ timesheet_task_entry (FK hrms.task)
                                           |                          └ timesheet_day_entry
                                           ├ EmployeeService.currentEmployee()        (core)
                                           └ AssignmentRepository / TaskRepository    (hrms, W-41)
[employee] --> GET /api/v1/me/timesheet --> MyTimesheetController --> TimesheetService (current week)
   all: TenantContext --> @RequiresModule(HRMS) --> @RequiresAction --> RLS
```

## 4. Backend changes

All under `code/backend/hrms/src/main/java/com/infinevo/hrms/`. Copy `W-41`'s shape from
`hrms/project/`: envelope `ApiResponse.java`, exceptions, `TenantContext.require()` in every
service method (`AssignmentServiceImpl.java:39`).

| Layer | File | Change |
|---|---|---|
| Entity | `timesheet/Timesheet.java`, `TimesheetProjectEntry.java`, `TimesheetTaskEntry.java`, `TimesheetDayEntry.java` | new, `@Table(schema = "hrms")`, `UUID id`, `UUID tenantId`; parent to child `@OneToMany(cascade = ALL, orphanRemoval = true)` |
| Enumeration | `timesheet/TimesheetStatus.java` | `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`, as `legacy/.../enumuration/TimesheetStatus.java`. This ticket writes only `DRAFT`; `CANCELLED` stays in the set for legacy rows that `W-67` may bring across |
| Repository | four | new; every finder takes `tenantId` |
| Service / ServiceImpl | `TimesheetService`, `TimesheetServiceImpl` | new |
| Controller | `timesheet/TimesheetController.java` | new |
| DTO | `TimesheetRequest`, `TimesheetResponse` (nested projects, tasks, days) | new; responses carry ids only, no names |
| Portal | `portal/MyTimesheetPlaceholderController.java` | **replaced** by `portal/MyTimesheetController.java` on the same path, `GET /api/v1/me/timesheet` (`MyTimesheetPlaceholderController.java:1-25`); `TimesheetPanelProvider.java` unchanged |
| Navigation | `navigation/HrmsNavigation.java` | new `NavigationContributor`, copy of `payroll/navigation/PayrollNavigation.java:14-31` |
| W-41 | `project/ProjectServiceImpl.java` (`delete`, `:218`), `TaskServiceImpl.java` (`delete`, `:146`) | refuse `409` while any non-cancelled timesheet references the project or task |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/hrms/timesheets` | `weekStartDate`, `projects[{projectId, tasks[{taskId, days[{date, hours, description}]}]}]` | `201`, the timesheet as `DRAFT`; `409` if the caller already has a non-cancelled one for that week | `hrms.timesheet.submit` |
| PUT | `/api/v1/hrms/timesheets/{id}` | same body, `weekStartDate` must not change | `200`; replaces every entry; `409` unless `DRAFT` | `hrms.timesheet.submit`, owner only |
| DELETE | `/api/v1/hrms/timesheets/{id}` | — | `204`; removes the timesheet and all its entries; `409` unless `DRAFT` | `hrms.timesheet.submit`, owner only |
| GET | `/api/v1/hrms/timesheets/{id}` | — | one, nested | `hrms.timesheet.read_own`, owner only |
| GET | `/api/v1/hrms/timesheets/mine` | `?from=&to=&status=&projectId=` | list, newest week first | `hrms.timesheet.read_own` |
| GET | `/api/v1/me/timesheet` | `?weekStart=` defaults to this week's Monday | the caller's timesheet for that week, or `data: null` | `hrms.timesheet.read_own` |

All six carry `@RequiresModule(PlatformModule.HRMS)`. Not the owner is `404`, not `403`, so
ids do not leak. A login with no employee record is `403` on every path. `W-25` gave `500`
on `/me/employee` for this case, and that must not happen again (`active-work.md`,
2026-10-02 `W-16` entry).

The picker needs no new endpoint. `GET /api/v1/hrms/projects/mine` and
`GET /api/v1/hrms/projects/{id}/tasks` from `W-41` replace legacy's `get-initial-data`
and `get-tasks/{projectId}` (`legacy/.../controller/timesheet/TimesheetsController.java:99-121`).

Legacy endpoints replaced here: `TimesheetsController.java:50-206`, `:430` and `:551`. The
rest belong to `W-42.3`.

**Validation, all `400` unless stated**

| Rule | Legacy |
|---|---|
| `weekStartDate` is a Monday; the week is Monday to Sunday | default `LocalDate.now().with(MONDAY)`, `TimesheetsController.java:134-139` |
| Every `date` falls inside the week | not checked |
| Every `projectId` is a live project the caller is assigned to (`AssignmentRepository.existsByTenantIdAndProjectIdAndEmployeeIdAndDeletedFalse`, `AssignmentRepository.java:24`) | not checked |
| Every `taskId` is a live task on that project (`TaskRepository.findByIdAndTenantIdAndDeletedFalse`, `TaskRepository.java:20`) | not checked |
| A project appears once per timesheet, a task once per project, a date once per task | not checked |
| `hours` greater than 0 and at most 24, two decimals | `Float`, no limit |
| A day's total across all tasks is at most 24 | not checked |
| At least one project with one day entry | not checked |
| `description` at most 500 characters | default 255 |

## 5. Frontend changes

None. Screens are `W-48`.

**Navigation:** `HrmsNavigation` adds one item. `NavigationCatalogueValidator` refuses to
boot when an item's endpoint has no `GET` mapping (`NavigationCatalogue.java:10-13`).

| Code | Label key | Route | Endpoint | Module | Action |
|---|---|---|---|---|---|
| `hrms.timesheets` | `nav.hrms.timesheets` | `/hrms/timesheets` | `/api/v1/hrms/timesheets/mine` | `HRMS` | `hrms.timesheet.read_own` |

`NavigationCatalogue.java:19` lists this item as not built yet. Remove that one Javadoc line
in the same commit. It is a comment only, not a `core` code change.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `hrms/V141__timesheet.sql` | `hrms.timesheet` | yes | additive |
| `hrms/V142__timesheet_project_entry.sql` | `hrms.timesheet_project_entry` | yes | additive |
| `hrms/V143__timesheet_task_entry.sql` | `hrms.timesheet_task_entry` | yes | additive |
| `hrms/V144__timesheet_day_entry.sql` | `hrms.timesheet_day_entry` | yes | additive |

`V141`–`V144` are reserved for `W-42.1` (2026-10-02), above `W-38.1`'s `V140`. There is one
table per script (`migration/README.md:138-140`). Copy `hrms/V088__assignment.sql` for the
shape and the `CASE` RLS clause (`migration/README.md:87-135`). Every statement names its
schema (`:33-50`).

**No action script.** Every code this ticket needs already exists and is granted:
`hrms.timesheet.submit` and `read_own` to `employee`, `read` to `hr`
(`reference/V020__action.sql:97-99`, `core/V135__hrms_project_seed_roles.sql:64-66,130-131`).

Table names follow `02-data-model.md:145`. The nesting is legacy's: project, then task, then
day.

**`hrms.timesheet`**, from `entity/timesheet/Timesheets.java:16-55`:
`id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`week_start_date date NOT NULL CHECK (EXTRACT(ISODOW FROM week_start_date) = 1)` ·
`week_end_date date NOT NULL CHECK (week_end_date = week_start_date + 6)` ·
`status varchar(16) NOT NULL CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED'))` ·
`submitted_at timestamptz` · four audit columns as `V088`.
Dropped: `employee_name` (`:40-41`), and the display id `timesheet_id` built as
`"TS-" + (100 + id)` (`:24-25`, `TimesheetServiceImpl.java:121-123`).

**`hrms.timesheet_project_entry`**, from `ProjectEntry.java:11-40`:
`id` · `tenant_id` · `timesheet_id uuid NOT NULL REFERENCES hrms.timesheet(id) ON DELETE CASCADE` ·
`project_id uuid NOT NULL REFERENCES hrms.project(id)` ·
`status varchar(16) NOT NULL` with the same `CHECK` · `rejection_reason varchar(1000)` · audit.
Dropped: `project_name`. `status` and `rejection_reason` are carried so `W-42.3` adds no
column for them.

**`hrms.timesheet_task_entry`**, from `TaskEntry.java:11-33`:
`id` · `tenant_id` · `project_entry_id uuid NOT NULL REFERENCES hrms.timesheet_project_entry(id) ON DELETE CASCADE` ·
`task_id uuid NOT NULL REFERENCES hrms.task(id)` · audit. Dropped: `task_name`.

**`hrms.timesheet_day_entry`**, from `DayEntry.java:9-37`:
`id` · `tenant_id` · `task_entry_id uuid NOT NULL REFERENCES hrms.timesheet_task_entry(id) ON DELETE CASCADE` ·
`work_date date NOT NULL` · `hours numeric(4,2) NOT NULL CHECK (hours > 0 AND hours <= 24)` ·
`description varchar(500)` · audit. Dropped: `day_name`, which is derived from the date
(`:22-24`).

- [x] `tenant_id` on all four, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018):
  `uk_timesheet_tenant_employee_week (tenant_id, employee_id, week_start_date) WHERE status <> 'CANCELLED'` ·
  `idx_timesheet_tenant_status (tenant_id, status)` ·
  `uk_tpe_tenant_timesheet_project (tenant_id, timesheet_id, project_id)` ·
  `idx_tpe_tenant_project_status (tenant_id, project_id, status)` ·
  `uk_tte_tenant_entry_task (tenant_id, project_entry_id, task_id)` ·
  `idx_tte_tenant_task (tenant_id, task_id)` ·
  `uk_tde_tenant_entry_date (tenant_id, task_entry_id, work_date)`
- [x] No money. `hours` is `numeric(4,2)` and `BigDecimal` in the entity; never `Float` or `double`
- [x] Expand / contract: four new tables, no destructive step

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/.../timesheet/TimesheetValidationTest.java` | every row of the §4 validation table, one case each, including a Tuesday week start, 24.01 hours, and two tasks totalling 25 hours on one day |
| Integration | `TimesheetRlsIT.java` | as `app_user`, tenant A cannot read or write tenant B's rows in any of the four tables; the no-tenant connection sees zero rows |
| Integration | `TimesheetCrudIT.java` | create, replace and delete; a second create for the same week is `409`, and is allowed again after the first is deleted; replace or delete of a non-draft (row set to `SUBMITTED` in the test) is `409`; delete leaves no entry rows behind; `hours` round-trips at scale 2; a project the caller is not assigned to is `400` |
| Integration | `TimesheetPermissionIT.java` | another employee's id gives `404` on `GET`, `PUT` and `DELETE`; a login with no employee gives `403`, not `500`; a Payroll-only tenant gets the module refusal |
| Integration | `MyTimesheetIT.java` | `/api/v1/me/timesheet` returns this week's timesheet, and `data: null` when there is none |
| Integration | `ProjectCrudIT.java` (existing) | extended: deleting a project or a task on a draft timesheet is `409`; after that timesheet is deleted it is allowed |
| Unit | `portal/TimesheetPanelProviderTest.java` (existing) | still passes unchanged |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`, like `W-41`'s
(`hrms/src/test/java/com/infinevo/hrms/project/ProjectCrudIT.java`). Reuse `HrmsTestApp`
and `HrmsProjectTestSchema` from that package.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in timesheet timesheet_project_entry timesheet_task_entry timesheet_day_entry; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='hrms.$t'::regclass;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT data_type, numeric_precision, numeric_scale FROM information_schema.columns
   WHERE table_schema='hrms' AND table_name='timesheet_day_entry' AND column_name='hours';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexname FROM pg_indexes WHERE schemaname='hrms' AND tablename LIKE 'timesheet%' ORDER BY 1;"
cd code/backend && mvn -q verify
node .claude/scripts/check-done.mjs
```

| Check | Expected |
|---|---|
| RLS on all four | `t` four times |
| `hours` | `numeric`, `4`, `2` |
| Indexes | the seven in §6 plus the four primary keys |
| Suite | green, no skips |
| `check-done.mjs` | 5/5 |
| App boots | `NavigationCatalogueValidator` accepts `hrms.timesheets`; `/api/v1/me/timesheet` no longer returns `"status": "placeholder"` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The employee is taken from the body, as legacy does | medium; it is the legacy DTO shape | §4: the body has no `employeeId`; `TimesheetPermissionIT` |
| `hours` ported as `Float` | medium; it is the legacy type | `numeric(4,2)` and `BigDecimal`; the column check in §8 |
| `hrms` joins to `core.employee` in JPA | low; `W-41` set the pattern | ids only, `EmployeeService` for the caller |
| A draft delete leaves entry rows behind | low | `ON DELETE CASCADE` on all three child tables; `TimesheetCrudIT` counts them |
| `W-42.3` needs a column this ticket did not add | low | `status` and `rejection_reason` are already on the project entry |

## 10. Rollback

Nothing is deployed. All four scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | all four, each in its own script |
| Flyway only, `ddl-auto` nowhere | `V141`–`V144` |
| `Money`/`BigDecimal` for money | no money; `hours` is `numeric(4,2)`, `BigDecimal` |
| Index on `tenant_id` plus lookup columns | seven indexes, `tenant_id` leading |
| Expand / contract | new tables only |
| No module references another module | `hrms` uses `core` (`EmployeeService`, navigation, portal) and `shared` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for these tables**: `tenant_id` + RLS |
| BUG-007 duplicate entities | **Settled by `D-24`**: one model is built; the flat `Timesheet` is not ported. Data is `W-67` |
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-008 hand-built response maps | **Fixed**: one envelope; legacy returns bare strings (`TimesheetsController.java:80`) |
| DEBT-013 `timeshhet/` typo | **Not carried**: the new package is `timesheet/`. The typo stays in `legacy/` |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed**: every finder takes `tenantId`, with RLS behind it |

## 13. Decisions — set by the founder 2026-10-02

| # | Question | Answer |
|---|---|---|
| 1 | One timesheet per employee per week? | **Yes.** Legacy allowed duplicates (`TimesheetServiceImpl.java:71-125`) |
| 2 | Limit on hours? | **24 a day across all tasks.** No weekly limit. Legacy had none |
| 3 | Keep `DELETE`? | **Drafts only**, removed for good with their entries. Nothing submitted can be deleted. Legacy hard-deleted in any state (`TimesheetServiceImpl.java:313-318`) and cancelled in any state (`:323-338`); cancel is not ported |
| 4 | Copy project, task and employee names? | **No.** Ids only, as `W-41` decision 6 |
| 5 | A task on every line? | **Yes**, `task_id NOT NULL`. Legacy always sends one (`TaskEntry.java:20-21`) |
| 6 | Week start | **Monday**, as legacy (`TimesheetsController.java:138`). Not per tenant |

## 14. As built (devashish, 2026-10-02)

Where the build differs from, or settles a point left open in, the sections above:

- A project line with no tasks, or a task line with no days, is rejected (`projects[i].tasks`, `projects[i].tasks[j].days`). An empty line has no meaning and would leave a row nothing can reach.
- JSON is snake_case, as `W-41`, and every field also accepts its camelCase name (`@JsonAlias`).
- The project and task checks give one message, "No such project, or you are not assigned to it", for a missing id, another tenant's id and an unassigned one, so the response does not say which ids exist.
- `W-25`'s `MyTimesheetPlaceholderController` and its test are replaced by `MyTimesheetController`; `PortalEntitlementIT` and `PortalPanelDiscoveryIT` now mock `TimesheetService` instead.
- The error handler is `@ControllerAdvice(assignableTypes = ...)`, not `@RestControllerAdvice`: `EntitlementCoverageIT` counts any file whose code contains `@RestController` and wants `@RequiresModule` on it.
- Deleting a project or task that a live timesheet line names now answers 409 (`ResourceInUseException`), through a `TimesheetUsage` port that `hrms.project` owns and `hrms.timesheet` implements.
