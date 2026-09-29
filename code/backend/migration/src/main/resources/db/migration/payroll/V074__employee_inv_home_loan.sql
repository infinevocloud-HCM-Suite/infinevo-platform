-- W-32.2: Employee Investment Declaration - Home Loan (Section 24(b), 80C, 80EE/80EEA)
-- Stores self-occupied home loan principal, interest, lender details, sanction date and first-time buyer flag.

CREATE TABLE payroll.employee_inv_home_loan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    declaration_id UUID NOT NULL REFERENCES payroll.employee_investment_declaration(id) ON DELETE CASCADE,
    lender_name VARCHAR(150) NOT NULL,
    lender_pan VARCHAR(10),
    principal_paid NUMERIC(19,4) NOT NULL DEFAULT 0,
    interest_paid NUMERIC(19,4) NOT NULL DEFAULT 0,
    is_first_time_buyer BOOLEAN NOT NULL DEFAULT FALSE,
    loan_sanctioned_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_home_loan_principal CHECK (principal_paid >= 0),
    CONSTRAINT chk_home_loan_interest CHECK (interest_paid >= 0)
);

CREATE INDEX idx_employee_inv_home_loan_tenant_declaration
    ON payroll.employee_inv_home_loan (tenant_id, declaration_id);

ALTER TABLE payroll.employee_inv_home_loan ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_inv_home_loan
    FOR ALL
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL OR current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );
