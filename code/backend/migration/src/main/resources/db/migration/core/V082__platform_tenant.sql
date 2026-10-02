-- Migration: V082__platform_tenant.sql
-- Schema: core
-- Purpose: Infinevo platform tenant and cross-tenant overview functions (W-65.1)

-- 1. Insert the Infinevo platform tenant
INSERT INTO core.tenant (
    tenant_id,
    name,
    country_code,
    timezone,
    leave_year_start_month,
    created_by,
    updated_by
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Infinevo',
    'IN',
    'Asia/Kolkata',
    4,
    'migration',
    'migration'
) ON CONFLICT (tenant_id) DO NOTHING;

-- 2. Insert subscription for the platform tenant (active, no modules)
INSERT INTO core.subscription (
    tenant_id,
    status,
    created_by,
    updated_by
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'active',
    'migration',
    'migration'
) ON CONFLICT (tenant_id) DO NOTHING;

-- 3. Seed system roles for the platform tenant so platform-admin exists in it
SELECT core.seed_system_roles('00000000-0000-0000-0000-000000000001');

-- 3a. Grant core.tenant.provision to platform-admin in the platform tenant only. V025 took it out of
--     every tenant-seeded role ("provisioning grants that action elsewhere"), and this is that place:
--     without it no one anywhere holds the action, and GET/POST /tenants refuse platform staff too.
--     Customer tenants still never hold it.
INSERT INTO core.role_action (tenant_id, role_id, action_code)
SELECT r.tenant_id, r.id, 'core.tenant.provision'
FROM core.role r
WHERE r.tenant_id = '00000000-0000-0000-0000-000000000001'
  AND r.code = 'platform-admin'
  AND r.is_system
ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;

-- 4. Cross-tenant SECURITY DEFINER function to list all tenants
CREATE OR REPLACE FUNCTION core.list_tenants()
RETURNS TABLE (
    tenant_id uuid,
    name text,
    country_code char(2),
    timezone varchar(64),
    status varchar(16),
    modules text[],
    created_at timestamptz,
    current_period_end date,
    user_count bigint
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT
        t.tenant_id,
        t.name,
        t.country_code,
        t.timezone,
        s.status,
        coalesce(
            (
                SELECT array_agg(sm.module::text ORDER BY sm.module)
                FROM core.subscription_module sm
                WHERE sm.tenant_id = t.tenant_id
                  AND sm.revoked_on IS NULL
            ),
            ARRAY[]::text[]
        ) AS modules,
        t.created_at,
        s.current_period_end,
        (
            SELECT count(*)::bigint
            FROM core.user_account ua
            WHERE ua.tenant_id = t.tenant_id
        ) AS user_count
    FROM core.tenant t
    LEFT JOIN core.subscription s ON s.tenant_id = t.tenant_id
    ORDER BY t.name;
$$;

REVOKE EXECUTE ON FUNCTION core.list_tenants() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.list_tenants() TO app_user;

-- 5. Cross-tenant SECURITY DEFINER function to get single tenant overview
CREATE OR REPLACE FUNCTION core.get_tenant_overview(p_tenant_id uuid)
RETURNS TABLE (
    tenant_id uuid,
    name text,
    country_code char(2),
    timezone varchar(64),
    status varchar(16),
    modules text[],
    created_at timestamptz,
    current_period_end date,
    user_count bigint
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT
        t.tenant_id,
        t.name,
        t.country_code,
        t.timezone,
        s.status,
        coalesce(
            (
                SELECT array_agg(sm.module::text ORDER BY sm.module)
                FROM core.subscription_module sm
                WHERE sm.tenant_id = t.tenant_id
                  AND sm.revoked_on IS NULL
            ),
            ARRAY[]::text[]
        ) AS modules,
        t.created_at,
        s.current_period_end,
        (
            SELECT count(*)::bigint
            FROM core.user_account ua
            WHERE ua.tenant_id = t.tenant_id
        ) AS user_count
    FROM core.tenant t
    LEFT JOIN core.subscription s ON s.tenant_id = t.tenant_id
    WHERE t.tenant_id = p_tenant_id;
$$;

REVOKE EXECUTE ON FUNCTION core.get_tenant_overview(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.get_tenant_overview(uuid) TO app_user;
