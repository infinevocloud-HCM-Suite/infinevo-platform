-- Migration: V046__ctc_structure.sql
-- Description: W-26.2 payroll.ctc_structure — dated salary versions for employees with RLS isolation

CREATE TABLE payroll.ctc_structure (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    effective_from DATE NOT NULL,
    annual_ctc NUMERIC(19,4) NOT NULL,
    monthly_ctc NUMERIC(19,4) NOT NULL,
    is_cancelled BOOLEAN NOT NULL DEFAULT false,
    cancelled_at TIMESTAMPTZ,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Unique effective date per employee in a tenant
CREATE UNIQUE INDEX uk_ctc_structure_tenant_employee_effective
    ON payroll.ctc_structure (tenant_id, employee_id, effective_from);

-- As-of lookup index: non-cancelled versions ordered by effective_from descending
CREATE INDEX idx_ctc_structure_tenant_employee_cancelled
    ON payroll.ctc_structure (tenant_id, employee_id, is_cancelled, effective_from DESC);

-- Row-Level Security
ALTER TABLE payroll.ctc_structure ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.ctc_structure
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
