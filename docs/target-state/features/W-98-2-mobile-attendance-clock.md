# Feature: Mobile Attendance Clock-In/Out & Location

| Field | Value |
|---|---|
| **Feature ID** | `W-98.2` · `HRMS-MOBILE-ATTENDANCE` |
| **Promoted to** | `docs/target-state/features/W-98-2-mobile-attendance-clock.md` — **`W-98-2` with hyphens**, never `W-98.2` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/frontend` (`src/mobile/attendance/`), `code/backend/hrms`, `code/backend/migration` |
| **Related gaps** | founder 2026-11-13 planned product roadmap (`W-98` Mobile app slice 2: Attendance) |
| **Status** | **Spec corrected 2026-11-19 after review. Code on `feature/krushna-specs` does not yet meet it.** |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | `W-98.1` |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `hrms` | 1 | Pass |
| Flyway migration | 1 (three nullable columns on `hrms.clock_session`) | 1 | Pass |
| Externally testable behaviour | An employee clocks in and out on the phone and the place of each punch is stored | 1 | Pass |
| Frontend area | `src/mobile/attendance/` | 1 | Pass |

---

## 1. Problem

| # | What is missing today | Evidence |
|---|---|---|
| 1 | The clock card is built for the desktop sidebar: small targets, no touch feedback. | `code/frontend/src/hrms/attendance/ClockCard.jsx` |
| 2 | A punch carries no location, and `hrms.clock_session` has no column to hold one. | `code/backend/migration/src/main/resources/db/migration/hrms/V123__clock_session.sql:4-22` |
| 3 | On a weak connection the user gets no clear sign that a punch failed. | — |

---

## 2. Scope

**In scope**

| Area | Rule |
|---|---|
| Screen | `MobileAttendanceScreen.jsx` on the Attendance tab (`/me/attendance`): one large Clock In / Clock Out button, a live timer counted from the server's `clockInAt`, today's first in / last out / worked time. |
| Recent days | The last 7 calendar days, **one row per day** with that day's status (Present, Half day, Absent, Leave, Holiday, Weekend), built from `GET /api/v1/hrms/attendance/sessions/mine` (`dayStatus` on `ClockSessionResponse`). A day with no session follows the same rule as the web `code/frontend/src/hrms/attendance/MyAttendance.jsx`. |
| Location capture | On each punch the app reads latitude, longitude and accuracy with `@capacitor/geolocation` (5-second timeout). |
| Location stored | The server stores the three values on the session: clock-in values on clock-in, clock-out values on clock-out. |
| Range check | Latitude −90..90, longitude −180..180, accuracy ≥ 0. Out of range returns 400 `VALIDATION_FAILED`; nothing is stored. |
| No location | Permission denied, timeout or no signal: the punch still succeeds with null coordinates, and the screen says "Location not recorded". |
| Consent | Before the first location request the app shows: "Infinevo records your location only at the moment you clock in or out, so your employer can see where the punch was made. It is not tracked at any other time." The OS permission prompt follows. |
| Who sees it | The employee sees their own (`/sessions/mine`). HR sees it on `GET /api/v1/hrms/attendance/sessions`, which already needs `core.attendance.read`. Nobody else. |
| Retention | Coordinates live and die with the session row; no separate copy, no log line carries them. |
| Failure | A failed punch shows an error and leaves the button in its previous state; nothing is shown as done until the server answers. |

**Out of scope**

- Geofence rules, selfie check (`W-100`); biometric devices (`W-99`); shift rosters (`W-101`).
- Offline queuing of punches.
- Showing coordinates on a map.

---

## 3. Flow

```
Attendance tab --> GET /api/v1/hrms/attendance/today --> button state + timer
Tap Clock In   --> consent (first time) --> read GPS (5 s)
               --> POST /api/v1/hrms/attendance/clock-in {latitude, longitude, accuracyMeters}
               --> ClockServiceImpl --> hrms.clock_session (in_* columns) --> 201
Tap Clock Out  --> read GPS --> POST .../clock-out {...} --> out_* columns --> 200
```

---

## 4. Backend changes

| Layer | File (`code/backend/hrms/src/main/java/com/infinevo/hrms/attendance/`) | Change |
|---|---|---|
| Controller | `ClockController.java` | Unchanged paths; already passes `ClockRequest`. Maps a range error to 400. |
| ServiceImpl | `ClockServiceImpl.java` | Validates and stores the coordinates on clock-in and clock-out. |
| Entity | `ClockSession.java` | Six nullable fields: `inLatitude`, `inLongitude`, `inAccuracyM`, `outLatitude`, `outLongitude`, `outAccuracyM`. |
| DTO | `ClockRequest.java` | Already has `latitude`, `longitude`, `accuracyMeters`; all optional. |
| DTO | `ClockSessionResponse.java` | Adds the six fields. |

**API contract** (base `/api/v1/hrms/attendance`, all existing)

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/clock-in` | `{latitude?, longitude?, accuracyMeters?}` or empty | **201** `ClockSessionResponse` · 409 open session exists · 400 out of range | `hrms.attendance.mark` |
| POST | `/clock-out` | same | 200 `ClockOutResponse` · error as today when no session is open · 400 out of range | `hrms.attendance.mark` |
| GET | `/today` | — | 200 `TodayResponse` | `hrms.attendance.mark` |
| GET | `/sessions/mine?from&to` | — | 200 list of `ClockSessionResponse` | `core.attendance.read_own` |
| GET | `/sessions?from&to&employeeId` | — | 200 list of `ClockSessionResponse` | `core.attendance.read` |

## 5. Frontend changes

| File | Change |
|---|---|
| `code/frontend/package.json` | `@capacitor/geolocation`, `@capacitor/haptics`, both synced into `android/` and `ios/`. |
| `code/frontend/android/app/src/main/AndroidManifest.xml`, `code/frontend/ios/App/App/Info.plist` | Location permission and its purpose text. |
| `code/frontend/src/mobile/attendance/mobileAttendanceService.js` | Location read, punch calls, today and last-7-days reads. |
| `code/frontend/src/mobile/attendance/MobileAttendanceScreen.jsx` | Screen as in Scope, including the consent notice. |
| `code/frontend/src/mobile/attendance/ShiftTimer.jsx` | Timer from server time. |
| `code/frontend/src/mobile/MobileShell.jsx` | Route. |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/me/attendance` | `MobileAttendanceScreen` | `MobileShell` (native only) |

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| next free Flyway version at build time, under `db/migration/hrms/` | `hrms.clock_session`: adds `in_latitude`, `in_longitude`, `out_latitude`, `out_longitude` `NUMERIC(9,6)` and `in_accuracy_m`, `out_accuracy_m` `NUMERIC(8,2)`, all nullable, with CHECK constraints for the ranges | yes — existing table, existing RLS policy | yes — columns are additive and can be dropped |

- [x] No new table; `tenant_id` and RLS already on `hrms.clock_session`
- [x] No new index needed
- [x] No money columns
- [x] Expand only, no destructive step

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `code/backend/hrms/src/test/java/com/infinevo/hrms/attendance/ClockServiceTest.java` | The saved session carries the exact coordinates sent, for in and for out; null coordinates accepted; out-of-range rejected. Asserts on stored values, not on "response is not null". |
| Integration | `code/backend/hrms/src/test/java/com/infinevo/hrms/attendance/ClockFlowIT.java` | Clock-in with coordinates returns 201 and `/sessions/mine` returns them; 400 on latitude 91. |
| Integration | `code/backend/hrms/src/test/java/com/infinevo/hrms/attendance/ClockSessionRlsIT.java` | Another tenant cannot read the coordinates. |
| Component | `code/frontend/src/mobile/attendance/MobileAttendanceScreen.test.jsx` | Both button states; consent shown once; failed punch keeps the old state; seven day rows. |
| Unit | `code/frontend/src/mobile/attendance/mobileAttendanceService.test.js` | Denied, timeout and unavailable all send null coordinates. |

## 8. Verification

```bash
cd code/backend && ./mvnw -pl hrms -am verify -Dtest='ClockServiceTest' -Dit.test='ClockFlowIT,ClockSessionRlsIT'
cd ../frontend && npm test -- src/mobile/attendance/ && npm run lint
```

| Check | Expected | Result |
|---|---|---|
| Backend tests | Pass | not run |
| Frontend tests and lint | Pass, 0 warnings | not run |
| Clock in on a device with location on | 201; the row in `hrms.clock_session` has `in_latitude`, `in_longitude`, `in_accuracy_m` | not run |
| Clock out | 200; `out_*` columns filled | not run |
| Location permission denied | Punch succeeds, columns null, "Location not recorded" shown | not run |
| HR reads `/sessions` for that employee | Coordinates present | not run |
| A manager without `core.attendance.read` | Cannot read them | not run |
| Recent days card | Seven rows, one per day | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Slow GPS blocks the punch | Medium | 5-second timeout, then punch without location. |
| Phone clock is wrong | Low | Timer and times come from the server. |
| Employees object to location capture | Medium | Consent text, capture only at the punch, HR-only visibility. |

## 10. Rollback

Hide the Attendance tab route; the web clock card is untouched. The six columns can stay empty or be dropped.

---

## Code gaps against this spec (2026-11-19)

| Gap | Where |
|---|---|
| Coordinates are accepted and thrown away; no columns exist | `code/backend/hrms/src/main/java/com/infinevo/hrms/attendance/ClockServiceImpl.java:103-126,136-155` |
| The test only checks the response is not null | `code/backend/hrms/src/test/java/com/infinevo/hrms/attendance/ClockServiceTest.java:305-328` |
| No range check, no consent notice | `ClockServiceImpl.java` · `code/frontend/src/mobile/attendance/MobileAttendanceScreen.jsx` |
| "Recent 7 days" lists up to 7 sessions, not one row per day | `code/frontend/src/mobile/attendance/MobileAttendanceScreen.jsx:220` |
