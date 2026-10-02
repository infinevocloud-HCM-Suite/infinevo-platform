-- Migration: V061__payrun_off_cycle.sql
-- Description: W-30.2 off-cycle pay run. A second run_type on payroll.payrun, zero new tables
-- (founder 2026-09-25): an off-cycle run is the W-29 run created for named employees, paying only
-- the pay inputs tagged with its id. Expand only: a nullable column, a widened CHECK, the
-- one-per-period index narrowed to regular runs. No row is touched.

ALTER TABLE payroll.payrun
    -- The officer's note on why the run exists; legacy OffCyclePayRun.notes.
    ADD COLUMN notes VARCHAR(500);

-- V055 declared the CHECK inline, so Postgres named it payrun_run_type_check.
ALTER TABLE payroll.payrun DROP CONSTRAINT payrun_run_type_check;
ALTER TABLE payroll.payrun
    ADD CONSTRAINT ck_payrun_run_type CHECK (run_type IN ('REGULAR','OFF_CYCLE'));

-- BUG-010 stays fixed for regular runs: one non-cancelled regular run per tenant and period.
-- Off-cycle runs are many per month by design.
DROP INDEX payroll.uk_payrun_tenant_period;
CREATE UNIQUE INDEX uk_payrun_tenant_period
    ON payroll.payrun (tenant_id, period)
    WHERE status <> 'CANCELLED' AND run_type = 'REGULAR';

-- DEBT-018: the list filter by run type.
CREATE INDEX idx_payrun_tenant_type_period ON payroll.payrun (tenant_id, run_type, period);
