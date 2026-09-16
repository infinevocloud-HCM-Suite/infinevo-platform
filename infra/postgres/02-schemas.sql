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

-- F-10: AUTHORIZATION only applies when the schema is created. Re-provisioning
-- over a volume where these schemas already exist (a W-02-era volume owns them
-- as postgres) would silently leave the old owner. ALTER is the idempotent form.
ALTER SCHEMA core      OWNER TO migration_user;
ALTER SCHEMA hrms      OWNER TO migration_user;
ALTER SCHEMA payroll   OWNER TO migration_user;
ALTER SCHEMA reference OWNER TO migration_user;

COMMENT ON SCHEMA core      IS 'Always on, whatever the tenant bought. Employee, leave, identity, approvals, audit.';
COMMENT ON SCHEMA hrms      IS 'HRMS module. Attendance experience, overtime requests, projects, timesheets.';
COMMENT ON SCHEMA payroll   IS 'Payroll module. Pay runs, tax, claims, statutory.';
COMMENT ON SCHEMA reference IS 'Shared national data. NO tenant column, by design - the one exception.';
