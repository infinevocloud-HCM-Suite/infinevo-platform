# Feature: Tenant list and overview for platform staff

| Field | Value |
|---|---|
| **Feature ID** | `W-65.1` · from ticket #85 · `PLAT-02` |
| **Promoted to** | `docs/target-state/features/W-65-1-tenant-list.md` — **`W-65-1` with hyphens**, never `W-65.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/shared` (one class), `code/backend/migration`, `infra/docker/seed`, `infra/keycloak` |
| **Related gaps** | BUG-002, DEBT-018 (both honoured, neither fixed here) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-27 |
| **Blocked by** | nothing — `W-12.1` is on `main` (`b7d03ec`) |
| **Split** | `W-65` was one ticket crossing every axis of the size cap. It is now `W-65.1` (this), `W-65.2` impersonation, `W-65.3` screens |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` (plus one config class in `shared` that `W-65.2` reuses) | 1 |
| Flyway migration | one `core` script — the Infinevo tenant and two read functions | 1 |
| Externally testable behaviour | a platform user lists every tenant with its subscription; a customer user cannot | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

**Platform staff have nowhere to stand, and nothing to list.**

- Every request is bound to one tenant from the token or `X-Tenant-Id`, and the caller must be a member of it — `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantContextFilter.java:98-121`
- The only tenant endpoint is `POST` — `code/backend/core/src/main/java/com/infinevo/core/tenant/TenantController.java:26-36`. There is no `GET`, so after provisioning a tenant nobody can see it without `psql`
- `GET /tenants/{id}/subscription` refuses any id other than the bound tenant — `code/backend/core/src/main/java/com/infinevo/core/subscription/SubscriptionController.java:35-40`
- RLS on `core.tenant` hides every row but the bound one — `code/backend/migration/src/main/resources/db/migration/core/V001__tenant.sql:24-33`
- The seeded `platform-admin` role exists in every tenant but no user holds it — `infra/docker/seed/03-user-roles.sql:26-27`

`09-build-order.md:297` says W-65 is done when "a tenant can be provisioned without touching the database". Today it can be created without the database and then only *seen* through it.

**Founder decision 2026-09-27:** platform staff are members of one **Infinevo-owned tenant**, not a Keycloak realm role. They log in like anyone else, are bound to that tenant, and hold `platform-admin` there. Nothing in the binding filter changes.

## 2. Scope

**In scope**

- The Infinevo tenant, created by migration with a fixed id, and `platform-admin` seeded in it
- `GET /api/v1/tenants` — every tenant with name, status, modules, created date
- `GET /api/v1/tenants/{id}` — one tenant with its subscription, the same shape `W-65.3` renders
- Both read across RLS through `SECURITY DEFINER` functions, the pattern `W-12.1` set
- A dev seed user who is platform staff

**Out of scope**

- Impersonation — `W-65.2`. Acting inside a customer tenant is that ticket
- Screens — `W-65.3`
- Any change to `POST /tenants` or the two subscription `PUT`s; they already work cross-tenant from the Infinevo tenant (`W-12-1-subscription.md` §4, functions)
- A cross-tenant audit view — founder decision 2026-09-27: audit is read one tenant at a time, through the existing endpoint, while impersonating
- Search, paging, sorting on the list. Fewer than a hundred tenants for years (`D-19` scale)

## 3. Flow

```
[platform staff, bound to Infinevo tenant]
   --> GET /api/v1/tenants        --> TenantController --> TenantQueryService
   --> core.list_tenants()        SECURITY DEFINER, owner migration_user
   --> core.tenant ⋈ core.subscription ⋈ core.subscription_module

[platform staff] --> GET /api/v1/tenants/{id} --> core.get_tenant_overview(id)
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../tenant/TenantController.java` | add `GET` and `GET /{id}`, both `@RequiresAction("core.tenant.provision")` |
| Service | `core/.../tenant/TenantQueryService.java` | new — `JdbcTemplate` over the two functions, like `TenantServiceImpl.java:94` |
| DTO | `core/.../tenant/TenantOverview.java` | new record: `tenantId`, `name`, `countryCode`, `timezone`, `status`, `modules`, `createdAt`, `currentPeriodEnd`, `userCount` (rows in `core.user_account`; `W-65.2` and `W-65.3` use zero to mean "bootstrap") |
| Config | `shared/.../tenant/PlatformTenant.java` | new — property `infinevo.platform.tenant-id`, default `00000000-0000-0000-0000-000000000001`; `isPlatformTenant(UUID)`, `requirePlatformTenant()` |
| Navigation | `core/.../navigation/NavigationCatalogue.java:82-83` | add `ItemDefinition("core.tenants", "nav.tenants", "/admin/tenants", "/api/v1/tenants", null, "core.tenant.provision")`. Only `platform-admin` holds the action, so only staff see the item; `W-65.3` renders it. `NavigationMatchesEnforcementIT` picks it up |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/tenants` | — | `200` list of `TenantOverview` | `core.tenant.provision`, **and** the bound tenant is the Infinevo tenant |
| GET | `/api/v1/tenants/{id}` | — | `200` `TenantOverview`, `404 TENANT_NOT_FOUND` | same |

**The gate is the action plus the tenant.** `core.tenant.provision` is "platform staff only; never granted to a customer role" (`reference/V020__action.sql:43-44`) and `tenant-admin` excludes it (`core/V022__role_action.sql:86-91`). That is already the rule for `POST`. The second check, `PlatformTenant.requirePlatformTenant()`, is belt and braces: a customer who somehow gains the action still gets `403 FORBIDDEN`, because the functions below are the only door and the service refuses to call them from any other tenant. **Do not** put the check in the SQL; a function cannot see who is bound.

**The Infinevo tenant is a tenant like any other.** It has a `core.subscription` row (`status=active`, no modules) so `EntitlementReadService.modulesOf` and the navigation feed do not special-case it, and `core.seed_system_roles` runs for it so `platform-admin` exists there. It never holds `HRMS` or `PAYROLL`; the service refuses `set_subscription_modules` on it.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V082__platform_tenant.sql` | inserts one `core.tenant` and one `core.subscription` row; two `SECURITY DEFINER` read functions | existing RLS | additive |

`V082` is the first free number after the reserved blocks (`DEV-TRACKER.md` § Assignments, `V042`–`V081`). Use the lane's number if the ticket is assigned into a lane with a block.

The script:

- Inserts tenant `00000000-0000-0000-0000-000000000001`, name `Infinevo`, `created_by='migration'`, `ON CONFLICT (tenant_id) DO NOTHING`, and its `core.subscription` row. Runs as `migration_user`, which RLS does not bind (`migration/README.md` § row-level security)
- `SELECT core.seed_system_roles('00000000-0000-0000-0000-000000000001');`
- `core.list_tenants()` → `TABLE (tenant_id, name, country_code, timezone, status, modules text[], created_at, current_period_end, user_count)`, ordered by name; `modules` is the non-revoked set
- `core.get_tenant_overview(p_tenant_id uuid)` → the same row for one tenant, zero rows when unknown
- Both `LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, pg_temp`, every name schema-qualified, `REVOKE EXECUTE FROM PUBLIC; GRANT EXECUTE TO app_user`, in the form of `core/V002__user_tenant.sql:30-40`

- [x] `tenant_id` — creates no table
- [x] Index on `tenant_id` — none new; the join uses the existing unique indexes in `V034__subscription.sql`
- [x] Money columns — none
- [x] Expand / contract — inserts and functions only

**The dev seed changes.** `infra/docker/seed/03-user-roles.sql` gains `staff@infinevo.local`, keycloak id `c0000000-0000-0000-0000-000000000001`, in the Infinevo tenant with `platform-admin`, and `02-user-tenants.sql` the membership row. The comment at `:26-27` ("nobody gets platform-admin") is rewritten: one person does, and they are not a customer. The Keycloak dev realm carries the matching user under `infra/keycloak/`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../tenant/TenantQueryServiceTest.java` | bound to a customer tenant, both reads throw `PermissionDeniedException` before any SQL runs; `set_subscription_modules` on the Infinevo tenant is refused |
| Integration | `core/.../tenant/TenantListIT.java` | as `staff` bound to the Infinevo tenant, `GET /tenants` returns Acme (`PAYROLL`), Globex (`HRMS`, `PAYROLL`) and Infinevo (none); `GET /tenants/{acme}` matches; unknown id is `404` |
| Integration | `core/.../tenant/TenantListGuardIT.java` | `admin.acme` (holds `core.tenant.read`, not `provision`) gets `403` on both; a test user granted `core.tenant.provision` inside Acme still gets `403` — the tenant check |
| Integration | `core/.../tenant/PlatformTenantRlsIT.java` | as `app_user` bound to Acme, `SELECT * FROM core.tenant` returns one row and `SELECT * FROM core.list_tenants()` returns all — the function is the only door |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
bash infra/docker/seed/seed.sh
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT name, status, modules FROM core.list_tenants();"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT code FROM core.role WHERE tenant_id='00000000-0000-0000-0000-000000000001' AND code='platform-admin';"
cd code/backend && mvn -q verify
node .claude/hooks/check-done.mjs W-65.1
```

| Check | Expected |
|---|---|
| `list_tenants` | three rows: Acme `{PAYROLL}`, Globex `{HRMS,PAYROLL}`, Infinevo `{}` |
| role | one row, `platform-admin` |
| Suite | green, no skips |
| done-check | 5/5 |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The tenant check is forgotten on a later platform endpoint | medium | `PlatformTenant` is one class in `shared`; `W-65.2` reuses it. `TenantListGuardIT` is the template |
| The Infinevo tenant shows up in customer-facing counts or reports | low | It has no modules; anything that iterates tenants by module skips it. `W-65.3` renders it as a distinct row |
| The fixed UUID collides with a real tenant | none | `provision_tenant` generates random UUIDs; the all-zero-then-one form is reserved by this spec |
| A migration inserts data | low | Precedent: `V020` and `V022` seed reference rows and system roles. Idempotent on conflict |

## 10. Rollback

Nothing is deployed. Additive, forward-only — `migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table | creates no table |
| Flyway only | one script |
| `Money`/`BigDecimal` | no money column |
| Index on `tenant_id` + lookup columns | none new |
| Expand / contract | additive |
| Cross-tenant reads (`12-core-contracts.md` §6 decision 4) | `SECURITY DEFINER` functions; no RLS bypass for `app_user` |
| No module references another module | `core` only; `PlatformTenant` lives in `shared` because `W-65.2`'s filter needs it |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 no tenant column in HRMS | **Discounted** — new capability, nothing ported |
| DEBT-018 tenant indexes | **Honoured** — no new table |

## 13. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | Who is platform staff? | **Members of the Infinevo tenant holding `platform-admin`** — founder, 2026-09-27. Not a realm role: the filter and `RequiresAction` already do the work |
| 2 | New action code for the list? | **No.** `core.tenant.provision` already means "platform staff only". A separate read action would be a second thing to forget |
| 3 | Where does the Infinevo tenant come from in production? | **The migration.** There is no earlier moment: `POST /tenants` needs a platform user, who needs a tenant to belong to |
| 4 | Audit across tenants? | **No** — founder, 2026-09-27. One tenant at a time via `GET /api/v1/audit` while impersonating (`W-65.2`) |
