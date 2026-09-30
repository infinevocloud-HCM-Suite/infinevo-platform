# Feature: Invitation screens

| Field | Value |
|---|---|
| **Feature ID** | `W-46.7` · `CORE-05` |
| **Spec file** | `docs/target-state/features/W-46-7-invitation-screens.md` |
| **Owner** | biren |
| **Apps touched** | `code/frontend/src/core/invitation` · `code/frontend/src/main.jsx` (one branch, §5a) · `code/backend/core` — `NavigationCatalogue.java` menu items only |
| **Related gaps** | none |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-30 — named by `W-46-3a` §14 row 2; split from the checklist screen, which is `W-46.6` |
| **Blocked by** | `W-45` only. `W-24.2` is on `main` (`288f9fa`) |
| **Size** | **M** |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` — two menu items | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an administrator invites a user or an employee, and the invitee accepts or declines from the emailed link | 1 |
| Frontend area | `src/core/invitation` | 1 |

Within cap. `main.jsx` is the boot file, not an area; it gains one branch, and §5a says why nothing else can carry it.

---

## 1. Problem

`W-24.2` builds invitations and **the emailed link opens a page that does not exist**. The email points at `<INVITATION_LINK_BASE_URL>?token=…` (`InvitationServiceImpl.java:687`), which is `/invitations/accept` on the frontend (`infra/docker/compose.yml`, `.github/workflows/deploy.yml`). `W-24-2-invitations.md:67` puts the acceptance screens out of scope, and no frontend ticket owned them (`W-46-3a-org-setup-screens.md:164`).

| Frozen screen | Ported? |
|---|---|
| `legacy/Payroll-Fend-react/src/pages/mainPages/acceptInvite/index.js` | **yes** in shape: accept, or decline with a reason (`:66-84`, `:95-125`). Its status lookup before the buttons (`:46-48`) is **not** ported — the new API has no lookup by token |
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/users/inviteUser.js` | **yes**: email and role (`:67-80`, `:90-96`). One role becomes several |
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/users/index.js` | **no** — a user list with delete and toggle. User management is not `W-24.2`'s |
| `legacy/Payroll-Fend-react/src/pages/mainPages/employee/OverviewTab.js:195` | the "Re-invite" action, as resend on the employee tab here |
| HRMS | nothing |

## 2. Scope

**In scope**

- User invitations at `/invitations/users`: list with status filter, invite (email and roles), resend, revoke
- Employee invitations at `/invitations/employees`: list with status filter, invite (pick an employee), resend, revoke
- The public page at `/invitations/accept?token=…`: accept, or decline with a reason; rendered with no login
- Menu items `core.invitations.users` and `core.invitations.employees`

**Out of scope**

- An "Invite" button on the employee page — that page is `W-46.1`'s area; see §14 row 2
- A user list, deactivate or delete — no endpoint
- Setting the password — Keycloak's own page, reached from Keycloak's email (`KeycloakProvisioningServiceImpl.java`, `UPDATE_PASSWORD`)
- Showing who invited you or to which organisation — no lookup by token exists, on purpose: the token is single-use and is never sent in a `GET` to the API
- Any backend change beyond the menu items

## 3. Flow

```
[admin]   --> /invitations/users      --> list(status) · create({email, roleIds}) · resend(id) · revoke(id)
          --> /invitations/employees  --> list(status) · create({employeeId})     · resend(id) · revoke(id)

[invitee] --> email link /invitations/accept?token=T     (no session, no Keycloak redirect)
          --> Accept  --> POST /v1/invitations/accept  {token}
                200 --> "Accepted. Check your email to set your password, then sign in."  [Sign in] --> /
          --> Decline --> reason --> POST /v1/invitations/decline {token, reason}
                200 --> "Declined."
          any 409 --> one message: the link is no longer valid
          503     --> "Try again in a few minutes"
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Config | `code/backend/core/.../navigation/NavigationCatalogue.java` | add `core.invitations.users` (`nav.userInvitations`, `/invitations/users`, target `/api/v1/user-invitations`, module `null`, action `core.user.manage`) and `core.invitations.employees` (`nav.employeeInvitations`, `/invitations/employees`, target `/api/v1/employee-invitations`, module `null`, action `core.employee.create`) |

Two items, not one, because the two lists need different permissions and the feed decides what a user can reach (`shell/routes.js:24-29`).

**API contract** — consumed, as on `main` (`288f9fa`):

| Call | Body / query | Returns | Permission |
|---|---|---|---|
| `GET /api/v1/user-invitations` | `?status` | list | `core.user.manage` |
| `POST /api/v1/user-invitations` | `{email, roleIds[]}` | the invitation; `400` invalid, `409` conflict | `core.user.manage` |
| `POST /api/v1/user-invitations/{id}/resend` | — | the new invitation | `core.user.manage` |
| `POST /api/v1/user-invitations/{id}/revoke` | — | empty | `core.user.manage` |
| `GET /api/v1/employee-invitations` | `?status`, `?employeeId` | list | `core.employee.create` |
| `POST /api/v1/employee-invitations` | `{employeeId}` | the invitation | `core.employee.create` |
| `POST /api/v1/employee-invitations/{id}/resend`, `/revoke` | — | as above | `core.employee.create` |
| `POST /api/v1/invitations/accept` | `{token}` | `{message}`; `409` generic; `400` blank token; `503` Keycloak unavailable | **none** — the token is the credential |
| `POST /api/v1/invitations/decline` | `{token, reason}` | `{message}`; `409` generic; `400` blank token or reason | **none** |

Status is one of `PENDING`, `ACCEPTED`, `DECLINED`, `REVOKED`, `EXPIRED`. No response carries the token.

Citations: `UserInvitationController.java:33,42-65`, `EmployeeInvitationController.java:33,42-67`, `InvitationAcceptanceController.java:26,39,42-52,67-68,87-88`, `PublicEndpoints.java` (`INVITATION_ACCEPT`, `INVITATION_DECLINE`), `InvitationStatus.java`.

## 5. Frontend changes

`W-45` contract throughout.

| File | Change |
|---|---|
| `src/core/invitation/userInvitationService.js`, `employeeInvitationService.js` | **new.** `list({status})`, `create(body)`, `resend(id)`, `revoke(id)` |
| `src/core/invitation/publicInvitationService.js` | **new.** `accept(token)`, `decline(token, reason)`. Same `apiClient`; with no token provider it sends no `Authorization` header (`shared/api/client.js:45-47`) |
| `src/core/invitation/InvitationTable.jsx` | **new.** One Ant `Table` for both lists: recipient, status `Tag`, expires, created, actions. Resend and revoke only on `PENDING`; revoke asks for confirmation |
| `src/core/invitation/UserInvitations.jsx` | **new.** `InvitationTable` plus "Invite user": email, roles multi-select from `GET /v1/roles` |
| `src/core/invitation/EmployeeInvitations.jsx` | **new.** `InvitationTable` plus "Invite employee": a searchable select over `GET /v1/employees?q=`; the employee's email is shown, not typed |
| `src/core/invitation/AcceptInvitation.jsx` | **new.** The public page. Reads `token` from the query string once, then **removes it from the address bar** (`history.replaceState`). Two buttons; decline opens a required-reason field. No call is made on load |
| `src/core/index.js` | `routes` gains the two admin routes; `publicRoutes` exported with the accept route |
| `src/main.jsx` | §5a |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/invitations/users` | `UserInvitations` | inside `AppShell`; present when the feed carries `core.invitations.users` |
| `/invitations/employees` | `EmployeeInvitations` | `core.invitations.employees` |
| `/invitations/accept` | `AcceptInvitation` | **public**, outside `AppShell`, no login |

### 5a. The public route

`main.jsx:13-34` renders nothing until `initAuth()` resolves, and `initAuth` is `login-required` (`shell/auth/keycloak.js:46-54`). An invitee has no account yet, so today the emailed link would bounce them to a Keycloak login they cannot pass.

The change: before `initAuth()`, if `location.pathname` matches a path in `core.publicRoutes`, render that element alone — `ConfigProvider` and `BrowserRouter`, no store, no `AppShell`, no `setTokenProvider` — and do not call `initAuth()`. Every other path is untouched.

`publicRoutes` is a list on purpose: `W-36.2`'s payslip link and `W-21`'s document link are API downloads and need no page, but the next public page has one obvious place to go.

**What the public page must not do**

| Rule | Why |
|---|---|
| No API call on load | mail scanners open links; an accept on load would accept for them |
| Token never in a `GET`, in storage, or in a log line | it is the credential (`W-24-2` §6) |
| One message for every `409` | the API answers every unusable token the same way (`InvitationAcceptanceController.java:39`); the page must not guess which |

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `src/core/invitation/*Service.test.js` | paths and methods; the public service sends no `Authorization` header |
| Component | `InvitationTable.test.jsx` | resend and revoke only on `PENDING`; revoke confirms first; a `409` shows the envelope message |
| Component | `UserInvitations.test.jsx` | email and at least one role required; roles posted as `roleIds` |
| Component | `EmployeeInvitations.test.jsx` | posts `employeeId`; no free-text email field |
| Component | `AcceptInvitation.test.jsx` | **no request on render**; accept posts the token; decline refuses a blank reason; `409` shows the one message; `503` shows "try again"; no token in the URL after mount; no token in `localStorage` or `sessionStorage` |
| Unit | `src/main.test.jsx` | at `/invitations/accept`, `initAuth` is **not** called and the page renders; at `/`, `initAuth` is called |
| IT | `code/backend/core/.../navigation/NavigationIT.java` (existing) | each item appears only with its own action |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
cd code/backend && ./mvnw -pl core -am verify
docker compose -f infra/docker/compose.yml up -d
```

| Check | Expected |
|---|---|
| invite user | admin@acme.local invites `new@acme.local` with one role; the row shows `PENDING`; mailpit (`localhost:8025`) holds one mail whose link starts `http://localhost:5173/invitations/accept?token=` |
| open the link, logged out | the accept page renders; **no redirect to Keycloak**; the address bar shows no token |
| accept | the success message; mailpit holds Keycloak's set-password mail; the row shows `ACCEPTED` |
| accept again | the one "no longer valid" message |
| decline | a second invitation, declined with a reason, shows `DECLINED` and the reason |
| resend | the old link gives the "no longer valid" message; the new one works |
| revoke | the row shows `REVOKED`; its link gives the same message |
| employee invitation | inviting a seeded employee mails that employee's address |
| permissions | a user with `core.employee.create` and not `core.user.manage` sees only "Employee invitations" |
| isolation | Globex's invitations are absent for Acme's admin |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The public branch in `main.jsx` widens to other paths | low | exact match against `core.publicRoutes`; `main.test.jsx` proves `/` still logs in |
| A custom role holds `core.user.manage` but not `core.role.read`, so the role list is a `403` | low — no seeded role is in that state | the form shows the envelope message and cannot submit; §14 row 3 |
| The invitee is already logged in as someone else | low | the page ignores any session; it sends only the token |
| Keycloak mail is not configured in Azure, so no set-password mail arrives | **certain until `W-10.1`** | out of this ticket; the success message still tells the truth locally |

## 10. Rollback

Revert the branch. The email link returns to a not-found page; the API is untouched.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money` / `BigDecimal` | no money |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/core/invitation` imports `@shared/*`, `@shell/screens`; `main.jsx` imports `@core` (it already imports `@shell` and `@shared`) |

## 12. Gap inventory

No BUG or DEBT row covers invitations (`legacy/docs/GAP_INVENTORY.md`).

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `code/backend/core` | two items in `NavigationCatalogue.java`; `NavigationIT` cases |
| 2 | `src/core/invitation` | the two admin services, `InvitationTable`, `UserInvitations`, `EmployeeInvitations`, tests |
| 3 | `src/core/invitation` + `src/main.jsx` | `publicInvitationService`, `AcceptInvitation`, `publicRoutes`, the boot branch, tests |

## 14. Decisions and open questions

| # | Question | Answer |
|---|---|---|
| 1 | Why does the accept page not show the organisation or the inviter? | No lookup by token exists and none is added: a lookup is a second place the token travels. The email already names the organisation (`InvitationServiceImpl.java:702`, `tenant_name`) |
| 2 | Where is "Invite" on the employee page? | Not here. `src/core/employee` is `W-46.1`'s area. When both are on `main`, one line mounts a button there that calls `employeeInvitationService.create`. Until then the Employee invitations screen does the same job |
| 3 | Can an inviter always read the role list? | With the seeded roles, yes. The list needs `core.role.read` (`RoleController.java:45-46`). Only `tenant-admin` and `platform-admin` hold `core.user.manage`, and both hold every action (`V025__catalogue_correction.sql:139-146`). A custom role with `core.user.manage` alone gets the `403` message in the form; no grant is added here |
