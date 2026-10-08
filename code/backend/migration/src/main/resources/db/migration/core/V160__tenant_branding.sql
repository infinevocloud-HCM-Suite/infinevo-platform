-- Migration: V160__tenant_branding.sql
-- Schema: core
-- Purpose: W-73.1 header branding — a logo and a tagline on core.tenant, and the TENANT_LOGO document kind.
--
-- Both columns are NULLABLE: every tenant already exists and none has a logo or a tagline yet. The
-- header falls back to the company's initials when logo_document_id is null and hides the tagline
-- when it is null (spec section 10, rollback). The table's RLS policy (V001) is unchanged: the
-- bound tenant reads and updates its own row only.
--
-- The logo is a row in core.document (W-21) of kind TENANT_LOGO, filed against the tenant rather
-- than an employee (employee_id null). The foreign key is checked as the table owner with row
-- security off, so it alone would accept another tenant's document — TenantProfileServiceImpl
-- looks the document up in the bound tenant before writing the reference, as DocumentServiceImpl
-- does for employee_id.

ALTER TABLE core.tenant
    ADD COLUMN logo_document_id uuid NULL REFERENCES core.document (id),
    ADD COLUMN tagline text NULL;

-- The bound TenantProfileServiceImpl checks, so a row written by any other path cannot hold a
-- value the API would refuse. The service check is what produces a 400 naming the field; this is
-- the backstop.
ALTER TABLE core.tenant
    ADD CONSTRAINT ck_tenant_tagline_length CHECK (tagline IS NULL OR char_length(tagline) <= 80);

-- core.document.kind accepts TENANT_LOGO. Widening only, as V108 did: the seven existing kinds are
-- kept, so every row already stored still passes. V037 and V108 are not edited — an applied
-- script's checksum must not change.
ALTER TABLE core.document DROP CONSTRAINT document_kind_check;

ALTER TABLE core.document
    ADD CONSTRAINT document_kind_check CHECK (kind IN (
        'EMPLOYEE_DOCUMENT', 'LEAVE_ATTACHMENT', 'REIMBURSEMENT_RECEIPT',
        'INVESTMENT_PROOF', 'PAYSLIP', 'EXPORT', 'FORM16_PART_A', 'TENANT_LOGO'));
