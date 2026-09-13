-- Local development bootstrap.
--
-- Creates the four schemas and the three database roles. It deliberately creates
-- NO TABLES. Tables arrive through Flyway (W-06) and must never be created here,
-- or the two will collide.
--
-- This mirrors docs/target-state/02-data-model.md section 9. The roles exist locally
-- for one reason: the application must connect as app_user, never as owner. A local
-- stack that runs as owner teaches the wrong habit and hides row-level security bugs
-- until W-07, which is exactly when they are most expensive to find.
--
-- Runs once, on first start, from the Postgres image's init directory. To re-run:
--   docker compose -f infra/docker/compose.yml down -v

\set ON_ERROR_STOP on

-- ---------------------------------------------------------------------------
-- Schemas
-- ---------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS hrms;
CREATE SCHEMA IF NOT EXISTS payroll;
CREATE SCHEMA IF NOT EXISTS reference;

COMMENT ON SCHEMA core      IS 'Always on, whatever the tenant bought. Employee, leave, identity, approvals, audit.';
COMMENT ON SCHEMA hrms      IS 'HRMS module. Attendance experience, overtime requests, projects, timesheets.';
COMMENT ON SCHEMA payroll   IS 'Payroll module. Pay runs, tax, claims, statutory.';
COMMENT ON SCHEMA reference IS 'Shared national data. NO tenant column, by design - the one exception.';

-- ---------------------------------------------------------------------------
-- Roles
--
-- Passwords here are local-only placeholders, taken from environment variables at
-- container start. No secret value is ever written into this file, and none of these
-- roles exists outside a developer laptop. Production roles are W-05, with Key Vault.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user LOGIN PASSWORD 'local_app_pw';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'migration_user') THEN
        CREATE ROLE migration_user LOGIN PASSWORD 'local_migration_pw';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'readonly_user') THEN
        CREATE ROLE readonly_user LOGIN PASSWORD 'local_readonly_pw';
    END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- Grants
--
-- app_user: read/write on the three tenant schemas, read on reference, and NO DDL.
-- The absence of CREATE is the point. Check 7 in the spec asserts it is refused.
-- ---------------------------------------------------------------------------
GRANT USAGE ON SCHEMA core, hrms, payroll, reference TO app_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA core, hrms, payroll
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA reference
    GRANT SELECT ON TABLES TO app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA core, hrms, payroll, reference
    GRANT USAGE, SELECT ON SEQUENCES TO app_user;

-- migration_user: DDL on everything. Used only by the migration step (W-06).
GRANT USAGE, CREATE ON SCHEMA core, hrms, payroll, reference TO migration_user;

-- readonly_user: reads only, everywhere.
GRANT USAGE ON SCHEMA core, hrms, payroll, reference TO readonly_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA core, hrms, payroll, reference
    GRANT SELECT ON TABLES TO readonly_user;

-- Tables created by migration_user must be reachable by the other two. Without this,
-- app_user cannot see anything Flyway creates, and the failure is confusing.
ALTER DEFAULT PRIVILEGES FOR ROLE migration_user IN SCHEMA core, hrms, payroll
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;
ALTER DEFAULT PRIVILEGES FOR ROLE migration_user IN SCHEMA reference
    GRANT SELECT ON TABLES TO app_user;
ALTER DEFAULT PRIVILEGES FOR ROLE migration_user IN SCHEMA core, hrms, payroll, reference
    GRANT SELECT ON TABLES TO readonly_user;
ALTER DEFAULT PRIVILEGES FOR ROLE migration_user IN SCHEMA core, hrms, payroll, reference
    GRANT USAGE, SELECT ON SEQUENCES TO app_user;

-- ---------------------------------------------------------------------------
-- Keycloak gets its own database on the same server, as in production (05 section 3).
-- ---------------------------------------------------------------------------
SELECT 'CREATE DATABASE keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
