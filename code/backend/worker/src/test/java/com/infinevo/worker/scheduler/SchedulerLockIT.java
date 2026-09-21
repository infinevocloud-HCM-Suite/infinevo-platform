package com.infinevo.worker.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.worker.InfinevoWorkerApplication;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = InfinevoWorkerApplication.class)
class SchedulerLockIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Concurrency proof: Exactly 1 of 2 concurrent worker threads acquires the lock and runs")
    void testConcurrentSchedulerLocking() throws InterruptedException {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // Ensure shedlock table exists
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS core.shedlock ("
                + "name VARCHAR(64) NOT NULL PRIMARY KEY, "
                + "lock_until TIMESTAMPTZ NOT NULL, "
                + "locked_at TIMESTAMPTZ NOT NULL, "
                + "locked_by VARCHAR(255) NOT NULL)");

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
}
