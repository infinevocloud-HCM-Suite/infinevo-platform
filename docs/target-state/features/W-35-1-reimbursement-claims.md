# Feature: Reimbursement claims — submit, approve, feed the pay run

| Field | Value |
|---|---|
| **Feature ID** | `W-35.1` · from ticket #47 (`W-35`) · `PAY-12` |
| **Promoted to** | `docs/target-state/features/W-35-1-reimbursement-claims.md` on the developer's `dev-<name>` branch — **`W-35-1` with hyphens**, never `W-35.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-004 (discounted), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-011 (fixed), DEBT-018 (honoured), DEBT-022 (fixed), DEBT-031 (deferred to `W-47.4`) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | none for the build. `W-15` (approval engine) and `W-19` (ledger) are on `main`. The acceptance "an approved claim appears on the next payslip" is proven end to end only once `W-29.3` reads the ledger |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V098` — one table. `V097` is the action-code seed in `reference`, as `W-27.2` did with `V052`; it creates no schema | 1 |
| Externally testable behaviour | an employee submits a claim with a receipt, the tenant admin approves an amount, and a `REIMBURSEMENT` row for that amount is in the pay input ledger for the next open period | 1 |
| Frontend area | none — screens are `W-47.4` | 1 |

Within cap. `W-35` was split on 2026-09-29: this is the claim, `W-35.2` is the ad-hoc
deduction. They share nothing but the ledger.

---

## 1. Problem

The frozen Payroll backend has a working claim feature. It is a port, and four things about
it must not come across.

- **The pay run reads the claim table.** `EmployeePayRunServiceImpl.java:351-365` sums
  `APPROVED` + `UNPAID` claims by month; `:1223-1239` flips them to `INPAYRUN`;
  `PayRunServiceImpl.java:712-718`, `:768-774`, `:883-889` flip them to `PAID` or back to
  `UNPAID` on approve, reject and delete. Three services hold one state machine. The target
  pay run reads only `core.pay_input` (`W-29-3-pay-run-lop-and-inputs.md:36`, `:161`)
- **The month is a caller-supplied string.** `reimbursement_month` is `YYYY-MM` text set at
  submission (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/employeereimbursement/EmployeeReimbursementServiceImpl.java:423-424`)
  and matched exactly (`EmployeePayRunServiceImpl.java:353-357`). A claim approved after that
  month's run is never paid. Build order names this the trap (`09-build-order.md:231`)
- **Approval is a hand-coded admin endpoint.** `AdminReimbursementController.java:121-147`
  and `:161-182`, gated by an interceptor (`:27`). `D-33` puts every approval on one engine,
  and the `REIMBURSEMENT` flow is already seeded
  (`code/backend/migration/src/main/resources/db/migration/core/V089__approval_definition.sql:77-95`)
- **Receipts are public Cloudinary URLs.** `EmployeeReimbursementRequest.java:54-60` —
  DEBT-011. The document store (`W-21`) exists with a `REIMBURSEMENT_RECEIPT` kind
  (`code/backend/core/src/main/java/com/infinevo/core/document/DocumentKind.java:19`)
- The claim type is a fixed enum of six values
  (`legacy/.../enumeration/employeereimbursement/ReimbursementType.java`), while the tenant
  already configures its reimbursement components in `payroll.reimbursement` (`W-26.1`,
  `V045`)

## 2. Scope

**In scope**

- `payroll.employee_reimbursement_request` — one row per claim, `tenant_id`, RLS
- Employee submits a claim against one of the tenant's reimbursement components, with an
  optional receipt already uploaded through `POST /api/v1/documents`
- Submission starts a `REIMBURSEMENT` approval instance; the decision arrives through the
  engine's outcome handler, never through an endpoint of this ticket
- On approval the claim writes one `REIMBURSEMENT` row to the ledger for the approved amount,
  and records which period the ledger posted it to
- Employee reads own claims; the payroll officer reads any claim in the tenant
- Three action codes with grants for new and existing tenants

**Out of scope**

- Any approve, reject or pending-queue endpoint — those are `core`'s
  `/api/v1/approvals/*` (`W-15.2`)
- Withdrawing a submitted claim. Legacy has no such thing; it needs an instance-cancel on the
  engine that `W-15` did not build. A follow-up ticket if asked for
- A "paid" state. The ledger row and the period lock are the payment state, as `W-29.3`
  decided (`:161`). `W-36` shows it on the payslip
- The legacy `ReimbursementClaim` org-settings row (`/api/reimbursement-claims`,
  `ReimbursementClaimController.java:22-47`). It is mail and reminder flags, not a claim.
  `02-data-model.md:173` lists it under "Claims"; decision 5 retires it
- Screens — `W-47.4`

## 3. Flow

```
[employee] --> POST /api/v1/documents (kind=REIMBURSEMENT_RECEIPT)      (W-21, optional)
          --> POST /api/v1/me/reimbursement-claims
              --> ReimbursementClaimServiceImpl.submit
                  --> INSERT payroll.employee_reimbursement_request  status=SUBMITTED
                  --> ApprovalService.start(REIMBURSEMENT,
                          SubjectRef("payroll.employee_reimbursement_request", id), employeeId)

[tenant admin] --> POST /api/v1/approvals/steps/{stepId}/decide {APPROVE, approvedAmount}   (W-15.2)
              --> OutcomeDispatcher --> ReimbursementClaimOutcomeHandler.onApproved
                  --> claim.approved_amount = decision.approvedAmount ?? requested_amount
                  --> PayInputService.record(employee, YearMonth.now(), REIMBURSEMENT,
                          amount = approved_amount, source "payroll", ref "reimbursement_claim:{id}")
                  --> claim.pay_input_id, claim.posted_period = response.postedPeriod
                  --> status=APPROVED
              --> onRejected --> status=REJECTED, remarks = decision comment

[pay run, W-29.3] --> PayInputService.forPeriod --> REIMBURSEMENT line, is_taxable=false
```

A locked period is not this ticket's problem. `PayInputService.record` posts an untagged row
whose period is locked to the next open period and says so in `postedPeriod`
(`code/backend/core/src/main/java/com/infinevo/core/payinput/PayInputService.java:21-27`).
That is the whole answer to the build-order trap: a claim approved late lands in the next run,
and the claim row says which.

## 4. Backend changes

All new, under `code/backend/payroll/src/main/java/com/infinevo/payroll/reimbursement/`.

| Layer | File | Change |
|---|---|---|
| Entity | `ReimbursementClaim.java` | new, `@Table(name = "employee_reimbursement_request", schema = "payroll")`; `UUID id`, `UUID tenantId`, `UUID employeeId`, `UUID reimbursementId`, `BigDecimal requestedAmount`, `BigDecimal approvedAmount`, `LocalDate billDate`, `String description`, `UUID documentId`, `ClaimStatus status`, `String remarks`, `UUID approvalInstanceId`, `UUID payInputId`, `String postedPeriod`, `UUID approvedBy`, `Instant approvedAt`, audit columns |
| Enumeration | `ClaimStatus.java` | new — `SUBMITTED`, `APPROVED`, `REJECTED` |
| Repository | `ReimbursementClaimRepository.java` | new — `findByTenantIdAndId`, `findByTenantIdAndEmployeeIdOrderByCreatedAtDesc`, `findByTenantIdAndApprovalInstanceId`, a paged filtered finder for the officer list. Every finder takes `tenantId` (DEBT-022) |
| Service / ServiceImpl | `ReimbursementClaimService`, `ReimbursementClaimServiceImpl` | new — `submit`, `getOwn`, `listOwn`, `get`, `list` |
| Outcome handler | `ReimbursementClaimOutcomeHandler.java` | new, `@Component implements ApprovalOutcomeHandler`, `flowType() = REIMBURSEMENT` (`core/.../approval/ApprovalOutcomeHandler.java:10-17`). Loads the instance by id, reads `getSubjectId()` (`ApprovalInstance.java:159`), then the claim |
| Controller | `ReimbursementClaimController.java` | new |
| DTO | `ReimbursementClaimRequest`, `ReimbursementClaimResponse` | new; `status` / `message` / `data` envelope (`CONVENTIONS.md` §3) |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/me/reimbursement-claims` | `reimbursement_id`, `requested_amount`, `bill_date`, `description`, `document_id` (optional) | `201`, the claim with `status = SUBMITTED` and `approval_instance_id` | `payroll.reimbursement_claim.submit_own` |
| GET | `/api/v1/me/reimbursement-claims` | — | own claims, newest first | `payroll.reimbursement_claim.read_own` |
| GET | `/api/v1/me/reimbursement-claims/{id}` | — | one own claim; `404` for another employee's | `payroll.reimbursement_claim.read_own` |
| GET | `/api/v1/payroll/reimbursement-claims` | `employeeId`, `status`, `from`, `to` (bill date), `page`, `size` (25, clamped at 100) | paged claims in the tenant | `payroll.reimbursement_claim.read` |
| GET | `/api/v1/payroll/reimbursement-claims/{id}` | — | one claim | `payroll.reimbursement_claim.read` |

The response carries `requested_amount`, `approved_amount`, `status`, `remarks`,
`posted_period`, `document_id` and the component's `code` and `name`. Never a URL: the
receipt is fetched through `W-21`'s read endpoints under `core.document.read_own`.

`/me/*` resolves the employee with `EmployeeService.currentEmployee()` as `W-27.2` does
(`code/backend/payroll/src/main/java/com/infinevo/payroll/fbp/FbpDeclarationServiceImpl.java:84`);
a login linked to no employee gets `403`.

**Validation on submit, all `400`**

- `requested_amount > 0`, scale at most 2
- `bill_date` not after today
- `reimbursement_id` is an active, undeleted row of `payroll.reimbursement` in the bound
  tenant
- `document_id`, when given, is a document in the bound tenant of kind
  `REIMBURSEMENT_RECEIPT` whose employee is the caller (`DocumentService.get`)
- `description` at most 500 characters

`max_limit` on the component is **not** enforced on submit. Legacy never capped a claim, and
the approver sets the amount; the response shows `max_limit` so the screen can warn.

**Outcome handler rules**

- `onApproved`: if the claim is already `APPROVED`, return — the dispatcher retries a
  handler that threw, and the ledger's unique `(source_module, source_ref)` would refuse a
  second row anyway. Otherwise `approved_amount` is the last decision's `approvedAmount`
  (`core/.../approval/StepDecision.java`), or `requested_amount` when the approver gave
  none; an amount above `requested_amount` is clamped to it; zero becomes a rejection with
  remark `Approved amount was zero`. Then `PayInputService.record` with
  `PayInputCommand(employeeId, YearMonth.now(), REIMBURSEMENT, null, Money.of(approved_amount), "payroll", "reimbursement_claim:" + id)`.
  Store `pay_input_id`, `posted_period`, `approved_by` (the decider), `approved_at`
- `onRejected`: `status = REJECTED`, `remarks` = the deciding step's comment
- Both run inside the dispatcher's transaction; a thrown exception rolls the claim update
  back and the sweep retries (`OutcomeDispatcher.java:83`)

## 5. Frontend changes

None. `W-47.4` builds the claim form, own-claims list and the officer list against this
contract; the approval queue is `W-46.4`'s generic approvals screen.

## 6. Database changes

> Flyway only. Never `ddl-auto`. Every statement names its schema
> (`code/backend/migration/README.md`).

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `reference/V097__reimbursement_claim_actions.sql` | `reference.action` rows; `core.seed_system_roles` redefined; grants to existing tenants — the `V052` shape exactly | grants are per tenant | additive |
| `payroll/V098__employee_reimbursement_request.sql` | `payroll.employee_reimbursement_request` | yes | additive |

`payroll.employee_reimbursement_request`

| Column | Type | Note |
|---|---|---|
| `id` | `UUID PK DEFAULT gen_random_uuid()` | |
| `tenant_id` | `UUID NOT NULL REFERENCES core.tenant(tenant_id)` | |
| `employee_id` | `UUID NOT NULL REFERENCES core.employee(id)` | legacy `employee_id VARCHAR` was a Keycloak id |
| `reimbursement_id` | `UUID NOT NULL REFERENCES payroll.reimbursement(id)` | replaces the `ReimbursementType` enum |
| `requested_amount` | `NUMERIC(19,4) NOT NULL CHECK (> 0)` | legacy `DECIMAL(12,2)` (`legacy/docs/DB_SCHEMA.md:742-765`) |
| `approved_amount` | `NUMERIC(19,4) NULL CHECK (>= 0)` | |
| `bill_date` | `DATE NOT NULL` | |
| `description` | `VARCHAR(500)` | |
| `document_id` | `UUID NULL REFERENCES core.document(id)` | replaces `attachment_url`, `attachment_public_id`, `attachment_file_name` |
| `status` | `VARCHAR(16) NOT NULL CHECK (IN ('SUBMITTED','APPROVED','REJECTED'))` | replaces `status` + `payment_status` |
| `remarks` | `VARCHAR(500)` | decision comment |
| `approval_instance_id` | `UUID NULL` | no FK across to `core.approval_instance`; the engine owns it |
| `pay_input_id` | `UUID NULL` | the ledger row; no FK, the ledger is append-only and reversals are the ledger's business |
| `posted_period` | `CHAR(7) NULL` | `YYYY-MM`, the period the ledger actually took |
| `approved_by` | `UUID NULL` | |
| `approved_at` | `TIMESTAMPTZ NULL` | |
| `created_at`, `created_by`, `updated_at`, `updated_by` | as `V051` | |

Indexes: `idx_reimb_claim_tenant_employee_created (tenant_id, employee_id, created_at DESC)`,
`idx_reimb_claim_tenant_status_bill (tenant_id, status, bill_date)`,
`uk_reimb_claim_tenant_approval_instance (tenant_id, approval_instance_id) WHERE approval_instance_id IS NOT NULL`.
RLS policy `tenant_isolation` copied from `V051__fbp.sql:26-33`.

- [x] `tenant_id` present on every new table (`CONVENTIONS.md` rule 7)
- [x] Index on `tenant_id` plus lookup columns (DEBT-018)
- [x] Money columns `NUMERIC(19,4)`, `BigDecimal` in the entity, `Money` on the ledger call
- [x] Expand / contract — new table only

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../reimbursement/ReimbursementClaimRulesTest.java` | zero and negative amount refused; future bill date refused; inactive component refused; description over 500 refused; approved amount: absent → requested, above requested → clamped, zero → rejected |
| Unit | `payroll/.../reimbursement/ReimbursementClaimOutcomeHandlerTest.java` | `onApproved` calls `record` once with kind `REIMBURSEMENT`, positive `Money`, `sourceRef = "reimbursement_claim:" + id`; a second `onApproved` on an `APPROVED` claim calls nothing; `onRejected` stores the comment |
| Integration | `payroll/.../reimbursement/ReimbursementClaimIT.java` | submit creates one row and one `core.approval_instance` with the claim as subject; deciding `APPROVE` with `approvedAmount = 1500` on a 2000 claim leaves `approved_amount = 1500`, one `core.pay_input` row of `amount 1500`, `posted_period` = current period; `REJECT` leaves no ledger row; `GET /me/...` from another employee's login is `404` |
| Integration | `payroll/.../reimbursement/ReimbursementClaimLockedPeriodIT.java` | current period locked via `PayInputService.lock`; approval posts to the next period and `posted_period` says so |
| Integration | `payroll/.../reimbursement/ReimbursementClaimRlsIT.java` | as `app_user`, tenant A cannot read tenant B's claim by id or in the list; a raw-SQL `INSERT` with tenant B's id under tenant A's context is refused |
| Integration | `payroll/.../reimbursement/ReimbursementClaimActionSeedIT.java` | the three codes exist; `employee` holds `submit_own` and `read_own`; `payroll-officer` and `finance` hold `read`; a tenant provisioned after the migration holds the same |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. Payroll ITs use the
existing test stand-in for `EmployeeService` (noted in `active-work.md`, 2026-09-28).

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='payroll.employee_reimbursement_request'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type, numeric_precision, numeric_scale FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name='employee_reimbursement_request'
      AND column_name IN ('requested_amount','approved_amount','posted_period');"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexname FROM pg_indexes WHERE schemaname='payroll' AND tablename='employee_reimbursement_request';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT code FROM reference.action WHERE code LIKE 'payroll.reimbursement_claim.%' ORDER BY code;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Types | both amounts `numeric` 19,4; `posted_period` `character` |
| Indexes | the three named in §6 plus the PK |
| Actions | `read`, `read_own`, `submit_own` |
| Suite | green, no skips; `ReimbursementClaimIT` and `ReimbursementClaimLockedPeriodIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The claim grows a `payment_status` and the pay run is asked to flip it | high — it is the legacy shape | §2 rules it out; `ClaimStatus` has three values; the IT asserts a ledger row, not a flag |
| The handler writes the ledger row for the requested amount, ignoring the decision | medium | `ReimbursementClaimIT` approves 1500 on 2000 and asserts 1500 in `core.pay_input` |
| An approve endpoint is added to this controller "for convenience" | medium | §4's contract has none; the reviewer checks the controller has five mappings |
| The receipt is accepted as multipart on the claim, re-creating Cloudinary-style storage | medium | The request takes `document_id` only; `W-21` owns bytes |
| The dispatcher retries a handler that threw after `record` committed | low — same transaction | `onApproved` is idempotent on `APPROVED`; the ledger's unique `source_ref` is the backstop |

## 10. Rollback

Nothing is deployed. Both scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `payroll.employee_reimbursement_request` |
| Flyway only, `ddl-auto` nowhere | `V097` (seed), `V098` (table) |
| `Money`/`BigDecimal` for money | `NUMERIC(19,4)`, `BigDecimal`, `Money.of` on the ledger call |
| Index on `tenant_id` plus lookup columns | three indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `payroll` uses `core` (`ApprovalService`, `PayInputService`, `DocumentService`, `EmployeeService`) and `shared` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for this table** — RLS and tenant-scoped finders |
| DEBT-004 Cloudinary keys in properties | **Discounted** — no Cloudinary; `W-21` stores bytes |
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-008 hand-built envelope | **Fixed** |
| DEBT-011 public Cloudinary URLs for employee documents | **Fixed** — `document_id` only, read through `W-21`'s signed links |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** |
| DEBT-031 `mockAdminReimbursements.js` imported by a live screen | **Deferred to `W-47.4`** — frontend |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | When is the ledger row written? | **On approval, by the outcome handler.** The ledger is append-only and the pay run reads nothing else. Legacy wrote at run time (`EmployeePayRunServiceImpl.java:1223-1239`); that path is gone |
| 2 | Which period? | **The current calendar month at approval, redirected by the ledger if locked.** `PayInputService.record` already does the redirect (`PayInputService.java:21-27`); the claim stores `posted_period` so the employee can see which payslip |
| 3 | How does a claim show "paid"? | **It does not.** `posted_period` plus the period lock is the state (`W-29.3` §5 `:161`). `W-36` renders it |
| 4 | Claim type: enum or catalogue? | **Catalogue.** `reimbursement_id` → `payroll.reimbursement`. The legacy six-value enum is the tenant's catalogue in disguise; `W-26.1` already holds `reimbursement_type` per component (`V045:10`) |
| 5 | The legacy `ReimbursementClaim` settings row? | **Retired.** It is mail flags and Zoho-style reminders (`ReimbursementClaimController.java:22-47`); reminders are `W-22.2`'s `reminder_rule`. `02-data-model.md:173` and `:350` should drop it — `/sync-docs` after merge, not this ticket |
| 6 | Partial approval? | **Stored, not tracked.** `approved_amount` may be below `requested_amount`; the remainder is not a balance. As legacy (`AdminReimbursementController.java:125`) |
| 7 | Who approves? | **The seeded flow: one `tenant-admin` step, 3-day escalation** (`V089:77-95`). A tenant changes it through `W-15.1`'s definition endpoints, not here |
