# Feature: AI helper lists what waits for my approval and decides one after I confirm

| Field | Value |
|---|---|
| **Feature ID** | `W-114.18` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai/action`), `code/frontend/src/shell/assistant` (one line in `actionLinks.js`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.8` and `W-114.9` on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | The helper lists the caller's pending approvals and proposes one decision; the step is decided only when the user confirms | 1 |
| Frontend area | `shell` — one link line | 1 |

Size **M**.

## The rule

The model can only propose a decision. The approver confirms it on the card. **One decision per
card — there is no "approve all".**

## Decisions

| Point | Choice | Why |
|---|---|---|
| Which approvals | `LEAVE`, `WORK_FROM_HOME`, `REGULARIZATION`, `OVERTIME`, `TIMESHEET` | These are the flow types with no money attached (`approval/ApprovalFlowType.java`) |
| **Never offered** | `PAY_RUN`, `REIMBURSEMENT`, `PROOF_OF_INVESTMENT` — not listed for decision and refused if named | Founder 2026-11-23: the helper never approves a pay run. The other two carry `approvedAmount` (`ApprovalDecideRequest.java`); money decisions stay on the screen. They are still **counted** in the list, as "N more on the Approvals screen" |
| How the model names a step | By its **position** in the list, plus the requester's employee number and the flow type as a cross-check. The server runs the same query again and refuses when the row at that position does not match | The list is ordered oldest first by the repository (`ApprovalStepRepository.java:25`), so a position is stable unless the list changed — and then the cross-check catches it. No id reaches the model |
| Comment | Optional on approve. **Required on reject**, 500 characters | The service does not require one (`ApprovalService.java:244-245`); a rejection with no reason helps nobody, and the card should show it |
| Delegated and unassigned steps | Listed exactly as the Approvals screen lists them | The tool calls the same `getPendingSteps` (`ApprovalService.java:341-355`), which already applies delegation and the `core.approval.manage` case |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | A manager must open the Approvals inbox to see or decide anything. | `code/backend/core/src/main/java/com/infinevo/core/approval/ApprovalController.java:42-48`, `:84-90` |
| 2 | A step that someone else decided first answers `400` with "has already been decided". | `ApprovalService.java:189-191`; `ApprovalController.java:98-102` |

## Scope

**In scope**

- Read tool `core.my_pending_approvals`.
- Propose tool `core.propose_approval_decision`; executor `core.approval.decide`.

**Out of scope**

- Bulk decisions; reassign; delegation set-up; pay run, reimbursement and proof-of-investment decisions.
- The detail of the item beyond the summary the inbox already shows.

## Flow

```
"what is waiting for me?"
  --> core.my_pending_approvals --> ApprovalService.getPendingSteps(page 0, 20)
        --> rows: position, type, requester name + employee number, summary, waiting since   (no ids)

"approve the second one"
  --> core.propose_approval_decision { position: 2, employeeNumber: "E-014", flowType: "LEAVE", decision: "APPROVED" }
        --> run the query again --> row 2 matches the cross-check? --> flow type allowed?
        --> AiActionService.propose("core.approval.decide", payload { stepId, decision, comment }, summary, fields)

Confirm --> W-114.8 re-checks --> ApprovalDecideExecutor --> ApprovalService.decide(stepId, request)
```

## Backend changes

Paths are under `code/backend/core/src/main/java/com/infinevo/core/`.

| Layer | File | Change |
|---|---|---|
| Tools | `ai/action/ApprovalToolProvider.java` | **New.** The two tools below |
| Executor | `ai/action/ApprovalDecideExecutor.java` | **New.** `actionCode` and `requiredAction` `core.approval.decide`. Calls `ApprovalService.decide(stepId, new ApprovalDecideRequest(decision, comment))` — what `ApprovalController.java:86-89` calls. Never sends `approvedAmount`. Refuses a step whose flow type is not in the allowed five, even if the payload names it |

**The tools**

| Tool | Input | Does | `requiredAction` |
|---|---|---|---|
| `core.my_pending_approvals` | `page` (optional, 20 a page) | `getPendingSteps`. Per row: `position`, `flowType`, `subjectEmployeeName`, the requester's employee number, `summary`, `createdAt` as "waiting N days", and `decidable` (false for the three excluded types). Plus `total` from the page | `core.approval.decide` |
| `core.propose_approval_decision` | `position`, `employeeNumber`, `flowType`, `decision` (`APPROVED` / `REJECTED`, `approval/ApprovalDecision.java`), `comment` | Re-runs the query; checks the row; requires a comment on `REJECTED`; proposes. Card fields: Request, Requested by, Details, Waiting since, Decision, Comment | `core.approval.decide` |

`ApprovalDecideExecutor.requiresEmployeeProfile()` returns `false` (`W-114.8`, step 7): an approver is
identified by user, not by an employee record — `ApprovalController` never asks for one
(`approval/ApprovalController.java:84-85`).

The service's own checks run at confirm: the caller is the assigned approver or holds
`core.approval.manage` for an unassigned step (`ApprovalService.java:193-210`); earlier steps of a
sequential flow are approved (`:222-234`); the step is still undecided (`:189-191`). Each refusal
sets the intent `FAILED` with the service's message, and the card shows it:
"Step … has already been decided".

`result`: `{ "instanceId", "decision", "traceId" }`.

## Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/shell/assistant/actionLinks.js` | Add `core.approval.decide` → `/approvals/{instanceId}` (`code/frontend/src/core/index.js:143`) |

**Routes added:** none.

## Database changes

None.

- [x] No new table, no migration
- [x] No money value is read, sent or decided
- [x] Stays inside `core`

## What is sent to the model

Names of the people whose requests wait for this approver, their employee numbers, and the
inbox's one-line summary (for leave: type and dates). The caller already sees all of it on the
Approvals screen. `W-114.4`'s masking filter still applies.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/action/ApprovalToolProviderTest.java` | The list carries no id; a `PAY_RUN` row is `decidable: false`; proposing it is refused; a position whose row no longer matches is refused with "the list changed"; reject with no comment is refused; nothing is written on a refusal |
| Unit | `core/src/test/.../ai/action/ApprovalDecideExecutorTest.java` | Never passes `approvedAmount`; refuses an excluded flow type planted in the payload |
| Integration | `core/src/test/.../ai/action/ApprovalDecisionIT.java` | Confirm approves the step and the leave request becomes `APPROVED`; a second confirm decides nothing more; a step decided by someone else between proposal and confirm ends `FAILED` with the service's message; a user who is not the assignee cannot get the step into a proposal |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core -am verify
cd code/frontend && npx vitest run src/shell/assistant && npm run lint
```

| Check | Expected | Result |
|---|---|---|
| The commands | Build success | not run |
| "What is waiting for my approval?" as a manager with three leave steps | Three rows, oldest first, with names and dates | not run |
| Same as an employee with no approver action | The tool is not offered; the helper says it cannot see approvals | not run |
| "Approve the first one" | A card; the step is undecided until Confirm; then the request is approved and shows so on the Leave screen | not run |
| "Reject the second" with no reason | The helper asks for a reason; no card | not run |
| "Approve them all" | The helper says it decides one at a time | not run |
| A pending `PAY_RUN` step exists | Listed as "on the Approvals screen"; "approve the pay run" is refused | not run |
| Another approver decides the step before Confirm | `409 AI_ACTION_FAILED`, "has already been decided" on the card | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The model picks the wrong row | Low | Position plus cross-check; the card shows requester and details before anything happens |
| A money or pay run approval slips through | Low | Refused in the tool **and** in the executor; tested in both |
| A manager approves from a one-line summary without reading the request | Medium | The card links to `/approvals/{instanceId}`; one decision per card slows rubber-stamping |

## Rollback

Revert the commit. The two tools are no longer offered. Decisions already made stay.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` (`ai/action`) | The tool provider, the executor, tests |
| 2 | frontend `shell` | One line in `actionLinks.js` and its test |
