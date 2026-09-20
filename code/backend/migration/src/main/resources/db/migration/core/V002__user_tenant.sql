-- Migration: V002__user_tenant.sql
-- Description: User-Tenant membership mapping table with row-level security isolation

CREATE TABLE core.user_tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_user_tenant_tenant_id ON core.user_tenant(tenant_id);
CREATE INDEX idx_user_tenant_user_id ON core.user_tenant(user_id);
CREATE UNIQUE INDEX idx_user_tenant_unique ON core.user_tenant(user_id, tenant_id);

ALTER TABLE core.user_tenant ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.user_tenant
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN false
            WHEN current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );

-- Helper function to retrieve user tenant memberships across RLS for single-tenant auto-binding
CREATE OR REPLACE FUNCTION core.get_user_tenants(p_user_id UUID)
RETURNS TABLE (tenant_id UUID)
LANGUAGE sql
SECURITY DEFINER
SET search_path = core, pg_temp
AS $$
    SELECT tenant_id FROM core.user_tenant WHERE user_id = p_user_id;
$$;

REVOKE EXECUTE ON FUNCTION core.get_user_tenants(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.get_user_tenants(UUID) TO app_user;
