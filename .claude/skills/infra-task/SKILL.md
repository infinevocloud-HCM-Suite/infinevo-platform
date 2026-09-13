---
name: infra-task
description: Plan (and after approval, execute) a non-feature change — build tooling, CI, Docker/Azure, dependency upgrades, Flyway bootstrap, tenancy scaffolding. Anything that is not a business feature.
---

# infra-task

Same discipline as `plan-feature`, but for platform work where the "flow" is a pipeline or an
environment rather than a screen. Two phases with a hard stop between them.

## Phase 1 — plan
1. Read `.claude/work/active-work.md` → **Current direction** (new consolidated repo via subtree,
   shared-schema multi-tenancy, Azure AKS + Azure Database for MySQL) so the task fits the
   roadmap; note the **Open questions** it depends on.
2. Read the relevant GAP IDs (`DEBT-001` ddl-auto, `DEBT-002` secrets in properties,
   `DEBT-003` no tests, `DEBT-018` no indexes, `DEBT-021` unlocked schedulers, …).
3. Spawn **explorer** for the current state: which files/configs exist today, versions,
   what depends on them. Output `.claude/outputs/<date>-infra-<slug>-evidence.md`.
4. Write the plan to `.claude/outputs/<date>-infra-<slug>.md`: Goal, Current state (cited),
   Target state, Steps (each with the exact command or file, the app it touches, and a
   rollback), Verification (a command the **verifier** can run), Risks to running
   production (the origin repos are live — hard rule 5), Cost/time.
5. Reply with the plan path and the decisions needed. **STOP; wait for "approved".**

## Phase 2 — execute (only after approval)
6. For each step, spawn **implementer** confined to the one app (or the repo root for
   harness/CI files). Secrets never go into files; reference Key Vault / env var names.
7. Spawn **verifier** with the verification commands from the plan. Attach its report.
8. Append the outcome (done / partial / blocked, with the verifier report path) to
   `.claude/work/active-work.md` under **In flight**. Reply with the summary. **STOP.**
