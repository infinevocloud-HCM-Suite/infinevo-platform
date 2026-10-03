-- Migration: V107__tax_deductor.sql
-- Description: W-36.3 payroll.tax_deductor — employer TAN, PAN, TDS circle and signatory with RLS isolation

CREATE TABLE payroll.tax_deductor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    tan CHAR(10) NOT NULL CHECK (tan ~ '^[A-Z]{4}[0-9]{5}[A-Z]$'),
    pan CHAR(10) NOT NULL CHECK (pan ~ '^[A-Z]{5}[0-9]{4}[A-Z]$'),
    tds_circle VARCHAR(13) NULL,
    signatory_employee_id UUID NULL REFERENCES core.employee(id),
    signatory_name VARCHAR(120) NOT NULL,
    signatory_parent_name VARCHAR(120) NULL,
    signatory_designation VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_tax_deductor_tenant UNIQUE (tenant_id)
);

CREATE INDEX idx_tax_deductor_tenant_signatory
    ON payroll.tax_deductor (tenant_id, signatory_employee_id);

ALTER TABLE payroll.tax_deductor ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.tax_deductor
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
