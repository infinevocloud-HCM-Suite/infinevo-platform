-- Migration: V197__gratuity.sql
-- Description: W-91 Gratuity calculation and settlement store.
--   payroll.gratuity_settlement — stores employee gratuity calculations under the Payment of Gratuity Act, 1972
--   with Section 10(10) tax exemption split.

CREATE TABLE payroll.gratuity_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    joining_date DATE NOT NULL,
    exit_date DATE NOT NULL,
    completed_years INTEGER NOT NULL CHECK (completed_years >= 0),
    is_exempt_from_5yr_rule BOOLEAN NOT NULL DEFAULT false,
    last_drawn_basic NUMERIC(14,2) NOT NULL CHECK (last_drawn_basic >= 0),
    last_drawn_da NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (last_drawn_da >= 0),
    calculated_amount NUMERIC(14,2) NOT NULL CHECK (calculated_amount >= 0),
    statutory_ceiling NUMERIC(14,2) NOT NULL DEFAULT 2000000.00,
    gross_gratuity_payable NUMERIC(14,2) NOT NULL CHECK (gross_gratuity_payable >= 0),
    tax_exempt_amount NUMERIC(14,2) NOT NULL CHECK (tax_exempt_amount >= 0),
    taxable_amount NUMERIC(14,2) NOT NULL CHECK (taxable_amount >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'PAID', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_gratuity_settlement_employee
    ON payroll.gratuity_settlement (tenant_id, employee_id)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_gratuity_settlement_tenant_status
    ON payroll.gratuity_settlement (tenant_id, status);

ALTER TABLE payroll.gratuity_settlement ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.gratuity_settlement
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
