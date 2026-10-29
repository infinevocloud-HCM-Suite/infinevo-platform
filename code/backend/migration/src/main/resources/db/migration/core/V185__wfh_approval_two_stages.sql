-- Migration: V185__wfh_approval_two_stages.sql
-- Description: D-122 — update WORK_FROM_HOME approval definition to 2-stage approval (REPORTING_MANAGER -> ROLE hr)

-- 1. Update existing WORK_FROM_HOME approval definitions for all active tenants
UPDATE core.approval_definition
SET steps = '[
    {"kind": "REPORTING_MANAGER", "assignee": null, "escalate_after_days": 3, "per_item": false},
    {"kind": "ROLE", "assignee": "hr", "escalate_after_days": 3, "per_item": false}
]'::jsonb,
updated_at = now(),
updated_by = 'system'
WHERE flow_type = 'WORK_FROM_HOME';

-- 2. Update core.grant_wfh_actions function to seed 2-stage approval for future tenants
CREATE OR REPLACE FUNCTION core.grant_wfh_actions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF p_tenant_id = '00000000-0000-0000-0000-000000000001'::uuid THEN
        RETURN;
    END IF;

    -- Role grants for tenant roles (platform-admin, tenant-admin, hr, manager, employee)
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('platform-admin', 'core.wfh.read'),
        ('platform-admin', 'core.wfh.manage'),
        ('platform-admin', 'core.wfh_balance.manage'),
        ('platform-admin', 'core.wfh.export'),
        ('platform-admin', 'core.wfh.read_team'),
        ('platform-admin', 'core.wfh.apply'),
        ('platform-admin', 'core.wfh.read_own'),

        ('tenant-admin', 'core.wfh.read'),
        ('tenant-admin', 'core.wfh.manage'),
        ('tenant-admin', 'core.wfh_balance.manage'),
        ('tenant-admin', 'core.wfh.export'),
        ('tenant-admin', 'core.wfh.read_team'),
        ('tenant-admin', 'core.wfh.apply'),
        ('tenant-admin', 'core.wfh.read_own'),

        ('hr', 'core.wfh.read'),
        ('hr', 'core.wfh.manage'),
        ('hr', 'core.wfh_balance.manage'),
        ('hr', 'core.wfh.export'),

        ('manager', 'core.wfh.read_team'),

        ('employee', 'core.wfh.apply'),
        ('employee', 'core.wfh.read_own')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;

    -- Seed WORK_FROM_HOME approval definition with 2 stages
    INSERT INTO core.approval_definition (
        tenant_id, flow_type, step_ordering, steps, comment_scope, is_active, effective_from, created_by, updated_by
    ) VALUES (
        p_tenant_id,
        'WORK_FROM_HOME',
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
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO UPDATE
    SET steps = EXCLUDED.steps,
        updated_at = now(),
        updated_by = 'system';
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_wfh_actions(UUID) FROM PUBLIC;
