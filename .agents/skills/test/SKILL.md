---
name: test
description: Author targeted unit and integration tests for code changed in a ticket.
---

# Test Skill

Trigger: `/test W-nn`

## Workflow

1. Identify changed modules and classes.
2. Author JUnit 5 unit/integration tests in `code/backend/<module>/src/test/java`.
3. Author Vitest/RTL tests in `code/frontend/`.
4. Ensure negative test coverage (unauthenticated, wrong tenant, null/empty parameters).
5. Execute targeted test suite and verify 100% pass rate.
