-- Migration: V109__form16_part_a.sql
-- Description: W-36.5 payroll.form16_part_a — Form 16 Part A certificate link per employee and year

CREATE TABLE payroll.form16_part_a (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    financial_year VARCHAR(9) NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$'),
    document_id UUID NOT NULL REFERENCES core.document(id),
    source_file_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    superseded_at TIMESTAMPTZ NULL CHECK ((is_active) = (superseded_at IS NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_form16_part_a_tenant_employee_fy_active
    ON payroll.form16_part_a (tenant_id, employee_id, financial_year)
    WHERE is_active;

CREATE INDEX idx_form16_part_a_tenant_fy
    ON payroll.form16_part_a (tenant_id, financial_year, is_active);

CREATE INDEX idx_form16_part_a_tenant_document
    ON payroll.form16_part_a (tenant_id, document_id);

ALTER TABLE payroll.form16_part_a ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.form16_part_a
    FOR ALL
    USING (
        tenant_id = CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
            WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
            ELSE current_setting('app.current_tenant_id', true)::uuid
        END
    )
    WITH CHECK (
        tenant_id = CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
            WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
            ELSE current_setting('app.current_tenant_id', true)::uuid
        END
    );
