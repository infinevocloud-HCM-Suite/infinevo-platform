---
name: analyze
description: Read-only architectural investigation to answer "where/how does X work" in legacy or target state.
---

# analyze

Invoke as `/analyze <query>`. Performs read-only investigation across target-state specifications, frozen legacy codebases, and active implementations.

**READ-ONLY: NEVER EDITS CODE OR SPECIFICATIONS.**

---

## Workflow

1. Search relevant paths (`legacy/`, `docs/`, `code/`).
2. Trace request lifecycle: Controller → Service → Repository → Database.
3. Compare target-state design against frozen legacy behavior.
4. Output structured evidence table with exact `file:line` citations for every finding.
