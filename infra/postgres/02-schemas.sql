-- ---------------------------------------------------------------------------
-- 02-schemas.sql — Platform Schemas Creation
--
-- Pure ANSI/Postgres SQL only. No psql meta-commands (\set, \connect, \i).
-- Must execute identically via psql, Testcontainers JDBC, and Azure pipelines.
-- ---------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS core AUTHORIZATION migration_user;
CREATE SCHEMA IF NOT EXISTS hrms AUTHORIZATION migration_user;
CREATE SCHEMA IF NOT EXISTS payroll AUTHORIZATION migration_user;
CREATE SCHEMA IF NOT EXISTS reference AUTHORIZATION migration_user;

COMMENT ON SCHEMA core      IS 'Always on, whatever the tenant bought. Employee, leave, identity, approvals, audit.';
COMMENT ON SCHEMA hrms      IS 'HRMS module. Attendance experience, overtime requests, projects, timesheets.';
COMMENT ON SCHEMA payroll   IS 'Payroll module. Pay runs, tax, claims, statutory.';
COMMENT ON SCHEMA reference IS 'Shared national data. NO tenant column, by design - the one exception.';
