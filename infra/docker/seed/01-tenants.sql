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
-- What was missing and is now established in 04-subscriptions.sql: the module asymmetry.
-- Acme holds PAYROLL alone while Globex holds both HRMS and PAYROLL, so an entitlement
-- bug shows up locally rather than at a customer (W-12.1).

INSERT INTO core.tenant (tenant_id, name, created_by, updated_by) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Acme Manufacturing', 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222222', 'Globex Corporation', 'seed', 'seed')
ON CONFLICT (tenant_id) DO NOTHING;

SELECT format('seed 01-tenants: %s tenant(s) present', count(*)) AS status FROM core.tenant;
