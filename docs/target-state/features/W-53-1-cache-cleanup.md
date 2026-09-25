# Feature: Cache cleanup — remove the unused permission cache and the duplicate queue package

| Field | Value |
|---|---|
| **Feature ID** | `W-53.1` · from ticket `W-53` · `12-core-contracts.md` §5 row 22 |
| **Promoted to** | `docs/target-state/features/W-53-1-cache-cleanup.md` on branch `W-53-1-cache-cleanup` — **`W-53-1` with hyphens**, never `W-53.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core` (deletions only) |
| **Related gaps** | DEBT-020 (closed by `shared.authz.PermissionCache`, not by the classes removed here) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | nothing |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | the build is green with nine fewer files and no behaviour change; `@RequiresAction` still caches under a 10-minute TTL | 1 |
| Frontend area | none | 1 |

---

## 1. Problem

Two dead copies sit in `core`:

| Dead code | Live equivalent | Evidence it is dead |
|---|---|---|
| `core/.../cache/PermissionCacheService.java` (15-minute TTL, `:22`) and `PermissionInvalidationService.java` | `shared/.../authz/PermissionCache.java` (`PERMISSION_TTL = Duration.ofMinutes(10)`, `:62`), used by `PermissionService` and bumped by `RoleServiceImpl` (`:55`) | grep for either class name across `code/backend`, excluding `target/`, finds only the two classes and their two tests |
| `core/.../queue/QueueMessage.java`, `QueueConsumer.java`, `QueueProducer.java`, `PayloadTooLargeException.java` | `shared/.../queue/` — the same four names; `PayrunQueueListener` imports the `shared` ones (`PayrunQueueListener.java:6-7`) | grep for `com.infinevo.core.queue` finds only `core/src/test/.../queue/QueueMessageTest.java:1` |

`W-53`'s spec still describes the dead pair as the design (`W-53-redis-cache.md:122,139-140,165-166`)
and a 15-minute TTL (`:66,89`), while the built TTL is 10 minutes.

## 2. Scope

**In scope**

- Delete the six production files and three tests listed in §4
- Record the TTL and file-list corrections `W-53-redis-cache.md` needs (§4, last row)

**Out of scope**

- `core/.../cache/MasterDataCacheService.java` — not named in `12-core-contracts.md:159`; left as is
- Any change to `shared.authz.PermissionCache` or its TTL
- Editing `W-53-redis-cache.md` on this branch: `guard-edit` allows a branch to write only its own spec (`CLAUDE.md`, "The rules a machine checks")

## 3. Flow

```
git rm  core/src/main/java/com/infinevo/core/cache/Permission{Cache,Invalidation}Service.java
git rm  core/src/test/java/com/infinevo/core/cache/Permission{Cache,Invalidation}ServiceTest.java
git rm -r core/src/main/java/com/infinevo/core/queue/  core/src/test/java/com/infinevo/core/queue/
mvn -q verify
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Cache | `core/src/main/java/com/infinevo/core/cache/PermissionCacheService.java` | Delete |
| Cache | `core/src/main/java/com/infinevo/core/cache/PermissionInvalidationService.java` | Delete |
| Test | `core/src/test/java/com/infinevo/core/cache/PermissionCacheServiceTest.java` | Delete |
| Test | `core/src/test/java/com/infinevo/core/cache/PermissionInvalidationServiceTest.java` | Delete |
| Queue | `core/src/main/java/com/infinevo/core/queue/QueueMessage.java`, `QueueConsumer.java`, `QueueProducer.java`, `PayloadTooLargeException.java` | Delete the package |
| Test | `core/src/test/java/com/infinevo/core/queue/QueueMessageTest.java` | Delete |
| Docs | `docs/target-state/features/W-53-redis-cache.md:66,89` → "TTL: 10 minutes"; `:122,139-140,165-166,198-199` → drop the `PermissionCacheService` / `PermissionInvalidationService` rows | **Not on this branch.** Done by `/sync-docs` or the founder, citing this spec |

No code is added. Nothing is renamed. `MasterDataCacheService` stays.

**API contract** — unchanged; no endpoint touches these classes.

## 5. Frontend changes

None.

## 6. Database changes

None.

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | no table |
| Flyway only, `ddl-auto` nowhere | no script; none added |
| Money as `Money` / `BigDecimal` | no money |
| Expand / contract | n/a |
| No module references another | deletions only; `core` still depends on `shared` alone |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Existing | `shared/.../authz/*Test`, `core/.../authz/ActionCatalogueIT`, `RoleRlsIT` | still green — proves the live cache path never touched the deleted classes |
| Existing | `worker/.../listener/PayrunQueueListenerTest` | still compiles against `shared.queue` |

No new test: the change is a deletion, and the proof is the compile.

## 8. Verification

```bash
cd code/backend && mvn -q verify
grep -rn "PermissionCacheService\|PermissionInvalidationService\|com\.infinevo\.core\.queue" code/backend --include=*.java ; echo "exit=$?"
grep -n "ofMinutes" code/backend/shared/src/main/java/com/infinevo/shared/authz/PermissionCache.java
```

| Check | Expected |
|---|---|
| Suite | green, no skips |
| grep | no output, `exit=1` |
| TTL | one line: `PERMISSION_TTL = Duration.ofMinutes(10)` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A branch in flight imports `com.infinevo.core.queue` | low — `12-core-contracts.md:111-112` names the `shared` seam | Compile fails at rebase; fix the import |
| `W-53` spec and code keep disagreeing on the TTL | certain until synced | The doc row in §4 lists the exact lines |

## 10. Rollback

Revert the commit; the files come back. Nothing else depends on the change.
