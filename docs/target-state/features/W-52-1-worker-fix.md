# Feature: Worker fix — a consumer loop that actually runs

| Field | Value |
|---|---|
| **Feature ID** | `W-52.1` · from ticket `W-52` · `12-core-contracts.md` §5 row 21 |
| **Promoted to** | `docs/target-state/features/W-52-1-worker-fix.md` on branch `W-52-1-worker-fix` — **`W-52-1` with hyphens**, never `W-52.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/worker`, `code/backend/shared`, `code/backend/app` |
| **Related gaps** | DEBT-021 (the reason `W-52` exists) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-11.3` — `core.job.read` must exist in the catalogue before `@RequiresAction("core.job.read")` can pass |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `worker` (loop), `shared` (config moves here), `app` (one controller) | 1 (+ exception: the producer bean must exist in both runtimes) |
| Flyway migration | none | 1 |
| Externally testable behaviour | a message put on `payrun` is consumed once, the job reaches `COMPLETED`, and a poison message is marked `FAILED` after three deliveries | 1 |
| Frontend area | none | 1 |

---

## 1. Problem

`W-52` shipped the halves that do not talk to each other:

- `QueueConsumer<T>` is an interface with `getQueueName()` and `onMessage()`
  (`shared/.../queue/QueueConsumer.java:6-18`); `PayrunQueueListener` implements it
  (`PayrunQueueListener.java:20`). **Nothing calls it.** `StorageQueueConfig` builds a
  `QueueServiceClient` and a producer (`StorageQueueConfig.java:17-31`) and no receiver.
- The producer bean is defined in `worker` only (`StorageQueueConfig.java:27-31`); `app`, which
  is where a request would enqueue a job, has no queue configuration at all
  (`app/src/main/java/com/infinevo/app/` holds `InfinevoApplication` and `JobStatusController`).
- Idempotency drops `COMPLETED` only (`PayrunQueueListener.java:53-56`). A second delivery of
  a `RUNNING` job runs it twice — the spec itself says `RUNNING` is dropped
  (`W-52-queue-worker.md:280`).
- `JobStatusController` has no `@RequiresAction` (`JobStatusController.java:28-31`) and takes the
  tenant from an `organizationId` header when no context is bound (`:33-36`) — a header any caller
  can set. `EndpointGuardCoverageTest` does not catch it because it scans `core`'s test classpath
  (`EndpointGuardCoverageTest.java:45`) and this controller is in `app`.

## 2. Scope

**In scope**

- A polling receiver in `worker` that dispatches to the `QueueConsumer<T>` bean for each queue
- The queue configuration moved to `shared` so `app` and `worker` both get the producer
- `RUNNING` treated as already taken; dequeue count ≥ 3 → `markFailed` and delete
- `@RequiresAction("core.job.read")` on `GET /api/v1/jobs/{jobId}`; header fallback removed

**Out of scope**

- New queues or consumers (`import`, `report`, `notification`) — their tickets
- A sweep for jobs stuck in `RUNNING` after a crash — noted in §9
- Migrations — none (`V006` is correct: `tenant_id UUID`, `V006__job_status_and_shedlock.sql:8`; the `V003`/varchar text in `W-52-queue-worker.md:147,190-191` is stale, code wins)
- A local queue emulator: `compose.yml:59-64` runs RabbitMQ under `queue` and Azurite blob-only (`:75-77`); this ticket tests with Testcontainers (§7)

## 3. Flow

```
app  --> QueueProducer.send("payrun", QueueMessage.of(jobId, tenantId, "payrun", payload))
worker QueueConsumerLoop (one thread per registered queue)
     --> QueueClient.receiveMessages(32, visibility 5 min)
     --> for each: dequeueCount >= 3 ? markFailed + delete
                 : parse QueueMessage<String> --> consumer.onMessage --> delete
                   (exception: log, leave the message; it reappears after visibility timeout)
PayrunQueueListener.onMessage --> status RUNNING or COMPLETED ? return : markRunning ... markCompleted
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Config | `shared/.../queue/StorageQueueConfig.java` | Moved from `worker/config/StorageQueueConfig.java:14-32` unchanged (same `@ConditionalOnProperty("azure.storage.queue.connection-string")`). Both runtimes scan `com.infinevo` (`InfinevoApplication.java:19`, `InfinevoWorkerApplication.java:18`) and `app` reaches `shared` through `core` (`core/pom.xml:19`, `app/pom.xml:17`) |
| Loop | `worker/.../queue/QueueConsumerLoop.java` | New. `SmartLifecycle`, same `@ConditionalOnProperty`. Constructor takes `QueueServiceClient`, `ObjectMapper`, `List<QueueConsumer<String>>`, `JobService`. Builds `Map<queueName, consumer>`; two consumers on one queue → fail startup. One daemon thread per queue; `receiveMessages(32, Duration.ofMinutes(5), ...)`; empty batch → sleep `azure.storage.queue.poll-interval` (default `PT1S`); `stop()` interrupts and joins |
| Loop | same | Per message: `getDequeueCount() >= 3` → `jobService.markFailed(jobId, "poison: delivered N times")` and `deleteMessage(id, popReceipt)`, `jobId` read from the raw JSON without full parsing; else `objectMapper.readValue(body, new TypeReference<QueueMessage<String>>(){})` (constructor is `@JsonCreator`, `QueueMessage.java:28-37`) → `consumer.onMessage` → delete. Unparseable body → `markFailed` if a `jobId` is readable, delete either way |
| Listener | `PayrunQueueListener.java:53` | `if (state == COMPLETED \|\| state == RUNNING)` → warn and return |
| Controller | `app/.../JobStatusController.java:28-36` | `@RequiresAction("core.job.read")`; signature `getJobStatus(@PathVariable String jobId)`; tenant = `TenantContext.require()` (`TenantContext.java:50`), bound by `TenantContextFilter` from the token |
| Worker config | `worker/config/StorageQueueConfig.java` | Deleted |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/jobs/{jobId}` | — (no `organizationId` header) | `200` `JobStatusResponseDTO`; `404` when not in the caller's tenant | `core.job.read` |

`JobService` is unchanged (`JobService.java:10-20`); `markRunning` already refuses to regress a
`COMPLETED`/`FAILED` job (`JobServiceImpl.java:34-40`).

## 5. Frontend changes

None.

## 6. Database changes

None.

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | no table |
| Flyway only, `ddl-auto` nowhere | no script; none added |
| Money as `Money` / `BigDecimal` | no money |
| Expand / contract | n/a |
| No module references another | `worker` → `core`, `shared`; `app` → `core`, `shared` — both already so (`worker/pom.xml:17,56`, `app/pom.xml:17`) |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `worker/.../queue/QueueConsumerLoopTest.java` | mocked `QueueClient`: dispatch by queue name; delete after success; no delete after exception; dequeue count 3 → `markFailed` + delete; duplicate queue name fails startup |
| Unit | `worker/.../listener/PayrunQueueListenerTest.java` (extend `:51-62`) | `RUNNING` dropped, `markRunning` never called |
| Unit | `app/.../controller/JobStatusControllerTest.java` (rewrite `:38-57`) | no header parameter; unbound tenant → `TenantContext.require()` throws |
| Integration | `worker/.../queue/QueueRoundTripIT.java` | Testcontainers `mcr.microsoft.com/azure-storage/azurite` with `azurite-queue`; producer sends, loop consumes, job `COMPLETED`; same message re-sent → dropped |
| Integration | `app/.../JobStatusGuardIT.java` | `403` without `core.job.read`; `404` for another tenant's `jobId` |

## 8. Verification

```bash
cd code/backend && mvn -q verify
grep -rn "organizationId" code/backend/app/src/main ; echo "exit=$?"
grep -rn "@ConditionalOnProperty(name = \"azure.storage.queue.connection-string\")" code/backend/*/src/main | wc -l
```

| Check | Expected |
|---|---|
| Suite | green, no skips; the five classes in §7 run |
| `organizationId` grep | no output, `exit=1` |
| Conditional beans | `3` (client, producer, loop), all under `shared` and `worker` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Worker dies mid-job; redelivery finds `RUNNING` and drops it; job never finishes | medium | Accepted for now, as `12-core-contracts.md:158` asks. A stale-`RUNNING` sweep on `worker` under ShedLock is the next `W-52` ticket |
| No local Storage Queue in `compose.yml` | certain | Round-trip covered by Testcontainers; a `queue` swap to `azurite-queue` is an `infra/` ticket |
| `core.job.read` missing → every status call `403` | until `W-11.3` merges | Blocked-by row; `JobStatusGuardIT` proves the grant to `tenant-admin` |

## 10. Rollback

Revert the commit. No schema change; the queues keep their messages until the loop returns.
