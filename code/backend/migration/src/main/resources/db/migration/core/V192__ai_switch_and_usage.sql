-- Migration: V192__ai_switch_and_usage.sql
-- Schema: core
-- Purpose: W-114.1 the AI helper switch and monthly request cap on core.subscription, set by platform staff through
--          core.set_subscription_ai(); core.ai_usage counts requests per tenant and calendar month (UTC);
--          core.list_tenants() and core.get_tenant_overview() report the switch, the cap and this month's usage.
--
-- Off for every customer (founder 2026-11-16): every existing row gets ai_enabled = false and a NULL cap. A cap is
-- required when the switch is on. Expand only: the previous release never reads these columns or the new table, and
-- its row mapper ignores the extra OUT columns, which are added at the end.

-- 1. The columns
ALTER TABLE core.subscription
    ADD COLUMN ai_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ai_monthly_request_cap INTEGER NULL;

-- 2. A cap is a whole number above 0, and the switch cannot be on without one.
ALTER TABLE core.subscription
    ADD CONSTRAINT ck_subscription_ai_monthly_request_cap
        CHECK (ai_monthly_request_cap IS NULL OR ai_monthly_request_cap > 0),
    ADD CONSTRAINT ck_subscription_ai_enabled_needs_cap
        CHECK (NOT ai_enabled OR ai_monthly_request_cap IS NOT NULL);

-- 3. Cross-tenant setter for platform staff, on the pattern of core.set_subscription_plan (V188).
--    Both arguments are written as given; switching off keeps whatever cap is passed.
CREATE OR REPLACE FUNCTION core.set_subscription_ai(
    p_tenant_id uuid,
    p_enabled boolean,
    p_cap integer
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    UPDATE core.subscription
    SET ai_enabled = p_enabled,
        ai_monthly_request_cap = p_cap,
        updated_at = now()
    WHERE tenant_id = p_tenant_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Subscription not found for tenant %', p_tenant_id;
    END IF;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.set_subscription_ai(uuid, boolean, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.set_subscription_ai(uuid, boolean, integer) TO app_user;

-- 4. Usage: one row per tenant and month. usage_month is the first day of the calendar month in UTC.
CREATE TABLE core.ai_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant (tenant_id) ON DELETE CASCADE,
    usage_month DATE NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0 CHECK (request_count >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5. The one lookup, and the conflict target of the counting statement. Leads with tenant_id, so it also indexes the FK.
CREATE UNIQUE INDEX uk_ai_usage_tenant_month ON core.ai_usage (tenant_id, usage_month);

-- 6. Row-level security, the standard policy (migration/README.md).
ALTER TABLE core.ai_usage ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.ai_usage
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- 7. The customer's own request adds to its count.
GRANT SELECT, INSERT, UPDATE ON core.ai_usage TO app_user;

-- 8. The tenant list reports the switch, the cap and this month's usage. The OUT columns change, which
--    CREATE OR REPLACE cannot do, so each function is dropped first, as V188 did. Rebuilt from V188, the newest
--    definition; the three new columns go at the end. The functions are SECURITY DEFINER and owned by
--    migration_user, the owner of core.ai_usage, so RLS does not hide other tenants' rows, as for core.employee.
DROP FUNCTION IF EXISTS core.get_tenant_overview(uuid);
DROP FUNCTION IF EXISTS core.list_tenants();

CREATE FUNCTION core.list_tenants()
RETURNS TABLE (
    tenant_id uuid,
    name text,
    country_code char(2),
    timezone varchar(64),
    status varchar(16),
    modules text[],
    created_at timestamptz,
    current_period_end date,
    user_count bigint,
    admin_invitation_email varchar(255),
    admin_invitation_status text,
    employee_count bigint,
    employee_limit integer,
    plan varchar(16),
    support_plan varchar(16),
    ai_enabled boolean,
    ai_monthly_request_cap integer,
    ai_usage_this_month integer
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
        ) AS user_count,
        ai.email AS admin_invitation_email,
        coalesce(ai.state, 'NONE') AS admin_invitation_status,
        (
            SELECT count(*)::bigint
            FROM core.employee e
            WHERE e.tenant_id = t.tenant_id
              AND e.is_deleted = false
              AND e.status <> 'TERMINATED'
        ) AS employee_count,
        s.employee_limit,
        s.plan,
        s.support_plan,
        coalesce(s.ai_enabled, false) AS ai_enabled,
        s.ai_monthly_request_cap,
        coalesce(
            (
                SELECT au.request_count
                FROM core.ai_usage au
                WHERE au.tenant_id = t.tenant_id
                  AND au.usage_month = date_trunc('month', now() AT TIME ZONE 'UTC')::date
            ),
            0
        ) AS ai_usage_this_month
    FROM core.tenant t
    LEFT JOIN core.subscription s ON s.tenant_id = t.tenant_id
    LEFT JOIN LATERAL (
        SELECT
            ui.email,
            CASE WHEN ui.status = 'ACCEPTED' THEN 'ACCEPTED' ELSE 'PENDING' END AS state
        FROM core.user_invitation ui
        JOIN core.user_invitation_role uir
          ON uir.tenant_id = ui.tenant_id
         AND uir.invitation_id = ui.id
        JOIN core.role r
          ON r.tenant_id = uir.tenant_id
         AND r.id = uir.role_id
         AND r.code = 'tenant-admin'
        WHERE ui.tenant_id = t.tenant_id
          AND (ui.status = 'ACCEPTED' OR (ui.status = 'PENDING' AND ui.expires_at > now()))
        ORDER BY (ui.status = 'ACCEPTED') DESC, ui.created_at DESC
        LIMIT 1
    ) ai ON true
    ORDER BY t.name;
$$;

REVOKE EXECUTE ON FUNCTION core.list_tenants() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.list_tenants() TO app_user;

CREATE FUNCTION core.get_tenant_overview(p_tenant_id uuid)
RETURNS TABLE (
    tenant_id uuid,
    name text,
    country_code char(2),
    timezone varchar(64),
    status varchar(16),
    modules text[],
    created_at timestamptz,
    current_period_end date,
    user_count bigint,
    admin_invitation_email varchar(255),
    admin_invitation_status text,
    employee_count bigint,
    employee_limit integer,
    plan varchar(16),
    support_plan varchar(16),
    ai_enabled boolean,
    ai_monthly_request_cap integer,
    ai_usage_this_month integer
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT
        lt.tenant_id,
        lt.name,
        lt.country_code,
        lt.timezone,
        lt.status,
        lt.modules,
        lt.created_at,
        lt.current_period_end,
        lt.user_count,
        lt.admin_invitation_email,
        lt.admin_invitation_status,
        lt.employee_count,
        lt.employee_limit,
        lt.plan,
        lt.support_plan,
        lt.ai_enabled,
        lt.ai_monthly_request_cap,
        lt.ai_usage_this_month
    FROM core.list_tenants() lt
    WHERE lt.tenant_id = p_tenant_id;
$$;

REVOKE EXECUTE ON FUNCTION core.get_tenant_overview(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.get_tenant_overview(uuid) TO app_user;
