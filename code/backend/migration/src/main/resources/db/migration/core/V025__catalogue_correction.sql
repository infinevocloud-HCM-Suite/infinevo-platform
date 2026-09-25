-- Migration: V025__catalogue_correction.sql
-- Description: W-11.3 catalogue correction — Core codes out of the hrms.* prefix, the 24 missing Core
-- codes, and core.tenant.provision out of every tenant-seeded role

-- Why (12-core-contracts.md §4, D-4, D-5). Leave, holidays and administrator-entered attendance are
-- Core features, but V020__action.sql filed their 14 codes under 'hrms'. The catalogue forces the
-- code prefix to equal the module column (V020__action.sql:22), so a module filter would strip them
-- from a Payroll-only tenant. They move to 'core'. hrms.attendance.mark and hrms.timesheet.* stay
-- 'hrms': they are the HRMS-only experience.
--
-- core.tenant.provision is "never granted to a customer role" (V020__action.sql:43-44), yet
-- V022__role_action.sql:86-87 gave it to platform-admin, which every tenant gets. After this script
-- no tenant-seeded role holds it; who does is W-12.1's decision.
--
-- reference.action.code is the primary key that core.role_action.action_code references
-- (V022__role_action.sql:9), so a rename is expand (insert the new codes), move (repoint the grants),
-- contract (delete the old codes). All of it runs in one Flyway transaction; nothing on main reads
-- the old codes except the V022 seed function, which this script replaces.
--
-- Runs as migration_user, which owns core.role_action and so bypasses its RLS (migration/README.md
-- §row-level security). Forward-only.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Expand: the 14 renamed codes, same name and description, module 'core'
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.action (code, name, module, description)
SELECT regexp_replace(a.code, '^hrms\.', 'core.'), a.name, 'core', a.description
FROM reference.action a
WHERE a.code IN (
    'hrms.leave.apply', 'hrms.leave.read_own', 'hrms.leave.read_team', 'hrms.leave.read',
    'hrms.leave.approve', 'hrms.leave_type.manage', 'hrms.leave_balance.manage',
    'hrms.attendance.read_own', 'hrms.attendance.read_team', 'hrms.attendance.read',
    'hrms.attendance.manage', 'hrms.attendance.export', 'hrms.holiday.read', 'hrms.holiday.manage');

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. The 24 Core codes the specs cite that did not exist (12-core-contracts.md:128)
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.action (code, name, module, description) VALUES
    -- Reporting lines and approvals
    ('core.reporting_line.manage', 'Manage reporting lines', 'core',
        'Set and change who each employee reports to'),
    ('core.approval_definition.manage', 'Manage approval paths', 'core',
        'Define the approval steps each kind of request goes through'),
    ('core.approval.read', 'View approvals', 'core', 'Read approval requests and their history'),
    ('core.approval.decide', 'Decide approvals', 'core', 'Approve or reject a step assigned to you'),
    ('core.approval.delegate', 'Delegate approvals', 'core', 'Hand your approval steps to someone else for a period'),
    ('core.approval.manage', 'Manage approvals', 'core',
        'Reassign, escalate or cancel any approval request in the tenant'),

    -- Loss of pay and leave administration
    ('core.lop_policy.read', 'View loss-of-pay policy', 'core', 'Read how loss of pay is counted'),
    ('core.lop_policy.manage', 'Manage loss-of-pay policy', 'core', 'Define how loss of pay is counted'),
    ('core.leave.manage', 'Manage leave requests', 'core',
        'Raise, correct or cancel leave on an employee''s behalf'),

    -- Pay inputs and overtime
    ('core.pay_input.read', 'View pay inputs', 'core', 'Read the attendance, leave and overtime figures sent to payroll'),
    ('core.pay_input.write', 'Record pay inputs', 'core', 'Enter and correct pay inputs for a period'),
    ('core.pay_input.lock', 'Lock pay inputs', 'core', 'Freeze a period''s pay inputs for the payroll run'),
    ('core.overtime.read', 'View overtime', 'core', 'Read overtime across the tenant'),
    ('core.overtime.manage', 'Manage overtime', 'core', 'Approve, correct and set the rules for overtime'),

    -- Notifications
    ('core.notification_template.manage', 'Manage notification templates', 'core',
        'Edit the wording of emails and in-app notifications'),
    ('core.reminder_rule.manage', 'Manage reminders', 'core', 'Define when and to whom reminders are sent'),

    -- Documents
    ('core.document.read', 'View documents', 'core', 'Read every employee document in the tenant'),
    ('core.document.read_own', 'View own documents', 'core', 'Read and download your own documents'),
    ('core.document.upload', 'Upload documents', 'core', 'Add a document to an employee record'),
    ('core.document.delete', 'Delete documents', 'core', 'Remove a document from an employee record'),

    -- Reports and background jobs
    ('core.report.read', 'View reports', 'core', 'Run and download reports'),
    ('core.report.manage', 'Manage reports', 'core', 'Create, edit and delete report definitions'),
    ('core.report_schedule.manage', 'Manage report schedules', 'core', 'Schedule reports to run and be sent'),
    ('core.job.read', 'View background jobs', 'core', 'Read the status and progress of background jobs');

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Move: every grant of an old code now points at its core.* twin
-- ─────────────────────────────────────────────────────────────────────────────

UPDATE core.role_action
SET action_code = regexp_replace(action_code, '^hrms\.', 'core.'),
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'W-11.3'
WHERE action_code IN (
    'hrms.leave.apply', 'hrms.leave.read_own', 'hrms.leave.read_team', 'hrms.leave.read',
    'hrms.leave.approve', 'hrms.leave_type.manage', 'hrms.leave_balance.manage',
    'hrms.attendance.read_own', 'hrms.attendance.read_team', 'hrms.attendance.read',
    'hrms.attendance.manage', 'hrms.attendance.export', 'hrms.holiday.read', 'hrms.holiday.manage');

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Contract: the old codes go; no grant references them any more
-- ─────────────────────────────────────────────────────────────────────────────

DELETE FROM reference.action
WHERE code IN (
    'hrms.leave.apply', 'hrms.leave.read_own', 'hrms.leave.read_team', 'hrms.leave.read',
    'hrms.leave.approve', 'hrms.leave_type.manage', 'hrms.leave_balance.manage',
    'hrms.attendance.read_own', 'hrms.attendance.read_team', 'hrms.attendance.read',
    'hrms.attendance.manage', 'hrms.attendance.export', 'hrms.holiday.read', 'hrms.holiday.manage');

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. The seed function, corrected — V022__role_action.sql:65-185 verbatim except:
--
--   platform-admin   every action except core.tenant.provision (was: every action)
--   tenant-admin     every action except core.tenant.provision (unchanged)
--   the leave, attendance (not mark) and holiday grants name the core.* codes
--
-- The 24 new codes reach the two admin roles through the catalogue select; each feature ticket
-- grants its own code to hr, manager or employee in its own script (spec §2, out of scope).
-- The core.tenant trigger calls this function by name, so it needs no change.
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
            ('employee', 'core.leave.apply'),
            ('employee', 'core.leave.read_own'),
            ('employee', 'hrms.attendance.mark'),
            ('employee', 'core.attendance.read_own'),
            ('employee', 'hrms.timesheet.submit'),
            ('employee', 'hrms.timesheet.read_own'),
            ('employee', 'core.holiday.read'),
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

-- CREATE OR REPLACE keeps the owner and the V022 REVOKE; restated so the grant is visible here.
REVOKE EXECUTE ON FUNCTION core.seed_system_roles(UUID) FROM PUBLIC;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. The grant no tenant may hold: provision out of every tenant's system platform-admin
-- ─────────────────────────────────────────────────────────────────────────────

DELETE FROM core.role_action ra
USING core.role r
WHERE r.tenant_id = ra.tenant_id
  AND r.id = ra.role_id
  AND r.is_system
  AND r.code = 'platform-admin'
  AND ra.action_code = 'core.tenant.provision';

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. Backfill — idempotent (V022__role_action.sql:60-62): existing tenants' admin roles pick up
-- the 24 new codes; every other grant is already in place after step 3
-- ─────────────────────────────────────────────────────────────────────────────

SELECT core.seed_system_roles(t.tenant_id) FROM core.tenant t;
