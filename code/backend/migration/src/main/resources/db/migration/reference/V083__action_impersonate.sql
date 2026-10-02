-- Migration: V083__action_impersonate.sql
-- Schema: reference
-- Purpose: Impersonate action code and platform tenant grant (W-65.2)

-- 1. Insert action in reference.action
INSERT INTO reference.action (code, name, module, description) VALUES
    ('core.tenant.impersonate', 'Impersonate tenant user', 'core',
     'Act as a user of a customer tenant. Platform staff only')
ON CONFLICT (code) DO NOTHING;

-- 2. Only platform-admin in the Infinevo platform tenant may ever hold core.tenant.impersonate.
--    core.seed_system_roles grants every catalogue code but core.tenant.provision to platform-admin
--    and tenant-admin in every tenant, and V085 backfills every existing tenant with it, so without
--    this each customer tenant-admin (and every bootstrap session, which acts with tenant-admin's
--    actions) would hold the platform-only action. The row is dropped rather than refused so that
--    seeding a tenant still succeeds, and the rule holds for any later rewrite of the seed function.
--    Created before step 3, whose seed call would otherwise grant it to the platform tenant-admin.
CREATE OR REPLACE FUNCTION core.restrict_impersonate_grant()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF NEW.action_code = 'core.tenant.impersonate'
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
        RETURN NULL;
    END IF;
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.restrict_impersonate_grant() FROM PUBLIC;

CREATE TRIGGER role_action_restrict_impersonate
    BEFORE INSERT OR UPDATE ON core.role_action
    FOR EACH ROW
    EXECUTE FUNCTION core.restrict_impersonate_grant();

-- 3. Grant to platform-admin in the Infinevo platform tenant
SELECT core.seed_system_roles('00000000-0000-0000-0000-000000000001');

INSERT INTO core.role_action (tenant_id, role_id, action_code)
SELECT r.tenant_id, r.id, 'core.tenant.impersonate'
FROM core.role r
WHERE r.tenant_id = '00000000-0000-0000-0000-000000000001'
  AND r.code = 'platform-admin'
ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
