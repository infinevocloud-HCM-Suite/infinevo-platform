-- Migration: V231__set_tenant_country.sql
-- Schema: core
-- Purpose: W-116 cross-tenant country setter for platform staff.
--          Updates core.tenant.country_code and updated_at.

CREATE OR REPLACE FUNCTION core.set_tenant_country(
    p_tenant_id uuid,
    p_country_code char(2)
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    UPDATE core.tenant
    SET country_code = p_country_code,
        updated_at = now()
    WHERE tenant_id = p_tenant_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Tenant not found: %', p_tenant_id;
    END IF;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.set_tenant_country(uuid, char(2)) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.set_tenant_country(uuid, char(2)) TO app_user;
