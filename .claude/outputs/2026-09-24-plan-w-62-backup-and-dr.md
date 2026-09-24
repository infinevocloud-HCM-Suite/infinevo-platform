# W-62 — Backup & Disaster Recovery

> **For infra tickets** — labelled `skill-INFRA`, whose subject is an environment rather than a screen.
> Written to `.agents/outputs/2026-09-24-plan-w-62-backup-and-dr.md` per `infra-task` protocol.

| Field | Value |
|---|---|
| **Work item** | `W-62` · issue [#82](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/82) |
| **Kind** | Infra |
| **Stream / track** | Stream H — Security, operations & go-to-market |
| **Wave** | Wave 3 — Identity and tenancy |
| **Size / skill** | M — INFRA |
| **Owner** | KarmaveerM |
| **Blocked by** | `W-50` ([#70](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/70), merged) |
| **Blocks** | None (operational readiness) |
| **Capabilities** | `PLAT-10` (Backup & disaster recovery: tested restores against agreed targets) |
| **Decisions** | `D-09` (one Postgres), `D-10` (Container Apps), `D-18` (India region `centralindia`), `D-19` (scale 10 tenants x 100 employees), `D-22` (public surface policy), `D-52` (Postgres private endpoint) |
| **Gaps addressed** | `DEBT-002` (ddl-auto/no migration framework), `DEBT-003` (automated verification tests), `DEBT-018` (untested legacy DB state) |
| **Status** | **Approved by Founder** |
| **Approved by** | Founder |
| **Approved on** | 2026-09-24 |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

1. **Untested Restores**: `09-build-order.md:291` defines `W-62`'s completion condition: *"Done when: a restore has actually been performed. Watch: an untested backup is not a backup."* While PostgreSQL Flexible Server in Azure (`psql-infinevo-dev`) runs automated daily backups, **zero restores have ever been executed, timed, or verified in the cloud**.
2. **Container Soft-Delete Missing**: In `infra/azure/modules/storage.bicep:58-62`, blob-level soft delete is enabled for 7 days, but `containerDeleteRetentionPolicy` is not configured (`null`). An operator deleting a container (`documents`, `payslips`, `proofs`) permanently destroys all blobs immediately with no soft-delete safety net.
3. **Postgres Retention Parameterization**: In `infra/azure/modules/postgres.bicep:59`, `backupRetentionDays: 7` is hardcoded. Production environments under Indian DPDP compliance (`D-18`, `05-azure-architecture.md:143`) require configurable retention (up to 35 days).
4. **No Disaster Recovery Runbook**: There is no runbook defining Recovery Point Objectives (RPO), Recovery Time Objectives (RTO), point-in-time recovery procedures, or break-glass reconnection steps for operators.
5. **Legacy State**: Legacy HRMS and Payroll had zero backup policies, ran `ddl-auto=update` on MySQL (`BUG-004`, `DEBT-002`), and had no documented recovery procedures.

### Baseline

| Command | Exit | Output |
|---|---|---|
| `az postgres flexible-server backup list -g rg-infinevo-dev -s psql-infinevo-dev` | 0 | 5 daily automated backups exist (`2026-09-19` to `2026-09-23`), but 0 point-in-time restores have been performed. |
| `az storage account blob-service-properties show --account-name stinfinevodev -g rg-infinevo-dev --query "containerDeleteRetentionPolicy"` | 0 | `null` (container soft-delete disabled). |
| `test -f infra/azure/DR_RUNBOOK.md` | 1 | File does not exist. |
| `test -f infra/azure/dr/restore-drill.sh` | 1 | Script does not exist. |

---

## 2. Scope

### In scope
1. **Bicep Infrastructure Enhancements**:
   - Parameterize `postgresBackupRetentionDays` in `infra/azure/modules/postgres.bicep` and `infra/azure/main.bicep` (default 7 days for dev, configurable up to 35 days in `prod.bicepparam`).
   - Add `containerDeleteRetentionPolicy` (7 days retention) to `infra/azure/modules/storage.bicep`.
2. **Automated Restore Drill Script (`infra/azure/dr/restore-drill.sh`)**:
   - Idempotent script to perform Point-In-Time Restore (PITR) of `psql-infinevo-dev` to a temporary test server `psql-infinevo-dev-dr-test`.
   - Polls and measures elapsed restore time against agreed RTO (< 30 minutes).
   - Validates existence and integrity of `infinevo` and `keycloak` databases, core schemas (`core`, `hrms`, `payroll`, `reference`), and tables (`core.tenant`, `flyway_schema_history`).
   - Includes automatic cleanup (`trap` handler) that deletes `psql-infinevo-dev-dr-test` upon completion or failure to avoid accumulating cloud costs.
3. **Disaster Recovery Runbook (`infra/azure/DR_RUNBOOK.md`)**:
   - Explicit RPO/RTO service level targets:
     - Database: RPO < 5 minutes (continuous WAL archiving), RTO < 30 minutes (PITR provision + DNS cutover).
     - Blob Storage: RPO 0 (soft-delete), RTO < 5 minutes (undelete).
     - Key Vault: RPO 0 (soft-delete), RTO < 5 minutes (undelete).
   - Step-by-step operational runbook for CLI and Portal recovery.
   - Private endpoint and DNS redirection procedure for disaster recovery scenarios.
   - Break-glass database access procedures (`D-52`).
4. **Live Execution & Evidence**:
   - Execute `restore-drill.sh` against live Azure `rg-infinevo-dev`.
   - Record verifiable restore output and timing in `.agents/outputs/2026-09-24-verify-W-62.md`.

### Out of scope
- **Geo-redundant backup replication**: Explicitly forbidden by `D-18` and `docs/target-state/05-azure-architecture.md:143-145` (primary and backups must both remain in `centralindia` under Indian DPDP Act).
- **Read replicas**: Deferred at current scale (`D-19`) per `05-azure-architecture.md:105`.
- **Application schema changes**: No database tables, entities, or Flyway scripts are created or modified by `W-62`.

---

## 3. What gets built

### Architecture of Restore & Disaster Recovery

```
                     PRIMARY ENVIRONMENT (Live)
┌─────────────────────────────────────────────────────────────────┐
│ Resource Group: rg-infinevo-dev                                 │
│                                                                 │
│   [psql-infinevo-dev] ──► Continuous WAL archiving (RPO < 5m)  │
│   (Postgres 16, VNet) ──► Daily Full Backups (7-day retention)  │
│                               │                                 │
└───────────────────────────────┼─────────────────────────────────┘
                                │ Point-In-Time Restore (PITR)
                                ▼
                     DRILL / RESTORE TARGET
┌─────────────────────────────────────────────────────────────────┐
│ Resource Group: rg-infinevo-dev                                 │
│                                                                 │
│   [psql-infinevo-dev-dr-test] (Restored Server)                 │
│     ├── Verified: infinevo DB & keycloak DB populated           │
│     ├── Verified: core.tenant & flyway_schema_history intact    │
│     └── Cleaned Up: Server deleted immediately post-drill        │
└─────────────────────────────────────────────────────────────────┘
```

### Exact Files Changed / Created

| File | Change | Why |
|---|---|---|
| `infra/azure/modules/postgres.bicep` | Changed | Parameterize `backupRetentionDays` (default 7, up to 35) |
| `infra/azure/modules/storage.bicep` | Changed | Add `containerDeleteRetentionPolicy` with 7 days retention |
| `infra/azure/main.bicep` | Changed | Expose and wire `postgresBackupRetentionDays` parameter |
| `infra/azure/parameters/dev.bicepparam` | Changed | Explicitly declare `postgresBackupRetentionDays = 7` |
| `infra/azure/dr/restore-drill.sh` | New | Automated PITR drill script with validation and guaranteed teardown |
| `infra/azure/DR_RUNBOOK.md` | New | Comprehensive operational DR runbook (RPO, RTO, PITR, soft-delete, break-glass) |

### What is NOT touched
- Application source code (`code/backend/`, `code/frontend/`).
- Database schema scripts (creates **no tables**, no Flyway scripts).
- Running container apps (`ca-infinevo-dev-*`) and Front Door routing remain untouched.

---

## 4. Proving it

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Attempt PITR with a timestamp beyond the 7-day retention window | Azure CLI / ARM rejects the request with an error naming the valid restore window. |
| 2 | Delete a test container on `stinfinevodev` | Container enters soft-deleted state; `az storage container list --include-deleted` displays the container with `deleted: true` and undelete restores it. |
| 3 | Interrupt `restore-drill.sh` mid-execution | The script's `trap ... EXIT` handler ensures the temporary server `psql-infinevo-dev-dr-test` is cleanly deleted, leaving 0 orphan cloud resources. |

---

## 5. Verification

Exact verification commands:

```bash
# 1. Bicep build & lint validation
az bicep build --file infra/azure/main.bicep

# 2. Verify Storage Account container soft-delete policy in Azure
az storage account blob-service-properties show \
  --account-name stinfinevodev \
  -g rg-infinevo-dev \
  --query "containerDeleteRetentionPolicy.enabled" -o tsv
# Expected: true

# 3. Execute Point-In-Time Restore drill
bash infra/azure/dr/restore-drill.sh --env dev --source psql-infinevo-dev

# 4. Verify drill server teardown
az postgres flexible-server show \
  -g rg-infinevo-dev \
  -n psql-infinevo-dev-dr-test 2>&1 || true
# Expected: ResourceNotFound
```

| Check | Expected |
|---|---|
| Bicep build | Clean exit code 0, 0 errors |
| Container soft-delete | `true` (7 days retention) |
| PITR drill execution | Server restored, databases verified, elapsed time < 30m |
| Drill cleanup | Server deleted, exit code 0 |

---

## 6. Gap disposition

| Gap | Disposition |
|---|---|
| `DEBT-002` | Both backends ran `ddl-auto=update` without migration framework. Discounted for W-62 (resolved by W-06 and W-54). |
| `DEBT-003` | No automated test coverage. Fixed for W-62 by automated `restore-drill.sh` test harness. |
| `DEBT-018` | Legacy MySQL databases had no automated backups, point-in-time recovery, or DR runbooks. Fixed by W-62. |

---

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Restored test server incurs cloud spend | High | `restore-drill.sh` enforces automatic deletion via bash `trap` handler, guaranteeing deletion even on error or abort. |
| PITR takes 15–25 minutes to provision | High | Expected Azure provisioning duration. Script polls with non-blocking status logging and 30-minute timeout. |
| Data residency violation under DPDP Act | Low | `geoRedundantBackup: 'Disabled'` enforced in Bicep (`D-18`), ensuring all backup and restore data remains strictly in `centralindia`. |

---

## 8. Rollback

- Bicep changes are backward-compatible parameter additions. Rollback is a standard git revert.
- In the event a drill server fails to delete automatically, operators can delete it manually with:
  ```bash
  az postgres flexible-server delete -g rg-infinevo-dev -n psql-infinevo-dev-dr-test --yes
  ```

---

## 9. Done when

1. Bicep parameterizes PostgreSQL backup retention (7 days dev, up to 35 prod) and compiles with 0 errors via `az bicep build`.
2. Bicep adds 7-day container delete retention to Blob Storage (`storage.bicep`).
3. `infra/azure/dr/restore-drill.sh` exists, is executable, and contains automated restore, schema validation, and guaranteed cleanup logic.
4. `infra/azure/DR_RUNBOOK.md` exists and documents RPO/RTO targets, CLI/Portal PITR procedures, blob/Key Vault soft-delete undelete, and break-glass procedures.
5. An actual point-in-time restore has been performed in Azure `rg-infinevo-dev` and verified per `09-build-order.md:291`.
6. Verified report with live Azure evidence recorded in `.agents/outputs/2026-09-24-verify-W-62.md`.

---

## Decisions recorded from Founder Review

**1. Drill server verification networking route:**
- **Selected**: **(a) Private-only verification**. Verified via in-VNet access / private endpoint without exposing any public endpoint, adhering strictly to `05-azure-architecture.md:75` ("Only Front Door is public").

**2. Production backup retention window (`prod.bicepparam`):**
- **Selected**: **(a) 7 days**. Standard baseline across dev, uat, and prod. Configurable via `postgresBackupRetentionDays` parameter.

