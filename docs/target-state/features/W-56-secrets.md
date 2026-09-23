# W-56 — Secrets

> Key Vault Secret Integration, Managed Identity Secret Resolution, Windowed Secret Rotation, and Repository-Wide Secret Elimination.
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
| **Status** | **Approved — dev** · revision 5 |
| **Review history** | rev1 `.claude/outputs/2026-09-19-review-spec-W-56.md` · rev2 `…-rev2.md` (B-6, B-7) · rev3 `…-rev3.md` (F-1 to F-4). Revision 4 trimmed the checks judged unnecessary for one-time setup — see §5b. **Revision 5 cuts zero-downtime rotation — see §0.** |
| **Approved by** | sanjib (founder) |
| **Approved on** | 2026-09-19 (rev 4) · **2026-09-22 (rev 5)** |

> **Hard rule 1:** No code is written until this spec is approved by the founder.

---

## 0. Revision 5 — what was cut, and why

**Founder decision, 2026-09-22: build the basic fulfilment of the need.** Secrets in Key
Vault, containers reading them, nothing committed. Zero-downtime rotation is not a need at
`D-19` scale and was the largest single source of complexity in this ticket.

| Cut | Kept instead |
|---|---|
| `app_user_a` / `app_user_b` alternating login roles | One `app_user`, still `LOGIN`, exactly as `W-05` created it |
| `psql-app-active-role` Key Vault pointer | Nothing. There is no active role to track |
| `psql-app-pw-a` / `psql-app-pw-b` | `psql-app-pw`, which is **no longer retired** |
| Ten-step zero-downtime rotation | **Cut entirely on 2026-09-23** — no rotation script, no runbook. See §0a |
| The `app_user` → `NOLOGIN` change and the five login sites that moved with it | No change. `compose.yml`, `application-local.yml`, `post-deploy-db.sh` and `PostgresTestContainerInitializer` all stay on `app_user` |

**What this ticket still delivers, unchanged:** the Key Vault secret catalogue, Container
Apps reading those secrets by managed identity, `worker_user` as its own login, the
rotation runbook, and the repository-wide secret sweep that actually closes `DEBT-004`.

**Why the cut is safe.** Zero downtime during a password change matters when a rotation
would drop live customer traffic. There are no customers, no production environment and a
planned rotation is quarterly. A thirty-second window costs nothing and removes a piece of
distributed state — which role is live — that has to stay correct across Key Vault,
Postgres, Bicep and two shell scripts.

**Where a later section disagrees with this one, this section wins.** Revision 5 rewrites
§2, §3b, §3e, §3f, §3h, §3i and §5; earlier prose that still argues for the dual-role
scheme is superseded, not re-litigated.

**Cost of reversing.** `W-64` or a real production incident can reinstate it. The group
role, the `a`/`b` logins and the active-role pointer are additive — nothing in revision 5
blocks them later.

---

## 1. Problem

Sensitive credentials and connection strings remain at risk across the platform:

1. **Committed Secrets in Git History (`DEBT-004`, `legacy/docs/GAP_INVENTORY.md:42`):** Legacy backends contain hardcoded credentials in committed configuration files — Keycloak client secret, Cloudinary keys, Brevo API key, `fed.secret`, and database passwords. Any repository clone exposes production and test systems.
2. **Missing Container Secret Wiring:** While `W-50` created Key Vault (`kv-infinevo-shared`) and `W-51` assigned `Key Vault Secrets User` RBAC roles, `infra/azure/modules/containerapps.bicep:105,160,208,267` currently deploys starter images (`mcr.microsoft.com/k8se/quickstart:latest`) with no `configuration.secrets` block and no `env` block referencing Key Vault.
3. **Absence of a Secret Rotation Runbook:** If a database password or API token is leaked or expired, operators have no tested, documented or automated procedure to rotate the secret in Key Vault and propagate the change to running Container Apps.
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
     - `psql-app-pw`: PostgreSQL password for `app_user`. Seeded by `deploy.sh`. **Rotation is manual and undocumented** — see §0a.
     - `psql-worker-pw`: PostgreSQL password for `worker_user`. Seeded by `deploy.sh`. **Rotation is manual and undocumented** — see §0a.
     - `psql-migration-pw`: PostgreSQL password for `migration_user`. Owned by `deploy.sh`.
     - `psql-readonly-pw`: PostgreSQL password for `readonly_user`. Owned by `deploy.sh`.
     - `psql-keycloak-pw`: PostgreSQL password for Keycloak database user (`keycloak_user`). Owned by `deploy.sh`.
     - `keycloak-admin-pw`: Keycloak target-state bootstrap administrative password. Seeded by `deploy.sh`.
     - `keycloak-client-secret`: Keycloak OAuth2 client secret for `infinevo-platform`. Seeded by `deploy.sh`. **Rotation is manual and undocumented** — see §0a.
     - `brevo-api-key`: Placeholder secret for Brevo transactional email delivery (`CORE-12`). Consumer arrives in `W-20`.
     - `jwt-signing-secret`: Placeholder secret used for signing internal tokens and session cookies. Consumer arrives in `W-57`.
   - **Ten secrets. `psql-app-pw` is kept, not retired** (rev 5, §0).
2. **Database Role Provisioning (`infra/postgres/01-roles.sql`):**
   - Add one role: `worker_user`, `LOGIN`, `NOBYPASSRLS`, granted `app_user` so it inherits every grant and every row-level security policy without a second set of grants to maintain.
   - **`app_user` is not changed.** It keeps `LOGIN` and its password, so no site that connects as it has to move. This is the whole of the revision 5 cut.
   - The local stack's own role assertions (`infra/docker/smoke.sh`) gain `worker_user` and prove DDL refusal from the privilege error rather than the exit code — the `W-56` rev 2 finding B-6, which stands on its own merits.
3. **Container Apps Secret Reference Integration (All 4 Containers):**
   - Update `infra/azure/modules/containerapps.bicep` to define Key Vault secret references in `configuration.secrets` using User-Assigned Managed Identity (`identities.<role>.id`):
     - `ca-infinevo-{env}-app`: references `psql-app-pw`, `keycloak-client-secret`, `jwt-signing-secret`, `brevo-api-key`.
     - `ca-infinevo-{env}-worker`: references `psql-worker-pw`, `brevo-api-key`.
     - `ca-infinevo-{env}-keycloak`: references `psql-keycloak-pw`, `keycloak-admin-pw`.
     - `ca-infinevo-{env}-web`: **Zero secrets.** Frontend SPA (`nginx-unprivileged:alpine` on port 8080 per `D-49`) executes in client browsers and must never receive or hold backend secrets. Its runtime configuration is public and injected into `env.js` at container start. `id-web-{env}` requires `AcrPull` on `crinfinevo` for image pulling, but has zero entries in `configuration.secrets` and requires no `Key Vault Secrets User` role.
   - Map secret references to container environment variables (`DB_PASSWORD`, `KEYCLOAK_CLIENT_SECRET`, etc.).
4. **Repository-Wide Secret Elimination & Multi-Path Sweep:**
   - Scan across all repository directories (`code/backend/`, `code/frontend/src/`, `infra/azure/parameters/`, `.github/workflows/`, and root `.env*`) ensuring zero live credentials exist in committed files.

### Out of Scope

| Not here | Belongs to |
|---|---|
| Key Vault creation and RBAC role assignment | `W-50`, `W-51` |
| Virtual network private endpoint for Key Vault | `W-51` |
| Cloudinary keys migration | `DEBT-011` / `W-05` (Azure Blob Storage replaces Cloudinary) |
| In-application Spring Security JWT filter enforcement | `W-57` |
| Application-level Brevo email delivery integration | `W-20` |
| **Zero-downtime database password rotation** — dual alternating roles, an active-role pointer, connection draining | **Deferred (rev 5, §0).** Reinstate at `W-64` or on a real incident. Additive, so nothing here blocks it |
| Dynamic database credential leasing via HashiCorp Vault | Out of scope / over-engineering for `D-19` scale |
| Database schema modifications, tables, or Flyway migrations | Product feature tickets (`W-06+`) |
| Rotating the 17 credentials committed in `legacy/` | Operator work — see §6a |

---

## 3. What Gets Built

### 3a. Secret Resolution Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ Azure Key Vault: kv-infinevo-shared                         │
│ • psql-app-pw                                               │
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
│ │   - name: db-pw, keyVaultUrl: .../psql-app-pw           │ │
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
| `infra/postgres/01-roles.sql` | Modified | Adds `worker_user` (`LOGIN`, `NOBYPASSRLS`) and `GRANT app_user TO worker_user`. **`app_user` is untouched.** |
| `infra/azure/deploy.sh` | Modified | Seeds the ten-secret catalogue: keeps `psql-app-pw`, adds `psql-worker-pw`, `keycloak-admin-pw`, `keycloak-client-secret` and the two placeholders. |
| `infra/azure/post-deploy-db.sh` | Modified | Passes `WORKER_PW` to `provision.sh`. Check 6b at `:136-151` keeps logging in as `app_user`; its DDL test at `:145-151` infers refusal from a nonzero `psql` exit, the same defect as `smoke.sh:70-78`, and must match `permission denied for schema core` instead. |
| `infra/postgres/provision.sh` | Modified | `:13-16` and `:25-30` are the only site that passes role passwords into `01-roles.sql`; without `WORKER_PW` here the new `:'worker_pw'` variable has no source and `01-roles.sql` fails under `ON_ERROR_STOP=1`. |
| `infra/postgres/03-grants.sql` | Modified | `:49` restricts the security self-check to the four original roles. `worker_user` must join that list, or the block asserts nothing about it. |
| `infra/docker/migration-runner-entrypoint.sh` | Modified | `:51` reads `psql-app-pw` and keeps doing so; the guard loop at `:60` gains `psql-worker-pw`. |
| `infra/docker/compose.yml` | Modified | `:178-179` connects `worker` as `app_user` — it becomes `worker_user`. `app` at `:149-150` does not move. `:27-33` gains `WORKER_PW` alongside `APP_PW`, `MIGRATION_PW`, `READONLY_PW`, `KEYCLOAK_PW`; without it `provision.sh` has nothing to pass. |
| `infra/docker/smoke.sh` | Modified | `:49` loops the role assertions over four roles — `worker_user` joins them — and `:70-78` proves DDL refusal from a nonzero `psql` exit, which is rewritten. See §5b. |
| `code/backend/app/src/main/resources/application-local.yml` | **Unchanged** | `:13-14` default to `app_user` / `local_app_pw` and stay there. Listed so the revision-4 entry is not re-applied. |
| `code/backend/shared/.../PostgresTestContainerInitializer.java` | Modified | Gains `worker_pw` in its variable map. JDBC has no `:'name'` substitution, so every psql variable `01-roles.sql` uses must be declared here too — the rev-4 lesson, which still holds. |
| `infra/azure/modules/containerapps.bicep` | Modified | Wires `configuration.secrets` to Key Vault URLs using container UAMIs, injecting `DB_PASSWORD`, `KEYCLOAK_CLIENT_SECRET`, etc. into container `env`. Explicitly sets `secrets: []` on `web`. |
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
-- infra/postgres/01-roles.sql. One role is added. The script is idempotent and runs
-- against existing databases, so every attribute is set in BOTH branches.

IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'worker_user') THEN
    CREATE ROLE worker_user WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
ELSE
    ALTER ROLE worker_user WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
END IF;

GRANT app_user TO worker_user;

-- and, outside the DO block, alongside the existing password statements:
ALTER ROLE worker_user WITH PASSWORD :'worker_pw';
```

**`app_user` does not change.** It keeps `LOGIN`, its password and every grant in
`03-grants.sql:15,23,28-38`. `worker_user` is a member, so it inherits those grants and
every row-level security policy without a second set to maintain, and it is `NOBYPASSRLS`
so tenant isolation holds for it exactly as it does for `app_user`.

| Site | Today | After |
|---|---|---|
| `infra/docker/compose.yml:149` (`app`) | `DB_USERNAME: app_user` | **unchanged** |
| `infra/docker/compose.yml:178` (`worker`) | `DB_USERNAME: app_user` | `worker_user` |
| `code/backend/app/src/main/resources/application-local.yml:13` | `${DB_USERNAME:app_user}` | **unchanged** |
| `infra/azure/post-deploy-db.sh:138` | `psql -U app_user` | **unchanged** |
| `PostgresTestContainerInitializer:179` | `spring.datasource.username=app_user` | **unchanged** |

> **One lesson from revision 4 survives the cut.** `infra/postgres/` is run by **three**
> callers, not two: local Docker and Azure go through `psql`, and the test path goes
> through JDBC, which has no `:'name'` substitution.
> `PostgresTestContainerInitializer` expands the variables itself. Every psql variable this
> ticket adds — now just `worker_pw` — must be declared there, or Postgres answers
> `syntax error at or near ":"`, which names neither the variable nor the file.

**Why give the worker its own login at all, when it could share `app_user`?** Two reasons
that survive the trim: `pg_stat_activity` then says which side is running a query, and a
misbehaving worker can be cut off without stopping the API. Neither needs a second
application role to exist.

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
| T2 | `infra/` — Azure | `infra/azure/modules/containerapps.bicep`, `infra/azure/deploy.sh`, `infra/azure/post-deploy-db.sh`, | T1 — role names |
| T3 | `infra/` — local stack | `infra/docker/compose.yml`, `infra/docker/migration-runner-entrypoint.sh`, `infra/docker/smoke.sh` | T1 — the role must exist before smoke.sh can assert it |
| T4a | `code/backend/app` | `application.yml` | — |
| T4b | `code/backend/worker` | `application.yml` | T3 |
| T4c | `code/backend/shared` | `PostgresTestContainerInitializer.java` — declare `worker_pw` | T1 |

T1, T2 and T3 are all under `infra/` and could run as one task; they are split because
each is independently verifiable. T4 is split because an implementer works inside one
module.

**Revision 5 removes work that the branch already contains.** The existing commits carry
`app_user_a`, `app_user_b` and `psql-app-active-role` across `deploy.sh`,
`post-deploy-db.sh`, `compose.yml` and `smoke.sh`. Each task above
**reverts** those references as well as adding its own. The check in §5a (4) asserts the
result: zero occurrences of `app_user_a`, `app_user_b` or `psql-app-active-role` anywhere
outside this spec.

### 3i. Standing Rules

| Rule | Impact |
|---|---|
| `tenant_id` on every table outside `reference`, plus an RLS policy (`02-data-model.md:15-16`) | **Creates no table, column or schema.** The only database objects are roles. Nothing to scope |
| Flyway for every schema change; never `ddl-auto` (`D-46`) | **No migration script.** Roles are provisioned by `infra/postgres/01-roles.sql`, which is not a Flyway migration and never has been — `W-05` set that boundary |
| RLS remains a real boundary | `worker_user` inherits from `app_user` and is `NOBYPASSRLS`. `core/V001__tenant.sql:26` has no `TO` clause, so its policy applies to every non-owner role including it |
| `Money`/`BigDecimal` precision | No monetary value is touched |
| Index on `tenant_id` (`DEBT-018`) | No index changes |
| Expand / contract, no destructive step | **Now satisfied.** Revision 4 broke this rule by removing `LOGIN` from `app_user`; revision 5 does not touch `app_user` at all. `worker_user` is purely additive, so the previous release runs unchanged against the new roles |
| Maven dependency edges | None added. No `pom.xml` changes |

---

## 4. Proving It (Deliberate Breaks)

| # | Deliberate Break | Test Command | What Must Happen |
|---|---|---|---|
| 1 | Remove `Key Vault Secrets User` role from app UAMI | Revoke role on `kv-infinevo-shared`; deploy revision | Container App fails to start / revision provisioning fails with secret resolution error. |
| 2 | Point secret reference to non-existent Key Vault secret | Set secret URL to `.../secrets/fake-secret` in Bicep | Deployment fails fast with Key Vault secret resolution error. |
| 3 | Commit plaintext password in `application.yml` or `.bicepparam` | Introduce dummy secret string in `infra/azure/parameters/dev.bicepparam`; run CI | CI secret scanning / git hook fails with blocking error (`DEBT-004`). |
| 5 | Give `worker_user` `BYPASSRLS` | `ALTER ROLE worker_user WITH BYPASSRLS;` then §5b check 2 | `FAIL: role worker_user missing or holds a forbidden attribute`. Proves the check reads the catalogue, and that the worker cannot see across tenants. |
| 6 | Drop `GRANT app_user TO worker_user` | Remove the grant; run §5b check 3 | The `worker` container never reaches healthy — it can log in but cannot read. Proves inheritance is what carries the grants, not a second copy of them. |
| 7 | Drop `worker_user` from `01-roles.sql` | Remove its `CREATE ROLE`; run §5b check 2 | `FAIL: role worker_user missing or holds a forbidden attribute`. |
| 8 | Point one `app` secret at the wrong Key Vault name | Change a `keyVaultUrl` to `.../secrets/psql-readonly-pw`; run §5a check 2 | `FAIL: app secrets are [...], expected [...]`. Proves the set is asserted, not the count. |
| 9 | Give `keycloak`'s secrets the `web` identity | `identity: identities.web.id` on both; run §5a check 2 | `FAIL: keycloak has 2 secret(s) on the wrong vault or wrong identity`. `id-web` holds no Key Vault Secrets User, so this would 403 at deploy time while reading as non-null. |
| 10 | Delete the `web` container app from the module | Remove `webContainerApp`; run §5a check 2 | `FAIL: selector for web matched 0 resources, expected 1`. Proves `web: no secrets` is not satisfied by `web` being absent. |
| 11 | Revert `compose.yml:178` to `app_user` | Restore `DB_USERNAME: app_user` on the `worker` service; run §5a check 5 | `FAIL: worker still connects as app_user`. Proves the one login site that moves is enforced, not just listed. |
| 12 | Reintroduce the cut dual-role scheme | Add `app_user_a` anywhere under `infra/`; run §5a check 4 | `FAIL: the dual-role scheme is deferred (rev 5)`. Revision 5 removes code the branch already carries, so the check has to assert its absence, not merely stop requiring it. |

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
expect_app="brevo-api-key jwt-signing-secret keycloak-client-secret psql-app-pw"
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
  #
  # The vault name is a module PARAMETER, so a standalone build renders
  # [format('https://{0}.{1}/secrets/psql-app-pw', parameters('keyVaultName'), ...)] and the
  # literal 'kv-infinevo-shared' never appears in the URL. Asserting the literal here could
  # never pass. The parameter reference is what the URL must carry; the literal is asserted
  # once, on the parameter's default, below.
  bad=$(printf '%s' "$ARM_JSON" | jq --arg n "$name" "[ $sel | .properties.configuration.secrets[]
          | select(((.keyVaultUrl // \"\") | contains(\"parameters('keyVaultName')\") | not)
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

# 2.6 The vault the parameter resolves to. Checked once, on the default, because 2.4 can
# only see the parameter reference.
vault=$(printf '%s' "$ARM_JSON" | jq -r '.parameters.keyVaultName.defaultValue // ""')
[ "$vault" = "kv-infinevo-shared" ] \
  || { echo "FAIL: keyVaultName defaults to '$vault', expected kv-infinevo-shared"; exit 1; }

echo "== 4. deploy.sh seeds every canonical secret, and the cut scheme is absent =="
# Comments are stripped first. deploy.sh lists all ten names in a header comment, so
# grepping the raw file passes even after a name is dropped from the seed loop - proved by
# deleting jwt-signing-secret from the loop and watching the check stay green.
CODE=$(sed -E 's/^[[:space:]]*#.*$//' infra/azure/deploy.sh)
for s in psql-admin-pw psql-app-pw psql-worker-pw \
         psql-migration-pw psql-readonly-pw psql-keycloak-pw keycloak-admin-pw \
         keycloak-client-secret brevo-api-key jwt-signing-secret; do
  printf '%s' "$CODE" | grep -q -- "$s" || { echo "FAIL: deploy.sh never seeds $s"; exit 1; }
done
# Revision 5 removes code the branch already contains, so absence is asserted rather than
# merely no longer required. A check that stops demanding something does not remove it.
cut=$(grep -rnE -- "app_user_[ab]|psql-app-pw-[ab]|psql-app-active-role" infra/ code/ .github/ || true)
[ -z "$cut" ] || { echo "FAIL: the dual-role scheme is deferred (rev 5), still present:"; echo "$cut"; exit 1; }
echo "PASS: 10 canonical secrets seeded, the dual-role scheme is absent"

echo "== 5. The worker connects as worker_user =="
# One site moves under revision 5, and only one. app_user stays everywhere else, so the
# check is narrow on purpose: it reads the worker service block, not the whole file.
sed -n '/^  worker:/,/^  [a-z]/p' infra/docker/compose.yml | grep -q "DB_USERNAME: worker_user" \
  || { echo "FAIL: worker still connects as app_user"; exit 1; }
# The live DDL-refusal check must match the privilege error, not a nonzero exit - the same
# defect this ticket fixes in smoke.sh (spec review rev2, B-6).
grep -q "permission denied for schema core" infra/azure/post-deploy-db.sh \
  || { echo "FAIL: post-deploy-db.sh still infers DDL refusal from an exit code"; exit 1; }
echo "PASS: worker on worker_user; live DDL check matches the privilege error"

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
# deploy.sh will write to Key Vault.
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
| 2 ARM secret sets | `app` `[brevo-api-key jwt-signing-secret keycloak-client-secret psql-app-pw]`, `worker` `[brevo-api-key psql-worker-pw]`, `keycloak` `[keycloak-admin-pw psql-keycloak-pw]`, `web` `[]`; exactly one resource matched per app; every secret on `kv-infinevo-shared` under that app's own identity; declared secrets and `secretRef` names in exact correspondence |
| 3 Deliverables | Both files non-empty, four scripts parse, `trap … EXIT` present and revoking, dry-run names all four rotation steps |
| 4 Secret inventory | 10 names present in `deploy.sh`; zero occurrences of `app_user_a`, `app_user_b` or `psql-app-active-role` anywhere under `infra/`, `code/`, `.github/` |
| 5 Worker role | `worker` connects as `worker_user`; `post-deploy-db.sh` matches the privilege error |
| 6 Secret sweep | > 200 files scanned, zero plaintext credentials outside `local_*` fallbacks |

### 5b. Local stack — requires `docker compose -f infra/docker/compose.yml up -d`

Deliberately short. Whether a password works, and whether a role is refused DDL, is proved
by the stack starting and by the first deployment — not by a local negative test that can
only be a weaker copy of it. What is checked here is the part nothing else would notice:
that `worker_user` carries the declared attributes, and that `app_user` was left alone.

**This script is what `infra/docker/smoke.sh` must contain** — it replaces the role loop at
`smoke.sh:49` and the DDL check at `smoke.sh:70-78`, rather than living beside them as a
second copy. The verifier runs `smoke.sh`.

Run after `down -v`. `01-roles.sql` is idempotent and sets every attribute in both branches,
so a re-run against an existing volume is equivalent to a clean one.

```bash
#!/usr/bin/env bash
set -eo pipefail
C="docker compose -f infra/docker/compose.yml"

# 1. worker_user inherits from app_user. Read from the catalogue rather than inferred from
# a query succeeding: a grant that is missing but compensated elsewhere would still pass a
# behavioural test, and then break the moment the compensation moves.
$C exec -T postgres psql -tAU postgres \
  -c "select 1 from pg_auth_members m
       join pg_roles r on r.oid=m.roleid join pg_roles g on g.oid=m.member
      where r.rolname='app_user' and g.rolname='worker_user'" | grep -q 1 \
  || { echo "FAIL: worker_user does not inherit from app_user"; exit 1; }

# 2. Declared attributes, for the new role as well as the four original ones.
for r in app_user worker_user migration_user readonly_user keycloak_user; do
  $C exec -T postgres psql -tAU postgres \
    -c "select 1 from pg_roles where rolname='$r' and rolsuper=false and rolbypassrls=false and rolcreatedb=false and rolcreaterole=false" \
    | grep -q 1 || { echo "FAIL: role $r missing or holds a forbidden attribute"; exit 1; }
done

# 3. The stack came up. This is the positive proof, and it is the real thing rather than a
# simulation of it: app connects as app_user and worker as worker_user, over TCP, with the
# passwords the stack issued. If the grant or the password is wrong the worker does not
# reach healthy. A local battery of negative tests would only be a worse copy of what
# starting the stack already does.
for svc in app worker; do
  $C ps --format '{{.Service}} {{.Health}}' | grep -qx "$svc healthy" \
    || { echo "FAIL: $svc is not healthy"; exit 1; }
done
echo "PASS: app healthy on app_user, worker healthy on worker_user"
```

| Check | Expected |
|---|---|
| 1 Inheritance | `worker_user` is a member of `app_user` |
| 2 Attributes | All five roles exist, none superuser, none `BYPASSRLS`, none `CREATEDB`/`CREATEROLE` |
| 3 Stack health | `app` and `worker` healthy — they connected as `app_user` and `worker_user` with their issued passwords |

### 5c. Live — DEFERRED to #129

> **Deferred:** The following checks require a deployed `dev` environment in `centralindia` and will run under #129 when cloud deployment is authorized:

```bash
# ── Live verification script (to be executed after Azure deployment #129) ──
# 1. Verify Key Vault secrets exist
for secret in "psql-admin-pw" "psql-app-pw" "psql-worker-pw" "keycloak-admin-pw" "keycloak-client-secret"; do
  az keyvault secret show --vault-name "$VAULT_NAME" --name "$secret" --query "id" -o tsv
done

# 2. Check latest revision provisioning state and health (avoids false positive of top-level app status)
for app in "app" "worker" "keycloak"; do
  LATEST_REV=$(az containerapp show -g "$RG_ENV" -n "ca-infinevo-${ENV}-${app}" --query "properties.latestRevisionName" -o tsv)
  REV_STATE=$(az containerapp revision show -g "$RG_ENV" -n "ca-infinevo-${ENV}-${app}" --revision "$LATEST_REV" --query "properties.provisioningState" -o tsv)
  [ "$REV_STATE" = "Succeeded" ] || { echo "FAIL: Revision $LATEST_REV provisioningState is '$REV_STATE' (expected Succeeded)"; exit 1; }
done

# 3. Prove authentication using the Key Vault secret (matching post-deploy-db.sh:137-151)
for role in app_user worker_user; do
  pw_secret="psql-app-pw"; [ "$role" = "worker_user" ] && pw_secret="psql-worker-pw"
  ROLE_PW=$(az keyvault secret show --vault-name "$VAULT_NAME" --name "$pw_secret" --query "value" -o tsv)
  PGPASSWORD="$ROLE_PW" psql -h "$PG_HOST" -U "$role" -d infinevo_dev -c "SELECT current_user;" | grep -q "$role"
done
echo "PASS: both roles authenticate with their Key Vault secret value"
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
| **No rotation procedure of any kind (rev 6).** A leaked or expired secret has no tested path back to a working system. | **Accepted, and the largest open risk in this ticket.** Rotation is done by hand against Key Vault and the database, undocumented. Revisit at `W-64` or on a real incident |
| **Keycloak Password Runtime Ignored:** Operators updating `KEYCLOAK_ADMIN_PASSWORD` in Key Vault expect the container to change its database password automatically. | Medium | Documented explicitly in §3g: Keycloak only reads the admin password on initial DB bootstrap. Runtime updates must run through `kcadm.sh`. |

---

## 8. Rollback

If secret resolution or rotation encounters issues:
1. **Secret Version Rollback:** Key Vault maintains version history for all secrets. Revert the Container App secret reference to the previous working version GUID.
2. **Rotation Rollback:** If a rotated password fails, reset the role with `psql-admin-pw` to the previous Key Vault version and restart the revision. This is the same short window as the rotation itself, run backwards.
3. **Container Revision Rollback:** If a new revision fails to start due to secret resolution failure, Container Apps automatically leaves the previous healthy revision active (traffic remains at 100% on the old revision).
4. **Database Credential Emergency Fallback:** In the event of a database password desynchronization, use `psql-admin-pw` to reset the role password to match the active Key Vault secret version.
5. **Local stack rollback:** `docker compose -f infra/docker/compose.yml down -v` and re-run. The roles are provisioned from scratch, so a half-applied `01-roles.sql` leaves nothing behind.

---

## 9. Done When

Every item names the check that attempts it. An item with no runnable check does not belong
on this list.

| # | Done when | Proved by |
|---|---|---|
| 1 | `deploy.sh` seeds all ten canonical secrets, and `app_user_a`, `app_user_b` and `psql-app-active-role` appear nowhere in `infra/`, `code/` or `.github/` | §5a check 4 |
| 2 | `app`, `worker` and `keycloak` each declare exactly their expected secret set, on `kv-infinevo-shared`, under their own identity, with every declared secret consumed by exactly one `secretRef` | §5a check 2 |
| 3 | `web` exists in the template and declares no secrets | §5a check 2.1 and 2.2 |
| 4 | `01-roles.sql` provisions `worker_user` in both branches of the idempotent block and grants it `app_user`; `app_user` itself is unchanged | §5b checks 1 and 2 |
| 5 | The local stack comes up — `app` healthy as `app_user`, `worker` healthy as `worker_user`. That is the proof the passwords and grants are right; a local negative test would be a weaker copy of it | §5b check 3 |
| 6 | All five roles exist with the declared attributes, and none is superuser or `BYPASSRLS` | §5b check 2 |
| 7 | `worker` connects as `worker_user`, and `post-deploy-db.sh` proves DDL refusal from the privilege error rather than an exit code | §5a check 5 |
| 9 | The sweep scans `code/backend`, `code/frontend/src`, `infra/azure`, `infra/docker`, `infra/postgres` and `.github` — over 200 files — and finds zero plaintext credentials outside `local_*` development fallbacks (`DEBT-004` closed for new code) | §5a check 6 |
| 10 | PR description contains `Closes #76` | Merge gate |

**Not proved before #129:** that a container actually resolves a secret from Key Vault and
starts, and that each env var receives the secret it names. Both fail loudly on the first
deployment rather than silently, which is why neither is simulated here. §5c is the check;
it needs an environment that does not exist yet.

---

## Decisions taken at approval

1. **Key Vault Secret Versioning Strategy in Container Apps — Option A.** Secret versions are
   pinned in Container Apps revisions (`.../secrets/psql-app-pw/<version-guid>`), for
   deterministic blue/green deployment and immediate revision rollback without touching Key
   Vault. `deploy.sh` resolves the current version at deploy time and passes it as a
   parameter, so the rendered ARM carries a parameter reference rather than a literal — which
   is why §5a check 2 asserts the secret name and vault, and the pinned version is asserted
   in §5c.
2. **Dual-Role Database Provisioning — withdrawn at revision 5.** Superseded by §0. One
   `app_user` with `LOGIN`, plus `worker_user` inheriting from it. Any future rotation needs
   only `ALTER ROLE` password rights and never `CREATE ROLE`.

Revision 4 was approved by the founder on 2026-09-19 for the `dev` environment.
> ### Revision 6 — rotation removed entirely, 2026-09-23
>
> `rotate-secrets.sh` and `infra/azure/docs/SECRET_ROTATION.md` are **deleted**, and every
> requirement, deliverable, check and risk row that described them is gone with them.
>
> **Why.** The merge review found the script promised more than it did: `--postgres-url` was
> parsed and ignored, so a rotation aimed at a test server would have hit the real Azure
> database; `--target keycloak` and `--target brevo` rotated nothing and returned success; and
> the runbook assigned a 90-day cadence to four secrets no code rotated. A procedure that is
> documented but not real is worse than an absent one, because an operator trusts it in the
> hour they can least afford to.
>
> **What this ticket still delivers.** Secrets live in Key Vault, containers resolve them
> through their own identity, nothing is committed, and `worker` has its own database role.
> That is the whole of `DEBT-004`.
>
> **What is now absent, and is a real gap.** There is no rotation procedure at all — manual,
> scripted or documented. Rotating a leaked secret means changing it in the database and Key
> Vault by hand and restarting the revisions, with nothing written down. That belongs to
> `W-64` or to the first incident, whichever comes first.

**Revision 5 was approved by the founder on 2026-09-22** — the trim was chosen explicitly
and `/develop W-56` invoked on the same turn. `/develop W-56` may resume.
