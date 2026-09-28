# Feature: Approval screens

| Field | Value |
|---|---|
| **Feature ID** | `W-46.4` · from ticket #61 · `CORE-11` |
| **Spec file** | `docs/target-state/features/W-46-4-approval-screens.md` |
| **Owner** | biren |
| **Apps touched** | `code/frontend/src/core/approvals` · `code/backend/core` — `NavigationCatalogue.java` menu items only |
| **Related gaps** | DEBT-025 (discounted), DEBT-026 (prevented) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-28 |
| **Blocked by** | `W-45`; `W-15.1`, `W-15.2`, `W-15.3` (karma) — every endpoint is spec-only today. `W-16.3` for the first real item in the inbox |
| **Size** | **M** |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` — menu items, nothing else | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an approver sees everything waiting on them in one place and decides it there, whichever flow it came from | 1 |
| Frontend area | `src/core/approvals` | 1 |

Within cap.

---

## 1. Problem

The frozen systems have five hard-coded approval paths and five screens for them (`01-platform-shape.md:72`). The engine `W-15` replaces them with one instance-and-step model and one `decide` call for every flow type (`W-15-2-approval-lifecycle.md` §2).

| Frozen screen | What it hard-codes | Ported? |
|---|---|---|
| `legacy/HRMS_Frontend/src/components/adminDashboard/LeaveRequest.jsx:80-82,266-270` | two queues, `PENDING_MANAGER_APPROVAL` and `PENDING_HR_APPROVAL`; `PUT /leaves/{id}/status` | **no** — the two stages become two steps of one `LEAVE` definition (`W-15-1`) |
| `legacy/HRMS_Frontend/src/components/adminDashboard/OvertimeRequest.jsx` | overtime queue | no |
| `legacy/Payroll-Fend-react/src/pages/mainPages/approval/approvalView.js:113,126,422` | investment-proof approval with "consider for IT declaration" | **partly** — the `approvedAmount` on a decision (`W-15-2` §2) carries the amount; the proof-specific view stays with `W-47.3` |
| `.../approval/salaryRevisionApproval.js`, `viewReviseSalary.js` | salary revision approval | no — not a `W-15.1` flow type; `W-47.1` decides |

The engine's own screens do not exist in either frozen system: definitions (who approves what, in which order), delegations, history, reassignment.

## 2. Scope

**In scope**

- My inbox: every step assigned to me, across flow types, with a summary of the item, approve / reject with comment, and `approvedAmount` when the flow type carries one
- Instance detail: the item summary, steps with assignees and decisions, the history trail
- My delegations: create (delegate, from, to, flow types), list, delete
- Administration: approval definitions per flow type (steps: kind, assignee, escalate after days, per item); reassign a step
- Menu items under `core.approvals`
- A `pendingCount` slice value the shell header may show as a badge (`W-45`'s `Header.jsx` reads the store; this ticket does not edit the header)

**Out of scope**

- Rendering the full item (the leave request's dates and balance, the claim's lines). The inbox shows the summary the instance carries; a "View item" link goes to the owning screen (`/leave/requests/:id` from `W-46.2`; Payroll routes from `W-47`)
- Notifications — `W-20.1`
- Escalation and reminder *rules* — configured through the definition's `escalateAfterDays`; the scheduler is `W-15.3`

## 3. Flow

```
[approver] --> /approvals                 --> approvalService.pending(page)
                 decide                   --> approvalService.decide(stepId, {decision, comment, approvedAmount})
           --> /approvals/:instanceId     --> approvalService.get(id) + history(id)
           --> /approvals/delegations     --> delegationService.list({employeeId: me}) / create / remove
[admin]    --> /approvals/definitions     --> definitionService.list() / save(flowType, {steps})
           --> instance detail, Reassign  --> approvalService.reassign(instanceId, {employeeId, reason})
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Config | `code/backend/core/.../navigation/NavigationCatalogue.java` | add `core.approvals` (`nav.approvals`, `/approvals`, target `/api/v1/approvals/pending`, module `null`, action `core.approval.decide`) with children `core.approvals.inbox` (same), `core.approvals.delegations` (`/approvals/delegations`, target `/api/v1/approval-delegations`, `core.approval.delegate`), `core.approvals.definitions` (`/approvals/definitions`, target `/api/v1/approval-definitions`, `core.approval_definition.manage`) |

No `W-15.x` spec adds these items; they ride here, after `W-15` is on `main`.

**API contract** — consumed: `W-15-1` §4 (definitions), `W-15-2` §4 (`pending`, `{instanceId}`, `steps/{stepId}/decide`), `W-15-3` §4 (delegations, `history`, `reassign`). **Assumed of the instance response:** `flowType`, `subjectEmployee` (id and name), `summary` (one line), `itemId`, `createdAt`. If `W-15.2` ships without a `summary`, that is a `W-15.2` defect, not a reason to fetch the item from every module here.

## 5. Frontend changes

`W-45` contract throughout.

| File | Change |
|---|---|
| `src/core/approvals/approvalService.js` | **new.** `pending(page)`, `get(instanceId)`, `history(instanceId)`, `decide(stepId, body)`, `reassign(instanceId, body)` |
| `src/core/approvals/delegationService.js` | **new.** `createService('/v1/approval-delegations')` spread; `list({employeeId, activeOn})` |
| `src/core/approvals/definitionService.js` | **new.** `list(flowType)`, `save(flowType, body)` → `PUT /v1/approval-definitions/{flowType}` |
| `src/core/approvals/approvalSlice.js` | **new.** `{ pendingCount }`, refreshed after every decision and on inbox load |
| `src/core/approvals/Inbox.jsx` | **new.** Paged table: flow type `Tag`, subject employee, summary, waiting since, step (n of m). Row actions Approve / Reject open `DecideModal`. Filter by flow type client-side on the page. "View item" link per flow type from `itemRoutes.js` |
| `src/core/approvals/DecideModal.jsx` | **new.** Decision fixed by the button pressed; comment (required on reject, ≤ 1000); `approvedAmount` `InputNumber` with two decimals shown only for `REIMBURSEMENT` and `PROOF_OF_INVESTMENT`, sent as a string so no float leaves the browser |
| `src/core/approvals/InstanceDetail.jsx` | **new.** `Descriptions` of the instance; steps table with assignee, decision, comment, decided at; history `Timeline` (delegations, escalations, reassignments); Reassign button when `useCan('core.approval.manage')` |
| `src/core/approvals/Delegations.jsx` | **new.** My active and past delegations; create `Drawer`: delegate (employee `Select` from `@core/employee`), from, to, flow types multi-select; delete |
| `src/core/approvals/Definitions.jsx` | **new.** One `Tabs` per flow type; each an ordered list of steps with kind (`REPORTING_MANAGER`, `INDIRECT_MANAGER`, `APPROVER_LEVEL_1..3`, `ROLE`, `NAMED_EMPLOYEE`, `PROJECT_MANAGER`), assignee (role or employee when the kind needs one), escalate after days, per-item flag. Save writes the whole list. Flow types from `W-15-1` §4: `LEAVE`, `REGULARIZATION`, `OVERTIME`, `REIMBURSEMENT`, `PROOF_OF_INVESTMENT`, `PAY_RUN`, `TIMESHEET` |
| `src/core/approvals/itemRoutes.js` | **new.** `flowType → path builder`. `LEAVE → /leave/requests/:itemId`; others `null` until their screen tickets register one. A `null` hides the link |
| `src/core/index.js` | `routes` gains the four below; `reducers` gains `approvals` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/approvals` | `Inbox` | inside `AppShell`; present when the feed carries `core.approvals.inbox` |
| `/approvals/:instanceId` | `InstanceDetail` | same |
| `/approvals/delegations` | `Delegations` | `core.approvals.delegations` |
| `/approvals/definitions` | `Definitions` | `core.approvals.definitions` |

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `src/core/approvals/*Service.test.js` | paths and methods; `decide` posts to `/v1/approvals/steps/{stepId}/decide` |
| Component | `Inbox.test.jsx` | rows across two flow types render; approve calls `decide` with `APPROVED`; reject without comment is blocked; `pendingCount` drops after a decision |
| Component | `DecideModal.test.jsx` | amount field appears only for the two money flows and is sent as a string |
| Component | `Definitions.test.jsx` | reorder and save sends the ordered steps; `ROLE` kind reveals the role select |
| Component | `Delegations.test.jsx` | create sends four fields; delete confirms |
| Backend unit | `NavigationCatalogueTest` | the three leaves point at existing `GET`s |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
cd ../backend && ./mvnw -pl core test -Dtest=NavigationCatalogue*
docker compose -f infra/docker/compose.yml up -d
```

| Check | Expected |
|---|---|
| definition | as admin@acme.local set `LEAVE` to two steps: reporting manager, then role HR |
| inbox | an employee applies for leave (`W-46.5`, or `curl` the `W-16.3` endpoint); the manager's inbox shows one row with the leave summary; HR's shows nothing yet |
| decide | manager approves; HR's inbox shows the row; HR approves; the request is `APPROVED` at `/leave/requests/:id` |
| reject | a rejection without a comment is blocked; with one, the trail shows it |
| delegation | the manager delegates to a colleague for today; the colleague's inbox shows the row |
| Payroll-only | a tenant holding Payroll only sees the inbox and definitions for `REIMBURSEMENT` and `PROOF_OF_INVESTMENT` (`D-36`, `D-37`) |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The instance carries no item summary and the inbox is a list of ids | medium | named in §4 as a `W-15.2` requirement; check the spec's response before building the row |
| Each module wants its own inbox again (the frozen pattern) | medium | `itemRoutes.js` is the only per-flow code; a module registers a path, not a screen |
| `approvedAmount` handled as a JavaScript number | low | sent as a string; the test asserts it |

## 10. Rollback

Revert the branch; the catalogue rows go with it.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money` / `BigDecimal` | `approvedAmount` crosses the wire as a string with two decimals; never a float in the client |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/core/approvals` imports `@shared/*`, `@shell/screens`, `@core/employee`; Payroll and HRMS register a route string in `itemRoutes.js`, never a component |

## 12. Gap inventory

| ID | Overlap | Decision |
|---|---|---|
| DEBT-025 (two POI controllers) | proof approvals | **discounted** — the proof screen is `W-47.3`; here it is one row |
| DEBT-026 (admin/user file pairs) | approval views | **prevented** — one inbox for every flow |

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` (backend) | catalogue rows and test |
| 2 | `src/core/approvals` | services, slice, `itemRoutes.js`, tests |
| 3 | `src/core/approvals` | `Inbox`, `DecideModal`, `InstanceDetail`, tests |
| 4 | `src/core/approvals` | `Delegations`, `Definitions`, tests; `src/core/index.js` registration |

## 14. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | One inbox or one per flow? | **One.** The engine exists so the screen can be one; per-flow lists are `Tag` filters on it |
| 2 | Who renders the item? | **The owning screen**, by link. The inbox never imports a module component |
| 3 | Badge in the header? | **Slice only.** `W-45` owns `Header.jsx`; reading `pendingCount` there is a one-line follow-up, not this ticket |
