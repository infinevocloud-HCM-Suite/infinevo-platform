#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 00-bootstrap.sh — Local Docker Postgres Entrypoint Initializer
#
# Calls canonical infra/postgres/provision.sh script to create roles,
# schemas, and grants, then provisions the isolated keycloak database.
# ---------------------------------------------------------------------------
set -eo pipefail

KEYCLOAK_PW="${KEYCLOAK_PW:-local_keycloak_pw}"
APP_PW="${APP_PW:-local_app_pw}"
WORKER_PW="${WORKER_PW:-local_worker_pw}"
MIGRATION_PW="${MIGRATION_PW:-local_migration_pw}"
READONLY_PW="${READONLY_PW:-local_readonly_pw}"

# 1. Execute Canonical Platform Provisioning Script
APP_PW="$APP_PW" WORKER_PW="$WORKER_PW" MIGRATION_PW="$MIGRATION_PW" READONLY_PW="$READONLY_PW" KEYCLOAK_PW="$KEYCLOAK_PW" bash /provision/provision.sh

# 2. Isolated Keycloak Database Setup
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE keycloak OWNER keycloak_user'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec

    ALTER DATABASE keycloak OWNER TO keycloak_user;
    REVOKE CONNECT ON DATABASE keycloak FROM PUBLIC;
    GRANT CONNECT ON DATABASE keycloak TO keycloak_user;
EOSQL
