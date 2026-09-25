---
name: test
description: Author targeted unit and integration tests for code changed in a ticket.
---

# test

Invoke as `/test W-nn`. Authors targeted unit and integration tests covering positive and negative paths for modified modules.

---

## Testing Principles

1. **Backend Tests:**
   - Package structure mirrors main code in `code/backend/<module>/src/test/java`.
   - Unit tests use JUnit 5 and Mockito.
   - Integration tests extend `AbstractIntegrationTest` (runs PostgreSQL with Testcontainers as non-owner `app_user`).
2. **Negative Scenarios Required:**
   - Unauthenticated requests return `401`.
   - Missing action code returns `403`.
   - Cross-tenant data access blocked by RLS.
   - Invalid payloads return `400 VALIDATION_FAILED`.
3. **Frontend Tests:**
   - Vitest and React Testing Library in `code/frontend/`.
