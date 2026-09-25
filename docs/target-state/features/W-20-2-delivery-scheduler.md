# Feature: Reminder rules, scheduler and delivery

| Field | Value |
|---|---|
| **Feature ID** | `W-20.2` · from ticket #24 · `CORE-12` |
| **Promoted to** | `docs/target-state/features/W-20-2-delivery-scheduler.md` on branch `W-20-2-delivery-scheduler` — **`W-20-2` with hyphens**, never `W-20.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/worker`, `code/backend/core` (the `/reminder-rules` API), `code/backend/migration` |
| **Related gaps** | DEBT-004 (fixed), DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-20.1` — there must be something to deliver · `W-12.1` — `core.tenant.timezone` (`W-12-1-subscription.md:55`) · `W-11.3` — adds `core.reminder_rule.manage` to the catalogue |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 rows 15, 17 and §6 decision 7 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `worker` for the jobs; `core` holds the `ReminderRule` entity and its CRUD API — exception noted 2026-09-25, `12-core-contracts.md:71` | 1 |
| Flyway migration | one script, one table — `core.reminder_rule` — plus one `SECURITY DEFINER` tenant-list function in the same script | 1 |
| Externally testable behaviour | a queued notification is delivered once, and a due reminder rule composes one | 1 |
| Frontend area | none | 1 |

Within cap, with the module exception. This is the half that could not ride with `W-20.1`: a
different module, and delivery is its own observable behaviour. The API sits in `core` because
`worker` exposes no HTTP endpoint and `worker` may reference `core` (`12-core-contracts.md:82`).

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
- Tenant-local time, not UTC — read from `core.tenant.timezone`, the column `W-12.1` adds (`W-12-1-subscription.md:55`; legacy kept it on the organisation, `legacy/Payroll-Bend-SBoot/.../entity/organization/Organization.java:61-62`)
- A `/api/v1/reminder-rules` CRUD API in `core`, guarded by `core.reminder_rule.manage`
- A `ReminderAudienceResolver` bean interface: a rule names an audience, a module supplies the bean that turns it into employee ids (`12-core-contracts.md:106`)
- The per-tenant sweep, iterating a `SECURITY DEFINER` tenant list rather than a bypass role
- **This ticket owns the scheduler.** Registering `W-15.3`'s escalation sweep and `W-22.2`'s retention sweep with the same `@Scheduled` + `@SchedulerLock` pattern (contracts §5 row 17)

**Out of scope**

- Composition and templates — `W-20.1`
- Any channel but email and in-app
- Migrating the five HRMS reminder tables' contents — `W-67`
- Replacing Brevo. See decision 2

## 3. Flow

```
[@Scheduled + @SchedulerLock] --> core.list_tenants_for_sweep() --> for each tenant: bind, read tenant.timezone
   --> [ReminderEvaluator] due rules --> [ReminderAudienceResolver] recipients --> W-20.1 compose --> queue `notification`
   --> rule.last_executed_at, repeat_count updated
[queue consumer on worker]  --> [BrevoDeliveryClient] --> sent, or retry, or dead-letter
                            --> core.notification.status updated

[admin] --> /api/v1/reminder-rules CRUD (core) --> core.reminder_rule
```

**The tenant list.** A sweep runs with no request behind it, so nothing has bound a tenant and
RLS maps to nothing. The job cannot enumerate `core.tenant` as `worker_user` for the same
reason. The pattern already on `main` is `core.get_user_tenants(UUID)` — a `SECURITY DEFINER`
function with `SET search_path = core, pg_temp`, `REVOKE ... FROM PUBLIC`, granted to
`app_user` (`M/core/V002__user_tenant.sql:30-40`). This ticket adds
`core.list_tenants_for_sweep()` in the same shape, granted to `worker_user`, which is a member
of `app_user` (`infra/postgres/01-roles.sql:32`). The job then binds each tenant in turn, so
every read and write inside the loop is still under RLS. `W-22.2` and `W-23.2` call the same
function; none of the three builds a second one (contracts §5 row 17, §6 decision 4).

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Config | `worker/.../config/SchedulerLockConfig.java` | **exists already (`W-52`)** — reused, not modified |
| Job | `worker/.../notification/ReminderEvaluator.java` | new |
| Consumer | `worker/.../notification/NotificationDeliveryConsumer.java` | new |
| Client | `worker/.../notification/BrevoDeliveryClient.java` | new |
| Entity | `core/.../notification/ReminderRule.java` | new, `@Table(schema="core")` — in `core`, so the API and the evaluator share it |
| Repository | `core/.../notification/ReminderRuleRepository.java` | new |
| Controller | `core/.../notification/ReminderRuleController.java` | new |
| Interface | `core/.../notification/ReminderAudienceResolver.java` | new — `audience()`, `resolve(ReminderRule, tenantId)` → employee ids; one bean per audience, module-supplied |
| Config | `worker/src/main/resources/application.yml` | change — Brevo key from environment, **no default**; batch size; poll interval |

**`@Scheduled` plus `@SchedulerLock`, never `@Scheduled` alone.** Bare `@Scheduled` is what
makes the frozen jobs fire once per replica; pairing it with ShedLock is what `W-52` already
established, and `WorkerAutoLockScheduler` is the worked example — see §6.

**No new scheduler abstraction.** `W-15.3`'s escalation sweep and `W-22.2`'s retention sweep
use the same `@Scheduled` + `@SchedulerLock` pair directly. Inventing a registry on top of
ShedLock would be a second mechanism, which is the thing to avoid.

**API contract** — in `core`; `worker` exposes no HTTP endpoint beyond its health probe.

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/reminder-rules` | `?event=&isActive=` | the tenant's rules | `@RequiresAction("core.reminder_rule.manage")` |
| POST | `/api/v1/reminder-rules` | event, audience, anchor, offset_days, day_of_week?, send_at_local_time, repeat_every_days?, max_repeats? | `201` + id | `@RequiresAction("core.reminder_rule.manage")` |
| PUT | `/api/v1/reminder-rules/{id}` | same body | `200` | `@RequiresAction("core.reminder_rule.manage")` |
| DELETE | `/api/v1/reminder-rules/{id}` | — | `204`, sets `is_active=false` | `@RequiresAction("core.reminder_rule.manage")` |

`core.reminder_rule.manage` arrives with `W-11.3` (`12-core-contracts.md:128`). An `audience`
with no registered `ReminderAudienceResolver` is refused at `POST`, not discovered at sweep time.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__reminder_rule.sql` | `core.reminder_rule` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` · `event varchar(64) NOT NULL` ·
`audience varchar(32) NOT NULL` — the subject, their manager, an HR role holder ·
`anchor varchar(32) NOT NULL` — what `offset_days` counts from ·
`offset_days int NOT NULL` · `day_of_week smallint NULL` — ISO 1–7, weekly rules only ·
`send_at_local_time time NOT NULL` ·
`repeat_every_days int NULL` · `max_repeats int NULL` ·
`last_executed_at timestamptz NULL` · `repeat_count int NOT NULL DEFAULT 0` ·
`is_active boolean NOT NULL DEFAULT true` · four audit columns.

Same script, after the table: `core.list_tenants_for_sweep()` — `SECURITY DEFINER`, in the
shape of `M/core/V002__user_tenant.sql:30-40`, `RETURNS SETOF core.tenant` so a column added
to the tenant later (`W-22.2`'s retention windows) reaches the sweeps without a redefinition;
`EXECUTE` granted to `worker_user` here, and to `retention_user` by `W-22.2`.

**Why these four columns** (contracts §1, §5 row 15). The frozen reminders come in two shapes
and one table must hold both:

| Legacy shape | Where | Maps to |
|---|---|---|
| Weekly: a weekday, a local time, a level | HRMS `EmployeeReminder.java:25-31` (`day`, `time`, `level`); `RoleReminderConfig.java:38-45` (`day_of_week`, `time`, `timezone`) | `day_of_week` + `send_at_local_time`; `anchor = WEEKLY` |
| Offset: *n* days before a deadline | Payroll `Reminder.java:22-23` (`number_of_days`) | `offset_days` counted from `anchor` — `DECLARATION_LOCK_DATE`, `POI_DUE_DATE`, `LEAVE_START`, ... |

`anchor` is an enum in code, not free text. `last_executed_at` and `repeat_count` are what
make `repeat_every_days` and `max_repeats` decidable without a second table, and they are
what the evaluator updates. `timezone` is **not** on the rule: `RoleReminderConfig` carried one
per row, and this ticket reads `core.tenant.timezone` instead, one clock per tenant.

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
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, event, is_active)` and `(tenant_id, is_active, last_executed_at)` for the sweep
- [x] Money columns — none
- [x] Expand / contract — new table only

**`send_at_local_time` with the tenant's timezone**, not a UTC cron. A reminder at 09:00 means
09:00 where the employee is. The zone is `core.tenant.timezone` from `W-12.1`
(`W-12-1-subscription.md:55`); a tenant with none set falls back to UTC and the sweep logs it.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `worker/.../notification/ReminderEvaluatorTest.java` | a rule due today fires; one due tomorrow does not; repeats stop at `max_repeats` and `repeat_count` advances; local time honoured across timezones read from `tenant.timezone`; a weekly rule fires only on `day_of_week`; an offset rule counts `offset_days` from its `anchor`; `last_executed_at` stops a rule firing twice in one day |
| Unit | `core/.../notification/ReminderRuleControllerTest.java` | an unknown `audience` is refused with `400`; `anchor` outside the enum is refused |
| Integration | `core/.../notification/ReminderRuleGuardIT.java` | every `/reminder-rules` verb returns `403` without `core.reminder_rule.manage` |
| Integration | `worker/.../notification/TenantSweepIT.java` | `core.list_tenants_for_sweep()` returns every tenant to `worker_user`; a direct `SELECT FROM core.tenant` with no tenant bound returns none; `app_user` cannot execute the function |
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
| No module references another module | `worker` and `core` only; `hrms`/`payroll` reach the sweep only by registering a `ReminderAudienceResolver` bean |

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
