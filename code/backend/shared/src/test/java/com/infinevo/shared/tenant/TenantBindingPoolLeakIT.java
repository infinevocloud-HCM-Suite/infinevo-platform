package com.infinevo.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-08 — regression test for finding F-10: the tenant binding must not survive the connection's
 * return to the pool.
 *
 * <p>The pool is deliberately sized to exactly one connection, so the second transaction is
 * guaranteed to run on the same physical PostgreSQL session as the first. If the binding were
 * written at session scope — which is what {@code set_config(..., false)} did when the proxy bound
 * at pool checkout, while auto-commit was still on — the second transaction would start already
 * bound to the first transaction's tenant, and would read that tenant's rows through RLS. That is
 * a cross-tenant leak.
 *
 * <p>This test fails against the checkout-time binding and passes against the lazy,
 * transaction-local binding.
 */
@SpringBootTest(
        classes = TenantBindingPoolLeakIT.TestApp.class,
        properties = {
            "spring.datasource.hikari.maximum-pool-size=1",
            "spring.datasource.hikari.minimum-idle=1",
            "spring.datasource.hikari.pool-name=tenant-leak-pool"
        })
class TenantBindingPoolLeakIT extends AbstractIntegrationTest {

    /** Distinct from the tenants used by {@link TenantBindingIT} — the container is shared. */
    private static final UUID TENANT_A = UUID.fromString("c3333333-3333-3333-3333-333333333333");

    /**
     * The whole autoconfiguration stack, exactly as {@link TenantBindingIT} starts it. Excluding
     * only {@code HibernateJpaAutoConfiguration} left {@code JpaRepositoriesAutoConfiguration}
     * — still active, because {@code spring-boot-starter-data-jpa} is a test dependency of this
     * module — registering the shared-EntityManager holder {@code jpaSharedEM_entityManagerFactory},
     * which then had no {@code entityManagerFactory} to point at and the context failed to load.
     *
     * <p>JPA is simply unused here: the test talks to the database through {@link JdbcTemplate}
     * over the {@link DataSourceTransactionManager} declared below, which wins over the JPA
     * transaction manager because that one is {@code @ConditionalOnMissingBean}.
     */
    @SpringBootApplication(scanBasePackages = "com.infinevo.shared")
    static class TestApp {

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeAll
    static void applyMigrationsAndSeed() throws Exception {
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "tenant")) {
                executeSqlScript(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "user_tenant")) {
                executeSqlScript(conn, "db/migration/core/V002__user_tenant.sql");
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO core.tenant (tenant_id, name) VALUES ('" + TENANT_A
                        + "', 'Pool Leak Tenant A') ON CONFLICT DO NOTHING");
            }
        }
    }

    private static Connection migrationConnection() throws Exception {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    private static boolean tableExists(Connection conn, String table) throws Exception {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT 1 FROM pg_tables WHERE schemaname = 'core' AND tablename = '" + table + "'")) {
            return rs.next();
        }
    }

    private static void executeSqlScript(Connection conn, String resourcePath) throws Exception {
        try (var is = TenantBindingPoolLeakIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                String sql = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                conn.createStatement().execute(sql);
            }
        }
    }

    @BeforeEach
    void clearContext() {
        TenantContext.clear();
    }

    @AfterEach
    void clearContextAfter() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("F-10: the tenant binding does not survive the connection's return to the pool")
    void tenantBindingDoesNotLeakToTheNextBorrowerOfThePooledConnection() {
        // 1. A transaction with tenant A bound. RLS lets exactly its own row through.
        TenantContext.set(TENANT_A);
        try {
            Integer tenantARows = transactionTemplate.execute(
                    status -> jdbcTemplate.queryForObject("SELECT count(*) FROM core.tenant", Integer.class));
            assertThat(tenantARows).isEqualTo(1);

            String boundInsideTransaction = transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                    "SELECT coalesce(current_setting('app.current_tenant_id', true), '')", String.class));
            assertThat(boundInsideTransaction).isEqualTo(TENANT_A.toString());
        } finally {
            // 2. The connection goes back to the pool of one and the thread forgets the tenant.
            TenantContext.clear();
        }

        // 3. The next borrower is the same physical session. It must start unbound.
        String leakedSetting = transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                "SELECT coalesce(current_setting('app.current_tenant_id', true), '')", String.class));
        assertThat(leakedSetting).isEmpty();

        Integer rowsVisibleUnbound = transactionTemplate.execute(
                status -> jdbcTemplate.queryForObject("SELECT count(*) FROM core.tenant", Integer.class));
        assertThat(rowsVisibleUnbound).isZero();
    }
}
