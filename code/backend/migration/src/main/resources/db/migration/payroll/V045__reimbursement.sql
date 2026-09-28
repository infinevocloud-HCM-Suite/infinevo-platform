-- Migration: V045__reimbursement.sql
-- Description: W-26.1 payroll.reimbursement — organization salary reimbursement components with RLS isolation

CREATE TABLE payroll.reimbursement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    display_name VARCHAR(128),
    reimbursement_type VARCHAR(32) NOT NULL,
    calculation_type VARCHAR(16) NOT NULL DEFAULT 'FLAT',
    default_value NUMERIC(19,4),
    percentage_of VARCHAR(16),
    max_limit NUMERIC(19,4),
    carry_forward_option VARCHAR(32),
    is_included_in_ctc BOOLEAN NOT NULL DEFAULT false,
    is_included_in_salary_structure BOOLEAN NOT NULL DEFAULT false,
    is_fbp_component BOOLEAN NOT NULL DEFAULT false,
    is_opt_in BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Unique code within a tenant
CREATE UNIQUE INDEX uk_reimbursement_tenant_code ON payroll.reimbursement (tenant_id, code);

-- Listing index filtering soft-deleted and active rows
CREATE INDEX idx_reimbursement_tenant_deleted_active ON payroll.reimbursement (tenant_id, is_deleted, is_active);

-- Row-Level Security
ALTER TABLE payroll.reimbursement ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.reimbursement
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
