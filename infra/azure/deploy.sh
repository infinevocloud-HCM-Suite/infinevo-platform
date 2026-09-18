#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# deploy.sh — Idempotent End-to-End Azure Environment Deployment
#
# Usage:
#   bash infra/azure/deploy.sh [--env dev|uat|prod] [--location centralindia]
# ---------------------------------------------------------------------------
set -euo pipefail

# Resolved from the script's own location so it runs from any working directory
# (review F-15); post-deploy-db.sh already did this.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ENV="dev"
LOCATION="centralindia"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      ENV="$2"
      shift 2
      ;;
    --location)
      LOCATION="$2"
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
echo " Deploying Infinevo Platform Environment: [${ENV}] in [${LOCATION}]"
echo "================================================================="

# Verify Azure CLI login
az account show >/dev/null 2>&1 || {
  echo "ERROR: Not logged into Azure CLI. Run 'az login' first." >&2
  exit 1
}

SUB_ID=$(az account show --query id -o tsv)
echo "Active Subscription: ${SUB_ID}"

DEPLOYER_OID=$(az ad signed-in-user show --query id -o tsv 2>/dev/null || true)
if [[ -z "$DEPLOYER_OID" ]]; then
  # A service principal (CI) has no signed-in user; resolve its own object id instead.
  DEPLOYER_OID=$(az ad sp show --id "$(az account show --query user.name -o tsv)" --query id -o tsv 2>/dev/null || true)
fi
if [[ -z "$DEPLOYER_OID" ]]; then
  echo "ERROR: could not resolve the deploying principal object id. Without it the" >&2
  echo "       template cannot grant Key Vault Secrets Officer and every secret write" >&2
  echo "       later in this script would fail with 403 (review F-5)." >&2
  exit 1
fi
echo "Deploying principal object id: ${DEPLOYER_OID}"

SHARED_RG="rg-infinevo-shared"
VAULT_NAME="kv-infinevo-shared"
ADMIN_USER="infinevo_admin"

# Check if psql-admin-pw is already stored in Key Vault
ADMIN_PW=""
if az keyvault show --name "$VAULT_NAME" >/dev/null 2>&1; then
  ADMIN_PW=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "psql-admin-pw" --query value -o tsv 2>/dev/null || true)
fi

if [[ -z "$ADMIN_PW" ]]; then
  echo "Generating new secure password for ${ADMIN_USER}..."
  ADMIN_PW=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9!#%*+=' | head -c 24)
  # Ensure password meets complexity requirements
  ADMIN_PW="${ADMIN_PW}Aa1!"
fi

# The admin password is generated in memory and only reaches Key Vault after the
# deployment returns. If anything in between fails, the Flexible Server exists with a
# password nobody holds - so persist it on every exit path (review F-12).
ADMIN_PW_STORED=0
persist_admin_pw() {
  if [[ "$ADMIN_PW_STORED" -eq 0 ]] && az keyvault show --name "$VAULT_NAME" >/dev/null 2>&1; then
    echo "Persisting administrator password to Key Vault before exit..." >&2
    if az keyvault secret set --vault-name "$VAULT_NAME" --name "psql-admin-pw" --value "$ADMIN_PW" >/dev/null 2>&1; then
      ADMIN_PW_STORED=1
    else
      echo "WARNING: could not write psql-admin-pw to ${VAULT_NAME}. If a Flexible Server" >&2
      echo "         was created in this run its administrator password is now lost and" >&2
      echo "         must be reset with: az postgres flexible-server update --admin-password" >&2
    fi
  fi
}
trap persist_admin_pw EXIT

PARAM_FILE="${SCRIPT_DIR}/parameters/${ENV}.bicepparam"
if [[ ! -f "$PARAM_FILE" ]]; then
  echo "ERROR: Parameter file $PARAM_FILE not found." >&2
  exit 1
fi

DEPLOY_NAME="infinevo-${ENV}-$(date +%Y%m%d%H%M%S)"
echo "Executing subscription deployment: ${DEPLOY_NAME}..."

az deployment sub create \
  --name "$DEPLOY_NAME" \
  --location "$LOCATION" \
  --template-file "${SCRIPT_DIR}/main.bicep" \
  --parameters "$PARAM_FILE" \
  --parameters deployerObjectId="$DEPLOYER_OID" \
  --parameters postgresAdminPassword="$ADMIN_PW" \
  --parameters postgresAdminUsername="$ADMIN_USER" \
  --output table

echo "Deployment finished. Storing/updating administrator credentials in Key Vault..."
az keyvault secret set --vault-name "$VAULT_NAME" --name "psql-admin-pw" --value "$ADMIN_PW" >/dev/null
ADMIN_PW_STORED=1

echo "Seeding ACR and exercising AcrPull against private registry (Check 5b)..."
ACR_NAME="crinfinevo"
SMOKE_IMAGE="platform-smoke:latest"

az acr import \
  --name "$ACR_NAME" \
  --source "mcr.microsoft.com/k8se/quickstart:latest" \
  --image "$SMOKE_IMAGE" \
  --force >/dev/null

APP_NAME="ca-infinevo-${ENV}-app"
APP_RG="rg-infinevo-${ENV}"
ACR_LOGIN_SERVER=$(az acr show --name "$ACR_NAME" --query loginServer -o tsv)

echo "Repointing ${APP_NAME} to ${ACR_LOGIN_SERVER}/${SMOKE_IMAGE}..."
az containerapp update \
  -g "$APP_RG" \
  -n "$APP_NAME" \
  --image "${ACR_LOGIN_SERVER}/${SMOKE_IMAGE}" >/dev/null

echo "Waiting for ${APP_NAME} to reach Running status..."
STATE=""
for i in {1..30}; do
  STATE=$(az containerapp show -g "$APP_RG" -n "$APP_NAME" --query "properties.runningStatus" -o tsv 2>/dev/null || true)
  if [[ "$STATE" == "Running" ]]; then
    echo "PASS: ${APP_NAME} runningStatus = Running (successfully pulled from private ${ACR_LOGIN_SERVER})"
    break
  fi
  sleep 5
done

# The loop used to fall through silently, so this reported green whatever happened and
# the one test of AcrPull against the private registry could never fail (review F-4).
if [[ "$STATE" != "Running" ]]; then
  echo "FAIL: ${APP_NAME} runningStatus is ${STATE:-<empty>} after 150s, not Running." >&2
  echo "      It could not pull ${ACR_LOGIN_SERVER}/${SMOKE_IMAGE} - AcrPull is not working." >&2
  exit 1
fi

echo "================================================================="
echo " Environment [${ENV}] successfully provisioned and ready!"
echo " Next step: Run post-deploy-db.sh --env ${ENV}"
echo "================================================================="
