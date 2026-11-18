# Feature: Employee Onboarding Tasks & New-Joiner Checklist

| Field | Value |
|---|---|
| **Feature ID** | `W-102` · `CORE-ONBOARDING` |
| **Promoted to** | `docs/target-state/features/W-102-employee-onboarding-tasks.md` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/core`, `code/backend/migration`, `code/frontend` (`src/core/onboarding`, `src/core/employee/tabs`, `src/shell/portal`) |
| **Related gaps** | founder 2026-11-13 planned product roadmap ("Priority 2 — competing with Keka: `W-102` Employee onboarding tasks") |
| **Status** | **Complete (`feature/krushna-specs`, 100% tests/lint green)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | nothing |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `core` | 1 | **Pass (1 module)** |
| Flyway migration | `V192__employee_onboarding_tasks.sql` (`core`) | 1 | **Pass (1 migration)** |
| Externally testable behaviour | HR manages template checklist and assigns to new joiners; employee views and completes tasks / document uploads on self-service `/me/onboarding`; HR monitors onboarding completion status | 1 | **Pass (1 behaviour)** |
| Frontend area | `core/onboarding` (with tab in `EmployeePage.jsx` and `/me` portal panel) | 1 | **Pass (1 area)** |

Within size cap across all four axes. No split required.

---

## 1. Problem

Today, when an employee record is created (`code/backend/core/src/main/java/com/infinevo/core/employee/EmployeeServiceImpl.java:80-140`), there is no structured onboarding process for either HR or the new joiner:
1. **No new-joiner task checklist**: New employees have no guided list of tasks to complete upon joining (e.g. uploading mandatory Aadhaar/PAN proofs, submitting previous employment relieving letters, signing code-of-conduct policies, or attending manager orientation).
2. **Scattered document collection**: While `W-73.5` introduced the Documents tab (`code/frontend/src/core/employee/tabs/DocumentsTab.jsx:20-85`), HR must manually inspect missing files with no visibility into which documents are outstanding or overdue.
3. **Legacy system gap**: The legacy system had only a hardcoded 5-flag organization setup checklist (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/leaveAndAttedance/onboarding/OnboardingStatus.java:21-36`) and had zero employee-level onboarding capabilities.
4. **Competitor parity**: Leading HR platforms (Keka, Darwinbox) offer automated onboarding workflows with checklist templates, task assignments, due dates, and progress tracking for new hires.

This ticket delivers tenant-configurable onboarding task templates, automatic checklist assignment on employee creation, employee self-service task completion, and HR onboarding progress tracking.

---

## 2. Scope

### In scope
- **Task template configuration (`core.onboarding_task_template`)**:
  - Tenant-level default checklist items: Title, description, task type (`DOCUMENT_UPLOAD`, `PROFILE_UPDATE`, `POLICY_ACKNOWLEDGEMENT`, `MANUAL_TASK`), document label, assigned role (`EMPLOYEE`, `HR`, `MANAGER`, `IT`), mandatory flag, and relative due date (`due_days_after_joining`).
  - Seeded India defaults for new tenants: ID & Address Proof submission, Bank details verification, PF/Gratuity nomination form, Emergency contact submission, and Policy acknowledgement.
- **Employee onboarding task instances (`core.employee_onboarding_task`)**:
  - Automatically created on employee creation when templates exist, setting `due_date = dateOfJoining + due_days_after_joining`.
  - Status lifecycle: `PENDING` $\rightarrow$ `SUBMITTED` $\rightarrow$ `COMPLETED` (or `WAIVED`).
  - Direct integration with document store: `DOCUMENT_UPLOAD` tasks link to `core.document.id`.
- **Employee Self-Service (`/me/onboarding`)**:
  - New tab/panel in Employee Portal (`code/frontend/src/shell/portal/panels/MyOnboarding.jsx`) showing progress meter, upcoming tasks, overdue alerts, and direct completion actions.
- **HR Onboarding Management**:
  - "Onboarding" tab in `EmployeePage.jsx` (`code/frontend/src/core/employee/tabs/OnboardingTab.jsx`) showing task progress breakdown ("5 of 7 completed — 71%"), task verification, and manual waive/complete actions.
  - Settings screen (`code/frontend/src/core/onboarding/OnboardingTemplateSettings.jsx`) to customize tenant templates.
- **Authorization**:
  - `core.onboarding.manage` granted to `tenant-admin` and `hr`.
  - `core.onboarding.read_own` and `core.onboarding.update_own` granted to `employee`, `hr`, `manager`, `payroll-officer`, `finance`.

### Out of scope
- Cross-tenant or global template sharing outside seeded defaults.
- Electronic digital signature integrations (DocuSign / Adobe Sign) — policy acknowledgement uses in-platform timestamped confirmation.
- Automated email reminders on task overdue (covered by future notification scheduler ticket).

---

## 3. Flow

```
[HR Adds Employee / Date of Joining Set]
                  │
                  ▼
[EmployeeServiceImpl.create()]
                  │
                  ▼
[Auto-assign Active Onboarding Templates] ──► [core.employee_onboarding_task Created]
                                                           │
               ┌───────────────────────────────────────────┴───────────────────────────────────────────┐
               ▼                                                                                       ▼
[Employee Portal: /me/onboarding]                                                       [HR View: /employees/:id/onboarding]
               │                                                                                       │
               ├─► [Task 1: Upload Aadhaar/PAN] ──► Links to Document Store ──► SUBMITTED             ├─► Monitors Progress (e.g. 71% Done)
               ├─► [Task 2: Policy Handbook] ──► 1-Click Acknowledge ──► COMPLETED                     ├─► Verifies Uploaded Documents
               └─► [Task 3: Fill Emergency Contact] ──► Deep link to /me/profile                       └─► Marks HR-Verified / Waived
```

---

## 4. Architecture & Changes

### 4.1 Backend changes (`code/backend/core`)

| Layer | File | Change |
|---|---|---|
| Entity | `com.infinevo.core.onboarding.OnboardingTaskTemplate.java` | **New**. Entity mapped to `core.onboarding_task_template`. |
| Entity | `com.infinevo.core.onboarding.EmployeeOnboardingTask.java` | **New**. Entity mapped to `core.employee_onboarding_task`. |
| Enum | `com.infinevo.core.onboarding.OnboardingTaskType.java` | **New**. `DOCUMENT_UPLOAD`, `PROFILE_UPDATE`, `POLICY_ACKNOWLEDGEMENT`, `MANUAL_TASK`. |
| Enum | `com.infinevo.core.onboarding.OnboardingTaskStatus.java` | **New**. `PENDING`, `SUBMITTED`, `COMPLETED`, `WAIVED`. |
| Repository | `com.infinevo.core.onboarding.OnboardingTaskTemplateRepository.java` | **New**. Query templates by tenant ordered by `sort_order`. |
| Repository | `com.infinevo.core.onboarding.EmployeeOnboardingTaskRepository.java` | **New**. Query tasks by `employee_id` and status. |
| Service | `com.infinevo.core.onboarding.OnboardingService.java` | **New**. Service interface for templates, task assignment, progress calculation, and completion. |
| ServiceImpl | `com.infinevo.core.onboarding.OnboardingServiceImpl.java` | **New**. Service implementation handling assignment hooks, document linkage, and status transitions. |
| Controller | `com.infinevo.core.onboarding.OnboardingTemplateController.java` | **New**. CRUD endpoints for onboarding templates (`/api/v1/onboarding/templates`). |
| Controller | `com.infinevo.core.onboarding.EmployeeOnboardingController.java` | **New**. Endpoints for employee task tracking (`/api/v1/employees/{id}/onboarding`, `/api/v1/me/onboarding`). |
| Hook | `com.infinevo.core.employee.EmployeeServiceImpl.java` | After employee persist, invoke `onboardingService.assignDefaultTasks(employee)`. |

### 4.2 API contract

| Method | Path | Request Body | Response Body | Auth / Permission |
|---|---|---|---|---|
| `GET` | `/api/v1/onboarding/templates` | — | `List<OnboardingTaskTemplateResponse>` | `core.onboarding.manage` |
| `POST` | `/api/v1/onboarding/templates` | `CreateTemplateRequest` | `OnboardingTaskTemplateResponse` | `core.onboarding.manage` |
| `PUT` | `/api/v1/onboarding/templates/{id}` | `UpdateTemplateRequest` | `OnboardingTaskTemplateResponse` | `core.onboarding.manage` |
| `DELETE` | `/api/v1/onboarding/templates/{id}` | — | `204 No Content` | `core.onboarding.manage` |
| `GET` | `/api/v1/employees/{employeeId}/onboarding` | — | `OnboardingProgressResponse` (tasks + completion %) | `core.onboarding.manage` OR `core.onboarding.read_own` (self) |
| `POST` | `/api/v1/employees/{employeeId}/onboarding/assign` | — | `OnboardingProgressResponse` | `core.onboarding.manage` |
| `PATCH` | `/api/v1/employees/{employeeId}/onboarding/{taskId}` | `UpdateTaskStatusRequest` | `EmployeeOnboardingTaskResponse` | `core.onboarding.manage` OR `core.onboarding.update_own` (self) |
| `GET` | `/api/v1/me/onboarding` | — | `OnboardingProgressResponse` | `core.onboarding.read_own` |

### 4.3 Frontend changes (`code/frontend`)

| File | Change |
|---|---|
| `src/core/onboarding/onboardingService.js` | **New**. API client for templates and employee onboarding tasks. |
| `src/core/onboarding/OnboardingTemplateSettings.jsx` | **New**. Settings screen under Settings › Onboarding Templates to add/reorder checklist templates. |
| `src/core/employee/tabs/OnboardingTab.jsx` | **New**. Onboarding progress tab on `/employees/:id` with completion bar, checklist rows, verify and waive actions. |
| `src/core/employee/EmployeePage.jsx` | Add "Onboarding" tab to employee profile navigation (`file:src/core/employee/EmployeePage.jsx:32-75`). |
| `src/shell/portal/panels/MyOnboarding.jsx` | **New**. Employee self-service panel on `/me` with action cards, document upload triggers, and completion checkmarks. |
| `src/shell/portal/PortalLayout.jsx` | Register "Onboarding" tab in portal items feed (`file:src/shell/portal/PortalLayout.jsx:45-85`). |

---

## 5. Database Changes

> **Flyway script:** `code/backend/migration/src/main/resources/db/migration/core/V192__employee_onboarding_tasks.sql`

```sql
-- Schema: core
-- Version: V192
-- Feature: W-102 Employee onboarding tasks and checklist templates

CREATE TABLE core.onboarding_task_template (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    description             TEXT,
    task_type               VARCHAR(50) NOT NULL,
    document_label          VARCHAR(100),
    assigned_role           VARCHAR(50) NOT NULL DEFAULT 'EMPLOYEE',
    is_mandatory            BOOLEAN NOT NULL DEFAULT true,
    due_days_after_joining  INTEGER NOT NULL DEFAULT 7,
    sort_order              INTEGER NOT NULL DEFAULT 0,
    is_active               BOOLEAN NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_onboarding_template_tenant_sort 
    ON core.onboarding_task_template (tenant_id, is_active, sort_order);

ALTER TABLE core.onboarding_task_template ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.onboarding_task_template
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

CREATE TABLE core.employee_onboarding_task (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    employee_id             BIGINT NOT NULL REFERENCES core.employee(id) ON DELETE CASCADE,
    template_id             BIGINT REFERENCES core.onboarding_task_template(id) ON DELETE SET NULL,
    title                   VARCHAR(200) NOT NULL,
    description             TEXT,
    task_type               VARCHAR(50) NOT NULL,
    document_label          VARCHAR(100),
    assigned_role           VARCHAR(50) NOT NULL DEFAULT 'EMPLOYEE',
    is_mandatory            BOOLEAN NOT NULL DEFAULT true,
    due_date                DATE,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    completed_at            TIMESTAMPTZ,
    completed_by            UUID,
    document_id             BIGINT REFERENCES core.document(id) ON DELETE SET NULL,
    remarks                 TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_employee_onboarding_task_tenant_emp 
    ON core.employee_onboarding_task (tenant_id, employee_id, status);

CREATE INDEX idx_employee_onboarding_task_tenant_due 
    ON core.employee_onboarding_task (tenant_id, due_date, status);

ALTER TABLE core.employee_onboarding_task ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.employee_onboarding_task
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Seed role action grants
CREATE OR REPLACE FUNCTION core.grant_onboarding_actions(p_tenant_id UUID)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF p_tenant_id = '00000000-0000-0000-0000-000000000001'::uuid THEN RETURN; END IF;
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('tenant-admin', 'core.onboarding.manage'),
        ('hr', 'core.onboarding.manage'),
        ('employee', 'core.onboarding.read_own'),
        ('employee', 'core.onboarding.update_own'),
        ('manager', 'core.onboarding.read_own'),
        ('manager', 'core.onboarding.update_own'),
        ('payroll-officer', 'core.onboarding.read_own'),
        ('payroll-officer', 'core.onboarding.update_own'),
        ('finance', 'core.onboarding.read_own'),
        ('finance', 'core.onboarding.update_own')
    ) AS grants (role_code, action_code)
    JOIN core.role r ON r.tenant_id = p_tenant_id AND r.code = grants.role_code AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

SELECT core.grant_onboarding_actions(t.tenant_id) FROM core.tenant t;
```

- [x] Standing rule: `tenant_id` present as leading index column on both tables.
- [x] Standing rule: RLS enabled and policy `tenant_isolation` using fail-safe `CASE` format.
- [x] Standing rule: Multi-tenant safety — no module crosses boundaries.
- [x] Standing rule: Money/BigDecimal: N/A (creates no financial columns).

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `com.infinevo.core.onboarding.OnboardingServiceImplTest.java` | Template creation, default task assignment upon employee creation, due date calculation relative to `dateOfJoining`, progress percentage calculation. |
| Unit | `com.infinevo.core.onboarding.OnboardingGuardTest.java` | Verifies employee can only read/update their own onboarding tasks; verifies HR can waive and approve tasks for all tenant employees. |
| Integration | `com.infinevo.core.onboarding.OnboardingRlsIT.java` | Confirms tenant isolation for templates and employee tasks under `app_user`. |
| Component | `src/core/employee/tabs/OnboardingTab.test.jsx` | Renders onboarding progress bar; tests HR verification button and status badge display. |
| Component | `src/shell/portal/panels/MyOnboarding.test.jsx` | Renders employee checklist; tests document upload trigger and acknowledgement click. |

---

## 7. Verification

Verification commands and criteria:

```bash
# 1. Run core backend unit & integration tests
./mvnw -pl core test -Dtest='OnboardingServiceImplTest,OnboardingGuardTest,OnboardingRlsIT'

# 2. Run frontend tests
cd code/frontend
npm test -- src/core/employee/tabs/OnboardingTab.test.jsx src/shell/portal/panels/MyOnboarding.test.jsx

# 3. Frontend lint check
npm run lint
```

### Verification matrix

| Check | Command / Action | Expected Result |
|---|---|---|
| Auto-assignment on employee create | Create employee via `POST /api/v1/employees` | Default onboarding checklist tasks created for employee with `due_date = dateOfJoining + 7`. |
| Progress Calculation | Mark 3 of 4 tasks completed | Response returns `completedTasks: 3`, `totalTasks: 4`, `progressPercentage: 75`. |
| Ownership Isolation | Call `GET /api/v1/employees/{otherId}/onboarding` as regular employee | Returns `403 Forbidden` (`core.onboarding.manage` required). |
| Portal Self-Service | Employee logs into `/me/onboarding` | Displays employee's tasks, allows document upload and task completion. |
| RLS Isolation | Query `core.employee_onboarding_task` across different tenant IDs | App connection sees only rows matching bound session tenant. |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Employee created before templates configured has no tasks | Medium | Provide explicit "Assign Default Onboarding Checklist" button on HR `OnboardingTab` to generate tasks retroactively. |
| Document deleted from documents tab breaks task link | Low | Use `ON DELETE SET NULL` on `document_id` foreign key and retain status history in task remarks. |
| Overdue onboarding tasks block payroll | Low | Onboarding tasks are non-blocking advisory checklists; they track compliance without halting payroll computation. |

---

## 9. Rollback

1. Flyway script is forward-only; if rolled back before production data entry, tasks and templates can be soft-deleted by setting `is_active = false`.
2. Frontend `OnboardingTab` and `MyOnboarding` tabs can be toggled via permission removal in Keycloak role actions without requiring a backend rollback.

