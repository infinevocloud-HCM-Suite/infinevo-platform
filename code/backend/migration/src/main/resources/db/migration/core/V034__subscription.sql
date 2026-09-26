-- Migration: V034__subscription.sql
-- Schema: core
-- Purpose: Subscription, subscription modules, and cross-tenant provisioning functions (W-12.1)

-- 1. core.subscription
CREATE TABLE core.subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    status VARCHAR(16) NOT NULL,
    started_on DATE NOT NULL DEFAULT CURRENT_DATE,
    current_period_end DATE NULL,
    external_ref VARCHAR(128) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by TEXT NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by TEXT NOT NULL DEFAULT 'system',
    CONSTRAINT uk_subscription_tenant UNIQUE (tenant_id)
);

ALTER TABLE core.subscription ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.subscription
    USING (
        tenant_id = CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
            WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
            ELSE current_setting('app.current_tenant_id', true)::uuid
        END
    );

-- 2. core.subscription_module
CREATE TABLE core.subscription_module (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    subscription_id UUID NOT NULL REFERENCES core.subscription(id),
    module VARCHAR(16) NOT NULL,
    granted_on DATE NOT NULL DEFAULT CURRENT_DATE,
    revoked_on DATE NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by TEXT NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by TEXT NOT NULL DEFAULT 'system'
);

ALTER TABLE core.subscription_module ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.subscription_module
    USING (
        tenant_id = CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
            WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
            ELSE current_setting('app.current_tenant_id', true)::uuid
        END
    );

CREATE INDEX idx_subscription_module_tenant_subscription ON core.subscription_module (tenant_id, subscription_id);

CREATE UNIQUE INDEX uk_subscription_module_tenant_active_module ON core.subscription_module (tenant_id, module) WHERE revoked_on IS NULL;

-- 3. Cross-tenant SECURITY DEFINER functions for platform staff (W-12.1 §4, §6 decision 4)

-- 3a. Provision tenant
CREATE OR REPLACE FUNCTION core.provision_tenant(
    p_name text,
    p_country_code char(2),
    p_timezone varchar(64),
    p_leave_year_start_month smallint,
    p_modules text[]
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    v_tenant_id uuid := gen_random_uuid();
    v_sub_id uuid := gen_random_uuid();
    v_mod text;
BEGIN
    INSERT INTO core.tenant (
        tenant_id,
        name,
        country_code,
        timezone,
        leave_year_start_month,
        created_by,
        updated_by
    ) VALUES (
        v_tenant_id,
        p_name,
        coalesce(p_country_code, 'IN'),
        coalesce(p_timezone, 'Asia/Kolkata'),
        coalesce(p_leave_year_start_month, 4::smallint),
        'system',
        'system'
    );

    INSERT INTO core.subscription (
        id,
        tenant_id,
        status,
        started_on,
        created_by,
        updated_by
    ) VALUES (
        v_sub_id,
        v_tenant_id,
        'ACTIVE',
        CURRENT_DATE,
        'system',
        'system'
    );

    IF p_modules IS NOT NULL THEN
        FOREACH v_mod IN ARRAY p_modules LOOP
            IF v_mod IS NOT NULL AND trim(v_mod) <> '' THEN
                INSERT INTO core.subscription_module (
                    tenant_id,
                    subscription_id,
                    module,
                    granted_on,
                    created_by,
                    updated_by
                ) VALUES (
                    v_tenant_id,
                    v_sub_id,
                    trim(v_mod),
                    CURRENT_DATE,
                    'system',
                    'system'
                )
                ON CONFLICT DO NOTHING;
            END IF;
        END LOOP;
    END IF;

    RETURN v_tenant_id;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.provision_tenant(text, char(2), varchar(64), smallint, text[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.provision_tenant(text, char(2), varchar(64), smallint, text[]) TO app_user;

-- 3b. Set subscription modules (grants new, sets revoked_on on dropped, re-grants a revoked one in place)
CREATE OR REPLACE FUNCTION core.set_subscription_modules(
    p_tenant_id uuid,
    p_modules text[]
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    v_sub_id uuid;
    v_mod text;
BEGIN
    SELECT id INTO v_sub_id
    FROM core.subscription
    WHERE tenant_id = p_tenant_id;

    IF v_sub_id IS NULL THEN
        RAISE EXCEPTION 'Subscription not found for tenant %', p_tenant_id;
    END IF;

    -- Revoke modules that are currently active but not present in p_modules
    UPDATE core.subscription_module
    SET revoked_on = CURRENT_DATE,
        updated_at = now()
    WHERE tenant_id = p_tenant_id
      AND revoked_on IS NULL
      AND (p_modules IS NULL OR module <> ALL(p_modules));

    -- Grant or unrevoke modules present in p_modules
    IF p_modules IS NOT NULL THEN
        FOREACH v_mod IN ARRAY p_modules LOOP
            IF v_mod IS NOT NULL AND trim(v_mod) <> '' THEN
                -- Check if a revoked row exists for this module
                IF EXISTS (
                    SELECT 1 FROM core.subscription_module
                    WHERE tenant_id = p_tenant_id
                      AND module = trim(v_mod)
                      AND revoked_on IS NOT NULL
                ) THEN
                    -- Re-grant in place rather than duplicating
                    UPDATE core.subscription_module
                    SET revoked_on = NULL,
                        updated_at = now()
                    WHERE tenant_id = p_tenant_id
                      AND module = trim(v_mod);
                ELSIF NOT EXISTS (
                    SELECT 1 FROM core.subscription_module
                    WHERE tenant_id = p_tenant_id
                      AND module = trim(v_mod)
                      AND revoked_on IS NULL
                ) THEN
                    -- Insert new module
                    INSERT INTO core.subscription_module (
                        tenant_id,
                        subscription_id,
                        module,
                        granted_on,
                        created_by,
                        updated_by
                    ) VALUES (
                        p_tenant_id,
                        v_sub_id,
                        trim(v_mod),
                        CURRENT_DATE,
                        'system',
                        'system'
                    );
                END IF;
            END IF;
        END LOOP;
    END IF;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.set_subscription_modules(uuid, text[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.set_subscription_modules(uuid, text[]) TO app_user;

-- 3c. Set subscription status
CREATE OR REPLACE FUNCTION core.set_subscription_status(
    p_tenant_id uuid,
    p_status text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    UPDATE core.subscription
    SET status = p_status,
        updated_at = now()
    WHERE tenant_id = p_tenant_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Subscription not found for tenant %', p_tenant_id;
    END IF;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.set_subscription_status(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.set_subscription_status(uuid, text) TO app_user;
