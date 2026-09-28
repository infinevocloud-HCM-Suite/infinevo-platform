-- Migration: V063__esi_setting.sql
-- Description: W-31.1 payroll.esi_setting — state insurance settings per tenant with RLS isolation

CREATE TABLE payroll.esi_setting (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    is_enabled BOOLEAN NOT NULL DEFAULT false,
    registration_number VARCHAR(32),
    registration_date DATE,
    deduction_cycle VARCHAR(16) NOT NULL DEFAULT 'MONTHLY',
    employee_rate NUMERIC(7,4) NOT NULL DEFAULT 0.7500,
    employer_rate NUMERIC(7,4) NOT NULL DEFAULT 3.2500,
    wage_ceiling NUMERIC(19,4) NOT NULL DEFAULT 21000.0000,
    include_employer_in_ctc BOOLEAN NOT NULL DEFAULT false,
    include_in_structure BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_esi_setting_tenant UNIQUE (tenant_id)
);

ALTER TABLE payroll.esi_setting ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.esi_setting
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
