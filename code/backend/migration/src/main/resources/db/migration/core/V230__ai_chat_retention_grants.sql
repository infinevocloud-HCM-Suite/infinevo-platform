-- Migration: V230__ai_chat_retention_grants.sql
-- Schema: core
-- Purpose: W-114.10 the nightly AI chat retention sweep. retention_user may read and delete the three chat
--          tables of W-114.3 (V200), and two indexes let it find old rows per customer.
--
-- Expand only: grants and two indexes, no new table, so no new tenant_id or policy. The chat tables already
-- carry both (V200:12-84), and retention_user is not BYPASSRLS (infra/postgres/01-roles.sql:52-53), so the
-- sweep deletes only inside the tenant it binds.
-- retention_user gains no UPDATE and no access to any other table (V094:63-71 for the existing grants).
--
-- Reversible:
--   REVOKE SELECT, DELETE ON core.ai_message, core.ai_action_intent, core.ai_session FROM retention_user;
--   DROP INDEX core.idx_ai_message_tenant_created;
--   DROP INDEX core.idx_ai_intent_tenant_created;

-- 1. Grants. retention_user already has USAGE on schema core (V094:67).
GRANT SELECT, DELETE ON core.ai_message TO retention_user;
GRANT SELECT, DELETE ON core.ai_action_intent TO retention_user;
GRANT SELECT, DELETE ON core.ai_session TO retention_user;

-- 2. Indexes for the age scan. Each leads with tenant_id (migration/README.md, DEBT-018). core.ai_session is
--    left to idx_ai_session_tenant_user (V200:48): a customer has few sessions, so the sweep scans them.
CREATE INDEX IF NOT EXISTS idx_ai_message_tenant_created ON core.ai_message (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_intent_tenant_created ON core.ai_action_intent (tenant_id, created_at);
