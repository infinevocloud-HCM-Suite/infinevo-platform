-- W-32.2: Employee Investment Declaration - Let-Out Property (Section 24)
-- Stores let-out property header and derived net income/loss.

CREATE TABLE payroll.employee_inv_let_out_property (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    declaration_id UUID NOT NULL REFERENCES payroll.employee_investment_declaration(id) ON DELETE CASCADE,
    property_name VARCHAR(150) NOT NULL,
    address VARCHAR(1000),
    net_income_loss NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_employee_inv_let_out_property_tenant_declaration
    ON payroll.employee_inv_let_out_property (tenant_id, declaration_id);

ALTER TABLE payroll.employee_inv_let_out_property ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_inv_let_out_property
    FOR ALL
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL OR current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );
