-- Migration: V010__employee.sql
-- Description: W-13.1 core.employee — the neutral employee root record, one row per employee per tenant, with row-level security isolation

CREATE TABLE core.employee (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_number VARCHAR(64) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    last_name VARCHAR(100),
    gender VARCHAR(32),
    date_of_joining DATE NOT NULL,
    termination_date DATE,
    status VARCHAR(32) NOT NULL,
    work_email VARCHAR(255),
    mobile VARCHAR(32),
    is_portal_enabled BOOLEAN NOT NULL DEFAULT true,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- date_of_joining is a real DATE. This corrects a defect rather than copying it: the frozen
-- system stores it as a String —
-- legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/BasicDetails.java:44
-- ("private String dateOfJoining; // Can be changed to LocalDate if needed"), so no database
-- can order, compare or validate it. termination_date is a DATE for the same reason.

-- is_portal_enabled defaults to true by founder decision 2 (spec §13). The frozen system
-- leaves it unset/off — BasicDetails.java:75-76 — and requires an administrator to enable each
-- person, which is onboarding friction with no security benefit once roles exist.

-- Deliberately absent, and must stay absent until their own tickets add them:
--   department_id, designation_id, work_location_id — W-14 adds these nullable in its own
--     script (spec §2 Out of scope); adding them here "to save a migration" is a named risk (§9).
--   PF / PT / LWF / ESI / EPS / higher-wages eligibility flags — payroll semantics, they go to a
--     payroll-schema table under PAY-01 (spec §13 decision 1). BasicDetails.java carries them on
--     the employee row; a neutral core record must not.
--   amountInPercentage — a Double at BasicDetails.java:123, which CONVENTIONS.md §2 forbids.
--     It is a payroll figure and is not carried into core.

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- employee_number is unique WITHIN a tenant, never globally: the frozen employeeUniqueId
-- (BasicDetails.java:140) is globally unique, which would let one tenant's numbering block another's.
CREATE UNIQUE INDEX idx_employee_tenant_employee_number ON core.employee (tenant_id, employee_number);
CREATE INDEX idx_employee_tenant_work_email ON core.employee (tenant_id, work_email);
CREATE INDEX idx_employee_tenant_status ON core.employee (tenant_id, status);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.employee ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.employee
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
