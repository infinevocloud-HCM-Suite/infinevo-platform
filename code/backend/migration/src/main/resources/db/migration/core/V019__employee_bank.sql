-- Migration: V019__employee_bank.sql
-- Description: W-13.2 core.employee_bank — how an employee is paid, one optional row per employee, with row-level security isolation

CREATE TABLE core.employee_bank (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    payment_mode VARCHAR(32) NOT NULL,
    account_holder_name VARCHAR(100),
    bank_name VARCHAR(128),
    ifsc_code VARCHAR(20),
    bank_account_number VARCHAR(64),
    bank_account_type VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Payroll is the whole shape here —
-- legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/EmployeeBankDetail.java:16-34.
-- HRMS models no bank detail at all (spec §1).

-- bank_account_number is VARCHAR and never numeric — spec §9, and it is irreversible if wrong. An
-- account number is a string of digits, not a number: 0012345678 is not 12345678, and a numeric
-- column loses the leading zeros on the way in with nothing to recover them from.

-- payment_mode and bank_account_type are free-text strings on the frozen row
-- (EmployeeBankDetail.java:18, :34). They are enumerations in the platform — PaymentMode and
-- BankAccountType — and the column stays VARCHAR because Hibernate writes the enum name. There is
-- deliberately no CHECK constraint: W-13.1 recorded the same gap for core.employee.status, and the
-- ticket that knows what the frozen values actually are is W-67.

-- Uniqueness is deliberately NOT asserted on bank_account_number. The frozen column is
-- unique = true (EmployeeBankDetail.java:30) — globally, across every customer — so one tenant's
-- employee holding an account blocks another's, and a joint or family account legitimately repeats.

-- This table and core.employee_identification are why the audit redaction fix had to land first
-- (spec §2, the founder's addition): both hold values that must never appear in core.audit_log in
-- clear. bank_account_number and ifsc_code are matched by AuditWriter.REDACTED_FRAGMENTS; renaming
-- either column silently un-redacts it, so do not rename them without changing that list.

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
CREATE UNIQUE INDEX idx_employee_bank_tenant_employee ON core.employee_bank (tenant_id, employee_id);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.employee_bank ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.employee_bank
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
