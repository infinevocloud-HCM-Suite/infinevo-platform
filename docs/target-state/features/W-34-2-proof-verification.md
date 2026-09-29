# Feature: Proof of investment — verification, comments and return

| Field | Value |
|---|---|
| **Feature ID** | `W-34.2` · from ticket #46 (`W-34`) · `PAY-11` part 2 of 3 |
| **Promoted to** | `docs/target-state/features/W-34-2-proof-verification.md` on the developer's `dev-<name>` branch — **`W-34-2` with hyphens**, never `W-34.2`; `guard-edit` blocks the dotted form |
| **Owner** | mohit |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-018 (honoured), DEBT-022 (fixed), DEBT-025 (not ported) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | `W-34.1` — the proof and `ProofSubmittedEvent` · `W-33.3` — `ProofVerifiedEvent` and its listener · `W-33.1`, `W-33.2` — `TaxInputGatherer`. `W-15.1`–`.3` (`a34c14f`) and `W-20.1` (`b6e6012`) are on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V115` — one table, `payroll.employee_proof_item_comment` | 1 |
| Externally testable behaviour | a reviewer decides each item and then the proof; approved amounts drive tax, and a returned proof goes back to the employee with a reason | 1 |
| Frontend area | none | 1 |

`09-build-order.md:229`: *"Done when: a rejected proof returns to the employee with a reason.
Watch: uses the approval engine, not its own workflow."*

---

## 1. Problem

| Today (frozen) | Evidence |
|---|---|
| Review is its own hard-coded workflow, one of five | `W-15-1-approval-definition.md:52-58`; `D-33` |
| Item approve, item reject, final approve, final reject, bulk | `controller/employeeitdeclaration/AdminProofOfInvestmentController.java:283`, `:331`, `:379`, `:424`, `:470` |
| Item reject sets the approved amount to 0 and the review continues | `serviceimpl/employeeitdeclaration/EmployeeProofOfInvestmentServiceImpl.java:1683-1700` |
| Final approve needs every claimed item decided | `:1781-1790` |
| Final reject sends everything back to draft and reopens the declaration | `:1830-1870` |
| Tax moves only when an officer clicks "consider for IT" | `AdminProofOfInvestmentController.java:596` → `EmployeeProofOfInvestmentServiceImpl.java:1906` |
| Threaded employee ↔ reviewer comments per item | `:275`, `:371`; `entity/EmployeeITDeclaration/poi/EmployeePOIItemComment.java` |

Paths above are under `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/`.

**The engine already has the shape** (`W-15.1`). `PROOF_OF_INVESTMENT` is seeded as a
`per_item` `hr` step, then a final `hr` step
(`code/backend/migration/src/main/resources/db/migration/core/V089__approval_definition.sql:94-110`).
One rule matters: **a `REJECTED` decision on any step rejects the whole instance** and closes
every undecided step with the comment "Instance rejected"
(`code/backend/core/src/main/java/com/infinevo/core/approval/ApprovalService.java:233-248`).

## 2. Scope

**In scope**

- Start the instance when a proof is submitted (listener on `W-34.1`'s `ProofSubmittedEvent`)
- A review read and three item actions: `APPROVE` (with amount), `DISALLOW` (amount 0, reason), `RETURN` (reason)
- The final decision: `APPROVE` or `RETURN`
- `ProofOutcomeHandler implements ApprovalOutcomeHandler` — writes the outcome, publishes `ProofVerifiedEvent`, notifies the employee
- `payroll.employee_proof_item_comment` — a flat comment list per item, employee and reviewer
- `TaxInputGatherer` prefers approved amounts when the proof is `APPROVED` (`W-33-2-tax-calculator-old-regime.md:203`)

**Out of scope**

- Bulk action — the reviewer decides item by item; the engine has no bulk decide
- Officer editing of the declaration itself — legacy `adminProofEdit.js`; not ported (DEBT-026)
- The "consider for IT" button — replaced by the event (§13 decision 2)
- Chase list — `W-34.3`

## 3. Flow

```
W-34.1 submit --> ProofSubmittedEvent  (@EventListener, same transaction)
   --> ApprovalService.start(PROOF_OF_INVESTMENT, SubjectRef("payroll.employee_proof_of_investment", proofId),
                             employeeId, itemIds where claimed_amount > 0)
   --> proof.approval_instance_id = instanceId

[hr] GET  /payroll/proof-of-investment/{proofId}/review              payroll.proof.review
[hr] POST /payroll/proof-of-investment/{proofId}/items/{itemId}/decide   { action, approved_amount?, comment? }
   APPROVE  --> ApprovalService.decide(step, APPROVED, comment, approved_amount)
   DISALLOW --> ApprovalService.decide(step, APPROVED, comment, 0)
   RETURN   --> item RETURNED, reviewer_note = comment                    same transaction, before the decide
            --> ApprovalService.decide(step, REJECTED, comment)        instance rejected
[hr] POST /payroll/proof-of-investment/{proofId}/final               { action: APPROVE | RETURN, comment? }
   RETURN   --> proof.reviewer_note = comment, then decide REJECTED

ProofOutcomeHandler  (engine, after commit, retried by the sweep)
   onApproved --> items: approved_amount, APPROVED | DISALLOWED, reviewer_note
              --> proof APPROVED, decided_at
              --> publish ProofVerifiedEvent(tenant, employeeId, declarationId, fy)   (W-33.3 recalculates)
              --> compose(APPROVAL_DECIDED, employee, decision "Approved")
   onRejected --> items the wrapper marked RETURNED keep status and reason
              --> every other item: PENDING, approved_amount NULL
              --> no RETURNED item and no proof note (a decide made outside the wrapper):
                  proof.reviewer_note = the REJECTED step's comment
              --> proof REJECTED, decided_at --> employee edits and resubmits (W-34.1)
              --> compose(APPROVAL_DECIDED, employee, decision "Returned")
```

The step for an item is the instance's undecided step whose `item_ref` is the item id. No
undecided step is `409 NOT_UNDER_REVIEW`. The engine enforces the assignee and the order
(`ApprovalService.java:164-218`), so the final is refused until every item step is approved.

**Validation in the wrapper**, all `400`:

| Rule | Source |
|---|---|
| `APPROVE`: `0 <= approved_amount <= claimed_amount` | legacy capped unless an override flag was on (`EmployeeProofOfInvestmentServiceImpl.java:1558`); the flag is not ported |
| `APPROVE` below `claimed_amount`, `DISALLOW`, `RETURN`: `comment` required | the reason is what the employee reads |
| `poi_comment_mandatory` on: `comment` required for every action | `:1552`; column added by `W-34.1` |

**The handler is idempotent.** It acts only while the proof is `SUBMITTED` and its
`approval_instance_id` equals the instance id; otherwise it logs and returns. The engine
retries a handler that failed (`OutcomeDispatcher.java:82-100`).

**Tax.** `TaxInputGatherer` asks `ProofAmountReader.approvedAmounts(declarationId)`. When the
proof is `APPROVED`, each item's `approved_amount` replaces its source figure. A
`HOUSE_RENT` amount is spread back as `approved_amount ÷ months`, scale 4, `HALF_UP`. Lines
with no item keep their declared figure. One pipeline, with no "with proofs" copy.

## 4. Backend changes

Under `code/backend/payroll/src/main/java/com/infinevo/payroll/proof/` unless noted.

| Layer | File | Change |
|---|---|---|
| Listener | `ProofApprovalStarter` | new — `@EventListener` on `ProofSubmittedEvent`, calls `ApprovalService.start`, stores the instance id |
| Handler | `ProofOutcomeHandler` | new — `flowType()` = `PROOF_OF_INVESTMENT`; `onApproved`, `onRejected` as §3 |
| Service / Impl | `ProofReviewService`, `…Impl` | new — `review(proofId)`, `decideItem`, `decideFinal` |
| Service / Impl | `ProofCommentService`, `…Impl` | new — `list(itemId)`, `add(itemId, body)` for reviewer and `/me` |
| Reader | `ProofAmountReader` | new — `approvedAmounts(declarationId)` → map keyed by `(source_kind, source_line_id)`; empty unless `APPROVED` |
| Entity / Repository | `EmployeeProofItemComment` | new |
| Controller | `ProofReviewController`, `MyProofController` (comments) | new / changed |
| Changed | `taxcalc/…/TaxInputGatherer` | prefers `ProofAmountReader` figures |
| DTO | `ProofReviewResponse`, `ProofItemDecisionRequest`, `ProofFinalDecisionRequest`, `ProofCommentRequest/Response` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/payroll/proof-of-investment/{proofId}/review` | — | proof, items (declared, claimed, approved, status, notes, documents, open step id), comment counts | `payroll.proof.review` |
| POST | `/api/v1/payroll/proof-of-investment/{proofId}/items/{itemId}/decide` | `action` `APPROVE`/`DISALLOW`/`RETURN`, `approved_amount`, `comment` | `200` item; `400`; `403` not the assignee; `409 NOT_UNDER_REVIEW` | `payroll.proof.review` |
| POST | `/api/v1/payroll/proof-of-investment/{proofId}/final` | `action` `APPROVE`/`RETURN`, `comment` | `200` proof; `409 ITEMS_UNDECIDED` | `payroll.proof.review` |
| GET / POST | `/api/v1/payroll/proof-of-investment/{proofId}/items/{itemId}/comments` | `body` (1–1000) | list oldest first / `201` | `payroll.proof.review` |
| GET / POST | `/api/v1/me/proof-of-investment/{fy}/items/{itemId}/comments` | `body` (1–1000) | list / `201` | `payroll.proof.read_own` / `payroll.proof.submit_own` |

The reviewer's decision comment stays on the engine step (`ApprovalStep.comment`, 1000 chars)
and is copied to `reviewer_note`. The comment table is the conversation around it.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V115__employee_proof_item_comment.sql` | `payroll.employee_proof_item_comment` | yes | additive |

`id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant(tenant_id)` ·
`item_id uuid NOT NULL REFERENCES payroll.employee_proof_item(id) ON DELETE CASCADE` ·
`author_employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`author_role varchar(8) NOT NULL CHECK (author_role IN ('EMPLOYEE','REVIEWER'))` ·
`body varchar(1000) NOT NULL` · `created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP` · `created_by varchar(100) NOT NULL`.

| Legacy (`EmployeePOIItemComment.java`) | Here |
|---|---|
| `comment TEXT` | `body varchar(1000)` |
| `commentedByEmployee` / `commentedByAdmin` (a free string) | `author_employee_id` + `author_role` |
| `responseTo` self-reference (threading) | **not ported** — flat, oldest first |
| edit and delete (`EmployeeProofOfInvestmentServiceImpl.java:401-520`) | **not ported** — append-only, it is the record of the review |

- [x] `tenant_id` + RLS `tenant_isolation`, `CASE` form
- [x] `idx_employee_proof_item_comment_tenant_item_created (tenant_id, item_id, created_at)` (DEBT-018)
- [x] Money — none on this table; approved amounts use `W-34.1`'s `NUMERIC(19,4)` column
- [x] Expand only

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../proof/ProofDecisionRulesTest.java` | amount above claimed refused; negative refused; comment required for `DISALLOW`, `RETURN`, a partial `APPROVE`, and for all when `poi_comment_mandatory` |
| Unit | `payroll/.../proof/ProofOutcomeHandlerTest.java` | approved: amounts and statuses written, event published once; rejected: an item already `RETURNED` keeps its reason, the rest `PENDING` with amounts cleared; a reject made outside the wrapper copies the step comment to the proof; a second call is a no-op; a stale instance id is a no-op |
| Unit | `payroll/.../taxcalc/…/TaxInputGathererProofTest.java` | no proof and `SUBMITTED` proof: declared figures; `APPROVED`: approved figures per kind; house rent spread over its months |
| Integration | `payroll/.../proof/ProofVerificationIT.java` | **acceptance**: submit (`W-34.1`) starts one instance with one item step per claimed item; `hr` approves one, disallows one; final approve ⇒ proof `APPROVED`, `ProofVerifiedEvent` captured, `APPROVAL_DECIDED` notification queued. Second proof: `RETURN` one item ⇒ proof `REJECTED`, item `RETURNED` with the reason on the `/me` read; employee edits and resubmits ⇒ a new instance id |
| Integration | `payroll/.../proof/ProofReviewAccessIT.java` | an employee cannot decide; a reviewer who is not the assignee gets `403`; final before items `409` |
| Integration | `payroll/.../proof/ProofCommentRlsIT.java` | as `app_user`, tenant A cannot read or insert tenant B's comments |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid = 'payroll.employee_proof_item_comment'::regclass;"
grep -rn "implements ApprovalOutcomeHandler" code/backend/payroll/src/main/java/
grep -rn "double\|float\|Double\|Float" code/backend/payroll/src/main/java/com/infinevo/payroll/proof/ || echo "no floating point"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Handler | one hit, `proof/ProofOutcomeHandler.java` |
| Floating point | `no floating point` |
| Suite | green, no skips; `ProofVerificationIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A reviewer uses core's `/api/v1/approvals/steps/{id}/decide` and skips the wrapper's checks | medium | the handler still writes the outcome; screens call only the payroll path; an amount above claimed is clamped in the handler and logged as a warning |
| The handler cannot tell a reviewer's reject from an auto-closed step | high if left to the handler | `StepDecision.deciderId` is the assignee for every step (`OutcomeDispatcher.java:110-116`) and auto-closed steps are also `REJECTED` (`ApprovalService.java:238-246`), so the wrapper marks the `RETURNED` item and the reason before it calls `decide`, in one transaction |
| Tax recomputed twice (declaration submit, then proof) | low, harmless | `tax_computation` is append-only; the latest row wins (`W-33.3`) |
| `hr` role missing in a Payroll-only tenant | low | `V022__role_action.sql:76` seeds `hr` for every tenant |

## 10. Rollback

Nothing is deployed. `V115` is additive; the handler and listener are new beans.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | one table |
| Flyway only | one script |
| `Money`/`BigDecimal` | `BigDecimal` throughout; no new money column |
| Index on `tenant_id` plus lookup columns | one index, `tenant_id` leading |
| Expand / contract | additive |
| No module references another module | `payroll` → `core` (`ApprovalService`, `ApprovalOutcomeHandler`, `NotificationService`); `proof` → `taxcalc` inside `payroll` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for this table** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** |
| DEBT-025 parallel POI implementations | **Not ported** — the engine is the only workflow |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | Legacy item reject keeps the review going; the engine ends it. Which? | **Both, as two actions.** `DISALLOW` = approve at 0 with a reason, the review goes on (legacy `:1683`). `RETURN` = engine reject, the proof goes back (the build-order test) |
| 2 | Keep "consider for IT"? | **No.** Final approval publishes `ProofVerifiedEvent`; `W-33.3` recalculates after commit. A verified proof that does not reach TDS until someone clicks was a legacy gap |
| 3 | After a return, which items are reviewed again? | **All of them**, as legacy's final reject (`:1830-1870`). The earlier approvals stay readable in the engine history |
| 4 | Does a return reopen the declaration? | **No.** Legacy did (`:1867`); the employee reopens it through `W-32.1` if a line must change, since the proof is `REJECTED` and no longer blocks that |
| 5 | Threaded or flat comments? | **Flat, append-only.** Threading was one level deep and editable, which made the record rewritable |

## 14. Open for the founder

None.
