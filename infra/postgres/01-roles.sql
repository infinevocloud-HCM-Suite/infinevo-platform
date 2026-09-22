-- ---------------------------------------------------------------------------
-- 01-roles.sql — Platform Database Roles Creation
--
-- Creates platform database roles (app_user, migration_user, readonly_user, keycloak_user)
-- with explicit security attributes.
-- Passwords are set outside dollar quotes via psql variables:
-- :'app_pw', :'migration_pw', :'readonly_pw', :'keycloak_pw'
-- ---------------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    ELSE
        ALTER ROLE app_user WITH NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'migration_user') THEN
        CREATE ROLE migration_user WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    ELSE
        ALTER ROLE migration_user WITH NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'readonly_user') THEN
        CREATE ROLE readonly_user WITH LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB NOBYPASSRLS;
    ELSE
        ALTER ROLE readonly_user WITH NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'keycloak_user') THEN
        CREATE ROLE keycloak_user WITH LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB NOBYPASSRLS;
    ELSE
        ALTER ROLE keycloak_user WITH NOSUPERUSER NOCREATEROLE NOCREATEDB NOBYPASSRLS;
    END IF;
END
$$;

ALTER ROLE app_user WITH PASSWORD :'app_pw';
ALTER ROLE migration_user WITH PASSWORD :'migration_pw';
ALTER ROLE readonly_user WITH PASSWORD :'readonly_pw';
ALTER ROLE keycloak_user WITH PASSWORD :'keycloak_pw';
