# Feature: Project and task screens

| Field | Value |
|---|---|
| **Feature ID** | `W-48.1` · screens for `W-41` (ticket #68) · `HRMS-*` |
| **Promoted to** | `docs/target-state/features/W-48-1-project-screens.md` — **hyphens**, never `W-48.1`; `guard-edit` blocks the dotted form |
| **Owner** | *(unassigned)* |
| **Apps touched** | `code/frontend/src/hrms/projects` (new); `code/backend/hrms` — names on four responses, one picker endpoint, two menu items |
| **Related gaps** | DEBT-007, DEBT-008 (already fixed by `W-41`); no new ones |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-03 |
| **Blocked by** | nothing — `W-41` on `main` `5fa04b1`, `W-45` shell on `main` `11ec157` |
| **Size** | M |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` — additive fields, one read endpoint, two menu items | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | HR or a project manager creates a project, assigns people, adds and moves tasks; an employee sees their projects and moves their own tasks | 1 |
| Frontend area | `src/hrms/projects` | 1 |

---

## 1. Problem

`W-41` gives an API and no screen; `src/hrms/index.js:3` registers no routes. The frozen screens are four MUI
pages (`legacy/HRMS_Frontend/src/components/Projects/Project.jsx`, 1,249 lines; `Projects/Task.jsx`, 818;
`EmployeeDashboard/MyProject.jsx`, 601; `EmployeeDashboard/MyTask.jsx`, 325), each behind its own legacy action
(`legacy/HRMS_Frontend/src/App.jsx:236-259`).

Two gaps stop a screen being built on the API as it is:

- **No names.** Projects, assignments and tasks carry employee ids only (`ProjectResponse.java` `manager_employee_id`,
  `team_member_ids`; `AssignmentResponse.java:14`; `TaskResponse.java:16`).
- **A manager cannot look anyone up.** The only employee search is `GET /api/v1/employees`, which needs
  `core.employee.read` (`core/.../employee/EmployeeController.java:71-72`). The `manager` role does not hold it
  (`migration/.../core/V139__hrms_request_seed_roles.sql:66-77`), so a project manager could not pick an assignee.

## 2. Scope

**In scope**

- `hrms`: names on project, assignment and task responses; an assignable-employee search for whoever holds
  `hrms.project.manage`; menu items `hrms.projects` and `hrms.my_work`
- `/hrms/projects` — list, search, status filter, create
- `/hrms/projects/:id` — details with edit, status, progress and delete; **Team** tab with assign and remove;
  **Tasks** tab with create, edit, status and delete
- `/hrms/my-work` — the caller's projects and tasks, moving their own tasks' status

**Out of scope**

- Timesheets — `W-48.2`, `W-48.3`
- Budget reporting; legacy shows `budget` as a field only, and so does this
- Any change to `W-41`'s rules

## 3. Flow

```
[hr | manager] /hrms/projects
   list   --> GET /v1/hrms/projects?status=&search=&managed=   managed=true unless useCan('hrms.project.read')
   create --> POST /v1/hrms/projects     manager picker --> GET /v1/hrms/employees/assignable?q=
[hr | manager] /hrms/projects/:id
   details  --> GET /v1/hrms/projects/{id}           edit PUT {id} · status PUT {id}/status · progress PUT {id}/progress
   delete   --> DELETE {id}                           409 when a live timesheet uses it — show the message
   Team     --> GET/POST {id}/assignments, DELETE {id}/assignments/{employeeId}   picker as above
   Tasks    --> GET/POST /v1/hrms/projects/{id}/tasks, PUT /v1/hrms/tasks/{id}, PUT {id}/status, DELETE {id}
[employee] /hrms/my-work
   --> GET /v1/hrms/projects/mine, GET /v1/hrms/tasks/mine, PUT /v1/hrms/tasks/{id}/status (own tasks only)
```

## 4. Backend changes

All in `code/backend/hrms`. Fields are added, none removed or renamed.

| Layer | File | Change |
|---|---|---|
| DTO | `project/ProjectResponse.java` | add `manager_name` and `team: [{employee_id, name}]`; `team_member_ids` stays |
| DTO | `project/AssignmentResponse.java` | add `employee_name` |
| DTO | `project/TaskResponse.java` | add `assignee_name`, `project_name` |
| Service | `ProjectServiceImpl`, `AssignmentServiceImpl`, `TaskServiceImpl` | fill names with one batch read of the ids on the page, through `core`'s `EmployeeService` — never one call per row |
| Controller | `project/AssignableEmployeeController.java` | **new** — `GET /api/v1/hrms/employees/assignable?q=`, `@RequiresModule(HRMS)`, `@RequiresAction("hrms.project.manage")`; at most 10 active employees matching name or number, `[{employee_id, employee_number, name}]`, through `core`'s employee search (`EmployeeController.java:78` calls `employeeQueryService.search`) |
| Navigation | `navigation/HrmsNavigation.java` | add `hrms.projects` → `/hrms/projects`, `GET /api/v1/hrms/projects`, action `hrms.project.manage`; `hrms.my_work` → `/hrms/my-work`, `GET /api/v1/hrms/projects/mine`, action `hrms.project.read_own` |

`hrms.project.manage` is the menu action because both `hr` and `manager` hold it (`V139…sql:64,77`); `read` and
`read_team` split between them and a menu item takes one action (`NavigationCatalogue.java:26-33`).

**API contract — new or changed**

| Method | Path | Response | Auth |
|---|---|---|---|
| GET | `/api/v1/hrms/employees/assignable?q=` | `ApiResponse<[{employee_id, employee_number, name}]>`, ≤ 10, `q` under 2 characters gives `[]` | `hrms.project.manage` |
| GET | every `W-41` project, assignment, task read | as today plus the name fields above | unchanged |

## 5. Frontend changes

`W-45` contract, as `src/payroll/priorpayroll`: `apiClient` from `@shared/api/client`, paths from `/v1/…`, reply
unwrapped as `res.data.data`; `useCan` and `NotEntitled` from `@shell/screens`.

| File | Change |
|---|---|
| `src/hrms/projects/projectService.js` | **new** — one function per endpoint in §3 |
| `src/hrms/projects/ProjectList.jsx` | **new** — `Table` (name, manager, status `Tag`, progress `Progress`, end date, team size); search `Input`, status `Select`; "New project" `Drawer` with `ProjectForm` (`useCan('hrms.project.manage')`) |
| `src/hrms/projects/ProjectForm.jsx` | **new** — Formik + Yup: name required, priority required, end date not before start date, manager from `EmployeePicker` |
| `src/hrms/projects/ProjectPage.jsx` | **new** — header with status, progress `Slider` (0–100) and Delete (confirm); `Tabs`: Details, Team, Tasks |
| `src/hrms/projects/TeamTab.jsx` | **new** — assignments `Table` with names; assign via `EmployeePicker` and date; remove with confirm |
| `src/hrms/projects/TasksTab.jsx` | **new** — tasks `Table` (title, assignee, due, priority, status, estimate), status filter; create and edit `Drawer`; assignee limited to the project's team |
| `src/hrms/projects/EmployeePicker.jsx` | **new** — `Select showSearch` over `/v1/hrms/employees/assignable`, debounced |
| `src/hrms/projects/MyWork.jsx` | **new** — my projects as `Card`s (name, status, progress, end date); my tasks `Table` grouped by status, overdue in red, status `Select` per row |
| `src/hrms/index.js` | `routes` gains `/hrms/projects`, `/hrms/projects/:id`, `/hrms/my-work` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/hrms/projects` | `ProjectList` | `AppShell`; feed carries `hrms.projects` |
| `/hrms/projects/:id` | `ProjectPage` | beneath `/hrms/projects`; edit controls by `useCan('hrms.project.manage')` |
| `/hrms/my-work` | `MyWork` | feed carries `hrms.my_work` |

## 6. Database changes

None. No table, no column, no Flyway script.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/.../project/ProjectNamesTest.java` | names filled from one batch read; an id with no employee gives `null` name, not an error |
| Integration | `hrms/.../project/AssignableEmployeeIT.java` | `manager` gets matches; `employee` gets `403`; another tenant's employee never matches; inactive employees excluded |
| Integration | `ProjectCrudIT.java` (extend) | list and get carry `manager_name` and `team` |
| Unit | `HrmsNavigationTest.java` (extend) | three items; each `targetEndpoint` has a `GET` |
| Unit | `projectService.test.js` | every function hits its path and unwraps `data` |
| Component | `ProjectList.test.jsx` | manager without `hrms.project.read` sends `managed=true`; create hidden without `manage` |
| Component | `ProjectPage.test.jsx` | delete `409` shows the server message; task assignee options are the team only |
| Component | `MyWork.test.jsx` | status change calls `PUT /tasks/{id}/status`; overdue task marked |

## 8. Verification

```bash
cd code/backend && ./mvnw -q -pl hrms -am spotless:check verify -Dtest='ProjectNamesTest,HrmsNavigationTest' -Dit.test='AssignableEmployeeIT,ProjectCrudIT'
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d    # browser: admin@acme.local, then a manager and an employee
```

| Check | Expected |
|---|---|
| backend, lint, tests | `BUILD SUCCESS`; clean |
| manager login | menu shows Projects; list shows only projects they manage; picker finds employees by name |
| employee login | menu shows My work, not Projects; moving own task status works; another's task has no control |
| delete a project with a submitted timesheet | message from the `409`, project stays |
| isolation | admin@globex-full.local sees none of Acme's projects |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The picker exposes the whole directory to managers | medium | names and numbers only, 10 at most, `q` ≥ 2 characters, active employees only |
| Names cost one read per row | low | one batch read per response; the unit test |

## 10. Rollback

Revert the branch. The added fields are additive; no data changes.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | none |
| `Money` / `BigDecimal` | `budget` shown as sent; nothing summed |
| Index | none new |
| Expand / contract | fields added only |
| No module references another | `hrms` reads `core` only; `src/hrms` imports `@shared/*` and `@shell/screens` only |

## 12. Gap inventory

None open. DEBT-007 and DEBT-008 were fixed by `W-41`.

## 13. Decisions — founder, 2026-10-03

| # | Question | Answer |
|---|---|---|
| 1 | One screen per legacy role? | **No.** One set of screens; controls follow `useCan` |
| 2 | How does a manager pick people? | **A narrow `hrms` search** under `hrms.project.manage`, not `core.employee.read` for managers |
| 3 | Names: join in the API or look up in the screen? | **In the API**, batched. The screen looking up every row is `W-47.2`'s F-2 |
