#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# migration-runner-entrypoint.sh — what caj-db-migration-{env} actually runs.
#
#   1. assume id-migration-{env} and read the five PostgreSQL passwords from Key Vault
#   2. run infra/postgres/provision.sh UNCHANGED against the private $PGHOST
#   3. run the six private-path probes of W-51 section 5 step 6
#
# Exits non-zero on any failure, which is what makes `deploy.sh` and W-51 section 5 step 6
# able to gate on the job's status rather than on its log text alone.
#
# NO SECRET IS BAKED INTO THE IMAGE OR PASSED AS AN ENVIRONMENT VARIABLE FROM BICEP. The
# job's env block carries names and endpoints; every password is fetched here, at run time,
# over the Key Vault private endpoint, using a role assignment that can be revoked.
# ---------------------------------------------------------------------------
set -euo pipefail

echo "================================================================="
echo " caj-db-migration — environment [${ENVIRONMENT}]"
echo "================================================================="

# ── 1. Assume id-migration ───────────────────────────────────────────────────
# `--username` rather than `--client-id`: the Azure CLI pinned in
# migration-runner.Dockerfile predates the rename. If that base image is bumped, check the
# flag before assuming this still works.
echo "Authenticating as id-migration-${ENVIRONMENT} (${MIGRATION_CLIENT_ID})..."
export APPSETTING_WEBSITE_SITE_NAME="${APPSETTING_WEBSITE_SITE_NAME:-azcli-workaround}"
if [[ -n "${IDENTITY_ENDPOINT:-}" ]]; then
  export MSI_ENDPOINT="${MSI_ENDPOINT:-$IDENTITY_ENDPOINT}"
  export MSI_SECRET="${MSI_SECRET:-${IDENTITY_HEADER:-}}"
fi
az login --identity --username "$MIGRATION_CLIENT_ID" >/dev/null
az account set --subscription "$AZURE_SUBSCRIPTION_ID" >/dev/null

# ── 2. Fetch the PostgreSQL credentials from Key Vault ───────────────────────
# Declared before assignment on purpose, for the same reason post-deploy-db.sh:43-45 gives:
# `export X=$(...)` returns export's status, not the command's, so set -e never sees a
# failed lookup and provision.sh would run with an empty password.
read_secret() {
  local name="$1" value
  value="$(az keyvault secret show --vault-name "$KEY_VAULT_NAME" --name "$name" \
           --query value -o tsv 2>/dev/null || true)"
  if [[ -z "$value" ]]; then
    echo "ERROR: secret '${name}' is absent from ${KEY_VAULT_NAME}, or id-migration-${ENVIRONMENT}" >&2
    echo "       cannot read it. id-migration holds Key Vault Secrets User (read only) by" >&2
    echo "       design (W-51 section 3f), so this job CANNOT create the secret itself." >&2
    echo "       deploy.sh seeds all five before starting this job; if you started the job" >&2
    echo "       by hand, run deploy.sh --env ${ENVIRONMENT} first." >&2
    return 1
  fi
  printf '%s' "$value"
}

echo "Reading PostgreSQL credentials from ${KEY_VAULT_NAME}..."
PGPASSWORD="$(read_secret psql-admin-pw)"
APP_PW="$(read_secret psql-app-pw)"
WORKER_PW="$(read_secret psql-worker-pw)"
MIGRATION_PW="$(read_secret psql-migration-pw)"
READONLY_PW="$(read_secret psql-readonly-pw)"
KEYCLOAK_PW="$(read_secret psql-keycloak-pw)"
export PGPASSWORD APP_PW WORKER_PW MIGRATION_PW READONLY_PW KEYCLOAK_PW

# The committed local-development fallbacks in provision.sh:13-16 must never reach a cloud
# server. post-deploy-db.sh:119-126 makes the same check; it is repeated here because this
# job is now the path that actually runs provision.sh against Azure.
for var in APP_PW WORKER_PW MIGRATION_PW READONLY_PW KEYCLOAK_PW; do
  if [[ "${!var}" =~ ^local_.*_pw$ ]]; then
    echo "ERROR: ${var} holds the committed repo default '${!var}'." >&2
    exit 1
  fi
done

# ── 3. Run provision.sh, unchanged ───────────────────────────────────────────
# W-51 section 3c: infra/postgres/ is not touched by this ticket. The image carries it
# verbatim and the script is idempotent (section 3e, "Idempotency").
echo "Running provision.sh as ${PGUSER} against ${PGHOST}..."
bash /app/postgres/provision.sh
echo "provision.sh completed."

# ── 4. Prove the private data path ───────────────────────────────────────────
# Run last, and its exit status is the job's: a green provision.sh with a red probe must
# still report Failed, otherwise the perimeter is unverified and the deployment looks fine.
exec bash /app/private-path-probes.sh
