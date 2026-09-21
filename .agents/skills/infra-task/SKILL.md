---
name: infra-task
description: Spec and plan platform infrastructure, database, security, and CI/CD tasks (skill-INFRA, skill-DATA, skill-SEC). Stops before code is written.
---

# Infra Task Skill

Trigger: `/infra-task W-nn`

## Workflow

1. Read `.agents/work/active-work.md` and ticket issue for infrastructure, database, or security tasks.
2. Formulate execution plan covering Bicep IaC, Docker compose, Keycloak realms, Flyway migrations, or CI pipelines.
3. Draft plan under `.agents/outputs/<date>-plan-infra-<slug>.md`.
4. Run `/review-spec <draft-path>`.
5. Present plan to founder and stop for approval before implementing.
