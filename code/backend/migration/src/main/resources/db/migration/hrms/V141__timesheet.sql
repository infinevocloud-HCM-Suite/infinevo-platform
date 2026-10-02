-- Migration: V141__timesheet.sql
-- Description: W-42.1 hrms.timesheet - one weekly timesheet header per employee per week, with RLS isolation
--
-- Ported from the frozen HRMS entity/timesheet/Timesheets.java:16-55. Not carried: employee_name (names are
-- never copied beside ids, W-41 decision 6) and the display id "TS-" + (100 + id). The same week cannot be saved
-- twice (legacy TimesheetServiceImpl.java:71-125 allowed it): one non-cancelled timesheet per employee per week.

CREATE TABLE hrms.timesheet (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    -- The week runs Monday to Sunday, as legacy (TimesheetsController.java:138); not per tenant.
    week_start_date DATE NOT NULL CHECK (EXTRACT(ISODOW FROM week_start_date) = 1),
    week_end_date DATE NOT NULL CHECK (week_end_date = week_start_date + 6),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    submitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- One timesheet per employee per week. A cancelled one (legacy rows W-67 may bring across) does not block a new week.
CREATE UNIQUE INDEX uk_timesheet_tenant_employee_week
    ON hrms.timesheet (tenant_id, employee_id, week_start_date) WHERE status <> 'CANCELLED';

-- Review lists by status (W-42.4)
CREATE INDEX idx_timesheet_tenant_status
    ON hrms.timesheet (tenant_id, status);

-- Row-Level Security
ALTER TABLE hrms.timesheet ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.timesheet
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
