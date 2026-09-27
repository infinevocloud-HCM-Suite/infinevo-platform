-- Migration: V042__earning.sql
-- Description: W-26.1 payroll.earning — organization salary earning components with RLS isolation

CREATE TABLE payroll.earning (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    display_name VARCHAR(128),
    earning_type VARCHAR(32) NOT NULL,
    calculation_type VARCHAR(16) NOT NULL DEFAULT 'FLAT',
    default_value NUMERIC(19,4),
    percentage_of VARCHAR(16),
    max_limit NUMERIC(19,4),
    earning_frequency VARCHAR(16),
    parent_earning_id UUID REFERENCES payroll.earning(id),
    is_pro_rata BOOLEAN NOT NULL DEFAULT false,
    is_included_in_ctc BOOLEAN NOT NULL DEFAULT false,
    is_included_in_salary_structure BOOLEAN NOT NULL DEFAULT false,
    is_taxable BOOLEAN NOT NULL DEFAULT false,
    is_variable BOOLEAN NOT NULL DEFAULT false,
    is_one_time BOOLEAN NOT NULL DEFAULT false,
    is_fbp_component BOOLEAN NOT NULL DEFAULT false,
    is_included_in_epf BOOLEAN NOT NULL DEFAULT false,
    epf_inclusion_type VARCHAR(32),
    is_included_in_esi BOOLEAN NOT NULL DEFAULT false,
    show_in_payslip BOOLEAN NOT NULL DEFAULT true,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Unique code within a tenant
CREATE UNIQUE INDEX uk_earning_tenant_code ON payroll.earning (tenant_id, code);

-- Listing index filtering soft-deleted and active rows
CREATE INDEX idx_earning_tenant_deleted_active ON payroll.earning (tenant_id, is_deleted, is_active);

-- Parent earning self-reference index
CREATE INDEX idx_earning_tenant_parent ON payroll.earning (tenant_id, parent_earning_id);

-- Row-Level Security
ALTER TABLE payroll.earning ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.earning
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
