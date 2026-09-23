# Feature: Leave request, approval and documents

| Field | Value |
|---|---|
| **Feature ID** | `W-16.3` · ticket #19 · `CORE-07` |
| **Promoted to** | `docs/target-state/features/W-16-3-request-approval.md` on branch `W-16-3-request-approval` — **`W-16-3` with hyphens**, never `W-16.3`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | BUG-003 (honoured), DEBT-013 (discounted), DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-15.2` (approval lifecycle), `W-16.2` (balance), `W-21` (documents), `W-17` (holidays) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 2 scripts, one table each — aggregate exception | 1 — exception granted 2026-09-22 |
| Externally testable behaviour | a request is raised, routed for approval, and on approval reduces the balance | 1 |
| Frontend area | none | 1 |

Within cap. `leave_request_document` is a satellite of `leave_request`.

---

## 1. Problem

HRMS has the only working request-and-approval flow in either product, and it is hard-coded.

- The request entity is `LeaveRequests.java:17`, with dates, a half-day flag and period, a total, selected dates, comments and two status columns
- Approval is exactly two stages — reporting manager, then HR — enforced in the controller by string-matching the caller's role: `"reporting manager"`, `"reporting_manager"` and `"reporting-manager"` are each normalised and accepted — `LeaveRequestController.java:155-222`
- HR may act only once the manager has decided, at `:200-204`. There is no configuration table; the rule lives in an `if`
- There are **two** request entities, `LeaveRequest` and `LeaveRequests`, mapping to `leave_request` and `leave_requests`. Only the plural one is used
- Payroll has no request flow at all. Leave arrives as an allocation or an import

Two further hazards in the frozen shape. `manual_days_allocation` is a map allowing an
approver to override how many days each leave type absorbs — `LeaveRequests.java:73` — and
`lop_allocation` is a second map doing the same for loss of pay. Both bypass the balance
calculation entirely.

`09-build-order.md:189` is explicit that the approval engine must serve five flows, and
`:224` says proof of investment *"uses the approval engine, not its own workflow."* Leave is
the first of the five, so this ticket is where the engine proves itself — and the one place
it would be easiest to rebuild the hard-coded ladder instead.

## 2. Scope

**In scope**

- `core.leave_request` — the request, its dates, its half-day handling and its outcome
- `core.leave_request_document` — attachments, pointing at `core.document`
- Raising, cancelling and withdrawing a request
- Submitting it to the approval engine and reacting to the engine's decision
- Working-day calculation: weekends and holidays excluded per the policy's inclusion flags

**Out of scope**

- **The approval mechanism itself** — `W-15.2`. This ticket calls it and implements no routing, no delegation and no escalation
- Balance arithmetic — `W-16.2`; this ticket reads it and writes nothing to the allocation
- Consumption rows and loss of pay — `W-16.4a`
- Storing file bytes — `W-21`; this table holds a document id
- The apply-for-leave screen — the portal's is `W-25`, and this ticket ships no frontend

## 3. Flow

```
[employee] --> [LeaveRequestController] --> [LeaveRequestService]
   --> LeaveEligibilityService (W-16.1) --> LeaveBalanceService (W-16.2)
   --> HolidayQueryService (W-17) for working days
   --> [core.leave_request] --> [ApprovalService.start(...) (W-15.2)]

[approver] --> [approval engine] --> callback --> request APPROVED or REJECTED
   --> W-16.4a writes consumption
```

The request never asks who the approver is. The engine routes by reporting line — that is
what `W-14.2` built `reporting_line` for.

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../leave/LeaveRequestController.java` | new |
| Service | `core/.../leave/LeaveRequestService.java` | new |
| ServiceImpl | `core/.../leave/LeaveRequestServiceImpl.java` | new |
| Service | `core/.../leave/WorkingDayCalculator.java` | new — weekends and holidays per policy |
| Entity | `core/.../leave/LeaveRequest.java`, `LeaveRequestDocument.java` | new, each `@Table(schema="core")` |
| Repository | `core/.../leave/LeaveRequestRepository.java`, `LeaveRequestDocumentRepository.java` | new |
| Enumeration | `core/.../leave/LeaveRequestStatus.java`, `HalfDayPeriod.java` | new |
| DTO | `core/.../leave/LeaveRequest*.java` | new |

One entity, one table, singular — the `LeaveRequest` / `LeaveRequests` duplication is not
carried across.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/leave-requests` | typeId, from, to, halfDay, period, reason, documentIds | `201` | Bearer, tenant bound |
| GET | `/api/v1/leave-requests` | `?employeeId=&status=&from=&to=&page=` | page | Bearer, tenant bound |
| GET | `/api/v1/leave-requests/{id}` | — | request with its approval trail | Bearer, tenant bound |
| POST | `/api/v1/leave-requests/{id}/cancel` | reason | `200` | Bearer, tenant bound |

There is no `approve` endpoint here. Approving is the engine's API, and duplicating it is how
leave approval quietly becomes its own workflow again.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__leave_request.sql` | `core.leave_request` | yes | additive |
| `core/V0NN__leave_request_document.sql` | `core.leave_request_document` | yes | additive |

Version numbers assigned when the branch is cut — `migration/README.md:17-31`.

`leave_request`: `id uuid` · `tenant_id uuid NOT NULL` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`leave_type_id uuid NOT NULL REFERENCES core.leave_type(id)` ·
`from_date date NOT NULL` · `to_date date NOT NULL` ·
`is_half_day boolean NOT NULL DEFAULT false` · `half_day_period varchar(8) NULL` ·
`working_days numeric(5,2) NOT NULL` · `reason text` ·
`status varchar(16) NOT NULL` · `approval_instance_id uuid NULL` ·
`decided_at timestamptz NULL` · four audit columns.

`leave_request_document`: `id uuid` · `tenant_id uuid NOT NULL` ·
`leave_request_id uuid NOT NULL REFERENCES core.leave_request(id)` ·
`document_id uuid NOT NULL REFERENCES core.document(id)` · four audit columns.

- [x] `tenant_id` on both tables, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, employee_id, from_date DESC)`, `(tenant_id, status)`, `(tenant_id, leave_request_id)` on documents
- [x] **Money columns — none.** `working_days` is `numeric(5,2)`
- [x] Expand / contract — new tables only

**No `manual_days_allocation` and no `lop_allocation`.** Both exist on the frozen request —
`LeaveRequests.java:73,106-113` — and both let an approver overwrite what the balance
calculation produced. If a figure needs correcting, the correction belongs in an allocation
adjustment with an audit row behind it, not in an untracked map on a request.

**No `employee_name` column.** The frozen row denormalises it and it goes stale on the first
rename.

**No `reporting_manager_status` and no `hr_status`.** One `status`, plus
`approval_instance_id` pointing at the engine, which owns however many stages the tenant
configured.

**`working_days` is stored, not recomputed.** A holiday added next year must not change what
an approved request consumed last year.

Each script carries its own RLS and `tenant_isolation` policy in the exact `CASE` form —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../leave/WorkingDayCalculatorTest.java` | weekends and holidays excluded or included per policy; a half-day is `0.5`; a single-day request spanning a holiday is zero working days |
| Unit | `core/.../leave/LeaveRequestServiceImplTest.java` | overlapping requests refused; a request beyond the future-booking limit refused; an ineligible type refused |
| Integration | `core/.../leave/LeaveRequestApprovalIT.java` | a request raised, approved through the engine, ends `APPROVED` with a trail — and the service exposes no way to approve it directly |
| Integration | `core/.../leave/LeaveRequestRlsIT.java` | tenant A cannot read tenant B's requests or attachments as `app_user` |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`LeaveRequestApprovalIT` asserting the **absence** of a direct approval path is the test that
keeps the engine honest.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in leave_request leave_request_document; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name FROM information_schema.columns
    WHERE table_schema='core' AND table_name='leave_request'
      AND column_name IN ('manual_days_allocation','lop_allocation','hr_status',
                          'reporting_manager_status','employee_name');"
cd code/backend && mvn -q verify
grep -rn 'reporting.manager\|hr_status' core/src/main/java/com/infinevo/core/leave/ || echo "no hard-coded ladder"
```

| Check | Expected |
|---|---|
| RLS on both | `t` twice |
| Forbidden columns | **no rows** — none of the five exists |
| Ladder grep | `no hard-coded ladder` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The two-stage ladder is rebuilt because it is familiar and the engine is not ready | **high — the single largest risk in this ticket** | Blocked on `W-15.2`; the grep and the integration test both check for it |
| Override maps come back to solve a real correction case | medium | Corrections go through an allocation adjustment, which is auditable; the column check catches the shortcut |
| Working days computed at read time, so history changes when a holiday is added | medium | Stored on the row; the calculator is called once, at submission |
| Half-days lost between request and consumption | medium | `working_days numeric(5,2)`; the calculator test asserts `0.5` |
| Overlapping requests both approved, double-consuming balance | medium | Overlap check at submission **and** a partial unique index on approved requests per employee per date range |
| Attachments pointing at another tenant's document | low | FK plus RLS; asserted in `LeaveRequestRlsIT` |

## 10. Rollback

Nothing is deployed. Both scripts are additive and forward-only —
`migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | both tables, each in its own script |
| Flyway only, `ddl-auto` nowhere | two scripts; none added |
| `Money`/`BigDecimal` for money | no money column; `working_days` is `numeric(5,2)` |
| Index on `tenant_id` plus lookup columns | three indexes, `tenant_id` leading |
| Expand / contract | new tables only |
| No module references another module | `core` only; the engine, balance, holiday and document services are all `core` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-003 half-day precision (`GAP_INVENTORY.md:29`) | **Honoured.** `working_days` is `numeric(5,2)` end to end |
| DEBT-013 package typo (`:51`) | **Discounted.** New code is `core/.../leave/` |
| Dual `LeaveRequest` / `LeaveRequests` entities | **Fixed.** One entity, one table |
| Hard-coded two-stage approval (`LeaveRequestController.java:155-222`) | **Fixed by replacement.** Routing belongs to `W-15.2` |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Can an approved request be cancelled, and what happens to the balance?** **Recommend** cancellation allowed until the leave starts, re-crediting the balance through `W-16.4a`, and refused afterwards — an absence that already happened is a correction, not a cancellation.
2. **Are attachments ever mandatory?** **Yes** — settled 2026-09-22. The `requires_document` flag lives on `core.leave_policy` and is created by **`W-16.1`**, not here. This ticket reads it and refuses a submission that needs a document and has none; `LeaveRequestServiceImplTest` covers that case.

## 14. Doc drift found while specifying

`legacy/docs/FEATURE_MAP.md:346` says the pay run reads `employee_leave_balance_consumption`
for loss-of-pay days, *"replacing the earlier HRMS `fetchLeaves` path."* The code does the
opposite: `EmployeePayRunServiceImpl.java:1091` calls HRMS and the consumption figure is not
used. The document describes an intention, not the frozen system. It is `legacy/docs/`, so
`sync-docs` does not cover it — raise it as its own correction.
