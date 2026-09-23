-- ---------------------------------------------------------------------------
-- 03-grants.sql — Privileges, Default Privileges, and Security Verification
--
-- Pure ANSI/Postgres SQL only. No psql meta-commands (\set, \connect, \i).
-- Executable identically by psql, Testcontainers JDBC, and Azure pipelines.
-- ---------------------------------------------------------------------------

-- Database Level Connection Security
-- F-11: provision.sh parameterises PGDATABASE, so the database name must not be
-- hardcoded here. format()/current_database() works identically under psql and
-- JDBC, where a psql variable would not.
DO $$
BEGIN
    EXECUTE format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', current_database());
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO app_user, worker_user, migration_user, readonly_user',
                   current_database());
END
$$;

-- Schema Usage Grants
GRANT USAGE, CREATE ON SCHEMA core, hrms, payroll, reference TO migration_user;
GRANT USAGE, CREATE ON SCHEMA migration TO migration_user;
GRANT USAGE ON SCHEMA core, hrms, payroll, reference TO app_user;
GRANT USAGE ON SCHEMA core, hrms, payroll, reference TO readonly_user;
-- No grant on migration schema to app_user or readonly_user (spec §4 file 10, §13 R1)

-- Default Privileges FOR ROLE migration_user
ALTER DEFAULT PRIVILEGES FOR ROLE migration_user IN SCHEMA core, hrms, payroll
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;

ALTER DEFAULT PRIVILEGES FOR ROLE migration_user IN SCHEMA reference
    GRANT SELECT ON TABLES TO app_user;

ALTER DEFAULT PRIVILEGES FOR ROLE migration_user IN SCHEMA core, hrms, payroll, reference
    GRANT SELECT ON TABLES TO readonly_user;

ALTER DEFAULT PRIVILEGES FOR ROLE migration_user IN SCHEMA core, hrms, payroll, reference
    GRANT USAGE, SELECT ON SEQUENCES TO app_user;

-- Security Self-Check Assertion Block
DO $$
DECLARE
    r RECORD;
    has_usage boolean;
BEGIN
    FOR r IN
        SELECT rolname, rolsuper, rolbypassrls
        FROM pg_roles
        WHERE rolname IN ('app_user', 'worker_user', 'migration_user', 'readonly_user', 'keycloak_user')
    LOOP
        IF r.rolsuper THEN
            RAISE EXCEPTION 'Security assertion failed: Role % must NOT be superuser', r.rolname;
        END IF;
        IF r.rolbypassrls THEN
            RAISE EXCEPTION 'Security assertion failed: Role % must NOT have BYPASSRLS', r.rolname;
        END IF;
    END LOOP;

    -- Assert app_user has no USAGE on the migration schema (spec §13 R1)
    SELECT has_schema_privilege('app_user', 'migration', 'USAGE') INTO has_usage;
    IF has_usage THEN
        RAISE EXCEPTION 'Security assertion failed: app_user must NOT have USAGE on the migration schema';
    END IF;
END
$$;
