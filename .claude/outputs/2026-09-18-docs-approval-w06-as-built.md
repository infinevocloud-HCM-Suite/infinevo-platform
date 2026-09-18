# Docs change approval — w06-as-built — 2026-09-18

| Field | Value |
|---|---|
| **Patch** | `.claude/outputs/2026-09-18-docs-diff-w06-as-built.patch` |
| **Status** | **Approved 2026-09-18** by the founder |
| **Why** | W-06's code reached `main` as `365a319`, so the spec must stop reading "ready for `/develop`"; and four lines still demanded a `public` REVOKE that §13 R7 deleted and `03-grants.sql` never contained |

## Paths covered

- `docs/target-state/09-build-order.md` @ `83615e951767add4c621a9423512a6f55327bd6f`
- `docs/target-state/features/W-06-flyway.md` @ `902cb31dfd4e873f61b144e41fcdd2bc9e8d1aca`

## What changed

| Doc | Line | Change |
|---|---|---|
| `features/W-06-flyway.md` | 22 | Status `Approved — ready for /develop` → **Built and merged 2026-09-18**, naming `365a319` / PR #119 as the route it took |
| `features/W-06-flyway.md` | 122 | Drops "and the `public` revoke" from the one provisioning change W-06 makes |
| `features/W-06-flyway.md` | 263 | §6 T1 — drops the `public` revoke from the task, and the instruction to baseline `public`/`CREATE` before editing `03-grants.sql` |
| `features/W-06-flyway.md` | 451 | §12 done-when item 6 — drops "`migration_user` has no `CREATE` on `public`; the `REVOKE` is declared in `03-grants.sql`". This was the self-contradiction (review F-10, verify F-2) |
| `features/W-06-flyway.md` | 471 | §13 R1 — drops the trailing "Also allows `public` to be closed" |
| `09-build-order.md` | 173 | W-06 marked merged, matching W-05's phrasing at `:171`, with the CI evidence and a note on how it reached `main` |

## Evidence

- `03-grants.sql` contains exactly one REVOKE, at `:14` — `REVOKE CONNECT ON DATABASE %I FROM PUBLIC`. That is W-05's database-level connect revoke, not the schema-level `CREATE` revoke §12 item 6 demanded. No `public` schema revoke has ever existed.
- §5 of the spec (`:192`) already reads "**No change to `public`**", and §13 R7 records the ruling that dropped it. The four patched lines were left behind by that ruling.
- W-06 code on `main`: `git log main -- code/backend/migration/pom.xml` → `365a319`.
- CI run `35225300625`, `headSha=ad3f3c6`: `FlywayMigrationIT` 10 run / 0 skipped, `DatabasePrivilegesIT` 10 run / 0 skipped.

## Deliberately out of scope

- **The "four schemas" sweep.** Ten sites still describe the database as having four schemas when it has five: `02-data-model.md:5,21`, `04-runtime-containers.md:141`, `05-azure-architecture.md:43`, `06-current-to-target.md:16`, `09-build-order.md:50`, `10-scoping.md:64`, `README.md:18`, `W-02-local-stack.md:38,93`. Neighbouring uses — "four application schemas", "four migration script trees" — are correct, so each site needs reading rather than a global replace. Its own pass.
- **R6** (`W-06-flyway.md:486`) — `CONVENTIONS.md:21` and root `CLAUDE.md:76-77` still say tenant scoping has "no exceptions, including lookup tables", contradicting `D-08`. The spec routes this as its own pull request before `W-07` starts.
- **R1 and R3 needed nothing.** `CONVENTIONS.md:18` rule 4 already reads "never set, in any environment" citing `D-46`; `02-data-model.md:14` and `04-runtime-containers.md:130` already name the fifth schema.
