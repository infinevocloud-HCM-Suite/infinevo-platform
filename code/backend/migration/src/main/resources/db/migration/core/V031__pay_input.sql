-- Migration: V031__pay_input.sql
-- Description: W-19 core.pay_input — the append-only ledger every module writes a pay-affecting
-- value to, and the pay run reads. No UPDATE and no DELETE for app_user: a correction is a new
-- reversal row, never an edit, which is what makes the ledger explainable after the fact (spec §1).

CREATE TABLE core.pay_input (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    -- YYYY-MM. A YearMonth in Java end to end; char(7) is the SQL shape only (spec §4).
    period CHAR(7) NOT NULL CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('LOP_DAYS', 'OVERTIME', 'REIMBURSEMENT', 'AD_HOC_DEDUCTION', 'ONE_TIME_PAYOUT')),
    -- Both nullable, and both positive when given: a CHECK on a NULL column evaluates to NULL, which
    -- Postgres treats as satisfied, so a row may carry quantity only, amount only, or both.
    quantity NUMERIC(10, 2) CHECK (quantity > 0),
    amount NUMERIC(19, 4) CHECK (amount > 0),
    source_module VARCHAR(16) NOT NULL,
    source_ref VARCHAR(64),
    -- The row this one reverses, or NULL for an original entry. No FK-driven cascade of any kind
    -- touches this column; a reversal is inserted like any other row and simply points back.
    reverses_id UUID REFERENCES core.pay_input(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Composite indexes, tenant_id leading (DEBT-018). The first is the per-employee read
-- (forEmployee); the second is the batch read for a whole period (forPeriod).
CREATE INDEX idx_pay_input_tenant_employee_period_kind ON core.pay_input (tenant_id, employee_id, period, kind);
CREATE INDEX idx_pay_input_tenant_period ON core.pay_input (tenant_id, period);

-- Idempotency (12-core-contracts.md §6 decision 2): a retried caller cannot post the same source
-- event twice. The WHERE clause exempts reversal rows on purpose — PayInputIdempotencyIT reverses a
-- row with the same (source_module, source_ref) as the original, and that must be accepted.
CREATE UNIQUE INDEX uk_pay_input_tenant_source ON core.pay_input (tenant_id, source_module, source_ref)
    WHERE reverses_id IS NULL;

-- One reversal per row. A retried reverse, or two cancels racing each other, must not leave two
-- -1 rows against one original — that would flip the sign of the total instead of netting it to
-- zero. The service checks first (PayInputServiceImpl.reverse); this index is what makes the check
-- hold when two transactions read the same unreversed row at once.
CREATE UNIQUE INDEX uk_pay_input_tenant_reverses ON core.pay_input (tenant_id, reverses_id)
    WHERE reverses_id IS NOT NULL;

ALTER TABLE core.pay_input ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.pay_input
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Append-only: infra/postgres/03-grants.sql grants all four DML privileges to app_user on every new
-- core table by default, so this is a REVOKE rather than a narrower GRANT (core.audit_log,
-- V008__audit_log.sql, is the precedent). The ledger is immutable from the application's own
-- connection; only a reversal row, inserted through the same seam, ever changes what a period reads
-- as. Locking (V032) piggybacks on the same fact: no UPDATE means a "locked" flag on this table
-- could never be set, which is why the lock is a row in another table instead.
REVOKE UPDATE, DELETE ON core.pay_input FROM app_user;
