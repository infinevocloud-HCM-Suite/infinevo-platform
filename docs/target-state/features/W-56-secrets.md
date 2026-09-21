# W-56 — Secrets

> Key Vault Secret Integration, Managed Identity Secret Resolution, Dual-Role Database Zero-Downtime Rotation, and Repository-Wide Secret Elimination.
> Based on `TEMPLATE-INFRA.md`.

| Field | Value |
|---|---|
| **Work item** | `W-56` · issue [#76](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/76) |
| **Kind** | Infra (Security) |
| **Stream / track** | Stream H — Security, operations & go-to-market · Track S |
| **Wave** | Wave 3 — Identity and tenancy |
| **Size / skill** | S · SEC |
| **Owner** | |
| **Blocked by** | `W-51` ([#71](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/71)) — Networking & Identity |
| **Blocks** | `W-64` (Penetration test) · Production cutover |
| **Capabilities** | `PLAT-09` (Security hardening; zero plaintext credentials in code or repository) |
| **Decisions** | `D-10` (Azure Container Apps) · `D-18` (India region) · `D-53` (Key Vault network ACLs & bypass) · `D-48` (Unified backend image) · `D-49` (Frontend non-root nginx) |
| **Gaps addressed** | `DEBT-004` (Secrets hardcoded in `.properties` — **closed for new code**; see §6a) · `DEBT-033` (Hardcoded integration MD5 key — **retired**) |
| **Status** | **Approved — dev** · revision 4 |
| **Review history** | rev1 `.claude/outputs/2026-09-19-review-spec-W-56.md` · rev2 `…-rev2.md` (B-6, B-7) · rev3 `…-rev3.md` (F-1 to F-4). Revision 4 trims the checks judged unnecessary for one-time setup — see §5b. |
| **Approved by** | sanjib (founder) |
| **Approved on** | 2026-09-19 |

> **Hard rule 1:** No code is written until this spec is approved by the founder.

---

## 1. Problem

Sensitive credentials and connection strings remain at risk across the platform:

1. **Committed Secrets in Git History (`DEBT-004`, `legacy/docs/GAP_INVENTORY.md:42`):** Legacy backends contain hardcoded credentials in committed configuration files — Keycloak client secret, Cloudinary keys, Brevo API key, `fed.secret`, and database passwords. Any repository clone exposes production and test systems.
2. **Missing Container Secret Wiring:** While `W-50` created Key Vault (`kv-infinevo-shared`) and `W-51` assigned `Key Vault Secrets User` RBAC roles, `infra/azure/modules/containerapps.bicep:105,160,208,267` currently deploys starter images (`mcr.microsoft.com/k8se/quickstart:latest`) with no `configuration.secrets` block and no `env` block referencing Key Vault.
3. **Absence of a Zero-Downtime Secret Rotation Runbook:** If a database password or API token is leaked or expired, operators have no tested, documented, or automated procedure to rotate the secret in Key Vault and propagate the change to running Container Apps without breaking active connection pools (HikariCP) or causing downtime.
4. **Live Keycloak Admin Exposure (`09-build-order.md:279`):** The legacy Keycloak administrative password was committed to the repository with a trivial password (`local_dev_pw`). This is a live operational exposure that must be resolved prior to ticket implementation.

### Baseline — measured from repository code on `main` before W-56

> **Note on environment state (`.claude/work/active-work.md:10,19`, `.claude/outputs/2026-09-19-verify-W-51.md:6`):**
> No live Azure environment has been deployed to date (`rg-infinevo-dev` does not exist). The baseline below reflects the actual code state on `main` before `W-56`.

| Command | Exit | Output |
|---|---|---|
| `grep -rn "DB_PASSWORD" code/backend/app/src/main/resources/application.yml` | 0 | `password: ${DB_PASSWORD}` — requires env var; no Key Vault container binding exists in Bicep |
| `grep -rn "local_app_pw" code/backend/app/src/main/resources/application-local.yml` | 0 | `password: ${DB_PASSWORD:local_app_pw}` — committed local fallback |
| `grep -A 10 "resource appContainerApp" infra/azure/modules/containerapps.bicep` | 0 | Starter image deployed; no `configuration.secrets` and no `env` block present |
| `grep -rn "CREATE ROLE" infra/postgres/01-roles.sql` | 0 | Only `app_user`, `migration_user`, `readonly_user`, `keycloak_user` created; no alternating roles or `worker_user` |
| `grep -rn "psql-app-pw" infra/azure/deploy.sh` | 0 | Seeds legacy single `psql-app-pw` only |
| `az containerapp show -g rg-infinevo-dev -n ca-infinevo-dev-app` | 3 | `ResourceNotFound` *(environment not deployed; .claude/work/active-work.md:19)* |
| `az keyvault show --name kv-infinevo-shared` | 3 | `ResourceNotFound` *(environment not deployed; .claude/work/active-work.md:19)* |

---

## 1b. Prerequisites & Environment Setup

### 1. Mandatory Pre-Implementation Prerequisite (Live Keycloak Admin Rotation)
Per `09-build-order.md:279` (*"Watch: rotate the current Keycloak administrative password before this, not as part of it. That is a live exposure"*):
- **Action:** The founder/operator must log into the live legacy Keycloak administrative console (`authentication.infinevocloud.com`) and change the administrator password before any implementation code on `W-56` is merged.
- **Boundary:** This is an operational prerequisite executed on the live legacy server, **not** a code deliverable or Done When item of `W-56`.

### 2. Azure Identity & Environment Prerequisites
- **Target Azure Subscription & Tenant**:
  - `AZURE_SUBSCRIPTION_ID`: Target Azure Subscription ID.
  - `AZURE_TENANT_ID`: Azure Active Directory (Entra ID) Tenant ID.
  - Location: `centralindia` (`D-18`).
- **Key Vault Authorisation**:
  - `kv-infinevo-shared` operates in Azure RBAC authorization mode.
  - Operator running rotation requires `Key Vault Secrets Officer` role.
  - Container and migration identities require `Key Vault Secrets User` role (provisioned in `W-51`).

---

## 2. Scope

### In Scope

1. **Key Vault Canonical Secret Inventory & Password Ownership:**
   - Formalise and populate the complete platform secret catalog in `kv-infinevo-shared`:
     - `psql-admin-pw`: PostgreSQL Flexible Server administrator password (`infinevo_admin`). Owned by `deploy.sh`.
     - `psql-app-pw-a`: PostgreSQL password for alternating role `app_user_a`. Seeded by `deploy.sh`, rotated by `rotate-secrets.sh`.
     - `psql-app-pw-b`: PostgreSQL password for alternating role `app_user_b`. Seeded by `deploy.sh`, rotated by `rotate-secrets.sh`.
     - `psql-app-active-role`: Name of currently active application role (`app_user_a` or `app_user_b`). Configuration secret read by `rotate-secrets.sh` and deploy scripts.
     - `psql-worker-pw`: PostgreSQL password for `worker_user`. Seeded by `deploy.sh`, rotated by `rotate-secrets.sh`.
     - `psql-migration-pw`: PostgreSQL password for `migration_user`. Owned by `deploy.sh`.
     - `psql-readonly-pw`: PostgreSQL password for `readonly_user`. Owned by `deploy.sh`.
     - `psql-keycloak-pw`: PostgreSQL password for Keycloak database user (`keycloak_user`). Owned by `deploy.sh`.
     - `keycloak-admin-pw`: Keycloak target-state bootstrap administrative password. Seeded by `deploy.sh`.
     - `keycloak-client-secret`: Keycloak OAuth2 client secret for `infinevo-platform`. Seeded by `deploy.sh`, rotated by `rotate-secrets.sh`.
     - `brevo-api-key`: Placeholder secret for Brevo transactional email delivery (`CORE-12`). Consumer arrives in `W-20`.
     - `jwt-signing-secret`: Placeholder secret used for signing internal tokens and session cookies. Consumer arrives in `W-57`.
   - **Retirement of `psql-app-pw`:** The legacy unversioned secret `psql-app-pw` is formally retired and replaced by `psql-app-pw-a` and `psql-app-pw-b`.
2. **Database Role Provisioning (`infra/postgres/01-roles.sql`):**
   - Update `01-roles.sql` to provision dual alternating roles `app_user_a` and `app_user_b` inheriting from group role `app_user`, and dedicated role `worker_user` inheriting from `app_user`.
   - `app_user` becomes a `NOLOGIN` group role, so **every site that logs in as it moves with this ticket** — the local stack, the app's local profile, and the Azure post-deploy check. §3e lists all four.
   - The local stack's own role assertions (`infra/docker/smoke.sh`) are rewritten to cover the three new roles and to prove DDL refusal from the privilege error rather than the exit code.
3. **Container Apps Secret Reference Integration (All 4 Containers):**
   - Update `infra/azure/modules/containerapps.bicep` to define Key Vault secret references in `configuration.secrets` using User-Assigned Managed Identity (`identities.<role>.id`):
     - `ca-infinevo-{env}-app`: references `psql-app-pw-a` (or active role password), `keycloak-client-secret`, `jwt-signing-secret`, `brevo-api-key`.
     - `ca-infinevo-{env}-worker`: references `psql-worker-pw`, `brevo-api-key`.
     - `ca-infinevo-{env}-keycloak`: references `psql-keycloak-pw`, `keycloak-admin-pw`.
     - `ca-infinevo-{env}-web`: **Zero secrets.** Frontend SPA (`nginx-unprivileged:alpine` on port 8080 per `D-49`) executes in client browsers and must never receive or hold backend secrets. Its runtime configuration is public and injected into `env.js` at container start. `id-web-{env}` requires `AcrPull` on `crinfinevo` for image pulling, but has zero entries in `configuration.secrets` and requires no `Key Vault Secrets User` role.
   - Map secret references to container environment variables (`DB_PASSWORD`, `KEYCLOAK_CLIENT_SECRET`, etc.).
4. **Dual-Role PostgreSQL Zero-Downtime Rotation Architecture (`rotate-secrets.sh`):**
   - Provide an idempotent bash script `infra/azure/rotate-secrets.sh` that automates zero-downtime rotation for:
     - PostgreSQL application credentials (rotates dormant role, updates Key Vault, rolls Container App revision, drains old connections, scrambles old role).
     - Keycloak client secret (dual-secret acceptance window during revision rollout).
     - Third-party API keys (Brevo).
   - Under `D-53` (`defaultAction: 'Deny'`), `rotate-secrets.sh` automatically adds its own `/32` egress IP rule to Key Vault and revokes it on every catchable exit path (matching `deploy.sh:95-133`).
5. **Secret Rotation Runbook (`infra/azure/docs/SECRET_ROTATION.md`):**
   - Step-by-step operational runbook detailing emergency rotation, planned quarterly rotation, dual-role management, and rollback steps.
6. **Repository-Wide Secret Elimination & Multi-Path Sweep:**
   - Scan across all repository directories (`code/backend/`, `code/frontend/src/`, `infra/azure/parameters/`, `.github/workflows/`, and root `.env*`) ensuring zero live credentials exist in committed files.

### Out of Scope

| Not here | Belongs to |
|---|---|
| Key Vault creation and RBAC role assignment | `W-50`, `W-51` |
| Virtual network private endpoint for Key Vault | `W-51` |
| Cloudinary keys migration | `DEBT-011` / `W-05` (Azure Blob Storage replaces Cloudinary) |
| In-application Spring Security JWT filter enforcement | `W-57` |
| Application-level Brevo email delivery integration | `W-20` |
| Dynamic database credential leasing via HashiCorp Vault | Out of scope / over-engineering for `D-19` scale |
| Database schema modifications, tables, or Flyway migrations | Product feature tickets (`W-06+`) |
| Rotating the 17 credentials committed in `legacy/` | Operator work — see §6a |

---

## 3. What Gets Built

### 3a. Secret Resolution Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ Azure Key Vault: kv-infinevo-shared                         │
│ • psql-app-pw-a / psql-app-pw-b                             │
│ • psql-worker-pw                                            │
│ • psql-keycloak-pw                                          │
│ • keycloak-client-secret                                    │
│ • brevo-api-key (placeholder)                               │
│ • jwt-signing-secret (placeholder)                          │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               │ Azure Managed Identity (UAMI)
                               │ RBAC: Key Vault Secrets User
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ Azure Container Apps Managed Environment: cae-infinevo-{env} │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ca-infinevo-{env}-app                                   │ │
│ │ configuration.secrets:                                  │ │
│ │   - name: db-pw, keyVaultUrl: .../psql-app-pw-a         │ │
│ │   - name: kc-secret, keyVaultUrl: .../keycloak-secret   │ │
│ │ env:                                                    │ │
│ │   - DB_PASSWORD: secretRef(db-pw)                       │ │
│ │   - KEYCLOAK_CLIENT_SECRET: secretRef(kc-secret)        │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ca-infinevo-{env}-worker                                │ │
│ │ configuration.secrets:                                  │ │
│ │   - name: worker-db-pw, keyVaultUrl: .../psql-worker-pw │ │
│ │ env:                                                    │ │
│ │   - DB_PASSWORD: secretRef(worker-db-pw)                │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ca-infinevo-{env}-keycloak                              │ │
│ │ configuration.secrets:                                  │ │
│ │   - name: kc-db-pw, keyVaultUrl: .../psql-keycloak-pw   │ │
│ │   - name: kc-admin-pw, keyVaultUrl: .../keycloak-admin  │ │
│ │ env:                                                    │ │
│ │   - KC_DB_PASSWORD: secretRef(kc-db-pw)                 │ │
│ │   - KEYCLOAK_ADMIN_PASSWORD: secretRef(kc-admin-pw)     │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ca-infinevo-{env}-web (Frontend SPA)                    │ │
│ │ configuration.secrets: [] (Zero Secrets)                │ │
│ │ env:                                                    │ │
│ │   - VITE_API_URL, VITE_KEYCLOAK_URL (Public config only)│ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 3b. File Inventory

| File | Change | Why |
|---|---|---|
| `infra/postgres/01-roles.sql` | Modified | Updates role creation to provision dual alternating roles `app_user_a` and `app_user_b` (granting `app_user` to both) and `worker_user` (granting `app_user`). |
| `infra/azure/deploy.sh` | Modified | Replaces retired `psql-app-pw` with `psql-app-pw-a`, `psql-app-pw-b`, `psql-app-active-role`, `psql-worker-pw`, `keycloak-admin-pw`, and placeholders. |
| `infra/azure/post-deploy-db.sh` | Modified | Passes `APP_PW_A`, `APP_PW_B`, `WORKER_PW` to `provision.sh` during database bootstrap. Check 6b at `:136-151` logs in as `app_user`, which becomes `NOLOGIN` — it moves to the active role from `psql-app-active-role`. Its DDL test at `:145-151` also infers refusal from a nonzero `psql` exit, the same defect as `smoke.sh:70-78`; it must match `permission denied for schema core` instead. |
| `infra/postgres/provision.sh` | Modified | `:13-16` and `:25-30` are the only site that passes role passwords into `01-roles.sql`; without `APP_PW_A`, `APP_PW_B` and `WORKER_PW` here, the new `:'app_pw_a'`, `:'app_pw_b'` and `:'worker_pw'` variables have no source and `01-roles.sql` fails under `ON_ERROR_STOP=1`. |
| `infra/postgres/03-grants.sql` | Modified | `:49` restricts the security self-check to the four original roles. The three new roles must join that list, or the block asserts nothing about them. |
| `infra/docker/migration-runner-entrypoint.sh` | Modified | `:51` reads the retired `psql-app-pw`. Reads `psql-app-pw-a`, `psql-app-pw-b` and `psql-worker-pw` instead; the guard loop at `:60` gains the new variable names. |
| `infra/docker/compose.yml` | Modified | Two changes. `:149-150` and `:178-179` connect `app` and `worker` as `app_user` / `local_app_pw` — they become `app_user_a` and `worker_user`. `:27-33` is where the postgres service passes `APP_PW`, `MIGRATION_PW`, `READONLY_PW`, `KEYCLOAK_PW` to the bootstrap, and it gains `APP_PW_A`, `APP_PW_B` and `WORKER_PW`; without them `provision.sh` has nothing to pass. |
| `infra/docker/smoke.sh` | Modified | `:49` loops the role assertions over four roles and `:70-78` proves DDL refusal from a nonzero `psql` exit. Both are rewritten — see §5b. |
| `code/backend/app/src/main/resources/application-local.yml` | Modified | `:13-14` default to `app_user` / `local_app_pw`. Defaults follow compose. |
| `infra/azure/modules/containerapps.bicep` | Modified | Wires `configuration.secrets` to Key Vault URLs using container UAMIs, injecting `DB_PASSWORD`, `KEYCLOAK_CLIENT_SECRET`, etc. into container `env`. Explicitly sets `secrets: []` on `web`. |
| `infra/azure/rotate-secrets.sh` | NEW | Idempotent CLI script to execute zero-downtime dual-role rotation for Postgres, Keycloak client secrets, and third-party API keys, including Key Vault transient IP rule handling under `D-53`. |
| `infra/azure/docs/SECRET_ROTATION.md` | NEW | Operator runbook documenting planned and emergency secret rotation procedures. |
| `code/backend/app/src/main/resources/application.yml` | Modified | Maps `${KEYCLOAK_CLIENT_SECRET}`, `${JWT_SIGNING_SECRET}`, `${BREVO_API_KEY}` into Spring configuration with zero defaults. |
| `code/backend/worker/src/main/resources/application.yml` | Modified | Maps `${DB_PASSWORD}` and worker-specific secrets. |

### 3c. What is NOT Touched

- Application domain and business logic (`code/backend/core/`, `code/backend/hrms/`, `code/backend/payroll/`).
- Database schema structure, tables, and Flyway migrations (this ticket modifies `01-roles.sql` for role grants only; zero tables, columns, or schemas are created).
- Frontend codebase (`code/frontend/`).
- Frozen legacy code (`legacy/`).

---

### 3d. Architectural Pattern: Service Containers vs Ephemeral Jobs

The repository intentionally uses two distinct secret delivery mechanisms:

1. **Long-Running Service Containers (`app`, `worker`, `keycloak`, `web`):**
   - **Mechanism:** Bicep `configuration.secrets` with `keyVaultUrl` + container `env` `secretRef`.
   - **Why:** Azure Container Apps natively manages secret lifecycle, identity authentication via UAMI, and masks secret values in logs and deployment history. Revisions restart cleanly with new secret versions.
2. **Ephemeral Batch Migration Job (`caj-db-migration-{env}`):**
   - **Mechanism:** Direct Azure CLI / SDK fetch at container startup (`migration-runner-entrypoint.sh:50-54`).
   - **Why:** The migration job runs once per deployment before service containers start. It requires multiple dynamic credentials (`infinevo_admin`, role passwords) to bootstrap the database and private endpoints before service containers come online.

---

### 3e. Database Changes (Roles Provisioning)

Per `TEMPLATE-INFRA.md:16-17`, because this ticket provisions database roles:

```sql
-- infra/postgres/01-roles.sql modifications. The script is idempotent and runs against
-- existing databases, so every attribute is set in BOTH branches. The current ELSE branch
-- at 01-roles.sql:15 omits LOGIN, which would leave app_user able to log in on every
-- volume that already exists.

-- 1. Base group role (holds permissions and RLS policies, NOLOGIN)
IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
    CREATE ROLE app_user NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
ELSE
    ALTER ROLE app_user WITH NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
END IF;
-- and the password statement at 01-roles.sql:38 is removed: a NOLOGIN role holding a
-- password is a credential nothing can use.

-- 2. Dual alternating login roles for zero-downtime rotation
CREATE ROLE app_user_a WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'app_pw_a';
CREATE ROLE app_user_b WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'app_pw_b';
GRANT app_user TO app_user_a, app_user_b;

-- 3. Dedicated worker role
CREATE ROLE worker_user WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD :'worker_pw';
GRANT app_user TO worker_user;
```

**`app_user` stops being a login role, and four places still log in as it.** It keeps every
grant in `03-grants.sql:15,23,28-38` and every row-level security policy, which members
inherit; what it loses is `LOGIN`. Each site below must move to a role that can still connect,
and each is listed in §3b:

| Site | Today | After |
|---|---|---|
| `infra/docker/compose.yml:149` | `DB_USERNAME: app_user` | `app_user_a` |
| `infra/docker/compose.yml:178` | `DB_USERNAME: app_user` | `worker_user` |
| `code/backend/app/src/main/resources/application-local.yml:13` | `${DB_USERNAME:app_user}` | `${DB_USERNAME:app_user_a}` |
| `infra/azure/post-deploy-db.sh:138` | `psql -U app_user` | the role named by `psql-app-active-role` |

The group role also needs its own password statement removed: `01-roles.sql:38` runs
`ALTER ROLE app_user WITH PASSWORD :'app_pw'`, and a `NOLOGIN` role holding a password is
a credential nothing uses.

---

### 3f. Detailed Zero-Downtime Secret Rotation Mechanics

#### 1. PostgreSQL Role Password Rotation (Dual Alternating Roles)
- **Problem:** PostgreSQL Flexible Server does **not** support multiple passwords for a single database role. Changing a password via `ALTER ROLE app_user WITH PASSWORD 'new'` immediately invalidates existing connection pools (HikariCP) and causes Container Apps blue/green revision rollovers to fail.
- **Dual-Role Solution in `rotate-secrets.sh`:**
  1. **Identify Dormant Role:** Query Key Vault secret `psql-app-active-role` (e.g. `app_user_a`). The dormant role is `app_user_b`.
  2. **Transient Key Vault IP Rule (`D-53`):** Add runner egress IP to Key Vault firewall rule; wait 10s for propagation.
  3. **Set Password on Dormant Role:** Connect as admin (`psql-admin-pw`) and run:
     `ALTER ROLE app_user_b WITH PASSWORD '<new-secure-password>';`
  4. **Update Key Vault:** Store the new password in Key Vault as `psql-app-pw-b` and set `psql-app-active-role` = `app_user_b`.
  5. **Deploy New Revision:** Deploy new Container Apps revision for `ca-infinevo-{env}-app` with `DB_USERNAME=app_user_b` and secret reference pointing to `psql-app-pw-b`.
  6. **Warm-up & Health Check:** New revision warms up and passes `/health` probes. The old revision continues processing traffic using `app_user_a`.
  7. **Traffic Switch:** Ingress shifts 100% traffic to the new revision.
  8. **Connection Draining:** Old revision drains active connections (graceful shutdown: 30s) and terminates.
  9. **Scramble Dormant Role:** Scramble `app_user_a`'s password in Postgres:
     `ALTER ROLE app_user_a WITH PASSWORD '<scrambled-random-string>';`
  10. **Cleanup:** Revoke transient Key Vault network rule.

#### 2. Keycloak Client Secret Rotation
- **Zero-Downtime Sequence:**
  1. Keycloak supports two active client secrets concurrently (primary and secondary) during rotation.
  2. Generate a secondary client secret in Keycloak via Admin API.
  3. Store the new secret in Key Vault as `keycloak-client-secret`.
  4. Deploy new Container Apps revision for `app`.
  5. Verify authentication token exchanges succeed on the new revision.
  6. In Keycloak, promote secondary secret to primary and delete the old secret.

#### 3. Third-Party API Keys (Brevo)
- **Zero-Downtime Sequence:**
  1. Generate a new API key in the Brevo dashboard (Brevo supports multiple active API keys).
  2. Update Key Vault secret `brevo-api-key`.
  3. Deploy new Container Apps revision for `app` and `worker`.
  4. Verify transactional email delivery (`CORE-12`).
  5. Revoke the old API key in the Brevo dashboard.

---

### 3g. Keycloak Administrative Password Lifecycle & Management

| Lifecycle Stage | Environment / Scope | Mechanism & Responsibility |
|---|---|---|
| **Live Legacy Exposure** | Live legacy Keycloak server (`authentication.infinevocloud.com`) | **Prerequisite (Pre-Implementation Gate):** The founder/operator must manually log into the legacy Keycloak admin console and change the admin password from the trivial committed password (`local_dev_pw`). This is a prerequisite to starting `W-56`, not a deliverable inside it (§1b). |
| **Target-State Initial Bootstrap** | Container Apps `ca-infinevo-{env}-keycloak` | On initial database creation, Keycloak reads `KEYCLOAK_ADMIN` and `KEYCLOAK_ADMIN_PASSWORD` from Key Vault (`keycloak-admin-pw`) to create the `master` realm admin user. |
| **Target-State Ongoing Rotation** | Running container in `cae-infinevo-{env}` | **Critical Behavior:** Keycloak only evaluates `KEYCLOAK_ADMIN_PASSWORD` when the database is empty. Changing this environment variable on an existing container does **not** update the database password. Ongoing rotation must be executed via `kcadm.sh` CLI: `kcadm.sh set-password -r master --username admin --new-password "<new-pw>"`, followed by updating Key Vault `keycloak-admin-pw` so records match. |

---

### 3h. Implementer Tasks

One area per task — Phase 2 spawns one **implementer** each and none may cross areas.

| # | Area | Files | Depends on |
|---|---|---|---|
| T1 | `infra/` — Postgres provisioning | `infra/postgres/01-roles.sql`, `infra/postgres/provision.sh`, `infra/postgres/03-grants.sql` | — |
| T2 | `infra/` — Azure | `infra/azure/modules/containerapps.bicep`, `infra/azure/deploy.sh`, `infra/azure/post-deploy-db.sh`, `infra/azure/rotate-secrets.sh` (new), `infra/azure/docs/SECRET_ROTATION.md` (new) | T1 — role names and the active-role secret |
| T3 | `infra/` — local stack | `infra/docker/compose.yml`, `infra/docker/migration-runner-entrypoint.sh`, `infra/docker/smoke.sh` | T1 — the roles must exist before smoke.sh can assert them |
| T4a | `code/backend/app` | `application.yml`, `application-local.yml` | T3 — the compose defaults it mirrors |
| T4b | `code/backend/worker` | `application.yml` | T3 |

T1, T2 and T3 are all under `infra/` and could run as one task; they are split because
each is independently verifiable and T3 is where §5a check 5 is proved. T4 is split in two
because an implementer works inside one module.

### 3i. Standing Rules

| Rule | Impact |
|---|---|
| `tenant_id` on every table outside `reference`, plus an RLS policy (`02-data-model.md:15-16`) | **Creates no table, column or schema.** The only database objects are roles. Nothing to scope |
| Flyway for every schema change; never `ddl-auto` (`D-46`) | **No migration script.** Roles are provisioned by `infra/postgres/01-roles.sql`, which is not a Flyway migration and never has been — `W-05` set that boundary |
| RLS remains a real boundary | The three new roles inherit from `app_user` and are `NOBYPASSRLS`. `core/V001__tenant.sql:26` has no `TO` clause, so its policy applies to every non-owner role including them |
| `Money`/`BigDecimal` precision | No monetary value is touched |
| Index on `tenant_id` (`DEBT-018`) | No index changes |
| Expand / contract, no destructive step | **This is the one standing rule the ticket breaks, deliberately.** `app_user` keeps its grants and its members; only `LOGIN` is removed, and the previous release cannot run against it. All four login sites move inside this ticket (§3e), and nothing is in production (`.claude/work/active-work.md:14`), so the window the rule protects does not exist yet |
| Maven dependency edges | None added. No `pom.xml` changes |

---

## 4. Proving It (Deliberate Breaks)

| # | Deliberate Break | Test Command | What Must Happen |
|---|---|---|---|
| 1 | Remove `Key Vault Secrets User` role from app UAMI | Revoke role on `kv-infinevo-shared`; deploy revision | Container App fails to start / revision provisioning fails with secret resolution error. |
| 2 | Point secret reference to non-existent Key Vault secret | Set secret URL to `.../secrets/fake-secret` in Bicep | Deployment fails fast with Key Vault secret resolution error. |
| 3 | Commit plaintext password in `application.yml` or `.bicepparam` | Introduce dummy secret string in `infra/azure/parameters/dev.bicepparam`; run CI | CI secret scanning / git hook fails with blocking error (`DEBT-004`). |
| 4 | Corrupt dormant role password prior to rotation | Temporarily scramble dormant password in DB out of sync with Key Vault; run `rotate-secrets.sh` pre-check | Rotation script preflight authentication test fails fast before updating Key Vault or triggering revision rollout, proving zero-downtime safety guardrail. |
| 5 | Give `app_user` its `LOGIN` attribute back | `ALTER ROLE app_user WITH LOGIN;` then §5b check 1 | `FAIL: app_user can still log in`. Proves the check reads the catalogue rather than inferring from a failed connection. |
| 6 | Leave `app_user`'s password statement in `01-roles.sql` | Keep `ALTER ROLE app_user WITH PASSWORD :'app_pw';`; run §5b check 1 | `FAIL: app_user still holds a password nothing can use`. |
| 7 | Drop `app_user_b` from `01-roles.sql` | Remove its `CREATE ROLE`; run §5b check 2 | `FAIL: role app_user_b missing or holds a forbidden attribute`. The dormant role becomes production after one rotation, so its absence must fail now, not then. |
| 8 | Point one `app` secret at the wrong Key Vault name | Change a `keyVaultUrl` to `.../secrets/psql-readonly-pw`; run §5a check 2 | `FAIL: app secrets are [...], expected [...]`. Proves the set is asserted, not the count. |
| 9 | Give `keycloak`'s secrets the `web` identity | `identity: identities.web.id` on both; run §5a check 2 | `FAIL: keycloak has 2 secret(s) on the wrong vault or wrong identity`. `id-web` holds no Key Vault Secrets User, so this would 403 at deploy time while reading as non-null. |
| 10 | Delete the `web` container app from the module | Remove `webContainerApp`; run §5a check 2 | `FAIL: selector for web matched 0 resources, expected 1`. Proves `web: no secrets` is not satisfied by `web` being absent. |
| 11 | Revert `compose.yml:149` to `app_user` | Restore `DB_USERNAME: app_user`; run §5a check 5 | `FAIL: still connecting as the NOLOGIN group role`. Proves the four login sites are enforced, not just listed. |

---

## 5. Verification

Three sections, split by what each needs to run. §5a needs nothing but a checkout; §5b
needs the local stack up; §5c needs an Azure environment and is deferred. The split is
the point: a check that quietly needs something it does not have is a check that passes
for the wrong reason.

### 5a. Static — runs on a clean checkout, no services

```bash
#!/usr/bin/env bash
set -eo pipefail

echo "== 1. Bicep build and lint =="
az bicep build --file infra/azure/main.bicep
az bicep lint --file infra/azure/main.bicep
echo "PASS: Bicep build and lint clean"

echo "== 2. Rendered ARM: the right secrets, on the right app, under the right identity =="
ARM_JSON=$(az bicep build --file infra/azure/modules/containerapps.bicep --stdout)

# Expected secret NAMES per app, sorted, space separated. The set is asserted, not the
# count: four secrets all pointing at psql-readonly-pw would satisfy a count of four.
expect_app="brevo-api-key jwt-signing-secret keycloak-client-secret psql-app-pw-a"
expect_worker="brevo-api-key psql-worker-pw"
expect_keycloak="keycloak-admin-pw psql-keycloak-pw"
expect_web=""

for name in app worker keycloak web; do
  eval "want=\$expect_${name}"
  # The rendered name is an ARM expression - [format('ca-infinevo-{0}-app', ...)] - so the
  # selector matches the literal suffix inside it.
  sel=".resources[] | select(.type==\"Microsoft.App/containerApps\") | select(.name | test(\"-${name}'\"))"

  # 2.1 Exactly one resource matched. A selector that matches nothing returns 0 from every
  # length below, which is indistinguishable from "correct and empty" - so the match count
  # is asserted first, for all four apps including web.
  hits=$(printf '%s' "$ARM_JSON" | jq "[ $sel ] | length")
  [ "$hits" = "1" ] || { echo "FAIL: selector for $name matched $hits resources, expected 1"; exit 1; }

  # 2.2 The raw secret count first. capture() DROPS an element whose URL does not match -
  # a URL rendered as {vaultUri}secrets/name has no leading slash, vanishes silently, and
  # would leave web reading as "no secrets" while holding some.
  declared=$(printf '%s' "$ARM_JSON" | jq "[ $sel | (.properties.configuration.secrets // [])[] ] | length")
  n_want=$(printf '%s' "$want" | wc -w)
  [ "$declared" = "$n_want" ] || { echo "FAIL: $name declares $declared secrets, expected $n_want"; exit 1; }

  # 2.3 The secret name set is exactly the expected one. No leading slash in the pattern,
  # and the capture count must equal the declared count so nothing is dropped unseen.
  got=$(printf '%s' "$ARM_JSON" | jq -r "[ $sel | (.properties.configuration.secrets // [])[]
          | .keyVaultUrl | capture(\"secrets/(?<n>[a-z0-9-]+)\").n ] | sort | join(\" \")")
  n_got=$(printf '%s' "$got" | wc -w)
  [ "$n_got" = "$declared" ] || { echo "FAIL: $name has $((declared - n_got)) secret URL(s) in an unreadable form"; exit 1; }
  [ "$got" = "$want" ] || { echo "FAIL: $name secrets are [$got], expected [$want]"; exit 1; }
  if [ -z "$want" ]; then echo "PASS: $name declares no secrets"; continue; fi

  # 2.4 Every secret resolves from the platform vault under this app's own identity.
  # W-51 gives only that identity Key Vault Secrets User; another app's identity renders as
  # a non-null string and would pass a null check while 403ing at deploy time.
  bad=$(printf '%s' "$ARM_JSON" | jq --arg n "$name" "[ $sel | .properties.configuration.secrets[]
          | select(((.keyVaultUrl // \"\") | contains(\"kv-infinevo-shared\") | not)
                   or ((.identity // \"\") | contains(\").\" + \$n + \".id\") | not)) ] | length")
  [ "$bad" = "0" ] || { echo "FAIL: $name has $bad secret(s) on the wrong vault or wrong identity"; exit 1; }

  # 2.5 Every env secretRef names a secret this app declares, and every declared secret is
  # consumed. Counting refs would pass four env vars pointing at one secret.
  orphan=$(printf '%s' "$ARM_JSON" | jq "[ $sel | .properties.configuration.secrets[].name ] as \$declared
          | [ $sel | .properties.template.containers[].env[]? | select(.secretRef != null)
              | select(.secretRef as \$r | \$declared | index(\$r) | not) ] | length")
  [ "$orphan" = "0" ] || { echo "FAIL: $name has $orphan env secretRef(s) naming an undeclared secret"; exit 1; }

  refs=$(printf '%s' "$ARM_JSON" | jq "[ $sel | .properties.template.containers[].env[]? | select(.secretRef != null) | .secretRef ] | unique | length")
  declared=$(printf '%s' "$ARM_JSON" | jq "[ $sel | .properties.configuration.secrets[].name ] | length")
  [ "$refs" = "$declared" ] || { echo "FAIL: $name declares $declared secrets but consumes $refs of them"; exit 1; }
  echo "PASS: $name - [$got], own identity, every secret consumed"
done

echo "== 3. Deliverables exist and the rotation script cleans up after itself =="
for f in infra/azure/rotate-secrets.sh infra/azure/docs/SECRET_ROTATION.md; do
  [ -s "$f" ] || { echo "FAIL: $f is missing or empty"; exit 1; }
done
bash -n infra/azure/rotate-secrets.sh
bash -n infra/azure/deploy.sh
bash -n infra/azure/post-deploy-db.sh
bash -n infra/postgres/provision.sh
# D-53: the transient /32 rule must be revoked on every catchable exit, as deploy.sh:95-133
# does. A trap is the only construct that survives an early exit, and the handler has to be
# the thing that revokes - a trap that calls nothing would satisfy a bare existence check.
grep -qE "^[[:space:]]*trap .* (EXIT|ERR)" infra/azure/rotate-secrets.sh \
  || { echo "FAIL: rotate-secrets.sh has no EXIT trap; a failed run leaves its IP rule on the vault"; exit 1; }
grep -q "network-rule remove" infra/azure/rotate-secrets.sh \
  || { echo "FAIL: rotate-secrets.sh never revokes its Key Vault IP rule"; exit 1; }
# --dry-run prints the sequence it would run; the real sequence cannot run offline.
DRY=$(bash infra/azure/rotate-secrets.sh --dry-run --target postgres --env dev)
for step in "dormant role" "key vault" "revision" "drain" "scramble"; do
  printf '%s' "$DRY" | grep -qi "$step" || { echo "FAIL: --dry-run never mentions '$step'"; exit 1; }
done
echo "PASS: deliverables present, EXIT trap revokes the rule, dry-run prints all five steps"

echo "== 4. deploy.sh seeds every canonical secret, and the retired one is gone =="
for s in psql-admin-pw psql-app-pw-a psql-app-pw-b psql-app-active-role psql-worker-pw \
         psql-migration-pw psql-readonly-pw psql-keycloak-pw keycloak-admin-pw \
         keycloak-client-secret brevo-api-key jwt-signing-secret; do
  grep -q -- "$s" infra/azure/deploy.sh || { echo "FAIL: deploy.sh never names $s"; exit 1; }
done
# psql-app-pw, not psql-app-pw-a. A word-boundary anchor does NOT work here: a hyphen is
# not a word character, so the anchored form also matches psql-app-pw-a, and the check
# could never pass.
retired=$(grep -rnE -- "psql-app-pw($|[^-])" infra/ code/ .github/ || true)
[ -z "$retired" ] || { echo "FAIL: retired psql-app-pw still read:"; echo "$retired"; exit 1; }
echo "PASS: 12 canonical secrets seeded, psql-app-pw fully retired"

echo "== 5. Every site that logged in as app_user has moved =="
# app_user becomes NOLOGIN. These are the sites §3e lists; a miss leaves a container that
# cannot start, which no other static check would see.
stale=$(grep -rn "DB_USERNAME: app_user$" infra/docker/compose.yml || true)
stale="$stale$(grep -rn "DB_USERNAME:app_user}" code/backend/app/src/main/resources/application-local.yml || true)"
stale="$stale$(grep -rn -- "-U app_user\b" infra/azure/post-deploy-db.sh || true)"
[ -z "$stale" ] || { echo "FAIL: still connecting as the NOLOGIN group role:"; echo "$stale"; exit 1; }
# The live DDL-refusal check must match the privilege error, not a nonzero exit - the same
# defect this ticket fixes in smoke.sh (spec review rev2, B-6).
grep -q "permission denied for schema core" infra/azure/post-deploy-db.sh \
  || { echo "FAIL: post-deploy-db.sh still infers DDL refusal from an exit code"; exit 1; }
echo "PASS: no site logs in as app_user; live DDL check matches the privilege error"

echo "== 6. Repository secret sweep =="
# grep exits 2 on a missing path and `|| true` makes that identical to "no matches", so the
# paths are counted first. A sweep that scans nothing must not report clean.
scan_paths="code/backend code/frontend/src infra/azure infra/docker infra/postgres .github"
scanned=0
for p in $scan_paths; do
  [ -d "$p" ] || { echo "FAIL: sweep path $p does not exist"; exit 1; }
  scanned=$((scanned + $(find "$p" -type f | wc -l)))
done
[ "$scanned" -gt 200 ] || { echo "FAIL: sweep saw only $scanned files; the paths are wrong"; exit 1; }

Q="'"
# Two shapes: `password: "x"` / `secret = "x"`, and the CLI form `--value "x"`, which is how
# rotate-secrets.sh will write to Key Vault.
SWEEP="((password|passwd|secret|api[-_]?key|client[-_]?secret)[\"$Q]?[[:space:]]*[:=]|--value|--password)[[:space:]]*[\"$Q][^\"$Q]+[\"$Q]"
# The exclusions apply to the VALUE, isolated by the sed, never to the whole line. Excluding
# by line lets a real credential through whenever it shares a line with a $VAR or the word
# local_ - and `--value "$VAULT" … --value "<real secret>"` is exactly that shape.
EXCL=':(local_|[$][{(]?[A-Za-z_])'
hits=$(grep -roniE "$SWEEP" $scan_paths --exclude-dir=test --exclude-dir=target \
         --include="*.yml" --include="*.yaml" --include="*.sh" --include="*.bicep" \
         --include="*.bicepparam" --include="*.ts" --include="*.tsx" --include="*.js" \
         --include="*.jsx" --include="*.json" --include="*.java" --include="*.sql" \
         --include="*.properties" --include="*.xml" \
       | grep -v ":$Q" \
       | sed -E "s/^(.*:[0-9]+:).*[\"$Q]([^\"$Q]+)[\"$Q]$/\1\2/" \
       | grep -vE "$EXCL" \
       | grep -viE ":(changeme|example|placeholder|<)" || true)
[ -z "$hits" ] || { echo "FAIL: plaintext credentials:"; echo "$hits"; exit 1; }

env_hits=$(find . -path ./legacy -prune -o -name ".env" -print -o -name ".env.*" ! -name ".env.example" -print \
           | xargs -r grep -HniE "(PASSWORD|SECRET|KEY)=.+" || true)
[ -z "$env_hits" ] || { echo "FAIL: live secrets in .env files:"; echo "$env_hits"; exit 1; }
echo "PASS: $scanned files scanned, zero committed plaintext credentials"
```

| Check | Expected |
|---|---|
| 1 Bicep build and lint | Exit 0, 0 violations |
| 2 ARM secret sets | `app` `[brevo-api-key jwt-signing-secret keycloak-client-secret psql-app-pw-a]`, `worker` `[brevo-api-key psql-worker-pw]`, `keycloak` `[keycloak-admin-pw psql-keycloak-pw]`, `web` `[]`; exactly one resource matched per app; every secret on `kv-infinevo-shared` under that app's own identity; declared secrets and `secretRef` names in exact correspondence |
| 3 Deliverables | Both files non-empty, four scripts parse, `trap … EXIT` present and revoking, dry-run names all five rotation steps |
| 4 Secret inventory | 12 names present in `deploy.sh`; zero occurrences of `psql-app-pw` |
| 5 Login sites moved | No `app_user` login remains; `post-deploy-db.sh` matches the privilege error |
| 6 Secret sweep | > 200 files scanned, zero plaintext credentials outside `local_*` fallbacks |

### 5b. Local stack — requires `docker compose -f infra/docker/compose.yml up -d`

Deliberately short. Whether a password works, and whether a role is refused DDL, is proved
by the stack starting and by the first deployment — not by a local negative test that can
only be a weaker copy of it. What is checked here is the part nothing else would notice:
that the group role really did lose `LOGIN`, and that the new roles carry the declared
attributes.

**This script is what `infra/docker/smoke.sh` must contain** — it replaces the role loop at
`smoke.sh:49` and the DDL check at `smoke.sh:70-78`, rather than living beside them as a
second copy. The verifier runs `smoke.sh`.

Run after `down -v`. `01-roles.sql` is idempotent and its `ELSE` branch must set `NOLOGIN`
(§3e), so a re-run against an existing volume is equivalent to a clean one; check 1 is what
proves that, and it is the check that fails if the `ELSE` branch is left as it is today.

```bash
#!/usr/bin/env bash
set -eo pipefail
C="docker compose -f infra/docker/compose.yml"

# 1. The group role cannot log in and holds no password of its own. Both read from the
# catalogue: inferring NOLOGIN from a failed connection is the defect this replaces.
$C exec -T postgres psql -tAU postgres \
  -c "select rolcanlogin from pg_roles where rolname='app_user'" | grep -qx f \
  || { echo "FAIL: app_user can still log in"; exit 1; }
$C exec -T postgres psql -tAU postgres \
  -c "select rolpassword is null from pg_authid where rolname='app_user'" | grep -qx t \
  || { echo "FAIL: app_user still holds a password nothing can use"; exit 1; }

# 2. Declared attributes, for the three new roles as well as the four original ones.
for r in app_user app_user_a app_user_b worker_user migration_user readonly_user keycloak_user; do
  $C exec -T postgres psql -tAU postgres \
    -c "select 1 from pg_roles where rolname='$r' and rolsuper=false and rolbypassrls=false and rolcreatedb=false and rolcreaterole=false" \
    | grep -q 1 || { echo "FAIL: role $r missing or holds a forbidden attribute"; exit 1; }
done

# 3. The stack came up on the new roles. This is the positive proof, and it is the real
# thing rather than a simulation of it: app connects as app_user_a and worker as
# worker_user, over TCP, with the passwords the stack issued. If any of that is wrong the
# container does not reach healthy. A local battery of negative tests would only be a
# worse copy of what starting the stack already does.
for svc in app worker; do
  $C ps --format '{{.Service}} {{.Health}}' | grep -qx "$svc healthy" \
    || { echo "FAIL: $svc is not healthy on its new role"; exit 1; }
done
echo "PASS: app and worker healthy on app_user_a and worker_user"
```

| Check | Expected |
|---|---|
| 1 Group role | `app_user` `rolcanlogin=f`, `rolpassword` null |
| 2 Attributes | All seven roles exist, none superuser, none `BYPASSRLS`, none `CREATEDB`/`CREATEROLE` |
| 3 Stack health | `app` and `worker` healthy — they connected as `app_user_a` and `worker_user` with their issued passwords |

### 5c. Live — DEFERRED to #129

> **Deferred:** The following checks require a deployed `dev` environment in `centralindia` and will run under #129 when cloud deployment is authorized:

```bash
# ── Live verification script (to be executed after Azure deployment #129) ──
# 1. Verify Key Vault secrets exist
for secret in "psql-admin-pw" "psql-app-pw-a" "psql-app-pw-b" "psql-worker-pw" "keycloak-admin-pw" "keycloak-client-secret"; do
  az keyvault secret show --vault-name "$VAULT_NAME" --name "$secret" --query "id" -o tsv
done

# 2. Check latest revision provisioning state and health (avoids false positive of top-level app status)
for app in "app" "worker" "keycloak"; do
  LATEST_REV=$(az containerapp show -g "$RG_ENV" -n "ca-infinevo-${ENV}-${app}" --query "properties.latestRevisionName" -o tsv)
  REV_STATE=$(az containerapp revision show -g "$RG_ENV" -n "ca-infinevo-${ENV}-${app}" --revision "$LATEST_REV" --query "properties.provisioningState" -o tsv)
  [ "$REV_STATE" = "Succeeded" ] || { echo "FAIL: Revision $LATEST_REV provisioningState is '$REV_STATE' (expected Succeeded)"; exit 1; }
done

# 3. Prove active process authentication using Key Vault secret (matching post-deploy-db.sh:137-151)
ACTIVE_ROLE=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "psql-app-active-role" --query "value" -o tsv)
ROLE_PW=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "psql-${ACTIVE_ROLE}-pw" --query "value" -o tsv)
PGPASSWORD="$ROLE_PW" psql -h "$PG_HOST" -U "$ACTIVE_ROLE" -d infinevo_dev -c "SELECT current_user;" | grep -q "$ACTIVE_ROLE"
echo "PASS: Process authenticated successfully with Key Vault secret value"
```

---

## 6. Gap Disposition

| Gap | Disposition |
|---|---|
| `DEBT-004` (Secrets hardcoded in `.properties`) | **Closed for new code, open for the frozen system.** Every credential the platform uses is in Key Vault and injected by managed identity; §5a check 6 proves none is committed under `code/`, `infra/` or `.github/`. It does **not** close the exposure: 17 live credentials remain in `legacy/` and in git history — see §6a. |
| `DEBT-033` (Hardcoded integration MD5 key) | **Retired:** The legacy HRMS↔Payroll HTTP integration is eliminated in target state (`D-23`), so `X-API-KEY: md5("12345AB")` is deleted and requires no Key Vault secret. |

### 6a. The frozen credentials this ticket cannot close

`legacy/` is frozen and never edited, and the values are in git history regardless, so no
code change removes them. **Only rotating each one at its provider makes the committed value
worthless.** That is operator work, not a deliverable here, and it does not wait for this
ticket.

| File | Credentials |
|---|---|
| `legacy/Payroll-Bend-SBoot/src/main/resources/application.properties` | Keycloak admin password and client secret (`:19,21`), Cloudinary key and secret (`:26-27`), Brevo API key (`:46`), `fed.secret` (`:67`) |
| `legacy/HRMS_Backend/src/main/resources/application.properties` | Database password (`:8`), mail password (`:32`), MailerSend key (`:36`), Brevo key (`:48`), Cloudinary key and secret (`:55-56`), API key (`:59`) |

§1b requires one of these — the Keycloak admin password — to be rotated before this ticket
merges, because it is the one the target state also uses. The rest are tracked as incident 1
in `.claude/work/active-work.md` and are listed here so the count is on the record.

---

## 7. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| **Live Keycloak Administrative Password Exposure:** The legacy Keycloak admin password was committed in git history with a trivial password (`09-build-order.md:279`). | High | **Pre-implementation prerequisite:** The founder/operator rotates the live Keycloak admin password immediately before starting code changes on this ticket (§1b). |
| **Database Connection Pool Disruption During Rotation:** Changing a PostgreSQL role password immediately breaks active connections and causes Container Apps blue/green overlaps to fail. | Medium | Use **Dual Alternating Roles** (`app_user_a` / `app_user_b`) as defined in §3f to ensure seamless zero-downtime rollover. |
| **Keycloak Password Runtime Ignored:** Operators updating `KEYCLOAK_ADMIN_PASSWORD` in Key Vault expect the container to change its database password automatically. | Medium | Documented explicitly in §3g: Keycloak only reads the admin password on initial DB bootstrap. Runtime updates must run through `kcadm.sh`. |
| **Key Vault IP Firewall Blocks Rotation Runner:** `rotate-secrets.sh` gets 403 against Key Vault default Deny action (`D-53`). | Low | `rotate-secrets.sh` includes transient `/32` IP rule creation and cleanup trap matching `deploy.sh`. |

---

## 8. Rollback

If secret resolution or rotation encounters issues:
1. **Secret Version Rollback:** Key Vault maintains version history for all secrets. Revert the Container App secret reference to the previous working version GUID.
2. **Dual-Role Rollback:** If a newly rotated role (e.g. `app_user_b`) fails, immediately route traffic back to the previous revision running on the proven role (`app_user_a`).
3. **Container Revision Rollback:** If a new revision fails to start due to secret resolution failure, Container Apps automatically leaves the previous healthy revision active (traffic remains at 100% on the old revision).
4. **Database Credential Emergency Fallback:** In the event of a database password desynchronization, use `psql-admin-pw` to reset the role password to match the active Key Vault secret version.
5. **Local stack rollback:** `docker compose -f infra/docker/compose.yml down -v` and re-run. The roles are provisioned from scratch, so a half-applied `01-roles.sql` leaves nothing behind.

---

## 9. Done When

Every item names the check that attempts it. An item with no runnable check does not belong
on this list.

| # | Done when | Proved by |
|---|---|---|
| 1 | `deploy.sh` seeds all twelve canonical secrets, and `psql-app-pw` appears nowhere in `infra/`, `code/` or `.github/` | §5a check 4 |
| 2 | `app`, `worker` and `keycloak` each declare exactly their expected secret set, on `kv-infinevo-shared`, under their own identity, with every declared secret consumed by exactly one `secretRef` | §5a check 2 |
| 3 | `web` exists in the template and declares no secrets | §5a check 2.1 and 2.2 |
| 4 | `01-roles.sql` provisions `app_user_a`, `app_user_b` and `worker_user`; `app_user` is `NOLOGIN` with no password, in both branches of the idempotent block | §5b checks 1 and 2 |
| 5 | The local stack comes up on the new roles — `app` healthy as `app_user_a`, `worker` healthy as `worker_user`. That is the proof the passwords and grants are right; a local negative test would be a weaker copy of it | §5b check 3 |
| 6 | `app_user_a`, `app_user_b` and `worker_user` exist with the declared attributes, and none is superuser or `BYPASSRLS` | §5b check 2 |
| 7 | No file logs in as `app_user`, and `post-deploy-db.sh` proves DDL refusal from the privilege error rather than an exit code | §5a check 5 |
| 8 | `rotate-secrets.sh` and `SECRET_ROTATION.md` exist, the script parses, revokes its Key Vault IP rule from an `EXIT` trap, and its `--dry-run` prints all five rotation steps | §5a check 3 |
| 9 | The sweep scans `code/backend`, `code/frontend/src`, `infra/azure`, `infra/docker`, `infra/postgres` and `.github` — over 200 files — and finds zero plaintext credentials outside `local_*` development fallbacks (`DEBT-004` closed for new code) | §5a check 6 |
| 10 | PR description contains `Closes #76` | Merge gate |

**Not proved before #129:** that a container actually resolves a secret from Key Vault and
starts, and that each env var receives the secret it names. Both fail loudly on the first
deployment rather than silently, which is why neither is simulated here. §5c is the check;
it needs an environment that does not exist yet.

---

## Decisions taken at approval

1. **Key Vault Secret Versioning Strategy in Container Apps — Option A.** Secret versions are
   pinned in Container Apps revisions (`.../secrets/psql-app-pw-a/<version-guid>`), for
   deterministic blue/green deployment and immediate revision rollback without touching Key
   Vault. `deploy.sh` resolves the current version at deploy time and passes it as a
   parameter, so the rendered ARM carries a parameter reference rather than a literal — which
   is why §5a check 2 asserts the secret name and vault, and the pinned version is asserted
   in §5c.
2. **Dual-Role Database Provisioning — Option A.** `app_user_a` and `app_user_b` are
   pre-provisioned in `01-roles.sql` inheriting from `app_user`, so `rotate-secrets.sh` needs
   only `ALTER ROLE` password rights and never `CREATE ROLE`.

Approved by the founder on 2026-09-19 for the `dev` environment. `/develop W-56` or
`/infra-task W-56` may now begin.
