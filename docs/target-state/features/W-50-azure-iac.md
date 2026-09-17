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
| **Gaps addressed** | `DEBT-004` secrets in properties — full resolution: Key Vault replaces all connection strings |
| **Status** | **Draft — not approved** |
| **Approved by** | |
| **Approved on** | |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

The platform is deployed manually from a cloud portal today (`infra/README.md:16-20`). There is no infrastructure as code, no automated script to recreate an environment, and no audit trail of running cloud resources. Specifically:

- `infra/azure/` contains only `.gitkeep` — explicitly reserved for `W-50` (`W-01-repository-skeleton.md:90`).
- Secrets and connection strings live in committed configuration files (`DEBT-004`, deferred from `W-05`). Any leaked repository clone compromises production databases.
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

## 2. Scope

**In scope**

- `infra/azure/` — Modular Bicep infrastructure templates for every resource in `05-azure-architecture.md` §3.
- Three identical-topology environments: `dev`, `uat`, `prod` (differing only in SKU sizing and redundancy — `05` §2).
- One shared resource group (`rg-infinevo-shared`) containing:
  - Azure Container Registry (`crinfinevo`) with admin user disabled.
  - Azure Key Vault (`kv-infinevo-shared`) in Azure RBAC authorization mode.
  - Log Analytics Workspace (`law-infinevo-shared`).
- Per-environment resource groups (`rg-infinevo-{dev,uat,prod}`) containing:
  - Container Apps Managed Environment (`cae-infinevo-{env}`).
  - Container Apps definitions (`app`, `worker`, `web`, `keycloak`) with starter images and sizing.
  - PostgreSQL 16 Flexible Server (`psql-infinevo-{env}`) hosting `infinevo` and `keycloak` databases.
  - Azure Cache for Redis (`redis-infinevo-{env}`).
  - Azure Service Bus Namespace (`sb-infinevo-{env}`) with queues: `payrun`, `import`, `report`.
  - Azure Blob Storage Account (`stinfinevo{env}`) with containers: `documents`, `payslips`, `proofs`.
  - User-Assigned Managed Identity stubs (`id-{app,worker,web,keycloak}-{env}`).
- Idempotent orchestration scripts:
  - `infra/azure/deploy.sh` — provisions a named environment (`--env dev|uat|prod`).
  - `infra/azure/teardown.sh` — destroys an ephemeral environment (`rg-infinevo-{env}`) while guarding `prod`.
- Post-deployment database bootstrap: runs `infra/postgres/provision.sh` against the Flexible Server using Key Vault credentials (`W-05-postgres-schemas.md:102`).
- CI validation workflow (`.github/workflows/infra.yml`) executing `az bicep build --lint` and `az deployment sub what-if` on pull requests.

**Out of scope**

| Not here | Belongs to |
|---|---|
| Full VNet integration, private endpoints, Front Door + WAF, DNS routing | `W-51` |
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
├── PostgreSQL 16 Flexible Server       (psql-infinevo-{env})
│   ├── database: infinevo
│   └── database: keycloak
├── Azure Cache for Redis               (redis-infinevo-{env})
├── Service Bus Namespace               (sb-infinevo-{env})
│   ├── queue: payrun
│   ├── queue: import
│   └── queue: report
├── Storage Account                     (stinfinevo{env})
│   ├── container: documents
│   ├── container: payslips
│   └── container: proofs
└── Managed Identities                  (id-{app,worker,web,keycloak}-{env})
```

### 3b. SKU Sizing Matrix (`D-19` Scale: 10 Tenants × 100 Employees)

| Resource | Dev | UAT | Prod |
|---|---|---|---|
| **Container Registry** | Basic | Basic | Standard |
| **Key Vault** | Standard (Shared) | Standard (Shared) | Standard (Shared) |
| **PostgreSQL Flexible** | `Standard_B1ms` (Burstable) | `Standard_B1ms` (Burstable) | `Standard_D2ds_v5` (General Purpose, Zone-Redundant HA) |
| **PostgreSQL Storage** | 32 GiB, autogrow | 32 GiB, autogrow | 128 GiB, autogrow |
| **Redis Cache** | Basic C0 (250 MB) | Basic C0 (250 MB) | Standard C1 (1 GB, Replicated) |
| **Service Bus** | Standard | Standard | Standard |
| **Blob Storage** | Standard LRS | Standard LRS | Standard ZRS |
| **Container Apps** | 0.25-0.5 vCPU, min 0 / max 3 | 0.25-0.5 vCPU, min 0 / max 3 | 0.5-1.0 vCPU, min 2 / max 10 |

### 3c. File Inventory

```
infra/azure/
├── main.bicep                          # Root subscription/resource group orchestrator
├── modules/
│   ├── registry.bicep                  # Azure Container Registry
│   ├── keyvault.bicep                  # Key Vault with RBAC
│   ├── loganalytics.bicep              # Shared Log Analytics Workspace
│   ├── containerapp-env.bicep          # Container Apps Managed Environment
│   ├── containerapps.bicep             # Container Apps definitions (app, worker, web, keycloak)
│   ├── postgres.bicep                  # PostgreSQL Flexible Server & databases
│   ├── redis.bicep                     # Azure Cache for Redis
│   ├── servicebus.bicep                # Service Bus Namespace & queues
│   ├── storage.bicep                   # Storage Account & blob containers
│   └── managed-identities.bicep        # User-assigned managed identities per container role
├── parameters/
│   ├── dev.bicepparam                  # Dev parameters (minimal SKUs, scale-to-zero)
│   ├── uat.bicepparam                  # UAT parameters (prod topology, scaled-down)
│   └── prod.bicepparam                 # Prod parameters (HA, ZRS, redundant instances)
├── scripts/
│   ├── deploy.sh                       # End-to-end deployment script (idempotent)
│   ├── teardown.sh                     # Ephemeral teardown script with prod guard
│   └── post-deploy-db.sh               # Invokes infra/postgres/provision.sh against Flexible Server
└── README.md                           # Deployment runbook and Bicep conventions
.github/workflows/
└── infra.yml                           # CI validation (bicep build --lint & what-if)
```

| File | Change |
|---|---|
| `infra/azure/main.bicep` | **New.** Root orchestrator accepting `environment`, `location`, and SKU overrides. Deploys shared RG and environment RG. |
| `infra/azure/modules/registry.bicep` | **New.** Container Registry with admin user disabled (managed identity pull only). |
| `infra/azure/modules/keyvault.bicep` | **New.** Key Vault with Azure RBAC authorization, soft-delete, and purge protection for prod. |
| `infra/azure/modules/loganalytics.bicep` | **New.** Log Analytics workspace for platform container and diagnostic logging. |
| `infra/azure/modules/containerapp-env.bicep` | **New.** Container Apps Environment connected to the Log Analytics workspace. |
| `infra/azure/modules/containerapps.bicep` | **New.** Deploys `app`, `worker`, `web`, and `keycloak` Container Apps with ports, probes, and environment variables. |
| `infra/azure/modules/postgres.bicep` | **New.** Flexible Server 16, creates `infinevo` and `keycloak` databases; HA enabled on prod only. |
| `infra/azure/modules/redis.bicep` | **New.** Azure Cache for Redis instance (Basic C0 in dev/uat, Standard C1 in prod). |
| `infra/azure/modules/servicebus.bicep` | **New.** Service Bus Standard namespace with queues: `payrun`, `import`, `report`. |
| `infra/azure/modules/storage.bicep` | **New.** Storage account with blob containers: `documents`, `payslips`, `proofs`. |
| `infra/azure/modules/managed-identities.bicep` | **New.** Creates User-Assigned Managed Identities (`app`, `worker`, `web`, `keycloak`). |
| `infra/azure/parameters/*.bicepparam` | **New.** Parameter files for `dev`, `uat`, and `prod`. |
| `infra/azure/scripts/deploy.sh` | **New.** Automated deployment entrypoint wrapping Azure CLI deployment commands. |
| `infra/azure/scripts/teardown.sh` | **New.** Deletes environment resource group; strictly refuses `prod`. |
| `infra/azure/scripts/post-deploy-db.sh` | **New.** Runs `infra/postgres/provision.sh` against the Flexible Server using Key Vault admin credentials. |
| `.github/workflows/infra.yml` | **New.** GitHub Actions workflow running Bicep linting and what-if validation on PRs. |

**Untouched by this ticket:**
All code in `code/backend/`, `code/frontend/`, `infra/docker/`, `infra/postgres/*.sql`, and `.github/workflows/ci.yml`.

---

## 4. Proving it

The core principle: **an environment you cannot recreate is not infrastructure as code** (`09-build-order.md:265`).

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Deploy dev, tear down, redeploy twice | All three runs exit 0. Third deployment matches the first. Link output in PR |
| 2 | Delete `parameters/dev.bicepparam` and run `deploy.sh` | Script exits non-zero; no partial resources deployed |
| 3 | Remove a required Key Vault secret after deployment; run `post-deploy-db.sh` | Script exits non-zero (`ERROR: secret not found`); no database scripts execute |
| 4 | Set an invalid SKU string in `.bicepparam` | `az bicep build --lint` fails in CI; deployment is blocked |
| 5 | Introduce invalid Bicep syntax in a module | `infra.yml` CI workflow fails; PR cannot merge |
| 6 | Run `teardown.sh --env prod` | Script aborts immediately with `ERROR: prod teardown is forbidden`; prod resources untouched |

Prove tests 1–6 on a throwaway branch. Link evidence in the pull request.

---

## 5. Verification

Exact commands the verifier runs on a clean checkout with Azure CLI credentials:

```bash
# ── 1. Bicep syntax & lint validation ────────────────────────────────────────
az bicep build --lint --file infra/azure/main.bicep

# ── 2. Dry-run what-if on dev parameters ─────────────────────────────────────
az deployment sub what-if \
  --location centralindia \
  --template-file infra/azure/main.bicep \
  --parameters infra/azure/parameters/dev.bicepparam

# ── 3. Cold deployment from zero ─────────────────────────────────────────────
az group delete --name rg-infinevo-dev --yes --no-wait 2>/dev/null || true
sleep 10
bash infra/azure/scripts/deploy.sh --env dev

# ── 4. Verify all resources in Succeeded provisioning state ───────────────────
az resource list -g rg-infinevo-dev -o table | \
  grep -E "flexibleServers|redis|namespaces|storageAccounts|managedEnvironments|containerApps"

# ── 5. Run post-deploy database provisioning and verify schemas ───────────────
bash infra/azure/scripts/post-deploy-db.sh --env dev

PGHOST=$(az postgres flexible-server show -g rg-infinevo-dev -n psql-infinevo-dev \
  --query fullyQualifiedDomainName -o tsv)
PGPASSWORD=$(az keyvault secret show --vault-name kv-infinevo-shared --name psql-admin-pw --query value -o tsv) \
psql -h "$PGHOST" -U infinevo_admin -d infinevo -c "\dn" | grep -E "core|hrms|payroll|reference|migration"

# ── 6. Rebuild-twice test ────────────────────────────────────────────────────
bash infra/azure/scripts/teardown.sh --env dev && bash infra/azure/scripts/deploy.sh --env dev
bash infra/azure/scripts/teardown.sh --env dev && bash infra/azure/scripts/deploy.sh --env dev
```

| Check | Expected | Result |
|---|---|---|
| `az bicep build --lint` | Exit 0, 0 warnings | |
| `az deployment sub what-if` | Exit 0, clean resource plan | |
| Cold `deploy.sh --env dev` | Exit 0, all resources deployed | |
| `az resource list` state | All resources `Succeeded` | |
| `psql \dn` on Flexible Server | 5 schemas: `core`, `hrms`, `payroll`, `reference`, `migration` | |
| Rebuild-twice test | Both redeployments exit 0 | |
| CI `infra.yml` workflow | Green on PR | |

---

## 6. Gap disposition

| Gap | Disposition | Rationale |
|---|---|---|
| `DEBT-004` secrets in `application.properties` | **Fixed forward** | All credentials (Postgres, Redis, Service Bus) stored in Azure Key Vault. Applications consume credentials via User-Assigned Managed Identity (`W-51`), eliminating committed secrets. |
| `DEBT-020` in-process permission cache | **Deferred to `W-53`** | `W-50` provisions the Azure Cache for Redis instance; cache client abstraction and invalidation belong to `W-53`. |
| `DEBT-021` unlocked `@Scheduled` jobs | **Deferred to `W-52`** | `W-50` provisions the Service Bus queues; queue dispatchers and ShedLock clustered locking belong to `W-52`. |

---

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| PostgreSQL Flexible Server provisioning latency (10–15 min) | High | Normal Azure provisioning time. Use asynchronous provisioning wait in scripts. |
| SKU unavailability in Central India | Medium | Check SKU availability via `what-if` before deployment. Sizing adheres to standard `centralindia` SKUs. |
| Key Vault soft-delete blocks name reuse on teardown | Medium | In dev teardown, purge soft-deleted Key Vault if recreating the shared group, or use environment-specific suffixes. |
| Transient DB access during initial provision (pre-`W-51` private endpoints) | High | In dev only, temporarily authorize deployment runner IP in Flexible Server firewall; revoke immediately after `post-deploy-db.sh`. Document in PR. |
| Cost overrun from orphaned resources | Low | Container Apps scale to zero when idle; ephemeral environments torn down with `teardown.sh`. |

---

## 8. Rollback

Nothing is live in production. If the PR is reverted:

- **Git Revert:** Removes `infra/azure/` Bicep templates, scripts, and `.github/workflows/infra.yml`.
- **Azure Resources:** Reverting git commits does not destroy running cloud resources. Run `infra/azure/scripts/teardown.sh --env dev` to delete `rg-infinevo-dev`.
- **Shared Resources:** If `rg-infinevo-shared` was provisioned, delete with `az group delete -n rg-infinevo-shared --yes --no-wait`.
- **Database Scripts:** `infra/postgres/provision.sh` remains backward-compatible with local dev (`PGHOST=localhost`).

---

## 9. Done when

1. `infra/azure/main.bicep` and all module files pass `az bicep build --lint` with zero errors and zero warnings.
2. `deploy.sh --env dev` provisions all resources from zero in a single command (exit 0).
3. `teardown.sh --env dev` deletes `rg-infinevo-dev` (exit 0) and strictly refuses execution when `--env prod` is passed.
4. Rebuild-twice test passes: dev is torn down and redeployed twice cleanly, with terminal output linked in the PR.
5. `infra/azure/scripts/post-deploy-db.sh` executes against the Flexible Server and confirms all 5 schemas (`core`, `hrms`, `payroll`, `reference`, `migration`) exist and are owned by `migration_user`.
6. `parameters/prod.bicepparam` specifies zone-redundant HA Postgres, Standard Redis, and ZRS storage.
7. `parameters/dev.bicepparam` and `uat.bicepparam` specify minimal SKUs with scale-to-zero.
8. `.github/workflows/infra.yml` passes on pull requests changing Bicep definitions.
9. Zero plain connection strings or secrets are committed.
10. PR description contains `Closes #70`.

---

## Decisions confirmed

1. **IaC Tooling — Bicep (`D-10`)**: Azure-native Bicep chosen over Terraform to eliminate state file locking overhead and simplify CI.
2. **Dev Postgres SKU — `Standard_B1ms` (`D-19`)**: Burstable compute sufficient for smoke tests; upgrades to `Standard_D2ds_v5` with HA in prod.
3. **Teardown Guard**: `teardown.sh` strictly forbids destroying `prod`; production resource decommissioning requires manual elevated authorization.
4. **ACR Tiering**: Basic for `dev`/`uat`, Standard for `prod`.
5. **Key Vault Topology**: One shared Key Vault in `rg-infinevo-shared` (`05-azure-architecture.md:28`), using Azure RBAC with per-environment secret scoping.
