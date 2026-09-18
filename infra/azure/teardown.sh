#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# teardown.sh — Safe Ephemeral Environment Teardown Script
#
# Usage:
#   bash infra/azure/teardown.sh [--env dev|uat]
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

if [[ "$ENV" == "prod" ]]; then
  echo "ERROR: prod teardown is forbidden. Production resources require manual elevation in the Azure portal." >&2
  exit 1
fi

if [[ ! "$ENV" =~ ^(dev|uat)$ ]]; then
  echo "ERROR: --env must be one of: dev, uat" >&2
  exit 1
fi

TARGET_RG="rg-infinevo-${ENV}"

echo "================================================================="
echo " Initiating Teardown for Ephemeral Environment: [${ENV}]"
echo " Target Resource Group: ${TARGET_RG}"
echo " Note: Shared resources (rg-infinevo-shared) will remain untouched."
echo "================================================================="

if az group exists --name "$TARGET_RG" | grep -q true; then
  echo "Deleting resource group ${TARGET_RG}..."
  az group delete --name "$TARGET_RG" --yes --no-wait
  
  echo "Waiting for complete resource group deletion (az group wait --deleted)..."
  az group wait --deleted --name "$TARGET_RG"
  echo "PASS: ${TARGET_RG} successfully deleted."
else
  echo "Resource group ${TARGET_RG} does not exist. Nothing to tear down."
fi

echo "================================================================="
echo " Teardown completed cleanly."
echo "================================================================="
