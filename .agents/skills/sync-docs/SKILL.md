---
name: sync-docs
description: Safe, controlled updates to docs/ after code changes or architectural decisions.
---

# Sync Docs Skill

Trigger: `/sync-docs`

## Workflow

1. Detect documentation drift between `docs/` and current `code/` / architectural state.
2. Propose exact markdown diff.
3. Obtain explicit founder approval before applying any write to `docs/`.
