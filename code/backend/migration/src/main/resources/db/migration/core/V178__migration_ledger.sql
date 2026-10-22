-- Migration: V178__migration_ledger.sql
-- Schema: core
-- Purpose: W-68 / W-70 data migration restartability ledger and loss-of-pay mismatch log.
--          Tenant isolation and RLS on both tables.
--
-- Brought to main from origin/core-migration-non-prod-data, where it was
-- V177__migration_ledger.sql. Renumbered: main's V177 is payroll_officer_permissions.
-- The branch file's third part (core.employee designation_id / work_location_id) is
-- dropped: V014__employee_org_columns.sql added both columns and their indexes, so it
-- would only have created two duplicate indexes under different names.

-- 1. Migration Execution Ledger (W-68.3 restartability & idempotency)
CREATE TABLE core.migration_ledger (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    step_name           VARCHAR(64) NOT NULL,
    records_processed   INT NOT NULL DEFAULT 0,
    status              VARCHAR(32) NOT NULL, -- PENDING, IN_PROGRESS, COMPLETED, FAILED
    started_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMPTZ NULL,
    error_message       TEXT NULL
);

-- tenant_id leading index (DEBT-018)
CREATE INDEX idx_migration_ledger_tenant_step 
    ON core.migration_ledger (tenant_id, step_name);

ALTER TABLE core.migration_ledger ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.migration_ledger
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

COMMENT ON TABLE core.migration_ledger IS
    'W-68.3: tracks migration step execution state and processed row counts for safe crash recovery';

-- 2. Loss-of-Pay Mismatch Log (W-70 reconciliation & Finance sign-off)
CREATE TABLE core.lop_mismatch_log (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_number             VARCHAR(64) NOT NULL,
    work_email                  VARCHAR(255) NOT NULL,
    pay_period                  VARCHAR(7) NOT NULL CHECK (pay_period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    hrms_unpaid_days            NUMERIC(4,1) NOT NULL,
    payroll_lop_days            NUMERIC(4,1) NOT NULL,
    variance_days               NUMERIC(4,1) NOT NULL,
    payroll_processed_deduction NUMERIC(12,2) NOT NULL,
    status                      VARCHAR(32) NOT NULL DEFAULT 'PENDING_FINANCE_SIGN_OFF',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_lop_mismatch_status CHECK (status IN ('PENDING_FINANCE_SIGN_OFF', 'APPROVED_BY_FINANCE', 'ADJUSTED'))
);

-- tenant_id leading index (DEBT-018)
CREATE INDEX idx_lop_mismatch_log_tenant_period 
    ON core.lop_mismatch_log (tenant_id, pay_period);

ALTER TABLE core.lop_mismatch_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.lop_mismatch_log
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

COMMENT ON TABLE core.lop_mismatch_log IS
    'W-70: tracks variances between HRMS unpaid leave days and Payroll historical LOP deductions for Finance sign-off';
