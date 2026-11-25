-- Migration: V234__hiring_permissions.sql
-- Description: W-112 Hiring permissions & role grants (hrms.hiring.read, hrms.hiring.manage, hrms.hiring.interview)

-- 1. Seed reference.action catalogue
INSERT INTO reference.action (code, name, module, description) VALUES
    ('hrms.hiring.read', 'View job openings, candidates, and applications', 'hrms', 'View job openings, pipeline status, candidate profiles, and offers'),
    ('hrms.hiring.manage', 'Manage requisitions, candidate pipeline, and issue offers', 'hrms', 'Create and edit job openings, move application stages, schedule interviews, and issue job offers'),
    ('hrms.hiring.interview', 'Conduct scheduled interviews and submit scorecards', 'hrms', 'Access assigned candidate interviews and submit evaluation scorecards')
ON CONFLICT (code) DO NOTHING;

-- 2. Grant function for tenant roles
CREATE OR REPLACE FUNCTION core.grant_hiring_actions(p_tenant_id UUID)
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
        ('platform-admin', 'hrms.hiring.read'),
        ('platform-admin', 'hrms.hiring.manage'),
        ('platform-admin', 'hrms.hiring.interview'),

        ('tenant-admin', 'hrms.hiring.read'),
        ('tenant-admin', 'hrms.hiring.manage'),
        ('tenant-admin', 'hrms.hiring.interview'),

        ('hr', 'hrms.hiring.read'),
        ('hr', 'hrms.hiring.manage'),
        ('hr', 'hrms.hiring.interview'),

        ('manager', 'hrms.hiring.read'),
        ('manager', 'hrms.hiring.interview'),

        ('employee', 'hrms.hiring.interview')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_hiring_actions(UUID) FROM PUBLIC;

-- 3. Trigger for new tenants
CREATE OR REPLACE FUNCTION core.tenant_grant_hiring_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_hiring_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_hiring_actions() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_system_roles_hiring ON core.tenant;
CREATE TRIGGER tenant_seed_system_roles_hiring
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_hiring_actions();

-- 4. Backfill existing non-platform tenants
SELECT core.grant_hiring_actions(t.tenant_id) FROM core.tenant t;

