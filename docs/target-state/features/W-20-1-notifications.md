# Feature: Notifications — templates and composition

| Field | Value |
|---|---|
| **Feature ID** | `W-20.1` · from ticket #24 · `CORE-12` |
| **Promoted to** | `docs/target-state/features/W-20-1-notifications.md` on branch `W-20-1-notifications` — **`W-20-1` with hyphens**, never `W-20.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-004 (discounted), DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-13.1` — a notification has a recipient · `W-11.3` — adds `core.notification_template.manage` to the catalogue |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 row 15 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 2 scripts, one table each — aggregate exception | 1 — exception granted 2026-09-22 |
| Externally testable behaviour | an event composes a notification from a template and queues it, and the recipient sees it in-app | 1 |
| Frontend area | none | 1 |

Within cap. `notification_template` is a satellite of `notification`.

---

## 1. Problem

Both products send mail, neither has a template store, and the composition is scattered
through the services that trigger it.

- HRMS `notifications` holds message, recipient id, type, read flag and a timestamp — **in-app only, no email field**
- HRMS composes plain text inline in `MailService` and posts it to Brevo
- Payroll posts to the same Brevo API but uses **Brevo-hosted template ids** — `BrevoEmailService`, with names like `POI_REMINDER` and `SALARY_SLIP`
- So the templates for one product live in a third-party dashboard and the templates for the other live in Java string concatenation. Neither is in the repository, neither is versioned, and neither is per tenant

**A correction to the build order.** `09-build-order.md:202` says the ticket is done when *"a
payroll event sends an email — something Payroll has never done."* Payroll **does** send one:
the salary slip after a pay run is finalised, at
`legacy/Payroll-Bend-SBoot/.../serviceimpl/payruns/PayRunServiceImpl.java:975`. The gap is
narrower and different from the one described — there is no *framework* for payroll events, so
a run that fails, stalls or completes sends nothing. One hard-coded email is not the same as
none, and the difference changes what this ticket must build.

## 2. Scope

**In scope**

- `core.notification_template` — per tenant, versioned, in the database rather than in Brevo or in Java
- `core.notification` — one row per notification, in-app and email alike
- Composition: an event plus a template plus data produces a rendered notification
- Enqueueing it for delivery, on the Storage Queue `D-50` selected — a fourth queue, `notification`, beside `W-52`'s `payrun`, `import` and `report` (`W-52-queue-worker.md:64`)
- In-app read and mark-as-read
- The events `W-15.2` already raises — a step assigned, an approval decided
- The `NotificationEvent` enum, closed and taken from what legacy actually sends (§4)

**Out of scope**

- **Delivery** — `W-20.2`, on `worker`. Nothing in this ticket sends anything
- Reminder rules and the scheduler — `W-20.2`
- SMS, push, or any channel but in-app and email
- Migrating Brevo's hosted templates — they are not in the repository and cannot be read from it

## 3. Flow

```
[any core event] --> [NotificationService.compose(event, recipient, data)]
   --> template for tenant + event --> rendered subject and body
   --> [core.notification, status QUEUED] --> [Storage Queue `notification`]

[recipient] --> GET /api/v1/notifications --> in-app list
```

Composition happens in `app`; the queue is the seam. That is `09-build-order.md:202` exactly:
*"composed in `app`, sent by `worker`. The queue joins them."*

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../notification/NotificationController.java` | new |
| Controller | `core/.../notification/NotificationTemplateController.java` | new |
| Service | `core/.../notification/NotificationService.java` | new |
| Service | `core/.../notification/TemplateRenderer.java` | new |
| Entity | `core/.../notification/Notification.java`, `NotificationTemplate.java` | new, each `@Table(schema="core")` |
| Repository | two | new |
| Enumeration | `core/.../notification/NotificationEvent.java`, `Channel.java`, `NotificationStatus.java` | new |

**The seam.** `NotificationService.compose(NotificationEvent event, UUID recipientEmployeeId, Map<String,Object> data)`
— the one method every module calls (`12-core-contracts.md:105`). `recipientEmployeeId` may be
null when `recipient_email` is supplied in `data`, for invitations. It writes the
`core.notification` row and puts one message on the `notification` queue through
`shared.queue.QueueProducer.send(queue, message)` (`shared/.../queue/QueueProducer.java:15`).

**`NotificationEvent`** — closed enum, per §5 row 15 of the contracts. The Payroll list is the
Brevo template map at `legacy/Payroll-Bend-SBoot/src/main/resources/application.properties:52-65`
(`poi.reminder`, `itdeclaration.reminder/lock/release`, `poi.submission`, `salary.slip`,
`employee/user.invitation`, `employee/user.credentials`); the rest are the events this
platform's own tickets raise.

| Event | Legacy source |
|---|---|
| `POI_REMINDER`, `POI_SUBMITTED` | `application.properties:52,55` |
| `IT_DECLARATION_REMINDER`, `IT_DECLARATION_LOCK`, `IT_DECLARATION_RELEASE` | `application.properties:53,54,56` |
| `PAYSLIP_READY` | `application.properties:57` (`salary.slip`) |
| `USER_INVITATION`, `EMPLOYEE_INVITATION` | `application.properties:60-61`; consumed by `W-24.2` |
| `CREDENTIALS` | `application.properties:64-65` — one event, the template decides the wording |
| `LEAVE_APPLIED`, `LEAVE_APPROVED`, `LEAVE_REJECTED`, `LEAVE_CANCELLED` | `W-16.3` |
| `APPROVAL_PENDING`, `APPROVAL_DECIDED` | `W-15.2` |
| `TIMESHEET_REMINDER` | HRMS `NotificationSchedular`; rule-driven by `W-20.2` |

A new event is a code change, deliberately: a template must exist for it, and seeding is per
event.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/notifications` | `?unreadOnly=&page=` | the caller's notifications | Bearer, tenant bound — recipient only, no action code |
| POST | `/api/v1/notifications/{id}/read` | — | `200` | Bearer, tenant bound, recipient only |
| GET | `/api/v1/notification-templates` | `?event=` | the tenant's templates | `@RequiresAction("core.notification_template.manage")` |
| PUT | `/api/v1/notification-templates/{event}` | subject, body, channels | `200` | `@RequiresAction("core.notification_template.manage")` |

`core.notification_template.manage` is added to `reference.action` by `W-11.3`
(`12-core-contracts.md:128`). Every Core endpoint must carry a code or a documented "recipient
only" scope — `EndpointGuardCoverageTest` (`12-core-contracts.md:45-46`).

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__notification_template.sql` | `core.notification_template` | yes | additive |
| `core/V0NN__notification.sql` | `core.notification` | yes | additive |

Version numbers assigned when the branch is cut — `migration/README.md:17-31`.

`notification_template`: `id uuid` · `tenant_id uuid NOT NULL` · `event varchar(64) NOT NULL` ·
`channel varchar(16) NOT NULL` · `subject varchar(255) NULL` · `body text NOT NULL` ·
`locale varchar(8) NOT NULL DEFAULT 'en'` · `is_active boolean NOT NULL DEFAULT true` ·
`effective_from date NOT NULL` · four audit columns.

`notification`: `id uuid` · `tenant_id uuid NOT NULL` ·
`recipient_employee_id uuid NULL REFERENCES core.employee(id)` ·
`recipient_email varchar(255) NULL` · `event varchar(64) NOT NULL` ·
`channel varchar(16) NOT NULL` · `subject varchar(255) NULL` · `body text NOT NULL` ·
`status varchar(16) NOT NULL` · `template_id uuid NULL REFERENCES core.notification_template(id)` ·
`queued_at timestamptz NOT NULL` · `read_at timestamptz NULL` ·
`subject_ref varchar(128) NULL` · four audit columns.

- [x] `tenant_id` on both, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, recipient_employee_id, read_at)` for the in-app list, `(tenant_id, status, queued_at)` for the delivery sweep, `(tenant_id, event, channel, effective_from DESC)` on templates
- [x] Money columns — none. A figure inside a notification body is rendered text, never a stored amount
- [x] Expand / contract — new tables only

**The rendered body is stored, not re-rendered.** A template edited next month must not change
what an employee was told last month — the same reasoning as `W-18.2`'s stamp, and
`template_id` records which template produced it.

**`recipient_email` is stored beside the employee id** because an invitation goes to someone
who is not yet an employee, and `W-24.2` needs that.

RLS and the `tenant_isolation` policy in the exact `CASE` form in each script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../notification/TemplateRendererTest.java` | placeholders substituted; a missing placeholder fails loudly rather than rendering `${name}`; HTML escaped |
| Unit | `core/.../notification/NotificationServiceTest.java` | no template for an event is an error, not a silent skip; the rendered body is stored |
| Unit | `core/.../notification/NotificationEventTest.java` | the enum holds exactly the §5 row 15 list; every event has a seeded default template for both channels |
| Integration | `core/.../notification/NotificationQueueIT.java` | composing enqueues exactly one message against Azurite, on the `notification` queue and no other |
| Integration | `core/.../notification/NotificationTemplateGuardIT.java` | `GET`/`PUT /notification-templates` return `403` without `core.notification_template.manage`; `/notifications` returns only the caller's rows |
| Integration | `core/.../notification/NotificationRlsIT.java` | a recipient cannot read another tenant's notifications, and cannot mark someone else's as read |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`TemplateRendererTest`'s escaping case matters: bodies carry employee-supplied text such as a
leave reason, and the email channel renders HTML.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres azurite
docker compose -f infra/docker/compose.yml up --build migrate
for t in notification notification_template; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
done
cd code/backend && mvn -q -pl core -Dit.test=NotificationQueueIT verify
cd code/backend && mvn -q verify

# nothing sends from app
grep -rn 'brevo\|smtp\|api.brevo.com' core/src/main/java/com/infinevo/core/notification/ \
  && echo "REVIEW: delivery leaked into app" || echo "composition only"
```

| Check | Expected |
|---|---|
| RLS on both | `t` twice |
| Queue test | green — one message on the queue |
| Delivery grep | `composition only` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Delivery is added here because composing without sending feels unfinished | **medium** | The grep; `W-20.2` owns the provider, and `app` holding an email credential defeats the split |
| Templates stay in Brevo and the table is bypassed | medium | The renderer takes a template id from this table; no Brevo template id is accepted |
| A missing template silently sends nothing | medium | An explicit error; seeded defaults per tenant at creation |
| Employee text is injected into an HTML email | medium | Escaped by the renderer, asserted in a unit test |
| Notification volume grows without bound | low, now | **Settled 2026-09-23:** `core.notification` is a second target of `W-22.2`'s sweep, with its own twelve-month default window |

## 10. Rollback

Nothing is deployed. Both scripts are additive and forward-only —
`migration/README.md:135-143`. Nothing is sent by this ticket, so a rollback loses queued
rows at worst.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | both tables, each in its own script |
| Flyway only, `ddl-auto` nowhere | two scripts; none added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | four indexes, `tenant_id` leading |
| Expand / contract | new tables only |
| No module references another module | `core` only; `payroll` events call a `core` service |

## 12. Gap inventory

| ID | Decision |
|---|---|
| Templates in a third-party dashboard and in Java strings | **Fixed.** Templates are rows, per tenant, versioned |
| HRMS notifications in-app only, no email | **Fixed.** One row, a channel column |
| No framework for payroll events (`PayRunServiceImpl.java:975` is one hard-coded email) | **Fixed.** Any event composes through the same path |
| DEBT-004 secrets in `.properties` | **Discounted.** No provider credential is in this ticket; `W-20.2` takes it from Key Vault |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **May a tenant edit its own templates?** **Recommend** yes, with platform-seeded defaults — the frozen system's templates sit in a Brevo account nobody in the tenant can reach, which is why nothing is ever worded correctly.
2. **Retention for notifications.** **Settled 2026-09-23:** `W-22.2`'s sweep gains `core.notification` as a second target rather than a second sweep being built. `core.tenant` carries `notification_retention_months`, defaulting to twelve — a notification has no statutory value after a year, unlike an audit row.

## 14. Doc correction — applied 2026-09-23

`09-build-order.md` stated Payroll has never sent an email for a payroll event. It sends the
salary slip at `PayRunServiceImpl.java:975`. The build order now says the gap is the absence
of a framework, not of any email.
