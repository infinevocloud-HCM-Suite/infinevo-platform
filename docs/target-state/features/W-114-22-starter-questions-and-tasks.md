# Feature: AI helper starters — suggested questions and tasks per role

| Field | Value |
|---|---|
| **Feature ID** | `W-114.22` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai/starter`), `code/frontend` (`src/shell/assistant`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.5` on `main` (the chat drawer and `AssistantEmptyState`), `W-114.4` on `main` (`AiToolRegistry`). Built after `W-114.11`–`W-114.16`, so the first chips have answers behind them |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | An empty chat shows up to six suggested questions and tasks that fit the user's roles and that the helper can really answer for that user; clicking one sends it | 1 |
| Frontend area | `shell` (`src/shell/assistant`) | 1 |

Size **S**.

## Decisions

| Date | Who | Decision |
|---|---|---|
| 2026-11-23 | founder | Suggested questions and tasks per role are shown when the chat opens, to help the user start |
| 2026-11-23 | founder | A starter shows only when the user has the permission and the part behind it is on `main` |
| 2026-11-23 | founder | Pay run starters are read-only: summary and "take me there". The helper never starts or approves a run |

Decided in this spec, for the founder to overrule:

| Point | Choice | Why |
|---|---|---|
| "The part is on `main`" | A starter names the tool it needs. It is returned only when that tool is registered **and** offered to this caller | A tool exists only when its part is deployed, so no flag and no list to keep in step. The caller's permission, their employee record and the customer's modules are already applied by `AiToolRegistry.toolsFor(caller)` (`W-114.3`, `W-114.4` rules 3 and 4) |
| The permission | The tool's own `requiredAction`, not a second copy on the starter | One place to change. A starter may add one extra condition: a screen that must be in the caller's menu |
| How many | At most 6 | The drawer is 420 px wide (`W-114-5-chat-drawer.md`); more than six pushes the input off a phone screen |
| A click | Sends the starter's prompt as a normal message | It counts as one request against the monthly cap, like typing it |
| Where the list lives | A registry in `core`, in code | Starters are product text reviewed with the code. Tool names of other modules appear in it as text only, never as a class reference |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | An empty chat shows one fixed line and nothing to click. A new user does not know what the helper can do. | `W-114-5-chat-drawer.md`, file table: `AssistantEmptyState.jsx` — "Ask a question about your leave, attendance or payslip." |
| 2 | That line is the same for an employee and a payroll officer, and it names things the user may not be able to ask about. | same |
| 3 | The helper's abilities arrive part by part (`W-114.6` to `W-114.21`), so a fixed list would be wrong most of the time. | the order in `W-114-1-tenant-ai-switch-and-monthly-cap.md`, "Where this sits" |

## Scope

**In scope**

- A starter registry and `GET /api/v1/ai/starters`.
- Chips in the chat drawer's empty state.
- The first starter list (below).

**Out of scope**

- Any tool. Every tool named below is built by its own part.
- Follow-up suggestions after an answer; starters that depend on the current screen (`W-114.13` may add them later through the same registry).
- Thumbs up or down on answers.
- A screen for a customer to edit starters.

## Flow

```
drawer opens with no messages
  --> GET /api/v1/ai/starters
        --> helper off for this customer: 403 AI_DISABLED (the drawer is not shown then anyway)
        --> keep a starter when: its tool is in AiToolRegistry.toolsFor(caller)
                                 and its screen, if it names one, is in the caller's navigation feed
        --> only starters written for one of the caller's roles (founder 2026-11-26); roles in turn; at most 6
  --> chips; click --> the prompt is sent through POST /api/v1/ai/sessions/{id}/messages
```

## Rules

| # | Rule |
|---|---|
| 1 | A starter is `{ id, label, prompt, kind, toolName, screenKey?, roles }`. `kind` is `QUESTION` or `TASK`. `label` is at most 40 characters and is what the chip shows; `prompt` is what is sent |
| 2 | Returned only when `toolName` is among `AiToolRegistry.toolsFor(caller)`. A tool that is not deployed yet is not registered, so its starters do not exist for anyone |
| 3 | If `screenKey` is set, the menu key must be in the caller's navigation feed (`NavigationService.navigation()`, `code/backend/core/src/main/java/com/infinevo/core/navigation/NavigationService.java:157`) |
| 4 | `roles` decides who sees a starter: only one written for a role the caller holds is shown, even when that leaves fewer than six (founder 2026-11-26, after the read: "own roles only"; this replaces "only orders the list, then the rest"). The caller's role codes come from `RoleSource.roleCodesOf` (`code/backend/shared/src/main/java/com/infinevo/shared/authz/RoleSource.java:24`). With several roles, chips are taken one role at a time in turn, so no role fills all six |
| 5 | The endpoint calls no model and does not count against the cap. It checks the customer switch through `AiAccessGate.status()` (`code/backend/core/src/main/java/com/infinevo/core/ai/AiAccessGate.java`) and answers `403 AI_DISABLED` when off |
| 6 | The response carries `id`, `label`, `prompt`, `kind` only — no tool name, no action code |
| 7 | Startup fails on a duplicate starter `id`, a `label` over 40 characters, or a `toolName` that is not in `module.verb_noun` form. A tool name that matches no registered tool is **not** an error — it is how a not-yet-built part looks |

## API contract

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/ai/starters` | — | `200` `[{ "id": "leave-left", "label": "How many leave days do I have left?", "prompt": "How many leave days do I have left?", "kind": "QUESTION" }]`, at most 6. `403 AI_DISABLED` when the helper is off | `core.ai.use` |

## The first starters

"Needs" is the tool and the part that builds it. "Action" is that tool's `requiredAction`,
shown for the reader only — the registry does not repeat it.

**Employee**

| Kind | Label | Needs | Action |
|---|---|---|---|
| Question | How many leave days do I have left? | `core.my_leave_balances` — `W-114.4` | `core.leave.read_own` |
| Question | Explain my last payslip | `payroll.explain_payslip` — `W-114.14` | `payroll.payslip.read_own` |
| Question | Why did my TDS change this month? | `payroll.explain_tds` — `W-114.14` | `payroll.tax_declaration.read_own` |
| Question | Which holidays are coming up? | `core.holidays` — `W-114.4` | `core.holiday.read` |
| Task | Apply leave for me | `core.propose_leave_request` — `W-114.8` | `core.leave.apply` |
| Task | Request work from home | `core.propose_wfh_request` — `W-114.17` | `core.wfh.apply` |
| Task | Cancel one of my leave requests | `core.propose_leave_cancel` — `W-114.17` | `core.leave.apply` |

**Manager**

| Kind | Label | Needs | Action |
|---|---|---|---|
| Question | What is waiting for my approval? | `core.my_pending_approvals` — `W-114.18` | `core.approval.decide` |
| Question | Who in my team is on leave this week? | `core.team_on_leave` — `W-114.19` | `core.leave.read_team` or `core.leave.read` |
| Question | What can I do as a manager here? | `core.my_role_guide` — `W-114.12` | `core.ai.use` |

**HR**

| Kind | Label | Needs | Action |
|---|---|---|---|
| Question | Who is on leave today? | `core.team_on_leave` — `W-114.19` | `core.leave.read_team` or `core.leave.read` |
| Question | Who joined this month? | `core.joiners` — `W-114.19` | `core.employee.read` |
| Question | How do I add a leave type? | `core.search_help` — `W-114.11`; screen `core.leave.types` | `core.ai.use` |
| Question | What should HR do before month end? | `core.my_role_guide` — `W-114.12` | `core.ai.use` |
| Task | Add a new employee | `core.propose_employee_create` — `W-114.20` | `core.employee.create` |
| Task | Take me to leave allocations | `core.find_screen` — `W-114.13`; screen `core.leave.allocations` | `core.ai.use` |

**Payroll officer**

| Kind | Label | Needs | Action |
|---|---|---|---|
| Question | Summarise this month's pay run | `payroll.payrun_summary` — `W-114.21` | `payroll.run.read` |
| Question | What changed from last month? | `payroll.payrun_summary` — `W-114.21` | `payroll.run.read` |
| Question | Who has zero or negative pay? | `payroll.payrun_summary` — `W-114.21` | `payroll.run.read` |
| Question | What is my next step on this run? | `payroll.payrun_next_step` — `W-114.21` | `payroll.run.read` |
| Task | Take me to the pay runs | `core.find_screen` — `W-114.13`; screen `payroll.runs` | `core.ai.use` |
| Task | Take me to salary components | `core.find_screen` — `W-114.13`; screen `payroll.components` | `core.ai.use` |

**Tenant admin**

| Kind | Label | Needs | Action |
|---|---|---|---|
| Question | What is left to set up? | `core.setup_status` — `W-114.16` | `core.tenant.read` |
| Question | How do I invite my HR team? | `core.search_help` — `W-114.11`; screen `core.users` | `core.ai.use` |
| Question | What can each role do? | `core.my_role_guide` — `W-114.12`; screen `core.roles` | `core.ai.use` |
| Question | How do I set up statutory details? | `core.search_help` — `W-114.11`; screen `payroll.settings` | `core.ai.use` |
| Task | Take me to the company profile | `core.find_screen` — `W-114.13`; screen `core.settings.company` | `core.ai.use` |
| Task | Take me to roles | `core.find_screen` — `W-114.13`; screen `core.roles` | `core.ai.use` |

Notes on the list:

- "Take me to salary structures" in the first draft is "salary components" here: the menu
  item is `payroll.components` at `/payroll/components`
  (`code/backend/payroll/src/main/java/com/infinevo/payroll/navigation/PayrollNavigation.java:75`); there is no separate structures screen in the menu.
- A "take me" starter needs `core.find_screen` (`W-114.13`), which finds a screen in the
  caller's own menu, and the named screen in that menu. `W-114.13` also makes the link
  clickable, and is built before this part.
- The three pay run questions are separate chips over one tool. They never start, approve
  or pay a run.

## Backend changes

All under `code/backend/core/src/main/java/com/infinevo/core/ai/starter/`.

| Layer | File | Change |
|---|---|---|
| Record | `AiStarter.java` | **New.** `id`, `label`, `prompt`, `kind`, `toolName`, `screenKey` (nullable), `roles` |
| Registry | `AiStarterRegistry.java` | **New.** The list above as constants; the startup checks of rule 7 |
| Service | `AiStarterService.java` | **New.** Rules 2 to 4. Uses `AiToolRegistry.toolsFor(caller)`, and `HelpAccess.currentViewer()` (`W-114.11`) for the menu keys and role codes - it wraps `NavigationService.navigation()` and `RoleSource.roleCodesOf`. With several roles the narrower one leads: tenant-admin, payroll-officer, hr, manager, employee |
| Controller | `AiStarterController.java` | **New.** `GET /api/v1/ai/starters`, `@RequiresAction("core.ai.use")`. Declares the `AiDisabledException` handler and builds the body with `AiErrorResponses` (`code/backend/core/src/main/java/com/infinevo/core/ai/AiErrorResponses.java`), because handlers here are per controller |
| DTO | `AiStarterController.AiStarterResponse` | **New**, nested in the controller. `id`, `label`, `prompt`, `kind` |

## Database changes

None.

- [x] Creates no table
- [x] No Flyway script
- [x] No money value
- [x] No destructive step
- [x] `core` names tools of `hrms` and `payroll` as text only; no import, so `maven-enforcer` is unaffected

## Frontend changes

All under `code/frontend/src/shell/assistant/` (created by `W-114.5`).

| File | Change |
|---|---|
| `assistantService.js` | `listStarters()` → `GET /v1/ai/starters` |
| `AssistantEmptyState.jsx` | Loads the starters when the drawer shows an empty chat. Renders each as a button (`role="button"`, `aria-label` = the label), questions first, then tasks under a small "Do it for me" heading. While loading: the existing one line of text. On an empty list or a failed call: the one line of text, no error shown. No hex colours — theme tokens only, as CI requires |
| `AssistantDrawer.jsx` | A chip click calls the same send path as the input, with the starter's `prompt`; chips disappear once the chat has a message |

**Routes added:** none.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/starter/AiStarterRegistryTest.java` | The shipped list passes the startup checks; duplicate id, long label and a bad tool name each fail |
| Unit | `core/src/test/.../ai/starter/AiStarterServiceTest.java` | A starter whose tool is not registered is never returned; one whose tool the caller is not offered is not returned; `screenKey` not in the feed hides it; own-role starters come first; two roles alternate; at most 6 |
| Unit | `core/src/test/.../ai/starter/AiStarterControllerTest.java` | `403 AI_DISABLED` when off; the body has no `toolName`; `core.ai.use` required |
| Unit | `core/src/test/.../guard/EndpointGuardCoverageTest.java` | Passes with the new endpoint guarded |
| Component | `src/shell/assistant/AssistantEmptyState.test.jsx` | Chips render from the service; questions before tasks; empty list and failed call show the one line; a click calls send with the prompt |
| Component | `src/shell/assistant/AssistantDrawer.test.jsx` (extend) | Chips are gone after the first message |

**Break the guard once:** register no tool in a test context and see the endpoint return `[]`.

## Verification

```bash
cd code/backend && ./mvnw -B -pl core -am verify
cd code/frontend && npx vitest run src/shell/assistant && npm run lint && npm run build
```

| Check | Expected | Result |
|---|---|---|
| `GET /api/v1/ai/starters` as an employee, with only `W-114.4` tools deployed | "How many leave days do I have left?" and "Which holidays are coming up?"; no payslip, WFH or approval starter | not run |
| Same after `W-114.14` is deployed, customer holds Payroll | The two payslip starters appear with no change to this part | not run |
| Same for a customer without Payroll | No payslip starter | not run |
| As a user holding `hr` and `manager` | Six chips, alternating between the two roles' lists | `AiStarterServiceTest.ordering` |
| As a tenant admin whose menu has no `payroll.settings` | "How do I set up statutory details?" absent | `AiStarterServiceTest.screenKeyMustBeInTheMenu` |
| Helper switched off for the customer | `403 AI_DISABLED` | `AiStarterControllerTest.off` |
| Click a chip in the drawer | The prompt is sent, the answer shows, usage goes up by one, the chips are gone | not run |
| A phone-width drawer with six chips | The input stays on screen | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A starter's tool name does not match the name its part finally ships | Medium | The starter then never shows — it fails safe. Each of `W-114.14`, `.17`–`.21` checks its tool names against this registry when it is built |
| A chip is shown but the answer is poor | Medium | Starters are built after the understand group, and the founder tries each on the test company |
| A tool is registered under a name without its module prefix, so its starter silently never shows | Low | Every spec now names tools `module.verb_noun`, and `W-114.3`'s registry refuses a bare name at startup |
| Users read a chip as a promise for every role | Low | Chips come only from what `toolsFor(caller)` offers this user |

## Rollback

Revert the commit. The empty state goes back to its one line of text. No data to clean up.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` | `AiStarter`, `AiStarterRegistry`, `AiStarterService`, tests |
| 2 | `core` | `AiStarterController`, DTO, guard coverage |
| 3 | frontend `shell` | `listStarters`, chips in `AssistantEmptyState`, the drawer change, tests |
