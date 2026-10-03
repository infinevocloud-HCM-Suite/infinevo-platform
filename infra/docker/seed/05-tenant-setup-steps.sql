-- Seed: setup checklist rows for the two dev tenants (W-24.1).
--
-- Local development only. Not a Flyway migration, and never run against a deployed environment.
--
-- A tenant provisioned through POST /api/v1/tenants gets its checklist assembled on the spot
-- (TenantServiceImpl), and the first GET /api/v1/setup-checklist assembles one for any tenant
-- that lacks it. These two tenants are created by 01-tenants.sql and 04-subscriptions.sql, which
-- do neither, so without this file core.tenant_setup_step stays empty until someone calls the
-- API — and the spec's verification query (W-24.1 §8) expects Acme's payroll steps to be there.
--
-- The rows mirror SetupStepCatalogue.DEFAULT_STEPS for each tenant's modules: the two core steps
-- for everyone, the payroll steps for PAYROLL holders. Both tenants hold PAYROLL
-- (04-subscriptions.sql) and there are no HRMS steps yet, so both get the same seven.
-- SeedSetupStepsMatchCatalogueTest (core) fails the build if this file and the catalogue drift.
--
-- Idempotent: the unique index on (tenant_id, step_code) (V035__tenant_setup_step.sql) makes a
-- re-run insert nothing, and completion is never seeded — it is detected when the checklist is
-- read.

INSERT INTO core.tenant_setup_step (tenant_id, step_code, module, display_order, created_by, updated_by)
VALUES
    -- Acme Manufacturing: PAYROLL only
    ('11111111-1111-1111-1111-111111111111', 'WORK_LOCATION', NULL, 1, 'seed', 'seed'),
    ('11111111-1111-1111-1111-111111111111', 'EMPLOYEE', NULL, 2, 'seed', 'seed'),
    ('11111111-1111-1111-1111-111111111111', 'PAY_SCHEDULE', 'PAYROLL', 3, 'seed', 'seed'),
    ('11111111-1111-1111-1111-111111111111', 'PRIOR_PAYROLL', 'PAYROLL', 4, 'seed', 'seed'),
    ('11111111-1111-1111-1111-111111111111', 'SALARY_COMPONENTS', 'PAYROLL', 6, 'seed', 'seed'),
    ('11111111-1111-1111-1111-111111111111', 'EPF', 'PAYROLL', 7, 'seed', 'seed'),
    ('11111111-1111-1111-1111-111111111111', 'ESI', 'PAYROLL', 8, 'seed', 'seed'),
    ('11111111-1111-1111-1111-111111111111', 'PROFESSIONAL_TAX', 'PAYROLL', 9, 'seed', 'seed'),
    -- Globex Corporation: HRMS and PAYROLL
    ('22222222-2222-2222-2222-222222222222', 'WORK_LOCATION', NULL, 1, 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222222', 'EMPLOYEE', NULL, 2, 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222222', 'PAY_SCHEDULE', 'PAYROLL', 3, 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222222', 'PRIOR_PAYROLL', 'PAYROLL', 4, 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222222', 'SALARY_COMPONENTS', 'PAYROLL', 6, 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222222', 'EPF', 'PAYROLL', 7, 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222222', 'ESI', 'PAYROLL', 8, 'seed', 'seed'),
    ('22222222-2222-2222-2222-222222222222', 'PROFESSIONAL_TAX', 'PAYROLL', 9, 'seed', 'seed')
ON CONFLICT (tenant_id, step_code) DO NOTHING;

SELECT format('seed 05-tenant-setup-steps: %s setup step row(s) present', count(*)) AS status
FROM core.tenant_setup_step;
