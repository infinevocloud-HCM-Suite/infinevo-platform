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

# 2. Open a temporary firewall rule for this machine
# W-51 brings private endpoints; until then the server carries only the Azure-services
# rule, so an operator running this script has no route to it at all (review F-2).
# The rule is named for this run and revoked on every exit path, success or failure -
# which is what the spec's risk table promised and the script did not do.
FW_RULE_NAME="deploy-$(date +%Y%m%d%H%M%S)-$$"
FW_RULE_CREATED=0

revoke_firewall_rule() {
  if [[ "$FW_RULE_CREATED" -eq 1 ]]; then
    echo "Revoking temporary firewall rule ${FW_RULE_NAME}..." >&2
    az postgres flexible-server firewall-rule delete --resource-group "$RG_NAME" --name "$SERVER_NAME" --rule-name "$FW_RULE_NAME" --yes >/dev/null 2>&1 || {
      echo "WARNING: could not revoke ${FW_RULE_NAME}. Delete it by hand:" >&2
      echo "  az postgres flexible-server firewall-rule delete -g ${RG_NAME} -n ${SERVER_NAME} --rule-name ${FW_RULE_NAME} --yes" >&2
    }
  fi
}
trap revoke_firewall_rule EXIT

MY_IP=$(curl -s --max-time 10 https://api.ipify.org || true)
if [[ ! "$MY_IP" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "ERROR: could not determine this machine's public IP, so no firewall rule can be" >&2
  echo "       opened and psql cannot reach ${PGHOST}." >&2
  exit 1
fi

echo "Authorising ${MY_IP} on ${SERVER_NAME} for the duration of this script..."
az postgres flexible-server firewall-rule create --resource-group "$RG_NAME" --name "$SERVER_NAME" --rule-name "$FW_RULE_NAME" --start-ip-address "$MY_IP" --end-ip-address "$MY_IP" >/dev/null
FW_RULE_CREATED=1

# 3. Generate and store role passwords in Key Vault if absent
declare -A ROLE_SECRETS=(
  ["APP_PW"]="psql-app-pw"
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
for VAR_NAME in APP_PW MIGRATION_PW READONLY_PW KEYCLOAK_PW; do
  VAL="${!VAR_NAME}"
  if [[ "$VAL" =~ ^local_.*_pw$ ]]; then
    echo "ERROR: $VAR_NAME is using committed repo default literal '$VAL'" >&2
    exit 1
  fi
done

echo "PASS: All four role credentials dynamically generated and retrieved from Key Vault."

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

# 5. Check 6b: Exercise app_user Login and DDL Refusal
echo "Exercising app_user login with generated Key Vault password (Check 6b)..."
APP_CURRENT_USER=$(PGPASSWORD="$APP_PW" psql -h "$PGHOST" -U app_user -d "$PGDATABASE" -t -A -c "SELECT current_user;")
if [[ "$APP_CURRENT_USER" != "app_user" ]]; then
  echo "FAIL: app_user login returned current_user='${APP_CURRENT_USER}' (expected 'app_user')." >&2
  exit 1
fi
echo "PASS: app_user connected successfully with its Key Vault password."

echo "Testing that app_user is refused DDL permissions..."
if PGPASSWORD="$APP_PW" psql -h "$PGHOST" -U app_user -d "$PGDATABASE" -c \
  "CREATE TABLE core.should_not_exist(id int);" >/dev/null 2>&1; then
  echo "FAIL: app_user was allowed to create a table — permissions misconfigured." >&2
  exit 1
fi
echo "PASS: app_user was correctly refused DDL permissions."

echo "================================================================="
echo " Database bootstrap and privilege verification complete!"
echo "================================================================="
