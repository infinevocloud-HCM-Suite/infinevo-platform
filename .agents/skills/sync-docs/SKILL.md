---
name: sync-docs
description: Controlled updates to docs/ after code changes or architectural decisions.
---

# sync-docs

Invoke as `/sync-docs`. Safe, controlled updates to `docs/` after code changes or architectural decisions.

**Never edits docs without founder approval of the proposed patch.**

---

## Workflow Steps

1. Check git status and compare `docs/target-state/` against actual implemented code in `code/`.
2. Generate unified diff patch.
3. Present the diff clearly to the founder explaining each update.
4. **STOP and wait for approval.**
5. Upon approval, apply patch and update documentation indices.
