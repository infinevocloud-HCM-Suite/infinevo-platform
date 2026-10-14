-- Migration: V175__certification_role_grants.sql
-- Schema: core
-- Purpose: Grant certification-required actions to hr and manager roles:
--   hr:      core.role.assign (K-06: invite/assign roles to employees)
--            core.document.delete (H-02: delete uploaded employee documents)
--            core.leave.manage (L-08: record leave on behalf of employees)
--            core.approval.delegate (delegate approval authority)
--   manager: core.approval.delegate (L-10: delegate approval authority)
--
-- Same pattern as V170–V174: an idempotent grant function, an AFTER INSERT trigger on
-- core.tenant for future tenants, and a backfill for existing ones. Platform tenant is skipped.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.grant_certification_actions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF p_tenant_id = '00000000-0000-0000-0000-000000000001'::uuid THEN
        RETURN;
    END IF;

    -- Grants to HR role
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, v.action_code
    FROM core.role r
    CROSS JOIN (VALUES
        ('core.role.assign'),
        ('core.document.delete'),
        ('core.leave.manage'),
        ('core.approval.delegate')
    ) AS v(action_code)
    WHERE r.tenant_id = p_tenant_id
      AND r.code = 'hr'
      AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;

    -- Grants to Manager role
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, v.action_code
    FROM core.role r
    CROSS JOIN (VALUES
        ('core.approval.delegate')
    ) AS v(action_code)
    WHERE r.tenant_id = p_tenant_id
      AND r.code = 'manager'
      AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_certification_actions(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_grant_certification_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_certification_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_certification_actions() FROM PUBLIC;

CREATE TRIGGER tenant_seed_system_roles_certification
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_certification_actions();

-- Backfill all existing non-platform tenants
SELECT core.grant_certification_actions(t.tenant_id) FROM core.tenant t;
