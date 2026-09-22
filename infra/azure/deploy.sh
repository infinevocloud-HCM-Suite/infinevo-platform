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

# ── Front Door backend prefixes (W-51 section 2.5) ───────────────────────────
# containerapps.bicep restricts every ingress to these addresses. The list is not stable -
# Microsoft republishes the service tag - so it is resolved on every run rather than
# committed. Resolved BEFORE the deployment, because it is a template parameter.
#
# IPv6 prefixes are dropped: Container Apps ipSecurityRestrictions.ipAddressRange takes
# IPv4 CIDR only, and a v6 entry fails the whole deployment with a validation error naming
# the rule rather than the address family.
echo "Resolving AzureFrontDoor.Backend service tag prefixes in ${LOCATION}..."
FD_PREFIX_LIST=$(az network list-service-tags --location "$LOCATION" \
  --query "values[?name=='AzureFrontDoor.Backend'].properties.addressPrefixes | [0]" -o tsv \
  | tr '\t' '\n' | grep -v ':' | grep -v '^$' || true)

if [[ -z "$FD_PREFIX_LIST" ]]; then
  echo "ERROR: AzureFrontDoor.Backend returned no IPv4 prefixes for ${LOCATION}." >&2
  echo "       Deploying with an empty list would leave every Container App ingress" >&2
  echo "       closed to Front Door as well - containerapps.bicep fails closed - so this" >&2
  echo "       stops here rather than shipping a perimeter nothing can pass." >&2
  exit 1
fi

FD_PREFIX_COUNT=$(echo "$FD_PREFIX_LIST" | wc -l | tr -d ' ')
FD_PREFIXES_JSON="[$(echo "$FD_PREFIX_LIST" | sed 's/^/"/;s/$/"/' | paste -sd, -)]"
echo "Resolved ${FD_PREFIX_COUNT} IPv4 Front Door backend prefixes."

# ── Transient Key Vault ipRule (W-51 section 2.3) ────────────────────────────
# kv-infinevo-shared runs networkAcls.defaultAction: 'Deny'. Every `az keyvault secret`
# call in this script is a DATA-PLANE call from this machine, which is not in the VNet, so
# without an ipRule for this one address they all return 403 - including the write below
# that is the only record of the administrator password.
#
# The rule is added two ways on purpose. The template parameter puts it in place as part of
# the deployment, because ipRules is declarative and deploying with an empty list would
# REMOVE a rule added by CLI mid-run; the CLI call covers the window before the deployment,
# when the vault already exists from a previous run and is read at the top of this script.
#
# It is revoked on EVERY exit path by the trap below - the pattern already proven at
# post-deploy-db.sh. W-51 section 5 step 2 fails if any ipRule survives, so a leaked rule
# is a verification failure rather than a silent exposure.
MY_IP=$(curl -s --max-time 10 https://api.ipify.org || true)
if [[ ! "$MY_IP" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "ERROR: could not determine this machine's public egress IP, so no Key Vault" >&2
  echo "       network rule can be opened and every secret write would return 403." >&2
  exit 1
fi
KV_IP_RULE="${MY_IP}/32"

if az keyvault show --name "$VAULT_NAME" >/dev/null 2>&1; then
  echo "Authorising ${KV_IP_RULE} on ${VAULT_NAME} for the duration of this script..."
  az keyvault network-rule add --name "$VAULT_NAME" --ip-address "$KV_IP_RULE" >/dev/null 2>&1 || true
  # The ACL takes several seconds to reach the data plane; without this the first secret
  # read below still returns 403 and the script reports a missing password instead.
  sleep 10
fi

revoke_kv_ip_rule() {
  # Unconditional rather than guarded on whether the CLI call above ran: the deployment
  # adds the rule through the template parameter even when that call was skipped because
  # the vault did not exist yet. Removing a rule that is not there is a no-op.
  if az keyvault show --name "$VAULT_NAME" >/dev/null 2>&1; then
    echo "Revoking transient Key Vault network rule ${KV_IP_RULE}..." >&2
    az keyvault network-rule remove --name "$VAULT_NAME" --ip-address "$KV_IP_RULE" >/dev/null 2>&1 || {
      echo "WARNING: could not revoke ${KV_IP_RULE} from ${VAULT_NAME}. W-51 section 5 step 2" >&2
      echo "         WILL FAIL while it survives. Remove it by hand:" >&2
      echo "  az keyvault network-rule remove --name ${VAULT_NAME} --ip-address ${KV_IP_RULE}" >&2
    }
  fi
}

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
# One trap, two jobs, and the order matters: persisting the password is itself a data-plane
# write, so it must happen while the network rule is still in place.
cleanup() {
  persist_admin_pw
  revoke_kv_ip_rule
}
trap cleanup EXIT

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
  --parameters frontDoorBackendPrefixes="$FD_PREFIXES_JSON" \
  --parameters keyVaultAllowedIpRules="[{\"value\":\"${KV_IP_RULE}\"}]" \
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

# ── Seed the four PostgreSQL role passwords (W-51 section 3e) ────────────────
# The migration job runs provision.sh, which needs app/migration/readonly/keycloak
# passwords, and id-migration-${ENV} holds Key Vault Secrets User - READ ONLY, by section
# 3f - so the job cannot create them itself. post-deploy-db.sh seeds them today, but it now
# runs AFTER this script and after the job, so on a fresh environment the job would fail
# with four missing secrets. Seeded here instead, inside the ipRule window, with the same
# generator and the same idempotency: an existing non-placeholder value is left alone.
echo "Verifying / seeding PostgreSQL role passwords in ${VAULT_NAME}..."
for SECRET_NAME in psql-app-pw psql-migration-pw psql-readonly-pw psql-keycloak-pw; do
  EXISTING_PW=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "$SECRET_NAME" --query value -o tsv 2>/dev/null || true)
  if [[ -z "$EXISTING_PW" ]] || [[ "$EXISTING_PW" =~ ^local_.*_pw$ ]]; then
    echo "Generating secure dynamic password for ${SECRET_NAME}..."
    NEW_PW=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9!#%*+=' | head -c 24)
    NEW_PW="${NEW_PW}Aa1!"
    az keyvault secret set --vault-name "$VAULT_NAME" --name "$SECRET_NAME" --value "$NEW_PW" >/dev/null
  fi
done

# ── Build the migration runner image ─────────────────────────────────────────
# `az acr build` rather than a local `docker build`: the runner has to exist in crinfinevo
# before the job can pull it, and CI has no Docker daemon. Built on every run because the
# tag is `latest` - a stale image would run yesterday's probes against today's perimeter
# and report OK for checks that no longer exist.
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
echo "Building migration-runner:latest in ${ACR_NAME} from infra/docker/migration-runner.Dockerfile..."
az acr build \
  --registry "$ACR_NAME" \
  --image "migration-runner:latest" \
  --platform linux/amd64 \
  --file "infra/docker/migration-runner.Dockerfile" \
  "$REPO_ROOT" >/dev/null

# ── Run the in-VNet migration job and gate on it (W-51 section 3e) ───────────
# This is what replaces the transient Postgres firewall rule that post-deploy-db.sh used to
# open: provision.sh now runs from inside snet-cae, against a server whose public endpoint
# is Disabled. The job also carries the six private-path probes of section 5 step 6.
JOB_NAME="caj-db-migration-${ENV}"
echo "Starting ${JOB_NAME}..."
JOB_EXEC=$(az containerapp job start -g "$APP_RG" -n "$JOB_NAME" --query name -o tsv)
if [[ -z "$JOB_EXEC" ]]; then
  echo "FAIL: ${JOB_NAME} did not start - no execution name was returned." >&2
  exit 1
fi
echo "Execution ${JOB_EXEC} started; polling for up to 900s (matches replicaTimeout)..."

JOB_STATUS=""
JOB_DEADLINE=$(( $(date +%s) + 900 ))
while :; do
  JOB_STATUS=$(az containerapp job execution show -g "$APP_RG" -n "$JOB_NAME" \
    --job-execution-name "$JOB_EXEC" --query status -o tsv 2>/dev/null || true)
  # `if`, not `cond && break`: under set -e an AND-list whose left side is false exits 1
  # and takes the whole script with it on the first poll, before the job has done anything.
  if [[ "$JOB_STATUS" == "Succeeded" || "$JOB_STATUS" == "Failed" ]]; then
    break
  fi
  if [[ "$(date +%s)" -gt "$JOB_DEADLINE" ]]; then
    JOB_STATUS="${JOB_STATUS:-<empty>} (timed out)"
    break
  fi
  sleep 10
done

# Logs are printed either way. On success they carry the six PROBE-*: OK lines that W-51
# section 5 step 6 greps for; on failure they are the only explanation of what broke.
az containerapp job logs show -g "$APP_RG" -n "$JOB_NAME" --execution "$JOB_EXEC" --tail 200 || true

# The same hard-fail shape as the AcrPull check above (review F-4): a loop that falls
# through silently would report this deployment green whatever the database did.
if [[ "$JOB_STATUS" != "Succeeded" ]]; then
  echo "FAIL: ${JOB_NAME}/${JOB_EXEC} reported ${JOB_STATUS}, not Succeeded." >&2
  echo "      provision.sh or one of the six private-path probes failed - the database is" >&2
  echo "      not bootstrapped, or the private data path is not working." >&2
  exit 1
fi
echo "PASS: ${JOB_NAME}/${JOB_EXEC} Succeeded - provision.sh ran over the private endpoint."

echo "================================================================="
echo " Environment [${ENV}] successfully provisioned and ready!"
echo " Next step: Run post-deploy-db.sh --env ${ENV}"
echo "================================================================="
