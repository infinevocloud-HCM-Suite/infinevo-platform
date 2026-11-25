-- Migration: V220__pay_input_lock_stage.sql
-- Description: D-144 - a stage on core.pay_input_period_lock. Founder 2026-11-23: a month is CLOSED
-- once its regular run is approved (and stays closed when paid); a LOCKED month (run locked or
-- computed) still takes entries, and the run must be recomputed. Before this, one lock state meant
-- "closed" to the ledger, so an entry for a month whose run was merely locked - or later cancelled
-- (D-138) - was moved to the next month and that month's run never saw it.
--
-- V192 is taken (V192__ai_switch_and_usage.sql) and V201-V209 are used on other branches
-- (feature/krushna-specs and others), so this is V220: clear of them, no reliance on out-of-order.
--
-- Expand only: one column with a default (existing rows become LOCKED), the trigger body replaced,
-- two narrow grants with two restrictive policies that keep them narrow. payroll/V221 then marks the
-- months whose run is already approved or paid as CLOSED.

ALTER TABLE core.pay_input_period_lock
    ADD COLUMN stage VARCHAR(16) NOT NULL DEFAULT 'LOCKED'
        CONSTRAINT ck_pay_input_period_lock_stage CHECK (stage IN ('LOCKED','CLOSED'));

COMMENT ON COLUMN core.pay_input_period_lock.stage IS
    'D-144: LOCKED - run locked or computed, entries still post here and the run must be recomputed; '
    'CLOSED - run approved or paid, untagged entries are refused. A run lock (run_ref set) is always LOCKED.';

-- Same trigger, same name, CREATE OR REPLACE only (V032, V060). The untagged branch now raises only
-- for a CLOSED period lock; a LOCKED one lets the row in. The run_ref branch is unchanged: a run
-- lock of any stage refuses a row tagged to that run (W-30.1 §3).
CREATE OR REPLACE FUNCTION core.pay_input_check_period_lock()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.run_ref IS NULL THEN
        IF EXISTS (
            SELECT 1 FROM core.pay_input_period_lock
            WHERE tenant_id = NEW.tenant_id AND period = NEW.period AND run_ref IS NULL AND stage = 'CLOSED'
        ) THEN
            RAISE EXCEPTION 'core.pay_input: period % is closed for tenant %', NEW.period, NEW.tenant_id;
        END IF;
    ELSE
        IF EXISTS (
            SELECT 1 FROM core.pay_input_period_lock
            WHERE tenant_id = NEW.tenant_id AND run_ref = NEW.run_ref
        ) THEN
            RAISE EXCEPTION 'core.pay_input: run % is locked for tenant %', NEW.run_ref, NEW.tenant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

-- V032 revoked UPDATE and DELETE outright: a lock was written once and never undone. Two moves are
-- now legitimate, and only those two:
--   * LOCKED -> CLOSED on a period lock, when the run is approved (column UPDATE on stage and the
--     audit columns only; the restrictive policy refuses any new row that is not CLOSED, so a
--     CLOSED month is never reopened by an UPDATE);
--   * removing a LOCKED period lock, when the run is cancelled (the restrictive policy makes a
--     CLOSED row and every run lock invisible to DELETE).
GRANT UPDATE (stage, updated_at, updated_by) ON core.pay_input_period_lock TO app_user;
GRANT DELETE ON core.pay_input_period_lock TO app_user;

CREATE POLICY close_period_lock_only ON core.pay_input_period_lock
    AS RESTRICTIVE
    FOR UPDATE
    USING (run_ref IS NULL)
    WITH CHECK (run_ref IS NULL AND stage = 'CLOSED');

CREATE POLICY delete_locked_period_lock_only ON core.pay_input_period_lock
    AS RESTRICTIVE
    FOR DELETE
    USING (run_ref IS NULL AND stage = 'LOCKED');
