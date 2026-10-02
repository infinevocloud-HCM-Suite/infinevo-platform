-- Migration: V113__employee_proof_item.sql
-- Description: W-34.1 payroll.employee_proof_item — one row per declared line that needs proof

CREATE TABLE payroll.employee_proof_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    proof_id UUID NOT NULL REFERENCES payroll.employee_proof_of_investment(id) ON DELETE CASCADE,
    source_kind VARCHAR(24) NOT NULL
        CHECK (source_kind IN ('HOUSE_RENT', 'HOME_LOAN_PRINCIPAL', 'HOME_LOAN_INTEREST',
                               'LET_OUT_PROPERTY', 'SECTION_6A', 'PREV_EMPLOYMENT')),
    source_line_id UUID NOT NULL,
    description VARCHAR(150) NOT NULL,
    declared_amount NUMERIC(19,4) NOT NULL CHECK (declared_amount >= 0),
    claimed_amount NUMERIC(19,4) CHECK (claimed_amount >= 0),
    approved_amount NUMERIC(19,4) CHECK (approved_amount >= 0),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'DISALLOWED', 'RETURNED')),
    employee_note VARCHAR(1000),
    reviewer_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_employee_proof_item_tenant_proof_source
    ON payroll.employee_proof_item (tenant_id, proof_id, source_kind, source_line_id);

ALTER TABLE payroll.employee_proof_item ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_proof_item
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
