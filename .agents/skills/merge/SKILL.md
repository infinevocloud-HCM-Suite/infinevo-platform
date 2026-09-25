---
name: merge
description: Put a finished branch onto main. Runs the machine-checked definition of done first and refuses if any gate fails.
---

# merge

Invoke as `/merge W-nn` or `/merge`. Validates Definition of Done, creates squash merge commit to `main`, and performs post-merge cleanup.

**Refuses to merge if any of the Definition of Done gates fail.**

---

## Workflow Steps

1. **Pre-Merge Validation:**
   - Execute Definition of Done:
     ```bash
     node .agents/scripts/check-done.mjs W-nn
     ```
   - If any gate fails, stop immediately and report required fixes.
   - Verify signed receipt exists in `.agents/outputs/.merge-receipts/`.

2. **Local Squash Merge:**
   - Fetch latest `origin/main`.
   - Checkout `main` and execute squash merge:
     ```bash
     git checkout main
     git merge --squash <branch>
     ```

3. **Commit with Structured Message:**
   - Format:
     ```text
     feat(<module>): <ticket title> (#<issue>)

     - <bullet 1>
     - <bullet 2>
     - Closes #<issue>
     ```

4. **Push to Remote:**
   - Push to `origin main` (allowed by `guard-merge.mjs` because a valid PASS receipt exists).

5. **Post-Merge Housekeeping:**
   - Update `.agents/work/active-work.md` and `docs/trackers/DEV-TRACKER.md` row to `Merged`.
   - Run `/sync-docs` if any documentation updates are required.
   - Run output pruner:
     ```bash
     node .agents/scripts/prune-outputs.mjs --delete
     ```
