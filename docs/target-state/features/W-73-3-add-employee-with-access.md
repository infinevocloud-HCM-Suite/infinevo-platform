# Feature: Add Employee with access — one save creates the record and sends the invitation

| Field | Value |
|---|---|
| **Feature ID** | `W-73.3` · from `W-73` |
| **Promoted to** | `docs/target-state/features/W-73-3-add-employee-with-access.md` |
| **Owner** | claude (founder) |
| **Apps touched** | `code/backend/core` (invitation), `code/frontend/src/core/employee` |
| **Related gaps** | `D-40` (form fields) |
| **Status** | Draft |
| **Written by** | claude for the founder, 2026-10-07 |
| **Blocked by** | `D-40` on `main` (same form) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | `V163` — `role_ids` on `core.employee_invitation` | 1 |
| Externally testable behaviour | adding an employee with "Give portal access" and role `hr` sends one email; on accept the account holds `employee` + `hr` | 1 |
| Frontend area | `core/employee` | 1 |

## 1. Problem

| Fact | Evidence |
|---|---|
| Add Employee saves a record and stops | `EmployeeCreate.jsx:143-241` |
| Portal access is a second screen: Employee invitations → pick the employee → Invite | `EmployeeInvitations.jsx`, `POST /api/v1/employee-invitations` with `EmployeeInvitationRequest(employeeId)` |
| Extra roles are a third screen: User invitations, by email, with role ids | `UserInvitations.jsx`, `UserInvitationRequest(email, roleIds)` |
| Accepting an employee invitation grants `employee` only | `InvitationServiceImpl.java:503-506` |
| `grantRoles` already unions, so extra roles on the same invitation are safe | `InvitationServiceImpl.java:528-542` |

## 2. Scope

**In scope**

- `EmployeeInvitationRequest` gains `roleIds` (optional); `core.employee_invitation.role_ids uuid[]`; acceptance grants `employee` **plus** those roles
- Add Employee: a **Portal access** section at the bottom — checkbox "Give portal access", shown when ticked: roles multi-select (seeded roles except `platform-admin`; `employee` pre-ticked and locked), note "An email goes to the work email"
- One save: `POST /employees` then `POST /employee-invitations {employeeId, roleIds}`; if the second call fails the employee stays and the form shows "Saved. Invitation failed: … — invite from the employee page"
- Employee page header: an **Access** badge — *No access* with an Invite button · *Invited, expires …* with Resend · *Active* with the roles
- Validation: work email required when access is ticked (`EmployeeCreate.jsx:226` makes it optional today)

**Out of scope**

- Changing roles of an existing account (`W-73.4`)
- Bulk (`W-73.7`)

## 3. Flow

```
hr --> /employees/new --> fill --> tick Give portal access --> roles [employee ✓ locked] [hr]
   --> Save --> POST /api/v1/employees --> 201 {id}
            --> POST /api/v1/employee-invitations {employeeId, roleIds:[hr]} --> 201, email queued
employee --> link --> POST /api/v1/invitations/accept --> account linked, roles employee+hr
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| DTO | `core/.../invitation/EmployeeInvitationRequest.java` | `+ Set<UUID> roleIds` (nullable → empty) |
| Entity | `core/.../invitation/EmployeeInvitation` | `+ roleIds` (`uuid[]`) |
| ServiceImpl | `InvitationServiceImpl.java` create (employee path) | validate each role id exists in the tenant and is not `platform-admin` — same check the user path does at `:104-108` |
| ServiceImpl | `InvitationServiceImpl.java:503-506` | `grantRoles(tenantId, account, employeeRole + invitation.roleIds)` |
| DTO | `EmployeeInvitationResponse` | `+ roleIds` |
| Controller | `EmployeeController` | `GET /api/v1/employees/{id}/access` → `{state: NONE|INVITED|ACTIVE, invitationId, expiresAt, roles[]}` built from `user_account_id` (`W-13.4`) and the latest invitation |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/employee-invitations` | `{employeeId, roleIds?}` | `201 {id, …, roleIds}` | `core.employee.create` (unchanged, `EmployeeInvitationController.java:42`) |
| GET | `/api/v1/employees/{id}/access` | — | `{state, invitationId, expiresAt, roles}` | `core.employee.read` |

## 5. Frontend changes

| File | Change |
|---|---|
| `core/employee/EmployeeCreate.jsx` | Portal access section; two-step save with the partial-failure message |
| `core/employee/EmployeePage.jsx` | Access badge with Invite / Resend |
| `core/employee/employeeService.js` | `getAccess(id)` |
| `core/invitation/employeeInvitationService.js` | `create({employeeId, roleIds})` |
| `core/roles/roleService.js` (exists for `/roles`) | reuse for the select |

**Routes added** — none.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `V163__employee_invitation_roles.sql` | `core.employee_invitation + role_ids uuid[] not null default '{}'` | existing table | yes |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `EmployeeInvitationRolesIT` | accept grants `employee` + given roles; `platform-admin` id refused; another tenant's role id refused |
| Unit | `EmployeeCreate.test.jsx` | section hidden until ticked; `employee` locked; email required when ticked; partial failure message |
| Unit | `EmployeePage.test.jsx` | three badge states |

## 8. Verification

| Check | Expected |
|---|---|
| Add employee, no access | No email; badge *No access* |
| Add with access + `hr` | One email; after accept `GET /me` roles = `employee, hr` |
| Add with access, work email empty | Form blocks save |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Employee saved, invitation lost on a network error | low | message names the fallback; Invite button on the page |

## 10. Rollback

`role_ids` defaults to empty; old clients keep working. Revert the frontend commit.
