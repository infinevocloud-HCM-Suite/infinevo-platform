-- Migration: V009__user_account.sql
-- Description: Local user profile, one row per (user, tenant), synced from Keycloak token claims, with row-level security isolation

CREATE TABLE core.user_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    keycloak_user_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone_number VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Two columns of the legacy tables this merges are deliberately absent, and must stay absent:
--   password         — Keycloak is the sole credential authority (01-platform-shape.md:63).
--   blacklisted_token — tokens are stateless and validated against JWKS; there is nothing to blacklist.

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
CREATE UNIQUE INDEX idx_user_account_tenant_keycloak_user ON core.user_account (tenant_id, keycloak_user_id);
CREATE INDEX idx_user_account_tenant_email ON core.user_account (tenant_id, email);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.user_account ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.user_account
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
