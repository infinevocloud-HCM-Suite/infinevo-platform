# Infinevo Platform — Operational On-Call Playbook

> Operational triage runbooks, severity classifications, escalation ladders, and response procedures (`PLAT-08`, `W-61`).

| Field | Value |
| :--- | :--- |
| **Capability** | `PLAT-08` — Alert rules, routing to a person, on-call process |
| **Notification Channel** | Azure Monitor Action Group `ag-infinevo-{env}-oncall` (`alerts@infinevocloud.com`) |
| **Target Infrastructure** | Azure Container Apps, PostgreSQL Flexible Server, Azure Storage Queue, Key Vault |
| **Region** | `centralindia` (`D-18`) |

---

## 1. Severity Matrix & Response SLAs

| Severity | Definition | Target Response SLA | Resolution SLA | Example Alert |
| :--- | :--- | :---: | :---: | :--- |
| **Sev-1 (Critical)** | Core business failure impacting payroll, money, or complete platform downtime. | **15 minutes** | **2 hours** | `alert-payrun-failure-{env}`: Background pay run failed in worker |
| **Sev-2 (High)** | Platform degradation, elevated 5xx error spikes, container crashlooping, or database connection saturation. | **30 minutes** | **4 hours** | `alert-5xx-spikes-{env}`, `alert-container-restarts-{env}`, `alert-postgres-connections-{env}` |
| **Sev-3 (Warning)** | Minor warning, non-critical background task delay, certificate expiry warning (>14d). | Next business day | 3 business days | Transient probe blip, non-blocking telemetry delay |

---

## 2. Escalation Ladder

```
[ Azure Monitor Alert Fires ] ──► Action Group: alerts@infinevocloud.com
                                                │
                                                ▼
                                    [ 1. Primary On-Call Engineer ]
                                    (Acknowledge within SLA)
                                                │
                          (If unacknowledged or unmitigated after 30 mins)
                                                │
                                                ▼
                                    [ 2. Lead Infrastructure Engineer ]
                                                │
                               (If major outage or payroll impact > 1 hour)
                                                │
                                                ▼
                                    [ 3. Platform Founder ]
```

---

## 3. Triage Runbooks

### Runbook 1: Failed Background Pay Run (`alert-payrun-failure-{env}`) — Sev-1

#### Symptom & Trigger
Azure Monitor fires `alert-payrun-failure-{env}` because the background worker (`ca-infinevo-{env}-worker`) caught an exception during a pay run execution and logged an `ERROR` message.

#### Step 1: Identify Tenant and Job ID in Log Analytics
Open Azure Portal -> Log Analytics Workspace (`law-infinevo-shared`) and execute:
```kusto
ContainerAppConsoleLogs_CL
| where ContainerAppName_s startswith "ca-infinevo-" and ContainerAppName_s endswith "-worker"
| where Log_s has "Failed to process payrun job" or Log_s has "markFailed"
| order by TimeGenerated desc
| project TimeGenerated, ContainerAppName_s, Log_s
| take 20
```
- Extract the `jobId` and `tenant_id` from the MDC context in the log.
- Check the exception stack trace attached to the log entry.

#### Step 2: Check Job Status in Database
From within the VNet (or via the migration runner container):
```sql
SELECT id, tenant_id, status, error_message, updated_at
FROM core.job_status
WHERE id = '<job_id>';
```

#### Step 3: Identify Root Cause
- **PostgreSQL Connection Exhaustion / Lock Timeout:** Look for `PSQLException: Connection refused` or `LockAcquisitionException`. Check connection counts in `alert-postgres-connections`.
- **Invalid Salary Structure / Computation Error:** Look for `ArithmeticException` or validation exceptions.
- **Storage Queue Message Poisoning:** Check if the message in `payrun-queue` on `stinfinevo{env}` reached its max dequeue count (default 5) and moved to poison queue.

#### Step 4: Remediation
1. If caused by transient database saturation or temporary worker restart:
   - Reset the job status to `QUEUED` or re-enqueue the pay run request via the internal admin API.
2. If caused by data corruption or software defect:
   - Notify the affected tenant administrator within 1 hour.
   - Deploy hotfix revision via CI/CD release workflow (`deploy.yml`).

---

### Runbook 2: Container Apps CrashLoop / Restarts (`alert-container-restarts-{env}`) — Sev-2

#### Symptom & Trigger
Alert fires when `ContainerAppSystemLogs_CL` records container failure or restart reason (`CrashLoopBackOff`, `OOMKilled`, or `ContainerFailed`).

#### Step 1: Query System Logs
```kusto
ContainerAppSystemLogs_CL
| where TimeGenerated > ago(30m)
| where Reason_s in~ ("CrashLoopBackOff", "OOMKilled", "ContainerFailed") or Log_s has "restarting"
| order by TimeGenerated desc
| project TimeGenerated, ContainerAppName_s, Reason_s, Log_s
```

#### Step 2: Check Console Stdout/Stderr Prior to Crash
```kusto
ContainerAppConsoleLogs_CL
| where ContainerAppName_s == "<failing-app-name>"
| order by TimeGenerated desc
| take 50
```

#### Step 3: Triage Common Failure Patterns
- **OOMKilled (Out of Memory):**
  - Increase memory allocation in `parameters/{env}.bicepparam` (e.g. from `0.5Gi` to `1.0Gi`) and redeploy IaC.
- **Key Vault 403 Forbidden / Secret Resolution Failure:**
  - Verify managed identity role assignment (`Key Vault Secrets User`) via `verify_roles.py`.
- **Database Connection Failure at Startup:**
  - Check if PostgreSQL server is accepting connections and whether private DNS zone `privatelink.postgres.database.azure.com` resolves to `10.10.3.7`.

#### Step 4: Rollback
If the crash started immediately following a new revision deployment:
```bash
# Shift 100% traffic back to the previous stable revision
az containerapp revision set-mode -n <app-name> -g rg-infinevo-<env> --mode multiple
az containerapp ingress traffic set -n <app-name> -g rg-infinevo-<env> --revision-weight <previous-revision>=100
```

---

### Runbook 3: PostgreSQL Active Connection Saturation (`alert-postgres-connections-{env}`) — Sev-2

#### Symptom & Trigger
Alert fires when PostgreSQL Flexible Server `active_connections` exceeds 80 (80% of `Standard_B1ms` limit).

#### Step 1: Inspect Active Queries
Connect to the database via `psql` within the private network:
```sql
SELECT pid, usename, client_addr, state, now() - query_start AS duration, query
FROM pg_stat_activity
WHERE state != 'idle'
ORDER BY duration DESC;
```

#### Step 2: Check for Idle in Transaction Connections
```sql
SELECT pid, usename, client_addr, state, now() - state_change AS idle_duration
FROM pg_stat_activity
WHERE state = 'idle in transaction'
ORDER BY idle_duration DESC;
```

#### Step 3: Terminate Blocking or Leaked Queries
If a query is holding locks or connection pools are leaking:
```sql
SELECT pg_terminate_backend(<pid>);
```

#### Step 4: Adjust HikariCP Pool Sizing
Check backend `application.yml` HikariCP `maximum-pool-size` settings (standard: `10` per replica). If container replica count scaled up dynamically, ensure total connections across all replicas remain $\le 80$.

---

## 4. Verification of Alerting System

To test the alerting pipeline without causing production disruption:

```bash
# Send test notification to the Action Group
az monitor action-group test-notifications create \
  --action-group ag-infinevo-dev-oncall \
  --resource-group rg-infinevo-dev \
  --alert-type "Budget" \
  --notification-type Email
```
Confirm receipt of the test notification in `alerts@infinevocloud.com`.
