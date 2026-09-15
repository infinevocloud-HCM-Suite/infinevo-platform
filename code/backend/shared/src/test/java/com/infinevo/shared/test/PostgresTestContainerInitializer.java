package com.infinevo.shared.test;

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

    /** Starts the container and creates the {@code app_user} role (idempotent). */
    private static synchronized void startIfNeeded() {
        if (!started) {
            POSTGRES.start();
            createAppUserRole();
            started = true;
        }
    }

    /**
     * Creates the {@code app_user} role inside the container using the owner
     * connection. This mirrors the production setup where Flyway (owner) creates
     * the schema and the application connects as {@code app_user}.
     */
    private static void createAppUserRole() {
        try (Connection conn =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            conn.createStatement().execute("CREATE ROLE " + APP_USER + " LOGIN PASSWORD '" + APP_USER_PASSWORD + "'");
            conn.createStatement().execute("GRANT CONNECT ON DATABASE " + DATABASE_NAME + " TO " + APP_USER);
            // Grant schema usage so app_user can see tables once they exist (W-06+)
            conn.createStatement().execute("GRANT USAGE ON SCHEMA public TO " + APP_USER);
            // Grant default privileges so future tables are accessible
            conn.createStatement()
                    .execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public"
                            + " GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + APP_USER);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create app_user role in test container", e);
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
