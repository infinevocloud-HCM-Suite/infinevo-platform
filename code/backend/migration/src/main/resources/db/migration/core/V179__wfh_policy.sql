-- Migration: V179__wfh_policy.sql
-- Description: W-74 core.wfh_policy — tenant remote work policy rules with RLS isolation

CREATE TABLE core.wfh_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    annual_days NUMERIC(10,2) NOT NULL DEFAULT 12.00,
    monthly_limit_days NUMERIC(10,2) NULL DEFAULT 2.00,
    allow_half_day BOOLEAN NOT NULL DEFAULT true,
    advance_notice_days INT NOT NULL DEFAULT 1,
    max_consecutive_days INT NULL DEFAULT 3,
    requires_manager_approval BOOLEAN NOT NULL DEFAULT true,
    requires_hr_approval BOOLEAN NOT NULL DEFAULT false,
    effective_from DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_wfh_policy_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_wfh_policy_tenant ON core.wfh_policy (tenant_id, is_active);

ALTER TABLE core.wfh_policy ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.wfh_policy
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON core.wfh_policy TO app_user;
