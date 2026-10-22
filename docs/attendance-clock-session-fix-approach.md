# Implementation Approach: Attendance Clock-Out Deadlock & Stale Session Remediation (D-104)

> **Feature Area:** HRMS Attendance & Clock Sessions  
> **Ticket Reference:** `D-104` (in `docs/trackers/DEV-TRACKER.md` §15)  
> **Related Specifications:** `W-40.3` (Clock Sessions), `W-40.4` (Attendance Regularization), `W-48.4` (Attendance Screens)  
> **Target Files:**
> - Backend: `code/backend/hrms/src/main/java/com/infinevo/hrms/attendance/ClockServiceImpl.java`
> - Backend Test: `code/backend/hrms/src/test/java/com/infinevo/hrms/attendance/ClockServiceTest.java`
> - Frontend: `code/frontend/src/hrms/attendance/ClockCard.jsx`
> - Frontend Test: `code/frontend/src/hrms/attendance/ClockCard.test.jsx`

---

## 1. Executive Summary & Problem Statement

### 1.1 The Issue
When an employee clocks in and forgets to clock out, and later attempts to clock out after 24 hours (e.g. the next morning after 25 hours), the backend API returns:
```json
{
  "code": "CONFLICT",
  "message": "Session cannot be clocked out more than 24 hours after clock in",
  "status": 409
}
```
While this error response conforms to domain rules (a single shift cannot legally span more than 24 hours), the frontend component (`ClockCard.jsx`) suffered from a complete **deadlock**:
1. `GET /api/v1/hrms/attendance/today` returned the unclosed session as `openSession`.
2. The UI unconditionally rendered a red **"Clock out"** button whenever `today.openSession` was non-null.
3. Clicking "Clock out" triggered `POST /api/v1/hrms/attendance/clock-out`, which threw the 409 Conflict error.
4. There was **no way** for the employee to clock in or start a new day's session from the UI, permanently stranding the user.

---

## 2. Domain & Specification Background

In accordance with specification `W-40-3-clock-sessions.md` (§4) and statutory labour guidelines:
- **No shift > 24 hours:** An employee who works from 09:00 to 18:00 and forgets to clock out cannot clock out at 10:00 the next day to claim 25 continuous hours of work.
- **Clock-out rejection:** Attempting to clock out a session older than 24 hours must return `409 CONFLICT` and leave the session open to prevent corrupting attendance calculations and payroll work hours.
- **Auto-void on next clock-in:** The spec explicitly defines:
  > *"Clock-out more than 24 hours after the clock-in: 409 CONFLICT, session left open — it is voided at the next clock-in."*
  > *"Clock-in while a session from an earlier date is open: That session is voided (`void_reason = NOT_CLOCKED_OUT`) and the new one opens. It counted zero anyway; the employee regularizes that day."*
- **Attendance Regularization:** The employee recovers the legitimate hours worked on that past day by submitting an Attendance Regularization request (`W-40.4`) for manager approval.

---

## 3. Root Cause Analysis (RCA)

| Layer | Component | Root Cause |
|---|---|---|
| **Frontend UI** | `ClockCard.jsx` | The button state was strictly binary based on existence: `open ? 'Clock out' : 'Clock in'`. It had no check to evaluate if `open` was expired (`attendanceDate < today.date` or `elapsed > 24h`). When `open` was present, `act()` exclusively invoked `attendanceService.clockOut()`. |
| **Backend Service** | `ClockServiceImpl.java` | In `clockIn()`, the conflict guard checked `openSession.getAttendanceDate().equals(today)` before throwing `ClockConflictException("Active session already open for today")`. In edge cases where a session exceeded 24 hours while matching the calendar date (e.g. late night shifts or clock zone adjustments), `clockIn()` would fail instead of auto-voiding. |

---

## 4. Remediation Architecture & Implementation Approach

```
                  ┌────────────────────────────────────────┐
                  │ Employee opens Attendance Card (/me)  │
                  └───────────────────┬────────────────────┘
                                      │
                         GET /hrms/attendance/today
                                      │
                                      ▼
                        ┌───────────────────────────┐
                        │ Is openSession present?   │
                        └───────┬───────────┬───────┘
                                │ No        │ Yes
                                │           ▼
                                │    ┌───────────────────────────────┐
                                │    │ Is session stale?             │
                                │    │ (>24h or attendanceDate < date│
                                │    └───────┬───────────────┬───────┘
                                │            │ Yes           │ No
                                ▼            ▼               ▼
                       ┌─────────────────────────┐   ┌───────────────────────────┐
                       │ Primary Action:         │   │ Primary Action:           │
                       │ "Clock in"              │   │ "Clock out"               │
                       │ (Warning banner if stale│   │ (Normal running timer)    │
                       └────────────┬────────────┘   └─────────────┬─────────────┘
                                    │                              │
                                    ▼                              ▼
                       POST /attendance/clock-in      POST /attendance/clock-out
                                    │                              │
                                    ▼                              ▼
                       Backend auto-voids stale       Closes active session,
                       session (NOT_CLOCKED_OUT)      rederives day, and
                       and opens new session          writes to core.attendance
```

### 4.1 Frontend Implementation (`ClockCard.jsx`)

1. **Stale Session Detection:**
   Evaluates whether the open session belongs to a prior calendar date or has exceeded 24 elapsed hours:
   ```javascript
   const isStale = Boolean(
     open &&
       ((open.attendanceDate && today?.date && open.attendanceDate < today.date) ||
         (open.clockInAt && now - new Date(open.clockInAt).getTime() > 24 * 60 * 60 * 1000))
   );
   ```

2. **Contextual Warning Alert:**
   Renders an Ant Design `<Alert>` with clear user guidance:
   ```jsx
   {isStale && (
     <Alert
       type="warning"
       showIcon
       message="Previous clock-in session expired (>24 hours)"
       description={`Your previous session from ${formatDate(open.attendanceDate || open.clockInAt)} exceeded 24 hours and cannot be clocked out. Clicking "Clock in" will automatically close it (marked as Not Clocked Out) and start your new session for today. You can regularize that day's hours via Attendance Regularization.`}
     />
   )}
   ```

3. **Dynamic Action Routing:**
   - When `open && !isStale`: Shows danger **"Clock out"** button; clicking calls `attendanceService.clockOut()`.
   - When `!open || isStale`: Shows primary **"Clock in"** button; clicking calls `attendanceService.clockIn()`.
   ```javascript
   const act = async () => {
     setBusy(true);
     setError(null);
     try {
       if (open && !isStale) {
         const out = await attendanceService.clockOut();
         setDayStatus(out?.status || null);
       } else {
         await attendanceService.clockIn();
         setDayStatus(null);
       }
       await load();
     } catch (err) {
       setError(err?.message || 'Something went wrong');
     } finally {
       setBusy(false);
     }
   };
   ```

4. **Running Timer Formatting:**
   - Active session: Displays live running duration (`Clocked in at 09:00 · running 2:15`).
   - Expired session: Displays secondary audit notice (`Previous session clocked in at 09:14 (21 Oct 2026) · exceeded 24h`) without misleading running counters.

---

### 4.2 Backend Implementation (`ClockServiceImpl.java`)

1. **Airtight Auto-Voiding on `clockIn()`:**
   Updated `clockIn()` in `ClockServiceImpl.java` to check both the calendar date and the 24-hour elapsed duration:
   ```java
   Optional<ClockSession> openOpt =
           clockSessions.findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(tenantId, employee.id());
   if (openOpt.isPresent()) {
       ClockSession openSession = openOpt.get();
       boolean isOver24Hours =
               Duration.between(openSession.getClockInAt(), now).compareTo(Duration.ofHours(24)) > 0;
       if (openSession.getAttendanceDate().equals(today) && !isOver24Hours) {
           throw new ClockConflictException("Active session already open for today");
       }
       openSession.voidSession(VoidReason.NOT_CLOCKED_OUT, now, currentActor());
       clockSessions.saveAndFlush(openSession);
   }

   ClockSession newSession =
           new ClockSession(tenantId, employee.id(), today, now, SessionOrigin.CLOCK, currentActor());
   newSession = clockSessions.save(newSession);
   return ClockSessionResponse.from(newSession);
   ```

2. **Preserved Invariant on `clockOut()`:**
   - Retained the strict 24-hour check in `clockOut()`:
     ```java
     Duration duration = Duration.between(openSession.getClockInAt(), now);
     if (duration.compareTo(Duration.ofHours(24)) > 0) {
         throw new ClockConflictException("Session cannot be clocked out more than 24 hours after clock in");
     }
     ```
   - This ensures statutory compliance: shifts > 24 hours can never be confirmed via clock-out; they must pass through manager-reviewed regularization.

---

## 5. Verification & Test Coverage

### 5.1 Backend Automated Tests (`ClockServiceTest.java`)
Added unit test verifying that calling `clockIn()` when an open session is older than 24 hours auto-voids it with `VoidReason.NOT_CLOCKED_OUT` and opens the new session:
- `clockIn_over24Hours_voidsStaleSession_andOpensNew()`: **PASSED**
- All 10 unit tests in `ClockServiceTest`: **PASSED**
- Spotless code formatting check: **PASSED**

### 5.2 Frontend Automated Tests (`ClockCard.test.jsx`)
Added Vitest test cases in `ClockCard.test.jsx`:
1. `shows Clock in and warning alert when open session is > 24 hours old`:
   - Asserts button is "Clock in" (not "Clock out").
   - Asserts warning banner is rendered.
   - Verifies click triggers `attendanceService.clockIn()`, not `clockOut()`.
2. `shows Clock in when open session is from an earlier date`:
   - Asserts button is "Clock in".
- All 5 tests in `ClockCard.test.jsx`: **PASSED**
- All 18 tests across `src/hrms/attendance/`: **PASSED**
- `npm run lint`: **0 warnings, 0 errors**

---

## 6. End-to-End User Experience Lifecycle

| Step | User Action | System Response |
|---|---|---|
| **1. Expired Session State** | Employee opens application after forgetting to clock out yesterday (>24h ago). | Card displays warning banner: *"Previous clock-in session expired (>24 hours)"*. Button displays **"Clock in"** (primary blue). |
| **2. Clock In Click** | Employee clicks **"Clock in"**. | Frontend sends `POST /api/v1/hrms/attendance/clock-in`. Backend sets `void_reason = NOT_CLOCKED_OUT` on the old session and creates a new session for today. |
| **3. New Shift Active** | Card reloads automatically. | Warning banner disappears. Worked minutes today shows `0h 0m`. Running timer starts from `0m`. Button becomes red **"Clock out"**. |
| **4. Prior Day Hours** | Employee navigates to `/hrms/regularizations`. | Employee submits an Attendance Regularization request specifying their actual start and end times for the previous day. Upon manager approval, the day's attendance status is derived. |
