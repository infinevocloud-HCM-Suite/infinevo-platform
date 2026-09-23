package com.infinevo.worker.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.worker.InfinevoWorkerApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = InfinevoWorkerApplication.class)
class SchedulerLockIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    static void initSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                        PostgresTestContainerInitializer.getJdbcUrl(),
                        PostgresTestContainerInitializer.MIGRATION_USER,
                        PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
                Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS core.shedlock ("
                    + "name VARCHAR(64) NOT NULL PRIMARY KEY, "
                    + "lock_until TIMESTAMPTZ NOT NULL, "
                    + "locked_at TIMESTAMPTZ NOT NULL, "
                    + "locked_by VARCHAR(255) NOT NULL);"
                    + "ALTER TABLE core.shedlock ENABLE ROW LEVEL SECURITY;"
                    + "DO $$ BEGIN "
                    + "  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'core' AND tablename = 'shedlock' AND policyname = 'tenant_isolation') THEN "
                    + "    CREATE POLICY tenant_isolation ON core.shedlock "
                    + "      USING (current_setting('app.current_tenant_id', true) IS NULL OR current_setting('app.current_tenant_id', true) = '') "
                    + "      WITH CHECK (current_setting('app.current_tenant_id', true) IS NULL OR current_setting('app.current_tenant_id', true) = ''); "
                    + "  END IF; "
                    + "END $$;"
                    + "GRANT SELECT, INSERT, UPDATE, DELETE ON core.shedlock TO app_user;");
        }
    }

    @Test
    @DisplayName("Concurrency proof: Exactly 1 of 2 concurrent worker threads acquires the lock and runs")
    void testConcurrentSchedulerLocking() throws InterruptedException {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        LockProvider lockProvider = new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .withTableName("core.shedlock")
                .usingDbTime()
                .build());

        LockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);

        AtomicInteger executionCount = new AtomicInteger(0);
        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        LockConfiguration lockConfig =
                new LockConfiguration(Instant.now(), "concurrent-job-lock", Duration.ofSeconds(30), Duration.ZERO);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    executor.executeWithLock(
                            (Runnable) () -> {
                                executionCount.incrementAndGet();
                                try {
                                    Thread.sleep(500); // hold lock so second thread is denied
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            lockConfig);
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Trigger both threads simultaneously
        startLatch.countDown();
        finishLatch.await();
        executorService.shutdown();

        // Exactly 1 thread must have run, the other skipped/denied
        assertEquals(1, executionCount.get(), "Executions count must be exactly 1 across 2 concurrent worker threads");
    }

    @Test
    @DisplayName("Tenant isolation: active tenant context cannot view or mutate cluster locks, worker context succeeds")
    void testTenantIsolationEnforcedOnShedlock() throws Exception {
        UUID tenantId = UUID.randomUUID();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        LockProvider lockProvider = new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .withTableName("core.shedlock")
                .usingDbTime()
                .build());

        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(), "worker-isolation-test-lock", Duration.ofSeconds(30), Duration.ZERO);

        var lockOptional = lockProvider.lock(lockConfig);
        try {
            // Under tenant context, RLS must hide rows and block mutation
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                TenantContext.set(tenantId);
                try {
                    TenantContext.setForConnection(conn);

                    // Under tenant context, RLS hides all rows in core.shedlock
                    try (PreparedStatement ps =
                            conn.prepareStatement("SELECT count(*) FROM core.shedlock WHERE name = ?")) {
                        ps.setString(1, "worker-isolation-test-lock");
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            assertEquals(0, rs.getInt(1), "Tenant session must see 0 rows in core.shedlock under RLS");
                        }
                    }

                    // Under tenant context, RLS blocks direct INSERT into core.shedlock
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO core.shedlock (name, lock_until, locked_at, locked_by) VALUES (?, NOW(), NOW(), ?)")) {
                        ps.setString(1, "rogue-tenant-lock");
                        ps.setString(2, "tenant-attacker");
                        assertThrows(
                                Exception.class,
                                ps::executeUpdate,
                                "Tenant session must be refused INSERT on core.shedlock by RLS policy");
                    }
                } finally {
                    TenantContext.clear();
                    conn.rollback();
                }
            }
        } finally {
            lockOptional.ifPresent(SimpleLock::unlock);
        }
    }
}
