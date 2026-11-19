# Feature: Employee Goals & OKRs Tracking

| Field | Value |
|---|---|
| **Feature ID** | `W-106.2` · `HRMS-GOALS` |
| **Promoted to** | `docs/target-state/features/W-106-2-employee-goals-and-okrs.md` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration`, `code/backend/app` (one test), `code/frontend` (`src/hrms/performance`, `src/shell/portal`) |
| **Related gaps** | founder 2026-11-13 product roadmap ("Priority 2 — competing with Keka: `W-106` Performance reviews and goals") |
| **Status** | **Spec corrected 2026-11-19 after review. Code on `feature/krushna-specs` does not yet meet it.** |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | `W-106.1` (uses `hrms.performance_cycle` and the manager's rating drawer) |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `hrms` | 1 | Pass |
| Flyway migration | `V194` (exists) plus one follow-up for check-ins | 1 | **Over by one** — the follow-up exists only because `V194` is already pushed and cannot be edited |
| Externally testable behaviour | An employee sets goals and checks in progress; the manager and HR read them | 1 | Pass |
| Frontend area | `hrms/performance`, `shell/portal` | 2 | Pass |

---

## 1. Problem

| # | What is missing today |
|---|---|
| 1 | Employees have nowhere to set measurable goals or record progress between reviews. |
| 2 | Managers cannot see their team's goals before they rate. |
| 3 | A review (`W-106.1`) is written without the goals of the period in front of the manager. |

---

## 2. Scope

### In scope

| Area | What is delivered |
|---|---|
| Goals | An employee creates, edits, starts, checks in on and cancels **their own** goals. |
| Metric types | `PERCENTAGE` (0–100), `NUMERIC` (current against target), `MILESTONE` (done or not). |
| Category | `INDIVIDUAL`, `TEAM`, `COMPANY`. **A label only.** Every goal has exactly one owner, the employee who created it. |
| Check-ins | Each progress update is stored with its note, as history. |
| Cycle link | A goal may be linked to one review cycle. |
| Manager screen | `/hrms/team-goals`: direct reports' goals, read-only. |
| Review link | The manager's rating drawer (`W-106.1`) lists the employee's goals linked to that cycle, read-only. |
| Employee panel | "Goals & OKRs" tab on `/me`. |

### Out of scope

- Goals owned by a team or by the company, cascading goals, goal weights.
- A manager or HR creating, editing or approving someone's goal.
- Goals changing the review rating. They are shown, not scored.

### Rules

**Goal states**

| From | To | How | Rule |
|---|---|---|---|
| — | `DRAFT` | Create with `draft: true` | |
| — | `IN_PROGRESS` | Create (default) | Progress starts at 0. |
| `DRAFT` | `IN_PROGRESS` | `start` | Owner only. |
| `IN_PROGRESS` | `ACHIEVED` | A check-in that brings progress to 100% | **Automatic.** There is no other way to reach `ACHIEVED`. |
| `DRAFT`, `IN_PROGRESS` | `CANCELLED` | `cancel` | Owner only. |
| `ACHIEVED`, `CANCELLED` | — | — | **Final.** No edit, check-in, start or cancel. |

**Status is never read from a request.** `CreateGoalRequest` and the check-in request carry no `status` and no `progressPercentage` the server trusts; progress is worked out from the metric.

**Progress**

| Metric | Check-in sends | Progress |
|---|---|---|
| `PERCENTAGE` | `currentValue` 0–100 | the value |
| `NUMERIC` | `currentValue` ≥ 0 | `currentValue / targetValue`, capped at 100; `targetValue` must be above 0 |
| `MILESTONE` | `currentValue` 0 or 1 | 0 or 100 |

Check-ins are allowed only on `IN_PROGRESS`.

**Who may do what**

| Who | Read | Write |
|---|---|---|
| Owner (`hrms.goal.read_own`, `hrms.goal.update_own`) | Own goals and check-ins | Everything on own goals |
| Manager (`hrms.goal.review_team`) | Current direct reports' goals and check-ins | **Nothing** |
| HR (`hrms.goal.manage`) | Every goal in the tenant | **Nothing** |

**Cycle link**

| Rule | Detail |
|---|---|
| Linking | A goal may be linked, or its link changed, only to a cycle that is not `CLOSED`. |
| Lock | Once the linked cycle is `CLOSED` the goal is read-only: no edit, check-in, start or cancel (`409`). |
| On the review | Linked goals are listed in the manager's rating drawer for that cycle. They do not change the rating. |

---

## 3. Flow

```
[Employee] --> /me › Goals & OKRs --> POST /goals/me --> hrms.employee_goal
[Employee] --> Check-in --> POST /goals/me/{id}/check-ins --> hrms.employee_goal_check_in
                                                          --> progress worked out; 100% => ACHIEVED
[Manager]  --> /hrms/team-goals --> GET /goals/team (read-only)
[Manager]  --> /hrms/performance-reviews › rating drawer
           --> GET /goals/employee/{employeeId}?cycleId= (read-only)
```

---

## 4. Backend changes

All classes are in `code/backend/hrms/src/main/java/com/infinevo/hrms/`.

| Layer | File | Change |
|---|---|---|
| Controller | `performance/EmployeeGoalController.java` | Exists (`/api/v1/hrms/goals`). Replace `PATCH /me/{id}/progress` with the check-in endpoints; add edit, start, cancel. |
| Service | `performance/EmployeeGoalService.java` / `EmployeeGoalServiceImpl.java` | Exist. Writes are owner-only: remove the manager and HR branches from the write path. Enforce the state, progress and cycle rules of §2. |
| Entity | `performance/EmployeeGoal.java` | Exists. Constructor takes no status and no progress. State changes only through `start`, `checkIn`, `cancel`, `edit`, each refusing a final state. |
| Entity | `performance/EmployeeGoalCheckIn.java` | **New.** Maps `hrms.employee_goal_check_in`. |
| Repository | `performance/EmployeeGoalRepository.java` | Exists. Add employee-and-cycle lookup for the review drawer if missing. |
| Repository | `performance/EmployeeGoalCheckInRepository.java` | **New.** |
| DTO | `performance/CreateGoalRequest.java` | Remove `status`, `progressPercentage`, `currentValue`. Add `draft`. |
| DTO | `performance/UpdateGoalRequest.java`, `GoalCheckInRequest.java`, `GoalCheckInResponse.java` | **New.** `UpdateGoalProgressRequest.java` is deleted. |
| DTO | `performance/EmployeeGoalResponse.java` | Exists. Add `readOnly` (final state or closed cycle). |
| Enumeration | `performance/GoalStatus.java`, `GoalCategory.java`, `MetricType.java` | Exist. No change. |
| Portal | `portal/GoalsPanelProvider.java` | Exists: code `goals`, order 11, endpoint `/api/v1/hrms/goals/me`, action `hrms.goal.read_own`. No change. |
| Navigation | `navigation/HrmsNavigation.java` | Exists: `TEAM_GOALS` (`/hrms/team-goals`, `hrms.goal.review_team`). No change. |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/hrms/goals/me` | — | `List<EmployeeGoalResponse>` | `hrms.goal.read_own` |
| `POST` | `/api/v1/hrms/goals/me` | `CreateGoalRequest` (title, description, category, metricType, targetValue, metricUnit, startDate, dueDate, cycleId, draft) | `201` `EmployeeGoalResponse` | `hrms.goal.update_own` |
| `PATCH` | `/api/v1/hrms/goals/me/{id}` **new** | `UpdateGoalRequest` (title, description, category, targetValue, metricUnit, startDate, dueDate, cycleId) | `EmployeeGoalResponse` | `hrms.goal.update_own`, owner only |
| `POST` | `/api/v1/hrms/goals/me/{id}/start` **new** | — | `EmployeeGoalResponse` | `hrms.goal.update_own`, owner only |
| `POST` | `/api/v1/hrms/goals/me/{id}/cancel` **new** | — | `EmployeeGoalResponse` | `hrms.goal.update_own`, owner only |
| `POST` | `/api/v1/hrms/goals/me/{id}/check-ins` **new, replaces `PATCH …/progress`** | `GoalCheckInRequest` (currentValue, notes ≤ 2000) | `201` `EmployeeGoalResponse` | `hrms.goal.update_own`, owner only |
| `GET` | `/api/v1/hrms/goals/{id}/check-ins` **new** | — | `List<GoalCheckInResponse>`, newest first | owner, the owner's current manager, or `hrms.goal.manage` |
| `GET` | `/api/v1/hrms/goals/team` | — | `List<EmployeeGoalResponse>` | `hrms.goal.review_team` |
| `GET` | `/api/v1/hrms/goals/employee/{employeeId}` | optional `?cycleId=` | `List<EmployeeGoalResponse>` | `hrms.goal.review_team`; the employee's current manager or `hrms.goal.manage` |
| `GET` | `/api/v1/hrms/goals/cycle/{cycleId}` | — | `List<EmployeeGoalResponse>` — own goals; all goals for `hrms.goal.manage` | `hrms.goal.read_own` |

Errors, as the controller already maps them: bad input `400`, not the owner `403`, final state or closed cycle `409`.

---

## 5. Frontend changes

| File | Change |
|---|---|
| `src/hrms/performance/goalService.js` | Exists. Match the contract above. |
| `src/shell/portal/panels/MyGoals.jsx` | Exists. Add Goal sends no status. The check-in modal sends value and notes only and shows the check-in history. Add Edit, Start and Cancel. No actions on a read-only goal. |
| `src/hrms/performance/TeamGoalsScreen.jsx` | Exists. Read-only. The drawer shows the check-in history. |
| `src/hrms/performance/TeamReviewsScreen.jsx` | The rating drawer lists the employee's goals linked to that cycle, read-only. |
| `src/hrms/index.js` | Exists: route and `goals` panel. No change. |
| `src/shell/navigation/navLabels.js` | Exists: `nav.hrms.team_goals`. No change. |

**Every list screen:** when loading fails it shows an error state with a Retry button, not an empty table.

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/hrms/team-goals` | `TeamGoalsScreen` | HRMS feed item `hrms.team_goals` (`hrms.goal.review_team`) |
| `/me/goals` | `MyGoals` | Portal panel `goals` (`hrms.goal.read_own`) |

---

## 6. Database changes

> Flyway only. Never `ddl-auto`.

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `hrms/V194__employee_goals_and_okrs.sql` (exists — not edited) | `hrms.employee_goal`; four `hrms.goal.*` actions; role grants and the new-tenant trigger | yes, `tenant_id` + RLS | forward-only |
| `hrms/V<next>__employee_goal_check_in.sql` — **new, next free Flyway version at build time** | `hrms.employee_goal_check_in` | yes, `tenant_id` + RLS | forward-only |

`hrms.employee_goal_check_in`

| Column | Type | Note |
|---|---|---|
| `id` | `UUID` PK | |
| `tenant_id` | `UUID NOT NULL` → `core.tenant(tenant_id)` | |
| `goal_id` | `UUID NOT NULL` → `hrms.employee_goal(id) ON DELETE CASCADE` | |
| `current_value` | `NUMERIC(12,2) NOT NULL` | Value after this check-in |
| `progress_percentage` | `INTEGER NOT NULL`, 0–100 | Progress after this check-in |
| `notes` | `VARCHAR(2000)` | |
| `created_at`, `created_by` | as on `hrms.employee_goal` | |

Index `(tenant_id, goal_id, created_at DESC)`. `ENABLE ROW LEVEL SECURITY`, the fail-safe `tenant_isolation` policy copied from `V194`, and `GRANT SELECT, INSERT ON hrms.employee_goal_check_in TO app_user`.

- [ ] `tenant_id` and an RLS policy on the new table.
- [ ] `tenant_id` leads the index.
- [x] Money rule: not applicable — metric values are `NUMERIC(12,2)` quantities held as `BigDecimal`, not money.
- [x] No destructive step.

---

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/src/test/.../performance/EmployeeGoalServiceImplTest.java` (exists, extend) | Every row of the state table; **`CANCELLED` and `ACHIEVED` refuse every change**; **a goal at 0% cannot become `ACHIEVED`**; progress per metric type; `NUMERIC` with target 0 refused; a check-in stores its note; link to a `CLOSED` cycle refused; a goal on a `CLOSED` cycle is read-only. |
| Unit | `hrms/src/test/.../performance/GoalSecurityGuardTest.java` (exists, extend) | **The manager and HR cannot write to a report's goal**; a peer cannot read or write; the manager reads only current direct reports; HR reads all. |
| Unit | `hrms/src/test/.../portal/GoalsPanelProviderTest.java` (exists) | Panel code, order, endpoint, action. |
| Integration | `hrms/src/test/.../performance/GoalRlsIT.java` (**new — required**) | Tenant A cannot read or write tenant B's goals and check-ins as `app_user`. |
| Integration | `app/src/test/.../portal/PortalPanelDiscoveryIT.java` (exists, update) | Count and code list include `goals` (11). With `reviews` from `W-106.1` the count is 12. |
| Component | `src/shell/portal/panels/MyGoals.test.jsx` (exists, extend) | Create, check-in with note, history, cancel; no actions on a read-only goal; load-failure error state. |
| Component | `src/hrms/performance/TeamGoalsScreen.test.jsx` (exists, extend) | Read-only list and drawer; load-failure error state. |
| Component | `src/hrms/performance/TeamReviewsScreen.test.jsx` (extend) | Linked goals show in the rating drawer. |

---

## 8. Verification

```bash
cd code/backend
./mvnw -pl hrms -am verify      # unit tests and GoalRlsIT
./mvnw -pl app -am verify       # PortalPanelDiscoveryIT

cd ../frontend
npm test -- src/hrms/performance/ src/shell/portal/panels/MyGoals.test.jsx
npm run lint
```

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Employee creates a goal | Status `IN_PROGRESS`, progress 0 | not run |
| 2 | Create with `"status":"ACHIEVED"` in the body | Field ignored or `400`; never stored as `ACHIEVED` | not run |
| 3 | Check-in to 60 with a note | Progress 60; a check-in row holds the note | not run |
| 4 | Check-in to 100 | Status `ACHIEVED` | not run |
| 5 | Any change on an `ACHIEVED` or `CANCELLED` goal | `409` | not run |
| 6 | Manager calls a write endpoint on a report's goal | `403` | not run |
| 7 | HR calls a write endpoint on someone's goal | `403` | not run |
| 8 | Manager opens `/hrms/team-goals` | Direct reports' goals, no edit actions | not run |
| 9 | Peer reads another employee's goals | `403` | not run |
| 10 | Create a goal linked to a `CLOSED` cycle | `409` | not run |
| 11 | Close a cycle, then check in on a linked goal | `409` | not run |
| 12 | Manager opens the rating drawer for that cycle | The employee's linked goals are listed | not run |
| 13 | `GoalRlsIT` | Passes | not run |
| 14 | `PortalPanelDiscoveryIT` | Passes with `goals` in the list | not run |
| 15 | Stop the API and open My Goals | Error state with Retry, not an empty table | not run |

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| An employee marks a goal done without doing it | Medium | Goals do not change the rating; the manager reads the check-in history. |
| A goal is cancelled by mistake | Low | `CANCELLED` is final; the employee creates the goal again. |
| The path prefix differs from `W-106.1` (`/api/v1/hrms/goals` against `/api/v1/performance`) | Low | Kept as built; both are in the HRMS navigation feed. |
| Existing rows from the first build hold free-set statuses | Low | Feature is unreleased; no data fix is planned. |

---

## 10. Rollback

| Step | Action |
|---|---|
| 1 | Hide the feature: delete the `hrms.goal.*` rows from `core.role_action` for the tenant. The menu item and the portal tab disappear with the grants. |
| 2 | Data stays. Migrations are forward-only. No other module reads these tables. |

---

## 11. Code gaps against this spec (2026-11-19)

| Gap | Where |
|---|---|
| **CI is red.** The new `goals` provider makes 11; the test expects 10. Two-line fix: `hasSize(11)` and add `"goals"` to the list | `code/backend/app/src/test/java/com/infinevo/app/portal/PortalPanelDiscoveryIT.java:97,100-111` |
| Status is taken from the request: any state can be set at any time | `EmployeeGoal.java:129,153-157`; `CreateGoalRequest.java:27`; `UpdateGoalProgressRequest.java:9` |
| Progress percentage is taken from the request | `EmployeeGoal.java:165-169`; `MyGoals.jsx:448` |
| Check-in notes are accepted and dropped; there is no table for them | `UpdateGoalProgressRequest.java:9`; `EmployeeGoalServiceImpl.java:110-114`; `V194__employee_goals_and_okrs.sql:4-29` |
| A manager or HR can change a report's goal | `EmployeeGoalServiceImpl.java:102-108` |
| A goal can be linked to, and updated against, a `CLOSED` cycle | `EmployeeGoalServiceImpl.java:62-68,94-117` |
| No edit, start or cancel endpoint | `EmployeeGoalController.java:249-285` |
| Linked goals are not shown on the review | `code/frontend/src/hrms/performance/TeamReviewsScreen.jsx` |
| No RLS integration test | `code/backend/hrms/src/test/java/com/infinevo/hrms/performance/` |
| A failed list load shows a toast and an empty table | `TeamGoalsScreen.jsx:48-49`; `MyGoals.jsx:54-55` |
