# W-06 — Flyway

> **Approved 2026-09-17.** Promoted from
> `.claude/outputs/2026-09-17-plan-W-06-flyway-rev5.md`, the fifth revision. Reviewed by
> `/review-spec` five times; the last pass returned APPROVE WITH CONDITIONS with no High
> finding, and both conditions were applied before approval
> (`.claude/outputs/2026-09-17-review-spec-W-06-rev5.md`).

| Field | Value |
|---|---|
| **Work item** | `W-06` · issue [#7](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/7) |
| **Kind** | **Data / Infra** — migration mechanism, no end-user capability |
| **Stream / track** | Stream B — Data foundation · Track P |
| **Wave** | 2 — Data platform |
| **Size / skill** | S · DATA |
| **Owner** | unassigned — claimable (#107) |
| **Blocked by** | — (`W-05` #6 merged 2026-09-16) |
| **Blocks** | `W-07` tenant model · `W-09` reference schema & seed · every table from `W-13` on |
| **Capabilities** | — (`08-work-plan.md:47` — delivers no end-user feature) |
| **Decisions** | `D-08` reference schema · `D-09` Postgres with Flyway, `ddl-auto` disabled permanently · `D-10` Container Apps · `D-38` Java 21 · `D-43` no branch protection · **six W-06 rulings, §13** |
| **Gaps addressed** | `BUG-004`, `DEBT-002`, `DEBT-003`, `DEBT-018` — dispositioned in §7 with `BUG-002`, `DEBT-001`, `DEBT-021` |
| **Status** | **Built and merged 2026-09-18.** The code reached `main` inside `365a319` (PR #119), a documentation pull request, rather than through its own reviewed pull request. Issue [#7](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/7) is closed; eight review findings remain open as fix-forward work (`.claude/outputs/2026-09-18-review-W-06-rev2.md`) |
| **Approved by** | Founder |
| **Approved on** | 2026-09-17 |

> Hard rule 1 satisfied: approved 2026-09-17, so `/develop W-06` may start.
>
> **Naming, because two things share a word.** `migration` is a Maven **module**
> (`code/backend/migration`) and, from this ticket, also a Postgres **schema**. Below,
> *the module* and *the schema* are always written out.

---

## 1. Problem

`W-05` created the container for schema and left it empty. Four schemas exist, owned by
`migration_user`; `app_user` holds `USAGE` but no `CREATE`
(`infra/postgres/03-grants.sql:21-23`). **Nothing can create a table.** `app_user` is
refused DDL by design, `ddl-auto` is forbidden by `D-09`, and Flyway does not exist in
the build. The database is correctly locked and permanently empty.

That blocks the one rigid chain in the project — `09-build-order.md:66`: *"`W-06` →
`W-07` → `W-08` is the one genuinely rigid chain. Everything product-facing sits behind
it."* `W-07` cannot attach row-level security to tables that do not exist, and `W-09`
cannot seed a `reference` schema with no tables in it. Behind that chain sits `BUG-002`
— HRMS carries no tenant column on any entity, verified as 0 files matching
`organizationId|tenant` (`legacy/docs/GAP_INVENTORY.md:28`). W-06 fixes none of that; it
is the first link that makes fixing it possible.

The mechanism this replaces is the frozen system's, and it is a tracked defect:
`spring.jpa.hibernate.ddl-auto=update` runs in both backends in every environment —
`legacy/HRMS_Backend/src/main/resources/application.properties:10` and
`legacy/Payroll-Bend-SBoot/src/main/resources/application.properties:5` — with no
rollback and no audit trail (`BUG-004`, `DEBT-002`). Those files are frozen and are not
edited by this ticket.

**Baseline** — measured on `main` at `32829f5`, 2026-09-17.

| Command | Exit | Output |
|---|---|---|
| `git grep -nE '<(artifactId\|groupId)>[^<]*flyway' -- 'code/backend/**/pom.xml'` | 1 | *(none)* — no Flyway dependency anywhere |
| `git grep -nE '^[[:space:]]*flyway:' -- 'code/backend/**/*.yml'` | 1 | *(none)* — no Flyway configuration |
| `git ls-files code/backend/migration \| wc -l` | 0 | `5` — one `pom.xml` with no `<dependencies>`, four `.gitkeep` |
| `git grep -nE '^[^#]*ddl-auto[[:space:]]*[:=]' -- code infra` | 1 | *(none)* — already clean, and gated |

**The ticket's stated "done when" cannot be the acceptance criterion.**
`09-build-order.md:173` says *"Done when: `ddl-auto` is absent from every configuration
file."* That is already true, with zero Flyway code present, and three gates keep it
true — `.github/workflows/ci.yml:170-178`, `infra/docker/smoke.sh:76-81`,
`.claude/scripts/check-done.mjs:453-461`. A spec that adopts it unchanged passes before
a line is written. §12 replaces it.

## 2. Scope

**In scope**

- A **migration runner**: a Spring Boot application in the `migration` **module** that
  connects as `migration_user`, applies the scripts, and exits — non-zero on failure
  (§13 R2).
- **Script conventions** in `code/backend/migration/README.md`: naming, single global
  version sequence (§13 R5), forward-only, mandatory schema qualification, the
  `tenant_id` rule, the index standard, expand/contract sequencing.
- **Per-schema ordering**: one Flyway instance, one history table, four locations,
  applied `reference` → `core` → `hrms` → `payroll` (`03-code-structure.md:142`).
- **A `migration` schema** for Flyway's own bookkeeping (§13 R1).
- **Widening six constants and one accessor on `PostgresTestContainerInitializer` to
  `public`**, so a test outside `com.infinevo.shared.test` can use the shared container
  harness at all. Four-word change; no behaviour change (§4 file 13).
- **Proof by test fixtures, not by shipped tables.** The shipped script trees stay empty;
  `W-07` and `W-09` write the first real migrations.
- **Pipeline validation**: an integration test in the existing `backend` CI job, two
  `smoke.sh` checks, and one `ci.yml` gate confining `spring.flyway` to the `migration`
  module (§13 R4).
- The `migrate` service in `infra/docker/compose.yml`, which `app` and `worker` wait on.

**Out of scope**

- `tenant_id` on application tables, row-level security policies, role grants, and the
  build check that fails on an unscoped table — **`W-07`** (`08-work-plan.md:48`).
- The 15 `reference` tables and the tax seed — **`W-09`** (`08-work-plan.md:50`).
- Every domain table — **`W-13`** onward.
- **Running migrations inside `shared`'s Testcontainers initializer** — **`W-07`**. rev2
  put it here and it produced a Maven cycle; see §3. `W-07` is the first ticket whose
  tests need migrated tables in `shared`, and by then the `<testResource>` route is
  available. W-06 widens the constants but adds no behaviour there.
- **Database-level enforcement that every migration script names its schema** —
  **`W-07`** (§13 R7). Two attempts to build it here failed review, and the second made
  the hazard worse. `W-07` already delivers a check over migration scripts, which is
  where a check over migration scripts belongs. W-06 ships the rule in
  `migration/README.md` and relies on review until then; §10 states the residual risk.
- **The three `docs/` rewordings the rulings imply** — §13 lists them. `docs/` changes
  only through `sync-docs` with an approved diff (hard rule 3).
- Adding `docker compose up` / `smoke.sh` to CI — **#112, `F-18`**.
- Production images and the Azure migration job — **`W-49`** and **`W-54`**.
- The `keycloak` database, which manages its own schema.

## 3. Standing rules — impact

| Rule | Impact |
|---|---|
| **`tenant_id` on every table in `core`, `hrms`, `payroll`, plus an RLS policy** (`02-data-model.md:15` — *"No exceptions"*; `:16` makes an unscoped table outside `reference` a bug by definition) | **W-06 creates no table in those three schemas.** The test fixtures of §4 live only inside a disposable test container, and every fixture outside `reference` carries `tenant_id`, so they model the rule. **One object cannot comply: `flyway_schema_history`.** It is bookkeeping, not tenant data. §13 R1 puts it in the `migration` schema — outside the three the rule names, with `app_user` granted nothing — so **`W-07`'s check stays absolute**: it scans `core`, `hrms`, `payroll`, and the `migration` schema is out of its scope by construction, with no exemption list to maintain |
| **Flyway script for every schema change, under `code/backend/migration/`. Never `ddl-auto`** | This ticket is the mechanism. The one schema change W-06 makes — the `migration` schema — is *provisioning*, not migration: Flyway cannot create the schema that holds its own history table before it has run. It belongs in `infra/postgres/` beside W-05's scripts. §5 says so. `ddl-auto` stays unset everywhere (§13 R3) |
| **`Money` / `BigDecimal` with explicit precision and scale** | **No impact.** W-06 creates no money column. The rule goes into `migration/README.md` for the first script that does |
| **Index on `tenant_id` plus lookup columns** (`DEBT-018`, `02-data-model.md:363-372`) | **Deferred with a hook.** No application table exists to index. The standard is copied into `migration/README.md`, and the `core`, `hrms`, `payroll` fixtures demonstrate it — `tenant_id` leading. Enforcement is `W-07`'s build check |
| **Expand / contract, no destructive step; the previous release must still run** (`05-azure-architecture.md:133`) | Nothing to expand or contract yet. Written into `migration/README.md`; §11 records that this is the last ticket where rollback is free. This rule is also why `ddl-auto: validate` was rejected (§13 R3) |
| **Nothing under `legacy/` or `docs/` is edited** | Honoured. The three rewordings the rulings imply travel their own `sync-docs` pull requests (§13) |

> **A documentation contradiction, now ruled on.** `docs/CONVENTIONS.md:21` rule 7 and
> root `CLAUDE.md` hard rule 7 read *"`tenant_id` is mandatory … **No exceptions,
> including lookup and reference tables**"*. `D-08`, `02-data-model.md:16` and the
> `COMMENT ON SCHEMA reference` that `W-05` wrote at `infra/postgres/02-schemas.sql:24`
> all say `reference` carries **no tenant column, by design**. **Founder ruling
> 2026-09-17: `reference` is exempt; the two rule statements are reworded** (§13 R6).
> This spec follows that — fixture `V001` creates a `reference` table with no
> `tenant_id` deliberately. The reword must land **before `W-07` writes its build
> check**, because the two readings produce checks that disagree about all 15
> `reference` tables.

**Dependency edges this ticket adds**, both directions, on adjacent lines:

| Edge | Verdict |
|---|---|
| `migration` module → `shared` (`test-jar`, `test` scope) | **Added.** One direction only. The test-jar has no excludes (`shared/pom.xml:69-78`), so it carries `shared`'s test *resources* as well as its classes — including the `db/provision/*.sql` that `shared/pom.xml:55-61` maps in from `infra/postgres/`. The fixtures therefore get a provisioned container from this one dependency |
| `shared` → `migration` module | **Not added.** This is what rev2 implied by making `shared`'s initializer run the migration. With the edge above, Maven cannot build it. Deferred to `W-07`. W-06's change to `shared` is four `public` keywords and creates no edge |
| `app` → `migration` module, `worker` → `migration` module | **Never.** It would put the scripts on the application's classpath and invite Boot's Flyway autoconfiguration into the process that must never hold owner credentials. §13 R4's CI gate makes this mechanical |

## 4. What gets built

```
 docker compose up -d
        │
   postgres (healthy)                      ← W-05: 4 roles, 4 app schemas, 0 tables
        │                                    W-06 adds: migration schema, public locked
        ▼
   migrate  ── runs once, exits 0 ──┐      ← Flyway as migration_user
        │                           │        history table -> migration schema
        │  service_completed_        │
        │  successfully             │
        ▼                           ▼
       app                       worker     ← connect as app_user, no DDL, ever
```

The same jar is what `W-54` runs as a Container Apps job *"before the new revision takes
traffic, using `migration_user`"* (`05-azure-architecture.md:126`).

**The runner is a Spring Boot application, like `app` and `worker`.** Those are the only
two executables in the build and both use `spring-boot-maven-plugin` with an explicit
`<mainClass>` (`app/pom.xml:44-50`, `worker/pom.xml:44-50`); there is no
`exec-maven-plugin` anywhere. Matching them means `mvn -pl migration spring-boot:run`
works in the dev image and `java -jar` works in Azure at `W-54`, from one artifact. **It
exits by itself**: with `spring.main.web-application-type: none` there is no non-daemon
thread, so the context closes and the JVM exits — the mechanism
`worker/src/main/resources/application.yml:11-13` documents in reverse, where the worker
serves health precisely *because* "without HTTP the process has no non-daemon thread and
exits immediately."

**The shipped script trees stay empty.** W-06 adds no permanent table. Behaviour is
proved by **test-only fixtures** under `db/migration-test/`, against a Testcontainers
database destroyed afterwards.

| # | File | Change |
|---|---|---|
| 1 | `code/backend/migration/pom.xml` | Today it has **no `<dependencies>` block at all**. Add `spring-boot-starter`, `flyway-core`, `flyway-database-postgresql`, `postgresql`, and `com.infinevo:shared` `<type>test-jar</type>` `<scope>test</scope>`. Add `spring-boot-maven-plugin` with `<mainClass>com.infinevo.migration.MigrationApplication</mainClass>`, copying the block at `worker/pom.xml:44-50` |
| 2 | `code/backend/migration/src/main/java/com/infinevo/migration/MigrationApplication.java` | **New.** A `@SpringBootApplication` whose only job is to let Boot's Flyway autoconfiguration run and then exit. No `CommandLineRunner`, no datasource bean |
| 3 | `code/backend/migration/src/main/resources/application.yml` | **New.** `spring.main.web-application-type: none`, `banner-mode: off`, and `spring.flyway`: `url/user/password` from `DB_URL` / `DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD`; **`default-schema: migration` and `schemas: migration,reference,core,hrms,payroll`** — the default schema is listed among the managed schemas so the history table's placement does not depend on Flyway's version-specific handling of a default outside that list; `create-schemas: false` (provisioning owns schema creation); `locations` in schema order; `validate-on-migrate: true`; `clean-disabled: true`; `fail-on-missing-locations: true`. **No `spring.datasource`, no `ddl-auto`** — this module has no JPA |
| 4 | `code/backend/migration/README.md` | **New.** The script conventions — the deliverable named in `08-work-plan.md:47`. Must carry the **Always name the schema** section verbatim from §5a, including its closing reviewer instruction, because after §13 R7 this file *is* the control |
| 5 | `.../src/test/resources/db/migration-test/reference/V001__country_fixture.sql` | **New, test scope.** `reference.country_fixture (code char(2) PK, name text NOT NULL)` — **no `tenant_id`, deliberately**, per `D-08` and §13 R6 |
| 6 | `.../db/migration-test/core/V002__tenant_fixture.sql` | **New, test scope.** `core.tenant_fixture (id bigserial PK, tenant_id uuid NOT NULL, country_code char(2) NOT NULL REFERENCES reference.country_fixture(code))` + `CREATE INDEX ON core.tenant_fixture (tenant_id, id)`. **The foreign key is the ordering proof** |
| 7 | `.../db/migration-test/hrms/V003__hrms_fixture.sql` and `payroll/V004__payroll_fixture.sql` | **New, test scope.** One table each, `tenant_id` with its leading index |
| 8 | `code/backend/migration/src/test/java/com/infinevo/migration/FlywayMigrationIT.java` | **New.** Uses `PostgresTestContainerInitializer` from the `shared` test-jar for a provisioned container — possible only after file 13 — then runs Flyway against `db/migration-test` as `migration_user`. Carries §6's assertions and §8's breaks |
| 9 | `infra/postgres/02-schemas.sql` | Add `CREATE SCHEMA IF NOT EXISTS migration AUTHORIZATION migration_user`, the idempotent `ALTER SCHEMA migration OWNER TO migration_user`, and a `COMMENT ON SCHEMA` — exactly the pattern in lines 8-24 (the whole file; it is 24 lines), including the `ALTER` that line 14's comment explains is needed for re-provisioned volumes |
| 10 | `infra/postgres/03-grants.sql` | `GRANT USAGE, CREATE ON SCHEMA migration TO migration_user`. **No grant to `app_user` or `readonly_user`.** Extend the self-check `DO` block at lines 39-56 to assert `app_user` has no `USAGE` on the `migration` schema. **No change to `public`** — see §13 R7 |
| 11 | `infra/docker/compose.yml` | **New service `migrate`**, after `postgres`. Same `build.context: ../..` and `dockerfile: infra/docker/dev.Dockerfile.backend` as `app` (`:125-126`) and — because that image is `ENTRYPOINT ["mvn","-B"]` / `CMD ["-pl","app","spring-boot:run",…]` at lines 29-30 — an **explicit `command: ["-pl","migration","spring-boot:run"]`**, the same array-form override `app` uses at `:127` and `worker` at `:157`. Without it the service starts a second copy of `app`. `-pl` alone works because Dockerfile line 26 installs every module. `restart: "no"`, `depends_on: postgres service_healthy`, env `DB_URL`, `DB_MIGRATION_USERNAME: migration_user`, `DB_MIGRATION_PASSWORD: local_migration_pw` (matching `MIGRATION_PW` at `:31`). `app` and `worker` gain `depends_on: migrate: {condition: service_completed_successfully}` |
| 12 | `infra/docker/smoke.sh` | Two `check` lines in the existing `── database` section, in the file's own idiom (`$C exec -T postgres psql -tAU postgres …`): the history table exists in the `migration` schema owned by `migration_user`; `app_user` has no `USAGE` on that schema. **`migrate` must not be added to the running-services loop at line 17** — that loop asserts `--status running`, and a run-once service has exited |
| 13 | `code/backend/shared/src/test/java/.../PostgresTestContainerInitializer.java` | **Widen to `public`**: the six role constants at lines 30, 33, 36, 39, 42, 45 and the `getJdbcUrl()` accessor at line 66. All are package-private today, which is why `DatabasePrivilegesIT` (same package, `:1`) can use them and a test in `com.infinevo.migration` cannot. No behaviour change, no new dependency, nothing else in the file touched |
| 14 | `.github/workflows/ci.yml` | One `static` step beside "ddl-auto set nowhere" (lines 170-178): `spring.flyway` appears nowhere under `code/` outside `code/backend/migration` (§13 R4) |

**Not touched.** `infra/postgres/01-roles.sql` — the four roles are right and W-06 adds
none. `code/backend/app`, `code/backend/worker` — no Flyway section, no `migration_user`
credential, datasource stays `app_user` (`application-local.yml:12-15`).
`code/backend/{core,hrms,payroll}`. `DatabasePrivilegesIT` — unaffected by file 13;
widening access breaks no caller. `dev.Dockerfile.backend` — it already copies
`migration/pom.xml` at line 18 and installs every module at line 26. No `legacy/` file.
No `docs/` file but this spec. The `backend` CI job gains no step.

## 5. Database changes

Required by `TEMPLATE-INFRA.md:16-17`, because this ticket creates database objects.

| Object | Schema | Owner | Created by | `tenant_id`? |
|---|---|---|---|---|
| `migration` schema | — | `migration_user` | `infra/postgres/02-schemas.sql` — **provisioning, not a Flyway script** | n/a |
| `flyway_schema_history` | `migration` | `migration_user` | Flyway itself, on first run | **No** — bookkeeping, not tenant data. Outside the three schemas the rule names, invisible to `app_user`. §3 and §13 R1 |
| `country_fixture` | `reference` | `migration_user` | test fixture `V001` | **No, by design** (`D-08`, `02-data-model.md:16`, §13 R6) |
| `tenant_fixture`, and two probes | `core`, `hrms`, `payroll` | `migration_user` | test fixtures `V002`–`V004` | **Yes**, `tenant_id` as the leading index column |

**Flyway scripts shipped by this ticket: none.**
`db/migration/{reference,core,hrms,payroll}/` keep their `.gitkeep` files. `V001__` in
the *shipped* sequence is `W-07`'s or `W-09`'s to take; the fixtures use a separate
location root and their own history table and consume no numbers from it.
`migration/README.md` states this, along with the single-global-sequence rule (§13 R5).

**No change to the `public` schema.** Earlier revisions revoked `CREATE` there to make a
script that omits its schema qualifier fail. It does not work: Flyway connects with a
search path covering the schemas it manages, so an unqualified `CREATE TABLE` lands in
the first of those the role may write to — never `public`. The revoke would have been
inert, and the test asserting otherwise would have passed for the wrong reason. §13 R7
records the decision and §5a the replacement.

## 5a. The convention that replaces it

This text goes into `code/backend/migration/README.md` (file 4) and is the control W-06
ships in place of a database-level guard:

> ### Always name the schema
>
> Every statement names its schema. No exceptions, including indexes and constraints.
>
> ```sql
> CREATE TABLE core.employee (...)           -- yes
> CREATE INDEX ON core.employee (tenant_id)  -- yes
> CREATE TABLE employee (...)                -- NO
> ```
>
> **Why this is not a style rule.** Flyway connects with a search path, so an unqualified
> `CREATE TABLE` does not fail — it succeeds, in the wrong schema. The table then exists
> outside `core`, `hrms` and `payroll`, which is where `W-07`'s tenant check looks. So it
> never gets `tenant_id`, never gets a row-level security policy, and nothing reports it.
> A missing `core.` is the one typo in this directory that is silent and permanent.
>
> **Reviewing a migration script?** Read the first word after `CREATE`, `ALTER` or
> `DROP`. If it has no dot in it, stop and ask.

The last line is the deliverable. "Review carefully" is not a control; "read the first
word after CREATE and look for a dot" is one a reviewer can actually run their eye down.

## 6. Implementer tasks

One area each, per `infra-task` step 7.

| # | Area | Task | Depends on |
|---|---|---|---|
| **T1** | `infra/` | Files 9-12: the `migration` schema, the grants, the `migrate` compose service, the two smoke checks | — |
| **T2** | `code/backend/shared` | File 13 only: widen six constants and one accessor to `public`. Four-word change, no behaviour change | — |
| **T3** | `code/backend/migration` | Files 1-8: dependencies and packaging, `MigrationApplication`, `application.yml`, the conventions README, four fixtures, `FlywayMigrationIT` | **T2** to compile the IT; **T1** for the IT to pass |
| **T4** | `.github/workflows/` | File 14: the `spring.flyway` gate | — |

**T1, T2 and T4 can run in parallel; T3 needs both T1 and T2.** T2 exists only because
`PostgresTestContainerInitializer`'s members are package-private — nothing outside
`com.infinevo.shared.test` has needed them until now, and `FlywayMigrationIT` is the
first. T3 carries the one dependency change: the `migration` module must gain the
`shared` test-jar dependency before it can host any integration test. Only `core`,
`hrms` and `payroll` have it today (`core/pom.xml:29`, `hrms/pom.xml:33`,
`payroll/pom.xml:33`).

## 7. Gap disposition

| Gap | Disposition |
|---|---|
| `BUG-004` — uncontrolled schema drift, `ddl-auto=update` in both frozen backends | **Fixed for the target state.** W-06 delivers the migration framework the gap names as missing. The frozen backends keep the defect until decommissioned |
| `DEBT-002` — no Flyway/Liquibase, `ddl-auto=update` in all environments | **Fixed.** This ticket is the fix |
| `DEBT-003` — effectively zero automated test coverage | **Partly addressed.** The `migration` module gets its first tests, behavioural rather than smoke — seven deliberate breaks. Platform coverage stays open |
| `DEBT-018` — zero `@Index` declarations across 99 Payroll entities | **Deferred with a hook.** No application table exists to index. The standard from `02-data-model.md:363-372` goes into `migration/README.md`, and fixtures 6-7 demonstrate it. Enforcement is `W-07`'s build check |
| `BUG-002` — HRMS has no tenant column on any entity; cross-tenant exposure risk | **Not fixed here, and named so it is not mistaken for in scope.** Fixed by `W-07` and `W-08`. W-06 is the first link in the chain that makes them possible (§1) |
| `DEBT-001` — dead 271KB `dashboardcopy.js` in the Payroll frontend | **Discounted.** Frontend dead code, unrelated |
| `DEBT-021` — two `@Scheduled` jobs with no distributed lock | **Discounted.** Worker concurrency, owned by `W-52` |

> Read from `legacy/docs/GAP_INVENTORY.md:28,30,39-41,68,71`, which is authoritative for
> the ID meanings. All 7 `BUG-` and 26 `DEBT-` IDs were scanned; no other overlaps.

## 8. Proving it

The deliverable is a constraint — *schema changes happen one way only, as one role, in
one order.* The tests that matter are the ones that break it.

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Point `spring.flyway.user` at `app_user` | `permission denied for schema migration`; the process exits non-zero |
| 2 | Edit an applied fixture script | `FlywayValidateException`, checksum mismatch; nothing runs |
| 3 | Give two fixtures in different schema directories the same version number | Flyway fails on the duplicate. This is what makes one global sequence (§13 R5) self-policing |
| 4 | Renumber `V002` (core, the FK) below `V001` (reference, its target) | Migration fails: the referenced table does not exist |
| 5 | Give `migrate` a bad `DB_MIGRATION_PASSWORD`, then `up -d` | `migrate` exits non-zero; `app` and `worker` never start, because `service_completed_successfully` is unmet. This is the behavioural proof of ordering that §9 check 9 cannot make |
| 6 | Re-run the runner on an up-to-date database | Exit 0, nothing applied, history table unchanged |

Prove these on a throwaway branch, link the evidence from the pull request, never merge
the breaks. Break 5 is the one most likely to be skipped and the most expensive to find
later.

**There is deliberately no break for "a script omits its schema qualifier."** Two
revisions tried; both were wrong, and the second made the hazard worse. See §13 R7.

## 9. Verification

Exact commands for the **verifier**, which has only Bash. Clean checkout, Docker active.

```bash
# 1 — build and run the migration integration test
cd code/backend && ./mvnw clean verify -pl migration -am
# Expected: BUILD SUCCESS, and FlywayMigrationIT in the Failsafe summary with tests run > 0

# 2 — the migrate service runs once and exits clean
cd ../.. && docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up migrate
docker compose -f infra/docker/compose.yml ps -a --status exited migrate
# Expected: one row, migrate, "Exited (0)".  -a is required: `ps` alone hides it

# 3 — exactly one history table, in the migration schema, owned by migration_user
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -tAU postgres -d infinevo -c \
  "select count(*), coalesce(string_agg(schemaname||'/'||tableowner, ','), 'NONE')
     from pg_tables where tablename = 'flyway_schema_history';"
# Expected exactly: 1|migration/migration_user
# Counted, not sampled: "0|NONE" is the failure this catches

# 4 — nothing was created in an application schema or in public
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -tAU postgres -d infinevo -c \
  "select count(*) from pg_tables
    where schemaname in ('core','hrms','payroll','reference','public');"
# Expected exactly: 0   — W-06 ships no migration

# 5 — app_user cannot see Flyway's bookkeeping
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -tAU postgres -d infinevo -c \
  "select has_schema_privilege('app_user','migration','USAGE');"
# Expected exactly: f

# 6 — app_user is still refused DDL
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -U app_user -d infinevo -c "create table core.should_fail(id int);" \
  || echo DDL_BLOCKED_SUCCESS
# Expected: ERROR: permission denied for schema core / DDL_BLOCKED_SUCCESS

# 7 — re-running applies nothing
docker compose -f infra/docker/compose.yml up migrate
docker compose -f infra/docker/compose.yml logs migrate | grep -c "is up to date"
# Expected: at least 1

# 8 — the whole stack, and the smoke suite
docker compose -f infra/docker/compose.yml up -d && bash infra/docker/smoke.sh
# Expected: "N passed, 0 failed"

# 9 — app is gated on migrate, declared and healthy
grep -A3 'service_completed_successfully' infra/docker/compose.yml
# Expected: the condition appears under both app and worker, naming migrate
docker compose -f infra/docker/compose.yml ps app
# Expected: app is "Up (healthy)"
# Ordering itself is proved behaviourally by break 5, not by this command

# 12 — the README carries the control R7 relies on
grep -q 'Read the first word after' code/backend/migration/README.md ; echo "exit=$?"
# Expected: exit=0

# 10 — ddl-auto absent; Flyway config confined to the migration module
git grep -nE '^[^#]*ddl-auto[[:space:]]*[:=]' -- code infra ; echo "exit=$?"
# Expected: no output, exit=1
git grep -n 'spring\.flyway' -- code ':(exclude)code/backend/migration' ; echo "exit=$?"
# Expected: no output, exit=1

# 11 — the shared widening broke nothing
cd code/backend && ./mvnw verify -pl shared
# Expected: BUILD SUCCESS, DatabasePrivilegesIT still green (10 tests)
```

| Check | Expected | Result |
|---|---|---|
| 1 `mvnw verify -pl migration -am` | BUILD SUCCESS, `FlywayMigrationIT` ran, tests > 0 | |
| 2 `migrate` | one row, `Exited (0)` | |
| 3 History table | exactly `1\|migration/migration_user` | |
| 4 Application schemas and `public` | exactly `0` tables | |
| 5 `app_user` USAGE on `migration` | exactly `f` | |
| 6 `app_user` DDL in `core` | permission denied | |
| 7 Re-run | "is up to date" ≥ 1 | |
| 8 `smoke.sh` | `0 failed` | |
| 9 `depends_on` declared; `app` healthy | condition present; `Up (healthy)` | |
| 10 `ddl-auto`, stray `spring.flyway` | both absent, exit 1 | |
| 11 `shared` still green | BUILD SUCCESS, `DatabasePrivilegesIT` 10 tests | |
| 12 README carries the schema rule | `exit=0` | |

> **What could pass while this is broken?** Checks 3 and 4 are counted, not sampled —
> "every row is correct" is satisfied by zero rows, the `W-02` failure where 23 of 23
> passed while Keycloak admin login returned 401. Check 1 names the class **and**
> requires tests > 0, because integration tests **skip silently without Docker**. Check 9
> does not claim to prove ordering; break 5 does. **Nothing here claims to catch a script
> that omits its schema qualifier** — §13 R7 says why, and §10 states the residual risk
> rather than hiding it behind a check that would pass regardless. CI is authoritative.

## 10. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Someone later adds `spring.flyway.*` to `app`, putting owner credentials in the application container and dissolving the boundary `02-data-model.md:383` rests on | **High** — the path of least resistance, and Spring Boot invites it | §13 R4's CI gate makes it mechanical; one sentence in `migration/README.md` with the citation; break 1 is the standing test |
| **A script omits its schema qualifier.** The table is created in whichever schema Flyway's search path reaches first, outside `core`/`hrms`/`payroll`, so `W-07`'s tenant check never sees it: no `tenant_id`, no RLS policy, no error | Medium, and **accepted for now** | Not mitigated by the database — §13 R7. Three partial controls: the **Always name the schema** section of `migration/README.md` (§5a), which ends with a one-line instruction a reviewer can execute; `03-code-structure.md:144`, "every script reviewed like code"; and the application failing fast, because an entity mapped to `core.employee` cannot find a table that landed elsewhere. **`W-07` makes it mechanical.** The exposure is the interval between the two tickets, during which `W-07` and `W-09` write the only scripts |
| Boot's Flyway autoconfiguration changes behaviour on a Spring Boot upgrade | Medium | `application.yml` sets every option explicitly rather than relying on a default, including `create-schemas: false`, `fail-on-missing-locations: true`, and `default-schema` listed within `schemas`. Break 1 and check 3 both fail loudly if it stops running |
| Widening `PostgresTestContainerInitializer`'s members invites other modules to reach into the test harness in ways it was not designed for | Low | It is already a `public` class used across modules (`core`, `hrms`, `payroll` all depend on the test-jar); the constants were package-private by omission, not by design. Check 11 proves `shared` still passes |
| The fifth schema drifts from docs that say "four schemas" | Medium | §13 R1's `sync-docs` pull request is a condition of approval, not a follow-up |
| Two tickets take the same version number in the shipped sequence | Medium | Flyway fails loudly on a duplicate (break 3) |
| Flyway 10.x needs `flyway-database-postgresql` separately; without it the runner fails with an unhelpful message | Medium | Named in file 1; break 1 surfaces it immediately |
| Shipping no real migration means the mechanism is exercised only by tests | Medium | The fixtures exercise every path a real script takes — ownership, ordering, grants, checksum, idempotency. `W-09` inherits a proven runner |
| `app` start time grows because it waits on `migrate` | Low | The runner does nothing on an up-to-date database; `start_period` already allows 90s |

## 11. Rollback

**Nothing is in production. Rollback is `git revert` of the pull request**, plus what a
revert does not remove.

| Left behind by a revert | How to clear it |
|---|---|
| The `migration` schema, its history table, and the `public` revoke, in a developer's local volume | `docker compose -f infra/docker/compose.yml down -v`, then `up -d`. W-05's provisioning rebuilds roles and schemas from scratch |
| Testcontainers state | None — disposable per run |
| Registry images | None — W-06 pushes nothing; the `images` job builds dev targets and does not push (`ci.yml:190-212`) |
| Azure resources, secrets, roles | None |
| The `sync-docs` pull requests from §13 R1 and R3, if merged | Revert them separately. They are deliberately their own pull requests |

**After the first real tables exist, this stops being true.** Forward-only means a merged
migration cannot be undone by reverting the commit (`05-azure-architecture.md:130`).
W-06 is the last ticket in the chain where rollback is free.

## 12. Done when

**V** = the **verifier** runs it from §9. **R** = a reviewer reads it; no Bash command
can check it.

| # | V/R | Criterion |
|---|---|---|
| 1 | **V** | `./mvnw clean verify -pl migration -am` is BUILD SUCCESS and `FlywayMigrationIT` ran with tests > 0 *(§9.1)* |
| 2 | **V** | The `migrate` service runs once and exits 0 *(§9.2)* |
| 3 | **V** | Exactly `1\|migration/migration_user` for `flyway_schema_history` *(§9.3)* |
| 4 | **V** | `core`, `hrms`, `payroll`, `reference`, `public` hold exactly 0 tables *(§9.4)* |
| 5 | **V** | `has_schema_privilege('app_user','migration','USAGE')` is `f` *(§9.5)* |
| 6 | **V** | `app_user` refused `CREATE TABLE` in `core` *(§9.6)* |
| 7 | **V** | Re-running applies nothing and logs "is up to date" *(§9.7)* |
| 8 | **V** | `smoke.sh` reports `0 failed`, including the two new checks *(§9.8)* |
| 9 | **V** | `service_completed_successfully` on `migrate` declared for both `app` and `worker`, and `app` reaches `Up (healthy)` *(§9.9)* |
| 10 | **V** | `ddl-auto` set nowhere; `spring.flyway` nowhere under `code/` outside the `migration` module *(§9.10)* |
| 11 | **V** | `mvnw verify -pl shared` is green and `DatabasePrivilegesIT` still runs 10 tests — the widening in file 13 broke nothing *(§9.11)* |
| 12 | **V** | `FlywayMigrationIT` proves, in the test container: every fixture table owned by `migration_user`; `app_user` can `SELECT`/`INSERT`/`UPDATE`/`DELETE` the `core` fixture **with no grant written anywhere but `infra/postgres/03-grants.sql`**, proving the `ALTER DEFAULT PRIVILEGES FOR ROLE migration_user` chain at `:25-35` is live; `app_user` reads but cannot write in `reference`; `readonly_user` reads and cannot write |
| 13 | **V** | Cross-schema ordering proved by `core.tenant_fixture`'s foreign key to `reference.country_fixture` resolving |
| 14 | **R** | Breaks 1-6 in §8 each reproduced, output linked from the pull request, none merged |
| 15 | **V** | `migration/README.md` carries the **Always name the schema** section of §5a **verbatim, including its final reviewer instruction** — `grep -q 'Read the first word after'` returns 0 *(§9.12)*. A reviewer also confirms the rest: the single global version sequence; forward-only; `tenant_id` on every table outside `reference`; the index standard; expand/contract; that the shipped `V001__` is unclaimed; that Flyway must never run inside `app` or `worker` |
| 16 | **V** | No `migration_user` credential in `code/backend/app` or `code/backend/worker` — `git grep -n migration_user -- code/backend/app code/backend/worker` returns nothing, exit 1 |

---

## 13. Decisions taken

Ruled by the founder, **2026-09-17**, on the six questions raised by rev3.

| # | Question | Ruling |
|---|---|---|
| **R1** | Where `flyway_schema_history` lives | **A dedicated `migration` schema**, owned by `migration_user`, on which `app_user` and `readonly_user` are granted nothing. `W-07`'s build check stays absolute — no exemption list |
| **R2** | What runs Flyway | **Its own short-lived process** — a Spring Boot application in the `migration` module, a compose service locally, a Container Apps job in Azure. `app` never holds an owner credential. Settles `02-data-model.md:380` against `04-runtime-containers.md:131`: the job runs at stack start and `app` waits on it |
| **R3** | `ddl-auto` absent or `validate` | **Absent everywhere.** Hibernate defaults to `none`. `CONVENTIONS.md:18` rule 4 is reworded; the three gates are untouched |
| **R4** | Gate `spring.flyway` outside the `migration` module | **Yes, in W-06** — one `ci.yml` step beside the `ddl-auto` gate (file 14, task T4) |
| **R5** | Version numbering across the four trees | **One global sequence.** `V001__`, `V002__` … unique across all four directories; within a batch, `reference` numbered below `core` below the modules. A duplicate is a loud Flyway failure |
| **R6** | Is `reference` exempt from `tenant_id` | **Yes, exempt.** `D-08` and `02-data-model.md:16` stand; `CONVENTIONS.md:21` rule 7 and root `CLAUDE.md` hard rule 7 are reworded to name the exception |
| **R7** | How to stop a script omitting its schema qualifier *(ruled 2026-09-17, after the rev4 review)* | **Not by the database.** The `public` revoke is dropped. The rule goes in `migration/README.md` (§5a) with an instruction a reviewer can execute, and mechanical enforcement moves to **`W-07`**, which already builds a check over migration scripts. Rev3 and rev4 each tried a database-level guard; the first was wrong and the second made the hazard worse by putting a writable schema at the head of Flyway's search path. Residual risk stated in §10, not hidden |

**Three `docs/` rewordings are now owed. None is W-06's own work** — `docs/` changes only
through `sync-docs` with an approved diff (hard rule 3).

| From | Change | When |
|---|---|---|
| R1 | "four schemas" → five, naming `migration` as non-application: `02-data-model.md:14`, `04-runtime-containers.md:120`, `.claude/work/active-work.md:28,45` | **Condition of W-06 approval** — bundle with R3 |
| R3 | `CONVENTIONS.md:18` rule 4 — `ddl-auto` is never set, rather than "moves to `validate`" | **Condition of W-06 approval** — bundle with R1 |
| R6 | `CONVENTIONS.md:21` rule 7 and root `CLAUDE.md` hard rule 7 — name `reference` as the exception | **Separate pull request, before `W-07` starts.** It touches `CLAUDE.md` and is `W-07`'s prerequisite, not W-06's. Note that `sync-docs` covers root `CLAUDE.md` by declared scope (`sync-docs/SKILL.md:21`) but `guard-edit.mjs:28-35` does **not** block it mechanically — the route is policy, so take it deliberately |

---

## Review findings closed

Against `.claude/outputs/2026-09-17-review-spec-W-06-rev3.md`.

### Revision 5 — against `-review-spec-W-06-rev4.md`

| ID | Sev | Closed by |
|---|---|---|
| J-1 | High | The claim is gone with the paragraph. §8 has no break asserting an unqualified `CREATE` fails; §5, §9 check 6, §9's footnote and §12 no longer depend on one |
| J-1b | High | §13 R7 and §10 — the hazard is now **named and accepted** with three partial controls and an owner (`W-07`), instead of being recorded as mitigated by a revoke that could not mitigate it |
| J-2 | High | `03-grants.sql:39-56`, was `:39-57` on a 56-line file |
| J-3 | Medium | §9 check 11 drops `-q`, matching `ci.yml:48` and `W-05-postgres-schemas.md:198` |
| J-4 | Low | §13's route note — `sync-docs/SKILL.md:21` covers root `CLAUDE.md` by policy, though `guard-edit.mjs:28-35` does not block it mechanically |
| J-5 | Low | **Harness, not this spec.** Unchanged and recorded below |

### Revision 4 — against `-review-spec-W-06-rev3.md`

| ID | Sev | Closed by |
|---|---|---|
| H-1 | High | §4 file 13 and §6 T2 — the six constants and `getJdbcUrl()` widened to `public`; `shared` regains a small task and §4 "Not touched" is corrected. §9 check 11 and §12 item 11 prove the widening broke nothing |
| H-2 | Medium | §4 file 3 — `migration` is listed in `schemas:` as well as `default-schema:`, so placement does not depend on Flyway's handling of a default outside that list |
| H-3 | Medium | §1 adds the baseline measurement with the command; §5 explains that the property may already hold and why the explicit `REVOKE` is still right; §8 break 5 and §12 item 13 make the discriminating test an executable one; §9 check 6 says plainly it is not the proof |
| H-4 | Low | §6 — "T1, T2 and T4 can run in parallel; T3 needs both T1 and T2" replaces the contradictory prose |
| H-5 | Low | **Harness, not this spec.** `verify/SKILL.md:27` expects "§9 Verification", `reviewer.md:39` expects "§9 … against §13's done-when"; `W-05` ships §8/§11. Rev4 keeps Verification at §9; Done when is §12 and Decisions §13, which now matches `reviewer.md`'s §13 by coincidence rather than design. Durable fix belongs in the template and the two agents |

*Analysis: `.claude/outputs/2026-09-17-analyze-w06-main.md`. Citation sweep:
`-rev3-citations.md`, 42 of 42 resolving after the `02-schemas.sql:8-24` correction.
Every file changed by this spec was opened and its relevant line quoted before the change
was described (`infra-task` step 8).*
