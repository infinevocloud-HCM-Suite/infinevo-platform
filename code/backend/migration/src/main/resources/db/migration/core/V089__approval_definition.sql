-- Migration: V089__approval_definition.sql
-- Description: W-15.1 core.approval_definition — approval flow models, steps, and default seed per tenant

CREATE TABLE core.approval_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    flow_type VARCHAR(32) NOT NULL,
    step_ordering VARCHAR(16) NOT NULL,
    steps JSONB NOT NULL,
    comment_scope VARCHAR(16) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    effective_from DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_approval_definition_effective UNIQUE (tenant_id, flow_type, effective_from)
);

CREATE INDEX idx_approval_definition_lookup ON core.approval_definition (tenant_id, flow_type, effective_from DESC);

ALTER TABLE core.approval_definition ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.approval_definition
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Seed function for a tenant's default approval definitions (all seven flows)
CREATE OR REPLACE FUNCTION core.seed_approval_definitions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    -- 1. LEAVE: strict two-stage ladder (sequential, manager then HR)
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'LEAVE',
        'SEQUENTIAL',
        '[
            {"kind": "REPORTING_MANAGER", "assignee": null, "escalate_after_days": 3, "per_item": false},
            {"kind": "ROLE", "assignee": "hr", "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'PER_STEP',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 2. OVERTIME: permissive two-stage set (any order)
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'OVERTIME',
        'ANY_ORDER',
        '[
            {"kind": "REPORTING_MANAGER", "assignee": null, "escalate_after_days": 3, "per_item": false},
            {"kind": "ROLE", "assignee": "hr", "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'PER_STEP',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 3. REIMBURSEMENT: single tenant-admin step with approved amount
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'REIMBURSEMENT',
        'SEQUENTIAL',
        '[
            {"kind": "ROLE", "assignee": "tenant-admin", "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'SHARED',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 4. PROOF_OF_INVESTMENT: per-item stage then final decision
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'PROOF_OF_INVESTMENT',
        'SEQUENTIAL',
        '[
            {"kind": "ROLE", "assignee": "hr", "escalate_after_days": 3, "per_item": true},
            {"kind": "ROLE", "assignee": "hr", "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'SHARED',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 5. REGULARIZATION: single step to reporting manager
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'REGULARIZATION',
        'SEQUENTIAL',
        '[
            {"kind": "REPORTING_MANAGER", "assignee": null, "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'PER_STEP',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 6. PAY_RUN: single payroll-officer step
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'PAY_RUN',
        'SEQUENTIAL',
        '[
            {"kind": "ROLE", "assignee": "payroll-officer", "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'SHARED',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;

    -- 7. TIMESHEET: single project-manager step
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'TIMESHEET',
        'SEQUENTIAL',
        '[
            {"kind": "PROJECT_MANAGER", "assignee": null, "escalate_after_days": 3, "per_item": false}
        ]'::jsonb,
        'PER_STEP',
        true,
        '2024-01-01',
        'system',
        'system'
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.seed_approval_definitions(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_seed_approval_definitions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.seed_approval_definitions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_seed_approval_definitions() FROM PUBLIC;

CREATE TRIGGER tenant_seed_approval_definitions
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_seed_approval_definitions();

-- Backfill: existing tenants get default definitions for all seven flows
SELECT core.seed_approval_definitions(t.tenant_id) FROM core.tenant t;
