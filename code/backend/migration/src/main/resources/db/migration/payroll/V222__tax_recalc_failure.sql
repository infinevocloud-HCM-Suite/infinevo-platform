-- Migration: V222__tax_recalc_failure.sql
-- Description: D-147 a tax recalculation that fails is recorded where the payroll officer can see it.
--   The recalculation (W-33.3) runs after the employee's submit has committed, so a failure there used to be
--   one log line. Now the whole recalculation rolls back and one open row per employee and year says why;
--   a later successful recalculation, or the officer's retry, closes it (resolved_at).
--   V193-V221 are taken on other branches, so this is V222.

CREATE TABLE payroll.tax_recalc_failure (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id     UUID NOT NULL REFERENCES core.employee(id),
    financial_year  VARCHAR(9)  NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$'),
    trigger         VARCHAR(24) NOT NULL CHECK (trigger IN (
                        'DECLARATION_SUBMITTED','SALARY_REVISION',
                        'SALARY_DEFAULT','PROOF_VERIFIED','OFFICER')),
    error_message   TEXT        NOT NULL,
    attempts        INTEGER     NOT NULL DEFAULT 1 CHECK (attempts >= 1),
    first_failed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_failed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- One open failure per employee and year: a repeat failure updates it rather than adding a row.
CREATE UNIQUE INDEX uk_tax_recalc_failure_open
    ON payroll.tax_recalc_failure (tenant_id, employee_id, financial_year)
    WHERE resolved_at IS NULL;

CREATE INDEX idx_tax_recalc_failure_tenant_open
    ON payroll.tax_recalc_failure (tenant_id, last_failed_at DESC)
    WHERE resolved_at IS NULL;

ALTER TABLE payroll.tax_recalc_failure ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.tax_recalc_failure
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE ON payroll.tax_recalc_failure TO app_user;
