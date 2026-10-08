# Feature: Users & Access — one screen for accounts, roles and invitations

| Field | Value |
|---|---|
| **Feature ID** | `W-73.4` · from `W-73` |
| **Promoted to** | `docs/target-state/features/W-73-4-users-and-access.md` |
| **Owner** | claude (founder) |
| **Apps touched** | `code/backend/core` (authz, invitation, navigation), `code/frontend/src/core` |
| **Related gaps** | — |
| **Status** | Draft |
| **Written by** | claude for the founder, 2026-10-07 |
| **Blocked by** | `W-73.3` (its access endpoint and `roleIds` on employee invitations) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | a tenant admin changes a user's roles and sees every pending invitation on one screen | 1 |
| Frontend area | `core/users` (new), replacing `core/invitation`'s two list screens | 1 |

## 1. Problem

| Fact | Evidence |
|---|---|
| Two menu items, User invitations and Employee invitations, for one job | `NavigationCatalogue.java:123-135` |
| No screen lists the tenant's user accounts | `core/` has no users screen |
| Roles can be changed by API only | `UserRoleController.java:30` |
| An admin cannot see "who has access and as what" without reading three lists | `UserInvitations.jsx`, `EmployeeInvitations.jsx`, `/roles` |

## 2. Scope

**In scope**

- `GET /api/v1/users`: accounts in the tenant with email, name, roles, linked employee, status (active / disabled), last sign-in if held
- `/users` screen, two tabs: **Users** (table, Change roles drawer using `PUT /users/{id}/roles`, Disable) · **Invitations** (both kinds in one table with a Kind column; Invite user, Resend, Revoke — the existing endpoints)
- "Invite user" drawer = today's user invitation form (email + roles)
- Menu: one item `core.users` → `/users` (`core.user.manage`) replaces `core.invitations.users` and `core.invitations.employees`; `navLabels.js` updated; the old routes redirect
- Guard: a user cannot remove their own `tenant-admin`; the last `tenant-admin` cannot be removed (backend check)

**Out of scope**

- Creating a user without an invitation
- Password reset (Keycloak's own page)
- Role editing (`/roles` stays)

## 3. Flow

```
tenant-admin --> /users
  Users tab       --> GET /api/v1/users --> table --> Change roles --> PUT /api/v1/users/{id}/roles
  Invitations tab --> GET /api/v1/user-invitations + GET /api/v1/employee-invitations --> one table
                  --> Invite user --> POST /api/v1/user-invitations
                  --> Resend / Revoke --> existing POSTs
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../authz/UserController.java` (new) | `GET /api/v1/users` (`core.user.manage`), `POST /{id}/disable`, `POST /{id}/enable` |
| Controller | `UserRoleController.java:30` | keep; add the two guards (own `tenant-admin`, last `tenant-admin`) → `409` |
| Service | `UserDirectoryService` (new) | one query: `core.user_account` ⟕ `core.user_role` ⟕ `core.employee` by `user_account_id` |
| DTO | `UserView(id, email, displayName, roles[], employeeId, employeeNumber, enabled)` | new |
| Navigation | `NavigationCatalogue.java:123-135` | two items → one `core.users` |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/users` | `?q=` | `[UserView]` | `core.user.manage` |
| PUT | `/api/v1/users/{id}/roles` | `{roleIds}` | `200` / `409` last admin | `core.user.manage` (unchanged) |
| POST | `/api/v1/users/{id}/disable` · `/enable` | — | `204` | `core.user.manage` |

Disable = Keycloak user `enabled=false` through the admin client `W-24.2` wired (`fix/w24-2` history) plus `core.user_account.enabled`.

## 5. Frontend changes

| File | Change |
|---|---|
| `core/users/UsersScreen.jsx` (new) | tabs, tables, drawers |
| `core/users/userService.js` (new) | `list`, `setRoles`, `disable`, `enable` |
| `core/invitation/UserInvitations.jsx`, `EmployeeInvitations.jsx` | become the Invitations tab's two sources; their routes redirect to `/users?tab=invitations` |
| `core/invitation/InvitationTable.jsx` | gains a Kind column |
| `shell/navigation/navLabels.js` | `nav.users` → "Users & access"; drop the two old keys (the label test fails until both sides agree — intended) |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/users` | `UsersScreen` | feed item `core.users` |
| `/invitations/users`, `/invitations/employees` | `Navigate` to `/users?tab=invitations` | — |

## 6. Database changes

None if `core.user_account` already has `enabled`; otherwise `V164__user_account_enabled.sql` (boolean not null default true). Check before branching.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `UserDirectoryIT` | roles and linked employee per row; another tenant's users never appear |
| Integration | `UserRoleGuardIT` | own `tenant-admin` removal → 409; last `tenant-admin` → 409 |
| Unit | `UsersScreen.test.jsx` | both invitation kinds in one table; Change roles sends ids; old routes redirect |

## 8. Verification

| Check | Expected |
|---|---|
| Menu as `tenant-admin` | One item "Users & access"; the two invitation items gone |
| Change an `employee` to `employee` + `manager` | Next sign-in shows Approvals |
| Remove your own `tenant-admin` | Refused with a clear message |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Bookmarks to the old invitation routes | medium | redirects kept one release |

## 10. Rollback

Restore the two catalogue items; the screen can stay unreachable.
