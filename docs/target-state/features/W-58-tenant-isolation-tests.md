# W-58 — Tenant isolation tests

| Field | Value |
|---|---|
| **Work item** | `W-58` · issue [#78](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/78) |
| **Kind** | Security |
| **Stream / track** | Track S — Security and operations |
| **Wave** | 2 |
| **Size / skill** | S — SEC (tracker says M; most of the work has already landed with W-07 to W-14, see §1) |
| **Owner** | unassigned |
| **Blocked by** | `W-08` (merged) |
| **Blocks** | `W-64` Penetration test |
| **Capabilities** | `PLAT-09` |
| **Decisions** | `D-21` tenant bound once per request, on the DB session · `D-55` RLS enabled not forced, owner bypasses · `D-45` history table outside the checked schemas |
| **Gaps addressed** | `BUG-002` discounted · `DEBT-022` discounted · `DEBT-018` discounted |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-27 |

## Size cap

| Axis | This ticket |
|---|---|
| Backend module | `migration` test tree only. No main code changes anywhere |
| Flyway migration | none |
| Externally testable behaviour | one: a tenant table whose policy is missing, weaker than the standard, or not proven by a behavioural test fails `mvnw verify` |
| Frontend area | none |

`W-57.1` line 76 hands this ticket "per-endpoint `@RequiresAction` coverage". That is a second
behaviour in a second module (`app`'s test tree) and is **out of scope** here — see §2.

---

## 1. Problem

The build order says W-58 should have landed with W-08 (`docs/target-state/09-build-order.md:80`).
It did not, but every ticket since has carried its own RLS test, so most of what W-58 asked for
already exists on `main`:

| Asked for (`08-work-plan.md:152`) | Already on `main` | Evidence |
|---|---|---|
| Cross-tenant read tests | 18 of 19 tenant tables have a raw-`app_user` test | `EmployeeDetailRlsIT.java:127`, `OrgMasterRlsIT.java:103`, `RoleRlsIT.java:88`, `AuditRlsIT.java:84`, `UserAccountRlsIT.java:100`, `SubscriptionRlsIT.java`, `JobStatusTenantIT.java`, `TenantIsolationIT.java:103` |
| RLS verification | Catalogue check: every applied table has `tenant_id`, RLS enabled, a policy named `tenant_isolation` | `ShippedMigrationsIT.java:130` |
| Pipeline integration | `mvnw verify` runs all ITs on every push; two CI gates read the migration SQL | `.github/workflows/ci.yml:135`, `:329`, `:396` |

What is still missing, and is this ticket:

1. **`core.user_tenant` has a policy and no behavioural test.** It is the membership table the
   tenant filter reads (`TenantContextFilter.java:28-143`). A wrong policy there leaks who belongs
   to which company. `V002__user_tenant.sql:20`.
2. **The catalogue check accepts a weak policy.** `ShippedMigrationsIT.java:163` counts policies
   named `tenant_isolation` and stops. A `FOR SELECT`-only policy, a policy scoped to one role, or a
   `USING (true)` policy all pass it. Writes would then be unprotected on that table.
3. **Nothing ties the catalogue to the behavioural tests.** A new table gets its policy checked by
   `ShippedMigrationsIT`, but nothing fails if nobody writes the cross-tenant read test for it.
   `core.user_tenant` is the proof: it slipped for eight tickets.

**Baseline** (measured 2026-09-27 on `main` `0e9086f`, by grep; re-measure with the same commands):

| Command | Output |
|---|---|
| `grep -rhoE 'CREATE POLICY tenant_isolation ON [a-z_.]+' code/backend/migration/src/main \| sort -u \| wc -l` | `20` (19 tenant tables + `core.shedlock`) |
| `find code/backend -name '*Rls*IT.java' -o -name 'TenantIsolationIT.java' -o -name 'CrossTenantWriteIT.java' -o -name 'JobStatusTenantIT.java' \| wc -l` | `11` |
| `grep -l 'FROM core.user_tenant' $(find code/backend -name '*IT.java')` | no RLS test; only `TenantBindingIT` seeds it |
| `grep -c "cmd\|permissive\|qual" code/backend/migration/src/test/java/com/infinevo/migration/ShippedMigrationsIT.java` | `0` — policy shape is not asserted |

## 2. Scope

**In scope**

- A behavioural RLS test for `core.user_tenant`, same shape as `TenantIsolationIT`.
- Policy-shape assertions added to the existing catalogue check in `ShippedMigrationsIT`.
- A coverage registry test: every tenant table in the applied catalogue must be named in the list
  of tables that have a behavioural test, or `verify` fails.
- The `docs/target-state/09-build-order.md` "done when" sentence for W-58, satisfied literally: an
  unscoped query as `app_user` returns zero rows, asserted on the new table.

**Out of scope**

- Per-endpoint `@RequiresAction` coverage (an authenticated endpoint with no permission code).
  `W-57-1-deny-by-default-exception-list.md:76` named W-58 as owner; this spec declines it because it
  is `app`-module work with its own behaviour. **Owner: a new ticket `W-57.3`, to be opened by the
  founder**, built on `W-57.1`'s `PublicEndpointAuditIT` which already walks every mapping.
- RLS on `hrms.*` and `payroll.*` tables. Those tables do not exist yet; each porting ticket (`W-67`
  onward) carries its own policy and its own test, and the registry test built here will refuse the
  table until it does.
- The two CI gates that read migration SQL (`ci.yml:329`, `:396`). They stay as they are.
- Any change under `code/backend/*/src/main`.

## 3. What gets built

All three pieces live in one module's test tree and run under the shared Testcontainers Postgres.

| File | Change |
|---|---|
| `code/backend/migration/src/test/java/com/infinevo/migration/UserTenantIsolationIT.java` | **New.** Two tenants, one membership row each. As `app_user`: no tenant bound → 0 rows; bound to A → A's row only; bound to A, `UPDATE`/`DELETE` of B's row touches 0 rows and B's row is unchanged; bound to A, `INSERT` with `tenant_id = B` is refused. As `migration_user`: both rows visible (D-55 owner bypass, the control). `core.get_user_tenants(uuid)` returns B's membership for B's user even with no tenant bound, because that `SECURITY DEFINER` function is the deliberate single-tenant auto-bind path (`V002__user_tenant.sql:31-38`) |
| `code/backend/migration/src/test/java/com/infinevo/migration/ShippedMigrationsIT.java` | **Changed.** `everyTenantScopedTableHasTenantIdAndRowLevelSecurity` also asserts from `pg_policies` for each policy: `cmd = 'ALL'`, `permissive = 'PERMISSIVE'`, `roles = '{public}'`, `qual` contains `app.current_tenant_id`, and `qual` does not contain `true` as the whole expression. `core.shedlock` is excluded from the `qual` assertion only (its policy is inverted by design, `CONVENTIONS.md` rule 7) |
| `code/backend/migration/src/test/java/com/infinevo/migration/RlsCoverageIT.java` | **New.** Holds one static list: every `schema.table` that has a behavioural cross-tenant test, each entry commented with the test class that proves it. Reads the applied catalogue the same way `ShippedMigrationsIT.shippedTables()` does, subtracts `reference.*` and `core.shedlock`, and fails naming any table not in the list. Also fails on a list entry that is not in the catalogue, so a dropped table cannot leave a stale claim |
| `code/backend/migration/README.md` | **Changed.** One paragraph under "Row-level security": a new table is not done until it appears in `RlsCoverageIT` with a test that proves it |

**Not touched:** every `src/main` tree, every Flyway script, `ci.yml`, `check-done.mjs`, the
`shared` and `core` test trees, `infra/`, `docs/` other than this spec.

The initial list in `RlsCoverageIT`, from §1:

| Table | Proved by |
|---|---|
| `core.tenant` | `TenantIsolationIT` |
| `core.user_tenant` | `UserTenantIsolationIT` (this ticket) |
| `core.user_account` | `UserAccountRlsIT` |
| `core.audit_log` | `AuditRlsIT` |
| `core.role`, `core.role_action`, `core.user_role` | `RoleRlsIT`, `PermissionRlsIT` |
| `core.employee` | `EmployeeRlsIT` |
| `core.employee_personal`, `_contact`, `_identification`, `_employment`, `_bank` | `EmployeeDetailRlsIT` |
| `core.department`, `core.designation`, `core.work_location` | `OrgMasterRlsIT` |
| `core.subscription`, `core.subscription_module` | `SubscriptionRlsIT`, `CrossTenantWriteIT` |
| `core.job_status` | `JobStatusTenantIT` |

The implementer verifies each row by reading the named test before listing it. A table whose
test only goes through the service layer, never a raw `app_user` connection, is not listed until
the test is fixed — and that fix is out of this ticket; report it instead.

## 4. Proving it

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Add a throwaway `V900__leak.sql` creating `core.leak (id uuid, tenant_id uuid)` with RLS enabled and a policy `tenant_isolation ... USING (true)` | `ShippedMigrationsIT` fails naming `core.leak` and `qual`; `RlsCoverageIT` fails naming `core.leak` as untested. CI gates at `ci.yml:329`/`:396` pass, which is why the test exists |
| 2 | Same table, correct policy but `FOR SELECT` | `ShippedMigrationsIT` fails on `cmd` |
| 3 | Same table, correct policy, `TO app_user` only | `ShippedMigrationsIT` fails on `roles` |
| 4 | Same table, correct `FOR ALL` policy, no test | `RlsCoverageIT` fails naming `core.leak`; nothing else fails |
| 5 | Remove `core.user_tenant` from the `RlsCoverageIT` list | `RlsCoverageIT` fails naming `core.user_tenant` |
| 6 | Add `core.nonexistent` to the list | `RlsCoverageIT` fails naming the stale entry |
| 7 | In `UserTenantIsolationIT`, bind tenant A and query without the policy by running the read as `migration_user` | The "unscoped query" assertion fails: 2 rows, not 1. This is the build-order done-when, inverted on purpose |
| 8 | Comment out `ALTER TABLE core.user_tenant ENABLE ROW LEVEL SECURITY` in a copy of `V002` on a throwaway branch | `UserTenantIsolationIT` no-tenant case fails: 2 rows visible, not 0 |

Run each on a throwaway branch, paste the failing assertion lines into the merge commit message,
never merge the breaks.

## 5. Verification

```bash
cd code/backend
./mvnw -B -pl migration -am verify
./mvnw -B -pl migration -am verify -Dit.test=UserTenantIsolationIT,ShippedMigrationsIT,RlsCoverageIT -DfailIfNoTests=false
```

| Check | Expected | Result |
|---|---|---|
| Full `verify` on `migration` | `BUILD SUCCESS`; failsafe reports `UserTenantIsolationIT` 6 tests, `RlsCoverageIT` 2 tests, 0 skipped | |
| `ShippedMigrationsIT.everyTenantScopedTableHasTenantIdAndRowLevelSecurity` | passes on the 20 shipped policies, so the shape assertions describe what is there today | |
| Breaks 1 to 8 in §4 | each fails with the named message; evidence in the commit message | |
| `node .claude/scripts/check-done.mjs W-58` | 5/5 | |
| `git diff --stat main -- code/backend/*/src/main docs/ infra/ .github/` | only `docs/target-state/features/W-58-tenant-isolation-tests.md` | |

## 6. Gap disposition

| Gap | Disposition |
|---|---|
| `BUG-002` HRMS has no tenant column (`legacy/docs/GAP_INVENTORY.md:28`) | Discounted. The new platform has `tenant_id` and RLS on every table. This ticket makes that unforgettable, not more true. Ported HRMS tables get their tests in `W-67` |
| `DEBT-022` 57 Payroll queries not org-scoped (`GAP_INVENTORY.md:72`) | Discounted. RLS makes an unscoped query return nothing rather than everything. §4 break 7 is the direct test of that claim |
| `DEBT-018` no indexes (`GAP_INVENTORY.md:68`) | Discounted. No table created. `DatabaseIndexConventionIT` already covers `tenant_id` indexes |

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The `qual` string from `pg_policies` is Postgres's re-rendered expression, not the SQL as written, so a substring match is brittle | Medium | Assert on `current_tenant_id` alone, which survives re-rendering; the 20 existing policies are the fixture that proves the match |
| The registry list becomes a rubber stamp: a developer adds a table name without a test | Medium | Each entry names its test class; the merge reviewer reads the pair. It is a speed bump, not a wall, and is stated as such in the README paragraph |
| `ShippedMigrationsIT` uses its own database `infinevo_shipped`; a new IT that reuses the default database sees fixture tables | Low | `UserTenantIsolationIT` follows `TenantIsolationIT` (applies `V001` and `V002` by JDBC on the default database); `RlsCoverageIT` follows `ShippedMigrationsIT` and provisions its own database |
| Docker is absent on the developer's machine, so every IT here is skipped and looks green | Medium | `@EnabledIfDockerAvailable` marks them skipped, not passed; CI runs Docker; §5 expects 0 skipped in the failsafe report |

## 8. Rollback

Revert. The ticket adds test files and one README paragraph. It creates no table, role, image,
secret or cloud resource.

## 9. Done when

1. `UserTenantIsolationIT` exists in `migration` and its no-tenant case asserts 0 rows on a raw `app_user` connection.
2. `ShippedMigrationsIT` asserts policy command, permissiveness, roles and qualifier for every tenant table, and passes on `main`'s 20 policies.
3. `RlsCoverageIT` exists, lists 19 tables each with a named test class, and fails on an unlisted catalogue table and on a stale list entry.
4. Breaks 1 to 8 in §4 each fail with the expected message; the merge commit message carries the evidence.
5. `./mvnw -B -pl migration -am verify` is green with 0 skipped ITs in CI.
6. `git diff main -- code/backend/*/src/main` is empty.
7. `code/backend/migration/README.md` tells the next table's author about `RlsCoverageIT`.

---

## Decisions taken in this spec

**1. Where do the tests live?** `migration`, because it is the only module that applies every
shipped script and sees the full catalogue (`ShippedMigrationsIT.java:50`). `shared` was the
alternative; it applies scripts one at a time by JDBC and cannot see tables it did not load.

**2. Is the `@RequiresAction` endpoint sweep in this ticket?** No. It is `app`-module work and a
second behaviour; the size cap says two tickets. Proposed as `W-57.3`, founder to open it and
correct `W-57-1-deny-by-default-exception-list.md:76` when they do.

**3. Registry list or automatic discovery of tests?** A list. Discovery would mean grepping test
source across four modules for table names, which is more fragile than the thing it checks.
