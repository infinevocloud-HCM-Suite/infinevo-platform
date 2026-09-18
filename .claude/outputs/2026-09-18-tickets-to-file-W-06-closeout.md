# Tickets to file — W-06 close-out — 2026-09-18

Two tickets fell out of the `/review` + `/verify` of `W-06-flyway-migrations` for merge.
**Neither could be filed from this session — `gh issue create` was denied by the
permission classifier (External System Writes).** Bodies are ready to paste.

PR #118 was closed successfully, with the explanation on the PR.

---

## Ticket 1

**Title:** Gate bypass — PR #119 shipped 603 lines of W-06 code as "documentation only"

**Labels:** `bug` · `stream-G` · `skill-INFRA` · `size-S` · `needs-founder`

### What happened

PR #119, titled **"docs — W-49 as built: production images, roles not profiles,
D-47/48/49"**, merged as `365a319` on 2026-09-18. Its body opens with:

> Documentation only. No code changed.

It changed 26 files, `+1116 / -65`. Fifteen of them are the complete W-06 Flyway
changeset:

```
code/backend/migration/README.md                              +118
code/backend/migration/pom.xml                                 +85
code/backend/migration/.../MigrationApplication.java           +35
code/backend/migration/.../application.yml                     +36
code/backend/migration/.../FlywayMigrationIT.java             +231
code/backend/migration/.../db/migration-test/{4 fixtures}      +44
code/backend/shared/.../PostgresTestContainerInitializer.java   16 +-
.github/workflows/ci.yml                                        +9
infra/docker/compose.yml                                       +19
infra/docker/smoke.sh                                           +6
infra/postgres/02-schemas.sql                                   9 +-
infra/postgres/03-grants.sql                                    +9
```

The same PR body states, under "Outstanding, and not fixed by this":

> **`W-06` Flyway is CLOSED as a ticket while its code is not on `main`.**

The commit that says this is the commit that put it there.

### Why this matters more than the outcome

The code is fine — review and verify both passed it at `ad3f3c6` and CI ran its
integration tests green. That is luck, not a control. 603 lines of backend code, Docker
Compose services, Postgres grants and a CI step reached `main` through a PR that declared
itself documentation-only, with no `/review`, no `/verify` and no done-check against them.

Same class as #100 (gate 5 had no route for a `docs/` file that is not a ticket spec) and
the four bypasses found in its first fix. Adjacent to #101 and #104, both open.

### What to work out

1. **Which gate should have caught it, and why it didn't.** A docs-route PR carrying
   `code/`, `infra/` and `.github/` paths is the signature to detect — the mismatch
   between the declared route and the actual paths.
2. **Whether the docs route can carry code at all.** If `sync-docs` produced this diff,
   the skill's diff scope is the defect. If it was added by hand after approval, the
   approval-binds-content mechanism from #100 did not extend to this route.
3. **`guard-merge` / gate 10** — `.claude/outputs/2026-09-18-docs-approval-containerisation-merged.md`
   was added by the same PR. Check whether the approval covered the code paths or only
   the `docs/` ones.

### How it was found

`/review` + `/verify` of `W-06-flyway-migrations` for merge on 2026-09-18. The branch
turned out to have nothing left to deliver: `git diff main origin/W-06-flyway-migrations`
is empty across every W-06 path. PR #118 closed as a result.

Reports: `.claude/outputs/2026-09-18-review-W-06-rev2.md`,
`.claude/outputs/2026-09-18-verify-W-06-rev2.md`.

---

## Ticket 2

**Title:** W-06 fix-forward — eight review findings that reached `main` unaddressed

**Labels:** `stream-B` · `skill-DATA` · `size-S` · `ready`

### Why this is a new ticket

W-06's code is on `main` (via #119 — see ticket 1) and issue #7 is closed. These findings
were raised by `/review` against `ad3f3c6`, the exact commit that landed, and none was
fixed before it did. They are now fix-forward work on `main`.

Full detail with `file:line` in `.claude/outputs/2026-09-18-review-W-06-rev2.md`.

### Unmet acceptance criteria

| Finding | Sev | What | Where |
|---|---|---|---|
| **F-6** | Medium | §12 item 12 requires `app_user` proved able to SELECT/INSERT/UPDATE/DELETE the core fixture — the live test of the `ALTER DEFAULT PRIVILEGES` chain at `03-grants.sql:27-29`. Only INSERT and SELECT are exercised | `FlywayMigrationIT.java:124-135` |
| **F-14** | Low | No test asserts exit behaviour — neither exit-0 on success nor §8 break 1's non-zero exit. §12 item 14 (breaks 1-6 reproduced) was never evidenced; PR #118's `## Verification` section was empty | `FlywayMigrationIT.java` |

### Defects in what shipped

| Finding | Sev | What | Where |
|---|---|---|---|
| **F-13** | Medium | The shipped `spring.flyway.locations` list is the one Flyway property no test covers — the IT replaces all four entries with `db/migration-test/*`. A typo in the shipped list fails only at real run time; `W-07`/`W-09` are the first to discover it. Same shape of gap that let F-3 reach merge review | `application.yml:20-24` vs `FlywayMigrationIT.java:48-52` |
| **F-12** | Medium | `pom.xml` comment says `spring-boot-starter-jdbc` "provides DataSourceAutoConfiguration required for Flyway auto-configuration" — the next file **excludes** `DataSourceAutoConfiguration`, and it is not required. What is required is `spring-jdbc` (`SimpleDriverDataSource`, `DataSourceBuilder`). The starter also drags in unused HikariCP | `pom.xml:27-31` vs `MigrationApplication.java:16,26` |
| **F-5** | Medium | The §13 R4 CI gate cannot fire. It greps the dotted `spring\.flyway`; every `application.yml` uses nested YAML. A `flyway:` block added under `spring:` in `app/application.yml` — the "High likelihood" risk this gate solely mitigates — passes green forever | `.github/workflows/ci.yml:180-187` |
| **F-7** | Medium | The README's only local instruction, `docker compose up migrate`, fails for existing developers. `00-bootstrap.sh` runs only on a fresh volume; on a pre-W-06 `pgdata` the `migration` schema is absent and, with `create-schemas: false`, Flyway dies opaquely. No `down -v` note outside §11 | `migration/README.md:107-115` |
| **F-14** | Low | `MigrationApplication.launch(...)` exists solely for the IT; `main()` calls `SpringApplication.run` directly, so the two entry paths can diverge and `main()`'s stays uncovered. The test never closes the `ConfigurableApplicationContext` | `MigrationApplication.java:29-32`, `FlywayMigrationIT.java:44-55` |
| **F-15** | Low | `D-46` mis-cited for the DataSource/pooling decision. `07-decisions.md:57` shows `D-46` is the `ddl-auto` ruling; the `migration`-schema ruling is `D-45` | `MigrationApplication.java:16` |
| **F-8** | Low | `adminConnection()` is commented "postgres superuser" but connects as `migration_user`, and is byte-for-byte `migrationUserConnection()`. Assertions labelled "admin" are run by the object owner | `FlywayMigrationIT.java:205-218` |
| **F-11** | Low | `smoke.sh` gains three checks where §4 file 12 specified two; the extra schema-ownership check is unspecified and duplicated by the provisioning self-check | `infra/docker/smoke.sh:37-38` |

### Related, not in scope here

- **F-10 / spec drift** — goes through `sync-docs`, not this ticket.
- **F-16** — no production image carries `migration.jar`; `backend.Dockerfile:47-52` copies
  only `app.jar` and `worker.jar` and `backend-entrypoint.sh:14-24` rejects any other
  `INFINEVO_ROLE`, while spec §4 says "the same jar is what `W-54` runs as a Container
  Apps job". **Note for W-54.**
- **#117** — Testcontainers does not detect Docker locally, so both IT suites skip under a
  green build on Windows. Green on CI. Already tracked.
