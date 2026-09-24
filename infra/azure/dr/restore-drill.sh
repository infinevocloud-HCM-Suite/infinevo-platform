#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# restore-drill.sh — Automated Point-In-Time Restore (PITR) Drill
#
# Performs an automated disaster recovery test against Azure PostgreSQL
# Flexible Server, measures RTO, verifies databases, and guarantees cleanup.
#
# Usage:
#   bash infra/azure/dr/restore-drill.sh [--env dev|uat|prod] [--source <server-name>]
#                                       [--keep-on-failure]
# ---------------------------------------------------------------------------
set -euo pipefail

ENV="dev"
SOURCE_SERVER=""
CLEANUP=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      ENV="$2"
      shift 2
      ;;
    --source)
      SOURCE_SERVER="$2"
      shift 2
      ;;
    --keep-on-failure)
      CLEANUP=false
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

RESOURCE_GROUP="rg-infinevo-${ENV}"
if [ -z "$SOURCE_SERVER" ]; then
  SOURCE_SERVER="psql-infinevo-${ENV}"
fi

DRILL_TARGET="${SOURCE_SERVER}-dr"
# Limit name to max 63 characters (PostgreSQL flexible server constraint)
if [ ${#DRILL_TARGET} -gt 63 ]; then
  DRILL_TARGET="${DRILL_TARGET:0:63}"
fi

echo "================================================================="
echo " Starting Disaster Recovery Point-In-Time Restore Drill"
echo " Environment:    [${ENV}]"
echo " Resource Group: [${RESOURCE_GROUP}]"
echo " Source Server:  [${SOURCE_SERVER}]"
echo " Drill Target:   [${DRILL_TARGET}]"
echo "================================================================="

# If running under Git Bash on Windows, alias az to az.cmd to avoid WSL forwarder failure
if command -v az.cmd >/dev/null 2>&1; then
  az() {
    az.cmd "$@"
  }
  export -f az
fi

# Verify Azure CLI login
az account show >/dev/null 2>&1 || {
  echo "ERROR: Not logged into Azure CLI. Run 'az login' first." >&2
  exit 1
}

# Trap handler: ALWAYS ensure drill target is cleaned up on exit
cleanup() {
  local exit_code=$?
  if [ "$CLEANUP" = true ]; then
    echo "-----------------------------------------------------------------"
    echo " Teardown Handler: Checking if ${DRILL_TARGET} exists for cleanup..."
    if az postgres flexible-server show -g "$RESOURCE_GROUP" -n "$DRILL_TARGET" >/dev/null 2>&1; then
      echo " Deleting temporary drill server [${DRILL_TARGET}] to prevent cloud costs..."
      az postgres flexible-server delete -g "$RESOURCE_GROUP" -n "$DRILL_TARGET" --yes --output none || true
      echo " Cleanup complete: ${DRILL_TARGET} deleted."
    else
      echo " Teardown Handler: ${DRILL_TARGET} does not exist or was already deleted."
    fi
    echo "-----------------------------------------------------------------"
  else
    echo " WARNING: --keep-on-failure specified. ${DRILL_TARGET} was NOT deleted."
  fi
  exit "$exit_code"
}
trap cleanup EXIT INT TERM

# 1. Query source server backup window
echo "Querying backup window from ${SOURCE_SERVER}..."
EARLIEST_RESTORE=$(az postgres flexible-server show \
  -g "$RESOURCE_GROUP" -n "$SOURCE_SERVER" \
  --query "backup.earliestRestoreDate" -o tsv | tr -d '\r')

if [ -z "$EARLIEST_RESTORE" ] || [ "$EARLIEST_RESTORE" = "None" ]; then
  echo "ERROR: Could not retrieve earliestRestoreDate from ${SOURCE_SERVER}." >&2
  exit 1
fi

echo "Earliest available restore timestamp: ${EARLIEST_RESTORE}"

# 2. Pick a safe restore timestamp (20 minutes ago in UTC)
RESTORE_POINT=$(date -u -d "20 minutes ago" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null | tr -d '\r' || \
                python3 -c "from datetime import datetime, timezone, timedelta; print((datetime.now(timezone.utc) - timedelta(minutes=20)).strftime('%Y-%m-%dT%H:%M:%SZ'))" 2>/dev/null | tr -d '\r' || \
                python -c "from datetime import datetime, timezone, timedelta; print((datetime.now(timezone.utc) - timedelta(minutes=20)).strftime('%Y-%m-%dT%H:%M:%SZ'))" 2>/dev/null | tr -d '\r')

echo "Selected Point-In-Time restore target: [${RESTORE_POINT}]"

# Clean up any leftover drill server if present before starting
if az postgres flexible-server show -g "$RESOURCE_GROUP" -n "$DRILL_TARGET" >/dev/null 2>&1; then
  echo "Found pre-existing ${DRILL_TARGET} - removing before new drill..."
  az postgres flexible-server delete -g "$RESOURCE_GROUP" -n "$DRILL_TARGET" --yes --output none
fi

# 3. Trigger Point-In-Time Restore
START_TIME=$(date +%s)
echo "Triggering Point-In-Time Restore from ${SOURCE_SERVER} to ${DRILL_TARGET}..."

az postgres flexible-server restore \
  --resource-group "$RESOURCE_GROUP" \
  --name "$DRILL_TARGET" \
  --source-server "$SOURCE_SERVER" \
  --restore-time "$RESTORE_POINT" \
  --no-wait

echo "Restore initiated. Polling for provisioning completion (timeout: 30 minutes)..."

DEADLINE=$(( START_TIME + 1800 ))
RESTORE_STATUS=""

while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  RESTORE_STATUS=$(az postgres flexible-server show \
    -g "$RESOURCE_GROUP" -n "$DRILL_TARGET" \
    --query "state" -o tsv 2>/dev/null | tr -d '\r' || true)
  if [ -z "$RESTORE_STATUS" ]; then
    RESTORE_STATUS="Starting"
  fi
  
  CURRENT_TIME=$(date +%s)
  ELAPSED=$(( CURRENT_TIME - START_TIME ))
  echo "  [+${ELAPSED}s] Status: ${RESTORE_STATUS}"

  if [ "$RESTORE_STATUS" = "Ready" ]; then
    break
  elif [ "$RESTORE_STATUS" = "Failed" ] || [ "$RESTORE_STATUS" = "Disabled" ]; then
    echo "ERROR: Server restore transitioned to failed state: ${RESTORE_STATUS}" >&2
    exit 1
  fi
  sleep 30
done

if [ "$RESTORE_STATUS" != "Ready" ]; then
  echo "ERROR: Restore drill timed out after 30 minutes (last status: ${RESTORE_STATUS})" >&2
  exit 1
fi

TOTAL_RTO_SECONDS=$(( $(date +%s) - START_TIME ))
echo "================================================================="
echo " RESTORE SUCCEEDED in ${TOTAL_RTO_SECONDS} seconds ($(( TOTAL_RTO_SECONDS / 60 ))m $(( TOTAL_RTO_SECONDS % 60 ))s)"
echo " Measured RTO meets < 30 minute target: YES"
echo "================================================================="

# 4. Verification of Restored Databases
echo "Verifying databases on restored server ${DRILL_TARGET}..."

INFINEVO_DB_STATE=$(az postgres flexible-server db show \
  -g "$RESOURCE_GROUP" -s "$DRILL_TARGET" -n infinevo \
  --query "name" -o tsv 2>/dev/null | tr -d '\r' || echo "MISSING")

KEYCLOAK_DB_STATE=$(az postgres flexible-server db show \
  -g "$RESOURCE_GROUP" -s "$DRILL_TARGET" -n keycloak \
  --query "name" -o tsv 2>/dev/null | tr -d '\r' || echo "MISSING")

if [ "$INFINEVO_DB_STATE" != "infinevo" ]; then
  echo "ERROR: 'infinevo' database was not restored properly on ${DRILL_TARGET}." >&2
  exit 1
fi

if [ "$KEYCLOAK_DB_STATE" != "keycloak" ]; then
  echo "ERROR: 'keycloak' database was not restored properly on ${DRILL_TARGET}." >&2
  exit 1
fi

echo "  - Database 'infinevo': VERIFIED"
echo "  - Database 'keycloak': VERIFIED"

echo "Listing all databases on ${DRILL_TARGET}:"
az postgres flexible-server db list -g "$RESOURCE_GROUP" -s "$DRILL_TARGET" -o table

echo "================================================================="
echo " DISASTER RECOVERY DRILL VERIFIED SUCCESSFULLY"
echo " - Source Server:    ${SOURCE_SERVER}"
echo " - Restore Time:     ${RESTORE_POINT}"
echo " - Target Server:    ${DRILL_TARGET}"
echo " - Measured RTO:     ${TOTAL_RTO_SECONDS}s"
echo " - Database Checks:  ALL PASSED"
echo "================================================================="

# Normal exit: trap will cleanly delete the drill target server
