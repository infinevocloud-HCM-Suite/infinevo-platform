#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# deploy.sh — Idempotent End-to-End Azure Environment Deployment
#
# Usage:
#   bash infra/azure/deploy.sh [--env dev|uat|prod] [--location centralindia]
#                              [--image-tag git-<sha>]
#
# Without --image-tag this is an INFRASTRUCTURE-ONLY run: it reads what every Container
# App is running and which revision holds the traffic, hands both back to the template,
# and so changes neither. With --image-tag it declares that release instead.
# ---------------------------------------------------------------------------
set -euo pipefail

# Resolved from the script's own location so it runs from any working directory
# (review F-15); post-deploy-db.sh already did this.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ENV="dev"
LOCATION="centralindia"
# Empty means "infrastructure only": the apps keep the images they are already running and
# caj-flyway-{env} keeps its unpullable sentinel tag. Supply --image-tag git-<sha> only to
# declare a RELEASE from this script; the pipeline passes the same value through
# backendImageTag (W-54 findings F-7 and F-8).
IMAGE_TAG=""

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
    --image-tag)
      IMAGE_TAG="$2"
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

APP_RG="rg-infinevo-${ENV}"

# ── Read the live apps, so this deployment cannot move them (W-54 finding F-1) ──
# containerapps.bicep must name an image and a traffic target on every run; `image` is a
# required property and there is no "leave it" in Bicep. The previous version defaulted
# both to a guess, and the guess reverted dev to the starter image with 100 percent of the
# traffic on it. So the values are not guessed any more - they are read off the running
# apps, immediately before the deployment, and handed straight back to the template. An
# infrastructure-only run therefore declares exactly what is already there.
#
# On a first deployment nothing is readable, both objects stay {} and the template falls
# back to starterImage and latestRevision, which is what a brand-new app needs.
CURRENT_IMAGES_JSON="{}"
TRAFFIC_REVISIONS_JSON="{}"
APP_TRAFFIC_PIN=""

if [[ "$(az group exists --name "$APP_RG")" == "true" ]]; then
  echo "Reading the live Container Apps in ${APP_RG} so this deployment leaves them where they are..."
  IMG_PAIRS=""
  PIN_PAIRS=""
  for CA_ROLE in app worker web keycloak; do
    CA_NAME="ca-infinevo-${ENV}-${CA_ROLE}"
    LIVE_IMAGE=$(az containerapp show -g "$APP_RG" -n "$CA_NAME" \
      --query "properties.template.containers[0].image" -o tsv 2>/dev/null || true)
    if [[ -z "$LIVE_IMAGE" ]]; then
      echo "  ${CA_NAME}: not present - it will be created on starterImage."
      continue
    fi
    IMG_PAIRS="${IMG_PAIRS:+${IMG_PAIRS},}\"${CA_ROLE}\":\"${LIVE_IMAGE}\""
    echo "  ${CA_NAME}: image ${LIVE_IMAGE}"

    # The worker has no ingress and so no traffic block (W-50); nothing to pin.
    if [[ "$CA_ROLE" != "worker" ]]; then
      LIVE_PIN=$(az containerapp revision list -g "$APP_RG" -n "$CA_NAME" \
        --query "[?properties.trafficWeight==\`100\`].name | [0]" -o tsv 2>/dev/null || true)
      if [[ -z "$LIVE_PIN" || "$LIVE_PIN" == "None" ]]; then
        # Not a missing app - the app answered, but no single revision holds the whole
        # weight. That is a half-finished traffic shift. Passing {} here would emit
        # latestRevision and hand 100 percent to whatever revision this deployment
        # creates, which is the F-1 failure in a different costume. Stop instead.
        echo "FAIL: ${CA_NAME} exists but no revision holds 100% of the traffic." >&2
        echo "      A traffic shift is half-finished. Deploying now would re-point the" >&2
        echo "      weight at a revision nothing has health-checked. Settle the weights" >&2
        echo "      first:  az containerapp ingress traffic show -g ${APP_RG} -n ${CA_NAME}" >&2
        exit 1
      fi
      PIN_PAIRS="${PIN_PAIRS:+${PIN_PAIRS},}\"${CA_ROLE}\":\"${LIVE_PIN}\""
      echo "  ${CA_NAME}: 100% traffic on ${LIVE_PIN}"
      if [[ "$CA_ROLE" == "app" ]]; then
        APP_TRAFFIC_PIN="$LIVE_PIN"
      fi
    fi
  done
  CURRENT_IMAGES_JSON="{${IMG_PAIRS}}"
  TRAFFIC_REVISIONS_JSON="{${PIN_PAIRS}}"
fi

if [[ -n "$IMAGE_TAG" ]]; then
  echo "RELEASE deploy: all four apps and caj-flyway-${ENV} will be declared at tag ${IMAGE_TAG}."
  echo "                The images read above are ignored; the traffic pins are not."
else
  echo "Infrastructure-only deploy: no --image-tag, so no app changes image and no weight moves."
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
  --parameters backendImageTag="$IMAGE_TAG" \
  --parameters containerAppCurrentImages="$CURRENT_IMAGES_JSON" \
  --parameters containerAppTrafficRevisions="$TRAFFIC_REVISIONS_JSON" \
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
ACR_LOGIN_SERVER=$(az acr show --name "$ACR_NAME" --query loginServer -o tsv)

# ── The AcrPull smoke test must not undo the deployment (W-54 finding F-1) ──
# W-51 check 5b proves the app can pull from the private registry by repointing it at
# platform-smoke:latest. That is a write to the running image, and it is the LOUDEST way
# this script could break the guarantee the template now gives: it would take a released
# ca-infinevo-dev-app off its git-<sha> image on every infrastructure run.
#
# It is skipped whenever the app is already running an image out of crinfinevo, because in
# that case the app IS the proof - a running replica on a private-registry image is a
# successful AcrPull, and repointing it proves nothing the app does not already show. The
# repoint still happens on a placeholder from mcr.microsoft.com, which is the only state
# where the check has anything to add.
SMOKE_SKIPPED=0
CURRENT_APP_IMAGE=$(az containerapp show -g "$APP_RG" -n "$APP_NAME" \
  --query "properties.template.containers[0].image" -o tsv 2>/dev/null || true)
if [[ "$CURRENT_APP_IMAGE" == "${ACR_LOGIN_SERVER}/"* && "$CURRENT_APP_IMAGE" != "${ACR_LOGIN_SERVER}/${SMOKE_IMAGE}" ]]; then
  echo "Skipping the smoke repoint: ${APP_NAME} already runs ${CURRENT_APP_IMAGE} from the"
  echo "private registry, so AcrPull is already demonstrated and overwriting it would"
  echo "revert a release."
  SMOKE_SKIPPED=1
else
  echo "Repointing ${APP_NAME} to ${ACR_LOGIN_SERVER}/${SMOKE_IMAGE}..."
  az containerapp update \
    -g "$APP_RG" \
    -n "$APP_NAME" \
    --image "${ACR_LOGIN_SERVER}/${SMOKE_IMAGE}" >/dev/null
fi

# THE REVISION, NOT THE APP. This used to poll properties.runningStatus on the app, which
# was a sound reading under activeRevisionsMode 'Single' - one revision, so the app's state
# WAS that revision's state. The three ingress apps are 'Multiple' now (W-54 F-9), the
# smoke revision is born at 0 percent beside the old one, and the app reports Running off
# the OLD revision whether or not the new one ever pulled. That check would have passed on
# a total AcrPull failure. Named revision, and runningState on it, is the only reading that
# still means what it says.
#
# WHICH revision depends on which branch above ran, and getting this backwards turns a
# healthy environment red. Repointed: the revision that repoint created, which is the
# latest. Skipped: the revision SERVING, because that is the one already pulling from
# crinfinevo - the latest revision is then the inert 0-percent one this deployment minted,
# and before W-56 lands it has no env block, so it crash-loops by design (see the `env`
# comments in containerapps.bicep) and would fail a check it was never the subject of.
if [[ "$SMOKE_SKIPPED" -eq 1 && -n "$APP_TRAFFIC_PIN" ]]; then
  SMOKE_REVISION="$APP_TRAFFIC_PIN"
else
  SMOKE_REVISION=$(az containerapp show -g "$APP_RG" -n "$APP_NAME" \
    --query "properties.latestRevisionName" -o tsv 2>/dev/null || true)
fi
if [[ -z "$SMOKE_REVISION" ]]; then
  echo "FAIL: could not read latestRevisionName from ${APP_NAME}, so nothing proves the" >&2
  echo "      app can pull from the private registry." >&2
  exit 1
fi

echo "Waiting for revision ${SMOKE_REVISION} to reach runningState Running..."
STATE=""
for i in {1..30}; do
  STATE=$(az containerapp revision show -g "$APP_RG" -n "$APP_NAME" \
    --revision "$SMOKE_REVISION" --query "properties.runningState" -o tsv 2>/dev/null || true)
  if [[ "$STATE" == "Running" ]]; then
    echo "PASS: ${SMOKE_REVISION} runningState = Running (successfully pulled from private ${ACR_LOGIN_SERVER})"
    break
  fi
  sleep 5
done

# The loop used to fall through silently, so this reported green whatever happened and
# the one test of AcrPull against the private registry could never fail (review F-4).
if [[ "$STATE" != "Running" ]]; then
  echo "FAIL: ${SMOKE_REVISION} runningState is ${STATE:-<empty>} after 150s, not Running." >&2
  if [[ "$SMOKE_SKIPPED" -eq 1 ]]; then
    echo "      The smoke repoint was skipped because ${APP_NAME} already runs" >&2
    echo "      ${CURRENT_APP_IMAGE}, so this is that released revision failing to run." >&2
  else
    echo "      It could not pull ${ACR_LOGIN_SERVER}/${SMOKE_IMAGE} - AcrPull is not working." >&2
  fi
  exit 1
fi

# ── Seed canonical platform secrets (W-56) ──────────────────────────────────
# The migration job and container apps need all canonical secrets seeded:
# psql-app-pw, psql-worker-pw, psql-migration-pw, psql-readonly-pw, psql-keycloak-pw,
# keycloak-admin-pw, keycloak-client-secret, brevo-api-key, jwt-signing-secret
# (plus psql-admin-pw above) - ten in all.
# Seeded here inside the ipRule window, with the same generator and idempotency:
# an existing non-placeholder value is left alone.
echo "Verifying / seeding canonical platform secrets in ${VAULT_NAME}..."
for SECRET_NAME in psql-app-pw psql-worker-pw psql-migration-pw psql-readonly-pw psql-keycloak-pw keycloak-admin-pw keycloak-client-secret brevo-api-key jwt-signing-secret; do
  EXISTING_PW=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "$SECRET_NAME" --query value -o tsv 2>/dev/null || true)
  if [[ -z "$EXISTING_PW" ]] || [[ "$EXISTING_PW" =~ ^local_.*_pw$ ]]; then
    echo "Generating secure dynamic secret for ${SECRET_NAME}..."
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
#
# The tag follows --image-tag when one was given, because main.bicep resolves
# caj-db-migration-{env} to `migration-runner:${backendImageTag}` and falls back to
# :latest only when the tag is empty (main.bicep:478, W-54 finding F-7). Building :latest
# here while the job had been declared at :git-<sha> would leave the job pointing at an
# image this run never produced.
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RUNNER_TAG="${IMAGE_TAG:-latest}"
echo "Building migration-runner:${RUNNER_TAG} in ${ACR_NAME} from infra/docker/migration-runner.Dockerfile..."
az acr build \
  --registry "$ACR_NAME" \
  --image "migration-runner:${RUNNER_TAG}" \
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
    --job-execution-name "$JOB_EXEC" --query "properties.status" -o tsv 2>/dev/null || true)
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
az containerapp job logs show -g "$APP_RG" -n "$JOB_NAME" --execution "$JOB_EXEC" --container "migration-runner" --tail 200 || true

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
