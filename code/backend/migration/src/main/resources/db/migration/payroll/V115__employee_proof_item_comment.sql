-- Migration: V115__employee_proof_item_comment.sql
-- Description: W-34.2 payroll.employee_proof_item_comment — flat comment list per proof item

CREATE TABLE payroll.employee_proof_item_comment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    item_id UUID NOT NULL REFERENCES payroll.employee_proof_item(id) ON DELETE CASCADE,
    author_employee_id UUID NOT NULL REFERENCES core.employee(id),
    author_role VARCHAR(8) NOT NULL CHECK (author_role IN ('EMPLOYEE', 'REVIEWER')),
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_employee_proof_item_comment_tenant_item_created
    ON payroll.employee_proof_item_comment (tenant_id, item_id, created_at);

ALTER TABLE payroll.employee_proof_item_comment ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_proof_item_comment
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
