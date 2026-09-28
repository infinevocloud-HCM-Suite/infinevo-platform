-- Migration: V090__approval_instance.sql
-- Description: W-15.2 core.approval_instance — approval lifecycle instance table

CREATE TABLE core.approval_instance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    flow_type VARCHAR(32) NOT NULL,
    definition_id UUID NOT NULL REFERENCES core.approval_definition(id),
    subject_table VARCHAR(64) NOT NULL,
    subject_id UUID NOT NULL,
    subject_employee_id UUID NOT NULL REFERENCES core.employee(id),
    status VARCHAR(16) NOT NULL,
    outcome_notified_at TIMESTAMPTZ NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_approval_instance_lookup ON core.approval_instance (tenant_id, subject_table, subject_id);
CREATE INDEX idx_approval_instance_status ON core.approval_instance (tenant_id, status);

ALTER TABLE core.approval_instance ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.approval_instance
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Cross-tenant SECURITY DEFINER function to find tenants with pending outcome dispatches (W-15.2)
CREATE OR REPLACE FUNCTION core.list_distinct_tenants_with_pending_outcomes()
RETURNS TABLE (tenant_id UUID)
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT DISTINCT tenant_id FROM core.approval_instance
    WHERE status IN ('APPROVED', 'REJECTED') AND outcome_notified_at IS NULL;
$$;

REVOKE EXECUTE ON FUNCTION core.list_distinct_tenants_with_pending_outcomes() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.list_distinct_tenants_with_pending_outcomes() TO app_user, worker_user;
