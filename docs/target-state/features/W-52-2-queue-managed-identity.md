# Feature: Queue by managed identity — connect the Storage Queue on Azure

| Field | Value |
|---|---|
| **Feature ID** | `W-52.2` · from ticket `W-52` · defect `D-11` |
| **Promoted to** | `docs/target-state/features/W-52-2-queue-managed-identity.md` — **`W-52-2` with hyphens**, never `W-52.2`; `guard-edit` blocks the dotted form |
| **Owner** | karma · branch `dev-karma` (reset to `main` first) |
| **Apps touched** | `code/backend/shared`, `code/backend/worker`, `code/backend/app` (config only), `infra/azure` |
| **Related gaps** | `D-11` (tracker) · `DEBT-004` (no account keys — the reason a connection string cannot be the Azure answer) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-07 |
| **Blocked by** | nothing — the queues, private endpoint, DNS zone and role assignments are already deployed (`W-51`) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `shared` (queue config) · `worker` (one annotation) · `app` (yml only) | 1 (+ exception: the queue client must exist in both runtimes, as `W-52.1`) |
| Flyway migration | none | 1 |
| Externally testable behaviour | on Azure, an email is sent within seconds of being queued instead of after the 2–3 minute sweep | 1 |
| Frontend area | none | 1 |

---

## 1. Problem

The Storage Queue has never been connected on Azure. Every queued email waits for `W-20.2`'s sweep.

| Fact | Evidence |
|---|---|
| `W-52` asked for managed identity, with a connection string only as the Azurite fallback | `W-52-queue-worker.md:64` |
| Only the connection-string path was built | `shared/.../queue/StorageQueueConfig.java:16-18,26` |
| `W-52.1` moved that config "unchanged", carrying the gap into its own spec | `W-52-1-worker-fix.md:76` |
| The consumer loop has the same condition | `worker/.../queue/QueueConsumerLoop.java:30` |
| `W-51` forbids account keys on Azure, so no connection string can be set there | `infra/azure/modules/rbac.bicep:18-20` |
| No ticket passed the queue endpoint to the containers; it is a Bicep output nobody reads into an env var | `infra/azure/modules/storage.bicep:123` · `main.bicep:615` · `containerapps.bicep` has no queue variable |
| Neither container on `rg-infinevo-dev` has a queue or storage-queue env var | `az containerapp show … --query properties.template.containers[0].env[].name`, 2026-10-07 |
| So the email outbox is drained only by the sweep: rows older than 2 minutes, every minute | `worker/src/main/resources/application.yml:91-92` |
| Everything else is in place: queues, private endpoint, `privatelink.queue` zone, `Storage Queue Data Message Sender` for app and worker, `Processor` for worker | `storage.bicep:83-125` · `private-dns.bicep:82` · `rbac.bicep:53-54,113-125` |

The document store already solved the same problem for Blob — connection string for Azurite, endpoint plus managed identity for Azure (`core/.../document/DocumentStorageConfig.java:35-60`, `containerapps.bicep:362-372,515-523`). This ticket copies that pattern.

## 2. Scope

**In scope**

- `StorageQueueConfig` builds the `QueueServiceClient` from **a connection string (Azurite) or an endpoint with the container's managed identity (Azure)**; connection string wins when both are set
- One condition, "queue configured", used by the client, the producer and `QueueConsumerLoop`, true only when either value is **non-blank**
- `azure-identity` on `shared` (it is on `core` today, `core/pom.xml:42-64`; `shared` cannot depend on `core`), with the same exclusions
- Bicep passes the queue endpoint to `app` and `worker`; `AZURE_CLIENT_ID` is already set on both

**Out of scope**

- Tuning the sweep (`WORKER_NOTIFICATION_SWEEP_GRACE`, `…_INTERVAL`) — a separate Bicep-only change; the sweep stays as the safety net
- Queue-depth (KEDA) scaling of the worker — `W-54-deployment-pipeline.md:106` gives it to `W-52`; not this correction
- An Azurite queue in `compose.yml` — local runs keep using the sweep, as today (`W-52-1-worker-fix.md:58`)
- New queues or consumers

## 3. Flow

```
app/worker start
  azure.storage.queue.connection-string non-blank --> QueueServiceClient by connection string   (Azurite)
  else azure.storage.queue.endpoint non-blank      --> QueueServiceClient by endpoint
                                                      + ManagedIdentityCredential(AZURE_CLIENT_ID) (Azure)
  else                                              --> no client, no producer, no loop; sweep only (today)

app  NotificationServiceImpl --after commit--> QueueProducer.send("notification", id)
worker QueueConsumerLoop --> NotificationDeliveryConsumer.deliver --> Brevo        (seconds)
worker NotificationDeliverySweep (unchanged)                                       (safety net)
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Condition | `shared/.../queue/QueueConfiguredCondition.java` | **New.** A Spring `Condition`: true when `azure.storage.queue.connection-string` **or** `azure.storage.queue.endpoint` is set and not blank. Not `@ConditionalOnProperty`: it matches an empty string, and the yml below defaults both keys to empty |
| Config | `shared/.../queue/StorageQueueConfig.java` | `queueServiceClient` and `queueProducer` move to `@Conditional(QueueConfiguredCondition.class)`. The client: connection string → `connectionString(…)`; else `endpoint(…)` + `credential(new ManagedIdentityCredentialBuilder().clientId(…).build())`, `clientId` from `azure.storage.queue.managed-identity-client-id` and skipped when blank. `messageEncoding(BASE64)` on both paths. Log once, never the connection string: `Queue: Storage Queue by connection string (an emulator)` or `Queue: Storage Queue at {endpoint} by managed identity`. Class comment states the three cases, as `DocumentStorageConfig.java:11-28` does |
| Loop | `worker/.../queue/QueueConsumerLoop.java:30` | `@ConditionalOnProperty(name = "azure.storage.queue.connection-string")` → `@Conditional(QueueConfiguredCondition.class)` |
| Producer | `shared/.../queue/AzureStorageQueueProducer.java:31-38` | Unchanged. The `(String connectionString, ObjectMapper)` constructor stays: `core/src/test/.../notification/NotificationTestApp.java:74` builds an Azurite producer with it |
| Build | `shared/pom.xml` | Add `com.azure:azure-identity` with the exclusions copied from `core/pom.xml:45-63`; version from the BOM already in use. Remove it from `core/pom.xml` only if `core` then gets it transitively and `mvn dependency:tree` shows one copy |
| Config | `app/src/main/resources/application.yml` · `worker/src/main/resources/application.yml` | Under `azure.storage.queue`: `connection-string: ${AZURE_STORAGE_QUEUE_CONNECTION_STRING:}`, `endpoint: ${AZURE_STORAGE_QUEUE_ENDPOINT:}`, `managed-identity-client-id: ${AZURE_CLIENT_ID:}`. Keep the worker's `poll-interval` and `visibility-timeout` |

No API change.

## 5. Infrastructure changes

| File | Change |
|---|---|
| `infra/azure/modules/containerapps.bicep` | New `param queueEndpoint string = ''` with a `@description` like `blobEndpoint`'s (`:58`). Add `AZURE_STORAGE_QUEUE_ENDPOINT = queueEndpoint` to the app container env (beside `DOCUMENT_BLOB_ENDPOINT`, `:366`) and the worker container env (`:517`), with a comment naming `W-52.2` and the role that makes it work (`rbac.bicep:113-125`) |
| `infra/azure/main.bicep` | Pass `queueEndpoint: storage.outputs.primaryQueueEndpoint` to the `containerApps` module (`:338-357`) |

No new role, endpoint or DNS zone: all exist.

**The deploy pipeline does not run Bicep.** `deploy.yml` only merges a few env vars with `az containerapp update --set-env-vars` (`.github/workflows/deploy.yml:641-643`), so `AZURE_STORAGE_QUEUE_ENDPOINT` reaches `dev` when the founder runs the Bicep — as `DOCUMENT_BLOB_ENDPOINT` did. Do not add it to `deploy.yml`: Bicep is where the containers' settings live.

## 6. Frontend changes

None.

## 7. Database changes

None.

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | no table |
| Flyway only, `ddl-auto` nowhere | no script |
| Money as `Money` / `BigDecimal` | no money |
| No module references another | `shared` gains a library, not a module; `worker` → `shared` already |

## 8. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `shared/.../queue/QueueConfiguredConditionTest.java` | neither set → false; both blank strings → false; connection string only → true; endpoint only → true |
| Unit | `shared/.../queue/StorageQueueConfigTest.java` | `ApplicationContextRunner`: endpoint only → `QueueServiceClient` and `QueueProducer` beans exist and the client's account URL is the endpoint (building a client opens no connection); connection string and endpoint → connection string used; neither → no beans; the connection string never appears in captured logs |
| Unit | `worker/.../queue/QueueConsumerLoopConditionTest.java` | `ApplicationContextRunner`: endpoint only → loop bean present; neither → absent |
| Integration | `worker/.../queue/QueueRoundTripIT.java` (existing) | still green on the connection-string path (Azurite) |
| Infra | `infra/azure` what-if in CI | both container apps gain `AZURE_STORAGE_QUEUE_ENDPOINT`; no other env change |

The managed-identity path cannot run in CI (no identity there); §9 proves it on `dev`.

## 9. Verification

```bash
cd code/backend && mvn -q verify
grep -rn 'ConditionalOnProperty(name = "azure.storage.queue.connection-string")' code/backend/*/src/main ; echo "exit=$?"
grep -n "AZURE_STORAGE_QUEUE_ENDPOINT" infra/azure/modules/containerapps.bicep
```

After the branch is on `main` **and the founder has run the Bicep** (§5):

```bash
az containerapp logs show -n ca-infinevo-dev-app    -g rg-infinevo-dev --type console --tail 500 | grep "Queue: Storage Queue at"
az containerapp logs show -n ca-infinevo-dev-worker -g rg-infinevo-dev --type console --tail 500 | grep "Queue: Storage Queue at"
```

Then send one invitation and run:

```sql
SELECT status, updated_at - queued_at AS delay
FROM core.notification
WHERE channel = 'EMAIL'
ORDER BY queued_at DESC
LIMIT 5;
```

| Check | Expected |
|---|---|
| Suite | green; the three new classes run |
| Old condition grep | no output, `exit=1` |
| Bicep grep | two lines (app, worker) |
| Startup logs | one `Queue: Storage Queue at https://stinfinevodev.queue.core.windows.net/ by managed identity` line in each container; no `403 AuthorizationPermissionMismatch` |
| Delay | newest invitation `SENT`, `delay` under 15 seconds (today 2–3 minutes) |

## 10. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The private endpoint DNS does not resolve from the container environment, so sends time out | low — Blob already works the same way through `privatelink.blob` | The producer runs after commit and a failure leaves the row `QUEUED`; the sweep still sends it (`NotificationServiceImpl.java:38-54`). Check the §9 startup log and one send |
| Every queue's consumers start on Azure for the first time (`payrun`, `import`, `report`, `notification`) and something dormant now runs | medium | Each consumer is idempotent by design (`W-52.1` claim, `NotificationDeliveryConsumer` claim). Read the worker log for one hour after deploy |
| An empty env var made `@ConditionalOnProperty` match and a client built with no address | removed | `QueueConfiguredCondition` checks for non-blank |

## 11. Rollback

Set `AZURE_STORAGE_QUEUE_ENDPOINT` to empty (or revert the Bicep change) and redeploy: no client is built and email returns to the sweep, as today. No schema change.
