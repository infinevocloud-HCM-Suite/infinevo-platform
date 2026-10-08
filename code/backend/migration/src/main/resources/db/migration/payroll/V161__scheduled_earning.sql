-- Migration: V161__scheduled_earning.sql
-- Description: W-73.6 scheduled earnings — a one-time or instalment earning planned for a future month.
--
-- One table, payroll.scheduled_earning, plus the "Scheduled" flag on the earning component the
-- drawer sets (spec §2). core.pay_input already carries source_ref (V031, VARCHAR(64), unique with
-- tenant_id and source_module): each instalment is written through the pay-input service with
-- source_ref 'scheduled_earning:<id>:<yyyy-MM>' (the month actually paid into), which is what makes
-- materialisation idempotent. last_paid_period is that month for the latest instalment: the next one
-- is due the month after it, so an instalment held up by a pause is paid late rather than never.

ALTER TABLE payroll.earning
    ADD COLUMN is_scheduled_earning BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE payroll.scheduled_earning (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_id         UUID NOT NULL REFERENCES core.employee(id) ON DELETE RESTRICT,
    earning_id          UUID NOT NULL REFERENCES payroll.earning(id) ON DELETE RESTRICT,
    amount              NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    first_period        DATE NOT NULL,
    instalments         SMALLINT NOT NULL CHECK (instalments BETWEEN 1 AND 12),
    paid_instalments    SMALLINT NOT NULL DEFAULT 0 CHECK (paid_instalments >= 0 AND paid_instalments <= instalments),
    last_paid_period    DATE,
    status              VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED'
                        CHECK (status IN ('SCHEDULED', 'PAUSED', 'CANCELLED', 'PAID')),
    reason              VARCHAR(255),
    status_reason       VARCHAR(255),
    created_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_scheduled_earning_first_period_is_month_start
        CHECK (first_period = date_trunc('month', first_period)::date),
    CONSTRAINT chk_scheduled_earning_last_paid_period_is_month_start
        CHECK (last_paid_period IS NULL OR last_paid_period = date_trunc('month', last_paid_period)::date)
);

CREATE INDEX idx_scheduled_earning_tenant_status_period
    ON payroll.scheduled_earning (tenant_id, status, first_period);

CREATE INDEX idx_scheduled_earning_tenant_employee
    ON payroll.scheduled_earning (tenant_id, employee_id);

ALTER TABLE payroll.scheduled_earning ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.scheduled_earning
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE ON payroll.scheduled_earning TO app_user;
