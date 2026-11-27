# Feature: AI helper applies WFH and withdraws or cancels own leave or WFH, after the user confirms

| Field | Value |
|---|---|
| **Feature ID** | `W-114.17` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai/action`, one extraction in `wfh`), `code/frontend/src/shell/assistant` (five lines in `actionLinks.js`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.8` (propose and confirm, `AiActionExecutor`) and `W-114.9` (the card) on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | The helper proposes a WFH request, or a withdraw or cancel of the caller's own leave or WFH request; nothing changes until the user confirms | 1 |
| Frontend area | `shell` — five link lines, no component | 1 |

Size **M**. Five executors and five propose tools on the pieces `W-114.8` built. No new
endpoint and no new card. It grew from three actions to five on 2026-11-23 (founder); every
action is the same small shape, so it stays one part.

## Shipped first: the WFH screen's ownership check (`W-114.17a`)

Found while writing this spec, fixed ahead of it as part of this feature (founder, 2026-11-23).

| Before | After |
|---|---|
| `POST /api/v1/wfh-requests/{id}/withdraw` and `/cancel` need only `core.wfh.apply` and never compare the request's owner with the caller (`WfhRequestServiceImpl.java:248-301`, `WfhRequestController.java:73-88`), so any employee could withdraw or cancel another employee's WFH request in the same customer by id | Unless the caller holds `core.wfh.manage`, the request must be the caller's own; otherwise `403` |
| `GET /api/v1/wfh-requests/{id}` admits `core.wfh.read_own` and returns any request | With only `core.wfh.read_own`, the request must be the caller's own; otherwise `403` |

Same pattern as leave (`LeaveRequestController.java:175-195`). The list endpoint is unchanged.
The executors below still make their own ownership check, because they call the service, not
the controller.

## The rule

The model can only propose. Confirm is `POST /api/v1/ai/actions/{intentId}/confirm` (`W-114.8`),
called by the signed-in user. Every re-check in `W-114.8`'s confirm table applies unchanged.

## Decisions

| Point | Choice | Why |
|---|---|---|
| Whose requests | The caller's own only | The on-behalf paths need `core.leave.manage` / `core.wfh.manage` (`LeaveRequestController.java:68-70`, `WfhRequestController.java:45-47`); not offered |
| WFH withdraw and cancel — **founder, 2026-11-23** | In this part: two more actions, each behind Confirm | Both exist in the product today: `PENDING → WITHDRAWN` (`wfh/WfhRequestServiceImpl.java:249-259`) and `APPROVED → CANCELLED`, only before the start date (`:285-301`); both guarded by `core.wfh.apply` (`wfh/WfhRequestController.java:74-75`, `:83-84`) |
| How the model names a WFH request | By its **start date**, as for leave | One employee's active WFH requests cannot overlap (`WfhRequestServiceImpl.java:118-122`), so the start date picks at most one |
| How the model names a leave request | By its **start date**. The server finds the caller's own request in the right status that starts on that date | Two active requests of one employee cannot overlap (`LeaveRequestServiceImpl.java:618-621`), so the start date picks at most one. The model never sees a database id |
| Ownership at confirm | The executor checks the request's `employeeId` is the caller's before calling the service, for leave and for WFH | The leave service does not check it; its controller does (`LeaveRequestController.java:181-188`, `:202-209`), and the executor does not go through the controller. For WFH neither the service nor the controller compares the request's owner with the caller: `withdraw` and `cancel` receive `callerEmployeeId` and never use it for that (`WfhRequestServiceImpl.java:249-283`, `:285-330`; `WfhRequestController.java:74-90`). The executor's own check is therefore the only one on this path. The same gap on the WFH screen's own endpoints is outside this ticket and is reported to the founder |
| WFH validation | The checks inside `WfhRequestServiceImpl.submitRequest` move into `validateApply`, which `submitRequest` still calls first | They run before the first save today (`WfhRequestServiceImpl.java:75-152`, save at `:167`) and cannot be called alone. Same extraction `W-114.8` makes for leave |
| Reason on withdraw and cancel | Optional, 500 characters | The leave service accepts none (`LeaveRequestService.java:20,22`); the WFH endpoints take it as optional too (`WfhRequestController.java:79`, `:88`) |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | The helper's only action is applying leave. | `W-114.8` registers one executor |
| 2 | WFH checks (dates, half day, working days, overlap with WFH and with leave, quota, monthly maximum) run only while creating the request. | `code/backend/core/src/main/java/com/infinevo/core/wfh/WfhRequestServiceImpl.java:89-152` |
| 3 | A pending leave request is withdrawn, an approved one is cancelled; each is a different endpoint and a different rule. | `LeaveRequestController.java:175-176`, `:196-197`; `LeaveRequestStatus.java` (`PENDING → WITHDRAWN`, `APPROVED → CANCELLED`) |
| 4 | WFH follows the same two rules on its own endpoints; cancel is refused on or after the start date. | `wfh/WfhRequestController.java:74-90`; `wfh/WfhRequestServiceImpl.java:257-260`, `:293-301`; `wfh/WfhRequestStatus.java` |

## Scope

**In scope**

- Read tool `core.my_open_requests`.
- Propose tools `core.propose_wfh_request`, `core.propose_wfh_withdraw`, `core.propose_wfh_cancel`, `core.propose_leave_withdraw`, `core.propose_leave_cancel`.
- Executors `core.wfh.apply`, `core.wfh.withdraw`, `core.wfh.cancel`, `core.leave.withdraw`, `core.leave.cancel`.
- `validateApply` in the WFH service.

**Out of scope**

- Anything on behalf of another employee; draft requests.
- A new endpoint or card — both come from `W-114.8` and `W-114.9`.

## Flow

```
"cancel my leave on 12 December"
  --> model calls core.my_open_requests          --> the caller's PENDING and APPROVED leave and WFH, no ids
  --> model calls core.propose_leave_cancel { fromDate: 2026-12-12, reason }
        --> find the caller's own APPROVED request starting that date (none or several: error with the list)
        --> check the service's cancel rule (start date still ahead)
        --> AiActionService.propose("core.leave.cancel", payload { leaveRequestId }, summary, fields)
  --> answer carries proposedAction --> card (W-114.9)

Confirm --> W-114.8 re-checks --> LeaveCancelExecutor: request belongs to caller? --> LeaveRequestService.cancel(...)

"withdraw my WFH request for 8 December" takes the same path:
core.propose_wfh_withdraw --> card --> Confirm --> WfhWithdrawExecutor --> WfhRequestService.withdraw(...)
```

## Backend changes

Paths are under `code/backend/core/src/main/java/com/infinevo/core/`.

| Layer | File | Change |
|---|---|---|
| Service | `wfh/WfhRequestService.java`, `WfhRequestServiceImpl.java` | **New method** `validateApply(UUID tenantId, UUID employeeId, ApplyWfhRequest request)`: the checks of `submitRequest` (`:75-152`) moved unchanged; `submitRequest` calls it first. No behaviour change for the WFH screen |
| Tools | `ai/action/SelfServiceToolProvider.java` | **New.** An `AiToolProvider` with the six tools below |
| Executor | `ai/action/WfhApplyExecutor.java` | **New.** `actionCode` and `requiredAction` `core.wfh.apply`. Calls `WfhRequestService.apply(tenantId, callerEmployeeId, request)` — what `MyWfhController.java:44-50` calls |
| Executor | `ai/action/WfhWithdrawExecutor.java` | **New.** `actionCode` `core.wfh.withdraw`, `requiredAction` `core.wfh.apply`. Ownership check, then `WfhRequestService.withdraw(tenantId, requestId, callerEmployeeId, reason)` (`WfhRequestServiceImpl.java:249`) |
| Executor | `ai/action/WfhCancelExecutor.java` | **New.** `actionCode` `core.wfh.cancel`, `requiredAction` `core.wfh.apply`. Ownership check, then `WfhRequestService.cancel(...)` (`:285`) |
| Executor | `ai/action/LeaveWithdrawExecutor.java` | **New.** `actionCode` `core.leave.withdraw`, `requiredAction` `core.leave.apply`. Ownership check, then `LeaveRequestService.withdraw(tenantId, requestId, callerEmployeeId, reason)` (`LeaveRequestServiceImpl.java:340`) |
| Executor | `ai/action/LeaveCancelExecutor.java` | **New.** `actionCode` `core.leave.cancel`, `requiredAction` `core.leave.apply`. Ownership check, then `LeaveRequestService.cancel(...)` (`:371`) |

**The tools**

| Tool | Input | Does | `requiredAction` |
|---|---|---|---|
| `core.my_open_requests` | — | Two lists, each at most 25, no ids. **Leave:** the caller's requests in `PENDING` or `APPROVED` with a start date today or later, through `leaveRequestService.searchRequests(tenantId, caller.id(), status, null, null, …)` and filtered on the start date in code - the search's own date filter fails in PostgreSQL when a date is bound (`42P18`, found while building this; reported): leave type, from, to, days, status, "can withdraw" / "can cancel". **WFH:** the same for the caller's WFH requests, through `WfhRequestService.listEmployeeRequests(tenantId, caller.id())` (`wfh/WfhRequestService.java:14`): from, to, half day, status, "can withdraw" / "can cancel". A list is left out when the caller lacks its read action | `core.leave.read_own`, with `requiredAnyOf = [core.wfh.read_own]` (`W-114.3`; `MyWfhController.java:53-54`) |
| `core.propose_wfh_request` | `fromDate`, `toDate` (ISO), `halfDay`, `halfDayPeriod` (`first` / `second`, `WfhRequestServiceImpl.java:102`), `reason` (500) | `validateApply`, then propose `core.wfh.apply`. Card fields: From, To, Half day, Working days, Reason | `core.wfh.apply` |
| `core.propose_wfh_withdraw` | `fromDate`, `reason` | Finds the caller's own `PENDING` WFH request starting that date, then proposes `core.wfh.withdraw`. Card fields: From, To, Half day, Status, Reason | `core.wfh.apply` |
| `core.propose_wfh_cancel` | `fromDate`, `reason` | Finds the caller's own `APPROVED` WFH request starting that date; refuses when today is on or after the start date, with the service's wording (`WfhRequestServiceImpl.java:298-301`); proposes `core.wfh.cancel` | `core.wfh.apply` |
| `core.propose_leave_withdraw` | `fromDate`, `reason` | Finds the caller's own `PENDING` request starting that date, then proposes `core.leave.withdraw`. Card fields: Leave type, From, To, Days, Status, Reason | `core.leave.apply` |
| `core.propose_leave_cancel` | `fromDate`, `reason` | Finds the caller's own `APPROVED` request starting that date; refuses when today is on or after the start date, with the service's wording (`:384-386`); proposes `core.leave.cancel` | `core.leave.apply` |

A refusal goes back to the model as the tool's error text and no intent is written. None found, or
more than one, returns the list from `core.my_open_requests` so the helper can ask which.

**At confirm** the `W-114.8` checks run first (own intent, not expired, helper on, action still
held, employee profile). The service then runs its own rule again; a refusal — already withdrawn,
approved in the meantime, start date reached — sets `FAILED` with the service's message.

`result`: `{ "wfhRequestId", "status", "traceId" }` or `{ "leaveRequestId", "status", "traceId" }`.

## Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/shell/assistant/actionLinks.js` (`W-114.9`) | Add `core.wfh.apply`, `core.wfh.withdraw` and `core.wfh.cancel` → `/me/wfh` (a WFH request has no detail route; `/me/wfh` is the portal's WFH tab, `code/frontend/src/shell/routes.js:19`); `core.leave.withdraw` and `core.leave.cancel` → `/me/leave/requests/{leaveRequestId}` (`routes.js:21`) |

**Routes added:** none.

## Database changes

None.

- [x] No new table, no migration
- [x] No money value
- [x] Stays inside `core`: leave, WFH and the helper are all in `core`

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | the existing WFH service tests | Pass unchanged after the extraction; `validateApply` refuses what `apply` refused |
| Unit | `core/src/test/.../ai/action/SelfServiceToolProviderTest.java` | For leave and for WFH alike: no request on that date lists the open ones; a `PENDING` request cannot be proposed for cancel, an `APPROVED` one cannot be proposed for withdraw; cancel on the start date is refused with the service's text; no tool result carries an id; a refusal writes no intent. `core.my_open_requests` leaves out the WFH list for a caller without `core.wfh.read_own` |
| Integration | `core/src/test/.../ai/action/SelfServiceActionsIT.java` (requests that must already exist, pending or approved, are written as rows; WFH apply goes through the real policy and quota) | Confirmed WFH creates one `PENDING` request; confirmed withdraw moves `PENDING → WITHDRAWN` and cancels the approval instance; confirmed cancel moves `APPROVED → CANCELLED` and releases the consumed days; confirmed WFH withdraw moves `PENDING → WITHDRAWN` and cancels its approval instance; confirmed WFH cancel moves `APPROVED → CANCELLED`; a second confirm changes nothing; an intent whose payload names another employee's leave request or WFH request is refused at confirm and that request is unchanged |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core -am verify
cd code/frontend && npx vitest run src/shell/assistant && npm run lint
```

| Check | Expected | Result |
|---|---|---|
| The commands | Build success; existing leave and WFH tests pass unchanged | not run |
| "Work from home on 2026-12-08" | A card; no row in `core.wfh_request` until Confirm; then one `PENDING` request on the WFH screen | not run |
| Same dates as an existing leave | The helper explains the overlap; no card | not run |
| "Withdraw my leave of 15 December" (pending) | Card; Confirm sets `WITHDRAWN` | not run |
| "Cancel my leave of 20 December" (approved, in the future) | Card; Confirm sets `CANCELLED`; the balance goes back | not run |
| Cancel a leave that starts today | Refused with the service's message; no card | not run |
| "Withdraw my WFH request for 8 December" (pending) | Card; Confirm sets `WITHDRAWN`; the card links to `/me/wfh` | not run |
| "Cancel my WFH on 10 December" (approved, in the future) | Card; Confirm sets `CANCELLED` | not run |
| Cancel a WFH request that starts today | Refused with the service's message; no card | not run |
| An intent planted with another employee's WFH request id | Refused at confirm; that request is unchanged | not run |
| The request is approved between proposal and confirm of a withdraw | `409 AI_ACTION_FAILED` with the service's message | not run |
| Confirm twice | Same result; one change | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The extraction changes the WFH screen's behaviour | Low | Lines move unchanged; existing tests must pass without edits |
| The model picks the wrong request | Low | Start date picks at most one; the card shows type, dates and status before anything happens |
| Ownership is skipped because the executor bypasses the controller | Medium if forgotten | The executor's own check, and the integration test that plants another employee's request id in the payload |

## Rollback

Revert the commit. The six tools are no longer offered. Requests already created or changed stay.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` (`wfh`) | Extract `validateApply`; existing tests unchanged |
| 2 | `core` (`ai/action`) | The tool provider, five executors, tests |
| 3 | frontend `shell` | Five lines in `actionLinks.js` and their test |
