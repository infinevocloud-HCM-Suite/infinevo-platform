-- Migration: V183__wfh_import_log.sql
-- Description: W-74 core.wfh_import_log — bulk WFH allocation CSV import log with RLS isolation

CREATE TABLE core.wfh_import_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    source_document_id  UUID NULL REFERENCES core.document(id) ON DELETE RESTRICT,
    error_document_id   UUID NULL REFERENCES core.document(id) ON DELETE RESTRICT,
    file_name           VARCHAR(255) NOT NULL,
    wfh_year            VARCHAR(9) NOT NULL,
    status              VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    is_dry_run          BOOLEAN NOT NULL DEFAULT false,
    rows_total          INT NOT NULL DEFAULT 0,
    rows_imported       INT NOT NULL DEFAULT 0,
    rows_failed         INT NOT NULL DEFAULT 0,
    error_summary       TEXT NULL,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at         TIMESTAMPTZ NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_wfh_import_log_tenant_started ON core.wfh_import_log (tenant_id, started_at DESC);
CREATE INDEX idx_wfh_import_log_tenant_status ON core.wfh_import_log (tenant_id, status);

ALTER TABLE core.wfh_import_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.wfh_import_log
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE ON core.wfh_import_log TO app_user;
