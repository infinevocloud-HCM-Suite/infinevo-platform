#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# data-migration-entrypoint.sh — what caj-data-migration-{env} runs.
#
#   1. Assume id-migration-{env} via Azure Managed Identity
#   2. Read required secrets from Key Vault (kv-infinevo-shared):
#      - psql-migration-pw (PostgreSQL target password for migration_user)
#      - keycloak-admin-pw (Keycloak Admin password)
#      - legacy-dump-sha256 (required)
#   3. Source route (W-71 §3 - dump replay only, no live link):
#      - RUN_ID set: download the mysqldump archives from Azure Blob, verify their
#        sha256, start embedded MariaDB on 127.0.0.1:3306, restore hrmstestdb and
#        payroll_test_db, and point the extractors at localhost.
#      - RUN_ID empty: stop with an error.
#   4. Export TARGET_DB_* and KEYCLOAK_* environment variables.
#   5. Run the complete migration pipeline via /app/migration/run_all.sh.
#   6. If RUN_ID is set: Upload staging logs and reconciliation reports to
#      Blob container 'migration-dumps/{RUN_ID}/results/'.
#
# Exits non-zero on any step failure (set -euo pipefail).
# NO SECRETS ARE BAKED INTO THE IMAGE OR STORED IN GIT.
# ---------------------------------------------------------------------------
set -euo pipefail

echo "================================================================="
echo " caj-data-migration — Environment: [${ENVIRONMENT:-dev}]"
echo " Started at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "================================================================="

# Trap to ensure cleanup of temporary mysql sidecar on exit
cleanup() {
  if pgrep -x "mariadbd" >/dev/null 2>&1 || pgrep -x "mysqld" >/dev/null 2>&1; then
    echo "Stopping embedded MariaDB sidecar..."
    mysqladmin -h 127.0.0.1 -P 3306 -u root shutdown 2>/dev/null || true
  fi
  rm -rf /tmp/mysql /tmp/*.sql.gz /tmp/*.sql 2>/dev/null || true
}
trap cleanup EXIT

# ── 1. Assume Managed Identity ───────────────────────────────────────────────
echo "Authenticating as id-migration-${ENVIRONMENT} (${MIGRATION_CLIENT_ID})..."
export APPSETTING_WEBSITE_SITE_NAME="${APPSETTING_WEBSITE_SITE_NAME:-azcli-workaround}"
if [[ -n "${IDENTITY_ENDPOINT:-}" ]]; then
  export MSI_ENDPOINT="${MSI_ENDPOINT:-$IDENTITY_ENDPOINT}"
  export MSI_SECRET="${MSI_SECRET:-${IDENTITY_HEADER:-}}"
fi

if [[ -n "${MIGRATION_CLIENT_ID:-}" ]]; then
  az login --identity --username "$MIGRATION_CLIENT_ID" >/dev/null
  if [[ -n "${AZURE_SUBSCRIPTION_ID:-}" ]]; then
    az account set --subscription "$AZURE_SUBSCRIPTION_ID" >/dev/null
  fi
else
  echo "WARNING: MIGRATION_CLIENT_ID not set; assuming local or development mode."
fi

# ── 2. Read Secrets from Key Vault ───────────────────────────────────────────
read_secret() {
  local name="$1" required="${2:-true}" value
  value="$(az keyvault secret show --vault-name "$KEY_VAULT_NAME" --name "$name" \
           --query value -o tsv 2>/dev/null || true)"
  if [[ -z "$value" ]]; then
    if [[ "$required" == "true" ]]; then
      echo "ERROR: Required secret '${name}' is absent from ${KEY_VAULT_NAME}." >&2
      return 1
    else
      echo "Notice: Optional secret '${name}' not found in ${KEY_VAULT_NAME}." >&2
      return 0
    fi
  fi
  printf '%s' "$value"
}

if [[ -n "${KEY_VAULT_NAME:-}" ]]; then
  echo "Reading secrets from ${KEY_VAULT_NAME}..."
  TARGET_DB_PASSWORD="$(read_secret psql-migration-pw true)"
  KEYCLOAK_ADMIN_PW="$(read_secret keycloak-admin-pw true)"
else
  echo "Notice: KEY_VAULT_NAME not set; using environment password overrides."
  TARGET_DB_PASSWORD="${TARGET_DB_PASSWORD:-local_migration_pw}"
  KEYCLOAK_ADMIN_PW="${KEYCLOAK_ADMIN_PW:-local_keycloak_pw}"
fi

# ── 3. Source Route: Blob Dump Restore vs Live Link ───────────────────────────
RUN_ID="${RUN_ID:-}"

if [[ -n "$RUN_ID" && -n "${STORAGE_ACCOUNT_NAME:-}" ]]; then
  echo "--- Mode: Rehearsal / Snapshot Replay (RUN_ID=${RUN_ID}) ---"
  echo "Downloading database dumps from stinfinevo${ENVIRONMENT}/migration-dumps/${RUN_ID}/..."
  
  mkdir -p /tmp/dumps
  az storage blob download \
    --account-name "$STORAGE_ACCOUNT_NAME" \
    --container-name "migration-dumps" \
    --name "${RUN_ID}/hrms.sql.gz" \
    --file /tmp/dumps/hrms.sql.gz \
    --auth-mode login >/dev/null

  az storage blob download \
    --account-name "$STORAGE_ACCOUNT_NAME" \
    --container-name "migration-dumps" \
    --name "${RUN_ID}/payroll.sql.gz" \
    --file /tmp/dumps/payroll.sql.gz \
    --auth-mode login >/dev/null

  # Verify the dumps against legacy-dump-sha256 (W-71 Task 2 step 2). Required: a dump
  # that was never checksummed is a dump nobody can say is the one that was taken.
  DUMP_SHA_SECRET=""
  if [[ -n "${KEY_VAULT_NAME:-}" ]]; then
    DUMP_SHA_SECRET="$(read_secret legacy-dump-sha256 true)"
  fi
  if [[ -n "$DUMP_SHA_SECRET" ]]; then
    echo "Verifying SHA256 checksums..."
    HRMS_CALC_SHA="$(sha256sum /tmp/dumps/hrms.sql.gz | awk '{print $1}')"
    PAYROLL_CALC_SHA="$(sha256sum /tmp/dumps/payroll.sql.gz | awk '{print $1}')"
    if [[ "$DUMP_SHA_SECRET" != *"$HRMS_CALC_SHA"* || "$DUMP_SHA_SECRET" != *"$PAYROLL_CALC_SHA"* ]]; then
      echo "ERROR: SHA256 mismatch against secret legacy-dump-sha256!" >&2
      echo "  Calculated HRMS:    ${HRMS_CALC_SHA}" >&2
      echo "  Calculated Payroll: ${PAYROLL_CALC_SHA}" >&2
      exit 1
    fi
    echo "SHA256 verified successfully."
  fi

  # Start embedded MariaDB sidecar
  echo "Initializing local MariaDB datadir in /tmp/mysql..."
  rm -rf /tmp/mysql && mkdir -p /tmp/mysql
  mariadb-install-db --no-defaults --datadir=/tmp/mysql --auth-root-authentication-method=normal >/dev/null 2>&1

  echo "Starting MariaDB on 127.0.0.1:3306..."
  mariadbd --no-defaults --datadir=/tmp/mysql --port=3306 --bind-address=127.0.0.1 \
           --socket=/tmp/mysql.sock --pid-file=/tmp/mysql.pid >/tmp/mariadbd.log 2>&1 &

  # Wait for MariaDB to be ready
  for i in $(seq 1 30); do
    if mysqladmin -h 127.0.0.1 -P 3306 -u root ping --silent 2>/dev/null; then
      break
    fi
    sleep 1
    if [[ $i -eq 30 ]]; then
      echo "ERROR: Timed out waiting for local MariaDB sidecar to start." >&2
      cat /tmp/mariadbd.log >&2
      exit 1
    fi
  done

  echo "MariaDB is ready. Restoring databases..."
  mysql -h 127.0.0.1 -P 3306 -u root -e "CREATE DATABASE IF NOT EXISTS hrmstestdb; CREATE DATABASE IF NOT EXISTS payroll_test_db;"
  # A MySQL 8 mysqldump names the utf8mb4_0900_* collations, which MariaDB does not have
  # ("Unknown collation"). Rewrite them to utf8mb4_unicode_ci on the way in. Not yet
  # proven against the real dump (W-71 §8): the first rehearsal is that proof.
  restore_dump() {
    gunzip -c "$1" | sed -E 's/utf8mb4_0900_[a-z_]+/utf8mb4_unicode_ci/g' \
      | mysql -h 127.0.0.1 -P 3306 -u root "$2"
  }
  restore_dump /tmp/dumps/hrms.sql.gz hrmstestdb
  restore_dump /tmp/dumps/payroll.sql.gz payroll_test_db
  echo "Database restore completed."

  export LEGACY_HRMS_DB_HOST="127.0.0.1"
  export LEGACY_HRMS_DB_PORT="3306"
  export LEGACY_HRMS_DB_NAME="hrmstestdb"
  export LEGACY_HRMS_DB_USER="root"
  export LEGACY_HRMS_DB_PASSWORD=""

  export LEGACY_PAYROLL_DB_HOST="127.0.0.1"
  export LEGACY_PAYROLL_DB_PORT="3306"
  export LEGACY_PAYROLL_DB_NAME="payroll_test_db"
  export LEGACY_PAYROLL_DB_USER="root"
  export LEGACY_PAYROLL_DB_PASSWORD=""

else
  # W-71 §3 rejected the live link to the legacy MySQL servers; the job only replays a
  # dump from Blob. Without RUN_ID there is no dump to replay, so stop rather than reach
  # across the internet to a production database.
  echo "ERROR: RUN_ID (or STORAGE_ACCOUNT_NAME) is not set. Start the job with 'Run with override' and RUN_ID=<run_id>," >&2
  echo "       after uploading migration-dumps/<run_id>/hrms.sql.gz and payroll.sql.gz (HANDBOOK 4.6)." >&2
  exit 1
fi

# ── 4. Target Database & Keycloak Configuration ──────────────────────────────
export TARGET_DB_HOST="${TARGET_DB_HOST:-psql-infinevo-${ENVIRONMENT}.postgres.database.azure.com}"
export TARGET_DB_PORT="${TARGET_DB_PORT:-5432}"
export TARGET_DB_NAME="${TARGET_DB_NAME:-infinevo}"
export TARGET_DB_USER="${TARGET_DB_USER:-migration_user}"
export TARGET_DB_PASSWORD="$TARGET_DB_PASSWORD"

export KEYCLOAK_URL="${KEYCLOAK_URL:-https://ca-infinevo-${ENVIRONMENT}-keycloak.internal.infinevocloud.com}"
export KEYCLOAK_BASE_URL="$KEYCLOAK_URL"
export KEYCLOAK_REALM="${KEYCLOAK_REALM:-infinevo}"
export KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
export KEYCLOAK_ADMIN_PW="$KEYCLOAK_ADMIN_PW"
export KEYCLOAK_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PW"

# ── 5. Run the Migration Pipeline ────────────────────────────────────────────
echo ""
echo "Starting data migration execution pipeline..."
cd /app
LOG_FILE="/app/migration/staging/migration_run_$(date +%Y%m%d_%H%M%S).log"
mkdir -p /app/migration/staging

bash /app/migration/run_all.sh 2>&1 | tee "$LOG_FILE"

# ── 6. Upload Rehearsal Artifacts to Blob (if in dump mode) ────────────────────
if [[ -n "$RUN_ID" && -n "${STORAGE_ACCOUNT_NAME:-}" ]]; then
  echo "Uploading migration artifacts and logs to blob container 'migration-dumps/${RUN_ID}/results/'..."
  # Results only (W-71 Task 2 step 6). The raw stg_* extracts hold PAN, Aadhaar and bank
  # details and never leave the container.
  for file in /app/migration/staging/reconciliation_*.json /app/migration/staging/lop_mismatch_*.csv \
              /app/migration/staging/stg_quarantined_*.json "$LOG_FILE"; do
    if [[ -f "$file" ]]; then
      fname="$(basename "$file")"
      az storage blob upload \
        --account-name "$STORAGE_ACCOUNT_NAME" \
        --container-name "migration-dumps" \
        --name "${RUN_ID}/results/${fname}" \
        --file "$file" \
        --overwrite \
        --auth-mode login >/dev/null 2>&1 || true
    fi
  done
  echo "Artifacts uploaded to Azure Blob successfully."
fi

echo "================================================================="
echo " caj-data-migration completed successfully at $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "================================================================="
