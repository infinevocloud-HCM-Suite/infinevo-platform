-- Migration: V144__timesheet_day_entry.sql
-- Description: W-42.1 hrms.timesheet_day_entry - the hours worked on one day on one task, with RLS isolation
--
-- Ported from the frozen HRMS entity/timesheet/DayEntry.java:9-37. Not carried: day_name, which is derived from
-- the date (:22-24). Hours were a Float with no limit (:27-28); here they are numeric(4,2) between 0 (exclusive)
-- and 24. The 24 hours a day across all tasks is a rule of the service: one row cannot see its siblings.

CREATE TABLE hrms.timesheet_day_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    task_entry_id UUID NOT NULL REFERENCES hrms.timesheet_task_entry(id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    hours NUMERIC(4, 2) NOT NULL CHECK (hours > 0 AND hours <= 24),
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- A date appears once per task line. (tenant_id leads, and the unique index serves the entry lookup too.)
CREATE UNIQUE INDEX uk_tde_tenant_entry_date
    ON hrms.timesheet_day_entry (tenant_id, task_entry_id, work_date);

-- Row-Level Security
ALTER TABLE hrms.timesheet_day_entry ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.timesheet_day_entry
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
