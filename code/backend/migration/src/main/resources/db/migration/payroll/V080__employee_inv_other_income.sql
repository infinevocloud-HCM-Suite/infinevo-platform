-- W-32.4: Other income declarations.
--
-- Declared income from other sources (savings interest, FD interest, NSC interest, other).
-- Unique per tenant, declaration, and kind.

CREATE TABLE payroll.employee_inv_other_income (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    declaration_id UUID NOT NULL REFERENCES payroll.employee_investment_declaration(id) ON DELETE CASCADE,
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('SAVINGS_INTEREST', 'FD_INTEREST', 'NSC_INTEREST', 'OTHER')),
    description VARCHAR(150),
    amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_employee_inv_other_income_tenant_declaration_kind UNIQUE (tenant_id, declaration_id, kind)
);

CREATE INDEX idx_employee_inv_other_income_tenant_declaration
    ON payroll.employee_inv_other_income(tenant_id, declaration_id);

ALTER TABLE payroll.employee_inv_other_income ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_inv_other_income
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
