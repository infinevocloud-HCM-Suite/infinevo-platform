-- Migration: V012__designation.sql
-- Description: W-14.1 core.designation — the tenant's list of job designations, with row-level security isolation

CREATE TABLE core.designation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- A designation carries no level and no grade, by founder decision 1 (spec §13). Pay grade lives on
-- the employment record (core.employee_employment, W-13.2); a designation is an org master, not a
-- compensation band. Adding a level later is easy, removing one is not.

-- Converting HRMS's free-text jobTitle strings (Work.java) is NOT done here — that is W-67, and it
-- needs production data the repository does not hold (spec §2 Out of scope, §9).

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- code is unique WITHIN a tenant, never globally: one tenant's coding scheme must not block another's.
CREATE UNIQUE INDEX idx_designation_tenant_code ON core.designation (tenant_id, code);
CREATE INDEX idx_designation_tenant_is_active ON core.designation (tenant_id, is_active);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.designation ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.designation
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
