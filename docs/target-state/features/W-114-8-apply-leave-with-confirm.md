# Feature: AI helper applies leave after the user confirms

| Field | Value |
|---|---|
| **Feature ID** | `W-114.8` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai`, one extraction in `leave`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.3` (`core.ai_action_intent`, the tool plug-in point), `W-114.4` (the gateway that runs tools) on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none — the intent table is created by `W-114.3` | 1 |
| Externally testable behaviour | The helper proposes a leave request; nothing is written until the user confirms; a confirmed proposal creates exactly one leave request | 1 |
| Frontend area | none — the card is `W-114.9` | 1 |

Size **M**. This is the first action. Every later action (`W-114.17`, `.18`, `.20`) reuses the
propose and confirm pieces built here.

## The rule

**The model can only propose. Only a person can confirm.** Confirm is an endpoint the signed-in
user calls from the browser. It is not a tool, so no model output can reach it.

## The Confirm rules, in full (Added 2026-11-23, research review)

Founder 2026-11-23. The card is the only barrier between the model and a write, so each
property is a rule with a test. "Had" means the spec already said it; "New" means added now.

| # | Rule | Had or new | Where |
|---|---|---|---|
| C1 | The card is drawn from the stored intent, never from text the model wrote. `summary` and `fields` are built by the server from the stored `payload` each time the intent is read | **New** | `AiActionExecutor.describe`, below; `W-114.9` |
| C2 | The card shows every field of the payload that will be sent, in full. Free text (the reason) is not shortened | **New** | `describe`; `W-114.9` |
| C3 | The intent belongs to one tenant, one user and one chat session. It is read by id **and** tenant **and** user. Deleting the session deletes the intent | Had (tenant, user: check 1). **New**: session stated | `AiActionIntentRepository.java:22-25`; `V200__ai_chat.sql:35` (`ON DELETE CASCADE`) |
| C4 | Single use. The status changes under a row lock. A second Confirm runs nothing and returns the first result | Had | check 2; `AiActionConfirmIT` |
| C5 | It expires 15 minutes after the proposal. An expired intent becomes `EXPIRED` and never runs | Had | "Decisions"; check 4 |
| C6 | The caller's permission is checked again at Confirm | Had | check 6 |
| C7 | The customer's module licence is checked again at Confirm | **New** | check 6a |
| C8 | The server runs the **stored** payload. Confirm takes no body; anything sent is ignored. The real service validates it again | Had (check 8). **New**: "no body" stated | API contract |
| C9 | Cancel is stored. On the next turn the model is told "the user declined" as a fact, not as an error, so it does not try again | **New** | "What the model is told", below |
| C10 | One audit row each for propose, confirm, cancel, expire and fail | **New** | "Audit", below |
| C11 | The model can never confirm. No tool reaches the confirm, cancel or read endpoint | Had | "The rule" |

## Decisions

| Point | Choice | Why |
|---|---|---|
| Where `summary` and `fields` come from (this spec · Claude, 2026-11-23) | Not stored. Each executor builds them from the stored `payload` in `describe(...)`, on every read | `core.ai_action_intent` has `payload` and no summary column (`code/backend/migration/src/main/resources/db/migration/core/V200__ai_chat.sql:32-46`). Building them from the payload means the card cannot differ from what runs, and needs no migration |
| Audit rows (this spec · Claude, 2026-11-23) | Written with `AuditWriter.write(AuditChange)`, not with `@Audited` on the entity | `@Audited` would copy the whole `payload` into `core.audit_log`. An explicit row leaves `payload` and `result` out; the leave request's own audit row holds the values |
| Validating a proposal | The checks inside `LeaveRequestServiceImpl.createRequest` move into one method, `validateApply`, that `createRequest` still calls first. The tool calls the same method | The checks run before the first save today (`LeaveRequestServiceImpl.java:113-186`) and cannot be called alone. Copying them would let the two drift |
| What confirm calls | `LeaveRequestService.createRequest(tenantId, employeeId, request)` with `submit = true` | It is the method the Leave screen's endpoint calls (`LeaveRequestController.java:59-66`) |
| Audit link | The confirm request's own correlation id is stored on the intent, in `result.traceId` | See "Audit" below. The split's `ai-intent:<id>` marker does not fit |
| Expiry | 15 minutes from the proposal. An expired intent is marked `EXPIRED` the first time it is read | No job needed |
| Whose leave | The caller's own only. Applying for someone else (`core.leave.manage`) is not offered | Smallest safe first action |
| Supporting documents | Not offered. A leave type that needs a document is refused at proposal with "apply on the Leave screen" | The chat has no file upload |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | The helper can answer questions and cannot change anything. | `W-114.4` registers read-only tools only |
| 2 | Leave checks (dates, half day, active type, eligibility, policy) run only as part of creating the request. | `code/backend/core/src/main/java/com/infinevo/core/leave/LeaveRequestServiceImpl.java:113-186` |
| 3 | The audit row's trace id is the request's correlation id, cut to 36 characters. `ai-intent:` plus a UUID is 46. | `code/backend/shared/src/main/java/com/infinevo/shared/audit/AuditWriter.java:108,213`; `code/backend/migration/src/main/resources/db/migration/core/V008__audit_log.sql:17` |

## Scope

**In scope**

- The tool `core.propose_leave_request`.
- `POST /api/v1/ai/actions/{intentId}/confirm` and `/cancel`; `GET /api/v1/ai/actions/{intentId}`.
- The re-check at confirm time; one leave request per intent, however often confirm is called.
- `validateApply` in the leave service.

**Out of scope**

- The Confirm and Cancel card — `W-114.9`.
- WFH, withdraw and cancel leave — `W-114.17`. Approvals — `W-114.18`.
- Leave on behalf of another employee; leave that needs a document.

## Flow

```
User: "apply casual leave for Friday"
  --> POST /api/v1/ai/sessions/{id}/messages            (W-114.4)
  --> model calls tool core.propose_leave_request
        --> resolve leave type by name --> LeaveRequestService.validateApply(...)
        --> INSERT core.ai_action_intent  status PROPOSED, expires_at = now + 15 min
        --> tool result: { intentId, summary, fields }   (nothing in core.leave_request yet)
  --> answer carries proposedAction { intentId, actionCode, summary, fields, expiresAt }

User presses Confirm
  --> POST /api/v1/ai/actions/{intentId}/confirm
        --> lock the intent row --> re-check (table below)
        --> LeaveRequestService.createRequest(tenant, callerEmployee, payload, submit = true)
        --> intent CONFIRMED, result { leaveRequestId, status, traceId }
```

## Backend changes

Paths are under `code/backend/core/src/main/java/com/infinevo/core/`.

| Layer | File | Change |
|---|---|---|
| Service | `leave/LeaveRequestService.java`, `LeaveRequestServiceImpl.java` | **New method** `validateApply(UUID tenantId, UUID employeeId, LeaveApplyRequest request)`: the checks now at `:118-186` up to the first save, moved unchanged. `createRequest` calls it first. No behaviour change for the Leave screen |
| Tool | `ai/action/LeaveActionToolProvider.java` | **New.** An `AiToolProvider` with one `AiTool`, `core.propose_leave_request`; `requiredAction` = `core.leave.apply` |
| Service | `ai/action/AiActionService.java`, `AiActionServiceImpl.java` | **New.** `propose(actionCode, payload, summary, fields)`, `confirm(intentId)`, `cancel(intentId)`, `get(intentId)`. Generic: it knows nothing about leave. **(Added 2026-11-23)** `propose` takes the action code and the payload only — the `summary` and `fields` arguments are dropped. `propose`, `get` and the answer's `proposedAction` all return `executor.describe(ctx, storedPayload)`. `propose`, `confirm`, `cancel`, the expiry in `get` or `confirm`, and a failure each write one audit row ("Audit") |
| Executor | `ai/action/AiActionExecutor.java` | **New interface.** `actionCode()`, `requiredAction()`, `default boolean requiresEmployeeProfile() { return true; }`, `execute(AiToolContext, JsonNode payload)` returning a result node. One bean per action; `AiActionService` picks by `actionCode`. **(Added 2026-11-23)** Two more methods: `describe(AiToolContext, JsonNode payload)` returns `summary` and `fields[{label, value}]`, read from the payload and the database only — every payload value the action will send appears as a field, free text in full; and `default PlatformModule requiredModule() { return null; }` — the module the customer must hold, or null for a `core` action |
| Executor | `ai/action/LeaveApplyExecutor.java` | **New.** `actionCode` `core.leave.apply`. Builds a `LeaveApplyRequest` from the payload and calls `createRequest` |
| Controller | `ai/action/AiActionController.java` | **New.** The three endpoints. `@RequiresAction("core.ai.use")`. Own handlers: not found or not the caller's → `404`; expired → `410 AI_ACTION_EXPIRED`; cancelled → `409 AI_ACTION_CANCELLED`; permission gone → `403 FORBIDDEN`; helper off → `403 AI_DISABLED` |
| Repository | `ai/AiActionIntentRepository.java` (from `W-114.3`) | No change: `W-114.3` already declares `findByIdAndTenantIdAndUserIdForUpdate` — the row lock (`PESSIMISTIC_WRITE`) confirm takes — and `findByMessageIdIn` |
| Service | `ai/AiChatServiceImpl.java` (from `W-114.4`) | When a tool round wrote an intent, the answer's `proposedAction` is filled from it, and after the `ASSISTANT` row is saved the intent's `message_id` is set to that row (`core.ai_action_intent.message_id`, `W-114.3`). That is the link `GET …/sessions/{id}/messages` reads back as `proposedAction: { intentId }`. **(Added 2026-11-23)** When it builds the history for a model call, it adds the status note of "What the model is told" to each `ASSISTANT` message whose intent is no longer `PROPOSED` |

**The tool**

| Input | Rule |
|---|---|
| `leaveType` (text) | Matched to an active leave type of the tenant by name, ignoring case. No match, or more than one, returns an error to the model listing the valid names |
| `fromDate`, `toDate` | ISO dates (`2026-12-04`). The system prompt in `W-114.4` gives the model today's date; the tool does no "next Friday" arithmetic |
| `halfDay` (boolean), `halfDayPeriod` | `first` or `second`, the values `HalfDayPeriod` stores (`leave/HalfDayPeriod.java:13-14`) |
| `reason` | Free text, 500 characters at most |

The tool calls `validateApply`. A refusal (`IllegalArgumentException`) goes back to the model as
the tool's error text, so the helper tells the user why; no intent is written. On success it
stores the payload exactly as it will be sent (`leaveTypeId`, dates, half-day values, reason,
`submit: true`) and returns the fields the card shows: Leave type, From, To, Half day, Days, Reason.

**Confirm re-checks, in this order, inside one transaction holding the row lock**

| # | Check | Fails with |
|---|---|---|
| 1 | The intent exists in the caller's tenant and `user_id` is the caller | `404` |
| 2 | Status is `CONFIRMED` | Return the stored result, `200`. Nothing is executed again |
| 3 | Status is `CANCELLED`, `EXPIRED` or `FAILED` | `409` or `410` as above |
| 4 | `expires_at` is past | Set `EXPIRED`, `410 AI_ACTION_EXPIRED` |
| 5 | `AiAccessGate.status().enabled()` is true (`W-114.1`) | `403 AI_DISABLED` |
| 6 | `PermissionService.holds(executor.requiredAction())` — here `core.leave.apply` | `403 FORBIDDEN` |
| 6a | **(Added 2026-11-23)** When `executor.requiredModule()` is not null: `EntitlementService.holds(module)` (`code/backend/shared/src/main/java/com/infinevo/shared/entitlement/EntitlementService.java:46`). Null for leave: leave is in `core`, and `core` is not a module (`shared/entitlement/PlatformModule.java:6`). The check exists for later `hrms` and `payroll` actions, whose module check sits on the controller and so is not reached by an executor | `403 FORBIDDEN` |
| 7 | When `executor.requiresEmployeeProfile()` is true: the caller has an employee profile (`EmployeeService.currentEmployee()`, as `LeaveRequestController.java:217-223` does). True by default and for leave. Later executors that act for a user who need not be an employee return false: the approval decision (`W-114.18`) and employee create (`W-114.20`) | `403 FORBIDDEN` |
| 8 | Run the executor. The leave service runs every leave check again | Leave refusal → status `FAILED`, `result.reason` is the service's message, `409 AI_ACTION_FAILED` |
| 9 | Set `CONFIRMED`, `confirmed_at`, `result` | `200` |

Confirm does not count against the monthly cap; it calls no model.

**What the model is told (Added 2026-11-23, research review)**

The model learns the outcome of a proposal on the user's next message. The server adds one
fixed line to the end of the `ASSISTANT` message that proposed the action, in the copy sent
to the model only. The stored message is not changed, and the line is never model text.

| Intent status | Line added |
|---|---|
| `PROPOSED` | none |
| `CANCELLED` | `[The user declined this action. This is their choice, not an error. Do not propose it again unless they ask.]` |
| `CONFIRMED` | `[The user confirmed this action and it was carried out.]` |
| `EXPIRED` | `[This proposal expired and was not carried out.]` |
| `FAILED` | `[The user confirmed, but the action was refused: <result.reason>]` |

The intents are found with `findByTenantIdAndMessageIdIn` (`ai/AiActionIntentRepository.java:28`)
for the `ASSISTANT` messages in the 20-message history window of `W-114.4`.

**Audit**

| What | Where |
|---|---|
| Who proposed what, and when | The intent row: `user_id`, `action_code`, `payload`, `created_at` |
| Who confirmed, and when | `confirmed_at`; the confirmer is always `user_id` (check 1) |
| The leave request row written | `core.audit_log`, written by `AuditWriter` as for any leave request; its `trace_id` is the confirm request's correlation id (`AuditWriter.java:213`) |
| The link between the two | `result.traceId` on the intent holds that same correlation id, read from `MDC.get(MdcLoggingContext.CORRELATION_ID_KEY)` (`shared/logging/MdcLoggingContext.java:10`); `result.leaveRequestId` names the row |

**Audit rows for the intent itself (Added 2026-11-23, research review)**

The intent row is deleted after 30 days (`W-114.10`), so it is not the audit. Each step also
writes one row to `core.audit_log` through `AuditWriter.write` (`shared/audit/AuditWriter.java:173`).
`operation` allows only `INSERT`, `UPDATE`, `DELETE` (`V008__audit_log.sql:18`), so the step is
read from the status.

| Step | `operation` | `entity_table` · `entity_id` | Values |
|---|---|---|---|
| Propose | `INSERT` | `ai_action_intent` · the intent id | new: `action_code`, `status = PROPOSED`, `session_id`, `expires_at` |
| Confirm | `UPDATE` | same | `status`: `PROPOSED` → `CONFIRMED` |
| Cancel | `UPDATE` | same | `status`: `PROPOSED` → `CANCELLED` |
| Expire | `UPDATE` | same | `status`: `PROPOSED` → `EXPIRED`. Written when the intent is first read after its time |
| Fail | `UPDATE` | same | `status`: `PROPOSED` → `FAILED` |

`payload` and `result` are **not** copied into the audit row. The actor is the signed-in user
and `trace_id` is the request's correlation id, both filled by `AuditWriter` (`:197-213`). The
confirm row and the leave request's row therefore carry the same `trace_id`.

**API contract**

Confirm and Cancel take **no request body**. A body, if sent, is ignored: what runs is the stored payload.

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `POST` | `/api/v1/ai/actions/{intentId}/confirm` | — | `{ intentId, actionCode, status, result }` | `core.ai.use`, own intent only |
| `POST` | `/api/v1/ai/actions/{intentId}/cancel` | — | same shape, `status: "CANCELLED"`. Cancelling twice is `200`. Cancelling a confirmed intent is `409` | same |
| `GET` | `/api/v1/ai/actions/{intentId}` | — | `{ intentId, actionCode, summary, fields, status, expiresAt, result }` | same |

`result` for a confirmed leave: `{ "leaveRequestId": "…", "status": "PENDING", "traceId": "…" }`.

## Frontend changes

None. **Routes added:** none.

## Database changes

None. `core.ai_action_intent` comes from `W-114.3`; this spec only writes rows.

- [x] No new table, so no new `tenant_id` or policy. The intent table already has both (`W-114.3`)
- [x] No migration
- [x] No money value
- [x] Stays inside `core`: leave lives in `core/leave`, the helper in `core/ai`

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../leave/LeaveRequestServiceTest.java` (extend, or the existing create tests) | `validateApply` refuses exactly what `createRequest` refused before; `createRequest` still passes its existing tests unchanged |
| Unit | `core/src/test/.../ai/action/LeaveActionToolProviderTest.java` | Unknown leave type lists the valid names; an ineligible request writes no intent; a valid one writes `PROPOSED` with a 15-minute expiry and writes nothing to `core.leave_request` |
| Unit | `core/src/test/.../ai/action/AiActionServiceImplTest.java` | Each row of the re-check table: wrong user, expired, cancelled, helper switched off after the proposal, permission removed after the proposal, executor failure sets `FAILED` |
| Integration | `core/src/test/.../ai/action/AiActionConfirmIT.java` | Confirm creates one `PENDING` leave request for the caller; a second confirm returns the same `leaveRequestId` and the table still has one row; ten parallel confirms create one row; tenant B gets `404` for tenant A's intent; `result.traceId` equals the `trace_id` on the leave request's audit row |
| Unit | `core/src/test/.../ai/action/LeaveApplyExecutorTest.java` (added 2026-11-23) | C1, C2: `describe` returns every payload value as a field; a 500-character reason comes back whole; the fields are the same before and after Confirm; `describe` reads nothing but the payload and the leave type name |
| Unit | `AiActionServiceImplTest.java` (extend, added 2026-11-23) | C7: an executor with `requiredModule() = HRMS` and a customer without HRMS → `403`, executor not run. C10: propose, confirm, cancel, expire and fail each write exactly one audit row with the values above and no `payload` |
| Unit | `core/src/test/.../ai/AiChatServiceImplTest.java` (extend, added 2026-11-23) | C9: after a cancel, the next request sent to the stub model carries the "declined" line on that assistant message and no error; the stored message is unchanged; a `PROPOSED` intent adds no line |
| Integration | `AiActionConfirmIT.java` (extend, added 2026-11-23) | C8: Confirm with a body naming other dates creates the leave for the **stored** dates. C3: after the chat session is deleted, Confirm answers `404` and no leave is created. C5: an intent past its time answers `410`, is `EXPIRED`, and a later Confirm still runs nothing. C10: `core.audit_log` holds the `INSERT` and the `UPDATE` rows for the intent |
| Unit | `core/src/test/.../ai/AiToolRegistryTest.java` (extend, added 2026-11-23) | C11: no registered tool name contains `confirm` or `cancel_action`, and no tool class depends on `AiActionService.confirm` |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core -am verify
```

| Check | Expected | Result |
|---|---|---|
| The command | Build success; the new tests and every existing leave test pass | not run |
| Chat: "apply casual leave for 2026-12-04" | The answer carries `proposedAction` with status `PROPOSED`; `core.leave_request` has no new row | not run |
| `POST …/actions/{id}/confirm` | `200`, `status: "CONFIRMED"`, a `leaveRequestId`; the request shows on the Leave screen as Pending | not run |
| Confirm again | `200`, the same `leaveRequestId`; still one leave request | not run |
| Confirm after 15 minutes | `410 AI_ACTION_EXPIRED`; no leave request | not run |
| Confirm as another user of the same company | `404` | not run |
| Remove `core.leave.apply` from the user's role, then confirm | `403`; no leave request | not run |
| Platform admin switches the helper off, then confirm | `403 AI_DISABLED`; no leave request | not run |
| Ask for leave on dates that overlap an existing request | The helper explains the refusal; no intent is written | not run |
| `SELECT trace_id FROM core.audit_log` for the new leave request | Equals `result.traceId` on the intent | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Moving the leave checks changes the Leave screen's behaviour | Low | The lines move unchanged; the existing leave tests must pass without edits |
| The model proposes the wrong dates | Medium | The card shows the exact dates and day count before anything happens (`W-114.9`); the user can cancel |
| The card shows one thing and the server runs another (added 2026-11-23) | Low | C1 and C8: both come from the one stored payload; the model writes neither |
| After a Cancel the model proposes the same action again and again (added 2026-11-23) | Medium | C9: it is told the user declined, as a fact. `W-114.23` checks this against the real model |
| Balance changes between proposal and confirm | Low | The leave service checks again at confirm; a refusal becomes `FAILED` with the reason |
| The correlation id is missing (a call with no filter in front) | Low | `result.traceId` is then null; `result.leaveRequestId` still names the row |

## Rollback

Revert the commit. `core.propose_leave_request` is no longer offered to the model and the three
endpoints go. Leave requests already created stay; they are ordinary leave requests.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` (`leave`) | Extract `validateApply`; existing tests unchanged |
| 2 | `core` (`ai/action`) | `AiActionService`, the executor interface, the controller, the row lock, tests |
| 3 | `core` (`ai/action`) | `LeaveActionToolProvider`, `LeaveApplyExecutor`, the integration test |
| 4 | `core` (`ai`, `ai/action`) | (Added 2026-11-23) `describe`, `requiredModule`, the audit rows, the status line for the model, and the tests for C1–C11 |
