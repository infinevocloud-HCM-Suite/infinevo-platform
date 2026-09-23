# W-61 — Alerting

> Azure Monitor alert rules, action group routing, and on-call response procedures (`PLAT-08`).  
> Derived from `TEMPLATE-INFRA.md`.

| Field | Value |
| :--- | :--- |
| **Work item** | `W-61` · issue [#81](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/81) |
| **Kind** | Infra |
| **Stream / track** | Stream H — Security, operations & go-to-market · Track I |
| **Wave** | Wave 4 — Core data and services |
| **Size / skill** | S · INFRA |
| **Owner** | BirenGit |
| **Blocked by** | `W-60` ([#80](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/80) — Observability) · Merged |
| **Blocks** | `W-63` ([#83](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/83) — Load test baseline) |
| **Capabilities** | `PLAT-08` — alert rules, routing to a person, on-call process |
| **Decisions** | `D-10` (Azure Container Apps) · `D-18` (India region Central India) · `D-50` (Storage Queue) · `PLAT-07` (Observability telemetry) |
| **Gaps addressed** | None in `GAP_INVENTORY.md` (Operational alerting is a new platform capability) |
| **Status** | **Approved** |
| **Approved by** | Founder |
| **Approved on** | 2026-09-23 |

> **Hard rule 1 satisfied:** Approved by founder on 2026-09-23. Decisions: alertEmail = `alerts@infinevocloud.com`; 5m evaluation over 15m window.

---

## 1. Problem

Today, the platform has structured JSON logging, distributed tracing context, and metric collection in Log Analytics (`law-infinevo-shared`) delivered by `W-60` ([W-60-observability.md:55-56](file:///d:/Infinevo%20Platform/infinevo-platform/docs/target-state/features/W-60-observability.md#L55-L56)). However, there is zero automated alerting:

1. **Silent Business Failures:** When a background pay run fails in the worker, `PayrunQueueListener` catches the exception, logs it at ERROR level, and marks `core.job_status` as `FAILED` ([PayrunQueueListener.java:68-70](file:///d:/Infinevo%20Platform/infinevo-platform/code/backend/worker/src/main/java/com/infinevo/worker/listener/PayrunQueueListener.java#L68-L70)). Nobody is alerted. A failed payroll batch remains silent until an HR administrator notices missing payslips.
2. **Silent Infrastructure Outages:** If a Container App enters a crashloop ([containerapps.bicep](file:///d:/Infinevo%20Platform/infinevo-platform/infra/azure/modules/containerapps.bicep)), if PostgreSQL connections are starved, or if Key Vault 403 Forbidden bursts occur, no notification is sent.
3. **No On-Call Routing or Playbook:** There is no Azure Action Group, no alert routing mechanism to an engineer, and no documented triage playbook or escalation SLA.

### Baseline

| Command | Exit | Output |
| :--- | :---: | :--- |
| `az monitor action-group list -g rg-infinevo-dev --query "[].name" -o tsv` | 0 | *(empty — 0 action groups)* |
| `az monitor scheduled-query list -g rg-infinevo-dev --query "[].name" -o tsv` | 0 | *(empty — 0 scheduled query alert rules)* |
| `az monitor metrics alert list -g rg-infinevo-dev --query "[].name" -o tsv` | 0 | *(empty — 0 metric alert rules)* |
| `ls infra/azure/modules/alerting.bicep` | 1 | `ls: cannot access 'infra/azure/modules/alerting.bicep': No such file or directory` |

---

## 2. Scope

### In scope

- **Azure Action Group (`ag-infinevo-{env}-oncall`):** Provisions an Azure Monitor Action Group supporting email notification (`alertEmail` parameter) and webhook integration (for Slack / Microsoft Teams / PagerDuty).
- **Critical Business Alert (Sev-1):** Scheduled KQL query rule on `law-infinevo-shared` alerting immediately if any worker pay run fails (`PayrunQueueListener` error log or `core.job_status` failure).
- **Core Infrastructure Alerts (Sev-2 & Sev-3):**
  - **High 5xx Failure Rate:** Alert if HTTP 5xx responses exceed 5% of total requests over a 5-minute window.
  - **Container Crashloop:** Alert if Container App `RestartCount` > 3 in 15 minutes on `app`, `worker`, or `keycloak`.
  - **PostgreSQL Saturation:** Metric alert if PostgreSQL `active_connections` > 80% of server maximum or storage > 85%.
  - **Key Vault Unauthorized Bursts:** Metric alert if Key Vault 403 Forbidden errors > 5 in 5 minutes.
- **On-Call Playbook (`infra/azure/ONCALL_PLAYBOOK.md`):** Severity classification (Sev-1/2/3), response SLAs, escalation ladder, and triage runbooks for failed pay runs, container restarts, and database connection exhaustion.
- **Live Verification Check (`infra/azure/verify-live.sh`):** Check 10 verifying that the Action Group and Alert Rules exist, are enabled, and target `centralindia`.

### Out of scope

- **Application Code Changes:** Out of scope. `code/backend/` and `code/frontend/` are untouched. Telemetry emitted by `W-60` is already sufficient.
- **Paid PagerDuty / Opsgenie Subscription:** Out of scope. Uses native Azure Action Group email + webhook architecture; webhook supports any external provider when credentials are provided.
- **SMS / Voice Call Costs:** SMS and voice call notifications require dedicated Azure carrier subscriptions; email and webhook notifications are standard.

---

## 3. What Gets Built

### Architecture & Notification Flow

```
[ Worker: Pay Run Job Fails ] ──► Emits ERROR log (MDC: tenant_id, correlation_id)
                                          │
                                          ▼
                         [ law-infinevo-shared: ContainerAppConsoleLogs_CL ]
                                          │
                     (Scheduled KQL Query Rule evaluates every 5m)
                                          │
                                          ▼
                             [ Alert Rule Triggers ]
                                          │
                                          ▼
                      [ Action Group: ag-infinevo-dev-oncall ]
                                    │               │
                     (Email to on-call engineer)    (Webhook to Ops / Slack)
                                    ▼               ▼
                 [ Engineer Opens infra/azure/ONCALL_PLAYBOOK.md ]
```

### Files Changed & Created

| File | Change | Purpose |
| :--- | :--- | :--- |
| `infra/azure/modules/alerting.bicep` | **New** | Bicep module defining Action Group, Scheduled Query Rules, and Metric Alerts |
| `infra/azure/main.bicep` | **Modify** | Invoke `alerting` module, wiring `workspaceId`, `alertEmail`, and resource IDs |
| `infra/azure/parameters/dev.bicepparam` | **Modify** | Add `alertEmail` parameter (defaults to operator email) |
| `infra/azure/HANDBOOK.md` | **Modify** | Document alerting architecture, action groups, and rule thresholds |
| `infra/azure/ONCALL_PLAYBOOK.md` | **New** | Operational on-call runbook, severity definitions, response SLAs, and triage guides |
| `infra/azure/verify-live.sh` | **Modify** | Add Check 10 asserting live alerting rules and action group status |

### What Is Not Touched

- `code/backend/**`: Untouched (zero Java or Maven changes).
- `code/frontend/**`: Untouched.
- `infra/docker/**`: Untouched.
- `infra/azure/modules/containerapps.bicep`: Untouched.
- `infra/azure/modules/postgres.bicep`: Untouched.

---

## 4. Proving It (Deliberate Breaks)

| # | Deliberate break | What must happen |
| :--- | :--- | :--- |
| 1 | Pass an invalid KQL syntax in `alerting.bicep` | `az bicep build` or deployment what-if fails validation with query parsing error. |
| 2 | Supply an invalid email address (missing `@`) in `dev.bicepparam` | ARM deployment validation rejects the Action Group with an invalid email schema error. |
| 3 | Lower threshold of payrun failure alert to trigger on synthetic log message | Alert triggers, fires Action Group, and test notification arrives in email inbox within 5 minutes. |

---

## 5. Verification

The verification is machine-checked using Azure CLI and Bicep compiler:

```bash
# 1. Bicep syntax and compilation validation
az bicep build --file infra/azure/modules/alerting.bicep
az bicep lint --file infra/azure/modules/alerting.bicep
az bicep build --file infra/azure/main.bicep
az bicep lint --file infra/azure/main.bicep

# 2. Live deployment check (after founder applies)
bash infra/azure/verify-live.sh dev
```

| Check | Expected | Result |
| :--- | :--- | :--- |
| Bicep Module Build | Exit 0, clean ARM output | Pass |
| Bicep Module Lint | Exit 0, zero warnings | Pass |
| Root Orchestrator Build | Exit 0, clean ARM output | Pass |
| Live Verification Check 10 | Action group and 5 alert rules confirmed enabled in live Azure | Pass |

---

## 6. Gap Disposition

| Gap ID | Disposition |
| :--- | :--- |
| `DEBT-003` (No tests in legacy) | Discounted: Infra monitoring ticket; validated by Bicep build and live Azure query test. |
| `DEBT-021` (Unlocked schedulers) | Discounted: Addressed by `W-52` (ShedLock). Alerting adds monitoring for ShedLock contention. |
| `DEBT-033` (Plaintext API key) | Discounted: Addressed by `W-57`. |

---

## 7. Risks & Mitigations

| Risk | Likelihood | Mitigation |
| :--- | :---: | :--- |
| **Alert Fatigue / Storming** | Medium | Throttling / suppression window of 15–30 minutes configured on every alert rule so an ongoing issue does not fire hundreds of duplicate emails. |
| **Ingestion Latency Gap** | Low | KQL queries evaluate over a 15-minute sliding window with a 5-minute aggregation granularity, absorbing the 30–90 second Log Analytics ingestion delay without false alarms. |
| **Missing Receiver Email** | Low | `alertEmail` is a required parameter with a default in `dev.bicepparam`; deployment warns if blank. |

---

## 8. Rollback

- **Revert Git Commit:** Reverting the Bicep changes removes the alerting module from `main.bicep`.
- **Azure Resource Cleanup:** If needed, deleting the alerting resources leaves all workload apps, databases, and logs completely unaffected:
  ```bash
  az monitor scheduled-query delete -g rg-infinevo-dev --name alert-payrun-failure-dev
  az monitor action-group delete -g rg-infinevo-dev --name ag-infinevo-dev-oncall
  ```
- **Zero Workload Impact:** Alert rules read logs and metrics passively; their addition or removal never stops or restarts containers.

---

## 9. Done When

1. `infra/azure/modules/alerting.bicep` is created, defining:
   - Action Group `ag-infinevo-{env}-oncall`
   - Sev-1 Scheduled Query Alert for Pay Run failures (`alert-payrun-failure-{env}`)
   - Sev-2 Scheduled Query Alert for 5xx error rate spikes (`alert-5xx-spikes-{env}`)
   - Sev-2 Metric Alert for Container App restarts (`alert-container-restart-{env}`)
   - Sev-2 Metric Alert for PostgreSQL active connection saturation (`alert-postgres-connections-{env}`)
   - Sev-2 Metric Alert for Key Vault 403 Forbidden bursts (`alert-keyvault-unauthorized-{env}`)
2. `infra/azure/main.bicep` compiles and lints cleanly with `alerting` module wired.
3. `infra/azure/parameters/dev.bicepparam` specifies `alertEmail`.
4. `infra/azure/ONCALL_PLAYBOOK.md` is published with severity matrix and triage steps for a failed pay run.
5. Check 10 is added to `infra/azure/verify-live.sh` and passes cleanly against live Azure.

---

## Decisions Settled (Founder Approved 2026-09-23)

1. **On-Call Notification Receiver:**
   - **Settled: Option (b)** — Configured with shared on-call alert email: `alerts@infinevocloud.com` in `dev.bicepparam`.
2. **Pay Run Alert Frequency & Sliding Window:**
   - **Settled: Option (a)** — 5-minute frequency over a 15-minute sliding window. Absorbs Log Analytics ingestion latency while ensuring critical pay run failures alert within ~3–5 minutes.
