# Feature: Mobile Leave Balances, History & Application

| Field | Value |
|---|---|
| **Feature ID** | `W-98.3` · `CORE-MOBILE-LEAVE` |
| **Promoted to** | `docs/target-state/features/W-98-3-mobile-leave-and-balances.md` — **`W-98-3` with hyphens**, never `W-98.3` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/frontend` (`src/mobile/leave/`). No backend change. |
| **Related gaps** | founder 2026-11-13 planned product roadmap (`W-98` Mobile app slice 3: Leave) |
| **Status** | **Spec corrected 2026-11-19 after review. Code on `feature/krushna-specs` does not yet meet it.** |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | `W-98.1` |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | none (uses existing `core` leave endpoints) | 1 | Pass |
| Flyway migration | none | 1 | Pass |
| Externally testable behaviour | An employee sees leave balances, applies for leave and takes back a request on the phone | 1 | Pass |
| Frontend area | `src/mobile/leave/` | 1 | Pass |

---

## 1. Problem

| # | What is missing today | Evidence |
|---|---|---|
| 1 | The portal leave panel is a desktop table; on a phone it scrolls sideways and clips buttons. | `code/frontend/src/shell/portal/panels/MyLeave.jsx` |
| 2 | Applying for leave uses desktop pickers that are slow to use by touch. | same |

---

## 2. Scope

**In scope**

| Area | Rule |
|---|---|
| Screen | `MobileLeaveScreen.jsx` on the Leave tab (`/me/leave`): balance cards (one per leave type: available, used, allocated), an "Apply leave" button, and the list of the employee's requests with status, dates and days. |
| Refresh | A refresh control reloads balances and requests. A button or a pull gesture are both acceptable. |
| Apply | `ApplyLeaveDrawer.jsx`: leave type, from date, to date, half-day switch with First half / Second half, reason. Submits to `POST /api/v1/leave-requests`. |
| Half-day values | `halfDayPeriod` is sent as **`first`** or **`second`** — the values `HalfDayPeriod` accepts. Never `FIRST_HALF` / `SECOND_HALF`. |
| Server is the judge | The drawer may warn when days exceed the balance, but the server's answer decides; a 4xx message from the server is shown as it is. |
| Take back a request | A **PENDING** request shows "Withdraw" → `POST /api/v1/leave-requests/{id}/withdraw` → status `WITHDRAWN`. A future-dated **APPROVED** request shows "Cancel" → `POST /api/v1/leave-requests/{id}/cancel` → status `CANCELLED`. No other status shows either button. Both ask for confirmation. |
| Statuses shown | `DRAFT`, `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `WITHDRAWN`. |
| States | Loading, empty ("No leave requests yet") and error (message with retry) are three different views; a failed load never looks like an empty list. |
| Double tap | Submit, Withdraw and Cancel disable themselves while the call is running. |

**Out of scope**

- Manager approval (`W-98.5`); leave policy and allocation (desktop).
- Attaching documents to a request from the phone.
- Any offline view of balances.

---

## 3. Flow

```
Leave tab --> GET /api/v1/me/leave-balances + GET /api/v1/me/leave-requests --> cards + list
Apply     --> drawer --> POST /api/v1/leave-requests --> LeaveRequestServiceImpl --> core.leave_request --> 201
Withdraw (PENDING)  --> POST /api/v1/leave-requests/{id}/withdraw --> WITHDRAWN
Cancel   (APPROVED) --> POST /api/v1/leave-requests/{id}/cancel   --> CANCELLED
```

---

## 4. Backend changes

None. The endpoints below exist in `code/backend/core/src/main/java/com/infinevo/core/leave/`.

**API contract** (all existing)

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me/leave-balances` | — | 200 balances (`LeaveAllocationController`) | `core.leave.read_own` |
| GET | `/api/v1/me/leave-requests` | paging | 200 page of requests (`LeaveRequestController`) | `core.leave.read_own` |
| POST | `/api/v1/leave-requests` | `LeaveApplyRequest`: `{leaveTypeId, fromDate, toDate, isHalfDay, halfDayPeriod: "first"\|"second"\|null, reason, submit}` | 201 `LeaveRequestResponse` | `core.leave.apply` |
| POST | `/api/v1/leave-requests/{id}/withdraw` | `{reason?}` | 200 `LeaveRequestResponse` | `core.leave.apply` (own request) |
| POST | `/api/v1/leave-requests/{id}/cancel` | `{reason?}` | 200 `LeaveRequestResponse`; refused unless APPROVED and before the start date | `core.leave.apply` (own request) |

## 5. Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/mobile/leave/mobileLeaveService.js` | Calls above; `withdrawLeave` and `cancelLeave` are two functions; half-day values as in Scope. |
| `code/frontend/src/mobile/leave/MobileLeaveScreen.jsx` | Screen, three states, Withdraw / Cancel by status. |
| `code/frontend/src/mobile/leave/LeaveBalanceCard.jsx` | One balance card. |
| `code/frontend/src/mobile/leave/ApplyLeaveDrawer.jsx` | Apply form. |
| `code/frontend/src/mobile/MobileShell.jsx` | Route. |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/me/leave` | `MobileLeaveScreen` | `MobileShell` (native only) |

## 6. Database changes

None. Reads and writes go through existing endpoints over `core.leave_request` and `core.leave_allocation`.

## 7. Tests

Tests must send and expect the **real contract values**. A test that mocks a success for a call the backend refuses does not count.

| Type | File | Covers |
|---|---|---|
| Unit | `code/frontend/src/mobile/leave/mobileLeaveService.test.js` | Request body for a half day carries `first` / `second`; withdraw calls `/withdraw`; cancel calls `/cancel`; paths match the table in §4. |
| Component | `code/frontend/src/mobile/leave/MobileLeaveScreen.test.jsx` | PENDING row shows Withdraw only; APPROVED future row shows Cancel only; loading, empty and error views differ. |
| Component | `code/frontend/src/mobile/leave/ApplyLeaveDrawer.test.jsx` | Required fields; submit payload; server error shown inline; double tap sends one call. |
| Component | `code/frontend/src/mobile/leave/LeaveBalanceCard.test.jsx` | Numbers rendered as given. |
| Existing backend | `code/backend/core/src/test/java/com/infinevo/core/leave/LeaveRequestStatusTest.java` | Unchanged; proves which status allows which action. |

## 8. Verification

```bash
cd code/frontend && npm test -- src/mobile/leave/ && npm run lint
```

| Check | Expected | Result |
|---|---|---|
| Frontend tests and lint | Pass, 0 warnings | not run |
| Open Leave tab | Balances equal the web portal's for the same employee | not run |
| Apply one full day | 201; row appears as `PENDING` | not run |
| Apply a half day, second half | 201; request stored with `second` | not run |
| Withdraw a PENDING request | 200; status `WITHDRAWN` | not run |
| Cancel a future APPROVED request | 200; status `CANCELLED` | not run |
| Load with the network off | Error view with retry, not an empty list | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Mobile and backend contract drift again | Medium | Tests assert real values; device checks above hit the real server. |
| Double submission on a slow line | Medium | Buttons disable while a call runs. |

## 10. Rollback

Remove the `/me/leave` mobile route; the web panel `MyLeave.jsx` is untouched.

---

## Code gaps against this spec (2026-11-19)

| Gap | Where |
|---|---|
| Half day sends `FIRST_HALF` / `SECOND_HALF`; the backend takes `first` / `second` | `code/frontend/src/mobile/leave/ApplyLeaveDrawer.jsx:201-204` · `code/frontend/src/mobile/leave/mobileLeaveService.js:73` · `code/backend/core/src/main/java/com/infinevo/core/leave/HalfDayPeriod.java:13-14` |
| "Cancel" on a PENDING request calls `/cancel`, which the backend refuses; there is no withdraw call | `code/frontend/src/mobile/leave/MobileLeaveScreen.jsx:117-118` · `mobileLeaveService.js:87` · `code/backend/core/src/main/java/com/infinevo/core/leave/LeaveRequestServiceImpl.java:379-381` |
| The test mocks a success for that refused call | `code/frontend/src/mobile/leave/MobileLeaveScreen.test.jsx:120-121` |
