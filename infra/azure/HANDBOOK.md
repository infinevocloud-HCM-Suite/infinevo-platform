# Azure Infrastructure DevOps Handbook
**Infinevo HCM & Payroll Cloud Platform**

---

## 1. Executive & Architecture Overview

The Infinevo platform runs on a **Zero-Trust, Private-by-Default Architecture** in **Azure Central India (`centralindia`)** adhering to DPDP (Digital Personal Data Protection) compliance and high-security enterprise SaaS standards (`PLAT-09`, `PLAT-12`, `D-18`, `D-22`).

### Architectural Principles:
1. **Single Public Entrypoint**: Only **Azure Front Door** is exposed to the public internet.
2. **Zero Public Attack Surface**: PostgreSQL, Storage, Redis, and Key Vault run with **public network access disabled**. They communicate exclusively via **Private Endpoints** on dedicated private IP addresses inside `10.10.3.0/24`.
3. **VNet-Injected Compute**: Container Apps run inside a dedicated delegated subnet (`snet-cae`: `10.10.0.0/23`).
4. **Direct Origin Ingress Blocked**: The Container Apps explicitly drop and reject any traffic that does not originate from Azure Front Door backend IP ranges (returning HTTP `403 Forbidden`).
5. **No Long-Lived Secrets in Code**: Workloads authenticate using **Azure User-Assigned Managed Identities** (RBAC) and **Azure Key Vault**.

```
                           THE PUBLIC INTERNET
                                    │
                                    ▼ (HTTPS only)
                 ┌───────────────────────────────────────┐
                 │    Azure Front Door Standard + WAF    │
                 │      (afd-infinevo-shared)            │
                 │  - Edge TLS termination               │
                 │  - WAF Inspection & Rate Limiting     │
                 │  - RuleSet stamps X-Infinevo-Origin   │
                 └──────────────────┬────────────────────┘
                                    │
               (Allowed ONLY from AzureFrontDoor.Backend IPs)
                                    │
 ═══════════════════════════════════╪════════════════════════════════════════════════════════
 VIRTUAL NETWORK: vnet-infinevo-dev (10.10.0.0/16) in Central India
 ───────────────────────────────────┼────────────────────────────────────────────────────────
                                    ▼
 ┌──────────────────────────────────────────────────────────────────────────────────────────┐
 │ SUBNET: snet-cae (10.10.0.0/23) — Delegated to Microsoft.App/environments                │
 │ Container Apps Environment: cae-infinevo-dev                                             │
 │                                                                                          │
 │   ┌───────────────────────┐   ┌───────────────────────┐   ┌──────────────────────────┐   │
 │   │  ca-infinevo-dev-web  │   │  ca-infinevo-dev-app  │   │ ca-infinevo-dev-keycloak │   │
 │   │      (React UI)       │   │   (Spring Boot API)   │   │       (Keycloak 25)      │   │
 │   │      [Port 8080]      │   │      [Port 8080]      │   │        [Port 8080]       │   │
 │   └───────────────────────┘   └───────────┬───────────┘   └────────────┬─────────────┘   │
 │                                           │                            │                 │
 │   ┌────────────────────────┐              │                            │                 │
 │   │ ca-infinevo-dev-worker │              │                            │                 │
 │   │   (Queue Processor)    │              │                            │                 │
 │   └───────────┬────────────┘              │                            │                 │
 │               │                           │                            │                 │
 │   ┌───────────┴────────────┐              │                            │                 │
 │   │  caj-db-migration-dev  │              │                            │                 │
 │   │ (In-VNet Schema Runner)│              │                            │                 │
 │   └───────────┬────────────┘              │                            │                 │
 └───────────────┼───────────────────────────┼────────────────────────────┼─────────────────┘
                 │                           │                            │
                 ▼                           ▼                            ▼
 ┌──────────────────────────────────────────────────────────────────────────────────────────┐
 │ SUBNET: snet-pe (10.10.3.0/24) — Private Endpoints                                       │
 │                                                                                          │
 │  • 10.10.3.4  stinfinevodev.blob.core.windows.net      (Blob Storage: documents, payslips)│
 │  • 10.10.3.5  stinfinevodev.queue.core.windows.net     (Queue Storage: payrun, import)   │
 │  • 10.10.3.6  kv-infinevo-shared.vault.azure.net       (Key Vault: Database Credentials) │
 │  • 10.10.3.7  psql-infinevo-dev.postgres.database.azure.com (PostgreSQL Flexible Server)│
 └──────────────────────────────────────────────────────────────────────────────────────────┘
 ════════════════════════════════════════════════════════════════════════════════════════════
```

---

## 2. Directory & Account Coordinates

| Parameter | Value | Description |
|---|---|---|
| **Subscription Name** | `InvoiceLLM` | Primary cloud billing account |
| **Subscription ID** | `2ae37d8b-3189-474c-9508-4b3d7ceec4dd` | Target subscription |
| **Primary Region** | `centralindia` (Central India) | DPDP compliance data boundary |
| **Active Environment** | `dev` | Development & Integration |
| **Shared Resource Group** | `rg-infinevo-shared` | Global and shared resources |
| **Environment Group** | `rg-infinevo-dev` | Workload resources for dev |

---

## 3. Resource Inventory & Detailed Roles

### 3.1 `rg-infinevo-shared` (Shared Infrastructure)

1. **`afd-infinevo-shared` (Azure Front Door Standard + WAF)**:
   - **Type**: Global Application Gateway, CDN, and Layer 7 WAF.
   - **Dev Endpoint**: `ep-infinevo-dev` (`https://ep-infinevo-dev-huc0atg2hvd2fgbr.z02.azurefd.net/`)
   - **Routing Paths**:
     - `/` $\rightarrow$ Origin Group `og-dev-web` (Frontend UI)
     - `/api/*` $\rightarrow$ Origin Group `og-dev-app` (Backend API)
     - `/auth/*` $\rightarrow$ Origin Group `og-dev-keycloak` (Keycloak IDP)
   - **Response Stamping**: Custom ruleset `rsorigintag` appends response header `X-Infinevo-Origin: web|app|keycloak` for auditability and routing verification.
   - **WAF Security Policy (`wafinfinevoshared`)**:
     - Operating Mode: `Prevention`
     - Rate-Limit Rule: Enforces a ceiling of 100 requests per minute per client IP.
     - Canary Rule: Immediately returns `403 Forbidden` on requests matching `?wafcanary=block`.

2. **`crinfinevo` (Azure Container Registry)**:
   - **Tier**: Standard (Admin user disabled).
   - **Login Server**: `crinfinevo.azurecr.io`
   - **Access**: Pull access is granted via Managed Identity role assignment (`AcrPull`).

3. **`kv-infinevo-shared` (Azure Key Vault)**:
   - **Security Mode**: Azure RBAC (No legacy vault access policies).
   - **Network Policy**: Default Action is `Deny`. All public access is blocked. Communicates over Private Endpoint (`10.10.3.6`).
   - **Stored Secrets**:
     - `psql-admin-pw`: PostgreSQL Server admin (`infinevo_admin`) password.
     - `psql-app-pw`: Application user (`app_user`) credential.
     - `psql-migration-pw`: Migration runner (`migration_user`) credential.
     - `psql-readonly-pw`: Read-only reporting (`readonly_user`) credential.
     - `psql-keycloak-pw`: Keycloak database (`keycloak_user`) credential.

4. **`law-infinevo-shared` (Log Analytics Workspace)**:
   - **SKU**: PerGB2018 (30-day retention).
   - Collects container console stdout/stderr logs and diagnostic telemetry across all applications.

---

### 3.2 `rg-infinevo-dev` (Workload Infrastructure)

1. **`vnet-infinevo-dev` (Virtual Network)**:
   - **Address Space**: `10.10.0.0/16`
   - **Subnet `snet-cae`**: `10.10.0.0/23` (Delegated to `Microsoft.App/environments` for Container Apps).
   - **Subnet `snet-pe`**: `10.10.3.0/24` (Hosts Private Endpoints with Network Security Group `nsg-pe-dev`).

2. **`cae-infinevo-dev` (Container Apps Managed Environment)**:
   - VNet-injected into `snet-cae`.
   - Sends diagnostic telemetry directly to `law-infinevo-shared`.

3. **Container Apps (Compute Microservices)**:
   - **`ca-infinevo-dev-web`**: React Single Page Application (non-root nginx on port 8080 - it cannot bind a privileged port; see infra/docker/nginx/default.conf:11).
   - **`ca-infinevo-dev-app`**: Spring Boot Core Backend API (Port 8080). Configured with `DB_URL` (`jdbc:postgresql://psql-infinevo-dev.postgres.database.azure.com:5432/infinevo?sslmode=require`), `DB_USERNAME` (`app_user`), and `DB_PASSWORD` (Key Vault secret ref `psql-app-pw`), connecting to PostgreSQL Flexible Server over the Private Endpoint (`10.10.3.7`).
   - **`ca-infinevo-dev-worker`**: Spring Boot Background Async Processor (ShedLock + Queue consumer). Configured with `DB_URL` (`jdbc:postgresql://psql-infinevo-dev.postgres.database.azure.com:5432/infinevo?sslmode=require`), `DB_USERNAME` (`worker_user`), and `DB_PASSWORD` (Key Vault secret ref `psql-worker-pw`), connecting to PostgreSQL Flexible Server over the Private Endpoint (`10.10.3.7`).
   - **`ca-infinevo-dev-keycloak`**: Quay Keycloak 25 Authentication Server (Port 8080, Management Port 9000). Configured with `KC_DB_URL` (`jdbc:postgresql://psql-infinevo-dev.postgres.database.azure.com:5432/keycloak?sslmode=require`), `KC_DB_USERNAME` (`keycloak_user`), `KC_DB_PASSWORD` (Key Vault secret ref `psql-keycloak-pw`), and `KEYCLOAK_ADMIN_PASSWORD` (Key Vault secret ref `keycloak-admin-pw`).
   - **Security Restriction**: Every app configures `ipSecurityRestrictions` that whitelist only `AzureFrontDoor.Backend` IP CIDRs. Direct hits return `403 Forbidden`.

4. **`caj-db-migration-dev` (Database Migration Runner Job)**:
   - Manual or scheduled Container App Job running within `snet-cae`.
   - Uses `id-migration-dev` to read credentials from Key Vault and runs `provision.sh` directly against PostgreSQL Flexible Server (`10.10.3.7`).

5. **`psql-infinevo-dev` (PostgreSQL 16 Flexible Server)**:
   - **Compute**: Burstable `Standard_B1ms` (1 vCore, 2 GiB RAM), 32 GiB SSD (autogrow enabled).
   - **Public Access**: `Disabled`.
   - **Private IP**: `10.10.3.7` via `pe-postgres-infinevo-dev`.
   - **Databases**:
     - `infinevo`: Platform database containing schemas `core`, `hrms`, `payroll`, `reference`, and `migration`.
     - `keycloak`: Dedicated database for identity tokens and user profiles.

6. **`stinfinevodev` (Azure Storage Account)**:
   - **SKU**: Standard_LRS (`publicNetworkAccess: Disabled`).
   - **Blob Storage (`10.10.3.4`)**: Private containers `documents`, `payslips`, `proofs`.
   - **Queue Storage (`10.10.3.5`)**: Message queues `payrun`, `import`, `report`.

7. **Managed Identities (Zero Trust Security)**:
   - `id-app-dev`: Granted `Key Vault Secrets User`, `Storage Blob Data Contributor`, `Storage Queue Data Message Sender`, `AcrPull`.
   - `id-worker-dev`: Granted `Key Vault Secrets User`, `Storage Blob Data Contributor`, `Storage Queue Data Message Processor`, `Storage Queue Data Message Sender`, `AcrPull`.
   - `id-web-dev`: Granted `AcrPull` (static React/nginx frontend; no Key Vault access).
   - `id-keycloak-dev`: Granted `Key Vault Secrets User`, `AcrPull`.
   - `id-migration-dev`: Granted `Key Vault Secrets User`, `AcrPull`.

8. **GitHub Actions Service Principal (`sp-github-infinevo`)**:
   - **Authentication**: Federated OIDC credentials for repository `infinevocloud-HCM-Suite/infinevo-platform`.
   - **Subscription Scope**: Granted `Contributor` on `/subscriptions/${AZURE_SUBSCRIPTION_ID}` for running subscription-scoped Bicep validation (`az deployment sub what-if`) in CI (`infra.yml`).
   - **Resource Group Scopes**: Granted `Container Apps Contributor` on `rg-infinevo-dev` and `Reader` on `rg-infinevo-shared`.
   - **Registry Scope**: Granted `AcrPush` and custom role `Infinevo ACR Build Queue` on `crinfinevo.azurecr.io`.

---

## 4. Everyday DevOps GUI Playbook (Azure Portal)

### 4.1 Viewing Real-Time Logs of Container Apps
1. Open the [Azure Portal](https://portal.azure.com).
2. Go to **Resource Groups** > **`rg-infinevo-dev`**.
3. Click the Container App you want to inspect (e.g., `ca-infinevo-dev-app`).
4. In the left navigation menu under **Monitoring**, click **Log stream**.
5. Live stdout/stderr container logs will stream in real time.

### 4.2 Querying Centralized Logs in Log Analytics
1. Go to **Resource Groups** > **`rg-infinevo-shared`** > **`law-infinevo-shared`**.
2. Click **Logs** in the left sidebar.
3. Useful KQL queries:

**Find all errors in the backend over the last 2 hours:**
```kusto
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == "ca-infinevo-dev-app"
| where TimeGenerated > ago(2h)
| where Log_s has "ERROR" or Log_s has "Exception"
| order by TimeGenerated desc
| project TimeGenerated, Log_s
```

**Track traffic through Front Door:**
```kusto
AzureDiagnostics
| where ResourceProvider == "MICROSOFT.CDN"
| where Category == "FrontDoorAccessLog"
| order by TimeGenerated desc
| project TimeGenerated, requestUri_s, httpStatusCode_d, clientIp_s
| take 100
```

### 4.3 Opening a Shell (Console) inside a Container
1. In `rg-infinevo-dev`, click on the Container App.
2. In the left menu under **Monitoring**, click **Console**.
3. Select the container and startup command `/bin/sh` > Click **Connect**.
4. You can now execute shell commands directly inside the running container.

### 4.4 Deploying a New Container Image Tag
1. In `rg-infinevo-dev`, click on the Container App.
2. Under **Application**, click **Containers**.
3. Click **Edit and deploy** (or **Create new revision**).
4. Select the container > Update the image tag (e.g. `crinfinevo.azurecr.io/backend:v2.1.0`).
5. Click **Save** > **Create**. Azure will spin up the new revision and switch traffic with zero downtime.

### 4.5 Managing / Rotating Passwords in Key Vault
Because Key Vault network default action is `Deny`:
1. In `rg-infinevo-shared` > `kv-infinevo-shared` > Click **Networking**.
2. Select **"Allow public access from specific virtual networks and IP addresses"**.
3. Click **"Add your client IPv4 address"** > Click **Apply**.
4. Go to **Objects > Secrets** > View, create, or update the secret.
5. **CRITICAL**: Return to **Networking** and set back to **"Disable public access"** > Click **Apply** to re-lock the vault.

### 4.6 Triggering Database Migrations

**There are two jobs and they do different things. Starting the wrong one reports
Succeeded and applies nothing — that was bug #138.**

| Job | What it actually does |
|---|---|
| `caj-flyway-dev` | **Applies the Flyway migrations**, as `migration_user`. This is the migration job |
| `caj-db-migration-dev` | Runs `provision.sh` (roles, schemas, grants) and the six private-path probes. Applies no migration script |

To apply migrations:

1. In `rg-infinevo-dev`, click **`caj-flyway-dev`** (Container Apps Job).
2. Check its image tag is the release you mean. The job is created with a sentinel tag
   that cannot be pulled, so a hand-started run fails at image pull unless the pipeline,
   or you, has pointed it at a real `git-<sha>` image.
3. Click **Start job**.
4. In **Execution history**, open the new execution and read the log. A real run says
   `Successfully applied N migration(s)`. `No migration necessary` means nothing was
   applied — treat that as a result to explain, not as success.

To provision roles and schemas, or to re-run the private-path probes, start
`caj-db-migration-dev` instead. It must have been run once before the first Flyway run,
because it is what creates `migration_user`.

---

## 5. Automated Verification & Runbooks

To run the complete automated 8-point verification test suite from terminal:
```bash
# Add Azure CLI and Git bash to PATH
$env:PATH = "C:\Program Files\Microsoft SDKs\Azure\CLI2\wbin;C:\Users\Kmohapatra\.azure\bin;" + $env:PATH

# Execute the live test suite
bash infra/azure/verify-live.sh
```

### The 8 Verification Gates Tested:
1. **Bicep Build & Lint**: Validates templates adhere to strict security constraints.
2. **Public Network Access Closed**: Confirms Postgres (`Disabled`), Storage (`Disabled`), and Key Vault (`Deny`, 0 IP rules).
3. **Subnet Injection**: Confirms `cae-infinevo-dev` is inside `snet-cae`.
4. **App Health**: Verifies `web`, `app`, `worker`, and `keycloak` are `Running`.
5. **RBAC Grants**: Verifies all 16 granular identity role assignments exist.
6. **Data Residency**: Confirms all storage and compute reside strictly in `centralindia`.
7. **Direct Ingress Refusal**: Confirms calling Container App FQDNs directly returns `403 Forbidden`.
8. **Front Door & WAF**: Confirms endpoint returns `200 OK` on `/` and canary returns `403 Forbidden`.

---

## 6. Incident Response & Troubleshooting

### Scenario 1: Users report "504 Gateway Time-out" via Front Door
- **Cause**: Front Door cannot reach the backend Container App or the container app is not responding on its target port.
- **Troubleshooting Steps**:
  1. Go to `rg-infinevo-dev` > Select `ca-infinevo-dev-app`.
  2. Under **Application > Revision management**, check if the revision is in status `Ready`.
  3. Under **Monitoring > Log stream**, inspect the startup logs. Check if Spring Boot failed to connect to PostgreSQL.

### Scenario 2: Direct hit returns "403 Forbidden"
- **Normal Behavior**: Any hit to `https://ca-infinevo-dev-*.centralindia.azurecontainerapps.io/` **must return 403**. All legitimate traffic must pass through `https://ep-infinevo-dev-huc0atg2hvd2fgbr.z02.azurefd.net/`.

### Scenario 3: Database Connection Timeout
- **Cause**: Application cannot resolve or reach `10.10.3.7`.
- **Troubleshooting Steps**:
  1. Verify private DNS zone `privatelink.postgres.database.azure.com` has a virtual network link to `vnet-infinevo-dev`.
  2. Verify Network Security Group `nsg-pe-dev` allows port 5432 inbound from `10.10.0.0/23`.
