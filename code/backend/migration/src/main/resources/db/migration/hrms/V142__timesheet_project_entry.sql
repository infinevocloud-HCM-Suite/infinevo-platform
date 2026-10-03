-- Migration: V142__timesheet_project_entry.sql
-- Description: W-42.1 hrms.timesheet_project_entry - the project lines of a weekly timesheet, with RLS isolation
--
-- Ported from the frozen HRMS entity/timesheet/ProjectEntry.java:11-40. Not carried: project_name. status and
-- rejection_reason are carried now so that W-42.3 (one approval per project line) adds no column for them.

CREATE TABLE hrms.timesheet_project_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    timesheet_id UUID NOT NULL REFERENCES hrms.timesheet(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES hrms.project(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- A project appears once per timesheet.
CREATE UNIQUE INDEX uk_tpe_tenant_timesheet_project
    ON hrms.timesheet_project_entry (tenant_id, timesheet_id, project_id);

-- "Is this project on a live timesheet?" (W-41 delete refusal) and the project manager's list (W-42.4).
CREATE INDEX idx_tpe_tenant_project_status
    ON hrms.timesheet_project_entry (tenant_id, project_id, status);

-- Row-Level Security
ALTER TABLE hrms.timesheet_project_entry ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.timesheet_project_entry
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
