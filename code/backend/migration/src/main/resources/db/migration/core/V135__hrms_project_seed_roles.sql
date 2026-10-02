-- Migration: V135__hrms_project_seed_roles.sql
-- Description: W-41 core.seed_system_roles — the single current grant list: everything V100 grants (V097's list
-- plus W-35.2's payroll.employee_deduction.*), plus the hrms.project.* grants V085 introduced.
--
-- Why this exists. V085 (W-41) and V097 (W-35.1) each used to redefine the whole function from an older copy,
-- and whichever ran last silently dropped the other's grants: after V097, a new tenant had no hrms.project.*
-- grants; and on a database that already had V097, V085 (out-of-order) would have dropped the reimbursement
-- claim grants. V085 now only inserts the action codes; this script, numbered after both, is the one place
-- the function is defined with both sets. A later ticket that adds grants must extend THIS list, not an old one.

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
            ('hr', 'hrms.timesheet.read'),
            ('hr', 'hrms.timesheet.approve'),
            ('hr', 'hrms.timesheet.export'),
            ('hr', 'core.holiday.read'),
            ('hr', 'core.holiday.manage'),

            ('hr', 'hrms.project.read'),
            ('hr', 'hrms.project.manage'),

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

            ('payroll-officer', 'core.employee.read'),
            ('payroll-officer', 'core.employee_identification.read'),
            ('payroll-officer', 'core.employee_bank.read'),
            ('payroll-officer', 'core.org.read'),
            ('payroll-officer', 'core.attendance.read'),
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
            ('employee', 'hrms.project.read_own')
        ) AS v (role_code, action_code)
    ) AS grants
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.seed_system_roles(UUID) FROM PUBLIC;

-- Existing tenants get the hrms.project.* grants (V085 no longer backfills them).
SELECT core.seed_system_roles(t.tenant_id) FROM core.tenant t;
