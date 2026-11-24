# Feature: AI helper — thumbs up or down on an answer

| Field | Value |
|---|---|
| **Feature ID** | `W-114.25` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai`, one read in `tenant`), `code/frontend/src/shell/assistant` |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.5` on `main` (the drawer and its `MessageList`); through it `W-114.3` and `W-114.4` |
| **Build order** | After `W-114.5` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one — `core.ai_message_feedback` | 1 |
| Externally testable behaviour | A user rates one of the helper's answers up or down, with an optional reason code; the rating is kept with the chat | 1 |
| Frontend area | `shell` — the drawer of `W-114.5` | 1 |

Size **S**. The platform admin's count is one read endpoint here. **Showing** it is on the
tenant page, a second frontend area, so that one line belongs to the "AI helper" card of
`W-114.24` — see "Frontend changes".

## Decisions

| Date | Who | Decision |
|---|---|---|
| 2026-11-23 | founder | (Research review) Thumbs up or down on an assistant message, with an optional reason code: wrong figure, did not answer, other. **No free-text comment in version 1.** The platform admin sees counts only, never chat text. A rating is deleted with its chat |
| this spec | Claude | A **separate table**, not two columns on `core.ai_message` — reasons below |
| this spec | Claude | One rating per message. The user may change it or take it back |
| this spec | Claude | A reason code is accepted only with thumbs down |

**Table or columns**

| | Two columns on `core.ai_message` | A table `core.ai_message_feedback` (chosen) |
|---|---|---|
| The message row | Must become updatable. Today every column is `updatable = false` and the class says "Immutable once stored" (`code/backend/core/src/main/java/com/infinevo/core/ai/AiMessage.java:18-19,43`) | Unchanged |
| When it was rated, and a change of mind | Needs a third and fourth column | Its own `created_at`, `updated_at` |
| Counting | Scans every message of the customer, tool rows included | Scans ratings only |
| Deleted with the chat | Yes | Yes — `ON DELETE CASCADE` on `message_id`, the way messages follow their session (`code/backend/migration/src/main/resources/db/migration/core/V200__ai_chat.sql:25`) |
| Cost | No new table | One small table with `tenant_id` and a policy |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | Nobody can tell us an answer was wrong. The only signals are the request count and, after `W-114.24`, tokens. | `core.ai_usage`, `V192__ai_switch_and_usage.sql:52-58` |
| 2 | The message the API returns has no rating. | `code/backend/core/src/main/java/com/infinevo/core/ai/AiMessageResponse.java:10-11` |
| 3 | The drawer shows an answer as text and nothing else. It is not built yet; `W-114.5` specifies it. | `W-114-5-chat-drawer.md`, `MessageList.jsx` row; no `code/frontend/src/shell/assistant` on `main` |

## Scope

**In scope**

- `core.ai_message_feedback`.
- Set, change and remove a rating on one's own assistant message.
- The rating returned with the messages of a chat.
- Two small buttons under each answer in the drawer, and three reason choices after thumbs down.
- A count per customer for the platform admin.

**Out of scope**

- A typed comment. Free text would be one more place for personal data, and for instructions.
- Showing ratings to the customer's own admin. Reading chat text by anyone but its owner.
- Using ratings to change the prompt automatically. A person reads the counts.
- Rating a `USER` or `TOOL` message, or an action card.

## Flow

```
User clicks thumbs down under an answer --> picks "Wrong figure" (or skips)
  --> PUT /api/v1/ai/messages/{messageId}/feedback { rating: "DOWN", reasonCode: "WRONG_FIGURE" }
        --> the message is an ASSISTANT message of a session of this user, in this tenant?  no --> 404
        --> upsert core.ai_message_feedback (one row per message)
User clicks the same thumb again --> DELETE /api/v1/ai/messages/{messageId}/feedback

Chat reloaded --> GET /api/v1/ai/sessions/{id}/messages --> each assistant message carries feedback or null

Platform admin --> GET /api/v1/tenants/{id}/ai-feedback --> counts only
Chat deleted by the user, or by the 30-day job (W-114.10) --> the ratings go with the messages
```

## Rules

| # | Rule |
|---|---|
| 1 | Only the owner of the chat can rate, change or remove. Anyone else, and any unknown id, gets `404`. |
| 2 | Only an `ASSISTANT` message can be rated. A `USER` or `TOOL` message id answers `404`. |
| 3 | `rating` is `UP` or `DOWN`. `reasonCode` is `WRONG_FIGURE`, `DID_NOT_ANSWER`, `OTHER` or absent. A reason with `UP` is `400`. |
| 4 | No text field exists — not in the request, not in the table. An unknown field in the body is ignored. |
| 5 | Rating needs `core.ai.use` and nothing else. It still works when the helper is at its cap or switched off: it calls no model and counts against nothing. |
| 6 | The platform admin's endpoint returns numbers only. No message id, no user id, no text. |
| 7 | A rating lives exactly as long as its message. Nothing copies it elsewhere, so counts cover the last 30 days at most (`W-114.10`). |
| 8 | A rating is never sent to the model. |

## Backend changes

Paths are under `code/backend/core/src/main/java/com/infinevo/core/`.

| Layer | File | Change |
|---|---|---|
| Entity | `ai/AiMessageFeedback.java` | **New.** Maps the table |
| Enumeration | `ai/AiFeedbackRating.java`, `ai/AiFeedbackReason.java` | **New.** `UP`, `DOWN`; `WRONG_FIGURE`, `DID_NOT_ANSWER`, `OTHER` |
| Repository | `ai/AiMessageFeedbackRepository.java` | **New.** `findByTenantIdAndMessageId`, `findByTenantIdAndMessageIdIn`, `deleteByTenantIdAndMessageId` |
| Service | `ai/AiFeedbackService.java`, `AiFeedbackServiceImpl.java` | **New.** `set(messageId, rating, reason)`, `remove(messageId)`. Loads the message, then its session by id, tenant and user, as `AiSessionService` does for every lookup — so another user's message is "not found" |
| Controller | `ai/AiFeedbackController.java` | **New.** `PUT` and `DELETE /api/v1/ai/messages/{messageId}/feedback`, `@RequiresAction("core.ai.use")`. Not found → `404`; bad value → `400` |
| DTO | `ai/AiFeedbackRequest.java`; `ai/AiMessageResponse.java` (`W-114.3`) | **New** request `{ rating, reasonCode }`. `AiMessageResponse` gains `feedback`: `{ rating, reasonCode }` or `null` (`AiMessageResponse.java:10-11`) |
| Service | `ai/AiSessionServiceImpl.java` (`W-114.3`) | `messages(sessionId)` fills `feedback` with one `findByTenantIdAndMessageIdIn` call |
| Query | `tenant/TenantQueryService.java`, `tenant/TenantController.java` | **Add** `GET /api/v1/tenants/{id}/ai-feedback`, platform tenant only, guarded as the subscription endpoints are (`subscription/SubscriptionController.java:76`). Reads `core.ai_feedback_counts(uuid)` |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `PUT` | `/api/v1/ai/messages/{messageId}/feedback` | `{ "rating": "DOWN", "reasonCode": "WRONG_FIGURE" }` — `reasonCode` optional | `200` `{ "rating": "DOWN", "reasonCode": "WRONG_FIGURE" }` | `core.ai.use`; own chat only |
| `DELETE` | `/api/v1/ai/messages/{messageId}/feedback` | — | `204`, also when there was no rating | same |
| `GET` | `/api/v1/ai/sessions/{id}/messages` (`W-114.3`) | — | each assistant message gains `feedback` | unchanged |
| `GET` | `/api/v1/tenants/{id}/ai-feedback` | — | `{ "up": 41, "down": 6, "downByReason": { "WRONG_FIGURE": 2, "DID_NOT_ANSWER": 3, "OTHER": 0, "NONE": 1 } }` — ratings that exist now | `core.tenant.provision`, platform tenant only |

## Frontend changes

Files are under `code/frontend/src/shell/assistant/` (created by `W-114.5`).

| File | Change |
|---|---|
| `FeedbackButtons.jsx` | **New.** Two small icon buttons under an assistant message: `aria-label` "Good answer" and "Bad answer", `aria-pressed` for the chosen one. After thumbs down, three choices appear in a row — "Wrong figure", "Did not answer", "Other" — and can be skipped. Clicking the chosen thumb again removes the rating. No text box |
| `MessageList.jsx` | `renderMessage` draws `FeedbackButtons` under each `ASSISTANT` message, below an action card if there is one. Not under the "Thinking…" row or an error row |
| `assistantService.js` | **Add** `setFeedback(messageId, rating, reasonCode)`, `removeFeedback(messageId)` |
| `assistant.css` | Spacing only. Colours from theme tokens (`code/frontend/.eslintrc.cjs:56-64,147-151`) |

| State | What the user sees |
|---|---|
| Not rated | Two outline thumbs |
| Rated up or down | That thumb filled. For down, the chosen reason is marked, if any |
| Saving fails | The thumb goes back and a small line: "Could not save your rating." |
| Chat reloaded | The saved rating is shown from `feedback` |

**The platform admin's view** is one line on the "AI helper" card of the tenant page:
"Answers rated: 41 up · 6 down (2 wrong figure · 3 did not answer)". That card is in
`code/frontend/src/core/admin` — a second frontend area — so the line is specified in
`W-114.24`, "Feedback line". Whichever of the two parts is built second adds it.

**Routes added:** none.

## Database changes

> Flyway only. Read `code/backend/migration/README.md` first: every statement names its schema.

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `V<next free version at build time>__ai_message_feedback.sql` (`core`) | **New** `core.ai_message_feedback`; **new** function `core.ai_feedback_counts(uuid)` | yes | yes — drop the table and the function |

| Column | Type |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL REFERENCES core.tenant(tenant_id)` |
| `message_id` | `UUID NOT NULL REFERENCES core.ai_message(id) ON DELETE CASCADE` |
| `user_id` | `UUID NOT NULL` — the rater, always the chat's owner |
| `rating` | `VARCHAR(8) NOT NULL CHECK (rating IN ('UP','DOWN'))` |
| `reason_code` | `VARCHAR(24) NULL CHECK (reason_code IN ('WRONG_FIGURE','DID_NOT_ANSWER','OTHER'))`, and `CHECK (reason_code IS NULL OR rating = 'DOWN')` |
| `created_at`, `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |

| Also in the script | Detail |
|---|---|
| Unique index | `(tenant_id, message_id)` — one rating per message, and the conflict target of the upsert |
| Row-level security | `ENABLE ROW LEVEL SECURITY` and the standard `tenant_isolation` policy, as on the chat tables (`V200__ai_chat.sql:65-74`) |
| Grants | `SELECT, INSERT, UPDATE, DELETE` to `app_user`, as `V200__ai_chat.sql:89-91` |
| `core.ai_feedback_counts(p_tenant_id uuid)` | `SECURITY DEFINER`, fixed `search_path`, `EXECUTE` revoked from `PUBLIC` and granted to `app_user`, on the pattern of `core.set_subscription_ai` (`V192__ai_switch_and_usage.sql:25-49`). Returns counts only. The endpoint that calls it is platform-tenant only |

- [x] `tenant_id` and an RLS policy on the new table
- [x] Index leads with `tenant_id` (`DEBT-018`)
- [x] No money value
- [x] Expand only: one new table and one new function; the previous release reads neither
- [x] Deleted with the chat by the foreign key. `W-114.10` needs **no change** and `retention_user` needs **no new grant**: it deletes messages, and the database removes their ratings, as it already removes a session's messages
- [x] Stays inside `core`

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/AiFeedbackServiceImplTest.java` | Set, change up to down, remove; a reason with `UP` is refused; a `USER` or `TOOL` message is not found; another user's message is not found |
| Integration | `core/src/test/.../ai/AiFeedbackIT.java` | `PUT` twice leaves one row with the second value; **user B of the same tenant gets `404` for A's message and no row is written**; tenant B cannot read or rate tenant A's message; `GET …/messages` returns the rating; deleting the chat removes the rating; deleting only the message row (as the 30-day job does) removes the rating; rating works with the helper at its cap; a body with a `comment` field stores nothing extra |
| Integration | `core/src/test/.../tenant/AiFeedbackCountsIT.java` | Counts match the rows; a customer tenant calling the endpoint gets `403`; the response holds numbers only |
| Component | `src/shell/assistant/FeedbackButtons.test.jsx` | Up calls `setFeedback(id, 'UP')`; down shows the three reasons and sends the chosen one; clicking the chosen thumb calls `removeFeedback`; a failed save restores the thumb and shows the line; there is no text input in the component |
| Component | `src/shell/assistant/MessageList.test.jsx` (extend) | Buttons under assistant messages only; a loaded chat shows the saved rating |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core -am verify
cd code/frontend && npx vitest run src/shell/assistant && npm run lint && npm run build
git grep -nE '^[^#]*ddl-auto[[:space:]]*[:=]' -- code/      # expect nothing
```

| Check | Expected | Result |
|---|---|---|
| The commands | All pass; lint reports 0 warnings | not run |
| The migration on a database with the `W-114.3` tables | `Successfully applied 1 migration`; `\d core.ai_message_feedback` shows the policy | not run |
| Thumbs down, then "Wrong figure" | One row: `DOWN`, `WRONG_FIGURE` | not run |
| Thumbs up on the same answer | Still one row: `UP`, no reason | not run |
| Reload the page, open the chat | The up thumb is filled | not run |
| `PUT` as another user of the same company | `404`; no row | not run |
| Delete the chat | The rating row is gone | not run |
| `GET /api/v1/tenants/{id}/ai-feedback` as platform admin | Numbers only | not run |
| Same as a customer's admin | `403` | not run |
| Keyboard only | Both thumbs and the reasons are reachable and work with Enter | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Few people rate, so the counts say little | High | Accepted. Thumbs down with a reason is still the only direct signal of a wrong figure. The measured quality number is `W-114.23` |
| Counts cover 30 days at most, because ratings go with the chat | Certain, by design | Founder's rule. If a longer trend is wanted, a monthly total with no link to a message can be added later |
| A platform admin tries to find the chat behind a thumbs down | Low | The endpoint returns numbers only; no id leaves the tenant |
| A later change adds a comment box | Medium | Rule 4, and the component test fails on a text input |

## Rollback

Revert the commit. The buttons and the endpoints go. The table stays, unused; its rows are
removed with their chats within 30 days.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `migration` | The table, the policy, the counts function |
| 2 | `core` (`ai`) | Entity, repository, service, controller, `feedback` on the message response, tests |
| 3 | `core` (`tenant`) | The counts endpoint and its test |
| 4 | frontend `shell` | `FeedbackButtons`, the `MessageList` hook-in, the two service calls, tests |
