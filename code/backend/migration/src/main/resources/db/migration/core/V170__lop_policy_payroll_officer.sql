-- Migration: V170__lop_policy_payroll_officer.sql
-- Schema: core
-- Purpose: D-68 — the payroll officer reads and saves the loss-of-pay policy.
--
-- LopPolicyController guards GET with core.lop_policy.read and PUT with core.lop_policy.manage (V025), and only
-- the admin roles held them, so the officer who runs payroll got 403 on the screen that decides what a day of
-- pay is worth.
--
-- A function and a trigger of their own rather than a replacement of core.seed_system_roles, the pattern of
-- V059: every lane that rewrites that function has to carry every other lane's grants.
-- tenant_seed_system_roles_lop_policy sorts after tenant_seed_system_roles (V022), so the roles exist when this
-- runs. Only system roles are granted, as V022 does. The platform tenant is skipped (D-33, V158): its roles hold
-- platform actions only, and the V158 trigger would refuse the insert.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.grant_lop_policy_actions(p_tenant_id UUID)
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
        ('payroll-officer', 'core.lop_policy.read'),
        ('payroll-officer', 'core.lop_policy.manage')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_lop_policy_actions(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_grant_lop_policy_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_lop_policy_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_lop_policy_actions() FROM PUBLIC;

CREATE TRIGGER tenant_seed_system_roles_lop_policy
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_lop_policy_actions();

-- Tenants that exist before this script ran get the grants too.
SELECT core.grant_lop_policy_actions(t.tenant_id) FROM core.tenant t;
