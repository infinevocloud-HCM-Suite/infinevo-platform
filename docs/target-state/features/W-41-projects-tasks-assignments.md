# Feature: Projects, tasks, assignments

| Field | Value |
|---|---|
| **Feature ID** | `W-41` · ticket #53 · `HRMS-06`, `HRMS-07`, `HRMS-08` |
| **Promoted to** | `docs/target-state/features/W-41-projects-tasks-assignments.md` on the developer's `dev-<name>` branch |
| **Owner** | devashis |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for these tables), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-018 (honoured), DEBT-022 (fixed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-28 |
| **Blocked by** | nothing — `W-13.1` and `W-13.3` are on `main` (`ccc8e48`), `W-11.3` is on `main`. First real code in the `hrms` module |
| **Analysis** | `.claude/outputs/2026-09-28-analyze-w-41-projects-tasks-assignments.md` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` | 1 |
| Flyway migration | 4 scripts, one table each plus one permission script — aggregate exception, same as `W-26.1` | 1 — exception granted 2026-09-28 |
| Externally testable behaviour | an employee can be put on a project, and only their own tenant can see it | 1 |
| Frontend area | none (`10-scoping.md:118`, `BE`) | 1 |

Within cap. Tasks ride along because a task cannot exist without a project and the
timesheet ticket (`W-42`) needs both on the same day.

---

## 1. Problem

The frozen HRMS has project, task and assignment tables and screens, and they are being
ported, not invented (`08-work-plan.md:109`, `Port`). Four things are wrong and must not
be carried across.

- **Money as a float.** `Project.budget` is a `Double` —
  `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Project.java:32`.
  CI rejects floating-point money.
- **Copied names.** An assignment stores the employee's full name beside their id
  (`entity/Assignment.java:18-19`, built at `service/AssignmentService.java:55-62`), and a
  project stores its manager's name beside the manager id (`entity/Project.java:46-50`). Both
  go stale on a rename.
- **No duplicate check.** The same employee can be added to the same project twice
  (`service/AssignmentService.java:35-70` — lookup, build, save, no existence check).
- **Role checks by hand.** Who sees which projects is an inline `roles.contains("manager")`
  branch in each controller (`controller/ProjectController.java:35-40`,
  `controller/TaskController.java:41-51`).
- **No tenant column** on `projects`, `tasks`, `assignments` (`legacy/docs/GAP_INVENTORY.md:28`, BUG-002).

One legacy behaviour needs a decision rather than a fix: "my tasks" returns every task on
every project the employee is assigned to and ignores `assignee_id`
(`repository/TaskRepository.java:15-16`). See §13, decision 3.

## 2. Scope

**In scope**

- `hrms.project`, `hrms.task`, `hrms.assignment`
- Permission codes `hrms.project.*` — they do not exist yet (`12-core-contracts.md:127` names them; no migration seeds them)
- Create, list, get, update, soft delete for projects and tasks, tenant-scoped, under `/api/v1/hrms/projects/...`
- Add an employee to a project and remove them; list a project's team
- Employee self-service reads: my projects, my tasks
- Manager reads: projects I manage and their tasks

**Out of scope**

- Timesheet entries against a project or task — `W-42`
- Notifications on assignment — `W-20.1` events, added when `W-42` needs them
- Project counts for dashboards (`ProjectController.java:160-170`) — `W-44`
- Screens — Stream F

## 3. Flow

```
[HR] --> ProjectController --> ProjectService --> hrms.project
[HR] --> TaskController    --> TaskService    --> hrms.task (FK project)
[HR] --> AssignmentController --> AssignmentService --> hrms.assignment (FK project, FK core.employee)
[employee] --> GET /projects/mine, /tasks/mine  --> EmployeeService.currentEmployee() --> filter by assignment
[manager]  --> GET /projects?managed=true       --> EmployeeService.currentEmployee() --> filter by manager_employee_id
   all: TenantContext bound by W-08 --> @RequiresAction (W-11.2) --> RLS
```

## 4. Backend changes

All new, under `code/backend/hrms/src/main/java/com/infinevo/hrms/project/`. Package
convention from `code/backend/hrms/src/main/java/com/infinevo/hrms/package-info.java:7-9`.

| Layer | File | Change |
|---|---|---|
| Entity | `Project.java`, `Task.java`, `Assignment.java` | new, each `@Table(schema = "hrms")`, `UUID id`, `UUID tenantId` |
| Enumeration | `ProjectStatus` (`STARTED`, `COMPLETED`), `TaskStatus` (`TODO`, `IN_PROGRESS`, `IN_REVIEW`, `COMPLETED`), `Priority` (`LOW`, `MEDIUM`, `HIGH`) | new — the values the legacy screens use: `legacy/HRMS_Frontend/src/components/Projects/Project.jsx:764-765,953-955`, `Task.jsx:399-436` |
| Repository | three | new, every finder takes `tenantId` |
| Service / ServiceImpl | three pairs | new |
| Controller | three | new |
| DTO | `*Request.java`, `*Response.java` | new; `status` / `message` / `data` envelope (`CONVENTIONS.md` §3) |
| `hrms/pom.xml` | dependencies | add `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation` — the module has only `spring-boot-starter` today (`code/backend/hrms/pom.xml:24-27`) |

Employee lookups go through `com.infinevo.core.employee.EmployeeService` — `get(UUID)`
for validation, `currentEmployee()` for the self-service reads
(`code/backend/core/src/main/java/com/infinevo/core/employee/EmployeeService.java:24,39`).
Same pattern as `payroll` (`EmployeeSalaryServiceImpl.java:321`). No entity join to
`core.employee` from `hrms`; responses carry `employeeId` and the caller resolves names.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/hrms/projects` | name, category, description, startDate, endDate, priority, status, budget, managerEmployeeId | `201` | `hrms.project.manage` |
| GET | `/api/v1/hrms/projects` | `?status=&search=&managed=true` | list | `hrms.project.read`, or `hrms.project.read_team` with `managed=true` |
| GET | `/api/v1/hrms/projects/mine` | — | projects the caller is assigned to | `hrms.project.read_own` |
| GET | `/api/v1/hrms/projects/{id}` | — | one, with `progress` and team member ids | `hrms.project.read` / `read_team` (manager of it) / `read_own` (assigned to it) |
| PUT | `/api/v1/hrms/projects/{id}` | same as POST | `200` | `hrms.project.manage` |
| PUT | `/api/v1/hrms/projects/{id}/status` | `{status}` | `200` | `hrms.project.manage` |
| PUT | `/api/v1/hrms/projects/{id}/progress` | `{progress}` 0–100 | `200` | `hrms.project.manage` |
| DELETE | `/api/v1/hrms/projects/{id}` | — | `204`, soft | `hrms.project.manage` |
| GET | `/api/v1/hrms/projects/{id}/assignments` | — | team list | as `GET {id}` |
| POST | `/api/v1/hrms/projects/{id}/assignments` | `{employeeId}` | `201`; `409` if already on it | `hrms.project.manage` |
| DELETE | `/api/v1/hrms/projects/{id}/assignments/{employeeId}` | — | `204` | `hrms.project.manage` |
| POST | `/api/v1/hrms/projects/{id}/tasks` | title, description, assigneeEmployeeId, dueDate, priority, status, estimatedHours | `201` | `hrms.project.manage` |
| GET | `/api/v1/hrms/projects/{id}/tasks` | `?status=` | list | as `GET {id}` |
| GET | `/api/v1/hrms/tasks/mine` | — | tasks assigned to the caller | `hrms.project.read_own` |
| PUT | `/api/v1/hrms/tasks/{id}` | same as POST | `200` | `hrms.project.manage` |
| PUT | `/api/v1/hrms/tasks/{id}/status` | `{status}` | `200` | `hrms.project.manage`, or `hrms.project.read_own` when the caller is the assignee |
| DELETE | `/api/v1/hrms/tasks/{id}` | — | `204`, soft | `hrms.project.manage` |

The legacy endpoints being replaced: `ProjectController.java:28-213`,
`TaskController.java:29-106`, `AssignmentController.java:23-47`. The dashboard counts
(`ProjectController.java:160-170`) are not ported here.

Validation, all `400`: `name` unique within the tenant among non-deleted projects;
`endDate >= startDate` when both present; `budget` non-negative; `progress` 0–100;
`managerEmployeeId` and `assigneeEmployeeId` must be a live employee in the tenant
(`EmployeeService.get`, `404` from the seam becomes `400` here); a task's `assigneeEmployeeId`
must be on the project's team. Adding an employee who is already on the project is `409`.

Soft delete of a project cascades in the service: its tasks and assignments are soft deleted
in the same transaction. Nothing outside this ticket references these rows yet; `W-42` adds
the refusal while a timesheet references a project.

## 5. Frontend changes

None. Navigation item `hrms.projects` is **not** added: `NavigationCatalogue.java:14-19`
says an item lands with the ticket that ships its endpoint, and the screen ticket in Stream F
owns the label. Record it in that ticket.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V085__hrms_project_actions.sql` | none — inserts `reference.action`, extends `core.seed_system_roles`, backfills `core.role_action` | n/a | additive |
| `hrms/V086__project.sql` | `hrms.project` | yes | additive |
| `hrms/V087__task.sql` | `hrms.task` | yes | additive |
| `hrms/V088__assignment.sql` | `hrms.assignment` | yes | additive |

**`V085`–`V088` are reserved for `W-41`**, 2026-09-28, above the `V082`–`V084` block
(`DEV-TRACKER.md:48`). One table per script (`migration/README.md` §one table creation).
Copy `payroll/V046__ctc_structure.sql` for the shape and the RLS clause.

**`V085`** — the pattern is `core/V025__catalogue_correction.sql:27-45,119-243,262`:

| Code | Name | Granted to |
|---|---|---|
| `hrms.project.read` | View all projects and tasks | `hr` |
| `hrms.project.read_team` | View projects I manage | `manager` |
| `hrms.project.read_own` | View my projects and tasks | `employee` |
| `hrms.project.manage` | Create and change projects, tasks and assignments | `hr`, `manager` |

`platform-admin` and `tenant-admin` pick every code up automatically (`V025:139-145`).
`CREATE OR REPLACE` the seed function with the four rows added, then
`SELECT core.seed_system_roles(t.tenant_id) FROM core.tenant t` so existing tenants get them.

**`hrms.project`**, ported from `entity/Project.java:14-50`:
`id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` · `name varchar(128) NOT NULL` ·
`category varchar(64)` · `description text` · `start_date date` · `end_date date` ·
`priority varchar(16) NOT NULL` · `status varchar(16) NOT NULL` ·
`progress smallint NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100)` ·
`budget numeric(19,4)` · `manager_employee_id uuid REFERENCES core.employee(id)` ·
`is_deleted boolean NOT NULL DEFAULT false` · four audit columns as `V046`.
`manager_name` (`:49`) is dropped.

**`hrms.task`**, from `entity/Task.java:13-36`:
`id` · `tenant_id` · `project_id uuid NOT NULL REFERENCES hrms.project(id)` ·
`title varchar(128) NOT NULL` · `description text` ·
`assignee_employee_id uuid REFERENCES core.employee(id)` · `due_date date` ·
`priority varchar(16) NOT NULL` · `status varchar(16) NOT NULL` · `estimated_hours integer` ·
`is_deleted` · audit columns.

**`hrms.assignment`**, from `entity/Assignment.java:15-24`:
`id` · `tenant_id` · `project_id uuid NOT NULL REFERENCES hrms.project(id)` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`assigned_on date NOT NULL DEFAULT CURRENT_DATE` · `is_deleted` · audit columns.
`emp_name` (`:18-19`) is dropped.

- [x] `tenant_id` on all three, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `uk_project_tenant_name (tenant_id, name) WHERE NOT is_deleted`; `idx_project_tenant_deleted_status (tenant_id, is_deleted, status)`; `idx_project_tenant_manager (tenant_id, manager_employee_id)`; `idx_task_tenant_project (tenant_id, project_id, is_deleted)`; `idx_task_tenant_assignee (tenant_id, assignee_employee_id, is_deleted)`; `uk_assignment_tenant_project_employee (tenant_id, project_id, employee_id) WHERE NOT is_deleted`; `idx_assignment_tenant_employee (tenant_id, employee_id, is_deleted)`
- [x] Money column `budget numeric(19,4)`; entity `BigDecimal(precision = 19, scale = 4)`; nothing floating
- [x] Expand / contract — three new tables, no destructive step

RLS and `tenant_isolation` in the exact `CASE` form in each of `V086`–`V088` —
`migration/README.md` §row-level security.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/.../project/ProjectValidationTest.java` | end before start refused; negative budget refused; progress 101 refused; task assignee not on the team refused; second assignment of the same employee refused |
| Integration | `hrms/.../project/ProjectRlsIT.java` | as `app_user`, tenant A cannot read, update or assign on tenant B's project, task or assignment; the no-tenant connection sees zero rows |
| Integration | `hrms/.../project/ProjectCrudIT.java` | create project, add task, assign employee, `GET /projects/mine` and `/tasks/mine` for that employee, soft delete project hides its tasks and assignments; `budget` round-trips at scale 4 |
| Integration | `hrms/.../project/ProjectPermissionIT.java` | `hr` lists all; `manager` with `managed=true` sees only projects where they are manager; `employee` gets `403` on `POST /projects` |
| Integration | `core/.../authz/ActionCatalogueIT` (existing) | extended: the four `hrms.project.*` codes exist and every system role holds the grants in the table above |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`
(`code/backend/shared/src/test/java/com/infinevo/shared/test/`). `hrms` tests need a test
stand-in for `EmployeeService` the way `payroll` ITs have one (`active-work.md`, 2026-09-28
entry); copy it, do not share it across modules.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in project task assignment; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='hrms.$t'::regclass;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema='hrms' AND column_name='budget';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT code FROM reference.action WHERE code LIKE 'hrms.project.%' ORDER BY 1;"
cd code/backend && mvn -q verify
node .claude/hooks/check-done.mjs
```

| Check | Expected |
|---|---|
| RLS on all three | `t` three times |
| `budget` | `numeric` |
| Action codes | four rows: `manage`, `read`, `read_own`, `read_team` |
| Suite | green, no skips |
| `check-done.mjs` | 5/5 |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `hrms` gains a JPA join to `core.employee` | medium — the legacy service does exactly that (`AssignmentService.java:47`) | §4: ids only, `EmployeeService` for lookups; `maven-enforcer` does not catch a JPA join, review does |
| The four action codes are inserted but existing tenants' roles do not get them | medium | `V085` calls `seed_system_roles` for every tenant; `ActionCatalogueIT` asserts the grants |
| `budget` ported as `Double` | low — CI money gate | `check-done.mjs` |
| "my tasks" ported as all tasks on my projects | medium — that is the legacy query | §13 decision 3; `ProjectCrudIT` asserts a task assigned to a teammate is absent |

## 10. Rollback

Nothing is deployed. All four scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | all three, each in its own script |
| Flyway only, `ddl-auto` nowhere | four scripts |
| `Money`/`BigDecimal` for money | `budget numeric(19,4)`, entity `BigDecimal` |
| Index on `tenant_id` plus lookup columns | seven indexes, `tenant_id` leading, soft-delete column included |
| Expand / contract | new tables only |
| No module references another module | `hrms` uses `core` (`EmployeeService`) and `shared` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for these tables** — `tenant_id` + RLS |
| DEBT-007 no `/api/v1` | **Fixed** — all paths versioned |
| DEBT-008 hand-built response maps | **Fixed** — one envelope |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** — every finder takes `tenantId`, RLS behind it |

## 13. Decisions — settled 2026-09-28

| # | Question | Answer |
|---|---|---|
| 1 | Keep free-text `status` and `priority` (`Project.java:30,38`)? | **No — enums** with the values the legacy screens already send. Unknown values are `400` |
| 2 | Keep `category` as a picklist (`Project.jsx:913-917`)? | **Free text, `varchar(64)`.** The five legacy values are a screen concern |
| 3 | What does "my tasks" mean? | **Tasks where I am the assignee.** The legacy query returned every task on my projects (`TaskRepository.java:15-16`); a project's full task list is `GET /projects/{id}/tasks`, which an assigned employee may read |
| 4 | Manager on a project: free-text id and copied name (`Project.java:46-50`)? | **FK to `core.employee`, no name.** `read_team` means "projects where `manager_employee_id` is me"; it does not use the reporting line from `W-14.2` |
| 5 | Numbered `Long` ids? | **UUID**, matching `core.employee` and `W-26.1` decision 1 |
| 6 | Assignment stores a copied employee name? | **No.** Responses carry `employeeId`; the caller resolves names through `GET /api/v1/employees` |
