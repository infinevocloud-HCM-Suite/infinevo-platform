-- W-32.3: Chapter VI-A investment declaration line items.
--
-- Declared amounts against statutory Section 6A catalogue (reference.section6a_item_master).
-- Money columns are NUMERIC(19,4). RLS is tenant-scoped.

CREATE TABLE payroll.employee_inv_section6a (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    declaration_id UUID NOT NULL REFERENCES payroll.employee_investment_declaration(id) ON DELETE CASCADE,
    section6a_item_id UUID NOT NULL REFERENCES reference.section6a_item_master(id),
    description VARCHAR(150) NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_employee_inv_section6a_tenant_declaration
    ON payroll.employee_inv_section6a(tenant_id, declaration_id);

CREATE INDEX idx_employee_inv_section6a_tenant_item
    ON payroll.employee_inv_section6a(tenant_id, section6a_item_id);

ALTER TABLE payroll.employee_inv_section6a ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_inv_section6a
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
