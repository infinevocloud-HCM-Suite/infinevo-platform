#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# oidc-setup.sh — Entra ID federation for GitHub Actions (W-54).
#
# Creates one App Registration, its service principal, one custom role definition scoped to
# the registry, the narrow role assignments listed below, and four federated credentials -
# one per subject a deploy.yml job actually presents - so .github/workflows/deploy.yml can
# log into Azure with OIDC and no stored password.
#
# RUN IT ONCE, BY HAND, BY AN OPERATOR WHO READ IT. It is a prerequisite that is nobody's
# code (W-54 section 2). It is NOT called by the pipeline and must never be.
#
# IT DOES NOTHING UNTIL YOU PASS --apply. Without that flag it prints the exact identities,
# roles and subjects it would create and exits 0. That is deliberate: the previous version
# of this script created federated credentials naming a GitHub organisation that does not
# exist and is claimable by anyone (review finding F-3), and it did so on the first run of
# a reader who only meant to look at it.
#
# Usage:
#   bash infra/azure/oidc-setup.sh                 # plan only, changes nothing
#   bash infra/azure/oidc-setup.sh --apply         # prompts, then creates
#   bash infra/azure/oidc-setup.sh --apply --yes   # no prompt, for a scripted operator
#
# Prerequisites:
#   - az CLI logged in (`az login`) as a principal holding Application.ReadWrite.All in
#     Entra ID and User Access Administrator (or Owner) on the subscription. Role
#     assignment needs the second; creating the app registration needs the first. The
#     custom role definition (F-3) needs the same User Access Administrator or Owner that
#     the assignments need - nothing further.
#   - Run from inside a checkout of this repository — the GitHub organisation is read from
#     the git remote, never hardcoded.
#
# Afterwards, set three repository VARIABLES (not secrets — none of the three is one):
#   AZURE_CLIENT_ID · AZURE_TENANT_ID · AZURE_SUBSCRIPTION_ID
# The script prints all three.
# ---------------------------------------------------------------------------
set -euo pipefail

# ── Roles granted. These four lines are the whole authorisation surface ──────
# Founder decision 3 (W-54): built-in roles, never subscription-wide or resource-group-wide
# write. The previous version granted a far broader role on both resource groups plus a Key
# Vault role its own comment called unnecessary (F-15). The pipeline creates revisions and
# shifts traffic, it pushes and builds images, and it reads one Front Door endpoint name.
# Nothing else. Revisit at W-57.
CONTAINERAPPS_ROLE="Container Apps Contributor"
REGISTRY_ROLE="AcrPush"
# Round-2 finding F-4: the release job runs `az afd endpoint show -g rg-infinevo-shared`
# to resolve the Front Door hostname, and nothing granted above is readable in that
# resource group except the registry, so the lookup 403s. Reader is the built-in that
# covers it. Nothing else the pipeline calls in rg-infinevo-shared needs more than read:
# the only other calls there are `az acr build` and `az acr manifest show` against
# crinfinevo, which the registry roles below cover.
SHARED_READ_ROLE="Reader"

# Round-2 finding F-3, and A DELIBERATE PARTIAL REVERSAL OF FOUNDER DECISION 3, which
# preferred built-ins. It is inside what the founder saw - decision 3 presented a custom
# role as option (b) - and it is here because no built-in fits:
#
#   REGISTRY_ROLE above                  = pull/read + push/write, and nothing more - read
#                                          back from the live definition. NO scheduleRun,
#                                          so `az acr build` 403s: it queues an ACR task.
#   Contributor                          = ruled out by decision 3.
#   Container Registry Tasks Contributor = a built-in that DOES grant scheduleRun, but it
#                                          also grants tasks write/delete and agent pool
#                                          write. A task is an arbitrary build directive,
#                                          so that role is effectively registry-wide code
#                                          execution. Rejected as wider than Contributor is
#                                          narrow.
#
# So: a custom role scoped to the one registry resource, carrying the five actions
# `az acr build` actually performs and nothing else. Each was verified against
# `az provider operation show --namespace Microsoft.ContainerRegistry`.
#
# The reviewer asked for exactly scheduleRun + registries/read. Three more are included
# because those two alone still fail: `az acr build` uploads the source context via
# listBuildSourceUploadUrl, then polls the run and streams its log. Granting two of the
# five would leave F-3 half open, with the 403 merely moved later in the command.
ACR_BUILD_ROLE="Infinevo ACR Build Queue"
ACR_BUILD_ACTIONS=(
  "Microsoft.ContainerRegistry/registries/read"                      # ARM read on the registry
  "Microsoft.ContainerRegistry/registries/scheduleRun/action"        # queue the quick build
  "Microsoft.ContainerRegistry/registries/listBuildSourceUploadUrl/action"  # upload the source context
  "Microsoft.ContainerRegistry/registries/runs/read"                 # poll the run to completion
  "Microsoft.ContainerRegistry/registries/runs/listLogSasUrl/action" # stream the build log
)

APP_DISPLAY_NAME="${APP_DISPLAY_NAME:-sp-github-infinevo}"
REGISTRY_NAME="${REGISTRY_NAME:-crinfinevo}"
SHARED_RG="${SHARED_RG:-rg-infinevo-shared}"
# Environment resource groups the deploy job targets. uat and prod do not exist yet
# (W-54 section 2); the script skips a group that is absent and says so, rather than
# failing or — worse — pretending it granted something.
ENV_RESOURCE_GROUPS=("rg-infinevo-dev" "rg-infinevo-uat" "rg-infinevo-prod")

# GitHub environments that deploy.yml gates on, INCLUDING dev (round-2 finding F-2). The
# earlier comment here claimed dev needs no credential because it has no `environment:`.
# That is wrong: deploy.yml's migrate job carries `environment: ${{ ... }}`, which resolves
# to `dev` on a push to main, so that job presents subject `...:environment:dev`. With no
# dev credential, every dev deploy 403s at `azure/login`. All three are federated.
GITHUB_ENVIRONMENTS=("dev" "uat" "prod")

APPLY=false
ASSUME_YES=false
for arg in "$@"; do
  case "$arg" in
    --apply) APPLY=true ;;
    --yes|-y) ASSUME_YES=true ;;
    --help|-h) sed -n '2,36p' "$0"; exit 0 ;;
    *) echo "ERROR: unknown argument '${arg}'. Try --help." >&2; exit 1 ;;
  esac
done

# ── 0. Who is this repository, really ────────────────────────────────────────
# F-3, the severe finding. The previous version hardcoded an organisation name that is not
# ours, so every federated subject was wrong: login would have returned 403, and the
# unowned name could be registered by a third party who would then hold a credential
# against our subscription. Derive it, and refuse to guess.
EXPECTED_GITHUB_ORG="infinevocloud-HCM-Suite"

REMOTE_URL="$(git remote get-url origin 2>/dev/null || true)"
if [[ -z "$REMOTE_URL" ]]; then
  echo "ERROR: cannot read 'git remote get-url origin'." >&2
  echo "       Run this from inside a checkout of the repository, or set GITHUB_ORG and" >&2
  echo "       GITHUB_REPO explicitly. The federated subject must name the real repository;" >&2
  echo "       a wrong one is a credential granted to whoever claims that name." >&2
  exit 1
fi

# Handles both https://github.com/<org>/<repo>(.git) and git@github.com:<org>/<repo>(.git)
REMOTE_PATH="${REMOTE_URL#*github.com}"
REMOTE_PATH="${REMOTE_PATH#:}"
REMOTE_PATH="${REMOTE_PATH#/}"
REMOTE_PATH="${REMOTE_PATH%.git}"
DERIVED_ORG="${REMOTE_PATH%%/*}"
DERIVED_REPO="${REMOTE_PATH##*/}"

GITHUB_ORG="${GITHUB_ORG:-$DERIVED_ORG}"
GITHUB_REPO="${GITHUB_REPO:-$DERIVED_REPO}"

if [[ -z "$GITHUB_ORG" || -z "$GITHUB_REPO" || "$GITHUB_ORG" == "$REMOTE_PATH" ]]; then
  echo "ERROR: could not parse an org/repo out of origin: ${REMOTE_URL}" >&2
  exit 1
fi

# A guard, not a default. If the remote is not the organisation this platform belongs to,
# stop — the operator has to say so out loud.
if [[ "$GITHUB_ORG" != "$EXPECTED_GITHUB_ORG" ]]; then
  echo "ERROR: origin resolves to organisation '${GITHUB_ORG}', expected '${EXPECTED_GITHUB_ORG}'." >&2
  echo "       Refusing to federate a subject for an unexpected organisation." >&2
  echo "       If this is intentional (a fork), re-run with GITHUB_ORG=... set explicitly." >&2
  exit 1
fi

# ── 0b. Azure context ────────────────────────────────────────────────────────
az account show >/dev/null 2>&1 || {
  echo "ERROR: not logged into Azure CLI. Run 'az login' first." >&2
  exit 1
}
TENANT_ID="$(az account show --query tenantId -o tsv)"
SUBSCRIPTION_ID="${AZURE_SUBSCRIPTION_ID:-$(az account show --query id -o tsv)}"
SUBSCRIPTION_NAME="$(az account show --query name -o tsv)"

REGISTRY_SCOPE="/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${SHARED_RG}/providers/Microsoft.ContainerRegistry/registries/${REGISTRY_NAME}"

# ── 1. The plan, printed before anything is touched ──────────────────────────
echo "================================================================="
echo " Azure OIDC federation for GitHub Actions — PLAN"
echo "================================================================="
echo " Tenant           : ${TENANT_ID}"
echo " Subscription     : ${SUBSCRIPTION_NAME} (${SUBSCRIPTION_ID})"
echo " GitHub repository: ${GITHUB_ORG}/${GITHUB_REPO}   (from ${REMOTE_URL})"
echo ""
echo " Would create or reuse:"
echo "   App registration    ${APP_DISPLAY_NAME}"
echo "   Service principal   for that app"
echo ""
echo "   Custom role      ${ACR_BUILD_ROLE}, scoped to ${REGISTRY_NAME} only"
for ACTION in "${ACR_BUILD_ACTIONS[@]}"; do
  echo "                       ${ACTION}"
done
echo ""
echo " Would grant:"
for RG in "${ENV_RESOURCE_GROUPS[@]}"; do
  echo "   ${CONTAINERAPPS_ROLE} on ${RG}"
done
echo "   ${SHARED_READ_ROLE} on ${SHARED_RG}"
echo "   ${REGISTRY_ROLE} on ${REGISTRY_NAME}"
echo "   ${ACR_BUILD_ROLE} on ${REGISTRY_NAME}"
echo ""
# Every subject below is one a job in .github/workflows/deploy.yml actually presents, and
# the list is complete (round-2 finding F-2). Jobs with no `environment:` - gate, build,
# release, health, shift, verify - present the branch subject; the migrate job carries
# `environment:` and presents the environment subject for whichever environment the gate
# resolved. Both triggers that reach this pipeline, push-to-main via workflow_run and
# workflow_dispatch from main, present the same branch subject, so one covers both (F-23).
echo " Would federate these subjects, one per subject a job presents:"
echo "   repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/main"
echo "       presented by: gate, build, release, health, shift, verify (no environment:)"
for ENV_NAME in "${GITHUB_ENVIRONMENTS[@]}"; do
  echo "   repo:${GITHUB_ORG}/${GITHUB_REPO}:environment:${ENV_NAME}"
  echo "       presented by: migrate, when the gate resolves environment=${ENV_NAME}"
done
echo "================================================================="

if [[ "$APPLY" != true ]]; then
  echo ""
  echo "DRY RUN — nothing was created. Re-run with --apply to make these changes."
  exit 0
fi

if [[ "$ASSUME_YES" != true ]]; then
  echo ""
  read -r -p "Create the above in Azure? Type 'yes' to continue: " REPLY
  if [[ "$REPLY" != "yes" ]]; then
    echo "Aborted. Nothing was created."
    exit 1
  fi
fi

# ── 2. App registration ──────────────────────────────────────────────────────
# Existence checks before every create, so a second run is a no-op rather than a duplicate.
echo ""
echo "[1/5] App registration '${APP_DISPLAY_NAME}'..."
APP_ID="$(az ad app list --display-name "$APP_DISPLAY_NAME" --query "[0].appId" -o tsv 2>/dev/null || true)"
if [[ -n "$APP_ID" && "$APP_ID" != "None" ]]; then
  echo "      exists: ${APP_ID}"
else
  APP_ID="$(az ad app create --display-name "$APP_DISPLAY_NAME" --query appId -o tsv)"
  echo "      created: ${APP_ID}"
fi
APP_OBJECT_ID="$(az ad app show --id "$APP_ID" --query id -o tsv)"

# ── 3. Service principal ─────────────────────────────────────────────────────
echo "[2/5] Service principal..."
SP_OID="$(az ad sp show --id "$APP_ID" --query id -o tsv 2>/dev/null || true)"
if [[ -n "$SP_OID" && "$SP_OID" != "None" ]]; then
  echo "      exists: ${SP_OID}"
else
  az ad sp create --id "$APP_ID" >/dev/null
  SP_OID="$(az ad sp show --id "$APP_ID" --query id -o tsv)"
  echo "      created: ${SP_OID}"
fi

# ── 3b. Custom role definition for `az acr build` (F-3) ──────────────────────
# assignableScopes is the registry resource itself, not the resource group and not the
# subscription: the role cannot be handed out anywhere else even by someone who can assign
# roles. Creating a role definition needs the same User Access Administrator or Owner the
# assignments below need, so nothing new is asked of the operator.
echo "[3/5] Custom role definition '${ACR_BUILD_ROLE}'..."
EXISTING_ROLE="$(az role definition list --name "$ACR_BUILD_ROLE" --scope "$REGISTRY_SCOPE" \
                   --query "[0].roleName" -o tsv 2>/dev/null || true)"
ACR_BUILD_ACTIONS_JSON="$(printf '"%s",' "${ACR_BUILD_ACTIONS[@]}")"
ACR_BUILD_ACTIONS_JSON="[${ACR_BUILD_ACTIONS_JSON%,}]"
ROLE_JSON="{
  \"Name\": \"${ACR_BUILD_ROLE}\",
  \"IsCustom\": true,
  \"Description\": \"Queue an ACR quick build (az acr build) on one registry. Created by infra/azure/oidc-setup.sh for W-54; no built-in role grants scheduleRun without also granting task write.\",
  \"Actions\": ${ACR_BUILD_ACTIONS_JSON},
  \"NotActions\": [],
  \"DataActions\": [],
  \"NotDataActions\": [],
  \"AssignableScopes\": [\"${REGISTRY_SCOPE}\"]
}"
if [[ -n "$EXISTING_ROLE" && "$EXISTING_ROLE" != "None" ]]; then
  # Updated, not skipped: a definition left over from an earlier run may carry a shorter
  # action list, and a silently stale role is how F-3 reached a reviewer in the first place.
  az role definition update --role-definition "$ROLE_JSON" >/dev/null
  echo "      updated: ${ACR_BUILD_ROLE}"
else
  az role definition create --role-definition "$ROLE_JSON" >/dev/null
  echo "      created: ${ACR_BUILD_ROLE}"
fi

# ── 4. Role assignments ──────────────────────────────────────────────────────
# F-16: the previous version ended every assignment with `|| echo "(already assigned or
# insufficient permissions)"`, so a run with no permission to assign roles exited 0 having
# granted nothing, and the first sign of it was a 403 mid-deploy. Here an already-present
# assignment is detected BEFORE the create, and any create that fails fails the script.
grant_role() {
  local role="$1" scope="$2" label="$3"
  local existing
  existing="$(az role assignment list --assignee "$SP_OID" --role "$role" --scope "$scope" \
                --query "[0].id" -o tsv 2>/dev/null || true)"
  if [[ -n "$existing" && "$existing" != "None" ]]; then
    echo "      already granted: ${role} on ${label}"
    return 0
  fi
  if ! az role assignment create \
        --assignee-object-id "$SP_OID" \
        --assignee-principal-type ServicePrincipal \
        --role "$role" \
        --scope "$scope" >/dev/null; then
    echo "ERROR: failed to grant '${role}' on ${label}." >&2
    echo "       The caller needs User Access Administrator or Owner on that scope." >&2
    echo "       Stopping — a partially granted identity fails later and further away." >&2
    return 1
  fi
  echo "      granted: ${role} on ${label}"
}

echo "[4/5] Role assignments..."
for RG in "${ENV_RESOURCE_GROUPS[@]}"; do
  if ! az group exists --name "$RG" | grep -q true; then
    echo "      skipped: ${RG} does not exist yet (uat and prod are unbuilt — W-54 section 2)"
    continue
  fi
  grant_role "$CONTAINERAPPS_ROLE" "/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}" "$RG"
done

# Read on the shared resource group (F-4). Read only: the pipeline looks the Front Door
# endpoint hostname up and never writes anything there.
grant_role "$SHARED_READ_ROLE" "/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${SHARED_RG}" "$SHARED_RG"

grant_role "$REGISTRY_ROLE" "$REGISTRY_SCOPE" "$REGISTRY_NAME"

# A freshly created role definition is not always visible to the assignment API for a few
# seconds. Wait for it rather than failing the whole run on a propagation delay.
for attempt in 1 2 3 4 5 6; do
  if [[ -n "$(az role definition list --name "$ACR_BUILD_ROLE" --scope "$REGISTRY_SCOPE" \
               --query "[0].roleName" -o tsv 2>/dev/null || true)" ]]; then
    break
  fi
  echo "      waiting for '${ACR_BUILD_ROLE}' to become assignable (attempt ${attempt})"
  sleep 10
done
grant_role "$ACR_BUILD_ROLE" "$REGISTRY_SCOPE" "$REGISTRY_NAME"

# ── 5. Federated credentials ─────────────────────────────────────────────────
# One per subject a job actually presents, and no others.
federate() {
  local name="$1" subject="$2"
  local existing
  existing="$(az ad app federated-credential list --id "$APP_OBJECT_ID" \
                --query "[?name=='${name}'].name" -o tsv 2>/dev/null || true)"
  if [[ -n "$existing" ]]; then
    echo "      exists: ${name}"
    return 0
  fi
  az ad app federated-credential create --id "$APP_OBJECT_ID" --parameters "{
    \"name\": \"${name}\",
    \"issuer\": \"https://token.actions.githubusercontent.com\",
    \"subject\": \"${subject}\",
    \"audiences\": [\"api://AzureADTokenExchange\"]
  }" >/dev/null
  echo "      created: ${name}  →  ${subject}"
}

echo "[5/5] Federated credentials..."
# The branch credential covers both triggers that reach dev: push to main via workflow_run,
# and workflow_dispatch from main — both present subject ref:refs/heads/main, so one
# credential serves both and a separate dispatch credential would be dead weight (F-23).
# The environment credentials that follow cover the migrate job, dev included (F-2).
federate "github-main" "repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/main"
for ENV_NAME in "${GITHUB_ENVIRONMENTS[@]}"; do
  federate "github-env-${ENV_NAME}" "repo:${GITHUB_ORG}/${GITHUB_REPO}:environment:${ENV_NAME}"
done

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "================================================================="
echo " DONE"
echo "================================================================="
echo " Set these three as repository VARIABLES:"
echo "   GitHub → Settings → Secrets and variables → Actions → Variables"
echo ""
echo "   AZURE_CLIENT_ID       = ${APP_ID}"
echo "   AZURE_TENANT_ID       = ${TENANT_ID}"
echo "   AZURE_SUBSCRIPTION_ID = ${SUBSCRIPTION_ID}"
echo ""
echo " None of the three is a secret; all three are public identifiers, and the workflow"
echo " must fail loudly if one is missing rather than skipping the login."
echo ""
echo " To undo everything this script created (spec section 8):"
echo "   az ad app delete --id ${APP_ID}"
echo "   az role definition delete --name \"${ACR_BUILD_ROLE}\" --scope ${REGISTRY_SCOPE}"
echo " Delete the role definition LAST - a definition with live assignments cannot be removed."
echo "================================================================="
