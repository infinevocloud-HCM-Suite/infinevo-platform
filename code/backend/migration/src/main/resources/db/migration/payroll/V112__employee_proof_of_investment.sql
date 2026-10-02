-- Migration: V112__employee_proof_of_investment.sql
-- Description: W-34.1 payroll.employee_proof_of_investment — one proof per investment declaration

CREATE TABLE payroll.employee_proof_of_investment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    declaration_id UUID NOT NULL REFERENCES payroll.employee_investment_declaration(id),
    financial_year VARCHAR(9) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    approval_instance_id UUID REFERENCES core.approval_instance(id),
    reviewer_note VARCHAR(1000),
    submitted_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_employee_proof_of_investment_tenant_declaration
    ON payroll.employee_proof_of_investment (tenant_id, declaration_id);
CREATE INDEX idx_employee_proof_of_investment_tenant_fy_status
    ON payroll.employee_proof_of_investment (tenant_id, financial_year, status);

ALTER TABLE payroll.employee_proof_of_investment ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_proof_of_investment
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
