-- Migration: V086__project.sql
-- Description: W-41 hrms.project — project master records with RLS isolation

CREATE TABLE hrms.project (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    description TEXT,
    start_date DATE,
    end_date DATE,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    progress SMALLINT NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    budget NUMERIC(19,4),
    manager_employee_id UUID REFERENCES core.employee(id),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Unique project name per tenant among active (non-deleted) projects (case-insensitive)
CREATE UNIQUE INDEX uk_project_tenant_name
    ON hrms.project (tenant_id, LOWER(name)) WHERE NOT is_deleted;

-- Status and soft-deletion index for tenant listings
CREATE INDEX idx_project_tenant_deleted_status
    ON hrms.project (tenant_id, is_deleted, status);

-- Project manager lookup index
CREATE INDEX idx_project_tenant_manager
    ON hrms.project (tenant_id, manager_employee_id);

-- Row-Level Security
ALTER TABLE hrms.project ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hrms.project
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
