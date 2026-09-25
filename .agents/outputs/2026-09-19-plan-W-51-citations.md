# Citation Verification Report — W-51 — 2026-09-19

## Summary

Verified 30 citations across Bicep, shell, and Markdown files. **All 30 citations PASS.**

Additionally answered three factual questions about ACR role assignment, security restrictions, and idempotency.

---

## Citation Verification Table

| # | Citation | File exists | Range exists | Content (quote) | Verdict |
|---|----------|-------------|--------------|-----------------|---------|
| 1 | infra/azure/modules/keyvault.bicep:33 | ✓ | ✓ | `publicNetworkAccess: 'Enabled'` | OK |
| 2 | infra/azure/modules/keyvault.bicep:10-11 | ✓ | ✓ | `@description('Object id of the principal running the deployment. Granted Key Vault Secrets Officer so deploy.sh and post-deploy-db.sh can write secrets to an RBAC-authorised vault (review F-5). Empty skips the assignment.')`; `param deployerObjectId string = ''` | OK |
| 3 | infra/azure/modules/keyvault.bicep:39-46 | ✓ | ✓ | `resource deployerSecretsOfficer 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(deployerObjectId)) { name: guid(keyVault.id, deployerObjectId, secretsOfficerRoleId) scope: keyVault properties: { roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', secretsOfficerRoleId) principalId: deployerObjectId } }` | OK |
| 4 | infra/azure/modules/containerapps.bicep:14 | ✓ | ✓ | `param starterImage string = 'mcr.microsoft.com/k8se/quickstart:latest'` | OK |
| 5 | infra/azure/modules/containerapps.bicep:49-54 | ✓ | ✓ | `ingress: { external: true targetPort: 8080 transport: 'auto' allowInsecure: false }` | OK |
| 6 | infra/azure/modules/containerapps.bicep:75 | ✓ | ✓ | `path: '/'` | OK |
| 7 | infra/azure/modules/containerapps.bicep:148 | ✓ | ✓ | `ingress: {` | OK |
| 8 | infra/azure/modules/containerapps.bicep:206 | ✓ | ✓ | `ingress: {` | OK |
| 9 | infra/azure/modules/containerapp-env.bicep:25-39 | ✓ | ✓ | `resource containerAppEnv 'Microsoft.App/managedEnvironments@2024-03-01' = { name: environmentName location: location tags: tags properties: { appLogsConfiguration: { destination: 'log-analytics' logAnalyticsConfiguration: { customerId: logAnalyticsCustomerId sharedKey: logAnalytics.listKeys().primarySharedKey } } zoneRedundant: false } }` | OK |
| 10 | infra/azure/deploy.sh:52-63 | ✓ | ✓ | Lines resolving DEPLOYER_OID: `DEPLOYER_OID=$(az ad signed-in-user show --query id -o tsv 2>/dev/null \|\| true)` through `echo "Deploying principal object id: ${DEPLOYER_OID}"` | OK |
| 11 | infra/azure/deploy.sh:120 | ✓ | ✓ | `az keyvault secret set --vault-name "$VAULT_NAME" --name "psql-admin-pw" --value "$ADMIN_PW" >/dev/null` | OK |
| 12 | infra/azure/deploy.sh:123-160 | ✓ | ✓ | Lines 123–160 contain seeding ACR, smoke image check, waiting for app to reach Running status, and success message | OK |
| 13 | infra/azure/deploy.sh:143-160 | ✓ | ✓ | Lines 143–160 contain waiting loop: `echo "Waiting for ${APP_NAME} to reach Running status..."` through `exit 1` | OK |
| 14 | infra/azure/post-deploy-db.sh:58 | ✓ | ✓ | `export PGPASSWORD=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "psql-admin-pw" --query value -o tsv)` | OK |
| 15 | infra/azure/post-deploy-db.sh:65-93 | ✓ | ✓ | Firewall rule creation and revocation block, including FW_RULE_NAME, FW_RULE_CREATED, revoke_firewall_rule function, and trap | OK |
| 16 | infra/azure/post-deploy-db.sh:66-67 | ✓ | ✓ | Comment lines: `# W-51 brings private endpoints; until then the server carries only the Azure-services` and `# rule, so an operator running this script has no route to it at all (review F-2).` | OK |
| 17 | infra/azure/post-deploy-db.sh:70-93 | ✓ | ✓ | Firewall rule variables and revoke_firewall_rule function definition | OK |
| 18 | infra/azure/post-deploy-db.sh:106 | ✓ | ✓ | `PW=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "$SECRET_NAME" --query value -o tsv 2>/dev/null \|\| true)` | OK |
| 19 | infra/azure/post-deploy-db.sh:112 | ✓ | ✓ | `az keyvault secret set --vault-name "$VAULT_NAME" --name "$SECRET_NAME" --value "$NEW_PW" >/dev/null` | OK |
| 20 | infra/azure/main.bicep:1 | ✓ | ✓ | `targetScope = 'subscription'` | OK |
| 21 | infra/azure/main.bicep:80-91 | ✓ | ✓ | Resource definitions for sharedRg and envRg | OK |
| 22 | infra/azure/main.bicep:156 | ✓ | ✓ | `module containerAppEnv 'modules/containerapp-env.bicep' = {` | OK |
| 23 | legacy/docs/GAP_INVENTORY.md:42 | ✓ | ✓ | `\| **DEBT-004** \| Both backends \| Secrets hardcoded in `.properties` — Keycloak secret, Cloudinary keys, Brevo key, `fed.secret`, DB passwords \| Credentials in git history; assume compromised \| \| \|` | OK |
| 24 | docs/target-state/05-azure-architecture.md:28 | ✓ | ✓ | `\| `rg-infinevo-shared` \| Container registry, Key Vault, Log Analytics workspace, DNS zone, Front Door \| Permanent, one only \|` | OK |
| 25 | docs/target-state/05-azure-architecture.md:29 | ✓ | ✓ | `\| `rg-infinevo-dev` \| Full stack, minimal sizing, scales to zero \| Rebuildable at will \|` | OK |
| 26 | docs/target-state/05-azure-architecture.md:63-75 | ✓ | ✓ | ASCII diagram showing network flow: Internet → Front Door → Container Apps → data services | OK |
| 27 | docs/target-state/05-azure-architecture.md:74 | ✓ | ✓ | `\| Rule \|` (table header) | OK |
| 28 | docs/target-state/05-azure-architecture.md:155 | ✓ | ✓ | `Only Front Door is public. Nothing else has a public endpoint` | OK |
| 29 | docs/target-state/07-decisions.md:30 | ✓ | ✓ | `\| `D-18` \| 2026-09-11 \| **Azure India region** \| Indian payroll data under the Digital Personal Data Protection Act. Primary and backups both in-jurisdiction \| `05` \|` | OK |
| 30 | docs/target-state/09-build-order.md:279 | ✓ | ✓ | `**`W-56` Secrets** · Build: Key Vault, managed identity wiring, rotation process. Done when: no credential exists in any file in the repository. Watch: **rotate the current Keycloak administrative password before this, not as part of it.** That is a live exposure.` | OK |

---

## Factual Questions — Answers with Evidence

### A. Does `infra/azure/modules/acr-role-assignment.bicep` exist, and what role does it assign?

**Answer:** YES, file exists. Assigns **AcrPull** role.

**Evidence:**
- File: `infra/azure/modules/acr-role-assignment.bicep:11-12`
  - Line 11: `// Built-in AcrPull role definition ID: 7f951dda-4ed3-4680-a7ca-43fe172d538d`
  - Line 12: `var acrPullRoleDefinitionId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')`
  
- File: `infra/azure/modules/acr-role-assignment.bicep:14-22`
  - Line 14: `resource acrPullRoleAssignments 'Microsoft.Authorization/roleAssignments@2022-04-01' = [for (principalId, i) in principalIds: {`
  - Line 18: `roleDefinitionId: acrPullRoleDefinitionId`
  - Line 20: `principalType: 'ServicePrincipal'`

**Principals:** The array of principal IDs passed in via the `principalIds` parameter (line 5). Populated by `main.bicep:142-153`, which passes:
  - `managedIdentities.outputs.appIdentityPrincipalId`
  - `managedIdentities.outputs.workerIdentityPrincipalId`
  - `managedIdentities.outputs.webIdentityPrincipalId`
  - `managedIdentities.outputs.keycloakIdentityPrincipalId`

All are service principals (line 20).

---

### B. Does any file under `infra/azure/` set `ipSecurityRestrictions` or `vnetConfiguration`?

**Answer:** NO.

**Evidence:**
- Grep search for `ipSecurityRestrictions\|vnetConfiguration` across `infra/azure/` returned **no matches**.
- Checked files:
  - `keyvault.bicep` — no matches
  - `containerapps.bicep` — no matches
  - `containerapp-env.bicep` — no matches
  - `deploy.sh` — no matches
  - `post-deploy-db.sh` — no matches
  - `main.bicep` — no matches
  - All other `.bicep` and `.sh` files in the directory — no matches

---

### C. Is `infra/postgres/provision.sh` idempotent?

**Answer:** YES. Can be run multiple times against the same database without failing.

**Evidence:**

1. **Role creation guards** — `infra/postgres/01-roles.sql:10-36`
   - Lines 12–16: `IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN CREATE ROLE ... ELSE ALTER ROLE ... END IF;`
   - Lines 18–22: Same pattern for `migration_user`
   - Lines 24–28: Same pattern for `readonly_user`
   - Lines 30–34: Same pattern for `keycloak_user`
   - All roles are updated rather than recreated if they already exist.

2. **Password update always succeeds** — `infra/postgres/01-roles.sql:38-41`
   - Lines 38–41: `ALTER ROLE app_user WITH PASSWORD ...` (and same for other roles)
   - `ALTER ROLE` succeeds whether the role is new or existing.

3. **Schema creation with IF NOT EXISTS** — `infra/postgres/02-schemas.sql:8-12`
   - Lines 8–12: `CREATE SCHEMA IF NOT EXISTS core ... migration ...`
   - All five schemas use `IF NOT EXISTS`, so second run skips creation.

4. **Schema ownership idempotent fixup** — `infra/postgres/02-schemas.sql:14-21`
   - Lines 14–21: Comment and `ALTER SCHEMA core OWNER TO migration_user;` (and same for other schemas)
   - Comment on lines 14–16: *"AUTHORIZATION only applies when the schema is created. Re-provisioning over a volume where these schemas already exist (a W-02-era volume owns them as postgres) would silently leave the old owner. ALTER is the idempotent form."*
   - `ALTER SCHEMA OWNER TO` succeeds on subsequent runs, fixing ownership if needed.

**Conclusion:** All three SQL files use guard clauses (`IF NOT EXISTS`, `CREATE SCHEMA IF NOT EXISTS`) and idempotent commands (`ALTER` instead of `CREATE`). The script sets `-eo pipefail` (line 7 of provision.sh) and uses `ON_ERROR_STOP=1` in all three psql invocations (lines 25, 32, 35), meaning any SQL error will fail the script, but idempotent re-runs will not trigger errors.

---

## Files and Paths for Reference

| File | Path |
|------|------|
| Keyvault Bicep | `/infra/azure/modules/keyvault.bicep` |
| Container Apps Bicep | `/infra/azure/modules/containerapps.bicep` |
| Container App Environment Bicep | `/infra/azure/modules/containerapp-env.bicep` |
| ACR Role Assignment Bicep | `/infra/azure/modules/acr-role-assignment.bicep` |
| Main Bicep | `/infra/azure/main.bicep` |
| Deploy script | `/infra/azure/deploy.sh` |
| Post-deploy DB script | `/infra/azure/post-deploy-db.sh` |
| PostgreSQL Roles script | `/infra/postgres/01-roles.sql` |
| PostgreSQL Schemas script | `/infra/postgres/02-schemas.sql` |
| PostgreSQL Provision runner | `/infra/postgres/provision.sh` |
| Target-state Azure architecture | `/docs/target-state/05-azure-architecture.md` |
| Target-state decisions | `/docs/target-state/07-decisions.md` |
| Target-state build order | `/docs/target-state/09-build-order.md` |
| Legacy GAP inventory | `/legacy/docs/GAP_INVENTORY.md` |

