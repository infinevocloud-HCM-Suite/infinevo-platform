-- Migration: V084__impersonation_session.sql
-- Schema: core
-- Purpose: Impersonation session table, RLS, and security definer functions (W-65.2)

-- 1. Table: core.impersonation_session
CREATE TABLE core.impersonation_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    platform_user_id UUID NOT NULL,
    target_user_account_id UUID NULL REFERENCES core.user_account(id),
    reason VARCHAR(200) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- 2. Indexes (DEBT-018: tenant_id leading)
CREATE INDEX idx_impersonation_session_tenant_id ON core.impersonation_session (tenant_id, id);
CREATE INDEX idx_impersonation_session_tenant_platform_started ON core.impersonation_session (tenant_id, platform_user_id, started_at DESC);

-- 3. Row-level security
ALTER TABLE core.impersonation_session ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.impersonation_session
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- 4. Permissions: app_user may not update or delete directly; ended_at is set only via close_impersonation
REVOKE UPDATE, DELETE ON core.impersonation_session FROM app_user;

-- 5. Functions (SECURITY DEFINER, schema-qualified, search_path = pg_catalog, pg_temp)

-- 5a. Open impersonation session
--     RETURNS TABLE makes tenant_id, session_id, expires_at etc. PL/pgSQL variables too, so every
--     column reference below is qualified; a bare tenant_id is "ambiguous" at call time.
CREATE OR REPLACE FUNCTION core.open_impersonation(
    p_target_tenant uuid,
    p_platform_user_id uuid,
    p_target_user_account_id uuid,
    p_target_email text,
    p_reason text
)
RETURNS TABLE (
    session_id uuid,
    tenant_id uuid,
    user_account_id uuid,
    user_email text,
    expires_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    v_target_account_id uuid := NULL;
    v_target_email text := NULL;
    v_session_id uuid;
    v_started_at timestamptz;
    v_expires_at timestamptz;
    v_user_count bigint;
BEGIN
    -- Refuse impersonation of the platform tenant itself
    IF p_target_tenant = '00000000-0000-0000-0000-000000000001'::uuid THEN
        RAISE EXCEPTION 'Cannot impersonate platform tenant' USING ERRCODE = 'P0001';
    END IF;

    -- Validate target tenant exists
    IF NOT EXISTS (SELECT 1 FROM core.tenant t WHERE t.tenant_id = p_target_tenant) THEN
        RAISE EXCEPTION 'Tenant not found: %', p_target_tenant USING ERRCODE = 'P0002';
    END IF;

    -- Validate reason is present and <= 200 chars
    IF p_reason IS NULL OR trim(p_reason) = '' THEN
        RAISE EXCEPTION 'Reason must not be blank' USING ERRCODE = 'P0003';
    END IF;

    IF length(trim(p_reason)) > 200 THEN
        RAISE EXCEPTION 'Reason must not exceed 200 characters' USING ERRCODE = 'P0003';
    END IF;

    -- Resolve target user
    IF p_target_user_account_id IS NOT NULL THEN
        SELECT ua.id, ua.email
        INTO v_target_account_id, v_target_email
        FROM core.user_account ua
        WHERE ua.tenant_id = p_target_tenant
          AND ua.id = p_target_user_account_id;

        IF v_target_account_id IS NULL THEN
            RAISE EXCEPTION 'Target user not found: %', p_target_user_account_id USING ERRCODE = 'P0004';
        END IF;
    ELSIF p_target_email IS NOT NULL AND trim(p_target_email) <> '' THEN
        SELECT ua.id, ua.email
        INTO v_target_account_id, v_target_email
        FROM core.user_account ua
        WHERE ua.tenant_id = p_target_tenant
          AND lower(ua.email) = lower(trim(p_target_email));

        IF v_target_account_id IS NULL THEN
            RAISE EXCEPTION 'Target user not found: %', p_target_email USING ERRCODE = 'P0004';
        END IF;
    ELSE
        -- Bootstrap session: neither user account ID nor email provided.
        -- Allowed only while the target tenant has zero core.user_account rows.
        SELECT count(*) INTO v_user_count
        FROM core.user_account ua
        WHERE ua.tenant_id = p_target_tenant;

        IF v_user_count > 0 THEN
            RAISE EXCEPTION 'Bootstrap session not allowed: tenant has existing user accounts' USING ERRCODE = 'P0005';
        END IF;

        v_target_account_id := NULL;
        v_target_email := NULL;
    END IF;

    v_session_id := gen_random_uuid();
    v_started_at := CURRENT_TIMESTAMP;
    v_expires_at := v_started_at + interval '30 minutes';

    INSERT INTO core.impersonation_session (
        id,
        tenant_id,
        platform_user_id,
        target_user_account_id,
        reason,
        started_at,
        expires_at,
        created_by,
        updated_by
    ) VALUES (
        v_session_id,
        p_target_tenant,
        p_platform_user_id,
        v_target_account_id,
        trim(p_reason),
        v_started_at,
        v_expires_at,
        'system',
        'system'
    );

    RETURN QUERY
    SELECT v_session_id, p_target_tenant, v_target_account_id, v_target_email, v_expires_at;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.open_impersonation(uuid, uuid, uuid, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.open_impersonation(uuid, uuid, uuid, text, text) TO app_user;

-- 5b. Resolve impersonation session
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

        SELECT coalesce(array_agg(DISTINCT ra.action_code::text ORDER BY ra.action_code::text), ARRAY[]::text[])
        INTO v_action_codes
        FROM core.user_role ur
        JOIN core.role_action ra
          ON ra.tenant_id = ur.tenant_id
         AND ra.role_id = ur.role_id
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

-- 5c. Close impersonation session
CREATE OR REPLACE FUNCTION core.close_impersonation(
    p_session_id uuid,
    p_platform_user_id uuid
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    UPDATE core.impersonation_session
    SET ended_at = CURRENT_TIMESTAMP,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = p_session_id
      AND platform_user_id = p_platform_user_id
      AND ended_at IS NULL;

    RETURN FOUND;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.close_impersonation(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.close_impersonation(uuid, uuid) TO app_user;
