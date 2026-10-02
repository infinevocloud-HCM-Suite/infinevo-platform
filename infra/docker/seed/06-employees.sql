-- Seed: an employee record for each of the two dev employee logins.
--
-- Local development only. Not a Flyway migration, and never run against a deployed
-- environment — seed.sh says the same thing and is the only thing that runs this file.
--
-- Why this file exists: the self-service portal (W-25) resolves the caller through
-- core.employee.user_account_id (V026). A login with no linked employee has no portal:
-- GET /api/v1/me/panels answers [] and W-25 §8 cannot pass. In a real tenant the link is
-- made by PUT /api/v1/employees/{id}/login or by accepting an invitation (W-24.2); locally,
-- this does it.
--
--   employee.acme     -> ACME-0001 in Acme Manufacturing
--   employee.globex   -> GLBX-0001 in Globex Corporation
--
-- The two admins get no employee record on purpose: a login without one is the case the
-- /me endpoints must answer cleanly, and it needs someone to exercise it.
--
-- The keycloak ids are the ones pinned in infra/docker/keycloak/dev-realm.json,
-- 02-user-tenants.sql and 03-user-roles.sql — edit all four together. Runs after
-- 03-user-roles.sql, which creates the core.user_account rows this joins to.

INSERT INTO core.employee
    (tenant_id, employee_number, first_name, last_name, date_of_joining, status, work_email,
     user_account_id, created_by, updated_by)
SELECT ua.tenant_id, e.employee_number, e.first_name, e.last_name, e.date_of_joining, 'ACTIVE', ua.email,
       ua.id, 'seed', 'seed'
FROM (VALUES
        ('11111111-1111-1111-1111-111111111111'::uuid, 'a0000000-0000-0000-0000-000000000002'::uuid,
         'ACME-0001', 'Asha', 'Employee', DATE '2024-04-01'),
        ('22222222-2222-2222-2222-222222222222'::uuid, 'b0000000-0000-0000-0000-000000000002'::uuid,
         'GLBX-0001', 'Gautam', 'Employee', DATE '2024-04-01')
     ) AS e (tenant_id, keycloak_user_id, employee_number, first_name, last_name, date_of_joining)
JOIN core.user_account ua ON ua.tenant_id = e.tenant_id AND ua.keycloak_user_id = e.keycloak_user_id
ON CONFLICT (tenant_id, employee_number) DO NOTHING;

SELECT format('seed 06-employees: %s employee(s) linked to a login', count(*)) AS status
FROM core.employee
WHERE user_account_id IS NOT NULL;
