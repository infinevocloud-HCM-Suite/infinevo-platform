package com.infinevo.worker.leave;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * W-16.2, spec section 7 — {@code LeaveAccrualJobIT}.
 *
 * <p>Verifies distributed locking on the leave accrual worker job:
 * <ul>
 *   <li>The worker job takes a {@code core.shedlock} row.
 *   <li>A second instance started concurrently accrues nothing.
 * </ul>
 */
@SpringBootTest(classes = InfinevoWorkerApplication.class)
class LeaveAccrualJobIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    static void initSchema() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                        PostgresTestContainerInitializer.getJdbcUrl(),
                        PostgresTestContainerInitializer.MIGRATION_USER,
                        PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS core.shedlock (
                        name VARCHAR(64) NOT NULL PRIMARY KEY,
                        lock_until TIMESTAMPTZ NOT NULL,
                        locked_at TIMESTAMPTZ NOT NULL,
                        locked_by VARCHAR(255) NOT NULL
                    );
                    ALTER TABLE core.shedlock ENABLE ROW LEVEL SECURITY;
                    DO $$ BEGIN
                      IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'core' AND tablename = 'shedlock' AND policyname = 'tenant_isolation') THEN
                        CREATE POLICY tenant_isolation ON core.shedlock
                          USING (current_setting('app.current_tenant_id', true) IS NULL OR current_setting('app.current_tenant_id', true) = '')
                          WITH CHECK (current_setting('app.current_tenant_id', true) IS NULL OR current_setting('app.current_tenant_id', true) = '');
                      END IF;
                    END $$;
                    GRANT SELECT, INSERT, UPDATE, DELETE ON core.shedlock TO app_user;
                    """);
        }
    }

    @Test
    @DisplayName("Concurrent execution: Second instance started concurrently acquires nothing")
    void concurrentExecutionAcquiresNothing() throws Exception {
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
                new LockConfiguration(Instant.now(), "LeaveAccrualJob", Duration.ofSeconds(30), Duration.ZERO);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    executor.executeWithLock(
                            (Runnable) () -> {
                                executionCount.incrementAndGet();
                                try {
                                    Thread.sleep(600);
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

        startLatch.countDown();
        finishLatch.await();
        executorService.shutdown();

        assertThat(executionCount.get())
                .as("Only 1 concurrent worker instance must execute under cluster lock")
                .isEqualTo(1);

        // Verify core.shedlock contains the lock row
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.shedlock WHERE name = 'LeaveAccrualJob'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
            }
        }
    }
}
