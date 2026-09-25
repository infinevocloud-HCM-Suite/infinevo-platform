---
name: develop
description: Build an approved ticket end to end — writes the code, runs test, verify, and review, fixes what they find, and stops after three rounds. Refuses to start without an approved spec.
---

# develop

Invoke as `/develop W-nn` or `/develop <branch>`. Runs the autonomous implementation loop for an approved specification.

**Refuses to start without an approved specification in `docs/target-state/features/W-nn-*.md`.**

---

## The Loop (Up to 3 Rounds)

```
[Start /develop W-nn]
        │
        ▼
   Round 1: Implement changes one module at a time
        │
        ▼
   Run targeted tests (/test W-nn)
        │
        ▼
   Run build & contract verification (/verify W-nn)
        │
        ▼
   Run code review against spec (/review W-nn)
        │
        ├─▶ Findings exist? ──▶ Round 2/3: Fix & re-verify
        │
        ▼
   Clean Pass (0 High, 0 Medium)
        │
        ▼
   Ready for /merge W-nn
```

## Workflow Steps

1. **Pre-flight Check:**
   - Read `.agents/work/active-work.md`.
   - Ensure the ticket is Approved and unblocked.
   - Stop immediately if the specification is not approved or if it violates size caps.

2. **Branch Hygiene:**
   - Ensure working on the assigned branch (`W-nn-<slug>` or `dev-<name>`).
   - Rebase cleanly on `origin/main`.

3. **Autonomous Implementation:**
   - Modify only **one module at a time** (`code/backend/<module>` or `code/frontend/src/<area>`).
   - Never edit `legacy/`. Cite `legacy/path:line` for any ported logic in comments and commit messages.
   - Enforce standing rules: `tenant_id` + RLS on every table, Flyway migrations for all DDL, `BigDecimal` for money, zero `ddl-auto`.

4. **Verify & Review:**
   - Run `/test W-nn` to create and execute unit/integration tests.
   - Run `/verify W-nn` to test compile, build, lint, and architecture boundary enforcement.
   - Run `/review W-nn` to perform an independent diff review against spec acceptance criteria.

5. **Fix Loop:**
   - Fix High and Medium findings. Repeat up to 3 rounds maximum. Round 4 never runs.

6. **Completion:**
   - Run `node .agents/scripts/check-done.mjs W-nn` to verify definition of done and generate merge receipt.
   - Report summary table of changed files and rounds used. Remind developer to run `/merge W-nn`.
