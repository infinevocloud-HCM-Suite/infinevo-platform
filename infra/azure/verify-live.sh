#!/usr/bin/env bash
set -euo pipefail

ENV="dev"
RG_ENV="rg-infinevo-${ENV}"
RG_SHARED="rg-infinevo-shared"

echo "================================================================="
echo " RUNNING LIVE AZURE INFRASTRUCTURE VERIFICATION SUITE [${ENV}]"
echo "================================================================="

# 1. Bicep build & lint
echo "--- Check 1: Bicep Build & Lint ---"
az bicep build --file infra/azure/main.bicep
az bicep lint --file infra/azure/main.bicep
echo "PASS 1: Bicep build and lint clean."

# 2. Public Access Closed on Data Services
echo "--- Check 2: Public Network Access Closed ---"
PG_PNA=$(az postgres flexible-server show -g "$RG_ENV" -n "psql-infinevo-${ENV}" --query network.publicNetworkAccess -o tsv)
if [ "$PG_PNA" != "Disabled" ]; then echo "FAIL: Postgres publicNetworkAccess is $PG_PNA"; exit 1; fi
echo "PASS: Postgres publicNetworkAccess is Disabled"

ST_PNA=$(az storage account show -g "$RG_ENV" -n "stinfinevo${ENV}" --query publicNetworkAccess -o tsv)
if [ "$ST_PNA" != "Disabled" ]; then echo "FAIL: Storage publicNetworkAccess is $ST_PNA"; exit 1; fi
echo "PASS: Storage publicNetworkAccess is Disabled"

KV_ACTION=$(az keyvault show --name kv-infinevo-shared --query properties.networkAcls.defaultAction -o tsv)
if [ "$KV_ACTION" != "Deny" ]; then echo "FAIL: Key Vault defaultAction is $KV_ACTION"; exit 1; fi
echo "PASS: Key Vault networkAcls.defaultAction is Deny"

KV_RULES=$(az keyvault show --name kv-infinevo-shared --query "length(properties.networkAcls.ipRules)" -o tsv)
if [ "$KV_RULES" != "0" ]; then echo "FAIL: Key Vault has $KV_RULES active ipRules"; exit 1; fi
echo "PASS: Key Vault ipRules count is 0 (cleanly revoked)"

# 3. Container Apps Environment VNet Injection
echo "--- Check 3: Container Apps Environment VNet Injection ---"
SUBNET=$(az containerapp env show -g "$RG_ENV" -n "cae-infinevo-${ENV}" --query "properties.vnetConfiguration.infrastructureSubnetId" -o tsv)
if [[ "$SUBNET" != *"/subnets/snet-cae" ]]; then echo "FAIL: Environment not injected into snet-cae ($SUBNET)"; exit 1; fi
echo "PASS: cae-infinevo-${ENV} is VNet-injected into snet-cae"

# 4. Container Apps Running Status
echo "--- Check 4: Container Apps Running Status ---"
for app in app worker web keycloak; do
  STATUS=$(az containerapp show -g "$RG_ENV" -n "ca-infinevo-${ENV}-${app}" --query "properties.runningStatus" -o tsv)
  if [ "$STATUS" != "Running" ]; then echo "FAIL: ca-infinevo-${ENV}-${app} status is $STATUS"; exit 1; fi
  echo "PASS: ca-infinevo-${ENV}-${app} runningStatus = Running"
done

# 5. Managed Identity Role Assignments
echo "--- Check 5: Managed Identity Role Assignments ---"
declare -A EXPECTED_ROLES=(
  ["id-app-${ENV}"]="Key Vault Secrets User|Storage Blob Data Contributor|Storage Queue Data Message Sender|AcrPull"
  ["id-worker-${ENV}"]="Key Vault Secrets User|Storage Blob Data Contributor|Storage Queue Data Message Processor|Storage Queue Data Message Sender|AcrPull"
  ["id-keycloak-${ENV}"]="Key Vault Secrets User|AcrPull"
  ["id-web-${ENV}"]="AcrPull"
  ["id-migration-${ENV}"]="Key Vault Secrets User|AcrPull"
)
for uami in "${!EXPECTED_ROLES[@]}"; do
  sp_id=$(az identity show -g "$RG_ENV" -n "$uami" --query principalId -o tsv)
  roles=$(az role assignment list --assignee "$sp_id" --all --query "[].roleDefinitionName" -o tsv)
  IFS='|' read -ra want <<< "${EXPECTED_ROLES[$uami]}"
  for r in "${want[@]}"; do
    echo "$roles" | grep -Fqx "$r" || { echo "FAIL: $uami missing role '$r'"; exit 1; }
  done
  echo "PASS: $uami has all expected roles: ${EXPECTED_ROLES[$uami]}"
done

# 6. Data Residency in Central India
echo "--- Check 6: Data Residency (D-18) ---"
ENV_ROWS=$(az resource list -g "$RG_ENV" --query "[?location!='centralindia'].[name,location]" -o tsv)
BAD_RES=$(echo "$ENV_ROWS" | awk 'NF && tolower($2) != "global" { print $1 }')
if [ -n "$BAD_RES" ]; then echo "FAIL: Non-global resources outside centralindia in $RG_ENV: $BAD_RES"; exit 1; fi

SHARED_ROWS=$(az resource list -g "$RG_SHARED" --query "[?location!='centralindia'].[name,location]" -o tsv)
BAD_SHARED=$(echo "$SHARED_ROWS" | awk 'NF && tolower($2) != "global" { print $1 }')
if [ -n "$BAD_SHARED" ]; then echo "FAIL: Non-global resources outside centralindia in $RG_SHARED: $BAD_SHARED"; exit 1; fi
echo "PASS: All regional resources strictly reside in Central India"

# 7. Direct Origin Access Blocked (403)
echo "--- Check 7: Direct Origin Access Refusal (403) ---"
for role in web app keycloak; do
  fqdn=$(az containerapp show -g "$RG_ENV" -n "ca-infinevo-${ENV}-${role}" --query "properties.configuration.ingress.fqdn" -o tsv)
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "https://${fqdn}/" || echo "000")
  if [ "$code" != "403" ]; then echo "FAIL: Direct access to $role returned $code, expected 403"; exit 1; fi
  echo "PASS: Direct origin hit on ca-infinevo-${ENV}-${role} ($fqdn) blocked with 403"
done

# 8. Front Door Routing & WAF Canary
echo "--- Check 8: Front Door Routing & WAF Canary ---"
AFD_HOST=$(az afd endpoint show -g "$RG_SHARED" --profile-name afd-infinevo-shared --endpoint-name "ep-infinevo-${ENV}" --query hostName -o tsv)
echo "Front Door Endpoint Host: $AFD_HOST"

WEB_CODE=$(curl -s -o /dev/null -w "%{http_code}" "https://${AFD_HOST}/")
if [ "$WEB_CODE" != "200" ]; then echo "FAIL: Front Door / returned $WEB_CODE, expected 200"; exit 1; fi
echo "PASS: Front Door / returns HTTP 200 OK"

CANARY_CODE=$(curl -s -o /dev/null -w "%{http_code}" "https://${AFD_HOST}/api/?wafcanary=block")
if [ "$CANARY_CODE" != "403" ]; then echo "FAIL: Front Door WAF canary returned $CANARY_CODE, expected 403"; exit 1; fi
echo "PASS: Front Door WAF canary correctly blocked with HTTP 403"

# 9. Container Database Environment Variables
echo "--- Check 9: Container Database Environment Configuration ---"
EXPECTED_DB_URL="jdbc:postgresql://psql-infinevo-${ENV}.postgres.database.azure.com:5432/infinevo?sslmode=require"
APP_DB_URL=$(az containerapp show -g "$RG_ENV" -n "ca-infinevo-${ENV}-app" --query "properties.template.containers[0].env[?name=='DB_URL'].value | [0]" -o tsv)
APP_DB_USER=$(az containerapp show -g "$RG_ENV" -n "ca-infinevo-${ENV}-app" --query "properties.template.containers[0].env[?name=='DB_USERNAME'].value | [0]" -o tsv)
if [ "$APP_DB_URL" != "$EXPECTED_DB_URL" ]; then echo "FAIL: ca-infinevo-${ENV}-app DB_URL is '$APP_DB_URL', expected '$EXPECTED_DB_URL'"; exit 1; fi
if [ "$APP_DB_USER" != "app_user" ]; then echo "FAIL: ca-infinevo-${ENV}-app DB_USERNAME is '$APP_DB_USER', expected 'app_user'"; exit 1; fi
echo "PASS: ca-infinevo-${ENV}-app has DB_URL and DB_USERNAME configured correctly"

WORKER_DB_URL=$(az containerapp show -g "$RG_ENV" -n "ca-infinevo-${ENV}-worker" --query "properties.template.containers[0].env[?name=='DB_URL'].value | [0]" -o tsv)
WORKER_DB_USER=$(az containerapp show -g "$RG_ENV" -n "ca-infinevo-${ENV}-worker" --query "properties.template.containers[0].env[?name=='DB_USERNAME'].value | [0]" -o tsv)
if [ "$WORKER_DB_URL" != "$EXPECTED_DB_URL" ]; then echo "FAIL: ca-infinevo-${ENV}-worker DB_URL is '$WORKER_DB_URL', expected '$EXPECTED_DB_URL'"; exit 1; fi
if [ "$WORKER_DB_USER" != "worker_user" ]; then echo "FAIL: ca-infinevo-${ENV}-worker DB_USERNAME is '$WORKER_DB_USER', expected 'worker_user'"; exit 1; fi
echo "PASS: ca-infinevo-${ENV}-worker has DB_URL and DB_USERNAME configured correctly"

echo "================================================================="
echo " ALL LIVE AZURE INFRASTRUCTURE TESTS PASSED SUCCESSFULLY!"
echo "================================================================="
