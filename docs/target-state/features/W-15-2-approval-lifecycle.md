# Feature: Approval instance lifecycle and routing

| Field | Value |
|---|---|
| **Feature ID** | `W-15.2` · from ticket #16 · `CORE-11` |
| **Promoted to** | `docs/target-state/features/W-15-2-approval-lifecycle.md` on branch `W-15-2-approval-lifecycle` — **`W-15-2` with hyphens**, never `W-15.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-15.1` (a definition), `W-14.2` (the reporting line it routes by), `W-11.3` (the `core.approval.decide` code). **Not** `W-16.3` — leave is wired there, against this engine, so the block runs one way |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 row 7 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 2 scripts, one table each — aggregate exception | 1 — exception granted 2026-09-22 |
| Externally testable behaviour | a request routes to the right approver, and on the last approval the consumer's handler fires exactly once, after commit | 1 |
| Frontend area | none | 1 |

Within cap. `approval_step` is the instance's detail and has no life without it.

---

## 1. Problem

`W-15.1` defined what an approval looks like and deliberately ran nothing. This ticket runs
it, and it has to absorb every difference the five frozen flows encode in `if` statements.

The routing is the part the frozen system cannot do at all. Leave finds its approver by
matching the **caller's** role name — `LeaveRequestController.java:185-187` accepts three
spellings of "reporting manager" — which means the system never decides who *should* approve;
it only checks whether whoever turned up is allowed to. Nobody is notified, nothing is
assigned, and a request with no manager set simply waits forever.

Two other behaviours must survive the move:

- **Strict versus permissive ordering.** Leave enforces manager-then-HR at `:200-204`; overtime does not
- **Side effects on the final approval.** Overtime sets `compOffCreated`; reimbursement moves a payment status. An engine that only flips a status silently breaks both

`W-15.1` left `ApprovalOutcomeHandler` as the seam for the second. This ticket calls it.

## 2. Scope

**In scope**

- `core.approval_instance` — one per thing awaiting approval
- `core.approval_step` — one per step, with its assignee, decision, comment and timestamps
- Routing: resolving each step's approver from the reporting line, a role, or a named employee
- Acting: approve, reject, with a comment, honouring the definition's ordering
- Calling the consumer's `ApprovalOutcomeHandler` exactly once on completion, **after commit**
- A decision carrying an optional `approvedAmount`, so reimbursement and proof of investment fit without a second engine
- A **stub** consumer, in tests only, that proves the contract end to end

**Out of scope**

- Delegation, escalation, reminders, reassignment and the history view — `W-15.3`
- Notifying an approver — `W-20.1` composes, this ticket raises the event
- **Every real consumer.** Leave wires in `W-16.3`; reimbursement, proof, overtime, pay run and timesheet each move in their own ticket once the engine is proven. The first draft wired leave here, which made `W-15.2` and `W-16.3` block each other (`12-core-contracts.md:144`)
- Amount-based *routing* rules — `W-15.1` decision 2. Recording an amount on a decision is in; choosing an approver by amount is not

## 3. Flow

```
[consumer, e.g. W-16.3 leave submitted]
  --> [ApprovalService.start(ApprovalFlowType, SubjectRef(table, id), employeeId)] --> instanceId
  --> definition (W-15.1) --> resolve step 1 approver via ReportingLineService.chainAbove(employee, asOf) (W-14.2)
  --> [core.approval_instance] + [core.approval_step, first assigned]

[approver] --> POST /approvals/steps/{stepId}/decide {decision, comment, approvedAmount?}
  --> ordering honoured --> next step assigned, or instance completes --> COMMIT
  --> after commit --> ApprovalOutcomeHandler.onApproved(instance, decisions) / onRejected(...)  (exactly once)
```

The seam is exactly `12-core-contracts.md:92-93`: `start` returns the `instanceId`, which the
consumer stores against its own row; the handler is the Spring bean whose `flowType()` matches.

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../approval/ApprovalController.java` | new |
| Service | `core/.../approval/ApprovalService.java` | new |
| Service | `core/.../approval/CoreApproverResolver.java` | new — implements `W-15.1`'s `ApproverResolver` for `REPORTING_MANAGER`, `INDIRECT_MANAGER`, `APPROVER_LEVEL_n`, `ROLE`, `NAMED_EMPLOYEE`; `PROJECT_MANAGER` is looked up as a bean another module registers, and is unassignable if none is |
| Service | `core/.../approval/OutcomeDispatcher.java` | new — the after-commit hook that finds the handler by `flowType()` and calls it once |
| Record | `core/.../approval/SubjectRef.java` | new — `(String table, UUID id)` |
| Entity | `core/.../approval/ApprovalInstance.java`, `ApprovalStep.java` | new, each `@Table(schema="core")` |
| Repository | two | new |
| Enumeration | `core/.../approval/ApprovalDecision.java`, `InstanceStatus.java` | new |
| Test double | `core/src/test/.../approval/StubApprovalOutcomeHandler.java` | new — a handler for `REGULARIZATION`, which has no consumer yet, recording every call |

**API contract**

| Method | Path | Request | Response | `@RequiresAction` |
|---|---|---|---|---|
| GET | `/api/v1/approvals/pending` | `?page=` | steps assigned to the caller | `core.approval.decide` |
| GET | `/api/v1/approvals/{instanceId}` | — | the instance with every step and decision | `core.approval.decide` |
| POST | `/api/v1/approvals/steps/{stepId}/decide` | decision, comment, **approvedAmount?** | `200` | `core.approval.decide`, assignee only |

All Bearer, tenant bound. Codes per `12-core-contracts.md:60`, added by `W-11.3`
(`12-core-contracts.md:128`).

**`approvedAmount` is optional and is money.** Reimbursement approves an amount that may differ
from the claim (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/employeereimbursement/AdminReimbursementController.java:121-132`);
proof of investment approves one per item before a final decision
(`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/employeeitdeclaration/AdminProofOfInvestmentController.java:283-297,379`).
The engine stores it as `Money` and hands it to the handler in `decisions`; it never
interprets it. For a step marked `per_item` in the definition, one `approval_step` row exists
per item (`item_ref` set), and the step completes when every row is decided.

**A rejected instance ends.** Any step's `REJECTED` completes the instance as rejected;
resubmission is a **new** `start`, never a reopen (decision 2, `12-core-contracts.md:144`).

**Only the assignee may decide.** The frozen system asks whether the caller holds a role; this
asks whether the caller is the person the step was assigned to. That difference is what makes
an approval attributable.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__approval_instance.sql` | `core.approval_instance` | yes | additive |
| `core/V0NN__approval_step.sql` | `core.approval_step` | yes | additive |

Version numbers assigned when the branch is cut — `migration/README.md:17-31`.

`approval_instance`: `id uuid` · `tenant_id uuid NOT NULL` · `flow_type varchar(32) NOT NULL` ·
`definition_id uuid NOT NULL REFERENCES core.approval_definition(id)` ·
`subject_table varchar(64) NOT NULL` · `subject_id uuid NOT NULL` ·
`subject_employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`status varchar(16) NOT NULL` · `outcome_notified_at timestamptz NULL` ·
`started_at timestamptz NOT NULL` · `completed_at timestamptz NULL` · four audit columns.

`approval_step`: `id uuid` · `tenant_id uuid NOT NULL` ·
`instance_id uuid NOT NULL REFERENCES core.approval_instance(id)` ·
`step_index int NOT NULL` · `item_ref varchar(64) NULL` — set only on `per_item` steps ·
`approver_kind varchar(24) NOT NULL` ·
`assignee_employee_id uuid NULL REFERENCES core.employee(id)` ·
`decision varchar(16) NULL` · `comment varchar(1000) NULL` ·
`approved_amount numeric(19,4) NULL` · `decided_at timestamptz NULL` · four audit columns.

- [x] `tenant_id` on both, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, assignee_employee_id, decision)` for the pending list, `(tenant_id, subject_table, subject_id)` for the lookup back, `(tenant_id, instance_id, step_index)`
- [x] Money columns — **one**: `approved_amount numeric(19,4)`, `Money` in Java, per `CONVENTIONS.md` §2
- [x] Expand / contract — new tables only

**`definition_id` is stored on the instance**, so an approval completed under last quarter's
definition stays explainable after the definition changes — the reason `W-15.1` versioned it.

**`outcome_notified_at` is how the handler fires exactly once, after commit.** Completion
commits with `status` and `completed_at` set and `outcome_notified_at` still null. An
after-commit hook (`OutcomeDispatcher`) then opens its own transaction, claims the row —
`UPDATE ... SET outcome_notified_at = now() WHERE id = ? AND outcome_notified_at IS NULL`, and
proceeds only if one row changed — and calls the handler inside that transaction. A handler
that throws rolls the claim back, so the outcome is retried, never lost; a handler that ran
is never run again. The approval itself is already committed, so a failing side effect cannot
undo an approval (`12-core-contracts.md:93`). A handler that creates a compensatory day off
must not create two.

**`subject_table` plus `subject_id` rather than five nullable foreign keys.** A leave request,
a reimbursement and a proof are different tables; a polymorphic reference is honest about that,
and the alternative grows a column per flow forever.

RLS and the `tenant_isolation` policy in the exact `CASE` form in each script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../approval/ApproverResolverTest.java` | each approver kind resolves; an employee with no manager yields an unassignable step rather than a silent wait |
| Unit | `core/.../approval/ApprovalOrderingTest.java` | sequential refuses an out-of-order decision; any-order accepts either first |
| Unit | `core/.../approval/OutcomeHandlerOnceTest.java` | the handler fires once on completion, and not again on a repeated call; a handler that throws leaves `outcome_notified_at` null and is called again on retry |
| Unit | `core/.../approval/ApprovedAmountTest.java` | a decision with `approvedAmount` stores it as `Money` and passes it to the handler in `decisions`; a decision without one stores null; a negative amount is refused |
| Integration | `core/.../approval/StubFlowApprovalIT.java` | a `REGULARIZATION` instance routes to the manager via `chainAbove(employee, asOf)`, then to a second step, and on the last approval `StubApprovalOutcomeHandler.onApproved` is called exactly once **after the completing transaction committed** — asserted by a second connection seeing `status = APPROVED` before the handler returns |
| Integration | `core/.../approval/RejectionEndsInstanceIT.java` | a rejection at step 1 completes the instance as rejected, `onRejected` fires once, and a second `start` for the same subject creates a new instance rather than reopening |
| Integration | `core/.../approval/ApprovalAssigneeIT.java` | a user holding `core.approval.decide` but not the assignment is refused; a user without the code is `403` on all three endpoints |
| Integration | `core/.../approval/ApprovalRlsIT.java` | tenant A cannot see or decide tenant B's steps |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. **No test touches
leave.** The stub proves the contract; `W-16.3` proves leave against it.

`ApproverResolverTest`'s unassignable case matters: in the frozen system a request with no
manager waits forever and nobody is told. Here it is a visible state.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in approval_instance approval_step; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
done
cd code/backend && mvn -q -pl core -Dit.test=StubFlowApprovalIT,RejectionEndsInstanceIT verify
cd code/backend && mvn -q verify

# the engine must know no consumer
grep -rln 'leave\|reimburse\|payrun\|timesheet' core/src/main/java/com/infinevo/core/approval/ \
  && echo "REVIEW: a consumer leaked into the engine" || echo "engine knows no consumer"
```

| Check | Expected |
|---|---|
| RLS on both | `t` twice |
| `StubFlowApprovalIT` | green — two stages, in order, handler fires once, after commit |
| `RejectionEndsInstanceIT` | green — rejected ends; resubmission is a new instance |
| Consumer grep | `engine knows no consumer` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A consumer keeps a private approval path alongside the engine | **high — it is the easier change** | The consumer grep here; `W-16.3` ships without an approve endpoint and proves leave against this engine |
| The handler fires twice and a side effect duplicates | **medium, and visible to customers** | The after-commit claim on `outcome_notified_at`; `OutcomeHandlerOnceTest` |
| The handler runs inside the completing transaction and a failed side effect silently undoes an approval | medium | Handler runs after commit by construction; `StubFlowApprovalIT` asserts the approval is visible before the handler returns |
| A step with no resolvable approver stalls silently, as today | medium | An explicit unassignable state, surfaced in the pending list and tested |
| Ordering is implemented for leave only and overtime breaks later | medium | Both orderings implemented and tested now, even though only leave is wired |
| The polymorphic subject reference loses integrity | medium | `subject_table` is validated against a known set; the reverse lookup index makes orphans findable |

## 10. Rollback

Nothing is deployed. Both scripts are additive and forward-only —
`migration/README.md:135-143`. No consumer is wired in this ticket, so withdrawing the engine
affects nothing outside it.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | both tables, each in its own script |
| Flyway only, `ddl-auto` nowhere | two scripts; none added |
| `Money`/`BigDecimal` for money | `approved_amount numeric(19,4)`, `Money` in Java; no `double` anywhere |
| Index on `tenant_id` plus lookup columns | three indexes, `tenant_id` leading |
| Expand / contract | new tables only |
| No module references another module | `core` only; `payroll` and `hrms` consumers implement a `core` interface when they move |

## 12. Gap inventory

| ID | Decision |
|---|---|
| Approval by caller's role rather than assignment (`LeaveRequestController.java:185-187`) | **Fixed.** Steps are assigned; only the assignee decides |
| No routing — nobody is told they must approve | **Fixed.** Each step resolves an assignee; `W-20.1` notifies |
| Requests with no manager wait forever | **Fixed.** An explicit unassignable state |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

| # | Question | Decision |
|---|---|---|
| 1 | Can a requester approve their own request when the chain points back at them? | **Yes, allowed.** Against my recommendation of refusing and escalating |
| 2 | Does a rejection end the instance? | **Yes.** A new request is raised rather than the old one reopened; resubmission is a new `start` and a new `instanceId` (`12-core-contracts.md:144`) |

**Decision 1 removes a check, and the control it removes is worth naming.** Anyone whose
approval chain resolves to themselves — a director, or a manager with no one above them — can
approve their own leave, reimbursements and investment proofs. The alternative refused the
approval and escalated, which left directors permanently unassignable, so this is a defensible
trade rather than an oversight.

Two things keep it visible rather than silent:

- `core.approval_step` records `assignee_employee_id` and the instance records `subject_employee_id`. Where they match, the audit is explicit that the approver and the requester were the same person
- That makes "which approvals were self-approved last quarter" a report, not an investigation — a good candidate for one of `W-23.1`'s seeded report definitions

`ApprovalAssigneeIT` therefore asserts that self-approval **succeeds** and is recorded, rather
than asserting it is refused.
