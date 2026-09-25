package com.infinevo.shared.authz;

import static com.infinevo.shared.authz.AuthzTestSupport.authenticate;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.cache.RedisCacheService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-11.2 spec section 7 — a user's actions in tenant A do not leak into tenant B, through the whole
 * path: real Postgres under row-level security as {@code app_user}, and real Redis.
 *
 * <p>The tables are {@code W-11.1}'s, created by the shipped scripts ({@code V001}, {@code V009},
 * {@code V020}-{@code V023}) in a database of this test's own — {@code V022}'s trigger seeds roles on
 * every tenant insert and would otherwise reach the suites sharing {@code infinevo}, the reason
 * {@code core}'s {@code AuthzTestSchema} does the same.
 *
 * <p>The {@link ActionSource} and resolver here are test adapters doing what {@code core}'s will: one
 * query as {@code app_user} in a transaction with the tenant bound. Their queries carry <em>no</em>
 * {@code tenant_id} predicate on purpose — row-level security is the only thing standing between
 * tenants, so if it were off these assertions would fail.
 *
 * <p>One Keycloak user is a member of both tenants, with a profile row in each (the shape of
 * {@code core.user_account}, {@code V009}): {@code hr} in A, only {@code employee} in B.
 */
@SpringBootTest(classes = PermissionRlsIT.TestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, RedisTestContainerInitializer.class})
@EnabledIfDockerAvailable
class PermissionRlsIT extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {
        // Boots only to start the shared containers through the initializers.
    }

    private static final String DATABASE = "infinevo_permission";
    private static final String READ = "core.employee.read";
    private static final String READ_OWN = "core.employee.read_own";

    private static String jdbcUrl;

    private final UUID keycloakUser = UUID.randomUUID();
    private UUID tenantA;
    private UUID tenantB;
    private UUID accountInA;

    private LettuceConnectionFactory connections;
    private CacheService cacheService;
    private PermissionCache cache;
    private CountingSource source;
    private PermissionService service;

    @BeforeAll
    static void provision() throws Exception {
        if (jdbcUrl != null) {
            return;
        }
        String url = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
        try (Connection conn = DriverManager.getConnection(
                url,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
            if (!tableExists(conn, "user_role")) {
                execute(conn, "db/migration/core/V001__tenant.sql");
                execute(conn, "db/migration/core/V009__user_account.sql");
                execute(conn, "db/migration/reference/V020__action.sql");
                execute(conn, "db/migration/core/V021__role.sql");
                execute(conn, "db/migration/core/V022__role_action.sql");
                execute(conn, "db/migration/core/V023__user_role.sql");
                // W-11.3: the catalogue correction, so this test sees the codes main actually ships.
                execute(conn, "db/migration/core/V025__catalogue_correction.sql");
            }
        }
        jdbcUrl = url;
    }

    @BeforeEach
    void setUp() throws SQLException {
        tenantA = insertTenant("Tenant A");
        tenantB = insertTenant("Tenant B");
        accountInA = insertUserAccount(tenantA, keycloakUser);
        UUID accountInB = insertUserAccount(tenantB, keycloakUser);
        grant(tenantA, accountInA, "hr");
        grant(tenantB, accountInB, "employee");

        connections = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                RedisTestContainerInitializer.getHost(), RedisTestContainerInitializer.getPort()));
        connections.afterPropertiesSet();
        connections.start();
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connections);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        cacheService = new RedisCacheService(template, new ObjectMapper());
        cache = new PermissionCache(cacheService);
        source = new CountingSource();
        service = new PermissionService(() -> cache, () -> source, PermissionRlsIT::userAccountIdOf);

        authenticate(keycloakUser);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        connections.destroy();
    }

    @Test
    @DisplayName("The same user holds hr's actions in A and only employee's in B - through cache and database")
    void actionsAreDecidedPerTenant() {
        TenantContext.set(tenantA);
        assertThat(service.holds(READ)).isTrue();

        TenantContext.set(tenantB);
        assertThat(service.holds(READ))
                .as("A's cached set must not answer for B")
                .isFalse();
        assertThat(service.holds(READ_OWN)).isTrue();

        TenantContext.set(tenantA);
        assertThat(service.holds(READ)).isTrue();
        assertThat(source.calls.get()).as("one load per tenant, then hits").isEqualTo(2);
    }

    @Test
    @DisplayName("Asked for A's profile row with B bound, the database returns nothing - RLS, not a WHERE clause")
    void rowLevelSecurityHidesTheOtherTenantsGrants() {
        assertThat(source.actionsOf(tenantA, accountInA)).contains(READ);
        assertThat(source.actionsOf(tenantB, accountInA)).isEmpty();
        assertThat(userAccountIdOf(tenantB, keycloakUser)).isPresent().get().isNotEqualTo(accountInA);
    }

    @Test
    @DisplayName("Bumping A's version leaves B's cached set in place")
    void bumpingOneTenantLeavesTheOtherAlone() {
        TenantContext.set(tenantA);
        service.holds(READ);
        TenantContext.set(tenantB);
        service.holds(READ_OWN);
        String versionB = cacheService
                .get(PermissionCache.versionKey(tenantB), String.class)
                .orElseThrow();

        cache.bumpVersion(tenantA);

        assertThat(cacheService.get(PermissionCache.versionKey(tenantB), String.class))
                .contains(versionB);
        assertThat(cacheService.get(PermissionCache.permissionKey(tenantB, keycloakUser, versionB), String[].class))
                .isPresent();
        assertThat(service.holds(READ_OWN)).isTrue();
        assertThat(source.calls.get()).as("B was a hit after A's bump").isEqualTo(2);
    }

    /** What core's PermissionReadServiceImpl does: one query in a transaction with the tenant bound. */
    private static final class CountingSource implements ActionSource {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Set<String> actionsOf(UUID tenantId, UUID userAccountId) {
            calls.incrementAndGet();
            return queryAsAppUser(
                    tenantId,
                    """
                    SELECT DISTINCT ra.action_code
                      FROM core.user_role ur
                      JOIN core.role_action ra ON ra.role_id = ur.role_id
                     WHERE ur.user_account_id = ?
                    """,
                    userAccountId);
        }
    }

    private static Optional<UUID> userAccountIdOf(UUID tenantId, UUID keycloakUserId) {
        return queryAsAppUser(
                        tenantId, "SELECT id::text FROM core.user_account WHERE keycloak_user_id = ?", keycloakUserId)
                .stream()
                .findFirst()
                .map(UUID::fromString);
    }

    private static Set<String> queryAsAppUser(UUID tenantId, String sql, UUID param) {
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD)) {
            conn.setAutoCommit(false);
            try (PreparedStatement bind =
                    conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                bind.setString(1, tenantId.toString());
                bind.execute();
            }
            Set<String> out = new LinkedHashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, param);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(rs.getString(1));
                    }
                }
            }
            conn.commit();
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    /** As the owner, so V022's trigger - not a hand call - gives the tenant its seven roles. */
    private static UUID insertTenant(String name) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        try (Connection conn = owner();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
        return tenantId;
    }

    private static UUID insertUserAccount(UUID tenantId, UUID keycloakUserId) throws SQLException {
        try (Connection conn = owner();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.user_account (tenant_id, keycloak_user_id, email, created_by, updated_by)
                        VALUES (?, ?, ?, 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, keycloakUserId);
            ps.setString(3, keycloakUserId + "@example.test");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static void grant(UUID tenantId, UUID userAccountId, String roleCode) throws SQLException {
        try (Connection conn = owner();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.user_role (tenant_id, user_account_id, role_id)
                        SELECT ?, ?, r.id FROM core.role r WHERE r.tenant_id = ? AND r.code = ?
                        """)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userAccountId);
            ps.setObject(3, tenantId);
            ps.setString(4, roleCode);
            assertThat(ps.executeUpdate())
                    .as("role %s exists in the tenant", roleCode)
                    .isEqualTo(1);
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM pg_tables WHERE schemaname = 'core' AND tablename = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void execute(Connection conn, String resource) throws Exception {
        try (InputStream is = PermissionRlsIT.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException("migration not on the test classpath: " + resource);
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}
