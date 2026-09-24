# Disaster Recovery & Backup Runbook
**Infinevo HCM & Payroll Cloud Platform**

---

## 1. Executive Summary & Recovery Objectives

This document establishes the authoritative operational disaster recovery runbook for the Infinevo Cloud Platform (`PLAT-10`, `W-62`).

### 1.1 Recovery Service Level Targets

| Tier / Component | Data Loss Target (RPO) | Recovery Time Target (RTO) | Mechanism |
|---|---|---|---|
| **Database (PostgreSQL 16 Flexible Server)** | **< 5 minutes** | **< 30 minutes** | Continuous WAL archiving + automated daily full snapshots + Point-In-Time Restore (PITR) |
| **Object Storage (Azure Blob Storage)** | **0 (within 7-day retention)** | **< 5 minutes** | Blob and container soft-delete (7 days retention, permanent delete disallowed) |
| **Key Vault Secrets (`kv-infinevo-shared`)** | **0 (within 7-day retention)** | **< 5 minutes** | Key Vault soft-delete (7 days retention) + purge protection |
| **Compute & APIs (Azure Container Apps)** | **0 (stateless)** | **< 10 minutes** | Immutable container images in ACR (`crinfinevo.azurecr.io`) redeployed via `deploy.yml` |

### 1.2 Data Residency & Regulatory Compliance (`D-18`)
Under the Indian **Digital Personal Data Protection (DPDP) Act**:
- Primary compute, storage, and database instances reside exclusively in **Azure Central India (`centralindia`)**.
- Cross-region geo-replication is **strictly disabled** (`geoRedundantBackup: 'Disabled'`).
- All backup copies, transaction logs, and restored test instances must strictly remain within the Indian jurisdiction (`centralindia`).

---

## 2. PostgreSQL Flexible Server Point-In-Time Restore (PITR)

### 2.1 How PITR Works on Azure Flexible Server
Azure Database for PostgreSQL Flexible Server **does not support in-place restore** (overwriting a running server). Instead, PITR provisions a **brand-new server instance** from backup snapshots and transaction logs (WAL) up to a specified second in time within the retention window (7 days).

### 2.2 CLI Procedure for Point-In-Time Restore

#### Step 1: Identify the Target Restore Point
Determine the UTC timestamp (ISO 8601 format) to restore:
```bash
# Check current available backup window
az postgres flexible-server show \
  -g rg-infinevo-dev \
  -n psql-infinevo-dev \
  --query "{earliest:properties.backup.earliestRestoreDate, retention:properties.backup.backupRetentionDays}"
```

#### Step 2: Trigger Point-In-Time Restore
Execute the restore command specifying source server and target timestamp:
```bash
RESTORE_TIME="2026-09-24T08:00:00Z"
TARGET_NAME="psql-infinevo-dev-restored"

az postgres flexible-server restore \
  --resource-group rg-infinevo-dev \
  --name "$TARGET_NAME" \
  --source-server psql-infinevo-dev \
  --restore-time "$RESTORE_TIME" \
  --no-wait
```

#### Step 3: Monitor Provisioning
```bash
while true; do
  STATUS=$(az postgres flexible-server show \
    -g rg-infinevo-dev \
    -n "$TARGET_NAME" \
    --query "state" -o tsv 2>/dev/null || echo "Starting")
  echo "Current status: ${STATUS}"
  if [ "$STATUS" = "Ready" ]; then
    echo "Restore completed successfully!"
    break
  fi
  sleep 30
done
```

#### Step 4: Verification of Data Consistency
Connect over the private VNet or run a verification container to validate databases:
```sql
-- Connect to target
\l
-- Expected databases: infinevo, keycloak, postgres

-- Connect to infinevo database
\c infinevo
SELECT count(*) FROM core.tenant;
SELECT count(*) FROM core.user_tenant;
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

#### Step 5: Traffic Cutover (Disaster Recovery Scenario)
In an emergency where the primary server is unrecoverable, cut traffic over to the restored server:
1. **Option A (Private Endpoint Swap - Recommended)**:
   Delete the old private endpoint and attach `pe-psql-infinevo-dev` to the new restored server's resource ID. Private DNS automatically points `psql-infinevo-dev.postgres.database.azure.com` to the new private IP.
2. **Option B (Data Dump & Import)**:
   Export the damaged tables from the restored server and import them into the primary server if only partial data corruption occurred.

### 2.3 Azure Portal Procedure for PITR
1. Open the [Azure Portal](https://portal.azure.com).
2. Navigate to **Resource Groups** ➔ `rg-infinevo-dev` ➔ `psql-infinevo-dev`.
3. In the top toolbar, click **Restore**.
4. Select **Point-in-time restore**.
5. Set **Restore point**: choose the exact date and time (UTC) prior to the failure event.
6. Provide a new server name: `psql-infinevo-dev-restored`.
7. Click **Review + Create**, then click **Create**.

---

## 3. Azure Blob Storage Disaster Recovery

All unstructured employee data (payslips, tax proofs, onboarding documents) is stored in `stinfinevodev` under containers `payslips`, `proofs`, and `documents`.

### 3.1 Accidental Blob Deletion Recovery
Soft delete is enabled for 7 days. Deleted blobs can be restored immediately without data loss:
```bash
# List soft-deleted blobs in the payslips container
az storage blob list \
  --account-name stinfinevodev \
  --container-name payslips \
  --include d \
  --auth-mode login \
  --query "[?deleted].{name:name, deletedTime:properties.deletedTime}"

# Undelete a specific deleted blob
az storage blob undelete \
  --account-name stinfinevodev \
  --container-name payslips \
  --name "tenant-1/2026/09/payslip-101.pdf" \
  --auth-mode login
```

### 3.2 Accidental Container Deletion Recovery
Container soft delete is enabled for 7 days:
```bash
# List deleted containers
az storage container list \
  --account-name stinfinevodev \
  --include-deleted \
  --auth-mode login \
  --query "[?deleted].{name:name, version:version}"

# Restore soft-deleted container
az storage container restore \
  --account-name stinfinevodev \
  --name payslips \
  --deleted-version "<VERSION_ID>" \
  --auth-mode login
```

---

## 4. Key Vault Secret Recovery

Key Vault `kv-infinevo-shared` holds all production secrets (`psql-admin-pw`, `psql-app-pw`, `jwt-signing-secret`, etc.).

### 4.1 Recovering a Deleted Secret
```bash
# List deleted secrets in the vault
az keyvault secret list-deleted \
  --vault-name kv-infinevo-shared \
  --query "[].{name:name, deletedDate:properties.deletedDate}"

# Recover a deleted secret
az keyvault secret recover \
  --vault-name kv-infinevo-shared \
  --name psql-app-pw
```

---

## 5. Break-Glass Direct Database Access (`D-52`)

Under `D-52`, PostgreSQL has `publicNetworkAccess: Disabled`. The database is accessible strictly via private endpoints inside `snet-pe`.

If the VNet becomes unroutable or an emergency requires external break-glass administrative access:
1. Subscription Owner temporarily enables public access restricted to the operator's `/32` IP address:
   ```bash
   OPERATOR_IP=$(curl -s https://ifconfig.me)
   az postgres flexible-server update \
     -g rg-infinevo-dev \
     -n psql-infinevo-dev \
     --public-network-access Enabled

   az postgres flexible-server firewall-rule create \
     -g rg-infinevo-dev \
     -n psql-infinevo-dev \
     -r break-glass \
     --start-ip-address "$OPERATOR_IP" \
     --end-ip-address "$OPERATOR_IP"
   ```
2. Operator completes emergency database repair.
3. Operator **immediately revokes** public access:
   ```bash
   az postgres flexible-server firewall-rule delete \
     -g rg-infinevo-dev \
     -n psql-infinevo-dev \
     -r break-glass --yes

   az postgres flexible-server update \
     -g rg-infinevo-dev \
     -n psql-infinevo-dev \
     --public-network-access Disabled
   ```

---

## 6. Regular DR Drills & Automation

Per `09-build-order.md:291` (*"an untested backup is not a backup"*), the platform team executes an automated restore drill regularly:

```bash
# Run automated restore drill in dev
bash infra/azure/dr/restore-drill.sh --env dev --source psql-infinevo-dev
```

### Verification Checklist Post-Drill:
- [ ] Restore duration measured and logged (must be < 30 minutes).
- [ ] Database `infinevo` verified with core tables.
- [ ] Database `keycloak` verified.
- [ ] Temporary drill server cleanly deleted (0 orphan resources).
- [ ] Evidence recorded in audit log.
