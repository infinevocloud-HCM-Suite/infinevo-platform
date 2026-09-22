-- Migration: V003__user_account.sql
-- Description: Core user account master profile synchronized from Keycloak OIDC identity provider

CREATE TABLE core.user_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    keycloak_sub VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX idx_user_account_keycloak_sub ON core.user_account(keycloak_sub);
CREATE INDEX idx_user_account_tenant_id ON core.user_account(tenant_id);
CREATE INDEX idx_user_account_tenant_email ON core.user_account(tenant_id, email);

ALTER TABLE core.user_account ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.user_account
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN false
            WHEN current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );
