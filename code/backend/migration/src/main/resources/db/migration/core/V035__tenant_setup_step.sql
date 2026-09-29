-- Migration: V035__tenant_setup_step.sql
-- Description: W-24.1 core.tenant_setup_step — module-aware onboarding checklist with row-level security isolation

CREATE TABLE core.tenant_setup_step (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    step_code VARCHAR(64) NOT NULL,
    module VARCHAR(16) NULL,
    display_order INT NOT NULL,
    is_skipped BOOLEAN NOT NULL DEFAULT false,
    skip_reason VARCHAR(500) NULL,
    completed_at TIMESTAMPTZ NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
CREATE UNIQUE INDEX idx_tenant_setup_step_tenant_step ON core.tenant_setup_step (tenant_id, step_code);
CREATE INDEX idx_tenant_setup_step_tenant_order ON core.tenant_setup_step (tenant_id, display_order);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.tenant_setup_step ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.tenant_setup_step
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
