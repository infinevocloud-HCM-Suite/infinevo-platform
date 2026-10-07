-- Migration: V154__employee_profile_columns.sql
-- Description: D-40 core.employee_employment — employment type, probation end date and notice period, nullable; core.employee gender spellings normalised

-- Only the three columns the employee record does not already have. The other three D-40 names are
-- already there and are NOT added again:
--   gender          core.employee.gender VARCHAR(32), V010__employee.sql:11
--   date_of_joining core.employee.date_of_joining DATE NOT NULL, V010__employee.sql:12
--   job title       core.employee.designation_id, V014__employee_org_columns.sql — the frozen
--                   Work.jobTitle (legacy/HRMS_Backend/.../entity/Work.java:21) maps onto the
--                   designation master, as V018__employee_employment.sql records. A free-text
--                   job_title beside it would be two answers to "what is this person's role".
--
-- They go on core.employee_employment, not core.employee: they are terms of employment, edited on
-- the Employment section (PUT /api/v1/employees/{id}/employment), which is this table. On
-- core.employee they would be a second write path for that section and would make every hand-built
-- test schema that maps the Employee entity apply this script.
--
-- All three are NULLABLE: the table already has rows, and none of them has a value to give.
-- Making any NOT NULL is a later contract step after a backfill (migration/README.md §forward-only).
ALTER TABLE core.employee_employment
    ADD COLUMN employment_type VARCHAR(32) NULL,
    ADD COLUMN probation_end_date DATE NULL,
    ADD COLUMN notice_period_days INTEGER NULL;

-- The bounds EmployeeEmploymentServiceImpl and EmployeeServiceImpl check, so a row written by any
-- other path cannot hold a value the API would refuse. The service check is what produces a 400
-- naming the field; these are the backstop. "Probation ends on or after joining" spans two tables
-- and cannot be a CHECK — the services hold it.
ALTER TABLE core.employee_employment
    ADD CONSTRAINT ck_employee_employment_notice_period_days
        CHECK (notice_period_days IS NULL OR notice_period_days BETWEEN 0 AND 365),
    ADD CONSTRAINT ck_employee_employment_type
        CHECK (employment_type IS NULL
               OR employment_type IN ('PERMANENT', 'CONTRACT', 'PART_TIME', 'INTERN', 'PROBATION', 'CONSULTANT'));

-- core.employee.gender becomes one of MALE, FEMALE, OTHER, UNDISCLOSED
-- (com.infinevo.core.employee.Gender). The column stays VARCHAR(32) — LeaveEligibilityServiceImpl
-- and the payroll professional-tax lookup read it as text — but rows written before D-40 hold
-- whatever the caller sent ("M", "Female"). Normalise the spellings that have one obvious meaning;
-- anything else is left as it is and is refused only when that employee is next saved.
UPDATE core.employee SET gender = 'MALE' WHERE upper(trim(gender)) IN ('M', 'MALE');
UPDATE core.employee SET gender = 'FEMALE' WHERE upper(trim(gender)) IN ('F', 'FEMALE');
UPDATE core.employee SET gender = 'OTHER' WHERE upper(trim(gender)) IN ('O', 'OTHER');
UPDATE core.employee SET gender = 'UNDISCLOSED' WHERE upper(trim(gender)) IN ('U', 'UNDISCLOSED');

-- This script creates no table, so it needs no ENABLE ROW LEVEL SECURITY and no tenant_isolation
-- policy of its own. core.employee_employment has both from V018__employee_employment.sql and
-- core.employee from V010__employee.sql; they cover these columns like every other
-- (migration/README.md §row-level security: exactly one isolation policy per table).
