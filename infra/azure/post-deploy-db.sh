#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# post-deploy-db.sh — Cloud Database Role & Schema Bootstrap
#
# Generates role passwords into Key Vault, runs provision.sh against
# Azure Database for PostgreSQL Flexible Server, and verifies privileges.
#
# Usage:
#   bash infra/azure/post-deploy-db.sh [--env dev|uat|prod]
# ---------------------------------------------------------------------------
set -euo pipefail

ENV="dev"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      ENV="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! "$ENV" =~ ^(dev|uat|prod)$ ]]; then
  echo "ERROR: --env must be one of: dev, uat, prod" >&2
  exit 1
fi

echo "================================================================="
echo " Bootstrapping Database for Environment: [${ENV}]"
echo "================================================================="

VAULT_NAME="kv-infinevo-shared"
SERVER_NAME="psql-infinevo-${ENV}"
RG_NAME="rg-infinevo-${ENV}"

# 1. Resolve host and admin credentials
echo "Resolving PostgreSQL server endpoint..."
# Declared before assignment on purpose: `export PGHOST=$(...)` returns export's exit
# status, not the command's, so set -e never sees a failed lookup. An empty PGHOST makes
# provision.sh drop -h and silently bootstrap localhost instead (review F-11).
PGHOST=$(az postgres flexible-server show -g "$RG_NAME" -n "$SERVER_NAME" --query fullyQualifiedDomainName -o tsv)
if [[ -z "$PGHOST" ]]; then
  echo "ERROR: could not resolve ${SERVER_NAME} in ${RG_NAME}. Refusing to continue -" >&2
  echo "       an empty PGHOST would bootstrap the local machine, not Azure." >&2
  exit 1
fi
export PGHOST
export PGPORT="5432"
export PGDATABASE="infinevo"
export PGUSER="infinevo_admin"

echo "Fetching administrator password from Key Vault (${VAULT_NAME})..."
export PGPASSWORD=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "psql-admin-pw" --query value -o tsv)

if [[ -z "$PGPASSWORD" ]]; then
  echo "ERROR: psql-admin-pw not found in Key Vault." >&2
  exit 1
fi

# 2. No firewall rule is opened. W-51 replaced it.
# The block that used to sit here added the operator's public IP to psql-infinevo-{env} for
# the length of this script and revoked it on exit. It exists no longer because the server
# now runs publicNetworkAccess: 'Disabled' behind a private endpoint in snet-pe - there is
# no public listener for a firewall rule to admit, so the rule would grant nothing and the
# comment that named this ticket as the thing that retires it has been honoured.
#
# WHAT RUNS provision.sh NOW: caj-db-migration-{env}, from inside snet-cae, started and
# gated by deploy.sh (W-51 section 3e). This script keeps its Key Vault reads and its
# privilege checks, but every psql call below needs a route INTO THE VNET, so it only
# succeeds from inside it or through the break-glass procedure in W-51 section 8c. Run it
# to re-verify privileges after a break-glass session, not as part of a normal deployment.

# 3. Generate and store role passwords in Key Vault if absent
declare -A ROLE_SECRETS=(
  ["APP_PW"]="psql-app-pw"
  ["WORKER_PW"]="psql-worker-pw"
  ["MIGRATION_PW"]="psql-migration-pw"
  ["READONLY_PW"]="psql-readonly-pw"
  ["KEYCLOAK_PW"]="psql-keycloak-pw"
)

echo "Verifying / seeding application role passwords in Key Vault..."
for VAR_NAME in "${!ROLE_SECRETS[@]}"; do
  SECRET_NAME="${ROLE_SECRETS[$VAR_NAME]}"
  PW=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "$SECRET_NAME" --query value -o tsv 2>/dev/null || true)
  
  if [[ -z "$PW" ]] || [[ "$PW" =~ ^local_.*_pw$ ]]; then
    echo "Generating secure dynamic password for ${SECRET_NAME}..."
    NEW_PW=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9!#%*+=' | head -c 24)
    NEW_PW="${NEW_PW}Aa1!"
    az keyvault secret set --vault-name "$VAULT_NAME" --name "$SECRET_NAME" --value "$NEW_PW" >/dev/null
    PW="$NEW_PW"
  fi
  
  export "$VAR_NAME"="$PW"
done

# Double check that no password uses local repo fallbacks
for VAR_NAME in APP_PW WORKER_PW MIGRATION_PW READONLY_PW KEYCLOAK_PW; do
  VAL="${!VAR_NAME}"
  if [[ "$VAL" =~ ^local_.*_pw$ ]]; then
    echo "ERROR: $VAR_NAME is using committed repo default literal '$VAL'" >&2
    exit 1
  fi
done

echo "PASS: All role credentials dynamically generated and retrieved from Key Vault."

# 4. Execute canonical provision.sh against Flexible Server
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROVISION_SCRIPT="${SCRIPT_DIR}/../postgres/provision.sh"

if [[ ! -f "$PROVISION_SCRIPT" ]]; then
  echo "ERROR: provision.sh not found at ${PROVISION_SCRIPT}" >&2
  exit 1
fi

echo "Running ${PROVISION_SCRIPT} as ${PGUSER} against ${PGHOST}..."
bash "$PROVISION_SCRIPT"

# 4. Verify Schema Creation and Ownership
echo "Verifying schema creation and migration_user ownership..."
SCHEMA_COUNT=$(PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -t -A -c \
  "SELECT count(*) FROM pg_namespace n JOIN pg_roles r ON n.nspowner = r.oid WHERE n.nspname IN ('core','hrms','payroll','reference','migration') AND r.rolname = 'migration_user';")

if [[ "$SCHEMA_COUNT" != "5" ]]; then
  echo "FAIL: Expected 5 schemas owned by migration_user, found ${SCHEMA_COUNT}." >&2
  exit 1
fi
echo "PASS: All 5 schemas exist and are owned by migration_user."

# 5. Check 6b: Exercise app_user login and DDL refusal
# app_user is the application's login role and keeps its password (spec rev 5, §0).
APP_ROLE="app_user"
APP_ROLE_PW=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "psql-app-pw" --query value -o tsv 2>/dev/null || true)
if [[ -z "$APP_ROLE_PW" ]]; then
  echo "FAIL: Could not retrieve secret psql-app-pw from ${VAULT_NAME} for role ${APP_ROLE}" >&2
  exit 1
fi

echo "Exercising ${APP_ROLE} login with generated Key Vault password (Check 6b)..."
APP_CURRENT_USER=$(PGPASSWORD="$APP_ROLE_PW" psql -h "$PGHOST" -U "$APP_ROLE" -d "$PGDATABASE" -t -A -c "SELECT current_user;")
if [[ "$APP_CURRENT_USER" != "$APP_ROLE" ]]; then
  echo "FAIL: ${APP_ROLE} login returned current_user='${APP_CURRENT_USER}' (expected '${APP_ROLE}')." >&2
  exit 1
fi
echo "PASS: ${APP_ROLE} connected successfully with its Key Vault password."

# The refusal is proved by the privilege error itself, not by a nonzero psql exit: a
# password prompt or a missing role exits nonzero just as well (spec §3b, rev2 finding B-6).
echo "Testing that ${APP_ROLE} is refused DDL permissions..."
DDL_OUT=$(PGPASSWORD="$APP_ROLE_PW" psql -h "$PGHOST" -U "$APP_ROLE" -d "$PGDATABASE" -c \
  "CREATE TABLE core.should_not_exist(id int);" 2>&1 || true)
if echo "$DDL_OUT" | grep -q "permission denied for schema core"; then
  echo "PASS: ${APP_ROLE} was correctly refused DDL permissions (permission denied for schema core)."
else
  echo "FAIL: ${APP_ROLE} DDL test failed (expected permission denied for schema core): ${DDL_OUT}" >&2
  exit 1
fi

echo "================================================================="
echo " Database bootstrap and privilege verification complete!"
echo "================================================================="
