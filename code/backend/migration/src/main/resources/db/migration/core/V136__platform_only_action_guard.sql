-- Migration: V136__platform_only_action_guard.sql
-- Schema: core
-- Purpose: core.tenant.provision may be held only by platform-admin in the Infinevo platform tenant
--          (W-65.2 review). V083 already does this for core.tenant.impersonate.
--
-- Why. core.tenant.provision changes ANY tenant's modules and status. V025 stopped the seed function granting
-- it to tenant-seeded roles, but nothing stopped a customer's own role from carrying it: RoleService only
-- checks that the code exists. A customer user holding such a role could then call the subscription endpoints
-- against another tenant (the path names the tenant). The application now refuses the grant (RoleServiceImpl)
-- and the subscription endpoints require the platform tenant; this is the same rule where it cannot be skipped.
--
-- Unlike V083 this REFUSES instead of dropping the row: the seed function never grants this code except to the
-- platform tenant's platform-admin (V082), so refusing cannot break seeding, and a refusal tells the caller.

-- 1. Remove any grant that should never have existed. Additive-safe: it removes only what this script forbids.
DELETE FROM core.role_action ra
WHERE ra.action_code = 'core.tenant.provision'
  AND NOT (
      ra.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
      AND EXISTS (
          SELECT 1
          FROM core.role r
          WHERE r.id = ra.role_id
            AND r.tenant_id = ra.tenant_id
            AND r.code = 'platform-admin'
            AND r.is_system
      )
  );

-- 2. Refuse it from now on.
CREATE OR REPLACE FUNCTION core.restrict_provision_grant()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF NEW.action_code = 'core.tenant.provision'
       AND NOT (
           NEW.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
           AND EXISTS (
               SELECT 1
               FROM core.role r
               WHERE r.id = NEW.role_id
                 AND r.tenant_id = NEW.tenant_id
                 AND r.code = 'platform-admin'
                 AND r.is_system
           )
       ) THEN
        RAISE EXCEPTION 'core.tenant.provision may be held only by platform-admin in the platform tenant'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.restrict_provision_grant() FROM PUBLIC;

CREATE TRIGGER role_action_restrict_provision
    BEFORE INSERT OR UPDATE ON core.role_action
    FOR EACH ROW
    EXECUTE FUNCTION core.restrict_provision_grant();
