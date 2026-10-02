-- Migration: V088__assignment.sql
-- Description: W-41 hrms.assignment — project team member assignments with RLS isolation

CREATE TABLE hrms.assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    project_id UUID NOT NULL REFERENCES hrms.project(id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    assigned_on DATE NOT NULL DEFAULT CURRENT_DATE,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Unique assignment per employee per project in a tenant (duplicate assignment guard)
CREATE UNIQUE INDEX uk_assignment_tenant_project_employee
    ON hrms.assignment (tenant_id, project_id, employee_id) WHERE NOT is_deleted;

-- Employee lookup index (/projects/mine)
CREATE INDEX idx_assignment_tenant_employee
    ON hrms.assignment (tenant_id, employee_id, is_deleted);

-- Row-Level Security
ALTER TABLE hrms.assignment ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.assignment
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
