-- W-32.2: Employee Investment Declaration - House Rent (Section 10(13A))
-- Stores rented periods, monthly rent, landlord details and PAN.

CREATE TABLE payroll.employee_inv_house_rent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    declaration_id UUID NOT NULL REFERENCES payroll.employee_investment_declaration(id) ON DELETE CASCADE,
    from_month DATE NOT NULL,
    to_month DATE NOT NULL,
    address VARCHAR(1000) NOT NULL,
    landlord_name VARCHAR(150) NOT NULL,
    landlord_pan VARCHAR(10),
    is_metro BOOLEAN NOT NULL DEFAULT FALSE,
    amount_per_month NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_house_rent_months CHECK (from_month <= to_month),
    CONSTRAINT chk_house_rent_amount CHECK (amount_per_month >= 0)
);

CREATE INDEX idx_employee_inv_house_rent_tenant_declaration
    ON payroll.employee_inv_house_rent (tenant_id, declaration_id);

ALTER TABLE payroll.employee_inv_house_rent ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_inv_house_rent
    FOR ALL
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL OR current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );
