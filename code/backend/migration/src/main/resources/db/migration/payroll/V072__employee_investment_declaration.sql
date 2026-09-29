-- Migration: V072__employee_investment_declaration.sql
-- Description: W-32.1 payroll.employee_investment_declaration — employee investment declaration root per financial year

CREATE TABLE payroll.employee_investment_declaration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    financial_year VARCHAR(9) NOT NULL,
    tax_regime VARCHAR(3) NOT NULL CHECK (tax_regime IN ('OLD', 'NEW')),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SUBMITTED')),
    is_staying_in_rented_house BOOLEAN NOT NULL DEFAULT false,
    is_repaying_self_occupied_loan BOOLEAN NOT NULL DEFAULT false,
    has_let_out_property BOOLEAN NOT NULL DEFAULT false,
    is_locked BOOLEAN NOT NULL DEFAULT false,
    submitted_at TIMESTAMPTZ,
    locked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_employee_investment_declaration_tenant_employee_fy ON payroll.employee_investment_declaration (tenant_id, employee_id, financial_year);
CREATE INDEX idx_employee_investment_declaration_tenant_fy_status ON payroll.employee_investment_declaration (tenant_id, financial_year, status);

ALTER TABLE payroll.employee_investment_declaration ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_investment_declaration
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
