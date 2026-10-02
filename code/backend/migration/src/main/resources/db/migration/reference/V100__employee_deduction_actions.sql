-- Migration: V100__employee_deduction_actions.sql
-- Description: W-35.2 reference.action — ad-hoc salary deduction action codes and role_action grants.
-- The V052 / V097 shape (spec section 6): the codes, core.seed_system_roles redefined so a tenant
-- provisioned from now on gets the grants, and the grants for every existing tenant.
--
-- The function below is V097's body unchanged, plus the four deduction grants marked W-35.2.
-- Whoever redefines core.seed_system_roles after this must start from this copy, or the grants of
-- this and every earlier lane are lost for new tenants (see V059's note on the same risk).

INSERT INTO reference.action (code, name, module, description) VALUES
    ('payroll.employee_deduction.read', 'View salary deductions', 'payroll', 'Read any employee''s ad-hoc salary deduction in tenant'),
    ('payroll.employee_deduction.read_own', 'View own salary deductions', 'payroll', 'Read your own ad-hoc salary deductions'),
    ('payroll.employee_deduction.manage', 'Manage salary deductions', 'payroll', 'Enter and reverse ad-hoc salary deductions')
ON CONFLICT (code) DO NOTHING;

-- Update the tenant seed function so future tenants get the deduction grants (W-35.2)
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

            ('manager', 'core.employee.read_team'),
            ('manager', 'core.org.read'),
            ('manager', 'core.leave.read_team'),
            ('manager', 'core.leave.approve'),
            ('manager', 'core.attendance.read_team'),
            ('manager', 'hrms.timesheet.read_team'),
            ('manager', 'hrms.timesheet.approve'),
            ('manager', 'core.holiday.read'),

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
            ('employee', 'payroll.employee_deduction.read_own')
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

-- Grant to existing tenants' system roles
INSERT INTO core.role_action (tenant_id, role_id, action_code)
SELECT r.tenant_id, r.id, grants.action_code
FROM (
    SELECT 'platform-admin'::VARCHAR AS role_code, 'payroll.employee_deduction.read'::VARCHAR AS action_code
    UNION ALL
    SELECT 'platform-admin', 'payroll.employee_deduction.read_own'
    UNION ALL
    SELECT 'platform-admin', 'payroll.employee_deduction.manage'
    UNION ALL
    SELECT 'tenant-admin', 'payroll.employee_deduction.read'
    UNION ALL
    SELECT 'tenant-admin', 'payroll.employee_deduction.read_own'
    UNION ALL
    SELECT 'tenant-admin', 'payroll.employee_deduction.manage'
    UNION ALL
    SELECT 'payroll-officer', 'payroll.employee_deduction.read'
    UNION ALL
    SELECT 'payroll-officer', 'payroll.employee_deduction.manage'
    UNION ALL
    SELECT 'finance', 'payroll.employee_deduction.read'
    UNION ALL
    SELECT 'employee', 'payroll.employee_deduction.read_own'
) AS grants
JOIN core.role r
    ON r.code = grants.role_code
   AND r.is_system
ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
