-- Migration: V096__scheduled_report_notification.sql
-- Description: W-23.2 — add SCHEDULED_REPORT to the closed notification event list and seed its default
-- templates for every tenant.
--
-- NotificationEvent is a closed enum and core.notification_template carries a CHECK over it (V038), so a
-- new event is always a code change and a migration (W-20.1 spec section 4). This script:
--   1. widens the CHECK to include SCHEDULED_REPORT;
--   2. replaces core.seed_notification_templates with the V038 list plus SCHEDULED_REPORT, so a tenant
--      created from now on gets it;
--   3. runs the seed for every existing tenant. ON CONFLICT DO NOTHING means only the new rows are added;
--      a template a tenant has edited is another version and is left alone.
--
-- A scheduled report goes to an address, never to an employee, so compose only ever uses its EMAIL
-- template. The IN_APP default is seeded anyway: W-20.1 guarantees every event a default on both channels
-- (NotificationEventTest), and one unused row is cheaper than an exception to that rule.
-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Widen the CHECK constraint to include SCHEDULED_REPORT
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE core.notification_template
    DROP CONSTRAINT notification_template_event_check;

ALTER TABLE core.notification_template
    ADD CONSTRAINT notification_template_event_check CHECK (event IN (
        'POI_REMINDER', 'POI_SUBMITTED', 'IT_DECLARATION_REMINDER', 'IT_DECLARATION_LOCK',
        'IT_DECLARATION_RELEASE', 'PAYSLIP_READY', 'USER_INVITATION', 'EMPLOYEE_INVITATION', 'CREDENTIALS',
        'LEAVE_APPLIED', 'LEAVE_APPROVED', 'LEAVE_REJECTED', 'LEAVE_CANCELLED', 'APPROVAL_PENDING',
        'APPROVAL_DECIDED', 'TIMESHEET_REMINDER', 'SCHEDULED_REPORT'));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. The seed function, with SCHEDULED_REPORT; new tenants get it through V038's trigger
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.seed_notification_templates(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    INSERT INTO core.notification_template (tenant_id, event, channel, subject, body, effective_from)
    SELECT p_tenant_id, d.event, d.channel, d.subject, d.body, DATE '2026-01-01'
    FROM (VALUES
        ('POI_REMINDER', 'IN_APP', NULL, 'Submit your proof of investment for ${financial_year} by ${due_date}.'),
        ('POI_REMINDER', 'EMAIL', 'Reminder: submit your proof of investment', '<p>Hello ${employee_name},</p><p>Please submit your proof of investment for ${financial_year} by ${due_date}.</p>'),
        ('POI_SUBMITTED', 'IN_APP', NULL, '${employee_name} submitted proof of investment for ${financial_year}.'),
        ('POI_SUBMITTED', 'EMAIL', 'Proof of investment submitted', '<p>${employee_name} submitted proof of investment for ${financial_year}.</p>'),
        ('IT_DECLARATION_REMINDER', 'IN_APP', NULL, 'Complete your income tax declaration for ${financial_year} by ${due_date}.'),
        ('IT_DECLARATION_REMINDER', 'EMAIL', 'Reminder: complete your income tax declaration', '<p>Hello ${employee_name},</p><p>Please complete your income tax declaration for ${financial_year} by ${due_date}.</p>'),
        ('IT_DECLARATION_LOCK', 'IN_APP', NULL, 'Income tax declarations for ${financial_year} are now locked.'),
        ('IT_DECLARATION_LOCK', 'EMAIL', 'Income tax declarations are locked', '<p>Hello ${employee_name},</p><p>Income tax declarations for ${financial_year} are now locked.</p>'),
        ('IT_DECLARATION_RELEASE', 'IN_APP', NULL, 'Income tax declarations for ${financial_year} are open until ${due_date}.'),
        ('IT_DECLARATION_RELEASE', 'EMAIL', 'Income tax declarations are open', '<p>Hello ${employee_name},</p><p>Income tax declarations for ${financial_year} are open until ${due_date}.</p>'),
        ('PAYSLIP_READY', 'IN_APP', NULL, 'Your payslip for ${period} is ready.'),
        ('PAYSLIP_READY', 'EMAIL', 'Your payslip for ${period} is ready', '<p>Hello ${employee_name},</p><p>Your payslip for ${period} is ready. <a href="${link}">Download it</a>. The link works for seven days.</p>'),
        ('USER_INVITATION', 'IN_APP', NULL, 'You have been invited to ${tenant_name}.'),
        ('USER_INVITATION', 'EMAIL', 'You are invited to ${tenant_name} on Infinevo', '<p>You have been invited to join ${tenant_name} on Infinevo.</p><p><a href="${link}">Accept the invitation</a></p>'),
        ('EMPLOYEE_INVITATION', 'IN_APP', NULL, 'You have been invited to the ${tenant_name} employee portal.'),
        ('EMPLOYEE_INVITATION', 'EMAIL', 'Your ${tenant_name} employee portal', '<p>Hello ${employee_name},</p><p>You have been invited to the ${tenant_name} employee portal.</p><p><a href="${link}">Accept the invitation</a></p>'),
        ('CREDENTIALS', 'IN_APP', NULL, 'Your account for ${tenant_name} is ready.'),
        ('CREDENTIALS', 'EMAIL', 'Your Infinevo account for ${tenant_name}', '<p>Your account for ${tenant_name} is ready.</p><p><a href="${link}">Sign in</a></p>'),
        ('LEAVE_APPLIED', 'IN_APP', NULL, '${employee_name} applied for ${leave_type} from ${from_date} to ${to_date}.'),
        ('LEAVE_APPLIED', 'EMAIL', '${employee_name} applied for leave', '<p>${employee_name} applied for ${leave_type} from ${from_date} to ${to_date}.</p>'),
        ('LEAVE_APPROVED', 'IN_APP', NULL, 'Your ${leave_type} from ${from_date} to ${to_date} was approved.'),
        ('LEAVE_APPROVED', 'EMAIL', 'Your leave was approved', '<p>Hello ${employee_name},</p><p>Your ${leave_type} from ${from_date} to ${to_date} was approved.</p>'),
        ('LEAVE_REJECTED', 'IN_APP', NULL, 'Your ${leave_type} from ${from_date} to ${to_date} was not approved.'),
        ('LEAVE_REJECTED', 'EMAIL', 'Your leave was not approved', '<p>Hello ${employee_name},</p><p>Your ${leave_type} from ${from_date} to ${to_date} was not approved.</p>'),
        ('LEAVE_CANCELLED', 'IN_APP', NULL, 'The ${leave_type} from ${from_date} to ${to_date} was cancelled.'),
        ('LEAVE_CANCELLED', 'EMAIL', 'Leave cancelled', '<p>Hello ${employee_name},</p><p>The ${leave_type} from ${from_date} to ${to_date} was cancelled.</p>'),
        ('APPROVAL_PENDING', 'IN_APP', NULL, '${requester_name} is waiting for your approval: ${request_title}.'),
        ('APPROVAL_PENDING', 'EMAIL', 'An approval is waiting for you', '<p>Hello ${employee_name},</p><p>${requester_name} is waiting for your approval: ${request_title}.</p>'),
        ('APPROVAL_DECIDED', 'IN_APP', NULL, 'Your request ${request_title} was ${decision}.'),
        ('APPROVAL_DECIDED', 'EMAIL', 'Your request was ${decision}', '<p>Hello ${employee_name},</p><p>Your request ${request_title} was ${decision}.</p>'),
        ('TIMESHEET_REMINDER', 'IN_APP', NULL, 'Your timesheet for the week of ${week_start} is due.'),
        ('TIMESHEET_REMINDER', 'EMAIL', 'Reminder: submit your timesheet', '<p>Hello ${employee_name},</p><p>Your timesheet for the week of ${week_start} is due.</p>'),
        ('SCHEDULED_REPORT', 'IN_APP', NULL, 'Your scheduled report ${report_name} is ready until ${expires_at}.'),
        ('SCHEDULED_REPORT', 'EMAIL', 'Your scheduled report ${report_name} is ready', '<p>Your scheduled report <strong>${report_name}</strong> is ready.</p><p><a href="${link}">Download the report</a>. The link works until ${expires_at}.</p>')
    ) AS d (event, channel, subject, body)
    ON CONFLICT (tenant_id, event, channel, locale, effective_from) DO NOTHING;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Existing tenants: only the SCHEDULED_REPORT rows are new, so only they are added
-- ─────────────────────────────────────────────────────────────────────────────
SELECT core.seed_notification_templates(t.tenant_id) FROM core.tenant t;
