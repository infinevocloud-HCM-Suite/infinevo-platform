# Feature: Employee Resignation, Exit Clearances & Handover to Payroll

| Field            | Value                                                                            |
| ---------------- | -------------------------------------------------------------------------------- |
| **Feature ID**   | `W-103` · `HRMS-EXIT`                                                            |
| **Owner**        | Mohit (`dev-mohit`)                                                              |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration`, `code/frontend` (`src/hrms/exit`) |
| **Related gaps** | none in `GAP_INVENTORY.md`                                                       |
| **Status**       | Ready for founder review                                                         |
| **Blocked by**   | `W-90` (Full and Final Settlement)                                               |

## 1. Size cap

| Axis                          | This spec                                                                                                                              | Limit |
| ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | ----- |
| Backend module                | `hrms`                                                                                                                                 | 1     |
| Flyway migration              | `hrms/V199__exit_process.sql`                                                                                                          | 1     |
| Externally testable behaviour | Employee submits resignation, manager approves exit date, departments complete clearance checklist, and status triggers F&F in Payroll | 1     |
| Frontend area                 | `hrms/exit`                                                                                                                            | 1     |

---

## 2. Problem & Context

Currently, employee status transitions are binary (`ACTIVE`, `SUSPENDED`, `TERMINATED`). There is no operational workflow to:

1. Allow an employee to submit a formal resignation online.
2. Track served notice period days vs expected last working day (LWD).
3. Assign clearance checklists across departments:
   - **IT:** Laptop/hardware return, access revocation, email archiving.
   - **Finance:** Travel advances cleared, corporate credit card cancelled.
   - **Admin:** ID card, access badge, parking tag collected.
   - **HR:** Exit interview conducted, knowledge transfer sign-off.
4. Hand off completed clearances to Payroll to trigger Full & Final settlement (`W-90`).

---

## 3. Scope

**In scope:**

- PostgreSQL tables `hrms.exit_request` and `hrms.exit_clearance_item` with RLS.
- Employee self-service resignation submission (`POST /api/v1/me/exit`).
- Manager approval / revision of proposed Last Working Day.
- Multi-department clearance checklist with assignees and completion timestamps.
- Automated notification trigger upon all clearances completed to trigger F&F initiation.
- HRMS screens: Employee resignation form under `/me/exit` and HR clearance tracker under `/hrms/exits`.

**Out of scope:**

- Pay computation, gratuity, and leave encashment (handled in `W-90` and `W-91`).

---

## 4. Flow

```
Employee --> POST /api/v1/me/exit (resignation date, requested LWD, reason)
         --> Manager reviews & approves LWD
         --> Generates Clearance Tasks: IT, Finance, Admin, HR
Department Leads --> PUT /api/v1/hrms/exits/{id}/clearance/{dept} (Sign-off)
All Cleared --> Automated event: EXITS_CLEARED
            --> Payroll notified to run F&F settlement (W-90)
```

---

## 5. Database changes

| Migration                     | Change                                                             | Tenant-aware? | Reversible? |
| ----------------------------- | ------------------------------------------------------------------ | ------------- | ----------- |
| `hrms/V199__exit_process.sql` | Create `hrms.exit_request` and `hrms.exit_clearance_item` with RLS | yes           | yes         |

### Table: `hrms.exit_request`

```sql
CREATE TABLE hrms.exit_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    resignation_date DATE NOT NULL,
    requested_lwd DATE NOT NULL,
    approved_lwd DATE NULL,
    exit_type VARCHAR(30) NOT NULL DEFAULT 'RESIGNATION',
    reason_category VARCHAR(50) NOT NULL,
    reason_notes TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED', 'MANAGER_APPROVED', 'CLEARANCE_IN_PROGRESS', 'READY_FOR_FNF', 'SETTLED', 'REJECTED', 'WITHDRAWN')),
    approved_by VARCHAR(100) NULL,
    approved_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_exit_request_active_employee
    ON hrms.exit_request (tenant_id, employee_id)
    WHERE status NOT IN ('SETTLED', 'REJECTED', 'WITHDRAWN');

ALTER TABLE hrms.exit_request ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.exit_request
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
```

### Table: `hrms.exit_clearance_item`

```sql
CREATE TABLE hrms.exit_clearance_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    exit_request_id UUID NOT NULL REFERENCES hrms.exit_request(id) ON DELETE CASCADE,
    department VARCHAR(50) NOT NULL CHECK (department IN ('IT', 'FINANCE', 'ADMIN', 'HR')),
    item_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CLEARED', 'WAIVED')),
    cleared_by VARCHAR(100) NULL,
    cleared_at TIMESTAMPTZ NULL,
    remarks VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_exit_clearance_tenant_request
    ON hrms.exit_clearance_item (tenant_id, exit_request_id);

ALTER TABLE hrms.exit_clearance_item ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.exit_clearance_item
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
```

---

## 5a. Payroll Hand-off Contract

When all clearance items for an exit request are resolved (`CLEARED` or `WAIVED`), the exit moves to `READY_FOR_FNF`. The hand-off to Payroll is executed via synchronous or asynchronous integration:

1. **REST Endpoint**: `POST /api/v1/hrms/exits/{id}/hand-off-to-payroll`
   - Permissions: `hrms.exit.manage`
   - Pre-condition: `hrms.exit_request.status = 'READY_FOR_FNF'` and no clearance item remains `PENDING`.
2. **Payload to Payroll Settlement Engine (`W-90.3`)**:
   ```json
   {
     "employeeId": "uuid",
     "resignationDate": "2026-10-01",
     "exitDate": "2026-10-31",
     "exitReason": "RESIGNATION",
     "workingDaysInExitMonth": 22
   }
   ```
3. **Response & Lifecycle Transition**:
   - Payroll creates settlement draft in `payroll.fnf_settlement` (`W-90.3`).
   - HRMS updates `hrms.exit_request.status = 'SETTLED'`.

---

## 6. Backend changes

| Layer      | File                                                   | Change                                                                     |
| ---------- | ------------------------------------------------------ | -------------------------------------------------------------------------- |
| Entity     | `hrms/exit/ExitRequest.java`, `ExitClearanceItem.java` | Entities mapping exit request and clearance checklist                      |
| Repository | `hrms/exit/ExitRequestRepository.java`                 | Spring Data JPA repo with tenant queries                                   |
| Service    | `hrms/exit/ExitService.java`, `ExitServiceImpl.java`   | Handles submission, LWD approval, clearance toggling, and completion check |
| Controller | `hrms/exit/ExitController.java`                        | REST endpoints for employee and HR                                         |

---

## 7. Frontend changes

| File                                | Change                                                       |
| ----------------------------------- | ------------------------------------------------------------ |
| `src/hrms/exit/ResignationPage.jsx` | Self-service resignation submission under `/me/exit`         |
| `src/hrms/exit/ExitDashboard.jsx`   | HR management dashboard of active exits under `/hrms/exits`  |
| `src/hrms/exit/ClearanceDrawer.jsx` | Drawer to tick off department assets and clearance checklist |

---

## 8. Verification

```bash
mvnw clean test -pl migration,hrms -am -Dtest="*Exit*"
cd code/frontend && npx vitest run src/hrms/exit
```

- Integration test `ExitProcessLifecycleIT`: tests submitting resignation, manager approval, completing all 4 departmental clearances, and verifying status moves to `READY_FOR_FNF`.
