-- W-73.4 merge review: Disable / Enable on Users & access.
--
-- 1. core.keycloak_user_account_states — how many OTHER core.user_account rows, in any tenant, share one
--    Keycloak user, split by status. One Keycloak user may hold a row in several tenants (V009: one row per
--    tenant and user; invitations reuse an existing Keycloak user). Disable turns the realm-wide Keycloak
--    flag off only when no other row is ACTIVE, and Enable turns it on only when no other row is DISABLED;
--    otherwise the per-tenant status alone decides. Row-level security hides other tenants from app_user,
--    so this is SECURITY DEFINER and returns two counts for one Keycloak user id — never a tenant id, an
--    email or a row.
--
-- 2. core.resolve_impersonation (V084) again, unchanged except that a target account whose status is not
--    ACTIVE grants no action: impersonating a disabled user must not act with its roles. V084 is applied
--    and is never edited; this replaces the function.

CREATE OR REPLACE FUNCTION core.keycloak_user_account_states(
    p_keycloak_user_id uuid,
    p_exclude_user_account_id uuid
)
RETURNS TABLE (
    active_elsewhere integer,
    disabled_elsewhere integer
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT
        count(*) FILTER (WHERE ua.status = 'ACTIVE')::integer,
        count(*) FILTER (WHERE ua.status <> 'ACTIVE')::integer
    FROM core.user_account ua
    WHERE ua.keycloak_user_id = p_keycloak_user_id
      AND ua.id <> p_exclude_user_account_id;
$$;

REVOKE EXECUTE ON FUNCTION core.keycloak_user_account_states(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.keycloak_user_account_states(uuid, uuid) TO app_user;

CREATE OR REPLACE FUNCTION core.resolve_impersonation(
    p_session_id uuid,
    p_platform_user_id uuid
)
RETURNS TABLE (
    session_id uuid,
    tenant_id uuid,
    platform_user_id uuid,
    target_user_account_id uuid,
    target_email text,
    action_codes text[]
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    v_session record;
    v_target_email text := NULL;
    v_action_codes text[] := ARRAY[]::text[];
BEGIN
    SELECT s.id, s.tenant_id, s.platform_user_id, s.target_user_account_id
    INTO v_session
    FROM core.impersonation_session s
    WHERE s.id = p_session_id
      AND s.platform_user_id = p_platform_user_id
      AND s.ended_at IS NULL
      AND s.expires_at > CURRENT_TIMESTAMP;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    IF v_session.target_user_account_id IS NOT NULL THEN
        SELECT ua.email INTO v_target_email
        FROM core.user_account ua
        WHERE ua.tenant_id = v_session.tenant_id
          AND ua.id = v_session.target_user_account_id;

        -- W-73.4: a disabled target account holds no action
        SELECT coalesce(array_agg(DISTINCT ra.action_code::text ORDER BY ra.action_code::text), ARRAY[]::text[])
        INTO v_action_codes
        FROM core.user_role ur
        JOIN core.role_action ra
          ON ra.tenant_id = ur.tenant_id
         AND ra.role_id = ur.role_id
        JOIN core.user_account ua
          ON ua.tenant_id = ur.tenant_id
         AND ua.id = ur.user_account_id
         AND ua.status = 'ACTIVE'
        WHERE ur.tenant_id = v_session.tenant_id
          AND ur.user_account_id = v_session.target_user_account_id;
    ELSE
        -- Bootstrap session: returns distinct action codes of the tenant-admin role
        SELECT coalesce(array_agg(DISTINCT ra.action_code::text ORDER BY ra.action_code::text), ARRAY[]::text[])
        INTO v_action_codes
        FROM core.role r
        JOIN core.role_action ra
          ON ra.tenant_id = r.tenant_id
         AND ra.role_id = r.id
        WHERE r.tenant_id = v_session.tenant_id
          AND r.code = 'tenant-admin'
          AND r.is_system;
    END IF;

    RETURN QUERY
    SELECT
        v_session.id,
        v_session.tenant_id,
        v_session.platform_user_id,
        v_session.target_user_account_id,
        v_target_email,
        v_action_codes;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.resolve_impersonation(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.resolve_impersonation(uuid, uuid) TO app_user;
