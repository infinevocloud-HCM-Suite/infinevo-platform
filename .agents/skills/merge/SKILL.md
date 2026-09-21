---
name: merge
description: Put a finished ticket branch onto main. Runs the machine-checked definition of done first and refuses if any gate fails.
---

# Merge Skill

Trigger: `/merge W-nn`

## Workflow

1. Run `node .agents/scripts/check-done.mjs`. If any gate fails, stop and report.
2. Squash-merge ticket branch onto `main` locally: `git merge --squash W-nn-<slug>`.
3. Commit with structured summary citing closed issue.
4. Push to origin main (authorized by `guard-merge` due to passing receipt).
5. Post-merge steps: verify main build, update `.agents/work/active-work.md`, run `/sync-docs`, and prune outputs via `node .agents/scripts/prune-outputs.mjs`.
