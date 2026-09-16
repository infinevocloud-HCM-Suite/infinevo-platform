#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# provision.sh — PostgreSQL Canonical Provisioning Script Runner
#
# Runs 01-roles.sql, 02-schemas.sql, and 03-grants.sql sequentially using psql.
# ---------------------------------------------------------------------------
set -eo pipefail

PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-postgres}"
PGDATABASE="${PGDATABASE:-infinevo}"

APP_PW="${APP_PW:-local_app_pw}"
MIGRATION_PW="${MIGRATION_PW:-local_migration_pw}"
READONLY_PW="${READONLY_PW:-local_readonly_pw}"
KEYCLOAK_PW="${KEYCLOAK_PW:-local_keycloak_pw}"

PGHOST_ARG=()
if [ -n "${PGHOST:-}" ]; then
  PGHOST_ARG=(-h "$PGHOST")
fi

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

psql "${PGHOST_ARG[@]}" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 \
  -v app_pw="$APP_PW" \
  -v migration_pw="$MIGRATION_PW" \
  -v readonly_pw="$READONLY_PW" \
  -v keycloak_pw="$KEYCLOAK_PW" \
  -f "$DIR/01-roles.sql"

psql "${PGHOST_ARG[@]}" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 \
  -f "$DIR/02-schemas.sql"

psql "${PGHOST_ARG[@]}" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 \
  -f "$DIR/03-grants.sql"
