# Feature: Mobile Approvals Inbox & 1-Tap Decisions

| Field | Value |
|---|---|
| **Feature ID** | `W-98.5` · `CORE-MOBILE-APPROVALS` |
| **Promoted to** | `docs/target-state/features/W-98-5-mobile-approvals-inbox.md` — **`W-98-5` with hyphens**, never `W-98.5` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/frontend` (`src/mobile/approvals/`, `src/mobile/MobileTabBar.jsx`), `code/backend/core` |
| **Related gaps** | founder 2026-11-13 planned product roadmap (`W-98` Mobile app slice 5: Approvals) |
| **Status** | **Built on `feature/krushna-specs` (100% tests/lint green, pending manager review)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | `W-98.1` (mobile foundation & auth container) |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `core` (approval workflow) | 1 | **Pass (1 module)** |
| Flyway migration | None (creates 0 tables; uses existing `core.approval_instance` and `core.approval_step`) | 1 | **Pass (0 migrations)** |
| Externally testable behaviour | Manager or HR opens mobile Approvals tab, reviews pending requests (Leave, WFH, Regularization, Overtime) with employee details, and executes 1-tap Approve or Reject with comments | 1 | **Pass (1 behaviour)** |
| Frontend area | `mobile/approvals` (`src/mobile/approvals/`) | 1 | **Pass (1 area)** |

Within size cap across all four axes. No split required.

---

## 1. Problem

Managers and HR leaders spend significant time away from their desks and need to review time-sensitive employee requests on the go:
1. **Desktop approvals inbox is dense**: `/approvals` (`code/frontend/src/core/approvals/`) is designed with heavy desktop data tables that require navigating to separate sub-routes for Leave, WFH, Overtime, and Regularization requests.
2. **Delayed approvals stall employee requests**: Leave applications and WFH requests remain pending for days because managers wait until they are back at a computer to approve them.
3. **No 1-tap mobile decision flow**: Mobile users need a unified, aggregated inbox with fast action buttons ("Approve" in green, "Reject" in red) and optional comment popovers without multi-page roundtrips.

---

## 2. Scope

### In scope
- **Mobile Approvals Screen (`src/mobile/approvals/MobileApprovalsScreen.jsx`)**:
  - Mounted under the bottom navigation "Approvals" tab (`/approvals`).
  - Conditional visibility: Rendered in `MobileTabBar.jsx` only when the authenticated user holds `core.approval.decide`.
  - Aggregated pending badge: Displays total pending count badge on the bottom tab icon (e.g. `3`).
  - Categorized segment filter: **All**, **Leave**, **WFH**, **Regularization**, **Overtime**.
  - Pull-to-refresh to fetch updated pending instances.
- **Request Card & 1-Tap Action Flow**:
  - Each pending item card displays:
    - Employee Name and Employee Code (e.g. `[EMP-045] Priya Sharma`).
    - Request Type tag (Leave / WFH / Regularization / Overtime) with distinct brand colors.
    - Date range or requested period (e.g. `24 Nov – 26 Nov (3 days)`).
    - Employee submitted reason / comments.
    - Inline action buttons: **Approve** (primary green) and **Reject** (subtle red).
- **Fast Decision Handling**:
  - Tapping **Approve**: Prompts with optional comment bottom sheet, executes `POST /api/v1/approvals/{instanceId}/decide` with `decision: "APPROVE"`, removes card with smooth animation, and increments badge count.
  - Tapping **Reject**: Opens mandatory reason dialog, executes `POST /api/v1/approvals/{instanceId}/decide` with `decision: "REJECT"`, updates status, and refreshes list.
- **Request Details Drawer (`MobileApprovalDetailDrawer.jsx`)**:
  - Tapping anywhere on the card body opens a full details drawer showing multi-tier approval trail (Step 1 Manager, Step 2 HR), employee contact details, and full request history.

### Out of scope
- Multi-instance bulk approval checkboxes (reserved for desktop).
- Approval rule / workflow definition editing (desktop administrative feature).
- Delegation configuration (covered by `W-15.3`).

---

## 3. Flow

```
[Manager Logs In on Mobile App]
                │
                ▼
[MobileTabBar Detects core.approval.decide ──► Approvals Tab Visible with Badge "4"]
                │
                ▼
[User Taps Approvals Tab]
                │
                ▼
[GET /api/v1/approvals/inbox]
  ├── Aggregates Pending Requests across LEAVE, WFH, REGULARIZATION, OVERTIME
  └── Renders Card Feed with Employee Monograms & Date Badges
                │
    ┌───────────┴───────────┐
    ▼                       ▼
[Tap "Approve"]         [Tap "Reject"]
    │                       │
    ▼                       ▼
[Optional Note]         [Mandatory Reason]
    │                       │
    └───────────┬───────────┘
                ▼
[POST /api/v1/approvals/{id}/decide { decision, comments }]
                │
                ▼
[Card Dismissed with Animation, Badge Decremented, Toast Displayed]
```

---

## 4. Architecture & Changes

### 4.1 Backend changes (`code/backend/core`)

> No new endpoints required. Uses existing Core approval workflow engine:
- `GET /api/v1/approvals/inbox` (`ApprovalInstanceController.java`)
- `GET /api/v1/approvals/{instanceId}` (`ApprovalInstanceController.java`)
- `POST /api/v1/approvals/{instanceId}/decide` (`ApprovalInstanceController.java`)
- `GET /api/v1/approvals/{instanceId}/history` (`ApprovalInstanceController.java`)

### 4.2 Frontend changes (`code/frontend`)

| File | Change |
|---|---|
| Service | `code/frontend/src/mobile/approvals/mobileApprovalService.js` | **New**. Mobile wrapper for fetching inbox items and executing fast decisions. |
| Screen | `code/frontend/src/mobile/approvals/MobileApprovalsScreen.jsx` | **New**. Mobile approvals screen with segment filter, card feed, and badge counter. |
| Card | `code/frontend/src/mobile/approvals/ApprovalRequestCard.jsx` | **New**. Touch card with employee avatar, request dates, reason preview, and 1-tap Approve/Reject buttons. |
| Drawer | `code/frontend/src/mobile/approvals/MobileApprovalDetailDrawer.jsx` | **New**. Full detail bottom sheet with multi-tier approval timeline (`<Timeline>`) matching `D-71`. |
| Tab Bar | `code/frontend/src/mobile/MobileTabBar.jsx` | Render 5th "Approvals" tab conditionally when `useCan('core.approval.decide')` is true. |

---

## 5. Database Changes

> **Creates no database tables and executes 0 Flyway migrations.**

- [x] Standing rule: Reuses existing `core.approval_instance`, `core.approval_step`, and `core.approval_history` tables.
- [x] Standing rule: Multi-tenant safety enforced by tenant context filter.
- [x] Standing rule: No cross-module database foreign keys.

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Component | `code/frontend/src/mobile/approvals/MobileApprovalsScreen.test.jsx` | Renders pending approval cards; tests segment category switching; verifies empty state when no items pending. |
| Component | `code/frontend/src/mobile/approvals/ApprovalRequestCard.test.jsx` | Verifies clicking Approve invokes decision API with `APPROVE`; verifies Reject requires mandatory comments. |
| Unit | `code/frontend/src/mobile/approvals/mobileApprovalService.test.js` | Tests inbox aggregation and badge count calculations. |

---

## 7. Verification

```bash
# 1. Run core approvals backend tests
./mvnw -pl core test -Dtest='ApprovalInstanceServiceImplTest,ApprovalEngineTest'

# 2. Run mobile approvals frontend tests
cd code/frontend
npm test -- src/mobile/approvals/

# 3. Frontend lint check
npm run lint
```

| Check | Action | Expected Result |
|---|---|---|
| View Inbox | Manager opens Approvals tab | Pending items displayed with formatted employee names and date ranges. |
| Quick Approve | Tap Approve on pending Leave card | Card animates away; badge drops from 4 to 3; backend moves step to APPROVED. |
| Reject with Reason | Tap Reject, enter "Coverage required", submit | Card removed; backend records REJECTED with comments; employee notified. |
| Non-Manager Hidden | Log in as employee without `core.approval.decide` | Approvals tab is not rendered in bottom navigation. |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Concurrent approval by co-approver | Low | If request was already decided elsewhere, API returns 409 Conflict; mobile UI catches it and displays "Already decided by another approver" while removing the stale card. |
| Accidental tap on Reject | Low | Rejection always requires entering a rejection note, preventing accidental rejection taps. |

---

## 9. Rollback

1. Additive mobile screen; desktop approvals inbox (`/approvals`) continues unaffected.

