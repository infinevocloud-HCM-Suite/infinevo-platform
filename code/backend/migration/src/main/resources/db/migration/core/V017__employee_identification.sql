-- Migration: V017__employee_identification.sql
-- Description: W-13.2 core.employee_identification — identity documents for an employee, one optional row per employee, with row-level security isolation

CREATE TABLE core.employee_identification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    immigration_status VARCHAR(64),
    aadhaar_number VARCHAR(12),
    pan_number VARCHAR(10),
    personal_tax_id VARCHAR(32),
    social_insurance_number VARCHAR(64),
    id_proof_type VARCHAR(64),
    id_document_name VARCHAR(128),
    id_document_number VARCHAR(64),
    address_proof_type VARCHAR(64),
    address_document_name VARCHAR(128),
    address_document_number VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- HRMS is the whole shape here —
-- legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Identification.java:15-45.
-- Payroll carries PAN only, on the personal row (EmployeePersonalDetail.java:33-39), and that PAN
-- lands in this table rather than in V015: it is an identity document, and keeping the identity
-- documents in one place is what lets the audit trail and any future access rule name one table.

-- Spelling: aadhaar_number, not the frozen aadharCardNumber (Identification.java:20). The column
-- name is what the audit redaction deny-list matches on (AuditWriter.REDACTED_FRAGMENTS), and
-- "aadhaar" is the spelling that list holds.

-- Uniqueness is deliberately NOT asserted on pan_number or aadhaar_number. The frozen PAN column
-- is unique = true outright (EmployeePersonalDetail.java:33), which is globally unique across
-- every customer — the same defect W-13.1 corrected for employee_number (V010__employee.sql).
-- Tenant-scoped uniqueness would be the correct rule, but it cannot be turned on before the rows
-- that violate it are known: that is W-67's migration, and it is the ticket holding the data.

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
CREATE UNIQUE INDEX idx_employee_identification_tenant_employee ON core.employee_identification (tenant_id, employee_id);
-- The two lookups a payroll filing actually makes — "who is this PAN", "who is this Aadhaar" —
-- and both are tenant-scoped, so neither reaches another customer's rows. RLS applies to every
-- read of them regardless (spec §9, last risk).
CREATE INDEX idx_employee_identification_tenant_pan ON core.employee_identification (tenant_id, pan_number);
CREATE INDEX idx_employee_identification_tenant_aadhaar ON core.employee_identification (tenant_id, aadhaar_number);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.employee_identification ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.employee_identification
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
