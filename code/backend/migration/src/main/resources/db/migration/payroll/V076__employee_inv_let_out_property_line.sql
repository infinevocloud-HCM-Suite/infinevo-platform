-- W-32.2: Employee Investment Declaration - Let-Out Property Lines
-- Stores typed financial breakdown lines (ANNUAL_RENT, MUNICIPAL_TAX, LOAN_INTEREST) per property.

CREATE TABLE payroll.employee_inv_let_out_property_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    declaration_id UUID NOT NULL REFERENCES payroll.employee_investment_declaration(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES payroll.employee_inv_let_out_property(id) ON DELETE CASCADE,
    line_type VARCHAR(16) NOT NULL CHECK (line_type IN ('ANNUAL_RENT','MUNICIPAL_TAX','LOAN_INTEREST')),
    amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (amount >= 0),
    lender_name VARCHAR(150),
    lender_pan VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_employee_inv_let_out_property_line_tenant_property_type
    ON payroll.employee_inv_let_out_property_line (tenant_id, property_id, line_type);

CREATE INDEX idx_employee_inv_let_out_property_line_tenant_declaration
    ON payroll.employee_inv_let_out_property_line (tenant_id, declaration_id);

CREATE INDEX idx_employee_inv_let_out_property_line_tenant_property
    ON payroll.employee_inv_let_out_property_line (tenant_id, property_id);

ALTER TABLE payroll.employee_inv_let_out_property_line ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_inv_let_out_property_line
    FOR ALL
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL OR current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );
