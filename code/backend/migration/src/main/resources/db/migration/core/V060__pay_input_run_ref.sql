-- Migration: V060__pay_input_run_ref.sql
-- Description: W-30.1 — run_ref on core.pay_input and core.pay_input_period_lock, so an off-cycle
-- run (W-30.2) can tag its own rows and lock its own run instead of a whole period. Expand only:
-- two nullable columns, two new indexes, the W-19 trigger's body replaced. No row is touched.

ALTER TABLE core.pay_input             ADD COLUMN run_ref uuid NULL;
ALTER TABLE core.pay_input_period_lock ADD COLUMN run_ref uuid NULL;

-- The forRun read: every tagged row for one run, one statement (spec §4).
CREATE INDEX idx_pay_input_tenant_run ON core.pay_input (tenant_id, run_ref) WHERE run_ref IS NOT NULL;

-- W-19's unconditional UNIQUE (tenant_id, period) would collide with the first run lock in a
-- month, since a run lock also carries the run's period (for reporting). Replaced by a pair of
-- partial indexes: one lock row per tenant-period among period locks, one lock row per
-- tenant-run among run locks. Same transaction as the ALTERs above; no row is touched.
DROP INDEX core.uk_pay_input_period_lock_tenant_period;

CREATE UNIQUE INDEX uk_pay_input_period_lock_tenant_period ON core.pay_input_period_lock (tenant_id, period)
    WHERE run_ref IS NULL;

CREATE UNIQUE INDEX uk_pay_input_period_lock_tenant_run ON core.pay_input_period_lock (tenant_id, run_ref)
    WHERE run_ref IS NOT NULL;

-- Same trigger, same BEFORE INSERT, same name (spec §6) — CREATE OR REPLACE only. It now branches
-- on the incoming row: an untagged row is refused by a period lock, a tagged row by its own run's
-- lock instead. The period lock is never consulted for a tagged row (spec §3) — the common
-- off-cycle case is a bonus paid in an already-locked month, and neither redirecting it (there is
-- no "next" off-cycle run) nor refusing it would serve the run.
CREATE OR REPLACE FUNCTION core.pay_input_check_period_lock()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.run_ref IS NULL THEN
        IF EXISTS (
            SELECT 1 FROM core.pay_input_period_lock
            WHERE tenant_id = NEW.tenant_id AND period = NEW.period AND run_ref IS NULL
        ) THEN
            RAISE EXCEPTION 'core.pay_input: period % is locked for tenant %', NEW.period, NEW.tenant_id;
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

-- run_ref has no foreign key on purpose: the run lives in payroll.payrun, and core does not
-- reference a module schema (02-data-model.md §4). W-30.2's endpoint validates the run before
-- writing; a stray run_ref written through the core endpoint directly is orphaned and harmless —
-- no run collects it, and forPeriod/forEmployee never return it (spec decision 4).
