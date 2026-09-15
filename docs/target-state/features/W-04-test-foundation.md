# W-04 — Test foundation

| Field | Value |
|---|---|
| **Work item** | `W-04` · issue [#5](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/5) |
| **Kind** | **Infra** — test harness, base integration classes, coverage plugin |
| **Stream / track** | Stream A — Foundation · Track P |
| **Wave** | 1 — Foundations |
| **Size / skill** | M · JAVA / INFRA |
| **Owner** | SayInfi |
| **Blocked by** | — (`W-01` merged) |
| **Blocks** | `W-05`, `W-06`, `W-07`, and all feature implementation tickets |
| **Capabilities** | — |
| **Decisions** | `D-38` Java 21 |
| **Gaps addressed** | `DEBT-003` no tests |
| **Status** | **Approved 2026-09-15 — implemented, pending merge** |
| **Approved by** | Founder |
| **Approved on** | 2026-09-15 |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

There is virtually no automated test infrastructure across the backend repository (`DEBT-003`). Today, `code/backend` contains only two unit tests in `shared` (`TenantContextTest.java` and `MoneyTest.java`). No integration test harness exists, no database testing container setup exists, and JaCoCo coverage reporting is unconfigured.

This creates four immediate risks:
1. **No integration testing capability.** There is no base harness to run tests against a running Spring context or a database.
2. **PostgreSQL Row-Level Security (RLS) testing cannot be executed on in-memory DBs.** Databases like H2 do not support PostgreSQL RLS policies (`CREATE POLICY ... ON ...`). RLS must be tested against a real PostgreSQL container.
3. **Database owner RLS bypass risk.** In PostgreSQL, table owners and superusers bypass RLS policies by default (`02-data-model.md` §9, `05-azure-architecture.md` §5). If integration tests run as the database owner, RLS policies will be bypassed and data leaks will go undetected. Integration tests must connect using a non-owner application role (`app_user`).
4. **No test data builder standards or module boundary rules.** Without a clear builder architecture, test helper utilities risk violating Maven module isolation rules.

## 2. Scope

**In scope**

- **Unit test foundation** — JUnit 5 (Jupiter), AssertJ, and Mockito conventions across all backend modules.
- **Testcontainers PostgreSQL infrastructure** — Reusable Testcontainers PostgreSQL container setup using the platform database name (`infinevo`).
- **Abstract integration test base** — `AbstractIntegrationTest` base class with non-owner database role configuration (`app_user`), ensuring RLS enforcement is preserved during test execution.
- **Maven test-jar utility sharing** — Configuring `shared` module to produce a `test-jar` so common test utilities (e.g., base classes, context helpers) can be shared across modules without domain coupling.
- **Domain builder placement rules** — Core builders live in `core`, HRMS builders in `hrms`, Payroll builders in `payroll`. `shared` never owns domain entity builders.
- **JaCoCo coverage reporting** — `jacoco-maven-plugin` added to parent `pom.xml`, generating coverage reports during `./mvnw verify`.

**Out of scope**

- **Tenant or Employee entity implementation.** Owned by `W-07` and `W-13`.
- **Flyway schema migrations.** Owned by `W-06`.
- **Actual RLS policy validation tests.** Added in `W-07` when RLS policies are created.
- **The "tenant + employee in three lines" test proof.** Implemented when `Tenant` (`W-07`) and `Employee` (`W-13`) entities exist.
- **Frontend test runner setup.** Covered separately under frontend tooling.

## 3. Flow & Proposed Testing Architecture

```
[ Unit Tests (JUnit 5 + Mockito) ] ──► Instant in-memory execution (no Spring context)

[ Integration Tests (@SpringBootTest) ]
       │
       ▼
┌──────────────────────────────────────────────┐
│          AbstractIntegrationTest             │
│  (Testcontainers PostgreSQL 16 `infinevo` DB)│
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│  Non-Owner DB Role Connection (`app_user`)   │  ◄── Enforces Postgres RLS Policies
└──────────────────────────────────────────────┘      (Owner BYPASSRLS prevented)
```

### Module Builder & Test Utility Placement Rules

```
shared/src/test/java/   ──► AbstractIntegrationTest, TenantContext helpers (No domain entities)
                             [Exported via Maven test-jar]
core/src/test/java/     ──► Core entity builders (e.g., DepartmentBuilder, LeaveTypeBuilder)
hrms/src/test/java/     ──► HRMS entity builders (e.g., ClockSessionBuilder, TimesheetBuilder)
payroll/src/test/java/  ──► Payroll entity builders (e.g., PayScheduleBuilder, PayrunBuilder)
```

`shared` does not own domain entities or application context; therefore, domain entity builders must live exclusively in their respective domain modules (`core`, `hrms`, `payroll`). Reusable test infrastructure in `shared` is exposed to other modules using Maven `<type>test-jar</type>`.

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Parent POM | `code/backend/pom.xml` | Add Testcontainers BOM (`org.testcontainers:testcontainers-bom`), `jacoco-maven-plugin`, and `maven-surefire-plugin` / `maven-failsafe-plugin` management |
| Shared POM | `code/backend/shared/pom.xml` | Configure `maven-jar-plugin` `test-jar` execution to export test infrastructure |
| Core / HRMS / Payroll POM | `code/backend/{core,hrms,payroll}/pom.xml` | Add `<type>test-jar</type>` dependency on `com.infinevo:shared` for test scope |
| Test Base | `code/backend/shared/src/test/java/com/infinevo/shared/test/AbstractIntegrationTest.java` | Abstract base class establishing PostgreSQL container with non-owner `app_user` connection |
| Test Property Source | `code/backend/shared/src/test/java/com/infinevo/shared/test/PostgresTestContainerInitializer.java` | Dynamic property registry initializer for Testcontainers PostgreSQL (`infinevo`) |

**Founder decision, 2026-09-15.** The `app_user` role is created by `PostgresTestContainerInitializer` with plain SQL against the container owner, granted on the `public` schema only. It does not reuse `infra/docker/postgres/00-bootstrap.sql`. Accepted because `AbstractIntegrationTestTest` proves the role is neither superuser nor `BYPASSRLS`. `W-06` must switch the initializer to the bootstrap script when the four schemas arrive.

**API contract**

> N/A — Infrastructure task. No HTTP API endpoints created or modified.

## 5. Frontend changes

| File | Change |
|---|---|
| — | N/A — Infrastructure task. Frontend test runner setup is out of scope. |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| — | N/A | N/A |

## 6. Database changes

> Flyway only. Never `ddl-auto`. See `CONVENTIONS.md` rule 4.

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| — | None | N/A | N/A |

- [x] No schema changes in `W-04`. Testcontainers runs against the target PostgreSQL `infinevo` database structure.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `code/backend/shared/src/test/java/com/infinevo/shared/tenant/TenantContextTest.java` | Existing tenant context unit tests |
| Unit | `code/backend/shared/src/test/java/com/infinevo/shared/money/MoneyTest.java` | Existing monetary precision unit tests |
| Integration Base | `code/backend/shared/src/test/java/com/infinevo/shared/test/AbstractIntegrationTestTest.java` | Verification that `AbstractIntegrationTest` connects to Postgres as non-owner `app_user` |

## 8. Verification

Run by **verifier** on a clean checkout.

```bash
# 1 - Verify Testcontainers and JaCoCo plugins in Maven pom
cd code/backend && ./mvnw dependency:tree | grep -i testcontainers

# 2 - Run unit and integration tests with coverage reporting
./mvnw clean verify

# 3 - Verify JaCoCo report generation
ls -la target/site/jacoco/index.html || find . -name "index.html" | grep jacoco
```

| Check | Expected | Result |
|---|---|---|
| `./mvnw clean verify` | Exit code 0, BUILD SUCCESS | |
| Testcontainers PostgreSQL | Spins up PostgreSQL 16 container with database name `infinevo` | |
| Non-owner role connection | Integration test connects as `app_user` (not DB owner) | |
| JaCoCo HTML report | Generated at `target/site/jacoco/index.html` | |
| Module isolation | `shared` has no domain entities; domain builders reside in domain modules | |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Integration tests connect as DB owner, bypassing RLS | **High** | Explicitly configure Testcontainers and DataSource properties to connect as non-owner `app_user` (`02-data-model.md` §9). |
| Slow Testcontainers startup delays local build loop | Medium | Use static single-instance container lifecycle pattern across integration tests. |
| Domain entity builders placed in `shared` module | Medium | Enforce builder placement rules in review: `core` builders in `core`, `hrms` in `hrms`, `payroll` in `payroll`. |
| Developers attempt to test RLS using H2 in-memory DB | Medium | Ban H2 dependency in `pom.xml` via `maven-enforcer-plugin`. |

## 10. Rollback

Revert commit. No database migrations, Azure resources, or external dependencies are deployed.

## 11. Done when

1. Testcontainers BOM (`org.testcontainers:testcontainers-bom`) and PostgreSQL module added to `code/backend/pom.xml`.
2. `AbstractIntegrationTest` implemented in `shared` module, connecting to a PostgreSQL container with database name `infinevo`.
3. Non-owner database role (`app_user`) configured for integration test connections so PostgreSQL RLS policies cannot be bypassed by owner privileges.
4. Maven `test-jar` execution configured on `shared` module so test utilities can be imported across modules via `<type>test-jar</type>`.
5. Builder placement rules documented: `core` entity builders live in `core`, `hrms` in `hrms`, `payroll` in `payroll`. `shared` owns zero domain entity builders.
6. JaCoCo plugin (`jacoco-maven-plugin`) configured in root `pom.xml`, generating reports on `./mvnw verify`.
7. Deferred items clearly documented: "tenant + employee in 3 lines" proof deferred to `W-07`/`W-13`; RLS policy validation tests deferred to `W-07`.
8. `./mvnw clean verify` passes green across all backend modules.
