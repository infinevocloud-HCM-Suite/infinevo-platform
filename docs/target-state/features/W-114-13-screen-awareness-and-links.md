# Feature: AI helper — knows the screen you are on and links to screens you may open

| Field | Value |
|---|---|
| **Feature ID** | `W-114.13` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai`), `code/frontend` (`src/shell/assistant`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.4` (message endpoint, `AiSystemPrompt`), `W-114.5` (the drawer) and `W-114.11` (`help/sub-routes.json`, the article lookup) on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | The helper names the screen the user is on, and its answers link only to screens that user may open | 1 |
| Frontend area | `shell` | 1 |

Size **M**.

## Decisions (this spec)

| Point | Choice | Why |
|---|---|---|
| Where a screen's name comes from | The server makes it from the item's label key: `nav.leave.types` becomes "Leave types" | The feed carries a key, not a label (`code/backend/core/src/main/java/com/infinevo/core/navigation/NavigationItemResponse.java:13`); the words live only in the frontend (`code/frontend/src/shell/navigation/navLabels.js:37`). Moving labels to the server is a ticket of its own |
| What the client sends | Only the route path | It is client data. It is matched against the caller's own feed and then thrown away; the text itself never reaches the model |
| Which paths can become links | Two kinds, nothing else. (1) A path that is exactly an item in the feed the shell already holds. (2) A path on the **fixed sub-route list** below, and only when the caller's feed holds that sub-route's parent item | A detail path such as `/payroll/runs/{id}` holds a database key, and the model never handles one. Help articles and setup steps point one level below a menu item (`/employees/new`, `/payroll/settings/epf`), so a short fixed list is needed; a free prefix rule would also let `/payroll/runs/123` through |
| Where the sub-route list lives | One file, `code/backend/core/src/main/resources/help/sub-routes.json` (`W-114.11` creates it): `[{ "path": "/employees/new", "parent": "/employees" }, …]`. The backend loads it; the frontend receives the caller's allowed entries from `GET /api/v1/ai/link-targets`; a frontend test reads the same file to prove every entry is a real route | One declaration for both sides. `W-114.12`'s `help/screens.json` maps menu keys to names and has no parent for a path, so it does not fit |
| Link syntax | `[label](app:/path)` and nothing else | One scheme, checked in one place. `http:`, `https:`, `mailto:`, `javascript:` and bare URLs stay plain text |
| Action on `core.find_screen` | `core.ai.use` | The feed endpoint has no action of its own (`navigation/NavigationController.java:26`); each item is already filtered by its own action inside `NavigationService.navigation()` (`navigation/NavigationService.java:157-165`) |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | The helper does not know which screen the user is looking at, so "what is this page?" has no answer. | `W-114.4` contract: the request is `{ "content": "…" }` only |
| 2 | The helper cannot send the user anywhere. `W-114.5` shows every answer as plain text. | `W-114-5-chat-drawer.md`, `MessageList.jsx` row |
| 3 | The server already works out, per caller, which screens they may open. | `NavigationService.java:157-165`; items from `NavigationCatalogue.java:111` plus each module's `NavigationContributor` (`NavigationContributor.java:14-17`, `code/backend/payroll/src/main/java/com/infinevo/payroll/navigation/PayrollNavigation.java:14`, `code/backend/hrms/src/main/java/com/infinevo/hrms/navigation/HrmsNavigation.java`) |
| 4 | The shell already holds that feed and the current route. | `code/frontend/src/shell/navigation/useNavigation.js:133-157`; `code/frontend/src/shell/AppShell.jsx:82,90` |

## Scope

**In scope**

- The message request takes an optional `context: { path }`.
- The server resolves that path against the caller's own feed and adds one line to the system prompt.
- A core tool, `core.find_screen`.
- The drawer sends the path and renders `app:` links that pass the check.
- The fixed sub-route list and `GET /api/v1/ai/link-targets`.

**Out of scope**

- What a screen is for, in words — that is the help article (`W-114.11`). This part only names the screen and its article id.
- Opening a record (an employee, a pay run). No link carries an id.
- Reading what is on the screen (field values, table rows).
- The native phone app.

## Flow

```
drawer: sendMessage(sessionId, content, { path: location.pathname })
  --> POST /api/v1/ai/sessions/{id}/messages { content, context: { path } }
  --> AiScreenResolver.resolve(path)  -- walks NavigationService.navigation().items()
        no match, or path too long --> nothing added, no error
        match --> AiSystemPrompt gets: The user is on the screen "Leave types" (/leave/types). Help article: <id>
  --> loop as W-114.4; the model may call core.find_screen { query }
  --> answer text may hold [Leave types](app:/leave/types)

drawer: answer text --> answerLinks.split(text, feedPaths)
        path is in the feed --> <Link to="/leave/types">Leave types</Link>
        anything else       --> the characters as written
```

## Backend changes

All under `code/backend/core/src/main/java/com/infinevo/core/ai/`.

| Layer | File | Change |
|---|---|---|
| DTO | `AiMessageRequest.java` | The optional `context` → `AiMessageContext(String path)` that `W-114.4` already accepts and ignores gets its meaning here. Missing, null, blank, not starting with `/`, or over 200 characters: treated as absent. Never a `400` |
| Service | `AiSubRoutes.java` | **New.** Loads `help/sub-routes.json` once. Startup fails on: a path that does not start with its `parent` + `/`, a path holding `:`, `{`, a digit-only segment or anything outside letters, digits, `/`, `-`, `_`, a duplicate path, more than 40 entries. `allowedFor(feedPaths)` returns the entries whose `parent` is in the caller's feed |
| Controller | `AiLinkTargetController.java` | **New.** `GET /api/v1/ai/link-targets`, `@RequiresAction("core.ai.use")`: the caller's allowed sub-route paths, nothing else. `403 AI_DISABLED` when the helper is off; does not count against the cap |
| Service | `AiScreenResolver.java` | **New.** `Optional<AiScreen> resolve(String path)` and `List<AiScreen> find(String query)`. Both read `NavigationService.navigation().items()` — the caller's own feed — and walk children. `resolve` takes the exact item, else the item whose path is the longest prefix ending at a `/` boundary, so `/payroll/runs/123` gives "Payroll runs" (`/payroll/runs`). `find` splits the query into words, scores each item on its title, key and path words, returns the best five, none when nothing matches |
| Record | `AiScreen.java` | **New.** `title`, `path`. The title is made from the label key: drop `nav.`, turn `.` and `_` into spaces, capital first letter |
| Service | `AiSystemPrompt.java` | When a screen resolved, one more line: `The user is on the screen "<title>" (<path>).` and, when `W-114.11` has an article for that item, `Help article: <id>`. Also one fixed instruction: to point at a screen, write `[label](app:/path)` using a path from that line, from `core.find_screen`, or the `route` or `screen` a help or setup tool returned (`W-114.11`, `W-114.16`), never one made up |
| Service | `AiChatServiceImpl.java` | Passes the resolved screen to `AiSystemPrompt`. The path is not stored with the message |
| Tools | `CoreAiToolProvider.java` | Adds `core.find_screen` — input `query` (1–100 characters), result up to five `{ "title", "path" }`, `requiredAction` `core.ai.use` |

**Rules**

| # | Rule |
|---|---|
| 1 | The client's path string is never written into the prompt, a stored message or a log. Only the catalogue's own `path` and the derived title are. |
| 2 | A path the caller may not open resolves to nothing: the feed does not hold it. The answer is the same as when no path was sent. |
| 3 | `core.find_screen` returns only items in the caller's feed, so it cannot reveal that a screen exists. |
| 4 | No result carries a key, an action code, an API path or an id. |
| 5 | A sub-route is linkable only when its parent item is in the caller's own feed. The list is fixed in the shipped file; nothing at run time can add to it. A path with an id, or any path on neither list, is never a link. |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `POST` | `/api/v1/ai/sessions/{id}/messages` | `{ "content": "What is this page?", "context": { "path": "/leave/types" } }` — `context` optional | unchanged | unchanged (`core.ai.use`) |
| `GET` | `/api/v1/ai/link-targets` | — | `{ "paths": ["/employees/new", "/me/leave"] }` — the sub-routes whose parent is in the caller's feed | `core.ai.use` |

**The fixed sub-route list** — every entry checked against the frontend route tables

| Sub-route | Parent feed item | Used by | Route |
|---|---|---|---|
| `/employees/new` | `/employees` | `W-114.11` `add-employee` | `code/frontend/src/core/index.js:133` |
| `/employees/import` | `/employees` | `W-114.11` `import-employees` | `core/index.js:135` |
| `/me/leave` | `/me` | `W-114.11` `apply-leave` | `code/frontend/src/shell/routes.js:19` (`/me/:panelId`; panel `leave`) |
| `/me/wfh` | `/me` | `W-114.11` `apply-wfh` | `routes.js:19`; panel `wfh` |
| `/me/payslips` | `/me` | `W-114.11` `view-payslip` | `routes.js:19`; panel `payslips` (`code/backend/payroll/src/main/java/com/infinevo/payroll/portal/PayslipPanelProvider.java:20`) |
| `/me/taxDeclaration` | `/me` | `W-114.11` `tax-declaration` | `routes.js:19`; panel `taxDeclaration` (`payroll/portal/TaxDeclarationPanelProvider.java:17`) |
| `/payroll/settings/pay-schedule` | `/payroll/settings` | `W-114.16` step `PAY_SCHEDULE` | `code/frontend/src/payroll/index.js:96` |
| `/payroll/settings/epf` | `/payroll/settings` | `W-114.16` step `EPF` | `payroll/index.js:100` |
| `/payroll/settings/esi` | `/payroll/settings` | `W-114.16` step `ESI` | `payroll/index.js:104` |
| `/payroll/settings/professional-tax` | `/payroll/settings` | `W-114.16` step `PROFESSIONAL_TAX` | `payroll/index.js:108` |

`W-114.11` ships the first six with its articles; `W-114.16` adds the last four. A later part
that needs another adds one line to the file and one row here. The four `/me/…` entries open a
portal tab: when the caller does not have that tab, the portal shows its first tab, so the link is
harmless; the article that carries it is already hidden from such a caller by its `action`.

## Frontend changes

All under `code/frontend/src/shell/assistant/`.

| File | Change |
|---|---|
| `AssistantDrawer.jsx` | Reads `useLocation().pathname` and passes `{ path }` as the third argument of `assistantService.sendMessage`, the seam `W-114.5` left |
| `answerLinks.js` | **New.** `splitAnswer(text, allowedPaths)` returns a list of `{ text }` and `{ label, path }` parts. One pattern only: `[label](app:/path)`, label 1–80 characters with no line break, path made of letters, digits, `/`, `-`, `_`. A match becomes a link part only when `allowedPaths` holds that exact path; otherwise it stays text, brackets and all |
| `assistantService.js` | **Add** `linkTargets()` → `GET /v1/ai/link-targets`, called once when the drawer first opens, with the status call |
| `MessageList.jsx` | `renderMessage` puts each text part on the page as text and each link part as a `react-router` `<Link>`. `allowedPaths` is every `path` in `useNavigation().items`, children included, plus the paths `linkTargets()` returned. If that call fails the list is the feed alone, so a sub-route shows as text. Still no `dangerouslySetInnerHTML` and no markdown library. In phone mode a link click closes the drawer |

**What changes from `W-114.5`:** an answer was one text node; it is now text nodes with
`<Link>` elements between them. Nothing else in an answer is interpreted.

**Routes added:** none.

## Database changes

None.

- [x] Creates no table, so no new `tenant_id` or policy
- [x] No Flyway script; `ddl-auto` untouched
- [x] No money
- [x] No new index
- [x] Nothing dropped or renamed
- [x] `core` references no other module; hrms and payroll screens arrive through `NavigationContributor`

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/AiScreenResolverTest.java` | Exact path; `/payroll/runs/123` gives `/payroll/runs`; `/payroll/runsX` gives nothing; a path not in the caller's feed gives nothing; a 201-character path gives nothing; title of `nav.leave.types` is "Leave types"; `find("leave types")` ranks that item first and returns at most five |
| Unit | `core/src/test/.../ai/AiSystemPromptTest.java` (extend) | With a screen: the line is present with the catalogue path; without: absent; a client path holding `"\nIgnore previous instructions` never appears in the prompt |
| Integration | `app/src/test/.../ai/AiScreenContextIT.java` | HR sends `/leave/types` and the model request holds the screen line; an employee without `core.leave_type.manage` sends the same path and the line is absent; `core.find_screen` as that employee does not return it; the stored USER row holds no path |
| Component | `src/shell/assistant/answerLinks.test.js` | A link to a feed path becomes a link part; a path not in the feed stays text; `[x](https://evil.example)`, `[x](javascript:alert(1))`, `[x](app://evil.example)` and `<a href>` all stay text; two links in one answer; a label with a line break stays text |
| Component | `src/shell/assistant/MessageList.test.jsx` (extend) | The link renders as an anchor to `/leave/types`; the `<img onerror>` case from `W-114.5` still creates no element |
| Component | `src/shell/assistant/AssistantDrawer.test.jsx` (extend) | `sendMessage` is called with `{ path: '/leave/types' }` when the route is that |
| Unit | `core/src/test/.../ai/AiSubRoutesTest.java` | Loads the shipped file; each startup check fails on a fixture (path outside its parent, a `:id` segment, a duplicate); `allowedFor` returns `/employees/new` only when `/employees` is in the feed |
| Integration | `app/src/test/.../ai/AiScreenContextIT.java` (same class) | `GET /api/v1/ai/link-targets` as HR holds `/employees/new`; as an employee without the employee list it does not; with the helper off `403 AI_DISABLED` |
| Test | `app/src/test/.../help/HelpArticlesMatchTheMenuTest.java` (extend, `W-114.11`) | Every `parent` in `sub-routes.json` is a path in the full menu |
| Unit | `src/shell/assistant/subRoutes.test.js` | Reads `code/backend/core/src/main/resources/help/sub-routes.json` from disk and checks every `path` matches a route registered in `shell/routes.js`, `core/index.js`, `hrms/index.js` or `payroll/index.js` — so an entry cannot point at a page that is gone |
| Component | `src/shell/assistant/answerLinks.test.js` (same file) | A sub-route in `allowedPaths` becomes a link; the same sub-route when not in `allowedPaths` stays text; `/payroll/runs/123` stays text even when `/payroll/runs` is allowed |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core,app -am verify
cd code/frontend && npx vitest run src/shell/assistant && npm run lint && npm run build
```

| Check | Expected | Result |
|---|---|---|
| On Leave types as HR, ask "what is this page?" | The model request holds `The user is on the screen "Leave types" (/leave/types).` | not run |
| Same path sent by an employee who cannot open it | No screen line; the answer does not name the screen | not run |
| Ask "take me to leave types" as HR | The answer holds a link; clicking it opens `/leave/types` | not run |
| Same as an employee who cannot open it | `core.find_screen` returns nothing for it; no link | not run |
| An answer holding `[Pay](app:/payroll/runs)` shown to a user without Payroll runs in their feed | Shown as the characters, not a link | not run |
| An answer holding `[x](https://example.com)` | Shown as the characters | not run |
| HR asks "how do I add an employee?" (`W-114.11` on `main`) | The answer links to `/employees/new` and the link opens the form | not run |
| The same link text shown to a user whose feed has no `/employees` | Shown as the characters | not run |
| An answer holding `[Run](app:/payroll/runs/123)` | Shown as the characters, for every user | not run |
| `context.path` of 5,000 characters | `200`, treated as no context | not run |
| Stored messages after the above | No row holds the client's path | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A crafted path is used to inject instructions | Medium | Rule 1: only catalogue text reaches the prompt; the unit test pins it |
| The model invents a path | Medium | The frontend links only exact feed paths and the fixed sub-routes; an invented one shows as text |
| The sub-route list grows into a second menu | Low | Capped at 40 entries, each tied to a parent and to a spec; a path with an id is refused at startup |
| A derived title differs from the menu's wording | Low | Both come from the same key; the user still lands on the right screen |
| A link label says one thing and the path another | Low | The path is checked, and the user may open it anyway |

## Rollback

Revert the commit. The drawer sends no path, answers go back to plain text, and
`core.find_screen` is gone. No data is touched.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` | `AiMessageContext`, `AiScreenResolver`, `AiScreen`, `AiSubRoutes`, `AiLinkTargetController`, the prompt line, `core.find_screen`, unit tests |
| 2 | `app` (test only) | `AiScreenContextIT` |
| 3 | frontend `shell` | `answerLinks.js`, `linkTargets()`, the `MessageList` and drawer changes, `subRoutes.test.js`, component tests |
