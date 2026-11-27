# Feature: AI helper does HR and payroll steps after Confirm — part 2

| Field | Value |
|---|---|
| **Feature ID** | `W-114.29` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai/action`), `code/backend/payroll` (`ai`), `code/frontend/src/shell/assistant` (link lines) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.28` (the same pattern, built first) |

Founder, 2026-11-27, as for `W-114.28`. **Never:** a pay run step or a money decision.

## The steps

Same rules as `W-114.28`. Each step calls the screen's own service method; the exact method and
action are read from the controller when built and cited here then.

| # | Tool → action | What it does | Screen |
|---|---|---|---|
| 1 | `core.propose_leave_allocation` → `core.leave.allocate` | Give one employee, or everyone a policy covers, a leave type's allocation for a year | `/leave/allocations` |
| 2 | `core.propose_wfh_allocation` → `core.wfh.allocate` | The same for work from home | `/wfh/allocations` |
| 3 | `core.propose_delegation` → `core.approval.delegate` | Delegate the caller's approvals to a named colleague for a date range | `/approvals/delegations` |
| 4 | `core.propose_onboarding_assign` → `core.onboarding.assign` | Assign the onboarding checklist to a named new joiner | `/settings/onboarding` |

**Step 5 dropped (founder, 2026-11-27: "No, screen only").** Making the bank file stays on the run page; the helper explains it (`bank-file-download`) and links there. What follows was the case made for it.

**Why step 5 was not a pay run step.** It writes an export record and a file; it does not move the run
between statuses or pay anyone (`payrun/bankfile/BankFileExportController.java:42-66`). The run must
already be approved by a person on the screen. Recording the payment stays on the screen.

**Bulk steps** (1, 2 "everyone"): the card shows how many employees it covers and the first 20 by
name; nothing is written until Confirm; one card, one bulk call — the same call the screen makes.

## Tests

As `W-114.28`, per step, plus: a bulk allocation's card count equals what Confirm writes; a bank
file is refused for a run that is not approved or paid; no tool takes a run id.

## AI helper

This part is itself the AI helper's action layer for these screens.

**As built (2026-11-27, Claude).**

| Step | Tool → action code | Needs | Built as |
|---|---|---|---|
| 1 | `core.propose_leave_allocation` → `core.leave.allocate` | `core.leave_balance.manage` | One employee per card: neither screen nor service has a bulk call. Window from the leave-year start month (April when unset). A type with no policy in effect, or an overlapping allocation, is refused before the card |
| 2 | `core.propose_wfh_allocation` → `core.wfh.allocate` | `core.wfh_balance.manage` | One employee per card; 1 Jan to 31 Dec by default; no policy is named (the screen sends none, the service uses the default). A year already taken is refused before the card and again at Confirm |
| 3 | `core.propose_delegation` → `core.approval.delegate_create` | `core.approval.delegate`, own employee record | The screen's flow types less **Pay Run**, which stays on the screen (no pay run step from the helper). Oneself and both chain cases refused before the card |
| 4 | `core.propose_onboarding_assign` → `core.onboarding.assign` | `core.onboarding.manage` | Assigns the company's active checklist, as the employee's Onboarding tab does; an empty checklist or an employee who already has tasks ends FAILED, not a silent no-op |

People are found by employee number or full name (`NamedEmployee`, every search page read); the payload holds no ids and Confirm finds everything again. Card links: leave and WFH allocations, delegations, and the employee page for onboarding. Tests: four tool tests, `NamedEmployeeTest`, `HrStepsIT` 7, `actionLinks.test.js`.

## Rollback

Revert the commit. The tools are no longer offered; what was made stays.
