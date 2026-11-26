-- Migration: V236__surveys_permissions.sql
-- Description: W-110 Surveys permissions & role grants (hrms.survey.read, hrms.survey.manage, hrms.survey.respond)

-- 1. Seed reference.action catalogue
INSERT INTO reference.action (code, name, module, description) VALUES
    ('hrms.survey.read', 'View surveys and survey analytics results', 'hrms', 'View surveys, participant counts, and aggregated analytics results'),
    ('hrms.survey.manage', 'Create, edit, publish, and close surveys', 'hrms', 'Build question workflows, publish surveys, and manage survey lifecycles'),
    ('hrms.survey.respond', 'Submit responses to assigned surveys via employee portal', 'hrms', 'Access targeted active surveys and submit survey feedback')
ON CONFLICT (code) DO NOTHING;

-- 2. Grant function for tenant roles
CREATE OR REPLACE FUNCTION core.grant_survey_actions(p_tenant_id UUID)
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
        ('platform-admin', 'hrms.survey.read'),
        ('platform-admin', 'hrms.survey.manage'),
        ('platform-admin', 'hrms.survey.respond'),

        ('tenant-admin', 'hrms.survey.read'),
        ('tenant-admin', 'hrms.survey.manage'),
        ('tenant-admin', 'hrms.survey.respond'),

        ('hr', 'hrms.survey.read'),
        ('hr', 'hrms.survey.manage'),
        ('hr', 'hrms.survey.respond'),

        ('manager', 'hrms.survey.read'),
        ('manager', 'hrms.survey.respond'),

        ('employee', 'hrms.survey.respond')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_survey_actions(UUID) FROM PUBLIC;

-- 3. Trigger for new tenants
CREATE OR REPLACE FUNCTION core.tenant_grant_survey_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_survey_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_survey_actions() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_system_roles_surveys ON core.tenant;
CREATE TRIGGER tenant_seed_system_roles_surveys
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_survey_actions();

-- 4. Backfill existing non-platform tenants
SELECT core.grant_survey_actions(t.tenant_id) FROM core.tenant t;

