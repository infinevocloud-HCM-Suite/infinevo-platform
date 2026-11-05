-- Migration: V188__subscription_plan.sql
-- Schema: core
-- Purpose: W-84.1 plan, support plan and employee limit on core.subscription; platform staff set them through
--          core.set_subscription_plan(); core.list_tenants() and core.get_tenant_overview() report the tenant's
--          employee count against its limit.
--
-- All three columns are nullable. NULL employee_limit means unlimited, and is what every existing tenant gets
-- (founder 2026-11-05). The plan is a label for billing; it fixes nothing. Expand only: the previous release
-- never reads these columns and its row mapper ignores the extra OUT columns.
--
-- employee_count uses the definition W-84.2 enforces: rows in core.employee that are not soft-deleted and not
-- TERMINATED. SUSPENDED employees count, because reactivating one is a plain status edit.

-- 1. The columns
ALTER TABLE core.subscription
    ADD COLUMN plan VARCHAR(16) NULL,
    ADD COLUMN support_plan VARCHAR(16) NULL,
    ADD COLUMN employee_limit INTEGER NULL;

ALTER TABLE core.subscription
    ADD CONSTRAINT ck_subscription_plan CHECK (plan IS NULL OR plan IN ('STARTER', 'GROWTH', 'ENTERPRISE')),
    ADD CONSTRAINT ck_subscription_support_plan CHECK (support_plan IS NULL OR support_plan IN ('STANDARD', 'PRIORITY')),
    ADD CONSTRAINT ck_subscription_employee_limit CHECK (employee_limit IS NULL OR employee_limit > 0);

-- 2. Cross-tenant setter for platform staff, on the pattern of core.set_subscription_status (V034).
--    Every argument is written as given: NULL clears the field.
CREATE OR REPLACE FUNCTION core.set_subscription_plan(
    p_tenant_id uuid,
    p_plan text,
    p_support_plan text,
    p_employee_limit integer
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    UPDATE core.subscription
    SET plan = p_plan,
        support_plan = p_support_plan,
        employee_limit = p_employee_limit,
        updated_at = now()
    WHERE tenant_id = p_tenant_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Subscription not found for tenant %', p_tenant_id;
    END IF;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.set_subscription_plan(uuid, text, text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.set_subscription_plan(uuid, text, text, integer) TO app_user;

-- 3. The tenant list reports employees against the limit. The OUT columns change, which CREATE OR REPLACE cannot
--    do, so each function is dropped first, as V159 did.
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
    support_plan varchar(16)
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
        s.support_plan
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
    support_plan varchar(16)
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
        lt.support_plan
    FROM core.list_tenants() lt
    WHERE lt.tenant_id = p_tenant_id;
$$;

REVOKE EXECUTE ON FUNCTION core.get_tenant_overview(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.get_tenant_overview(uuid) TO app_user;
