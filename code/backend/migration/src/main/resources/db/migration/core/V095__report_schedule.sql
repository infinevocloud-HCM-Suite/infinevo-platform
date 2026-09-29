-- Migration: V095__report_schedule.sql
-- Description: W-23.2 core.report_schedule — scheduled reports and cadences per tenant, with row-level security isolation

CREATE TABLE core.report_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    definition_id UUID NOT NULL REFERENCES core.report_definition(id),
    -- Manager's review item B-4: whoever last confirmed, by saving this schedule, that they hold
    -- the definition's required_action. The worker re-checks this account's actions before every
    -- run, since it has no signed-in caller of its own to check instead - the check that stood in
    -- for it (spec section 6) only ever ran once, at save time. NULL only for a row saved before
    -- this column existed; the evaluator treats that the same as a check that fails, not as one
    -- skipped.
    owner_user_account_id UUID NULL REFERENCES core.user_account(id),
    cadence VARCHAR(16) NOT NULL,
    day_of_period INT NULL,
    send_at_local_time TIME NOT NULL,
    filters JSONB NULL,
    recipient_emails TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_run_at TIMESTAMPTZ NULL,
    last_run_status VARCHAR(16) NULL,
    last_document_id UUID NULL REFERENCES core.document(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT report_schedule_cadence_check CHECK (cadence IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT report_schedule_day_of_period_check CHECK (day_of_period IS NULL OR (day_of_period >= 1 AND day_of_period <= 31)),
    -- RUNNING: claimed by the evaluator and not yet finished (W-23.2, claim-first).
    CONSTRAINT report_schedule_status_check CHECK (last_run_status IS NULL OR last_run_status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

-- Composite index, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- Optimizes the scheduler sweep query: (tenant_id, is_active, send_at_local_time).
CREATE INDEX idx_report_schedule_tenant_active_time
    ON core.report_schedule (tenant_id, is_active, send_at_local_time);

-- The definition foreign key, tenant-leading: finding a definition's schedules, and checking the key
-- when a definition is removed, would otherwise scan the table.
CREATE INDEX idx_report_schedule_tenant_definition
    ON core.report_schedule (tenant_id, definition_id);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.report_schedule ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.report_schedule
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Grants: app_user can perform CRUD on report schedules.
-- readonly_user can only SELECT (used for replica reads).
GRANT SELECT, INSERT, UPDATE, DELETE ON core.report_schedule TO app_user;
GRANT SELECT ON core.report_schedule TO readonly_user;
