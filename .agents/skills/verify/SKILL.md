---
name: verify
description: Independent verification running build, lint, tests, and module boundaries. Read-only; captures actual exit codes and command outputs.
---

# Verify Skill

Trigger: `/verify W-nn`

## Verification Steps

1. Backend build & test: `cd code/backend && ./mvnw -B clean verify`
2. Frontend lint & build: `cd code/frontend && npm run lint && npm run build`
3. Static Flyway check: `grep -rn "ddl-auto" code/backend/` (must be empty)
4. Module boundary check: `mvn dependency:tree` verifying `hrms` and `payroll` never depend on each other.
5. Save verification report to `.agents/outputs/<date>-verify-W-nn.md`.
