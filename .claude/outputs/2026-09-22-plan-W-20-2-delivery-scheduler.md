# Feature: Reminder rules, scheduler and delivery

| Field | Value |
|---|---|
| **Feature ID** | `W-20.2` · from ticket #24 · `CORE-12` |
| **Promoted to** | `docs/target-state/features/W-20-2-delivery-scheduler.md` on branch `W-20-2-delivery-scheduler` — **`W-20-2` with hyphens**, never `W-20.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/worker`, `code/backend/migration` |
| **Related gaps** | DEBT-004 (fixed), DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-20.1` — there must be something to deliver |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `worker` | 1 |
| Flyway migration | one script, one table — `core.reminder_rule` | 1 |
| Externally testable behaviour | a queued notification is delivered once, and a due reminder rule composes one | 1 |
| Frontend area | none | 1 |

Within cap. This is the half that could not ride with `W-20.1`: a different module, and
delivery is its own observable behaviour.

---

## 1. Problem

Every scheduled job in the frozen system is either disabled, duplicated per replica, or both.

- `NotificationSchedular` ticks every 60 seconds and evaluates five reminder types
- `ReminderScheduler` is cron-based and **all of its crons are disabled**
- `POIReminderScheduler` runs at 09:00 UTC daily; `ITDeclarationAutoLockScheduler` at midnight UTC
- All are Spring `@Scheduled`, which means **every replica runs every job**. With one instance that is invisible; on Container Apps (`D-10`) it sends every reminder twice
- The reminder configuration is spread over five HRMS tables — `ApprovalReminder`, `EmployeeReminder`, `EscalationReminder`, `HrReminder`, `SupervisorReminder` — all timesheet-focused, plus a Payroll `Reminder` used only for proof-of-investment and tax workflows

And UTC is the wrong clock. A 09:00 UTC reminder reaches an Indian employee at 14:30, which is
why the timesheet crons were probably switched off rather than fixed.

Delivery itself is sound in shape — both backends post to the Brevo API — but the credential
is committed in plaintext, which is DEBT-004.

## 2. Scope

**In scope**

- `core.reminder_rule` — per tenant, replacing the six scattered tables with one
- A scheduler on `worker` that runs each job **once across all replicas**
- The delivery consumer: read the queue, send by Brevo, record the outcome
- Retry with backoff, and a dead-letter state a human can see
- The provider credential from Key Vault (`W-56`), never from a properties file
- Tenant-local time, not UTC
- Registering `W-15.3`'s escalation sweep and `W-22.2`'s retention sweep with the same scheduler

**Out of scope**

- Composition and templates — `W-20.1`
- Any channel but email and in-app
- Migrating the five HRMS reminder tables' contents — `W-67`
- Replacing Brevo. See decision 2

## 3. Flow

```
[@Scheduled + @SchedulerLock] --> [ReminderEvaluator] due rules --> W-20.1 compose --> queue
[queue consumer on worker]  --> [BrevoDeliveryClient] --> sent, or retry, or dead-letter
                            --> core.notification.status updated
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Config | `worker/.../config/SchedulerLockConfig.java` | **exists already (`W-52`)** — reused, not modified |
| Job | `worker/.../notification/ReminderEvaluator.java` | new |
| Consumer | `worker/.../notification/NotificationDeliveryConsumer.java` | new |
| Client | `worker/.../notification/BrevoDeliveryClient.java` | new |
| Entity | `worker/.../notification/ReminderRule.java` | new, `@Table(schema="core")` |
| Repository | `worker/.../notification/ReminderRuleRepository.java` | new |
| Config | `worker/src/main/resources/application.yml` | change — Brevo key from environment, **no default**; batch size; poll interval |

**`@Scheduled` plus `@SchedulerLock`, never `@Scheduled` alone.** Bare `@Scheduled` is what
makes the frozen jobs fire once per replica; pairing it with ShedLock is what `W-52` already
established, and `WorkerAutoLockScheduler` is the worked example — see §6.

**No new scheduler abstraction.** `W-15.3`'s escalation sweep and `W-22.2`'s retention sweep
use the same `@Scheduled` + `@SchedulerLock` pair directly. Inventing a registry on top of
ShedLock would be a second mechanism, which is the thing to avoid.

**API contract**

None. Reminder rules are managed through `W-20.1`'s admin surface; `worker` exposes no HTTP
endpoint beyond its health probe.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__reminder_rule.sql` | `core.reminder_rule` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` · `event varchar(64) NOT NULL` ·
`audience varchar(32) NOT NULL` — the subject, their manager, an HR role holder ·
`offset_days int NOT NULL` · `send_at_local_time time NOT NULL` ·
`repeat_every_days int NULL` · `max_repeats int NULL` ·
`is_active boolean NOT NULL DEFAULT true` · four audit columns.

**The lock already exists on `main`. This ticket builds nothing for it.**

`W-52` merged **ShedLock**, backed by a `core.shedlock` table, and wired it in
`code/backend/worker/src/main/java/com/infinevo/worker/config/SchedulerLockConfig.java`.
Its own comment states the purpose: *"prevents scheduled jobs from duplicate execution
across multi-replica workers"*, closing DEBT-021. `@EnableSchedulerLock` is configured with
`defaultLockAtMostFor = "PT10M"`, the provider uses `usingDbTime()`, and `SchedulerLockIT`
covers it.

Your decision of 2026-09-22 — the lock lives in Postgres, not Redis — is therefore already
satisfied. My earlier proposal to build a `scheduler_lease` table, and the advisory-lock
alternative after it, are both **withdrawn**: a second locking mechanism beside ShedLock is
precisely the "four schedulers" failure this spec exists to avoid.

What this ticket does instead: annotate each job with `@Scheduled` **plus** `@SchedulerLock`,
which is the pattern `WorkerAutoLockScheduler` and `WorkerPOIReminderScheduler` already
follow. `@Scheduled` alone is the defect; `@Scheduled` with `@SchedulerLock` is the fix, and
it is already the house style.

**A note on `core.shedlock` for whoever reviews the migration.** It carries no `tenant_id`
and no row-level security, because a lock row belongs to the platform rather than to any
tenant — which is the answer to the question I raised on 2026-09-22 about where such a table
can live. It is already merged, so the precedent is set and this ticket neither repeats nor
revisits it.

So this ticket ships **one** script and one table, as originally scoped.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, event, is_active)`
- [x] Money columns — none
- [x] Expand / contract — new table only

**`send_at_local_time` with the tenant's timezone**, not a UTC cron. A reminder at 09:00 means
09:00 where the employee is.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `worker/.../notification/ReminderEvaluatorTest.java` | a rule due today fires; one due tomorrow does not; repeats stop at `max_repeats`; local time honoured across timezones |
| Unit | `worker/.../notification/DeliveryRetryTest.java` | a transient failure retries with backoff; a permanent one dead-letters; a success is never retried |
| Integration | `worker/.../notification/ReminderSingleRunIT.java` | **two worker instances against one database: the reminder job body executes exactly once.** `W-52`'s `SchedulerLockIT` already proves ShedLock itself; this proves *this* job is annotated |
| Integration | `worker/.../notification/DeliveryIT.java` | a queued notification is delivered once and its status updated; a redelivered queue message does not send twice |
| Integration | `worker/.../notification/ReminderRuleRlsIT.java` | a sweep bound to tenant A evaluates no rule of tenant B |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`SingleRunnerIT` is the ticket's reason to exist, and `DeliveryIT`'s redelivery case is
required by `D-50`: **Storage Queue does not guarantee ordering or at-most-once delivery**, so
the consumer must be idempotent. `active-work.md` records that consequence as `W-52`'s, and it
lands here first.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres azurite redis
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.reminder_rule'::regclass;"

cd code/backend && mvn -q -pl worker -Dit.test=ReminderSingleRunIT,DeliveryIT verify
cd code/backend && mvn -q verify

# no per-replica scheduling, and no committed credential
# every @Scheduled must be paired with @SchedulerLock
for f in $(grep -rl '@Scheduled' worker/src/main/java/); do
  grep -q '@SchedulerLock' "$f" || echo "UNLOCKED: $f"
done; echo "scheduler check done"
grep -rn 'api-key:.*[A-Za-z0-9]' worker/src/main/resources/application.yml \
  && echo "REVIEW: committed credential" || echo "no committed credential"
```

| Check | Expected |
|---|---|
| RLS | `t` |
| `ReminderSingleRunIT` | green — one execution across two instances |
| `DeliveryIT` | green — redelivery sends once |
| `@Scheduled` pairing check | no `UNLOCKED:` lines |
| Credential grep | `no committed credential` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A job ships with `@Scheduled` and no `@SchedulerLock` | **high — it is one missing annotation** | The pairing check in §8 and `ReminderSingleRunIT` both catch it |
| A queue redelivery sends the same email twice | **high — `D-50` guarantees no better** | Idempotent consumer keyed on the notification id; asserted in `DeliveryIT` |
| A failing provider retries forever and floods | medium | Bounded backoff, then dead-letter with a visible status |
| The Brevo key is committed, as it is today (DEBT-004) | medium | No default in configuration; the app fails to start without it, and the grep checks |
| Reminders fire at the wrong local hour and get switched off, as the timesheet crons were | medium | Tenant-local time, tested across timezones |
| A replica dies mid-job and the lock is stranded | low | ShedLock's `lockAtMostFor` bounds it; `W-52` sets a 10-minute default and `usingDbTime()` removes clock-skew between replicas |
| A job outruns `lockAtMostFor` and a second replica starts it | **medium — the one real hazard** | Each job sets its own `lockAtMostFor` above its worst observed runtime; the delivery consumer is idempotent anyway, per `D-50` |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only —
`migration/README.md:135-143`. Stopping the consumer leaves notifications queued rather than
lost.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.reminder_rule` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added. Flyway must never run in `worker` — `migration/README.md:145-150` |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | one index, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `worker` and `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| `@Scheduled` on every replica (four schedulers) | **Fixed.** Single-runner lease |
| `ReminderScheduler` crons all disabled | **Fixed by replacement.** One live evaluator |
| Six reminder tables across two products | **Fixed.** One `reminder_rule` |
| UTC reminder times | **Fixed.** Tenant-local |
| DEBT-004 Brevo key in `.properties` | **Fixed.** Key Vault via `W-56`; no default |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | How does the scheduler stop two replicas running one job? | **ShedLock, already on `main` from `W-52`.** Your 2026-09-22 answer — Postgres, not Redis — is satisfied by what exists. The lease-table and advisory-lock proposals are both withdrawn |
| 2 | Stay on Brevo? | **Yes** — settled 2026-09-23 — behind a `DeliveryClient` interface so Azure Communication Services is a swap rather than a rewrite |
| 3 | Does `W-22.2`'s retention sweep register here? | **Yes** — settled 2026-09-23. Two answers to one problem is how the frozen system got four schedulers |
