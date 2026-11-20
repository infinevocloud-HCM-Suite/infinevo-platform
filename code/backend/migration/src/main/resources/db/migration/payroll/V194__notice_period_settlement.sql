-- Migration: V194__notice_period_settlement.sql
-- Description: W-90.2 Notice Period Pay & Shortfall Recovery Engine
--   payroll.notice_period_settlement — records notice period calculations, buyout payouts, and early exit shortfall recoveries.

CREATE TABLE payroll.notice_period_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    resignation_date DATE NOT NULL,
    expected_exit_date DATE NOT NULL,
    actual_exit_date DATE NOT NULL,
    required_notice_days INTEGER NOT NULL CHECK (required_notice_days >= 0),
    served_notice_days INTEGER NOT NULL CHECK (served_notice_days >= 0),
    shortfall_days INTEGER NOT NULL DEFAULT 0,
    daily_rate NUMERIC(14,2) NOT NULL CHECK (daily_rate >= 0),
    rate_basis VARCHAR(20) NOT NULL DEFAULT 'GROSS' CHECK (rate_basis IN ('GROSS', 'BASIC')),
    settlement_type VARCHAR(20) NOT NULL CHECK (settlement_type IN ('RECOVERY', 'PAYOUT', 'WAIVED')),
    settlement_amount NUMERIC(14,2) NOT NULL CHECK (settlement_amount >= 0),
    waiver_reason VARCHAR(255) NULL,
    approved_by VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'SETTLED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_notice_settlement_employee
    ON payroll.notice_period_settlement (tenant_id, employee_id)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_notice_settlement_tenant_emp
    ON payroll.notice_period_settlement (tenant_id, employee_id);

ALTER TABLE payroll.notice_period_settlement ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.notice_period_settlement
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
