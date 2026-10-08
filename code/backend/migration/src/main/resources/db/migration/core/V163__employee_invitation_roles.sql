-- Migration: V163__employee_invitation_roles.sql
-- Description: W-73.3 core.employee_invitation.role_ids — the extra roles granted with 'employee' on acceptance

-- 1. The roles an employee invitation carries. Empty for every row written before this script, so
--    acceptance of an older invitation still grants 'employee' and nothing else.
ALTER TABLE core.employee_invitation
    ADD COLUMN role_ids UUID[] NOT NULL DEFAULT '{}';

-- 2. The security-definer lookup returns the whole row, so its declared columns must carry the new
--    one. A function's return type cannot change under CREATE OR REPLACE: drop and recreate.
DROP FUNCTION IF EXISTS core.find_employee_invitation_by_token_hash(CHAR(64));

CREATE FUNCTION core.find_employee_invitation_by_token_hash(p_token_hash CHAR(64))
RETURNS TABLE (
    id UUID,
    tenant_id UUID,
    employee_id UUID,
    email VARCHAR(255),
    token_hash CHAR(64),
    status VARCHAR(16),
    expires_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    declined_at TIMESTAMPTZ,
    decline_reason VARCHAR(500),
    revoked_at TIMESTAMPTZ,
    superseded_by_id UUID,
    invited_by_user_id UUID,
    role_ids UUID[],
    created_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100)
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = core, pg_temp
AS $$
    SELECT id, tenant_id, employee_id, email, token_hash, status, expires_at, accepted_at, declined_at, decline_reason, revoked_at, superseded_by_id, invited_by_user_id, role_ids, created_at, created_by, updated_at, updated_by
    FROM core.employee_invitation
    WHERE token_hash = p_token_hash;
$$;

REVOKE EXECUTE ON FUNCTION core.find_employee_invitation_by_token_hash(CHAR(64)) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.find_employee_invitation_by_token_hash(CHAR(64)) TO app_user;
