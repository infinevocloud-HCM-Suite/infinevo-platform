# W-52 — Queue & Worker

> Asynchronous Queue Integration (Azure Storage Queue / Azurite), Background Job Dispatch, Job Status & Progress Tracking, and Distributed Scheduler Locking (ShedLock).
> Derived from `TEMPLATE.md` and `TEMPLATE-INFRA.md`.

| Field | Value |
|---|---|
| **Work item** | `W-52` · issue [#72](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/72) |
| **Kind** | Backend / Infra |
| **Stream / track** | Stream G — Infrastructure · Track I |
| **Wave** | Wave 2 — Data platform |
| **Size / skill** | M · BE |
| **Owner** | KarmaveerM |
| **Blocked by** | `W-50` ([#70](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/70)) · `W-51` ([#71](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/71)) |
| **Blocks** | `W-29` (Pay run execution) · `W-20` (Asynchronous notification delivery) · Horizontal scaling of `worker` container |
| **Capabilities** | `PLAT-04` — asynchronous job queue and worker subsystem |
| **Decisions** | `D-02` (Worker on same image, isolated role) · `D-10` (Azure Container Apps) · `D-18` (India region) · `D-19` (10 × 100 scale) · `D-48` (Unified backend image) · `D-50` (Azure Storage Queue replaces Service Bus) · `D-46` (No `ddl-auto`) |
| **Gaps addressed** | `DEBT-021` (Two `@Scheduled` cron jobs with no distributed lock — `ITDeclarationAutoLockScheduler`, `POIReminderScheduler`; duplicate execution on multiple replicas) |
| **Status** | **Approved** |
| **Approved by** | Founder |
| **Approved on** | 2026-09-21 |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 row 21 (§1 Jobs row, §2 `/jobs/{jobId}` row, §3 queue seams). The spec now follows the code on `main`; the gaps are listed in §11 and are `W-52.1`'s |

> **Hard rule 1:** No code is written until this spec is approved by the founder. (Approved 2026-09-21)

---

## 1. Problem

Long-running and scheduled operations are uncoordinated and synchronous across the platform:

1. **No Queue Mechanism (`docs/target-state/06-current-to-target.md:22,50`)**:
   Heavy operations like payroll calculations, document imports, and reporting execute synchronously on incoming HTTP request threads in `app`. Slow queries and complex calculations frequently trigger browser request timeouts.
2. **Unlocked Schedulers & Duplicate Execution (`DEBT-021`, `legacy/docs/GAP_INVENTORY.md:71`)**:
   In legacy `Payroll-Bend-SBoot`:
   - `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/scheduler/ITDeclarationAutoLockScheduler.java:24` runs daily at `00:00:00 UTC` without locks.
   - `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/scheduler/POIReminderScheduler.java:17` runs daily at `09:00:00 UTC` without locks.
   In `legacy/HRMS_Backend`:
   - `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/service/TimesheetReminderService.java:37,83,148` executes timesheet reminders on local cron schedules without coordination.
   - `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/scheduler/NotificationSchedular.java:54` polls reminders every 60 seconds.
   **Failure mode**: If more than one backend container replica is active, every replica fires the scheduled job simultaneously, sending duplicate emails to employees and running duplicate database lock transactions.
3. **Queue Broker Trade-offs Under `D-50` (`docs/target-state/07-decisions.md:62`)**:
   Azure Storage Queue replaced Service Bus to allow private endpoints on the Standard tier. Storage Queue provides at-least-once delivery with no broker-side message deduplication, no FIFO ordering, and a 64 KB message payload limit. Idempotency must be enforced in application code.

**Baseline — measured on `main` before this ticket opens:**

| Check | Target | Current State |
|---|---|---|
| Queue client dependencies | `code/backend/pom.xml` | Absent (`azure-storage-queue` and `shedlock` not present) |
| Active worker daemon | `code/backend/worker` | Empty shell entrypoint (`InfinevoWorkerApplication.java`) |
| Schedulers in `code/backend` | `code/backend/` | Zero active schedulers |
| Distributed lock table | Postgres schema `core` | Absent |

---

## 2. Scope

### In Scope
1. **Queue Abstraction Layer (`shared` / `core`)**:
   - Clean interface `QueueProducer` and `QueueConsumer` decoupling business logic from Azure SDK.
   - Generic message envelope `QueueMessage<T>` containing `messageId`, `tenantId`, `correlationId`, `payload`, `timestamp`, and `retryCount`.
2. **Azure Storage Queue Implementation**:
   - Driver backed by `com.azure:azure-storage-queue:12.24.1` (or latest Spring Cloud Azure / Azure SDK BOM).
   - Authentication via `DefaultAzureCredential` (Managed Identity `id-app` / `id-worker` in Azure) with connection string / local credential fallback for Azurite (`D-50`).
   - Three standard queues created: `payrun`, `import`, `report` (`infra/azure/modules/storage.bicep:104-108`). A fourth, `notification`, is added by `W-20.1` (`12-core-contracts.md` §3, "Enqueue a job"); it is not this ticket's.
3. **Job Status & Progress Subsystem**:
   - `core.job_status` table tracking `job_id`, `tenant_id`, `queue_name`, `status` (`QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`), `progress_percentage` (0–100), `result_payload`, and `error_message`.
   - Status polling endpoint on `app`: `GET /api/v1/jobs/{jobId}`.
4. **Distributed Scheduler Locking (ShedLock)**:
   - `net.javacrumbs.shedlock:shedlock-spring` + `shedlock-provider-jdbc-template` configured against `core.shedlock` table in PostgreSQL.
   - Configuration ensures `@Scheduled` jobs execute only when `INFINEVO_ROLE=worker` and lock is held.
   - Porting `ITDeclarationAutoLockScheduler` and `POIReminderScheduler` logic from legacy to `code/backend/worker`.
5. **Idempotency & Payload Protection**:
   - Check `core.job_status` before processing to drop duplicate deliveries.
   - Payload guard: payloads exceeding 48 KB reject with instructions to store in Blob storage and pass blob reference URI.

### Out of Scope
- Implementation of the full payroll calculation engine (belongs to `W-29`).
- Bulk CSV/Excel file parsing logic (belongs to `W-21` / `W-28`).
- Service Bus topics/subscriptions or scheduled messages (prohibited by `D-50`).
- Frontend UI components (frontend polling hook integration belongs to the respective feature tickets `W-29` and `W-20`).

---

## 3. Flow

### A. Asynchronous Job Dispatch & Execution Flow
```
[User / Client]
       │
       ▼ 1. POST /api/v1/payruns (or import/report)
[app Container (REST)]
       │
       ├─► 2. Save JobStatus(id, tenant_id, "QUEUED", 0%) in core.job_status
       ├─► 3. QueueProducer.send("payrun", message)
       ▼
[Azure Storage Queue: payrun]
       │
       ▼ 4. Polled by worker daemon
[worker Container (Batch)]
       │
       ├─► 5. Atomic check & update: status="RUNNING", progress=10%
       ├─► 6. Execute payload processing (pay run / report)
       ├─► 7. Periodic progress updates (e.g. 50% ... 100%)
       ├─► 8. Update status="COMPLETED", store result summary
       ▼ 9. Delete message from queue
```

### B. Distributed Scheduler Flow (ShedLock across Multi-Replica Workers)
```
[Clock Trigger: 00:00 UTC]
       │
   ┌───┴───────────────────────────────┐
   ▼                                   ▼
[worker Replica 1]                 [worker Replica 2]
   │                                   │
   ▼ 1. Try acquire lock               ▼ 1. Try acquire lock
   │    in core.shedlock               │    in core.shedlock
   │                                   │
   ├─► ACQUIRED                        └─► REJECTED (Already locked by Replica 1)
   │                                       │
   ▼ 2. Run ITDeclarationAutoLock          ▼ 2. Silently skip execution
   │    (Auto-locks declarations)
   ▼ 3. Release lock (or hold until lockAtMostFor)
```

---

## 4. Backend Changes

### Implementer Tasks & Modules Touched

| Module | Task ID | File / Package | Change Description |
|---|---|---|---|
| `code/backend/shared` | **T1** | `com.infinevo.shared.queue.QueueMessage<T>` | Standard immutable queue payload envelope with metadata — **as built**; `QueueMessage.of(jobId, tenantId, queue, payload)` is the seam every module uses (`12-core-contracts.md` §3) |
| `code/backend/shared` | **T1** | `com.infinevo.shared.queue.QueueProducer` | Interface for queue dispatch (`send(queueName, message)`) — as built, `shared/.../queue/QueueProducer.java:6` |
| `code/backend/shared` | **T1** | `com.infinevo.shared.queue.QueueConsumer<T>` | Interface for queue listeners: `getQueueName()`, `onMessage(QueueMessage<T>)` — as built, `shared/.../queue/QueueConsumer.java:18` |
| `code/backend/core` | **T1** | `com.infinevo.core.queue.*` | **Built as a duplicate of the `shared` package** — `core/.../queue/QueueProducer.java:7` only extends the `shared` one, and `core/.../queue/QueueMessage.java` is a copy nothing imports. Removed by `W-53.1` with the cache cleanup; nothing new may import `core.queue` |
| `code/backend/core` | **T2** | `com.infinevo.core.job.JobStatus` | Entity mapping `core.job_status` (`tenant_id`, `status`, `progress`) |
| `code/backend/core` | **T2** | `com.infinevo.core.job.JobStatusRepository` | Spring Data JPA repository for job tracking with tenant RLS |
| `code/backend/core` | **T2** | `com.infinevo.core.job.JobService` | Service to create, update progress, and fetch job records |
| `code/backend/shared` | **T3** | `com.infinevo.shared.queue.AzureStorageQueueProducer` | Azure Storage Queue client implementation |
| `code/backend/app` | **T4** | `com.infinevo.app.controller.JobStatusController` | REST API exposing `GET /api/v1/jobs/{jobId}` |
| `code/backend/worker` | **T5** | `com.infinevo.worker.config.StorageQueueConfig` | Queue listener configuration and thread pool manager |
| `code/backend/worker` | **T5** | `com.infinevo.worker.config.SchedulerLockConfig` | ShedLock configuration using `JdbcTemplateLockProvider` |
| `code/backend/worker` | **T5** | `com.infinevo.worker.listener.PayrunQueueListener` | Queue consumer bean for `payrun` queue |
| `code/backend/worker` | **T6** | `com.infinevo.worker.scheduler.WorkerAutoLockScheduler` | Ported IT Declaration auto-lock with `@SchedulerLock` |
| `code/backend/worker` | **T6** | `com.infinevo.worker.scheduler.WorkerPOIReminderScheduler` | Ported POI reminder scheduler with `@SchedulerLock` |
| `code/backend/migration` | **T7** | `db/migration/core/V006__job_status_and_shedlock.sql` | Flyway DDL for `core.job_status` and `core.shedlock` — **`V006`, not `V003`**: `V003`–`V005` are `reference` scripts on `main` and Flyway runs one sequence across all four locations |

### API Contract (`code/backend/app`)

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/jobs/{jobId}` | Header: `Authorization: Bearer <JWT>` | `200 OK` + `JobStatusResponseDTO` | `@RequiresAction("core.job.read")` — code added by `W-11.3` (`12-core-contracts.md` §4); **not on `main` yet**, see §11 |

The tenant comes from `TenantContext.require()` and from nowhere else. The built controller
also accepts an `organizationId` header (§11); that fallback is removed, not kept.

**JobStatusResponseDTO**:
```json
{
  "jobId": "b18a20d4-1a9e-4c7b-82a1-1234567890ab",
  "queueName": "payrun",
  "status": "RUNNING",
  "progressPercentage": 45,
  "errorMessage": null,
  "createdAt": "2026-09-21T18:00:00Z",
  "updatedAt": "2026-09-21T18:01:15Z"
}
```

---

## 5. Frontend Changes

*None.* This is a backend platform ticket (`skill-INFRA` / `BE`). Frontend polling hooks will be wired during ticket `W-29` (Pay run UI) and `W-20` (Notifications UI).

---

## 6. Database Changes

> **Flyway only. Never `ddl-auto`.**

### Migration Script: `V006__job_status_and_shedlock.sql` — as built on `main`
Target schema: `core`. The file is
`code/backend/migration/src/main/resources/db/migration/core/V006__job_status_and_shedlock.sql`;
the spec follows it (`12-core-contracts.md` §5 row 21). Three things differ from the first
draft of this section, and the built form is the right one:

| Draft said | Built (`V006`) | Why the built form stands |
|---|---|---|
| `V003` | `V006` (`:1`) | one Flyway sequence across four locations; `V003`–`V005` are `reference` |
| `tenant_id VARCHAR(64)` | `tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id)` (`:8`) | `D-58` — every tenant column is a `uuid` FK to `core.tenant` |
| policy on `app.current_tenant`, `TO app_user`, no RLS on `shedlock` | policy `tenant_isolation` in the exact `CASE` form on `app.current_tenant_id` (`:21-35`); `shedlock` **is** under RLS and readable only when no tenant is bound (`:49-61`) | the `CASE` form is the platform standard (`migration/README.md:76-123`); the `shedlock` policy stops a tenant request touching cluster locks |

```sql
-- V006__job_status_and_shedlock.sql (excerpt — the file on main is authoritative)
CREATE TABLE core.job_status (
    job_id VARCHAR(64) PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    queue_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    progress_percentage INT DEFAULT 0 CHECK (progress_percentage BETWEEN 0 AND 100),
    result_payload TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);
ALTER TABLE core.job_status ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON core.job_status USING (CASE ... END) WITH CHECK (CASE ... END);
CREATE INDEX idx_job_status_tenant_queue ON core.job_status (tenant_id, queue_name);
CREATE INDEX idx_job_status_created ON core.job_status (created_at);

CREATE TABLE core.shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
ALTER TABLE core.shedlock ENABLE ROW LEVEL SECURITY;
-- readable and writable only when app.current_tenant_id is unset or empty (the worker)

GRANT SELECT, INSERT, UPDATE, DELETE ON core.job_status TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON core.shedlock TO app_user;
```

`V024__index_standard_optimizations.sql` (`W-55`) later added to `core.job_status`'s indexes;
this ticket owns none of that.

### Standing Rules Verification Checklist
- [x] `tenant_id uuid` present on `core.job_status`, FK to `core.tenant`, with active RLS policy in the `CASE` form.
- [x] Compound index on `(tenant_id, queue_name)` created.
- [x] Zero floating point types (integers, varchars, timestamps only).
- [x] Explicit schema prefix `core.` on all table and index declarations.
- [x] No destructive steps; grants explicitly awarded to `app_user`.

---

## 7. Tests

| Type | Test Class | Coverage |
|---|---|---|
| **Unit** | `com.infinevo.core.queue.QueueMessageTest` | Serialization, deserialization, size validation (< 48 KB enforcement) |
| **Unit** | `com.infinevo.core.job.JobServiceTest` | Progress transition logic (`QUEUED` → `RUNNING` → `COMPLETED`/`FAILED`) |
| **Integration** | `com.infinevo.shared.queue.AzureStorageQueueIT` | Dispatches message to Azurite Testcontainer queue and asserts successful consumption |
| **Integration** | `com.infinevo.worker.scheduler.SchedulerLockIT` | **Concurrency proof**: Simulates 2 concurrent worker threads attempting the same `@SchedulerLock` task; asserts exactly 1 thread runs and 1 skips |
| **Integration** | `com.infinevo.core.job.JobStatusTenantIT` | Asserts tenant isolation on `core.job_status` under RLS |

**Added for `W-52.1`** (the defects in §11). None of these exists on `main`; each fails against
today's code by construction.

| Type | Test Class | Coverage |
|---|---|---|
| **Integration** | `com.infinevo.worker.queue.ConsumerLoopIT` | A message put on Azurite's `payrun` queue is **received** by the worker's polling loop and reaches `PayrunQueueListener.onMessage`; the message is deleted after success and left visible after failure |
| **Integration** | `com.infinevo.app.queue.AppProducerWiringIT` | With `azure.storage.queue.connection-string` set, `app`'s context holds a `QueueProducer` bean and `send("payrun", …)` lands a message in Azurite |
| **Unit** | `com.infinevo.worker.listener.PayrunQueueListenerTest` (extended) | A redelivery for a job in `RUNNING` is dropped, not re-run — today only `COMPLETED` is dropped |
| **Unit** | `com.infinevo.worker.queue.RetryPolicyTest` | A failing message is retried up to 3 times with `retryCount` incremented; the fourth failure marks the job `FAILED` and deletes the message |
| **Integration** | `com.infinevo.app.controller.JobStatusControllerIT` | `GET /jobs/{id}` without `core.job.read` is `403`; a request carrying another tenant's id in an `organizationId` header is answered from the caller's own tenant, never the header's |

---

## 8. Verification

Verification commands must execute cleanly in local environment and CI:

```bash
# 1. Build and verify backend compilation and unit tests
cd code/backend && ./mvnw -B clean test -pl :core,:shared,:worker,:app

# 2. Run Queue & ShedLock Integration Tests (uses Postgres & Azurite testcontainers)
cd code/backend && ./mvnw -B verify -pl :shared,:worker -Dtest=*IT

# 3. Assert zero ddl-auto occurrences
git grep -nE '^[^#]*ddl-auto[[:space:]]*[:=]' -- code/backend/

# 4. Verify module boundaries: worker must not violate hierarchy
cd code/backend && ./mvnw -B dependency:tree -pl :worker
```

| Check | Expected Output | Verdict |
|---|---|---|
| `SchedulerLockIT` | `Executions count: 1` across 2 worker threads | PASS |
| `AzureStorageQueueIT` | `Message received from queue: OK` | PASS |
| `JobStatusTenantIT` | Tenant A cannot view Tenant B's job record | PASS |
| `ddl-auto` check | Empty (exit code 1 from grep) | PASS |
| Build status | `BUILD SUCCESS` | PASS |

---

## 9. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| **Storage Queue 64 KB limit** (`D-50`) | Medium | Large payloads (e.g. bulk CSV import rows) are uploaded to Blob Storage; queue message carries only the Blob URL and metadata. Payloads > 48 KB throw `PayloadTooLargeException`. |
| **At-least-once duplicate delivery** | Medium | The worker checks `core.job_status` upon receipt. If status is already `RUNNING` or `COMPLETED`, message is dropped and deleted from queue without re-execution. **Built for `COMPLETED` only** (`PayrunQueueListener.java:53`); `RUNNING` is §11 / `W-52.1`. |
| **Clock drift across worker replicas** | Low | ShedLock is configured with `usingDbTime()` so lock acquisition evaluates against PostgreSQL server clock (`CURRENT_TIMESTAMP`), not container system clocks. |
| **Worker container crash mid-job** | Low | Message invisibility timeout is configured (5 minutes). If worker crashes, message becomes visible again and `retryCount` increments; after 3 retries, job is marked `FAILED`. **Not built** — `retryCount` exists on the envelope (`shared/.../queue/QueueMessage.java:25,127`) and nothing reads it; §11 / `W-52.1`. |

---

## 10. Rollback

If this release fails in staging or production:
1. Revert Container App deployment to previous revision (traffic shifts back to previous revision immediately).
2. Database migration `V006` is additive (creates `core.job_status` and `core.shedlock`) and does not modify any existing tables or schemas. Previous code will run completely unaffected.

---

## 11. Known defects on `main`, fixed by `W-52.1`

`W-52` merged (#72) with the seams in place and the loop that uses them missing. Each row is
verified against `main` on 2026-09-25 and is `W-52.1`'s to close; the tests are in §7.

| # | Defect | Evidence | Fix in `W-52.1` |
|---|---|---|---|
| 1 | **No consumer loop.** `QueueConsumer.onMessage` is called only by tests; nothing polls a queue | `grep receiveMessages code/backend` — no hit; the only `onMessage` callers are `worker/src/test/.../PayrunQueueListenerTest.java:42,60,80,111` | A `worker` poller per registered `QueueConsumer`: `QueueClient.receiveMessages` with a 5-minute visibility timeout, dispatch to `onMessage`, `deleteMessage` on success |
| 2 | **`app` cannot enqueue.** The `QueueProducer` bean is declared only in the worker | `worker/.../config/StorageQueueConfig.java:27-31` is the sole `@Bean QueueProducer`; no `app` configuration class produces one | Move `StorageQueueConfig` to `shared` (auto-configured on the connection-string property) so both roles get the producer |
| 3 | **Idempotency skips `COMPLETED` only.** A redelivery during `RUNNING` runs the job twice | `worker/.../listener/PayrunQueueListener.java:53` — `if (state == JobState.COMPLETED)` | Drop on `RUNNING` too; the check and `markRunning` become one conditional update |
| 4 | **No retry, no terminal `FAILED` from redelivery.** `retryCount` is carried and never read | `shared/.../queue/QueueMessage.java:25,127`; the listener's `catch` marks `FAILED` on the first exception (`PayrunQueueListener.java:68-70`) and the message stays on the queue | Poller reads Storage Queue's `dequeueCount`; below 4 it leaves the message to reappear, at 4 it marks `FAILED` and deletes |
| 5 | **`GET /jobs/{jobId}` is unguarded and trusts a header.** No `@RequiresAction`; falls back to an `organizationId` request header for the tenant | `app/src/main/java/com/infinevo/app/controller/JobStatusController.java:28-36` — no annotation; `:31-36` reads `organizationId` when no tenant is bound | `@RequiresAction("core.job.read")` (code from `W-11.3`), `TenantContext.require()` only, header parameter deleted. `EndpointGuardCoverageTest` covers `core` and `shared`; extend it to `app` so this cannot recur |
| 6 | **A fourth queue.** `notification` is not this ticket's, but the poller from row 1 must register consumers by name, not by a fixed list of three | `infra/azure/modules/storage.bicep:104-108` lists three | `W-20.1` adds the queue to Bicep and Azurite and registers its consumer; `W-52.1` builds nothing queue-specific |

Row 5 is a tenant-isolation defect, not a style one: with no tenant bound, a caller naming
another tenant's id in the header would be answered from that tenant's `core.job_status`
row (RLS is keyed on the same value the controller sets). It is listed first in `W-52.1`'s
order of work.
