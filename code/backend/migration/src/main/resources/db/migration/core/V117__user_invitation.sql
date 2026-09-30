-- Migration: V117__user_invitation.sql
-- Description: W-24.2 core.user_invitation and core.user_invitation_role with row-level security and lookup function (CORE-18)

-- 1. core.user_invitation — company user invitation with 7-day expiry and hashed single-use token
CREATE TABLE core.user_invitation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    email VARCHAR(255) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ NULL,
    declined_at TIMESTAMPTZ NULL,
    decline_reason VARCHAR(500) NULL,
    revoked_at TIMESTAMPTZ NULL,
    superseded_by_id UUID NULL REFERENCES core.user_invitation(id),
    invited_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Index on tenant_id plus lookup columns (DEBT-018)
CREATE INDEX idx_user_invitation_tenant_email_status ON core.user_invitation (tenant_id, email, status);
CREATE INDEX idx_user_invitation_tenant_id ON core.user_invitation (tenant_id);

-- Global unique index on token_hash for unauthenticated acceptance lookup
CREATE UNIQUE INDEX idx_user_invitation_token_hash ON core.user_invitation (token_hash);

-- Row-level security for user_invitation
ALTER TABLE core.user_invitation ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.user_invitation
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- 2. core.user_invitation_role — join table for roles granted upon invitation acceptance
CREATE TABLE core.user_invitation_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    invitation_id UUID NOT NULL REFERENCES core.user_invitation(id) ON DELETE CASCADE,
    role_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT user_invitation_role_role_fkey FOREIGN KEY (tenant_id, role_id) REFERENCES core.role (tenant_id, id),
    CONSTRAINT uq_user_invitation_role UNIQUE (tenant_id, invitation_id, role_id)
);

CREATE INDEX idx_user_invitation_role_tenant_invitation ON core.user_invitation_role (tenant_id, invitation_id);
CREATE INDEX idx_user_invitation_role_tenant_role ON core.user_invitation_role (tenant_id, role_id);

-- Row-level security for user_invitation_role
ALTER TABLE core.user_invitation_role ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.user_invitation_role
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- 3. Security definer function for unauthenticated exact-token-hash lookup (W-24.2 §6)
CREATE OR REPLACE FUNCTION core.find_user_invitation_by_token_hash(p_token_hash CHAR(64))
RETURNS TABLE (
    id UUID,
    tenant_id UUID,
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
    SELECT id, tenant_id, email, token_hash, status, expires_at, accepted_at, declined_at, decline_reason, revoked_at, superseded_by_id, invited_by_user_id, created_at, created_by, updated_at, updated_by
    FROM core.user_invitation
    WHERE token_hash = p_token_hash;
$$;

REVOKE EXECUTE ON FUNCTION core.find_user_invitation_by_token_hash(CHAR(64)) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.find_user_invitation_by_token_hash(CHAR(64)) TO app_user;
