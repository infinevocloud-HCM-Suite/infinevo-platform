-- Migration: V068__ctc_epf_component.sql
-- Description: W-31.3 payroll.ctc_epf_component — employee provident fund lines on a salary version with RLS isolation

CREATE TABLE payroll.ctc_epf_component (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    ctc_structure_id UUID NOT NULL REFERENCES payroll.ctc_structure(id) ON DELETE CASCADE,
    component_code VARCHAR(32) NOT NULL CHECK (component_code IN ('EPF_EMPLOYEE', 'EPF_EMPLOYER', 'EPS_EMPLOYER', 'EDLI', 'EPF_ADMIN')),
    share VARCHAR(16) NOT NULL CHECK (share IN ('EMPLOYEE', 'EMPLOYER')),
    wage_base NUMERIC(19,4) NOT NULL,
    rate NUMERIC(7,4) NOT NULL,
    monthly_amount NUMERIC(19,4) NOT NULL,
    annual_amount NUMERIC(19,4) NOT NULL,
    is_included_in_ctc BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_ctc_epf_component_tenant_structure_code UNIQUE (tenant_id, ctc_structure_id, component_code)
);

CREATE INDEX idx_ctc_epf_component_tenant_structure ON payroll.ctc_epf_component (tenant_id, ctc_structure_id);

ALTER TABLE payroll.ctc_epf_component ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.ctc_epf_component
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
