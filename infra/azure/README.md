# Azure Infrastructure as Code (Bicep)

This directory defines the automated Infrastructure as Code (IaC) for the **Infinevo HCM & Payroll Platform** on Microsoft Azure, adhering to `05-azure-architecture.md` and capability `PLAT-12`.

---

## 1. Structure

```
infra/azure/
├── main.bicep                          # Root subscription orchestrator
├── deploy.sh                           # Idempotent deployment runner
├── teardown.sh                         # Safe ephemeral environment teardown
├── post-deploy-db.sh                   # Cloud database role & schema bootstrap
├── modules/
│   ├── registry.bicep                  # Container Registry (crinfinevo)
│   ├── keyvault.bicep                  # Key Vault with Azure RBAC (kv-infinevo-shared)
│   ├── loganalytics.bicep              # Log Analytics Workspace (law-infinevo-shared)
│   ├── managed-identities.bicep        # User-Assigned Managed Identities (app, worker, web, keycloak)
│   ├── acr-role-assignment.bicep       # AcrPull role assignment for identities on ACR
│   ├── containerapp-env.bicep          # Container Apps Environment (cae-infinevo-{env})
│   ├── containerapps.bicep             # 4 Container Apps (app, worker, web, keycloak)
│   ├── postgres.bicep                  # PostgreSQL 16 Flexible Server (psql-infinevo-{env})
│   ├── redis.bicep                     # Azure Cache for Redis (redis-infinevo-{env})
│   ├── storage.bicep                   # Storage Account: private containers & queues (stinfinevo{env})
│   └── frontdoor.bicep                 # Front Door Standard + WAF (afd-infinevo-shared)
├── parameters/
│   ├── dev.bicepparam                  # Dev environment (burstable SKUs, scale-to-zero)
│   ├── uat.bicepparam                  # UAT environment (acceptance testing topology)
│   └── prod.bicepparam                 # Production (HA, zone-redundant, ZRS storage)
└── README.md                           # This runbook
```

---

## 2. Resource Groups

1. **`rg-infinevo-shared` (Central India, Permanent)**:
   - `crinfinevo`: Azure Container Registry (admin disabled).
   - `kv-infinevo-shared`: Key Vault (Azure RBAC authorization mode).
   - `law-infinevo-shared`: Log Analytics workspace.
   - `afd-infinevo-shared`: Front Door Standard profile (`global`) — the only public entry
     point. One endpoint `ep-infinevo-{env}` per environment, three origin groups
     `og-{env}-{web,app,keycloak}`, routes `/*`, `/api/*`, `/auth/*`, and the rule set
     `rsorigintag` stamping `X-Infinevo-Origin` on each response.
   - `wafinfinevoshared`: Front Door WAF policy (`Standard_AzureFrontDoor`, Prevention) —
     a 100-requests-per-minute-per-IP rate limit and a canary rule blocking
     `?wafcanary=block`, associated to each endpoint by `sp-infinevo-{env}`.

2. **`rg-infinevo-{env}` (Central India, Per Environment: `dev`, `uat`, `prod`)**:
   - `cae-infinevo-{env}`: Container Apps Managed Environment.
   - `ca-infinevo-{env}-{app,worker,web,keycloak}`: 4 Container Apps.
   - `psql-infinevo-{env}`: PostgreSQL 16 Flexible Server (`infinevo` and `keycloak` databases).
   - `redis-infinevo-{env}`: Azure Cache for Redis.
   - `stinfinevo{env}`: Storage Account with private containers `documents`, `payslips`, `proofs`
     and queues `payrun`, `import`, `report`. Storage Queue replaced Azure Service Bus
     (founder decision 2026-09-19): Service Bus private endpoints are Premium-tier only.
   - `id-{app,worker,web,keycloak}-{env}`: User-Assigned Managed Identities with `AcrPull` on `crinfinevo`.

---

## 3. Prerequisites

1. Azure CLI (`az`) version >= 2.47.0 with Bicep CLI (`az bicep install`).
2. Target Azure Subscription:
   ```bash
   az login --tenant "$AZURE_TENANT_ID"
   az account set --subscription "$AZURE_SUBSCRIPTION_ID"
   ```

---

## 4. Usage

### 4.1 Cold Deployment
Provision an environment from zero:
```bash
bash infra/azure/deploy.sh --env dev
```
`deploy.sh` will:
- Auto-generate a secure password for `infinevo_admin` and store it in Key Vault (`psql-admin-pw`).
- Read every Container App's current image and its 100%-traffic revision, and pass both
  into `main.bicep`, so an infrastructure-only run leaves the running release untouched
  (W-54 finding F-1). It stops if any app has no revision holding the whole weight.
- Execute `main.bicep` deployment with parameters from `dev.bicepparam`.
- Import a smoke container image into `crinfinevo` (`az acr import`) and repoint
  `ca-infinevo-dev-app` to test private `AcrPull` — **skipped** when the app already runs
  an image from `crinfinevo`, because overwriting it would revert a release.

To deploy a release from this script rather than from the pipeline, add the tag:
```bash
bash infra/azure/deploy.sh --env dev --image-tag git-1a2b3c4
```
That sets all four apps, `caj-db-migration-dev` and `caj-flyway-dev` to the one tag. The
traffic weights still do not move; `deploy.yml`'s shift job is what moves them, after the
health gate.

### 4.2 Database Bootstrap
Once the cloud resources exist, run:
```bash
bash infra/azure/post-deploy-db.sh --env dev
```
This script:
- Generates dynamic passwords for `psql-app-pw`, `psql-migration-pw`, `psql-readonly-pw`, and `psql-keycloak-pw` in Key Vault.
- Exports `PGUSER=infinevo_admin`, `PGHOST`, and `PGPASSWORD`.
- Executes `infra/postgres/provision.sh` to create database roles, schemas (`core`, `hrms`, `payroll`, `reference`, `migration`), and grants.
- Validates that `app_user` can connect with its Key Vault password and is strictly refused DDL.

### 4.3 Ephemeral Teardown
To destroy an ephemeral environment:
```bash
bash infra/azure/teardown.sh --env dev
```
*(Guarded: strictly refuses execution when `--env prod` is passed).*

It removes the environment's Front Door endpoint, routes, origin groups and WAF
association from `rg-infinevo-shared` first, then deletes `rg-infinevo-{env}` — which
takes the VNet, private endpoints and private DNS zones with it. The shared WAF policy and
rule set are removed only when the last endpoint has gone, so tearing down `dev` leaves
`uat` protected.

### 4.4 Custom domains — a manual step at the registrar

DNS stays at the external registrar (founder decision 2026-09-19, `W-51` decision 1), so
no Azure DNS zone exists and `deploy.sh` cannot create or validate these records. The
platform runs entirely on the default `ep-infinevo-{env}-<hash>.z01.azurefd.net` endpoint,
which needs no DNS at all; binding a friendly name is optional and changes no Bicep.

To bind one — for example `dev.infinevo.cloud` — create two records at the registrar and
then add the custom domain in the Front Door profile:

| Record | Name | Value |
|---|---|---|
| `TXT` | `_dnsauth.dev` | the validation token shown by `az afd custom-domain show` |
| `CNAME` | `dev` | the endpoint hostname from `az afd endpoint show --query hostName -o tsv` |

---

## 5. Verification & CI Validation

Run compilation and static lint analysis locally:
```bash
az bicep build --file infra/azure/main.bicep
az bicep lint --file infra/azure/main.bicep
for f in infra/azure/modules/*.bicep; do az bicep build --file "$f" && az bicep lint --file "$f"; done
```
CI runs this check automatically on pull requests via `.github/workflows/infra.yml`.
