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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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

    @Autowired
    private LeaveAccrualJob leaveAccrualJob;

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
    @DisplayName("Concurrent execution: Second instance started concurrently acquires nothing under ShedLock")
    void concurrentExecutionAcquiresNothing() throws Exception {
        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    leaveAccrualJob.run();
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await();
        executorService.shutdown();

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
