-- Migration: V159__list_tenants_admin_invitation.sql
-- Schema: core
-- Purpose: D-42 core.list_tenants() and core.get_tenant_overview() also return the state of the
--          tenant's administrator invitation, so platform staff see "invited" / "accepted" without
--          acting as the tenant.
--
-- The invitation rows sit behind row-level security on core.user_invitation (V117); the platform
-- tenant cannot read another tenant's rows. Both functions stay SECURITY DEFINER, as V082 made them.
--
-- admin_invitation_status is one of:
--   ACCEPTED  some invitation carrying the tenant-admin role was accepted
--   PENDING   none accepted, and the latest live (PENDING, unexpired) tenant-admin invitation is open
--   NONE      neither
-- admin_invitation_email is the email of the invitation the status describes, NULL for NONE.
--
-- The OUT columns change, which CREATE OR REPLACE cannot do, so each function is dropped first.

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
    admin_invitation_status text
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
        coalesce(ai.state, 'NONE') AS admin_invitation_status
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

DROP FUNCTION IF EXISTS core.get_tenant_overview(uuid);

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
    admin_invitation_status text
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
        lt.admin_invitation_status
    FROM core.list_tenants() lt
    WHERE lt.tenant_id = p_tenant_id;
$$;

REVOKE EXECUTE ON FUNCTION core.get_tenant_overview(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.get_tenant_overview(uuid) TO app_user;
