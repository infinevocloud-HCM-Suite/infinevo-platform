-- Migration: V055__payrun.sql
-- Description: W-29.1 payroll.payrun — one pay run per tenant and period, the status vocabulary for all of W-29

CREATE TABLE payroll.payrun (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    period CHAR(7) NOT NULL CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    -- The four dates are copied from W-28's periodFor when the run is created: a run keeps the
    -- dates it was paid on even if the schedule changes later (W-28 §13 decision 3).
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    cutoff_date DATE NOT NULL,
    pay_date DATE NOT NULL,
    run_type VARCHAR(16) NOT NULL DEFAULT 'REGULAR' CHECK (run_type IN ('REGULAR')),
    -- All eight statuses exist from day one (W-29.1 §13 decision 5); W-29.2 to W-36 add transitions only.
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','LOCKED','COMPUTING','COMPUTED','FAILED','APPROVED','PAID','CANCELLED')),
    included_count INT NOT NULL DEFAULT 0 CHECK (included_count >= 0),
    skipped_count INT NOT NULL DEFAULT 0 CHECK (skipped_count >= 0),
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(100),
    cancelled_at TIMESTAMPTZ,
    cancelled_by VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CHECK (period_start <= period_end)
);

-- BUG-010: one non-cancelled run per tenant and period. The index decides, not a service check,
-- so two officers creating the same month at once leave exactly one run.
CREATE UNIQUE INDEX uk_payrun_tenant_period
    ON payroll.payrun (tenant_id, period)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_payrun_tenant_status ON payroll.payrun (tenant_id, status);

-- Row-Level Security
ALTER TABLE payroll.payrun ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.payrun
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
