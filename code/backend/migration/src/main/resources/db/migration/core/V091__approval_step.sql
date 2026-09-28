-- Migration: V091__approval_step.sql
-- Description: W-15.2 core.approval_step — approval lifecycle steps, assignees, decisions and approved amounts

CREATE TABLE core.approval_step (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    instance_id UUID NOT NULL REFERENCES core.approval_instance(id) ON DELETE CASCADE,
    step_index INT NOT NULL,
    item_ref VARCHAR(64) NULL,
    approver_kind VARCHAR(24) NOT NULL,
    assignee_employee_id UUID NULL REFERENCES core.employee(id),
    decision VARCHAR(16) NULL,
    comment VARCHAR(1000) NULL,
    approved_amount NUMERIC(19,4) NULL,
    decided_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_approval_step_pending ON core.approval_step (tenant_id, assignee_employee_id, decision);
CREATE INDEX idx_approval_step_instance ON core.approval_step (tenant_id, instance_id, step_index);

ALTER TABLE core.approval_step ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.approval_step
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Cross-tenant SECURITY DEFINER function for escalation sweep (W-15.3, 12-core-contracts.md row 17)
CREATE OR REPLACE FUNCTION core.list_distinct_tenants_with_pending_approval_steps()
RETURNS TABLE (tenant_id UUID)
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT DISTINCT s.tenant_id FROM core.approval_step s
    JOIN core.approval_instance i ON s.instance_id = i.id
    WHERE s.decision IS NULL AND i.status = 'PENDING';
$$;

REVOKE EXECUTE ON FUNCTION core.list_distinct_tenants_with_pending_approval_steps() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.list_distinct_tenants_with_pending_approval_steps() TO app_user, worker_user;
