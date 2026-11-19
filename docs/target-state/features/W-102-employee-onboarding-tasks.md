# Feature: Employee Onboarding Tasks & New-Joiner Checklist

| Field | Value |
|---|---|
| **Feature ID** | `W-102` · `CORE-ONBOARDING` |
| **Promoted to** | `docs/target-state/features/W-102-employee-onboarding-tasks.md` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/core`, `code/backend/migration`, `code/backend/app` (one test), `code/frontend` (`src/core/onboarding`, `src/core/employee`, `src/shell/portal`) |
| **Related gaps** | founder 2026-11-13 planned product roadmap ("Priority 2 — competing with Keka: `W-102` Employee onboarding tasks") |
| **Status** | **Spec corrected 2026-11-19 after review. Code on `feature/krushna-specs` does not yet meet it.** See §11 for the gaps. |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | nothing |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `core` (plus one test in `app`, one test in `migration`) | 1 | Pass |
| Flyway migration | `V192__employee_onboarding_tasks.sql` (`core`), already written. Any further migration takes the next free Flyway version at build time. | 1 | Pass |
| Externally testable behaviour | A new joiner gets a checklist; the employee works through it; HR checks and closes it | 1 | Pass |
| Frontend area | Three areas: `core/onboarding` (settings screen, service), `core/employee` (Onboarding tab), `shell/portal` (My Onboarding panel) | 1 | **Over (3 areas).** Accepted: each is a thin screen on the same endpoints. |

---

## 1. Problem

| What is missing today | Evidence |
|---|---|
| Creating an employee starts no onboarding process. Before `W-102`, `create()` only saved the employee and its employment record. | `code/backend/core/src/main/java/com/infinevo/core/employee/EmployeeServiceImpl.java:146-178` (`create()`, on `feature/krushna-specs`) |
| HR cannot see which joining documents are outstanding; the Documents tab only lists what was uploaded. | `code/frontend/src/core/employee/tabs/DocumentsTab.jsx` |
| The frozen system had only a five-flag company setup checklist and nothing per employee. | `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/leaveAndAttedance/onboarding/OnboardingStatus.java:21-36` |

This ticket delivers checklist templates per tenant, automatic assignment when an employee is created, self-service completion by the employee, and checking and tracking by HR.

---

## 2. Scope

### In scope

| Item | What it means |
|---|---|
| Templates (`core.onboarding_task_template`) | Per tenant: title, description, task type, document label, assigned role, mandatory flag, due days after joining, sort order, active flag. |
| Seeded templates | Five per tenant, exactly as `V192` seeds them — see the table below. |
| Task instances (`core.employee_onboarding_task`) | Created from the active templates when an employee is created. `due_date = dateOfJoining + due_days_after_joining` of each template. |
| Status rules | Enforced on the server — see "Status rules". |
| Document tasks | The employee uploads a real file; the task links to it by `document_id`. |
| Employee portal panel | "Onboarding" panel on `/me`, shown only when the employee has at least one onboarding task. |
| HR tab | "Onboarding" tab on the employee page: progress, check submitted documents, complete, waive, reopen, assign the default checklist. |
| Template settings screen | Settings › Onboarding templates: list, add, edit, deactivate, reorder. **Must be built.** |
| Permissions | Three actions — see "Permissions". |

**Seeded templates** (`V192__employee_onboarding_tasks.sql:144-148`)

| # | Title | Type | Assigned role | Mandatory | Due days |
|---|---|---|---|---|---|
| 1 | Submit Identity & Address Proof | `DOCUMENT_UPLOAD` | `EMPLOYEE` | yes | 7 |
| 2 | Previous Employment Relieving & Experience Letter | `DOCUMENT_UPLOAD` | `EMPLOYEE` | no | 14 |
| 3 | Acknowledge Employee Handbook & Code of Conduct | `POLICY_ACKNOWLEDGEMENT` | `EMPLOYEE` | yes | 3 |
| 4 | Verify Emergency Contacts & Personal Details | `PROFILE_UPDATE` | `EMPLOYEE` | yes | 5 |
| 5 | Manager Orientation & IT Setup | `MANUAL_TASK` | `EMPLOYEE` | no | 7 |

**Status rules** — the server refuses every move not listed here with `400`.

| Who | Task | From | To | Condition |
|---|---|---|---|---|
| The employee | Not a document task, `assigned_role = EMPLOYEE` | `PENDING` | `COMPLETED` | — |
| The employee | `DOCUMENT_UPLOAD`, `assigned_role = EMPLOYEE` | `PENDING` | `SUBMITTED` | `documentId` is required and must be a document of that employee in that tenant. |
| The employee | Any task with `assigned_role` HR, MANAGER or IT | — | — | Read-only. Any change is `403`. |
| HR (`core.onboarding.manage`) | `DOCUMENT_UPLOAD` | `SUBMITTED` | `COMPLETED` | This is the HR check of the uploaded file. |
| HR | `DOCUMENT_UPLOAD` | `SUBMITTED` | `PENDING` | Rejects the file; `remarks` required. Clears `document_id`. |
| HR | Not a document task | `PENDING` | `COMPLETED` | — |
| HR | Any | `PENDING`, `SUBMITTED` | `WAIVED` | Only HR may waive. |
| HR | Any | `COMPLETED`, `WAIVED` | `PENDING` | Reopen. Clears `completed_at` and `completed_by`. |
| The employee's manager | `assigned_role = MANAGER`, not a document task | `PENDING` | `COMPLETED` | See "Assigned role". |

`completed_at` and `completed_by` are set on every move to `COMPLETED` or `WAIVED`, and cleared on every move away from them.

**Assigned role** — who works a task.

| `assigned_role` | Who may complete it | What the employee sees |
|---|---|---|
| `EMPLOYEE` | The employee, per the status rules. HR may also complete or waive. | An action button. |
| `HR` | HR only. | Read-only row. |
| `IT` | HR only. There is no IT role in the platform. | Read-only row. |
| `MANAGER` | HR, or the employee's current primary manager. The manager is resolved with `ReportingLineRepository.findCurrentOpenLine(tenantId, employeeId, ReportingLineKind.PRIMARY)` (`code/backend/core/src/main/java/com/infinevo/core/org/ReportingLineRepository.java:43`). | Read-only row. |

`assigned_role` accepts only `EMPLOYEE`, `HR`, `MANAGER`, `IT`. It becomes an enum `OnboardingAssignedRole`; any other value is `400`.

**Portal panel visibility**

| Employee | Sees the "Onboarding" panel? |
|---|---|
| Has one or more onboarding tasks (any status) | Yes |
| Has none — every employee who existed before `W-102`, unless HR presses "Assign default checklist" | No. `OnboardingPanelProvider.panel()` returns `null` and `PortalPanelService` skips it (`code/backend/core/src/main/java/com/infinevo/core/portal/PortalPanelService.java:72-74`). |

Existing employees get nothing: no tasks are back-filled and no empty tab appears.

**Permissions** — what the migration must grant.

| Action | Roles |
|---|---|
| `core.onboarding.manage` | `tenant-admin`, `hr` |
| `core.onboarding.read_own`, `core.onboarding.update_own` | `employee`, `manager`, `hr`, `tenant-admin`, `payroll-officer`, `finance` — every role a person with an employee record can hold |

`V192:109-111` also grants all three to `platform-admin`. **Remove those three rows** in a follow-up migration (next free Flyway version at build time): the platform admin has no employee record and reaches a tenant by impersonation.

### Out of scope

- Template sharing across tenants, beyond the seeded defaults.
- E-signature services. Policy acknowledgement is an in-platform, time-stamped confirmation.
- Reminder emails for overdue tasks.
- Back-filling tasks for employees who existed before this ticket.

---

## 3. Flow

```
[HR creates employee] --> EmployeeServiceImpl.create() --> OnboardingService.assignDefaultTasks()
                                                              --> core.employee_onboarding_task (one row per active template)

[Employee, /me › Onboarding] --> GET /api/v1/me/onboarding
   non-document task --> PATCH …/onboarding/{taskId} {status: COMPLETED}
   document task     --> POST /api/v1/documents (file) --> PATCH … {status: SUBMITTED, documentId}

[HR, /employees/:id › Onboarding] --> GET /api/v1/employees/{id}/onboarding
   opens the submitted file --> PATCH … {status: COMPLETED}   (or PENDING with remarks, or WAIVED)

[HR, Settings › Onboarding templates] --> /api/v1/onboarding/templates
```

---

## 4. Backend changes (`code/backend/core`)

Package `com.infinevo.core.onboarding` unless shown.

| Layer | File | Change |
|---|---|---|
| Controller | `OnboardingTemplateController.java` | Exists. Add the error handlers below. |
| Controller | `EmployeeOnboardingController.java` | Exists. Add the error handlers below. Let the employee's primary manager through for `MANAGER` tasks. |
| Service | `OnboardingService.java` | Exists. |
| ServiceImpl | `OnboardingServiceImpl.java` | `updateTaskStatus` enforces the status rules and the document ownership check. `getEmployeeProgress` returns `404` for an unknown employee. Template create and update validate input. |
| Entity | `OnboardingTaskTemplate.java`, `EmployeeOnboardingTask.java` | Exist. `assignedRole` typed as the new enum. |
| Repository | `OnboardingTaskTemplateRepository.java`, `EmployeeOnboardingTaskRepository.java` | Exist. |
| DTO | `CreateTemplateRequest`, `UpdateTemplateRequest`, `UpdateTaskStatusRequest`, `OnboardingTaskTemplateResponse`, `EmployeeOnboardingTaskResponse`, `OnboardingProgressResponse` | Exist. `assignedRole` becomes the enum. |
| Enumeration | `OnboardingTaskType`, `OnboardingTaskStatus` | Exist. |
| Enumeration | `OnboardingAssignedRole.java` | **New.** `EMPLOYEE`, `HR`, `MANAGER`, `IT`. |
| Portal | `com.infinevo.core.portal.OnboardingPanelProvider.java` | Return `null` when the employee has no onboarding task. Give it its own display order (payslips already uses 4 — `code/backend/payroll/src/main/java/com/infinevo/payroll/portal/PayslipPanelProvider.java:30`). |
| Hook | `com.infinevo.core.employee.EmployeeServiceImpl.java` | Exists at `:173-175`. Make `OnboardingService` a required constructor argument; remove the optional field and the null check (`:89-91`, `:141-143`). |
| Navigation | `com.infinevo.core.navigation.NavigationCatalogue.java` | Add `core.settings.onboarding` → `/settings/onboarding` under `core.settings`, behind `core.onboarding.manage`. |

**Error handling** — error handlers are per controller in this codebase (`MyDocumentController.java:49`, `EmployeeController.java:134-150`), so both onboarding controllers need their own. No case below may return `500`.

| Case | Status |
|---|---|
| Unknown template id, task id or employee id | `404` |
| `GET /api/v1/me/onboarding` when the user has no linked employee | `404` |
| Task does not belong to the employee in the path | `404` |
| A status move not in the status rules; missing `documentId` on submit; `documentId` of another employee; missing `remarks` on reject | `400` |
| Blank title, negative `dueDaysAfterJoining`, unknown `taskType` or `assignedRole` | `400` |
| Employee changes another employee's task, or a task not assigned to `EMPLOYEE` | `403` |

**Validation**

| Field | Rule |
|---|---|
| `title` | Required, not blank, at most 200 characters. |
| `dueDaysAfterJoining` | 0 or more. |
| `assignedRole` | One of the enum values; default `EMPLOYEE`. |
| `documentLabel` | Required when `taskType = DOCUMENT_UPLOAD`. |
| `documentId` (task PATCH) | Looked up with `DocumentRepository.findByIdAndTenantIdAndDeletedFalse`; its `employeeId` must equal the task's employee. |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/onboarding/templates` | — | `List<OnboardingTaskTemplateResponse>` | `core.onboarding.manage` |
| `POST` | `/api/v1/onboarding/templates` | `CreateTemplateRequest` | `201` `OnboardingTaskTemplateResponse` | `core.onboarding.manage` |
| `PUT` | `/api/v1/onboarding/templates/{id}` | `UpdateTemplateRequest` | `OnboardingTaskTemplateResponse` | `core.onboarding.manage` |
| `DELETE` | `/api/v1/onboarding/templates/{id}` | — | `204`. Deactivates the template (`is_active = false`); tasks already created keep their copy. | `core.onboarding.manage` |
| `GET` | `/api/v1/employees/{employeeId}/onboarding` | — | `OnboardingProgressResponse` | `core.onboarding.manage`, or `core.onboarding.read_own` for self, or the employee's primary manager |
| `POST` | `/api/v1/employees/{employeeId}/onboarding/assign` | — | `OnboardingProgressResponse`. Does nothing if the employee already has tasks. | `core.onboarding.manage` |
| `PATCH` | `/api/v1/employees/{employeeId}/onboarding/{taskId}` | `UpdateTaskStatusRequest` (`status`, `documentId`, `remarks`) | `EmployeeOnboardingTaskResponse` | `core.onboarding.manage`, or `core.onboarding.update_own` for self, or the primary manager for a `MANAGER` task — each within the status rules |
| `GET` | `/api/v1/me/onboarding` | — | `OnboardingProgressResponse` | `core.onboarding.read_own` |

The file itself is uploaded with the existing `POST /api/v1/documents` (`kind = EMPLOYEE_DOCUMENT`, `code/backend/core/src/main/java/com/infinevo/core/document/DocumentController.java:87-95`). The employee already holds an action that endpoint accepts (`core.leave.apply`); add `core.onboarding.update_own` to its `anyOf` list so the upload does not depend on a leave permission.

Progress: `progressPercentage = round(100 × (COMPLETED + WAIVED) / total)`. With no tasks the response has `totalTasks: 0` and `progressPercentage: 0`.

---

## 5. Frontend changes (`code/frontend`)

| File | Change |
|---|---|
| `src/core/onboarding/onboardingService.js` | Exists. |
| `src/core/onboarding/OnboardingTemplateSettings.jsx` | **New — not built yet.** Table of templates with add, edit, deactivate, and move up/down for sort order. Validation errors from the server shown on the field. |
| `src/core/index.js` | Add the route `/settings/onboarding`, mounted when the navigation feed carries `core.settings.onboarding` (same pattern as `/settings/company`, `:173-174`). |
| `src/core/employee/tabs/OnboardingTab.jsx` | Exists. Add "View document" on a submitted task, "Reject" with remarks, and show actions only where the status rules allow them. |
| `src/core/employee/employeeTabs.jsx`, `src/core/employee/EmployeePage.jsx` | Exist: the "Onboarding" tab entry (`employeeTabs.jsx:69-72`) and its permission filter (`EmployeePage.jsx:52-54`, `:232`). |
| `src/shell/portal/panels/MyOnboarding.jsx` | Exists. Replace the placeholder with a real file picker: upload to `/v1/documents`, then PATCH the task with `SUBMITTED` and the returned `documentId`. Tasks not assigned to `EMPLOYEE` are read-only. |
| `src/shell/portal/PortalLayout.jsx` | Exists: maps the `onboarding` panel code to `MyOnboarding` (`:40`, `:51`). The panel appears because the backend feed carries it, not because of this file. |

Every screen shows a loading state, an empty state, and an error state with a retry — never an empty table after a failed load.

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/settings/onboarding` | `OnboardingTemplateSettings` | App shell; navigation entry `core.settings.onboarding`; `core.onboarding.manage` |
| `/me` › panel `onboarding` | `MyOnboarding` | Portal layout; server panel feed; `core.onboarding.read_own` |
| `/employees/:id` › tab `onboarding` | `OnboardingTab` | Employee page; `core.onboarding.manage` or own record |

---

## 6. Database changes

> Flyway only. The migration is already written; the table below describes it as it is.

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V192__employee_onboarding_tasks.sql` | `core.onboarding_task_template`, `core.employee_onboarding_task` | yes — `tenant_id` and RLS policy `tenant_isolation` on both | Forward-only. Both tables are new, so they can be dropped by a later migration. |
| Follow-up, next free Flyway version at build time | `core.role_action` | yes | Removes the three `platform-admin` grants; adds the `assigned_role` CHECK below. |

**`core.onboarding_task_template`** (`V192:4-24`)

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK, `gen_random_uuid()` | |
| `tenant_id` | `UUID NOT NULL` → `core.tenant(tenant_id)` | |
| `title` | `VARCHAR(200) NOT NULL` | |
| `description` | `TEXT` | |
| `task_type` | `VARCHAR(50) NOT NULL` | CHECK: `DOCUMENT_UPLOAD`, `PROFILE_UPDATE`, `POLICY_ACKNOWLEDGEMENT`, `MANUAL_TASK` |
| `document_label` | `VARCHAR(100)` | |
| `assigned_role` | `VARCHAR(50) NOT NULL DEFAULT 'EMPLOYEE'` | No CHECK today — the follow-up migration adds one for the four values. |
| `is_mandatory` | `BOOLEAN NOT NULL DEFAULT true` | |
| `due_days_after_joining` | `INT NOT NULL DEFAULT 7` | CHECK `>= 0` |
| `sort_order` | `INT NOT NULL DEFAULT 0` | |
| `is_active` | `BOOLEAN NOT NULL DEFAULT true` | |
| `created_at`, `created_by`, `updated_at`, `updated_by` | audit | |

**`core.employee_onboarding_task`** (`V192:41-68`)

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` PK | |
| `tenant_id` | `UUID NOT NULL` → `core.tenant(tenant_id)` | |
| `employee_id` | `UUID NOT NULL` → `core.employee(id)` `ON DELETE CASCADE` | |
| `template_id` | `UUID` → `core.onboarding_task_template(id)` `ON DELETE SET NULL` | |
| `title`, `description`, `task_type`, `document_label`, `assigned_role`, `is_mandatory` | copied from the template | The task keeps its own copy, so a later template edit does not change it. |
| `due_date` | `DATE` | |
| `status` | `VARCHAR(30) NOT NULL DEFAULT 'PENDING'` | CHECK: `PENDING`, `SUBMITTED`, `COMPLETED`, `WAIVED` |
| `completed_at`, `completed_by` | `TIMESTAMPTZ`, `UUID` | |
| `document_id` | `UUID` → `core.document(id)` `ON DELETE SET NULL` | |
| `remarks` | `TEXT` | |
| audit columns | as above | |

The task table has **no** `is_active` column.

Also in `V192`: indexes `(tenant_id, is_active, sort_order)`, `(tenant_id, employee_id, status)`, `(tenant_id, due_date, status)`; table grants to `app_user`; three rows in `reference.action`; `core.grant_onboarding_actions(uuid)` for grants and seeded templates; the trigger `tenant_seed_system_roles_onboarding` for tenants created later; a back-fill for existing tenants.

- [x] `tenant_id` present on every new table
- [x] Index on `tenant_id` plus lookup columns
- [x] RLS enabled with a fail-safe policy on both tables
- [x] Money columns: none
- [x] Expand / contract sequencing — no destructive step

---

## 7. Tests

Every file in this table must exist before the ticket is done.

| Type | File | Covers | Exists? |
|---|---|---|---|
| Unit | `core/src/test/java/com/infinevo/core/onboarding/OnboardingServiceImplTest.java` | Assignment, due dates from each template's own days, progress, **every row of the status rules and every refused move**, document ownership, reopen clearing `completed_at`/`completed_by`, template validation | yes — extend |
| Unit | `core/src/test/java/com/infinevo/core/onboarding/OnboardingGuardTest.java` | An employee cannot read or change another employee's tasks (`403`); cannot waive; cannot complete a document task; cannot touch an HR, MANAGER or IT task; the primary manager can complete a `MANAGER` task; `404`/`400` cases from §4 | **no — write it** |
| Integration | `core/src/test/java/com/infinevo/core/onboarding/OnboardingRlsIT.java` | Tenant isolation of both tables under `app_user` | **no — write it** |
| Unit | `core/src/test/java/com/infinevo/core/employee/EmployeeServiceImplTest.java` | `create()` calls `assignDefaultTasks` once with the saved employee | **no test for the hook — add it** |
| Unit | `core/src/test/java/com/infinevo/core/portal/OnboardingPanelProviderTest.java` | Returns `null` with no tasks, a descriptor with one | **no — write it** |
| Integration | `app/src/test/java/com/infinevo/app/portal/PortalPanelDiscoveryIT.java` | Provider count and codes include `onboarding` | yes |
| Integration | `migration/src/test/java/com/infinevo/migration/RoleCatalogueIT.java` | Action count includes the three onboarding actions | yes |
| Component | `src/core/employee/tabs/OnboardingTab.test.jsx` | Progress bar, view document, complete, reject, waive, reopen | yes — extend |
| Component | `src/shell/portal/panels/MyOnboarding.test.jsx` | File upload then submit with `documentId`; read-only rows; acknowledgement | yes — extend |
| Component | `src/core/onboarding/OnboardingTemplateSettings.test.jsx` | List, add, edit, deactivate, reorder, server validation error | **no — write it** |

Tests must not be loosened to pass. `src/mobile/approvals/ApprovalRequestCard.test.jsx:37` was changed in the `W-102` commit and has nothing to do with this ticket — revert it.

---

## 8. Verification

```bash
# from code/backend
./mvnw -pl core test -Dtest='OnboardingServiceImplTest,OnboardingGuardTest,OnboardingPanelProviderTest,EmployeeServiceImplTest'
./mvnw -pl core,app,migration verify -Dit.test='OnboardingRlsIT,PortalPanelDiscoveryIT,RoleCatalogueIT'

# from code/frontend
npm test -- src/core/employee/tabs/OnboardingTab.test.jsx src/shell/portal/panels/MyOnboarding.test.jsx src/core/onboarding/OnboardingTemplateSettings.test.jsx
npm run lint
```

| Check | Expected | Result |
|---|---|---|
| Create an employee in a tenant with the five seeded templates | Five tasks; due dates are date of joining + 7, 14, 3, 5 and 7 days | not run |
| Mark 3 of 4 tasks completed | `completedTasks: 3`, `totalTasks: 4`, `progressPercentage: 75` | not run |
| Employee PATCHes own policy task to `COMPLETED` | `200` | not run |
| Employee PATCHes own document task to `COMPLETED` | `400` | not run |
| Employee PATCHes own task to `WAIVED` | `400` | not run |
| Employee submits a document task without `documentId`, or with another employee's document | `400` | not run |
| Employee uploads a file and submits | Task is `SUBMITTED` with `document_id` set; HR can open the file from the tab | not run |
| HR completes the submitted task, then reopens it | `COMPLETED`, then `PENDING` with `completed_at` and `completed_by` empty | not run |
| Employee calls `GET /api/v1/employees/{otherId}/onboarding` | `403` | not run |
| HR calls it with an unknown employee id | `404` | not run |
| User with no linked employee calls `GET /api/v1/me/onboarding` | `404` | not run |
| `POST` a template with `dueDaysAfterJoining: -1` or `assignedRole: "CEO"` | `400` | not run |
| Existing employee with no tasks opens `/me` | No "Onboarding" panel | not run |
| HR opens Settings › Onboarding templates and adds a template | It is saved and the next new employee gets a task from it | not run |
| Query both tables as `app_user` bound to tenant A | Only tenant A's rows | not run |
| CI on the branch head | Green | not run |

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Employee created before templates exist has no tasks | Medium | "Assign default checklist" button on the HR tab (`POST …/onboarding/assign`). |
| A linked document is deleted | Low | `document_id` is `ON DELETE SET NULL`; a `SUBMITTED` task with no document is shown to HR as "file removed" and can be sent back to `PENDING`. |
| Assignment fails and blocks employee creation | Low | The hook runs in the same transaction as `create()`; a failure rolls the employee back and surfaces as an error. This is intended: no silent skip. |
| Overdue tasks are read as blocking payroll | Low | Tasks are advisory; nothing in the pay run reads them. |

---

## 10. Rollback

1. **Hide the feature without a deploy:** remove the three `core.onboarding.*` rows from `core.role_action` for the affected tenant (roles screen, or SQL). The portal panel, the employee tab and the settings entry all disappear, because each is gated on one of those actions. Grants live in the database, not in Keycloak.
2. **Stop new checklists:** deactivate the tenant's templates (`is_active = false` on `core.onboarding_task_template`). Existing tasks stay; the task table has no active flag.
3. **Remove the data:** only by a new forward migration that drops the two tables. Do this only if no tenant has entered onboarding data.

---

## 11. Code gaps against this spec (2026-11-19)

Found by reading `feature/krushna-specs` at `f5d3e3de`. Paths are under `code/`.

| Gap | Where |
|---|---|
| No status rules: an employee can set `COMPLETED` or `WAIVED` on any own task, and the portal sends `COMPLETED` directly | `backend/core/src/main/java/com/infinevo/core/onboarding/OnboardingServiceImpl.java:202-208`; `frontend/src/shell/portal/panels/MyOnboarding.jsx:62` |
| Document upload is a placeholder: the task goes to `SUBMITTED` with no file and no `documentId` | `frontend/src/shell/portal/panels/MyOnboarding.jsx:74-80`, `:229-231` |
| `documentId` is stored without an ownership check | `OnboardingServiceImpl.java:209-211` |
| Template settings screen is not built | `frontend/src/core/onboarding/` holds only `onboardingService.js`; no route in `frontend/src/core/index.js`; no entry in `NavigationCatalogue.java` |
| No error handlers on either onboarding controller: `NoSuchElementException`, `IllegalArgumentException` and `EmployeeService.NotFoundException` return `500` | `EmployeeOnboardingController.java:68`; `OnboardingServiceImpl.java:44`, `:73`, `:112`, `:176`, `:196`, `:199` |
| Unknown employee id returns `200` with 0 tasks and 100% | `OnboardingServiceImpl.java:154-169` (`:165`) |
| Template input is not validated: negative due days reach the database CHECK; `assignedRole` is free text | `OnboardingServiceImpl.java:48`, `:57`, `:87-95`; `CreateTemplateRequest.java` |
| `assigned_role` has no effect: the employee can complete every task | `OnboardingServiceImpl.java:186-219` |
| The portal panel is shown to every employee, with or without tasks | `backend/core/src/main/java/com/infinevo/core/portal/OnboardingPanelProvider.java` (`panel()` never returns `null`) |
| Reopen leaves `completed_at` and `completed_by` filled | `frontend/src/core/employee/tabs/OnboardingTab.jsx:171`; `OnboardingServiceImpl.java:202-208` |
| The create hook is an optional field, skipped silently when the bean is missing, and has no test | `backend/core/src/main/java/com/infinevo/core/employee/EmployeeServiceImpl.java:89-91`, `:173-175` |
| `OnboardingGuardTest` and `OnboardingRlsIT` do not exist | `backend/core/src/test/java/com/infinevo/core/onboarding/` holds only `OnboardingServiceImplTest.java` |
| Onboarding and payslips share display order 4 | `OnboardingPanelProvider.java`; `backend/payroll/src/main/java/com/infinevo/payroll/portal/PayslipPanelProvider.java:30` |
| Extra grants to `platform-admin` | `backend/migration/src/main/resources/db/migration/core/V192__employee_onboarding_tasks.sql:109-111` |
| Unrelated mobile test loosened in the `W-102` commit | `frontend/src/mobile/approvals/ApprovalRequestCard.test.jsx:37` |
