# Feature: AI helper help library — how-to answers from articles we ship

| Field | Value |
|---|---|
| **Feature ID** | `W-114.11` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai/help`, new `src/main/resources/help/`), one test in `code/backend/app` |
| **Related gaps** | none in `GAP_INVENTORY.md`; the frozen system has no in-product help |
| **Status** | Ready |
| **Blocked by** | `W-114.4` on `main` (`AiToolRegistry`, `AiSystemPrompt`, the message endpoint) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` (plus one test class in `app`, test only) | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A user asks "how do I …" and gets steps taken from a shipped article they are allowed to see, or "I do not have a guide for that" | 1 |
| Frontend area | none | 1 |

Size **M**. It passes every axis, but it is two kinds of work: a small mechanism and
30 articles. **If the build runs long, cut here:** ship the mechanism with the first 8
articles (marked ★ in the list), and write the other 22 as `W-114.11a` — same folder, no
code change.

## Decisions

| Date | Who | Decision |
|---|---|---|
| 2026-11-23 | founder | How-to answers come **only** from articles we write and ship with the product, plus the user's real permissions. The helper does not describe a screen or a step that is not in an article |
| 2026-11-23 | founder | The articles are drafted by Claude in this ticket, from the feature specs and the real screens. The founder reviews them after they ship; a correction is a file edit |
| 2026-11-23 | founder | (Research review) The model finds an article from a **catalogue in the system prompt** — id, title and one-line summary of each article the caller may see — and reads it by id. The keyword search stays as a fallback. The whole library is not put in the prompt. No embeddings |
| 2026-11-23 | founder | (Research review) The tool that reads an article is named `core.read_help_article` (it was `core.get_help_article` in the first draft) |

Decided in this spec, for the founder to overrule:

| Point | Choice | Why |
|---|---|---|
| Where articles live | Markdown files in the `core` jar, `src/main/resources/help/articles/` | Versioned with the code they describe; no table, no migration, no admin screen |
| How they are searched | A keyword score over the loaded articles, in memory. **Changed 2026-11-23: this is now the fallback; the catalogue comes first — see "How an article is found"** | About 30 short articles. A vector store or a search service is not worth its upkeep at this size |
| Who may see an article | Whoever may open the article's screen: the screen must be in the caller's navigation feed | The feed already applies the customer's modules and the user's actions, so an article can never describe a screen the user cannot reach |
| Front matter | Plain `key: value` lines, parsed by hand | No YAML library is declared in `core/pom.xml` |
| Customer-written notes | Not in this ticket | Founder chose "articles we write and ship" |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | The product has no help content. | no `help` folder anywhere under `code/`; `code/backend/core/src/main/resources/` does not exist |
| 2 | The helper built in `W-114.4` answers only from data tools. Asked "how do I add a leave type", it has nothing to answer from and must say "I can't see that". | `W-114-4-model-gateway-masking-and-core-answers.md`, rule 7 and the Core tools table |
| 3 | The one list of screens a user may open is the navigation feed. | `code/backend/core/src/main/java/com/infinevo/core/navigation/NavigationService.java:157` |

## Scope

**In scope**

- The article file format and a loader that reads every article at startup.
- Two tools, `core.read_help_article` and the fallback `core.search_help`.
- The catalogue block and one new rule in the system prompt (changed 2026-11-23).
- The first 30 articles.
- A test that fails when an article points at a screen that is not in the menu.

**Out of scope**

- Role duty guides and "what can I do" — `W-114.12` (same folder, `help/roles/`).
- Knowing which screen the user is on, and turning `app:` links into real links — `W-114.13`.
- A screen to edit articles; customer-written notes; translation; screenshots.

## Flow

```
startup --> HelpLibrary loads help/articles/*.md --> broken file: the application does not start

every message (changed 2026-11-23):
  --> AiSystemPrompt adds the catalogue: one line per article the caller may see  (id | title | summary)

user: "how do I add a leave type?"
  --> model picks `leave-types` from the catalogue
  --> model calls core.read_help_article { id } --> access checked again --> body + screen path
  --> answer: the steps from the article, with [Leave types](app:/leave/types)

fallback, when no catalogue line fits the user's words:
  --> model calls core.search_help { query } --> top 5 of the same visible articles (id, title, summary)

no catalogue line fits and search returns nothing --> "I do not have a guide for that."
```

## The article format

One file per article, `<id>.md`, UTF-8.

```
---
id: leave-types
title: Create a leave type
summary: Add a leave type such as Casual or Sick leave and set its yearly days.
roles: hr, tenant-admin
screen: /leave/types
keywords: leave type, casual, sick, earned, entitlement, carry forward
---
1. Open Leave, then Leave types.
2. …
```

| Field | Required | Rule |
|---|---|---|
| `id` | yes | Lower case, digits and `-`; equals the file name; unique |
| `title` | yes | At most 80 characters |
| `summary` | yes | One line, at most 160 characters. This is what the search returns |
| `roles` | yes | One or more of the seven system role codes (`code/backend/migration/src/main/resources/db/migration/core/V022__role_action.sql:74-80`). Used to rank, **never to decide access** |
| `screen` | yes | The `path` of an item in the navigation catalogue, for example `/leave/types`. Decides who may see the article |
| `route` | no | A deeper page under `screen`, for example `/employees/new`. Must be an entry of `help/sub-routes.json` whose `parent` is this article's `screen` — the fixed list `W-114.13` links from |
| `action` | no | One more action the reader must hold, for an article about one tab of a shared screen (a portal tab, the Salary tab) |
| `keywords` | yes | Comma-separated words a user might type |
| body | yes | Plain numbered steps. At most 6,000 characters, so it fits the 8,000-character tool result limit of `W-114.4` |

**Writing rules** (checked by review, not by code): name buttons and fields exactly as the
screen shows them; one task per article; say who can do it in the first line; no step for a
screen that is not `screen` or `route`.

## Who may see an article

| # | Check | Source |
|---|---|---|
| 1 | The article's `screen` is the path of an item in the caller's navigation feed | `NavigationService.navigation()` (`NavigationService.java:157`). The feed has already dropped items whose module the customer does not hold and items whose action the caller lacks |
| 2 | If `action` is set, the caller holds it | `PermissionService.holds` (`code/backend/shared/src/main/java/com/infinevo/shared/authz/PermissionService.java:102`) |

An article that fails either check is not returned by search, and `core.read_help_article`
answers "not found" for it — the same answer as for an id that does not exist.

## How an article is found (Changed 2026-11-23, research review)

| Step | Rule |
|---|---|
| 1. The catalogue | `HelpLibrary.catalogueFor(caller)` returns the articles the caller may see (the two checks above), sorted by `id`. `AiSystemPrompt` adds them under the heading "Help articles you can read", one line each: `id \| title \| summary` |
| 2. Size | A line is at most about 250 characters (`id`, an 80-character title, a 160-character summary), near 50 tokens. Thirty lines are about 1,500 tokens. A caller sees fewer: an employee sees about 8 |
| 3. Reading | The model calls `core.read_help_article { id }`. Access is **checked again** there; an id that is not in the caller's catalogue answers "not found" |
| 4. Fallback | `core.search_help` stays, for a question whose words match no title or summary. It searches the same visible articles, bodies and keywords included |
| 5. Nothing fits | The model says "I do not have a guide for that." |
| 6. A cap | If the visible catalogue passes 60 articles, only the 60 that best fit the caller's roles are listed (the `roles` field), and the prompt says "more articles exist — use `core.search_help`". Not reached at 30 |

**What the catalogue never holds:** an article the caller may not see, an article body, or
anything typed by a user. Titles and summaries are our own shipped text.

**Why the whole library is not put in the prompt**

| Option | Tokens added to every question | Result |
|---|---|---|
| Catalogue (chosen) | about 1,500, plus one article of at most 1,500 when a how-to is asked | Small and steady |
| Every article body | 20,000 to 40,000 (30 articles of up to 6,000 characters) | The deployment allows 100,000 tokens a minute for all customers together (`W-114.2`). That is 2 to 5 questions a minute in total. It would also multiply the cost of every question, including "how many leave days do I have", by ten or more |

**Embeddings stay out.** One more line for later: if customers upload their own documents,
the path is `pgvector` in our own PostgreSQL — a table with `tenant_id` and row-level
security, exact search — in a future ticket. Not a separate vector store, not now.

## The search rule

The fallback of step 4.

| Step | Rule |
|---|---|
| Words | Lower-case the query, split on anything that is not a letter or digit, drop words shorter than 3 characters and a fixed list of 40 stop words ("how", "the", "can", …) |
| Score per query word | +5 if a keyword contains it; +3 if the title contains it; +1 if the body contains it |
| Role bonus | +2 once, if one of the caller's role codes is in the article's `roles` (`PermissionReadServiceImpl.roleCodesOf`, `code/backend/core/src/main/java/com/infinevo/core/authz/PermissionReadServiceImpl.java:83`) |
| Result | Articles scoring above 0, highest first, ties by `id`; at most 5 |

## What is checked, and when

| Check | When | Why there |
|---|---|---|
| Front matter present and every required field filled; `id` equals the file name; duplicate `id`; unknown role code; `route` is not in `help/sub-routes.json` with `parent` = `screen`; body over 6,000 characters | **Startup** — `HelpLibrary` throws and the application does not start | Needs only the files. A broken article is caught by the first test that starts the context |
| `screen` is the path of an item in the full menu (core, hrms and payroll together) | **Test** in `app`, where all three modules are visible | `core` cannot see `HrmsNavigation` or `PayrollNavigation`. The full menu is built the way `SeededRoleHomePathTest` builds it (`code/backend/app/src/test/java/com/infinevo/app/navigation/SeededRoleHomePathTest.java:245`) |
| `action`, when set, is an action some menu item or portal panel declares | **Test** in `app`, same class | `reference.action` is a database table; reading it at startup would stop the app for a typo in a help file |
| `route` exists in the frontend | **Frontend test, from `W-114.13`** (`subRoutes.test.js` reads `sub-routes.json` and matches each path to a registered route). Until that part lands: by review | The backend cannot see frontend routes. Each of the six was checked by hand for this spec: `code/frontend/src/core/index.js:133,135`; `code/frontend/src/shell/routes.js:19` with panels `leave`, `wfh`, `payslips`, `taxDeclaration` |

## Backend changes

All under `code/backend/core/src/main/java/com/infinevo/core/ai/help/` unless a path is given.

| Layer | File | Change |
|---|---|---|
| Record | `HelpArticle.java` | **New.** `id`, `title`, `summary`, `roles`, `screen`, `route`, `action`, `keywords`, `body` |
| Loader | `HelpLibrary.java` | **New.** Reads `classpath*:help/articles/*.md` and `help/sub-routes.json` once at startup, runs the startup checks, keeps the articles in an unmodifiable map. **(Added 2026-11-23)** `List<CatalogueLine> catalogueFor(caller)`: `id`, `title`, `summary` of each article `HelpAccess` lets the caller see, sorted by `id`, with the 60-article cap. Also `Optional<String> articleIdForScreen(String path)`: the article for a navigation item, used by `W-114.13` for its "Help article" line. It returns the article whose `screen` is that path and that the caller may see (`HelpAccess`); when several match, the one with no `route` and no `action` first, then the lowest `id`; none → empty. The caller passes the item's path; `W-114.13`'s `AiScreenResolver` turns a menu key into its path first |
| Search | `HelpSearch.java` | **New.** The scoring rule above. Pure function of (query, articles, caller roles) |
| Access | `HelpAccess.java` | **New.** `visibleTo(caller)`: the two checks above. Collects every `path` in `NavigationService.navigation()` including children (`NavigationItemResponse`, `code/backend/core/src/main/java/com/infinevo/core/navigation/NavigationItemResponse.java:13`) |
| Tools | `HelpAiToolProvider.java` | **New.** `implements AiToolProvider`; the two tools below |
| Prompt | `ai/AiSystemPrompt.java` (`W-114.4`) | **Changed 2026-11-23.** The catalogue block, built per request from `HelpLibrary.catalogueFor(caller)`, and one fixed rule: "For a question about how to do something in the product, pick the article from 'Help articles you can read' and call `core.read_help_article` with its id. If no line fits, call `core.search_help`. Answer only from the article you read. If no article fits, say you do not have a guide for that. When an article gives a screen, link it as `[name](app:/path)`." |
| Content | `code/backend/core/src/main/resources/help/articles/*.md` | **New.** The 30 articles |
| Content | `code/backend/core/src/main/resources/help/sub-routes.json` | **New.** `[{ "path", "parent" }]`, the six `route` values of the list below, each with its `screen` as parent: `/employees/new`, `/employees/import`, `/me/leave`, `/me/wfh`, `/me/payslips`, `/me/taxDeclaration`. `W-114.13` owns the rule that reads it and `W-114.16` adds four entries |

**Tools**

| Tool | Input | Returns | `requiredAction` |
|---|---|---|---|
| `core.search_help` | `query` (string, at most 200 characters) | up to 5 of `{ id, title, summary }` | `core.ai.use` |
| `core.read_help_article` | `id` | `{ id, title, body, screen, route }`, or `ok = false`, "not found" | `core.ai.use` |

No endpoint is added. Both tools run through `POST /api/v1/ai/sessions/{id}/messages`.

**Links.** Until `W-114.13` is on `main` an answer is plain text, so `[name](app:/path)` shows as
written. From `W-114.13` on, a `screen` becomes a link when it is in the user's menu, and a
`route` becomes a link because it is on the fixed sub-route list and its parent is in the user's
menu. A path on neither list is never a link.

## The first articles

★ = in the first 8 if the ticket is cut. Every `screen` below is a path in the navigation
catalogue today (`NavigationCatalogue.java:114-299`, `HrmsNavigation.java:17-151`,
`PayrollNavigation.java:17-75`).

| # | `id` | Title | Written for | `screen` | `route` |
|---|---|---|---|---|---|
| 1 | `add-employee` ★ | Add an employee | hr | `/employees` | `/employees/new` |
| 2 | `import-employees` | Add many employees from a file | hr | `/employees` | `/employees/import` |
| 3 | `invite-user` ★ | Give a person a login and a role | tenant-admin | `/users` | — |
| 4 | `roles` | What a role is and how to change one | tenant-admin | `/roles` | — |
| 5 | `departments` | Add a department | hr | `/org/departments` | — |
| 6 | `designations` | Add a designation | hr | `/org/designations` | — |
| 7 | `work-locations` | Add a work location | hr | `/org/work-locations` | — |
| 8 | `company-profile` | Change the company profile | tenant-admin | `/settings/company` | — |
| 9 | `setup-checklist` | Finish the setup checklist | tenant-admin | `/setup` | — |
| 10 | `leave-types` ★ | Create a leave type | hr | `/leave/types` | — |
| 11 | `leave-allocations` | Give employees their leave days | hr | `/leave/allocations` | — |
| 12 | `leave-requests-review` | See and act on all leave requests | hr | `/leave/requests` | — |
| 13 | `apply-leave` ★ | Apply for leave | employee | `/me` | `/me/leave` |
| 14 | `wfh-policies` | Set the work-from-home policy | hr | `/wfh/policies` | — |
| 15 | `apply-wfh` | Ask to work from home | employee | `/me` | `/me/wfh` |
| 16 | `approve-request` ★ | Approve or reject a request | manager | `/approvals` | — |
| 17 | `approval-definitions` | Set who approves what | tenant-admin | `/approvals/definitions` | — |
| 18 | `clock-in-out` | Clock in and out | employee | `/hrms/attendance` | — |
| 19 | `regularize-attendance` | Correct a missed clock-in | employee | `/hrms/regularizations` | — |
| 20 | `attendance-log` | Check everyone's attendance | hr | `/hrms/attendance-log` | — |
| 21 | `timesheets` | Fill and submit a timesheet | employee | `/hrms/timesheets` | — |
| 22 | `timesheet-review` | Review a team member's timesheet | manager | `/hrms/timesheet-review` | — |
| 23 | `loss-of-pay` | Enter loss of pay days | hr | `/hrms/loss-of-pay` | — |
| 24 | `holidays` | See the holiday list | employee | `/holidays` | — |
| 25 | `salary-components` | Create a salary component | payroll-officer | `/payroll/components` | — |
| 26 | `assign-salary` | Set an employee's salary | payroll-officer | `/employees` (`action: payroll.salary.manage`) | — |
| 27 | `run-payroll` ★ | Run payroll for a month, step by step | payroll-officer | `/payroll/runs` | — |
| 28 | `statutory-settings` ★ | Set up EPF, ESI, professional tax and TDS | payroll-officer | `/payroll/settings` | — |
| 29 | `view-payslip` ★ | See and download my payslip | employee | `/me` (`action: payroll.payslip.read_own`) | `/me/payslips` |
| 30 | `tax-declaration` | Declare my tax savings | employee | `/me` (`action: payroll.tax_declaration.read_own`) | `/me/taxDeclaration` |

Portal tabs are routed as `/me/<panel code>` (`code/frontend/src/shell/portal/PortalLayout.jsx:225`).
`run-payroll` explains the steps; it does not make the helper able to run them (`W-114.21`).

## Database changes

None. No table, no migration.

- [x] Creates no table — nothing needs `tenant_id` or a policy
- [x] No Flyway script
- [x] No money value
- [x] No destructive step
- [x] No module references another module: `core` reads the menu through `NavigationService`; the test that needs all three modules lives in `app`, which already depends on them

## Frontend changes

None. **Routes added:** none. Links in answers show as plain text until `W-114.13`.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/help/HelpLibraryTest.java` | Loads the shipped articles; each startup check fails on a fixture file (missing field, id ≠ file name, duplicate id, unknown role, a `route` missing from `sub-routes.json` or listed under another parent, body too long); `articleIdForScreen` returns the screen's own article before one with a `route` or an `action`, and nothing for a screen with no article |
| Unit | `core/src/test/.../ai/help/HelpSearchTest.java` | Keyword beats title beats body; role bonus; stop words; nothing above 0 returns an empty list; at most 5; ties by id |
| Unit | `core/src/test/.../ai/help/HelpAccessTest.java` | An article whose screen is not in the caller's feed is not found by search and "not found" by id; `action` is honoured |
| Unit | `core/src/test/.../ai/help/HelpAiToolProviderTest.java` | Tool shapes; `get_help_article` for a hidden article answers the same as for an unknown id |
| Unit | `core/src/test/.../ai/AiChatServiceImplTest.java` (extend, `W-114.4`) | **Changed 2026-11-23.** Stub scripted to read `leave-types` by id straight from the catalogue, then answer: the answer carries the article's steps and `core.search_help` was not called. A second script uses search, then read: same answer |
| Unit | `core/src/test/.../ai/help/HelpCatalogueTest.java` (added 2026-11-23) | HR's catalogue holds `leave-types`; an employee's does not; a customer without Payroll has no `run-payroll` line; lines are sorted by `id` and hold only id, title and summary; the full catalogue of the 30 shipped articles is under 8,000 characters; 61 visible fixture articles give 60 lines and the "more articles exist" sentence |
| Unit | `core/src/test/.../ai/AiSystemPromptTest.java` (extend, added 2026-11-23) | The prompt sent to the model for an employee contains the id `apply-leave` and not the id `leave-types`; it contains no article body |
| Test | `app/src/test/.../help/HelpArticlesMatchTheMenuTest.java` | Every shipped article's `screen` is a path in `NavigationCatalogue.withContributed(List.of(new HrmsNavigation(), new PayrollNavigation()))`; every `action` is declared by a menu item or a portal panel provider |

**Break the guard once:** change one article's `screen` to `/no/such/screen` and see
`HelpArticlesMatchTheMenuTest` fail; put it back.

## Verification

```bash
cd code/backend && ./mvnw -B -pl core,app -am verify
git grep -nE '^[^#]*ddl-auto[[:space:]]*[:=]' -- code/      # expect nothing
```

| Check | Expected | Result |
|---|---|---|
| The application starts with the 30 articles | No startup error; log line "Loaded 30 help articles" | not run |
| A fixture article with no `title` | Startup fails naming the file and the field | not run |
| `core.search_help { "query": "add leave type" }` as HR | `leave-types` first | not run |
| Same as an employee (no `/leave/types` in the feed) | `leave-types` absent | not run |
| `core.read_help_article { "id": "leave-types" }` as an employee | `ok = false`, "not found" | not run |
| `core.search_help` for a customer without Payroll, query "run payroll" | `run-payroll` absent | not run |
| `core.search_help { "query": "zzzz" }` | Empty list; the stubbed answer is "I do not have a guide for that." | not run |
| An article edited to `screen: /no/such/screen` | `HelpArticlesMatchTheMenuTest` fails | not run |
| The system prompt for HR (added 2026-11-23) | Holds a "Help articles you can read" block with `leave-types \| Create a leave type \| …`; about 30 lines | not run |
| The system prompt for an employee (added 2026-11-23) | The block has no `leave-types` line | not run |
| `core.read_help_article` with an id the employee's catalogue does not list (added 2026-11-23) | `ok = false`, "not found" | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| An article goes stale when its screen changes | Medium | The path test catches a removed screen. A changed button name is not caught: the spec of the ticket that changes a screen names the article to update |
| The model answers a how-to from general knowledge instead of the article | Medium | The prompt rule; the stub test; a founder check on the test company before real customers (the helper stays off until `W-114.10`) |
| Keyword search misses a differently worded question | Medium | Changed 2026-11-23: the model now picks from titles and summaries by meaning, so wording matters less; search is only the fallback. `W-114.23` measures how often the right article is picked |
| The catalogue adds about 1,500 tokens to every question, how-to or not (added 2026-11-23) | Certain | Accepted: about one-twentieth of putting the library in. `W-114.24` shows the real token use |
| The navigation feed is worked out on every message to build the catalogue (added 2026-11-23) | Low | It is the call the menu already makes on each page load; if it shows in timings, cache the visible ids for the length of one request only |
| A user learns a screen exists that they cannot open | Low | Access is checked on search and on read; the hidden and the unknown answers are identical |
| A `route` deeper than the menu path is wrong | Low | Used in 6 articles, each named in the table; the founder review covers them |

## Rollback

Revert the commit. The two tools and the prompt rule go; the helper answers data questions
as before. No data to clean up.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` | `HelpArticle`, `HelpLibrary`, `HelpSearch`, `HelpAccess`, with tests and fixture files |
| 2 | `core` | `HelpAiToolProvider`, the prompt rule, the chat service test |
| 3 | `core` | The ★ articles, then the other 22 |
| 4 | `app` | `HelpArticlesMatchTheMenuTest` |
