-- Migration: V016__employee_contact.sql
-- Description: W-13.2 core.employee_contact — addresses and emergency contacts for an employee, one optional row per employee, with row-level security isolation

CREATE TABLE core.employee_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    personal_email VARCHAR(255),
    alternate_mobile VARCHAR(32),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    state_code VARCHAR(10),
    zip_code VARCHAR(16),
    permanent_address_line1 VARCHAR(255),
    permanent_address_line2 VARCHAR(255),
    permanent_city VARCHAR(100),
    permanent_state VARCHAR(100),
    permanent_state_code VARCHAR(10),
    permanent_zip_code VARCHAR(16),
    emergency_contact_name VARCHAR(100),
    emergency_contact_number VARCHAR(32),
    emergency_contact_relationship VARCHAR(64),
    secondary_emergency_contact_name VARCHAR(100),
    secondary_emergency_contact_number VARCHAR(32),
    secondary_emergency_contact_relationship VARCHAR(64),
    family_doctor_name VARCHAR(100),
    family_doctor_contact_number VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

-- Address, settled — spec §6. The residential address is modelled twice in the frozen system and
-- they cannot both survive:
--   HRMS holds free text —
--     legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Contact.java:15-31
--     (residentialAddress, permanentAddress, city, state, country, postalCode).
--   Payroll holds a structured value object —
--     legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/ResidentialAddress.java:8-13
--     (addressLine1, addressLine2, city, state, stateCode, zipCode), embedded at
--     EmployeePersonalDetail.java:48-49.
-- Payroll's structure wins: a statutory filing needs a state code, and free text cannot supply
-- one. HRMS's country column is not carried — every filing this platform produces is Indian, and
-- state_code is the key those filings are cut by.

-- Permanent address is six more columns on this row, not a second row typed by purpose —
-- spec §13 decision 2. One row per employee keeps the RLS story and the unique index simple.

-- These are flat columns and NOT an @Embedded value object on the Java side, and that is not a
-- style choice. An @Embedded property maps to several columns, so Hibernate cannot name a single
-- column for it and the audit trail falls back to the Java property name plus
-- String.valueOf(object) — which the redaction deny-list, matching column names, cannot see.
-- W-13.2 fixes that fallback in AuditWriter as well (spec §2, the founder's addition), but flat
-- columns mean this table never depended on the fix.

-- personal_email is Payroll's personal_mail (EmployeePersonalDetail.java:21-22) and HRMS's
-- personalEmail (Contact.java:37-38) — one field, two homes, landing here.
-- work_email and mobile stay on the root record (V010__employee.sql): they identify the employee
-- to the platform, and the portal login path reads them without loading a detail section.

-- Composite indexes, tenant_id leading (DEBT-018, migration/README.md §index on tenant_id plus lookup columns).
CREATE UNIQUE INDEX idx_employee_contact_tenant_employee ON core.employee_contact (tenant_id, employee_id);

-- Row-level security — migration/README.md §row-level security.
-- Handles pooled connections safely: NULL or empty session variables resolve to NULL (false).
ALTER TABLE core.employee_contact ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.employee_contact
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
