-- Migration: V109__form16_part_a.sql
-- Description: W-36.5 payroll.form16_part_a — links each employee's Form 16 Part A certificate,
-- uploaded from the TRACES ZIP, to the document store row that holds it.
--
-- The frozen screen asked for the Part A ZIP and its upload handler only logged the file
-- (legacy/Payroll-Fend-react/.../form16/generateForm16.js:38-43, :69); there is no frozen table.
-- Superseded not edited, the W-36.1 shape (V102): a re-upload marks the active row is_active = false
-- with superseded_at set, soft-deletes its document, and inserts a new active row.

CREATE TABLE payroll.form16_part_a (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    financial_year VARCHAR(9) NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$'),
    document_id UUID NOT NULL REFERENCES core.document(id),
    -- The entry name inside the ZIP, shown to the officer. Never used as a path.
    source_file_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    superseded_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT form16_part_a_superseded_check CHECK ((is_active) = (superseded_at IS NULL))
);

-- Exactly one active certificate per employee per financial year (W-36.5 §6).
CREATE UNIQUE INDEX uk_form16_part_a_tenant_employee_fy_active
    ON payroll.form16_part_a (tenant_id, employee_id, financial_year)
    WHERE is_active;

-- The officer list for a year.
CREATE INDEX idx_form16_part_a_tenant_fy
    ON payroll.form16_part_a (tenant_id, financial_year, is_active);

-- Covers the foreign key to core.document.
CREATE INDEX idx_form16_part_a_tenant_document
    ON payroll.form16_part_a (tenant_id, document_id);

-- Row-Level Security
ALTER TABLE payroll.form16_part_a ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.form16_part_a
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
