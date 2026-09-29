-- Migration: V032__pay_input_period_lock.sql
-- Description: W-19 core.pay_input_period_lock — one row per locked tenant-period, and the
-- insert trigger on core.pay_input that a lock row makes real. Runs after V031: the trigger
-- references the ledger it guards.

CREATE TABLE core.pay_input_period_lock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    period CHAR(7) NOT NULL CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    locked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_pay_input_period_lock_tenant_period ON core.pay_input_period_lock (tenant_id, period);

ALTER TABLE core.pay_input_period_lock ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.pay_input_period_lock
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- A lock is never undone by the application (spec §6): an abandoned run is handled by a new lock
-- elsewhere, not by deleting this row. UPDATE is revoked too — a lock row is written once and never
-- corrected; locking again after correcting a mistake is a fresh row an administrator creates.
REVOKE UPDATE, DELETE ON core.pay_input_period_lock FROM app_user;

-- The lock is a database constraint, not a service check (spec §6). app_user has no UPDATE on
-- core.pay_input, so a `locked` flag on the ledger row could never be set — this is why the lock
-- lives in its own table and a trigger is what makes it bind. PayInputServiceImpl checks the lock
-- before inserting so it can redirect a late input to the next open period; this trigger is what
-- stops anything the service missed (a race between two callers, a write that bypasses the
-- service entirely — PayInputLockIT proves exactly that path). Plain function, no SECURITY DEFINER:
-- it runs as the inserting role, under the same tenant binding and the same RLS as the INSERT it
-- guards, and reads only the lock table's own tenant_id, which the WHERE clause already states for
-- a reader coming to this later without RLS in mind.
CREATE OR REPLACE FUNCTION core.pay_input_check_period_lock()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM core.pay_input_period_lock
        WHERE tenant_id = NEW.tenant_id AND period = NEW.period
    ) THEN
        RAISE EXCEPTION 'core.pay_input: period % is locked for tenant %', NEW.period, NEW.tenant_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_pay_input_period_lock
    BEFORE INSERT ON core.pay_input
    FOR EACH ROW
    EXECUTE FUNCTION core.pay_input_check_period_lock();
