-- Migration: V165__user_account_welcome.sql
-- Schema: core
-- Purpose: W-73.8 welcome screen — when the user dismissed the first-sign-in page, per (user, tenant) row.
--
-- NULLABLE with no default: every existing account has not seen the page yet, and null is what
-- GET /api/v1/me reports as welcomeSeen = false. PUT /api/v1/me/welcome-seen writes now() once and
-- never moves it again (UserAccountRepository#markWelcomeSeen, WHERE welcome_seen_at IS NULL).
-- The table's RLS policy (V009) is unchanged: the bound tenant reads and updates its own rows only.
--
-- Rollback (spec section 10): the frontend redirect goes back to the home path; the column can stay.

ALTER TABLE core.user_account
    ADD COLUMN welcome_seen_at timestamptz NULL;
