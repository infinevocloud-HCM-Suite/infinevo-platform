# W-71 — Rehearsals: the Azure migration job

| Field | Value |
|---|---|
| **Work item** | `W-71` · issue [#93](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/93) |
| **Kind** | Infra |
| **Stream / track** | I — Migration |
| **Size / skill** | M · INFRA |
| **Owner** | karma |
| **Blocked by** | `W-68`, `W-69`, `W-70` merged |
| **Blocks** | `W-72` |
| **Decisions** | `W-51` (private Postgres, in-VNet jobs, secrets from Key Vault at run time), `D-49` (non-root images), `W-54` F-16 (no `:latest` from the pipeline) |
| **Gaps addressed** | none |
| **Status** | Ready |

---

## 1. Problem

The engine (`W-68`–`W-70`) runs only on a laptop. The target Postgres has
`publicNetworkAccess: 'Disabled'` (`infra/azure/main.bicep:285` area, `W-51`), so the
only compute that can reach it is a Container Apps job on `cae-infinevo-{env}`. Two
such jobs exist, neither carries Python: `caj-db-migration-{env}` provisions roles and
probes (`infra/azure/modules/db-migration-job.bicep:2-8`), `caj-flyway-{env}` applies
Flyway (`infra/azure/HANDBOOK.md:227-237`). The build order says rehearse until three
consecutive runs are clean, on acceptance, which has production's shape
(`docs/target-state/09-build-order.md:313`).

**Baseline**

| Command | Exit | Output |
|---|---|---|
| `az containerapp job list -g rg-infinevo-dev --query "[].name" -o tsv` | 0 | `caj-db-migration-dev`, `caj-flyway-dev` |
| `grep -c python infra/docker/migration-runner.Dockerfile` | 1 | `0` |

## 2. Scope

**In scope**

- A third job, `caj-data-migration-{env}`, same pattern as `db-migration-job.bicep`.
- Its image, `data-migration`, built by `deploy.sh` with `az acr build`.
- The source route: a `mysqldump` of each legacy database uploaded to Blob, restored into
  a MySQL sidecar inside the job. No network path from Azure to DigitalOcean.
- Four Key Vault secrets. Result capture to Blob. A rehearsal log in the repo.

**Out of scope**

- Running the job — the founder's step (`deliverable is the Bicep`).
- The production dump and freeze window — `W-72`.
- Any change to the Python — `W-68`–`W-70`.

## 3. The source route, decided

| Option | Decision | Why |
|---|---|---|
| Live link, Azure job to DigitalOcean MySQL 3306 | **Rejected** | `main.bicep` has no NAT gateway, so the egress IP changes per run; the droplet firewall would need `0.0.0.0/0` on 3306 or a NAT gateway added for one job. Production MySQL would be reached from the internet |
| `mysqldump` to Blob, restore in the job | **Chosen** | Taken on the droplet by the founder over SSH, uploaded with `az storage blob upload`, read by the job over the storage private endpoint. Same dump replays for every rehearsal, so three clean runs mean the same thing |

## 4. Implementer tasks

### Task 1 — `infra/docker/data-migration.Dockerfile`

| Line | Content |
|---|---|
| Base | `python:3.12-slim` pinned by digest; `apt-get install mariadb-server mariadb-client postgresql-client-16 curl ca-certificates`; Azure CLI via pip is too heavy — use `azure-identity` + `azure-storage-blob` + `azure-keyvault-secrets` in `requirements.txt` instead and drop `az login` |
| App | `COPY code/migration/ /app/migration/`; `pip install -r /app/migration/requirements.txt` |
| User | uid/gid 1000 `infinevo`, as `migration-runner.Dockerfile:41-43` (`D-49`) |
| Entry | `ENTRYPOINT ["/app/entrypoint.sh"]`, no `EXPOSE` |
| Secrets | none in the image; `grep -i pass` on the Dockerfile returns nothing |

### Task 2 — `infra/docker/data-migration-entrypoint.sh`

| Step | What |
|---|---|
| 1 | `DefaultAzureCredential` via `AZURE_CLIENT_ID = MIGRATION_CLIENT_ID`; read `psql-migration-pw`, `keycloak-admin-pw`, `legacy-dump-sha256` from `kv-infinevo-shared` with the `read_secret` pattern of `migration-runner-entrypoint.sh:36-45` (declare, then assign, so `set -e` sees a failure) |
| 2 | Download `migration-dumps/{RUN_ID}/hrms.sql.gz` and `payroll.sql.gz` from `stinfinevo{env}`; verify sha256 against the secret; stop on mismatch |
| 3 | Start `mariadbd` on `127.0.0.1:3306` with `--datadir /tmp/mysql`, create `hrmstestdb` and `payroll_test_db`, restore both dumps. Export `LEGACY_*_DB_HOST=127.0.0.1`, user `root`, empty password, which the extractors accept as local (`extract_hrms.py:44`) |
| 4 | Export `TARGET_DB_*` from env + secret, `KEYCLOAK_*` from env + secret |
| 5 | Run in order: `extract_hrms.py`, `extract_payroll.py`, `merge_employees.py`, then the thirteen loaders of `W-68` Task 3 in their listed order (3a `load_tenant` … 3m `load_notifications`), then `load_documents.py` (`W-69`), `lop_mismatch_generator.py`, `financial_reconciliation.py`. The list lives in one place, `code/migration/run_all.sh`, which the entrypoint calls. `set -euo pipefail`; the first non-zero exit ends the job FAILED |
| 6 | Upload `staging/reconciliation_*.json`, `staging/lop_mismatch_*.csv`, `staging/stg_quarantined_*.json` and the full log to `migration-dumps/{RUN_ID}/results/`. Delete `/tmp/mysql` and `staging/` |
| Exit | 0 only when reconciliation exited 0 |

### Task 3 — `infra/azure/modules/data-migration-job.bicep`

Copy `db-migration-job.bicep`, then:

| Param / property | Value |
|---|---|
| `name` | `caj-data-migration-${environment}` |
| `triggerType` | `Manual`; `replicaTimeout` 14400; `replicaRetryLimit` 0 |
| `cpu` / `memory` | `2.0` / `4.0Gi` (MariaDB restore plus Python) |
| `identity` | `id-migration-{env}` only; drop the app and worker identities the probe job needs (`db-migration-job.bicep:46-55`) |
| `registries` | ACR with `identity: migrationIdentityId` as `:116-121` |
| `env` | `ENVIRONMENT`, `RUN_ID` (overridden at start), `MIGRATION_CLIENT_ID`, `KEY_VAULT_NAME`, `STORAGE_ACCOUNT_NAME`, `TARGET_DB_HOST` (private FQDN), `TARGET_DB_NAME`, `TARGET_DB_USER=migration_user`, `KEYCLOAK_URL`, `KEYCLOAK_REALM`. Names and endpoints only, no values |
| `image` | `${acrLoginServer}/data-migration:${imageTag}`, tag param defaulting to the sentinel the Flyway job uses, never `:latest` (`W-54` F-16) |

### Task 4 — wiring

| File | Change |
|---|---|
| `infra/azure/main.bicep` after the `dbMigrationJob` module (`:515-535`) | `module dataMigrationJob 'modules/data-migration-job.bicep'`, same params, `dependsOn` the RBAC module |
| `infra/azure/modules/storage.bicep:69-71` | add `'migration-dumps'` to `containerNames` |
| `infra/azure/modules/managed-identities.bicep` or the RBAC module | `id-migration-{env}` gets `Storage Blob Data Contributor` on `stinfinevo{env}` (it has Key Vault Secrets User and AcrPull already) |
| `infra/azure/deploy.sh` next to `:425-431` | second `az acr build --image "data-migration:${RUNNER_TAG}" --file infra/docker/data-migration.Dockerfile .` |
| `infra/azure/deploy.sh` secret seeding near `:199-205` | create `keycloak-admin-pw` if absent; `psql-migration-pw` exists; `legacy-dump-sha256` is set by the founder at dump time, not seeded |
| `infra/azure/HANDBOOK.md` §4.6 | a third row: `caj-data-migration-{env}` runs the legacy data migration from a dump in Blob; how to start it with `--env-vars RUN_ID=...` |
| `.github/workflows/infra.yml` | nothing — it already builds and lints every module (`:44-54`) |

### Task 5 — `docs/target-state/features/W-71-rehearsal-log.md`

A table the founder fills after each run. Columns: date, `RUN_ID`, environment, dump
date, duration, ledger failed steps, reconciliation `clean`, LOP mismatches, quarantined
rows, blobs verified, outcome. Three consecutive `clean` rows on `uat` close the ticket.

## 5. The founder's loop, per rehearsal

| # | Where | Command |
|---|---|---|
| 1 | droplet, SSH | `mysqldump --single-transaction --set-gtid-purged=OFF hrmstestdb \| gzip > hrms.sql.gz` and the same for `payroll_test_db` |
| 2 | laptop | `sha256sum *.sql.gz`; `az keyvault secret set -n legacy-dump-sha256 --value "<hrms> <payroll>"` |
| 3 | laptop | `az storage blob upload-batch -d migration-dumps/<RUN_ID> -s . --account-name stinfinevodev --auth-mode login` |
| 4 | laptop | `az containerapp job start -g rg-infinevo-dev -n caj-data-migration-dev --env-vars RUN_ID=<RUN_ID>` |
| 5 | laptop | `az containerapp job execution list ... --query "[0].properties.status"` until `Succeeded` or `Failed`; `az containerapp job logs show ... --container data-migration` |
| 6 | laptop | download `migration-dumps/<RUN_ID>/results/`, fill the rehearsal log |
| 7 | laptop | `psql "$MIGRATION_URL" -v tenant_id=… -v confirm_name=… -v apply=1 -f infra/postgres/purge-tenant.sql` per tenant, delete the Keycloak users and the blob prefix as the script prints (`purge-tenant.sql:20-23`), repeat from 4 |

## 6. Standing rules

Creates no table, no Flyway script, no Java change. Secrets: none in Bicep, none in the
image, read at run time from Key Vault under a revocable role (`W-51`). Non-root (`D-49`).

## 7. Verification

```bash
az bicep build --file infra/azure/main.bicep                                   # expected: no output, exit 0
az bicep lint  --file infra/azure/main.bicep                                   # expected: no warnings
az bicep build --file infra/azure/modules/data-migration-job.bicep             # expected: exit 0
docker build -f infra/docker/data-migration.Dockerfile -t data-migration .     # expected: image builds, no secret in `docker history`
docker run --rm --entrypoint python data-migration -c "import pymysql, psycopg2, azure.storage.blob, azure.keyvault.secrets; print('ok')"   # expected: ok
grep -nE "password|secret" infra/azure/modules/data-migration-job.bicep        # expected: only comments and secret NAMES
bash -n infra/docker/data-migration-entrypoint.sh                               # expected: exit 0
```

| Check | Expected |
|---|---|
| `az deployment group what-if` for `rg-infinevo-dev` | one new job, one new blob container, one new role assignment, nothing else |
| First start on dev with a non-prod dump | execution `Succeeded`, `results/reconciliation_*.json` has `clean: true` |
| `W-71-rehearsal-log.md` | three consecutive clean rows on `uat` |

## 8. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| MariaDB restore of a MySQL 8 dump fails on syntax | Medium | Use `mysql:8.0` client and server packages from the MySQL apt repo instead; the spec names MariaDB only because it is in Debian. Developer picks whichever restores the dump cleanly and says so in the Dockerfile comment |
| Keycloak admin REST not reachable from the job | Low | Keycloak runs in the same environment; `KEYCLOAK_URL` is its internal FQDN |
| 4 h timeout too short for production volume | Low | Non-prod runs in minutes; `W-72` re-measures |
| Dump holds production PII in Blob | Medium | `migration-dumps` is private, no public access, deleted after each rehearsal, with a 7-day lifecycle rule in `storage.bicep` |

## 9. Rollback

Delete the job and the container with the Bicep removed and redeployed. Each rehearsal
tenant is removed with `purge-tenant.sql`. No production data is touched by this ticket.
