-- Migration: V177__payroll_officer_permissions.sql
-- Schema: core
-- Purpose: Certification S-05 & M-15 — grant core permissions to payroll-officer and hr roles:
--   1. payroll-officer: core.document.read, core.document.upload (prior payroll CSV upload, S-05 / Defect #1)
--   2. payroll-officer: core.pay_input.read, core.pay_input.write (off-cycle bonus & pay inputs, M-15 / Defect #8)
--   3. hr: core.leave.manage (record leave on behalf of employees, L-08)
--
-- Same pattern as V170–V176: a grant function, an AFTER INSERT trigger on core.tenant for future
-- tenants, and a backfill for existing tenants. Platform tenant is skipped (D-33, V158).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.grant_payroll_officer_and_hr_actions(p_tenant_id UUID)
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
        ('payroll-officer', 'core.document.read'),
        ('payroll-officer', 'core.document.upload'),
        ('payroll-officer', 'core.pay_input.read'),
        ('payroll-officer', 'core.pay_input.write'),
        ('hr', 'core.leave.manage')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_payroll_officer_and_hr_actions(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_grant_payroll_officer_and_hr_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_payroll_officer_and_hr_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_payroll_officer_and_hr_actions() FROM PUBLIC;

CREATE TRIGGER tenant_seed_system_roles_payroll_officer_permissions
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_payroll_officer_and_hr_actions();

-- Backfill every existing tenant (the function skips the platform tenant itself).
SELECT core.grant_payroll_officer_and_hr_actions(t.tenant_id) FROM core.tenant t;
