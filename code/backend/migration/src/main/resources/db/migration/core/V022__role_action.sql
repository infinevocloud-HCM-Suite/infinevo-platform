-- Migration: V022__role_action.sql
-- Description: W-11.1 core.role_action — the actions a role holds, with row-level security isolation,
-- and the seeding of the seven system roles for every tenant

CREATE TABLE core.role_action (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    role_id UUID NOT NULL,
    action_code VARCHAR(64) NOT NULL REFERENCES reference.action(code),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    -- Same-tenant by construction: the role must belong to the tenant this row is in.
    CONSTRAINT role_action_role_fkey FOREIGN KEY (tenant_id, role_id) REFERENCES core.role (tenant_id, id)
);

-- action_code references the catalogue across schemas (spec §6), schema-qualified on both sides
-- (migration/README.md §always name the schema). A role can therefore never hold an action the
-- code does not define — the frozen Payroll invented actions at runtime (ActionServiceImpl.java:24-34).

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- Also serves the (tenant_id, role_id) lookup, "what does this role hold", as its leading prefix.
CREATE UNIQUE INDEX idx_role_action_tenant_role_action ON core.role_action (tenant_id, role_id, action_code);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.role_action ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.role_action
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- ─────────────────────────────────────────────────────────────────────────────
-- The seven system roles (spec §13 decision 2)
--
-- Least privilege, and additive: a user holds several roles, and every member of staff also
-- holds 'employee'. So the functional roles (hr, manager, payroll-officer, finance) do not
-- repeat the self-service actions — an HR officer applies for leave as an employee.
--
--   platform-admin   every action
--   tenant-admin     every action except core.tenant.provision, which is platform staff only
--   hr               employee records and sections, org masters, audit, all of leave,
--                    attendance, timesheets and holidays
--   manager          the team: records, leave, attendance and timesheets of reports, approvals
--   payroll-officer  prepares payroll: settings, structures, salaries, runs, payslips, tax
--                    declarations, statutory returns, exports. Does not approve a run
--   finance          reads payroll and approves the run — the checker to payroll-officer's maker
--   employee         self-service: own profile, leave, attendance, timesheet, payslip, tax
--
-- SECURITY DEFINER because it writes core.role and core.role_action under RLS for a tenant that
-- no session is bound to yet; it runs as the owner (migration_user), which bypasses RLS. The
-- search_path is pinned and every name is qualified, so a caller cannot redirect it.
--
-- Idempotent: ON CONFLICT DO NOTHING on both inserts. Re-running it after a release adds an
-- action gives the two admin roles the new action and changes nothing else. A tenant's own role
-- that happens to use a system code is never touched — the join below requires is_system.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.seed_system_roles(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    INSERT INTO core.role (tenant_id, code, name, is_system)
    VALUES
        (p_tenant_id, 'platform-admin', 'Platform administrator', true),
        (p_tenant_id, 'tenant-admin', 'Tenant administrator', true),
        (p_tenant_id, 'hr', 'HR', true),
        (p_tenant_id, 'manager', 'Manager', true),
        (p_tenant_id, 'payroll-officer', 'Payroll officer', true),
        (p_tenant_id, 'finance', 'Finance', true),
        (p_tenant_id, 'employee', 'Employee', true)
    ON CONFLICT (tenant_id, code) DO NOTHING;

    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (
        SELECT 'platform-admin'::VARCHAR AS role_code, a.code AS action_code
        FROM reference.action a
        UNION ALL
        SELECT 'tenant-admin', a.code
        FROM reference.action a
        WHERE a.code <> 'core.tenant.provision'
        UNION ALL
        SELECT v.role_code, v.action_code
        FROM (VALUES
            ('hr', 'core.tenant.read'),
            ('hr', 'core.user.read'),
            ('hr', 'core.role.read'),
            ('hr', 'core.employee.read'),
            ('hr', 'core.employee.create'),
            ('hr', 'core.employee.update'),
            ('hr', 'core.employee.delete'),
            ('hr', 'core.employee.export'),
            ('hr', 'core.employee_identification.read'),
            ('hr', 'core.employee_identification.update'),
            ('hr', 'core.employee_bank.read'),
            ('hr', 'core.employee_bank.update'),
            ('hr', 'core.org.read'),
            ('hr', 'core.org.manage'),
            ('hr', 'core.audit.read'),
            ('hr', 'hrms.leave.read'),
            ('hr', 'hrms.leave.approve'),
            ('hr', 'hrms.leave_type.manage'),
            ('hr', 'hrms.leave_balance.manage'),
            ('hr', 'hrms.attendance.read'),
            ('hr', 'hrms.attendance.manage'),
            ('hr', 'hrms.attendance.export'),
            ('hr', 'hrms.timesheet.read'),
            ('hr', 'hrms.timesheet.approve'),
            ('hr', 'hrms.timesheet.export'),
            ('hr', 'hrms.holiday.read'),
            ('hr', 'hrms.holiday.manage'),

            ('manager', 'core.employee.read_team'),
            ('manager', 'core.org.read'),
            ('manager', 'hrms.leave.read_team'),
            ('manager', 'hrms.leave.approve'),
            ('manager', 'hrms.attendance.read_team'),
            ('manager', 'hrms.timesheet.read_team'),
            ('manager', 'hrms.timesheet.approve'),
            ('manager', 'hrms.holiday.read'),

            ('payroll-officer', 'core.employee.read'),
            ('payroll-officer', 'core.employee_identification.read'),
            ('payroll-officer', 'core.employee_bank.read'),
            ('payroll-officer', 'core.org.read'),
            ('payroll-officer', 'hrms.attendance.read'),
            ('payroll-officer', 'hrms.leave.read'),
            ('payroll-officer', 'payroll.settings.manage'),
            ('payroll-officer', 'payroll.structure.read'),
            ('payroll-officer', 'payroll.structure.manage'),
            ('payroll-officer', 'payroll.salary.read'),
            ('payroll-officer', 'payroll.salary.manage'),
            ('payroll-officer', 'payroll.run.read'),
            ('payroll-officer', 'payroll.run.execute'),
            ('payroll-officer', 'payroll.payslip.read'),
            ('payroll-officer', 'payroll.payslip.publish'),
            ('payroll-officer', 'payroll.tax_declaration.read'),
            ('payroll-officer', 'payroll.tax_declaration.verify'),
            ('payroll-officer', 'payroll.statutory_report.read'),
            ('payroll-officer', 'payroll.statutory_report.generate'),
            ('payroll-officer', 'payroll.bank_file.export'),
            ('payroll-officer', 'payroll.report.export'),

            ('finance', 'core.org.read'),
            ('finance', 'payroll.structure.read'),
            ('finance', 'payroll.salary.read'),
            ('finance', 'payroll.run.read'),
            ('finance', 'payroll.run.approve'),
            ('finance', 'payroll.payslip.read'),
            ('finance', 'payroll.statutory_report.read'),
            ('finance', 'payroll.bank_file.export'),
            ('finance', 'payroll.report.export'),

            ('employee', 'core.employee.read_own'),
            ('employee', 'core.employee.update_own'),
            ('employee', 'core.org.read'),
            ('employee', 'hrms.leave.apply'),
            ('employee', 'hrms.leave.read_own'),
            ('employee', 'hrms.attendance.mark'),
            ('employee', 'hrms.attendance.read_own'),
            ('employee', 'hrms.timesheet.submit'),
            ('employee', 'hrms.timesheet.read_own'),
            ('employee', 'hrms.holiday.read'),
            ('employee', 'payroll.payslip.read_own'),
            ('employee', 'payroll.tax_declaration.submit'),
            ('employee', 'payroll.tax_declaration.read_own')
        ) AS v (role_code, action_code)
    ) AS grants
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

-- Owner-only. Nothing but the trigger below calls it; the application never seeds roles itself.
REVOKE EXECUTE ON FUNCTION core.seed_system_roles(UUID) FROM PUBLIC;

-- Why a trigger and not application code: no tenant-creation code path exists yet — tenants are
-- inserted by the dev seed, by tests and, later, by a provisioning flow nobody has written. A
-- trigger on core.tenant seeds every tenant whatever creates it, so there is no path that
-- produces a tenant with no roles and therefore nobody able to administer it.
CREATE OR REPLACE FUNCTION core.tenant_seed_system_roles()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.seed_system_roles(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_seed_system_roles() FROM PUBLIC;

CREATE TRIGGER tenant_seed_system_roles
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_seed_system_roles();

-- Backfill: tenants that existed before this script get the same seven.
SELECT core.seed_system_roles(t.tenant_id) FROM core.tenant t;
