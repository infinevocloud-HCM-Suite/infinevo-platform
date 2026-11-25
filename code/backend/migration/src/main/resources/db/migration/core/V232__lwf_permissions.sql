-- Migration: V232__lwf_permissions.sql
-- Description: W-92 seed payroll.lwf.read and payroll.lwf.manage actions and grant to platform-admin, tenant-admin, payroll-officer, finance

-- 1. Seed reference.action
INSERT INTO reference.action (code, name, module, description) VALUES
    ('payroll.lwf.read', 'View tenant LWF settings and return statements', 'payroll', 'View state LWF compliance settings and return reports'),
    ('payroll.lwf.manage', 'Configure tenant LWF registrations and state enablement', 'payroll', 'Configure state LWF registration numbers and enable/disable states')
ON CONFLICT (code) DO NOTHING;

-- 2. Grant function for tenant roles
CREATE OR REPLACE FUNCTION core.grant_lwf_actions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF p_tenant_id = '00000000-0000-0000-0000-000000000001'::uuid THEN
        RETURN;
    END IF;

    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('platform-admin', 'payroll.lwf.read'),
        ('platform-admin', 'payroll.lwf.manage'),
        ('tenant-admin', 'payroll.lwf.read'),
        ('tenant-admin', 'payroll.lwf.manage'),
        ('payroll-officer', 'payroll.lwf.read'),
        ('payroll-officer', 'payroll.lwf.manage'),
        ('finance', 'payroll.lwf.read')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_lwf_actions(UUID) FROM PUBLIC;

-- 3. Trigger for new tenants
CREATE OR REPLACE FUNCTION core.tenant_grant_lwf_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_lwf_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_lwf_actions() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_system_roles_lwf ON core.tenant;
CREATE TRIGGER tenant_seed_system_roles_lwf
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_lwf_actions();

-- 4. Backfill existing non-platform tenants
SELECT core.grant_lwf_actions(t.tenant_id) FROM core.tenant t;

