-- Migration: V171__employee_read_own_all_roles.sql
-- Schema: core
-- Purpose: D-75 — "My self-service" and "My profile" show for anyone with a linked employee record, not only for
-- holders of the employee role (founder decision, 2026-10-09).
--
-- The portal menu item and GET /api/v1/me/panels (PortalController) are guarded by core.employee.read_own, and V158
-- granted it to the employee role and the admins alone. An hr, manager, payroll officer or finance user who is also
-- on the payroll of the tenant therefore saw neither entry and would have got 403 on the panels.
-- core.employee.read_own reads the caller's own row and nothing else, so granting it widely gives nothing away;
-- whether a portal item shows at all is decided by the linked record (NavigationService), not by this action.
--
-- A function and a trigger of their own rather than a replacement of core.seed_system_roles, the pattern of
-- V059 and V170: every lane that rewrites that function has to carry every other lane's grants.
-- tenant_seed_system_roles_read_own sorts after tenant_seed_system_roles (V022), so the roles exist when this runs.
-- Only system roles are granted, as V022 does. The platform tenant is skipped (D-33, V158): its roles hold
-- platform actions only, and the V158 trigger would refuse the insert.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.grant_employee_read_own_actions(p_tenant_id UUID)
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
        ('hr', 'core.employee.read_own'),
        ('manager', 'core.employee.read_own'),
        ('payroll-officer', 'core.employee.read_own'),
        ('finance', 'core.employee.read_own')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_employee_read_own_actions(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_grant_employee_read_own_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_employee_read_own_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_employee_read_own_actions() FROM PUBLIC;

CREATE TRIGGER tenant_seed_system_roles_read_own
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_employee_read_own_actions();

-- Tenants that exist before this script ran get the grants too.
SELECT core.grant_employee_read_own_actions(t.tenant_id) FROM core.tenant t;
