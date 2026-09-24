-- Migration: V015__employee_personal.sql
-- Description: W-13.2 core.employee_personal — the personal section of an employee, one optional row per employee, with row-level security isolation

CREATE TABLE core.employee_personal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    date_of_birth DATE,
    marital_status VARCHAR(32),
    nationality VARCHAR(64),
    ethnicity VARCHAR(64),
    father_name VARCHAR(100),
    differently_abled_type VARCHAR(64),
    is_eligible_for_full_tax_exemption BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- The section is one row per employee and it is optional — spec §2. There is no row until the
-- first PUT, and there is never a second: the unique index below is what makes that true rather
-- than a rule the service remembers.

-- The union of two shapes, not a copy of either (spec §1):
--   HRMS legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/personal.java:36-48
--     gives date_of_birth, marital_status, nationality, ethnicity.
--   Payroll legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/EmployeePersonalDetail.java:30-45
--     gives father_name, differently_abled_type and the full income-tax exemption flag.

-- date_of_birth is a real DATE. personal.java:37 types it as java.sql.Date via a raw JDBC type
-- while Payroll uses LocalDate; the platform follows Payroll, for the same reason W-13.1 made
-- date_of_joining a DATE (V010__employee.sql).

-- NOT carried here, and the omission is deliberate:
--   empId, first_name, middle_name, last_name, gender, employment_status (personal.java:18-40)
--     are the root record — core.employee, V010__employee.sql. A second copy would drift.
--   personal_mail (EmployeePersonalDetail.java:21-22) is a contact detail and lives on
--     core.employee_contact — V016.
--   pan (EmployeePersonalDetail.java:33-39) is an identity document and lives on
--     core.employee_identification — V017.
--   custom_fields (EmployeePersonalDetail.java:52-54) is a JSON blob on the frozen row. It is
--     not carried: a schema-less column is how the frozen system avoided deciding, and W-67 is
--     the ticket that has to read whatever is in it.

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
-- UNIQUE is the one-to-one: one personal section per employee, per tenant.
CREATE UNIQUE INDEX idx_employee_personal_tenant_employee ON core.employee_personal (tenant_id, employee_id);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.employee_personal ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.employee_personal
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
