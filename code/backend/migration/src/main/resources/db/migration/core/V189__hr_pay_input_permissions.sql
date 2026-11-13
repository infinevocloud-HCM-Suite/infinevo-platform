-- Migration: V189__hr_pay_input_permissions.sql
-- Schema: core
-- Purpose: D-140 — HR enters loss-of-pay days by hand through the pay input ledger
--   (POST /api/v1/pay-inputs, kind LOP_DAYS), so hr holds core.pay_input.read and core.pay_input.write.
--
-- Same pattern as V177: a grant function, an AFTER INSERT trigger on core.tenant for future
-- tenants, and a backfill for existing tenants. Platform tenant is skipped (D-33, V158).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.grant_hr_pay_input_actions(p_tenant_id UUID)
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
        ('hr', 'core.pay_input.read'),
        ('hr', 'core.pay_input.write')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_hr_pay_input_actions(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_grant_hr_pay_input_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_hr_pay_input_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_hr_pay_input_actions() FROM PUBLIC;

CREATE TRIGGER tenant_seed_system_roles_hr_pay_input_permissions
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_hr_pay_input_actions();

-- Backfill every existing tenant (the function skips the platform tenant itself).
SELECT core.grant_hr_pay_input_actions(t.tenant_id) FROM core.tenant t;
