# Feature: Support impersonation

| Field | Value |
|---|---|
| **Feature ID** | `W-65.2` · from ticket #85 · `PLAT-02` |
| **Promoted to** | `docs/target-state/features/W-65-2-support-impersonation.md` — **`W-65-2` with hyphens**, never `W-65.2` |
| **Owner** | devashis |
| **Apps touched** | `code/backend/shared`, `code/backend/migration` |
| **Related gaps** | none in `GAP_INVENTORY.md`; the frozen apps have no impersonation |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-27 |
| **Blocked by** | `W-65.1` — the Infinevo tenant and `PlatformTenant` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `shared` | 1 |
| Flyway migration | two scripts — `reference` action, `core` table — aggregate exception, same shape as `W-12.1` | 1 — exception granted 2026-09-27 |
| Externally testable behaviour | platform staff open a time-boxed session and act inside a customer tenant as one of its users, and every write says so | 1 |
| Frontend area | none | 1 |

Within cap with the migration exception. The two scripts touch different schemas and the README forbids mixing them in one file.

---

## 1. Problem

**Support cannot see what the customer sees.**

- A caller is bound only to a tenant they are a member of — `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantContextFilter.java:98-107`. Platform staff are members of the Infinevo tenant alone (`W-65.1`)
- Permissions are the caller's own, keyed by tenant and user — `code/backend/shared/src/main/java/com/infinevo/shared/authz/PermissionService.java:56-70`
- The audit row's actor is the JWT subject — `code/backend/shared/src/main/java/com/infinevo/shared/audit/AuditWriter.java:221-236`. If staff could act in a tenant today, the trail would say the customer did it
- Nothing like it exists in `legacy/` — each app is one company (`legacy/docs/FEATURE_MAP.md` §2, §13)

**Founder decision 2026-09-27:** application-side act-as, not Keycloak token exchange. No Keycloak feature flag, no second token; the platform decides and records it.

## 2. Scope

**In scope**

- `POST /api/v1/tenants/{id}/impersonations` — staff open a session as one user account of that tenant, with a reason, valid 30 minutes
- Header `X-Impersonation: <session id>` on any later request binds the target tenant and evaluates permissions as the target user
- Every audit row written during the session names both people
- `DELETE /api/v1/impersonations/{id}` ends it early
- A new action `core.tenant.impersonate`, held by `platform-admin`
- **A bootstrap session for a tenant that has no users yet.** A brand-new tenant has nobody to act as and nobody to send its first invitation (`W-24-2` sends invitations from a logged-in user of the tenant). Staff open a session with no target; it carries the actions of the tenant's seeded `tenant-admin` role, and its only real use is to send the first invitation

**Out of scope**

- Screens — `W-65.3` sends the header
- Reading the audit trail across tenants — decided against (`W-65-1` §13 row 4)
- Impersonating platform staff, or a user of the Infinevo tenant. Refused
- Token exchange, Keycloak admin API, offline sessions

## 3. Flow

```
[staff, bound to Infinevo] --> POST /tenants/{T}/impersonations {userAccountId, reason}
   --> ImpersonationController --> ImpersonationService
   --> core.open_impersonation(T, staff, target, reason)   SECURITY DEFINER
   --> core.impersonation_session (tenant_id = T)  --> 201 {sessionId, expiresAt}

[staff, X-Impersonation: S] --> TenantContextFilter
   --> core.resolve_impersonation(S, staff)  SECURITY DEFINER: live, not expired, opened by this staff
   --> TenantContext.set(T) + ActingAs.set(staff, target)
   --> PermissionService reads actions of target in T
   --> AuditWriter.actor = staff id, label "staff@... as user@..."
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `shared/.../impersonation/ImpersonationController.java` | new — `POST`, `DELETE`; `@RequiresAction("core.tenant.impersonate")` plus `PlatformTenant.requirePlatformTenant()` |
| Service | `shared/.../impersonation/ImpersonationService.java` | new — calls the two functions; refuses a target in the Infinevo tenant |
| Context | `shared/.../impersonation/ActingAs.java` | new thread-local record `(platformUserId, targetUserAccountId, sessionId)`, cleared where `TenantContext.clear()` is (`TenantContextFilter.java`, finally block) |
| Filter | `shared/.../tenant/TenantContextFilter.java` | new branch before the membership check at `:98`: header present → resolve the session, bind its tenant, set `ActingAs`. Header present and invalid → `403 IMPERSONATION_INVALID`. `X-Tenant-Id` and `X-Impersonation` together → `400` |
| Authz | `shared/.../authz/PermissionService.java` | `currentUserId()` (`:56-70` path) returns the target user account when `ActingAs` is set; the cache key is therefore the target's, unchanged shape |
| Audit | `shared/.../audit/AuditWriter.java` | `resolveActor()` (`:221`) returns `actor_user_id = staff`, `actor_label = "<staff email> as <target email>"` when `ActingAs` is set |
| Error | `shared/.../error/ApiError.java` | add `IMPERSONATION_INVALID` |
| Logging | `MdcLoggingContext` | add `acting_as` key so every log line in the session carries the staff id |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/tenants/{id}/impersonations` | `userAccountId` **or** `email` (`email` resolved inside `open_impersonation` against `core.user_account`, unknown → `404 USER_NOT_FOUND`; **both absent = bootstrap**, allowed only while the tenant has no user account), `reason` (required, ≤ 200 chars) | `201` `{sessionId, tenantId, userAccountId, userEmail, expiresAt}` | `core.tenant.impersonate`, Infinevo tenant bound |
| DELETE | `/api/v1/impersonations/{id}` | — | `204` | same; only the staff who opened it |

**Why a session id and not two raw headers.** Two headers (`X-Act-As-Tenant`, `X-Act-As-User`) would let any request start acting, with no start, no end, no reason and no time-box. One row per session gives all four and one place to audit. The header choice the founder made is kept: it is an application header, not a token.

**Permission model while acting.** The target user's actions apply, never the staff member's. Staff cannot do in a tenant what its own admin cannot. The exception is none: `core.tenant.provision` is not held by any customer role, so a staff member acting as a customer cannot reach the platform endpoints, which is right.

**Bootstrap session.** `POST` with neither `userAccountId` nor `email` is accepted **only while the tenant has zero rows in `core.user_account`**; otherwise `400 VALIDATION_FAILED`. `resolve_impersonation` returns the session's action codes directly: the target user's `role_action` set, or the `tenant-admin` system role's set (`core/V022__role_action.sql:82-91`) for a bootstrap session. `PermissionService` uses those codes while `ActingAs` is set, so `ActionSource` in `core` is untouched and `shared` gains no dependency. The audit label is `<staff email> as tenant-admin (bootstrap)`. The first invitation itself is `W-24.2`'s endpoint, called during the session; this ticket does not touch invitations.

**The functions are the door.** `core.impersonation_session` is under RLS with `tenant_id = T`; staff bound to Infinevo cannot insert it directly. `open_impersonation` writes it, `resolve_impersonation` reads it, `close_impersonation` sets `ended_at`. All three `SECURITY DEFINER`, `search_path = pg_catalog, pg_temp`, `GRANT EXECUTE TO app_user`, in the form of `core/V034__subscription.sql:63-142`.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `reference/V083__action_impersonate.sql` | `reference.action` — one row `core.tenant.impersonate`, "Act as a user of a customer tenant. Platform staff only"; then `SELECT core.seed_system_roles('00000000-0000-0000-0000-000000000001')` so `platform-admin` in the Infinevo tenant gains it | no (reference, by design `V020:4-6`) | additive |
| `core/V084__impersonation_session.sql` | `core.impersonation_session` + three functions | yes | additive |

`core.impersonation_session`: `id uuid` · `tenant_id uuid NOT NULL REFERENCES core.tenant(tenant_id)` — the **target** tenant · `platform_user_id uuid NOT NULL` — the staff Keycloak subject · `target_user_account_id uuid NULL REFERENCES core.user_account(id)` — `NULL` only for a bootstrap session · `reason varchar(200) NOT NULL` · `started_at timestamptz NOT NULL DEFAULT now()` · `expires_at timestamptz NOT NULL` · `ended_at timestamptz NULL` · four audit columns.

- [x] `tenant_id` present, RLS `tenant_isolation` in the exact `CASE` form of `V001:26-33`
- [x] Index `(tenant_id, id)` and `(tenant_id, platform_user_id, started_at DESC)` (DEBT-018)
- [x] Money columns — none
- [x] Expand / contract — new table only
- [x] `REVOKE UPDATE, DELETE ON core.impersonation_session FROM app_user`, as `V008__audit_log.sql:41-47` does; `ended_at` is set only through the function

The table is itself part of the audit trail: it says who acted as whom, when and why. `@Audited` on it is not needed; the row is the record.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `shared/.../impersonation/ImpersonationServiceTest.java` | target in the Infinevo tenant refused; reason blank refused; `expires_at = started_at + 30 min`; no target on a tenant that already has a user account is `400` |
| Integration | `shared/.../impersonation/BootstrapSessionIT.java` | provision a fresh tenant; a no-target session opens; with the header, `GET /api/v1/navigation` returns the tenant-admin feed and the audit label reads `staff@infinevo.local as tenant-admin (bootstrap)`; after one `core.user_account` row exists, a second no-target session is `400` |
| Unit | `shared/.../audit/AuditWriterTest.java` | with `ActingAs` set, `actor_user_id` is the staff id and the label carries both emails; without, unchanged |
| Integration | `shared/.../impersonation/ImpersonationIT.java` | staff opens a session as `emp@globex-full.local`; with the header, `GET /api/v1/navigation` returns Globex's employee feed; `PUT` on an employee is `403` as it would be for that employee; the same `PUT` as `admin@globex-full.local` succeeds and the audit row reads `staff@infinevo.local as admin@globex-full.local` |
| Integration | `shared/.../impersonation/ImpersonationGuardIT.java` | `admin.acme` gets `403` on `POST`; an expired session gets `403 IMPERSONATION_INVALID`; a session opened by staff A used by staff B gets `403`; `DELETE` then reuse gets `403`; header plus `X-Tenant-Id` is `400` |
| Integration | `shared/.../impersonation/ImpersonationRlsIT.java` | as `app_user` bound to Infinevo, direct `INSERT` into `core.impersonation_session` for Globex is refused; `open_impersonation` succeeds |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. Filter tests build on `TenantContextFilterTest` in `shared`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up --build migrate
bash infra/docker/seed/seed.sh
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT code FROM reference.action WHERE code='core.tenant.impersonate';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.impersonation_session'::regclass;"
cd code/backend && mvn -q verify
node .claude/hooks/check-done.mjs W-65.2
```

| Check | Expected |
|---|---|
| action | one row |
| RLS | `t` |
| Suite | green, no skips; `ImpersonationIT` asserts the audit label |
| done-check | 5/5 |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Staff act with their own permissions inside a customer tenant | medium | `PermissionService` reads the target's actions; `ImpersonationIT` proves the employee-level `403` |
| A session outlives the incident | medium | 30-minute expiry in the row, checked by the function on every request, not by the client |
| The audit trail hides the staff member | high without this ticket | `AuditWriterTest` and `ImpersonationIT` assert the label |
| `ActingAs` leaks across pooled threads | medium | Cleared in the same `finally` as `TenantContext` (`TenantContextFilter.java`); a unit test asserts both are empty after the filter |
| The header is honoured on the exempt paths | low | The branch sits inside `doFilterInternal`, after `shouldNotFilter` (`:71-74`) |

## 10. Rollback

Nothing is deployed. Additive, forward-only.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table | `core.impersonation_session`, tenant is the target |
| Flyway only | two scripts, exception granted |
| `Money`/`BigDecimal` | no money column |
| Index on `tenant_id` + lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new table only |
| Cross-tenant writes | `SECURITY DEFINER` functions; no RLS bypass |
| No module references another module | `shared` only; it depends on nothing, and `core` calls nothing new |

## 12. Gap inventory

None overlap. Nothing is ported.

## 13. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | Token exchange or application header? | **Application header** — founder, 2026-09-27 |
| 2 | Two raw headers or a session id? | **Session id.** Same mechanism, plus a start, an end, a reason and a time-box. Recorded here as the one refinement of the founder's choice |
| 3 | Whose permissions apply? | **The target user's.** Support sees what the customer sees, which is the point |
| 4 | Session length | **30 minutes**, fixed. A property can follow if support asks |
| 5 | How does a new tenant get its first login? | **Bootstrap session** — founder, 2026-09-27. Customers cannot sign up (`W-12-1` §13 row 3) and `W-24.2` invitations are sent by a user of the tenant, so a tenant with no users had no sender. Staff act as `tenant-admin` only while the tenant is empty, send the first invitation, and the door closes once one account exists |

## 15. As built — review pass (2026-10-01)

| Topic | As built |
|---|---|
| The three integration tests | `ImpersonationIT`, `ImpersonationGuardIT` and `BootstrapSessionIT` exist, in `core/src/test/.../tenant/` beside `ImpersonationRlsIT` (not in `shared`: they need the navigation feed, the employee endpoint and the role seed, which only `core` has). They run over HTTP through the shipped filter chain, the real permission check, the real database functions and real row-level security; a session is opened by the staff member's own request. The permission cache is `InMemoryCacheService`, so they need Docker for PostgreSQL only |
| What they prove | Acting as an employee the menu is the employee feed, and a `PUT` on an employee is `403`; acting as the tenant admin it succeeds and the audit row reads `staff@infinevo.local as admin@globex-full.local`, with the staff member's id as actor. A bootstrap session serves the tenant-admin feed from tenant-admin's actions and is audited as `… as tenant-admin (bootstrap)`; once the tenant has a user account a second one is `400`. An expired session, a session used by another staff member, and a session after `DELETE` are `403 IMPERSONATION_INVALID`; ending another staff member's session is `404`; both headers together are `400`; the platform tenant, a missing tenant, a blank reason and an unknown user are clear refusals. The lifetime is read back from the stored row: thirty minutes |
| A bug the mocks could not see | `ImpersonationService.openImpersonation` and `closeImpersonation` ran with the staff member's tenant bound and **no transaction**, and the tenant-binding datasource refuses a connection in auto-commit mode: every attempt to open or end a session was a server error. Both are now `@Transactional`. `resolve` runs in the filter before a tenant is bound and needs none |
| A customer holding the action | A customer role cannot carry `core.tenant.impersonate`: the `V083` trigger drops the grant, so the role ends up with no action and the user is refused `403 FORBIDDEN` on both endpoints (`ImpersonationGuardIT`). (An earlier version of this note said the role could carry it and that the platform-tenant check refused it; that was wrong.) `core.tenant.provision` had no such guard until `V136`, `RoleServiceImpl` and the subscription endpoints (`PlatformOnlyActionIT`, `PlatformOnlyActionGuardIT`) |
| The expiry unit test | `ImpersonationServiceTest` no longer claims to prove the thirty minutes: the database function sets it, and the integration tests read it back |
| `GET /api/v1/me` while acting | Answered `500`: it looked up the staff member in the customer tenant, where the sync filter (rightly) never writes them. Now it answers with the user being acted as, which is what the customer would see; a bootstrap session, which has no such user, answers with the staff member's own token claims. Decided at the merge review, 2026-10-02 (`MeController`, `MeControllerTest`) |
