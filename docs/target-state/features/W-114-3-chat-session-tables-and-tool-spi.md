# Feature: AI helper chat sessions and the tool plug-in point

| Field | Value |
|---|---|
| **Feature ID** | `W-114.3` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai`), `code/backend/migration` |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.1` on `main` (the `core/ai` package and `AiAccessGate`) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 1 — next free version at build time | 1 |
| Externally testable behaviour | A signed-in user creates, lists, reads and deletes their own chat sessions, and nobody else's | 1 |
| Frontend area | none | 1 |

This part stores chats and defines how a module offers a tool. It calls no model
(`W-114.4`) and shows no screen (`W-114.5`).

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | Nowhere to keep a chat, its messages or a proposed action. | `grep -rn "ai_session" code/backend` → 0 matches |
| 2 | `hrms` and `payroll` may reference only `core`, so they need a `core` interface to offer tools to the helper. | no `AiToolProvider` under `code/backend`; the same need was met for portal panels by `core/portal/PortalPanelProvider.java` |
| 3 | No permission says "this user may use the helper". | no `core.ai.use` row in any migration |

## Scope

**In scope**

- Three tables in `core`, the action `core.ai.use` and its grants.
- Session create, list, read messages, delete.
- The tool types and a registry that collects every module's tools.

**Out of scope**

- Sending a message and getting an answer — `W-114.4` (`POST /api/v1/ai/sessions/{id}/messages`).
- Any real tool — `W-114.4` (core), `W-114.6` (hrms), `W-114.7` (payroll).
- Confirming or cancelling an action — `W-114.8`. This part only creates the intent table.
- Deleting old chats — `W-114.10`.

## Flow

```
User --> POST /api/v1/ai/sessions --> AiSessionService.create --> core.ai_session
User --> GET  /api/v1/ai/sessions --> own sessions, newest first
User --> GET  /api/v1/ai/sessions/{id}/messages --> core.ai_message (USER and ASSISTANT rows only)
User --> DELETE /api/v1/ai/sessions/{id} --> session, its messages and intents

module bean implements AiToolProvider --> AiToolRegistry collects them at startup (used by W-114.4)
```

## Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V…__ai_chat.sql` — next free version at build time | `core.ai_session`, `core.ai_message`, `core.ai_action_intent` | yes | yes — expand only |

| Table | Columns |
|---|---|
| `core.ai_session` | `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id)`, `user_id UUID NOT NULL`, `employee_id UUID NULL REFERENCES core.employee(id)`, `title VARCHAR(200) NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `last_message_at TIMESTAMPTZ NULL` |
| `core.ai_message` | `id`, `tenant_id` (as above), `session_id UUID NOT NULL REFERENCES core.ai_session(id) ON DELETE CASCADE`, `role VARCHAR(16) NOT NULL CHECK (role IN ('USER','ASSISTANT','TOOL'))`, `content TEXT NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `core.ai_action_intent` | `id`, `tenant_id`, `session_id` (as above, `ON DELETE CASCADE`), `user_id UUID NOT NULL`, `message_id UUID NULL REFERENCES core.ai_message(id) ON DELETE SET NULL` (the assistant message that proposed it; `W-114.8` fills it), `action_code VARCHAR(100) NOT NULL`, `payload JSONB NOT NULL`, `status VARCHAR(16) NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED','CONFIRMED','CANCELLED','EXPIRED','FAILED'))`, `expires_at TIMESTAMPTZ NOT NULL`, `confirmed_at TIMESTAMPTZ NULL`, `result JSONB NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` |

`user_id` is the signed-in user's token subject, the id the platform already keys a user
on (`code/backend/shared/src/main/java/com/infinevo/shared/identity/UserProfileSyncFilter.java:103`).
`employee_id` is empty for a user with no employee record.

| # | Step |
|---|---|
| 1 | The three tables, in the order above |
| 2 | Indexes: `idx_ai_session_tenant_user ON core.ai_session (tenant_id, user_id, last_message_at DESC)`; `idx_ai_message_tenant_session ON core.ai_message (tenant_id, session_id, created_at)`; `idx_ai_intent_tenant_session ON core.ai_action_intent (tenant_id, session_id)` |
| 3 | On each table: `ENABLE ROW LEVEL SECURITY` and the `tenant_isolation` policy with the exact `CASE` clause (`code/backend/migration/src/main/resources/db/migration/core/V094__retention_run.sql:30-39`) |
| 4 | `GRANT SELECT, INSERT, UPDATE, DELETE` on the three tables `TO app_user` (pattern: `core/V179__wfh_policy.sql:38`) |
| 5 | `INSERT INTO reference.action` the row `core.ai.use`, `ON CONFLICT (code) DO NOTHING` — before any grant (pattern: `core/V184__wfh_seed_roles.sql:5-13`) |
| 6 | `core.grant_ai_actions(p_tenant_id)`, `SECURITY DEFINER`, skipping the platform tenant, granting `core.ai.use` to every system role of the tenant; `REVOKE EXECUTE … FROM PUBLIC` (pattern: `V184:16-82`, without its approval seed at `:63-78`) |
| 7 | Trigger function and trigger `tenant_seed_system_roles_ai`, `AFTER INSERT ON core.tenant`, so a customer created later gets the grant (pattern: `V184:85-104`) |
| 8 | Backfill: `SELECT core.grant_ai_actions(t.tenant_id) FROM core.tenant t` (`V184:107`) |

"Every system role" means every row of `core.role` with `is_system` for the tenant, so the
function joins on `r.is_system` alone and names no role code. A role added later by
another ticket does not get the grant by itself.

- [x] `tenant_id` and an RLS policy on all three tables
- [x] Each index leads with `tenant_id`
- [x] No money column
- [x] Expand only
- [x] Every statement names `core.` or `reference.`

`core.ai.use` lets a user open the helper. Whether the customer has the helper at all is
the switch from `W-114.1`; both must hold.

## Backend changes

All under `code/backend/core/src/main/java/com/infinevo/core/ai/`.

| Layer | File | Change |
|---|---|---|
| Entities | `AiSession.java`, `AiMessage.java`, `AiActionIntent.java` | **New.** Map the three tables |
| Enums | `AiMessageRole.java`, `AiIntentStatus.java` | **New.** The values in the CHECKs |
| Repositories | `AiSessionRepository.java`, `AiMessageRepository.java`, `AiActionIntentRepository.java` | **New.** `findByIdAndTenantIdAndUserId`, `findByTenantIdAndUserIdOrderByLastMessageAtDesc` (paged), `findBySessionIdOrderByCreatedAt`; on the intent repository also `findByIdAndTenantIdAndUserIdForUpdate` (row lock, `PESSIMISTIC_WRITE`) for the confirm step of `W-114.8`, and `findByMessageIdIn` for the messages list |
| Service | `AiSessionService.java`, `AiSessionServiceImpl.java` | **New.** `create`, `listMine`, `messages(sessionId)`, `delete(sessionId)`. Every lookup is by id **and** tenant **and** user, so another user's session is "not found". `create` fills `employee_id` from `EmployeeService.currentEmployee()` (`core/employee/EmployeeServiceImpl.java:306`). `messages` returns `USER` and `ASSISTANT` rows only; `TOOL` rows are for the model |
| Controller | `AiSessionController.java` | **New.** The four endpoints below, each `@RequiresAction("core.ai.use")`. Own handlers: unknown or foreign session → `404 NOT_FOUND`; bad input → `400 VALIDATION_FAILED`. Create also calls `AiAccessGate.status()` and answers `403 AI_DISABLED` when the customer's switch is off |
| Tool types | `AiToolProvider.java`, `AiTool.java`, `AiToolContext.java`, `AiToolResult.java` | **New.** See the contract below |
| Registry | `AiToolRegistry.java` | **New.** Takes `List<AiToolProvider>` in its constructor, the way `PortalPanelService` takes its providers (`core/portal/PortalPanelService.java:35-47`). Fails startup on two tools with the same name. `find(name)`, `toolsFor(caller)` |

**Tool contract** — the names `W-114.4` to `W-114.8` build on

| Type | Shape |
|---|---|
| `AiToolProvider` | `List<AiTool> tools();` — a module declares one bean; `core` never names another module |
| `AiTool` | `String name()` (unique, `module.verb_noun`, for example `core.get_leave_balance`); `String description()`; `JsonNode inputSchema()`; `String requiredAction()`; `AiToolResult execute(AiToolContext ctx, JsonNode input)` |
| `AiToolContext` | `record(UUID tenantId, UUID userId, UUID employeeId)` — `employeeId` may be null |
| `AiToolResult` | `record(boolean ok, JsonNode data, String error)` |

Rules the registry enforces, so no tool has to:

- A tool runs as the caller, in the caller's tenant. Nothing in this package switches tenant or raises rights.
- `toolsFor(caller)` leaves out a tool whose `requiredAction` the caller does not hold, using the `PermissionService` check `PortalPanelService` uses (`:78`). The model is never told about a tool the user may not use.
- `execute` is called only through the registry, which checks `requiredAction` again at that moment.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `POST` | `/api/v1/ai/sessions` | `{ "title": "…" }` (optional) | `201` `{ id, title, createdAt, lastMessageAt }` | `core.ai.use` |
| `GET` | `/api/v1/ai/sessions` | `?page=&size=` (size 20, max 50) | page of the caller's sessions, newest activity first | `core.ai.use` |
| `GET` | `/api/v1/ai/sessions/{id}/messages` | — | `[{ id, role, content, createdAt, proposedAction }]`, oldest first. `proposedAction` is `{ intentId }` on an assistant message that proposed an action, else `null`; always `null` until `W-114.8` writes intents. The card in `W-114.9` reads the intent's state from `GET /api/v1/ai/actions/{intentId}` (`W-114.8`) | `core.ai.use` |
| `DELETE` | `/api/v1/ai/sessions/{id}` | — | `204` | `core.ai.use` |

## Frontend changes

None. **Routes added:** none.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/AiSessionServiceImplTest.java` | Create fills user and employee; a user with no employee still creates; list returns only the caller's; `messages` hides `TOOL` rows; delete of another user's session throws not found |
| Unit | `core/src/test/.../ai/AiToolRegistryTest.java` | Duplicate tool name fails startup; `toolsFor` hides a tool the caller lacks the action for; `execute` refuses when the action is missing |
| Unit | `core/src/test/.../ai/AiSessionControllerTest.java` | `404` for an unknown id; `403 AI_DISABLED` on create when the switch is off; `core.ai.use` required |
| Integration | `core/src/test/.../ai/AiChatRlsIT.java` | As `app_user`: tenant B reads no row of tenant A in any of the three tables; with no tenant bound, no row |
| Integration | `core/src/test/.../ai/AiSessionIT.java` | **User A cannot read, list or delete user B's session in the same tenant** (`404`, and B's session is still there); deleting a session removes its messages and intents |
| Integration | `migration/src/test/.../RoleCatalogueIT.java` (extend) | `core.ai.use` exists; every system role of an existing tenant holds it; a tenant created after the migration holds it too |

## Verification

```bash
cd code/backend && ./mvnw -B -pl migration,core -am verify
```

| Check | Expected | Result |
|---|---|---|
| The migration applies on the current `main` schema | `Successfully applied 1 migration`; `\d core.ai_session`, `core.ai_message`, `core.ai_action_intent` each show RLS enabled | not run |
| `POST /api/v1/ai/sessions` as an employee of a customer with the helper on | `201` | not run |
| Same call, customer's switch off | `403 AI_DISABLED` | not run |
| `GET /api/v1/ai/sessions` as user A, then as user B of the same customer | Each sees only their own | not run |
| `GET /api/v1/ai/sessions/{A's id}/messages` as user B | `404` | not run |
| `DELETE /api/v1/ai/sessions/{id}` then `GET …/messages` | `204`, then `404` | not run |
| A new customer created after the migration | Its employee role holds `core.ai.use` | not run |
| Application start with two test tools of the same name | Startup fails with both names in the message | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A tool forgets to scope to the caller and returns someone else's data | Medium, and the worst case in `W-114` | The context carries the caller; the registry checks the action twice; every tool part (`.4`, `.6`, `.7`) must carry a test "user A asks for user B's data and gets nothing" |
| Chats pile up before `W-114.10` | Certain until then | Founder rule: the helper stays off for real customers until `W-114.10` is on `main` |
| Another ticket adds a system role later and it gets no `core.ai.use` | Low | That ticket's migration calls `core.grant_ai_actions` again |
| Message text holds personal data | Certain | Masking happens before storage and before the model in `W-114.4`; RLS and the per-user lookup guard it here |

## Rollback

Expand only. Revert the application; the tables, action and grants stay and nothing reads
them.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `migration` | The one script, steps 1-8, and the `RoleCatalogueIT` cases |
| 2 | `core` | Entities, repositories, session service and controller, with tests |
| 3 | `core` | Tool types and `AiToolRegistry`, with tests |
