# Feature: Performance Review Cycles & Manager Ratings

| Field | Value |
|---|---|
| **Feature ID** | `W-106.1` · `HRMS-PERFORMANCE` |
| **Promoted to** | `docs/target-state/features/W-106-1-performance-review-cycles.md` — **`W-106-1` with hyphens**, never `W-106.1` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration`, `code/backend/app` (one test), `code/frontend` (`src/hrms/performance`, `src/shell/portal`) |
| **Related gaps** | founder 2026-11-13 product roadmap ("Priority 2 — competing with Keka: `W-106` Performance reviews and goals") |
| **Status** | **Spec corrected 2026-11-19 after review. Code on `feature/krushna-specs` does not yet meet it.** |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | nothing |

---

## Size cap — this ticket splits

| Ticket | Scope | Size |
|---|---|:---:|
| **`W-106.1`** (this spec) | Review cycles, self-review, manager rating on a fixed 1–5 scale, HR progress and closure | M |
| **`W-106.2`** | Employee goals and OKRs, check-ins, link to a review cycle | M |

`W-106.2` shows an employee's linked goals on the review. Nothing in this spec depends on goals.

---

## 1. Problem

| # | What is missing today |
|---|---|
| 1 | HR cannot start a quarterly or annual review; appraisals live in spreadsheets. |
| 2 | Managers cannot rate their direct reports in the system, although `core.reporting_line` already knows who reports to whom. |
| 3 | Employees have no self-service screen to write a self-review or to read their manager's rating. |

---

## 2. Scope

### In scope

| Area | What is delivered |
|---|---|
| Cycles | HR creates a cycle (name, period, self-review deadline, manager-review deadline), activates it, edits its deadlines, closes it. |
| Reviews | Activation creates one review per active employee. The employee submits a self-review; then the manager rates. |
| Rating scale | **Fixed 1–5.** 1 Needs improvement · 2 Partially meets · 3 Meets · 4 Exceeds · 5 Outstanding. There is no per-cycle scale. |
| HR screen | `/hrms/performance-cycles`: cycle list, progress, average rating, reviews of a cycle, reassign manager, close. |
| Manager screen | `/hrms/performance-reviews`: direct reports' reviews and the rating drawer. |
| Employee panel | "Reviews" tab on `/me`, served by a backend panel provider. |
| Permissions | The four `hrms.performance.*` actions seeded by `V193`. |

### Out of scope

- Goals and OKRs (`W-106.2`).
- 360-degree and peer feedback.
- Linking ratings to salary revision.
- A separate approval step on the manager's rating. There is none.

### Rules

**Cycle states**

| From | To | Who | Rule |
|---|---|---|---|
| `DRAFT` | `ACTIVE` | `hrms.performance.manage` | Creates the reviews. |
| `ACTIVE` | `CLOSED` | `hrms.performance.manage` | Allowed at any time. Reviews not yet `COMPLETED` stay as they are and become read-only. |
| `CLOSED` | — | — | Final. Nothing in a closed cycle can change. |

**Review states**

| State | Meaning | Who moves it on | Next |
|---|---|---|---|
| `PENDING_SELF_REVIEW` | Waiting for the employee | The employee, own review only | `PENDING_MANAGER_REVIEW` |
| `PENDING_MANAGER_REVIEW` | Waiting for the rater | The assigned manager, or HR (see below) | `COMPLETED` |
| `COMPLETED` | Rated | — | Final. It cannot be edited or re-rated by anyone. |

**Who rates, who sees**

| Rule | Detail |
|---|---|
| Rating needs a self-review first | `manager-submit` is refused with `409` unless the review is `PENDING_MANAGER_REVIEW`. |
| Nobody rates themselves | `manager-submit` is refused with `403` when the caller is the review's own employee, whatever actions they hold. |
| No manager | An employee with no primary reporting line gets a review with **no manager** (`manager_id` null). It is never set to the employee. |
| HR rates unassigned reviews | A holder of `hrms.performance.manage` may rate any review in the tenant that is not their own. This is how a review with no manager is completed. |
| HR reassigns | A holder of `hrms.performance.manage` sets or changes the manager of a review that is not `COMPLETED`. The new manager must be an active employee and not the review's employee. |
| Employee sees the rating | The employee sees `managerRating` and `managerComments` once the review is `COMPLETED`, and not before. |
| Manager sees the self-review | The manager sees `selfRating` and `selfComments` once the self-review is submitted. |

**Deadlines**

| Rule | Detail |
|---|---|
| Self-review | Refused with `409` and the message "The self-review deadline has passed" when today is after `self_review_deadline`. |
| Manager rating | Refused with `409` and the message "The manager review deadline has passed" when today is after `manager_review_deadline`. This applies to HR too. |
| Extending | HR edits either deadline while the cycle is `DRAFT` or `ACTIVE`. `self_review_deadline` must not be after `manager_review_deadline`. |
| Day used | The deadline day itself is still allowed. |

---

## 3. Flow

```
[HR] --> /hrms/performance-cycles --> POST /cycles, POST /cycles/{id}/activate
                                          --> one hrms.performance_review per active employee
[Employee] --> /me › Reviews --> PATCH /reviews/{id}/self-submit --> PENDING_MANAGER_REVIEW
[Manager, or HR when unassigned] --> /hrms/performance-reviews
                                 --> PATCH /reviews/{id}/manager-submit --> COMPLETED
[Employee] --> /me › Reviews --> reads manager rating and comments
[HR] --> POST /cycles/{id}/close --> CLOSED
```

---

## 4. Backend changes

All classes are in `code/backend/hrms/src/main/java/com/infinevo/hrms/`.

| Layer | File | Change |
|---|---|---|
| Entity | `performance/PerformanceCycle.java` | Exists. Add `updateDeadlines`. Stop using `ratingScaleMax`. |
| Entity | `performance/PerformanceReview.java` | Exists. `managerId` becomes nullable. `submitManagerReview` refuses any state except `PENDING_MANAGER_REVIEW`. `reassignManager` refuses `COMPLETED`. |
| Enum | `performance/PerformanceCycleStatus.java`, `PerformanceReviewStatus.java` | Exist. No change. |
| Repository | `performance/PerformanceCycleRepository.java`, `PerformanceReviewRepository.java` | Exist. Add a list of reviews by cycle, and a batch lookup used by activation. |
| Service | `performance/PerformanceReviewService.java` / `PerformanceReviewServiceImpl.java` | Exist. Enforce every rule in §2. Activation loads reporting lines and existing reviews in batches and saves with `saveAll` — **no query per employee**. |
| Controller | `performance/PerformanceCycleController.java` | Exists (`/api/v1/performance/cycles`). Add edit-deadlines and list-reviews. |
| Controller | `performance/PerformanceReviewController.java` | Exists (`/api/v1/performance/reviews`). Add reassign-manager. |
| DTO | `performance/CreateCycleRequest.java` | Remove `ratingScaleMax`. |
| DTO | `performance/UpdateCycleDeadlinesRequest.java`, `ReassignManagerRequest.java` | **New.** |
| DTO | `performance/PerformanceCycleResponse.java` | Remove `ratingScaleMax`. |
| Portal | `portal/ReviewsPanelProvider.java` | **New.** `PortalPanelProvider` with code `reviews`, module `HRMS`, title "Reviews", display order 10, endpoint `/api/v1/performance/reviews/my`, action `hrms.performance.read_own`. Without it the tab never shows: `PortalLayout.jsx` draws only the panels the server returns. |
| Navigation | `navigation/HrmsNavigation.java` | Exists: `PERFORMANCE_CYCLES`, `PERFORMANCE_REVIEWS`. No change. |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/performance/cycles` | — | `List<PerformanceCycleResponse>` | `hrms.performance.manage` |
| `POST` | `/api/v1/performance/cycles` | `CreateCycleRequest` (name, periodStart, periodEnd, selfReviewDeadline, managerReviewDeadline) | `201` `PerformanceCycleResponse` | `hrms.performance.manage` |
| `GET` | `/api/v1/performance/cycles/{id}` | — | `PerformanceCycleResponse` | `hrms.performance.manage` |
| `PATCH` | `/api/v1/performance/cycles/{id}` **new** | `UpdateCycleDeadlinesRequest` (selfReviewDeadline, managerReviewDeadline) | `PerformanceCycleResponse`; `409` when `CLOSED` | `hrms.performance.manage` |
| `POST` | `/api/v1/performance/cycles/{id}/activate` | — | `PerformanceCycleResponse` | `hrms.performance.manage` |
| `POST` | `/api/v1/performance/cycles/{id}/close` | — | `PerformanceCycleResponse` | `hrms.performance.manage` |
| `GET` | `/api/v1/performance/cycles/{id}/summary` | — | `CycleSummaryResponse` (total, completed, unassigned, average rating) | `hrms.performance.manage` |
| `GET` | `/api/v1/performance/cycles/{id}/reviews` **new** | — | `List<PerformanceReviewResponse>` | `hrms.performance.manage` |
| `GET` | `/api/v1/performance/reviews/my` | — | `List<PerformanceReviewResponse>` | `hrms.performance.read_own` |
| `PATCH` | `/api/v1/performance/reviews/{id}/self-submit` | `SelfReviewRequest` (selfRating 1–5, comments) | `PerformanceReviewResponse` | `hrms.performance.update_own`, own review only |
| `GET` | `/api/v1/performance/reviews/team` | — | `List<PerformanceReviewResponse>` | `hrms.performance.review_team` |
| `PATCH` | `/api/v1/performance/reviews/{id}/manager-submit` | `ManagerReviewRequest` (managerRating 1–5, comments) | `PerformanceReviewResponse` | `hrms.performance.review_team`; the assigned manager, or a holder of `hrms.performance.manage`; never the review's own employee |
| `PATCH` | `/api/v1/performance/reviews/{id}/manager` **new** | `ReassignManagerRequest` (managerId) | `PerformanceReviewResponse` | `hrms.performance.manage` |

Errors, as the controllers already map them: bad input `400`, not allowed `403`, wrong state or passed deadline `409`. A rating outside 1–5 is `400`, never `500`.

---

## 5. Frontend changes

| File | Change |
|---|---|
| `src/hrms/performance/performanceService.js` | Exists. Add edit-deadlines, list-reviews-of-cycle, reassign-manager. |
| `src/hrms/performance/PerformanceCyclesScreen.jsx` | Exists. Remove the rating-scale field and column. Add "Edit deadlines". The summary drawer lists the cycle's reviews, flags those with no manager, and offers "Assign manager" and "Rate". |
| `src/hrms/performance/TeamReviewsScreen.jsx` | Exists. "Evaluate" shows only on `PENDING_MANAGER_REVIEW`. `PENDING_SELF_REVIEW` shows "Waiting for self-review". `COMPLETED` shows a read-only view. |
| `src/shell/portal/panels/MyReviews.jsx` | Exists. Shows the manager's rating and comments only on `COMPLETED`. |
| `src/hrms/index.js` | Exists: `portalPanels` already maps `reviews` to `MyReviews`. No change. |
| `src/shell/portal/PortalLayout.jsx` | Exists: icon for `reviews`. No tab is added here; tabs come from the server. |
| `src/shell/navigation/navLabels.js` | Exists: both labels. No change. |

**Every list screen:** when loading fails it shows an error state with a Retry button, not an empty table.

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/hrms/performance-cycles` | `PerformanceCyclesScreen` | HRMS feed item `hrms.performance_cycles` (`hrms.performance.manage`) |
| `/hrms/performance-reviews` | `TeamReviewsScreen` | HRMS feed item `hrms.performance_reviews` (`hrms.performance.review_team`) |
| `/me/reviews` | `MyReviews` | Portal panel `reviews` (`hrms.performance.read_own`) |

---

## 6. Database changes

> Flyway only. Never `ddl-auto`.

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `hrms/V193__performance_review_cycles.sql` (exists — not edited) | `hrms.performance_cycle`, `hrms.performance_review`; four `hrms.performance.*` actions; role grants and the new-tenant trigger | yes, `tenant_id` + RLS on both | forward-only |
| `hrms/V<next>__performance_review_fixes.sql` — **new, next free Flyway version at build time** | See below | yes | forward-only |

The follow-up migration does three things:

| Change | Why |
|---|---|
| `UPDATE hrms.performance_cycle SET rating_scale_max = 5`; replace `chk_perf_cycle_scale` with `CHECK (rating_scale_max = 5)` | The scale is fixed at 5. The column is pinned, not dropped, so no step is destructive. |
| `ALTER TABLE hrms.performance_review ALTER COLUMN manager_id DROP NOT NULL` | A review may have no manager. |
| `UPDATE hrms.performance_review SET manager_id = NULL WHERE manager_id = employee_id` | Clears self-managed reviews created by the first build. |

- [x] `tenant_id` and an RLS policy on every table (`V193`).
- [x] `tenant_id` leads every index (`V193`).
- [x] Money rule: not applicable — ratings are integers.
- [x] No destructive step.

---

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/src/test/.../performance/PerformanceReviewServiceImplTest.java` (exists, extend) | Activation; both transitions; **rating 0 and 6 are refused**; **manager rating before the self-review is refused**; **a `COMPLETED` review cannot be re-rated**; **an employee with no manager gets `managerId` null**; **both deadlines refuse a late submit**; deadlines can be extended; reassign rules; activation issues a fixed number of queries. |
| Unit | `hrms/src/test/.../performance/PerformanceSecurityGuardTest.java` (exists, extend) | Own self-review only; assigned manager only; HR may rate an unassigned review; **nobody rates their own review, including HR**; an employee does not see the manager's rating before `COMPLETED`. |
| Unit | `hrms/src/test/.../portal/ReviewsPanelProviderTest.java` (new) | Code, order, endpoint and action of the panel. |
| Integration | `hrms/src/test/.../performance/PerformanceRlsIT.java` (**new — required**) | Tenant A cannot read or write tenant B's cycles and reviews as `app_user`. |
| Integration | `app/src/test/.../portal/PortalPanelDiscoveryIT.java` (exists, update) | Provider count and code list include `reviews`. With `goals` from `W-106.2` the count is 12. |
| Component | `src/hrms/performance/PerformanceCyclesScreen.test.jsx` (exists, extend) | No scale field; edit deadlines; assign manager; load-failure error state. |
| Component | `src/hrms/performance/TeamReviewsScreen.test.jsx` (exists, extend) | "Evaluate" only on `PENDING_MANAGER_REVIEW`; load-failure error state. |
| Component | `src/shell/portal/panels/MyReviews.test.jsx` (exists, extend) | Self-review submit; manager rating shown only on `COMPLETED`; load-failure error state. |

---

## 8. Verification

```bash
cd code/backend
./mvnw -pl hrms -am verify      # unit tests and PerformanceRlsIT
./mvnw -pl app -am verify       # PortalPanelDiscoveryIT

cd ../frontend
npm test -- src/hrms/performance/ src/shell/portal/panels/MyReviews.test.jsx
npm run lint
```

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Activate a cycle | One review per active employee; an employee with no reporting line has `manager_id` null | not run |
| 2 | Open `/me` as an employee | A "Reviews" tab shows | not run |
| 3 | Employee submits self-rating 4 | `self_rating = 4`, status `PENDING_MANAGER_REVIEW` | not run |
| 4 | Manager calls `manager-submit` while `PENDING_SELF_REVIEW` | `409` | not run |
| 5 | Manager submits rating 5 | `manager_rating = 5`, status `COMPLETED` | not run |
| 6 | Manager calls `manager-submit` again | `409`; rating unchanged | not run |
| 7 | Rating 6 on either submit | `400` | not run |
| 8 | A user with the manager role calls `manager-submit` on their own review | `403` | not run |
| 9 | HR rates a review that has no manager | `COMPLETED` | not run |
| 10 | HR assigns a manager to that kind of review | The review shows in that manager's team list | not run |
| 11 | Self-submit the day after `self_review_deadline` | `409` "The self-review deadline has passed" | not run |
| 12 | HR moves the deadline forward, employee submits again | Accepted | not run |
| 13 | Employee reads own review before `COMPLETED` | No manager rating or comments in the response | not run |
| 14 | Any write in a `CLOSED` cycle | `409` | not run |
| 15 | Employee rates a peer | `403` | not run |
| 16 | `PerformanceRlsIT` | Passes | not run |
| 17 | `PortalPanelDiscoveryIT` | Passes with `reviews` in the list | not run |
| 18 | Create-cycle form | No rating-scale field | not run |

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Many employees have no reporting line | Medium | The cycle summary counts unassigned reviews; HR assigns a manager or rates them. |
| The only HR user has no manager | Low | Their review needs a second holder of `hrms.performance.manage` to rate it, or an assigned manager. It stays pending otherwise. |
| A manager misses the deadline | Medium | HR extends the deadline, or reassigns the review. |
| Large tenant activation is slow | Medium | Batch reads and `saveAll`; the unit test fixes the query count. |

---

## 10. Rollback

| Step | Action |
|---|---|
| 1 | Hide the feature: delete the `hrms.performance.*` rows from `core.role_action` for the tenant. Grants live in that table, not in Keycloak. Menu items and the portal tab disappear with the grants. |
| 2 | Data stays. Migrations are forward-only; cycles can be closed. No payroll or attendance table is touched. |

---

## 11. Code gaps against this spec (2026-11-19)

| Gap | Where |
|---|---|
| No `reviews` panel provider, so the "Reviews" tab never shows | `code/backend/hrms/src/main/java/com/infinevo/hrms/portal/` (only goals and timesheet providers); `code/frontend/src/hrms/index.js:118` |
| An employee with no manager becomes their own manager | `PerformanceReviewServiceImpl.java:277-278` |
| A holder of the manager role or HR can rate their own review | `PerformanceReviewServiceImpl.java:238-243` |
| Manager submit does not check the review state: it can skip the self-review and overwrite `COMPLETED` | `PerformanceReviewServiceImpl.java:249-259`; `PerformanceReview.java:134-141` |
| "Evaluate" is offered in every state | `TeamReviewsScreen.jsx:152-157` |
| Scale up to 10 is accepted while the table caps ratings at 5, so 6–10 returns `500` | `PerformanceReviewServiceImpl.java:210,255,300`; `CreateCycleRequest.java`; `PerformanceCyclesScreen.jsx:160,288`; `V193__performance_review_cycles.sql:20,57-58` |
| Deadlines are stored and never checked; no endpoint edits them | `PerformanceReviewServiceImpl.java:184-262` |
| Activation runs queries and a save per employee | `PerformanceReviewServiceImpl.java:107-116,264-275` |
| `reassignManager` has no endpoint | `PerformanceReview.java:143-147` |
| `manager_id` is `NOT NULL` | `V193__performance_review_cycles.sql:44` |
| `PerformanceRlsIT` is missing; no tests for out-of-range rating, missing manager, manager-before-self | `code/backend/hrms/src/test/java/com/infinevo/hrms/performance/` |
| A failed list load shows a toast and an empty table | `PerformanceCyclesScreen.jsx:49-50`; `TeamReviewsScreen.jsx:39-40`; `MyReviews.jsx:40-41` |
