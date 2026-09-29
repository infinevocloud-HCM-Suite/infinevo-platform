-- W-32.4: Annual tax summary snapshot per declaration and regime.
--
-- Contains computed tax projection amounts populated by W-33.
-- Nullable computed figures initialized upon first read.

CREATE TABLE payroll.employee_inv_tax_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    declaration_id UUID NOT NULL REFERENCES payroll.employee_investment_declaration(id) ON DELETE CASCADE,
    regime VARCHAR(3) NOT NULL CHECK (regime IN ('OLD', 'NEW')),
    taxable_income NUMERIC(19,4),
    net_taxable_income NUMERIC(19,4),
    tax_on_taxable_income NUMERIC(19,4),
    tax_ytd_amount NUMERIC(19,4),
    tax_to_be_paid NUMERIC(19,4),
    tds_through_payroll NUMERIC(19,4),
    tds_previous_employer NUMERIC(19,4),
    tds_other_income NUMERIC(19,4),
    other_sources_income NUMERIC(19,4),
    exemption_under_section10 NUMERIC(19,4),
    exemption_under_section6a NUMERIC(19,4),
    remaining_months SMALLINT,
    computed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_employee_inv_tax_summary_tenant_declaration_regime UNIQUE (tenant_id, declaration_id, regime)
);

CREATE INDEX idx_employee_inv_tax_summary_tenant_declaration
    ON payroll.employee_inv_tax_summary(tenant_id, declaration_id);

ALTER TABLE payroll.employee_inv_tax_summary ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_inv_tax_summary
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
