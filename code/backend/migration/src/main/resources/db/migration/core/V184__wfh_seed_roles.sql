-- Migration: V184__wfh_seed_roles.sql
-- Description: W-74 reference.action & core.role_action — seed WFH permission actions and role grants for hr, manager, and employee

-- 1. Seed reference.action catalogue
INSERT INTO reference.action (code, name, module, description) VALUES
    ('core.wfh.read', 'View all WFH requests and allocations', 'core', 'View tenant-wide remote work requests, policies and allocations'),
    ('core.wfh.manage', 'Manage WFH policies and settings', 'core', 'Configure tenant-level WFH rules, annual entitlements and approval workflows'),
    ('core.wfh_balance.manage', 'Manage employee WFH allocations', 'core', 'Allocate yearly remote work quota, adjustments and bulk import'),
    ('core.wfh.export', 'Export WFH reports', 'core', 'Export remote work logs and consumption reports'),
    ('core.wfh.read_team', 'View team WFH requests', 'core', 'Line manager review of reporting team WFH requests'),
    ('core.wfh.apply', 'Apply for Work From Home', 'core', 'Employee self-service submission and withdrawal of remote work applications'),
    ('core.wfh.read_own', 'View own WFH requests and balance', 'core', 'Employee self-service view of own WFH history and quota')
ON CONFLICT (code) DO NOTHING;

-- 2. Grant function for tenant roles
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

    -- Seed WORK_FROM_HOME approval definition
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
    ) ON CONFLICT (tenant_id, flow_type, effective_from) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_wfh_actions(UUID) FROM PUBLIC;

-- 3. Trigger for new tenants
CREATE OR REPLACE FUNCTION core.tenant_grant_wfh_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_wfh_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_wfh_actions() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_system_roles_wfh ON core.tenant;
CREATE TRIGGER tenant_seed_system_roles_wfh
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_wfh_actions();

-- 4. Backfill existing non-platform tenants
SELECT core.grant_wfh_actions(t.tenant_id) FROM core.tenant t;
