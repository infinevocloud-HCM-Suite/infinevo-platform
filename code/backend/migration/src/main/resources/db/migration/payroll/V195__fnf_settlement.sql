-- Migration: V195__fnf_settlement.sql
-- Description: W-90.3 Full & Final (F&F) Settlement Computation & Lifecycle
--   payroll.fnf_settlement — consolidates exit salary, leave encashment, gratuity, notice buyout/shortfall, deductions, and tax.

CREATE TABLE payroll.fnf_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    resignation_date DATE NOT NULL,
    exit_date DATE NOT NULL,
    exit_reason VARCHAR(50) NOT NULL, -- RESIGNATION, RETIREMENT, TERMINATION, ABSCONDING
    working_days_in_exit_month INTEGER NOT NULL CHECK (working_days_in_exit_month >= 0),
    exit_month_salary NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    leave_encashment_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    gratuity_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    notice_pay_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    other_earnings NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    gross_earnings NUMERIC(14,2) NOT NULL,
    notice_recovery_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    epf_deduction NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    esi_deduction NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    pt_deduction NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    tds_deduction NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    other_recoveries NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    gross_deductions NUMERIC(14,2) NOT NULL,
    net_payable NUMERIC(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'UNDER_REVIEW', 'APPROVED', 'PAID', 'CANCELLED')),
    approved_by VARCHAR(100) NULL,
    approved_at TIMESTAMPTZ NULL,
    paid_at TIMESTAMPTZ NULL,
    remarks VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_fnf_settlement_employee
    ON payroll.fnf_settlement (tenant_id, employee_id)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_fnf_settlement_tenant_emp
    ON payroll.fnf_settlement (tenant_id, employee_id);

ALTER TABLE payroll.fnf_settlement ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.fnf_settlement
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
