# W-51 — Networking & Identity

> Azure Virtual Network, Private Endpoints, Azure Front Door Standard + WAF, Managed Identity RBAC, In-VNet Migration Runner, and Central India Data Residency.
> Based on `TEMPLATE-INFRA.md`.
>
> **Revision 3.** Rev 2 closed the five blockers in
> `.claude/outputs/2026-09-19-review-spec-W-51.md`: origin protection made implementable
> (F-2), Key Vault lockdown stopped locking out its own deployer (F-3), the Container Apps
> environment recreation sequenced (F-5), and §5 rewritten so Front Door, the WAF and the
> private data path are **attempted** rather than asserted (F-1, F-4).
>
> Rev 3 changes one thing, on the founder's question *"can the Owner still open resources
> inside the VNet?"* — **Postgres moves from delegated-subnet VNet integration to a private
> endpoint** (§2.2). The answer was no, and rev 2 made it permanently no. Three
> consequences: the database is converted **in place** rather than dumped and recreated,
> `publicNetworkAccess` becomes reversible, and §8c adds an ops container so an operator
> has a route in without re-opening the perimeter.
>
> **Founder decisions, 2026-09-19** — recorded so a later gate does not re-raise them:
> the Key Vault **brief opening** is confirmed (question 2 below, now closed); **F-6 is
> waived** — the private-path probes stay as they are; and a standing ops container was
> declined (§8c).

| Field | Value |
|---|---|
| **Work item** | `W-51` · issue [#71](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/71) |
| **Kind** | Infra |
| **Stream / track** | Stream G — Infrastructure · Track I |
| **Wave** | Wave 2 — Data platform |
| **Size / skill** | M · INFRA |
| **Owner** | |
| **Blocked by** | `W-50` ([#70](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/70), merged #123 `b3234d0`) — Azure infrastructure as code |
| **Blocks** | `W-52` (Queue & worker) · `W-53` (Caching) · `W-54` (Deployment pipeline) · `W-56` (Secrets) · `W-57` (Deny-by-default auth) · `W-64` (Penetration test) |
| **Capabilities** | `PLAT-09` (Security hardening, network perimeter isolation, managed identity RBAC) |
| **Decisions** | `D-10` (Azure Container Apps) · `D-18` (India region / DPDP) · `D-19` (10 × 100 scale) · `D-22` (Deny-by-default public surface) · `D-48` (Unified backend image) |
| **Gaps addressed** | `DEBT-004` (Secrets hardcoded in `.properties`) |
| **Status** | **Draft — not approved** |
| **Approved by** | |
| **Approved on** | |

> **Hard rule 1:** No Bicep or script implementation is merged until this spec is approved by the founder.

---

## 1. Problem

With `W-50` merged on `main` (`b3234d0`), every data service in Azure sits on a public
endpoint behind a firewall rule, and the only public entry point the target design allows
— Front Door — does not exist.

1. **Every data service has a public endpoint.** `infra/azure/modules/keyvault.bicep:33`
   sets `publicNetworkAccess: 'Enabled'`, and Postgres, Redis, Service Bus and Storage
   are provisioned the same way. `docs/target-state/05-azure-architecture.md:75` states
   the target rule plainly: *"Only Front Door is public. Nothing else has a public
   endpoint."* Today nothing meets it.
2. **No WAF and no ingress gateway.** Traffic reaches Container Apps directly over their
   `*.azurecontainerapps.io` FQDNs — `infra/azure/modules/containerapps.bicep:49-54`
   configures `external: true` with no restriction of any kind. There is no edge TLS
   termination, no rate limiting and no request filtering
   (`docs/target-state/05-azure-architecture.md:63-75`).
3. **Managed identities exist but reach nothing.** `W-50` created the four UAMIs and gave
   them `AcrPull` (`infra/azure/modules/acr-role-assignment.bicep`). They hold no data-plane
   role on Key Vault, Storage or Service Bus, so an application has no way to authenticate
   except a connection string — which is the exposure `DEBT-004` records
   (`legacy/docs/GAP_INVENTORY.md:42`).
4. **The database bootstrap depends on opening the firewall.**
   `infra/azure/post-deploy-db.sh:65-93` opens a transient public firewall rule for the
   operator's own IP in order to run `provision.sh`, and its comment at `:66-67` names
   this ticket as the thing that retires it: *"W-51 brings private endpoints; until then
   the server carries only the Azure-services rule."*
5. **Data residency (`D-18`).** Indian payroll data falls under the DPDP Act. Primary and
   backups must both stay in `centralindia` (`07-decisions.md:30`).

### Baseline — measured on `main` (`b3234d0`) before W-51

| Command | Exit | Output |
|---|---|---|
| `az postgres flexible-server show -g rg-infinevo-dev -n psql-infinevo-dev --query network.publicNetworkAccess -o tsv` | 0 | `Enabled` |
| `az redis show -g rg-infinevo-dev -n redis-infinevo-dev --query publicNetworkAccess -o tsv` | 0 | `Enabled` |
| `az servicebus namespace show -g rg-infinevo-dev -n sb-infinevo-dev --query publicNetworkAccess -o tsv` | 0 | `Enabled` |
| `az storage account show -g rg-infinevo-dev -n stinfinevodev --query publicNetworkAccess -o tsv` | 0 | `Enabled` |
| `az keyvault show --vault-name kv-infinevo-shared --query properties.networkAcls.defaultAction -o tsv` | 0 | `Allow` |
| `az afd profile show -g rg-infinevo-shared -n afd-infinevo-shared --query name -o tsv` | 3 | `ResourceNotFound` |
| `az containerapp env show -g rg-infinevo-dev -n cae-infinevo-dev --query properties.vnetConfiguration -o tsv` | 0 | *(empty — the environment is not VNet-injected)* |
| `az containerapp job show -g rg-infinevo-dev -n caj-db-migration-dev --query name -o tsv` | 3 | `ResourceNotFound` |

---

## 1b. Authentication & environment prerequisites

- **Subscription and tenant**: `AZURE_SUBSCRIPTION_ID`, `AZURE_TENANT_ID`. Location
  `centralindia` (`D-18`). No secret value appears in any file — names only.
- **Operator**: Azure CLI ≥ 2.53.0 with Bicep (`az bicep install`). `az login --tenant
  "$AZURE_TENANT_ID"`, then `az account set --subscription "$AZURE_SUBSCRIPTION_ID"`.
- **CI** (`.github/workflows/infra.yml`): OIDC federated credentials via `azure/login@v2`,
  using repository secrets `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`.
  The principal needs `Contributor` **and** `User Access Administrator` on the subscription
  — role assignments are part of this deployment. `deploy.sh:52-63` already resolves the
  deploying principal's object id for both the user and service-principal cases.

---

## 2. Scope

### In scope

1. **Virtual network `vnet-infinevo-{env}`** in `centralindia`, address space
   `10.{octet}.0.0/16` — `dev: 10.10`, `uat: 10.20`, `prod: 10.30`:
   - `snet-cae` `10.{octet}.0.0/23` — injected into `cae-infinevo-{env}`. A `/23` is the
     documented minimum for a workload-profiles environment.
   - `snet-pe` `10.{octet}.3.0/24` — private endpoints, `privateEndpointNetworkPolicies:
     'Disabled'`. **Postgres included** — see §2.2.
   - `10.{octet}.2.0/24` is left unallocated. Rev 2 held a delegated `snet-postgres`
     there; §2.2 removes the need for it and the range is reserved rather than reused.
2. **Private endpoints and private DNS zones**, all five in `snet-pe`, each zone linked to
   `vnet-infinevo-{env}`: `privatelink.redis.cache.windows.net`,
   `privatelink.servicebus.windows.net`, `privatelink.blob.core.windows.net`,
   `privatelink.vaultcore.azure.net` and `privatelink.postgres.database.azure.com`.

   **Postgres uses a private endpoint, not delegated-subnet VNet integration.** The two
   modes are mutually exclusive and fixed at creation, and the choice has three
   consequences that decide this ticket:

   | | Delegated subnet (rev 2) | Private endpoint (this revision) |
   |---|---|---|
   | `publicNetworkAccess` afterwards | Not a property that exists — there is no toggle | **Reversible.** Public access with firewall rules is supported alongside the private endpoint |
   | Applying it to `psql-infinevo-dev` | Delete and recreate the server | **In place.** `infra/azure/modules/postgres.bicep` sets no `delegatedSubnetResourceId` and no `network` block, so `W-50` created the server in public-access mode — which is precisely the mode eligible for a private endpoint |
   | Operator access after lockdown | None, ever, without recreating | Break-glass path in §8c — the only operator route, by founder's decision |

   Microsoft's constraint runs the other way and is the reason rev 2's design could not be
   undone later: *"The use of private endpoints isn't currently supported on servers
   created with virtual network integration."* Choosing delegation now would have closed
   the door permanently.
3. **Perimeter lockdown**, which is *not* uniform and the difference is deliberate:

   | Resource | Setting | Why not simply `Disabled` |
   |---|---|---|
   | PostgreSQL Flexible Server | `publicNetworkAccess: 'Disabled'` + private endpoint in `snet-pe` | Reached only from inside the VNet, and retires the transient firewall rule at `post-deploy-db.sh:70-93`. Unlike the other four this one is **reversible by the subscription Owner** without recreating anything (§8c) |
   | Redis · Service Bus · Storage | `publicNetworkAccess: 'Disabled'` | No deployment-time data-plane access is needed |
   | **Key Vault** | `publicNetworkAccess: 'Enabled'` with `networkAcls.defaultAction: 'Deny'`, `bypass: 'AzureServices'`, plus a private endpoint | **See F-3 below.** `deploy.sh:120` and `post-deploy-db.sh:58,106,112` are Key Vault *data-plane* calls made from CI, before anything exists inside the VNet. `defaultAction: Deny` closes the vault to everyone; `deploy.sh` adds its own egress IP as a transient `ipRule` and removes it on every exit path, reusing the trap pattern already proven at `post-deploy-db.sh:70-93`. The vault has no open public endpoint at rest — **question 2 asks the founder to confirm this reading of `05-azure-architecture.md:75`** |
4. **Shared Front Door Standard + WAF** in `rg-infinevo-shared`:
   - One `Standard_AzureFrontDoor` profile `afd-infinevo-shared`
     (`05-azure-architecture.md:28` — `rg-infinevo-shared` is permanent, one only), serving
     all three environments through separate endpoints and origin groups. `D-19` scale does
     not justify Premium at roughly ten times the base cost.
   - Routes per environment: `/*` → `web`, `/api/*` → `app`, `/auth/*` → `keycloak`.
   - **Rule set `rs-origin-tag`** stamping `X-Infinevo-Origin: web|app|keycloak` on each
     route's response. This exists so §5 can prove *which* route matched from outside;
     without it every origin returns the same starter-image body and a misrouted request
     is indistinguishable from a correct one.
   - WAF policy `wafinfinevoshared` (`Standard_AzureFrontDoor`) with two custom rules:
     a rate limit of 100 requests per minute per client IP, and a **canary rule** blocking
     any request carrying `?wafcanary=block`. The canary exists to make WAF enforcement
     deterministically testable in seconds; the rate limit is the protection that matters.
     Managed Default Rule Sets are Premium-only and are not available here.
5. **Origin protection that Container Apps can actually enforce** (F-2). Front Door
   Standard has no Private Link origin, and Container Apps ingress has no header-matching
   rule — `ipSecurityRestrictions` is the only control the ingress schema offers
   (`containerapps.bicep:49-54` currently sets none). So:
   - Each Container App ingress gets `ipSecurityRestrictions` with
     `defaultAction: 'Deny'` and allow rules covering the `AzureFrontDoor.Backend` service
     tag prefixes, resolved at deploy time by `deploy.sh` via
     `az network list-service-tags --location centralindia` and passed to Bicep as a
     parameter. Anything not arriving from Front Door is refused at the ingress.
   - `X-Azure-FDID` header validation is **deferred to `W-57`**, where application code is
     in scope, as defence in depth. Rev 1 specified it here; nothing in `infra/` can
     implement it, and `code/backend/` is out of scope for this ticket.
6. **In-VNet migration runner**:
   - `infra/docker/migration-runner.Dockerfile` — a small image carrying `psql`,
     `redis-cli`, `curl` and the Azure CLI, pushed to `crinfinevo`. **In scope**, because
     the job has to run `provision.sh` and prove the private data path from inside the
     VNet, and no existing image carries those tools.
   - `caj-db-migration-{env}` — a `Microsoft.App/jobs@2024-03-01` manual-trigger job on
     `snet-cae`, identity `id-migration-{env}`, 900-second timeout, running
     `infra/postgres/provision.sh` against the private `$PGHOST`.
   - `deploy.sh` starts the job, polls, and fails the deployment unless it reports
     `Succeeded` — the same hard-fail shape as `deploy.sh:143-160`.
7. **Managed identity RBAC** — the matrix in §3f.
8. **Data residency (`D-18`)** — `centralindia` for every regional resource; storage
   LRS/ZRS with no geo-replication; backups in-jurisdiction.

### Out of scope

| Not here | Belongs to |
|---|---|
| `X-Azure-FDID` validation in application code | `W-57` |
| Spring Boot queue consumption and ShedLock | `W-52` |
| Redis caching implementation | `W-53` |
| Image promotion and zero-downtime deployment | `W-54` |
| Flyway migration execution (the job runs `provision.sh` only) | `W-06` → `W-54` |
| Automated secret rotation | `W-56` |
| Deny-by-default endpoint audit | `W-57` |
| Cross-tenant RLS isolation tests | `W-58` |
| Container vulnerability scanning | `W-59` |
| Telemetry dashboards and alerting | `W-60`, `W-61` |
| Backup automation and DR drill | `W-62` |
| Penetration testing of the perimeter | `W-64` |
| Keycloak realm export and theme | `W-10` |
| Custom domain names and public DNS records | **Question 1** — until answered, the default `*.azurefd.net` endpoint is used, which needs no DNS |
| Any change under `code/` | Product feature tickets |

---

## 3. What gets built

### 3a. Target architecture

```
                                  Internet
                                     │
                                     ▼
        ┌─────────────────────────────────────────────────────────┐
        │ Front Door Standard: afd-infinevo-shared                │
        │ WAF: wafinfinevoshared  ·  Rule set: rs-origin-tag      │
        │ (rg-infinevo-shared)                                    │
        └────────────────────────────┬────────────────────────────┘
                                     │ source IP ∈ AzureFrontDoor.Backend
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│ vnet-infinevo-{env}  10.{octet}.0.0/16  (centralindia)                  │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ snet-cae 10.{octet}.0.0/23  —  cae-infinevo-{env}                  │ │
│ │  ├── ca-infinevo-{env}-web / -app / -worker / -keycloak            │ │
│ │  │     ingress ipSecurityRestrictions: default Deny                │ │
│ │  └── caj-db-migration-{env}                                        │ │
│ └────────────────────────┬───────────────────────────────────────────┘ │
│                          │ private endpoints                           │
│                          ▼                                             │
│ ┌────────────────────────────────────────────────────────────────┐     │
│ │ snet-pe .3.0/24                                                │     │
│ │ postgres · redis · servicebus · blob · vault                   │     │
│ │ all five publicNetworkAccess: Disabled                         │     │
│ └────────────────────────────────────────────────────────────────┘     │
└────────────────────────────────────────────────────────────────────────┘
        kv-infinevo-shared: networkAcls defaultAction Deny
        + private endpoint + transient deployer ipRule (§2.3)
```

### 3b. File inventory

| File | Change | Task |
|---|---|---|
| `infra/azure/modules/vnet.bicep` | **New** — VNet, two subnets (`snet-cae`, `snet-pe`), NSGs. No delegation — §2.2 | T1 |
| `infra/azure/modules/private-dns.bicep` | **New** — five zones and VNet links, Postgres included | T1 |
| `infra/azure/modules/private-endpoint.bicep` | **New** — generic PE + DNS zone group | T1 |
| `infra/azure/modules/postgres.bicep` | Update — `publicNetworkAccess: 'Disabled'` and drop the `allowAzureIps` firewall rule at `:88-89`. **No `delegatedSubnetResourceId`** — the private endpoint is a separate resource, so the server is converted in place | T1 |
| `infra/azure/modules/redis.bicep` · `servicebus.bicep` · `storage.bicep` | Update — `publicNetworkAccess: 'Disabled'` | T1 |
| `infra/azure/modules/keyvault.bicep` | Update — add `networkAcls` (`defaultAction: 'Deny'`, `bypass: 'AzureServices'`, parameterised `ipRules`). `publicNetworkAccess` stays `'Enabled'` at `:33`; the `deployerObjectId` grant at `:39-46` stays exactly as it is — it is the RBAC half of the same problem | T1 |
| `infra/azure/modules/containerapp-env.bicep` | Update — add `vnetConfiguration.infrastructureSubnetId`. The resource at `:25-39` has no `vnetConfiguration` today and the property is immutable, so this **recreates** the environment (§8a) | T2 |
| `infra/azure/modules/containerapps.bicep` | Update — add `ipSecurityRestrictions` to all four `ingress` blocks (`:49-54` and the three that follow at `:148`, `:206`); `starterImage` at `:14` is unchanged | T2 |
| `infra/azure/modules/frontdoor.bicep` | **New** — profile, endpoints, origin groups, routes, `rs-origin-tag` rule set, WAF policy, security policy | T3 |
| `infra/docker/migration-runner.Dockerfile` | **New** — `psql`, `redis-cli`, `curl`, `az` | T4 |
| `infra/azure/modules/db-migration-job.bicep` | **New** — `caj-db-migration-{env}` on `snet-cae` | T4 |
| `infra/azure/modules/managed-identities.bicep` | Update — add the `migration` role, producing `id-migration-{env}` | T4 |
| `infra/azure/modules/rbac.bicep` | **New** — the §3f role assignments | T4 |
| `infra/azure/main.bicep` | Update — wire the new modules. It is `targetScope = 'subscription'` (`:1`) with `sharedRg`/`envRg` at `:80-91`; VNet and DNS must be deployed before `containerAppEnv` at `:156` | T1–T4 |
| `infra/azure/deploy.sh` | Update — resolve Front Door backend prefixes; add and revoke the transient Key Vault `ipRule`; start the migration job and assert `Succeeded` | T4 |
| `infra/azure/post-deploy-db.sh` | Update — remove the transient Postgres firewall rule block (`:65-93`); the job replaces it | T4 |
| `infra/azure/teardown.sh` | Update — remove Front Door endpoints, PEs, DNS links, VNet | T3 |
| `infra/azure/parameters/{dev,uat,prod}.bicepparam` | Update — CIDRs and per-environment Front Door endpoint names | T1 |

### 3c. What is not touched

`code/backend/`, `code/frontend/`, `infra/postgres/*.sql` and `provision.sh` (the job runs
the existing script unchanged), and everything under `legacy/`.

### 3d. Front Door SKU

| Dimension | Decision | Consequence |
|---|---|---|
| SKU | `Standard_AzureFrontDoor`, one profile, all environments | ~$35/month base against ~$330 for Premium. `D-19` is 10 tenants × 100 employees |
| Given up | Managed Default Rule Set (OWASP) | Custom rules only — a rate limit and the canary. `W-64` re-tests this and may justify Premium |
| Given up | Private Link origins | Origin protection falls to `ipSecurityRestrictions` (§2.5), which is an IP boundary, not cryptographic. Recorded as a risk in §7 |

### 3e. Migration runner

| Component | Detail |
|---|---|
| Image | `crinfinevo.azurecr.io/migration-runner:latest`, built from `infra/docker/migration-runner.Dockerfile` |
| Resource | `Microsoft.App/jobs@2024-03-01`, `triggerType: 'Manual'`, `replicaTimeout: 900` |
| Placement | `rg-infinevo-{env}`, `snet-cae` |
| Identity | `id-migration-{env}` — `Key Vault Secrets User`, `AcrPull` |
| Runs | `infra/postgres/provision.sh` against private `$PGHOST`, then the six private-path probes of §5 step 6 |
| Idempotency | `provision.sh` is re-run on every verification pass. It is already idempotent and needs no change: `infra/postgres/01-roles.sql:12-16` guards each role with `IF NOT EXISTS ... ELSE ALTER ROLE`, and `02-schemas.sql:8-21` pairs `CREATE SCHEMA IF NOT EXISTS` with an unconditional `ALTER SCHEMA ... OWNER TO` for exactly this reason |
| Gate | `deploy.sh` starts it, polls, and exits non-zero unless `Succeeded` |

### 3f. Role assignments

| Identity | Resource | Role | Exercised by |
|---|---|---|---|
| `id-app-{env}` | `kv-infinevo-shared` | `Key Vault Secrets User` | §5 step 6c |
| `id-app-{env}` | `stinfinevo{env}` | `Storage Blob Data Contributor` | §5 step 6d |
| `id-app-{env}` | `sb-infinevo-{env}` | `Azure Service Bus Data Sender` | §5 step 6e |
| `id-worker-{env}` | `kv-infinevo-shared` | `Key Vault Secrets User` | control plane only |
| `id-worker-{env}` | `stinfinevo{env}` | `Storage Blob Data Contributor` | control plane only |
| `id-worker-{env}` | `sb-infinevo-{env}` | `Azure Service Bus Data Receiver` | §5 step 6e |
| `id-worker-{env}` | `sb-infinevo-{env}` | `Azure Service Bus Data Sender` | control plane only |
| `id-web-{env}` | `kv-infinevo-shared` | `Key Vault Secrets User` | control plane only |
| `id-keycloak-{env}` | `kv-infinevo-shared` | `Key Vault Secrets User` | control plane only |
| `id-migration-{env}` | `kv-infinevo-shared` | `Key Vault Secrets User` | §5 step 6a |
| all five | `crinfinevo` | `AcrPull` | `deploy.sh:123-160`, already proven by `W-50` |

**Where "control plane only" appears, the assignment is verified to exist but not used.**
That is deliberate and bounded: each role is exercised for at least one identity on each
resource, which proves the endpoint, the DNS record and the RBAC model work. Per-identity
exercise of the rest arrives with the code that uses it, at `W-52` and `W-53`.

---

## 4. Proving it (deliberate breaks)

| # | Break | Command | Must happen |
|---|---|---|---|
| 1 | Reach Postgres from outside | `psql -h psql-infinevo-dev.postgres.database.azure.com -U infinevo_admin` | Connection refused or times out |
| 2 | Read a blob publicly | `curl -sI https://stinfinevodev.blob.core.windows.net/documents/probe.txt` | `403` |
| 3 | Bypass Front Door | `curl` the Container App FQDN from the verifier's own IP | `403` from ingress (§5 step 8) |
| 4 | Remove the WAF security policy association, re-run §5 step 4c | — | Canary request returns `200`, verification **fails** |
| 5 | Point a Front Door route at the wrong origin group, re-run §5 step 4b | — | `X-Infinevo-Origin` mismatches, verification **fails** |
| 6 | Break `provision.sh`, trigger the job | `az containerapp job start` | Job `Failed`; `deploy.sh` exits 1 |
| 7 | Deploy outside India | `--location eastus` | Bicep `@allowed` constraint rejects it |
| 8 | Revoke `Storage Blob Data Contributor` from `id-app-dev`, re-run step 6d | — | Blob write returns `403`, verification **fails** |

Breaks 4, 5 and 8 exist because rev 1 could not fail in any of those three ways.

---

## 5. Verification

Run on a clean checkout against `dev`. Steps 4, 6 and 8 are the ones that attempt rather
than assert.

```bash
#!/usr/bin/env bash
set -eo pipefail
ENV="dev"; RG_ENV="rg-infinevo-${ENV}"; RG_SHARED="rg-infinevo-shared"

# ── 1. Bicep compiles and lints ──────────────────────────────────────────────
az bicep build --file infra/azure/main.bicep
az bicep lint  --file infra/azure/main.bicep
echo "PASS 1: bicep build and lint clean"

# ── 2. Public access closed ──────────────────────────────────────────────────
for item in \
  "PostgreSQL:az postgres flexible-server show -g $RG_ENV -n psql-infinevo-$ENV --query network.publicNetworkAccess -o tsv" \
  "Redis:az redis show -g $RG_ENV -n redis-infinevo-$ENV --query publicNetworkAccess -o tsv" \
  "ServiceBus:az servicebus namespace show -g $RG_ENV -n sb-infinevo-$ENV --query publicNetworkAccess -o tsv" \
  "Storage:az storage account show -g $RG_ENV -n stinfinevo$ENV --query publicNetworkAccess -o tsv"; do
  name="${item%%:*}"; val=$(eval "${item#*:}")
  [ "$val" = "Disabled" ] || { echo "FAIL 2: $name = '$val'"; exit 1; }
done
# Key Vault is closed by network ACL, not by publicNetworkAccess - see section 2.3.
kv_default=$(az keyvault show --vault-name kv-infinevo-shared --query properties.networkAcls.defaultAction -o tsv)
[ "$kv_default" = "Deny" ] || { echo "FAIL 2: Key Vault networkAcls.defaultAction = '$kv_default'"; exit 1; }
kv_rules=$(az keyvault show --vault-name kv-infinevo-shared --query "length(properties.networkAcls.ipRules)" -o tsv)
[ "$kv_rules" = "0" ] || { echo "FAIL 2: Key Vault still carries $kv_rules transient ipRule(s) - deploy.sh did not revoke"; exit 1; }
echo "PASS 2: all five data services closed to the public internet"

# ── 3. Container Apps environment is VNet-injected (F-5) ─────────────────────
subnet=$(az containerapp env show -g "$RG_ENV" -n "cae-infinevo-${ENV}" \
  --query "properties.vnetConfiguration.infrastructureSubnetId" -o tsv)
case "$subnet" in *"/subnets/snet-cae") ;; *) echo "FAIL 3: environment not injected into snet-cae (got '${subnet:-<empty>}')"; exit 1;; esac
echo "PASS 3: cae-infinevo-${ENV} injected into snet-cae"

# ── 4. Front Door is walked through, not just inspected (F-1) ────────────────
AFD_HOST=$(az afd endpoint show -g "$RG_SHARED" --profile-name afd-infinevo-shared \
  --endpoint-name "ep-infinevo-${ENV}" --query hostName -o tsv)

# 4a. each route reaches an origin at all
for path in "/" "/api/" "/auth/"; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "https://${AFD_HOST}${path}")
  [ "$code" = "200" ] || { echo "FAIL 4a: https://${AFD_HOST}${path} returned $code"; exit 1; }
done

# 4b. and reaches the RIGHT one - rs-origin-tag stamps the matched route
for pair in "/:web" "/api/:app" "/auth/:keycloak"; do
  path="${pair%%:*}"; want="${pair#*:}"
  got=$(curl -s -o /dev/null -D - "https://${AFD_HOST}${path}" | tr -d '\r' \
        | awk -F': ' 'tolower($1)=="x-infinevo-origin"{print $2}')
  [ "$got" = "$want" ] || { echo "FAIL 4b: $path tagged '$got', expected '$want'"; exit 1; }
  # cross-check the route's origin group actually holds that app's FQDN
  fqdn=$(az containerapp show -g "$RG_ENV" -n "ca-infinevo-${ENV}-${want}" \
         --query "properties.configuration.ingress.fqdn" -o tsv)
  az afd origin list -g "$RG_SHARED" --profile-name afd-infinevo-shared \
     --origin-group-name "og-${ENV}-${want}" --query "[].hostName" -o tsv | grep -Fqx "$fqdn" \
    || { echo "FAIL 4b: og-${ENV}-${want} does not contain $fqdn"; exit 1; }
done

# 4c. the WAF actually blocks something
code=$(curl -s -o /dev/null -w "%{http_code}" "https://${AFD_HOST}/api/?wafcanary=block")
[ "$code" = "403" ] || { echo "FAIL 4c: WAF canary returned $code, expected 403 - WAF is not enforcing"; exit 1; }

# 4d. and the rate limit is real
blocked=0
for i in $(seq 1 150); do
  c=$(curl -s -o /dev/null -w "%{http_code}" "https://${AFD_HOST}/api/")
  [ "$c" = "403" ] && blocked=$((blocked+1))
done
[ "$blocked" -gt 0 ] || { echo "FAIL 4d: 150 requests in one minute, none rate-limited"; exit 1; }
echo "PASS 4: routes resolve to the correct origins; WAF blocks the canary and rate-limits"

# ── 5. Role assignments exist (--all: every role in 3f is resource-scoped) ────
declare -A EXPECTED_ROLES=(
  ["id-app-${ENV}"]="Key Vault Secrets User|Storage Blob Data Contributor|Azure Service Bus Data Sender|AcrPull"
  ["id-worker-${ENV}"]="Key Vault Secrets User|Storage Blob Data Contributor|Azure Service Bus Data Receiver|Azure Service Bus Data Sender|AcrPull"
  ["id-keycloak-${ENV}"]="Key Vault Secrets User|AcrPull"
  ["id-web-${ENV}"]="Key Vault Secrets User|AcrPull"
  ["id-migration-${ENV}"]="Key Vault Secrets User|AcrPull"
)
for uami in "${!EXPECTED_ROLES[@]}"; do
  sp_id=$(az identity show -g "$RG_ENV" -n "$uami" --query principalId -o tsv)
  roles=$(az role assignment list --assignee "$sp_id" --all --query "[].roleDefinitionName" -o tsv)
  IFS='|' read -ra want <<< "${EXPECTED_ROLES[$uami]}"
  for r in "${want[@]}"; do
    echo "$roles" | grep -Fqx "$r" || { echo "FAIL 5: $uami missing '$r'"; exit 1; }
  done
done
echo "PASS 5: all role assignments present"

# ── 6. The private path is used, from inside the VNet (F-4) ──────────────────
# The job runs provision.sh and then four probes. Each probe RESOLVES the private
# name and COMPLETES a data-plane call; a private endpoint on the wrong subresource,
# or a DNS zone with no A record, fails here and nowhere else.
#   6a  Key Vault  - id-migration reads psql-admin-pw over the private endpoint
#   6b  Postgres   - provision.sh connects over the Postgres private endpoint
#   6c  Key Vault  - id-app reads a secret (proves the app identity's KV role)
#   6d  Storage    - id-app writes and reads back a probe blob
#   6e  Service Bus- id-app sends a probe message, id-worker receives it
#   6f  Redis      - redis-cli PING over the private endpoint (TLS, 6380)
#
# WAIVED, founder decision 2026-09-19 (review finding F-6,
# .claude/outputs/2026-09-19-review-spec-W-51-rev2.md): the gate below greps the job's own
# log, so a probe that prints "OK" without connecting passes, as does one that reached a
# public endpoint instead. The private path is therefore CONFIRMED BY THE JOB, NOT PROVEN
# INDEPENDENTLY. Accepted knowingly. Do not re-raise at /verify or /review; the first
# genuine exercise of these paths is W-52 (Service Bus) and W-53 (Redis).
# Every probe first asserts the name resolves into 10.x, so a public-endpoint
# fallback cannot make this pass.
job="caj-db-migration-${ENV}"
exec_name=$(az containerapp job start -g "$RG_ENV" -n "$job" --query name -o tsv)
deadline=$(( $(date +%s) + 900 ))   # matches replicaTimeout in section 3e
while :; do
  status=$(az containerapp job execution show -g "$RG_ENV" -n "$job" \
           --job-execution-name "$exec_name" --query status -o tsv 2>/dev/null || true)
  [ "$status" = "Succeeded" ] && break
  [ "$status" = "Failed" ] && { echo "FAIL 6: $exec_name reported Failed"; exit 1; }
  [ "$(date +%s)" -gt "$deadline" ] && { echo "FAIL 6: $exec_name still '$status' after 900s"; exit 1; }
  sleep 10
done
az containerapp job logs show -g "$RG_ENV" -n "$job" --execution "$exec_name" --tail 200 \
  | tee /tmp/w51-job.log
for probe in PROBE-KV-MIGRATION PROBE-PG PROBE-KV-APP PROBE-BLOB PROBE-SB PROBE-REDIS; do
  grep -Fq "${probe}: OK" /tmp/w51-job.log \
    || { echo "FAIL 6: ${probe} did not report OK"; exit 1; }
done
echo "PASS 6: provision.sh ran privately and all six private-path probes passed"

# ── 7. Data residency (D-18) ─────────────────────────────────────────────────
bad=$(az resource list -g "$RG_ENV" --query "[?location!='centralindia'].name" -o tsv)
[ -z "$bad" ] || { echo "FAIL 7: outside centralindia: $bad"; exit 1; }
bad=$(az resource list -g "$RG_SHARED" --query "[?location!='centralindia' && location!='global'].name" -o tsv)
[ -z "$bad" ] || { echo "FAIL 7: outside centralindia: $bad"; exit 1; }
echo "PASS 7: all regional resources in centralindia"

# ── 8. Direct origin access is refused (F-2) ─────────────────────────────────
# The verifier's IP is not in AzureFrontDoor.Backend, so ingress must deny it.
# Probing "/" rather than "/health": 05-azure-architecture.md:159 keeps health and
# readiness off the Front Door path, and the starter image serves "/" (containerapps.bicep:75).
for role in web app keycloak; do
  fqdn=$(az containerapp show -g "$RG_ENV" -n "ca-infinevo-${ENV}-${role}" \
         --query "properties.configuration.ingress.fqdn" -o tsv)
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 20 "https://${fqdn}/" || echo "000")
  [ "$code" = "403" ] || { echo "FAIL 8: direct hit on $role returned $code, expected 403"; exit 1; }
done
echo "PASS 8: direct origin access refused for all three ingress apps"
```

| Check | Expected |
|---|---|
| 1 Bicep build and lint | Exit 0, zero violations |
| 2 Postgres · Redis · Service Bus · Storage `publicNetworkAccess` | `Disabled` |
| 2 Key Vault `networkAcls.defaultAction`, transient rules revoked | `Deny`, `ipRules` length `0` |
| 3 Environment VNet injection | `infrastructureSubnetId` ends `/snet-cae` |
| 4a Routes reachable through Front Door | `200` on `/`, `/api/`, `/auth/` |
| 4b Route → origin mapping | `X-Infinevo-Origin` matches, origin group holds the app's real FQDN |
| 4c WAF canary | `403` |
| 4d Rate limit | ≥ 1 of 150 requests blocked |
| 5 Role assignments | All 16 present |
| 6 Job + six private-path probes | `Succeeded`, six `PROBE-*: OK` lines |
| 7 Residency | No resource outside `centralindia` except the global profile |
| 8 Direct origin access | `403` on all three |

---

## 6. Gap disposition

| Gap | Disposition |
|---|---|
| `DEBT-004` — secrets hardcoded in `.properties` (`GAP_INVENTORY.md:42`) | **Advanced, not closed.** Every identity gets a data-plane role, so an application can authenticate without a connection string, and §5 step 6 proves three of those paths work. Closing it means no credential in any file, which is `W-56`'s "done when" (`09-build-order.md:279`) |
| `DEBT-002` / `BUG-004` — `ddl-auto`, no migration framework | **Unaffected.** This ticket creates no database object and runs no DDL of its own; the job executes the existing `infra/postgres/provision.sh` |
| `DEBT-003` — no tests | **Unaffected.** Infrastructure ticket; §5 is the evidence |
| `DEBT-018` — no indexes | **Unaffected.** No tables |
| `DEBT-021` — unlocked schedulers | **Deferred to `W-52`**, which owns ShedLock |

Rev 1 cited `SEC-001` for the exposed database. **No such ID exists** — `GAP_INVENTORY.md`
holds `BUG-001`–`007` and `DEBT-001`–`033`. The public database is a property of the
`W-50` target state, not a frozen-system defect, so it is stated in §1 as the problem and
carries no gap ID.

### Standing rules

**This ticket creates no table, column, index or migration script.** `tenant_id`, RLS,
Flyway, `Money`/`BigDecimal` precision and expand/contract sequencing therefore do not
apply — recorded explicitly because `/review-spec` check 3 measures the draft against them.
Nothing under `docs/` or `legacy/` is edited.

---

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| **The Container Apps environment cannot switch networking in place.** `infrastructureSubnetId` is immutable and `containerapp-env.bicep:25-39` sets none, so `cae-infinevo-dev` and all four apps are destroyed and rebuilt, and the migration job cannot exist until the new environment does | High | §8a. This is now the **only** destructive step in the ticket — Postgres no longer needs recreating. Dev is "rebuildable at will" (`05-azure-architecture.md:29`); UAT and prod follow the full procedure |
| **`ipSecurityRestrictions` is an IP boundary, not authentication.** Anything egressing from a Front Door backend IP satisfies it, and the prefix list changes over time | Medium | Accepted for Standard. `deploy.sh` re-resolves the service tag on every run, `W-57` adds `X-Azure-FDID` in the application, and `W-64` tests the perimeter. Premium with Private Link origins remains the upgrade path |
| **The transient Key Vault `ipRule` is a real, if brief, opening** | Medium | Scoped to one `/32`, added after the deployment and revoked by an `EXIT` trap on every path — the pattern already working at `post-deploy-db.sh:70-93`. §5 step 2 fails if any rule survives |
| **Front Door backend prefix list is large and changes** | Low | Resolved at deploy time from `az network list-service-tags`; drift shows up as §5 step 4a returning `403` |
| **Job timeout on a slow first bootstrap** | Low | `replicaTimeout: 900`, and §5 step 6 polls to the same 900s rather than rev 1's 150s |
| **Rate-limit test is slow and can be flaky under a cold start** | Low | Step 4d runs last among the Front Door checks and needs only one block out of 150 |

---

## 8. Rollback

### 8a. Recreation procedure — the Container Apps environment

`infrastructureSubnetId` is immutable and `containerapp-env.bicep:25-39` sets none, so the
environment must be replaced. It is the only destructive step in this ticket: the database
is converted in place (§2.2), so there is no dump, no restore and no second server.

The one ordering constraint is that the migration job cannot exist before the environment
it runs in.

1. **Build the network first.** Deploy `vnet.bicep` and `private-dns.bicep`. Additive —
   nothing existing is touched.
2. **Add the Postgres private endpoint** and set `publicNetworkAccess: 'Disabled'`. The
   server keeps its data, its FQDN and its identity; only the route to it changes.
3. **Recreate the environment.** Delete `cae-infinevo-{env}`, which removes all four
   Container Apps, and redeploy it with `infrastructureSubnetId` set to `snet-cae`. The
   apps return on the starter image, as `W-50` left them.
4. **Create the migration job.** It can only exist now. `deploy.sh` starts it and
   `provision.sh` runs over the private endpoint.
5. **Validate.** Run §5 in full.

**If step 2 or 5 fails**, §8c re-opens the database in seconds without touching step 3.

### 8b. General rollback

1. **Emergency public re-enablement** — see §8c, which is now a supported operation
   rather than an emergency, because the server is private-endpoint based.
2. **Front Door** — routes can be repointed or an endpoint disabled without touching the
   VNet; the profile is in a separate resource group and a separate deployment.
3. **Clean rebuild** — `infra/azure/teardown.sh --env dev`, then redeploy `W-50`'s
   baseline at `b3234d0`.

### 8c. Operator access — how the Owner reaches a private resource

Subscription **Owner** is a control-plane role. It grants no network route, so once §2.3
lands, an Owner at a laptop cannot run `psql`, cannot browse the storage account in the
portal, and cannot read a Key Vault secret in the portal. Owner *can* still change those
settings — so nobody is locked out permanently, but the only way in would be to re-open the
perimeter, which turns a routine lookup into a security event. Two routes exist instead.

**The founder has declined a standing ops container** (2026-09-19). So break-glass below
is the *only* operator route, and it is the one to reach for. If it starts being used
routinely, revisit: an ops container costs nothing idle and opens nothing, whereas each
break-glass widens the perimeter for as long as it is open.

**Break-glass — re-open the database.** Available only because §2.2 chose a private
endpoint; under rev 2's delegated subnet there was no such command. Founder authorisation,
time-boxed, and the firewall rule is scoped to one address:

```bash
az postgres flexible-server update -g rg-infinevo-dev -n psql-infinevo-dev   --public-network-access Enabled
az postgres flexible-server firewall-rule create -g rg-infinevo-dev -n psql-infinevo-dev   --rule-name breakglass-$(date +%Y%m%d) --start-ip-address "$MY_IP" --end-ip-address "$MY_IP"
# ... work ...
az postgres flexible-server firewall-rule delete -g rg-infinevo-dev -n psql-infinevo-dev   --rule-name breakglass-$(date +%Y%m%d) --yes
az postgres flexible-server update -g rg-infinevo-dev -n psql-infinevo-dev   --public-network-access Disabled
```

Re-running §5 step 2 afterwards is what confirms the environment is closed again.

---

## 9. Done when

1. All six new Bicep modules compile and pass `az bicep lint` with zero violations.
2. `deploy.sh --env dev` provisions the VNet, subnets, private endpoints, DNS zones,
   Front Door, migration job and role assignments, and exits 0 — including the Key Vault
   data-plane write at `deploy.sh:120`, which proves §2.3's ACL mechanism works.
3. Postgres, Redis, Service Bus and Storage report `publicNetworkAccess: Disabled`;
   Key Vault reports `networkAcls.defaultAction: Deny` with zero surviving `ipRules`.
4. `cae-infinevo-dev` reports an `infrastructureSubnetId` ending `/snet-cae`.
5. A request through Front Door reaches each of the three origins, and the response
   carries the `X-Infinevo-Origin` value matching the route — verified against each app's
   live ingress FQDN.
6. The WAF returns `403` for the canary request, and blocks at least one request in a
   150-request burst.
7. The migration job runs `provision.sh` against the private server and emits six
   `PROBE-*: OK` lines covering Key Vault (twice), Postgres, Blob, Service Bus and Redis,
   each having first resolved a `10.x` address.
8. All 16 role assignments in §3f are present under `az role assignment list --all`.
9. A direct request to each Container App FQDN from outside Front Door returns `403`.
10. Every resource is in `centralindia`, the Front Door profile excepted as `global`.
11. `post-deploy-db.sh` no longer opens a Postgres firewall rule, and `psql-infinevo-dev`
    was converted in place — `az postgres flexible-server show --query id` returns the
    same resource id as before the change.
12. PR description contains `Closes #71`.

---

## Implementer tasks

All four are in `infra/` — one area, so one implementer, sequentially. Ordering is forced
by §8a: nothing can be verified until T1 and T2 are both in place.

| # | Task | Files | Depends on |
|---|---|---|---|
| **T1** | VNet, subnets, private DNS, private endpoints, and the five lockdown changes including the Key Vault ACL | `vnet.bicep`, `private-dns.bicep`, `private-endpoint.bicep`, `postgres.bicep`, `redis.bicep`, `servicebus.bicep`, `storage.bicep`, `keyvault.bicep`, `main.bicep`, three `.bicepparam` | — |
| **T2** | Environment VNet injection and ingress IP restrictions | `containerapp-env.bicep`, `containerapps.bicep` | T1 |
| **T3** | Front Door, WAF, `rs-origin-tag`, teardown | `frontdoor.bicep`, `teardown.sh`, `main.bicep` | T2 |
| **T4** | Migration runner image, job, RBAC, deploy orchestration | `migration-runner.Dockerfile`, `db-migration-job.bicep`, `managed-identities.bicep`, `rbac.bicep`, `deploy.sh`, `post-deploy-db.sh` | T2 |

No module gains a Maven dependency; there is no dependency edge to check in either
direction.

---

## Decisions — all closed, 2026-09-19

**No open questions remain.** All four were answered by the founder on 2026-09-19 and are
recorded here so a later gate does not re-open them.

1. ~~**Custom domains and DNS**~~ — **Answered: DNS stays at the external registrar.**
   No Azure DNS zone is created, and `05-azure-architecture.md:28` listing a DNS zone in
   `rg-infinevo-shared` is not acted on by this ticket. Two consequences:
   - **Custom domains are out of scope for automation.** `deploy.sh` cannot create or
     validate the records, so binding `dev|uat|app.infinevo.cloud` is a **manual step at
     the registrar** — a TXT record for domain validation and a CNAME to
     `afd-infinevo-shared.azurefd.net`, per environment, done once. T3 documents the exact
     two records in `infra/azure/README.md`; it does not attempt them.
   - **Everything in §5 runs on the default `*.azurefd.net` endpoint**, which needs no DNS.
     So verification is complete and green before any domain is bound, and binding one
     later changes no Bicep.
2. ~~**Key Vault is the one resource not set to `publicNetworkAccess: 'Disabled'`**~~
   — **Answered 2026-09-19: brief opening confirmed.** The vault keeps
   `networkAcls.defaultAction: 'Deny'` with a transient `/32` `ipRule` added and revoked by
   `deploy.sh` within the same run. The alternative — moving all secret generation inside
   the migration job — was considered and not taken. This is a deliberate, recorded
   deviation from `05-azure-architecture.md:75`, not an oversight.
3. ~~**`X-Azure-FDID` deferral**~~ — **Answered: confirmed, it moves to `W-57`.**
   Origin protection in this ticket is `ipSecurityRestrictions` against the Front Door
   backend prefixes (§2.5), verified by §5 step 8. `code/backend/` stays out of scope.
   **`W-57` inherits the follow-on**: until it lands, the origin boundary is IP-based only,
   which §7 records as a Medium risk.
4. ~~**Private-path probe evidence (review finding F-6)**~~ — **Answered: waived.** §5
   step 6 gates on the job's own log output. Accepted knowingly; see the comment in §5.

Founder approval is required before any Bicep is written.
