-- Migration: V143__timesheet_task_entry.sql
-- Description: W-42.1 hrms.timesheet_task_entry - the task lines of a project line, with RLS isolation
--
-- Ported from the frozen HRMS entity/timesheet/TaskEntry.java:11-33. Not carried: task_name. A task is on every
-- line (task_id NOT NULL): legacy always sends one (TaskEntry.java:20-21).

CREATE TABLE hrms.timesheet_task_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    project_entry_id UUID NOT NULL REFERENCES hrms.timesheet_project_entry(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES hrms.task(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- A task appears once per project line.
CREATE UNIQUE INDEX uk_tte_tenant_entry_task
    ON hrms.timesheet_task_entry (tenant_id, project_entry_id, task_id);

-- "Is this task on a live timesheet?" (W-41 delete refusal)
CREATE INDEX idx_tte_tenant_task
    ON hrms.timesheet_task_entry (tenant_id, task_id);

-- Row-Level Security
ALTER TABLE hrms.timesheet_task_entry ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.timesheet_task_entry
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
