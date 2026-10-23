# Feature: Work From Home (WFH) Module

| Field | Value |
|---|---|
| **Feature ID** | `W-74` · ticket #74 · `CORE-WFH` |
| **Promoted to** | `docs/target-state/features/W-74-work-from-home.md` on branch `W-74-work-from-home` |
| **Owner** | krushna |
| **Apps touched** | `code/backend/core`, `code/backend/migration`, `code/frontend` |
| **Related gaps** | `W-16` (Leave Engine mirror), `W-25` (Self-Service Portal), `W-39` (Attendance capture integration) |
| **Status** | **Ready** |
| **Approved by** | founder |
| **Approved on** | 2026-10-23 |
| **Blocked by** | `W-15.2` (Approval lifecycle), `W-16.2` (Leave allocation pattern), `W-39.1` (Attendance model) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | **6 scripts, one table/action set each** — aggregate exception | 1 — aggregate module exception granted (mirrors `W-16.1`–`W-16.4b` leave suite into single independent WFH subsystem) |
| Externally testable behaviour | Employee applies for WFH from `/me/wfh`; on manager approval, attendance auto-creates `PRESENT (WORK_FROM_HOME)` and quota deducts with zero Loss of Pay (LOP) | 1 |
| Frontend area | `core/portal` (`/me/wfh`), `core/wfh` (management), shell navigation | 1 |

---

## 1. Problem

Currently, remote work / work from home has no independent model in Infinevo HCM. Employers either attempt to model it as a "Leave Type" (`core.leave_type`) or rely on ad-hoc offline messages.

Treating WFH as a leave type creates architectural and operational failure modes:

| Fact | Evidence |
|---|---|
| In `/me/leave/apply`, leave types represent absence from work (CL, SL, PL, Comp-Off) | `code/frontend/src/core/portal/ApplyLeave.jsx:21-65` · `LeaveTypeController.java:35-82` |
| Leaves with exhausted quotas trigger Loss of Pay (LOP) deductions in payroll | `V133__leave_monthly_lop.sql:4-28` · `LeaveConsumptionServiceImpl.java:82-140` |
| Leaves record attendance as `ON_LEAVE` / `ABSENT`, penalizing attendance statistics | `V030__attendance.sql:4-20` (`attendance_status` enum) |
| Timesheets and daily work logging are disabled or excused during approved leaves | `W-42-1-timesheet-entry.md:32-45` |
| No dedicated tenant policy exists to govern annual remote work quotas (e.g., 12 days/yr) and monthly pacing caps (e.g., max 2 days/month) | `V127__leave_policy.sql:1-45` |

**Conclusion:** Work from home is an active, paid working day carried out remotely, not a time-off absence. It requires a dedicated subsystem that mirrors leave allocation mechanics while guaranteeing 100% payroll preservation and automatic `PRESENT` attendance recording.

---

## 2. Scope

**In scope**

- **Tenant Policy (`core.wfh_policy`):** Annual entitlement days (default 12.00), monthly limit cap (e.g., 2.00 days/mo), half-day allowance, advance notice days, max consecutive days, and manager/HR approval requirements.
- **Quota Allocation (`core.wfh_allocation`):** 100% mirror of leave allocation (`W-16.2`). Yearly quota records per employee with `year_start_date`, `year_end_date`, and non-overlapping `daterange` GIST constraints.
- **Mid-Year Pro-Rata Computation:** Automatic pro-rating of annual quota for employees joining mid-year ($\text{Quota} \times \frac{\text{Remaining Months}}{12}$).
- **Bulk CSV Import (`core.wfh_import_log`):** Sample template download, dry-run validation, and bulk insertion into `core.wfh_allocation`.
- **Request Lifecycle (`core.wfh_request`):** Employee self-service application (`DRAFT`, `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `WITHDRAWN`).
- **Conflict & Mutual Exclusion Guard:** Strict validation preventing overlapping approved WFH requests, as well as mutual exclusion against approved leaves on the same dates.
- **Immutable Consumption Ledger (`core.wfh_consumption`):** Append-only audit table recording every quota deduction upon approval, and balance credit upon request cancellation.
- **Automatic Attendance Integration:** Upon approval, automated creation of a `core.attendance` entry with `attendance_status = 'PRESENT'` and `work_mode = 'WORK_FROM_HOME'`.
- **Zero Loss of Pay (Zero LOP):** WFH days never generate records in `core.leave_monthly_lop` or pay input ledgers.
- **Frontend Navigation & Screens:**
  - Employee Portal tab: `/me/wfh` (Apply WFH modal, 12-day balance card, request history).
  - Main Sidebar group: `Work From Home (WFH)` (`/wfh/policies`, `/wfh/allocations`, `/wfh/requests`, `/wfh/import`).
  - Approval Integration: Decision action buttons in Manager Pending Inbox (`/approvals`).

**Out of scope**

- Geofencing and IP-subnet validation (handled by advanced clock-in rules if configured).
- Timesheet bypass: WFH employees must still complete active timesheets and task assignments (`W-42`).
- Encashment or carry-forward of unused WFH quotas: unused remote days expire at year-end.
- Hardware asset tracking (laptops, monitors) during remote work.

---

## 3. Flow

```
[Employee] ──► /me/wfh ──► POST /api/v1/wfh-requests 
                                   │
                                   ▼
                       [Validate Overlaps & Monthly Cap]
                                   │
                                   ▼
                       [Status: PENDING] ──► [core.wfh_request]
                                   │
                                   ▼
                       [Manager / HR Inbox (/approvals)]
                                   │
                                   ├─────────────────────────────┐
                                   ▼ [REJECT]                    ▼ [APPROVE]
                         [Status: REJECTED]           [Status: APPROVED]
                                                                 │
                                         ┌───────────────────────┴───────────────────────┐
                                         ▼                                               ▼
                           [Deduct Quota in Ledger]                         [Auto-Create Attendance]
                           (core.wfh_consumption)                           (core.attendance: PRESENT,
                                                                             work_mode: WORK_FROM_HOME)
```

---

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Entity | `com.infinevo.core.wfh.WfhPolicy` | JPA entity mapping `core.wfh_policy` |
| Entity | `com.infinevo.core.wfh.WfhAllocation` | JPA entity mapping `core.wfh_allocation` |
| Entity | `com.infinevo.core.wfh.WfhRequest` | JPA entity mapping `core.wfh_request` |
| Entity | `com.infinevo.core.wfh.WfhConsumption` | JPA entity mapping `core.wfh_consumption` (immutable ledger) |
| Entity | `com.infinevo.core.wfh.WfhImportLog` | JPA entity mapping `core.wfh_import_log` |
| Repository | `WfhPolicyRepository`, `WfhAllocationRepository`, `WfhRequestRepository`, `WfhConsumptionRepository` | Spring Data JPA repositories with tenant-scoped lookup |
| Service | `WfhPolicyService`, `WfhPolicyServiceImpl` | CRUD for tenant WFH policy and rules configuration |
| Service | `WfhAllocationService`, `WfhAllocationServiceImpl` | Manual allocation form, mid-year pro-rata formula, CSV parsing |
| Service | `WfhBalanceService`, `WfhBalanceServiceImpl` | Computes entitled, consumed, pending, remaining days and monthly velocity |
| Service | `WfhRequestService`, `WfhRequestServiceImpl` | Application submission, overlap checks (vs WFH & leave), withdrawal |
| Service | `WfhApprovalOutcomeHandler` | Implements approval hook: deducts quota in `wfh_consumption` and writes `core.attendance` |
| Portal Provider | `com.infinevo.core.portal.WfhPanelProvider` | Registers `"wfh"` tab in `/api/v1/me/panels` for `core.wfh.read_own` |
| Navigation | `NavigationCatalogue.java` | Registers `core.wfh` group and child items in `DEFAULT_ITEMS` |
| Controller | `WfhPolicyController` | `/api/v1/wfh-policies` endpoints (`core.wfh.manage`) |
| Controller | `WfhAllocationController` | `/api/v1/wfh-allocations` endpoints (`core.wfh_balance.manage`) |
| Controller | `WfhRequestController` | `/api/v1/wfh-requests` management endpoints (`core.wfh.read`, `core.wfh.manage`) |
| Controller | `MyWfhController` | `/api/v1/me/wfh-requests` self-service endpoints (`core.wfh.apply`, `core.wfh.read_own`) |
| Controller | `WfhImportController` | `/api/v1/wfh-imports` template download and CSV upload (`core.wfh_balance.manage`) |

### API Contract

| Method | Path | Request Body | Response | Required Action |
|---|---|---|---|---|
| `GET` | `/api/v1/wfh-policies` | — | `List<WfhPolicyResponse>` | `core.wfh.read` |
| `POST` | `/api/v1/wfh-policies` | `CreateWfhPolicyRequest` | `WfhPolicyResponse` (201) | `core.wfh.manage` |
| `GET` | `/api/v1/wfh-allocations` | `?employeeId=&year=` | `List<WfhAllocationResponse>` | `core.wfh.read` |
| `POST` | `/api/v1/wfh-allocations` | `CreateWfhAllocationRequest` | `WfhAllocationResponse` (201) | `core.wfh_balance.manage` |
| `POST` | `/api/v1/wfh-imports/template` | — | CSV template attachment | `core.wfh_balance.manage` |
| `POST` | `/api/v1/wfh-imports` | `multipart/form-data (file)` | `WfhImportSummaryResponse` | `core.wfh_balance.manage` |
| `GET` | `/api/v1/wfh-requests` | `?status=&from=&to=&employeeId=` | `Page<WfhRequestResponse>` | `core.wfh.read` |
| `GET` | `/api/v1/me/wfh-requests` | — | `List<WfhRequestResponse>` | `core.wfh.read_own` |
| `GET` | `/api/v1/me/wfh-balance` | `?year=2026` | `WfhBalanceSummaryResponse` | `core.wfh.read_own` |
| `POST` | `/api/v1/me/wfh-requests` | `ApplyWfhRequest` | `WfhRequestResponse` (201) | `core.wfh.apply` |
| `POST` | `/api/v1/me/wfh-requests/{id}/withdraw` | — | `204 No Content` | `core.wfh.apply` |
| `POST` | `/api/v1/wfh-requests/{id}/approve` | `DecisionRequest` | `WfhRequestResponse` | `core.approval.decide` |
| `POST` | `/api/v1/wfh-requests/{id}/reject` | `DecisionRequest` | `WfhRequestResponse` | `core.approval.decide` |

---

## 5. Frontend changes

### 5.1 UI Navigation Tree (Screen Structure)

```text
Infinevo Application
│
├── 👤 Employee Portal (/me - Self Service Tabs)
│   ├── 👤 Profile
│   ├── 🌴 Leave                 <-- (CL, SL, PL leaves only - NO WFH HERE)
│   ├── 🏠 Work From Home (WFH)  <-- [NEW TAB: /me/wfh] (12-day balance & apply screen)
│   ├── 📄 Documents
│   └── 💰 Payslips
│
└── 🛠️ Main Sidebar Menu (HR / Admin / Manager)
    ├── 👥 People
    │   ├── Employees
    │   └── Users & Roles
    │
    ├── 🏢 Organisation
    │   ├── Departments
    │   ├── Designations
    │   └── Holidays
    │
    ├── 🌴 Leave
    │   ├── Leave Types
    │   ├── Leave Allocations
    │   ├── Leave Requests
    │   └── Leave Import
    │
    ├── 🏠 Work From Home (WFH)     <-- [NEW SIDEBAR GROUP]
    │   ├── 📋 WFH Policies         <-- Rules: 12 days/yr, max 2/mo (/wfh/policies)
    │   ├── 🎯 WFH Allocations      <-- Assign quota to employees (/wfh/allocations)
    │   ├── 📑 WFH Requests         <-- Audit all team/tenant requests (/wfh/requests)
    │   └── 📤 WFH Bulk Import      <-- Bulk CSV quota upload (/wfh/import)
    │
    ├── ✅ Approvals
    │   ├── 📥 Pending Inbox        <-- Manager approve/reject buttons
    │   └── Delegations
    │
    └── ⚙️ Settings
```

### 5.2 Files Modified / Created

| File | Change |
|---|---|
| `code/frontend/src/shell/portal/PortalLayout.jsx` | Add `wfh: <HomeOutlined />` icon and register `MyWfh` panel component |
| `code/frontend/src/shell/routes.js` | Mount portal route `{ path: '/me/wfh/apply', element: React.createElement(ApplyWfh) }` |
| `code/frontend/src/core/portal/MyWfh.jsx` (new) | Self-service tab showing annual balance card, remaining days, and request history table |
| `code/frontend/src/core/portal/ApplyWfh.jsx` (new) | Apply modal with date-picker, half-day toggle, agenda/reason text, balance check |
| `code/frontend/src/core/wfh/WfhPolicies.jsx` (new) | Admin screen to configure tenant WFH policy (annual days, monthly limit) |
| `code/frontend/src/core/wfh/WfhAllocations.jsx` (new) | Quota assignment form for single employees with pro-rata calculation preview |
| `code/frontend/src/core/wfh/WfhRequests.jsx` (new) | HR/Manager listing of all tenant WFH requests with filter by status/employee |
| `code/frontend/src/core/wfh/WfhImport.jsx` (new) | Drag-and-drop CSV upload for bulk allocation with validation summary |
| `code/frontend/src/core/wfh/wfhService.js` (new) | Axios client for all `/api/v1/wfh-*` and `/api/v1/me/wfh-*` endpoints |

---

## 6. Database changes

> Flyway only. All tables enforce tenant isolation via PostgreSQL Row Level Security (RLS) matching Infinevo standards (`tenant_isolation`).

| Migration Script | Table / Object | Tenant-Aware | Purpose |
|---|---|---|---|
| `V179__wfh_policy.sql` | `core.wfh_policy` | Yes (RLS) | Tenant remote work rules, annual quota, monthly caps |
| `V180__wfh_allocation.sql` | `core.wfh_allocation` | Yes (RLS) | Yearly quota records with non-overlapping GiST bounds |
| `V181__wfh_request.sql` | `core.wfh_request` | Yes (RLS) | Employee applications with non-overlapping APPROVED constraint |
| `V182__wfh_consumption.sql` | `core.wfh_consumption` | Yes (RLS) | Immutable append-only deduction/reversal ledger |
| `V183__wfh_import_log.sql` | `core.wfh_import_log` | Yes (RLS) | Audit records of bulk CSV quota allocations |
| `V184__wfh_seed_roles.sql` | `core.role_action` seeds | Yes | Seeds action permissions for `hr`, `manager`, `employee` |

### DDL Definitions

#### 1. `core.wfh_policy`
```sql
CREATE TABLE core.wfh_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    annual_days NUMERIC(10,2) NOT NULL DEFAULT 12.00,
    monthly_limit_days NUMERIC(10,2) NULL DEFAULT 2.00,
    allow_half_day BOOLEAN NOT NULL DEFAULT true,
    advance_notice_days INT NOT NULL DEFAULT 1,
    max_consecutive_days INT NULL DEFAULT 3,
    requires_manager_approval BOOLEAN NOT NULL DEFAULT true,
    requires_hr_approval BOOLEAN NOT NULL DEFAULT false,
    effective_from DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_wfh_policy_tenant_code UNIQUE (tenant_id, code)
);

ALTER TABLE core.wfh_policy ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON core.wfh_policy
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON core.wfh_policy TO app_user;
```

#### 2. `core.wfh_allocation`
```sql
CREATE TABLE core.wfh_allocation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES core.employee(id) ON DELETE CASCADE,
    policy_id UUID NOT NULL REFERENCES core.wfh_policy(id) ON DELETE RESTRICT,
    wfh_year VARCHAR(9) NOT NULL,
    year_start_date DATE NOT NULL,
    year_end_date DATE NOT NULL,
    entitlement_days NUMERIC(10,2) NOT NULL DEFAULT 12.00,
    pro_rate_factor NUMERIC(5,4) NOT NULL DEFAULT 1.0000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_wfh_allocation UNIQUE (tenant_id, employee_id, wfh_year)
);

CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE core.wfh_allocation
    ADD CONSTRAINT no_overlapping_wfh_allocation
    EXCLUDE USING gist (
        tenant_id WITH =,
        employee_id WITH =,
        daterange(year_start_date, year_end_date, '[]') WITH &&
    );

ALTER TABLE core.wfh_allocation ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON core.wfh_allocation
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON core.wfh_allocation TO app_user;
```

#### 3. `core.wfh_request`
```sql
CREATE TABLE core.wfh_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES core.employee(id) ON DELETE CASCADE,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    is_half_day BOOLEAN NOT NULL DEFAULT false,
    half_day_period VARCHAR(8) NULL,
    working_days NUMERIC(10,2) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    approval_instance_id UUID NULL REFERENCES core.approval_instance(id) ON DELETE SET NULL,
    on_behalf BOOLEAN NOT NULL DEFAULT false,
    decided_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_wfh_request_dates CHECK (from_date <= to_date),
    CONSTRAINT chk_wfh_request_status CHECK (
        status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'WITHDRAWN')
    )
);

ALTER TABLE core.wfh_request
    ADD CONSTRAINT no_overlapping_approved_wfh
    EXCLUDE USING gist (
        tenant_id WITH =,
        employee_id WITH =,
        daterange(from_date, to_date, '[]') WITH &&
    ) WHERE (status = 'APPROVED');

CREATE INDEX idx_wfh_request_lookup ON core.wfh_request (tenant_id, employee_id, from_date DESC);
CREATE INDEX idx_wfh_request_status ON core.wfh_request (tenant_id, status);

ALTER TABLE core.wfh_request ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON core.wfh_request
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON core.wfh_request TO app_user;
```

#### 4. `core.wfh_consumption` (Immutable Ledger)
```sql
CREATE TABLE core.wfh_consumption (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES core.employee(id) ON DELETE CASCADE,
    allocation_id UUID NOT NULL REFERENCES core.wfh_allocation(id) ON DELETE RESTRICT,
    wfh_request_id UUID NULL REFERENCES core.wfh_request(id) ON DELETE RESTRICT,
    consumed_days NUMERIC(10,2) NOT NULL,
    consumed_on DATE NOT NULL,
    period CHAR(7) NOT NULL,
    reverses_id UUID NULL REFERENCES core.wfh_consumption(id) ON DELETE RESTRICT,
    reason VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_wfh_consumption_period CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$')
);

CREATE INDEX idx_wfh_consumption_period ON core.wfh_consumption (tenant_id, employee_id, period);
CREATE UNIQUE INDEX uk_wfh_consumption_request ON core.wfh_consumption (tenant_id, wfh_request_id) WHERE reverses_id IS NULL;

ALTER TABLE core.wfh_consumption ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON core.wfh_consumption
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

REVOKE UPDATE, DELETE ON core.wfh_consumption FROM app_user;
GRANT SELECT, INSERT ON core.wfh_consumption TO app_user;
```

---

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `WfhAllocationIT` | Pro-rata formula for mid-year joiners; bulk CSV import parsing and dry-run validation; GIST overlapping allocation prevention |
| Integration | `WfhRequestIT` | Submission validation against monthly cap; cross-module mutual exclusion check against approved leaves |
| Integration | `WfhLedgerIT` | Immutable ledger deduction upon approval; credit reversal upon withdrawal; prevention of UPDATE/DELETE on ledger |
| Integration | `WfhAttendanceIT` | Verification that approved WFH automatically writes `core.attendance` as `PRESENT` with `work_mode = 'WORK_FROM_HOME'` |
| Unit | `ApplyWfh.test.jsx` | Date picker selection, balance exhaustion disabling submit, client-side validation |
| Unit | `MyWfh.test.jsx` | Balance summary rendering (entitled, used, remaining) and request history table |
| Unit | `WfhImport.test.jsx` | File picker, invalid CSV format error messaging, dry-run response view |

---

## 8. Verification

```bash
# 1. Run backend unit and integration tests for core WFH
mvn clean test -pl code/backend/core -Dtest=*Wfh*

# 2. Run Flyway migration validation test
mvn test -pl code/backend/migration -Dtest=FlywayMigrationTest

# 3. Run frontend unit tests
cd code/frontend && npm test -- --watchAll=false src/core/wfh src/core/portal/ApplyWfh.test.jsx
```

| Check | Expected |
|---|---|
| Sign in as Employee & open `/me` | `🏠 Work From Home` tab is visible beside Profile, Leave, Documents, Payslips |
| Employee applies for 1 day WFH | Request created in `core.wfh_request` with status `PENDING` |
| Manager opens `/approvals` & approves | Status changes to `APPROVED`; 1 day deducted in `core.wfh_consumption` |
| Inspect `core.attendance` for approved date | Row exists with `attendance_status = 'PRESENT'` and `work_mode = 'WORK_FROM_HOME'` |
| Run monthly payroll payrun | Zero Loss of Pay (LOP) generated for WFH days; 100% full salary paid |
| Try applying for leave on same date | Request rejected by mutual exclusion guard ("WFH already approved for this date") |

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Overlapping requests between Leave and WFH | Medium | Central validation in `WfhRequestService` querying both `core.wfh_request` and `core.leave_request` inside a database transaction |
| Concurrent double-booking of WFH dates | Low | PostgreSQL exclusion constraint `no_overlapping_approved_wfh` using GiST daterange |
| Tampering with consumption audit ledger | Low | PostgreSQL `REVOKE UPDATE, DELETE ON core.wfh_consumption FROM app_user` guarantees an immutable append-only ledger |

---

## 10. Rollback

1. Flyway clean rollback: drop `core.wfh_consumption`, `core.wfh_request`, `core.wfh_allocation`, `core.wfh_policy`, `core.wfh_import_log`.
2. Delete action permissions seeded in `core.role_action` (`core.wfh.*`).
3. Revert navigation feed entries in `NavigationCatalogue.java` and `/me/panels` provider `WfhPanelProvider.java`.
4. Remove frontend `/me/wfh` tab and sidebar group.
