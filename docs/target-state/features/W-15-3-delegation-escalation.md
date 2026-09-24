# Feature: Approval delegation, escalation and history

| Field | Value |
|---|---|
| **Feature ID** | `W-15.3` · from ticket #16 · `CORE-11` |
| **Promoted to** | `docs/target-state/features/W-15-3-delegation-escalation.md` on branch `W-15-3-delegation-escalation` — **`W-15-3` with hyphens**, never `W-15.3`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-15.2` (a running engine), `W-20.2` (the scheduler escalation needs) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one script, one table — `core.approval_delegation` | 1 |
| Externally testable behaviour | a step assigned to an absent approver reaches their delegate, and an overdue step escalates | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

Nothing in either frozen product handles an approver being unavailable.

- No delegation exists in any of the five flows. A manager on leave means their team's requests wait
- No escalation exists. An untouched request stays untouched; the only reminders in the system are timesheet reminders on a scheduler whose crons are **all disabled** — `ReminderScheduler`
- No history view. Leave keeps `reportingManagerComment` and `hrComment` on the request row itself, so the trail is two columns and disappears if a stage is reused

`W-15.2` already stores every step with its decision, comment and timestamp, so the history
exists as data. What is missing is the reading of it, and the two mechanisms that keep an
approval moving when a person does not.

This is the ticket that makes the engine survive contact with holidays and notice periods.

## 2. Scope

**In scope**

- `core.approval_delegation` — A delegates to B, for a date range, optionally per flow type
- Assignment honouring an active delegation at the moment a step is assigned
- Escalation: a step untouched for longer than the definition's threshold moves up the reporting line
- A history endpoint returning the full trail of an instance, including delegated and escalated assignments
- The escalation sweep as a `@Scheduled` + `@SchedulerLock` job, using the ShedLock that `W-52` already merged

**Out of scope**

- The scheduler itself — `W-20.2`
- Notification content and delivery — `W-20.1` composes, this raises the event
- Reassigning an approval by hand — see decision 2
- Changing the definition model; the escalation threshold is a field `W-15.1` already allows in `steps`

## 3. Flow

```
[step assigned by W-15.2] --> active delegation for that approver and flow?
   --> yes: assign to the delegate, recording both --> no: assign as resolved

[scheduler, W-20.2] --> [EscalationSweep]
   --> steps pending longer than threshold --> reassign up the reporting line (W-14.2)
   --> record the escalation on the step
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../approval/DelegationController.java` | new |
| Controller | `core/.../approval/ApprovalController.java` | change — add the history endpoint |
| Service | `core/.../approval/DelegationService.java` | new |
| Service | `core/.../approval/EscalationService.java` | new |
| Job | `core/.../approval/EscalationSweep.java` | new — `@Scheduled` + `@SchedulerLock`, run by `worker` |
| Entity | `core/.../approval/ApprovalDelegation.java` | new, `@Table(schema="core")` |
| Entity | `core/.../approval/ApprovalStep.java` | change — two nullable columns, expand-style |
| Repository | `core/.../approval/ApprovalDelegationRepository.java` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/approval-delegations` | delegateId, from, to, flowTypes | `201` | Bearer, tenant bound |
| GET | `/api/v1/approval-delegations` | `?employeeId=&activeOn=` | list | Bearer, tenant bound |
| DELETE | `/api/v1/approval-delegations/{id}` | — | `204` | Bearer, tenant bound |
| GET | `/api/v1/approvals/{instanceId}/history` | — | every step, decision, delegation and escalation, in order | Bearer, tenant bound |

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__approval_delegation.sql` | `core.approval_delegation` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` ·
`delegator_employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`delegate_employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`flow_types varchar(256) NULL` — null means all · `effective_from date NOT NULL` ·
`effective_to date NOT NULL` · `is_active boolean NOT NULL DEFAULT true` ·
four audit columns.

The same script adds two **nullable** columns to `core.approval_step`:
`delegated_from_employee_id uuid NULL` and `escalated_from_employee_id uuid NULL`. Both are
additive, so a step created by `W-15.2` before this ticket stays valid — the expand half,
with no contract needed.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, delegator_employee_id, effective_from, effective_to)`, and `(tenant_id, decision, created_at)` on steps for the escalation sweep
- [x] Money columns — none
- [x] Expand / contract — one new table plus two nullable columns

**The delegation is recorded on the step, not only in the delegation table.** A delegation
deleted next month must not erase the fact that a decision was taken by a delegate, which is
exactly the question a dispute asks.

A check constraint refuses `delegator = delegate`. Chains of delegation — A to B, B to C —
are refused in the service; see decision 1.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../approval/DelegationServiceTest.java` | an active delegation redirects; an expired one does not; a flow-scoped one redirects only that flow; self-delegation refused |
| Unit | `core/.../approval/EscalationServiceTest.java` | a step under threshold is untouched; over threshold moves one level; an approver with no manager above yields unassignable rather than looping |
| Integration | `core/.../approval/DelegationIT.java` | a leave request raised while the manager is delegated reaches the delegate, and the step records both |
| Integration | `core/.../approval/EscalationIT.java` | an overdue step reassigns and the history shows the escalation |
| Integration | `core/.../approval/ApprovalHistoryIT.java` | the history endpoint shows a delegated decision and an escalated one distinctly |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.approval_delegation'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, is_nullable FROM information_schema.columns
    WHERE table_schema='core' AND table_name='approval_step'
      AND column_name IN ('delegated_from_employee_id','escalated_from_employee_id') ORDER BY 1;"
cd code/backend && mvn -q -pl core -Dit.test=DelegationIT,EscalationIT verify
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| New step columns | two rows, both `is_nullable = YES` |
| Delegation and escalation tests | green |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Escalation loops upward forever | **medium** | Bounded by the reporting chain's depth, and a chain with no one above yields unassignable rather than cycling |
| Escalation lands an approval back on the requester | medium | `W-15.2` decision 1 permits self-approval, so this succeeds rather than stalling. It is recorded — approver and requester match on the step — and is a candidate for one of `W-23.1`'s seeded reports |
| The escalation sweep runs on every replica and escalates repeatedly | medium | `@SchedulerLock` on the job, per `W-52`'s `SchedulerLockConfig`; a sweep without it is the defect |
| A deleted delegation erases the audit of who decided | medium | Recorded on the step, asserted by the history test |
| Escalation thresholds set so short that everything escalates | low | Threshold is per step in the definition; no global default that surprises a tenant |
| Delegation chains create ambiguity | medium | Refused; decision 1 |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only —
`migration/README.md:135-143`. Disabling the sweep stops escalation without touching data.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.approval_delegation` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | one new table plus two nullable columns; no destructive step |
| No module references another module | `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| No delegation in any flow | **Fixed** |
| No escalation; all reminder crons disabled (`ReminderScheduler`) | **Fixed.** A live sweep, not a disabled cron |
| Approval trail as two comment columns on the request | **Fixed by `W-15.2`'s steps; readable here** |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Are delegation chains allowed?** A delegates to B, and B is also away and delegates to C. **Recommend** refusing chains and requiring A to name C — chains are hard to reason about in a dispute and easy to create by accident.
2. **May an administrator reassign a stuck approval by hand?** **Recommend** yes, with the reassignment recorded on the step like a delegation, because "the approver left the company" is common and escalation only moves upward.
3. **Does a delegate see the requester's salary data?** Some approvals expose figures. **Recommend** the delegate sees exactly what the delegator would, and that this is stated in the delegation screen rather than discovered.
