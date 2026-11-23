# Feature: Confirm and Cancel cards in the AI helper

| Field | Value |
|---|---|
| **Feature ID** | `W-114.9` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/frontend` (`src/shell/assistant`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.5` (the drawer) and `W-114.8` (confirm, cancel and read an action) on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A proposed action shows as a card; Confirm carries it out, Cancel drops it, and the card always shows its true state | 1 |
| Frontend area | `shell` | 1 |

Size **S**.

## Decisions

| Point | Choice | Why |
|---|---|---|
| One card for every action | The card draws `summary` and `fields[{label, value}]` as given. It has no leave-specific code | WFH, approvals and create employee (`W-114.17`, `.18`, `.20`) reuse it with no new component |
| Where the true state comes from | `GET /api/v1/ai/actions/{intentId}` (`W-114.8`), called for each card when a chat is loaded and when the countdown ends | The server decides; the browser clock only draws the countdown |
| Where the card's words come from (Founder 2026-11-23, research review) | Only from that same `GET`. The card is given the `intentId` and nothing else; it also calls the `GET` when it is first drawn under a new answer. It never reads the helper's message text | `W-114.8` rule C1: the server builds `summary` and `fields` from the stored payload. If the card used model text, the model could describe one action and store another |
| Long text (Founder 2026-11-23, research review) | Every field is shown in full. No "…", no "show more", no tooltip. Long text wraps | `W-114.8` rule C2: the user must be able to read all of what they confirm |
| The "done" link | A small map from `actionCode` to a link builder, in one file. `core.leave.apply` → `/me/leave/requests/{leaveRequestId}` | That route exists (`code/frontend/src/shell/routes.js:21`). A later action adds one line; an action with no entry shows no link |
| Confirm is one click | No second "are you sure" dialog | The card is the review step |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | After `W-114.8` the answer carries `proposedAction`, but the drawer shows a message as plain text only. | `W-114-5-chat-drawer.md`, "Out of scope" seam for `W-114.9` |
| 2 | Nothing in the browser can call confirm or cancel. | no call to `/v1/ai/actions` under `code/frontend/src` |

## Scope

**In scope**

- `ActionCard`, drawn under the helper's message that proposed the action.
- Confirm, Cancel, a countdown to expiry, and every end state.
- The true state after a reload.

**Out of scope**

- Editing a proposed value on the card. The user cancels and asks again.
- Any action-specific layout. The native mobile app.

## Flow

```
answer with proposedAction --> MessageList.renderMessage --> <ActionCard intentId=… /> --> GET /api/v1/ai/actions/{intentId}
Confirm --> POST /api/v1/ai/actions/{intentId}/confirm --> Done (link) | Failed (reason) | Expired
Cancel  --> POST /api/v1/ai/actions/{intentId}/cancel  --> Cancelled
chat reloaded, or countdown reaches 0 --> GET /api/v1/ai/actions/{intentId} --> draw that status
```

## Backend changes

None.

**API used**

| Method | Path | From |
|---|---|---|
| `POST` | `/api/v1/ai/actions/{intentId}/confirm` | `W-114.8` |
| `POST` | `/api/v1/ai/actions/{intentId}/cancel` | `W-114.8` |
| `GET` | `/api/v1/ai/actions/{intentId}` → `{ intentId, actionCode, summary, fields, status, expiresAt, result }` | `W-114.8` |

**From the earlier parts.** After a reload the drawer must know which helper messages carry an
action. `GET /api/v1/ai/sessions/{id}/messages` (`W-114.3`) returns `proposedAction: { intentId }`
on each assistant message that proposed one; the link is `core.ai_action_intent.message_id`
(`W-114.3`), set by `W-114.8` when the answer is saved. The card reads everything else from
`GET …/actions/{intentId}`.

## Frontend changes

Files are under `code/frontend/src/shell/assistant/`.

| File | Change |
|---|---|
| `ActionCard.jsx` | **New.** Ant Design `Card`, small. Title = `summary`. Body = the `fields` as a two-column list. Footer by state (table below). `role="group"` with `aria-label` = the summary; a state change is announced through the message list's live region |
| `actionLinks.js` | **New.** `{ 'core.leave.apply': (result) => '/me/leave/requests/' + result.leaveRequestId }` and `linkFor(actionCode, result)`. Later parts add the lines in the table below; this part ships the first one only |
| `assistantService.js` | **Add** `confirmAction(intentId)`, `cancelAction(intentId)`, `getAction(intentId)` |

**The link map, as it grows** — one line per action, each route checked against the route tables

| `actionCode` | Link | Added by | Route |
|---|---|---|---|
| `core.leave.apply` | `/me/leave/requests/{leaveRequestId}` | this part | `code/frontend/src/shell/routes.js:21` |
| `core.leave.withdraw`, `core.leave.cancel` | `/me/leave/requests/{leaveRequestId}` | `W-114.17` | `routes.js:21` |
| `core.wfh.apply`, `core.wfh.withdraw`, `core.wfh.cancel` | `/me/wfh` — the portal's WFH tab; a WFH request has no detail route | `W-114.17` | `routes.js:19` (`/me/:panelId`) |
| `core.approval.decide` | `/approvals/{instanceId}` | `W-114.18` | `code/frontend/src/core/index.js:143` |
| `core.employee.create` | `/employees/{employeeId}` | `W-114.20` | `code/frontend/src/core/index.js:136` |

| File | Change |
|---|---|
| `MessageList.jsx` | `renderMessage` draws an `ActionCard` under a message that has `proposedAction`. This is the seam `W-114.5` left |
| `assistant.css` | Card spacing. Colours from theme tokens; lint refuses a hex colour here (`code/frontend/.eslintrc.cjs:56-64,147-151`) |

**States**

| Status | Card shows |
|---|---|
| `PROPOSED` | Confirm (primary, id `btn-ai-confirm`), Cancel, and "Expires in 14:32" counting down each second from `expiresAt` |
| confirming (local) | Confirm shows a spinner; both buttons disabled |
| `CONFIRMED` | "Done." and, when `linkFor` returns one, a link "Open the leave request". No buttons |
| `CANCELLED` | "Cancelled." No buttons |
| `EXPIRED`, or `410 AI_ACTION_EXPIRED` | "This expired. Ask again to try once more." No buttons |
| `FAILED`, or `409 AI_ACTION_FAILED` | "This could not be done:" and the server's `result.reason`. No buttons |
| `403` on confirm | "You no longer have permission to do this." or, for `AI_DISABLED`, "The AI helper is switched off for your company." No buttons |
| Network error on confirm | The buttons come back with "Could not reach the server. Try again." Confirm is safe to press again: the server carries an intent out once |

`fields` values are shown as text, never as HTML, the same rule as `W-114.5`.

**Card rules (Added 2026-11-23, research review)** — the browser's half of `W-114.8` rules C1–C11.

| # | Rule |
|---|---|
| 1 | `ActionCard` takes one prop, `intentId`. Title, fields, status and expiry all come from `getAction(intentId)`. `MessageList` passes no text from the message into the card. |
| 2 | Until `getAction` answers, the card shows a small loading row and **no Confirm button**. If it fails, the card shows "Could not load this action." with Retry, and still no Confirm. |
| 3 | Every field is drawn, in the order the server sent, with its whole value. No CSS that cuts text (`text-overflow`, line clamp, fixed height). |
| 4 | `confirmAction` and `cancelAction` send no body. |
| 5 | A second click on Confirm, or Confirm in two tabs, is safe: the server returns the first result (`W-114.8` C4) and the card shows "Done." once. |
| 6 | After Cancel the card shows "Cancelled." and nothing else is sent. The helper hears of it on the user's next message, from the server (`W-114.8` C9). |
| 7 | The remaining time comes from the server's `expiresAt` (15 minutes after the proposal). At 0, and on any `410`, the card asks the server and shows "This expired…". |

**Routes added:** none.

## Database changes

None. This spec creates no table.

- [x] No table, no migration, no money value
- [x] No module reference: `shell` calls `core` endpoints only

## Tests

| Type | File | Covers |
|---|---|---|
| Component | `src/shell/assistant/ActionCard.test.jsx` | Draws summary and fields; Confirm calls `confirmAction` once and shows Done with the link; Cancel shows Cancelled; countdown reaching 0 calls `getAction` and shows Expired; `FAILED` shows the reason; `403` messages; a network error restores the buttons; a field value with markup is shown as text |
| Component | `src/shell/assistant/ActionCard.test.jsx` | A made-up `actionCode` with three fields draws the same card and no link |
| Component | `src/shell/assistant/MessageList.test.jsx` (extend) | A loaded chat with one confirmed and one expired action draws each in its true state from `getAction` |
| Unit | `src/shell/assistant/actionLinks.test.js` | Leave link built from `leaveRequestId`; unknown code returns nothing |
| Component | `ActionCard.test.jsx` (added 2026-11-23) | Rule 1: the message text says "2 days from 4 Dec" and `getAction` returns 5 days from 1 Dec — the card shows 5 days from 1 Dec and nothing from the message. Rule 2: no Confirm button before `getAction` answers, nor when it fails. Rule 3: a 500-character reason is in the document whole. Rule 4: `confirmAction` and `cancelAction` are called with the id only. Rule 5: two fast clicks call `confirmAction` once |
| Component | `MessageList.test.jsx` (extend, added 2026-11-23) | A new answer with `proposedAction` renders `ActionCard` with `intentId` as its only prop |

## Verification

```bash
cd code/frontend && npx vitest run src/shell/assistant && npm run lint && npm run build
```

| Check | Expected | Result |
|---|---|---|
| The three commands | All pass; lint reports 0 warnings | not run |
| Ask "apply casual leave for 2026-12-04" | A card: Leave type, From, To, Days, Reason; Confirm, Cancel, a countdown | not run |
| Press Confirm | "Done." and a link that opens the new leave request at `/me/leave/requests/{id}` | not run |
| Ask again, press Cancel | "Cancelled."; no leave request created | not run |
| Ask again, wait 15 minutes | "This expired…"; Confirm is gone | not run |
| Reload the page, open the same chat | The three cards show Done, Cancelled and Expired again | not run |
| Ask for leave the policy refuses at confirm time | "This could not be done:" and the leave service's reason | not run |
| Keyboard only | Confirm and Cancel are reachable and work with Enter | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The user's clock is wrong, so the countdown is off | Low | At 0 the card asks the server; the server alone decides expiry |
| A chat with many cards makes one `getAction` call per card on load | Low | Changed 2026-11-23: every card needs its one call, because the card's words come only from the server. A 30-day chat has few cards; a finished card is not asked again |
| The helper's sentence and the card disagree (added 2026-11-23) | Low | The card is right by construction (rule 1). The user confirms what the card shows, and that is what runs |
| A very long reason makes a tall card (added 2026-11-23) | Low | Accepted. The reason is 500 characters at most (`W-114.8`); showing all of it is the point |
| A later action needs a field type the plain list cannot show | Medium | The card stays plain; that action's spec extends `fields`, not this component |

## Rollback

Revert the commit. A proposed action shows as plain text again and cannot be confirmed from the
browser; nothing is lost on the server.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | frontend `shell` | Three service calls, `actionLinks.js`, tests |
| 2 | frontend `shell` | `ActionCard`, the `MessageList` hook-in, tests |
