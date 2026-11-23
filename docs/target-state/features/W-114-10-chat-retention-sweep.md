# Feature: AI helper chats are deleted after 30 days

| Field | Value |
|---|---|
| **Feature ID** | `W-114.10` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/worker` (`retention`), `code/backend/migration` |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.3` on `main` (the three chat tables) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `worker` | 1 |
| Flyway migration | 1 — next free version at build time (grants only) | 1 |
| Externally testable behaviour | After the nightly run, no chat message, action intent or idle session older than 30 days exists for any customer | 1 |
| Frontend area | none | 1 |

**Founder rule (2026-11-23):** the helper is not switched on for a real customer until
this part is on `main`.

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | Chats are stored from `W-114.3` onward and nothing deletes them. | the `W-114.3` spec, "Out of scope" |
| 2 | The worker already has a nightly deletion job, but only for the audit log and notifications. | `code/backend/worker/src/main/java/com/infinevo/worker/retention/AuditRetentionService.java:45-46` |
| 3 | The database role that job uses may delete from those two tables only. | `code/backend/migration/src/main/resources/db/migration/core/V094__retention_run.sql:67-71` |

## Scope

**In scope**

- A nightly job in the worker that deletes old AI chat rows, customer by customer.
- The grants the deleting role needs.

**Out of scope**

- A per-customer retention period. It is 30 days for everyone.
- Marking an unconfirmed action as expired — `W-114.8` decides that when it reads the row.
- A "delete my chats now" button — a user can already delete a session (`W-114.3`).

## The rule

| Row | Deleted when |
|---|---|
| `core.ai_message` | `created_at` is more than 30 days ago |
| `core.ai_action_intent` | `created_at` is more than 30 days ago, whatever its status |
| `core.ai_session` | Its last activity — `last_message_at`, or `created_at` when that is empty — is more than 30 days ago. Its remaining messages and intents go with it through the foreign keys |

Messages are deleted by their own age, so a session kept alive for months does not keep
old messages. The cutoff is midnight UTC, 30 days back. What an action did stays in the
audit log, which has its own retention; only the chat goes.

## Flow

```
02:30 UTC --> AiChatRetentionJob (@SchedulerLock) --> AiChatRetentionService.runSweep
          --> core.list_tenants_for_sweep() --> for each customer, for each of the three tables:
              bind the tenant, delete in batches, record the run in core.retention_run
```

## How it reuses what exists

| Need | Existing piece | Evidence |
|---|---|---|
| One run at a time across worker replicas | `@SchedulerLock` | `worker/.../retention/AuditRetentionJob.java:40-41` |
| List every customer with no tenant bound | `core.list_tenants_for_sweep()`, a `SECURITY DEFINER` function the role may already execute | `core/V093__reminder_rule.sql:51`; `core/V094__retention_run.sql:71` |
| A connection that can delete and nothing else | The `retention_user` data source: not superuser, no `BYPASSRLS` | `worker/.../retention/RetentionDataSourceConfig.java`; `infra/postgres/01-roles.sql:52-53`; `worker/src/main/resources/application.yml:107-110` |
| Delete under row-level security | Bind the tenant, then delete a batch | `AuditRetentionService.java:228` (`executeBatchDelete`) |
| A record of what each run did | `core.retention_run`; its `target_table` is free text, so new names need no change | `V094__retention_run.sql:7-23` |

No new `SECURITY DEFINER` function is needed. The deletes run as `retention_user` inside
the customer's tenant, so the RLS policy on the chat tables applies to them too.

## Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V…__ai_chat_retention_grants.sql` — next free version at build time | `GRANT SELECT, DELETE ON core.ai_message, core.ai_action_intent, core.ai_session TO retention_user`; `CREATE INDEX idx_ai_message_tenant_created ON core.ai_message (tenant_id, created_at)`; `CREATE INDEX idx_ai_intent_tenant_created ON core.ai_action_intent (tenant_id, created_at)` | no new table | yes — revoke and drop |

- [x] No new table, so no new `tenant_id` or policy. The three tables already have both (`W-114.3`)
- [x] Both indexes lead with `tenant_id`. `core.ai_session` is covered by `idx_ai_session_tenant_user` only loosely; a customer has few sessions, so the sweep scans them
- [x] No money column
- [x] Nothing dropped or renamed
- [x] Every statement names `core.`
- [x] `retention_user` gains no `UPDATE` and no access to any other table

## Backend changes

All under `code/backend/worker/src/main/java/com/infinevo/worker/retention/`.

| Layer | File | Change |
|---|---|---|
| Service | `AiChatRetentionService.java` | **New.** `runSweep(boolean dryRun)`: lists customers, then for each sweeps `core.ai_message`, `core.ai_action_intent`, `core.ai_session` **in that order**, in batches, with the tenant bound the way `executeBatchDelete` binds it. One customer failing is logged and the next one runs (`AuditRetentionService.java:103-116`). Each table and customer writes a `core.retention_run` row through `RetentionRunStore`, with `target_table` set to the table's name. A dry run counts and deletes nothing |
| Job | `AiChatRetentionJob.java` | **New.** `@Scheduled(cron = "${worker.ai-retention.cron:0 30 2 * * *}")`, `@SchedulerLock(name = "ai_chat_retention_sweep", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")` |
| Settings | `worker/src/main/resources/application.yml` | `worker.ai-retention.cron`, `.days` (default 30, minimum 1), `.dry-run` (default **false**), `.batch-size` (default 500) |

**Why dry run is off by default here, unlike the audit job.** The audit job defaults to a
dry run because a deleted audit row cannot be recovered and is a legal record
(`AuditRetentionJob.java:18-24`). A chat is not a record anyone must keep, and the founder
rule is that chats are really deleted before the helper is switched on. A deployment that
forgot a setting must delete, not keep.

Running it twice in a night is harmless: the second run finds nothing to delete.

## Frontend changes

None. **Routes added:** none. **API:** none.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `worker/src/test/.../retention/AiChatRetentionServiceTest.java` | The cutoff is midnight UTC 30 days back; the three tables are swept in order; a failing customer does not stop the next; a dry run deletes nothing and records the count; `days` below 1 is refused |
| Integration | `worker/src/test/.../retention/AiChatRetentionIT.java` | Connected as `retention_user`: a 31-day-old message goes and a 29-day-old one stays; an idle 31-day-old session goes with its messages and intents; an active session keeps its recent messages and loses its old ones; customer B's rows are untouched while customer A is swept; a second run deletes 0; `retention_user` cannot `UPDATE` a chat row |
| Integration | `worker` test schema | Add the `W-114.3` chat migration and this one to the worker's test schema list, or the IT has no tables |

## Verification

```bash
cd code/backend && ./mvnw -B -pl migration,worker -am verify
```

| Check | Expected | Result |
|---|---|---|
| The migration applies on a schema that has the `W-114.3` tables | `Successfully applied 1 migration`; `\dp core.ai_message` shows `retention_user` with `r` and `d` only | not run |
| `AiChatRetentionIT` | All cases pass | not run |
| Worker log after one scheduled run | One line per customer and table with the number deleted | not run |
| `SELECT target_table, rows_deleted, status FROM core.retention_run` for that night | Three rows per customer, `COMPLETED` | not run |
| Two worker replicas at 02:30 | One runs, the other skips on the lock | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The helper is switched on before this is on `main` | Low | Founder rule, repeated in `W-114.1` and in the tracker |
| A wrong cutoff deletes recent chats | Low | The IT pins 29 and 31 days; a user loses a chat, not a record |
| The job and the audit job compete at night | Low | Different lock names, different start times (02:00 and 02:30) |
| A deleted message was the only trace of an action | None | The action's own row and the audit log keep that; only the chat text goes |

## Rollback

Set `WORKER_AI_RETENTION_DRY_RUN=true` to stop deleting at once, or revert the worker. The
grants and indexes can stay. Deleted chats cannot be brought back.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `migration` | The grants and two indexes |
| 2 | `worker` | Service, job, settings, the test schema list, tests |
