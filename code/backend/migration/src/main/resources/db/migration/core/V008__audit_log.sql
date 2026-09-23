-- Migration: V008__audit_log.sql
-- Description: Tenant-scoped audit trail of insert, update and delete, with row-level security isolation

CREATE TABLE core.audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_id UUID NULL,
    actor_label VARCHAR(100) NOT NULL,
    operation VARCHAR(10) NOT NULL,
    entity_schema VARCHAR(32) NOT NULL,
    entity_table VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    changed_columns TEXT[] NULL,
    old_values JSONB NULL,
    new_values JSONB NULL,
    trace_id VARCHAR(36) NULL,
    CONSTRAINT audit_log_operation_check CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE'))
);

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
CREATE INDEX idx_audit_log_tenant_entity ON core.audit_log (tenant_id, entity_table, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_log_tenant_occurred ON core.audit_log (tenant_id, occurred_at DESC);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.audit_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.audit_log
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- app_user may SELECT and INSERT, never UPDATE or DELETE: an audit row cannot be
-- edited by the application that wrote it.
-- This is a REVOKE and not a narrower GRANT because ALTER DEFAULT PRIVILEGES in
-- infra/postgres/03-grants.sql:29 has already granted SELECT, INSERT, UPDATE, DELETE
-- on every new core table to app_user. Granting only SELECT, INSERT would add nothing
-- and remove nothing; the two write privileges must be taken away explicitly.
REVOKE UPDATE, DELETE ON core.audit_log FROM app_user;
