-- ---------------------------------------------------------------------------
-- 01-roles.sql — Platform Database Roles Creation
--
-- Creates the platform database roles with explicit security attributes.
--
-- app_user is the application's login role and holds the grants and the RLS policies.
-- worker_user is the worker container's own login and inherits them through membership,
-- so the worker connects as itself rather than sharing the app's credential (W-56).
--
-- Passwords are set outside dollar quotes via psql variables:
--   app_pw, worker_pw, migration_pw, readonly_pw, keycloak_pw
--
-- Three callers run this file: local Docker and Azure through psql, and the test
-- Testcontainers path through JDBC, which has no variable substitution and does its own.
-- Adding a variable here means adding it to PostgresTestContainerInitializer too.
-- ---------------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user WITH LOGIN NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    ELSE
        ALTER ROLE app_user WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'worker_user') THEN
        CREATE ROLE worker_user WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    ELSE
        ALTER ROLE worker_user WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;

    GRANT app_user TO worker_user;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'migration_user') THEN
        CREATE ROLE migration_user WITH LOGIN NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    ELSE
        ALTER ROLE migration_user WITH NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'readonly_user') THEN
        CREATE ROLE readonly_user WITH LOGIN NOCREATEROLE NOCREATEDB NOBYPASSRLS;
    ELSE
        ALTER ROLE readonly_user WITH NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'keycloak_user') THEN
        CREATE ROLE keycloak_user WITH LOGIN NOCREATEROLE NOCREATEDB NOBYPASSRLS;
    ELSE
        ALTER ROLE keycloak_user WITH NOCREATEROLE NOCREATEDB NOBYPASSRLS;
    END IF;
END
$$;

ALTER ROLE app_user WITH PASSWORD :'app_pw';
ALTER ROLE worker_user WITH PASSWORD :'worker_pw';
ALTER ROLE migration_user WITH PASSWORD :'migration_pw';
ALTER ROLE readonly_user WITH PASSWORD :'readonly_pw';
ALTER ROLE keycloak_user WITH PASSWORD :'keycloak_pw';
