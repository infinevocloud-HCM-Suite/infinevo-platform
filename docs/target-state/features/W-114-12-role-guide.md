# Feature: AI helper role guide — what my role can do and should do

| Field | Value |
|---|---|
| **Feature ID** | `W-114.12` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai/help`, `src/main/resources/help/`), one test in `code/backend/app` |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.11` on `main` (`HelpLibrary`, `HelpAccess`, the `help/` folder) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` (plus one test class in `app`, test only) | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A user asks "what can I do here?" or "what should I do this week?" and gets an answer built from their own roles, their own screens and their role's duty guide | 1 |
| Frontend area | none | 1 |

Size **M**: one tool, six short guides, one name list.

## Decisions

| Date | Who | Decision |
|---|---|---|
| 2026-11-23 | founder | For each role the helper knows what that role can access and what it is supposed to do, answers from that, and helps do it |
| 2026-11-23 | founder | Guides are text we write and ship, like the articles of `W-114.11` |

Decided in this spec, for the founder to overrule:

| Point | Choice | Why |
|---|---|---|
| "What can I do?" | Answered from the caller's **navigation feed and held actions**, never from the guide text alone | A customer can change what a role holds (`RoleService.update`, `code/backend/core/src/main/java/com/infinevo/core/authz/RoleService.java:30`). The guide says what the role is for; the feed says what this user can really open |
| A duty the user cannot do | Dropped from the answer | Every duty line names the screen it needs; a line whose screen is not in the caller's feed is not returned |
| Which roles get a guide | `employee`, `manager`, `hr`, `payroll-officer`, `tenant-admin`, `finance` | The founder's five, plus `finance`: it is the seeded role that approves a pay run (`payroll.run.approve`, `code/backend/migration/src/main/resources/db/migration/core/V139__hrms_request_seed_roles.sql:110`), and the helper never approves one (`W-114.18`, `W-114.21`), so that user most needs to be told where the button is. `platform-admin` and a customer's own roles get no guide: the tool returns their screens only |
| Screen names | A small list shipped with the guides, `help/screens.json`, menu key → plain name | The backend has only label keys (`nav.leave.types`); the plain names live in the frontend (`code/frontend/src/shell/navigation/navLabels.js:17-37`) and the model needs English |
| Asking about another role | Allowed only for a caller holding `core.role.read` | A tenant admin asks "what can each role do"; an employee has no need to read the HR guide |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | Nothing tells a new user what their role is for. The seven system roles are seeded with a code and a display name only. | `code/backend/migration/src/main/resources/db/migration/core/V022__role_action.sql:74-80` |
| 2 | What a user may open is known — the navigation feed — but the helper has no tool that reads it. | `code/backend/core/src/main/java/com/infinevo/core/navigation/NavigationService.java:157`; `W-114-4-…md`, Core tools table |
| 3 | The helper cannot tell which roles the caller holds. | `RoleSource.roleCodesOf` exists (`code/backend/shared/src/main/java/com/infinevo/shared/authz/RoleSource.java:24`) and is used only by `MeController` (`code/backend/shared/src/main/java/com/infinevo/shared/identity/MeController.java:188`) |

## Scope

**In scope**

- One tool, `core.my_role_guide`.
- Six duty guides and the list of screen names.
- One more rule in the system prompt.

**Out of scope**

- How-to steps for a duty — `W-114.11` articles; the guide links to them by id.
- Live numbers inside a duty ("you have 3 approvals waiting") — `W-114.18`, `W-114.19`, `W-114.21` add those tools; the model may call them after this one.
- Turning `app:` links into real links — `W-114.13`.
- Changing what any role holds.

## Flow

```
user: "what can I do here?"  /  "what should I do this week?"
  --> model calls core.my_role_guide {}
        --> roles: the caller's role codes and names
        --> screens: every leaf of the caller's navigation feed, with its plain name and path
        --> guides: for each role that has one, its headings only
  --> model calls core.my_role_guide { "role": "hr" }
        --> the hr guide, minus every line whose screen is not in the caller's feed
  --> answer in plain words, with [Leave requests](app:/leave/requests) links
```

## The tool

| Tool | Input | Returns | `requiredAction` |
|---|---|---|---|
| `core.my_role_guide` | `role` (optional: a role code) | Without `role`: `{ roles: [{ code, name, hasGuide }], screens: [{ name, path }], guides: [{ role, sections: ["Every day", …] }] }`. With `role`: `{ role, name, sections: [{ heading, lines: [{ text, screen: { name, path }, article }] }] }` | `core.ai.use` |

| Rule | Detail |
|---|---|
| Whose roles | The caller's, from `RoleSource.roleCodesOf(tenantId, userAccountId)` — the lookup `MeController` uses (`MeController.java:188`) |
| `role` the caller does not hold | Refused with "not found", unless the caller holds `core.role.read` (`PermissionService.holds`, `code/backend/shared/src/main/java/com/infinevo/shared/authz/PermissionService.java:102`) |
| Lines kept | When the caller holds the role: only lines whose screen is in the caller's feed. When the caller is reading another role by `core.role.read`: every line, each marked `"forThatRole": true`, and no link |
| A role with no guide | `hasGuide: false`; the model answers from `screens` |
| No ids | Role ids, user ids and action codes are not returned. The model never sees an action code |
| Size | A guide is at most 4,000 characters (startup check), so one call fits the 8,000-character tool result limit of `W-114.4` |

## The guide format

`code/backend/core/src/main/resources/help/roles/<role code>.md`:

```
---
role: hr
title: HR
---
## Every day
- Check who is on leave and who has asked for leave. {screen:core.leave.requests} {article:leave-requests-review}
- …
## Every week
## Every month
## When someone joins or leaves
```

| Marker | Meaning | Checked |
|---|---|---|
| `{screen:<menu key>}` | The line needs this screen. **Required on every line.** | At startup: present. In the `app` test: the key exists in the full menu |
| `{article:<id>}` | The how-to for this line | At startup: the id is a loaded article |

`help/screens.json` maps every leaf menu key to a plain name: `{ "core.leave.types": "Leave types", … }`.

## Guide outlines

Each line below is one duty with its screen. The duties are limited to what the role's seeded
actions allow (`V022__role_action.sql:95-…` and the later seed scripts `V135`, `V139`, `V148`,
`V171`, `V176`, `V177`, `V184`, `V189`).

**`employee`** — holds own-data actions only: `core.leave.apply`, `core.wfh.apply`, `hrms.attendance.mark`, `hrms.timesheet.submit`, `payroll.payslip.read_own`, `payroll.tax_declaration.declare_own`.

| When | Duty | Screen key |
|---|---|---|
| Every day | Clock in and out | `hrms.attendance` |
| Every day | Fill today's timesheet | `hrms.timesheets` |
| Every week | Submit the week's timesheet | `hrms.timesheets` |
| When needed | Apply for leave or work from home; fix a missed clock-in | `core.me`, `hrms.regularizations` |
| Every month | Read the payslip | `core.me` |
| Once a year | Declare tax savings and upload proofs | `core.me` |

**`manager`** — holds `core.approval.decide`, `core.leave.approve`, `hrms.timesheet.approve`, team read actions.

| When | Duty | Screen key |
|---|---|---|
| Every day | Approve or reject what is waiting | `core.approvals.inbox` |
| Every week | Review the team's timesheets | `hrms.timesheet_review` |
| Every week | Check the team dashboard | `hrms.dashboard` |
| When away | Hand approvals to someone else | `core.approvals.delegations` |

**`hr`** — holds employee, org, leave, WFH, attendance and loss-of-pay management.

| When | Duty | Screen key |
|---|---|---|
| Every day | Act on leave and WFH requests that reach HR | `core.approvals.inbox`, `core.leave.requests`, `core.wfh.requests` |
| Every day | Check attendance and regularization requests | `hrms.attendance_log`, `hrms.regularizations_all` |
| Every week | Keep departments, designations and locations right | `core.org.departments`, `core.org.designations`, `core.org.locations` |
| Before month end | Enter loss of pay days for the month | `hrms.loss_of_pay` |
| When someone joins | Add the employee | `core.employee` |
| Once a year | Review leave types and give the new year's leave | `core.leave.types`, `core.leave.allocations` |

**`payroll-officer`** — holds `payroll.run.execute`, `payroll.salary.manage`, `payroll.structure.manage`, `payroll.settings.manage`, `payroll.tax_declaration.verify`.

| When | Duty | Screen key |
|---|---|---|
| Before the run | Set salary for joiners and revisions | `core.employee` |
| Before the run | Check claims and deductions | `payroll.claims`, `payroll.deductions` |
| Every month | Create, compute, check and send the pay run for approval | `payroll.runs` |
| After payment | Release payslips | `payroll.runs` |
| When rules change | Keep components and statutory settings right | `payroll.components`, `payroll.settings` |

**`tenant-admin`** — holds user, role and company settings actions.

| When | Duty | Screen key |
|---|---|---|
| At the start | Finish the setup checklist | `core.setup` |
| At the start | Invite HR and payroll staff and give them roles | `core.users`, `core.roles` |
| When needed | Keep the company profile right | `core.settings.company` |
| When needed | Set who approves what | `core.approvals.definitions` |
| Every month | Read the audit log | `core.audit` |

**`finance`** — holds `payroll.run.read`, `payroll.run.approve`, `payroll.payslip.read`, `payroll.salary.read`, `payroll.structure.read`, `payroll.reimbursement_claim.read`, `payroll.employee_deduction.read`, and the export actions `payroll.bank_file.export`, `payroll.statutory_report.read`, `payroll.report.export` (`V139__hrms_request_seed_roles.sql:106-116`). It does **not** hold `core.approval.decide`, so the Approvals inbox is not in its menu.

| When | Duty | Screen key |
|---|---|---|
| Every month | Check the computed pay run before approving: totals, the change from last month, the unusual cases | `payroll.runs` |
| Every month | Approve the pay run. This is done on the pay run screen with the Approve button (`code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/PayRunController.java:199`); the helper can summarise the run (`W-114.21`) and never approves it | `payroll.runs` |
| Every month | Read the claims and deductions going into the run | `payroll.claims`, `payroll.deductions` |
| When needed | Read the payroll dashboard | `payroll.dashboard` |

The finance guide names no export screen: the bank file and the statutory reports have no
menu item on `main` today, and a duty line must name a screen.

The guides state duties; they do not promise the helper can do them. Running or approving a
pay run stays on the pay run screen (founder, 2026-11-23).

## Backend changes

All under `code/backend/core/src/main/java/com/infinevo/core/ai/help/`.

| Layer | File | Change |
|---|---|---|
| Record | `RoleGuide.java` | **New.** Role code, title, sections, lines with their screen key and article id |
| Loader | `HelpLibrary.java` (`W-114.11`) | Also loads `help/roles/*.md` and `help/screens.json`. Startup fails on: unknown role code, a line with no `{screen:}`, an unknown `{article:}`, a guide over 4,000 characters |
| Tool | `RoleGuideAiToolProvider.java` | **New.** `implements AiToolProvider`; `core.my_role_guide` |
| Prompt | `ai/AiSystemPrompt.java` | One more fixed rule: "For a question about what the user can do, or should do, call `core.my_role_guide`. Say only what it returns. Never tell the user they can do something it did not list." |
| Content | `code/backend/core/src/main/resources/help/roles/{employee,manager,hr,payroll-officer,tenant-admin,finance}.md`, `help/screens.json` | **New.** |

No endpoint is added.

## Database changes

None.

- [x] Creates no table
- [x] No Flyway script
- [x] No money value
- [x] No destructive step
- [x] `core` reads roles through `RoleSource` and the menu through `NavigationService`; the test that needs `hrms` and `payroll` lives in `app`

## Frontend changes

None. **Routes added:** none.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/help/RoleGuideLoadTest.java` | The six shipped guides load; each startup check fails on a fixture |
| Unit | `core/src/test/.../ai/help/RoleGuideAiToolProviderTest.java` | Caller with `hr` and `manager` gets both; a line whose screen is not in the feed is dropped; a custom role gives `hasGuide: false`; `role` not held is "not found" without `core.role.read` and returned with it; no action code and no id in any result |
| Test | `app/src/test/.../help/HelpArticlesMatchTheMenuTest.java` (extend, `W-114.11`) | Every `{screen:}` key in every guide is a key in the full menu; every leaf key of the full menu has a name in `screens.json` |
| Unit | `core/src/test/.../ai/AiChatServiceImplTest.java` (extend) | Stub scripted to call the tool then answer |

**Break the guard once:** remove one key from `screens.json` and see the `app` test fail.

## Verification

```bash
cd code/backend && ./mvnw -B -pl core,app -am verify
```

| Check | Expected | Result |
|---|---|---|
| `core.my_role_guide {}` as a seeded HR user | `roles` has `hr`; `screens` equals the leaves of that user's `GET /api/v1/navigation`; `guides` has `hr` | not run |
| `core.my_role_guide { "role": "hr" }` for a customer without HRMS | No line that needs an `hrms.*` screen | not run |
| Same after the customer's admin removed `core.leave_type.manage` from `hr` | The "leave types" line is gone | not run |
| `{ "role": "payroll-officer" }` as an employee | "not found" | not run |
| Same as a tenant admin | The whole guide, lines marked `forThatRole` | not run |
| A user holding only a customer-made role | `hasGuide: false`, screens listed | not run |
| A guide line with no `{screen:}` | The application does not start | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A guide describes a duty the customer does differently | Medium | Guides are short and general; the founder reviews them; lines the user cannot do are dropped |
| The model adds duties of its own | Medium | The prompt rule; the stub test; founder check on the test company before `W-114.10` |
| `screens.json` and the frontend labels drift apart | Low | The `app` test forces a name for every menu key; a different wording is harmless |
| A new menu item ships without a name | Certain to be tried | The `app` test fails until the name is added |

## Rollback

Revert the commit. The tool and its prompt rule go. No data to clean up.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` | `RoleGuide`, the loader additions, `screens.json`, tests |
| 2 | `core` | `RoleGuideAiToolProvider`, the prompt rule |
| 3 | `core` | The six guides, written from the outlines above and the feature specs |
| 4 | `app` | The two added checks in `HelpArticlesMatchTheMenuTest` |
