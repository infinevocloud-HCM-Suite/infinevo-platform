# Independent Code Review: Audit Log Actor Email Resolution (K-11 / K-12 Defect Fix)

| Review Item | Detail |
|---|---|
| **Defect / Target** | `K-11` / `K-12` — Audit log displays raw Keycloak UUID instead of user email |
| **Tracker Reference** | `.claude/outputs/certification/TRACKER.md` (Defect #1) |
| **Scope Audit** | `code/backend/shared` (`AuditWriter`, `AuditQueryService`, `UserAccountRepository`, tests) |
| **Result** | **PASS** (0 High, 0 Medium, 0 Low) |

---

### Files Changed

| File | Status | Audit Result |
|---|---|---|
| `code/backend/shared/src/main/java/com/infinevo/shared/audit/AuditWriter.java` | Modified | `resolveActor()` extracts `email` (or `preferred_username`) from JWT claims to populate `actorLabel` while retaining subject UUID in `actorUserId`. |
| `code/backend/shared/src/main/java/com/infinevo/shared/identity/UserAccountRepository.java` | Modified | Added `findByTenantIdAndEmailIgnoreCase(UUID, String)` with strict tenant isolation leveraging existing composite index `(tenant_id, email)`. |
| `code/backend/shared/src/main/java/com/infinevo/shared/audit/AuditQueryService.java` | Modified | Added batch `enrich()` on query page content resolving historical UUIDs to emails via `core.user_account`. Updated `search()` actor filter to match email across both `actorLabel` and `actorUserId`. |
| `code/backend/shared/src/test/java/com/infinevo/shared/audit/AuditWriterTest.java` | Modified | Added test `buildRowCarriesEmailActorLabel()`. |
| `code/backend/shared/src/test/java/com/infinevo/shared/audit/AuditQueryServiceTest.java` | Added | Added unit tests asserting UUID enrichment, preserving non-UUID labels, and email search resolution. |
| `.claude/outputs/certification/TRACKER.md` | Modified | Marked K-11 and K-12 as Failed and registered Defect #1. |

---

### Findings

| Finding ID | Severity | File:Line | Description |
|---|---|---|---|
| — | — | — | *None. Zero high, medium, or low severity issues found.* |

---

### Audit Criteria Checklist

| Invariant / Rule | Status | Evidence |
|---|---|---|
| **Tenant Isolation** | **PASS** | `findByTenantIdAndEmailIgnoreCase` and `findByTenantIdAndKeycloakUserIdIn` strictly require `tenantId`. `AuditQueryService.search()` scopes queries using `TenantContext.require()`. |
| **Audit Immutability** | **PASS** | `core.audit_log` rows are never mutated or updated. Historical entries are resolved dynamically on read in `AuditQueryService.enrich()`. |
| **Database & Flyway** | **PASS** | Zero schema or DDL changes required; existing `(tenant_id, email)` index is leveraged. |
| **DDL-Auto Invariant** | **PASS** | Zero active `ddl-auto` configurations present. |
| **Module Boundaries** | **PASS** | Changes are confined to `shared`, which is imported by all downstream modules. No cross-dependencies between `hrms` and `payroll`. |
| **Legacy Immutability** | **PASS** | Zero edits under `legacy/`. |
| **Performance (N+1)** | **PASS** | Lookups in `AuditQueryService.enrich()` are batched in a single `findByTenantIdAndKeycloakUserIdIn()` query per page (<= 20 rows). |

---

### Recommendation
**PASS** — Ready for commit and deployment.
