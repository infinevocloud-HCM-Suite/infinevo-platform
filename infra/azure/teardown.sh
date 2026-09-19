#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# teardown.sh — Safe Ephemeral Environment Teardown Script
#
# Usage:
#   bash infra/azure/teardown.sh [--env dev|uat]
# ---------------------------------------------------------------------------
set -euo pipefail

# No default. The prod guard below is solid, but the unguarded path was the one with no
# argument at all: a bare `bash teardown.sh` deleted rg-infinevo-dev, unprompted (F-14).
ENV=""

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

if [[ -z "$ENV" ]]; then
  echo "ERROR: --env is required. Usage: bash infra/azure/teardown.sh --env dev|uat" >&2
  echo "       This script deletes a resource group; it will not guess which one." >&2
  exit 1
fi

if [[ "$ENV" == "prod" ]]; then
  echo "ERROR: prod teardown is forbidden. Production resources require manual elevation in the Azure portal." >&2
  exit 1
fi

if [[ ! "$ENV" =~ ^(dev|uat)$ ]]; then
  echo "ERROR: --env must be one of: dev, uat" >&2
  exit 1
fi

TARGET_RG="rg-infinevo-${ENV}"
SHARED_RG="rg-infinevo-shared"
AFD_PROFILE="afd-infinevo-shared"
WAF_POLICY="wafinfinevoshared"

echo "================================================================="
echo " Initiating Teardown for Ephemeral Environment: [${ENV}]"
echo " Target Resource Group: ${TARGET_RG}"
echo " Note: Shared resources (rg-infinevo-shared) are left in place,"
echo "       apart from this environment's own Front Door endpoint,"
echo "       routes, origin groups and WAF association (W-51 T3)."
echo "================================================================="

# ── Front Door (W-51) ────────────────────────────────────────────────────────
# The profile is shared by dev, uat and prod and lives in rg-infinevo-shared, so deleting
# the environment resource group leaves this environment's endpoint behind, pointing at
# origins that no longer exist. Everything below is scoped by ${ENV} for that reason -
# tearing down dev must not disturb uat.
#
# Order matters: the security policy references the endpoint, and the routes reference both
# the endpoint and the origin groups. Deleting the endpoint first would leave the control
# plane rejecting the security policy delete.
#
# The VNet, private endpoints, private DNS zones and their VNet links are all inside
# ${TARGET_RG} and go with the group delete below; they need no separate handling.
if az afd profile show -g "$SHARED_RG" --profile-name "$AFD_PROFILE" >/dev/null 2>&1; then
  echo "Front Door profile ${AFD_PROFILE} found. Removing [${ENV}] resources..."

  if az afd security-policy show -g "$SHARED_RG" --profile-name "$AFD_PROFILE" \
       --security-policy-name "sp-infinevo-${ENV}" >/dev/null 2>&1; then
    echo "  - security policy sp-infinevo-${ENV} (WAF association)"
    az afd security-policy delete -g "$SHARED_RG" --profile-name "$AFD_PROFILE" \
      --security-policy-name "sp-infinevo-${ENV}" --yes
  fi

  if az afd endpoint show -g "$SHARED_RG" --profile-name "$AFD_PROFILE" \
       --endpoint-name "ep-infinevo-${ENV}" >/dev/null 2>&1; then
    # Deleting the endpoint takes its routes with it, but they are removed explicitly first
    # so a failure names the route rather than a generic conflict on the endpoint.
    for role in web app keycloak; do
      if az afd route show -g "$SHARED_RG" --profile-name "$AFD_PROFILE" \
           --endpoint-name "ep-infinevo-${ENV}" --route-name "route-${ENV}-${role}" >/dev/null 2>&1; then
        echo "  - route route-${ENV}-${role}"
        az afd route delete -g "$SHARED_RG" --profile-name "$AFD_PROFILE" \
          --endpoint-name "ep-infinevo-${ENV}" --route-name "route-${ENV}-${role}" --yes
      fi
    done
    echo "  - endpoint ep-infinevo-${ENV}"
    az afd endpoint delete -g "$SHARED_RG" --profile-name "$AFD_PROFILE" \
      --endpoint-name "ep-infinevo-${ENV}" --yes
  fi

  # Origins are children of the origin group and are removed with it.
  for role in web app keycloak; do
    if az afd origin-group show -g "$SHARED_RG" --profile-name "$AFD_PROFILE" \
         --origin-group-name "og-${ENV}-${role}" >/dev/null 2>&1; then
      echo "  - origin group og-${ENV}-${role}"
      az afd origin-group delete -g "$SHARED_RG" --profile-name "$AFD_PROFILE" \
        --origin-group-name "og-${ENV}-${role}" --yes
    fi
  done

  # The WAF policy and the rs-origin-tag rule set (deployed as `rsorigintag` - Front Door
  # rule set names permit no hyphen) are shared by every environment, so they go only when
  # the last endpoint has gone. Deleting them while uat still serves traffic would drop its
  # rate limit and its origin tagging without anyone asking for it.
  remaining=$(az afd endpoint list -g "$SHARED_RG" --profile-name "$AFD_PROFILE" \
    --query "length(@)" -o tsv 2>/dev/null || echo "0")
  if [[ "$remaining" == "0" ]]; then
    if az afd rule-set show -g "$SHARED_RG" --profile-name "$AFD_PROFILE" \
         --rule-set-name rsorigintag >/dev/null 2>&1; then
      echo "  - rule set rsorigintag (no endpoints left in the profile)"
      az afd rule-set delete -g "$SHARED_RG" --profile-name "$AFD_PROFILE" \
        --rule-set-name rsorigintag --yes
    fi
    if az network front-door waf-policy show -g "$SHARED_RG" -n "$WAF_POLICY" >/dev/null 2>&1; then
      echo "  - WAF policy ${WAF_POLICY} (no endpoints left in the profile)"
      az network front-door waf-policy delete -g "$SHARED_RG" -n "$WAF_POLICY"
    fi
  else
    echo "  - WAF policy ${WAF_POLICY} kept: ${remaining} endpoint(s) still use it"
  fi

  echo "PASS: Front Door resources for [${ENV}] removed."
else
  echo "Front Door profile ${AFD_PROFILE} does not exist. Nothing to remove at the edge."
fi

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
