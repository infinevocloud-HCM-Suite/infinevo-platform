# Feature: Regularization and overtime request screens

| Field | Value |
|---|---|
| **Feature ID** | `W-48.5` · screens for `W-40.4`, `W-40.6` (ticket #68) · `HRMS-*` |
| **Promoted to** | `docs/target-state/features/W-48-5-request-screens.md` — **hyphens**, never `W-48.5` |
| **Owner** | *(unassigned)* |
| **Apps touched** | `code/frontend/src/hrms/requests` (new); `code/backend/hrms` — two `GET /{id}`, one added field, two menu items; one line in `src/core/approvals/itemRoutes.js` |
| **Related gaps** | DEBT-007, DEBT-008 (fixed by `W-40.4`, `W-40.6`); the camelCase-versus-snake_case split noted in `W-48.4` §9 |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-04 |
| **Blocked by** | nothing — `W-40.4`, `W-40.6` on `main` `6e19dd89`; `W-48.4` on `dev-claude` `bf3ac296` (shares `src/hrms/index.js`, merge it first) |
| **Size** | M |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` — two read endpoints, one field, two menu items | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an employee asks for a day to be regularized or overtime to be paid, sees its state, and an approver opens it from the inbox | 1 |
| Frontend area | `src/hrms/requests` | 1 |

---

## 1. Problem

`W-40.4` and `W-40.6` give two request APIs and no screen. Three things stop a screen being built on them as they are:

- **Nothing can be opened by id.** Both controllers list and submit only (`hrms/.../attendance/RegularizationController.java:42-64`, `hrms/.../overtime/OvertimeRequestController.java:44-56`). The approvals inbox has the subject id (`src/core/approvals/Inbox.jsx:210-221`) but `itemRoutes.js:7-8` maps `REGULARIZATION` and `OVERTIME` to `null`, so an approver decides on `regularization #uuid` without seeing the times or hours asked for.
- **HR's regularization list has ids only.** `RegularizationResponse.java:10-22` carries `employeeId`, no name — the gap `W-48.4` closed for the session log.
- **No menu items.** `HrmsNavigation.java` has seven items, none for requests.

Legacy had no regularization (`W-40-4-attendance-regularization.md` §1) and an overtime form per role (`legacy/HRMS_Frontend/src/components/OvertimeRequest.jsx`, `OverTimeForm.jsx`); nothing is ported in shape.

## 2. Scope

**In scope**

- `hrms`: `GET /{id}` for a regularization and for an overtime request; `employeeName` on `RegularizationResponse`; menu items `hrms.regularizations`, `hrms.overtime_requests`
- `/hrms/regularizations` — my requests, newest first, and a form to submit one
- `/hrms/regularizations/:id` — one request, read-only, opened from the list or the inbox
- `/hrms/regularizations/all` — HR: everyone's requests for a range, filter by status and employee
- `/hrms/overtime-requests`, `/hrms/overtime-requests/:id` — the same two for overtime, employee side only
- `itemRoutes.REGULARIZATION` and `itemRoutes.OVERTIME` → the detail pages

**Out of scope**

- Approve and reject — the inbox decides (`W-46.4`); the detail page links back to it
- An HR list of overtime requests. `GET /api/v1/overtime` needs `core.overtime.read`, which no seeded role holds (`V025__catalogue_correction.sql:62-63` defines it; no `V1xx` seed grants it). Granting it is a `core` seed change — its own ticket
- Cancelling a request — no endpoint
- The dashboard counts — `W-48.6` when `W-44` grows a block

## 3. Flow

```
[employee] /hrms/regularizations
   list   --> GET  /v1/hrms/attendance/regularizations/mine?from=&to=     last 93 days by default (the server's cap, RegularizationServiceImpl.java:273)
   submit --> POST /v1/hrms/attendance/regularizations {date, inAt, outAt, reason}   409 and 400 show the server message
[employee] /hrms/overtime-requests
   list   --> GET  /v1/hrms/overtime-requests/mine?from=&to=
   submit --> POST /v1/hrms/overtime-requests {overtime_date, hours, remarks}
[hr] /hrms/regularizations/all
   --> GET /v1/hrms/attendance/regularizations?from=&to=&status=&employeeId=
[anyone allowed] /hrms/regularizations/:id · /hrms/overtime-requests/:id
   --> GET /v1/hrms/attendance/regularizations/{id} · GET /v1/hrms/overtime-requests/{id}
[approver] /approvals → REGULARIZATION or OVERTIME row "Open" --> the detail page above
```

Replies are **bare** (no envelope), as `W-48.4` §5: the service returns `res.data`. Regularization replies are camelCase (`RegularizationResponse.java`); overtime replies are snake_case (`core/.../overtime/OvertimeResponse.java:11-22`, `@JsonProperty`). The service maps both to one camelCase shape so the screens see one.

The list's default range is **not** derived from the browser: the screen requests nothing until a range is picked, offering "Last 93 days" and "This month" as presets — the rule `W-48.4` settled at its independent read (`AttendanceLog.jsx`).

## 4. Backend changes

All in `code/backend/hrms`. Fields and endpoints added, none changed.

| Layer | File | Change |
|---|---|---|
| Controller | `attendance/RegularizationController.java` | add `GET /{id}` — `@RequiresAction(value = "core.attendance.read_own", anyOf = {"core.attendance.read", "core.approval.decide"})` |
| Service | `RegularizationService`, `RegularizationServiceImpl` | `get(id)`: the row in the bound tenant; **owner** sees it with `read_own`; `core.attendance.read` sees any; `core.approval.decide` sees it only if the caller is a step approver on its `approvalInstanceId` (ask `core`'s approval service — the inbox already answers who may decide); otherwise `404`, never `403`, so ids cannot be probed |
| DTO | `attendance/RegularizationResponse.java` | add `employeeName` (camelCase, as the record); `all` fills it with one `EmployeeService.displayNames` batch read; `mine`, `submit`, `get` leave it null |
| Controller | `overtime/OvertimeRequestController.java` | add `GET /{id}` — `@RequiresAction(value = "hrms.overtime.request", anyOf = {"core.overtime.read", "core.approval.decide"})`, same visibility rule as above |
| Service | `OvertimeRequestWorkflowService`, `…Impl` | `get(id)` through `core`'s `OvertimeService` (hrms holds no overtime entity — `W-40-5` §4) |
| Navigation | `navigation/HrmsNavigation.java` | `hrms.regularizations` → `/hrms/regularizations`, `GET /api/v1/hrms/attendance/regularizations/mine`, `core.attendance.read_own`; `hrms.overtime_requests` → `/hrms/overtime-requests`, `GET /api/v1/hrms/overtime-requests/mine`, `hrms.overtime.request` |

Who holds what (`migration/.../core/V139__hrms_request_seed_roles.sql`): `employee` — `hrms.attendance.mark`, `core.attendance.read_own` (`:123-124`), `hrms.overtime.request` (`:137`); `hr` — `core.attendance.read` (`:54`), `core.approval.decide` (`:65`); `manager` — `core.approval.decide` (`:78`).

**API contract — new or changed**

| Method | Path | Response | Auth |
|---|---|---|---|
| GET | `/api/v1/hrms/attendance/regularizations/{id}` | `200` `RegularizationResponse` · `404` | owner, `core.attendance.read`, or an approver of it |
| GET | `/api/v1/hrms/overtime-requests/{id}` | `200` `OvertimeResponse` · `404` | owner, `core.overtime.read`, or an approver of it |
| GET | `/api/v1/hrms/attendance/regularizations` | as today plus `employeeName` | unchanged |

## 5. Frontend changes

`W-45` contract, as `src/hrms/attendance`: `apiClient` from `@shared/api/client`, `useCan` and `NotEntitled` from `@shell/screens`, Ant Design 5, Formik + Yup for the two forms.

| File | Change |
|---|---|
| `src/hrms/requests/requestService.js` | **new** — `myRegularizations(from,to)`, `allRegularizations(from,to,status,employeeId)`, `regularization(id)`, `submitRegularization(body)`, `myOvertime(from,to)`, `overtime(id)`, `submitOvertime(body)`; maps the overtime snake_case reply to camelCase |
| `src/hrms/requests/RegularizationForm.jsx` | **new** — date (not after today), in and out as times on that date (out after in), reason required ≤ 500; sent as `inAt`/`outAt` ISO offset date-times |
| `src/hrms/requests/MyRegularizations.jsx` | **new** — range picker with presets, no request until picked; `Table` (date, in, out, reason, status `Tag`, decided at); "New request" `Drawer` with the form, shown with `useCan('hrms.attendance.mark')`; row opens the detail |
| `src/hrms/requests/RegularizationDetail.jsx` | **new** — one request: date, in, out, reason, status, decision comment, decided by; "Back to approvals" link when opened from `/approvals` (`location.state.from`), else back to the list; `404` shows `NotFound` from `@shell/screens` |
| `src/hrms/requests/RegularizationLog.jsx` | **new** — HR: range with presets, status `Select` (`PENDING`, `APPROVED`, `REJECTED`), employee `Select` from the names in the rows; `Table` with `employeeName` |
| `src/hrms/requests/OvertimeForm.jsx` | **new** — date (not after today), hours `InputNumber` 0.25–24 step 0.25 two decimals, remarks ≤ 500 |
| `src/hrms/requests/MyOvertime.jsx`, `OvertimeDetail.jsx` | **new** — as the regularization pair; the detail shows `hours`, `amount` (as sent, `BigDecimal` text), `status`, `source`, `postedPeriod` when present |
| `src/hrms/index.js` | `routes` gains the five paths |
| `src/core/approvals/itemRoutes.js` | `REGULARIZATION: (id) => (id ? \`/hrms/regularizations/${id}\` : null)`, `OVERTIME: (id) => (id ? \`/hrms/overtime-requests/${id}\` : null)` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/hrms/regularizations` | `MyRegularizations` | `AppShell`; feed carries `hrms.regularizations` |
| `/hrms/regularizations/all` | `RegularizationLog` | `useCan('core.attendance.read')`, else `NotEntitled` — no menu item, linked from the list for HR |
| `/hrms/regularizations/:id` | `RegularizationDetail` | beneath the list |
| `/hrms/overtime-requests` | `MyOvertime` | feed carries `hrms.overtime_requests` |
| `/hrms/overtime-requests/:id` | `OvertimeDetail` | beneath the list |

## 6. Database changes

None. No table, no column, no Flyway script.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `hrms/.../attendance/RegularizationReadIT.java` | owner reads own; HR reads any; a step approver reads it; a colleague with none gets `404`; another tenant's id is `404`; `all` carries `employeeName`, `mine` does not |
| Integration | `hrms/.../overtime/OvertimeRequestReadIT.java` | the same four for overtime |
| Unit | `HrmsNavigationTest.java` (extend) | nine items; each `targetEndpoint` has a `GET` |
| Unit | `requestService.test.js` | every function hits its path, returns the body bare, overtime keys mapped to camelCase |
| Component | `MyRegularizations.test.jsx` | nothing requested until a range is picked; form blocks out-before-in and a future date; `409` shows the server message |
| Component | `RegularizationLog.test.jsx` | status and employee filters sent; `NotEntitled` without `core.attendance.read` |
| Component | `OvertimeDetail.test.jsx` | renders hours, amount and status; `404` shows `NotFound`; back link |
| Component | `itemRoutes.test.js` (extend) | both builders; null id gives null |

## 8. Verification

```bash
cd code/backend && ./mvnw -q -pl hrms -am spotless:check verify -Dtest='HrmsNavigationTest' -Dit.test='RegularizationReadIT,OvertimeRequestReadIT'
cd code/frontend && npm run lint && npx vitest run src/hrms/requests src/core/approvals
```

| Check | Expected |
|---|---|
| backend, lint, tests | `BUILD SUCCESS`; clean |
| employee login | menu shows Regularizations and Overtime requests; submits one of each; sees PENDING |
| manager login | inbox row "Open" lands on the detail page with the times or hours; a colleague's id typed in the URL is `404` |
| HR login | `/hrms/regularizations/all` lists everyone with names |
| `check-done.mjs W-48.5` | no new table, no `double`, no `ddl-auto` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| "Approver may read" needs a question `core`'s approval service does not answer today | medium | read `core/.../approval/ApprovalService` first; if there is no "is caller an approver of instance X" method, add it in `core` as the only change there — say so in the commit |
| Two reply casings in one screen | certain | one mapping in `requestService.js`, tested |
| `src/hrms/index.js` conflicts with `W-48.4` | high | merge `W-48.4` first (it is Ready to merge on `dev-claude`) |

## 10. Rollback

Revert the branch. Endpoints and the field are additive; no data changes.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table; every read is bound to the tenant and `404`s across it |
| Flyway | none |
| `Money` / `BigDecimal` | `hours` and `amount` shown as sent; nothing summed |
| Index | none new |
| Expand / contract | additive only |
| No module references another | `hrms` reads `core` only (`EmployeeService`, `OvertimeService`, the approval service); `src/hrms` imports `@shared/*` and `@shell/screens` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-007, DEBT-008 | already fixed by `W-40.4`, `W-40.6` |
| camelCase / snake_case split (`W-48.4` §9) | **discounted** — mapped in the service; unifying it is its own ticket |

## 13. Decisions — founder, 2026-10-04

| # | Question | Answer |
|---|---|---|
| 1 | How does an approver see what they decide on? | **A `GET /{id}` an approver may read**, `404` otherwise. The inbox already carries the subject id |
| 2 | HR overtime list? | **Not here** — no seeded role holds `core.overtime.read`; granting it is a `core` seed ticket |
| 3 | Names? | **`employeeName` on HR's regularization list only**, one batch read, as `W-48.4` |
| 4 | Default range? | **None** — HR picks, with presets; the browser never supplies "today" for a request |
