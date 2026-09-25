# Feature: Permission check and shared cache

| Field | Value |
|---|---|
| **Feature ID** | `W-11.2` · from ticket #12 · `CORE-03` |
| **Promoted to** | `docs/target-state/features/W-11-2-permission-check.md` on branch `W-11-2-permission-check` — **`W-11-2` with hyphens**, never `W-11.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/shared` only — `W-53` already shipped Redis, its Compose service and its Bicep module |
| **Related gaps** | DEBT-018 (not applicable), replica-scaling defect (fixed) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-11.1` — there must be roles and actions to check |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `shared` | 1 |
| Flyway migration | **none** — creates no table | 1 |
| Externally testable behaviour | a role change takes effect on every replica, and an unheld action returns `403` | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

The frozen permission check works and cannot be run twice.

- Payroll caches a user's actions in a `ConcurrentHashMap<String, Set<String>>` keyed by `userId::orgId` — `legacy/Payroll-Bend-SBoot/.../serviceimpl/AuthzServiceImpl.java:29`
- It is populated on first call and invalidated by hand when a role's actions change — `RoleActionController.java:94`
- The map lives **inside the process**. A second replica has its own copy, and the invalidation call reaches only the replica that served it

That is the whole of `09-build-order.md:183`'s warning: *"the cache must be shared. The
current in-process one is why the system cannot run two replicas."* The evidence confirms the
map exists and is exactly as described. Azure Container Apps scales by adding replicas
(`D-10`), so the defect stops the platform doing the one thing the hosting choice was made
for.

HRMS has the opposite problem: no cache, and no action check either. It decides by role name
against a URL prefix — `legacy/HRMS_Backend/.../config/SecurityConfig.java:39-43` — so there
is nothing to invalidate and nothing fine-grained to enforce.

## 2. Scope

**In scope**

- A method-level permission check: `@RequiresAction("employee.read")` on a controller method
- Resolution of a user's action set for the bound tenant, from `W-11.1`'s tables
- A **shared** cache keyed by tenant and user, built on `shared/.../cache/RedisCacheService.java` — **which already exists on `main` from `W-53`**
- Invalidation that reaches every replica, driven by the data changing rather than by a controller remembering to call it

**Out of scope**

- The role and action tables — `W-11.1`
- Module entitlement — `W-12.2`. Holding an action and having bought the module are different refusals
- Keycloak realm roles — `W-10`
- Caching anything but permissions

## 3. Flow

```
[request] --> TenantContextFilter (W-08) binds tenant
   --> [@RequiresAction interceptor] --> [PermissionService]
        --> Redis hit?  --> Set<action>
        --> Redis miss? --> W-11.1 PermissionReadService --> Redis
   --> action held? proceed : 403

[role_action or user_role changes] --> version bump in Redis --> every replica misses --> reloads
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Annotation | `shared/.../authz/RequiresAction.java` | new |
| Interceptor | `shared/.../authz/RequiresActionAspect.java` | new |
| Service | `shared/.../authz/PermissionService.java` | new |
| Cache | `shared/.../authz/PermissionCache.java` | new — a thin wrapper over `RedisCacheService`, adding the per-tenant version key |
| Config | `shared/.../authz/AuthzAutoConfiguration.java` | new, following `TenantBindingAutoConfiguration` |
| Cache | `shared/.../cache/RedisCacheService.java`, `RedisConfig.java` | **exist already (`W-53`)** — reused, not modified |
| Compose | `infra/docker/compose.yml` | **no change** — `W-53` added the Redis service |
| Bicep | `infra/azure/modules/redis.bicep` | **no change** — `W-53` added it |

**Invalidation by version, not by eviction.** Each tenant holds a version number in Redis.
A permission set is cached under `perm:{tenant}:{user}:{version}`. Changing a role bumps the
tenant's version, so every replica's next lookup misses and reloads, and no replica needs to
be told anything. The frozen design's manual `invalidateUserOrgCache()` call —
`RoleActionController.java:94` — is what cannot work across replicas, and this removes the
need for it rather than distributing it.

**API contract**

No new endpoint. This ticket changes how existing and future endpoints are guarded.

## 5. Frontend changes

None. The frontend learns what a user may do from `W-12.3`'s navigation feed, not from here.

## 6. Database changes

**None. This ticket creates no table and ships no migration.**

- [x] `tenant_id` on every new table — creates none
- [x] Index on `tenant_id` plus lookup columns — adds none
- [x] Money columns — none
- [x] Expand / contract — no schema change

It reads `W-11.1`'s tables under RLS as `app_user`, like any other query.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `shared/.../authz/PermissionServiceTest.java` | held action passes; unheld returns `403`; an unbound tenant fails closed |
| Unit | `shared/.../authz/PermissionCacheTest.java` | a version bump invalidates; a miss repopulates; Redis being unreachable fails closed, not open |
| Integration | `shared/.../authz/PermissionReplicaIT.java` | **two `PermissionService` instances sharing one Redis**: a role change through instance A is visible to instance B without instance B being told |
| Integration | `shared/.../authz/PermissionRlsIT.java` | a user's actions in tenant A do not leak into tenant B |

`PermissionReplicaIT` is the ticket. It is the test that would have failed against the frozen
design, and it is the reason this is a separate ticket from `W-11.1`.

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. `W-53` already ships
`RedisTestContainerInitializer`, so no new container plumbing is needed.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d
docker compose -f infra/docker/compose.yml ps redis

cd code/backend && mvn -q -pl shared -Dit.test=PermissionReplicaIT verify
cd code/backend && mvn -q verify

# no in-process permission map survives anywhere
grep -rn 'ConcurrentHashMap\|HashMap' shared/src/main/java/com/infinevo/shared/authz/ \
  && echo "REVIEW: in-memory map in authz" || echo "no in-process cache"

cd infra/azure && az bicep build --file main.bicep && az bicep lint --file main.bicep
```

| Check | Expected |
|---|---|
| Redis service | healthy |
| `PermissionReplicaIT` | green — instance B sees the change |
| Cache grep | `no in-process cache` |
| Suite | green, no skips |
| Bicep | builds and lints clean; deployment is the founder's step |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Redis is unreachable and the check fails **open** | **medium, and severe** | Fails closed by design; `PermissionCacheTest` asserts it, because an authorization cache that degrades to "allow" is worse than an outage |
| An in-process map is added back for speed | medium | The grep in verification; a local map would reintroduce the exact defect this ticket exists to remove |
| Version bumps are missed on some write path | medium | The bump lives in `W-11.1`'s service layer, not in controllers — the frozen design's mistake was putting it in a controller |
| Redis holds permission data outside the database's RLS | medium | Keys include the tenant; values are action codes only, never employee or salary data |
| Duplicating `W-53`'s cache instead of reusing it | medium | `PermissionCache` wraps `RedisCacheService`; it opens no connection and owns no configuration of its own |

## 10. Rollback

Nothing is deployed and no schema changes. Removing `@RequiresAction` annotations withdraws
enforcement; `W-11.1`'s data is unaffected.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table | **creates no table** |
| Flyway only, `ddl-auto` nowhere | **ships no migration**; none added |
| `Money`/`BigDecimal` for money | holds no money |
| Index on `tenant_id` plus lookup columns | adds none; relies on `W-11.1`'s |
| Expand / contract | no schema change |
| No module references another module | code lands in `shared`, as the tenant filter does |

## 12. Gap inventory

| ID | Decision |
|---|---|
| In-process permission cache (`AuthzServiceImpl.java:29`) | **Fixed.** Shared Redis, version-keyed |
| Manual invalidation from a controller (`RoleActionController.java:94`) | **Fixed by replacement.** Version bump in the service layer |
| Permission by URL prefix and role name (`SecurityConfig.java:39-43`) | **Fixed.** Method-level, action-based |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Redis, or Postgres with a short cache?** **Redis** — settled 2026-09-23, and the question is now moot: `W-53` merged Redis, its Bicep module and `RedisCacheService` before this spec was written. This ticket consumes that rather than adding anything.
2. **Does an unheld action return `403` or `404`?** `403` confirms the endpoint exists. **Recommend** `403`, because this is an internal business application and hiding endpoint existence from an authenticated employee buys very little.
