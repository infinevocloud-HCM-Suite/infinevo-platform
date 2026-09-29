-- Migration: V038__notification_template.sql
-- Description: W-20.1 core.notification_template — per-tenant, versioned wording for every notification event and channel, with row-level security isolation, plus the platform defaults seeded per tenant

CREATE TABLE core.notification_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    event VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    subject VARCHAR(255) NULL,
    body TEXT NOT NULL,
    locale VARCHAR(8) NOT NULL DEFAULT 'en',
    is_active BOOLEAN NOT NULL DEFAULT true,
    -- Versioned by date: an edit is a new row from today, and the latest row in force is the one used.
    -- What an employee was told last month stays what the stored notification says (V039).
    effective_from DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT notification_template_channel_check CHECK (channel IN ('IN_APP', 'EMAIL')),
    -- The closed NotificationEvent list. A new event is a code change and a migration, deliberately:
    -- it needs a default template seeded for every tenant (W-20.1 spec section 4).
    CONSTRAINT notification_template_event_check CHECK (event IN (
        'POI_REMINDER', 'POI_SUBMITTED', 'IT_DECLARATION_REMINDER', 'IT_DECLARATION_LOCK',
        'IT_DECLARATION_RELEASE', 'PAYSLIP_READY', 'USER_INVITATION', 'EMPLOYEE_INVITATION', 'CREDENTIALS',
        'LEAVE_APPLIED', 'LEAVE_APPROVED', 'LEAVE_REJECTED', 'LEAVE_CANCELLED', 'APPROVAL_PENDING',
        'APPROVAL_DECIDED', 'TIMESHEET_REMINDER')),
    CONSTRAINT notification_template_email_subject_check CHECK (channel <> 'EMAIL' OR subject IS NOT NULL)
);

-- The templates live here, in the repository and per tenant — not in a Brevo dashboard nobody in the
-- tenant can reach (legacy/Payroll-Bend-SBoot/src/main/resources/application.properties:52-65 maps
-- events to Brevo-hosted template ids), and not in Java string concatenation (HRMS MailService).

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- The first serves "the template in force for this event and channel": latest effective_from first.
CREATE INDEX idx_notification_template_tenant_event_channel
    ON core.notification_template (tenant_id, event, channel, effective_from DESC);
CREATE UNIQUE INDEX uk_notification_template_tenant_version
    ON core.notification_template (tenant_id, event, channel, locale, effective_from);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.notification_template ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.notification_template
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- The platform defaults every tenant starts with (spec section 13, decision 1: a tenant may edit its
-- own, starting from these). One row per event per channel. Placeholders are ${name}; each must be one
-- the event supplies (NotificationEvent), which NotificationEventTest checks against this list.
-- EMAIL bodies are HTML - values are escaped into them; IN_APP bodies are plain text.
--
-- effective_from is a fixed early date so the defaults are in force from the start; a tenant's edit
-- from today supersedes them without deleting them.
--
-- SECURITY DEFINER, owned by migration_user, the pattern of core.seed_system_roles in V022.
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
        ('TIMESHEET_REMINDER', 'EMAIL', 'Reminder: submit your timesheet', '<p>Hello ${employee_name},</p><p>Your timesheet for the week of ${week_start} is due.</p>')
    ) AS d (event, channel, subject, body)
    ON CONFLICT (tenant_id, event, channel, locale, effective_from) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.seed_notification_templates(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_seed_notification_templates()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.seed_notification_templates(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_seed_notification_templates() FROM PUBLIC;

CREATE TRIGGER tenant_seed_notification_templates
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_seed_notification_templates();

-- Tenants that exist before this script ran get the defaults too.
SELECT core.seed_notification_templates(t.tenant_id) FROM core.tenant t;

-- No role grant here. core.notification_template.manage stays with platform-admin and tenant-admin,
-- which hold every code through the catalogue (V025): the wording a whole tenant receives is an
-- administrator's decision (W-20.1 spec section 13, decision 1). Contrast V037 and V040, which grant
-- their codes to hr and employee (W-11.3 spec section 2).
