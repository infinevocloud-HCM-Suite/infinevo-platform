---
name: analyze
description: Read-only architectural investigation to answer "where/how does X work" in legacy or target state.
---

# Analyze Skill

Trigger: `/analyze <query>`

## Workflow

1. Perform search across `legacy/`, `docs/`, and `code/`.
2. Trace flow across controllers, services, repositories, and database tables.
3. Formulate evidence table citing exact `file:line` references.
