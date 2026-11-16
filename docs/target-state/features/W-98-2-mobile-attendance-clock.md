# Feature: Mobile Attendance Clock-In/Out & Geolocation

| Field | Value |
|---|---|
| **Feature ID** | `W-98.2` · `HRMS-MOBILE-ATTENDANCE` |
| **Promoted to** | `docs/target-state/features/W-98-2-mobile-attendance-clock.md` — **`W-98-2` with hyphens**, never `W-98.2` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/frontend` (`src/mobile/attendance/`, `src/mobile/MobileTabBar.jsx`), `code/backend/hrms` |
| **Related gaps** | founder 2026-11-13 planned product roadmap (`W-98` Mobile app slice 2: Attendance) |
| **Status** | **In Review (pending manager approval)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | `W-98.1` (mobile foundation & auth container) |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `hrms` (clock endpoints) | 1 | **Pass (1 module)** |
| Flyway migration | None (creates 0 tables; uses existing `hrms.clock_session` from `V123`) | 1 | **Pass (0 migrations)** |
| Externally testable behaviour | Employee opens mobile Attendance tab, punches 1-tap Clock-In with captured GPS coordinates, sees live shift timer, and punches Clock-Out | 1 | **Pass (1 behaviour)** |
| Frontend area | `mobile/attendance` (`src/mobile/attendance/`) | 1 | **Pass (1 area)** |

Within size cap across all four axes. No split required.

---

## 1. Problem

While `W-98.1` provides the mobile container and shell, employees need an intuitive, thumb-accessible attendance punch experience:
1. **Desktop clock card is mouse-oriented**: The existing `ClockCard.jsx` (`code/frontend/src/hrms/attendance/ClockCard.jsx:1-120`) is designed for desktop browser sidebars and lacks native touch states, large tap targets, and haptic feedback.
2. **Missing mobile GPS capture**: Mobile punches require capturing geographic coordinates (latitude, longitude, accuracy) via native device APIs to verify whether the punch occurred at an approved work location.
3. **No offline / poor connectivity feedback**: Mobile workers frequently clock in with spotty cellular coverage; the mobile interface must provide clear optimistic feedback and handle connectivity drops gracefully.

---

## 2. Scope

### In scope
- **Mobile Clock Screen (`src/mobile/attendance/MobileAttendanceScreen.jsx`)**:
  - Mounted under the bottom navigation "Attendance" tab (`/me/attendance`).
  - Prominent 1-tap circular action button: "Clock In" (Green) when clocked out; "Clock Out" (Red/Orange) when clocked in.
  - Live active shift timer displaying elapsed time (`HH:mm:ss`) since `clock_in_at`.
  - Shift summary card: First In, Last Out, total hours worked today.
  - Recent attendance card: Mini list of the past 7 days' attendance status (Present, Half Day, Holiday, Weekend).
- **Native Geolocation Integration**:
  - `@capacitor/geolocation` plugin integration to capture `latitude`, `longitude`, and `accuracy` on punch.
  - Request payload hands coordinates to `POST /api/v1/attendance/clock-in` and `clock-out`.
  - Permission handling: Prompts user for native OS location permission; gracefully records punch with null coordinates if permission is denied, tagging location status.
- **Backend API Handshake (`code/backend/hrms`)**:
  - Uses existing `ClockSessionController.java` endpoints (`/api/v1/attendance/clock-in`, `/api/v1/attendance/clock-out`, `/api/v1/attendance/sessions/today`).
  - Validates session state transitions: cannot clock in while an open session exists; cannot clock out without an active session (`V123__clock_session.sql:18-22`).

### Out of scope
- Office geofence radius policy enforcement and selfie facial verification (owned by upcoming ticket `W-100`).
- Biometric physical hardware punch sync (owned by upcoming ticket `W-99`).
- Shift roster scheduling (owned by upcoming ticket `W-101`).

---

## 3. Flow

```
[Employee Taps Attendance Tab on Mobile App]
                    │
                    ▼
[GET /api/v1/attendance/sessions/today]
  ├── Active Open Session Found ──► Displays "Clock Out" Button & Live Elapsed Timer
  └── No Open Session ──► Displays "Clock In" Button & Previous Punch Timestamps
                    │
                    ▼
[User Taps "Clock In" / "Clock Out"]
                    │
                    ▼
[Capacitor Geolocation Retrieves Device GPS (lat, lng, accuracy)]
                    │
                    ▼
[POST /api/v1/attendance/clock-in { latitude, longitude, accuracy }]
                    │
                    ▼
[Server Records Clock Session in hrms.clock_session]
                    │
                    ▼
[Mobile UI Plays Haptic Feedback & Transitions to Active Clocked-In State]
```

---

## 4. Architecture & Changes

### 4.1 Backend changes (`code/backend/hrms`)

| Layer | File | Change |
|---|---|---|
| DTO | `com.infinevo.hrms.attendance.ClockRequest.java` | Add optional `Double latitude`, `Double longitude`, `Double accuracyMeters` to request payload. |
| Service | `com.infinevo.hrms.attendance.ClockSessionServiceImpl.java` | Persist coordinates into session metadata / audit context (`file:code/backend/hrms/src/main/java/com/infinevo/hrms/attendance/ClockSessionServiceImpl.java:45-90`). |
| Controller | `com.infinevo.hrms.attendance.ClockSessionController.java` | Pass `ClockRequest` with coordinates to clock-in and clock-out endpoints. |

### 4.2 Frontend changes (`code/frontend`)

| File | Change |
|---|---|
| Package | `code/frontend/package.json` | Add `@capacitor/geolocation: ^6.0.0` and `@capacitor/haptics: ^6.0.0`. |
| Service | `code/frontend/src/mobile/attendance/mobileAttendanceService.js` | **New**. Mobile attendance client wrapping geolocation capture, punch execution, and today's session polling. |
| Screen | `code/frontend/src/mobile/attendance/MobileAttendanceScreen.jsx` | **New**. Thumb-friendly mobile attendance view with circular punch button, live elapsed timer, and weekly attendance history cards. |
| Component | `code/frontend/src/mobile/attendance/ShiftTimer.jsx` | **New**. Real-time counter calculating hours and minutes worked today. |
| Router | `code/frontend/src/mobile/MobileShell.jsx` | Route `/me/attendance` to `MobileAttendanceScreen` on mobile native container. |

---

## 5. Database Changes

> **Creates no database tables and executes 0 Flyway migrations.**

- [x] Standing rule: Reuses existing `hrms.clock_session` table created in `V123__clock_session.sql`.
- [x] Standing rule: RLS enabled on `hrms.clock_session` using fail-safe `CASE` policy.
- [x] Standing rule: No cross-module database foreign keys.

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `com.infinevo.hrms.attendance.ClockSessionServiceImplTest.java` | Verifies clock-in and clock-out with geolocation parameters; ensures cannot clock-in twice; validates timer bounds. |
| Component | `code/frontend/src/mobile/attendance/MobileAttendanceScreen.test.jsx` | Renders punch button in clocked-out state; clicks punch button and verifies API call; renders active timer in clocked-in state. |
| Unit | `code/frontend/src/mobile/attendance/mobileAttendanceService.test.js` | Tests fallback when geolocation is denied or unavailable. |

---

## 7. Verification

```bash
# 1. Run HRMS attendance tests
./mvnw -pl hrms test -Dtest='ClockSessionServiceImplTest,ClockSessionControllerTest'

# 2. Run mobile attendance frontend tests
cd code/frontend
npm test -- src/mobile/attendance/

# 3. Frontend lint check
npm run lint
```

| Check | Action | Expected Result |
|---|---|---|
| Clock In | Tap Clock In on mobile | `POST /api/v1/attendance/clock-in` returns 200 OK; UI transitions to Clocked In; timer starts. |
| Clock Out | Tap Clock Out on mobile | `POST /api/v1/attendance/clock-out` returns 200 OK; session closed; total hours calculated. |
| Geolocation Permission Denied | Disable location permission in device settings and punch | Punch succeeds with warning tag; coordinates recorded as null. |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| GPS timeout or poor signal delay | Medium | Set Capacitor geolocation timeout to 5 seconds; proceed with punch if GPS times out so employee is not blocked. |
| Clock drift on mobile device | Low | Timer displays elapsed time calculated from server `clock_in_at` timestamp, not client device clock. |

---

## 9. Rollback

1. Additive mobile screen; desktop attendance logging continues to operate independently.
2. Web attendance screens (`ClockCard.jsx`, `AttendanceLog.jsx`) remain untouched.

