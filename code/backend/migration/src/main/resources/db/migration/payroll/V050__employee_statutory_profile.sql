-- Migration: V050__employee_statutory_profile.sql
-- Description: W-26.2 payroll.employee_statutory_profile — statutory eligibility profile per employee with RLS isolation

CREATE TABLE payroll.employee_statutory_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    is_eligible_for_pf BOOLEAN NOT NULL DEFAULT false,
    is_eligible_for_pt BOOLEAN NOT NULL DEFAULT false,
    is_eligible_for_lwf BOOLEAN NOT NULL DEFAULT false,
    is_eligible_for_esi BOOLEAN NOT NULL DEFAULT false,
    is_eligible_for_eps BOOLEAN NOT NULL DEFAULT false,
    contributes_eps_on_higher_wages BOOLEAN NOT NULL DEFAULT false,
    is_director BOOLEAN NOT NULL DEFAULT false,
    pf_account_number VARCHAR(32),
    uan VARCHAR(16),
    esi_number VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Unique statutory profile per employee in a tenant
CREATE UNIQUE INDEX uk_employee_statutory_profile_tenant_employee
    ON payroll.employee_statutory_profile (tenant_id, employee_id);

-- Row-Level Security
ALTER TABLE payroll.employee_statutory_profile ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_statutory_profile
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
