-- Migration: V112__leave_policy_eligibility.sql
-- Description: W-16.1 core.leave_policy_eligibility — eligibility matrix per policy with RLS isolation

CREATE TABLE core.leave_policy_eligibility (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    policy_id UUID NOT NULL REFERENCES core.leave_policy(id) ON DELETE CASCADE,
    dimension VARCHAR(32) NOT NULL,
    value_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_leave_policy_eligibility_dimension CHECK (dimension IN ('department','designation','work_location','employment_type')),
    CONSTRAINT uk_leave_policy_eligibility UNIQUE (tenant_id, policy_id, dimension, value_id)
);

CREATE INDEX idx_leave_policy_eligibility_lookup ON core.leave_policy_eligibility (tenant_id, policy_id);

ALTER TABLE core.leave_policy_eligibility ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.leave_policy_eligibility
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
