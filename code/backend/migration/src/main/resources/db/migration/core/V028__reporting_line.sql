-- Migration: V028__reporting_line.sql
-- Description: W-14.2 core.reporting_line — employee reporting hierarchy and approver assignments, with row-level security

CREATE TABLE core.reporting_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    manager_id UUID NOT NULL REFERENCES core.employee(id),
    kind VARCHAR(24) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT reporting_line_no_self_manage CHECK (employee_id <> manager_id)
);

CREATE INDEX idx_reporting_line_lookup ON core.reporting_line (tenant_id, employee_id, kind, effective_from DESC);
CREATE INDEX idx_reporting_line_reverse ON core.reporting_line (tenant_id, manager_id);

ALTER TABLE core.reporting_line ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.reporting_line
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
