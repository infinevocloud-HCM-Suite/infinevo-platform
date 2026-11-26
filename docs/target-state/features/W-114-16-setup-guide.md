# Feature: AI helper setup guide — what is left to set up

| Field | Value |
|---|---|
| **Feature ID** | `W-114.16` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai/help`), one test in `code/backend/app` |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.11` on `main` (`HelpLibrary`, `HelpAccess`, `help/sub-routes.json`). Better after `W-114.12`, not blocked by it. Its four settings links are clickable through `W-114.13`, which the build order puts first |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` (plus one test class in `app`, test only) | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A tenant admin asks "what is left to set up?" and gets the same done and not-done steps the Setup screen shows, each with its screen | 1 |
| Frontend area | none | 1 |

Size **S**. One tool over a service that already exists.

## Decisions

| Date | Who | Decision |
|---|---|---|
| 2026-11-23 | founder | The helper gives a new tenant admin a setup guide, read from the existing setup checklist |

Decided in this spec, for the founder to overrule:

| Point | Choice | Why |
|---|---|---|
| Who may ask | A caller holding `core.tenant.read` | The same action the checklist's own `GET` asks for (`code/backend/core/src/main/java/com/infinevo/core/setup/SetupChecklistController.java:36-37`) |
| Skipping a step | Not offered | Skip is a write behind `core.tenant.manage` (`SetupChecklistController.java:43-44`). The helper only reads here; the user skips on the Setup screen |
| Step → screen | A small map in `core`, the same pairs the frontend already uses | The backend step has no path (`SetupStepResponse.java`); the frontend keeps one in `code/frontend/src/core/setup/stepLinks.js:7-27` |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | The checklist exists and is correct, but the helper cannot read it, so "what is left to set up?" has no answer. | `SetupChecklistService.getChecklist` (`code/backend/core/src/main/java/com/infinevo/core/setup/SetupChecklistService.java:126`); no tool for it in `W-114-4-…md` |
| 2 | A step knows its code, label and state, not where to go to finish it. That lives only in the frontend. | `SetupStepResponse.java` (fields `code`, `label`, `module`, `completed`, `skipped`, `prefilled`); `stepLinks.js:7-16` |

## Scope

**In scope**

- One tool, `core.setup_status`.
- A step-to-screen map in `core`, with a test that every step has an entry.
- One more rule in the system prompt.

**Out of scope**

- Skipping or completing a step from the chat.
- Any new setup step. The eight in `SetupStepCatalogue.java:32-39` are used as they are.
- How-to steps for each item — the `W-114.11` articles (`work-locations`, `add-employee`, `salary-components`, `statutory-settings`).
- Removing the frontend's copy of the map.

## Flow

```
user: "what is left to set up?"
  --> model calls core.setup_status {}
        --> SetupChecklistService.getChecklist(tenantId)   (the call the Setup screen makes)
        --> each step: label, state, and its screen when the caller may open it
  --> answer: "3 of 8 done. Next: Pay schedule — [Pay schedule](app:/payroll/settings/pay-schedule)"
```

## The tool

| Tool | Input | Returns | `requiredAction` |
|---|---|---|---|
| `core.setup_status` | — | `{ total, done, skipped, steps: [{ label, state, screen: { name, path }, article }] }`, in checklist order | `core.tenant.read` |

| Field | Rule |
|---|---|
| `state` | `DONE` (`completed`, not `prefilled`); `REVIEW` (`completed` and `prefilled` — filled by the country template, nobody has saved it since: `code/backend/core/src/main/java/com/infinevo/core/setup/SetupStepChecker.java:32-40`); `SKIPPED`; `NOT_DONE` |
| `screen` | From the map below. Left out when the caller's navigation feed does not carry the step's menu key (`HelpAccess`, `W-114.11`), so the helper never links a screen the user cannot open |
| `article` | The id of the `W-114.11` article for the step, when one exists |
| Steps listed | Exactly those `getChecklist` returns. It already leaves out steps of a module the customer does not hold (`SetupChecklistService.java:84,93`) |
| No ids | Step ids, tenant id and timestamps are not returned |

**The map** — `SetupStepScreens`, the pairs of `stepLinks.js:7-16`, each with the menu key that opens it:

| Step code | Menu key | Path |
|---|---|---|
| `WORK_LOCATION` | `core.org.locations` | `/org/work-locations` |
| `EMPLOYEE` | `core.employee` | `/employees` |
| `PAY_SCHEDULE` | `payroll.settings` | `/payroll/settings/pay-schedule` |
| `PRIOR_PAYROLL` | `payroll.prior_payroll` | `/payroll/prior-payroll` |
| `SALARY_COMPONENTS` | `payroll.components` | `/payroll/components` |
| `EPF` | `payroll.settings` | `/payroll/settings/epf` |
| `ESI` | `payroll.settings` | `/payroll/settings/esi` |
| `PROFESSIONAL_TAX` | `payroll.settings` | `/payroll/settings/professional-tax` |

Four paths go one level below the menu item `/payroll/settings`. `W-114.13` links such a path
only when it is on the fixed sub-route list, `help/sub-routes.json`, and the caller's menu holds
its parent. This part adds the four to that file, each with parent `/payroll/settings`:
`/payroll/settings/pay-schedule`, `/payroll/settings/epf`, `/payroll/settings/esi`,
`/payroll/settings/professional-tax` (routes at `code/frontend/src/payroll/index.js:96-108`).
The other four step paths are menu items themselves and need no entry.

**One thing to know:** `getChecklist` is not a pure read. It adds rows for newly applicable
steps and refreshes the completion cache (`SetupChecklistService.java:125-135`), exactly as
opening the Setup screen does. The tool calls it as it is.

## Backend changes

All under `code/backend/core/src/main/java/com/infinevo/core/ai/help/`.

| Layer | File | Change |
|---|---|---|
| Map | `SetupStepScreens.java` | **New.** Step code → menu key, path, article id |
| Content | `code/backend/core/src/main/resources/help/sub-routes.json` (`W-114.11`) | **Add** the four `/payroll/settings/…` entries above. `W-114.13`'s frontend test then proves each is a real route |
| Tool | `SetupAiToolProvider.java` | **New.** `implements AiToolProvider`; `core.setup_status`. Calls `SetupChecklistService.getChecklist(TenantContext.require())` |
| Prompt | `ai/AiSystemPrompt.java` | One more fixed rule: "For a question about what is left to set up, call `core.setup_status` and list the steps it returns, not-done first. Do not add steps of your own." |

No endpoint is added.

## Database changes

None.

- [x] Creates no table
- [x] No Flyway script
- [x] No money value
- [x] No destructive step
- [x] Stays in `core`: the payroll steps are already defined in `core` (`SetupStepCatalogue.java:34-39`) and only their menu keys are named, as text

## Frontend changes

None. **Routes added:** none.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/help/SetupStepScreensTest.java` | Every code in `SetupStepCatalogue.DEFAULT_STEPS` has an entry; every path starts with `/`; every path is a menu item's path or an entry of `sub-routes.json` whose parent is that step's menu item |
| Unit | `core/src/test/.../ai/help/SetupAiToolProviderTest.java` | The four states; counts equal the service's; a step whose menu key is not in the caller's feed has no `screen`; no id or timestamp in the result |
| Unit | `core/src/test/.../ai/help/SetupAiToolProviderTest.java` | `requiredAction` is `core.tenant.read`; `AiToolRegistry` already withholds a tool whose action the caller lacks (`AiToolRegistryTest`), so no new registry case |
| Test | `app/src/test/.../help/HelpArticlesMatchTheMenuTest.java` (extend, `W-114.11`) | Every menu key in `SetupStepScreens` is in the full menu; every path starts with that item's path |

**Break the guard once:** remove one entry from `SetupStepScreens` and see
`SetupStepScreensTest` fail.

## Verification

```bash
cd code/backend && ./mvnw -B -pl core,app -am verify
```

| Check | Expected | Result |
|---|---|---|
| `core.setup_status {}` as a tenant admin of a new customer with Payroll | 8 steps, the same states `GET /api/v1/setup-checklist` returns, `total` 8 | not run |
| Same for a customer without Payroll | 2 steps: Work location, Employee | not run |
| A step filled by the country template and never saved | `state: REVIEW` | `SetupAiToolProviderTest.statesAndCounts` |
| As an employee (no `core.tenant.read`) | The tool is not in the list sent to the model | by `requiredAction` (`SetupAiToolProviderTest.shape`) and the registry |
| As HR on a customer with Payroll (holds `core.tenant.read`, not `payroll.settings.manage`) | Payroll steps listed with no `screen` | `SetupAiToolProviderTest.screenOnlyWhenTheMenuHoldsIt` |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The step map and the frontend's `stepLinks.js` drift apart | Low | The unit test forces an entry for every backend step; the paths are checked against the menu in `app` |
| A step is added to the catalogue with no map entry | Certain to be tried | `SetupStepScreensTest` fails until it is added |
| The helper reads the checklist more often than the screen would | Low | One read per question; the service is idempotent |

## Rollback

Revert the commit. The tool and its prompt rule go. No data to clean up.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` | `SetupStepScreens`, `SetupAiToolProvider`, the prompt rule, tests |
| 2 | `app` | The added check in `HelpArticlesMatchTheMenuTest` |
