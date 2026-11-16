# Feature: Mobile Leave Balances, History & Application

| Field | Value |
|---|---|
| **Feature ID** | `W-98.3` · `CORE-MOBILE-LEAVE` |
| **Promoted to** | `docs/target-state/features/W-98-3-mobile-leave-and-balances.md` — **`W-98-3` with hyphens**, never `W-98.3` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/frontend` (`src/mobile/leave/`, `src/mobile/MobileTabBar.jsx`), `code/backend/core` |
| **Related gaps** | founder 2026-11-13 planned product roadmap (`W-98` Mobile app slice 3: Leave) |
| **Status** | **In Review (pending manager approval)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | `W-98.1` (mobile foundation & auth container) |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `core` (leave endpoints) | 1 | **Pass (1 module)** |
| Flyway migration | None (creates 0 tables; uses existing `core.leave_request` and `core.leave_balance`) | 1 | **Pass (0 migrations)** |
| Externally testable behaviour | Employee inspects leave balances in a mobile card carousel, views past leave requests, and submits an "Apply Leave" bottom sheet with date range and leave type | 1 | **Pass (1 behaviour)** |
| Frontend area | `mobile/leave` (`src/mobile/leave/`) | 1 | **Pass (1 area)** |

Within size cap across all four axes. No split required.

---

## 1. Problem

Employees frequently need to check available leave balances or submit urgent leave requests on their phones:
1. **Desktop portal table does not fit mobile screens**: `MyLeave.jsx` (`code/frontend/src/shell/portal/panels/MyLeave.jsx:1-150`) uses multi-column desktop tables with dense filters, creating horizontal scroll and clipped action buttons on phone screens.
2. **Multi-step desktop modal**: The desktop leave application drawer requires precise mouse clicks for dropdowns, date pickers, and reason inputs; on mobile, native bottom-sheet drawers with touch spinners and clear date pickers provide a much faster, error-free experience.
3. **No offline balance snapshot**: Employees without immediate cellular connectivity cannot see their remaining leave days before making plans.

---

## 2. Scope

### In scope
- **Mobile Leave Screen (`src/mobile/leave/MobileLeaveScreen.jsx`)**:
  - Mounted under the bottom navigation "Leave" tab (`/me/leave`).
  - **Swipeable balance cards carousel**: Clean horizontal card slider showing each active leave type (Casual Leave, Sick Leave, Earned Leave, Comp Off) with Available, Used, and Total Allocated days.
  - Floating action button (FAB): Prominent "+ Apply Leave" button anchored above the bottom tab bar.
  - Recent leave requests list: Chronological list of past applications with status tags (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`), date ranges, and days count.
  - Pull-to-refresh to fetch live balances from server.
- **Apply Leave Mobile Bottom Sheet (`src/mobile/leave/ApplyLeaveDrawer.jsx`)**:
  - Touch-friendly drawer sliding up from the bottom of the screen.
  - Leave type picker with remaining balance indicators.
  - Native touch date picker for Start Date and End Date.
  - Half-day toggle switch (First Half / Second Half).
  - Reason textarea with character counter.
  - Submit action calling existing `POST /api/v1/leave/requests`.
- **Cancel Leave Action**:
  - Allows cancelling future-dated `PENDING` or `APPROVED` requests with 1 tap and confirmation modal calling `POST /api/v1/leave/requests/{id}/cancel`.

### Out of scope
- Manager leave approval actions (handled in `W-98.5` Mobile Approvals Inbox).
- HR leave policy creation or allocation adjustments (desktop administrative features).
- Leave encashment or year-end carry-forward processing.

---

## 3. Flow

```
[Employee Opens Leave Tab on Mobile App]
                    │
                    ▼
[GET /api/v1/leave/balances/my & GET /api/v1/leave/requests/my]
                    │
                    ▼
[Render Horizontal Swipeable Balance Cards & Recent History List]
                    │
                    ▼
[User Taps "+ Apply Leave" Button]
                    │
                    ▼
[Mobile Bottom Sheet Opens with Touch Controls]
  ├── Selects "Casual Leave (Available: 4.5 days)"
  ├── Picks Date: Tomorrow (Full Day)
  └── Types Reason: "Personal appointment"
                    │
                    ▼
[User Taps "Submit Leave Request"]
                    │
                    ▼
[POST /api/v1/leave/requests]
  ├── Balance & Policy Validated by Core Leave Engine
  ├── Approval Instance Created & Routed to Manager
  └── Returns 201 Created
                    │
                    ▼
[Drawer Closes, Success Toast Displays, Recent History Refreshed]
```

---

## 4. Architecture & Changes

### 4.1 Backend changes (`code/backend/core`)

> No new endpoints required. Uses existing validated Core leave APIs:
- `GET /api/v1/leave/balances/my` (`LeaveBalanceController.java`)
- `GET /api/v1/leave/requests/my` (`LeaveRequestController.java`)
- `POST /api/v1/leave/requests` (`LeaveRequestController.java`)
- `POST /api/v1/leave/requests/{id}/cancel` (`LeaveRequestController.java`)

### 4.2 Frontend changes (`code/frontend`)

| File | Change |
|---|---|
| Service | `code/frontend/src/mobile/leave/mobileLeaveService.js` | **New**. Mobile client wrapping balance queries, my-requests list, and leave application dispatch. |
| Screen | `code/frontend/src/mobile/leave/MobileLeaveScreen.jsx` | **New**. Mobile leave dashboard with carousel cards, floating "+ Apply Leave" button, and request history items. |
| Component | `code/frontend/src/mobile/leave/LeaveBalanceCard.jsx` | **New**. Individual card displaying leave code, available days, and donut progress indicator. |
| Drawer | `code/frontend/src/mobile/leave/ApplyLeaveDrawer.jsx` | **New**. Native-feel bottom sheet form for submitting new leave requests. |
| Router | `code/frontend/src/mobile/MobileShell.jsx` | Route `/me/leave` to `MobileLeaveScreen` on mobile native container. |

---

## 5. Database Changes

> **Creates no database tables and executes 0 Flyway migrations.**

- [x] Standing rule: Reuses existing `core.leave_request` and `core.leave_balance` tables.
- [x] Standing rule: RLS enabled and enforced automatically by `TenantContextFilter`.
- [x] Standing rule: No cross-module database foreign keys.

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Component | `code/frontend/src/mobile/leave/MobileLeaveScreen.test.jsx` | Renders balance carousel cards; displays empty state when no leaves applied; renders request history rows with proper status tags. |
| Component | `code/frontend/src/mobile/leave/ApplyLeaveDrawer.test.jsx` | Validates date pickers; verifies submit button calls `applyLeave` with form payload; handles API rejection with inline error. |
| Unit | `code/frontend/src/mobile/leave/mobileLeaveService.test.js` | Tests balance calculation and API error handling. |

---

## 7. Verification

```bash
# 1. Run core leave backend tests
./mvnw -pl core test -Dtest='LeaveRequestServiceImplTest,LeaveBalanceServiceImplTest'

# 2. Run mobile leave frontend tests
cd code/frontend
npm test -- src/mobile/leave/

# 3. Frontend lint check
npm run lint
```

| Check | Action | Expected Result |
|---|---|---|
| View Balances | Open Leave tab on mobile | Balance cards display accurate numbers matching server (e.g. "CL: 5.0 Available"). |
| Apply Leave | Submit 1-day Casual Leave in Apply Leave sheet | `POST /api/v1/leave/requests` returns 201 Created; sheet closes; item appears in history as `PENDING`. |
| Cancel Leave | Tap Cancel on pending request in history | `POST /api/v1/leave/requests/{id}/cancel` called; request status updates to `CANCELLED`. |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Applying for leave exceeding available balance | Low | Pre-flight validation in `ApplyLeaveDrawer` checks requested days against available balance before sending request. |
| Double submission on slow connection | Medium | Submit button enters loading state immediately on tap and disables repeat clicks. |

---

## 9. Rollback

1. Additive mobile screen; web self-service portal (`MyLeave.jsx`) remains unmodified and active.

