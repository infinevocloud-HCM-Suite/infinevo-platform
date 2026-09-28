-- Migration: V053__employee_fbp_component.sql
-- Description: W-27.2 payroll.employee_fbp_component — employee FBP declaration overlay with RLS isolation

CREATE TABLE payroll.employee_fbp_component (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    ctc_structure_id UUID NOT NULL REFERENCES payroll.ctc_structure(id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    earning_id UUID REFERENCES payroll.earning(id),
    reimbursement_id UUID REFERENCES payroll.reimbursement(id),
    annual_amount NUMERIC(19,4) NOT NULL,
    monthly_amount NUMERIC(19,4) NOT NULL,
    declared_at TIMESTAMPTZ NOT NULL,
    declared_by VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT check_component_choice CHECK ((earning_id IS NULL) <> (reimbursement_id IS NULL)),
    CONSTRAINT check_declared_by CHECK (declared_by IN ('EMPLOYEE', 'OFFICER', 'CARRIED')),
    CONSTRAINT check_annual_positive CHECK (annual_amount >= 0),
    CONSTRAINT check_monthly_positive CHECK (monthly_amount >= 0)
);

-- Partial unique indexes: exactly one declaration per component per salary version
CREATE UNIQUE INDEX uk_employee_fbp_component_tenant_structure_earning
    ON payroll.employee_fbp_component (tenant_id, ctc_structure_id, earning_id)
    WHERE earning_id IS NOT NULL;

CREATE UNIQUE INDEX uk_employee_fbp_component_tenant_structure_reimb
    ON payroll.employee_fbp_component (tenant_id, ctc_structure_id, reimbursement_id)
    WHERE reimbursement_id IS NOT NULL;

-- Lookup by tenant and employee
CREATE INDEX idx_employee_fbp_component_tenant_employee
    ON payroll.employee_fbp_component (tenant_id, employee_id);

-- Row-Level Security
ALTER TABLE payroll.employee_fbp_component ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_fbp_component
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
