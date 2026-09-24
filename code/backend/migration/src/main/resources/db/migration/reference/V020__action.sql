-- Migration: V020__action.sql
-- Description: W-11.1 reference.action — the catalogue of what the platform can do, seeded here

-- No tenant_id and no row-level security, deliberately (D-08, CONVENTIONS.md Rule 7, spec §6 and
-- §13 decision 1). An action is a name for something the code can do: every tenant has the same
-- list and only a release changes it. A tenant column would mean one identical copy per tenant
-- that nobody can edit. app_user holds SELECT and nothing else here, through the default
-- privileges on the reference schema (infra/postgres/03-grants.sql:32-33) — no grant is needed.

CREATE TABLE reference.action (
    code VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    module VARCHAR(16) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT action_module_check CHECK (module IN ('core', 'hrms', 'payroll')),
    -- <module>.<resource>.<verb>, lowercase, and the prefix must agree with the module column.
    CONSTRAINT action_code_format_check CHECK (code ~ '^[a-z]+\.[a-z_]+\.[a-z_]+$'),
    CONSTRAINT action_code_module_check CHECK (split_part(code, '.', 1) = module)
);

CREATE INDEX idx_action_module ON reference.action (module);

-- ─────────────────────────────────────────────────────────────────────────────
-- Seed
--
-- Written against what the new platform does or will do, not transcribed from the frozen
-- HRMS list (ActionServiceImpl.java:152-290, spec §9). That list mixes dashboards, menu
-- visibility and duplicate "manage" umbrellas with real operations; here an action is one
-- thing an endpoint does. Payroll had no seed at all and created actions at runtime
-- (Payroll ActionServiceImpl.java:24-34) — the platform never does: the API is read-only.
--
-- Verbs ending _own and _team are the self-service and line-manager forms of the same
-- operation. The action says what may be done; which rows it reaches (own, team, tenant) is
-- the service's filter, enforced in W-11.2 and later.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reference.action (code, name, module, description) VALUES
    -- Tenant and platform
    ('core.tenant.provision', 'Provision tenants', 'core',
        'Create and suspend customer tenants. Platform staff only; never granted to a customer role'),
    ('core.tenant.read', 'View tenant settings', 'core', 'Read the tenant''s own settings'),
    ('core.tenant.manage', 'Manage tenant settings', 'core', 'Change the tenant''s own settings'),

    -- Users, roles, access
    ('core.user.read', 'View users', 'core', 'List the tenant''s user accounts'),
    ('core.user.manage', 'Manage users', 'core', 'Invite, update and deactivate user accounts'),
    ('core.role.read', 'View roles', 'core', 'List roles, their actions and the action catalogue'),
    ('core.role.manage', 'Manage roles', 'core', 'Create, edit and delete the tenant''s own roles'),
    ('core.role.assign', 'Assign roles', 'core', 'Grant roles to and revoke roles from users'),

    -- Employees
    ('core.employee.read', 'View employees', 'core', 'Read every employee record in the tenant'),
    ('core.employee.read_team', 'View team members', 'core', 'Read the records of the employees who report to you'),
    ('core.employee.read_own', 'View own profile', 'core', 'Read your own employee record'),
    ('core.employee.update_own', 'Update own profile', 'core',
        'Edit the self-service parts of your own record: personal and contact details'),
    ('core.employee.create', 'Create employees', 'core', 'Add a new employee'),
    ('core.employee.update', 'Update employees', 'core',
        'Edit any employee''s record and its personal, contact and employment sections'),
    ('core.employee.delete', 'Delete employees', 'core', 'Remove an employee record'),
    ('core.employee.export', 'Export employees', 'core', 'Export employee data to a file'),
    ('core.employee_identification.read', 'View identification details', 'core',
        'Read PAN, Aadhaar and other identity numbers'),
    ('core.employee_identification.update', 'Update identification details', 'core',
        'Edit PAN, Aadhaar and other identity numbers'),
    ('core.employee_bank.read', 'View bank details', 'core', 'Read how an employee is paid'),
    ('core.employee_bank.update', 'Update bank details', 'core', 'Edit how an employee is paid'),

    -- Organisation masters and audit
    ('core.org.read', 'View organisation masters', 'core', 'Read departments, designations and work locations'),
    ('core.org.manage', 'Manage organisation masters', 'core',
        'Create, edit and deactivate departments, designations and work locations'),
    ('core.audit.read', 'View audit log', 'core', 'Read the tenant''s audit trail'),

    -- Leave
    ('hrms.leave.apply', 'Apply for leave', 'hrms', 'Raise and cancel your own leave requests'),
    ('hrms.leave.read_own', 'View own leave', 'hrms', 'Read your own leave requests and balances'),
    ('hrms.leave.read_team', 'View team leave', 'hrms', 'Read the leave requests and balances of your reports'),
    ('hrms.leave.read', 'View all leave', 'hrms', 'Read every leave request and balance in the tenant'),
    ('hrms.leave.approve', 'Approve leave', 'hrms', 'Approve or reject leave requests'),
    ('hrms.leave_type.manage', 'Manage leave types', 'hrms', 'Define leave types and their policies'),
    ('hrms.leave_balance.manage', 'Manage leave balances', 'hrms', 'Credit, adjust and carry forward leave balances'),

    -- Attendance
    ('hrms.attendance.mark', 'Mark attendance', 'hrms', 'Clock in and out, and request a regularisation'),
    ('hrms.attendance.read_own', 'View own attendance', 'hrms', 'Read your own attendance'),
    ('hrms.attendance.read_team', 'View team attendance', 'hrms', 'Read the attendance of your reports'),
    ('hrms.attendance.read', 'View all attendance', 'hrms', 'Read attendance across the tenant'),
    ('hrms.attendance.manage', 'Manage attendance', 'hrms', 'Correct attendance and approve regularisations'),
    ('hrms.attendance.export', 'Export attendance', 'hrms', 'Export attendance data to a file'),

    -- Timesheet
    ('hrms.timesheet.submit', 'Submit timesheet', 'hrms', 'Enter, edit and submit your own timesheet'),
    ('hrms.timesheet.read_own', 'View own timesheet', 'hrms', 'Read your own timesheet'),
    ('hrms.timesheet.read_team', 'View team timesheets', 'hrms', 'Read the timesheets of your reports'),
    ('hrms.timesheet.read', 'View all timesheets', 'hrms', 'Read timesheets across the tenant'),
    ('hrms.timesheet.approve', 'Approve timesheets', 'hrms', 'Approve or reject submitted timesheets'),
    ('hrms.timesheet.export', 'Export timesheets', 'hrms', 'Export timesheet data to a file'),

    -- Holidays
    ('hrms.holiday.read', 'View holidays', 'hrms', 'Read the holiday calendar'),
    ('hrms.holiday.manage', 'Manage holidays', 'hrms', 'Maintain the holiday calendar'),

    -- Payroll configuration
    ('payroll.settings.manage', 'Manage payroll settings', 'payroll',
        'Pay schedule, statutory registrations and payroll policies'),
    ('payroll.structure.read', 'View salary structures', 'payroll', 'Read salary structures and components'),
    ('payroll.structure.manage', 'Manage salary structures', 'payroll', 'Define salary structures and components'),
    ('payroll.salary.read', 'View employee salaries', 'payroll', 'Read each employee''s assigned salary'),
    ('payroll.salary.manage', 'Manage employee salaries', 'payroll', 'Assign and revise employee salaries'),

    -- Payroll runs and payslips
    ('payroll.run.read', 'View payroll runs', 'payroll', 'Read payroll runs and their results'),
    ('payroll.run.execute', 'Execute payroll runs', 'payroll', 'Start, recalculate and submit a payroll run'),
    ('payroll.run.approve', 'Approve payroll runs', 'payroll', 'Approve and lock a submitted payroll run'),
    ('payroll.payslip.read', 'View all payslips', 'payroll', 'Read every payslip in the tenant'),
    ('payroll.payslip.read_own', 'View own payslips', 'payroll', 'Read and download your own payslips'),
    ('payroll.payslip.publish', 'Publish payslips', 'payroll', 'Release payslips to employees'),

    -- Tax declarations
    ('payroll.tax_declaration.submit', 'Submit tax declaration', 'payroll',
        'Choose a tax regime and submit your own investment declarations and proofs'),
    ('payroll.tax_declaration.read_own', 'View own tax declaration', 'payroll', 'Read your own tax declarations'),
    ('payroll.tax_declaration.read', 'View all tax declarations', 'payroll', 'Read every employee''s tax declarations'),
    ('payroll.tax_declaration.verify', 'Verify tax declarations', 'payroll', 'Accept or reject declarations and proofs'),

    -- Statutory reports and exports
    ('payroll.statutory_report.read', 'View statutory reports', 'payroll', 'Read PF, ESI, PT and TDS reports'),
    ('payroll.statutory_report.generate', 'Generate statutory reports', 'payroll',
        'Generate PF, ESI, PT and TDS returns and challans'),
    ('payroll.bank_file.export', 'Export bank transfer file', 'payroll', 'Export the salary bank transfer file'),
    ('payroll.report.export', 'Export payroll reports', 'payroll', 'Export payroll registers and reports to a file');
