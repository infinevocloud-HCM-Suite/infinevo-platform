-- Migration: V238__asset_permissions.sql
-- Description: W-111 Asset Tracking permissions & role grants (hrms.asset.read, hrms.asset.manage, hrms.asset.read_own)

-- 1. Seed reference.action catalogue
INSERT INTO reference.action (code, name, module, description) VALUES
    ('hrms.asset.read', 'View company asset registry and assignment custody', 'hrms', 'View company assets, assignment history, and exit clearance status'),
    ('hrms.asset.manage', 'Register, update, assign, and accept return of company assets', 'hrms', 'Manage asset inventory, handovers to employees, and return processing'),
    ('hrms.asset.read_own', 'View company assets assigned to signed-in employee via portal', 'hrms', 'Access assigned company hardware and equipment in self-service portal')
ON CONFLICT (code) DO NOTHING;

-- 2. Grant function for tenant roles
CREATE OR REPLACE FUNCTION core.grant_asset_actions(p_tenant_id UUID)
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
        ('platform-admin', 'hrms.asset.read'),
        ('platform-admin', 'hrms.asset.manage'),
        ('platform-admin', 'hrms.asset.read_own'),

        ('tenant-admin', 'hrms.asset.read'),
        ('tenant-admin', 'hrms.asset.manage'),
        ('tenant-admin', 'hrms.asset.read_own'),

        ('hr', 'hrms.asset.read'),
        ('hr', 'hrms.asset.manage'),
        ('hr', 'hrms.asset.read_own'),

        ('manager', 'hrms.asset.read'),
        ('manager', 'hrms.asset.read_own'),

        ('employee', 'hrms.asset.read_own')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_asset_actions(UUID) FROM PUBLIC;

-- 3. Trigger for new tenants
CREATE OR REPLACE FUNCTION core.tenant_grant_asset_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_asset_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_asset_actions() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_system_roles_asset ON core.tenant;
CREATE TRIGGER tenant_seed_system_roles_asset
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_asset_actions();

-- 4. Backfill existing non-platform tenants
SELECT core.grant_asset_actions(t.tenant_id) FROM core.tenant t;

