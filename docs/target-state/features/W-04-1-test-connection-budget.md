# Feature: Test connection budget

| Field | Value |
|---|---|
| **Feature ID** | `W-04.1` · from ticket `W-04` · defect `D-1` |
| **Promoted to** | `docs/target-state/features/W-04-1-test-connection-budget.md` |
| **Owner** | claude — `dev-claude` |
| **Apps touched** | `code/backend/shared` (test source only) |
| **Related gaps** | DEBT-003 (tests exist now, and must stay green) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | nothing |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `shared` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | `mvn verify` passes on `main`, serially and with `-T 4` | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

`mvn -q verify` fails on `main` (2026-09-25, reproduced serially and in parallel):

```
DatabasePrivilegesIT.appUserDeniedDdl:55  expected 42501 but was 53300
DatabasePrivilegesIT.*  PSQL FATAL: remaining connection slots are reserved for roles with the SUPERUSER attribute
Tests run: 59, Failures: 1-2, Errors: 11-12
```

`53300` is `too_many_connections`. The cause is arithmetic, not the test:

- One Postgres Testcontainer serves every integration test in the module
  (`shared/src/test/java/com/infinevo/shared/test/PostgresTestContainerInitializer.java:77-78`),
  with the image default `max_connections = 100`.
- `shared` has **16** `@SpringBootTest` classes, most with their own `TestApp`, so Spring caches
  up to 16 contexts, each holding a live HikariCP pool. The default pool is 10
  (`HikariPoolCalibrationTest.java:34`). 16 × 10 exceeds 100 before the last class runs.
- `DatabasePrivilegesIT` runs last alphabetically among the heavy ones and opens raw
  `DriverManager` connections (`DatabasePrivilegesIT.java:48`), so it is the one that hits the wall.

The suite was green when the tracker last said "100 passing, none skipped" — before `W-11.2`,
`W-13.2`, `W-14.1` and `W-55` each added contexts.

## 2. Scope

**In scope**

- Every test context gets a small pool unless the test asks for a specific size
- The test container gets headroom, so a future 30th context does not reopen this defect
- A test that proves the budget: contexts × pool ≤ container slots

**Out of scope**

- Merging test contexts into fewer `TestApp` classes — a refactor with no defect behind it
- Any main-source change

## 3. Flow

```
PostgresTestContainerInitializer.initialize(ctx)
  --> if ctx has no spring.datasource.hikari.maximum-pool-size: set 2, minimum-idle 0
  --> container started with -c max_connections=200
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Test infra | `shared/src/test/.../test/PostgresTestContainerInitializer.java` | `POSTGRES.withCommand("postgres", "-c", "max_connections=200")`; in `initialize`, apply `spring.datasource.hikari.maximum-pool-size=2` and `minimum-idle=0` **only when the environment does not already define them** (`TenantBindingPoolLeakIT.java:41-44` sets 1 and must keep it) |
| Test | `shared/src/test/.../test/ConnectionBudgetIT.java` | New. Counts `@SpringBootTest` classes under `shared/src/test`, reads the effective pool size, asserts `count × pool ≤ 50% of max_connections` read from `SHOW max_connections` (the other half is headroom for raw connections, Flyway and the next context); and asserts `pg_stat_activity` for `app_user` never exceeds the pool during a burst of 50 **concurrent** queries — **tightened at merge review 2026-09-25**: the first form, `+ 20`, landed exactly on 200 at pool 10 and did not fail |

No API, no DTO, no entity.

## 5. Frontend changes

None.

## 6. Database changes

None. No migration.

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | creates no table |
| Flyway only | no schema change |
| Money types | no money |
| Index rule (`DEBT-018`) | n/a |
| Module boundary | `shared` only |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `ConnectionBudgetIT` | the arithmetic above; fails loudly the day a new context breaks it |
| Existing | `DatabasePrivilegesIT`, `TenantBindingPoolLeakIT`, `HikariPoolCalibrationTest` | must still pass unchanged: the leak test keeps its pool of 1, the calibration test reads config, not runtime |

## 8. Verification

```bash
cd code/backend && ./mvnw -B -q clean verify            # serial
cd code/backend && ./mvnw -B -q -T 4 clean verify       # parallel, the case that first failed
```

| Check | Expected |
|---|---|
| Serial | `BUILD SUCCESS`, `DatabasePrivilegesIT` 14/14 |
| Parallel | `BUILD SUCCESS` |
| `ConnectionBudgetIT` | passes at pool 2 (18 × 2 = 36 ≤ 100), and when the default is forced to 10 it fails (18 × 10 = 180 > 100) — proven once at merge review, message recorded in the merge commit |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A pool of 2 makes a parallel-request test flaky | low | Tests that need more set it themselves, and the initializer respects that |
| Another module (`core`, `app`) has the same arithmetic | medium | The initializer is shared, so the fix applies wherever `AbstractIntegrationTest` is used |

## 10. Rollback

Revert the commit. Test-only change; nothing in production.
