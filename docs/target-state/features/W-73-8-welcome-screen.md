# Feature: Welcome screen — the first sign-in, per role

| Field | Value |
|---|---|
| **Feature ID** | `W-73.8` · from `W-73` |
| **Promoted to** | `docs/target-state/features/W-73-8-welcome-screen.md` |
| **Owner** | claude (founder) |
| **Apps touched** | `code/backend/core` (one column), `code/frontend/src/shell` |
| **Related gaps** | `D-35` (home path) |
| **Status** | Draft |
| **Written by** | claude for the founder, 2026-10-07 |
| **Blocked by** | `D-35` and `W-73.1` on `main` (the page shows the branding and sits on the home path) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | `V165` — `welcome_seen_at` on `core.user_account` | 1 |
| Externally testable behaviour | a new tenant admin's first page explains the three next steps once, and never again after Dismiss | 1 |
| Frontend area | `shell/screens` | 1 |

## 1. Problem

| Fact | Evidence |
|---|---|
| After accepting an invitation the user lands on a working screen with no explanation of what to do | `AppShell.jsx:136` redirects to the home path |
| The setup checklist exists but nothing points a new admin at it | `/setup`, `SetupStepCatalogue` |
| An employee's first page is `/me` with empty panels | `core/portal` |

## 2. Scope

**In scope**

- `core.user_account.welcome_seen_at timestamptz null`; `/me` returns `welcomeSeen`
- `PUT /api/v1/me/welcome-seen`
- Page `/welcome`, shown instead of the home path while `welcomeSeen` is false, with the tenant branding (`W-73.1`) and one card set per role:
  - **tenant-admin**: "Three steps to your first payroll" — 1 Add work location and employees · 2 Pay schedule and salary components · 3 Statutory settings — each a link into `/setup`'s step; live progress from `GET /api/v1/setup-checklist` (`SetupChecklistResponse.completedCount`)
  - **hr / manager / payroll-officer**: "Where things are" — three links to their home, approvals and the people list
  - **employee**: "Complete your profile" — links to Personal and Contact in `/me`, "Your payslips appear here", "Apply for leave here"
- Buttons: **Go to my home** (marks seen) · the page is reachable later from the user menu as "Getting started"

**Out of scope**

- Product tours, tooltips, videos
- Per-tenant custom text

## 3. Flow

```
sign in --> GET /me {welcomeSeen:false, roles} --> /welcome (role cards) --> Go to my home
        --> PUT /me/welcome-seen --> homePath (D-35)
later   --> user menu "Getting started" --> /welcome (no write)
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Entity | `core/.../identity/UserAccount` | `welcomeSeenAt` |
| Controller | `shared/.../identity/MeController.java` | `MeView + welcomeSeen`; `PUT /api/v1/me/welcome-seen` |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me` | — | `+ welcomeSeen` | signed in |
| PUT | `/api/v1/me/welcome-seen` | — | `204` | signed in |

## 5. Frontend changes

| File | Change |
|---|---|
| `shell/screens/Welcome.jsx` (new) | branding header, cards by role, progress for the admin card |
| `shell/AppShell.jsx:136` | `/` → `/welcome` when `!welcomeSeen`, else `homePath` |
| `shell/Header.jsx` | user menu item "Getting started" |
| `shell/auth/useMe.js` | `welcomeSeen`, `markWelcomeSeen()` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/welcome` | `Welcome` | signed in |

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `V165__user_account_welcome.sql` | `core.user_account + welcome_seen_at timestamptz null` | existing table | yes |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `Welcome.test.jsx` | the right cards per role; admin card shows 2/8 from the checklist |
| Unit | `AppShell.test.jsx` | `/` → `/welcome` once; after seen → home path |
| Integration | `MeWelcomeIT` | PUT sets the timestamp once; idempotent |

## 8. Verification

| Check | Expected |
|---|---|
| New tenant admin accepts the invitation | `/welcome` with three steps and 0/8 progress |
| Click Go to my home, sign out, sign in | Lands on `/setup`, not `/welcome` |
| Employee first sign-in | Profile card, no setup card |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Users with no feed items (no modules) | low | `NoModules` screen wins over `/welcome` |

## 10. Rollback

Set the redirect back to the home path; the column can stay.
