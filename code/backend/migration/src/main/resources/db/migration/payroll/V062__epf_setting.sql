-- Migration: V062__epf_setting.sql
-- Description: W-31.1 payroll.epf_setting — provident fund settings per tenant with RLS isolation

CREATE TABLE payroll.epf_setting (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    is_enabled BOOLEAN NOT NULL DEFAULT false,
    registration_number VARCHAR(32),
    registration_date DATE,
    deduction_cycle VARCHAR(16) NOT NULL DEFAULT 'MONTHLY',
    employee_rate NUMERIC(7,4) NOT NULL DEFAULT 12.0000,
    employer_rate NUMERIC(7,4) NOT NULL DEFAULT 12.0000,
    eps_rate NUMERIC(7,4) NOT NULL DEFAULT 8.3300,
    edli_rate NUMERIC(7,4) NOT NULL DEFAULT 0.5000,
    admin_charge_rate NUMERIC(7,4) NOT NULL DEFAULT 0.5000,
    wage_ceiling NUMERIC(19,4) NOT NULL DEFAULT 15000.0000,
    restrict_employee_to_ceiling BOOLEAN NOT NULL DEFAULT false,
    restrict_employer_to_ceiling BOOLEAN NOT NULL DEFAULT false,
    prorate_restricted_wage BOOLEAN NOT NULL DEFAULT false,
    consider_earned_wage BOOLEAN NOT NULL DEFAULT true,
    eps_senior_age SMALLINT NOT NULL DEFAULT 58,
    include_employer_in_ctc BOOLEAN NOT NULL DEFAULT false,
    include_edli_admin_in_ctc BOOLEAN NOT NULL DEFAULT false,
    include_employer_in_structure BOOLEAN NOT NULL DEFAULT false,
    include_edli_admin_in_structure BOOLEAN NOT NULL DEFAULT false,
    abry_scheme BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_epf_setting_tenant UNIQUE (tenant_id)
);

ALTER TABLE payroll.epf_setting ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.epf_setting
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
