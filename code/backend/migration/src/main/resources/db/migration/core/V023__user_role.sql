-- Migration: V023__user_role.sql
-- Description: W-11.1 core.user_role — which roles a user holds in a tenant, with row-level security isolation

CREATE TABLE core.user_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    user_account_id UUID NOT NULL REFERENCES core.user_account(id),
    role_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    -- Same-tenant by construction: the role must belong to the tenant this grant row is in.
    CONSTRAINT user_role_role_fkey FOREIGN KEY (tenant_id, role_id) REFERENCES core.role (tenant_id, id)
);

-- core.user_account is already one row per (user, tenant) (V009), so user_account_id alone names
-- the tenant too. The foreign key stays on id because V009 declares no (tenant_id, id) key to
-- reference; the role side, which this script can protect, is the composite one above.

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- The unique index is also the (tenant_id, user_account_id) lookup: "what roles does this user hold".
CREATE UNIQUE INDEX idx_user_role_tenant_user_role ON core.user_role (tenant_id, user_account_id, role_id);
-- "Is this role granted to anyone" — the check that refuses DELETE /api/v1/roles/{id} (spec §4).
CREATE INDEX idx_user_role_tenant_role ON core.user_role (tenant_id, role_id);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.user_role ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.user_role
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
