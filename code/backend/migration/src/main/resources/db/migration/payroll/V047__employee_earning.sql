-- Migration: V047__employee_earning.sql
-- Description: W-26.2 payroll.employee_earning — employee earning component values in a CTC version with RLS isolation

CREATE TABLE payroll.employee_earning (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    ctc_structure_id UUID NOT NULL REFERENCES payroll.ctc_structure(id) ON DELETE CASCADE,
    component_id UUID NOT NULL REFERENCES payroll.earning(id),
    calculation_type VARCHAR(16) NOT NULL DEFAULT 'FLAT',
    value NUMERIC(19,4) NOT NULL,
    percentage_of VARCHAR(16),
    monthly_amount NUMERIC(19,4) NOT NULL,
    annual_amount NUMERIC(19,4) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    earning_frequency VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Structure and component lookup indices
CREATE INDEX idx_employee_earning_tenant_structure
    ON payroll.employee_earning (tenant_id, ctc_structure_id);

CREATE INDEX idx_employee_earning_tenant_component
    ON payroll.employee_earning (tenant_id, component_id);

-- Row-Level Security
ALTER TABLE payroll.employee_earning ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_earning
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
