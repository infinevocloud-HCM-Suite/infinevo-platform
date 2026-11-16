# Feature: Mobile App Foundation, Native Shell & Secure Auth

| Field | Value |
|---|---|
| **Feature ID** | `W-98.1` · `PLAT-MOBILE` (mobile app foundation & auth) |
| **Promoted to** | `docs/target-state/features/W-98-1-mobile-shell-and-auth.md` — **`W-98-1` with hyphens**, never `W-98.1` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/frontend` (`package.json`, `capacitor.config.ts`, `android/`, `ios/`, `src/mobile/`, `src/shell/auth/keycloak.js`) |
| **Related gaps** | founder 2026-11-13 planned product roadmap ("Priority 2 — competing with Keka: `W-98` Mobile app") |
| **Status** | **In Review (pending manager approval)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | nothing |

---

## Size cap — this ticket splits

`W-98` as ticketed ("Android and iOS app for attendance, leave, payslips, approvals. Size L") breaks every axis of the size cap:

| Axis | `W-98` as ticketed | Limit | Status |
|---|---|---|---|
| Backend module | `core`, `hrms`, `payroll` | 1 | **Breaks (3 modules)** |
| Flyway migration | None initially, but device tokens cross boundaries | 1 | Within cap |
| Externally testable behaviour | Clock-in, leave apply/history, payslip view/download, approval decisions | 1 | **Breaks (4 behaviours)** |
| Frontend area | Mobile shell, attendance, leave, payroll payslips, approvals | 1 | **Breaks (5 areas)** |

To protect team velocity, code quality, and maintain strict independent verification, `W-98` splits into five focused tickets.

### Proposed split

| Ticket | Scope | Size | Status |
|---|---|:---:|---|
| **`W-98.1`** (this spec) | **Mobile App Foundation, Native Shell & Secure Auth** — Capacitor 6 setup, Android/iOS native containers, Keycloak PKCE mobile auth with deep-link redirection (`infinevo://auth-callback`), secure token persistence, and bottom navigation shell for self-service (`/me`) | **M** | **Ready** (`dev-krushna`) |
| **`W-98.2`** | **Mobile Attendance & Clock-In/Out** — 1-tap mobile punch in/out widget, GPS geofencing & coordinates capture, punch status feedback, and daily shift status (`hrms`) | **M** | Queued (`dev-krushna`) |
| **`W-98.3`** | **Mobile Leave & Balances** — My leave balance cards, leave history, apply leave modal optimized for touch interactions (`core`) | **M** | Queued (`dev-krushna`) |
| **`W-98.4`** | **Mobile Payslips & Signed Download** — Payslip monthly summary list, payslip breakdown card, mobile PDF viewer and signed download to device filesystem (`payroll`) | **S** | Queued (`dev-krushna`) |
| **`W-98.5`** | **Mobile Approvals Inbox** — Manager / HR pending approvals list, request details modal, 1-tap Approve & Reject actions with comments modal (`core`) | **M** | Queued (`dev-krushna`) |

---

## 1. Problem

Today, employees accessing the platform from mobile devices rely exclusively on a mobile web browser:
1. **No native container or app store distribution**: Employees must open a web browser and navigate to the tenant URL. There are no native APK / IPA packages for Android and iOS devices.
2. **Keycloak web redirection on mobile (`code/frontend/src/shell/auth/keycloak.js:22-30`)**: In a mobile browser, session cookies are easily cleared or blocked by aggressive mobile privacy policies, forcing users to repeatedly type credentials. In a hybrid native webview, standard browser redirects (`keycloak.init({ onLoad: 'login-required' })`) can trap the user or fail deep-link returns without custom URI scheme handling (`infinevo://auth-callback`).
3. **Desktop navigation shell vs mobile thumb navigation (`code/frontend/src/shell/AppShell.jsx:116-160`)**: While `W-80.1` (`docs/target-state/features/W-80-1-phone-shell.md`) introduced a responsive drawer for phone widths, mobile users expect persistent bottom-tab navigation for primary self-service tasks rather than opening a hamburger sidebar drawer.
4. **Competitor parity**: Leading HRMS competitors (Keka, Darwinbox, ZingHR) provide dedicated Android and iOS apps with persistent login and mobile-first self-service.

This ticket (`W-98.1`) establishes the native mobile foundation, Capacitor container, secure mobile Keycloak authentication, and mobile shell navigation so subsequent functional modules (`W-98.2`–`W-98.5`) can plug in directly.

---

## 2. Scope

### In scope
- Capacitor 6 integration within `code/frontend`:
  - `capacitor.config.ts` defining App ID `com.infinevo.app`, App Name `Infinevo`, and bundled web directory `dist`.
  - Android native project scaffolding (`android/`) targeting Android 14+ (API 34).
  - iOS native project scaffolding (`ios/`) targeting iOS 16+.
- Native mobile authentication:
  - Deep linking protocol handler (`infinevo://auth-callback`) configured in `AndroidManifest.xml` and `Info.plist`.
  - Mobile OAuth2 PKCE auth adapter (`src/mobile/mobileAuth.js`) leveraging `@capacitor/browser` and `@capacitor/app`.
  - Secure token persistence using `@capacitor/preferences` to retain refresh tokens across app termination and backgrounding.
  - Seamless fallback: Web browser users remain on standard `keycloak.js`; native app runs `mobileAuth.js`.
- Mobile App Shell (`src/mobile/MobileShell.jsx`):
  - Fixed bottom navigation bar with thumb-accessible tabs:
    - **Home** (`/me`)
    - **Attendance** (`/me/attendance` — placeholder ready for `W-98.2`)
    - **Leave** (`/me/leave` — placeholder ready for `W-98.3`)
    - **Payslips** (`/me/payslips` — placeholder ready for `W-98.4`)
    - **Approvals** (`/approvals` — visible only when user has `core.approval.decide`, ready for `W-98.5`)
    - **Profile** (`/me/profile`)
  - Safe-area inset handling (`env(safe-area-inset-top)`, `env(safe-area-inset-bottom)`) avoiding camera notches, dynamic islands, and Android navigation bars.
  - Native splash screen and status bar styling (`@capacitor/status-bar`, `@capacitor/splash-screen`).

### Out of scope
- Implementation of functional attendance clocking logic (owned by `W-98.2`).
- Implementation of leave application modals and balances (owned by `W-98.3`).
- Implementation of payslip PDF render and device storage download (owned by `W-98.4`).
- Implementation of approval decision actions (owned by `W-98.5`).
- Native push notification FCM/APNS registration service (future ticket).
- App Store & Google Play developer account signing and store submission.

---

## 3. Flow

```
[Native Mobile App Launch]
           │
           ▼
[Check @capacitor/preferences for Stored Refresh Token]
    ├─── Valid Refresh Token Found ───► [POST Keycloak /token (grant_type=refresh_token)]
    │                                          ├── Success ──► [Save New Tokens] ──► [Mount MobileShell]
    │                                          └── Failed  ──► [Proceed to Login]
    │
    └─── No Token / Expired ───► [Open System Browser via @capacitor/browser]
                                       │ (Keycloak Auth URL with PKCE + redirect_uri=infinevo://auth-callback)
                                       ▼
                         [User Enters Credentials on Keycloak]
                                       │
                                       ▼
                         [Keycloak Redirects to infinevo://auth-callback?code=...]
                                       │
                                       ▼
                         [@capacitor/app Handles Deep Link URL Open]
                                       │
                                       ▼
                         [mobileAuth.js Exchanges Auth Code for JWT via PKCE]
                                       │
                                       ▼
                         [Store Tokens in @capacitor/preferences]
                                       │
                                       ▼
                         [Close Browser Window & Mount MobileShell (/me)]
```

---

## 4. Architecture & Technical Design

### 4.1 Native Platform Engine: Capacitor 6
Capacitor wraps the existing React 18 / Vite build artifacts (`dist/`) into high-performance webview containers for Android and iOS without rewriting application logic.

Key Capacitor plugins added to `code/frontend/package.json`:
- `@capacitor/core`: ^6.0.0
- `@capacitor/cli`: ^6.0.0 (devDependencies)
- `@capacitor/android`: ^6.0.0
- `@capacitor/ios`: ^6.0.0
- `@capacitor/app`: ^6.0.0 (app lifecycle & deep link event handling)
- `@capacitor/browser`: ^6.0.0 (system browser for OAuth2 authentication)
- `@capacitor/preferences`: ^6.0.0 (secure token and settings storage)
- `@capacitor/status-bar`: ^6.0.0 (native status bar styling)
- `@capacitor/splash-screen`: ^6.0.0 (smooth launch experience)

### 4.2 Deep Link Authentication Adapter (`src/mobile/mobileAuth.js`)
In native apps (`Capacitor.isNativePlatform() === true`), authentication cannot use full-page window redirects due to webview origin isolation and third-party cookie restrictions.

1. **Initiate Login**:
   - Generates cryptographically secure PKCE `code_verifier` and `code_challenge` (SHA-256).
   - Generates random `state` and stores it temporarily in preferences.
   - Builds Keycloak authorization URL:
     `{KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/auth?client_id={CLIENT_ID}&response_type=code&scope=openid&redirect_uri=infinevo://auth-callback&code_challenge={CHALLENGE}&code_challenge_method=S256&state={STATE}`
   - Opens the URL via `Browser.open({ url, windowName: '_system' })`.

2. **Handle Callback**:
   - Native container registers scheme `infinevo`.
   - `App.addListener('appUrlOpen', async (data) => ...)` intercepts `infinevo://auth-callback?code=...&state=...`.
   - Closes browser via `Browser.close()`.
   - Validates `state`, retrieves `code_verifier`, and performs direct POST to Keycloak token endpoint:
     `POST {KEYCLOAK_URL}/realms/{REALM}/protocol/openid-connect/token`
   - Stores `access_token`, `refresh_token`, and `id_token` in `@capacitor/preferences`.
   - Configures Axios request interceptor (`apiClient`) to inject the Bearer token.

3. **Background Token Refresh**:
   - `mobileAuth.getValidToken()` checks expiration timestamp.
   - If token has < 60 seconds of validity remaining, automatically executes token refresh using stored `refresh_token`.

### 4.3 Mobile Navigation Shell (`src/mobile/MobileShell.jsx`)
When `Capacitor.isNativePlatform()` is active:
- Standard desktop header and sidebar are suppressed.
- `MobileShell` wraps `<Outlet />` with a lightweight, thumb-optimized fixed bottom navigation bar (`MobileTabBar.jsx`).
- Uses standard Ant Design tokens from `shared/theme.js` matching platform design system.
- Tab items render SVG icons with active color indicators (`token.colorPrimary` = Royal Blue `#1ES998` / `#1677ff`).

---

## 5. Implementer Tasks & File Changes

| Layer / Area | File | Change |
|---|---|---|
| Frontend config | `code/frontend/capacitor.config.ts` | **New**. Capacitor configuration defining app name, ID, and plugins. |
| Frontend config | `code/frontend/package.json` | Add Capacitor dependencies and build scripts: `"cap:sync": "cap sync"`, `"cap:android": "cap open android"`, `"cap:ios": "cap open ios"`. |
| Native Android | `code/frontend/android/app/src/main/AndroidManifest.xml` | Configure `intent-filter` for deep link scheme `infinevo://auth-callback`. |
| Native iOS | `code/frontend/ios/App/App/Info.plist` | Configure `CFBundleURLTypes` for URL scheme `infinevo`. |
| Mobile auth | `code/frontend/src/mobile/mobileAuth.js` | **New**. Mobile PKCE generation, system browser launcher, deep-link callback listener, token refresh, and preference storage. |
| Auth bridge | `code/frontend/src/shell/auth/keycloak.js` | Update `initAuth()` and `getValidToken()` to delegate to `mobileAuth` when `Capacitor.isNativePlatform()` is true (`file:code/frontend/src/shell/auth/keycloak.js:22-48`). |
| Mobile shell | `code/frontend/src/mobile/MobileShell.jsx` | **New**. Mobile layout with safe-area padding, mobile top header (company logo + tenant), and fixed bottom tab bar. |
| Mobile tabs | `code/frontend/src/mobile/MobileTabBar.jsx` | **New**. 5-tab persistent bottom bar: Home, Attendance, Leave, Payslips, Approvals (conditional). |
| Mobile CSS | `code/frontend/src/mobile/mobile.css` | **New**. Safe area variables (`padding-top: env(safe-area-inset-top)`, `padding-bottom: env(safe-area-inset-bottom)`), touch active states, and mobile viewport styling. |
| Root entry | `code/frontend/src/App.jsx` | Mount `MobileShell` conditionally when running on a native platform. |

---

## 6. Database Changes

> **Creates no database tables and executes 0 Flyway migrations.**

- [x] Standing rule: Creates no table; all authorization and tokens leverage existing Keycloak realm and Core identity contracts (`CORE-02`, `CORE-03`).
- [x] No schema changes required under `code/backend/migration/`.
- [x] No module-to-module dependencies introduced.

---

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `code/frontend/src/mobile/mobileAuth.test.js` | PKCE code challenge calculation, deep-link callback parameter parsing, token storage & retrieval in preferences, automatic refresh logic. |
| Unit | `code/frontend/src/mobile/MobileTabBar.test.jsx` | Renders 4 standard tabs for employee; renders 5th Approvals tab when user has `core.approval.decide`; tab navigation triggers correct route navigation. |
| Unit | `code/frontend/src/mobile/MobileShell.test.jsx` | Renders mobile header with company monogram; applies safe-area padding classes; suppresses desktop `AppShell`. |
| Component | `code/frontend/src/shell/auth/keycloak.test.js` | Verifies delegation: calls web `keycloak.init` on browser; delegates to `mobileAuth` on native platform. |

---

## 8. Verification

Verification steps that prove `W-98.1` works end-to-end:

```bash
# 1. Run frontend unit tests
cd code/frontend
npm test -- src/mobile/

# 2. Run lint check
npm run lint

# 3. Production build
npm run build

# 4. Capacitor sync (validates native projects and copies dist)
npx cap sync
```

### Verification matrix

| Check | Command / Action | Expected Result |
|---|---|---|
| Unit Test Suite | `npm test -- src/mobile/` | All unit tests pass with 0 failures. |
| Lint Hygiene | `npm run lint` | 0 errors, 0 warnings. |
| Vite Bundle Build | `npm run build` | `dist/` created cleanly without bundle errors. |
| Capacitor Sync | `npx cap sync` | Android and iOS assets copied and plugins linked successfully. |
| Deep Link Android Check | `adb shell am start -a android.intent.action.VIEW -d "infinevo://auth-callback?code=test_code&state=test_state" com.infinevo.app` | App opens without crashing, captures intent URL, and passes code to `mobileAuth.js`. |
| Safe Area Styling | Inspect mobile webview element | Body and tab bar respect `env(safe-area-inset-*)` on iPhone / Android notch devices. |

---

## 9. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Mobile browser pop-up blocked during login | Low | Use `@capacitor/browser` with system browser (`_system` mode), ensuring native OS handles OAuth2 redirections cleanly. |
| Web app regression on desktop browsers | Low | All mobile-specific initialization is wrapped in `Capacitor.isNativePlatform()` guards. Web builds continue to use existing `keycloak.js` and `AppShell.jsx`. |
| Deep-link collision with other apps | Low | Use unique registered custom scheme `infinevo://` and app ID `com.infinevo.app`. Android App Links and iOS Universal Links can be added seamlessly on top. |
| Session expiration while app in background | Medium | `mobileAuth.getValidToken()` validates expiration on app foregrounding (`App.addListener('appStateChange')`) and refreshes token before API requests fire. |

---

## 10. Rollback

If native mobile container builds encounter critical issues:
1. `code/frontend/src/shell/auth/keycloak.js` can revert the `Capacitor.isNativePlatform()` condition, restoring 100% web browser behavior.
2. The `android/` and `ios/` folders are standalone and do not interfere with standard web server deployment (`Vite` / Docker container `frontend`).

