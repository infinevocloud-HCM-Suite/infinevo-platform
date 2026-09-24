-- Migration: V018__employee_employment.sql
-- Description: W-13.2 core.employee_employment — the working arrangement of an employee, one optional row per employee, with row-level security isolation

CREATE TABLE core.employee_employment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    pay_grade VARCHAR(64),
    workstation_id VARCHAR(64),
    time_zone VARCHAR(64),
    shift_start_time TIME,
    shift_end_time TIME,
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- HRMS legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Work.java:17-36
-- holds ten columns. Only five of them are employment detail; the other four already have a home
-- and are NOT duplicated here (spec §6, and this is the decision most likely to be queried):
--   department (Work.java:18)        -> core.employee.department_id, W-14.1 V014__employee_org_columns.sql
--   jobTitle (Work.java:21)          -> core.employee.designation_id, same script
--   doj (Work.java:26)               -> core.employee.date_of_joining, W-13.1 V010__employee.sql
--   terminationDate (Work.java:28)   -> core.employee.termination_date, same script
-- A second copy of a joining date is how two answers to "when did this person start" get written,
-- and a payroll run cannot tell which is right.

-- note is Report.java:27. Report.java's three approver columns — firstLevelApprover,
-- secondLevelApprover, thirdLevelApprover (Report.java:22-24) — are NOT here: they belong to
-- W-14's reporting_line, by the split decision (spec §2 Out of scope, §9).

-- shift_start_time and shift_end_time are real TIME columns. Work.java:35-36 types both as String,
-- so nothing can order or compare them and "09:00" and "9:00 AM" are both valid rows. This is the
-- same correction W-13.1 made for date_of_joining, and it is made for the same reason.

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
CREATE UNIQUE INDEX idx_employee_employment_tenant_employee ON core.employee_employment (tenant_id, employee_id);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.employee_employment ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.employee_employment
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
