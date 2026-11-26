# Feature: AI helper — off while acting as another user

| Field | Value |
|---|---|
| **Feature ID** | `W-114.13b` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114-13b`) |
| **Apps touched** | `code/backend/core` (`ai`), `code/frontend/src/shell` |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready (founder, 2026-11-26: "own small part now") |
| **Blocked by** | none |

Size **S**. Paths are under `code/backend/core/src/main/java/com/infinevo/core/` unless they say otherwise.

## Problem

The rule is that the AI helper stays off in act-as. Today it is on.

| # | Today | Evidence |
|---|---|---|
| 1 | During act-as, the permission check uses the target user's actions, so `core.ai.use` passes when the target holds it | `code/backend/shared/src/main/java/com/infinevo/shared/authz/PermissionService.java:65-82` |
| 2 | Nothing under `ai/` reads `ActingAs`. Staff acting as a user can ask questions, read that user's chat history, and confirm actions | `ai/` (no reference to `ActingAs`) |
| 3 | The shell mounts the launcher during a session | `code/frontend/src/shell/AppShell.jsx:317` |

## Scope

**In scope:** refuse every `/api/v1/ai/**` call while an act-as session is bound, and hide the launcher during a session.
**Out of scope:** an act-as AI mode, or any change to how act-as works.

## Rules

| # | Rule |
|---|---|
| 1 | Every request under `/api/v1/ai/` while `ActingAs` is bound (bootstrap or target) answers `403 AI_DISABLED` with the message "The AI helper is off while acting as another user." The controller does not run, so no chat row, intent, usage count or audit row is written |
| 2 | Backstop: under act-as, `AiAccessGate.status()` answers `enabled: false` and `consume()` throws `AiDisabledException` |
| 3 | The shell does not mount `AssistantLauncher` while an impersonation session is live |
| 4 | Outside act-as, nothing changes |

## Changes

| Layer | File | Change |
|---|---|---|
| Web | `ai/AiActAsGuard.java` | **New.** `HandlerInterceptor` and its `WebMvcConfigurer` registration for `/api/v1/ai/**` |
| Gate | `ai/AiAccessGateImpl.java` | Rule 2 |
| Shell | `frontend/src/shell/AppShell.jsx` | Rule 3 |

No migration, no table, no new route.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/AiActAsGuardTest.java` | Bound: 403 `AI_DISABLED` body, handler not reached. Not bound: passes |
| Unit | `core/src/test/.../ai/AiAccessGateImplTest.java` (extend) | Under act-as: status off, consume refuses, no repository call |
| Integration | `core/src/test/.../ai/AiActAsIT.java` | The real filter chain with an act-as session for a user who holds `core.ai.use`: status, session list, message post and link targets are all 403 `AI_DISABLED`; no `ai_message` or `ai_usage` row is written; the same user without act-as gets 200 |
| Vitest | `frontend/src/shell/AppShell.impersonation.test.jsx` (extend) | No launcher while a session is live |

## Rollback

Revert the commit.
