package com.infinevo.shared.authz;

import static com.infinevo.shared.authz.AuthzTestSupport.authenticate;
import static com.infinevo.shared.authz.AuthzTestSupport.identityResolver;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.cache.RedisCacheService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * W-11.2 spec section 7 — <strong>the ticket.</strong> Two {@link PermissionService} instances, each
 * with its own Redis connection, sharing one real Redis: a role change made through instance A is
 * seen by instance B without B being told.
 *
 * <p>This is the test the frozen design fails. Its cache was a map inside each process
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/auth/AuthzServiceImpl.java:29}),
 * and the invalidation reached only the replica that served the call
 * ({@code RoleActionController.java:94}), so B would keep answering from its own copy.
 *
 * <p>Each "replica" gets its own {@link LettuceConnectionFactory}, template and
 * {@link RedisCacheService} — nothing is shared between A and B but the Redis server and the stub
 * standing in for the database.
 */
@SpringBootTest(classes = PermissionReplicaIT.TestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, RedisTestContainerInitializer.class})
@EnabledIfDockerAvailable
class PermissionReplicaIT extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {
        // Boots only to start the shared Redis container through RedisTestContainerInitializer.
    }

    private static final String READ = "core.employee.read";
    private static final String UPDATE = "core.employee.update";

    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    /** The database both replicas read on a miss. */
    private final StubActionSource database = new StubActionSource();

    private Replica a;
    private Replica b;

    @BeforeEach
    void setUp() {
        a = new Replica(database);
        b = new Replica(database);
        TenantContext.set(tenant);
        authenticate(user);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        a.close();
        b.close();
    }

    @Test
    @DisplayName("A change bumped through replica A is seen by replica B, which was never told")
    void roleChangeReachesEveryReplica() {
        database.grant(tenant, user, READ);

        assertThat(a.service.holds(UPDATE)).isFalse();
        assertThat(b.service.holds(UPDATE)).isFalse();
        assertThat(database.calls())
                .as("B found A's entry in the shared cache - one load between two replicas")
                .isEqualTo(1);

        // The role change: the data changes, and A - the replica that served it - bumps.
        database.grant(tenant, user, READ, UPDATE);
        a.cache.bumpVersion(tenant);

        assertThat(b.service.holds(UPDATE)).isTrue();
        assertThat(a.service.holds(UPDATE)).isTrue();
        assertThat(database.calls()).isEqualTo(2);
    }

    @Test
    @DisplayName("Without the bump, B keeps serving the old set - the cache is real, not a pass-through")
    void withoutBumpTheCachedSetStands() {
        database.grant(tenant, user, READ);
        assertThat(a.service.holds(READ)).isTrue();
        assertThat(b.service.holds(READ)).isTrue();

        database.grant(tenant, user, UPDATE);

        assertThat(b.service.holds(READ)).as("stale by design until a bump").isTrue();
        assertThat(b.service.holds(UPDATE)).isFalse();
        assertThat(database.calls()).isEqualTo(1);

        b.cache.bumpVersion(tenant);
        assertThat(a.service.holds(READ))
                .as("and a bump from B reaches A just the same")
                .isFalse();
        assertThat(a.service.holds(UPDATE)).isTrue();
    }

    /** One application instance: its own connection pool to Redis, and its own service objects. */
    private static final class Replica implements AutoCloseable {
        final LettuceConnectionFactory connections;
        final PermissionCache cache;
        final PermissionService service;

        Replica(ActionSource database) {
            connections = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                    RedisTestContainerInitializer.getHost(), RedisTestContainerInitializer.getPort()));
            connections.afterPropertiesSet();
            connections.start();
            RedisTemplate<String, String> template = new RedisTemplate<>();
            template.setConnectionFactory(connections);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new StringRedisSerializer());
            template.afterPropertiesSet();
            cache = new PermissionCache(new RedisCacheService(template, new ObjectMapper()));
            service = new PermissionService(() -> cache, () -> database, identityResolver());
        }

        @Override
        public void close() {
            connections.destroy();
        }
    }
}
