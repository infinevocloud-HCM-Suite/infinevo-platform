-- Migration: V200__tenant_option.sql
-- Description: W-104 Tenant options — one typed place for per-customer switches

-- 1. Table: core.tenant_option
CREATE TABLE core.tenant_option (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    option_key VARCHAR(100) NOT NULL,
    option_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- 2. Unique index per tenant and option key
CREATE UNIQUE INDEX uk_tenant_option_tenant_key ON core.tenant_option (tenant_id, option_key);

-- 3. Row-level security
ALTER TABLE core.tenant_option ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.tenant_option
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- 4. Grants for app_user
GRANT SELECT, INSERT, UPDATE, DELETE ON core.tenant_option TO app_user;

-- 5. Seed reference.action catalogue
INSERT INTO reference.action (code, name, module, description) VALUES
    ('core.tenant_option.read', 'View tenant options', 'core', 'Read configured per-customer options and defaults'),
    ('core.tenant_option.manage', 'Manage tenant options', 'core', 'Update and reset per-customer options and switches')
ON CONFLICT (code) DO NOTHING;

-- 6. Function to grant actions per tenant
CREATE OR REPLACE FUNCTION core.grant_tenant_option_actions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF p_tenant_id = '00000000-0000-0000-0000-000000000001'::uuid THEN
        RETURN;
    END IF;

    -- Role action grants: platform-admin (read, manage), tenant-admin (read, manage), hr (read), payroll-officer (read)
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('platform-admin', 'core.tenant_option.read'),
        ('platform-admin', 'core.tenant_option.manage'),
        ('tenant-admin', 'core.tenant_option.read'),
        ('tenant-admin', 'core.tenant_option.manage'),
        ('hr', 'core.tenant_option.read'),
        ('payroll-officer', 'core.tenant_option.read')
    ) AS grants (role_code, action_code)
    JOIN core.role r
        ON r.tenant_id = p_tenant_id
       AND r.code = grants.role_code
       AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_tenant_option_actions(UUID) FROM PUBLIC;

-- 7. Trigger for new tenants
CREATE OR REPLACE FUNCTION core.tenant_grant_tenant_option_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_tenant_option_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_tenant_option_actions() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_system_roles_tenant_option ON core.tenant;
CREATE TRIGGER tenant_seed_system_roles_tenant_option
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_tenant_option_actions();

-- 8. Backfill existing non-platform tenants
SELECT core.grant_tenant_option_actions(t.tenant_id) FROM core.tenant t;
