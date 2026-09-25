# Feature: Pay run — asynchronous execution on the worker, status and progress

| Field | Value |
|---|---|
| **Feature ID** | `W-29.4` · from ticket #36 · `PAY-05` part 4 of 4 · `PLAT-04` |
| **Promoted to** | `docs/target-state/features/W-29-4-pay-run-async-worker.md` on the developer's `dev-<name>` branch — **`W-29-4` with hyphens**, never `W-29.4`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/worker` (the runtime that hosts it), `code/backend/migration` |
| **Related gaps** | DEBT-020 (discounted), DEBT-018; D-2 (`W-52.1`) is the blocker |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-29.3` — the computation this ticket moves · `W-52.1` — the consumer loop, the producer bean in `app`, and `RUNNING` treated as taken (D-2). Without it the worker never reads the queue |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll`. `worker` is a runtime, not a module: its one listener already exists (`code/backend/worker/src/main/java/com/infinevo/worker/listener/PayrunQueueListener.java`) and gains a body | 1 |
| Flyway migration | `V059` — progress and job columns on `payroll.payrun`, expand only | 1 |
| Externally testable behaviour | compute returns at once; the worker computes; the officer watches the count climb and the run reach `COMPUTED`; a duplicate or redelivered message never computes twice; a run abandoned mid-way can be resumed | 1 |
| Frontend area | none — `W-47` | 1 |

Within cap.

---

## 1. Problem

Nothing in the frozen system is asynchronous. `createPayRun` generates every employee's pay
inside the request (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/payruns/PayRunServiceImpl.java:413-440`),
in one transaction, with no `@Async`, no thread pool and no scheduler anywhere in the
`payruns` package (`.claude/outputs/2026-09-25-analyze-w29-pay-run.md` §4). There is no
progress: the caller waits, and a timeout leaves a half-written run with no way to tell.

The platform has the other half already. `W-52` shipped the `payrun` queue, `core.job_status`
with `progress_percentage` (`code/backend/migration/src/main/resources/db/migration/core/V006__job_status_and_shedlock.sql:6-16`),
`JobService` (`core/.../job/service/JobService.java:10-20`), `GET /api/v1/jobs/{jobId}`, and a
listener whose body is a `log.debug` (`PayrunQueueListener.java:76-78`). `W-29.2` §13
decision 5 promised this ticket would put the computation behind it.

`09-build-order.md:219`: *done when a 100-employee run completes on the worker with progress
visible. Watch: the run must be restartable and locked.*

## 2. Scope

**In scope**

- `POST /compute` enqueues and returns `202` with the job id, instead of computing inline
- The worker listener calls `W-29.2`'s `PayRunComputationService`
- Progress: employees done and total on the run, and `progress_percentage` on the job
- Idempotency: a duplicate message, a redelivery, or a second `POST` while computing never runs the loop twice
- Resume: a run left `COMPUTING` by a dead worker can be re-triggered and picks up where it stopped

**Out of scope**

- The consumer loop, poison handling, `RUNNING` guard — `W-52.1`
- A sweep that auto-detects stale `RUNNING` jobs — `W-52.1` §9 names it as the next `W-52` ticket; here the officer re-triggers
- Notifying anyone that a run finished or failed — `W-20.1`; the Sev-1 alert on a `FAILED` job already exists (`W-61` §2)
- Cancelling a run while it computes — `W-29.1` allows cancel from `DRAFT` and `LOCKED` only; a computing run finishes or fails first
- Screens — `W-47`

## 3. Flow

```
[officer] --> POST /payruns/{id}/compute --> payroll.run.execute
   --> transition to COMPUTING (W-29.1 rules; from COMPUTING only when stale, below) — this row lock is the double-enqueue guard
   --> compute_attempt += 1, compute_started_at = now, progress_done = 0, progress_total = included_count
   --> jobId = "payrun-" + payrunId + "-" + compute_attempt
   --> JobService.createJob(jobId, tenant, "payrun", {payrunId})            (core, built)
   --> QueueProducer.send("payrun", QueueMessage.of(jobId, tenant, "payrun", {payrunId}))   (shared; bean in app after W-52.1)
   --> 202 { job_id, status: COMPUTING }

[worker] PayrunQueueListener.onMessage
   --> W-52.1: COMPLETED or RUNNING ? drop : markRunning
   --> PayRunComputationService.compute(payrunId, attempt, progress -> { jobService.updateProgress(jobId, pct); payrun.progress_done = n })
        for each INCLUDED row where computed_attempt <> attempt:        <-- resume: rows done by a dead worker are skipped
            delete that employee's lines; run the contributors; write; computed_attempt = attempt   (REQUIRES_NEW, W-29.2)
            every 10 employees, or on the last: report progress
   --> run totals; COMPUTED, or FAILED with the failed-row count      --> markCompleted / markFailed

[officer] --> GET /payruns/{id}      --> status, progress_done / progress_total, job_id, compute_attempt
          --> GET /jobs/{job_id}     --> W-52's view of the same thing (core.job.read)
```

**Stale rule:** `POST /compute` on a `COMPUTING` run is `409` unless the job's `updated_at`
(`core.job_status`, touched on every progress report) is older than **15 minutes**. Then the
run is treated as abandoned: a new attempt starts, and rows already carrying the previous
attempt number are **not** recomputed — the resume. The dead worker's message, if it ever
reappears, finds `RUNNING`/`COMPLETED` on the old job id and is dropped (`W-52.1` §3).

**Failure inside the loop** is per employee, as `W-29.2` §3: the row keeps
`computation_error`, the rest proceed, the run ends `FAILED`, and the job is `markFailed`
with the count — which is what trips the `W-61` alert.

## 4. Backend changes

| Area | File | Change |
|---|---|---|
| `payroll` | `payrun/PayRunServiceImpl.java` (`W-29.1`) | `compute(id)` now enqueues per §3; the stale rule; `202` |
| `payroll` | `payrun/PayRunComputationService(Impl)` (`W-29.2`, `W-29.3`) | signature `compute(UUID payrunId, int attempt, ProgressReporter reporter)`; per-employee line delete replaces the run-wide delete; skip rows already at this attempt; report every 10 |
| `payroll` | `payrun/ProgressReporter.java` | new — `void report(int done, int total)` |
| `payroll` | `payrun/PayRunJobPayload.java` | new — `{payrunId}`, serialised by the `shared` `ObjectMapper`; well under `MAX_PAYLOAD_BYTES` (`QueueMessage.java:17`) |
| `payroll` | entity `PayRun`, `EmployeePayRun` | `jobId`, `computeAttempt`, `computeStartedAt`, `progressDone`, `progressTotal`; `computedAttempt` on the row |
| `payroll` | DTO `PayRunResponse`, `ComputeAcceptedResponse` | the progress fields; `{job_id, status}` |
| `worker` | `listener/PayrunQueueListener.java:76-78` | `processPayrunPayload` parses `PayRunJobPayload` and calls `PayRunComputationService.compute` with a reporter that calls `jobService.updateProgress`. Constructor gains the service — `worker` already depends on `payroll` (`worker/pom.xml:19`) |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/payroll/payruns/{id}/compute` | — | **`202`** `{job_id, status: COMPUTING, compute_attempt}`; `409` from `DRAFT`, `APPROVED`, `PAID`, `CANCELLED`, and from `COMPUTING` unless stale | `payroll.run.execute` |
| GET | `/api/v1/payroll/payruns/{id}` (`W-29.1`) | — | gains `job_id`, `compute_attempt`, `progress_done`, `progress_total`, `compute_started_at` | `payroll.run.read` |

`GET /api/v1/jobs/{jobId}` is unchanged and needs `core.job.read`; the `payroll-officer` role
must hold it — `W-52.1`'s `JobStatusGuardIT` proves the grant to `tenant-admin`; this ticket
adds the officer row in its verification and, if missing, as a `V059` insert into
`reference.role_action` per `V025__catalogue_correction.sql`'s pattern.

## 5. Frontend changes

None. `W-47` polls `GET /payruns/{id}` for the bar.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V059__payrun_job_progress.sql` | `ALTER payroll.payrun`, `ALTER payroll.employee_payrun` | yes (existing tables) | additive |

`V059` extends the payroll lane's block by one (`DEV-TRACKER.md` § lanes).

**`payrun` gains:** `job_id varchar(64)` · `compute_attempt int NOT NULL DEFAULT 0` ·
`compute_started_at timestamptz` · `progress_done int NOT NULL DEFAULT 0` ·
`progress_total int NOT NULL DEFAULT 0`.
**`employee_payrun` gains:** `computed_attempt int NOT NULL DEFAULT 0`.

No legacy field maps here; nothing like it existed (§1). `job_id` is a soft reference to
`core.job_status.job_id` (`varchar(64)`, `V006:7`) — no FK across schemas, the `W-52` convention.

- [x] No new table; `tenant_id` and RLS already on both
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `idx_payrun_tenant_job (tenant_id, job_id)` for the job-to-run lookup the worker does
- [x] No money column; counters are `int`
- [x] Expand / contract — columns with defaults only

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../payrun/PayRunEnqueueTest.java` | `compute` from `LOCKED` creates the job, sends one message, sets `COMPUTING`; a second call is `409`; a call on a `COMPUTING` run whose job `updated_at` is 16 minutes old starts attempt 2 and sends again; producer failure leaves the run `LOCKED` (the transition rolls back) |
| Unit | `payroll/.../payrun/PayRunResumeTest.java` | 5 rows, 2 already at attempt 2: `compute(id, 2, …)` computes 3, reports `5/5`, never touches the 2 |
| Unit | `worker/.../listener/PayrunQueueListenerTest.java` (extend) | the payload reaches `PayRunComputationService.compute` with the job's attempt; progress reports become `updateProgress` calls with the right percentages; an exception becomes `markFailed` |
| Integration | `payroll/.../payrun/PayRunAsyncIT.java` | **the acceptance test:** 100 included employees, `POST /compute` returns `202` in under a second, `PayrunQueueListener.onMessage` is invoked in-process with the real message, the run ends `COMPUTED`, `progress_done = 100`, the job is `COMPLETED` at `100`, and `W-29.3`'s hand-calculation employee still nets `33,733.87` |
| Integration | `payroll/.../payrun/PayRunDuplicateMessageIT.java` | the same message delivered twice: one set of lines, one `computed_at` per row, no duplicate line (the unique index would say so) |
| Integration | `payroll/.../payrun/PayRunResumeIT.java` | the listener is killed after 40 rows (the reporter throws on the 41st); the run is `COMPUTING`, 40 rows at attempt 1; the job's `updated_at` is backdated 16 minutes; `POST /compute` starts attempt 2; the second delivery computes 60 and the run is `COMPUTED` with exactly 100 rows at attempt 2 |
| Integration | `payroll/.../payrun/PayRunWorkerRlsIT.java` | the listener bound to tenant A with a payload naming tenant B's run: `404`-shaped failure, no line written; `job_status` `FAILED` |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. The listener tests call
`onMessage` directly, as `W-52`'s do (`worker/.../listener/PayrunQueueListenerTest.java`); the
real queue is exercised in §8, not in the suite.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres azurite
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='payrun'
      AND column_name IN ('job_id','compute_attempt','progress_done','progress_total');"
cd code/backend && mvn -q verify
grep -rn "PayRunComputationService" code/backend/worker/src/main/java | wc -l
grep -rn "Thread\|@Async\|ExecutorService" code/backend/payroll/src/main/java/com/infinevo/payroll/payrun | wc -l

# Through the real queue, app and worker both up:
docker compose -f infra/docker/compose.yml up -d app worker
# seed a tenant, 100 employees with salaries, a schedule and a policy (infra/docker/seed/seed.sh, then the W-26.2 seed)
# create + lock (W-29.1), then:
curl -s -X POST -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/payroll/payruns/$RUN/compute
watch -n1 'curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/payroll/payruns/'$RUN' | jq "{status,progress_done,progress_total}"'
```

| Check | Expected |
|---|---|
| Columns | four rows, `character varying` and `integer` |
| Suite | green, no skips; `PayRunAsyncIT` completes 100 rows; `PayRunResumeIT` ends with 100 rows at attempt 2 |
| Worker wiring | `1` — the listener is the only caller |
| Home-grown threading | `0` — the queue is the only asynchrony |
| Live | `202` returns at once; `progress_done` climbs in steps of 10 to 100; status `COMPUTED`; `GET /jobs/{job_id}` shows `COMPLETED`, `100` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The computation is copied into `worker` instead of called there | medium | The listener body is one call; the grep expects one reference and `worker` has no `payrun` package |
| Two workers pick the same message and compute twice | medium | `W-52.1`'s `RUNNING` guard on the job **and** the `COMPUTING` transition on the run; `PayRunDuplicateMessageIT` |
| A worker dies and the run is `COMPUTING` forever | medium | The 15-minute stale rule and per-row `computed_attempt`; `PayRunResumeIT`. The automatic sweep is `W-52`'s next ticket, named in §2 |
| Progress reported per employee floods `job_status` with 100 updates | low | Every 10 and on the last; `PayRunAsyncIT` counts `updateProgress` calls ≤ 11 |
| The producer sends before the transaction commits, and the worker reads a run still `LOCKED` | medium | Send after commit (`TransactionSynchronization.afterCommit`); `PayRunEnqueueTest` asserts the order; the worker treats a run not in `COMPUTING` as a drop with a warning |
| `app` has no producer bean until `W-52.1` | certain until then | Blocked-by row; the compose check in §8 cannot pass without it |

## 10. Rollback

Nothing is deployed. The script is additive. If the worker path misbehaves, a run is
re-triggered after the stale window; no data is lost because every employee commits alone.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | no new table; the worker binds `TenantContext` from the message (`PayrunQueueListener.java:48`) so RLS holds on the worker connection |
| Flyway only, `ddl-auto` nowhere | one script, `V059` |
| `Money`/`BigDecimal` for money | no money touched; the computation is `W-29.2`'s and `W-29.3`'s |
| Index on `tenant_id` plus lookup columns | `idx_payrun_tenant_job` |
| Expand / contract | columns with defaults only |
| No module references another module | `payroll` uses `core`'s `JobService` and `shared`'s `QueueProducer`; `worker` is the runtime and already depends on `payroll` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-020 in-process permission cache blocks scaling | **Discounted** — `W-53` replaced it; the worker scales on queue depth (`05-azure-architecture.md:179`) with no per-process state |
| DEBT-018 no indexes | **Honoured** |
| D-2 worker never reads the queue | **Blocker, not this ticket** — `W-52.1` |
| DEBT-034 no async framework (proposed in the analysis report, then withdrawn) | **Not a gap** — the framework is `W-52`; this ticket is the first real job on it |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Keep a synchronous compute for small tenants? | **No.** One path. A 5-employee run on the worker takes a second; two paths is two sets of bugs |
| 2 | Where does progress live? | **Both places.** `core.job_status.progress_percentage` is the platform's generic view; `payrun.progress_done / total` is what the payroll screen shows without a second permission (`core.job.read`) |
| 3 | Resume or restart from zero? | **Resume, by attempt number on the row.** `W-29.2` commits per employee for exactly this; throwing that away on a crash is what made the legacy timeout expensive |
| 4 | Who decides a run is stale? | **The officer, after 15 minutes of no progress.** An automatic sweep needs ShedLock and belongs to `W-52`; the manual rule is safe because the old message is dropped by the job guard either way |
| 5 | Send inside or after the transaction? | **After commit.** A message the worker cannot act on yet is a retry we do not need |
