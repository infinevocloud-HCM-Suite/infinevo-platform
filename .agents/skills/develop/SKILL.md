---
name: develop
description: Build an approved ticket end to end — writes code, runs test, verify, and review skills, fixes findings, and stops after three rounds. Refuses to start without an approved spec.
---

# Develop Skill

Trigger: `/develop W-nn`

## Workflow

1. Check `.agents/work/active-work.md` and verify `docs/target-state/features/W-nn-*.md` is marked **Approved**. Stop if not approved.
2. Checkout or create ticket branch `W-nn-<slug>`.
3. Round 1: Implement changes one module at a time (`code/backend/<module>` or `code/frontend/src/<area>`). Cite `legacy/` `file:line` for ported logic.
4. Run `/test W-nn`, `/verify W-nn`, `/review W-nn`.
5. Address findings: fix code defects (`FIXED`), defer out-of-scope items (`DEFERRED`), or request founder input on architectural disputes (`DISPUTED`).
6. Repeat review loop up to 3 rounds maximum. Round 4 never runs.
7. On clean exit: report summary table of changed files and rounds used. Remind developer to run `/merge W-nn`.
