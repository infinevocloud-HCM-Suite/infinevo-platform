-- Migration: V056__employee_payrun.sql
-- Description: W-29.1 payroll.employee_payrun — one row per employee a run considered, INCLUDED or SKIPPED with the reason

CREATE TABLE payroll.employee_payrun (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    payrun_id UUID NOT NULL REFERENCES payroll.payrun(id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    -- The salary version in force at period_end; set only when INCLUDED.
    salary_version_id UUID REFERENCES payroll.ctc_structure(id),
    inclusion_status VARCHAR(16) NOT NULL CHECK (inclusion_status IN ('INCLUDED','SKIPPED')),
    skip_reason VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    -- A skipped row always says why; an included row never carries a reason.
    CHECK ((inclusion_status = 'SKIPPED') = (skip_reason IS NOT NULL))
);

CREATE UNIQUE INDEX uk_employee_payrun_tenant_run_employee
    ON payroll.employee_payrun (tenant_id, payrun_id, employee_id);

CREATE INDEX idx_employee_payrun_tenant_run_inclusion
    ON payroll.employee_payrun (tenant_id, payrun_id, inclusion_status);

-- Row-Level Security
ALTER TABLE payroll.employee_payrun ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_payrun
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
