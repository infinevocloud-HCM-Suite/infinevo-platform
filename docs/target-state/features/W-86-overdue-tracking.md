# Feature: Overdue tracking — period end date and the nightly past-due sweep

| Field | Value |
|---|---|
| **Feature ID** | `W-86` · `CORE-SUBSCRIPTION` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/core`, `code/backend/worker`, `code/backend/migration`, `code/frontend/src/core/admin` |
| **Related gaps** | founder 2026-11-13 roadmap, "Priority 1 — customer lifecycle" |
| **Status** | Built on `feature/krushna-specs` (`V199`, 100% tests/lint green). |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | nothing. Founder decision 2026-11-05: `PAST_DUE` blocks nothing. |

## What this ticket does

| # | Behaviour |
|---|---|
| 1 | The platform admin sets "Current period ends" on the Plan card. **Nothing sets this date today**, so this is the only way it gets a value. |
| 2 | Every night a job moves each `ACTIVE` customer whose period end is before today (UTC) to `PAST_DUE`. |
| 3 | The platform home page shows a "Past due" tile that opens the tenant list filtered to those customers. |

`PAST_DUE` customers keep full access. Moving one to `SUSPENDED` stays a manual step by the
platform admin (`W-85.1` and `W-85.2` then make it view-only).

## Size

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `core` (plus one job class in `worker`) | 1 | Pass |
| Flyway migration | **1** (was wrongly "0") | 1 | Pass |
| Testable behaviour | period end is saved; an overdue active customer becomes `PAST_DUE` overnight | 1 | Pass |
| Frontend area | `core/admin` | 1 | Pass |

Size **M**.

---

## 1. Problem

| # | Today | Evidence |
|---|---|---|
| 1 | `core.subscription.current_period_end` exists but nothing writes it; it is always null. | `code/backend/core/src/main/java/com/infinevo/core/subscription/Subscription.java:143` has no caller |
| 2 | The plan is saved through `core.set_subscription_plan(uuid, text, text, integer)`, which has no period-end argument. | `code/backend/migration/src/main/resources/db/migration/core/V188__subscription_plan.sql:27-32`; `SubscriptionServiceImpl.java:284` |
| 3 | No job looks for expired periods, so a customer stays `ACTIVE` for ever. | no class under `code/backend/worker` reads the subscription |
| 4 | The platform home page shows Active and Suspended tiles but no Past due tile, though the API already returns the count. | `code/frontend/src/core/admin/PlatformHome.jsx:137-142`; `code/backend/core/src/main/java/com/infinevo/core/tenant/TenantQueryService.java:122-136` |
| 5 | The tenant list has no status filter. | `code/frontend/src/core/admin/TenantList.jsx` |

## 2. Scope

**In scope**

- Period end on the plan request, the plan write function and the Plan card.
- The nightly sweep job.
- "Past due" tile on the platform home page.
- Status filter on the tenant list, driven by `?status=`.
- A warning on the tenant page when the status is `ACTIVE` and the period end is in the past.

**Out of scope**

- Email or in-app alert to the platform admin. The tile is the alert in this ticket.
- Automatic move from `PAST_DUE` to `SUSPENDED`, or back to `ACTIVE` when the date is moved forward. Both stay manual.
- Charging, dunning, a customer payment page — `W-94`.

## 3. Flow

```
Platform admin --> TenantDetail Plan card --> PUT /api/v1/tenants/{id}/subscription/plan
               --> SubscriptionServiceImpl.updatePlan --> core.set_subscription_plan(…, date)

01:00 UTC --> SubscriptionOverdueJob --> core.list_overdue_subscriptions(today UTC)
          --> for each tenant: bind TenantContext --> SubscriptionService.markPastDue
          --> core.set_subscription_status(tenant, 'PAST_DUE') --> permissionCache.bumpVersion
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| DTO | `core/.../subscription/PlanUpdateRequest.java` | Add `LocalDate currentPeriodEnd`. Null clears the date, like the other three fields. |
| Service | `core/.../subscription/SubscriptionService.java` | Add `boolean markPastDue(UUID tenantId, LocalDate asOf)`. |
| ServiceImpl | `core/.../subscription/SubscriptionServiceImpl.java` | `updatePlan` calls the five-argument function and sets `subscription.setCurrentPeriodEnd(...)` so the audit listener records it. `markPastDue` re-reads the row; if it is still `ACTIVE` with a period end before `asOf`, it applies `PAST_DUE` through the existing `applyStatus` path (function call, cache bump, audit). Returns whether it changed anything. |
| Job | `worker/.../subscription/SubscriptionOverdueJob.java` | **New.** Same shape as `LeaveAccrualJob` (`worker/.../leave/LeaveAccrualJob.java:40-63`): `@Scheduled(cron = "${infinevo.scheduler.subscription-overdue.cron:0 0 1 * * *}", zone = "UTC")`, `@SchedulerLock(name = "SubscriptionOverdueJob", lockAtMostFor = "PT30M")`. Reads the tenant ids from `core.list_overdue_subscriptions(?)` with `LocalDate.now(ZoneOffset.UTC)`, then binds each tenant in turn and calls `markPastDue`. One tenant's failure is logged and does not stop the rest. |

Why the list function: `core.subscription` has row-level security
(`V034__subscription.sql:20-29`). A plain repository query with no tenant bound returns
no rows, so the sweep would silently find nothing.

No change to `TenantQueryService.summary()` — `byStatus` already carries `PAST_DUE`.

### API contract

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `PUT` | `/api/v1/tenants/{id}/subscription/plan` | `{ plan, supportPlan, employeeLimit, currentPeriodEnd }` — `currentPeriodEnd` is `YYYY-MM-DD` or null | `SubscriptionResponse` (already has `currentPeriodEnd`) | `core.tenant.provision` |
| `GET` | `/api/v1/tenants/summary` | — | unchanged | `core.tenant.provision` |

An older client that sends no `currentPeriodEnd` clears the date. The only client is
`TenantDetail.jsx`, changed in this ticket.

## 5. Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/core/admin/TenantDetail.jsx` | Add a "Current period ends" date picker to the Plan card; include it in `planDraft`, `applyPlan` and `onSavePlan`. Show a warning under the status select when status is `ACTIVE` and the date is before today: "The period has ended. Tonight's check will mark this customer past due again." |
| `code/frontend/src/core/admin/tenantService.js` | `setPlan(id, { plan, supportPlan, employeeLimit, currentPeriodEnd })` sends the new field. |
| `code/frontend/src/core/admin/PlatformHome.jsx` | Add a "Past due" tile from `byStatus.PAST_DUE`, linking to `/admin/tenants?status=PAST_DUE`. |
| `code/frontend/src/core/admin/TenantList.jsx` | Read `status` with `useSearchParams`; add a Status select above the table; filter the already-loaded rows on the client. |

**Routes added:** none. `/admin` and `/admin/tenants` exist (`code/frontend/src/core/index.js:177-178`).

## 6. Database changes

| Migration | Contents | Tenant-aware? | Reversible? |
|---|---|---|---|
| next free Flyway version at build time, `core/V…__subscription_period_end.sql` | See below. No table, no column. | n/a | yes — drop the two new functions and the grant |

1. `core.set_subscription_plan(uuid, text, text, integer, date)` — the V188 function plus `current_period_end = p_current_period_end`. `SECURITY DEFINER`, `REVOKE … FROM PUBLIC`, `GRANT EXECUTE … TO app_user`. **Keep the four-argument function**, so the previous release still works during a rolling deploy.
2. `core.list_overdue_subscriptions(p_as_of date) RETURNS TABLE (tenant_id uuid)` — rows with `status = 'ACTIVE' AND current_period_end < p_as_of`. `SECURITY DEFINER`, granted to `worker_user` only, on the pattern of `core.list_tenants_for_sweep()` (`V093__reminder_rule.sql:51-61`).
3. `GRANT EXECUTE ON FUNCTION core.set_subscription_status(uuid, text) TO worker_user` — today only `app_user` holds it (`V034__subscription.sql:246`).

No index. `core.subscription` holds one row per customer; a scan is cheap. The first draft
said an index on `(tenant_id, status)` exists — it does not, and none is needed.

- [x] No new table
- [x] Expand only — nothing dropped

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../subscription/SubscriptionServiceTest.java` (extend) | `updatePlan` saves and clears the date; `markPastDue` changes an overdue active row, and leaves alone a row that is not overdue, has no date, or is `SUSPENDED`, `CANCELLED` or already `PAST_DUE` |
| Integration | `core/src/test/.../subscription/SubscriptionPlanIT.java` (extend) | The five-argument function writes the date; the four-argument one still works |
| Integration | `worker/src/test/.../subscription/SubscriptionOverdueJobIT.java` | Three tenants (overdue, not overdue, no date): after `execute(asOf)` only the first is `PAST_DUE`; an audit row exists; a run with no tenant bound still finds the row (proves the list function beats RLS) |
| Unit | `worker/src/test/.../subscription/SubscriptionOverdueJobTest.java` | One tenant throwing does not stop the next |
| Component | `code/frontend/src/core/admin/TenantDetail.test.jsx` | Date picker shows the saved date and sends it; the warning shows for `ACTIVE` with a past date |
| Component | `code/frontend/src/core/admin/PlatformHome.test.jsx` | Past due tile shows the count and links to the filtered list |
| Component | `code/frontend/src/core/admin/TenantList.test.jsx` | `?status=PAST_DUE` shows only those rows |

## 8. Verification

```bash
cd code/backend && ./mvnw -pl core,worker,migration verify
cd code/frontend && npm test -- src/core/admin && npm run lint
```

| Check | Expected | Result |
|---|---|---|
| Save `2026-12-31` on the Plan card | Overview shows "Current period ends 31 Dec 2026"; the audit tab shows the change | not run |
| Clear the date and save | Column is null | not run |
| Tenant `ACTIVE`, period end yesterday; run the job | Status `PAST_DUE`; audit row written | not run |
| Tenant `ACTIVE`, period end today | Unchanged (overdue means before today, UTC) | not run |
| Tenant `SUSPENDED`, period end last month | Unchanged | not run |
| `POST /api/v1/employees` as the `PAST_DUE` tenant | 201 | not run |
| Open `/admin` | Past due tile shows 1; click opens the list with only that tenant | not run |
| Run the job twice | Second run changes nothing | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Two worker replicas run the sweep together | Low | ShedLock, as every other nightly job |
| A customer in India is marked overdue up to 5.5 hours late or early against its own midnight | Certain, harmless | One rule for all: the UTC date. `PAST_DUE` blocks nothing |
| The audit row for a job run has no user | Certain | The implementer checks how the audit listener names the actor with no signed-in user and records it in the pull request; if it fails on a null actor, the job sets a fixed "system" actor |
| Admin sets `ACTIVE` but leaves a past date, and the customer flips back overnight | Medium | The warning on the tenant page (§5) |

## 10. Rollback

1. Stop the sweep: set `infinevo.scheduler.subscription-overdue.cron` to `-` on the worker and restart it.
2. To put a customer back: on its tenant page **first move "Current period ends" to a future date (or clear it) and save, then set the status to `ACTIVE`**. Setting the status alone does not hold — the next night's run marks it past due again.
3. The migration adds functions only. They can stay in place after the code is reverted.
