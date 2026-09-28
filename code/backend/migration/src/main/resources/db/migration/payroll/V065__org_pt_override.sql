-- Migration: V065__org_pt_override.sql
-- Description: W-31.2 payroll.org_pt_override — tenant professional tax override header with RLS isolation

CREATE TABLE payroll.org_pt_override (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    state_code VARCHAR(10) NOT NULL REFERENCES reference.pt_state(state_code),
    registration_number VARCHAR(32),
    effective_from DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_org_pt_override_tenant_state UNIQUE (tenant_id, state_code)
);

ALTER TABLE payroll.org_pt_override ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.org_pt_override
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
