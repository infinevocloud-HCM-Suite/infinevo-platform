-- Migration: V114__employee_proof_item_document.sql
-- Description: W-34.1 payroll.employee_proof_item_document — links a proof item to core.document rows

CREATE TABLE payroll.employee_proof_item_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    item_id UUID NOT NULL REFERENCES payroll.employee_proof_item(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES core.document(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_employee_proof_item_document_tenant_item_document
    ON payroll.employee_proof_item_document (tenant_id, item_id, document_id);
CREATE INDEX idx_employee_proof_item_document_tenant_document
    ON payroll.employee_proof_item_document (tenant_id, document_id);

ALTER TABLE payroll.employee_proof_item_document ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_proof_item_document
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
