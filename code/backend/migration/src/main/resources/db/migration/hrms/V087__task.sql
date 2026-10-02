-- Migration: V087__task.sql
-- Description: W-41 hrms.task — project tasks with RLS isolation

CREATE TABLE hrms.task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    project_id UUID NOT NULL REFERENCES hrms.project(id),
    title VARCHAR(128) NOT NULL,
    description TEXT,
    assignee_employee_id UUID REFERENCES core.employee(id),
    due_date DATE,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    estimated_hours INTEGER,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Task lookup by project within tenant
CREATE INDEX idx_task_tenant_project
    ON hrms.task (tenant_id, project_id, is_deleted);

-- Task lookup by assignee for self-service (/tasks/mine)
CREATE INDEX idx_task_tenant_assignee
    ON hrms.task (tenant_id, assignee_employee_id, is_deleted);

-- Row-Level Security
ALTER TABLE hrms.task ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.task
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
