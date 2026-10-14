-- Migration: V176__manager_approval_delegate.sql
-- Schema: core
-- Purpose: certification L-10 — grant core.approval.delegate to the manager role. W-15.3 lets an
-- approver hand their steps to someone else for a period (own delegations only), and Delegations.jsx
-- and NavigationCatalogue show the screen only to a holder of this action; no migration had granted it
-- to manager, so a manager saw no Delegations entry and /approvals/delegations read Not Entitled.
-- hr is not granted: the certification step is a manager step, and hr already decides at its own level.
--
-- Same pattern as V170–V174: a grant function, an AFTER INSERT trigger on core.tenant for future
-- tenants, a backfill for existing ones. Platform tenant skipped (D-33, V158). Trigger name sorts after
-- tenant_seed_system_roles so the roles exist when it fires.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.grant_approval_delegate_to_manager(p_tenant_id UUID)
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
    SELECT p_tenant_id, r.id, 'core.approval.delegate'
    FROM core.role r
    WHERE r.tenant_id = p_tenant_id
      AND r.code = 'manager'
      AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_approval_delegate_to_manager(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_grant_approval_delegate_to_manager()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_approval_delegate_to_manager(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_approval_delegate_to_manager() FROM PUBLIC;

CREATE TRIGGER tenant_seed_system_roles_manager_delegate
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_approval_delegate_to_manager();

-- Backfill every existing tenant (the function skips the platform tenant itself).
SELECT core.grant_approval_delegate_to_manager(t.tenant_id) FROM core.tenant t;
