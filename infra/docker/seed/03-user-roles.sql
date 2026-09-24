-- Seed: the three dev users' roles.
--
-- Local development only. Not a Flyway migration, and never run against a deployed
-- environment — seed.sh says the same thing and is the only thing that runs this file.
--
-- Why this file exists: W-11.2 guards every endpoint with @RequiresAction, and granting a
-- role needs core.role.assign — which only a user who already holds a role can have. With
-- no seeded grant, every local login gets 403 on everything and nothing in the API can
-- fix it. Real tenants get their first admin from provisioning (W-65); locally, this does it.
--
-- Two steps, both idempotent:
--
-- 1. core.user_account rows. Normally UserProfileSyncFilter creates them on first login,
--    keyed by (tenant_id, keycloak_user_id). A grant needs the row to exist before that
--    first login, so it is inserted here with the same key; the filter then finds it and
--    only refreshes the name and email from the token (UserProfileSyncService.sync).
--    The keycloak ids are the ones pinned in infra/docker/keycloak/dev-realm.json and
--    02-user-tenants.sql — edit all three together.
--
-- 2. core.user_role rows, against the system roles the V022 trigger seeded for each tenant:
--
--   admin.acme        -> tenant-admin in Acme Manufacturing
--   admin.globex      -> tenant-admin in Globex Corporation
--   employee.globex   -> employee     in Globex Corporation
--
-- Nobody gets platform-admin: it holds core.tenant.provision, which no customer user may
-- hold, and none of these three is platform staff.

INSERT INTO core.user_account (tenant_id, keycloak_user_id, email, created_by, updated_by) VALUES
    ('11111111-1111-1111-1111-111111111111', 'a0000000-0000-0000-0000-000000000001', 'admin@acme-payroll.local', 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222222', 'b0000000-0000-0000-0000-000000000001', 'admin@globex-full.local', 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222222', 'b0000000-0000-0000-0000-000000000002', 'emp@globex-full.local', 'seed', 'seed')
ON CONFLICT (tenant_id, keycloak_user_id) DO NOTHING;

INSERT INTO core.user_role (tenant_id, user_account_id, role_id, created_by, updated_by)
SELECT ua.tenant_id, ua.id, r.id, 'seed', 'seed'
FROM (VALUES
        ('11111111-1111-1111-1111-111111111111'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'tenant-admin'),
        ('22222222-2222-2222-2222-222222222222'::uuid, 'b0000000-0000-0000-0000-000000000001'::uuid, 'tenant-admin'),
        ('22222222-2222-2222-2222-222222222222'::uuid, 'b0000000-0000-0000-0000-000000000002'::uuid, 'employee')
     ) AS g (tenant_id, keycloak_user_id, role_code)
JOIN core.user_account ua ON ua.tenant_id = g.tenant_id AND ua.keycloak_user_id = g.keycloak_user_id
JOIN core.role r ON r.tenant_id = g.tenant_id AND r.code = g.role_code
ON CONFLICT (tenant_id, user_account_id, role_id) DO NOTHING;

SELECT format('seed 03-user-roles: %s grant(s) present', count(*)) AS status FROM core.user_role;
