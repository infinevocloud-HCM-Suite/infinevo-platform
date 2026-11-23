# Feature: AI helper — model gateway, masking and Core answers

| Field | Value |
|---|---|
| **Feature ID** | `W-114.4` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai`), one settings block in `code/backend/app/src/main/resources/application.yml` |
| **Related gaps** | none in `GAP_INVENTORY.md`; the frozen system has no AI helper |
| **Status** | Ready |
| **Blocked by** | `W-114.1` (`AiAccessGate`) and `W-114.3` (session tables, `AiToolProvider`) on `main`. Not blocked by `W-114.2`: without Azure the stub model answers |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A signed-in user posts a question to a chat session and gets one full answer built from their own Core data, with PAN, Aadhaar and bank numbers masked | 1 |
| Frontend area | none | 1 |

It passes every axis, but it is the largest part of `W-114`: the endpoint, the loop, the
mask, the model client and seven tools. **If it runs long in the build, cut the tools in
"Core tools" below into a follow-up part and merge the gateway with one tool
(`my_leave_balances`) first.**

## Decisions

| Date | Who | Decision |
|---|---|---|
| 2026-11-23 | founder | Claude on Microsoft Foundry. Processing outside India is accepted: PAN, Aadhaar and bank account numbers are masked before sending; salary, leave and attendance figures are sent |
| 2026-11-16 | founder | One full answer per request. No streaming |
| this spec | Claude | The mask also hides IFSC, UAN, ESI number, passport number, passwords and tokens. They are never needed to answer |
| this spec | Claude | Text is masked **before it is stored** as well as before it is sent, so chat history holds no PAN either |
| this spec | Claude | The WFH tools sit here, not in `W-114.6`: WFH lives in `core` (`code/backend/core/src/main/java/com/infinevo/core/wfh/MyWfhController.java:30`) |
| this spec | Claude | No new library. The model is called with `java.net.http.HttpClient`, as `KeycloakProvisioningServiceImpl.java:8` does, and `azure-identity`, already in `code/backend/core/pom.xml:43-44` |
| this spec | Claude | A model failure answers `502` with the existing `ApiError.INTERNAL` code, so `shared` is not touched |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | Nothing in the backend calls an AI model. | no `ai` package under `code/backend/core/src/main/java/com/infinevo/core/` |
| 2 | A chat session (`W-114.3`) can be created but nothing answers a message. | `W-114.3` owns only session create, list, read, delete |
| 3 | The data a helper would read is already behind own-data endpoints, each with its own permission. | `LeaveAllocationController.java:100-101`, `LeaveRequestController.java:116-117`, `MyWfhController.java:53-62`, `HolidayCalendarController.java:80-81` |

## Scope

**In scope**

- `POST /api/v1/ai/sessions/{id}/messages`.
- `AiModelClient`, its Foundry implementation and its stub.
- The tool loop and what is stored.
- `AiMaskingFilter`.
- The first tools, as one `AiToolProvider` in `core`.

**Out of scope**

- The Azure resource and its settings in Bicep — `W-114.2`.
- The chat screen — `W-114.5`. HRMS and Payroll tools — `W-114.6`, `W-114.7`.
- Any action that changes data — `W-114.8`. The response already has an empty `proposedAction` field for it.
- Help articles and role guides — `W-114.11`, `W-114.12`.

## Flow

```
user --> POST /api/v1/ai/sessions/{id}/messages { content }
     --> session is the caller's?            no --> 404
     --> AiAccessGate.consume()              off --> 403 AI_DISABLED · at cap --> 429 AI_CAP_REACHED
     --> mask(content) --> store USER message
     --> offer only the tools the caller may use
     --> loop, at most 5 rounds:
            AiModelClient.send(system prompt, history, tools)
            model asks for a tool --> check requiredAction --> run as the caller --> mask(result) --> store TOOL message
            model gives text      --> mask(text) --> store ASSISTANT message --> 200
     --> model error or timeout  --> 502, the USER message stays, the request stays counted
```

## Rules

| # | Rule |
|---|---|
| 1 | **The model never receives** the tenant id, a user id, an employee id of another person, or a database key it could use to ask for someone else's data. Tool inputs carry dates, a status, a financial year — never "which employee". |
| 2 | A tool runs in the request thread as the caller, in the caller's tenant, through the same services the screens use. |
| 3 | A tool is offered to the model only when the caller holds its `requiredAction` (`PermissionService.holds`, `code/backend/shared/src/main/java/com/infinevo/shared/authz/PermissionService.java:102`). The action is checked again when the tool runs. |
| 4 | A tool that needs an employee record is not offered when the login has none (`EmployeeService.currentEmployee()` empty, `code/backend/core/src/main/java/com/infinevo/core/employee/EmployeeService.java:39`). |
| 5 | Everything that leaves the backend passes `AiMaskingFilter`: the user's text, every tool result, the stored history. The model's answer passes it too before it is stored or returned. |
| 6 | **Tool results are data, not instructions.** The system prompt says so, and each result is sent inside a tool-result block, never pasted into the instructions. |
| 7 | The system prompt tells the model to answer only from tool results and the conversation, to say "I can't see that" when no tool gives the answer, never to guess a figure, and never to claim it changed anything. |
| 8 | `consume()` is called once per user message, before the first model call. Tool rounds are not counted. A failed model call is still counted (`W-114.1` decision). |

**Limits**

| Limit | Value |
|---|---|
| User message length | 2,000 characters, else `400` |
| History sent | the last 20 messages of the session |
| Tool rounds per message | 5; on the 6th the helper answers "I could not finish that. Please ask in a smaller step." |
| Answer length | 1,024 output tokens |
| One model call | 30 seconds |
| Whole request | 60 seconds |
| A tool result | cut to 8,000 characters after masking, with "…(more rows not shown)" |

## Masking

`AiMaskingFilter.mask(String)` and `mask(JsonNode)`. Two passes, in this order.

**Pass 1 — by field name** (tool results are JSON). A value is replaced with `"[hidden]"` when its key, lower-cased with `_` and `-` removed, contains one of:

`aadhaar`, `aadhar`, `accountnumber`, `bankaccount`, `ifsc`, `esinumber`, `passport`, `password`, `secret`, `token`, `apikey`.

Two short names match **only as the whole key or with `number` / `no` after it**, because
they sit inside ordinary words: `pan` (`pan`, `pannumber`, `panno` — not `company`, `span`)
and `uan` (`uan`, `uannumber`, `uanno` — not `quantity`).

**Pass 2 — by pattern**, on every string, including the user's own text.

| # | What | Pattern | Becomes |
|---|---|---|---|
| 1 | Token | `\bBearer\s+[A-Za-z0-9._~+/=-]{20,}` and `\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}` | `[TOKEN]` |
| 2 | PAN | `\b[A-Za-z]{5}[0-9]{4}[A-Za-z]\b` | `[PAN]` |
| 3 | IFSC | `\b[A-Za-z]{4}0[A-Za-z0-9]{6}\b` | `[IFSC]` |
| 4 | Aadhaar | `\b[2-9][0-9]{3}[ -]?[0-9]{4}[ -]?[0-9]{4}\b` | `[AADHAAR]` |
| 5 | Bank account and other long numbers | `\b[0-9]{9,18}\b` | `[NUMBER]` |

**Passes through unchanged:** salary and payslip amounts, leave balances and dates,
attendance times, names, department, designation, location, email.

Pattern 5 is wide on purpose. It also hides phone numbers, UAN and ESI numbers. It cannot
hit a rupee amount below ₹10 crore, because amounts are written with a decimal point and
fewer than nine digits before it.

## Model access

| Piece | Detail |
|---|---|
| Interface | `AiModelClient.send(AiModelRequest) : AiModelResponse` in `core/ai`. The request holds the system prompt, the messages, the tool definitions and the limits. The response holds either text or a list of tool calls, plus the token counts |
| Real | `FoundryClaudeModelClient`. Calls Claude's Messages API on the Microsoft Foundry endpoint over HTTPS with a bearer token from the app's managed identity (`ManagedIdentityCredentialBuilder`, the pattern at `code/backend/core/src/main/java/com/infinevo/core/document/DocumentStorageConfig.java:47`). The token is cached until five minutes before it expires |
| Stub | `StubAiModelClient`. Used when `infinevo.ai.endpoint` is empty — every laptop and every CI run. It answers "The AI helper is not connected in this environment.", unless a test scripts its replies |
| Settings | `infinevo.ai.endpoint`, `infinevo.ai.deployment`, `infinevo.ai.managed-identity-client-id` |
| How they are supplied | Three lines in `code/backend/app/src/main/resources/application.yml`, each reading an environment variable with an empty default, as the queue settings do (`application.yml:97-98`): `${INFINEVO_AI_ENDPOINT:}`, `${INFINEVO_AI_DEPLOYMENT:}`, `${INFINEVO_AI_MANAGED_IDENTITY_CLIENT_ID:}`. `W-114.2` sets the variables on the container app. No `application*.properties` file is touched |
| Logged | Session id, rounds, tool names, token counts, duration. **Never** the prompt, a tool result or the answer |

`W-114.2` confirms the exact URL path of the Messages API on Foundry and the token scope.
This spec does not fix them; they are two constants in `FoundryClaudeModelClient`.

## Core tools

One class, `CoreAiToolProvider implements AiToolProvider`.

| Tool | Input | Calls | `requiredAction` | Evidence |
|---|---|---|---|---|
| `my_leave_balances` | `asOf` (optional date) | the balance call behind `GET /api/v1/me/leave-balances` | `core.leave.read_own` | `LeaveAllocationController.java:100-109` |
| `my_leave_requests` | `status`, `from`, `to` (all optional) | `leaveRequestService.searchRequests(tenantId, caller.id(), …)`, first 25 | `core.leave.read_own` | `LeaveRequestController.java:116-128` |
| `leave_types_for_me` | — | `leaveEligibilityService.getEligibleLeaveTypes(tenantId, caller.id(), today)` | `core.leave.apply` | `LeaveTypeController.java:87-96` |
| `holidays` | `from`, `to` (optional; default today to 12 months ahead) | `HolidayQueryService.holidaysBetween(…)` | `core.holiday.read` | `HolidayCalendarController.java:80-93`, `HolidayQueryService.java:54` |
| `org_lookup` | `kind`: `DEPARTMENT`, `DESIGNATION` or `LOCATION` | the list call behind each master's `GET` | `core.org.read` | `DepartmentController.java:53`, `DesignationController.java:47`, `WorkLocationController.java:51` |
| `my_wfh_requests` | — | `WfhRequestService.listEmployeeRequests(tenantId, caller.id())` | `core.wfh.read_own` | `MyWfhController.java:53-59` |
| `my_wfh_balance` | `asOf` (optional date) | `WfhBalanceService.getBalanceSummary(tenantId, caller.id(), asOf)` | `core.wfh.read_own` | `MyWfhController.java:61-69` |

All paths in this table are under `code/backend/core/src/main/java/com/infinevo/core/`
(`leave/`, `holiday/`, `org/`, `wfh/`).

Each tool returns the service's response as JSON, with database ids removed, so rule 1 holds.

## Backend changes

All under `code/backend/core/src/main/java/com/infinevo/core/ai/`.

| Layer | File | Change |
|---|---|---|
| Controller | `AiMessageController.java` | **New.** `POST /api/v1/ai/sessions/{id}/messages`, `@RequiresAction("core.ai.use")`. Its own handlers, because handlers here are per controller (`LeaveTypeController.java:98-112`): `AiDisabledException` → `403`, `AiCapReachedException` → `429`, session not found or not the caller's → `404`, bad input → `400`, `AiModelException` → `502` with `ApiError.INTERNAL` and the message "The helper could not answer just now. Please try again." |
| DTO | `AiMessageRequest.java`, `AiMessageResponse.java` | **New.** See the contract |
| Service | `AiChatService.java`, `AiChatServiceImpl.java` | **New.** The loop in "Flow". Each stored message is its own short transaction, so a model timeout never holds a database connection |
| Service | `AiToolRegistry.java` | **New.** Collects every `AiToolProvider` bean, refuses a duplicate tool name at startup, returns the tools the caller may use |
| Service | `AiMaskingFilter.java` | **New.** |
| Service | `AiSystemPrompt.java` | **New.** The fixed instructions plus today's date and the user's first name. Nothing else about the user |
| Client | `AiModelClient.java`, `AiModelRequest.java`, `AiModelResponse.java`, `AiModelException.java`, `FoundryClaudeModelClient.java`, `StubAiModelClient.java`, `AiModelConfig.java` | **New.** `AiModelConfig` picks the stub when `infinevo.ai.endpoint` is blank |
| Tools | `CoreAiToolProvider.java` | **New.** The seven tools |
| Settings | `code/backend/app/src/main/resources/application.yml` | The three `infinevo.ai.*` lines |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `POST` | `/api/v1/ai/sessions/{id}/messages` | `{ "content": "How many leave days do I have left?" }` | `200` `{ "message": { "id": "…", "role": "ASSISTANT", "content": "…", "createdAt": "…" }, "proposedAction": null }` | `core.ai.use`; the session must be the caller's |

| Case | Answer |
|---|---|
| Helper off for the customer | `403` `AI_DISABLED` |
| Monthly cap reached | `429` `AI_CAP_REACHED` |
| Session of another user, or unknown | `404` |
| Empty or over 2,000 characters | `400` |
| Model error or timeout | `502` |

**Routes added:** none (no frontend).

## Database changes

None. Rows are written to `core.ai_message`, which `W-114.3` creates.

- [x] Creates no table, so no new `tenant_id` or policy
- [x] No Flyway script; `ddl-auto` untouched
- [x] No money type introduced; amounts from tools stay `BigDecimal` and are written as plain decimal text
- [x] No new index
- [x] Nothing dropped or renamed
- [x] `core` references no other module; `hrms` and `payroll` reach the helper only through `AiToolProvider`

## Tests

All use `StubAiModelClient` with scripted replies. No test calls Azure.

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/AiMaskingFilterTest.java` | The table below |
| Unit | `core/src/test/.../ai/AiToolRegistryTest.java` | A tool whose action the caller lacks is **never offered**; duplicate name fails startup; no employee record hides the own-data tools |
| Unit | `core/src/test/.../ai/AiChatServiceImplTest.java` | Text answer stored; one tool round; a sixth round stops with the fixed text; model error keeps the USER message and throws; `consume()` called exactly once; a tool the model names but the caller may not use is refused and reported to the model as "not available" |
| Unit | `core/src/test/.../ai/CoreAiToolProviderTest.java` | Each tool calls its service with the caller's id and returns no database id |
| Unit | `core/src/test/.../ai/FoundryClaudeModelClientTest.java` | Request body carries the deployment, tools and limits; a tool-call reply and a text reply are parsed; HTTP 429, 500 and a timeout become `AiModelException`. Against a local stub HTTP server |
| Integration | `core/src/test/.../ai/AiMessageIT.java` | The verification table, on a real database |

**Masking test table**

| Input | Output |
|---|---|
| `My PAN is ABCDE1234F` | `My PAN is [PAN]` |
| `abcde1234f` | `[PAN]` |
| `Aadhaar 2345 6789 0123` | `Aadhaar [AADHAAR]` |
| `234567890123` | `[AADHAAR]` |
| `A/c 50100123456789, HDFC0001234` | `A/c [NUMBER], [IFSC]` |
| `Bearer eyJhbGciOi…` (a full token) | `[TOKEN]` |
| `{ "pan": "ABCDE1234F", "netPay": "45000.00" }` | `{ "pan": "[hidden]", "netPay": "45000.00" }` |
| `{ "bank_account_number": "12345", "name": "Asha" }` | `{ "bank_account_number": "[hidden]", "name": "Asha" }` |
| `Net pay 125000.00 for 2026-10, 4.5 days leave` | unchanged |
| `{ "company": "Acme", "quantity": "3", "span": "5" }` | unchanged (`pan` and `uan` match only as a whole key) |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core -am verify
```

| Check | Expected | Result |
|---|---|---|
| App starts with `INFINEVO_AI_ENDPOINT` unset | Starts; log line "AI helper: stub model client" | not run |
| `POST …/messages` for a customer with the helper off | `403 AI_DISABLED`; no row in `core.ai_message`; usage count unchanged | not run |
| Same, helper on, stub scripted to call `my_leave_balances` then answer | `200`; three rows stored: USER, TOOL, ASSISTANT; usage count + 1 | not run |
| Same question from a login with no `core.leave.read_own` | The tool is not in the list sent to the model | not run |
| Message containing a PAN | Stored USER row reads `[PAN]`; the text sent to the model reads `[PAN]` | not run |
| Post to another user's session | `404` | not run |
| Stub scripted to fail | `502`; USER row kept; usage count + 1 | not run |
| Customer at its cap | `429 AI_CAP_REACHED` | not run |
| Stub scripted to ask for tools six times | `200` with "I could not finish that…"; five TOOL rows | not run |
| Grep the test log for a PAN, a tool result or an answer text | Not found | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A tool result tells the model to do something ("ignore your rules") | Medium | Rule 6; every tool here is read-only and own-data, so the worst outcome is a wrong sentence |
| A new sensitive field is added to a response later and is not in the key list | Medium | Pattern pass 2 still catches numbers; a new tool's spec must state its sensitive fields |
| Pattern 5 hides a number the user wanted (an employee number of nine digits or more) | Low | Accepted; the user sees `[NUMBER]` and opens the screen |
| The Messages API path or token scope on Foundry differs from what the client assumes | Medium | Two constants, confirmed in `W-114.2`; nothing else depends on them |
| A slow model holds request threads | Low | 30 and 60 second limits; no database connection is held while waiting |

## Rollback

Revert the commit. The endpoint goes; stored messages stay in `core.ai_message` until the
30-day job (`W-114.10`) removes them. With the helper off for every customer, nothing a
customer sees changes.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` | `AiMaskingFilter` and its test table |
| 2 | `core` | `AiModelClient`, stub, Foundry client, config, the three settings lines |
| 3 | `core` | `AiToolRegistry`, `AiChatService`, `AiMessageController` |
| 4 | `core` | `CoreAiToolProvider`, the seven tools |
| 5 | `core` | `AiMessageIT` |
