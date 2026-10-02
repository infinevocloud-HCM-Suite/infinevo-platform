-- Migration: V101__employee_deduction.sql
-- Description: W-35.2 payroll.employee_deduction — one-off salary deductions an officer enters. Each
-- row is also one AD_HOC_DEDUCTION row in core.pay_input, written in the same transaction; the pay run
-- reads only the ledger (W-29.3). Never edited: a mistake is reversed on the ledger and the row marked
-- REVERSED. Replaces legacy salary_deduction (legacy/docs/DB_SCHEMA.md:1076-1095).

CREATE TABLE payroll.employee_deduction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    -- Legacy kept the Keycloak id as a string (SalaryDeduction.java:19).
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    -- YYYY-MM, as core.pay_input.period (V031); legacy stored a first-of-month DATE.
    period CHAR(7) NOT NULL CHECK (period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    -- A closed list; legacy free text (SalaryDeduction.java:35) cannot be reported on. reason keeps the words.
    deduction_type VARCHAR(32) NOT NULL
        CHECK (deduction_type IN ('ADVANCE_RECOVERY','LOAN_RECOVERY','DAMAGE','PENALTY','EXCESS_PAYMENT','OTHER')),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    reason VARCHAR(255) NOT NULL,
    remarks VARCHAR(500),
    -- Proof through W-21; replaces legacy's public Cloudinary URL (DEBT-011).
    document_id UUID NULL REFERENCES core.document(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('POSTED','REVERSED')),
    -- No foreign key: the ledger is append-only and owned by core.
    pay_input_id UUID NOT NULL,
    -- The period the ledger took; later than period when period was already locked.
    posted_period CHAR(7) NOT NULL CHECK (posted_period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    reversal_pay_input_id UUID NULL,
    reversed_at TIMESTAMPTZ NULL,
    -- The reversing officer's employee id, as approvals record a decider; null for a login with none.
    reversed_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_by VARCHAR(64) NOT NULL DEFAULT 'system',
    -- A reversed row always says which ledger row reversed it, and when.
    CHECK ((status = 'REVERSED') = (reversal_pay_input_id IS NOT NULL AND reversed_at IS NOT NULL))
);

CREATE INDEX idx_emp_deduction_tenant_employee_period
    ON payroll.employee_deduction (tenant_id, employee_id, period DESC);

CREATE INDEX idx_emp_deduction_tenant_period_status
    ON payroll.employee_deduction (tenant_id, period, status);

CREATE UNIQUE INDEX uk_emp_deduction_tenant_pay_input
    ON payroll.employee_deduction (tenant_id, pay_input_id);

-- Row-Level Security
ALTER TABLE payroll.employee_deduction ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.employee_deduction
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
