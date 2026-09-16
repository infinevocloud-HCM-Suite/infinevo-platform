package com.infinevo.shared.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * W-04 — Dynamic property initializer for Testcontainers PostgreSQL.
 *
 * <p>Starts a single PostgreSQL 16 container with the platform database name
 * ({@code infinevo}) and creates the non-owner application role {@code app_user}.
 * All integration tests that extend {@link AbstractIntegrationTest} share this
 * container instance to minimise startup overhead (static single-instance pattern).
 *
 * <p>The Spring datasource is pointed at {@code app_user} so that PostgreSQL
 * Row-Level Security policies are enforced during tests — the owner role
 * ({@code BYPASSRLS}) is never used for application queries.
 */
public class PostgresTestContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** Non-owner application role. RLS policies apply to this role. */
    static final String APP_USER = "app_user";

    /** Password for the {@code app_user} role. Test-only — not a secret. */
    static final String APP_USER_PASSWORD = "app_user_pass";

    /** Migration user role owning schemas and running Flyway DDL. */
    static final String MIGRATION_USER = "migration_user";

    /** Password for the {@code migration_user} role. Test-only — not a secret. */
    static final String MIGRATION_USER_PASSWORD = "migration_user_pass";

    /** Readonly role for reporting and analytics. */
    static final String READONLY_USER = "readonly_user";

    /** Password for the {@code readonly_user} role. Test-only — not a secret. */
    static final String READONLY_USER_PASSWORD = "readonly_user_pass";

    /** Database name matching the production schema. */
    static final String DATABASE_NAME = "infinevo";

    @SuppressWarnings("resource") // container lifecycle is managed by the JVM shutdown hook
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName(DATABASE_NAME);

    private static volatile boolean started = false;

    /** Returns {@code true} if Docker is available on this machine. */
    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns the JDBC URL of the running PostgreSQL Testcontainer (starting container if needed). */
    static String getJdbcUrl() {
        startIfNeeded();
        return POSTGRES.getJdbcUrl();
    }

    /** Starts the container and provisions roles, schemas, and grants (idempotent). */
    private static synchronized void startIfNeeded() {
        if (!started) {
            POSTGRES.start();
            provisionDatabase();
            started = true;
        }
    }

    /**
     * Provisions the test container database by creating the platform roles and
     * executing the canonical 02-schemas.sql and 03-grants.sql scripts over JDBC.
     */
    private static void provisionDatabase() {
        try (Connection conn =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            createRole(conn, APP_USER, APP_USER_PASSWORD);
            createRole(conn, MIGRATION_USER, MIGRATION_USER_PASSWORD);
            createRole(conn, READONLY_USER, READONLY_USER_PASSWORD);

            executeSqlScript(conn, "db/provision/02-schemas.sql");
            executeSqlScript(conn, "db/provision/03-grants.sql");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to provision test container database", e);
        }
    }

    private static void createRole(Connection conn, String roleName, String password) throws SQLException {
        conn.createStatement()
                .execute("DO $$\n"
                        + "BEGIN\n"
                        + "    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + roleName + "') THEN\n"
                        + "        CREATE ROLE " + roleName
                        + " WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD '" + password + "';\n"
                        + "    ELSE\n"
                        + "        ALTER ROLE " + roleName + " WITH NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;\n"
                        + "    END IF;\n"
                        + "END\n"
                        + "$$;");
    }

    private static void executeSqlScript(Connection conn, String resourcePath) {
        try (InputStream is =
                PostgresTestContainerInitializer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("SQL provisioning script not found on classpath: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            conn.createStatement().execute(sql);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to execute SQL script: " + resourcePath, e);
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        startIfNeeded();
        TestPropertyValues.of(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + APP_USER,
                        "spring.datasource.password=" + APP_USER_PASSWORD,
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect")
                .applyTo(ctx.getEnvironment());
    }
}
