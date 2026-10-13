-- Migration: V173__employee_termination_reason.sql
-- Description: BUG-D4-20 — core.employee gains termination_reason and termination_remarks.
--
-- The Terminate dialog (TerminateModal.jsx) asks for a reason and remarks and posts them with the
-- termination date, but until this script nothing stored them: EmployeeRequest had no such field,
-- Jackson dropped the unknown ones and the request still answered 200. Both columns sit on
-- core.employee beside termination_date (V010__employee.sql:13), because they describe the same
-- event and are written by the same PUT /api/v1/employees/{id}.
--
-- Both are NULLABLE: the table already has rows, and the service refuses a value whenever status is
-- not TERMINATED, so a NOT NULL would be wrong for every active employee.
-- termination_reason is one of the codes TerminateModal offers (RESIGNATION, TERMINATION,
-- RETIREMENT, CONTRACT_END, OTHER); the service checks the length, not the list, so a tenant can be
-- given more codes later without a schema change.
ALTER TABLE core.employee
    ADD COLUMN IF NOT EXISTS termination_reason VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS termination_remarks VARCHAR(1000) NULL;
