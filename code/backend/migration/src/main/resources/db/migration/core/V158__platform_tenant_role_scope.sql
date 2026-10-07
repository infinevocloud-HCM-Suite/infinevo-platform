-- Migration: V158__platform_tenant_role_scope.sql
-- Schema: core
-- Purpose: roles in the Infinevo platform tenant hold platform actions only (D-33).
--
-- Why. V082 seeds every system role into the platform tenant through core.seed_system_roles, and that
-- function grants platform-admin (and tenant-admin) every catalogue code. Platform staff therefore held every
-- customer action - employees, leave, payroll - and the navigation feed showed them every customer screen.
-- V136 guards only core.tenant.provision. The platform tenant runs no HR or payroll of its own: its roles
-- keep the core.tenant.* actions (tenants, provisioning, impersonation), core.audit.read and core.user.manage.
--
-- Like V136 this REFUSES rather than drops, so a wrong grant tells the caller (RoleServiceImpl answers first
-- with a 400). The seed function is rewritten below so that its backfill pattern
-- (SELECT core.seed_system_roles(t.tenant_id) FROM core.tenant t) still succeeds for the platform tenant.
-- A later rewrite of core.seed_system_roles must keep the platform-tenant filter, or its backfill fails here.

-- 1. Remove every grant this script forbids. Additive-safe: it removes only rows in the platform tenant.
DELETE FROM core.role_action ra
WHERE ra.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
  AND ra.action_code NOT LIKE 'core.tenant.%'
  AND ra.action_code NOT IN ('core.audit.read', 'core.user.manage');

-- 2. Refuse them from now on.
CREATE OR REPLACE FUNCTION core.restrict_platform_tenant_grant()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF NEW.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
       AND NEW.action_code NOT LIKE 'core.tenant.%'
       AND NEW.action_code NOT IN ('core.audit.read', 'core.user.manage') THEN
        RAISE EXCEPTION 'roles in the platform tenant may hold only core.tenant.*, core.audit.read and core.user.manage, not %',
            NEW.action_code
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.restrict_platform_tenant_grant() FROM PUBLIC;

CREATE TRIGGER role_action_restrict_platform_tenant
    BEFORE INSERT OR UPDATE ON core.role_action
    FOR EACH ROW
    EXECUTE FUNCTION core.restrict_platform_tenant_grant();

-- 3. core.seed_system_roles as of V148, with the platform-tenant filter in the final WHERE.
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
        WHERE a.code <> 'core.tenant.provision'
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
            ('hr', 'core.leave.read'),
            ('hr', 'core.leave.approve'),
            ('hr', 'core.leave_type.manage'),
            ('hr', 'core.leave_balance.manage'),
            ('hr', 'core.attendance.read'),
            ('hr', 'core.attendance.manage'),
            ('hr', 'core.attendance.export'),
            ('hr', 'core.overtime.read'),
            ('hr', 'hrms.timesheet.read'),
            ('hr', 'hrms.timesheet.approve'),
            ('hr', 'hrms.timesheet.export'),
            ('hr', 'core.holiday.read'),
            ('hr', 'core.holiday.manage'),

            ('hr', 'hrms.project.read'),
            ('hr', 'hrms.project.manage'),
            ('hr', 'core.approval.decide'),

            ('manager', 'core.employee.read_team'),
            ('manager', 'core.org.read'),
            ('manager', 'core.leave.read_team'),
            ('manager', 'core.leave.approve'),
            ('manager', 'core.attendance.read_team'),
            ('manager', 'hrms.timesheet.read_team'),
            ('manager', 'hrms.timesheet.approve'),
            ('manager', 'core.holiday.read'),

            ('manager', 'hrms.project.read_team'),
            ('manager', 'hrms.project.manage'),
            ('manager', 'core.approval.decide'),

            ('payroll-officer', 'core.employee.read'),
            ('payroll-officer', 'core.employee_identification.read'),
            ('payroll-officer', 'core.employee_bank.read'),
            ('payroll-officer', 'core.org.read'),
            ('payroll-officer', 'core.attendance.read'),
            ('payroll-officer', 'core.overtime.read'),
            ('payroll-officer', 'core.leave.read'),
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
            ('payroll-officer', 'payroll.fbp.read'),
            ('payroll-officer', 'payroll.reimbursement_claim.read'),
            ('payroll-officer', 'payroll.employee_deduction.read'),
            ('payroll-officer', 'payroll.employee_deduction.manage'),

            ('finance', 'core.org.read'),
            ('finance', 'payroll.structure.read'),
            ('finance', 'payroll.salary.read'),
            ('finance', 'payroll.run.read'),
            ('finance', 'payroll.run.approve'),
            ('finance', 'payroll.payslip.read'),
            ('finance', 'payroll.statutory_report.read'),
            ('finance', 'payroll.bank_file.export'),
            ('finance', 'payroll.report.export'),
            ('finance', 'payroll.reimbursement_claim.read'),
            ('finance', 'payroll.employee_deduction.read'),

            ('employee', 'core.employee.read_own'),
            ('employee', 'core.employee.update_own'),
            ('employee', 'core.org.read'),
            ('employee', 'core.leave.apply'),
            ('employee', 'core.leave.read_own'),
            ('employee', 'hrms.attendance.mark'),
            ('employee', 'core.attendance.read_own'),
            ('employee', 'hrms.timesheet.submit'),
            ('employee', 'hrms.timesheet.read_own'),
            ('employee', 'core.holiday.read'),
            ('employee', 'payroll.payslip.read_own'),
            ('employee', 'payroll.tax_declaration.submit'),
            ('employee', 'payroll.tax_declaration.read_own'),
            ('employee', 'payroll.fbp.read_own'),
            ('employee', 'payroll.fbp.declare_own'),
            ('employee', 'payroll.reimbursement_claim.read_own'),
            ('employee', 'payroll.reimbursement_claim.submit_own'),
            ('employee', 'payroll.employee_deduction.read_own'),
            ('employee', 'hrms.project.read_own'),
            ('employee', 'hrms.overtime.request')
        ) AS v (role_code, action_code)
    ) AS grants
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    -- D-33: the platform tenant keeps platform actions only (step 2 refuses the rest).
    WHERE p_tenant_id <> '00000000-0000-0000-0000-000000000001'::uuid
       OR grants.action_code LIKE 'core.tenant.%'
       OR grants.action_code IN ('core.audit.read', 'core.user.manage')
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.seed_system_roles(UUID) FROM PUBLIC;

-- 4. Re-run the seed for every tenant: a no-op for customers, and proves the platform tenant passes step 2.
SELECT core.seed_system_roles(t.tenant_id) FROM core.tenant t;
