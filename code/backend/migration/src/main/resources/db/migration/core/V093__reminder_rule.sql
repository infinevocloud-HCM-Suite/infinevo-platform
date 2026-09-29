-- Migration: V093__reminder_rule.sql
-- Description: W-20.2 core.reminder_rule — reminder rules and schedules per tenant, with row-level security isolation, plus core.list_tenants_for_sweep() function for multi-tenant worker sweeps

CREATE TABLE core.reminder_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    event VARCHAR(64) NOT NULL,
    audience VARCHAR(32) NOT NULL,
    anchor VARCHAR(32) NOT NULL,
    offset_days INT NOT NULL,
    day_of_week SMALLINT NULL,
    send_at_local_time TIME NOT NULL,
    repeat_every_days INT NULL,
    max_repeats INT NULL,
    last_executed_at TIMESTAMPTZ NULL,
    repeat_count INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT reminder_rule_day_of_week_check CHECK (day_of_week IS NULL OR (day_of_week >= 1 AND day_of_week <= 7)),
    CONSTRAINT reminder_rule_repeat_every_days_check CHECK (repeat_every_days IS NULL OR repeat_every_days > 0),
    CONSTRAINT reminder_rule_max_repeats_check CHECK (max_repeats IS NULL OR max_repeats >= 0)
);

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
CREATE INDEX idx_reminder_rule_tenant_event_active
    ON core.reminder_rule (tenant_id, event, is_active);

CREATE INDEX idx_reminder_rule_tenant_active_last_exec
    ON core.reminder_rule (tenant_id, is_active, last_executed_at);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.reminder_rule ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.reminder_rule
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- The tenant list for worker sweeps (W-20.2, 12-core-contracts.md §5 row 17).
-- Runs with no request behind it, so nothing has bound a tenant and RLS maps to nothing.
-- SECURITY DEFINER in the shape of core.get_user_tenants(UUID), returning SETOF core.tenant.
-- Granted to worker_user, which inherits from app_user but app_user cannot execute it.
CREATE OR REPLACE FUNCTION core.list_tenants_for_sweep()
RETURNS SETOF core.tenant
LANGUAGE sql
SECURITY DEFINER
SET search_path = core, pg_temp
AS $$
    SELECT * FROM core.tenant;
$$;

REVOKE EXECUTE ON FUNCTION core.list_tenants_for_sweep() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.list_tenants_for_sweep() TO worker_user;

-- Deletion of a rule is soft (DELETE /reminder-rules/{id} sets is_active = false), so app_user never
-- needs DELETE. A REVOKE rather than a narrower GRANT, for the reason V008__audit_log.sql gives.
REVOKE DELETE ON core.reminder_rule FROM app_user;

-- ─────────────────────────────────────────────────────────────────────────────
-- Delivery state on core.notification (W-20.2). W-20.1 created the table with the outcome only (QUEUED,
-- SENT, FAILED). Delivering safely needs more:
--   SENDING          the row is claimed by one consumer, so a message the queue delivers twice at once
--                    (D-50) is sent once
--   attempt_count    how many times sending has been tried
--   next_attempt_at  when a transient failure may be retried; the delivery sweep waits for it
--   last_error       why the last attempt failed, so a FAILED row says why to whoever looks
--   sent_at          when the provider accepted it
-- An ALTER on W-20.1's table in W-20.2's script, as W-22.2 alters core.tenant in its own.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE core.notification DROP CONSTRAINT notification_status_check;
ALTER TABLE core.notification
    ADD CONSTRAINT notification_status_check CHECK (status IN ('QUEUED', 'SENDING', 'SENT', 'FAILED'));

ALTER TABLE core.notification
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NULL,
    ADD COLUMN last_error VARCHAR(500) NULL,
    ADD COLUMN sent_at TIMESTAMPTZ NULL;

ALTER TABLE core.notification
    ADD CONSTRAINT notification_attempt_count_check CHECK (attempt_count >= 0);
