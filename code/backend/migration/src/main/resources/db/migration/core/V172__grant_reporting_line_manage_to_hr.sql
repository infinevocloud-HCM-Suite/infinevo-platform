-- Migration: V172__grant_reporting_line_manage_to_hr.sql
-- Schema: core
-- Purpose: Grant core.reporting_line.manage to hr role so HR can manage reporting lines & assign managers.
--
-- The ReportingLineController and ReportingLineTab UI require core.reporting_line.manage.
-- Previously only tenant-admin held it. This grants it to the hr role for all existing and future tenants.
-- Platform tenant is skipped (D-33, V158).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.grant_reporting_line_manage_to_hr(p_tenant_id UUID)
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
    SELECT p_tenant_id, r.id, 'core.reporting_line.manage'
    FROM core.role r
    WHERE r.tenant_id = p_tenant_id
      AND r.code = 'hr'
      AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_reporting_line_manage_to_hr(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_grant_reporting_line_manage_to_hr()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_reporting_line_manage_to_hr(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_reporting_line_manage_to_hr() FROM PUBLIC;

CREATE TRIGGER tenant_seed_system_roles_reporting_line
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_reporting_line_manage_to_hr();

-- Backfill all existing non-platform tenants
SELECT core.grant_reporting_line_manage_to_hr(t.tenant_id) FROM core.tenant t;
