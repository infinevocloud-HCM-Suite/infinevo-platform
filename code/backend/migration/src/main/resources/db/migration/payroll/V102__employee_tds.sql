-- Migration: V102__employee_tds.sql
-- Description: W-36.1 payroll.employee_tds — annual tax record per employee per financial year.
-- Superseded not edited: when a figure changes, the active row is marked is_active = false
-- with superseded_at = clock_timestamp(), and a new active row is inserted.
-- Replaces legacy employee_tds (legacy/docs/DB_SCHEMA.md:1374-1391, EmployeeTds.java:8-51).

CREATE TABLE payroll.employee_tds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    -- Legacy kept Keycloak string as employee_id (EmployeeTds.java:16).
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    financial_year VARCHAR(9) NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$'),
    regime VARCHAR(3) NOT NULL CHECK (regime IN ('OLD','NEW')),
    source VARCHAR(16) NOT NULL CHECK (source IN ('DECLARATION','OFFICER')),
    declaration_id UUID NULL REFERENCES payroll.employee_investment_declaration(id),
    annual_gross NUMERIC(19,4) NOT NULL CHECK (annual_gross >= 0),
    annual_taxable_income NUMERIC(19,4) NOT NULL CHECK (annual_taxable_income >= 0),
    annual_tax NUMERIC(19,4) NOT NULL CHECK (annual_tax >= 0),
    effective_from_period CHAR(7) NOT NULL CHECK (effective_from_period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    is_active BOOLEAN NOT NULL DEFAULT true,
    superseded_at TIMESTAMPTZ NULL,
    note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    CHECK ((is_active) = (superseded_at IS NULL))
);

-- Exactly one active row per employee per financial year (W-36.1 §6).
CREATE UNIQUE INDEX uk_employee_tds_tenant_employee_fy_active
    ON payroll.employee_tds (tenant_id, employee_id, financial_year)
    WHERE is_active;

CREATE INDEX idx_employee_tds_tenant_employee_fy
    ON payroll.employee_tds (tenant_id, employee_id, financial_year, created_at DESC);

-- Row-Level Security
ALTER TABLE payroll.employee_tds ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_tds
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- A run with no active record writes no tax line and says so on the employee's row (§2, §3).
ALTER TABLE payroll.employee_payrun ADD COLUMN computation_note VARCHAR(500);
