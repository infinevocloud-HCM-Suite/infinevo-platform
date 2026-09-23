# Feature: Approval definition — the model, designed against five flows

| Field | Value |
|---|---|
| **Feature ID** | `W-15.1` · from ticket #16 · `CORE-11` |
| **Promoted to** | `docs/target-state/features/W-15-1-approval-definition.md` on branch `W-15-1-approval-definition` — **`W-15-1` with hyphens**, never `W-15.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-14.2` — a step routes by reporting line |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one script, one table — `core.approval_definition` | 1 |
| Externally testable behaviour | a definition for each of the five flows can be expressed and read back | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

There are five approval flows and **no shared approval code anywhere**. Each is hard-coded,
and they disagree in five different ways.

| Flow | Stages | Status columns | First approver | Sequencing |
|---|---|---|---|---|
| Leave (HRMS) | 2 | `reportingManagerStatus`, `hrStatus` | reporting manager | **enforced** — HR cannot act first |
| Overtime (HRMS) | 2 | `managerStatus`, `hrStatus` | manager | **permissive** — either may act |
| Reimbursement (Payroll) | 1 | `status` | admin | n/a |
| Proof of investment (Payroll) | 1 | `status` | hr | n/a |
| Attendance regularization | — | — | — | **does not exist** |

Leave's ladder is in the controller: three spellings of "reporting manager" normalised, HR
gated on the manager having decided — `LeaveRequestController.java:155-222`, with the gate at
`:200-204`.

The differences the engine must absorb are not cosmetic:

- **One status column or two.** A single `status` cannot express "manager approved, HR pending"
- **The same level under two names** — "manager" in overtime, "reporting manager" in leave
- **Strict versus permissive ordering**, which is the difference between a ladder and a set
- **Comments per stage** (leave, overtime) versus **one shared comment** (reimbursement, proof)
- **Side effects on approval.** Overtime sets `compOffCreated`; reimbursement moves a payment status. An engine that only flips a status breaks both

And the fifth flow does not exist at all, so it cannot be ported — it must be designed.

`09-build-order.md:189` is explicit about the failure mode: *"five flows must fit. Design
against all five before building, or it becomes leave approval with adapters."* That is
exactly why this is its own ticket: **it defines the model and proves none of it.**

## 2. Scope

**In scope**

- `core.approval_definition` — what a flow's approval looks like: its steps, their order, who each routes to, and whether order is enforced
- A definition per flow type, seeded per tenant with sensible defaults
- The outcome contract: what the engine promises a consumer on approval and rejection, so side effects have somewhere to live
- A worked definition for **each of the five flows**, written down and tested as data

**Out of scope**

- **Running anything.** No instance, no step, no approval happens in this ticket — `W-15.2`
- Delegation, escalation and history — `W-15.3`
- Changing any consumer. Leave, reimbursement and proof keep whatever they have until `W-15.2`
- Designing attendance regularization's screens — this ticket only proves its approval shape fits

## 3. Flow

```
[tenant admin] --> [ApprovalDefinitionController] --> [ApprovalDefinitionService]
   --> [core.approval_definition under RLS]

[W-15.2, later] --> definitionFor(flowType) --> starts an instance
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../approval/ApprovalDefinitionController.java` | new |
| Service | `core/.../approval/ApprovalDefinitionService.java` | new |
| Entity | `core/.../approval/ApprovalDefinition.java` | new, `@Table(schema="core")` |
| Repository | `core/.../approval/ApprovalDefinitionRepository.java` | new |
| Enumeration | `core/.../approval/ApprovalFlowType.java` | new — the five |
| Enumeration | `core/.../approval/ApproverKind.java` | new — reporting manager, indirect manager, approver level 1/2/3, role holder, named employee |
| Enumeration | `core/.../approval/StepOrdering.java` | new — sequential, any order |
| Contract | `core/.../approval/ApprovalOutcomeHandler.java` | new — the interface consumers implement |

`ApprovalOutcomeHandler` is the answer to the side-effect problem. Overtime creating a
compensatory day off and reimbursement moving a payment status are consumer concerns; the
engine calls the handler and does not know what they do.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/approval-definitions` | `?flowType=` | the tenant's definitions | Bearer, tenant bound |
| PUT | `/api/v1/approval-definitions/{flowType}` | steps, ordering | `200` | Bearer, tenant bound |

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__approval_definition.sql` | `core.approval_definition` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` · `flow_type varchar(32) NOT NULL` ·
`step_ordering varchar(16) NOT NULL` · `steps jsonb NOT NULL` ·
`comment_scope varchar(16) NOT NULL` — per step or shared ·
`is_active boolean NOT NULL DEFAULT true` · `effective_from date NOT NULL` ·
four audit columns.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, flow_type, effective_from DESC)`
- [x] Money columns — none
- [x] Expand / contract — new table only

**Steps are `jsonb`, not a child table.** A step has no identity outside its definition, is
never referenced from elsewhere, and is read whole every time. A child table would add a
migration and a join for no query anyone will write. `core.approval_step`, which the data
model lists at `02-data-model.md:298`, belongs to the **instance** side in `W-15.2` — the
record of what actually happened, which is queried and does need rows.

**`effective_from` versioning**, for the same reason as `W-16.1`'s policy: an approval that
completed last March must stay explainable after the definition changes.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../approval/ApprovalDefinitionValidationTest.java` | a definition with no steps refused; an unknown approver kind refused; a named-employee step without an employee refused |
| Unit | **`core/.../approval/FiveFlowFitTest.java`** | **each of the five flows is expressed as a definition and reads back identically** |
| Integration | `core/.../approval/ApprovalDefinitionRlsIT.java` | tenant A cannot read or edit tenant B's definitions |
| Integration | `core/.../approval/DefinitionVersioningIT.java` | the definition in force on a date is the latest with `effective_from <= date` |

**`FiveFlowFitTest` is the entire point of this ticket.** It encodes leave's strict
two-stage ladder, overtime's permissive two-stage set, reimbursement's single admin step,
proof's single HR step, and a designed regularization flow. If any of the five cannot be
expressed, the model is wrong and it is cheap to find out here rather than after `W-15.2` is
built around it.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.approval_definition'::regclass;"

cd code/backend && mvn -q -pl core -Dtest=FiveFlowFitTest test
cd code/backend && mvn -q verify

# nothing executes an approval in this ticket
grep -rn 'class ApprovalInstance\|approve(' core/src/main/java/com/infinevo/core/approval/ \
  && echo "REVIEW: execution leaked into W-15.1" || echo "definition only"
```

| Check | Expected |
|---|---|
| RLS | `t` |
| `FiveFlowFitTest` | green — all five expressible |
| Execution grep | `definition only` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The model is shaped around leave and the other four need adapters | **high — the named trap** | `FiveFlowFitTest` fails the build if any flow cannot be expressed |
| Execution creeps in because a definition alone feels unfinished | **medium** | Grep in verification; the value here is the model being wrong cheaply |
| Attendance regularization is designed wrongly, having no precedent | medium | It is expressed as a definition only; the screens come later and can change the definition without changing the engine |
| Side effects are built into the engine | medium | `ApprovalOutcomeHandler` is an interface the engine calls and does not implement |
| `jsonb` steps become unqueryable when someone wants "all flows where HR approves" | low | That query belongs to the instance side, which has rows |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only —
`migration/README.md:135-143`. No consumer depends on this ticket until `W-15.2`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.approval_definition` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | creates no money column. **Note:** a threshold rule — "over ₹50,000 needs a second approver" — would introduce one; see decision 2 |
| Index on `tenant_id` plus lookup columns | one index, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `core` only; reimbursement and proof live in `payroll` and implement the handler interface from `core` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| Five hard-coded approval paths, no shared code | **Fixed by replacement**, beginning here |
| Leave's role-name string matching (`LeaveRequestController.java:185-187`) | **Fixed.** Approvers are resolved by kind, never by matching a role name's spelling |
| Attendance regularization has no flow at all | **Designed here**, built later |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Does a tenant edit its own approval definitions, or does the platform set them?** **Recommend** platform-seeded defaults that a tenant admin may edit, because the five flows differ per employer and hard-coding them is what created this problem.
2. **Are amount-based rules in scope for the engine?** "Reimbursements over ₹50,000 need a second approver" is the obvious next request. **Recommend** leaving it out of `W-15.1` and revisiting once the engine runs — it introduces a money column and a rule evaluator, which is a ticket of its own.
3. **Confirm the fifth flow.** Attendance regularization does not exist in the frozen system, so its approval shape is invented. **Recommend** a single step to the reporting manager, matching the lightest of the four real flows.
