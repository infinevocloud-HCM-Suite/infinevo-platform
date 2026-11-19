# Feature: Mobile Approvals Inbox & 1-Tap Decisions

| Field | Value |
|---|---|
| **Feature ID** | `W-98.5` · `CORE-MOBILE-APPROVALS` |
| **Promoted to** | `docs/target-state/features/W-98-5-mobile-approvals-inbox.md` — **`W-98-5` with hyphens**, never `W-98.5` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/frontend` (`src/mobile/approvals/`, `src/mobile/MobileShell.jsx`, `src/mobile/MobileTabBar.jsx`). No backend change. |
| **Related gaps** | founder 2026-11-13 planned product roadmap (`W-98` Mobile app slice 5: Approvals) |
| **Status** | **Spec corrected 2026-11-19 after review. Code on `feature/krushna-specs` does not yet meet it.** |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | `W-98.1` |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | none (uses existing `core` approval endpoints) | 1 | Pass |
| Flyway migration | none | 1 | Pass |
| Externally testable behaviour | An approver sees pending requests on the phone and approves or rejects one | 1 | Pass |
| Frontend area | `src/mobile/approvals/` | 1 | Pass |

---

## 1. Problem

| # | What is missing today | Evidence |
|---|---|---|
| 1 | The approvals inbox is a desktop table. | `code/frontend/src/core/approvals/Inbox.jsx` |
| 2 | Requests wait until the approver is back at a computer. | — |

---

## 2. Scope

**In scope**

| Area | Rule |
|---|---|
| Screen | `MobileApprovalsScreen.jsx` on the Approvals tab (`/me/approvals`), shown only with `core.approval.decide`. |
| Data | Pending steps from `GET /api/v1/approvals/pending` (`ApprovalStepResponse`). |
| Card | Employee name (`subjectEmployeeName`), request type (`flowType`), `summary`, and Approve / Reject buttons. The response carries no employee code, so none is shown. |
| Filter | All, Leave, WFH, Regularization, Overtime, Other. They map to `flowType` values `LEAVE`, `WORK_FROM_HOME`, `REGULARIZATION`, `OVERTIME`; every other value of `ApprovalFlowType` falls under Other. |
| More than one page | The list loads 20 at a time and loads the next page when the user reaches the end. Segment counts and the tab badge use the server's `totalElements`, not the number of loaded cards. |
| Approve | Optional comment → `POST /api/v1/approvals/steps/{stepId}/decide` with `{"decision":"APPROVED","comment":…}`. |
| Reject | Comment is required → same call with `{"decision":"REJECTED","comment":…}`. |
| After a decision | The card is removed and the badge goes **down** by one. |
| Already decided | If the step was decided elsewhere the server answers **400**. The app then says "This request was already decided", reloads the list, and the stale card is gone. Any other error leaves the card in place with the server's message. |
| Tab badge | `MobileShell.jsx` reads `pendingCount` from `approvalSlice` and passes it to `MobileTabBar` as `pendingBadgeCount`. The count is loaded when the shell mounts and after every decision, not only when the Approvals tab is open. |
| Detail | `MobileApprovalDetailDrawer.jsx` shows the instance and its history from `GET /api/v1/approvals/{instanceId}` and `…/history`. |
| States | Loading, empty ("Nothing to approve") and error views differ. Buttons disable while a call runs. |

**Out of scope**

- Bulk approve; approval definitions; delegation (`W-15.3`); reassign.
- Changing the approved amount on reimbursement steps (desktop only).

---

## 3. Flow

```
Shell mounts  --> GET /api/v1/approvals/pending?page=0&size=20 --> totalElements --> tab badge
Approvals tab --> cards (next page on scroll)
Approve / Reject --> POST /api/v1/approvals/steps/{stepId}/decide {decision, comment}
   200 --> remove card, badge - 1
   400 already decided --> message, reload list
```

---

## 4. Backend changes

None. The endpoints below exist in `code/backend/core/src/main/java/com/infinevo/core/approval/ApprovalController.java`.

**API contract** (all existing)

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/approvals/pending?page&size` | `size` default 20, max 100 | 200 page of `ApprovalStepResponse` | `core.approval.decide` |
| GET | `/api/v1/approvals/{instanceId}` | — | 200 `ApprovalInstanceDetailResponse` | `core.approval.decide` |
| GET | `/api/v1/approvals/{instanceId}/history` | — | 200 history | as on the controller |
| POST | `/api/v1/approvals/steps/{stepId}/decide` | `ApprovalDecideRequest` `{decision: "APPROVED"\|"REJECTED", comment?, approvedAmount?}` | 200 empty · **400** already decided or bad input · 404 unknown step | `core.approval.decide` |

## 5. Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/mobile/approvals/mobileApprovalService.js` | Paged pending list, detail, decide. |
| `code/frontend/src/mobile/approvals/MobileApprovalsScreen.jsx` | Screen, filter, paging, already-decided handling, count dispatch. |
| `code/frontend/src/mobile/approvals/ApprovalRequestCard.jsx` | Card. |
| `code/frontend/src/mobile/approvals/MobileApprovalDetailDrawer.jsx` | Detail and history. |
| `code/frontend/src/mobile/MobileShell.jsx` | Route; loads the count; passes `pendingBadgeCount`. |
| `code/frontend/src/mobile/MobileTabBar.jsx` | Shows the badge it is given. |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/me/approvals` | `MobileApprovalsScreen` | `MobileShell` (native only); tab hidden without `core.approval.decide` |

## 6. Database changes

None. Uses `core.approval_instance` and `core.approval_step` through the existing endpoints.

## 7. Tests

Tests use the real paths and the real values `APPROVED` / `REJECTED`.

| Type | File | Covers |
|---|---|---|
| Unit | `code/frontend/src/mobile/approvals/mobileApprovalService.test.js` | Decide path uses the step id; body values; paging parameters; counts from `totalElements`. |
| Component | `code/frontend/src/mobile/approvals/MobileApprovalsScreen.test.jsx` | Filters; next page loads; a 400 "already decided" shows the message and reloads; another error keeps the card; loading, empty, error. |
| Component | `code/frontend/src/mobile/approvals/ApprovalRequestCard.test.jsx` | Approve sends `APPROVED`; Reject needs a comment and sends `REJECTED`. Exact-match assertions, one element expected. |
| Component | `code/frontend/src/mobile/MobileShell.test.jsx` | The tab badge shows the pending count and drops by one after a decision. |
| Existing backend | `code/backend/core/src/test/java/com/infinevo/core/approval/ApprovalAssigneeIT.java` | Unchanged. |

## 8. Verification

```bash
cd code/frontend && npm test -- src/mobile/approvals/ src/mobile/MobileShell.test.jsx && npm run lint
```

| Check | Expected | Result |
|---|---|---|
| Frontend tests and lint | Pass, 0 warnings | not run |
| Approver opens the app | Tab badge shows the pending total before the tab is opened | not run |
| Approve a leave request | Card gone; badge down by one; the request is APPROVED on the web | not run |
| Reject without a comment | Blocked on the phone | not run |
| Decide on the web first, then tap Approve on the phone | "Already decided" message; list reloads | not run |
| More than 20 pending | Scrolling loads the rest; badge shows the full total | not run |
| Employee without `core.approval.decide` | No Approvals tab | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Two approvers decide the same step | Low | The 400 is handled as in Scope. |
| Accidental reject | Low | A comment is always required. |
| Badge drifts from the real count | Low | Reloaded on mount and after each decision. |

## 10. Rollback

Remove the `/me/approvals` mobile route and tab; the web inbox is untouched.

---

## Code gaps against this spec (2026-11-19)

| Gap | Where |
|---|---|
| The tab badge never shows: the shell renders the tab bar without `pendingBadgeCount` | `code/frontend/src/mobile/MobileShell.jsx:99` · `code/frontend/src/mobile/MobileTabBar.jsx:13,55` |
| An already-decided step gives a generic error and the stale card stays | `code/frontend/src/mobile/approvals/MobileApprovalsScreen.jsx:154-157` |
| Only the first 50 items are fetched and the counts come from that page | `code/frontend/src/mobile/approvals/mobileApprovalService.js:24-26,40-56` |
| A card test was loosened to "at least one match" | `code/frontend/src/mobile/approvals/ApprovalRequestCard.test.jsx:37` |
