-- Migration: V130__leave_request_document.sql
-- Description: W-16.3 core.leave_request_document — leave request attachments with RLS isolation

CREATE TABLE core.leave_request_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    leave_request_id UUID NOT NULL REFERENCES core.leave_request(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES core.document(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_leave_request_document UNIQUE (tenant_id, leave_request_id, document_id)
);

CREATE INDEX idx_leave_request_document_lookup ON core.leave_request_document (tenant_id, leave_request_id);
CREATE INDEX idx_leave_request_document_tenant_doc ON core.leave_request_document (tenant_id, document_id);


ALTER TABLE core.leave_request_document ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.leave_request_document
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
