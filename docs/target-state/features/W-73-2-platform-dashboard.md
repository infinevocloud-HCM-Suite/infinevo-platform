# Feature: Platform dashboard — the home page for platform staff

| Field | Value |
|---|---|
| **Feature ID** | `W-73.2` · from `W-73` |
| **Promoted to** | `docs/target-state/features/W-73-2-platform-dashboard.md` |
| **Owner** | claude (founder) |
| **Apps touched** | `code/backend/core` (tenant), `code/frontend/src/core/admin` |
| **Related gaps** | `D-33` (scope), `D-35` (home path), `D-42` (admin email) |
| **Status** | Draft |
| **Written by** | claude for the founder, 2026-10-07 |
| **Blocked by** | `D-33`, `D-35`, `D-42` on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | `V168` (one function, no table) — see §11 | 1 |
| Externally testable behaviour | platform staff land on one page that says how many tenants exist, which are waiting for their admin, and lets them create one | 1 |
| Frontend area | `core/admin` | 1 |

## 1. Problem

| Fact | Evidence |
|---|---|
| Platform staff land on the first feed path, a customer screen | `AppShell.jsx:42,136` |
| The tenant list exists (`W-65.1`, `W-65.3`) but there is no overview and no "what needs me" | `core/admin/` has list and create only |
| Tenant list returns rows, not counts by status | `TenantController.java:51` |
| After `D-42` a tenant has an admin invitation state nobody can see in one place | `D-42` |

## 2. Scope

**In scope**

- `GET /api/v1/tenants/summary`: counts by status (`ACTIVE`, `SUSPENDED`, …), tenants created in the last 30 days, tenants whose admin invitation is `PENDING` or `EXPIRED`
- `/admin` page: four tiles (total, active, suspended, waiting for admin), a "Needs attention" table (admin invitation pending / expired with Resend), "Recent tenants" (last 10, link to the tenant page), a **Create tenant** button
- Menu: `core.admin.home` → `/admin`, first item of the platform tenant's feed (`D-35` makes it the home path)

**Out of scope**

- Usage or billing numbers; employee counts per tenant (needs a cross-tenant read; later)
- Charts

## 3. Flow

```
platform-admin signs in --> / --> /admin (homePath from D-35)
/admin --> GET /api/v1/tenants/summary --> tiles + tables
       --> Create tenant --> /admin/tenants/new (W-65.3, with admin email from D-42)
       --> Resend --> POST /api/v1/tenants/{id}/admin-invitation/resend
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../tenant/TenantController.java` | `GET /summary` (`core.tenant.provision`, platform tenant only — the `V136` guard already limits who holds it); `POST /{id}/admin-invitation/resend` reuses the user-invitation resend in the target tenant under the platform's act-as context (`W-65.2`) |
| Service | `TenantService` | `summary()`: one query over `core.tenant` grouped by status; one over `core.user_invitation` joined to the tenant's `tenant-admin` role for pending/expired |
| DTO | `TenantSummaryResponse(total, byStatus, recent[], waitingForAdmin[])` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/tenants/summary` | — | `{total, byStatus{}, recent[{id,name,createdAt,status}], waitingForAdmin[{id,name,adminEmail,invitationStatus,expiresAt}]}` | `core.tenant.provision` |
| POST | `/api/v1/tenants/{id}/admin-invitation/resend` | — | `204` | `core.tenant.provision` |

## 5. Frontend changes

| File | Change |
|---|---|
| `core/admin/PlatformHome.jsx` (new) | tiles, two tables, Create button |
| `core/admin/tenantService.js` | `getSummary()`, `resendAdminInvitation(id)` |
| `shell/navigation/navLabels.js` | `nav.admin.home` → "Dashboard" |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/admin` | `PlatformHome` | feed item `core.admin.home` |

## 6. Database changes

`core/V168__list_waiting_admin_invitations.sql`: one `SECURITY DEFINER` function, `core.list_waiting_admin_invitations()`, no table (see §11). Counts and recent tenants come from the existing `core.list_tenants()`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `TenantSummaryIT` | counts match seeded tenants; a customer tenant's `platform-admin` gets 403 |
| Unit | `PlatformHome.test.jsx` | tiles render counts; Resend calls the service; empty state when no tenants |

## 8. Verification

| Check | Expected |
|---|---|
| Sign in to the platform tenant on Azure dev | `/admin` with the four tiles; menu shows Dashboard, Tenants, Audit |
| Create a tenant with an admin email | It appears under "Needs attention" until the admin accepts |
| Resend | A second email arrives; `expiresAt` moves |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Summary query crosses tenants | certain | same path as the tenant list (`W-65.1`); covered by `W-58` isolation tests |

## 10. Rollback

Remove the menu item; `D-35` then falls back to `/admin/tenants`.

## 11. Amendments while building (2026-10-08)

| Spec said | Code says | Built |
|---|---|---|
| §6 "None" — the list's cross-tenant read already covers pending / expired | `core.list_tenants()` reports only `ACCEPTED`, `PENDING` (live) or `NONE`; an expired invitation reads `NONE` and no `expires_at` is returned (`core/V159__list_tenants_admin_invitation.sql:10-13,66-80`). `core.user_invitation` is behind RLS (`core/V117__user_invitation.sql`), so the platform tenant cannot read it directly | `V168` adds `core.list_waiting_admin_invitations()`: per tenant with no accepted `tenant-admin` invitation, its latest non-superseded one when `PENDING` or `EXPIRED`, with id, email and `expires_at` |
| §4 "one query over `core.tenant` grouped by status" | `core.tenant` and `core.subscription` are tenant-scoped; the only cross-tenant read is `core.list_tenants()` (`TenantQueryService.java:87`) | `summary()` aggregates `core.list_tenants()` in Java. The platform tenant itself is left out of every count and table: it is not a customer |
| §4 resend "reuses the user-invitation resend" | `resendUserInvitation` refuses an expired invitation (`InvitationServiceImpl.java:180-184`) | `PENDING`: the existing resend, bound to the target tenant as `D-42` provisioning is (`TenantServiceImpl.java:213`). `EXPIRED`: a fresh `tenant-admin` invitation to the same email. No waiting invitation: `409 CONFLICT` |
| §2 "created in the last 30 days" not in the §4 contract | — | Reply carries `createdLast30Days` |
| §5 changes only `core/admin` and `navLabels.js` | `AppShell.findSelectedKey` picks the first item whose path prefixes the URL (`AppShell.jsx:224-235`), so `/admin` would claim `/admin/tenants` | `findSelectedKey` picks the longest matching path, on a segment boundary |
| §2 menu: first item | `D-35` home is `/admin/tenants` (`NavigationService.java:156`) | `core.admin.home` is the first catalogue item; platform home is `/admin`, falling back to `/admin/tenants` then the first leaf |
