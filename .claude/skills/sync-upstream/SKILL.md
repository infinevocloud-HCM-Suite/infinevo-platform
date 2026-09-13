---
name: sync-upstream
description: Pull the latest production code from the four read-only origin repos into this consolidated repo (git subtree, one-way). Reports conflicts; never pushes anywhere. DORMANT until the subtree migration is done.
---

# sync-upstream

> ⛔ **CANCELLED 2026-09-11 by decision `D-17`.** There is no sync mechanism: the new
> codebase is built once, and code is ported deliberately rather than pulled. The subtree
> migration this skill depended on is not happening. **Do not invoke this skill.** Kept for
> the record only; delete it when the new repo is created.

One-way refresh: **origin repos → this repo**. Hard rule 5: upstream is production and
read-only; nothing ever flows back. This skill does nothing useful until the four app folders
have been converted from gitignored clones to `git subtree` prefixes (a separate, founder-
approved `infra-task`).

## Preconditions (check first; if any fails, report and STOP)
- `git remote -v` at the root shows `up-hrms-be`, `up-hrms-fe`, `up-pay-be`, `up-pay-fe`.
- `.gitignore` no longer ignores the four app folders.
- Working tree is clean (`git status --short` empty) — never sync over uncommitted work.

## Steps
1. Read `.claude/work/active-work.md` → **Repo state** table for the expected branch per app
   (`main`, `main`, `taxation`, `employee`) and the last synced SHA.
2. `git fetch up-hrms-be up-hrms-fe up-pay-be up-pay-fe` (fetch only).
3. For each app, show what is new: `git log --oneline <last-synced-sha>..<remote>/<branch>`
   and `git diff --stat`. Flag any change to `application*.properties`, `pom.xml`,
   `package.json`, or `entity/` (schema drift) as **needs review**.
4. Reply with the four summaries and the flagged files. **STOP; wait for "pull".**
5. On "pull": `git subtree pull --prefix=<app> <remote> <branch> --squash -m "sync <app> <sha>"`
   for each app, one at a time.
6. On conflict: do **not** resolve automatically. Run `git status`, list conflicting files,
   write them to `.claude/outputs/<date>-sync-conflicts.md`, and stop with the tree left in
   the conflicted state for the founder to decide.
7. On success: spawn **verifier** for each app that changed (compile/lint/tests) and attach
   its report. Update the SHAs in `.claude/work/active-work.md` → **Repo state**.
8. **Never** `git push`, never add a push URL to an upstream remote, never rewrite history.
   Reply with the summary and verifier results. **STOP.**
