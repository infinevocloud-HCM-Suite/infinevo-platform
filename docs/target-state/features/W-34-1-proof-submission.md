# Feature: Proof of investment — window and submission

| Field | Value |
|---|---|
| **Feature ID** | `W-34.1` · from ticket #46 (`W-34`) · `PAY-11` part 1 of 3 |
| **Promoted to** | `docs/target-state/features/W-34-1-proof-submission.md` on the developer's `dev-<name>` branch — **`W-34-1` with hyphens**, never `W-34.1`; `guard-edit` blocks the dotted form |
| **Owner** | devashis |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for these tables), DEBT-011 (fixed — no public URL), DEBT-018 (honoured), DEBT-022 (fixed), DEBT-025 (not ported), DEBT-026 (not ported) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | nothing open — `W-32.1`–`.4` (`b704f3c`) and `W-21` (`b6e6012`) are on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V110` action rows · `V111` proof columns on the window · `V112`–`V114` one table each | 1 — **exception requested**, §14 |
| Externally testable behaviour | an employee with a submitted declaration attaches files to each declared line and submits the proof while the proof window is open | 1 |
| Frontend area | none — screens are a later `W-47` part | 1 |

`W-34` was split on 2026-09-29: `.1` submission (here), `.2` verification through the approval
engine, `.3` reminders and the chase list. `.2` and `.3` wait on this one.

---

## 1. Problem

| Today (frozen) | Evidence |
|---|---|
| Three POI implementations; two controllers share one path | `legacy/docs/GAP_INVENTORY.md` DEBT-025 |
| Proof files are public Cloudinary URLs | `entity/EmployeeITDeclaration/poi/EmployeePOIDocument.java` `documentUrl`; DEBT-011 |
| Submitting a proof also submits the declaration | `serviceimpl/employeeitdeclaration/EmployeeProofOfInvestmentServiceImpl.java:1275-1279` |
| The deadline is text on a settings row with no financial year | `entity/claimsanddeclarations/ProofOfInvestment.java` `lastDateForPoi` |
| Status reuses the pay-run enum | `EmployeeProofOfInvestment.java:50`, `EmployeePOIItem.java:42` |

Paths above are under `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/`.

What is kept: items built from the declared lines (`EmployeeProofOfInvestmentServiceImpl.java:680-900`),
the lock (`:1177`) and the mandatory-attachment rule on submit (`:1234`).

## 2. Scope

**In scope**

- Five proof columns on the per-year window `payroll.income_tax_declaration` (`W-32.1`): opens, due, lock, attachment mandatory, comment mandatory
- `payroll.employee_proof_of_investment` — one per declaration
- `payroll.employee_proof_item` — one per declared line that needs proof
- `payroll.employee_proof_item_document` — links an item to `core.document` rows
- Employee: open, set claimed amount, upload / remove / download a file, submit, resubmit after a return
- Officer: read any employee's proof
- `ProofSubmittedEvent` — published on submit; `W-34.2` starts the approval from it
- A guard on `W-32.1`'s reopen: refused while a proof is `SUBMITTED` or `APPROVED`
- Four action codes

**Out of scope**

- Review, decisions, comments, tax effect — `W-34.2`
- Reminders and the chase list — `W-34.3`
- Withdrawing a submitted proof — the engine has no cancel (§13 decision 4)
- Screens — a later `W-47` part

## 3. Flow

```
[officer]  PUT /payroll/tax-declaration/settings/{fy}   (W-32.1 endpoint, five new fields)

[employee] GET  /me/proof-of-investment/{fy}
   --> TaxDeclarationService.require(employee, fy); status != SUBMITTED --> 409 NOT_SUBMITTED
   --> find or create proof (DRAFT); sync items from the declared lines   (only while DRAFT or REJECTED)
[employee] PUT  /me/proof-of-investment/{fy}/items/{itemId}          claimed_amount, employee_note
[employee] POST /me/proof-of-investment/{fy}/items/{itemId}/documents   multipart
   --> DocumentService.store(INVESTMENT_PROOF, employeeId, name, stream)   (core, W-21)
[employee] POST /me/proof-of-investment/{fy}/submit
   --> proofOpen(fy) else 409 PROOF_WINDOW_CLOSED; attachment rule else 409 ATTACHMENT_REQUIRED
   --> DRAFT|REJECTED -> SUBMITTED, submitted_at
   --> publish ProofSubmittedEvent(tenant, proofId, employeeId, fy)       (W-34.2 listens)
   --> NotificationService.compose(POI_SUBMITTED, employeeId, …)          (core, W-20.1)
```

`proofOpen(fy)` is true when `poi_locked` is false, both dates are set, and
`poi_opens_on <= today <= poi_due_date`. `editable` is `proofOpen` and status `DRAFT` or `REJECTED`.

**Item sync.** One item per line with a positive amount. Lines gone since the last sync are
deleted with their links; new lines are added `PENDING`; `declared_amount` is refreshed.

| `source_kind` | Source line | `declared_amount` |
|---|---|---|
| `HOUSE_RENT` | `employee_inv_house_rent` (`V073`) | `amount_per_month` × months `from_month`..`to_month` inclusive |
| `HOME_LOAN_PRINCIPAL` | `employee_inv_home_loan` (`V074`) | `principal_paid` |
| `HOME_LOAN_INTEREST` | `employee_inv_home_loan` | `interest_paid` |
| `LET_OUT_PROPERTY` | `employee_inv_let_out_property_line` (`V076`) | `amount` |
| `SECTION_6A` | `employee_inv_section6a` (`V077`) | `amount` |
| `PREV_EMPLOYMENT` | `employee_inv_prev_employment` (`V079`), `entered_by = 'EMPLOYEE'` only | `amount` |

Pre-tax deductions and other income are **not** items (§13 decision 2).

## 4. Backend changes

New, under `code/backend/payroll/src/main/java/com/infinevo/payroll/proof/`.

| Layer | File | Change |
|---|---|---|
| Entity | `EmployeeProofOfInvestment`, `EmployeeProofItem`, `EmployeeProofItemDocument` | new, `@Table(schema = "payroll")`; `ProofStatus { DRAFT, SUBMITTED, APPROVED, REJECTED }`, `ProofItemStatus { PENDING, APPROVED, DISALLOWED, RETURNED }`, `ProofSourceKind` |
| Repository | three | new; every finder takes `tenantId` |
| Service | `ProofItemSync` | new — builds the item set from the six readers in §3 |
| Service / Impl | `ProofService`, `ProofServiceImpl` | new — `readOwn`, `updateItemOwn`, `attachOwn`, `detachOwn`, `openDocumentOwn`, `submitOwn`, `read(employeeId, fy)`; `proofOpen(fy)` |
| Event | `ProofSubmittedEvent(UUID tenantId, UUID proofId, UUID employeeId, String financialYear)` | new record |
| Controller | `MyProofController`, `ProofController` | new |
| Exception | `ProofConflictException` → `409` with a reason code, as `DeclarationNotEditableException` | new |
| Changed | `taxdeclaration/IncomeTaxDeclarationWindow`, its request / response DTOs | five fields |
| Changed | `taxdeclaration/TaxDeclarationServiceImpl.reopenOwn` | `409 PROOF_IN_PROGRESS` when this declaration's proof is `SUBMITTED` or `APPROVED`; officer `reopen` unchanged |

**API contract** — `status` / `message` / `data` envelope, as `W-32.1`.

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me/proof-of-investment/{fy}` | — | proof, items with documents, `proof_open`, `editable`, `due_date`; created on first read; `409 NOT_SUBMITTED` | `payroll.proof.read_own` |
| PUT | `/api/v1/me/proof-of-investment/{fy}/items/{itemId}` | `claimed_amount` (≥ 0), `employee_note` (≤ 1000) | item; `409 NOT_EDITABLE` | `payroll.proof.submit_own` |
| POST | `/api/v1/me/proof-of-investment/{fy}/items/{itemId}/documents` | multipart `file` | `201` link with document id, name, size; `409 NOT_EDITABLE`; `413`/`415` from `W-21` | `payroll.proof.submit_own` |
| DELETE | `/api/v1/me/proof-of-investment/{fy}/items/{itemId}/documents/{documentId}` | — | `204`; unlink + `DocumentService.delete` (soft) | `payroll.proof.submit_own` |
| GET | `/api/v1/me/proof-of-investment/{fy}/items/{itemId}/documents/{documentId}` | — | the file, streamed through `DocumentService.open` | `payroll.proof.read_own` |
| POST | `/api/v1/me/proof-of-investment/{fy}/submit` | — | `200` `SUBMITTED`; `409 PROOF_WINDOW_CLOSED`, `ALREADY_SUBMITTED`, `NOTHING_CLAIMED`, `ATTACHMENT_REQUIRED` | `payroll.proof.submit_own` |
| GET | `/api/v1/payroll/employees/{employeeId}/proof-of-investment/{fy}` and `…/documents/{documentId}` | — | as the `/me` read; `404` when no proof | `payroll.proof.read` |

`/me` resolves the employee through `EmployeeService.currentEmployee()`, never a path id. A
document id not linked to that item is `404`, so no one downloads another employee's file by
guessing an id.

Submit rules: at least one item with `claimed_amount > 0` else `NOTHING_CLAIMED`. When
`poi_attachment_mandatory`, every such item has ≥ 1 document, else `ATTACHMENT_REQUIRED`
with the item ids (`EmployeeProofOfInvestmentServiceImpl.java:1234-1250`).

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `reference/V110__proof_actions.sql` | rows in `reference.action`, `core.role_action` | n/a | additive |
| `payroll/V111__proof_window_columns.sql` | `ALTER TABLE payroll.income_tax_declaration` | yes (existing) | additive |
| `payroll/V112__employee_proof_of_investment.sql` | `payroll.employee_proof_of_investment` | yes | additive |
| `payroll/V113__employee_proof_item.sql` | `payroll.employee_proof_item` | yes | additive |
| `payroll/V114__employee_proof_item_document.sql` | `payroll.employee_proof_item_document` | yes | additive |

**`V110`** — four codes, the `V070` pattern, granted to every existing tenant's system roles:

| Code | Granted to |
|---|---|
| `payroll.proof.read` | `payroll-officer`, `hr` |
| `payroll.proof.review` | `hr` — used by `W-34.2`; seeded here so the lane has one action script |
| `payroll.proof.read_own` | `employee` |
| `payroll.proof.submit_own` | `employee` |

**`V111`**: `poi_opens_on date NULL` · `poi_due_date date NULL` · `poi_locked boolean NOT NULL DEFAULT false`
· `poi_attachment_mandatory boolean NOT NULL DEFAULT true` · `poi_comment_mandatory boolean NOT NULL DEFAULT false`
· `CHECK (poi_due_date IS NULL OR poi_opens_on IS NULL OR poi_due_date >= poi_opens_on)`.

**`V112` `employee_proof_of_investment`**: `id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant(tenant_id)` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`declaration_id uuid NOT NULL REFERENCES payroll.employee_investment_declaration(id)` ·
`financial_year varchar(9) NOT NULL` ·
`status varchar(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED'))` ·
`approval_instance_id uuid NULL REFERENCES core.approval_instance(id)` (written by `.2`) ·
`reviewer_note varchar(1000)` (the reason on a final return, written by `.2`) ·
`submitted_at timestamptz` · `decided_at timestamptz` · four audit columns.

**`V113` `employee_proof_item`**: `id uuid PK` · `tenant_id` · `proof_id uuid NOT NULL REFERENCES payroll.employee_proof_of_investment(id) ON DELETE CASCADE` ·
`source_kind varchar(24) NOT NULL CHECK (source_kind IN ('HOUSE_RENT','HOME_LOAN_PRINCIPAL','HOME_LOAN_INTEREST','LET_OUT_PROPERTY','SECTION_6A','PREV_EMPLOYMENT'))` ·
`source_line_id uuid NOT NULL` (polymorphic; no FK) · `description varchar(150) NOT NULL` ·
`declared_amount NUMERIC(19,4) NOT NULL CHECK (declared_amount >= 0)` ·
`claimed_amount NUMERIC(19,4) NULL CHECK (claimed_amount >= 0)` ·
`approved_amount NUMERIC(19,4) NULL CHECK (approved_amount >= 0)` ·
`status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','DISALLOWED','RETURNED'))` ·
`employee_note varchar(1000)` · `reviewer_note varchar(1000)` · four audit columns.

**`V114` `employee_proof_item_document`**: `id uuid PK` · `tenant_id` ·
`item_id uuid NOT NULL REFERENCES payroll.employee_proof_item(id) ON DELETE CASCADE` ·
`document_id uuid NOT NULL REFERENCES core.document(id)` · `created_at`, `created_by`.

| Legacy | Here |
|---|---|
| `EmployeePOIItem.actualAmount` | `claimed_amount` |
| `EmployeePOIItem.investmentType` + `section6aItemId` | `source_kind` + `source_line_id`, for every kind |
| `EmployeePOIDocument.documentUrl` (public) | `document_id` → `core.document`, streamed through the API |
| `ProofOfInvestment.lastDateForPoi` (text), `isPoiLocked`, `isAttachmentMandatoryPoiForPortal`, `isCommentsMandatoryForPoiApproval` | the five `V111` columns, per financial year |
| `EmployeePOIDocument` + `EmployeeInvestmentProofFile` + `ProofOfInvestmentDocument` | one link table (DEBT-025) |
| `considered_for_it`, `final_annual_tax`, `monthToConsiderPoi`, `canTdsExceedAnnualLimit` | **not ported** — tax follows the event in `W-34.2`; TDS spread is `W-36.1`'s |

- [x] `tenant_id` + RLS `tenant_isolation`, `CASE` form, on all three new tables — `migration/README.md` §row-level security
- [x] Indexes, `tenant_id` leading (DEBT-018): `uk_employee_proof_of_investment_tenant_declaration (tenant_id, declaration_id)` · `idx_employee_proof_of_investment_tenant_fy_status (tenant_id, financial_year, status)` · `uk_employee_proof_item_tenant_proof_source (tenant_id, proof_id, source_kind, source_line_id)` · `uk_employee_proof_item_document_tenant_item_document (tenant_id, item_id, document_id)` · `idx_employee_proof_item_document_tenant_document (tenant_id, document_id)`
- [x] Money `NUMERIC(19,4)`, `BigDecimal` in Java
- [x] Expand only — three new tables, five nullable-or-defaulted columns

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../proof/ProofItemSyncTest.java` | each of the six kinds maps to its amount; house rent months inclusive (Apr–Mar = 12); zero-amount lines skipped; officer-entered previous employment skipped; a removed line removes its item; a changed amount refreshes `declared_amount` |
| Unit | `payroll/.../proof/ProofRulesTest.java` | `proofOpen`: false when locked, dates null, before opens, after due; true on both boundary days; submit refused with nothing claimed; attachment rule on and off |
| Integration | `payroll/.../proof/ProofSubmissionIT.java` | **acceptance**: officer sets the proof window; employee with a `DRAFT` declaration gets `409 NOT_SUBMITTED`; after declaration submit, `GET` creates a `DRAFT` proof with one item per declared line; claim + upload a PDF; submit ⇒ `SUBMITTED`, `ProofSubmittedEvent` captured once; second submit `409 ALREADY_SUBMITTED`; `reopen` of the declaration `409 PROOF_IN_PROGRESS`; window past due ⇒ submit `409 PROOF_WINDOW_CLOSED` |
| Integration | `payroll/.../proof/ProofDocumentIT.java` | download streams the uploaded bytes; a document id from another item or employee is `404`; delete unlinks and soft-deletes |
| Integration | `payroll/.../proof/ProofRlsIT.java` | as `app_user`, tenant A cannot read or insert tenant B's proof, item or link |
| Integration | `payroll/.../proof/ProofActionSeedIT.java` | four codes exist and are granted, in a tenant provisioned before `V110` |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relname, relrowsecurity FROM pg_class WHERE oid IN ('payroll.employee_proof_of_investment'::regclass,
   'payroll.employee_proof_item'::regclass,'payroll.employee_proof_item_document'::regclass);"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT code FROM reference.action WHERE code LIKE 'payroll.proof.%' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name FROM information_schema.columns WHERE table_schema='payroll'
    AND table_name='income_tax_declaration' AND column_name LIKE 'poi_%' ORDER BY 1;"
grep -rn "double\|float\|Double\|Float" code/backend/payroll/src/main/java/com/infinevo/payroll/proof/ || echo "no floating point"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | three rows, all `t` |
| Action codes | `read`, `read_own`, `review`, `submit_own` |
| Window columns | five `poi_*` columns |
| Floating point | `no floating point` |
| Suite | green, no skips; `ProofSubmissionIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Item sync deletes an item that already carries documents | medium | sync runs only in `DRAFT` / `REJECTED`; the declaration cannot reopen while `SUBMITTED` / `APPROVED`; the IT covers a removed line |
| `source_line_id` has no FK and points at a deleted line | low | sync removes it on the next read; `.2` reads through the same sync readers |
| A file downloaded by guessing a document id | low | the link row is looked up by item and employee first; `ProofDocumentIT` |
| `V110` grants reach only tenants provisioned after it | medium | grant loop over existing tenants, as `V070`; `ProofActionSeedIT` |

## 10. Rollback

Nothing is deployed. All five scripts are additive and forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | three tables; `V111` alters one that already has both |
| Flyway only, `ddl-auto` nowhere | five scripts |
| `Money`/`BigDecimal` | three `NUMERIC(19,4)` columns |
| Index on `tenant_id` plus lookup columns | five indexes, `tenant_id` leading |
| Expand / contract | additive only |
| No module references another module | `payroll` → `core` (`EmployeeService`, `DocumentService`, `NotificationService`) and `shared` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 cross-tenant exposure | **Fixed for these tables** |
| DEBT-011 public Cloudinary URLs | **Fixed** — `core.document`, streamed with a tenant and owner check |
| DEBT-018 no indexes | **Honoured** |
| DEBT-022 unscoped finders | **Fixed** — every finder takes `tenantId` |
| DEBT-025 two controllers on one path | **Not ported** — one proof model, one path family |
| DEBT-026 duplicated admin/employee screens | **Not ported** — one API for both |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | Does submitting a proof submit the declaration? | **No.** It requires a `SUBMITTED` declaration and never writes its status (`W-32-1-tax-declaration-window.md:304`) |
| 2 | Which lines need proof? | **Six kinds in §3.** Legacy also made items of pre-tax deductions and other income (`EmployeeProofOfInvestmentServiceImpl.java:771`, `:890`); the first comes from payroll's own figures, the second is income, not a saving |
| 3 | Where do the proof settings live? | **On the per-year window row.** Legacy's settings row has no year, so last year's deadline blocked this year |
| 4 | May the employee withdraw a submitted proof? | **No.** Legacy allowed it (`:1328`); the engine has no cancel (`core/.../approval/InstanceStatus.java`). The reviewer returns it instead (`W-34.2`) |
| 5 | May the declaration reopen under a proof? | **Not by the employee while `SUBMITTED` or `APPROVED`.** Items would lose their lines mid-review. Legacy reopened it only on final reject (`:1830`) |

## 14. Open for the founder

| # | Question | Proposed |
|---|---|---|
| 1 | Size-cap exception: five scripts, one table each at most | **Grant.** `migration/README.md` asks for one table per file; same shape as `W-32.1` |

---

## 15. As built (2026-09-30, `dev-devashish`)

What differs from, or is decided beyond, sections 1 to 14.

| Topic | As built |
|---|---|
| Grants (`V110`) | Its own `core.seed_proof_roles` function and a trigger on `core.tenant`, the `V070` pattern, **not** a redefinition of `core.seed_system_roles`. That function is rewritten wholesale by several scripts and a copy taken from an older body drops the grants added in between (`W-41` hit this). The trigger is named `tenant_seed_tax_proof_roles` on purpose: Postgres fires same-event triggers by name, the system roles are created by `tenant_seed_system_roles`, and a name sorting before it would grant to roles that do not exist yet. `ProofActionSeedIT` covers a tenant created by the trigger, one seeded later, and a rerun |
| File cap | At most **10 files per item**, `409 DOCUMENT_LIMIT`. The spec set none |
| Window `PUT` | The five proof fields are optional: an absent one keeps the stored value, so a caller that only manages the declaration window never sees them. A date cannot be cleared once set, only replaced. `due < opens` is `400`. The old constructors of `TaxDeclarationWindowRequest` and `TaxDeclarationWindowResponse` are kept, so no existing caller changed |
| Reopen guard | Through `taxdeclaration/ProofInProgressCheck`, a port the declaration package owns and `proof` implements, so `taxdeclaration` imports nothing from `proof`. The officer's `reopen` is not guarded |
| Submit | Also refused `409 NOT_SUBMITTED` if an officer has reopened the declaration since, because the items mirror the declared lines only while it stays submitted. A resubmission after a return sets every item back to `PENDING`, clears `approved_amount` and the proof's reviewer note and `decided_at`; item reviewer notes stay until replaced |
| Concurrency | The proof is found by id first and loaded second, **under** `SELECT ... FOR UPDATE`. Loading it first left Hibernate holding the stale `DRAFT` state after the second request had waited, and two simultaneous submits both succeeded; `ProofSubmissionIT.concurrentSubmitIsOnce` caught it. Creating the proof takes a lock on the declaration row first, because a lost insert race aborts a PostgreSQL transaction |
| Reads | `GET /me/proof-of-investment/{fy}` creates and syncs, as specified. The officer read never does |
| Notification | `POI_SUBMITTED` is composed inside the submit transaction, as `NotificationServiceImpl` intends (it logs a missing template rather than throwing); a failure to compose is logged with ids only and does not undo the submit |
| Tests | `ProofItemSyncTest`, `ProofRulesTest`, `ProofSourceReaderTest`, `ProofControllerTest`; `ProofSubmissionIT`, `ProofDocumentIT`, `ProofRlsIT`, `ProofActionSeedIT`. The ITs use `ProofTestDocuments`, a `@Primary` document service that writes real `core.document` rows, because the link table has a foreign key to it and `PayrollTestApp`'s stand-in writes none. They clear their proof rows in every teardown: other ITs delete the declarations, employees and documents these rows reference |
| Review pass, 2026-10-01 | **Submit now syncs the items with the declaration first** (while the proof is editable and the declaration is submitted), so a proof submitted without being read again after the declaration changed does not claim a line that was removed (`ProofSubmitReviewFixesIT`). **A file soft-deleted through core no longer counts** as attached at submit, in the per-item limit, or in the read; detaching it no longer fails, because `DocumentServiceImpl.get`, `open` and `delete` now name `NotFoundException` in `noRollbackFor` (a caller that treats "already gone" as normal had its joined transaction marked rollback-only and failed at commit; `DocumentNotFoundTransactionTest`). **The notification is composed after the submit commits and in its own transaction**, so an error inside the notification service, a database error included, cannot undo the submit |
| Not done here | Review, decisions, comments and the tax effect are `W-34.2`; the chase list and reminders are `W-34.3`. `TaxDeclarationController`'s officer endpoints are unchanged |
