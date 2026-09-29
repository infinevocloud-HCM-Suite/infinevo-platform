-- Migration: V054__pay_schedule.sql
-- Description: W-28 payroll.pay_schedule — work week, pay-day rule, input cut-off day and first period with row-level security (PAY-04, D-60)

CREATE TABLE payroll.pay_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    frequency VARCHAR(16) NOT NULL DEFAULT 'MONTHLY' CHECK (frequency IN ('MONTHLY')),
    working_days SMALLINT[] NOT NULL CHECK (cardinality(working_days) BETWEEN 1 AND 7),
    pay_day_rule VARCHAR(24) NOT NULL CHECK (pay_day_rule IN ('LAST_DAY_OF_PERIOD', 'LAST_WORKING_DAY', 'SPECIFIC_DAY')),
    pay_day_of_month SMALLINT CHECK (pay_day_of_month BETWEEN 1 AND 28),
    input_cutoff_day SMALLINT NOT NULL DEFAULT 25 CHECK (input_cutoff_day BETWEEN 1 AND 28),
    first_period_start DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- One schedule per tenant (DEBT-018, W-28 §6)
CREATE UNIQUE INDEX uk_pay_schedule_tenant ON payroll.pay_schedule (tenant_id);

-- Row-level security — migration/README.md §row-level security
ALTER TABLE payroll.pay_schedule ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.pay_schedule
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
