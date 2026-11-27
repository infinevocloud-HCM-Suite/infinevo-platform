# Feature: AI helper does admin set-up steps after Confirm — part 1

| Field | Value |
|---|---|
| **Feature ID** | `W-114.28` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai/action`), `code/backend/payroll` (`ai`), `code/frontend/src/shell/assistant` (link lines) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.8`, `W-114.9` on `main` |

Founder, 2026-11-27: the helper does the steps HR, the payroll officer and the tenant admin take,
each behind a Confirm card and the caller's own permission. **Never:** a pay run step (founder,
2026-11-23 and 2026-11-27) or a money decision (2026-11-27, screen only for now).

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core`, plus one action in `payroll` | 2 — the bank file template is payroll's; it is one executor and one tool, split as task 2 |
| Flyway migration | none | 1 |
| Externally testable behaviour | Each step is proposed as a card and happens only on Confirm | 1 |
| Frontend area | `shell` — link lines | 1 |

Size **M**.

## The steps

Every step follows `W-114.8` and the rules proven in `.17`–`.20`: the tool checks and proposes,
writing nothing; the card is built from the stored payload and the database; Confirm calls the
**same service method the screen's endpoint calls**, so every rule of that screen runs again; a
refusal ends `FAILED` with the service's own words (non-`IllegalArgument`/`IllegalState` refusals
are translated); one proposal per message; no id reaches the model; names are resolved within the
tenant, never ids from the model.

| # | Tool → action | Service call (the screen's) | Action held |
|---|---|---|---|
| 1 | `core.propose_department` → `core.org.department.create` | `DepartmentService.create(DepartmentRequest(code, name, true))` (`org/DepartmentController.java:40-46`) | `core.org.manage` |
| 2 | `core.propose_designation` → `core.org.designation.create` | `DesignationService.create(...)` (`org/DesignationController.java:35-37`) | `core.org.manage` |
| 3 | `core.propose_work_location` → `core.org.location.create` | `WorkLocationService.create(...)` (`org/WorkLocationController.java:40-41`); address fields as the form takes them | `core.org.manage` |
| 4 | `core.propose_holiday` → `core.holiday.add` | add a holiday to a named calendar (`holiday/HolidayCalendarController.java:66-67`) | `core.holiday.manage` |
| 5 | `core.propose_user_invite` → `core.user.invite` | `InvitationService.createUserInvitation(UserInvitationRequest(email, roleIds), actor)`; roles named, resolved to the tenant's roles | the invitation endpoint's action |
| 6 | `payroll.propose_bank_file_template` → `payroll.bank_file_template.create` | `BankFileTemplateService.create(BankFileTemplateRequest(name, format, header, columns))` (`payrun/bankfile/BankFileTemplateController.java:59-62`); columns from the template's field list | `payroll.settings.manage` |

The user invite card shows every role by name and says what the invitee will be able to do (the
role guide's line for that role). A role the caller may not grant is refused by the service, as on
the Users screen.

After Confirm each card links to the screen that lists what was made (`actionLinks.js`).

## What is sent to the model

What the user typed (names, codes, an email address, a date), and the tenant's master names to
match against. No id.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | one test per tool provider | Each tool's action equals the screen's; a name that matches nothing lists the valid ones; the payload holds only the fields the step may set; one proposal per message |
| Integration | `core/src/test/.../ai/action/AdminStepsIT.java`, `payroll/src/test/.../ai/BankFileTemplateActionIT.java` | Each step: nothing exists until Confirm; Confirm makes exactly one; a second confirm makes none; a duplicate code or name made in between ends `FAILED`; a caller without the action is never offered the tool |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core,payroll,app -am verify
cd code/frontend && npx vitest run src/shell/assistant && npm run lint
```

## AI helper

This part is itself the AI helper's action layer for these screens. Help articles: `W-114.26`.

**As built (2026-11-27, Claude).**

| Step | Tool, executor | Built as |
|---|---|---|
| 1-3 | `core/ai/action/OrgMasterActionToolProvider.java`, `OrgMasterExecutors.java` (`OrgMasterCreateExecutor`) | Code upper-cased; a code or name in use (inactive rows too) is refused before a card |
| 4 | `HolidayActionToolProvider.java`, `HolidayAddExecutor.java` | No calendar named: the default one (or the only one). The payload names the calendar; Confirm finds it again by name. The service allows the same holiday twice, so the step refuses a holiday of the same name starting the same day, before the card and again at Confirm |
| 5 | `UserInviteToolProvider.java`, `UserInviteExecutor.java` | Roles by name or code, never platform-admin; roles need `core.role.assign` too, as on the Users screen. The card shows each role's first role-guide duty, or "A role made in this company" for one without a guide. An email with an invitation waiting is refused |
| 6 | `payroll/ai/BankFileTemplateToolProvider.java`, `BankFileTemplateCreateExecutor.java` | Columns in words: a field by its label (the part before any bracket will do), a fixed text, or serial; the screen's rules checked before the card |

`NoPayRunActionTest` (app) now allows exactly one payroll action, `payroll.bank_file_template.create`; any other payroll action still fails the build. `PayrollTestApp` gains stand-in `AiActionProposals` and `EmployeeQueryService` beans: its scan leaves core's out, and without them no payroll IT context starts. Tests: `OrgMasterActionToolProviderTest` 7, `HolidayActionToolProviderTest` 5, `UserInviteToolProviderTest` 5, `BankFileTemplateToolProviderTest` 5, `AdminStepsIT` 5, `BankFileTemplateActionIT` 3, `actionLinks.test.js`.

## Rollback

Revert the commit. The tools are no longer offered; what was made stays.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` (`ai/action`) | Steps 1-5: tools, executors, tests |
| 2 | `payroll` (`ai`) | Step 6: tool, executor, tests |
| 3 | frontend `shell` | Link lines and their test |
