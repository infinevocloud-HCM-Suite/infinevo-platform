-- Migration: V057__employee_payrun_line.sql
-- Description: W-29.2 payroll.employee_payrun_line — one row per component per employee per run, with
-- code and name snapshots; the money columns W-29.1 left to this ticket on payrun and employee_payrun.

CREATE TABLE payroll.employee_payrun_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_payrun_id UUID NOT NULL REFERENCES payroll.employee_payrun(id),
    -- Denormalised from employee_payrun for the run-wide delete a recompute starts with.
    payrun_id UUID NOT NULL REFERENCES payroll.payrun(id),
    line_kind VARCHAR(16) NOT NULL CHECK (line_kind IN ('EARNING','DEDUCTION','BENEFIT','REIMBURSEMENT')),
    -- Each tag belongs to the ticket that owns its contributor: STRUCTURE here, LOP and PAY_INPUT W-29.3,
    -- STATUTORY W-31, TAX W-36.
    source VARCHAR(16) NOT NULL CHECK (source IN ('STRUCTURE','LOP','PAY_INPUT','STATUTORY','TAX')),
    -- No foreign key: it points into one of four catalogue tables, and a catalogue soft-delete must
    -- not break a paid month. The code and name below are snapshots for the same reason.
    component_id UUID,
    component_code VARCHAR(64) NOT NULL,
    component_name VARCHAR(120) NOT NULL,
    -- The kind carries the sign; the amount is never negative (W-19's rule).
    amount NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    is_taxable BOOLEAN NOT NULL,
    sort_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_employee_payrun_line_tenant_row
    ON payroll.employee_payrun_line (tenant_id, employee_payrun_id, sort_order);

CREATE INDEX idx_employee_payrun_line_tenant_run
    ON payroll.employee_payrun_line (tenant_id, payrun_id);

-- The FBP split writes two rows of one code, told apart by is_taxable.
CREATE UNIQUE INDEX uk_employee_payrun_line_tenant_row_source_code
    ON payroll.employee_payrun_line (tenant_id, employee_payrun_id, source, component_code, is_taxable);

-- Row-Level Security
ALTER TABLE payroll.employee_payrun_line ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_payrun_line
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Expand only: the columns W-29.1 §6 said this ticket would add, each with a default.
ALTER TABLE payroll.employee_payrun
    ADD COLUMN gross_earnings NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN total_reimbursements NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN total_benefits NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN total_deductions NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN net_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN computed_at TIMESTAMPTZ,
    ADD COLUMN computation_error VARCHAR(500);

ALTER TABLE payroll.payrun
    ADD COLUMN total_gross NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN total_deductions NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN total_net_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN computed_at TIMESTAMPTZ,
    ADD COLUMN failure_reason VARCHAR(500);
