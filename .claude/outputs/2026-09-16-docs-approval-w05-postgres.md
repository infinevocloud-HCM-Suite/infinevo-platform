# Docs change approval — w05-postgres — 2026-09-16

| Field | Value |
|---|---|
| **Patch** | `.claude/outputs/2026-09-16-docs-diff-w05-postgres.patch` |
| **Status** | **Approved 2026-09-16** by the founder |
| **Why** | `W-05` merged as #110 and shipped different files from the ones its spec named. The scripts are `01-roles.sql`/`02-schemas.sql`/`03-grants.sql` run by `provision.sh`, not `00-roles.sql`/`01-schemas.sql`; `00-bootstrap.sql` was deleted; the documented verify command selected no tests; and two documents still said three roles where there are now four. |

## Paths covered

- `docs/target-state/04-runtime-containers.md` @ `f668972b5a370447ba8c96271744ed99885bf101`
- `docs/target-state/09-build-order.md` @ `781c97119d350575404147e839fe1f2559b67587`
- `docs/target-state/features/W-02-local-stack.md` @ `d1985e341a5165840f298d1c43ec5d598459fbad`
- `docs/target-state/features/W-04-test-foundation.md` @ `537c8a0a8b1d6cb0e0a7ed486a841dace5250d8a`
- `docs/target-state/features/W-05-postgres-schemas.md` @ `3f1b3c774db2bf65bd9c355dbac9f89db0ae711d`
