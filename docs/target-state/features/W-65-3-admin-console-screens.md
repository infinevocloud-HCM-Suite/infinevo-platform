# Feature: Admin console screens

| Field | Value |
|---|---|
| **Feature ID** | `W-65.3` · from ticket #85 · `PLAT-02` |
| **Promoted to** | `docs/target-state/features/W-65-3-admin-console-screens.md` — **`W-65-3` with hyphens**, never `W-65.3` |
| **Owner** | unassigned |
| **Apps touched** | `code/frontend/src/core` |
| **Related gaps** | none; nothing ported |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-27 |
| **Blocked by** | `W-45` (shell, service layer, lint rules), `W-65.1`, `W-65.2` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | platform staff provision and manage a tenant from the browser, and never open `psql` | 1 |
| Frontend area | `src/core/admin` | 1 |

Within cap.

---

## 1. Problem

**The endpoints exist; the onboarding tool does not.** `09-build-order.md:297`: done when a tenant can be provisioned without touching the database. After `W-65.1` and `W-65.2` every call exists and nothing calls them.

| Need | Endpoint on `main` or in a Ready spec |
|---|---|
| List, one tenant | `GET /api/v1/tenants`, `GET /{id}` — `W-65-1` §4 |
| Create | `POST /api/v1/tenants` — `TenantController.java:35-36` |
| Modules on and off | `PUT /api/v1/tenants/{id}/subscription/modules` — `SubscriptionController.java:45` |
| Suspend, reactivate | `PUT /api/v1/tenants/{id}/subscription/status` — `SubscriptionController.java:55` |
| Act as a user | `POST /api/v1/tenants/{id}/impersonations` — `W-65-2` §4 |
| Audit | `GET /api/v1/audit` — `AuditController.java:40-49`, one tenant at a time |

Nothing in `legacy/` is a source. Payroll's "manage organisation" is one company switching or deleting itself (`legacy/docs/FEATURE_MAP.md` §13).

## 2. Scope

**In scope**

- Tenant list screen, reached from the `core.tenants` navigation item (`W-65-1` §4), visible to platform staff only because the feed is entitlement-driven (`W-12.3`)
- Tenant detail: overview, module toggles, status, "act as" picker, audit tab
- Create-tenant form
- Impersonation banner in the shell header while a session is live, with "stop"
- Sending `X-Impersonation` from the API client while a session is live

**Out of scope**

- Any backend change. If a screen needs one, it is a defect in `W-65.1` or `W-65.2`
- Editing a tenant's name, country, timezone — no endpoint exists; a later ticket
- A cross-tenant audit view — decided against
- Users of a tenant beyond the "act as" picker. `W-24.2` invitations owns user management

## 3. Flow

```
[staff] --> /admin/tenants            --> tenantService.list()
        --> /admin/tenants/new        --> tenantService.create(body)      --> back to list
        --> /admin/tenants/:id        --> tenantService.get(id)
              modules switch          --> subscriptionService.setModules(id, modules)
              status                  --> subscriptionService.setStatus(id, status)
              act as                  --> impersonationService.open(id, userAccountId, reason)
                                          --> store: session --> client sends X-Impersonation
                                          --> navigation refetch (feed is now the target's)
              audit tab               --> auditService.search(params)   (during a session)
[banner: stop]                        --> impersonationService.close(sessionId) --> refetch
```

## 4. Backend changes

None.

## 5. Frontend changes

Follows the `W-45` contract: services over `apiClient`, slices registered from `src/core/index.js`, no `axios`, no storage, tokens from `theme.useToken()` (`W-45-frontend-shell.md` §5, §5b).

| File | Change |
|---|---|
| `src/core/admin/tenantService.js` | **new.** `createService('/v1/tenants')` spread, plus `subscription(id)`, `setModules(id, modules)`, `setStatus(id, status)` |
| `src/core/admin/impersonationService.js` | **new.** `open(tenantId, userAccountId, reason)`, `close(sessionId)` |
| `src/core/admin/auditService.js` | **new.** `search(params)` → `GET /v1/audit` |
| `src/core/admin/impersonationSlice.js` | **new.** `{ session: null | {sessionId, tenantId, tenantName, userLabel, expiresAt} }`, actions `started`, `stopped`. Registered in `src/core/index.js` `reducers` |
| `src/shared/api/client.js` | request interceptor: when the store holds a session, add `X-Impersonation`. On `403 IMPERSONATION_INVALID` dispatch `stopped` and `errorMsg` |
| `src/core/admin/TenantList.jsx` | **new.** Ant `Table`: name, status tag, modules tags, created. Infinevo row rendered with a "platform" tag and no actions. "New tenant" button |
| `src/core/admin/TenantCreate.jsx` | **new.** `Form`: name, country (default `IN`), timezone (default `Asia/Kolkata`), leave year start month (default 4), modules checkboxes. Field rules mirror `W-12-1` §4 |
| `src/core/admin/TenantDetail.jsx` | **new.** `Descriptions` overview; `Switch` per module with confirm on revoke (read-only mode follows, `W-12-1` §13 row 1); status `Select` with confirm on suspend; "Act as" email field and a reason field; when the overview reports zero user accounts the field is hidden and the button reads "Set up as admin" (a bootstrap session, `W-65-2` §4), after which the screen links to the invitation form so staff send the first admin's invitation; `Tabs`: overview, audit |
| `src/core/admin/AuditTab.jsx` | **new.** Filters entity, actor, from, to; paged table. Disabled with a one-line hint until a session for this tenant is live, since the endpoint reads the bound tenant |
| `src/shell/Header.jsx` | when `session` is set, an `Alert` banner: "Acting as {userLabel} in {tenantName} until {expiresAt}" with a stop button. The one shell file this ticket touches, in `W-45`'s header |
| `src/core/index.js` | `routes` gains the three below; `reducers` gains `impersonation` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/admin/tenants` | `TenantList` | inside `AppShell`; present only when the feed carries `core.tenants` |
| `/admin/tenants/new` | `TenantCreate` | same |
| `/admin/tenants/:id` | `TenantDetail` | same |

**Where the "act as" user list comes from.** `GET /api/v1/users` exists under `core.user.read` (`V020:49`) but reads the bound tenant. Staff are bound to Infinevo, so the picker cannot list a customer's users before a session starts. The detail screen therefore asks for a user's **email**, and `W-65.2`'s `POST` resolves it — recorded as a change to `W-65-2` §4: the request body accepts `userAccountId` **or** `email`. Once acting, the audit tab and everything else read the target tenant normally.

## 6. Database changes

None.

## 7. Tests

Vitest, the same runner `W-12.3` added to CI (`.github/workflows/ci.yml` frontend step).

| Type | File | Covers |
|---|---|---|
| Unit | `src/core/admin/tenantService.test.js` | each call hits the right path and method; no `axios` import |
| Unit | `src/core/admin/impersonationSlice.test.js` | `started` stores the session, `stopped` clears it |
| Unit | `src/shared/api/client.test.js` | header present iff a session is in the store; `IMPERSONATION_INVALID` clears it |
| Component | `src/core/admin/TenantDetail.test.jsx` | revoke and suspend ask for confirmation; "act as" calls `open` and dispatches `started`; the audit tab is disabled without a session |
| Component | `src/core/admin/TenantList.test.jsx` | the Infinevo row has no actions |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d
# browser: log in as staff@infinevo.local, open /admin/tenants
```

| Check | Expected |
|---|---|
| lint | zero warnings; the §5b rules pass on `src/core/admin/**` |
| tests | green |
| list | Acme, Globex, Infinevo; Infinevo tagged platform |
| create | a fourth tenant appears without `psql`; `core.list_tenants()` in `psql` agrees |
| modules | switching `HRMS` off on Globex shows the confirm; the feed for `admin@globex-full.local` loses the HRMS items on next login |
| act as | banner shows; the menu becomes Globex's; audit tab lists rows; stop restores the staff menu |

The create check is the ticket's definition of done (`09-build-order.md:297`).

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A session is left running in a tab | medium | Banner is in the header, unmissable; the server expires it in 30 minutes regardless |
| The header keeps being sent after the server rejected it | low | Interceptor clears the session on `IMPERSONATION_INVALID` |
| The screens grow user management | medium | Named in **Out of scope**; `W-24.2` owns it |
| Built before `W-45` lands and rebuilt after | high if started early | Blocked on `W-45` in the tracker; do not start it first |

## 10. Rollback

Frontend only. Revert the branch.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money`/`BigDecimal` | no money |
| Index | none |
| Expand / contract | nothing |
| No module references another module | `src/core/admin` imports `@shared/*` and `@shell/screens` only; the §5b lint rules enforce it. `Header.jsx` reads the slice through the store, not by importing `@core` |

## 12. Gap inventory

None overlap.

## 13. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | Where does the console live? | **`src/core/admin`**, not a fourth module. It is core capability for one tenant's users; the feed hides it from everyone else |
| 2 | How is the target user chosen? | **By email**, resolved server-side (`W-65-2` request body gains `email`). The user list of another tenant is not readable before a session |
| 3 | Audit tab before acting? | **Disabled with a hint.** Better than an empty table that looks like "no changes" |
