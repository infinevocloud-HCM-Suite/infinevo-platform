# W-11 Authorization — Evidence

| Item | Exists | Where |
|---|---|---|
| HRMS permission check | Yes, role-based | SecurityConfig:39-43 |
| Payroll permission check | Yes, action-based | AuthzServiceImpl:84-89 |
| In-process cache | Yes, Payroll only | AuthzServiceImpl:29 |
| Actions seeded | HRMS: 88 at startup | ActionServiceImpl:148-298 |
| Payroll: runtime-created | ActionServiceImpl:24-34 |
| Role scoping | HRMS: global | OurUsers.getAuthorities():103-111 |
| Payroll: org-scoped | OrganizationRole:8, OrganizationUserRoleMapping:468 |

---

## HRMS Authorization (`hrmstestdb`)

### Tables
- `role` — `role_name`, no org_id field
- `action` — `action_name`, `alias`, `description`
- `user_action_mapping` — `user_id` FK, `action_id` FK
- `user_roles` — `user_id` FK, `role_id` FK (join table for multi-role support)

### Permission check at request time
Backend uses role-based Spring Security: endpoints declare `@PreAuthorize("hasAuthority('admin')")` or `.requestMatchers("/admin/**").hasAuthority("admin")` at SecurityConfig:39-43. Frontend uses `ActionProtectedRoute` component which checks `actions` array in React Context (ActionProtectedRoute.jsx:40-41). Actual role-to-authority mapping happens in `OurUsers.getAuthorities()` which converts `roles` list to `SimpleGrantedAuthority` — backend does NOT use action-based checks, only roles.

Where: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/config/SecurityConfig.java:39-43` (request-level), `legacy/HRMS_Frontend/src/components/context/ActionProtectedRoute.jsx:40-41` (frontend).

### Actions
88 distinct actions seeded at startup per `createActionIfNotExist()`. Covers dashboards (6), employee CRUD (6), user management (6), roles (5), projects (8), tasks (5), timesheets (17), attendance (2), leaves (13), overtime (5), holidays (1), profile (1). Actions are mapped to roles at startup via `mapDefaultActionsToRoles()` — admin gets ~54, HR ~50, manager ~24, supervisor ~24, user ~21.

Where: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/serviceimpl/useraccess/ActionServiceImpl.java:152-290` (predefined list), `:301-359` (role-action mapping).

### Role scoping
**Global (not org-scoped).** `Role` entity has no `organization_id` field. Roles are shared across all users.

Where: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/useraccess/Role.java` (entity declares no org_id).

---

## Payroll Authorization (`payrollDB` / `payroll_test_db`)

### Tables
- `organizationRole` — `roleId`, `org_id` FK (org-scoped)
- `action` — `code` (e.g. `EMPLOYEE.BASIC.VIEW`), `label`, `category`, `active`
- `organization_role_action` — `organization_role_id` FK, `action_id` FK
- `organizationUserRoleMapping` — `org_id` FK, `company_user_id` FK, `role_id` FK

### Permission check at request time
Via `AuthzServiceImpl.canPerform()` at line 84: resolves user's allowed actions, checks membership. Called by controllers or filters to gate access. Actions are looked up at call time using `getAllowedActionsForUser()` which queries the `organization_role_action` and role mappings, then caches result.

Where: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/auth/AuthzServiceImpl.java:84-89`.

### The in-process cache
**Found.** Payroll holds an in-process cache in `AuthzServiceImpl` line 29: `private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();`. Key format: `userId::orgId`. Cache is populated on first call to `getAllowedActionsForUser()` (lines 45-81) and **invalidated when a role's actions change** via `invalidateUserOrgCache()` (lines 92-94, called from `RoleActionController:94`).

This is the cache the build order references as blocking two-replica scale-out: each instance holds its own copy, so a role change in one instance does not immediately propagate to others until the cache expires or is manually invalidated.

Where: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/auth/AuthzServiceImpl.java:29` (cache declaration), `:45-81` (population), `:92-94` (invalidation), `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/RoleActionController.java:94` (called after role action change).

### Actions
**Created at runtime.** No predefined seed set. Actions are added via `ActionServiceImpl.createAction()` per request (line 24-34). The system initializes with no actions; admins create them as needed.

Where: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/action/ActionServiceImpl.java:24-34` (runtime creation).

### Role scoping
**Organization-scoped.** `OrganizationRole` has `org_id` FK at column 19. `OrganizationUserRoleMapping` also has `org_id` at column 474. Roles are specific to each organization tenant.

Where: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/OrganizationRole.java:8-19` (org_id FK), `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/OrganizationUserRoleMapping.java:468` (org_id FK).

---

## Summary

| Aspect | HRMS | Payroll |
|---|---|---|
| Permission model | Role-based @ request | Action-based @ call |
| Action count | ~88, seeded at startup | Dynamic, created at runtime |
| Role scoping | Global | Per-organization |
| In-process cache | None (actions in localStorage only) | Yes, `ConcurrentHashMap` `userId::orgId` |
| Cache invalidation | N/A | Manual via `invalidateUserOrgCache()` on role change |
