# Feature: AI helper chat drawer

| Field | Value |
|---|---|
| **Feature ID** | `W-114.5` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/frontend` (`src/shell/assistant`, one mount in `src/shell/AppShell.jsx`) |
| **Related gaps** | none in `GAP_INVENTORY.md`; the frozen system has no AI helper |
| **Status** | Ready |
| **Blocked by** | `W-114.1` (status call), `W-114.3` (chat sessions), `W-114.4` (send a message) on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A user of a customer with the helper on opens the drawer, asks a question and reads the answer | 1 |
| Frontend area | `shell` | 1 |

Size **M**. This is the first part a person can try in the browser.

## Decisions

| Point | Choice | Why |
|---|---|---|
| Answers are plain text | The answer is put on the page as text, never as HTML. Line breaks are kept; nothing else is interpreted | The text comes from an AI model. Treating it as HTML would let it inject markup |
| No streaming | The drawer waits for the full answer and shows a "thinking" row meanwhile | Founder decision in the split: synchronous replies only |
| Phone layout | In. The drawer opens full width when the shell is in phone mode | The shell already knows it is a phone (`AppShell.jsx:118-119`) |
| Native mobile app | Out. `code/frontend/src/mobile/` does not exist on `main`; it is on `feature/krushna-specs` (`W-98`) | Not on `main` |
| State | Local React state inside the drawer; no Redux slice | Nothing else reads the chat |
| Past chats | Listed by date and first question, newest first, last 30 days | `W-114.10` deletes older ones |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | The shell has a side menu, a header and the page content. There is no place to ask a question. | `code/frontend/src/shell/AppShell.jsx:209-250` |
| 2 | The API client turns every error into one shape with `code`, `message` and `status`, but knows no AI codes. | `code/frontend/src/shared/api/client.js:128-140` |

## Scope

**In scope**

- A floating "Ask" button, shown only when the status call says the helper is on.
- A slide-over drawer: message list, input, New chat, Past chats, Delete chat.
- Loading, empty and error states, including "switched off" and "monthly limit reached".
- The usage line "N of M this month".
- Keyboard and screen-reader basics.

**Out of scope — the seams are left, nothing is built**

| Later part | Seam left here |
|---|---|
| `W-114.9` confirm cards | `MessageList` renders a message through one `renderMessage(message)` function; a message that carries `proposedAction` is shown as plain text for now |
| `W-114.13` screen awareness | `assistantService.sendMessage(sessionId, content, context)` takes a third argument that is not sent yet |
| `W-114.22` starter chips | The empty state is its own component, `AssistantEmptyState`, with one line of text |

Also out: the native mobile app, voice input, file upload, copy or export of a chat.

## Flow

```
AppShell mounts <AssistantLauncher/>
   --> GET /api/v1/ai/status --> enabled false or error: render nothing
                             --> enabled true: floating button

Button --> <AssistantDrawer/> --> POST /api/v1/ai/sessions (on the first question)
       user types, Enter  --> POST /api/v1/ai/sessions/{id}/messages { content }
                          --> answer appended; usage line refreshed from GET /api/v1/ai/status
       Past chats         --> GET /api/v1/ai/sessions --> GET /api/v1/ai/sessions/{id}/messages
       Delete chat        --> DELETE /api/v1/ai/sessions/{id}
```

## Backend changes

None.

**API used** (all built by earlier parts)

| Method | Path | From |
|---|---|---|
| `GET` | `/api/v1/ai/status` → `{ enabled, monthlyRequestCap, usedThisMonth, month }` | `W-114.1` |
| `POST`, `GET` | `/api/v1/ai/sessions` | `W-114.3` |
| `GET` | `/api/v1/ai/sessions/{id}/messages` | `W-114.3` |
| `DELETE` | `/api/v1/ai/sessions/{id}` | `W-114.3` |
| `POST` | `/api/v1/ai/sessions/{id}/messages` with `{ "content": "…" }` → `{ message, proposedAction? }` | `W-114.4` |

## Frontend changes

All new files are under `code/frontend/src/shell/assistant/`.

| File | Change |
|---|---|
| `assistantService.js` | **New.** `getStatus`, `createSession`, `listSessions`, `listMessages`, `deleteSession`, `sendMessage(sessionId, content, context)`. Uses `apiClient` (`shared/api/client.js:65`) |
| `AssistantLauncher.jsx` | **New.** Calls `getStatus` once when the shell mounts. Renders nothing when off or when the call fails. Otherwise an Ant Design `FloatButton`, bottom right, `aria-label="Ask the AI helper"`, id `btn-ai-helper`. Opens the drawer |
| `AssistantDrawer.jsx` | **New.** Ant Design `Drawer`, right side, 420 px wide; full width when `phone` is true. Header: title "AI helper", New chat, Past chats, close. Footer: the input and the usage line. Focus goes to the input on open and back to the button on close; Escape closes |
| `MessageList.jsx` | **New.** User and helper rows. Text set as text content with `white-space: pre-wrap`; no `dangerouslySetInnerHTML`, no markdown library. `role="log"` and `aria-live="polite"` so a screen reader reads a new answer. Scrolls to the newest row |
| `AssistantInput.jsx` | **New.** Text area, 2000 characters at most, Enter sends, Shift+Enter adds a line, Send button. Disabled while an answer is pending |
| `SessionList.jsx` | **New.** Past chats with a delete action behind a confirm |
| `AssistantEmptyState.jsx` | **New.** "Ask a question about your leave, attendance or payslip." |
| `assistant.css` | **New.** Layout only. Colours come from theme tokens |
| `../AppShell.jsx` | Mount `<AssistantLauncher phone={phone} />` once inside `ShellBoundary`, after the inner `Layout` (`:209-250`). No other change |

**States**

| State | What the user sees |
|---|---|
| Helper off, or the status call fails | No button |
| Drawer open, no messages | The empty state |
| Answer pending | The question, then a "Thinking…" row; input disabled |
| `403 AI_DISABLED` while chatting | "The AI helper is switched off for your company." The input is disabled and the button goes at the next load |
| `429 AI_CAP_REACHED` | "Your company has used its AI helper limit for this month." Input disabled; past chats can still be read |
| Any other error | "The helper could not answer. Try again." with a Retry that resends the same question. The question stays in the list |
| Past chats empty | "No earlier chats." |
| Usage line | "N of M this month", from the status call, refreshed after each answer |

**Colours.** Lint refuses a hex colour in `shell` files (`code/frontend/.eslintrc.cjs:56-64,147-151`). Use
`antdTheme.useToken()` as `AppShell.jsx` does (`:3`).

**Routes added:** none.

## Database changes

None. This spec creates no table.

- [x] No table, so no `tenant_id` and no policy
- [x] No migration
- [x] No money value is computed or stored
- [x] No module reference: `shell` calls `core` endpoints only

## Tests

| Type | File | Covers |
|---|---|---|
| Component | `src/shell/assistant/AssistantLauncher.test.jsx` | No button when `enabled` is false; no button when the call fails; button when true |
| Component | `src/shell/assistant/AssistantDrawer.test.jsx` | First question creates a session then sends; the answer shows; input is disabled while pending; Escape closes; `403` and `429` messages; Retry resends |
| Component | `src/shell/assistant/MessageList.test.jsx` | An answer containing `<img src=x onerror=…>` is shown as text and creates no element; line breaks kept |
| Component | `src/shell/assistant/SessionList.test.jsx` | Lists chats; delete asks first, then calls the service |
| Component | `src/shell/AppShell.test.jsx` (extend) | The launcher is mounted once; the shell renders unchanged when the status call fails |

## Verification

```bash
cd code/frontend && npx vitest run src/shell && npm run lint && npm run build
```

| Check | Expected | Result |
|---|---|---|
| The three commands | All pass; lint reports 0 warnings | not run |
| Sign in to a customer with the helper off | No floating button anywhere | not run |
| Platform admin switches it on with cap 500; reload | Button bottom right on every screen | not run |
| Ask "Which holidays are coming up?" | "Thinking…", then an answer as plain text; usage line reads "1 of 500 this month" | not run |
| Close and reopen the drawer | The same chat is shown | not run |
| Past chats, open an older one | Its messages load | not run |
| Delete a chat | Confirm, then it leaves the list | not run |
| Cap set to the current count, ask again | "Your company has used its AI helper limit for this month." | not run |
| Browser at 390 px wide | Drawer fills the width; input stays above the on-screen keyboard | not run |
| Tab through with the keyboard only | Button, input, Send, New chat, Past chats and close are all reachable | not run |

Until `W-114.2` is deployed, the answer in these checks comes from the stand-in model
`W-114.4` uses in tests and on a developer machine.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The floating button covers a page's own bottom-right button | Medium | Ant Design `FloatButton` sits above page content; check the Pay run and Leave request screens by eye in the verification pass |
| A slow model makes the drawer look stuck | Medium | The "Thinking…" row; the request uses the API client's normal timeout and then shows Retry |
| The status call adds one request to every page load | Certain, small | Called once per shell mount, not per route change |

## Rollback

Revert the commit. The button and the drawer go; nothing else changes.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | frontend `shell` | `assistantService.js` and its tests |
| 2 | frontend `shell` | Launcher, drawer, message list, input, session list, empty state, tests |
| 3 | frontend `shell` | The one mount in `AppShell.jsx` and the extended shell test |
