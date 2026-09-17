# Docs change approval — w06-rulings — 2026-09-17

| Field | Value |
|---|---|
| **Patch** | `.claude/outputs/2026-09-17-docs-diff-w06-rulings.patch` |
| **Status** | **Approved 2026-09-17** by the founder |
| **Why** | The founder ruled on six W-06 questions on 2026-09-17. Two of them change the design the documents describe: Flyway's history table gets its own `migration` schema, a fifth schema holding no application data (`D-45`), and `ddl-auto` is never set in any environment rather than moving to `validate` once Flyway lands (`D-46`). |

## Paths covered

- `docs/CONVENTIONS.md` @ `d61bb85c2eeefa118e32cedd050c828e464f1af6`
- `docs/target-state/02-data-model.md` @ `add18baa9ff81586cb76c5b9db8eb08eff1e70b9`
- `docs/target-state/04-runtime-containers.md` @ `4e61c1fe047a292b69b5a97d6180ac00d73d1ac4`
- `docs/target-state/07-decisions.md` @ `43217a1abe5a575b68fda4b7d74bf8ecc5eff900`

## Not covered by this approval

Deliberately excluded, each with its own route:

| Change | Why not here |
|---|---|
| Ruling **R6** — `reference` is exempt from `tenant_id`; reword `docs/CONVENTIONS.md` rule 7 and root `CLAUDE.md` hard rule 7 | Touches root `CLAUDE.md` and is `W-07`'s prerequisite, not W-06's. Its own pull request, before `W-07` starts |
| `.claude/work/active-work.md` | It states what `W-05` **built** — four schemas owned by `migration_user`. That stays true until `W-06` merges. It is updated then, not now |

## Note on sequencing

`D-45` and `D-46` are recorded before the work that implements them. That is correct for
`docs/target-state/`, which describes where the platform is going rather than what exists
— but it means these documents describe a fifth schema that does not yet exist in any
database. The `W-06` spec is the implementation; it was still in review when this was
approved.
