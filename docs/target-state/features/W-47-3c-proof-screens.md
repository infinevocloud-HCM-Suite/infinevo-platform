# Feature: Proof-of-investment screens

| Field | Value |
|---|---|
| **Feature ID** | `W-47.3c` · built as defect `D-152` · closes `D-95` |
| **Spec file** | `docs/target-state/features/W-47-3c-proof-screens.md` |
| **Owner** | Claude, `dev-claude-d152` |
| **Apps touched** | `code/frontend/src/payroll/proof`, one tab in `src/payroll/tax/DeclarationPage.jsx`; one menu item in `code/backend/payroll/.../navigation/PayrollNavigation.java`. No migration |
| **Related gaps** | DEBT-026 (prevented — one page per audience, one component per item) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-11-24 |
| **Blocked by** | none — `W-34.1`, `W-34.2`, `W-34.3` are on `main` |

## 1. Problem

| Today | Evidence |
|---|---|
| The declaration page has no place to upload a receipt | `code/frontend/src/payroll/tax/DeclarationPage.jsx:301-552` |
| No proof route, no proof menu item | `code/frontend/src/payroll/index.js:85-151`; `PayrollNavigation.java:84-85` |
| Screens deferred to an unnumbered `W-47.3c` | `W-47-3-tax-declaration-screens.md` §14 decision 3 |
| Receipt downloads need the bearer token; a plain link carries none | `proof/MyProofController.java:89-96`, `proof/ProofController.java:40-48` |

## 2. Scope

| In | Out |
|---|---|
| Employee: a **Proof of Investment** tab on the declaration page | Officer editing an employee's declaration (`W-34.2` §2, DEBT-026) |
| Officer: `/payroll/proof-of-investment`, verification queue, review drawer, chase list | Bulk decisions — the engine has none |
| The `POI_REMINDER` rule set from the chase list | Item comments thread — the API exists; a later part |

## 3. Behaviour

| Screen | What it does | API |
|---|---|---|
| Proof tab (`/me/taxDeclaration`) | Before the declaration is submitted, says so. Otherwise one card per declared item: declared amount, claimed amount, note, receipts, status in words and the reviewer's reason. While `editable`: change the claim, drop a receipt (PDF, JPG or PNG, 10 MB — the `W-21` store's cap), remove one, submit. Window dates and state shown from `proof_open`, `due_date` | `W-34.1` `/me/proof-of-investment/{fy}…` |
| Verification queue | Chase-list rows filtered to `SUBMITTED` by default, any status selectable, name or number search, counts from `/summary` | `W-34.3` list and summary |
| Review drawer | Every item with its receipts. A holder of `payroll.proof.review` verifies (amount ≤ claimed), disallows or returns with a reason, then approves or returns the proof. A holder of `payroll.proof.read` alone sees it read-only | `W-34.2` review, decide, final; `W-34.1` officer read |
| Chase list | Employees whose proof is not started, draft or returned; the `POI_REMINDER` / `POI_PENDING` / `POI_DUE_DATE` rule shown, and created by a holder of `core.reminder_rule.manage` | `W-34.3` list; `W-20.2` `/reminder-rules` |

Item status words, employee side: `PENDING` reads *Not submitted* on a draft and *Under review* once submitted;
`APPROVED` *Verified*; `DISALLOWED` *Rejected*; `RETURNED` *Returned — replace the receipt*.

**Receipts.** Every receipt is fetched through the authenticated client as a file and opened from a
local copy, so no URL to the API is ever handed to the browser without its token.

## 4. Backend changes

| File | Change |
|---|---|
| `payroll/.../navigation/PayrollNavigation.java` | `payroll.proofs` item: `/payroll/proof-of-investment`, target `GET /api/v1/payroll/proof-of-investment`, `payroll.proof.read`, after Deductions |

## 5. Frontend changes

| File | Change |
|---|---|
| `src/payroll/proof/proofService.js` | every call above |
| `src/payroll/proof/proofLabels.js`, `openReceipt.js` | words for codes; file checks; the token-carrying receipt open |
| `src/payroll/proof/MyProofPanel.jsx`, `ProofItemCard.jsx` | employee tab, one card per item |
| `src/payroll/proof/ProofQueuePage.jsx`, `ProofReviewDrawer.jsx`, `ProofChaseList.jsx` | officer screen |
| `src/payroll/tax/DeclarationPage.jsx` | third tab |
| `src/payroll/index.js`, `src/shell/navigation/navLabels.js` | routes `/payroll/proof-of-investment` and `/:proofId` (mounted with `/approvals` too), menu label |
| `src/core/approvals/itemRoutes.js` | `PROOF_OF_INVESTMENT` opens `/payroll/proof-of-investment/{subjectId}` — the subject is the proof (`ProofApprovalStarter`) |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `proof/*.test.js(x)` | each screen: states, upload limits, decide payloads, read-only without review, receipt fetched with the client |
| Unit | `PayrollNavigationTest.java` | the item matches `ProofChaseController`'s list GET and action |
