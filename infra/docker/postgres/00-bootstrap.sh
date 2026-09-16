#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 00-bootstrap.sh — Local Docker Postgres Entrypoint Initializer
#
# Creates the keycloak_user role and keycloak database (Q3), then calls
# the canonical infra/postgres/provision.sh script.
# ---------------------------------------------------------------------------
set -eo pipefail

KEYCLOAK_PW="${KEYCLOAK_PW:-local_keycloak_pw}"
APP_PW="${APP_PW:-local_app_pw}"
MIGRATION_PW="${MIGRATION_PW:-local_migration_pw}"
READONLY_PW="${READONLY_PW:-local_readonly_pw}"

# 1. Dedicated Keycloak Role and Isolated Database
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'keycloak_user') THEN
            CREATE ROLE keycloak_user WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD '$KEYCLOAK_PW';
        ELSE
            ALTER ROLE keycloak_user WITH NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
        END IF;
    END
    \$\$;

    SELECT 'CREATE DATABASE keycloak OWNER keycloak_user'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec

    ALTER DATABASE keycloak OWNER TO keycloak_user;
    REVOKE CONNECT ON DATABASE keycloak FROM PUBLIC;
    GRANT CONNECT ON DATABASE keycloak TO keycloak_user;
EOSQL

# 2. Execute Canonical Platform Provisioning Script
APP_PW="$APP_PW" MIGRATION_PW="$MIGRATION_PW" READONLY_PW="$READONLY_PW" /provision/provision.sh
