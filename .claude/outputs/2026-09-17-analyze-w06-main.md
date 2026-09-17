# Evidence sweep — W-06 Flyway (GitHub #7) — 2026-09-17

Scope: `main` @ 32829f5, read-only. No side branches consulted, no W-06 draft spec read.

---

## Q1 — What does the design require of W-06?

All citations `docs/target-state/` (target state, not yet built).

| # | Rule | Citation |
|---|---|---|
| 1 | W-06 scope: "Migration runner · Script conventions · Per-schema ordering · Pipeline validation", depends on W-05, blocks W-07 | `docs/target-state/08-work-plan.md:47` |
| 2 | Build detail: "runner, script conventions, per-schema ordering, pipeline validation." **Done when:** "`ddl-auto` is absent from every configuration file." Watch: "script order across four schemas — `reference` first, then `core`, then modules" | `docs/target-state/09-build-order.md:173` |
| 3 | W-06 → W-07 → W-08 is "the one genuinely rigid chain in the project," strictly sequential | `docs/target-state/09-build-order.md:59,66` |
| 4 | Migration scripts live at `code/backend/migration/{core,hrms,payroll,reference}/`, e.g. `V001__tenant.sql` naming | `docs/target-state/03-code-structure.md:128-136` |
| 5 | Rules: "Flyway only. `ddl-auto` disabled permanently"; "Numbered, forward-only. Never edit an applied script"; "One script set applied together, in schema order: `reference` → `core` → `hrms` → `payroll`"; "Reference data seeded by migration, not by application startup"; "Every script reviewed like code" | `docs/target-state/03-code-structure.md:138-144` |
| 6 | Module layout: `migration/` = "Flyway scripts, one tree per schema" | `docs/target-state/03-code-structure.md:32-33` |
| 7 | `D-09`: "Postgres, with Flyway. `ddl-auto` disabled permanently" — reason: RLS is the isolation mechanism, `ddl-auto` silently alters schema with no record | `docs/target-state/07-decisions.md:21` |
| 8 | `02-data-model.md`: "Migrations. Flyway. One numbered script set covering all four schemas, applied together. `ddl-auto` is disabled permanently." | `docs/target-state/02-data-model.md:21` |
| 9 | `migration_user` owns `core`, `hrms`, `payroll`, `reference` "to execute Flyway DDL migrations seamlessly" — W-05 spec, states W-06 "Attaches Flyway migration runner using `migration_user`" | `docs/target-state/features/W-05-postgres-schemas.md:253,170` |
| 10 | `04-runtime-containers.md`: "Migrations run on start, so the database is always current" is marked `⏳ W-06` (not yet built); notes W-05 created schemas and roles "and no tables, so it cannot collide with Flyway later" | `docs/target-state/04-runtime-containers.md:131` |
| 11 | W-01 skeleton: `migration/` dir created empty "Flyway script tree (empty; W-06 fills it)"; "Flyway runner and migration scripts" assigned to W-06 | `docs/target-state/features/W-01-repository-skeleton.md:60,81,195-196` |
| 12 | W-04 test foundation: "`W-06` must switch the initializer to the bootstrap script when the four schemas arrive" — **already done by W-05** per an inline update note; "Flyway schema migrations. Owned by `W-06`" listed as deferred | `docs/target-state/features/W-04-test-foundation.md:94,96,48` |

**`docs/CONVENTIONS.md` migration rule:**
- Hard rule 4: "**Flyway for migrations — never `ddl-auto`.** Once Flyway lands, `ddl-auto` moves to `validate` and every schema change is a versioned script" — enforcement: review; see DEBT-002 (`docs/CONVENTIONS.md:18`).
- No naming-convention prose in `CONVENTIONS.md` itself beyond the pointer above; naming (`V001__x.sql`) is defined in `03-code-structure.md:132` only.

**Decision IDs governing W-06:** `D-09` (Postgres + Flyway, `docs/target-state/07-decisions.md:21`), `D-38` (Java 21, cited by W-05 as also affecting the migration runner's toolchain, `docs/target-state/features/W-05-postgres-schemas.md:14`).

**No naming/versioning rule beyond `V001__tenant.sql, V002__employee.sql, ...`** is given (`03-code-structure.md:132`) — no statement on schema-qualified filenames, checksum policy, or "repeatable" (`R__`) scripts. **Not found** elsewhere in `docs/target-state/`.

---

## Q2 — What exists on `main` today?

### Flyway dependency / plugin / config
**None found anywhere in `code/backend`.** Grep for `flyway|Flyway` across all `.xml`, `.yml` under `code/backend` returns only prose comments, no dependency or plugin declaration:
- `code/backend/worker/pom.xml:29` — comment only
- `code/backend/migration/pom.xml:15` — comment only ("Empty until W-06 adds the runner")
- `code/backend/app/pom.xml:29` — comment only

No `org.flywaydb:flyway-core`, no `spring-boot-starter-flyway` (n/a, doesn't exist), no `spring.flyway.*` property anywhere.

### `code/backend/migration` module
- `pom.xml` (`code/backend/migration/pom.xml:1-19`): packaging inherited (jar, no `<packaging>` override), no `<dependencies>` block at all. Description states: "Flyway migration scripts, one tree per schema. Empty until W-06 adds the runner and the conventions."
- Contents: only 4 placeholder files, `code/backend/migration/src/main/resources/db/migration/{core,hrms,payroll,reference}/.gitkeep` — empty directories reserved, no `.sql` files.
- Listed in parent `<modules>` (`code/backend/pom.xml:41`), so it does build, producing an empty jar (`code/backend/migration/target/migration-0.1.0-SNAPSHOT.jar` present from a prior build).
- **Nothing depends on it** — no other module (`app`, `worker`, `core`, `hrms`, `payroll`, `shared`) declares `<artifactId>migration</artifactId>` as a dependency; not present in parent `dependencyManagement` (`code/backend/pom.xml:56-94`).

### `infra/postgres/`
Five files, all frozen-schema/role provisioning, **no Flyway invocation**:
| File | Purpose | Roles/schemas | Grants |
|---|---|---|---|
| `infra/postgres/01-roles.sql:1-42` | Creates 4 roles: `app_user`, `migration_user`, `readonly_user`, `keycloak_user`, all `NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS`; sets passwords via psql vars | — | — |
| `infra/postgres/02-schemas.sql:1-24` | Creates schemas `core`, `hrms`, `payroll`, `reference`, each `AUTHORIZATION migration_user`, then `ALTER SCHEMA ... OWNER TO migration_user` (idempotent re-provision) | Owner of all 4 schemas = `migration_user` | — |
| `infra/postgres/03-grants.sql:1-56` | Revokes CONNECT from PUBLIC, grants CONNECT to 3 roles; `GRANT USAGE, CREATE` on all 4 schemas to `migration_user`; `GRANT USAGE` only to `app_user`/`readonly_user`; `ALTER DEFAULT PRIVILEGES FOR ROLE migration_user` grants `SELECT,INSERT,UPDATE,DELETE` on future tables in `core/hrms/payroll` to `app_user`, `SELECT` only on `reference`; readonly gets `SELECT` everywhere; self-check `DO` block that raises if any role is superuser or `BYPASSRLS` | `migration_user` = only role with `CREATE` | `app_user` gets DML via default privileges (future tables), not DDL |
| `infra/postgres/provision.sh:1-37` | Runs the 3 scripts above in order via `psql -v ON_ERROR_STOP=1` | — | — |
| `infra/postgres/README.md:1-17` | Documents order and three consumers: docker compose (`00-bootstrap.sh`), Testcontainers (JDBC, `02`/`03` only), Azure Flexible Server post-deploy | — | — |

**This determines Flyway's landing conditions**: `migration_user` owns the 4 schemas and is the only role with `CREATE`; `app_user` only gets DML on tables that already exist at grant time (default privileges apply to *future* tables created by `migration_user`, not retroactively — so **Flyway must run as `migration_user`**, not `app_user`, for `app_user`'s default-privilege DML grants to apply to Flyway-created tables).

### Test harness
`code/backend/shared/src/test/java/com/infinevo/shared/test/`:
- `PostgresTestContainerInitializer.java` (full file read, 137 lines): starts `postgres:16-alpine` Testcontainer, database `infinevo` (line 52). Provisions by executing `db/provision/01-roles.sql` (with variables), `02-schemas.sql`, `03-schemas.sql`→`03-grants.sql` over JDBC as the container's default superuser (lines 84-93). Declares constants for all 4 roles/passwords including `MIGRATION_USER`/`MIGRATION_USER_PASSWORD` (lines 35-39) and `READONLY_USER` (41-45), but **the Spring datasource is wired only to `app_user`** (lines 128-134) — `MIGRATION_USER` constant is currently unused by any datasource wiring, just declared.
- `AbstractIntegrationTest.java` (full file, 57 lines): documents "Flyway schema migrations. Owned by `W-06`" as still deferred (line 47), confirming no Flyway execution happens in the test bootstrap today.

### `ddl-auto` occurrences
**`code/` and `infra/`** — zero *active* settings; only comments/gates:
- `code/backend/worker/src/main/resources/application.yml:3`, `application-local.yml:6` — comment "There is deliberately no ... ddl-auto ... and there must never be"
- `code/backend/app/src/main/resources/application.yml:3`, `application-local.yml:6` — same comment
- `code/backend/app/pom.xml:29-30`, `worker/pom.xml:29-30` — comments referencing W-06/D-09/DEBT-002
- `infra/docker/smoke.sh:76-81` — active grep gate that **fails** if a real `ddl-auto:`/`ddl-auto=` setting is found under `code/`
- `infra/docker/README.md:94-95` — "No `ddl-auto`, anywhere, ever... schema is owned by Flyway (`W-06`)"

**`legacy/`** — the motivating defect, real active settings:
- `legacy/HRMS_Backend/src/main/resources/application.properties:10` — `spring.jpa.hibernate.ddl-auto=update`
- `legacy/Payroll-Bend-SBoot/src/main/resources/application.properties:5` — `spring.jpa.hibernate.ddl-auto=update`
- Documented as `BUG-004` / `DEBT-002` in `legacy/docs/GAP_INVENTORY.md:30,40`

### CI (`.github/workflows/ci.yml`)
Four jobs, none starts a database or runs migrations:
| Job | What it does | Citation |
|---|---|---|
| `backend` | `./mvnw -B clean verify` (compiles, Spotless, unit+integration tests) — no `docker compose up`, no explicit Postgres service container declared in the workflow (Testcontainers manages its own container inside the Maven run) | `ci.yml:29-58` |
| `frontend` | npm lint + build | `ci.yml:61-91` |
| `static` | (a) legacy/ untouched diff check; (b) **`ddl-auto set nowhere`** — `git grep` gate across whole repo excluding `legacy/, docs/, .claude/, *.md`, matches `ddl-auto` or `DDL_AUTO` env-var spelling too (`ci.yml:170-178`); (c) no float/double money field gate | `ci.yml:111-188` |
| `images` | Builds two *dev* Dockerfiles only (`dev.Dockerfile.backend`, `dev.Dockerfile.frontend`); no push, no compose, no smoke.sh invocation | `ci.yml:200-212` |

**No job runs `infra/docker/smoke.sh` or `docker compose up`** — that script exists (`infra/docker/smoke.sh:76-81`) but is not wired into `ci.yml`; it appears to be a local/manual gate per `infra/docker/README.md`.

### `.claude/` gates already asserting ddl-auto absence
- `.claude/scripts/check-done.mjs:453-461` — gate `"ddl-auto set nowhere"`, same regex as `ci.yml`, used by the merge-gate script (`check-done.mjs`)
- `.claude/hooks/guard-merge.mjs:84` — merge is blocked unless "ddl-auto is set nowhere, money is not a floating-point type, and both builds pass"
- `.claude/agents/verifier.md:22` — verifier checklist: "No `ddl-auto` anywhere | `grep -rn "ddl-auto" code/backend/` | **No matches.** A match is a finding"
- `.claude/agents/reviewer.md:53` — "Flyway for every schema change. `ddl-auto` nowhere"
- `.claude/skills/verify/SKILL.md:38,52` — same grep command; warns "`smoke.sh` passed its `ddl-auto` check on a comment for an hour" (i.e., a known false-negative risk with comment-matching, since fixed by the `^[^#]*` anchor)
- `.claude/skills/merge/SKILL.md:35` — merge checklist: "`ddl-auto` set nowhere | A real setting, not a comment"

None of these gates yet assert anything Flyway-specific (script presence, ordering, or runner wiring) — they only assert the *absence* of `ddl-auto`, which W-06's own "done when" criterion is scoped to (`09-build-order.md:173`).

---

## Q3 — What would building it touch?

| Concern | Finding | Citation |
|---|---|---|
| Module to add the Flyway dependency/runner to | `code/backend/migration/pom.xml` (currently zero dependencies) — would need `org.flywaydb:flyway-core` (+ `flyway-database-postgresql` for FW 10+) and likely a `flyway-maven-plugin` or a runner class invoked from `app`/`worker` startup | `code/backend/migration/pom.xml:1-19` |
| Where the app wires to it | `code/backend/app/pom.xml` already depends on `spring-boot-starter-data-jpa` and `postgresql` (lines 31-39) but **not** on the `migration` module — would need `<dependency><artifactId>migration</artifactId></dependency>` added so Spring Boot's Flyway auto-configuration picks up scripts on the classpath | `code/backend/app/pom.xml:16-40` |
| Testcontainers initializer insertion point | `PostgresTestContainerInitializer.provisionDatabase()` at lines 84-93 currently runs only `01-roles.sql`, `02-schemas.sql`, `03-grants.sql`; a 4th step calling Flyway (e.g. `Flyway.configure().dataSource(...).load().migrate()`) would be inserted **after line 89** (after grants, before `started = true` on line 76). Also the datasource in `initialize()` (lines 128-134) is wired to `app_user` — Flyway needs to run as `migration_user` (the schema owner with `CREATE`), so either a second Flyway-only connection using the `MIGRATION_USER`/`MIGRATION_USER_PASSWORD` constants already declared (lines 36-39, currently unused) or a dedicated Flyway invocation ahead of test datasource wiring | `code/backend/shared/src/test/java/com/infinevo/shared/test/PostgresTestContainerInitializer.java:76-93,128-134` |
| Maven/Spring Boot version constraints | Parent pom pins `spring-boot-starter-parent` **3.3.13** (`code/backend/pom.xml:9-10`) and `java.version`/`maven.compiler.release` **21** (`code/backend/pom.xml:45-46`), enforced by maven-enforcer `requireJavaVersion [21,)` (`code/backend/pom.xml:184-186`). Spring Boot 3.3.x's dependency-management pins Flyway 10.x — no explicit `<flyway.version>` property exists yet in the parent pom, so it would default to Spring Boot's managed version unless overridden | `code/backend/pom.xml:9-10,44-47` |
| Permission conflict: `migration_user` owns schemas vs app/test datasource | Confirmed conflict as designed, not yet resolved in code: `03-grants.sql:26-30` grants `app_user` DML via *default privileges* set `FOR ROLE migration_user` — meaning **only tables created by `migration_user`** (i.e., by Flyway) get these grants automatically. Both `application-local.yml:13` (app) and `PostgresTestContainerInitializer.java:130` (test) connect as `app_user`, which has no `CREATE` (`03-grants.sql:22` grants `app_user` only `USAGE`, not `CREATE`). This is coherent *if* Flyway runs as `migration_user` — but no code path currently runs Flyway as any role. This is the open wiring gap W-06 must close | `infra/postgres/03-grants.sql:21-30`, `code/backend/app/src/main/resources/application-local.yml:9-14`, `PostgresTestContainerInitializer.java:128-134` |
| W-07 dependency on W-06 | W-07 (tenant model / RLS) needs actual tables to attach RLS policies and the tenant-column build check to — currently `migration/` has zero `.sql` scripts, so W-07 cannot start meaningfully until W-06 produces at least one migration script tree. Confirmed strictly sequential: "`W-06` → `W-07` → `W-08` is the one genuinely rigid chain in the project" | `docs/target-state/09-build-order.md:59,66,175` |

---

## Duplicates or contradictions found
- `docs/target-state/features/W-04-test-foundation.md:94` says "`W-06` must switch the initializer to the bootstrap script when the four schemas arrive," but a later inline note in the same file (`:96`) says this was **already done by W-05** — the initializer today (confirmed in code) already runs `01-roles.sql`/`02-schemas.sql`/`03-grants.sql` via JDBC, so this piece of "W-06 scope" per the doc is stale/already delivered by W-05, not an open W-06 task.
- `PostgresTestContainerInitializer.java` declares `MIGRATION_USER`/`MIGRATION_USER_PASSWORD` constants (lines 36-39) that are **not used anywhere** in the class yet — dead code today, presumably reserved for W-06's Flyway wiring.

## Gaps and uncertainties
- No naming/versioning rule beyond the one example line (`V001__tenant.sql, V002__employee.sql`) in `03-code-structure.md:132` — nothing on repeatable migrations, checksums, or baseline-on-migrate policy. **Not found.**
- No target-state doc specifies *which* Maven plugin or Spring Boot auto-config mechanism runs Flyway (plugin vs. `spring-boot-starter-flyway`-style dependency vs. custom runner in `worker`). **Not found** — left open for the W-06 spec itself.
- Whether Flyway runs from `app`, `worker`, or a standalone step in CI/CD is not stated in `08-work-plan.md` or `09-build-order.md`. **Not found.**

## Related GAP_INVENTORY IDs
- `DEBT-002` — No Flyway/Liquibase, `ddl-auto=update` in both legacy backends (`legacy/docs/GAP_INVENTORY.md:40`) — the defect W-06 fixes forward.
- `BUG-004` — Uncontrolled schema drift from `ddl-auto=update` (`legacy/docs/GAP_INVENTORY.md:30`) — same motivating defect.

---

## Analyst conclusions (spot-checked on `main` @ `32829f5`)

Citations re-verified by hand: `docs/target-state/09-build-order.md:173`,
`docs/target-state/03-code-structure.md:131-145`, `docs/target-state/07-decisions.md:21`,
`docs/CONVENTIONS.md:18`, `infra/postgres/03-grants.sql:21-35`,
`code/backend/shared/src/test/java/com/infinevo/shared/test/PostgresTestContainerInitializer.java:36-39,72-89`,
`.github/workflows/ci.yml:170-178`, `code/backend/migration/pom.xml:1-19`,
`code/backend/pom.xml:34-45`, `code/backend/app/pom.xml:17-37`,
`code/backend/app/src/main/resources/application.yml:1-4`. All resolve as reported.

### 1. The stated "done when" is already true and proves nothing

`09-build-order.md:173` — *"Done when: `ddl-auto` is absent from every configuration
file."* That condition holds on `main` today with zero Flyway code present: no
`ddl-auto` setting exists anywhere under `code/` or `infra/`
(`code/backend/app/src/main/resources/application.yml:1-4` says so in a comment), and
CI enforces it (`.github/workflows/ci.yml:170-178`), as do `check-done.mjs:453-461` and
`guard-merge.mjs:84`. A W-06 spec that adopts this criterion unchanged is untestable —
it passes before a line is written. The spec must invent real acceptance criteria:
migrations actually applied, applied **as `migration_user`**, in schema order, and
`app_user` able to DML the resulting tables.

### 2. Four decisions the design does not make

| Open question | Why the design does not answer it |
|---|---|
| **Who runs Flyway** — Spring Boot autoconfiguration in `app`, the `flyway-maven-plugin`, or a standalone job container | `03-code-structure.md:128-145` states conventions only; no doc names a mechanism. Container Apps (`D-10`) makes a migration job plausible, but nothing says so |
| **Second datasource for `migration_user`** | App runtime connects as `app_user` (`infra/postgres/03-grants.sql:22`), which has no `CREATE`. Flyway inside `app` needs its own `spring.flyway.user/password`, a second credential in the runtime container |
| **One Flyway instance or four** | `reference` → `core` → `hrms` → `payroll` ordering (`03-code-structure.md:142`) across four schemas with four script trees. One instance with four `locations` and one history table, or four instances each with its own history table, is a real fork with different failure modes. Undecided in every doc |
| **Checksum / repeatable-script policy** | "Numbered, forward-only. Never edit an applied script" (`03-code-structure.md:141`) is the only rule. Nothing on `R__` repeatable scripts, baselines, or `validateOnMigrate` |

### 3. Structural facts that constrain the build

- `migration` is a module with **no dependencies and no dependents**
  (`code/backend/migration/pom.xml:1-19`; `app/pom.xml:17-19` depends on `core`, `hrms`,
  `payroll` only). Whichever process runs Flyway must be made to depend on it, or the
  scripts are not on that process's classpath.
- Scripts on disk sit at `code/backend/migration/src/main/resources/db/migration/{reference,core,hrms,payroll}/`
  (four `.gitkeep` files), i.e. classpath `db/migration/<schema>`. That is the path
  Flyway `locations` must use.
- The Testcontainers insertion point is
  `PostgresTestContainerInitializer.java:89` — after `03-grants.sql`, before
  `started = true` (line 76). `MIGRATION_USER` / `MIGRATION_USER_PASSWORD`
  (lines 36-39) are declared and unused, reserved for exactly this.
- Spring Boot **3.3.13**, Java **21** (`code/backend/pom.xml:9,45`) — Flyway version
  comes from Boot's BOM unless overridden; Flyway 10.x splits Postgres support into a
  separate `flyway-database-postgresql` artifact, which must be added explicitly.
- `ALTER DEFAULT PRIVILEGES FOR ROLE migration_user` (`infra/postgres/03-grants.sql:25-35`)
  means `app_user`'s DML rights attach **only** to tables `migration_user` creates. If
  any migration ever runs as another role, the app silently loses access to those tables.
  This is the single hardest acceptance criterion W-06 should assert.

### 4. Pipeline validation has nowhere to land

`09-build-order.md:173` lists "pipeline validation" as W-06 scope, but `ci.yml` has no
job that starts a database outside Testcontainers — no `docker compose up`, no
`infra/docker/smoke.sh`. This is `F-18` on ticket #112. Either W-06 validates migrations
through the existing `backend` job's Testcontainers path, or it must first add the
stack-running CI step that #112 owns. The spec has to pick one.

### 5. Doc drift

| Doc | Says | Code says |
|---|---|---|
| `docs/target-state/03-code-structure.md:131-136` | Script tree is `migration/{core,hrms,payroll,reference}/` | Actual tree is `code/backend/migration/src/main/resources/db/migration/{...}/` — the doc omits the Maven resource path that Flyway `locations` depends on |
| `docs/target-state/features/W-04-test-foundation.md:94` | "`W-06` must switch the initializer to the bootstrap script" | Delivered by `W-05`; the same file corrects itself at `:96`. The line is stale as W-06 scope |

Neither is fixed here — that is `sync-docs`.

### 6. Proposed GAP entries

**None.** Every defect this sweep touched is already tracked: `BUG-004` / `DEBT-002`
(`ddl-auto` in the frozen backends — `legacy/HRMS_Backend/src/main/resources/application.properties:10`,
`legacy/Payroll-Bend-SBoot/src/main/resources/application.properties:5`), and `F-18` on
ticket #112 (CI never runs the stack).

---

# Part 2 — W-06 functionality, for writing the spec from scratch

Scope of this part: **what the feature must do**. All citations are `docs/target-state/`
(target state), except where marked `legacy/`.

## A. The four deliverables

`08-work-plan.md:47` — *"Migration runner · Script conventions · Per-schema ordering ·
Pipeline validation"*. Delivers no end-user feature (`Delivers` column is `—`); needs
only `W-05`.

## B. Functional rules the design already fixes

| # | Rule | Source |
|---|---|---|
| 1 | Flyway is the only mechanism that changes schema. `ddl-auto` disabled permanently | `07-decisions.md:21` (`D-09`), `CONVENTIONS.md:18` |
| 2 | Migrations run as `migration_user` — **the migration step in the pipeline only** | `02-data-model.md:380`, `05-azure-architecture.md:94,126` |
| 3 | **One numbered script set covering all four schemas, applied together** | `02-data-model.md:21` (rule 9) |
| 4 | Applied in schema order `reference` -> `core` -> `hrms` -> `payroll` | `03-code-structure.md:142`, `09-build-order.md:173` |
| 5 | Numbered, forward-only. Never edit an applied script | `03-code-structure.md:141` |
| 6 | No automatic schema rollback — a bad change needs a new script | `05-azure-architecture.md:130` |
| 7 | Every migration leaves the **previous** application version able to run: add columns before using them, drop a release later | `05-azure-architecture.md:133` |
| 8 | Reference data is seeded **by migration**, not by application startup | `03-code-structure.md:143` |
| 9 | Script tree is one directory per schema, `V001__tenant.sql` style | `03-code-structure.md:131-136` |
| 10 | Every script reviewed like code | `03-code-structure.md:144` |
| 11 | Locally, "migrations run on start, so the database is always current" | `04-runtime-containers.md:131` |

## C. The one contradiction the spec must resolve

Rule 2 and rule 11 disagree about **where the runner lives**.

- `02-data-model.md:380` — `migration_user` is used by *"the migration step in the
  pipeline only"*.
- `05-azure-architecture.md:126` — *"Migration runs as a separate step before the new
  revision takes traffic, using `migration_user`"*.
- `04-runtime-containers.md:131` — *"Migrations run on start, so the database is always
  current"*, marked as pending `W-06`.

The constraint that decides it is `02-data-model.md:383`: **"The application never
connects as an owner. That single constraint is what makes row-level security a real
boundary rather than a suggestion."** Spring Boot autoconfigured Flyway inside `app`
would put `migration_user` credentials in the application container, which breaks that
sentence even if the runtime datasource stays `app_user`. So the runner is a separate
execution — a migration job/profile that shares the `migration` module's scripts — and
rule 11 means *the local stack runs that job before `app` becomes healthy*, not that
`app` migrates itself. The spec should say this in one sentence and make it an
acceptance criterion, because it is the only part of W-06 that can be got wrong
irreversibly.

## D. Scope boundary — what W-06 does **not** do

| Belongs to | Not W-06 |
|---|---|
| `W-07` | `tenant_id` standard, RLS policies, role grants, the build check that fails on an unscoped table (`08-work-plan.md:49`) |
| `W-09` | `reference` schema tables and the seed — 15 tables, tax masters and lookups (`08-work-plan.md:51`, `02-data-model.md:180-190`). Parallel with `W-07`, needs only `W-06` |
| `W-13`+ | Every domain table |

**Consequence the spec must face:** on the day W-06 merges there are **no domain tables
to migrate**. The four script trees hold four `.gitkeep` files. So W-06 must define what
its own first migration is — a baseline that creates only the Flyway history table(s),
or a deliberate proof script that is later superseded. Without one, "the runner works"
is unprovable and `W-09` inherits an untested mechanism.

## E. Indexing standard that migrations must carry forward

`02-data-model.md:363-372` sets the minimum for every table a migration creates:
`tenant_id` leading on every index of a tenant-scoped table, an index on every foreign
key, an index on every column used in `WHERE`/`ORDER BY`/`JOIN` on a list screen, a
composite `(tenant_id, employee_id, period)` for anything per pay period, and the
soft-delete column inside the index. This is the fix for `DEBT-018` — zero `@Index`
declarations across 99 Payroll entities (`legacy/`, frozen). W-06 cannot enforce it
(no tables yet), but the script conventions it writes are the right place to state it.

## F. Acceptance criteria that are actually runnable

The ticket's stated "done when" — `ddl-auto` absent — is already true on `main` and
gated in CI, so it tests nothing. Runnable replacements, each an assertion a test or
script can make:

1. A migration applied to an empty provisioned database creates its objects, and the
   Flyway history table exists in the expected schema.
2. The connection that applied it was `migration_user` — assert
   `pg_tables.tableowner = 'migration_user'` for a created table.
3. `app_user` can `SELECT`/`INSERT`/`UPDATE`/`DELETE` that table without any explicit
   grant, proving the `ALTER DEFAULT PRIVILEGES` chain in `infra/postgres/03-grants.sql:25-35`
   is live.
4. `app_user` is still refused `CREATE TABLE` afterwards.
5. `readonly_user` can `SELECT` and cannot write.
6. Scripts apply in `reference` -> `core` -> `hrms` -> `payroll` order — provable by a
   script in a later schema that depends on an object from an earlier one.
7. Re-running the migration is a no-op; a modified applied script fails the checksum.
8. The local stack comes up with migrations applied — `smoke.sh` gains a check that a
   migrated object exists.
9. `app` starts against an already-migrated database without attempting DDL.

## G. Open design choices the spec must make (no doc decides them)

| Choice | Options | What tips it |
|---|---|---|
| Runner mechanism | Boot autoconfig in a migration profile / `flyway-maven-plugin` / standalone job container | Section C: must not be `app`'s runtime path. A job container matches `D-10` Container Apps |
| One Flyway instance or four | One with four `locations` and one history table / four instances, one history table per schema | Rule 3 says "one script set applied together", which points at one instance; but per-schema `V001` numbering (rule 9) collides in a single version namespace unless prefixes differ |
| Where the history table lives | `core` / a dedicated schema / one per module schema | Follows the choice above |
| Version numbering across four trees | Global sequence / per-schema prefix (`V1_001__`) | Rule 5 forward-only and rule 4 ordering both depend on this |
| Baseline / repeatable policy | `baselineOnMigrate`, `validateOnMigrate`, whether `R__` scripts are allowed at all | `03-code-structure.md:141` bans editing applied scripts; repeatable scripts are edited by definition, so the spec should either ban them or carve out the exception |
| Flyway artifact | Boot 3.3.13 pins Flyway 10.x, which needs `flyway-database-postgresql` as a separate dependency | `code/backend/pom.xml:9` |

## H. Note forward to W-07

`infra/postgres/01-roles.sql` creates `migration_user` `NOBYPASSRLS`
(`infra/postgres/README.md:7`), yet `02-data-model.md:380` lists its RLS as "Bypassed".
Both are true only because a table **owner** is exempt from its own RLS policies unless
the table is declared `FORCE ROW LEVEL SECURITY`. W-06 does not create policies, so
nothing breaks here — but whichever way W-06 writes its script conventions, W-07 will
need `FORCE ROW LEVEL SECURITY` or `migration_user` silently sees every tenant's rows.
Worth one line in W-06's conventions so W-07 does not rediscover it.
