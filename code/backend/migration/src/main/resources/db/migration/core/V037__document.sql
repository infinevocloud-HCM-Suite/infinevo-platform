-- Migration: V037__document.sql
-- Description: W-21 core.document — one blob pointer for every file in the platform, with or without an employee, with row-level security isolation

CREATE TABLE core.document (
    -- Assigned by the service, not defaulted here: blob_path names the id, and the blob is written
    -- before the row (W-21 spec section 4), so the id has to exist before either.
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    -- NULL for a document that belongs to the tenant rather than to a person — an export (W-23.1).
    employee_id UUID NULL REFERENCES core.employee(id),
    kind VARCHAR(32) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    blob_container VARCHAR(64) NOT NULL,
    blob_path VARCHAR(512) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    -- The vocabulary is checked here as well as in Java. core.employee.status has no such check, and
    -- a row written outside the service then fails at READ as a Hibernate enum conversion error —
    -- the gap active-work.md records against W-13.1. New kinds arrive with a migration anyway.
    CONSTRAINT document_kind_check CHECK (kind IN (
        'EMPLOYEE_DOCUMENT', 'LEAVE_ATTACHMENT', 'REIMBURSEMENT_RECEIPT',
        'INVESTMENT_PROOF', 'PAYSLIP', 'EXPORT')),
    CONSTRAINT document_size_check CHECK (size_bytes >= 0)
);

-- There is no url column, and there must never be one. Every legacy table this replaces stores a
-- full Cloudinary URL (employee_document, leave_documents, employee_reimbursement_request and four
-- more — W-21 spec section 1), and a URL is a credential: that is what made every file public. A
-- container and a path are not; a link is issued per request, signed and expiring, by
-- DocumentLinkService.

-- blob_path is {tenantId}/{employeeId}/{kind}/{documentId}, or {tenantId}/tenant/{kind}/{documentId}
-- when there is no employee. Tenant first, as the legacy layout already was
-- (legacy/Payroll-Bend-SBoot/.../serviceimpl/CloudinaryServiceImpl.java:92).

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- The first also serves the foreign key on employee_id.
CREATE INDEX idx_document_tenant_employee_kind ON core.document (tenant_id, employee_id, kind);
CREATE UNIQUE INDEX uk_document_tenant_blob_path ON core.document (tenant_id, blob_path);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.document ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.document
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Deletion is soft, by decision (W-21 spec section 13, decision 2): the row is flagged and the blob
-- retained, because an audit trail over a deleted proof of investment is worth more than the
-- storage. app_user may SELECT, INSERT and UPDATE — the soft delete is an UPDATE — and never DELETE.
-- A REVOKE rather than a narrower GRANT, for the reason V008__audit_log.sql gives:
-- infra/postgres/03-grants.sql has already granted DELETE on every new core table by default.
-- Hard deletion, when retention asks for it, is W-22.2's and runs as the owner.
REVOKE DELETE ON core.document FROM app_user;

-- ─────────────────────────────────────────────────────────────────────────────
-- Who holds the document codes beyond the two admin roles. W-11.3 added the four codes and left
-- them with platform-admin and tenant-admin only; "each feature ticket grants its own code to its
-- functional role in its own script" (W-11.3 spec section 2). This is W-21's:
--
--   hr        core.document.read, core.document.upload   HR files and reads employee documents
--   employee  core.document.read_own                     an employee reads their own, and never a
--                                                        document with no employee_id (spec section 4)
--
-- core.document.delete stays with the admin roles: soft delete by a tenant administrator (decision 2).
--
-- A function and a trigger of their own rather than a replacement of core.seed_system_roles: every
-- lane granting its codes would otherwise rewrite that one function, and whichever merged last would
-- drop the others' grants. PostgreSQL fires AFTER INSERT triggers on one table in name order, and
-- tenant_seed_system_roles_document sorts after tenant_seed_system_roles (V022), so the roles exist
-- when this runs. Only system roles are granted, as V022 does.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION core.grant_document_actions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('hr', 'core.document.read'),
        ('hr', 'core.document.upload'),
        ('employee', 'core.document.read_own')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_document_actions(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION core.tenant_grant_document_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_document_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_document_actions() FROM PUBLIC;

CREATE TRIGGER tenant_seed_system_roles_document
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_document_actions();

-- Tenants that exist before this script ran get the grants too.
SELECT core.grant_document_actions(t.tenant_id) FROM core.tenant t;
