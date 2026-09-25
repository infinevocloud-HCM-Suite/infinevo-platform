---
name: infra-task
description: Spec and plan platform infrastructure, database, security, and CI/CD tasks (skill-INFRA, skill-DATA, skill-SEC). Stops before code is written.
---

# infra-task

Invoke as `/infra-task W-nn <developer>`. Plans and specs platform infrastructure, Azure IaC (Bicep), database posture, Docker, networking, or security tasks.

**STOPS FOR FOUNDER APPROVAL BEFORE ANY INFRASTRUCTURE CODE IS WRITTEN.**

---

## Workflow Steps

1. Read `.agents/work/active-work.md` and related docs in `infra/` or `docs/target-state/`.
2. Inspect current configurations in `infra/azure/`, `infra/docker/`, `infra/postgres/`, or `.github/workflows/`.
3. Draft specification in `docs/target-state/features/W-nn-<slug>.md`.
4. Define concrete verification criteria (linting Bicep, dry-run container builds, testcontainer DB privilege matrix).
5. Ensure compliance with Azure Container Apps decisions (`D-10`), Postgres role security (`W-05`), and network perimeter (`W-51`).
6. Update `docs/trackers/DEV-TRACKER.md`.
7. **STOP and present plan to founder for approval.**
