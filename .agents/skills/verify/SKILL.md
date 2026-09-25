---
name: verify
description: Independent verification running build, lint, tests, and module boundaries. Read-only; captures actual exit codes and command outputs.
---

# verify

Invoke as `/verify W-nn`. Executes full compiler, test suite, linter, and module boundary checks.

**READ-ONLY: Captures actual exit codes and raw stdout/stderr.**

---

## Verification Pipeline

1. **Backend Verification:**
   ```bash
   cd code/backend && ./mvnw -B clean verify
   ```
2. **Frontend Verification:**
   ```bash
   cd code/frontend && npm run lint && npm run build
   ```
3. **Database & Flyway Invariant Checks:**
   - Assert zero `ddl-auto` across configs:
     ```bash
     grep -rn "ddl-auto" code/backend/
     ```
   - Assert all migrations are versioned sequentially above `main`.
4. **Architecture Boundary Enforcement:**
   - Verify Maven dependency tree ensures `hrms` and `payroll` modules remain decoupled.
5. **Output Report:**
   - Save execution results and exit codes to `.agents/outputs/<date>-verify-W-nn.md`.
