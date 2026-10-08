# Feature: Role-based workspaces & onboarding — parent

| Field | Value |
|---|---|
| **Feature ID** | `W-73` · parent of `W-73.1` – `W-73.9` |
| **Promoted to** | `docs/target-state/features/W-73-role-based-workspaces.md` |
| **Owner** | claude (founder) |
| **Apps touched** | `code/backend/core`, `code/backend/payroll`, `code/backend/worker`, `code/frontend`, `infra/keycloak` |
| **Related gaps** | `D-33` – `D-42` (tracker, the small half of the same review) |
| **Status** | Draft — children are specs; this file is the map |
| **Written by** | claude for the founder, 2026-10-07 |

## 1. Problem

Platform staff, a tenant admin and an employee all sign in to the same long flat menu and the same
first page. Onboarding a tenant takes platform staff three screens in two tenants.

| Fact | Evidence |
|---|---|
| Platform tenant's `platform-admin` holds every action, so platform staff see every screen | `V082__platform_tenant.sql:37-48` · `D-33` |
| The menu is flat: eight top-level items, four groups | `NavigationCatalogue.java:52-168` · `D-34` |
| Everyone lands on the first menu path | `AppShell.jsx:42,136` · `D-35` |
| Header shows the tenant name only (`D-13`), no logo, no role, no user | `Header.jsx:102-105` |
| `GET /api/v1/me` returns no roles | `MeController.java:40` |
| Create Tenant has no admin email; the admin is invited by hand after an act-as | `TenantCreate.jsx:70-109` · `D-42` |
| Add Employee creates a record only; portal access is a second screen (employee invitation) and extra roles a third (user invitation) | `EmployeeCreate.jsx:143-241` · `EmployeeInvitationRequest(employeeId)` · `UserInvitationRequest(email, roleIds)` |
| `PUT /api/v1/users/{id}/roles` exists with no screen | `UserRoleController.java:30` |
| Employee page has no documents tab although the document store and its client exist | `employeeTabs.jsx:11-40` · `core/document/documentService.js` |

## 2. Scope

**In scope** — nine child specs, each its own branch:

| Child | Spec | Size | Needs first |
|---|---|---|---|
| `W-73.1` Header branding | `W-73-1-header-branding.md` | S | — |
| `W-73.2` Platform dashboard | `W-73-2-platform-dashboard.md` | S | `D-33`, `D-35`, `D-42` |
| `W-73.3` Add Employee with access | `W-73-3-add-employee-with-access.md` | S | `D-40` |
| `W-73.4` Users & Access screen | `W-73-4-users-and-access.md` | M | `W-73.3` |
| `W-73.5` Documents tab | `W-73-5-employee-documents-tab.md` | S | — |
| `W-73.6` Scheduled earnings | `W-73-6-scheduled-earnings.md` | M | `D-37`, `D-38` |
| `W-73.7` Bulk invite | `W-73-7-bulk-invite.md` | M | `W-73.3` |
| `W-73.8` Welcome screen | `W-73-8-welcome-screen.md` | S | `D-35`, `W-73.1` |
| `W-73.9` Country templates | `W-73-9-country-templates.md` | M | `D-42` |

**Out of scope**

- The defects `D-33` – `D-42`: fixed as defects, one commit each, before or beside the children
- Payment, subscription plans, the marketing site (`W-66`)
- A per-user dashboard builder; each role gets one fixed home page

## 3. What each role sees when this is done

| Role | Home page | Menu groups |
|---|---|---|
| platform staff (platform tenant) | `/admin` dashboard (`W-73.2`) | Tenants · Audit |
| tenant-admin | `/setup` until complete, then module dashboard | People · Organisation · Payroll · HRMS · Settings |
| hr | `/employees` | People · Organisation · HRMS |
| payroll-officer / finance | `/payroll/dashboard` | Payroll · People (read) |
| manager | `/hrms/dashboard` | Approvals · HRMS · My team |
| employee | `/me` | Me (portal panels only) |

The menu itself is the feed, filtered by action as today; the groups come from `D-34`, the home path
from `D-35`. The children add screens and fields, not a second navigation system.

## 4. Order

1. Defects `D-33`, `D-34`, `D-35`, `D-42` — scope, groups, home page, admin email. One week, two developers.
2. `W-73.1`, `W-73.2`, `W-73.3`, `W-73.4` — the header, the platform home, one-screen employee access. These make the product look finished.
3. `W-73.5`, `W-73.8` — documents and the welcome page.
4. `W-73.6`, `W-73.7`, `W-73.9` — scheduled earnings, bulk invite, country templates. Each is independent.

## 5. Migrations reserved

`V149` is taken on `dev-devashish`. This parent reserves:

| Version | Ticket | What |
|---|---|---|
| `V153`–`V159` | taken on `main` | `D-39`, `D-40`, `D-33` and krushna's `testing-bug-fix` (`V150`, `V151`); W-73 renumbered to `V160`+ on 2026-10-08 |
| `V160` | `W-73.1` | `core.tenant` logo and tagline |
| `V161` | `W-73.6` | `payroll.scheduled_earning`, `core.pay_input.source_ref` |
| `V162` | `W-73.9` | `reference.country_template` (India), `core.tenant_template_applied` |
| `V153` | `D-39` | EPF EDLI and admin switches split |
| `V154` | `D-40` | employee columns: gender, joining date, job title, employment type, probation end, notice period |
| `V163` | `W-73.3` | `core.employee_invitation.role_ids` |
| `V164` | `W-73.4` | `core.user_account.enabled`, only if missing |
| `V165` | `W-73.8` | `core.user_account.welcome_seen_at` |

Confirm against the tracker's migration column before use — the number on the branch is the
number, not the number here.

## 6. Verification of the parent

| Check | Expected |
|---|---|
| Sign in as the platform tenant's `platform-admin` on Azure dev | Lands on `/admin`; menu shows Tenants and Audit only |
| Create a tenant with an admin email | The admin gets one email; on accept they land on `/setup` with the welcome page |
| As that admin, add an employee with "Give portal access" and role `hr` | One save; the employee gets one email; after accept they hold `employee` + `hr` |
| Sign in as that employee | Lands on `/me`; header shows company name, logo or initials, their name and role |
