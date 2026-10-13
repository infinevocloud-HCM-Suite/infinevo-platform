-- Migration: V174__finance_approval_actions.sql
-- Schema: core
-- Purpose: D-14 — grant core.approval.decide to the finance role, so a finance user sees Approvals
-- (NavigationCatalogue core.approvals needs it) and can decide the claims and deductions routed to
-- finance. The same grant hr and manager already hold (V158); not read or delegate, which neither has.
--
-- Replaces the branch's V149, which re-created core.seed_system_roles from a snapshot taken before
-- V158 and would have been refused by Flyway as out of order on every deployed database. Same
-- pattern as V170–V172: a grant function, an AFTER INSERT trigger on core.tenant for future tenants,
-- a backfill for existing ones. Platform tenant skipped (D-33, V158). Trigger name sorts after
-- tenant_seed_system_roles so the roles exist when it fires.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.grant_approval_actions_to_finance(p_tenant_id UUID)
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
    SELECT p_tenant_id, r.id, v.action_code
    FROM core.role r
    CROSS JOIN (VALUES ('core.approval.decide')) AS v(action_code)
    WHERE r.tenant_id = p_tenant_id
      AND r.code = 'finance'
      AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_approval_actions_to_finance(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_grant_approval_actions_to_finance()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_approval_actions_to_finance(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_approval_actions_to_finance() FROM PUBLIC;

CREATE TRIGGER tenant_seed_system_roles_finance_approval
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_approval_actions_to_finance();

-- Backfill all existing non-platform tenants
SELECT core.grant_approval_actions_to_finance(t.tenant_id) FROM core.tenant t;
