-- Migration: V021__role.sql
-- Description: W-11.1 core.role — a tenant's roles, seven of them seeded per tenant, with row-level security isolation

CREATE TABLE core.role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- tenant_id is NOT NULL on purpose: HRMS roles are global (its role entity has no organisation
-- column), so a role defined for one employer is a role for all of them (spec §1, §12). Here a
-- role always belongs to exactly one tenant, which is Payroll's shape.

-- is_system marks the seven roles core.seed_system_roles (V022) creates for every tenant. The
-- application refuses to edit or delete them (spec §7); the column is what it checks.

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- code is unique WITHIN a tenant, never globally: every tenant has its own 'hr'.
CREATE UNIQUE INDEX idx_role_tenant_code ON core.role (tenant_id, code);

-- The target of the composite foreign keys on core.role_action and core.user_role. Referencing
-- (tenant_id, id) rather than id alone makes a link from one tenant's row to another tenant's role
-- impossible at the database, whatever the application does.
CREATE UNIQUE INDEX idx_role_tenant_id ON core.role (tenant_id, id);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.role ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.role
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
