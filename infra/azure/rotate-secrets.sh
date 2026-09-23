#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# rotate-secrets.sh — Secret Rotation CLI
#
# Automates rotation for:
#   1. PostgreSQL role passwords (app_user, worker_user) — WINDOWED, not zero-downtime.
#      Flexible Server holds one password per role, so changing it drops the open
#      HikariCP connections. Revision 5 of W-56 accepts a short planned window rather
#      than carrying a dual-role scheme to avoid it (spec §0, §3f).
#   2. Keycloak OAuth2 client secret — zero-downtime; Keycloak holds two live secrets.
#   3. Third-party API keys (Brevo) — zero-downtime; Brevo allows several live keys.
#
# Adheres to:
#   - D-53: Transient /32 IP firewall rule on Key Vault with automatic cleanup trap
#   - D-10: Container Apps revision restart
# ---------------------------------------------------------------------------
set -eo pipefail

ENV="dev"
TARGET="postgres"
DRY_RUN=false
PG_URL=""

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Options:
  --env <dev|uat|prod>     Target environment (default: dev)
  --target <postgres|keycloak|brevo>
                           Secret target to rotate (default: postgres)
  --postgres-url <url>     Optional PostgreSQL connection URL (e.g. for local testing)
  --dry-run                Simulate rotation and print the sequence without executing
  -h, --help               Show this help message
EOF
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      ENV="$2"
      shift 2
      ;;
    --target)
      TARGET="$2"
      shift 2
      ;;
    --postgres-url)
      PG_URL="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    -h|--help)
      usage
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      ;;
  esac
done

VAULT_NAME="kv-infinevo-shared"
RG_ENV="rg-infinevo-${ENV}"
APP_NAME="ca-infinevo-${ENV}-app"
WORKER_NAME="ca-infinevo-${ENV}-worker"
KV_IP_RULE=""

# D-53: Transient Key Vault network rule cleanup
revoke_kv_ip_rule() {
  if [[ -n "$KV_IP_RULE" ]]; then
    echo "Revoking transient IP rule ${KV_IP_RULE} from Key Vault ${VAULT_NAME}..." >&2
    az keyvault network-rule remove --name "$VAULT_NAME" --ip-address "$KV_IP_RULE" >/dev/null 2>&1 || {
      echo "WARNING: Failed to revoke ${KV_IP_RULE} from ${VAULT_NAME}." >&2
    }
    KV_IP_RULE=""
  fi
}

cleanup() {
  revoke_kv_ip_rule
}
trap cleanup EXIT ERR

add_kv_ip_rule() {
  if [[ "$DRY_RUN" = true ]]; then
    return 0
  fi
  echo "Resolving egress IP for Key Vault firewall rule..."
  local my_ip
  my_ip=$(curl -s4 https://ifconfig.me/ip || curl -s4 https://api.ipify.org || true)
  if [[ -n "$my_ip" ]]; then
    KV_IP_RULE="${my_ip}/32"
    echo "Adding transient IP rule ${KV_IP_RULE} to ${VAULT_NAME}..."
    az keyvault network-rule add --name "$VAULT_NAME" --ip-address "$KV_IP_RULE" >/dev/null
    sleep 10
  fi
}

generate_password() {
  local pw
  pw=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9!#%*+=' | head -c 24)
  echo "${pw}Aa1!"
}

# One role, one container app. The window is between the ALTER ROLE and the container
# coming back on the new secret — seconds, planned, quarterly (spec §3f).
rotate_postgres_role() {
  local role="$1" secret_name="$2" ca_name="$3"
  local new_pw admin_pw pg_host revision state deadline

  echo "── Rotating ${role} (${secret_name}) on ${ca_name}"

  echo "Step 1: Reading psql-admin-pw from key vault and testing the admin connection..."
  admin_pw=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "psql-admin-pw" --query value -o tsv)
  # --postgres-url overrides the derived host, so a rotation can be aimed at a test
  # server. It was parsed and ignored (review F-1): passing it silently rotated the
  # real Azure server instead, which is the worst possible outcome for a flag whose
  # whole purpose is to avoid that.
  pg_host="${PG_URL:-psql-infinevo-${ENV}.postgres.database.azure.com}"
  # Preflight. A rotation that half-succeeds is worse than one that refuses to start
  # (spec §4 break 4), so nothing is written until the admin credential is proven.
  if [[ "$(PGPASSWORD="$admin_pw" psql -h "$pg_host" -U "infinevo_admin" -d "infinevo" -t -A -c 'SELECT 1')" != "1" ]]; then
    echo "FAIL: infinevo_admin could not authenticate with psql-admin-pw. Nothing was" >&2
    echo "      changed — resolve the credential before rotating ${role}." >&2
    return 1
  fi

  new_pw=$(generate_password)

  echo "Step 2: ALTER ROLE ${role} WITH PASSWORD — the window opens here..."
  PGPASSWORD="$admin_pw" psql -h "$pg_host" -U "infinevo_admin" -d "infinevo" -v ON_ERROR_STOP=1 -c \
    "ALTER ROLE ${role} WITH PASSWORD '${new_pw}';"

  echo "Step 3: Writing the new value to key vault (${secret_name})..."
  az keyvault secret set --vault-name "$VAULT_NAME" --name "$secret_name" --value "$new_pw" >/dev/null

  echo "Step 4: Restart the ${ca_name} revision so it re-resolves ${secret_name}..."
  revision=$(az containerapp show -g "$RG_ENV" -n "$ca_name" --query "properties.latestRevisionName" -o tsv)
  if [[ -z "$revision" ]]; then
    echo "FAIL: ${ca_name} has no latest revision to restart. The database password has" >&2
    echo "      already changed — restart the app by hand before it is called again." >&2
    return 1
  fi
  az containerapp revision restart -g "$RG_ENV" -n "$ca_name" --revision "$revision" >/dev/null

  echo "Step 5: Verifying ${revision} reaches Healthy..."
  state=""
  deadline=$(( $(date +%s) + 300 ))
  while :; do
    state=$(az containerapp revision show -g "$RG_ENV" -n "$ca_name" --revision "$revision" \
      --query "properties.healthState" -o tsv 2>/dev/null || true)
    if [[ "$state" == "Healthy" ]]; then
      break
    fi
    if [[ "$(date +%s)" -gt "$deadline" ]]; then
      break
    fi
    sleep 10
  done
  if [[ "$state" != "Healthy" ]]; then
    echo "FAIL: ${revision} healthState is ${state:-<empty>} after 300s. ${role} was rotated" >&2
    echo "      but ${ca_name} is not serving — roll back per SECRET_ROTATION.md section 5." >&2
    return 1
  fi
  echo "PASS: ${ca_name} healthy on the new ${secret_name}. The window is closed."
}

rotate_postgres() {
  if [[ "$DRY_RUN" = true ]]; then
    echo "== DRY RUN: PostgreSQL windowed role password rotation =="
    echo "Step 0: Add this runner's /32 egress IP to the key vault firewall (D-53)"
    echo "Step 1: Read psql-admin-pw from key vault and preflight the admin connection"
    echo "Step 2: ALTER ROLE app_user / worker_user WITH PASSWORD — the window opens"
    echo "Step 3: Write the new value to key vault (psql-app-pw, psql-worker-pw)"
    echo "Step 4: Restart the app and worker revisions so they re-resolve the secret"
    echo "Step 5: Verify each revision reaches Healthy — the window closes"
    echo "Step 6: Revoke the transient key vault IP rule (EXIT trap)"
    return 0
  fi

  add_kv_ip_rule

  rotate_postgres_role "app_user" "psql-app-pw" "$APP_NAME"
  rotate_postgres_role "worker_user" "psql-worker-pw" "$WORKER_NAME"

  echo "PostgreSQL role password rotation successfully completed!"
}

rotate_keycloak() {
  if [[ "$DRY_RUN" = true ]]; then
    echo "== DRY RUN: Keycloak Client Secret Rotation =="
    echo "Step 1: Generate secondary client secret in Keycloak"
    echo "Step 2: Store secret in key vault (keycloak-client-secret)"
    echo "Step 3: Roll container revision"
    echo "Step 4: Drain old connections"
    echo "Step 5: Promote secondary secret and delete old secret"
    return 0
  fi
  echo "FAIL: Keycloak rotation is not implemented - it requires live Keycloak Admin" >&2
  echo "      API access. Nothing was rotated. Use --dry-run to see the intended steps." >&2
  return 1
}

rotate_brevo() {
  if [[ "$DRY_RUN" = true ]]; then
    echo "== DRY RUN: Brevo API Key Rotation =="
    echo "Step 1: Generate new API key in Brevo"
    echo "Step 2: Store in key vault (brevo-api-key)"
    echo "Step 3: Roll container revision"
    echo "Step 4: Drain old connections"
    echo "Step 5: Revoke old Brevo API key"
    return 0
  fi
  echo "FAIL: Brevo rotation is not implemented - it requires live Brevo API access." >&2
  echo "      Nothing was rotated. Use --dry-run to see the intended steps." >&2
  return 1
}

case "$TARGET" in
  postgres)
    rotate_postgres
    ;;
  keycloak)
    rotate_keycloak
    ;;
  brevo)
    rotate_brevo
    ;;
  *)
    echo "Unknown target: $TARGET" >&2
    exit 1
    ;;
esac
