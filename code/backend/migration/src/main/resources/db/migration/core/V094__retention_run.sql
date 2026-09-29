-- Migration: V094__retention_run.sql
-- Description: W-22.2 core.retention_run — audit log and notification retention sweep records,
-- plus per-tenant retention window columns on core.tenant, with row-level security isolation
-- and grants for the dedicated retention_user role.

-- 1. core.retention_run table
CREATE TABLE core.retention_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    target_table VARCHAR(64) NOT NULL,
    cutoff_date DATE NOT NULL,
    rows_deleted BIGINT NOT NULL DEFAULT 0,
    is_dry_run BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT retention_run_status_check CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'PARTIAL')),
    CONSTRAINT retention_run_rows_deleted_check CHECK (rows_deleted >= 0)
);

-- Composite index: tenant_id leading plus lookup columns (DEBT-018, spec §6).
CREATE INDEX idx_retention_run_tenant_started_at
    ON core.retention_run (tenant_id, started_at DESC);

-- Row-level security on core.retention_run (spec §6).
ALTER TABLE core.retention_run ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.retention_run
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- 2. Per-tenant retention window columns on core.tenant (spec §6, decision 3).
-- Default 84 months (7 years) for audit log; 12 months (1 year) for notifications.
-- Enforce a floor of 12 months on audit_retention_months (spec decision 3).
ALTER TABLE core.tenant
    ADD COLUMN IF NOT EXISTS audit_retention_months INT NOT NULL DEFAULT 84,
    ADD COLUMN IF NOT EXISTS notification_retention_months INT NOT NULL DEFAULT 12;

ALTER TABLE core.tenant
    ADD CONSTRAINT tenant_audit_retention_months_check CHECK (audit_retention_months >= 12);

-- A notification window of zero or less would delete every notification on the next sweep.
ALTER TABLE core.tenant
    ADD CONSTRAINT tenant_notification_retention_months_check CHECK (notification_retention_months >= 1);

-- Index for notification retention sweep on queued_at (spec §6, DEBT-018).
CREATE INDEX IF NOT EXISTS idx_notification_tenant_queued_at
    ON core.notification (tenant_id, queued_at DESC);

-- 3. Grants and Revocations (spec §6, decision 1).
-- app_user keeps SELECT, INSERT and gains nothing. core.retention_run is itself never swept.
REVOKE DELETE, UPDATE ON core.retention_run FROM app_user;

-- Dedicated retention_user role grants (spec §6).
-- retention_user has SELECT, DELETE on core.audit_log and core.notification;
-- SELECT, INSERT, UPDATE on core.retention_run; EXECUTE on core.list_tenants_for_sweep().
-- Not superuser, not BYPASSRLS, no UPDATE on targets, no access to other tables.
GRANT USAGE ON SCHEMA core TO retention_user;
GRANT SELECT, DELETE ON core.audit_log TO retention_user;
GRANT SELECT, DELETE ON core.notification TO retention_user;
GRANT SELECT, INSERT, UPDATE ON core.retention_run TO retention_user;
GRANT EXECUTE ON FUNCTION core.list_tenants_for_sweep() TO retention_user;
