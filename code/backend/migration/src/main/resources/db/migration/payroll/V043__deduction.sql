-- Migration: V043__deduction.sql
-- Description: W-26.1 payroll.deduction — organization salary deduction components with RLS isolation

CREATE TABLE payroll.deduction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    display_name VARCHAR(128),
    deduction_type VARCHAR(32) NOT NULL,
    calculation_type VARCHAR(16) NOT NULL DEFAULT 'FLAT',
    default_value NUMERIC(19,4),
    percentage_of VARCHAR(16),
    max_limit NUMERIC(19,4),
    is_recurring BOOLEAN NOT NULL DEFAULT false,
    is_pre_tax BOOLEAN NOT NULL DEFAULT false,
    emi_type VARCHAR(32),
    perquisite_interest_rate NUMERIC(7,4),
    emi_interest_rate NUMERIC(7,4),
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Unique code within a tenant
CREATE UNIQUE INDEX uk_deduction_tenant_code ON payroll.deduction (tenant_id, code);

-- Listing index filtering soft-deleted and active rows
CREATE INDEX idx_deduction_tenant_deleted_active ON payroll.deduction (tenant_id, is_deleted, is_active);

-- Row-Level Security
ALTER TABLE payroll.deduction ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.deduction
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
