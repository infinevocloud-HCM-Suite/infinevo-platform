# Feature: Scheduled reports on the worker

| Field | Value |
|---|---|
| **Feature ID** | `W-23.2` · from ticket #27 · `CORE-16` |
| **Promoted to** | `docs/target-state/features/W-23-2-scheduled-reports.md` on branch `W-23-2-scheduled-reports` — **`W-23-2` with hyphens**, never `W-23.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/worker`, `code/backend/migration`, `code/backend/core` (`async=true` on `ExportController`), `code/backend/app` (the `core.job.read` guard on `JobStatusController`) |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-23.1` (a definition and an export path), `W-20.2` (the scheduler, the tenant-list function and the delivery it reuses), `W-52` (the queue consumer loop the async export rides on), `W-11.3` (adds `core.report_schedule.manage` and `core.job.read` to the catalogue) |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 rows 17, 18 and §6 decision 8 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `worker` | 1 |
| Flyway migration | one script, one table — `core.report_schedule` | 1 |
| Externally testable behaviour | a scheduled report runs at its time, produces a file, and emails a link once | 1 |
| Frontend area | none | 1 |

Within cap. It could not ride with `W-23.1`: a different module, and `09-build-order.md:207`
names the boundary — *"exports run on `worker`, reading the replica when one exists."*

---

## 1. Problem

**No scheduled or emailed report exists in either product.** The evidence pass found none:
every export is a user pressing a button and waiting.

That is workable while exports are small and synchronous, and it stops being workable for the
two cases that actually matter to a payroll customer:

- A monthly report someone must remember to run, and eventually forgets
- An export large enough that a browser request times out before it finishes

`W-23.1` moved the output into the document store, which solves the second case for a
user-triggered export. This ticket adds the schedule, and moves the work off the container
serving requests.

The replica part of the build order's instruction is a forward reference: no read replica
exists yet. The code must read through a role that *can* be pointed at one — `readonly_user`
already exists for exactly this, with read-only access and RLS enforced
(`02-data-model.md:400`).

## 2. Scope

**In scope**

- `core.report_schedule` — a definition plus a cadence, recipients and filters
- Running due schedules on `worker` as a `@Scheduled` + `@SchedulerLock` job, using `W-52`'s ShedLock
- Producing the file with `W-23.1`'s export service into `core.document`
- Notifying recipients with a signed link, through `W-20.1` and `W-20.2`
- Reading the report rows as `readonly_user`, so pointing at a replica later is configuration rather than a rewrite — **and writing the result as `worker_user`**, because `readonly_user` holds `SELECT` only (`infra/postgres/03-grants.sql:35`) and cannot write `core.document` or `core.job_status`
- An asynchronous path for a large user-triggered export: a `report` job on `W-52`'s queue, polled at `GET /api/v1/jobs/{id}` (contracts §5 row 18)
- The per-tenant sweep, iterating `W-20.2`'s `core.list_tenants_for_sweep()` (contracts §5 row 17)

**Out of scope**

- The export mechanics and definitions — `W-23.1`
- The scheduler and the delivery client — `W-20.2`
- Attaching the file to the email. A signed link, per `09-build-order.md:204`
- A read replica. `W-50`'s infrastructure decision, not this ticket

## 3. Flow

```
[@Scheduled + @SchedulerLock] --> core.list_tenants_for_sweep() --> [ReportScheduleEvaluator] due schedules
   --> [W-23.1 ExportService] rows read as readonly_user --> DocumentService.store(EXPORT, ...) as worker_user
   --> signedLink(id, 7 days) --> [W-20.1 compose] --> queue `notification` --> [W-20.2 deliver]

[large user export] --> POST /exports?async=true (app) --> JobService.createJob --> QueueProducer.send("report", ...)
   --> [worker: AsyncExportConsumer] --> same export path --> JobService.markCompleted(documentId)
   --> user polls GET /api/v1/jobs/{id} --> document id --> GET /documents/{id}/link
```

**Which database user.** Two, on purpose. The row query runs on the `readonly_user` datasource
so a replica is a config change; the export file (`core.document`), the schedule's
`last_run_*` columns and the job row (`core.job_status`) are written on the `worker_user`
datasource, which is a member of `app_user` (`infra/postgres/01-roles.sql:32`) and so holds
`app_user`'s grants. `readonly_user` cannot do this — `03-grants.sql:35` gives it `SELECT` and
nothing else, and that is the point of it. Both datasources are tenant-bound (§4).

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Job | `worker/.../report/ReportScheduleEvaluator.java` | new — `@Scheduled` + `@SchedulerLock` |
| Consumer | `worker/.../report/AsyncExportConsumer.java` | new — implements `shared.queue.QueueConsumer<ExportJobPayload>` for queue `report` (`12-core-contracts.md:111-112`) |
| Entity | `worker/.../report/ReportSchedule.java` | new, `@Table(schema="core")` |
| Repository | `worker/.../report/ReportScheduleRepository.java` | new |
| Config | `worker/src/main/resources/application.yml` | change — a second datasource bound to `readonly_user`, beside the existing `worker_user` one |
| Controller | `core/.../report/ExportController.java` | change — accept `async=true`: `JobService.createJob`, then `QueueProducer.send("report", QueueMessage.of(jobId, tenantId, "report", payload))`, return `202` + job id |
| Controller | `app/.../controller/JobStatusController.java` | change — add `@RequiresAction("core.job.read")` to `GET /api/v1/jobs/{jobId}` (`JobStatusController.java:19-28`; today it has none — `12-core-contracts.md:78`) |

The `report` queue already exists — `W-52` created `payrun`, `import`, `report`
(`W-52-queue-worker.md:64`); this is its first producer. The consumer is idempotent on
`jobId`: a redelivered message for a job already `COMPLETED` or `RUNNING` is dropped, per
`W-52`'s rule (`12-core-contracts.md:158`). The job's result payload carries the document id,
so `GET /jobs/{id}` is enough to reach the file.

**A second datasource, not a second connection string in the same pool.** `readonly_user` is
subject to RLS like `app_user` (`02-data-model.md:400`), so the tenant must still be bound per
transaction the way `W-08` does — `TenantBindingDataSourceProxy` already exists in `shared`
and the read datasource must be wrapped in it too. Skipping that is how a report quietly
becomes cross-tenant.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| PUT | `/api/v1/report-schedules/{id}` | definitionId, cadence, recipients, filters | `200` | `@RequiresAction("core.report_schedule.manage")` |
| GET | `/api/v1/report-schedules` | — | the tenant's schedules with last-run status | `@RequiresAction("core.report_schedule.manage")` |
| POST | `/api/v1/exports?async=true` | definitionId, filters | `202` + job id | `core.report.read` and the definition's `required_action`, as `W-23.1` |
| GET | `/api/v1/jobs/{jobId}` | — | status, progress, result (document id) | `@RequiresAction("core.job.read")` — built in `app`, guard added here |

`core.report_schedule.manage` and `core.job.read` arrive with `W-11.3`
(`12-core-contracts.md:128`). `PUT` also requires that the caller holds the definition's
`required_action`: nobody schedules a report they may not run.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__report_schedule.sql` | `core.report_schedule` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` ·
`definition_id uuid NOT NULL REFERENCES core.report_definition(id)` ·
`cadence varchar(16) NOT NULL` — daily, weekly, monthly · `day_of_period int NULL` ·
`send_at_local_time time NOT NULL` · `filters jsonb NULL` ·
`recipient_emails text NOT NULL` · `is_active boolean NOT NULL DEFAULT true` ·
`last_run_at timestamptz NULL` · `last_run_status varchar(16) NULL` ·
`last_document_id uuid NULL REFERENCES core.document(id)` · four audit columns.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, is_active, send_at_local_time)` for the sweep
- [x] Money columns — none
- [x] Expand / contract — new table only

**`send_at_local_time`, tenant-local**, for the same reason as `W-20.2`: a monthly payroll
report at 09:00 UTC arrives at 14:30 in India, which is why the frozen crons were disabled.

**`last_run_at` and `last_run_status` on the row.** A schedule that silently stops is worse
than no schedule, and the frozen system's disabled crons are exactly that failure with no
visible trace.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `worker/.../report/ReportScheduleEvaluatorTest.java` | daily, weekly and monthly cadences; month-end handled where a month has no 31st; local time honoured |
| Integration | `worker/.../report/ScheduledReportIT.java` | a due schedule produces a document and queues exactly one notification |
| Integration | `worker/.../report/ReadOnlyRoleIT.java` | **the report datasource is refused `INSERT`, and its reads are still tenant-scoped by RLS** |
| Integration | `worker/.../report/AsyncExportIT.java` | `POST /exports?async=true` puts one message on the `report` queue and returns `202`; the consumer completes it; `GET /jobs/{id}` returns `COMPLETED` with the document id; a redelivered message does not produce a second document |
| Integration | `worker/.../report/ScheduledReportLinkIT.java` | the emailed link is signed for 7 days, not 15 minutes; the document row has `kind = EXPORT` and no `employee_id` |
| Integration | `worker/.../report/ScheduleGuardIT.java` | `/report-schedules` returns `403` without `core.report_schedule.manage`; `GET /jobs/{id}` returns `403` without `core.job.read`, and `404` for another tenant's job |
| Integration | `worker/.../report/ScheduleRlsIT.java` | a sweep bound to tenant A runs no schedule of tenant B |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`ReadOnlyRoleIT` asserts both halves, because a read-only role that is not tenant-bound is a
cross-tenant read with extra steps. `DatabasePrivilegesIT` in `shared` already asserts the
role matrix; this asserts that the reporting path actually uses it.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres azurite redis
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.report_schedule'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT privilege_type FROM information_schema.table_privileges
    WHERE grantee='readonly_user' AND table_schema='core' GROUP BY 1 ORDER BY 1;"

cd code/backend && mvn -q -pl worker -Dit.test=ScheduledReportIT,ReadOnlyRoleIT verify
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| `readonly_user` privileges | `SELECT` only |
| Scheduled report test | green — one document, one notification |
| Read-only role test | green — writes refused, reads tenant-scoped |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The read datasource is not wrapped in the tenant proxy and reports go cross-tenant | **high, and it is the worst failure here** | `ReadOnlyRoleIT` asserts tenant scoping on the reporting path, not just the role's grants |
| A schedule stops silently, as the frozen crons did | **medium** | `last_run_at` and `last_run_status` on the row, surfaced in the list endpoint |
| The file is attached to the email instead of linked | medium | Signed link only, per `09-build-order.md:204`; attachments also break at size |
| A monthly schedule set to the 31st skips February | medium | Month-end clamping, unit-tested |
| Two replicas run the same schedule | medium | `@SchedulerLock` from `W-52`; not re-solved here |
| A signed link outlives the recipient's need for it | low | `W-21`'s `signedLink(id, Duration)` caps at 7 days; this ticket passes exactly that (contracts §6 decision 8) |
| The worker writes the export on the read-only datasource and fails, or the read runs on `worker_user` and a replica never helps | medium | Two datasources, named by purpose; `ReadOnlyRoleIT` asserts the read one refuses `INSERT`, `AsyncExportIT` asserts the write lands |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only —
`migration/README.md:135-143`. Deactivating schedules stops the sweep without losing
definitions or past documents.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.report_schedule` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added. Flyway must never run in `worker` — `migration/README.md:145-150` |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | one index, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `worker` and `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| No scheduled or emailed report in either product | **Fixed** |
| Large exports block a request thread | **Fixed.** Asynchronous path onto `worker` |
| `readonly_user` exists and nothing uses it (`02-data-model.md:400`) | **Fixed.** This is its first consumer |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **May a schedule send to an address outside the tenant?** An accountant or auditor often wants the file. **Recommend** allowing it but recording every external recipient in the audit log, since it is a deliberate export of tenant data to someone with no account.
2. **How long does a scheduled report's link live?** `W-21` decision 1 proposes fifteen minutes for interactive downloads, which is far too short for a report emailed overnight. **Recommend** seven days for scheduled reports, set when the link is created rather than as a second global default. **Confirmed 2026-09-25** as `12-core-contracts.md:173`, decision 8; `W-21` now caps `signedLink` at 7 days.
