-- W-32.3: Previous employment declarations.
--
-- Declared amounts from previous employer in current financial year (Section 192).
-- Includes entered_by flag ('EMPLOYEE' or 'OFFICER').
-- Unique per tenant, declaration, and kind.

CREATE TABLE payroll.employee_inv_prev_employment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    declaration_id UUID NOT NULL REFERENCES payroll.employee_investment_declaration(id) ON DELETE CASCADE,
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('INCOME', 'INCOME_TAX_DEDUCTED', 'PROFESSIONAL_TAX', 'EMPLOYEE_PF', 'LEAVE_ENCASHMENT')),
    amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    employer_name VARCHAR(150),
    employer_tan VARCHAR(10),
    entered_by VARCHAR(8) NOT NULL CHECK (entered_by IN ('EMPLOYEE', 'OFFICER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_employee_inv_prev_employment_tenant_declaration_kind UNIQUE (tenant_id, declaration_id, kind)
);

CREATE INDEX idx_employee_inv_prev_employment_tenant_declaration
    ON payroll.employee_inv_prev_employment(tenant_id, declaration_id);

ALTER TABLE payroll.employee_inv_prev_employment ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_inv_prev_employment
    FOR ALL
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL
              OR current_setting('app.current_tenant_id', true) = ''
            THEN false
            ELSE tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        END
    )
    WITH CHECK (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL
              OR current_setting('app.current_tenant_id', true) = ''
            THEN false
            ELSE tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        END
    );
