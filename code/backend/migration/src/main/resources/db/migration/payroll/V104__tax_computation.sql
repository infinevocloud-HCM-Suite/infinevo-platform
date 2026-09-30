-- V104: payroll.tax_computation table for W-33.3 tax computation history
CREATE TABLE payroll.tax_computation (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id           UUID NOT NULL REFERENCES core.employee(id),
    declaration_id        UUID NULL REFERENCES payroll.employee_investment_declaration(id),
    financial_year        VARCHAR(9)  NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$'),
    regime                VARCHAR(3)  NOT NULL CHECK (regime IN ('OLD','NEW')),
    trigger               VARCHAR(24) NOT NULL CHECK (trigger IN (
                              'DECLARATION_SUBMITTED','SALARY_REVISION',
                              'SALARY_DEFAULT','PROOF_VERIFIED','OFFICER')),

    -- 14 headline money columns — NUMERIC(19,4), all >= 0 except house_property_income (which may be negative)
    gross_total_income    NUMERIC(19,4) NOT NULL CHECK (gross_total_income  >= 0),
    hra_exemption         NUMERIC(19,4) NOT NULL CHECK (hra_exemption        >= 0),
    standard_deduction    NUMERIC(19,4) NOT NULL CHECK (standard_deduction   >= 0),
    professional_tax      NUMERIC(19,4) NOT NULL CHECK (professional_tax     >= 0),
    house_property_income NUMERIC(19,4) NOT NULL,               -- may be negative (loss)
    other_income          NUMERIC(19,4) NOT NULL CHECK (other_income         >= 0),
    chapter_via           NUMERIC(19,4) NOT NULL CHECK (chapter_via          >= 0),
    taxable_income        NUMERIC(19,4) NOT NULL CHECK (taxable_income       >= 0),
    tax_before_rebate     NUMERIC(19,4) NOT NULL CHECK (tax_before_rebate    >= 0),
    rebate                NUMERIC(19,4) NOT NULL CHECK (rebate               >= 0),
    surcharge             NUMERIC(19,4) NOT NULL CHECK (surcharge            >= 0),
    cess                  NUMERIC(19,4) NOT NULL CHECK (cess                 >= 0),
    prev_employer_tds     NUMERIC(19,4) NOT NULL CHECK (prev_employer_tds    >= 0),
    annual_tax            NUMERIC(19,4) NOT NULL CHECK (annual_tax           >= 0),

    working               JSONB        NOT NULL,
    computed_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    computed_by           UUID         NULL,     -- officer UUID for OFFICER trigger, else null
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            TEXT         NOT NULL DEFAULT 'system'
);

-- Compound index for employee history lookup
CREATE INDEX idx_tax_computation_tenant_employee_fy
    ON payroll.tax_computation (tenant_id, employee_id, financial_year, computed_at DESC);

-- Partial index for declaration tracking
CREATE INDEX idx_tax_computation_tenant_declaration
    ON payroll.tax_computation (tenant_id, declaration_id)
    WHERE declaration_id IS NOT NULL;

-- Enable Row Level Security (RLS)
ALTER TABLE payroll.tax_computation ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.tax_computation
    USING (
        CASE
            WHEN current_setting('app.tenant_id', true) IS NULL THEN false
            ELSE tenant_id = current_setting('app.tenant_id', true)::uuid
        END
    );

-- Enforce append-only at the database role level (no UPDATE / DELETE for app_user)
GRANT SELECT, INSERT ON payroll.tax_computation TO app_user;
