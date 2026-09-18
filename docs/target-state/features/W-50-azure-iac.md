# W-50 — Azure Infrastructure as Code

| Field | Value |
|---|---|
| **Work item** | `W-50` · issue [#70](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/70) |
| **Kind** | Infra |
| **Stream / track** | Stream G — Infrastructure · Track I |
| **Wave** | 2 — Data platform |
| **Size / skill** | L · INFRA |
| **Owner** | KarmaveerM |
| **Blocked by** | `W-49` ([#69](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/69)) — production Dockerfiles and images |
| **Blocks** | `W-51` networking & identity · `W-52` queue & worker · `W-53` caching · `W-54` deployment pipeline · `W-59` container scanning · `W-60` observability · `W-62` backup & DR |
| **Capabilities** | `PLAT-12` — infrastructure as code; Azure defined in the repository |
| **Decisions** | `D-10` Container Apps not Kubernetes · `D-11` API Gateway deferred · `D-18` India region (`centralindia`) · `D-19` 10 × 100 scale · `D-44` RabbitMQ local / Service Bus Azure · `D-02` worker on same image · `D-21` one Keycloak realm · `D-45` migration schema · `D-46` no ddl-auto |
| **Gaps addressed** | `DEBT-004` secrets in properties — full resolution: Key Vault replaces all connection strings<br>`DEBT-011` Cloudinary document storage — full resolution: Azure Blob Storage with private containers |
| **Status** | **Draft — not approved** |
| **Approved by** | |
| **Approved on** | |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

The platform is deployed manually from a cloud portal today (`infra/README.md:16-20`). There is no infrastructure as code, no automated script to recreate an environment, and no audit trail of running cloud resources. Specifically:

- `infra/azure/` contains only `.gitkeep` — explicitly reserved for `W-50` (`W-01-repository-skeleton.md:90`).
- Secrets and connection strings live in committed configuration files (`DEBT-004`, deferred from `W-05`). Any leaked repository clone compromises production databases.
- Documents sit in external third-party storage (Cloudinary) rather than compliance-governed Azure storage in India (`DEBT-011`, `05-azure-architecture.md:46,143`).
- No Azure Container Registry (ACR) exists; `W-49` produces production images but has no private registry to push them to (`W-49-containerisation.md:73`).
- Lack of reproducible environments prevents establishing environment parity between `dev`, `uat`, and `prod` (`05-azure-architecture.md` §2).

**Baseline — measured on `main` before this ticket opens:**

| Command | Exit | Output |
|---|---|---|
| `az group list --query "[?contains(name,'infinevo')]" -o table` | 0 | Empty — no Azure resource groups exist |
| `az acr list -o table` | 0 | Empty — no container registry exists |
| `ls infra/azure/` | 0 | `.gitkeep` only |

> Verifier re-runs this table before merge to confirm the baseline recorded here is accurate.

---

## 1b. Authentication & Environment Prerequisites (Resolving F-5)

Azure deployments require explicit subscription and identity configuration:

- **Target Azure Subscription & Tenant**:
  - `AZURE_SUBSCRIPTION_ID`: Target Azure Subscription ID.
  - `AZURE_TENANT_ID`: Azure Active Directory (Entra ID) Tenant ID.
  - Location: `centralindia` (`D-18`).
- **Local Developer & Verifier Authentication**:
  - Requires Azure CLI (`az`).
  - Login via `az login --tenant "$AZURE_TENANT_ID"`.
  - Set active subscription: `az account set --subscription "$AZURE_SUBSCRIPTION_ID"`.
- **Automated CI Authentication (`.github/workflows/infra.yml`)**:
  - Uses OpenID Connect (OIDC) federated credentials (`azure/login@v2`).
  - Configured repository secrets: `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`.
  - Grants `Contributor` and `User Access Administrator` (or `Role Based Access Control Administrator`) over the target subscription to manage resource groups and role assignments (`AcrPull`).

---

## 2. Scope

**In scope**

- `infra/azure/` — Modular Bicep infrastructure templates for the resources specified in `05-azure-architecture.md` §3, except those explicitly listed as Out of Scope below (networking/VNet, Front Door, App Insights, and automated backups). Specifically builds: Resource Groups, Container Registry, Key Vault, Log Analytics Workspace, Container Apps Managed Environment, 4 Container Apps, Postgres Flexible Server, Redis Cache, Service Bus Namespace, and Blob Storage.
- Three identical-topology environments: `dev`, `uat`, `prod` (differing only in SKU sizing and redundancy — `05` §2).
- One shared resource group (`rg-infinevo-shared`) containing:
  - Azure Container Registry (`crinfinevo`) with admin user disabled.
  - Azure Key Vault (`kv-infinevo-shared`) in Azure RBAC authorization mode.
  - Log Analytics Workspace (`law-infinevo-shared`).
- Per-environment resource groups (`rg-infinevo-{dev,uat,prod}`) containing:
  - Container Apps Managed Environment (`cae-infinevo-{env}`).
  - Container Apps definitions (`app`, `worker`, `web`, `keycloak`) deployed with starter images and reaching active running state.
  - PostgreSQL 16 Flexible Server (`psql-infinevo-{env}`) hosting `infinevo` and `keycloak` databases with admin user `infinevo_admin`.
  - Azure Cache for Redis (`redis-infinevo-{env}`).
  - Azure Service Bus Namespace (`sb-infinevo-{env}`) with queues: `payrun`, `import`, `report`.
  - Azure Blob Storage Account (`stinfinevo{env}`) with private containers: `documents`, `payslips`, `proofs` (`allowBlobPublicAccess: false`).
  - User-Assigned Managed Identities (`id-{app,worker,web,keycloak}-{env}`) with `AcrPull` role assigned on `crinfinevo` so images can be pulled.
- Idempotent orchestration scripts:
  - `infra/azure/deploy.sh` — provisions a named environment (`--env dev|uat|prod`), auto-generating and storing secure Postgres admin password and role credentials in Key Vault if absent.
  - `infra/azure/teardown.sh` — destroys an ephemeral environment (`rg-infinevo-{env}`) while guarding `prod`.
  - `infra/azure/post-deploy-db.sh` — exports `PGUSER=infinevo_admin` and `PGPASSWORD`, generates/stores all four role passwords in Key Vault (`psql-app-pw`, `psql-migration-pw`, `psql-readonly-pw`, `psql-keycloak-pw`), and passes them as `APP_PW`, `MIGRATION_PW`, `READONLY_PW`, `KEYCLOAK_PW` to `infra/postgres/provision.sh`.
- CI validation workflow (`.github/workflows/infra.yml`) executing `az bicep build` and `az deployment sub what-if` on pull requests.

**Out of scope**

| Not here | Belongs to |
|---|---|
| Full VNet integration, private endpoints, Front Door + WAF, DNS routing | `W-51` |
| Key Vault data plane role assignments (`Key Vault Secrets User`) for containers | `W-51` (W-50 provisions Key Vault and UAMIs; W-51 connects them) |
| Service Bus queue message dispatching, listener wiring, and ShedLock clustered locking | `W-52` |
| Redis cache client abstraction and application data caching | `W-53` |
| Multi-stage image promotion and zero-downtime revision switching | `W-54` |
| Automated container vulnerability scanning against ACR | `W-59` |
| Application Insights telemetry dashboards and alert rules | `W-60`, `W-61` |
| Backup policies and tested point-in-time disaster recovery | `W-62` |
| Keycloak production realm export and custom theme | `W-10` |
| Any application source code changes | Product feature tickets |

---

## 3. What gets built

### 3a. Resource Topology (Per Environment)

```
rg-infinevo-shared (Permanent, Central India)
├── Container Registry  (crinfinevo)
├── Key Vault           (kv-infinevo-shared)
└── Log Analytics       (law-infinevo-shared)

rg-infinevo-{dev|uat|prod} (Central India)
├── Container Apps Managed Environment  (cae-infinevo-{env})
├── Container Apps                      (app, worker, web, keycloak)
│   └── starter image: mcr.microsoft.com/k8se/quickstart:latest
├── PostgreSQL 16 Flexible Server       (psql-infinevo-{env})
│   ├── administratorLogin: infinevo_admin (Bicep parameter)
│   ├── database: infinevo
│   └── database: keycloak
├── Azure Cache for Redis               (redis-infinevo-{env})
├── Service Bus Namespace               (sb-infinevo-{env})
│   ├── queue: payrun
│   ├── queue: import
│   └── queue: report
├── Storage Account                     (stinfinevo{env}, public access disabled)
│   ├── container: documents
│   ├── container: payslips
│   └── container: proofs
└── Managed Identities                  (id-{app,worker,web,keycloak}-{env})
    └── Role Assignment: AcrPull on crinfinevo
```

### 3b. SKU Sizing Matrix (`D-19` Scale: 10 Tenants × 100 Employees)

| Resource | Dev | UAT | Prod |
|---|---|---|---|
| **Container Registry** | Basic | Basic | Standard |
| **Key Vault** | Standard (Shared) | Standard (Shared) | Standard (Shared) |
| **PostgreSQL Flexible** | `Standard_B1ms` (Burstable) | `Standard_B1ms` (Burstable) | `Standard_D2ds_v5` (General Purpose, Zone-Redundant HA) |
| **PostgreSQL Admin Login** | `infinevo_admin` (parameter) | `infinevo_admin` (parameter) | `infinevo_admin` (parameter) |
| **PostgreSQL Storage** | 32 GiB, autogrow | 32 GiB, autogrow | 128 GiB, autogrow |
| **Redis Cache** | Basic C0 (250 MB) | Basic C0 (250 MB) | Standard C1 (1 GB, Replicated) |
| **Service Bus** | Standard | Standard | Standard |
| **Blob Storage** | Standard LRS | Standard LRS | Standard ZRS |
| **Container Apps** | 0.25-0.5 vCPU, min 0 / max 3 | 0.25-0.5 vCPU, min 0 / max 3 | 0.5-1.0 vCPU, min 2 / max 10 |

> Note: Azure Database for PostgreSQL Flexible Server does not create a default `postgres` superuser. The admin username is declared as a Bicep parameter (`administratorLogin: 'infinevo_admin'`). In `post-deploy-db.sh`, `PGUSER=infinevo_admin` and `PGPASSWORD` (fetched from Key Vault `psql-admin-pw`) are exported so the bootstrap script connects successfully.

### 3c. File Inventory

```
infra/azure/
├── main.bicep                          # Root subscription/resource group orchestrator
├── deploy.sh                           # End-to-end deployment script (idempotent)
├── teardown.sh                         # Ephemeral teardown script with prod guard
├── post-deploy-db.sh                   # Invokes infra/postgres/provision.sh against Flexible Server
├── modules/
│   ├── registry.bicep                  # Azure Container Registry
│   ├── keyvault.bicep                  # Key Vault with RBAC
│   ├── loganalytics.bicep              # Shared Log Analytics Workspace
│   ├── containerapp-env.bicep          # Container Apps Managed Environment
│   ├── containerapps.bicep             # Container Apps definitions (app, worker, web, keycloak)
│   ├── postgres.bicep                  # PostgreSQL Flexible Server & databases
│   ├── redis.bicep                     # Azure Cache for Redis
│   ├── servicebus.bicep                # Service Bus Namespace & queues
│   ├── storage.bicep                   # Storage Account (allowBlobPublicAccess: false) & containers
│   └── managed-identities.bicep        # User-assigned managed identities per container role & AcrPull
├── parameters/
│   ├── dev.bicepparam                  # Dev parameters (minimal SKUs, scale-to-zero)
│   ├── uat.bicepparam                  # UAT parameters (prod topology, scaled-down)
│   └── prod.bicepparam                 # Prod parameters (HA, ZRS, redundant instances)
└── README.md                           # Deployment runbook and Bicep conventions
.github/workflows/
└── infra.yml                           # CI validation (bicep build & what-if)
```

| File | Change |
|---|---|
| `infra/azure/main.bicep` | **New.** Root orchestrator accepting `environment`, `location`, and SKU overrides. Deploys shared RG and environment RG. |
| `infra/azure/modules/registry.bicep` | **New.** Container Registry with admin user disabled; grants `AcrPull` to managed identities. |
| `infra/azure/modules/keyvault.bicep` | **New.** Key Vault with Azure RBAC authorization, soft-delete, and purge protection for prod. |
| `infra/azure/modules/loganalytics.bicep` | **New.** Log Analytics workspace for platform container and diagnostic logging. |
| `infra/azure/modules/containerapp-env.bicep` | **New.** Container Apps Environment connected to the Log Analytics workspace. |
| `infra/azure/modules/containerapps.bicep` | **New.** Deploys `app`, `worker`, `web`, and `keycloak` Container Apps with starter images, ports, and probes. |
| `infra/azure/modules/postgres.bicep` | **New.** Flexible Server 16, creates `infinevo` and `keycloak` databases; HA enabled on prod only. Declares `param postgresAdminUsername string = 'infinevo_admin'` and `@secure() param postgresAdminPassword string`; both sourced from Key Vault at deploy time. |
| `infra/azure/modules/redis.bicep` | **New.** Azure Cache for Redis instance (Basic C0 in dev/uat, Standard C1 in prod). |
| `infra/azure/modules/servicebus.bicep` | **New.** Service Bus Standard namespace with queues: `payrun`, `import`, `report`. |
| `infra/azure/modules/storage.bicep` | **New.** Storage account with `allowBlobPublicAccess: false` and blob containers: `documents`, `payslips`, `proofs`. |
| `infra/azure/modules/managed-identities.bicep` | **New.** Creates User-Assigned Managed Identities (`app`, `worker`, `web`, `keycloak`) with `AcrPull` bindings. |
| `infra/azure/parameters/*.bicepparam` | **New.** Parameter files for `dev`, `uat`, and `prod`. |
| `infra/azure/deploy.sh` | **New.** Generates secure Postgres admin password if absent, seeds Key Vault, and executes deployment. |
| `infra/azure/teardown.sh` | **New.** Deletes environment resource group; strictly refuses `prod`. |
| `infra/azure/post-deploy-db.sh` | **New.** Fetches `postgresAdminUsername` (`infinevo_admin`) and `psql-admin-pw` from Key Vault; exports `PGUSER`, `PGHOST`, `PGPASSWORD`. Generates and stores all four role passwords (`psql-app-pw`, `psql-migration-pw`, `psql-readonly-pw`, `psql-keycloak-pw`) in Key Vault if absent. Calls `infra/postgres/provision.sh` with `APP_PW`, `MIGRATION_PW`, `READONLY_PW`, `KEYCLOAK_PW`. No password falls back to a `local_*_pw` literal. |
| `.github/workflows/infra.yml` | **New.** GitHub Actions workflow running `az bicep build` and `what-if` validation on PRs. |

**Untouched by this ticket:**
All code in `code/backend/`, `code/frontend/`, `infra/docker/`, `infra/postgres/*.sql`, and `.github/workflows/ci.yml`.

---

## 3d. Database changes (TEMPLATE-INFRA.md compliance)

Although this is an infrastructure ticket, it creates the cloud database foundation for all subsequent work items:

1. **Server Provisioning**: Azure Database for PostgreSQL 16 Flexible Server (`psql-infinevo-{env}`).
   - Bicep parameter in `modules/postgres.bicep`: `param postgresAdminUsername string = 'infinevo_admin'` and `@secure() param postgresAdminPassword string`.
   - `post-deploy-db.sh` exports `PGUSER="$postgresAdminUsername"` (value: `infinevo_admin`), `PGHOST` (resolved via `az postgres flexible-server show ... --query fullyQualifiedDomainName`), and `PGPASSWORD` (from Key Vault secret `psql-admin-pw`).
   - All verification commands use `-U infinevo_admin` consistently — no assumption of an Azure-created `postgres` superuser.
2. **Databases Created**:
   - `infinevo` (application database for Core, HRMS, Payroll, and Reference).
   - `keycloak` (isolated database for Keycloak identity server).
3. **Database Roles Created via `infra/postgres/provision.sh`**:
   - `migration_user`: Owner of application schemas; executes Flyway DDL (`W-06`). Password generated dynamically (`psql-migration-pw` in Key Vault).
   - `app_user`: Non-owner runtime role used by backend `app` and `worker` (`02-data-model.md §9`). Password generated dynamically (`psql-app-pw` in Key Vault).
   - `readonly_user`: Read-only reporting and audit role. Password generated dynamically (`psql-readonly-pw` in Key Vault).
   - `keycloak_user`: Dedicated non-superuser owner of the `keycloak` database. Password generated dynamically (`psql-keycloak-pw` in Key Vault).
   - `post-deploy-db.sh` exports `PGUSER=infinevo_admin` and `PGPASSWORD`, fetches all four role passwords from Key Vault, and passes them explicitly as `APP_PW`, `MIGRATION_PW`, `READONLY_PW`, and `KEYCLOAK_PW` to `infra/postgres/provision.sh`. None falls back to local repo defaults (`local_*_pw`).
4. **Schemas Created via `infra/postgres/provision.sh`**:
   - `core`: Core capabilities (owned by `migration_user`).
   - `hrms`: HRMS module (owned by `migration_user`).
   - `payroll`: Payroll module (owned by `migration_user`).
   - `reference`: Shared reference data with no tenant column (owned by `migration_user`).
   - `migration`: Dedicated schema for Flyway schema history table (owned by `migration_user`, `D-45`).
5. **Enforcement**: `ddl-auto` is permanently disabled (`D-46`); all subsequent migrations use numbered Flyway scripts (`D-09`).

---

## 4. Proving it

The core principle: **an environment you cannot recreate is not infrastructure as code** (`09-build-order.md:265`).

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Deploy dev, tear down, redeploy twice | All three runs exit 0. Third deployment matches the first. Link output in PR |
| 2 | Delete `parameters/dev.bicepparam` and run `deploy.sh` | Script exits non-zero; no partial resources deployed |
| 3 | Remove a required Key Vault secret after deployment; run `post-deploy-db.sh` | Script exits non-zero (`ERROR: secret not found`); no database scripts execute |
| 4 | Set an invalid SKU string in `.bicepparam` | `az bicep build` fails in CI; deployment is blocked |
| 5 | Introduce invalid Bicep syntax in a module | `infra.yml` CI workflow fails; PR cannot merge |
| 6 | Run `teardown.sh --env prod` | Script aborts immediately with `ERROR: prod teardown is forbidden`; prod resources untouched |

Prove tests 1–6 on a throwaway branch. Link evidence in the pull request.

---

## 5. Verification

Exact commands the verifier runs on a clean checkout with authenticated Azure CLI:

```bash
# ── 0. Prerequisites ─────────────────────────────────────────────────────────
[ -n "$AZURE_SUBSCRIPTION_ID" ] || { echo "FAIL: AZURE_SUBSCRIPTION_ID must be set"; exit 1; }
az account set --subscription "$AZURE_SUBSCRIPTION_ID"

# ── 1a. Bicep compilation (F-3) ──────────────────────────────────────────────
# Compiles all Bicep files to ARM JSON; fails on syntax errors.
az bicep build --file infra/azure/main.bicep
for f in infra/azure/modules/*.bicep; do az bicep build --file "$f"; done

# ── 1b. Bicep lint (F-3) ──────────────────────────────────────────────────────
# Static analysis for anti-patterns and rule violations (az CLI >= 2.47).
az bicep lint --file infra/azure/main.bicep
for f in infra/azure/modules/*.bicep; do az bicep lint --file "$f"; done

# ── 2. Dry-run what-if on dev parameters ─────────────────────────────────────
az deployment sub what-if \
  --location centralindia \
  --template-file infra/azure/main.bicep \
  --parameters infra/azure/parameters/dev.bicepparam

# ── 3. Cold deployment from zero ─────────────────────────────────────────────
az group delete --name rg-infinevo-dev --yes --no-wait 2>/dev/null || true
az group wait --deleted -n rg-infinevo-dev 2>/dev/null || true
bash infra/azure/deploy.sh --env dev

# ── 4. Verify each resource exists and provisioningState == Succeeded (F-1) ──
# 4a. Shared resources in rg-infinevo-shared
for res in \
  "Microsoft.ContainerRegistry/registries crinfinevo" \
  "Microsoft.KeyVault/vaults kv-infinevo-shared" \
  "Microsoft.OperationalInsights/workspaces law-infinevo-shared"; do
  read -r type name <<< "$res"
  state=$(az resource show -g rg-infinevo-shared --resource-type "$type" -n "$name" --query "properties.provisioningState" -o tsv 2>/dev/null) || {
    echo "FAIL: Shared resource $name ($type) not found in rg-infinevo-shared"; exit 1;
  }
  [ "$state" = "Succeeded" ] || { echo "FAIL: $name ($type) provisioningState is '$state' (expected Succeeded)"; exit 1; }
  echo "PASS: $name ($type) = Succeeded"
done

# 4b. Environment resources in rg-infinevo-dev
for res in \
  "Microsoft.DBforPostgreSQL/flexibleServers psql-infinevo-dev" \
  "Microsoft.Cache/redis redis-infinevo-dev" \
  "Microsoft.ServiceBus/namespaces sb-infinevo-dev" \
  "Microsoft.Storage/storageAccounts stinfinevodev" \
  "Microsoft.App/managedEnvironments cae-infinevo-dev" \
  "Microsoft.App/containerApps ca-infinevo-dev-app" \
  "Microsoft.App/containerApps ca-infinevo-dev-worker" \
  "Microsoft.App/containerApps ca-infinevo-dev-web" \
  "Microsoft.App/containerApps ca-infinevo-dev-keycloak"; do
  read -r type name <<< "$res"
  state=$(az resource show -g rg-infinevo-dev --resource-type "$type" -n "$name" --query "properties.provisioningState" -o tsv 2>/dev/null) || {
    echo "FAIL: Resource $name ($type) not found in rg-infinevo-dev"; exit 1;
  }
  [ "$state" = "Succeeded" ] || { echo "FAIL: $name ($type) provisioningState is '$state' (expected Succeeded)"; exit 1; }
  echo "PASS: $name ($type) = Succeeded"
done

# ── 5. Verify Container Apps reach Running revision status (F-4) ─────────────
for app in app worker web keycloak; do
  app_name="ca-infinevo-dev-$app"
  status=$(az containerapp show -g rg-infinevo-dev -n "$app_name" --query "properties.runningStatus" -o tsv) \
    || { echo "FAIL: $app_name not found or az command failed"; exit 1; }
  [ -n "$status" ] || { echo "FAIL: $app_name returned empty runningStatus — app may not exist"; exit 1; }
  [ "$status" = "Running" ] || { echo "FAIL: $app_name runningStatus is '$status' (expected Running)"; exit 1; }
  echo "PASS: $app_name runningStatus = Running"
done

# ── 6. Run post-deploy DB bootstrap and verify all 5 schemas & ownership (F-2) ─
bash infra/azure/post-deploy-db.sh --env dev

# Verify no password in Key Vault matches local repository fallback literals
for secret in psql-admin-pw psql-app-pw psql-migration-pw psql-readonly-pw psql-keycloak-pw; do
  val=$(az keyvault secret show --vault-name kv-infinevo-shared --name "$secret" --query value -o tsv)
  [[ "$val" =~ ^local_.*_pw$ ]] && { echo "FAIL: $secret uses local repository fallback default '$val'"; exit 1; }
done
echo "PASS: All database passwords dynamically generated (no local_*_pw literal)"

PGHOST=$(az postgres flexible-server show -g rg-infinevo-dev -n psql-infinevo-dev --query fullyQualifiedDomainName -o tsv)
PGPASSWORD=$(az keyvault secret show --vault-name kv-infinevo-shared --name psql-admin-pw --query value -o tsv)
SCHEMA_COUNT=$(PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -U infinevo_admin -d infinevo -t -A -c \
  "SELECT count(*) FROM pg_namespace n JOIN pg_roles r ON n.nspowner = r.oid WHERE n.nspname IN ('core','hrms','payroll','reference','migration') AND r.rolname = 'migration_user';")
[ "$SCHEMA_COUNT" = "5" ] || { echo "FAIL: Expected 5 schemas owned by migration_user, found $SCHEMA_COUNT"; exit 1; }
echo "PASS: All 5 schemas exist and are owned by migration_user"

# ── 7. Rebuild-twice test ────────────────────────────────────────────────────
bash infra/azure/teardown.sh --env dev && bash infra/azure/deploy.sh --env dev
bash infra/azure/teardown.sh --env dev && bash infra/azure/deploy.sh --env dev
```

| Check | Expected | Result |
|---|---|---|
| `az bicep build` (all modules) | Exit 0, 0 errors | |
| `az bicep lint` (all modules) | Exit 0, 0 lint violations | |
| `az deployment sub what-if` | Exit 0, clean resource plan | |
| All 3 shared resources (`rg-infinevo-shared`) `provisioningState` | Every shared resource exists and is `Succeeded` | |
| All 9 environment resources (`rg-infinevo-dev`) `provisioningState` | Every resource exists and is `Succeeded` | |
| All 4 Container Apps `runningStatus` | Every app (`app`, `worker`, `web`, `keycloak`) is `Running` | |
| Key Vault database password check | Passwords dynamically generated, no `local_*_pw` literals | |
| Postgres schema count & ownership query | Exactly `5` schemas owned by `migration_user` | |
| Rebuild-twice test | Both redeployments exit 0 | |
| CI `infra.yml` workflow | Green on PR | |

---

## 6. Gap disposition

| Gap | Disposition | Rationale |
|---|---|---|
| `DEBT-004` secrets in `application.properties` | **Fixed forward** | All credentials (Postgres, Redis, Service Bus) stored in Azure Key Vault. Applications consume credentials via User-Assigned Managed Identity (`W-51`), eliminating committed secrets. |
| `DEBT-011` Cloudinary document storage | **Fixed forward** | Employee documents, payslips, and investment proofs moved to Azure Blob Storage with `allowBlobPublicAccess: false`. Private containers replace external Cloudinary. |
| `DEBT-020` in-process permission cache | **Deferred to `W-53`** | `W-50` provisions the Azure Cache for Redis instance; cache client abstraction and invalidation belong to `W-53`. |
| `DEBT-021` unlocked `@Scheduled` jobs | **Deferred to `W-52`** | `W-50` provisions the Service Bus queues; queue dispatchers and ShedLock clustered locking belong to `W-52`. |

---

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| PostgreSQL Flexible Server provisioning latency (10–15 min) | High | Normal Azure provisioning time. Use asynchronous provisioning wait in scripts. |
| SKU unavailability in Central India | Medium | Check SKU availability via `what-if` before deployment. Sizing adheres to standard `centralindia` SKUs. |
| Key Vault soft-delete blocks name reuse on teardown | Medium | In dev teardown, purge soft-deleted Key Vault if recreating the shared group, or use environment-specific suffixes. |
| Transient DB access during initial provision (pre-`W-51` private endpoints) | High | In dev only, temporarily authorize deployment runner IP in Flexible Server firewall; script sets a bash `trap` to guarantee revocation on script exit or error. |
| Cost overrun from orphaned resources | Low | Container Apps scale to zero when idle; ephemeral environments torn down with `teardown.sh`. |

---

## 8. Rollback

Nothing is live in production. If the PR is reverted:

- **Git Revert:** Removes `infra/azure/` Bicep templates, scripts, and `.github/workflows/infra.yml`.
- **Azure Resources:** Reverting git commits does not destroy running cloud resources. Run `infra/azure/teardown.sh --env dev` to delete `rg-infinevo-dev`.
- **Shared Resources:** If `rg-infinevo-shared` was provisioned, delete with `az group delete -n rg-infinevo-shared --yes --no-wait`.
- **Database Scripts:** `infra/postgres/provision.sh` remains backward-compatible with local dev (`PGHOST=localhost`).

---

## 9. Done when

> **Repository cleanup required (Issue #6):** Branch `origin/W-50-azure-infra` contains a second, conflicting W-50 specification (`W-50-azure-infra.md`). The merge gate (`check-done.mjs:116`) finds specs by filename pattern — two W-50 files on overlapping branches is undefined behaviour. **This branch must be deleted by a repository administrator before the PR for `W-50-azure-iac` can be merged.** The command is `git push origin --delete W-50-azure-infra`; it cannot be run from within a feature branch per project rules. The local copy of that branch has been deleted.

1. `infra/azure/main.bicep` and all module files pass both `az bicep build` (compilation) and `az bicep lint` (static analysis) with zero errors and zero warnings.
2. `deploy.sh --env dev` provisions all resources from zero in a single command (exit 0).
3. `teardown.sh --env dev` deletes `rg-infinevo-dev` (exit 0) and strictly refuses execution when `--env prod` is passed.
4. Rebuild-twice test passes: dev is torn down and redeployed twice cleanly, with terminal output linked in the PR.
5. All 4 Container Apps (`app`, `worker`, `web`, `keycloak`) reach running status with `AcrPull` role configured.
6. `infra/azure/post-deploy-db.sh` executes against the Flexible Server and confirms all 5 schemas (`core`, `hrms`, `payroll`, `reference`, `migration`) exist and are owned by `migration_user`.
7. `parameters/prod.bicepparam` specifies zone-redundant HA Postgres, Standard Redis, and ZRS storage.
8. `parameters/dev.bicepparam` and `uat.bicepparam` specify minimal SKUs with scale-to-zero.
9. `.github/workflows/infra.yml` passes on pull requests changing Bicep definitions. The workflow runs both `az bicep build` and `az bicep lint` on all modules.
10. Zero plain connection strings or secrets are committed, and neither `psql-admin-pw`, `psql-app-pw`, `psql-migration-pw`, `psql-readonly-pw`, nor `psql-keycloak-pw` in Azure Key Vault match local repo fallback literals (`local_*_pw`). Checkable via: `grep -rn "local_.*_pw" infra/azure/` returning 0 matches (exit 1).
11. PR description contains `Closes #70`.

---

## 10. Decisions requiring founder confirmation

The following 5 design choices require confirmation from the founder on issue [#70](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/70):

1. **IaC Tooling: Bicep or Terraform?**
   - **(a) (Recommended)** Bicep — Azure-native, no state file management or locking risk, native `az` CLI integration (`D-10`).
   - **(b)** Terraform — Multi-cloud portability, requires remote state storage backend in Blob Storage.

2. **Dev Postgres SKU: `Standard_B1ms` or `Standard_B2ms`?**
   - **(a) (Recommended)** `Standard_B1ms` (1 vCPU, 2 GiB RAM) — Cheapest burstable tier, sufficient for smoke tests and `D-19` scale.
   - **(b)** `Standard_B2ms` (2 vCPU, 4 GiB RAM) — Higher throughput for concurrent developers.

3. **Teardown Protection: Hard Script Guard or Separate Workflow?**
   - **(a) (Recommended)** Hard abort in `teardown.sh` when `--env prod` is passed; production teardown requires manual Azure portal elevation.
   - **(b)** Parameter confirmation prompt with countdown timer.

4. **ACR Tiering for Dev/UAT: Basic or Standard?**
   - **(a) (Recommended)** Basic for `dev`/`uat` (lowest cost); Standard for `prod` (higher webhook concurrency and throughput).
   - **(b)** Standard across all environments.

5. **Key Vault Topology: Single Shared or Per-Environment?**
   - **(a) (Recommended)** Single shared Key Vault in `rg-infinevo-shared` per `05-azure-architecture.md:28`, with RBAC-scoped secret naming per environment.
   - **(b)** Separate Key Vault per resource group (`kv-infinevo-dev`, `kv-infinevo-prod`).
