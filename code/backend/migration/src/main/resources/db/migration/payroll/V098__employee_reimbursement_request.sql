-- Migration: V098__employee_reimbursement_request.sql
-- Description: W-35.1 payroll.employee_reimbursement_request — employee reimbursement claims with RLS isolation

CREATE TABLE payroll.employee_reimbursement_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    reimbursement_id UUID NOT NULL REFERENCES payroll.reimbursement(id),
    requested_amount NUMERIC(19,4) NOT NULL CHECK (requested_amount > 0),
    approved_amount NUMERIC(19,4) NULL CHECK (approved_amount >= 0),
    bill_date DATE NOT NULL,
    description VARCHAR(500),
    document_id UUID NULL REFERENCES core.document(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('SUBMITTED','APPROVED','REJECTED')),
    remarks VARCHAR(500),
    approval_instance_id UUID NULL,
    pay_input_id UUID NULL,
    posted_period CHAR(7) NULL,
    approved_by UUID NULL,
    approved_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_reimb_claim_tenant_employee_created
    ON payroll.employee_reimbursement_request (tenant_id, employee_id, created_at DESC);

CREATE INDEX idx_reimb_claim_tenant_status_bill
    ON payroll.employee_reimbursement_request (tenant_id, status, bill_date);

CREATE UNIQUE INDEX uk_reimb_claim_tenant_approval_instance
    ON payroll.employee_reimbursement_request (tenant_id, approval_instance_id)
    WHERE approval_instance_id IS NOT NULL;

-- Row-Level Security
ALTER TABLE payroll.employee_reimbursement_request ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_reimbursement_request
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
