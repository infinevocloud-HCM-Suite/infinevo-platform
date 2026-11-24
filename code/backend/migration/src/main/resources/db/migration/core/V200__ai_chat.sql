-- Migration: V200__ai_chat.sql
-- Schema: core
-- Purpose: W-114.3 the AI helper's chat store - core.ai_session, core.ai_message and core.ai_action_intent - and
--          the action core.ai.use, granted to every system role of every customer tenant.
--
-- Expand only: three new tables, one new action and its grants. The previous release reads none of them.
-- core.ai.use lets a user open the helper; whether the customer has the helper at all is the switch on
-- core.subscription (V192). Both must hold.

-- 1. The three tables, in the order of the spec.
--    user_id is the signed-in user's token subject; employee_id is empty for a user with no employee record.
CREATE TABLE core.ai_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    user_id UUID NOT NULL,
    employee_id UUID NULL REFERENCES core.employee(id),
    title VARCHAR(200) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ NULL
);

CREATE TABLE core.ai_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    session_id UUID NOT NULL REFERENCES core.ai_session(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'TOOL')),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- message_id is the assistant message that proposed the action; W-114.8 fills it.
CREATE TABLE core.ai_action_intent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    session_id UUID NOT NULL REFERENCES core.ai_session(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    message_id UUID NULL REFERENCES core.ai_message(id) ON DELETE SET NULL,
    action_code VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROPOSED'
        CHECK (status IN ('PROPOSED', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'FAILED')),
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ NULL,
    result JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Indexes. Each leads with tenant_id (migration/README.md, DEBT-018).
CREATE INDEX idx_ai_session_tenant_user ON core.ai_session (tenant_id, user_id, last_message_at DESC);
CREATE INDEX idx_ai_message_tenant_session ON core.ai_message (tenant_id, session_id, created_at);
CREATE INDEX idx_ai_intent_tenant_session ON core.ai_action_intent (tenant_id, session_id);

-- 3. Row-level security, the standard policy (V094:30-39).
ALTER TABLE core.ai_session ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.ai_session
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

ALTER TABLE core.ai_message ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.ai_message
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

ALTER TABLE core.ai_action_intent ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.ai_action_intent
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- 4. Grants (V179:38). The same four rights ALTER DEFAULT PRIVILEGES gives app_user on new core tables
--    (infra/postgres/03-grants.sql:29-30), so nothing further needs revoking.
GRANT SELECT, INSERT, UPDATE, DELETE ON core.ai_session TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON core.ai_message TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON core.ai_action_intent TO app_user;

-- 5. The action, before any grant of it (V184:5-13).
INSERT INTO reference.action (code, name, module, description) VALUES
    ('core.ai.use', 'Use the AI helper', 'core', 'Open the AI helper and keep own chat sessions')
ON CONFLICT (code) DO NOTHING;

-- 6. Grant function (V184:16-82, without its approval seed). Every system role of the tenant, named by is_system
--    alone, so no role code is listed. The platform tenant is skipped: V158 refuses any grant there outside
--    core.tenant.*, core.audit.read and core.user.manage.
CREATE OR REPLACE FUNCTION core.grant_ai_actions(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF p_tenant_id = '00000000-0000-0000-0000-000000000001'::uuid THEN
        RETURN;
    END IF;

    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, 'core.ai.use'
    FROM core.role r
    WHERE r.tenant_id = p_tenant_id
      AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.grant_ai_actions(UUID) FROM PUBLIC;

-- 7. Trigger for tenants created later (V184:85-104). Postgres fires AFTER triggers of one event in name order,
--    so tenant_seed_system_roles (V022), which creates the roles, runs before tenant_seed_system_roles_ai.
CREATE OR REPLACE FUNCTION core.tenant_grant_ai_actions()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM core.grant_ai_actions(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.tenant_grant_ai_actions() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_system_roles_ai ON core.tenant;
CREATE TRIGGER tenant_seed_system_roles_ai
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION core.tenant_grant_ai_actions();

-- 8. Backfill existing tenants; the function skips the platform tenant (V184:107).
SELECT core.grant_ai_actions(t.tenant_id) FROM core.tenant t;
