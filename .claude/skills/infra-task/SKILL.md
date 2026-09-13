---
name: infra-task
description: Plan (and after approval, execute) a platform ticket — pipeline, containers, Azure, Postgres, Flyway, tenancy scaffolding, security tooling. Anything whose "flow" is an environment rather than a screen.
---

# infra-task

Same discipline as `plan-feature`, for tickets labelled `skill-INFRA`, `skill-DATA` or
`skill-SEC`. **Two phases with a hard stop between them.**

Invoke as `/infra-task W-nn`.

## The target state, so the plan aims at the right thing

| | |
|---|---|
| Runtime | **Azure Container Apps** (`D-10`). Not AKS |
| Database | **One Postgres**, four schemas: `core` `hrms` `payroll` `reference` (`D-09`). Not MySQL |
| Isolation | `tenant_id` everywhere outside `reference`, enforced by **row-level security** |
| Migrations | **Flyway only.** `ddl-auto` must appear in no configuration file, ever |
| Region | India (`D-18`) |
| Scale to design for | 10 tenants × up to 100 employees (`D-19`). Do not over-engineer |
| Code from the frozen apps | **Ported deliberately, once.** No subtree, no sync (`D-17`) |
| Secrets | Key Vault. Never a value in a file — reference the name only |

Full detail: `docs/target-state/05-azure-architecture.md` and `04-runtime-containers.md`.

## Phase 1 — plan

1. Read `.claude/work/active-work.md` for where the project actually stands, then the
   ticket's own GitHub issue for its features and build detail.
2. Read `docs/target-state/09-build-order.md` §3 for this item: **what to build, how you
   know it is done, and the trap to avoid.** That entry is deliberately the level that
   survives — use it as the spine of the plan.
3. Read the relevant gap IDs in `legacy/docs/GAP_INVENTORY.md` — `DEBT-001` ddl-auto,
   `DEBT-002` secrets in properties, `DEBT-003` no tests, `DEBT-018` no indexes,
   `DEBT-021` unlocked schedulers. The plan must fix, explicitly defer, or discount each.
4. Spawn **explorer** if current state is unclear. Evidence to
   `.claude/outputs/<date>-infra-<slug>-evidence.md`.
5. Write the spec into `docs/target-state/features/W-nn-<slug>.md` from `TEMPLATE.md`.
   For infra, sections 5 and 6 are usually "not applicable" — say so rather than
   inventing endpoints. What matters is:
   - **Verification**: exact commands the **verifier** can run, with expected output
   - **Rollback**: what to do if it goes wrong, given nothing is in production yet
   - **Done when**: a numbered list, each item checkable
6. Reply with the spec path, a summary of 10 lines or fewer, and any decisions the founder
   must make, as numbered questions. **STOP and wait for approval.**

> The guard hook blocks writes to `docs/`. Write the spec to `.claude/outputs/` first and
> copy it across once approved, or use `sync-docs`.

## Phase 2 — execute, only after approval

7. Spawn **implementer** per step, confined to one area — `code/backend/<module>`,
   `code/frontend`, `infra/`, or `.github/workflows/`.
8. Spawn **verifier** with the verification commands from the spec. Attach its report
   verbatim; do not summarise away a failure.
9. Update the spec to match what was actually built, then open a pull request saying
   `Closes #<issue>` with the real command output in the body.
10. Reply with the summary. **STOP.**

## Standing constraints

- Nothing under `legacy/` is edited, ever. It is frozen and `guard-edit` blocks it
- No secret value in any file. Key Vault reference or environment variable name only
- Every schema change is a Flyway script under `code/backend/migration/`
- `main` cannot be branch-protected on the Free plan (`D-43`) — the pipeline reports, it
  does not block. Do not write a plan that assumes a required check
