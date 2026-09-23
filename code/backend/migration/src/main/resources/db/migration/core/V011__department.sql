-- Migration: V011__department.sql
-- Description: W-14.1 core.department — the tenant's flat list of departments, with row-level security isolation

CREATE TABLE core.department (
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

-- Departments are flat by founder decision 2 (spec §13): neither frozen product nests them, and a
-- nullable parent column is cheap to add later but expensive to un-model once screens assume a tree.
-- There is deliberately no parent_id here.

-- is_active is Payroll's status flag, kept on purpose (spec §4): deactivating hides a department
-- from new assignments without breaking the employees already pointing at it. Deletion is refused
-- while an employee holds the record.

-- Converting HRMS's free-text department strings (Work.java) is NOT done here — that is W-67, and
-- it needs production data the repository does not hold (spec §2 Out of scope, §9).

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- code is unique WITHIN a tenant, never globally: one tenant's coding scheme must not block another's.
CREATE UNIQUE INDEX idx_department_tenant_code ON core.department (tenant_id, code);
CREATE INDEX idx_department_tenant_is_active ON core.department (tenant_id, is_active);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.department ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.department
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
