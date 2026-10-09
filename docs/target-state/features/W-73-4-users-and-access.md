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

## 11. Amended at build (2026-10-08)

| Spec said | Built | Evidence |
|---|---|---|
| `PUT /users/{id}/roles` auth `core.user.manage` | `core.role.assign`, unchanged; the screen hides Change roles without it | `UserRoleController.java:30-31` |
| `V164__user_account_enabled.sql` if no `enabled` | No new column: `status` exists, `enabled` = `status = 'ACTIVE'`, Disable writes `DISABLED`. `V164__user_account_disable.sql` holds the two functions below instead | `core/V009__user_account.sql:12` |
| `UserController` in `core/.../authz/` | `core/.../user/`: thirteen test contexts scan `authz` without the Keycloak bean Disable needs | `PermissionGuardTestApp.java`, `AuthzTestApp.java` |
| Last sign-in "if held" | Not held: `last_synced_at` moves only when token claims change | `UserAccount.java` `applyClaims` |
| — | A disabled account holds no action; its roles stay for Enable | `RoleActionRepository.findActionCodesOfUser` |
| Disable = Keycloak `enabled=false` | One Keycloak user can hold an account in several tenants, and the flag is realm-wide. Disable turns it off only when no other tenant holds that user ACTIVE; Enable turns it on only when no other tenant holds it DISABLED. Otherwise the per-tenant status decides (`core.keycloak_user_account_states`, SECURITY DEFINER, `V164`) | `UserDirectoryService.disable` / `enable` |
| — | Keycloak is called before commit; if the transaction then rolls back, the flag is set back | `UserDirectoryService.setKeycloakEnabled` |
| — | The last-tenant-admin count runs under a per-tenant advisory lock, in both the role-removal and the disable path | `UserRoleRepository.lockTenantAdminGuard` |
| — | A disabled account has no role chips on `/me`, and impersonating it grants no action (`core.resolve_impersonation` replaced in `V164`) | `UserRoleRepository.findRoleCodesOfUser` |
| Disable / Enable need `core.user.manage` | Also `core.role.assign` when the target holds `tenant-admin` | `UserController.requireRoleAssignForTenantAdmin` |
| — | HR (`core.employee.create` only) no longer sees the employee invitations list; Revoke sits beside Resend on the employee page | `EmployeePage.jsx` `handleRevoke` |
| — | `POST /user-invitations` with roles needs `core.role.assign` and refuses `platform-admin`, as `W-73.3` does for employee invitations | `EmployeeInvitationController.java:56-61` |
| — | The Employee invitations screen's "invite an existing employee" picker is gone; the employee page's Invite (`W-73.3`) does that | `EmployeePage.jsx:81` |
| Old routes redirect | A route may name `mountWith`; the two old paths mount with `/users` | `shell/routes.js` |
