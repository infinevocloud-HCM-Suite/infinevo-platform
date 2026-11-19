# Feature: Mobile App Foundation, Native Shell & Secure Auth

| Field | Value |
|---|---|
| **Feature ID** | `W-98.1` · `PLAT-MOBILE` (mobile app foundation & auth) |
| **Promoted to** | `docs/target-state/features/W-98-1-mobile-shell-and-auth.md` — **`W-98-1` with hyphens**, never `W-98.1` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/frontend` (`package.json`, `capacitor.config.json`, `android/`, `ios/`, `src/mobile/`, `src/shell/auth/keycloak.js`, `src/main.jsx`) · `infra/keycloak/infinevo-realm.json` · `code/backend/shared` (CORS allow-list only) |
| **Related gaps** | founder 2026-11-13 planned product roadmap ("Priority 2 — competing with Keka: `W-98` Mobile app") |
| **Status** | **Spec corrected 2026-11-19 after review. Code on `feature/krushna-specs` does not yet meet it.** |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | nothing |

---

## Size cap — `W-98` splits into five

| Ticket | Scope | Size |
|---|---|:---:|
| **`W-98.1`** (this spec) | Capacitor 6 container, Keycloak PKCE login by deep link, secure token storage, bottom tab shell | **M** |
| `W-98.2` | Attendance clock-in/out with location (`hrms`) | M |
| `W-98.3` | Leave balances, history, apply (`core`) | M |
| `W-98.4` | Payslips and the payslip PDF (`payroll`) | M |
| `W-98.5` | Approvals inbox (`core`) | M |

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `shared` (one CORS setting, no endpoint) | 1 | Pass |
| Flyway migration | none | 1 | Pass |
| Externally testable behaviour | An employee signs in on a phone and lands on the tab shell | 1 | Pass |
| Frontend area | `src/mobile/` | 1 | Pass |

---

## 1. Problem

| # | What is missing today | Evidence |
|---|---|---|
| 1 | There is no Android or iOS app; employees use a phone browser. | no `android/` or `ios/` on `main` |
| 2 | The web login is a full-page redirect, which cannot return to a native webview. | `code/frontend/src/shell/auth/keycloak.js` |
| 3 | Keycloak accepts only the web origin as redirect and origin, so a native login cannot finish. | `infra/keycloak/infinevo-realm.json:79-84` |
| 4 | The app gets its API and Keycloak addresses from `window.__ENV`, which only the web container fills in. A native bundle falls back to `/api` and `http://localhost:8081`. | `code/frontend/src/shared/config.js:16-19`, `code/frontend/public/env.js:3` |
| 5 | The API has no CORS setting, so a webview on another origin cannot call it. | no CORS configuration under `code/backend` |

---

## 2. Scope

**In scope**

| Area | Rule |
|---|---|
| Container | Capacitor 6. `@capacitor/cli`, `@capacitor/core`, `@capacitor/android` and `@capacitor/ios` are all on the **same major version (6)**. Config file is `code/frontend/capacitor.config.json`. |
| Targets | Android: `minSdkVersion` 22, target API 34 (as in `android/variables.gradle`). iOS: **13.0** (as in `ios/App/Podfile`). |
| Addresses | A native build is made with `VITE_API_BASE_URL` and `VITE_KEYCLOAK_URL` set as environment variables. `shared/config.js` already reads them (precedence 2). A new script `build:mobile` fails if either is unset or is not an absolute `https://` address. No `.env` file is committed. |
| Keycloak | `infinevo-web` in `infra/keycloak/infinevo-realm.json` gains redirect URI `infinevo://auth-callback`, post-logout redirect `infinevo://auth-callback`, and web origins `https://localhost` (Android webview) and `capacitor://localhost` (iOS webview). `infra/keycloak/check-realm.mjs` still passes. |
| API access | The API allows the two webview origins by CORS, bearer token only, no cookies. Set in `code/backend/shared/.../security/ResourceServerConfig.java`. The list is configuration, empty by default. |
| Login | PKCE (S256) in the system browser, return by deep link `infinevo://auth-callback`, `state` checked. The verifier comes from `crypto.getRandomValues` and the challenge from `crypto.subtle`; if either is missing, login fails with a message — no weaker fallback. |
| Token storage | Refresh token and ID token are kept in Android Keystore / iOS Keychain through a secure-storage plugin. `@capacitor/preferences` holds nothing secret. `android:allowBackup="false"`. |
| Session expiry | When refresh fails or the API answers 401, tokens are cleared and a new login starts. The mobile shell registers the 401 handler from `shared/api/client.js` (`setUnauthorizedHandler`). |
| Login cancelled | If the user closes the login browser, the app shows a "Sign in" screen with a retry button. It never stays blank. |
| Logout | Revokes the refresh token at Keycloak, ends the SSO session (end-session with `id_token_hint`, opened in the system browser), clears secure storage, then shows the "Sign in" screen. |
| Shell | `MobileShell.jsx` with a fixed bottom bar of **five** tabs: Home `/me`, Attendance `/me/attendance`, Leave `/me/leave`, Payslips `/me/payslips`, Approvals `/me/approvals` (only with `core.approval.decide`). Profile is reached from Home (`/me/profile`), not a tab. Safe-area insets respected. |
| Splash and status bar | `@capacitor/splash-screen` and `@capacitor/status-bar` are used at start-up. |
| Repository hygiene | Keystores are git-ignored (`*.jks`, `*.keystore` lines active in `android/.gitignore`). Unused dependencies are not added. |
| Web app | Unchanged. All mobile code runs only behind `Capacitor.isNativePlatform()`. |

**Out of scope**

- Attendance, leave, payslip and approval screens (`W-98.2`–`W-98.5`).
- Push notifications, biometric unlock, offline mode.
- Store accounts, signing and submission.

---

## 3. Flow

```
App start --> secure storage has refresh token?
   yes --> POST Keycloak /token (refresh) --> ok --> MobileShell (/me)
                                          --> fail --> clear tokens --> login
   no  --> system browser: Keycloak /auth (PKCE, redirect infinevo://auth-callback)
              user signs in --> deep link --> check state --> POST /token (code + verifier)
              --> store tokens in Keystore/Keychain --> MobileShell (/me)
              user closes browser --> "Sign in" screen with retry
API 401 --> clear tokens --> login
Logout  --> revoke refresh token --> end SSO session --> clear storage --> "Sign in" screen
```

---

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Security config | `code/backend/shared/src/main/java/com/infinevo/shared/security/ResourceServerConfig.java` | CORS allow-list read from configuration; allowed headers `Authorization`, `Content-Type`; no credentials. |
| Realm | `infra/keycloak/infinevo-realm.json` | Redirect URI, post-logout redirect and two web origins on `infinevo-web` (see Scope). |

**API contract** — no endpoint is added or changed.

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| — | — | — | — | — |

## 5. Frontend changes

| File | Change |
|---|---|
| `code/frontend/package.json` | Capacitor 6 packages, CLI on 6; a secure-storage plugin; scripts `build:mobile`, `cap:sync`, `cap:android`, `cap:ios`. |
| `code/frontend/capacitor.config.json` | App ID `com.infinevo.app`, name `Infinevo`, `webDir` `dist`, deep-link scheme `infinevo`. |
| `code/frontend/android/app/src/main/AndroidManifest.xml` | Intent filter for `infinevo://auth-callback`; `allowBackup="false"`. |
| `code/frontend/android/.gitignore` | Keystore lines active. |
| `code/frontend/ios/App/App/Info.plist` | URL scheme `infinevo`. |
| `code/frontend/src/mobile/mobileAuth.js` | PKCE, deep-link callback, secure storage, refresh, expiry, cancel, logout as in Scope. |
| `code/frontend/src/shell/auth/keycloak.js` | Delegates to `mobileAuth.js` on a native platform only. |
| `code/frontend/src/mobile/MobileShell.jsx` | Shell, routes, 401 handler, "Sign in" retry screen. |
| `code/frontend/src/mobile/MobileTabBar.jsx` | Five tabs. |
| `code/frontend/src/mobile/mobile.css` | Safe-area and touch styles. |
| `code/frontend/src/main.jsx` | Mounts `MobileShell` on a native platform. |

**Routes added** (native only)

| Path | Component | Guard / layout |
|---|---|---|
| `/me`, `/me/profile`, `/me/:panelId` | `PortalLayout` | `MobileShell` |
| `/me/attendance`, `/me/leave`, `/me/payslips`, `/me/approvals` | placeholders until `W-98.2`–`W-98.5` | `MobileShell`; Approvals needs `core.approval.decide` |

## 6. Database changes

None. No table, no migration.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `code/frontend/src/mobile/mobileAuth.test.js` | PKCE challenge; `state` mismatch rejected; no weak fallback; tokens go to secure storage, never to Preferences; refresh; refresh failure starts login; browser closed resolves to "cancelled"; logout calls revoke and end-session. |
| Unit | `code/frontend/src/mobile/MobileTabBar.test.jsx` | Four tabs for an employee, five with `core.approval.decide`; navigation. |
| Unit | `code/frontend/src/mobile/MobileShell.test.jsx` | Safe-area classes; a 401 triggers login; retry screen after a cancelled login. |
| Unit | `code/frontend/src/shared/config.test.js` (new or existing) | `VITE_*` values are used when `window.__ENV` is empty. |
| Script | `infra/keycloak/check-realm.mjs` | Realm still valid with the new redirect and origins. |

## 8. Verification

```bash
cd code/frontend
npm test -- src/mobile/
npm run lint
VITE_API_BASE_URL=https://<api-host>/api VITE_KEYCLOAK_URL=https://<keycloak-host> npm run build:mobile
npx cap sync
node ../../infra/keycloak/check-realm.mjs
```

| Check | Expected | Result |
|---|---|---|
| Unit tests | All pass | not run |
| Lint | 0 errors, 0 warnings | not run |
| `build:mobile` without the two variables | Build fails with a clear message | not run |
| `npx cap sync` | Completes; CLI and platforms are all version 6 | not run |
| Android debug build (`./gradlew assembleDebug` in `android/`) | Builds | not run |
| Sign in on an Android device or emulator against the deployed Keycloak | Lands on `/me`; an API call returns 200 | not run |
| Sign in on iOS simulator | Same | not run |
| Close the login browser | "Sign in" screen with retry | not run |
| Kill and reopen the app | Still signed in, no login prompt | not run |
| Expire the session at Keycloak, then pull data | App returns to login | not run |
| Logout, then tap Sign in | Keycloak asks for the password again | not run |
| `adb backup` / device backup | Contains no token | not run |
| Web app in a desktop browser | Login and shell unchanged | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A wide CORS list exposes the API | Low | Exact origins only, bearer token only, list empty by default. |
| Custom scheme taken over by another app | Low | PKCE and `state` make a stolen code useless; App Links / Universal Links can follow later. |
| Web regression | Low | Native code paths are gated; web tests stay green. |
| Realm change not applied to a running Keycloak | Medium | The realm imports once; `infra/keycloak/README.md` describes the re-import step, which the deploy must run. |

## 10. Rollback

Remove the redirect and origins from the realm and empty the CORS list; the native app can no longer sign in and the web app is unaffected. `android/` and `ios/` are not part of the web deployment.

---

## Code gaps against this spec (2026-11-19)

| Gap | Where |
|---|---|
| Redirect URI and webview origins are not registered in Keycloak, so login cannot finish | `code/frontend/src/mobile/mobileAuth.js:161,269` · `infra/keycloak/infinevo-realm.json:79-84` |
| No backend address in a native build; no `build:mobile` script; no CORS on the API | `code/frontend/src/shared/config.js:16-19` · `code/frontend/package.json` |
| CLI is Capacitor 7 while the platforms are 6 | `code/frontend/package.json:52-54` · `code/frontend/android/app/capacitor.build.gradle:5-6` |
| Tokens are in plain Preferences and Android backup is on | `code/frontend/src/mobile/mobileAuth.js:105-108` · `code/frontend/android/app/src/main/AndroidManifest.xml:10` |
| Session expiry clears tokens but does not start a login; no 401 handler in the mobile shell | `code/frontend/src/mobile/mobileAuth.js:351-353` · `code/frontend/src/mobile/MobileShell.jsx` |
| Closing the login browser leaves the app blank | `code/frontend/src/mobile/mobileAuth.js:294-298` |
| Logout does not revoke the token or end the SSO session | `code/frontend/src/mobile/mobileAuth.js:359-378` |
| PKCE falls back to `Math.random` and a non-hashed challenge | `code/frontend/src/mobile/mobileAuth.js:51-55,73-74` |
| Keystore ignore lines are commented out | `code/frontend/android/.gitignore:56-58` |
| Splash screen and status bar plugins are installed but never used; `typescript` is unused; `cap:*` scripts are missing | `code/frontend/package.json` |
