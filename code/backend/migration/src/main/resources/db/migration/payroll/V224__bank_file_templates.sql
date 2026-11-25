-- Migration: V224__bank_file_templates.sql
-- Description: W-88.2 bank file templates and the history of files made from them.
--   payroll.bank_file_template — a company's own bank upload layout: name, CSV or XLSX, header switch,
--     and an ordered JSON list of columns (heading, source FIELD/FIXED/SERIAL, field, value, format).
--     The built-in "Generic bulk NEFT" layout lives in code and is never stored.
--   payroll.bank_file_export — one row per file made for a pay run; the file itself is a tenant EXPORT
--     document (core.document). A deleted template leaves its exports, with template_id nulled.

CREATE TABLE payroll.bank_file_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    name VARCHAR(80) NOT NULL,
    file_format VARCHAR(8) NOT NULL CHECK (file_format IN ('CSV', 'XLSX')),
    include_header BOOLEAN NOT NULL DEFAULT true,
    columns JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_bank_file_template_name ON payroll.bank_file_template (tenant_id, lower(name));

ALTER TABLE payroll.bank_file_template ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.bank_file_template
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

CREATE TABLE payroll.bank_file_export (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    payrun_id UUID NOT NULL REFERENCES payroll.payrun(id),
    template_id UUID NULL REFERENCES payroll.bank_file_template(id) ON DELETE SET NULL,
    template_name VARCHAR(80) NOT NULL,
    document_id UUID NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    row_count INT NOT NULL CHECK (row_count >= 0),
    file_total NUMERIC(19, 4) NOT NULL,
    selected_employee_count INT NULL CHECK (selected_employee_count IS NULL OR selected_employee_count > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL
);

CREATE INDEX idx_bank_file_export_run ON payroll.bank_file_export (tenant_id, payrun_id, created_at DESC);

ALTER TABLE payroll.bank_file_export ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.bank_file_export
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
