package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.1 spec, Tests: {@link AiUsageRepository#countOne} - the whole cap check - against PostgreSQL as
 * {@code app_user}, with row-level security on {@code core.ai_usage} (V192).
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class AiUsageIT extends AbstractIntegrationTest {

    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);
    private static final LocalDate DECEMBER = LocalDate.of(2026, 12, 1);

    @Autowired
    private AiUsageRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setUp() throws SQLException {
        tenantA = AuthzTestSchema.insertTenant("AiUsage A " + UUID.randomUUID());
        tenantB = AuthzTestSchema.insertTenant("AiUsage B " + UUID.randomUUID());
    }

    @Test
    @DisplayName("cap 3: three requests are counted, the fourth returns nothing and the count stays 3")
    void capThree_fourthIsRefused() throws SQLException {
        assertThat(countOneAs(tenantA, NOVEMBER, 3)).contains(1);
        assertThat(countOneAs(tenantA, NOVEMBER, 3)).contains(2);
        assertThat(countOneAs(tenantA, NOVEMBER, 3)).contains(3);
        assertThat(countOneAs(tenantA, NOVEMBER, 3)).isEmpty();

        assertThat(storedCount(tenantA, NOVEMBER)).isEqualTo(3);
    }

    @Test
    @DisplayName("twenty requests at the same moment with cap 10: ten counted, ten refused, request_count = 10")
    void parallel_neverPassesTheCap() throws Exception {
        int calls = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(calls);
        try {
            List<Future<Optional<Integer>>> results = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                Callable<Optional<Integer>> call = () -> {
                    start.await();
                    return countOneAs(tenantA, NOVEMBER, 10);
                };
                results.add(pool.submit(call));
            }
            start.countDown();

            int counted = 0;
            int refused = 0;
            for (Future<Optional<Integer>> result : results) {
                if (result.get(60, TimeUnit.SECONDS).isPresent()) {
                    counted++;
                } else {
                    refused++;
                }
            }
            assertThat(counted).isEqualTo(10);
            assertThat(refused).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }

        assertThat(storedCount(tenantA, NOVEMBER)).isEqualTo(10);
    }

    @Test
    @DisplayName("a new month starts a new row at 1; the old month's row is left as it was")
    void newMonth_startsAtOne() throws SQLException {
        assertThat(countOneAs(tenantA, NOVEMBER, 2)).contains(1);
        assertThat(countOneAs(tenantA, NOVEMBER, 2)).contains(2);
        assertThat(countOneAs(tenantA, NOVEMBER, 2)).isEmpty();

        assertThat(countOneAs(tenantA, DECEMBER, 2)).contains(1);

        assertThat(storedCount(tenantA, NOVEMBER)).isEqualTo(2);
        assertThat(storedCount(tenantA, DECEMBER)).isEqualTo(1);
    }

    @Test
    @DisplayName("tenant B cannot read tenant A's row as app_user, through the repository or in plain SQL")
    void tenantB_cannotReadTenantA() throws SQLException {
        assertThat(countOneAs(tenantA, NOVEMBER, 5)).contains(1);

        // Control: the row exists, and tenant A reads it.
        assertThat(storedCount(tenantA, NOVEMBER)).isEqualTo(1);
        assertThat(readAs(tenantA, tenantA)).isPresent();

        assertThat(readAs(tenantB, tenantA)).isEmpty();

        try (Connection conn = AuthzTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement bind =
                    conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                bind.setString(1, tenantB.toString());
                bind.execute();
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core.ai_usage WHERE tenant_id = ?")) {
                ps.setObject(1, tenantA);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1)).isZero();
                }
            }
            conn.rollback();
        }
    }

    /**
     * Reads tenant {@code rowOwner}'s November row while bound to {@code boundTenant}. The read runs inside a
     * transaction, as it does behind {@code AiAccessGateImpl}: the tenant binding is transaction-local, so a
     * repository read on an auto-commit connection is refused.
     */
    private Optional<AiUsage> readAs(UUID boundTenant, UUID rowOwner) {
        return as(boundTenant, () -> new TransactionTemplate(transactionManager)
                .execute(status -> repository.findByTenantIdAndUsageMonth(rowOwner, NOVEMBER)));
    }

    private Optional<Integer> countOneAs(UUID tenantId, LocalDate month, int cap) {
        return as(tenantId, () -> repository.countOne(tenantId, month, cap));
    }

    private static <T> T as(UUID tenantId, java.util.function.Supplier<T> work) {
        TenantContext.set(tenantId);
        try {
            return work.get();
        } finally {
            TenantContext.clear();
        }
    }

    /** The stored {@code request_count}, read as the schema owner. */
    private static int storedCount(UUID tenantId, LocalDate month) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT request_count FROM core.ai_usage WHERE tenant_id = ? AND usage_month = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, month);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("a usage row for %s in %s", tenantId, month)
                        .isTrue();
                return rs.getInt(1);
            }
        }
    }
}
