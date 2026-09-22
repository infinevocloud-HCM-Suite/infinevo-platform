-- Seed: two tenants.
--
-- Local development only. Not a Flyway migration, and never run against a deployed
-- environment — seed.sh says the same thing and is the only thing that runs this file.
--
-- Idempotent: re-running changes nothing, because tenant_id carries a unique index
-- (V001__tenant.sql) and both inserts are ON CONFLICT DO NOTHING. The UUIDs are fixed
-- rather than generated so that a developer can hardcode one in a request, restart the
-- stack, and still be talking about the same tenant.
--
-- The point of seeding TWO is that one tenant hides every isolation bug there is. With a
-- single tenant, a query that forgot its tenant filter returns exactly the right rows, an
-- RLS policy that never matches looks identical to one that always matches, and both pass
-- review. Do not "simplify" this by seeding one.
--
-- What is still missing: the module asymmetry. The original intent was acme holding
-- payroll alone while globex holds both, so that an entitlement bug shows up locally
-- rather than at a customer. That needs core.subscription, which W-12 creates — it does
-- not exist yet, and neither does the `slug` or `status` column this file used to name in
-- its commented-out draft. Until W-12 lands both tenants are indistinguishable in what
-- they have bought, and an entitlement check has nothing to fail against.

INSERT INTO core.tenant (tenant_id, name, created_by, updated_by) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Acme Manufacturing', 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222222', 'Globex Corporation', 'seed', 'seed')
ON CONFLICT (tenant_id) DO NOTHING;

SELECT format('seed 01-tenants: %s tenant(s) present', count(*)) AS status FROM core.tenant;
