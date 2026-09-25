# W-53 — Redis Cache & Invalidation

> Distributed Redis Caching for User Permissions and Master Data with Cross-Instance Invalidation (`PLAT-05`).
> Derived from `TEMPLATE.md` and `TEMPLATE-INFRA.md`.

| Field | Value |
|---|---|
| **Work item** | `W-53` · issue [#73](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/73) |
| **Kind** | Backend / Infra |
| **Stream / track** | Stream G — Infrastructure · Track I |
| **Wave** | Wave 2 — Data platform |
| **Size / skill** | M · BE / INFRA |
| **Owner** | developers |
| **Blocked by** | `W-50` ([#70](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/70)) · `W-51` ([#71](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/71)) |
| **Blocks** | `W-11` (Authorization and role action checks) · Multi-replica scaling of `app` container |
| **Capabilities** | `PLAT-05` — distributed caching and multi-instance invalidation |
| **Decisions** | `D-09` (PostgreSQL) · `D-10` (Azure Container Apps) · `D-18` (India region) · `D-19` (10 × 100 scale) · `D-48` (Unified backend image) · `D-56` (Tenant isolation) |
| **Gaps addressed** | `DEBT-020` (In-process `ConcurrentHashMap` permission cache in `AuthzServiceImpl.java:28-29`), `BUG-002`, `DEBT-034`, `DEBT-035` |
| **Status** | **Approved by founder** |
| **Approved by** | founder |
| **Approved on** | 2026-09-22 |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 row 22 (§3 "Cache" and "Permission check" seams). TTL and the permission-cache class now follow the code on `main`; the dead classes are listed in §12 and are `W-53.1`'s |

---

## 1. Problem

The legacy platform cannot scale horizontally to multiple application replicas because authorization permissions and lookup data are cached in single-process JVM memory:

1. **In-Process Permission Cache (`DEBT-020`, `legacy/docs/GAP_INVENTORY.md:70`)**:
   In `legacy/Payroll-Bend-SBoot`:
   - `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/auth/AuthzServiceImpl.java:28-29` caches user action permissions in a local `ConcurrentHashMap<String, Set<String>>`:
     ```java
     private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();
     ```
   - When permissions are modified or assigned, invalidation only removes entries from the local map (`AuthzServiceImpl.java:92-94`):
     ```java
     public void invalidateUserOrgCache(String userId, String organizationId) {
         cache.remove(cacheKey(userId, organizationId));
     }
     ```
   - **Failure mode across multiple replicas**:
     If `app` runs with 2 or more replicas in Azure Container Apps, an administrator role revocation executed against Replica 1 clears only Replica 1's memory. Replica 2 continues serving stale permissions indefinitely until restarted (`docs/target-state/04-runtime-containers.md:66`).

2. **Missing Invalidation on User Role Mutation (`DEBT-034`)**:
   In `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/CompanyUserServiceImpl.java:405,762` and `OrganizationRoleServiceImpl.java:103` (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/organization/OrganizationRoleServiceImpl.java:103`), user role assignments and role modifications save to the database without calling `invalidateUserOrgCache`. Permissions remain stale until server restart even on a single instance.

3. **Repetitive Master Data Relational Queries & HRMS Defect (`BUG-002`)**:
   - In `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/service/UsersManagementService.java:284-294`, roles and actions are looked up on every request via unindexed joins with zero caching and no tenant isolation.
   - Master data and statutory reference lookups (e.g. tax regimes, deductions in `reference` schema) are query-heavy but change rarely. Without a shared cache, each HTTP request repeatedly queries PostgreSQL.

4. **Azure Cache for Redis Retirement (`DEBT-035`)**:
   Microsoft announced the retirement of legacy `Microsoft.Cache/redis` as of April 1, 2026, blocking new instance creations. `infra/azure/modules/redis.bicep:28` targets this dead service. It must be modernized to Azure Managed Redis (`Microsoft.Cache/redisEnterprise@2025-04-01`).

---

## 2. Target State

1. **Modernized Azure Infrastructure**:
   - Backed by Azure Managed Redis (`Microsoft.Cache/redisEnterprise@2025-04-01`) SKU `Balanced_B0` on TLS port 10000 over private endpoint (`pe-redis-infinevo-{env}`) in `snet-pe`, and `redis:7-alpine` on host port 6379 in local Docker Compose.
2. **Standard Cache Abstraction (`code/backend/shared`)**:
   - `CacheService` interface providing type-safe `get`, `put`, `evict`, `evictPattern`, and health operations.
   - Configured with Lettuce connection pooling, TLS support for Azure, and Jackson JSON serialization (`JavaTimeModule`).
   - Read fail-open policy: transient Redis connection failures log warnings and fall back directly to PostgreSQL.
3. **Tenant-Safe Cache Key Namespacing (`Hard Rule 7`)**:
   - Strict tenant scoping enforced by `TenantCacheKeyGenerator` requiring `TenantContext.require()`:
     - User Permissions: `infinevo:{tenantId}:authz:perm:{userId}` under a per-tenant version key (TTL: **10 minutes** — `code/backend/shared/src/main/java/com/infinevo/shared/authz/PermissionCache.java:62`, `PERMISSION_TTL = Duration.ofMinutes(10)`; the spec follows the code). The TTL is the backstop; the mechanism is the version bump `RoleServiceImpl` makes after every committed role write, which `W-11.2` built on this cache
     - Tenant Master Data: `infinevo:{tenantId}:master:{domain}:{id}` (TTL: 2 hours)
     - Global Reference Data: `infinevo:global:ref:{domain}:{id}` (TTL: 24 hours)
4. **Immediate Multi-Instance Invalidation**:
   - Single-tier shared Redis ensures cache modifications and evictions are **instantly visible across all container replicas in 0 ms**.
   - Eviction fail-closed policy: invalidation errors throw so stale permissions do not silently persist.

---

## 3. Architecture & Flows

### A. Permission Cache Read Flow
```
[HTTP Request on Replica 1 or Replica 2]
       │
       ▼
1. Query permission for userId + tenantId
       │
       ▼ 2. Check Redis key: "infinevo:{tenantId}:authz:perm:{userId}" at the tenant's current version
       ├─► HIT  ──► Return cached Set<String> (0.5 ms)
       │
       ▼ MISS (or Redis downtime fallback)
3. Query PostgreSQL (user_role, role_action) under tenant RLS
4. Store result in Redis with 10-minute TTL (PermissionCache.java:62)
5. Return Set<String> to caller
```

As built by `W-11.2`, this flow lives in `shared.authz.PermissionCache` and
`shared.authz.PermissionService` (`PermissionService.java:22,69`), wired by
`AuthzAutoConfiguration.java:33-34` — not in the `core.cache` classes Task 3 below named.
A permission miss with Redis unreachable is **refused**, not served from PostgreSQL
(`PermissionCache.java:50-52`): the fail-open policy in §2 applies to master and reference
data, never to a permission set.

### B. Invalidation Flow Across Multiple Replicas
```
[Admin modifies User Role on Replica 1]
       │
       ▼ 1. Save new role mapping to PostgreSQL
       ▼ 2. Evict Redis key: "infinevo:{tenantId}:auth:perm:{userId}"
       │
   [Shared Redis] ── (Key immediately deleted via DEL/UNLINK)
       │
       ├─────────────────────────────────────────┐
       ▼                                         ▼
[Next Request on Replica 1]               [Next Request on Replica 2]
       │                                         │
       ▼ Cache miss                              ▼ Cache miss
       ▼ Read fresh from DB                      ▼ Read fresh from DB
       ▼ Result: Sees new permission             ▼ Result: Sees new permission
                 immediately (0 ms)!                       immediately (0 ms)!
```

---

## 4. Implementer Tasks & Area Partitioning

Phase 2 executes each task independently within its single module/area:

| Task | Area / Module | Scope |
|---|---|---|
| **Task 1: Azure IaC** | `infra/azure/` | Modernize `modules/redis.bicep` to `Microsoft.Cache/redisEnterprise@2025-04-01`, update `private-endpoint.bicep` (`groupId: 'redisEnterprise'`), update `private-dns.bicep` (`privatelink.redisenterprise.cache.azure.net`), enable `deployRedis = true` in `main.bicep`, update `probes/private-path-probes.sh` for port 10000 TLS. |
| **Task 2: Shared Cache Layer** | `code/backend/shared/` | Add `spring-boot-starter-data-redis` to `pom.xml`. Implement `CacheService`, `RedisCacheService`, `TenantCacheKeyGenerator`, `RedisConfig`, `RedisTestContainerInitializer`. |
| **Task 3: Core Domain Adapters** | `code/backend/core/` | Implement `PermissionCacheService`, `PermissionInvalidationService`, `MasterDataCacheService`. **As built, the first two are dead** — `W-11.2` put the permission cache in `shared.authz.PermissionCache` instead. See §12. |
| **Task 4: Integration Verification** | `code/backend/` | Build `RedisCacheIT` with Testcontainers running real Redis 7, asserting cross-instance eviction and fail-open resilience. |

### File Changes Table

| File | Change | Why |
|---|---|---|
| `infra/azure/modules/redis.bicep` | Changed | Modernize to `Microsoft.Cache/redisEnterprise@2025-04-01` SKU `Balanced_B0` with `databases` resource |
| `infra/azure/modules/private-endpoint.bicep` | Changed | Add `'redisEnterprise'` to allowed `groupId` parameter |
| `infra/azure/modules/private-dns.bicep` | Changed | Change private DNS zone to `privatelink.redisenterprise.cache.azure.net` |
| `infra/azure/main.bicep` | Changed | Set `deployRedis = true`, pass `groupId: 'redisEnterprise'`, output `sslPort: 10000` |
| `infra/azure/probes/private-path-probes.sh` | Changed | Update `PROBE-REDIS` for port 10000 and auth verification |
| `code/backend/shared/pom.xml` | Changed | Add `spring-boot-starter-data-redis` dependency |
| `code/backend/shared/src/main/java/com/infinevo/shared/cache/CacheService.java` | New | Generic distributed cache abstraction contract |
| `code/backend/shared/src/main/java/com/infinevo/shared/cache/RedisCacheService.java` | New | Redis implementation with fail-open read resilience |
| `code/backend/shared/src/main/java/com/infinevo/shared/cache/TenantCacheKeyGenerator.java` | New | Tenant prefix enforcement per Hard Rule 7 |
| `code/backend/shared/src/main/java/com/infinevo/shared/cache/RedisConfig.java` | New | Lettuce connection factory with TLS & JSON serialization |
| `code/backend/core/src/main/java/com/infinevo/core/cache/PermissionCacheService.java` | New — **unused, deleted by `W-53.1`** | Superseded by `shared.authz.PermissionCache` (`W-11.2`) |
| `code/backend/core/src/main/java/com/infinevo/core/cache/PermissionInvalidationService.java` | New — **unused, deleted by `W-53.1`** | Superseded by the post-commit version bump in `RoleServiceImpl` |
| `code/backend/core/src/main/java/com/infinevo/core/cache/MasterDataCacheService.java` | New | Domain cache adapter for statutory reference and tenant config |
| `code/backend/shared/src/test/java/com/infinevo/shared/test/RedisTestContainerInitializer.java` | New | Testcontainers Redis 7 initializer for integration tests |
| `code/backend/shared/src/test/java/com/infinevo/shared/cache/RedisCacheIT.java` | New | Integration test for multi-instance invalidation, isolation, and fail-open |

---

## 5. Frontend Changes

*None.* This is an infrastructure and backend capability (`PLAT-05` / `skill-BE`).

---

## 6. Database Changes

> **Creates no database tables.**
> Redis is an in-memory key-value store. All tenant data remains isolated in Redis keys through strict prefixing (`infinevo:{tenantId}:...`).

---

## 7. Tests

| Type | Test Class | Coverage |
|---|---|---|
| **Unit** | `com.infinevo.shared.cache.TenantCacheKeyGeneratorTest` | Validates tenant key formats, throws when `TenantContext` absent |
| **Unit** | `com.infinevo.core.cache.PermissionCacheServiceTest` | Hit/miss logic, TTL evaluation, JSON serialization/deserialization — **goes with its class in `W-53.1`**; the behaviour is covered by `core/.../authz/RoleServiceTest.java:418-541` and `PermissionGuardIT.java:154` |
| **Unit** | `com.infinevo.core.cache.PermissionInvalidationServiceTest` | Eviction calls on role assignment or permission change — **goes with its class in `W-53.1`**, same coverage as above |
| **Integration** | `com.infinevo.shared.cache.RedisCacheIT` | Multi-instance simulation: Instance 1 writes, Instance 2 reads, Instance 1 evicts, Instance 2 immediately reflects eviction; fail-open fallback test |

**Added for `W-53.1`:**

| Type | Test Class | Coverage |
|---|---|---|
| **Unit** | `com.infinevo.shared.authz.PermissionCacheTtlTest` | `PERMISSION_TTL` is 10 minutes and is the TTL passed on every permission `put`; an entry written under version *n* is not found once the version is *n+1* |
| **Unit** | `com.infinevo.core.guard.DeadCacheClassesTest` | `core.cache.PermissionCacheService`, `core.cache.PermissionInvalidationService` and `core.queue.QueueMessage` are absent from the classpath — the test that stops them coming back |

---

## 8. Verification

Verification commands must execute cleanly in local environment and CI:

```bash
# 1. Bicep template validation
az bicep build --file infra/azure/main.bicep
az bicep lint --file infra/azure/main.bicep
az bicep build --file infra/azure/modules/redis.bicep
az bicep lint --file infra/azure/modules/redis.bicep

# 2. Build and verify backend compilation and unit tests
mvn -f code/backend/pom.xml spotless:check
mvn -f code/backend/pom.xml clean test

# 3. Cache integration tests with Testcontainers
mvn -f code/backend/pom.xml verify -Dtest=*Cache* -DfailIfNoTests=false

# 4. Assert zero ddl-auto occurrences
git grep -nE '^[^#]*ddl-auto[[:space:]]*[:=]' -- code/backend/
```

| Check | Expected Output | Verdict |
|---|---|---|
| Bicep build & lint | Exit code 0, clean validation | PASS |
| Spotless check | Exit code 0, clean formatting | PASS |
| `TenantCacheKeyGeneratorTest` | Keys include `{tenantId}` UUID segment | PASS |
| `RoleServiceTest` (W-11.2) | every committed role write bumps the tenant's permission version once, after commit | PASS |
| `PermissionCacheTtlTest` (W-53.1) | TTL is 10 minutes; a stale version is a miss | PASS |
| `RedisCacheIT` | Multi-replica eviction test reflects instantly | PASS |
| `ddl-auto` check | Empty (exit code 1 from grep) | PASS |
| Build status | `BUILD SUCCESS` | PASS |

---

## 9. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| **Redis temporary outage** | Low | Implement fail-open policy: If Redis times out or is unreachable, catch exception, log warning, and fall back to PostgreSQL database query directly without breaking caller requests. |
| **Cross-tenant key collision** | Low | All cache keys are generated through `TenantCacheKeyGenerator` which requires `tenant_id UUID` from `TenantContext.require()`. Non-tenanted data uses explicit `infinevo:global:...` namespace. |
| **Cache serialization drift** | Medium | Use Jackson JSON serializer configured with `JavaTimeModule` instead of raw Java serialization. |

---

## 10. Rollback

If caching causes issues in staging or production:
1. Setting configuration property `infinevo.cache.enabled=false` immediately bypasses Redis and executes all queries directly against PostgreSQL without requiring code rollbacks.
2. No relational schema changes or database migrations are involved.

---

## 11. Founder Decisions Recorded

1. **Azure Managed Redis SKU**: `Balanced_B0` selected. Cost-effective for dev (`D-19` scale) at ~$15–$25/month.
2. **Cache Value Serialization Format**: Jackson JSON serializer with `JavaTimeModule` selected for inspectability, version-tolerance, and security.

---

## 12. Known cleanup, `W-53.1`

`W-53` merged (#73) and `W-11.2` then built the permission check on `shared.cache.CacheService`
directly, leaving `W-53`'s core adapters with no caller. Verified against `main` on 2026-09-25.

| # | Item | Evidence | Fix in `W-53.1` |
|---|---|---|---|
| 1 | `core.cache.PermissionCacheService` is unused | `grep -r PermissionCacheService code/backend` hits only the class itself and `core/src/test/.../cache/PermissionCacheServiceTest.java`; the live cache is `shared/.../authz/PermissionCache.java`, registered at `AuthzAutoConfiguration.java:33-34` and read by `PermissionService.java:69` | Delete class and test |
| 2 | `core.cache.PermissionInvalidationService` is unused | same grep: only itself and `PermissionInvalidationServiceTest.java:19`; invalidation is `PermissionCache.bumpVersion`, called after commit (`core/.../authz/RoleServiceTest.java:418-541` proves it) | Delete class and test |
| 3 | `core.queue.QueueMessage` duplicates `shared.queue.QueueMessage` | the two files are line-for-line copies (`core/.../queue/QueueMessage.java:25,36,45,57,59,108,127,136,148,159-160` = `shared/.../queue/QueueMessage.java` same lines); `core/.../queue/QueueProducer.java:7` is an empty `extends`; nothing imports `com.infinevo.core.queue` | Delete the `core.queue` package; `shared.queue` is the seam (`12-core-contracts.md` §3) |
| 4 | Key prefix in this spec says `auth:perm`; code says `authz:perm` | `PermissionCache.java:64-66` — `DOMAIN = "authz"`, `PERMISSION_DOMAIN = "authz:perm"` | Spec corrected above; no code change |

`MasterDataCacheService` is **kept**: it is the only adapter with the fail-open read policy
and no other class provides one. Nothing in this section changes runtime behaviour; the
delete is guarded by `DeadCacheClassesTest` (§7) so the classes cannot be reintroduced by a
merge from an older branch.
