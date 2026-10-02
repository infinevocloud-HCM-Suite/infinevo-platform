# Feature: Reminder audiences supply their own placeholders, and the timesheet escalation event

| Field | Value |
|---|---|
| **Feature ID** | `W-43.1` · ticket #55 (`W-43`) · `CORE-12` seam for `HRMS-10` · part 1 of 2 |
| **Promoted to** | `docs/target-state/features/W-43-1-reminder-audience-placeholders.md` |
| **Owner** | devashis |
| **Apps touched** | `code/backend/core`, `code/backend/worker` (the reminder sweep core owns), `code/backend/migration` |
| **Related gaps** | DEBT-021 (unchanged — the sweep already holds a lock) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | nothing — `W-20.2` is on `main` (`b6e6012`) |
| **Followed by** | `W-43.2` timesheet reminders (`hrms`) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` — its interface, its rule validation, and its sweep, which runs in `worker` | 1 |
| Flyway migration | `core/V146` | 1 |
| Externally testable behaviour | a reminder audience can put its own values into the mail, and a `TIMESHEET_ESCALATION` rule is accepted only for an audience that supplies them | 1 |
| Frontend area | none | 1 |

---

## 1. Problem

| Today (on `main`) | Evidence |
|---|---|
| The sweep alone fills a reminder's values; an audience can only return ids | `core/.../notification/ReminderAudienceResolver.java:27`; `worker/.../notification/ReminderEvaluator.java:215`, `:252-275` |
| `week_start` is always **this** week's Monday, so a reminder about last week names the wrong week | `ReminderEvaluator.java:266` |
| A rule is refused unless the sweep can fill every value its event needs | `core/.../notification/ReminderRuleService.java:16`; `ReminderRuleServiceImpl.java:144-151` |
| There is no event for "these people in your team are late"; `TIMESHEET_REMINDER` speaks to the late employee | `core/.../notification/NotificationEvent.java:39`; `migration/.../core/V038__notification_template.sql:104-105` |

Legacy escalation mails one list of late employees per week (`legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/serviceimpl/schedular/NotificationSchedularServiceImpl.java:677`, `:695-700`).
`W-43.2` needs the audience to name the week it checked and, for a manager, the people who are late. Only the audience knows either.

Paths above are under `code/backend/` unless they start with `legacy/`.

## 2. Scope

**In scope**

- `ReminderAudienceResolver` gains two default methods: the extra values it supplies, and its recipients with those values
- The sweep sends each recipient's values with the mail; an audience's value replaces the sweep's value of the same name
- Rule validation counts the audience's values as supplied
- `TIMESHEET_ESCALATION(employee_name, week_start, late_employees)` with seeded in-app and email templates for every tenant

**Out of scope**

- Any audience that uses this — `W-43.2`
- Changing what `week_start` means for audiences that supply nothing — it stays this week's Monday
- Screens

## 3. Flow

```
[admin] POST /api/v1/reminder-rules { event: TIMESHEET_ESCALATION, audience: X, ... }
   --> ReminderRuleServiceImpl.validate
         supplied = SUPPLIED_PLACEHOLDERS ∪ resolver(X).suppliedPlaceholders()
         event.placeholders() ⊄ supplied  --> 400 on "event"

[worker sweep, W-20.2, unchanged schedule and lock]
   --> resolver.recipients(rule, tenant, slotDate)              was resolve(rule, tenant)
   --> claimRun                                                 unchanged, still before sending
   --> for each recipient:
         data = buildPlaceholderData(...)                       unchanged
         data.putAll(recipient.placeholders())                  audience wins on a clash
         compose(rule.event, recipient.employeeId, data)
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Interface | `core/.../notification/ReminderAudienceResolver.java` | add `default Set<String> suppliedPlaceholders()` → empty; add `default List<ReminderRecipient> recipients(ReminderRule rule, UUID tenantId, LocalDate slotDate)` → `resolve(...)` mapped to recipients with no values. `resolve` stays, so `SubjectAudienceResolver` and payroll's `ProofPendingAudienceResolver` compile and behave as before |
| Record | `core/.../notification/ReminderRecipient.java` | new: `UUID employeeId`, `Map<String, Object> placeholders` (never null) |
| ServiceImpl | `core/.../notification/ReminderRuleServiceImpl.java:144-151` | supplied set = `SUPPLIED_PLACEHOLDERS` plus the named audience's `suppliedPlaceholders()`; message unchanged in form |
| Enumeration | `core/.../notification/NotificationEvent.java` | add `TIMESHEET_ESCALATION("employee_name", "week_start", "late_employees")` |
| Sweep | `worker/.../notification/ReminderEvaluator.java:215`, `:228-233` | call `recipients(rule, tenantId, localDate)`; merge each recipient's values over `buildPlaceholderData`'s |

`slotDate` is the tenant-local day the sweep is sending for (`ReminderEvaluator.java:122-124`), so an audience counts weeks in the tenant's zone, not the server's.

**API contract** — no new endpoint. `POST` and `PUT /api/v1/reminder-rules` change only in what they accept:

| Request | Before | After |
|---|---|---|
| `TIMESHEET_ESCALATION` with `SUBJECT` | event unknown | `400`, `event` names `late_employees` |
| `TIMESHEET_ESCALATION` with an audience whose `suppliedPlaceholders()` includes `late_employees` | event unknown | `201` |

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V146__timesheet_escalation_notification.sql` | `core.notification_template` — widens a `CHECK`, adds rows | yes, existing table with RLS | yes — additive |

Copy `core/V096__scheduled_report_notification.sql` step by step:

1. Drop and re-add `notification_template_event_check` with `V096`'s list plus `TIMESHEET_ESCALATION`.
2. `CREATE OR REPLACE FUNCTION core.seed_notification_templates` with `V096`'s rows plus:
   - `('TIMESHEET_ESCALATION', 'IN_APP', NULL, 'Timesheets for the week of ${week_start} are still missing: ${late_employees}.')`
   - `('TIMESHEET_ESCALATION', 'EMAIL', 'Timesheets still missing for the week of ${week_start}', '<p>Hello ${employee_name},</p><p>These people in your team have not submitted their timesheet for the week of ${week_start}: ${late_employees}.</p>')`
3. `SELECT core.seed_notification_templates(t.tenant_id) FROM core.tenant t;` — `ON CONFLICT DO NOTHING` adds only the new rows.

Every statement names `core.` (`migration/README.md:33-44`). `${...}` is safe: Flyway placeholder replacement is off since `W-20.1`. `V146` is reserved for this ticket (2026-10-02), above `W-42.2`'s `V145`.

- [x] `tenant_id` + RLS — no new table
- [x] Index on `tenant_id` plus lookup columns — existing
- [x] Money — none
- [x] Expand / contract — a widened `CHECK` and new rows; the previous release never asks for the new event

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../notification/ReminderRuleServiceImplTest.java` (new) | `TIMESHEET_ESCALATION` with `SUBJECT` is refused naming `late_employees`; with a stub audience supplying it, accepted; `TIMESHEET_REMINDER` with `SUBJECT` still accepted |
| Unit | `core/.../notification/NotificationEventTest.java` | existing test passes with the new event: both channels seeded, placeholders only from its list |
| Unit | `worker/.../notification/ReminderEvaluatorTest.java` | a stub audience's `week_start` replaces the sweep's; its `late_employees` reaches `compose`; an audience with no override gets exactly today's values; `slotDate` passed is the tenant-local slot |
| Integration | `core/.../notification/TimesheetEscalationTemplateIT.java` | after migrate, every tenant has both `TIMESHEET_ESCALATION` templates; a tenant created afterwards gets them; an employee name with `<b>` is escaped in the email body (`TemplateRenderer.java:21`) |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec postgres psql -U postgres -d infinevo -c \
  "SELECT count(*) FROM core.notification_template WHERE event='TIMESHEET_ESCALATION';"
grep -n "default .*recipients\|default .*suppliedPlaceholders" code/backend/core/src/main/java/com/infinevo/core/notification/ReminderAudienceResolver.java
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| Migrate | `V146` applied, no error |
| Template count | 2 × number of tenants |
| Interface | two `default` methods |
| Suite | green, no skips; payroll's `ProofReminderSweepTest` still passes unchanged |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| An audience silently changes a value other rules rely on | low | the merge is per recipient of that audience's rules only; tested |
| Payroll's resolvers break | low | `resolve` stays; defaults keep their behaviour; `ProofReminderSweepTest` must pass untouched |
| A tenant has edited its seed function rows | low | `ON CONFLICT DO NOTHING`, as `V096` |

## 10. Rollback

Remove the two default methods' callers; rules with the new event then resolve nothing. The template rows and wider `CHECK` can stay.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table | creates no table |
| Flyway only | `core/V146` |
| `Money`/`BigDecimal` | no money |
| Index on `tenant_id` plus lookup columns | no new lookup |
| Expand / contract | additive only |
| No module references another module | `core` and `worker` only; `worker` already depends on every module (`worker/pom.xml:17-20`) |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-021 scheduler on every replica | **Unchanged** — the sweep's ShedLock and claim-before-send stay (`ReminderEvaluator.java:76`, `:218`) |

## 13. Decisions — settled 2026-10-02

| # | Question | Answer |
|---|---|---|
| 1 | Split `W-43`? | **Yes.** This core seam, then `W-43.2` in `hrms` |
| 2 | Fix `week_start` in core, or let the audience name its week? | **The audience.** It is the only one that knows which week it checked; other rules keep today's meaning |
| 3 | Escalation as one mail per late employee, or one list per manager? | **One list per manager**, as legacy — hence `late_employees` |

## 14. Open for the founder

None.

## 15. As built (devashish, 2026-10-02)

Where the build differs from, or settles a point left open in, the sections above:

- `ReminderRuleServiceImpl.validate` now looks the audience up first, because the event check needs its values. An unknown audience on an event that needs an audience's values reports both `event` and `audience`, so the fix is one trip. The `event` message keeps its form; the list it ends with is now the sweep's values plus the audience's.
- `ReminderRecipient` has a compact constructor: a null `placeholders` becomes an empty map, the map is copied, and a null `employeeId` is refused. `ReminderRecipient.of(id)` is the no-values recipient the default `recipients()` builds.
- `ReminderEvaluatorTest` mocks `ReminderAudienceResolver`, and a Mockito mock does not run an interface's default methods, so its set-up now says `recipients(...)` calls the real default. Without that line every existing evaluator test finds no recipients.
- `NotificationEventTest`'s hard-coded list gains `TIMESHEET_ESCALATION`; its seed and `CHECK` readers pick up `V146` as the latest script, as they did `V096`.
- `TimesheetEscalationTemplateIT` has no Spring context. The notification test application starts the queue emulator, which needs Docker; the migration and the renderer are the whole subject, so the test needs neither. `NotificationTestSchema` applies `V146` for the other notification ITs.
- Proven here without Docker: the unit tests and `TimesheetEscalationTemplateIT`, and `ProofReminderSweepTest` unchanged. The worker's database ITs and the notification ITs that use the queue emulator are CI's.
