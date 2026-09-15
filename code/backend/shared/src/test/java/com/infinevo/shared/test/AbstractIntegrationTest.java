package com.infinevo.shared.test;

import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * W-04 — Abstract base class for integration tests.
 *
 * <p>Subclasses get:
 * <ul>
 *   <li>A PostgreSQL 16 Testcontainer with the {@code infinevo} database</li>
 *   <li>Datasource configured as the non-owner {@code app_user} role, ensuring
 *       PostgreSQL Row-Level Security policies are enforced (the owner role
 *       has {@code BYPASSRLS} and would silently skip RLS checks)</li>
 *   <li>Static single-instance container lifecycle — the container is started
 *       once and reused across all test classes for fast feedback</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // In core, hrms, or payroll module test source:
 * @SpringBootTest
 * class MyFeatureIT extends AbstractIntegrationTest {
 *     @Autowired
 *     private MyRepository repo;
 *
 *     @Test
 *     void somethingWorks() {
 *         // test against real PostgreSQL
 *     }
 * }
 * }</pre>
 *
 * <h3>Module placement rules (W-04 §3)</h3>
 * <ul>
 *   <li>{@code shared/src/test/} — this base class and generic test utilities only.
 *       No domain entity builders.</li>
 *   <li>{@code core/src/test/} — core entity builders</li>
 *   <li>{@code hrms/src/test/} — HRMS entity builders</li>
 *   <li>{@code payroll/src/test/} — payroll entity builders</li>
 * </ul>
 *
 * <h3>Deferred to future work items</h3>
 * <ul>
 *   <li>Tenant + employee "three lines" proof → W-07 / W-13</li>
 *   <li>RLS policy validation tests → W-07</li>
 *   <li>Flyway schema migrations → W-06</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfDockerAvailable
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
public abstract class AbstractIntegrationTest {
    // Intentionally empty — configuration is inherited via annotations.
    // Subclasses add @SpringBootTest pointing at their module's application class.
}
