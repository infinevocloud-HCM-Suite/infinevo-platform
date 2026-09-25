-- Migration: V039__notification.sql
-- Description: W-20.1 core.notification — one row per notification, in-app and email alike, holding the rendered text, with row-level security isolation

CREATE TABLE core.notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    recipient_employee_id UUID NULL REFERENCES core.employee(id),
    -- Beside the employee id because an invitation goes to someone who is not an employee yet (W-24.2).
    recipient_email VARCHAR(255) NULL,
    event VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    subject VARCHAR(255) NULL,
    -- Stored rendered, never re-rendered: a template edited next month must not change what an
    -- employee was told last month. template_id records which version produced it.
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    template_id UUID NULL REFERENCES core.notification_template(id),
    queued_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ NULL,
    -- What the notification is about, e.g. "leave_request:<id>", for the screen that shows it.
    subject_ref VARCHAR(128) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT notification_channel_check CHECK (channel IN ('IN_APP', 'EMAIL')),
    CONSTRAINT notification_status_check CHECK (status IN ('QUEUED', 'SENT', 'FAILED')),
    CONSTRAINT notification_recipient_check CHECK (recipient_employee_id IS NOT NULL OR recipient_email IS NOT NULL)
);

-- A figure inside a body is rendered text, never a stored amount: no money column here.

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- The in-app list: a recipient's notifications, unread first. Also serves the foreign key on the employee.
CREATE INDEX idx_notification_tenant_recipient_read
    ON core.notification (tenant_id, recipient_employee_id, read_at);
-- The delivery sweep (W-20.2): what is still QUEUED, oldest first.
CREATE INDEX idx_notification_tenant_status_queued
    ON core.notification (tenant_id, status, queued_at);
-- The foreign key on the template.
CREATE INDEX idx_notification_tenant_template ON core.notification (tenant_id, template_id);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.notification ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.notification
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- app_user writes a notification and marks it read or delivered - INSERT and UPDATE - and never deletes
-- one. Removing old notifications is W-22.2's retention sweep (spec section 13, decision 2), which runs
-- as the owner. A REVOKE, for the reason V008__audit_log.sql gives: 03-grants.sql granted DELETE by default.
REVOKE DELETE ON core.notification FROM app_user;
