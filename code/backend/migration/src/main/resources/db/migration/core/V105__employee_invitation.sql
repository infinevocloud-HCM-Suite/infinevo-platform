-- Migration: V105__employee_invitation.sql
-- Description: W-24.2 core.employee_invitation with row-level security and lookup function (CORE-18)

-- 1. core.employee_invitation — employee invitation with 7-day expiry and hashed single-use token
CREATE TABLE core.employee_invitation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    email VARCHAR(255) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ NULL,
    declined_at TIMESTAMPTZ NULL,
    decline_reason VARCHAR(500) NULL,
    revoked_at TIMESTAMPTZ NULL,
    superseded_by_id UUID NULL REFERENCES core.employee_invitation(id),
    invited_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Index on tenant_id plus lookup columns (DEBT-018)
CREATE INDEX idx_employee_invitation_tenant_email_status ON core.employee_invitation (tenant_id, email, status);
CREATE INDEX idx_employee_invitation_tenant_employee ON core.employee_invitation (tenant_id, employee_id);
CREATE INDEX idx_employee_invitation_tenant_id ON core.employee_invitation (tenant_id);

-- Global unique index on token_hash for unauthenticated acceptance lookup
CREATE UNIQUE INDEX idx_employee_invitation_token_hash ON core.employee_invitation (token_hash);

-- Row-level security for employee_invitation
ALTER TABLE core.employee_invitation ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.employee_invitation
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- 2. Security definer function for unauthenticated exact-token-hash lookup (W-24.2 §6)
CREATE OR REPLACE FUNCTION core.find_employee_invitation_by_token_hash(p_token_hash CHAR(64))
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
    created_at TIMESTAMPTZ,
    created_by VARCHAR(100),
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(100)
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = core, pg_temp
AS $$
    SELECT id, tenant_id, employee_id, email, token_hash, status, expires_at, accepted_at, declined_at, decline_reason, revoked_at, superseded_by_id, invited_by_user_id, created_at, created_by, updated_at, updated_by
    FROM core.employee_invitation
    WHERE token_hash = p_token_hash;
$$;

REVOKE EXECUTE ON FUNCTION core.find_employee_invitation_by_token_hash(CHAR(64)) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.find_employee_invitation_by_token_hash(CHAR(64)) TO app_user;
