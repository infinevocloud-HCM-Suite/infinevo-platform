# Feature: Overdue Tracking & Past Due Subscription Sweep

| Field | Value |
|---|---|
| **Feature ID** | `W-86` · `CORE-SUBSCRIPTION` |
| **Promoted to** | `docs/target-state/features/W-86-overdue-tracking.md` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/core`, `code/backend/worker`, `code/frontend` (`src/core/admin`) |
| **Related gaps** | founder 2026-11-13 planned product roadmap ("Priority 1 — customer lifecycle: `W-86` Overdue tracking") |
| **Status** | **In Review (pending manager approval)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | nothing (founder decision 2026-11-05: `PAST_DUE` keeps blocking nothing; access remains uninterrupted) |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `core` (+ one scheduled worker sweep in `worker`) | 1 | **Pass (1 module + worker runtime)** |
| Flyway migration | None (creates 0 tables; `core.subscription.current_period_end` already exists) | 1 | **Pass (0 migrations)** |
| Externally testable behaviour | Platform admin edits `currentPeriodEnd` on the Plan card; daily worker sweep evaluates active subscriptions past their period end date, marks them `PAST_DUE`, records audit entries, and highlights overdue accounts on the `/admin` dashboard | 1 | **Pass (1 behaviour)** |
| Frontend area | `core/admin` (`TenantDetail.jsx` Plan card date picker & `/admin` dashboard alert card) | 1 | **Pass (1 area)** |

Within size cap across all four axes. No split required.

---

## 1. Problem & Decision

### 1.1 The problem today
1. **Unset period end dates**: While `core.subscription.current_period_end` was created in `V034__subscription.sql` (`code/backend/core/src/main/java/com/infinevo/core/subscription/Subscription.java:56-57`), the platform has no interface for platform administrators to set, edit, or extend this date. Today `current_period_end` is either null or statically populated at tenant creation.
2. **No automated overdue sweep**: When a tenant's subscription period passes, no process detects expiry. The tenant remains permanently `ACTIVE`.
3. **No overdue visibility**: Platform staff have no dashboard summary of accounts past due on renewal, preventing follow-ups before services require suspension.

### 1.2 The settled decision (founder, 2026-11-05)
- **`PAST_DUE` keeps blocking nothing**: Moving a tenant from `ACTIVE` to `PAST_DUE` does not disrupt tenant business operations. Employees can still clock in, record leaves, run payroll, and approve requests (`SubscriptionStatus.java:29-31`: `isActiveOrPastDue() == true`).
- **Overdue tracking serves operational visibility**: The sweep alerts the platform administrator and flags the tenant record so support/sales can engage the customer to renew their contract.
- **Manual suspension if needed**: If an account remains overdue past the grace window without payment, the platform admin manually transitions the status to `SUSPENDED` (activating `W-85` Core mutation blocking).

---

## 2. Scope

### In scope
- **Plan card date editor (`code/frontend/src/core/admin/TenantDetail.jsx`)**:
  - Add `currentPeriodEnd` (`DatePicker`) to the Plan card on `/admin/tenants/:id` (alongside Plan, Support plan, and Employee limit).
  - Update `PlanUpdateRequest` and `SubscriptionService.setPlan(...)` to persist `currentPeriodEnd` in `core.subscription`.
- **Automated Overdue Sweep (`SubscriptionOverdueJob` in `code/backend/worker`)**:
  - Daily scheduled job (01:00 UTC / 06:30 IST) protected by ShedLock (`@SchedulerLock(name = "subscription_overdue_sweep")`).
  - Finds all subscriptions where:
    `status = 'ACTIVE'` AND `current_period_end IS NOT NULL` AND `current_period_end < CURRENT_DATE`.
  - Atomically updates status to `PAST_DUE` and writes audit log entries with `actor = 'system'`, previous status `ACTIVE`, new status `PAST_DUE`.
  - Evicts the tenant's cache entry in `PermissionCache` / `EntitlementSnapshot` so the new status takes immediate effect.
- **Platform Admin Dashboard Overdue Alert Card (`code/frontend/src/core/admin/`)**:
  - On `/admin` platform dashboard (`PlatformDashboard.jsx`), surface a "Past Due Subscriptions" metric tile / card.
  - Clicking navigates to `/admin/tenants?status=PAST_DUE` with the overdue filter active.

### Out of scope
- Automatic credit card charging or dunning sequences (owned by `W-94`).
- Automatic escalation from `PAST_DUE` to `SUSPENDED` (suspension remains an explicit platform admin decision).
- Customer-facing payment portal.

---

## 3. Flow

```
[Platform Admin Sets current_period_end on /admin/tenants/:id]
                            │
                            ▼
[PUT /api/v1/tenants/{id}/plan { ..., currentPeriodEnd: "2026-11-15" }]
                            │
                            ▼
[Saved to core.subscription.current_period_end]

──────────────────────── Next Night (01:00 UTC) ────────────────────────

[SubscriptionOverdueJob Fires in Worker (ShedLock: subscription_overdue_sweep)]
                            │
                            ▼
[Query Subscriptions: status='ACTIVE' AND current_period_end < CURRENT_DATE]
                            │
            ┌───────────────┴───────────────┐
            ▼                               ▼
    [No Overdue Rows]            [Found N Overdue Tenants]
            │                               │
            ▼                               ▼
       Sweep Ends            [Update status = 'PAST_DUE']
                                            │
                                            ├─► [Log to core.audit_log (actor='system')]
                                            ├─► [Evict Tenant PermissionCache]
                                            └─► [Alert Placed on /admin Dashboard]
```

---

## 4. Architecture & Changes

### 4.1 Backend changes (`code/backend/core` & `code/backend/worker`)

| Layer | File | Change |
|---|---|---|
| DTO | `com.infinevo.core.subscription.PlanUpdateRequest.java` | Add `LocalDate currentPeriodEnd` field (`record PlanUpdateRequest(..., LocalDate currentPeriodEnd)`). |
| Service | `com.infinevo.core.subscription.SubscriptionService.java` | Support `currentPeriodEnd` in `setPlan(...)`; add `List<UUID> sweepOverdueSubscriptions(LocalDate asOfDate)`. |
| ServiceImpl | `com.infinevo.core.subscription.SubscriptionServiceImpl.java` | In `setPlan`, update `subscription.setCurrentPeriodEnd(...)`; in `sweepOverdueSubscriptions`, find active overdue rows, batch update to `PAST_DUE`, audit, and evict cache. |
| Query | `com.infinevo.core.subscription.SubscriptionRepository.java` | Add query: `findOverdueActive(LocalDate asOfDate)`. |
| Worker Job | `com.infinevo.worker.subscription.SubscriptionOverdueJob.java` | **New**. ShedLock-guarded `@Scheduled` daily job calling `subscriptionService.sweepOverdueSubscriptions(LocalDate.now())`. |
| Overview | `com.infinevo.core.tenant.TenantOverviewService.java` | Return `pastDueCount` on platform admin overview (`GET /api/v1/admin/dashboard`). |

### 4.2 Frontend changes (`code/frontend`)

| File | Change |
|---|---|
| Tenant Detail | `code/frontend/src/core/admin/TenantDetail.jsx` | Add `currentPeriodEnd` `DatePicker` to Plan card (lines 275–320); hand `currentPeriodEnd` in `onSavePlan` (`file:code/frontend/src/core/admin/TenantDetail.jsx:165-175`). |
| Tenant API | `code/frontend/src/core/admin/tenantService.js` | Include `current_period_end` in `setPlan(id, { plan, supportPlan, employeeLimit, currentPeriodEnd })`. |
| Admin Dashboard | `code/frontend/src/core/admin/PlatformDashboard.jsx` | Add "Past Due Accounts" metric tile linking to `/admin/tenants?status=PAST_DUE`. |

---

## 5. Database Changes

> **Creates no database tables and executes 0 Flyway migrations.**

- [x] Standing rule: Reuses existing column `core.subscription.current_period_end` created in `V034__subscription.sql`.
- [x] Standing rule: An existing index on `core.subscription (tenant_id, status)` supports overdue queries.
- [x] Standing rule: No schema changes; purely operational lifecycle automation.

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `com.infinevo.core.subscription.SubscriptionServiceImplTest.java` | Verifies `setPlan` sets and updates `currentPeriodEnd`; verifies `sweepOverdueSubscriptions` moves expired active tenants to `PAST_DUE`, records audit, and ignores unexpired tenants. |
| Unit | `com.infinevo.worker.subscription.SubscriptionOverdueJobTest.java` | Verifies worker job delegates to service with current date and handles exceptions gracefully without failing task. |
| Integration | `com.infinevo.core.subscription.SubscriptionOverdueIT.java` | Seeds tenant with `current_period_end = yesterday`, runs sweep, verifies status becomes `PAST_DUE` in database and cache is refreshed; verifies tenant remains able to access endpoints (`isActiveOrPastDue() == true`). |
| Component | `code/frontend/src/core/admin/TenantDetail.test.jsx` | Verifies `currentPeriodEnd` date picker renders, captures date change, and submits with Plan payload. |

---

## 7. Verification

Verification commands and steps:

```bash
# 1. Run core & worker tests for overdue sweep
./mvnw -pl core,worker test -Dtest='SubscriptionServiceImplTest,SubscriptionOverdueJobTest,SubscriptionOverdueIT'

# 2. Run frontend admin tests
cd code/frontend
npm test -- src/core/admin/TenantDetail.test.jsx src/core/admin/PlatformDashboard.test.jsx

# 3. Frontend lint check
npm run lint
```

### Verification matrix

| Check | Action | Expected Result |
|---|---|---|
| Set Period End | Platform admin picks `2026-12-31` on Plan card & clicks Save plan | `core.subscription.current_period_end` set to `2026-12-31`; displayed under Overview. |
| Overdue Sweep Execution | Tenant with `current_period_end = 2026-11-10` and `status = 'ACTIVE'` processed by sweep | Status changes to `PAST_DUE`; audit record created with `actor = 'system'`. |
| Uninterrupted Service | Call `POST /api/v1/employees` or clock-in as user in `PAST_DUE` tenant | Operation succeeds (200/201 OK) per 2026-11-05 founder decision. |
| Dashboard Visibility | Open `/admin` platform dashboard | "Past Due Accounts" metric increments; click navigates to filtered tenant list. |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Multi-replica worker race condition running sweep concurrently | Low | Job is strictly guarded by ShedLock (`lockAtMostFor = "PT30M"`), guaranteeing single-worker execution across replicas. |
| Premature status transition due to server timezone mismatch | Low | Use tenant locale or standardize cutoff comparison to UTC midnight (`LocalDate.now(ZoneOffset.UTC)`). |
| Customer panic on `PAST_DUE` label | Low | Status is visible to platform admin and tenant admin; employee portal users see no billing warnings. |

---

## 9. Rollback

1. The sweep job can be disabled in worker configuration (`worker.subscription.overdue-sweep.enabled=false`).
2. Status can be reverted to `ACTIVE` directly via the platform admin status dropdown on `TenantDetail.jsx`.

