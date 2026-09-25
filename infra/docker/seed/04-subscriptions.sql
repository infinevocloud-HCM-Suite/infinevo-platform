-- Seed: subscriptions and module selection for dev tenants (W-12.1).
--
-- Local development only. Not a Flyway migration, and never run against a deployed environment.
--
-- Establishes the asymmetry 01-tenants.sql:16-21 names:
-- - Acme Manufacturing (11111111-1111-1111-1111-111111111111) holds PAYROLL only.
-- - Globex Corporation (22222222-2222-2222-2222-222222222222) holds HRMS and PAYROLL.
--
-- Idempotent: re-running changes nothing.

-- 1. Subscriptions
INSERT INTO core.subscription (id, tenant_id, status, started_on, created_by, updated_by)
VALUES
    ('11111111-1111-1111-1111-111111111112', '11111111-1111-1111-1111-111111111111', 'ACTIVE', CURRENT_DATE, 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222223', '22222222-2222-2222-2222-222222222222', 'ACTIVE', CURRENT_DATE, 'seed', 'seed')
ON CONFLICT (tenant_id) DO NOTHING;

-- 2. Modules
-- Acme: PAYROLL only
INSERT INTO core.subscription_module (id, tenant_id, subscription_id, module, granted_on, created_by, updated_by)
VALUES
    ('11111111-1111-1111-1111-111111111113', '11111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111112', 'PAYROLL', CURRENT_DATE, 'seed', 'seed')
ON CONFLICT (tenant_id, module) WHERE revoked_on IS NULL DO NOTHING;

-- Globex: HRMS and PAYROLL
INSERT INTO core.subscription_module (id, tenant_id, subscription_id, module, granted_on, created_by, updated_by)
VALUES
    ('22222222-2222-2222-2222-222222222224', '22222222-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222223', 'HRMS', CURRENT_DATE, 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222225', '22222222-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222223', 'PAYROLL', CURRENT_DATE, 'seed', 'seed')
ON CONFLICT (tenant_id, module) WHERE revoked_on IS NULL DO NOTHING;

SELECT format('seed 04-subscriptions: %s subscription(s), %s module(s) present',
    (SELECT count(*) FROM core.subscription),
    (SELECT count(*) FROM core.subscription_module)) AS status;
