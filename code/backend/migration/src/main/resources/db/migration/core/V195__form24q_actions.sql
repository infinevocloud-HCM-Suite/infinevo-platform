-- Migration: V195__form24q_actions.sql
-- Schema: core
-- Description: W-89 Form 24Q action catalogue entries and role grants for payroll-officer and tenant-admin

-- 1. Actions in reference.action
INSERT INTO reference.action (code, name, module, description)
VALUES
    ('payroll.tax.view',   'View Form 24Q TDS return and challan data', 'payroll', 'View quarterly TDS returns, challans, Annexure I and II statements'),
    ('payroll.tax.manage', 'Manage Form 24Q TDS filings and challans',  'payroll', 'Create, record, allocate and file Form 24Q TDS challans')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

-- 2. Grant function for tenant roles
CREATE OR REPLACE FUNCTION core.grant_form24q_actions(p_tenant_id UUID)
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
        ('platform-admin',  'payroll.tax.view'),
        ('platform-admin',  'payroll.tax.manage'),
        ('tenant-admin',    'payroll.tax.view'),
        ('tenant-admin',    'payroll.tax.manage'),
        ('payroll-officer', 'payroll.tax.view'),
        ('payroll-officer', 'payroll.tax.manage')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_form24q_actions(UUID) FROM PUBLIC;

-- 3. Trigger for new tenants
CREATE OR REPLACE FUNCTION core.tenant_grant_form24q_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_form24q_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_form24q_actions() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_system_roles_form24q_permissions ON core.tenant;
CREATE TRIGGER tenant_seed_system_roles_form24q_permissions
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_form24q_actions();

-- 4. Backfill existing tenants
SELECT core.grant_form24q_actions(t.tenant_id) FROM core.tenant t;
