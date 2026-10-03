-- Migration: V140__prior_payroll.sql
-- Description: W-38.1 prior payroll import — template, dry run, load
-- Holds previous payroll months and bulk import logs with RLS isolation.

CREATE TABLE payroll.prior_payroll_import_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    source_document_id  UUID NOT NULL REFERENCES core.document(id) ON DELETE RESTRICT,
    error_document_id   UUID NULL REFERENCES core.document(id) ON DELETE RESTRICT,
    financial_year      VARCHAR(9) NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$'),
    status              VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    is_dry_run          BOOLEAN NOT NULL DEFAULT false,
    rows_total          INT NOT NULL DEFAULT 0,
    rows_imported       INT NOT NULL DEFAULT 0,
    rows_failed         INT NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at         TIMESTAMPTZ NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_prior_payroll_import_log_tenant_started
    ON payroll.prior_payroll_import_log (tenant_id, started_at DESC);

CREATE INDEX idx_prior_payroll_import_log_tenant_status
    ON payroll.prior_payroll_import_log (tenant_id, status);

ALTER TABLE payroll.prior_payroll_import_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.prior_payroll_import_log
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, UPDATE ON payroll.prior_payroll_import_log TO app_user;

CREATE TABLE payroll.prior_payroll_month (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_id         UUID NOT NULL REFERENCES core.employee(id),
    period              CHAR(7) NOT NULL CHECK (period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    gross_earnings      NUMERIC(19,4) NOT NULL CHECK (gross_earnings >= 0),
    epf_employee        NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (epf_employee >= 0),
    esi_employee        NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (esi_employee >= 0),
    professional_tax    NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (professional_tax >= 0),
    tds                 NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (tds >= 0),
    net_pay             NUMERIC(19,4) NOT NULL CHECK (net_pay >= 0),
    import_id           UUID NOT NULL REFERENCES payroll.prior_payroll_import_log(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(100) NOT NULL DEFAULT 'system',
    CHECK (net_pay <= gross_earnings - epf_employee - esi_employee - professional_tax - tds)
);

CREATE UNIQUE INDEX uk_prior_payroll_month_tenant_employee_period
    ON payroll.prior_payroll_month (tenant_id, employee_id, period);

CREATE INDEX idx_prior_payroll_month_tenant_period
    ON payroll.prior_payroll_month (tenant_id, period);

CREATE INDEX idx_prior_payroll_month_tenant_employee
    ON payroll.prior_payroll_month (tenant_id, employee_id, period);

ALTER TABLE payroll.prior_payroll_month ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.prior_payroll_month
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

GRANT SELECT, INSERT, DELETE ON payroll.prior_payroll_month TO app_user;
