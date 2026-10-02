# Feature: Claims screens — reimbursement claims and ad-hoc deductions

| Field | Value |
|---|---|
| **Feature ID** | `W-47.4` · screens for `W-35.1` and `W-35.2` (ticket #66) · `PAY-12`, `PAY-13` |
| **Promoted to** | `docs/target-state/features/W-47-4-claims-screens.md` — **`W-47-4` with hyphens**, never `W-47.4`; `guard-edit` blocks the dotted form |
| **Owner** | biren |
| **Apps touched** | `code/frontend/src/payroll/claims` (new), one line in `src/core/approvals/itemRoutes.js`; `code/backend/payroll` — one read endpoint, one panel provider, two menu items, one added response field |
| **Related gaps** | DEBT-031 (fixed for new code), DEBT-011 (honoured), DEBT-026 (prevented) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | none. `W-35.1` on `main` `840bf2e`, `W-35.2` on `main` `9d5c0a0`, `W-45` and `W-46.4` on `main` `11ec157`. The `/me` panel mounts through `W-47.1b` § 5a's `PortalLayout` change (sayeed); if that is not on `main` when this branch is ready, this ticket makes that change exactly as § 5a describes it and `W-47.1b` drops it (§ 13 decision 3) |
| **Size** | M |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` — read additions only | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an employee files a claim and sees it in their panel, the approver opens it from the inbox and approves an amount, the officer sees it `APPROVED` with its period; the officer enters a batch of deductions, the employee sees them, the officer reverses one | 1 |
| Frontend area | `src/payroll/claims`, plus one line in `src/core/approvals/itemRoutes.js` that the file reserves for this (`itemRoutes.js:3`) | 1 |

Within cap. Claims and deductions stay one ticket by founder decision (§ 13 decision 1): same module, same
menu group, same pattern.

---

## 1. Problem

`W-35.1` and `W-35.2` give an API and no screen (`W-35-1-reimbursement-claims.md:170`,
`W-35-2-ad-hoc-deductions.md:146`). Four things stop a screen from working on today's API:

- An employee cannot list the reimbursement components to claim against. The list is
  `GET /api/v1/payroll/components/reimbursements` under `payroll.structure.read`, which the
  `employee` role does not hold.
  - `code/backend/payroll/src/main/java/com/infinevo/payroll/component/ReimbursementController.java:33`, `:51`
  - `code/backend/migration/src/main/resources/db/migration/reference/V100__employee_deduction_actions.sql:123-140`
- Both lists return `employee_id` and no name, so an officer's table would show UUIDs. `EmployeeService.displayNames` already resolves a batch.
  - `payroll/.../reimbursement/ReimbursementClaimResponse.java:15`
  - `payroll/.../deduction/EmployeeDeductionResponse.java:11`
  - `core/.../employee/EmployeeService.java:49`, `EmployeeServiceImpl.java:142`
- The approvals inbox can decide a claim with an amount but cannot open it: the item route for `REIMBURSEMENT` is `null`.
  - `code/frontend/src/core/approvals/DecideModal.jsx:33-38`
  - `code/frontend/src/core/approvals/itemRoutes.js:9`
- Nothing puts a claim screen in the menu or the `/me` portal.
  - `payroll/.../navigation/PayrollNavigation.java:27`
  - `payroll/.../portal/PayslipPanelProvider.java:16`

Legacy, being replaced: the admin queue falls back to mock rows when the call fails (DEBT-031).
- `legacy/Payroll-Fend-react/src/pages/mainPages/adminReimbursement/AdminReimbursementPage.jsx:10`, `:61-62`

Legacy screens ported for layout only: the employee claim modal and the deduction grid.
- `legacy/Payroll-Fend-react/src/pages/mainPages/userPortal/Reimbursement/ApplyReimbursementModal.jsx`
- `legacy/Payroll-Fend-react/src/pages/mainPages/deduction/gridDeduction.js:282`

## 2. Scope

**In scope**

- `/me` panel "Claims and deductions": my claims list and detail, "New claim" form, my deductions (read only)
- `/payroll/claims`: officer list with filters, claim detail
- `/payroll/deductions`: officer list with filters, batch entry grid, reverse with reason
- The inbox opens a claim through `itemRoutes.REIMBURSEMENT`
- Backend: the claimable-components read, `employee_name` on both responses, the panel provider, two menu items

**Out of scope**

- Approve or reject screens. The `W-46.4` inbox and `DecideModal` already do it.
- Receipt and proof upload for employees and payroll officers. Neither role holds `core.document.upload` (§ 13 decision 2).
  - `code/backend/migration/src/main/resources/db/migration/core/V037__document.sql:96-98`
- Editing or withdrawing a claim, or editing a deduction. The API has no such call (`W-35.1` § 2, `W-35.2` § 2).
- CSV import of deductions. The grid posts JSON, 1–500 lines.

## 3. Flow

```
[employee] /me → panel "Claims and deductions"
   "New claim"   --> claimService.components()          GET /v1/me/reimbursement-claims/components
                 --> claimService.submit(body)          POST /v1/me/reimbursement-claims
   list / detail --> claimService.listOwn(), getOwn(id)
   Deductions tab--> deductionService.listOwn()         GET /v1/me/employee-deductions

[approver] /approvals → row → "Open item"  --> /payroll/claims/:id   (itemRoutes.REIMBURSEMENT)
           Decide (DecideModal, unchanged, approvedAmount) --> W-15.2

[officer] /payroll/claims          --> claimService.list({employeeId,status,from,to,page})
          /payroll/claims/:id      --> claimService.get(id)
          /payroll/deductions      --> deductionService.list({employeeId,period,status,deductionType,page})
          "Enter deductions" grid  --> deductionService.enter(lines)   POST, 1–500, all-or-nothing
                                   <-- 400 fieldErrors.line → that grid row is marked
          "Reverse" (reason)       --> deductionService.reverse(id, reason)   DELETE ?reason=
```

## 4. Backend changes

All in `code/backend/payroll`. No new action code; every gate below exists.

| Layer | File | Change |
|---|---|---|
| Controller | `reimbursement/ReimbursementClaimController.java` | **add** `GET /api/v1/me/reimbursement-claims/components`, `@RequiresAction("payroll.reimbursement_claim.submit_own")`. A literal segment, so Spring prefers it over `/{id}` |
| Service | `ReimbursementClaimService(Impl)` | **add** `claimableComponents()`: the bound tenant's active, undeleted `payroll.reimbursement` rows — the same filter submit validates against (`W-35.1` § 4) |
| DTO | `reimbursement/ClaimableComponentResponse.java` | **new** — `id`, `code`, `name`, `max_limit` |
| DTO | `ReimbursementClaimResponse`, `EmployeeDeductionResponse` | **add** `employee_name`, filled from one `EmployeeService.displayNames(ids)` call per page or row. Additive; no field removed |
| Panel | `portal/ClaimsPanelProvider.java` | **new**, the shape of `PayslipPanelProvider`: code `claims`, module `PAYROLL`, title "Claims and deductions", order 6, endpoint `/api/v1/me/reimbursement-claims`, action `payroll.reimbursement_claim.read_own` |
| Navigation | `navigation/PayrollNavigation.java` | **add** `CLAIMS` (`payroll.claims`, `nav.payroll.claims`, `/payroll/claims`, `/api/v1/payroll/reimbursement-claims`, `payroll.reimbursement_claim.read`) and `DEDUCTIONS` (`payroll.deductions`, `nav.payroll.deductions`, `/payroll/deductions`, `/api/v1/payroll/employee-deductions`, `payroll.employee_deduction.read`) |

**API contract (additions only; the rest is `W-35.1` § 4 and `W-35.2` § 4 unchanged)**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me/reimbursement-claims/components` | — | `{status, message, data: [{id, code, name, max_limit}]}` | `payroll.reimbursement_claim.submit_own` |
| GET | every claim and deduction read | — | each row gains `employee_name` | unchanged |

## 5. Frontend changes

This ticket follows the `W-45` contract and the `payroll/tax` service pattern. Calls go through `apiClient` from `@shared/api/client`, with paths starting `/v1/…`. **Every payroll reply is unwrapped as `res.data.data`**, which is the fault that sent `W-47.2` back. Do not use `createService`: it returns the envelope.
- `src/payroll/tax/taxSettingsService.js:18-19`
- `src/shared/api/createService.js:17-20` (returns the envelope)
- `.claude/work/active-work.md:25`

A paged reply is Spring's `Page`: rows are in `data.content` and the count in `data.totalElements`.

| File | Change |
|---|---|
| `src/payroll/claims/claimService.js` | **new.** `components`, `submit`, `listOwn`, `getOwn`, `list(params)`, `get(id)` |
| `src/payroll/claims/deductionService.js` | **new.** `enter(lines)`, `list(params)`, `get(id)`, `reverse(id, reason)`, `listOwn` |
| `src/payroll/claims/ClaimForm.jsx` | **new.** Drawer. The component `Select` comes from `components()`; also amount (`InputNumber`, min 0.01, 2 decimals, sent as a string), bill date (no future dates) and description (max 500). When the amount is over `max_limit`, an inline warning shows but submit is not blocked (`W-35.1` § 4). Submit is disabled while posting. A `400` puts `fieldErrors` on the fields |
| `src/payroll/claims/MyClaimsPanel.jsx` | **new.** The portal panel. Tabs: "Claims", a table (bill date, component, requested, approved, status `Tag`, posted period, remarks) with row detail and "New claim"; "Deductions", a read-only table (period, type, amount, reason, status). A reversed deduction shows as struck through, with the reversal date |
| `src/payroll/claims/ClaimList.jsx` | **new.** `/payroll/claims`. Filters: employee (search), status, bill date range. Columns: employee name, component, bill date, requested, approved, status, posted period. A row opens the detail |
| `src/payroll/claims/ClaimDetail.jsx` | **new.** `/payroll/claims/:id`, `Descriptions` of every response field. When `status = SUBMITTED` it links to `/approvals/{approval_instance_id}`. On a `404` it shows the shell's not-found screen |
| `src/payroll/claims/DeductionList.jsx` | **new.** `/payroll/deductions`. Filters: employee, period, status, type. "Enter deductions" and "Reverse" are shown only with `useCan('payroll.employee_deduction.manage')`. "Reverse" needs a reason (1–255) and confirms; a `409` reloads the row |
| `src/payroll/claims/DeductionGrid.jsx` | **new.** Drawer with an editable table, one row per line: employee (search over `GET /v1/employees?q=`, whose reply is a bare `Page` with no envelope, `core/.../EmployeeController.java:71-79`), period (month picker, at most one month ahead), type (the six `DeductionType` values), amount, reason, remarks. Add row, remove row, at most 500. "Post" is disabled while posting, and a double click posts once (`W-35.2` § 4). A `400` marks row `fieldErrors.line` and posts nothing |
| `src/payroll/claims/claimLabels.js` | **new.** Status and type labels and `Tag` colours, in one place |
| `src/payroll/index.js` | `routes` gains `/payroll/claims`, `/payroll/claims/:id`, `/payroll/deductions`; `portalPanels` gains `{ code: 'claims', component: MyClaimsPanel }` |
| `src/core/approvals/itemRoutes.js` | `REIMBURSEMENT: (id) => (id ? \`/payroll/claims/${id}\` : null)`. The inbox passes `subjectId`, which is the claim id (`W-35.1` § 3) |

Amounts are shown as the API sends them, formatted for display. The screen adds nothing up and never uses floating-point arithmetic on money.

When a load fails, the screen shows `Result status="error"` with "Retry". **There is no mock or placeholder data anywhere** (DEBT-031).

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/payroll/claims` | `ClaimList` | inside `AppShell`; present when the feed carries `payroll.claims` |
| `/payroll/claims/:id` | `ClaimDetail` | same; reached from the list and from the inbox |
| `/payroll/deductions` | `DeductionList` | inside `AppShell`; present when the feed carries `payroll.deductions` |
| `/me` panel `claims` | `MyClaimsPanel` | portal, listed by `ClaimsPanelProvider` |

## 6. Database changes

None. No table, no column, no Flyway script.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit (Java) | `ReimbursementClaimServiceImplTest` (extend) | `claimableComponents` returns active rows only and skips deleted ones; another tenant's rows never appear |
| Integration | `ReimbursementClaimIT` (extend) | `employee` gets `200` on `/components`; a login linked to no employee gets `403`; list rows carry `employee_name` |
| Integration | `EmployeeDeductionIT` (extend) | list and batch rows carry `employee_name` |
| Unit (Java) | `ClaimsPanelProviderTest` | descriptor fields |
| Integration | `PortalPanelDiscoveryIT` (change) | `claims` is listed for a Payroll tenant and not for an HRMS-only one |
| Integration | navigation catalogue boot test (existing) | both new items resolve to a `GET` mapping |
| Unit | `claimService.test.js`, `deductionService.test.js` | every function hits its path; the reply is unwrapped from `data.data`; `reverse` sends `reason` as a query parameter |
| Component | `ClaimForm.test.jsx` | the `max_limit` warning shows and submit still works; a future bill date is refused; `fieldErrors` land on fields; one click posts once |
| Component | `MyClaimsPanel.test.jsx` | both tabs render; a reversed deduction is marked; a load error shows Retry and no rows |
| Component | `DeductionGrid.test.jsx` | a `400` with `fieldErrors.line = 2` marks row 3; the 501st row cannot be added; Post is disabled in flight |
| Component | `DeductionList.test.jsx` | Enter and Reverse are hidden without `manage`; Reverse needs a reason |
| Component | `ClaimList.test.jsx`, `ClaimDetail.test.jsx` | filters become query params; `SUBMITTED` links to the approval |
| Unit | `itemRoutes.test.js` (change) | `REIMBURSEMENT` builds `/payroll/claims/{id}` |
| Lint | existing `lint-rules.test.js` | `src/payroll` imports only `@shared/*` and `@shell/*` |

## 8. Verification

```bash
cd code/backend && ./mvnw -q -pl payroll -am spotless:check verify -Dit.test='ReimbursementClaimIT,EmployeeDeductionIT,PortalPanelDiscoveryIT'
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d
```

| Check | Expected |
|---|---|
| builds, lint, tests | `BUILD SUCCESS`, all clean |
| employee claim | as `employee.acme`, `/me` shows "Claims and deductions"; a new claim appears `SUBMITTED` |
| inbox | as `admin@acme.local`, `/approvals` lists it; "Open item" opens `/payroll/claims/{id}`; approving 1,500 of 2,000 shows `APPROVED`, 1,500.00 and a posted period |
| officer list | `/payroll/claims` shows the employee's name, not an id |
| deductions | a 3-row batch with a bad amount on row 2 shows the error on row 2 and posts nothing; fixed, all 3 post; reverse one and the employee's tab shows it reversed |
| no mock | stop the backend and reload `/payroll/claims`: an error with Retry, no rows |
| isolation | `admin@globex-full.local` sees none of Acme's claims or deductions |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A screen reads the envelope as the data, as in `W-47.2` | medium | the service tests assert `data.data`; no `createService` |
| A batch is posted twice on a retry | low | Post is disabled in flight; the API accepts the exposure (`W-35.2` § 4) |
| `PortalLayout` is changed by both this ticket and `W-47.1b` | medium | whichever merges second rebases; § 13 decision 3 |
| The approver cannot see a receipt | certain until § 13 decision 2 | the claim is still decidable; the receipt is optional in `W-35.1` |

## 10. Rollback

Revert the branch. No data and no migration. The two menu items and the panel disappear with it.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table; the components read is bound to the tenant like every `payroll` finder |
| Flyway | none |
| `Money` / `BigDecimal` | `max_limit` is `BigDecimal`; amounts are sent as strings with 2 decimals; the screen sums nothing |
| Index | none |
| Expand / contract | additive field and endpoint only |
| No module references another | `payroll` uses `core`'s `EmployeeService` and `NavigationContributor` seams; `src/payroll` reaches `core` paths over HTTP only; `itemRoutes.js` holds a path string, not an import |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-031 mock reimbursements on a live screen | **Fixed for new code**: no mock, an error state instead |
| DEBT-011 public receipt URLs | **Honoured**: only `document_id`, never a URL |
| DEBT-026 duplicated admin and employee screens | **Prevented**: `claimLabels.js` and the services are shared by the panel and the officer screens |

## 13. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | One ticket or split claims from deductions? | **One** — founder, 2026-10-02 |
| 2 | Receipt and proof upload: only `hr` holds `core.document.upload`, and the upload trusts a caller-supplied `employeeId` (`core/.../document/DocumentController.java:68-81`). | **Open.** Recommendation: a small `core` ticket that grants `payroll-officer` the upload, and adds an own-upload code that pins `employeeId` to the caller. Until then this ticket has no upload control. The forms keep a `document_id` slot for the follow-up |
| 3 | `W-47.1b` § 5a and this ticket both need modules to supply portal panels to `PortalLayout`. | **Whoever merges first builds it as § 5a says; the other rebases.** Neither waits |
| 4 | Where does an employee see their deductions? | **The same panel, on a second tab.** One panel, because the two are one menu concept for the employee |
