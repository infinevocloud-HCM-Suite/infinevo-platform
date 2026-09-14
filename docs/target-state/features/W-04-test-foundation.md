# Feature: W-04 — Test Foundation

> Feature specification for `W-04` (`PLAT-11` / `DEBT-003`).
> Prepared in accordance with `docs/target-state/features/TEMPLATE.md` and `CONTRIBUTING.md`.

| Field | Value |
|---|---|
| **Feature ID** | W-04 (`PLAT-11`, `DEBT-003`) |
| **Owner** | Core / Backend Lead |
| **Apps touched** | `infinevo-platform` (`code/backend/`, root `pom.xml`, `shared`, `core`, `hrms`, `payroll`, `app`, `worker`) |
| **Related gaps** | `DEBT-003` (No test coverage), `DEBT-018` (Unindexed tenant queries), `DEBT-022` (Missing tenant security scope) |
| **Status** | Draft |
| **Approved by** | Pending Founder Approval |
| **Approved on** | |

---

## 1. Problem Statement & Current State Analysis

Today, backend automated testing infrastructure is severely lacking (`DEBT-003`).
* **Current Unit Tests**: Only two basic unit tests exist (`TenantContextTest.java` and `MoneyTest.java` in `shared`). No unit tests exist in `core`, `hrms`, `payroll`, `app`, or `worker`.
* **Current Integration Setup**: Zero integration test infrastructure exists. No `@SpringBootTest` or test suite harnesses are configured.
* **Testcontainers & Database Testing**: Testcontainers is **not configured** in `pom.xml`. There is no PostgreSQL container test infrastructure.
* **Row-Level Security Risk**: Without a real PostgreSQL test database, PostgreSQL Row-Level Security (RLS) policies on `tenant_id` cannot be tested or validated. In-memory databases like H2 do not support Postgres RLS rules and would mask tenant data leaks.
* **Coverage Reporting**: JaCoCo (`jacoco-maven-plugin`) is **not configured** in root or module `pom.xml` files.
* **Test Utilities**: No shared test data builders exist, forcing verbose setup across future tests.

## 2. Goal & Proposed Testing Architecture

Establish a robust testing foundation for the platform monorepo:
1. Standardize unit testing conventions using JUnit 5 and AssertJ across all backend Maven modules.
2. Implement integration test infrastructure using **Testcontainers with real PostgreSQL** (never H2 or in-memory DBs) to validate multi-tenancy and RLS policies.
3. Provide reusable test data builders (enabling tenant and employee creation in 3 lines of code).
4. Configure JaCoCo code coverage reporting in Maven with minimum coverage thresholds.

---

## 3. Scope

### In Scope

* **Maven Dependencies**: Add Testcontainers (`org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`) and JaCoCo plugin (`jacoco-maven-plugin`) to `code/backend/pom.xml`.
* **Base Integration Test Setup**: Abstract base integration test class (`AbstractIntegrationTest`) using Testcontainers for PostgreSQL, managing shared container lifecycle across integration test runs.
* **Test Data Builders**: Reusable builder classes (`TenantTestBuilder`, `EmployeeTestBuilder`) located in `shared` test utilities.
* **JaCoCo Coverage Setup**: Maven JaCoCo plugin configuration for generating HTML coverage reports (`mvn jacoco:report`) and enforcing minimum coverage rules.
* **Sample RLS Integration Test**: Verify multi-tenant RLS isolation with a real Postgres container test.

### Out of Scope

* End-to-end browser UI automation testing (Cypress/Playwright).
* Performance, stress, or load testing infrastructure (`PLAT-14`).
* Azure staging environment test runners (`W-54`).

---

## 4. Proposed Implementation Detail

### 4.1 Unit Testing Strategy
* Standardize on **JUnit Jupiter 5** and **AssertJ** (`org.assertj.core.api.Assertions`).
* Use **Mockito** (`@ExtendWith(MockitoExtension.class)`) for mocking dependencies in domain services (`serviceimpl`).
* Unit tests execute in isolation without Spring application context overhead (`mvn test`).

### 4.2 PostgreSQL Integration Testing Strategy (Testcontainers)
* **Real PostgreSQL Container**: Spin up a PostgreSQL 16 container via `org.testcontainers.containers.PostgreSQLContainer`.
* **No In-Memory DBs**: In-memory databases like H2 are strictly prohibited because they do not support PostgreSQL RLS policies (`CREATE POLICY ... ON ...`).
* **Base Test Class**:
  ```java
  @SpringBootTest
  @Testcontainers
  public abstract class AbstractIntegrationTest {
      @Container
      static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
              .withDatabaseName("infinevodb")
              .withUsername("app_user")
              .withPassword("secret");
      
      @DynamicPropertySource
      static void configureProperties(DynamicPropertyRegistry registry) {
          registry.add("spring.datasource.url", postgres::getJdbcUrl);
          registry.add("spring.datasource.username", postgres::getUsername);
          registry.add("spring.datasource.password", postgres::getPassword);
      }
  }
  ```
* **Tenant RLS Validation**: Tests verify that switching `TenantContext` prevents cross-tenant data leaks at the DB layer.

### 4.3 Test Data Builder Strategy
* Provide ergonomic, fluent builder classes to assemble test fixtures efficiently.
* **Acceptance Standard**: Create a tenant and an employee in **3 lines of code**:
  ```java
  Tenant tenant = TenantTestBuilder.builder().withName("Acme Corp").buildAndSave(tenantRepository);
  Employee employee = EmployeeTestBuilder.builder().withTenant(tenant).withEmail("alice@acme.com").buildAndSave(employeeRepository);
  ```

### 4.4 Coverage Reporting Strategy (JaCoCo)
* Configure `jacoco-maven-plugin` (v0.8.12) in `code/backend/pom.xml`.
* Generate code coverage reports during `mvn verify` execution.
* Target initial minimum coverage thresholds (e.g., 70% line coverage for new `serviceimpl` classes).

---

## 5. Component Changes

| Component | File / Path | Proposed Change |
|---|---|---|
| Parent POM | `code/backend/pom.xml` | Add Testcontainers & JaCoCo plugin management |
| Shared Module | `code/backend/shared/pom.xml` | Add test utility exports for builders and test fixtures |
| Integration Base | `code/backend/shared/src/test/java/com/infinevo/shared/test/AbstractIntegrationTest.java` | Base class for Testcontainers PostgreSQL integration tests |
| Data Builders | `code/backend/shared/src/test/java/com/infinevo/shared/test/builder/*Builder.java` | Ergonomic data builders (`TenantTestBuilder`, `EmployeeTestBuilder`) |
| JaCoCo Plugin | `code/backend/pom.xml` | Configure `jacoco-maven-plugin` execution goals |

---

## 6. Database Changes

> No production database schema changes. Testcontainers applies Flyway migrations (`code/backend/migration/`) dynamically during test container startup.

---

## 7. Acceptance Criteria

- [ ] Testcontainers PostgreSQL dependency (`org.testcontainers:postgresql`) configured in parent `pom.xml`.
- [ ] JaCoCo plugin (`jacoco-maven-plugin`) configured in parent `pom.xml` producing execution reports.
- [ ] `AbstractIntegrationTest` runs against a real PostgreSQL Docker container (H2 is prohibited).
- [ ] Tenant data isolation can be verified via real PostgreSQL RLS execution in an integration test.
- [ ] Test data builders allow creating a tenant and an employee in **3 lines of code**.
- [ ] `./mvnw verify` runs unit tests, integration tests against Testcontainers, and generates JaCoCo coverage reports without error.

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Slow Testcontainers startup on CI runners | Medium | Share a single static PostgreSQL container instance across integration test classes. |
| Docker daemon unavailable on local developer machine | Low | Document Docker Desktop requirements in `CONTRIBUTING.md` and fail fast with clear instructions. |
| In-memory DB fallback accidentally added by developer | Medium | Add Maven enforcer rule / code review checks banning `h2` dependency in `pom.xml`. |

---

## 9. Validation Plan

1. **Verify Maven Dependency Resolution**: Run `./mvnw dependency:tree` to confirm Testcontainers and JaCoCo plugins resolve cleanly.
2. **Verify 3-Line Data Builder Test**: Execute a test verifying tenant and employee creation in 3 lines:
   ```bash
   ./mvnw test -Dtest=EmployeeTestBuilderTest
   ```
3. **Verify PostgreSQL RLS Integration Test**: Run an integration test that attempts cross-tenant query execution and verifies PostgreSQL RLS blocks unauthorized rows.
4. **Verify JaCoCo Report Generation**: Run `./mvnw verify` and inspect `target/site/jacoco/index.html` to confirm coverage report generation.
