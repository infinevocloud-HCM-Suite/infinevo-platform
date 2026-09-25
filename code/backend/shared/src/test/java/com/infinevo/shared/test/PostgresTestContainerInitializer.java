package com.infinevo.shared.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * W-04 — Dynamic property initializer for Testcontainers PostgreSQL.
 *
 * <p>Starts a single PostgreSQL 16 container with the platform database name
 * ({@code infinevo}) and runs the canonical {@code infra/postgres/} scripts against it.
 * All integration tests that extend {@link AbstractIntegrationTest} share this
 * container instance to minimise startup overhead (static single-instance pattern).
 *
 * <p>The Spring datasource is pointed at the non-owner {@code app_user} role, so that
 * PostgreSQL Row-Level Security policies are enforced during tests — the owner role
 * ({@code BYPASSRLS}) is never used for application queries.
 */
public class PostgresTestContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /**
     * Non-owner application login role. Holds every grant and every RLS policy, and is what
     * the application connects as.
     */
    public static final String APP_USER = "app_user";

    /** Password for the {@code app_user} role. Test-only — not a secret. */
    public static final String APP_USER_PASSWORD = "app_user_pass";

    /**
     * Login role for the {@code worker} container. A member of {@code app_user}, so it
     * inherits the same grants and policies while connecting as itself rather than sharing
     * the application's credential (W-56).
     */
    public static final String WORKER_USER = "worker_user";

    /** Password for {@link #WORKER_USER}. Test-only — not a secret. */
    public static final String WORKER_USER_PASSWORD = "worker_user_pass";

    /** Migration user role owning schemas and running Flyway DDL. */
    public static final String MIGRATION_USER = "migration_user";

    /** Password for the {@code migration_user} role. Test-only — not a secret. */
    public static final String MIGRATION_USER_PASSWORD = "migration_user_pass";

    /** Readonly role for reporting and analytics. */
    public static final String READONLY_USER = "readonly_user";

    /** Password for the {@code readonly_user} role. Test-only — not a secret. */
    public static final String READONLY_USER_PASSWORD = "readonly_user_pass";

    /** Login role owning the Keycloak database. No grant on the platform schemas. */
    public static final String KEYCLOAK_USER = "keycloak_user";

    /** Password for the {@code keycloak_user} role. Test-only — not a secret. */
    public static final String KEYCLOAK_USER_PASSWORD = "local_keycloak_pw";

    /** Database name matching the production schema. */
    public static final String DATABASE_NAME = "infinevo";

    /**
     * Connection slots on the test container (W-04.1). The image default is 100.
     *
     * <p>Spring caches one context per distinct test configuration, and every cached context holds
     * a live HikariCP pool against this one container. {@code shared} alone has 16 such contexts;
     * at Hikari's default pool of 10 that is 160 connections, past 100 before the last class runs,
     * and the next {@code DriverManager} connection fails with {@code 53300 too_many_connections}.
     * 200 slots with {@link #DEFAULT_TEST_POOL_SIZE} of 2 leaves room for roughly 80 contexts plus
     * the raw connections tests open themselves. {@code ConnectionBudgetIT} checks the arithmetic.
     */
    static final int MAX_CONNECTIONS = 200;

    /**
     * Pool size given to every test context that does not choose its own (W-04.1). A test that
     * needs a specific size sets {@code spring.datasource.hikari.maximum-pool-size} itself, as
     * {@code TenantBindingPoolLeakIT} does, and this initializer leaves it alone.
     */
    static final int DEFAULT_TEST_POOL_SIZE = 2;

    static final String POOL_SIZE_PROPERTY = "spring.datasource.hikari.maximum-pool-size";

    @SuppressWarnings("resource") // container lifecycle is managed by the JVM shutdown hook
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName(DATABASE_NAME)
            .withCommand("postgres", "-c", "max_connections=" + MAX_CONNECTIONS);

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
    public static String getJdbcUrl() {
        startIfNeeded();
        return POSTGRES.getJdbcUrl();
    }

    /**
     * Creates an additional, empty database on the shared container and provisions it with the
     * canonical schemas and grants, returning its JDBC URL.
     *
     * <p>Roles are cluster-wide, so {@code 01-roles.sql} is not re-run; {@code 02-schemas.sql} and
     * {@code 03-grants.sql} are database-scoped and are. Both are idempotent, and {@code
     * 03-grants.sql} reads {@code current_database()} rather than a hardcoded name, so they apply
     * unchanged here.
     *
     * <p>Exists so a test can run the <em>shipped</em> migration tree against a database no other
     * test has touched. The default database carries the {@code db/migration-test/} fixtures, whose
     * versions collide with the shipped ones, and {@code TenantIsolationIT} applies {@code
     * V001__tenant.sql} by hand — either would make a real Flyway run fail for reasons that say
     * nothing about the scripts (#136).
     */
    public static synchronized String provisionAdditionalDatabase(String databaseName) {
        // The name goes into CREATE DATABASE unquoted, because an identifier cannot be a
        // bind parameter. Callers are test classes passing a literal, so this is a guard
        // against a typo rather than against an attacker - but an unchecked identifier
        // concatenated into DDL is the habit, not the value, that goes wrong later.
        if (!databaseName.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("not a safe database identifier: " + databaseName);
        }
        startIfNeeded();
        String url =
                "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/" + databaseName;
        try (Connection admin =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            boolean exists;
            try (var rs = admin.createStatement()
                    .executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + databaseName + "'")) {
                exists = rs.next();
            }
            if (!exists) {
                // CREATE DATABASE cannot run inside a transaction block; autocommit is on by default.
                admin.createStatement().execute("CREATE DATABASE " + databaseName);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create test database " + databaseName, e);
        }
        try (Connection conn = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            executeSqlScript(conn, "db/provision/02-schemas.sql");
            executeSqlScript(conn, "db/provision/03-grants.sql");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to provision test database " + databaseName, e);
        }
        return url;
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
     * Provisions the test container database by executing the canonical
     * 01-roles.sql, 02-schemas.sql, and 03-grants.sql scripts over JDBC.
     */
    private static void provisionDatabase() {
        try (Connection conn =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            executeSqlScriptWithVariables(conn, "db/provision/01-roles.sql");
            executeSqlScript(conn, "db/provision/02-schemas.sql");
            executeSqlScript(conn, "db/provision/03-grants.sql");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to provision test container database", e);
        }
    }

    /**
     * The psql variables {@code 01-roles.sql} expects, and the test-only values standing in
     * for them. {@code psql} expands {@code :'name'} itself; JDBC does not, so this path
     * substitutes them before sending the script.
     */
    private static final Map<String, String> SCRIPT_VARIABLES = Map.of(
            "app_pw", APP_USER_PASSWORD,
            "worker_pw", WORKER_USER_PASSWORD,
            "migration_pw", MIGRATION_USER_PASSWORD,
            "readonly_pw", READONLY_USER_PASSWORD,
            "keycloak_pw", KEYCLOAK_USER_PASSWORD);

    /** Matches a psql variable reference in single-quote form, e.g. {@code :'app_pw'}. */
    private static final Pattern PSQL_VARIABLE = Pattern.compile(":'([a-z_][a-z0-9_]*)'");

    private static void executeSqlScriptWithVariables(Connection conn, String resourcePath) {
        try (InputStream is =
                PostgresTestContainerInitializer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("SQL provisioning script not found on classpath: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            conn.createStatement().execute(substituteVariables(sql, resourcePath));
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to execute SQL script: " + resourcePath, e);
        }
    }

    /**
     * Replaces every {@code :'name'} with its test value, and refuses a script carrying one
     * this class does not know.
     *
     * <p>The refusal is the point. A new role added to the canonical script arrives here as
     * an unsubstituted variable, and Postgres reports it as {@code syntax error at or near
     * ":"} — a message that names neither the variable nor the file. W-56 added {@code
     * worker_user} and cost a red CI run finding that out. Naming the variable turns it into
     * a one-line fix.
     */
    static String substituteVariables(String sql, String resourcePath) {
        String substituted = sql;
        for (Map.Entry<String, String> e : SCRIPT_VARIABLES.entrySet()) {
            substituted = substituted.replace(":'" + e.getKey() + "'", "'" + e.getValue() + "'");
        }
        // Only executable text is checked. A header comment naming a variable is documentation,
        // not a statement, and tripping on it would push the next author to stop documenting them.
        Matcher leftover = PSQL_VARIABLE.matcher(stripLineComments(substituted));
        if (leftover.find()) {
            throw new IllegalStateException("No test value for psql variable :'" + leftover.group(1) + "' used by "
                    + resourcePath + ". Add it to SCRIPT_VARIABLES in PostgresTestContainerInitializer.");
        }
        return substituted;
    }

    /** Removes {@code --} line comments. Crude, and enough: these scripts have no {@code --} inside a literal. */
    private static String stripLineComments(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
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

    /**
     * A JWK set URI that resolves to nothing, so a context carrying {@code ResourceServerConfig}
     * can start without a Keycloak.
     *
     * <p>W-10 made the resource server refuse to start with no issuer configured, deliberately —
     * an application that boots without one authenticates nobody while reading as secured. Every
     * integration test whose component scan reaches {@code com.infinevo.shared.security} therefore
     * needs <em>some</em> decoder. This one is real but unreachable: {@code jwk-set-uri} is fetched
     * lazily, so the context starts, and any token presented against it fails to validate. Tests
     * that authenticate with {@code @WithMockUser} are unaffected; a test that needs a token that
     * actually verifies overrides this with a live issuer, as {@code LoginFlowIT} does.
     */
    private static final String UNREACHABLE_JWK_SET_URI = "http://127.0.0.1:1/protocol/openid-connect/certs";

    /**
     * The value behind {@code ${KEYCLOAK_ISSUER_URI}}, which the {@code app} and {@code worker}
     * profiles reference with no default — on purpose, so a deployment cannot start without one.
     *
     * <p>That deliberate absence reaches the tests too: any context loading one of those modules'
     * {@code application.yml} fails at placeholder resolution before a single bean is built, which
     * is what happened to {@code SchedulerLockIT} the moment the worker gained the property. It is
     * supplied here rather than given a default in the profile, because a default in the profile is
     * exactly the thing that lets a real deployment come up unsecured.
     *
     * <p>It is unreachable, and it is not what builds the decoder either — Boot prefers
     * {@code jwk-set-uri} when both are set. It exists only so the placeholder resolves.
     */
    private static final String UNREACHABLE_ISSUER_URI = "http://127.0.0.1:1/realms/infinevo";

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        startIfNeeded();
        TestPropertyValues.of(
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + UNREACHABLE_JWK_SET_URI,
                        "KEYCLOAK_ISSUER_URI=" + UNREACHABLE_ISSUER_URI,
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + APP_USER,
                        "spring.datasource.password=" + APP_USER_PASSWORD,
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect")
                .applyTo(ctx.getEnvironment());
        // Inline @SpringBootTest properties and application.yml are already in the environment
        // when initializers run, so a test (or module) that sized its own pool keeps it.
        if (!ctx.getEnvironment().containsProperty(POOL_SIZE_PROPERTY)) {
            TestPropertyValues.of(
                            POOL_SIZE_PROPERTY + "=" + DEFAULT_TEST_POOL_SIZE,
                            "spring.datasource.hikari.minimum-idle=0")
                    .applyTo(ctx.getEnvironment());
        }
    }
}
