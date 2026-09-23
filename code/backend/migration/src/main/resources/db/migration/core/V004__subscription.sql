-- Migration: V004__subscription.sql
-- Description: Core subscription and subscription module entitlement schema with row-level security isolation

CREATE TABLE core.subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_subscription_tenant_id ON core.subscription(tenant_id);

ALTER TABLE core.subscription ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.subscription
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN false
            WHEN current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );

CREATE TABLE core.subscription_module (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    subscription_id UUID NOT NULL REFERENCES core.subscription(id) ON DELETE CASCADE,
    module_code VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_subscription_module UNIQUE (subscription_id, module_code)
);

CREATE INDEX idx_subscription_module_tenant_id ON core.subscription_module(tenant_id);
CREATE INDEX idx_subscription_module_lookup ON core.subscription_module(tenant_id, module_code, status);

ALTER TABLE core.subscription_module ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.subscription_module
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN false
            WHEN current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );
